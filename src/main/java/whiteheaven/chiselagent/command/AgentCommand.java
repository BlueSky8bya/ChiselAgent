// src/main/java/whiteheaven/chiselagent/command/AgentCommand.java
package whiteheaven.chiselagent.command;

import static net.minecraft.server.command.CommandManager.literal;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos; // Vec3d → BlockPos 변환(Yarn/Fabric 1.20.4)
import whiteheaven.chiselagent.agent.AgentSpawner;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * /agent — 에이전트 전용 명령어 모음
 *
 * 환경: Fabric 1.20.4 / Fabric API v0.97.x
 *
 * 루트: /agent
 *  - /agent                 : 간단 도움말/상태 요약
 *  - /agent spawn           : (플레이어 전용) 멱등 스폰 — 있으면 1마리 유지+위치/차원 갱신, 없으면 새로 1마리 스폰
 *  - /agent call            : (플레이어 전용) 에이전트를 내 근처(반경 2칸)로 소환/이동(여러 마리면 1마리만 유지)
 *  - /agent where           : (플레이어 전용) 에이전트의 현재 위치 출력(차원/좌표/거리)
 *  - /agent despawn         : (플레이어 전용) 내 에이전트 전부 제거(월드에서 0마리 보장)
 *
 * 등록: ChiselAgent#onInitialize() → AgentCommand.register()
 * 권한: 현재 .requires(0) → 모두 사용 가능. 필요하면 개별 서브커맨드에 OP 레벨(2~4) 부여.
 */
public final class AgentCommand {
    private AgentCommand() {}

    // 플레이어별 /agent spawn 쿨다운(틱): 40틱=2초
    private static final Map<UUID, Integer> LAST_SPAWN_TICK = new HashMap<>();
    private static final int SPAWN_COOLDOWN_TICKS = 40;

    /** 공통 응답 헬퍼 (실행자에게만 표시) */
    private static void send(ServerCommandSource src, String msg) {
        src.sendFeedback(() -> Text.literal("[에이전트] " + msg), false);
    }

    /** 커맨드 트리 등록 */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    literal("agent")
                            .requires(src -> src.hasPermissionLevel(0))

                            // /agent : 간단 도움말 & 상태 요약
                            .executes(ctx -> {
                                var src = ctx.getSource();
                                var player = src.getPlayer();
                                if (player == null) {
                                    send(src, "사용법: /agent spawn | call | where | despawn (플레이어 전용)");
                                    return 1;
                                }
                                var opt = AgentSpawner.findNearestFor(player);
                                if (opt.isEmpty()) {
                                    send(src, "상태: 에이전트 없음. /agent spawn 으로 생성하세요.");
                                } else {
                                    var as = opt.get();
                                    var dim = as.getWorld().getRegistryKey().getValue().toString();
                                    var pos = as.getPos();
                                    double dist = pos.distanceTo(player.getPos());
                                    BlockPos bp = BlockPos.ofFloored(pos);
                                    send(src, String.format(
                                            "상태: 존재함 | 차원=%s | 좌표=%.2f/%.2f/%.2f (블록 %d,%d,%d) | 플레이어와 거리=%.1f",
                                            dim, pos.x, pos.y, pos.z, bp.getX(), bp.getY(), bp.getZ(), dist
                                    ));
                                }
                                return 1;
                            })

                            // /agent spawn : 멱등 스폰
                            .then(literal("spawn")
                                    .executes(ctx -> {
                                        var src = ctx.getSource();
                                        var player = src.getPlayer();
                                        if (player == null) {
                                            send(src, "콘솔에서는 사용할 수 없어요");
                                            return 0;
                                        }

                                        // 쿨다운 체크
                                        MinecraftServer server = src.getServer();
                                        int now = server.getTicks();
                                        UUID uid = player.getUuid();
                                        int last = LAST_SPAWN_TICK.getOrDefault(uid, -1000000);
                                        if (now - last < SPAWN_COOLDOWN_TICKS) {
                                            int wait = SPAWN_COOLDOWN_TICKS - (now - last);
                                            send(src, String.format("잠시 후 다시 시도하세요 (%.1f초)", wait / 20.0));
                                            return 0;
                                        }

                                        // 이미 노예가 있었는지 사전 체크
                                        boolean had = AgentSpawner.findNearestFor(player).isPresent();
                                        boolean ok = AgentSpawner.spawnOneFor(player); // 멱등 보장
                                        if (ok) LAST_SPAWN_TICK.put(uid, now);
                                        if (!ok) {
                                            send(src, "노예 생성에 실패했어요. 잠시 후 다시 시도해주세요.");
                                            return 0;
                                        }
                                        send(src, had ? "놀고있던 노예를 여기로 불렀어요." : "노예를 소환했어요.");
                                        return 1;
                                    })
                            )

                            // /agent call : 내 근처로 이동(여러 마리면 1마리만 유지)
                            .then(literal("call")
                                    .executes(ctx -> {
                                        var src = ctx.getSource();
                                        var player = src.getPlayer();
                                        if (player == null) {
                                            send(src, "콘솔에서는 사용할 수 없어요");
                                            return 0;
                                        }
                                        boolean ok = AgentSpawner.callToPlayer(player);
                                        send(src, ok ? "여기예요!" : "노예가 아직 없어요. /agent spawn으로 소환하세요.");
                                        return ok ? 1 : 0;
                                    })
                            )

                            // /agent where : 노예의 현재 위치 출력
                            .then(literal("where")
                                    .executes(ctx -> {
                                        var src = ctx.getSource();
                                        var player = src.getPlayer();
                                        if (player == null) {
                                            send(src, "콘솔에서는 사용할 수 없어요");
                                            return 0;
                                        }
                                        var opt = AgentSpawner.findNearestFor(player);
                                        if (opt.isEmpty()) {
                                            send(src, "노예가 없습니다. /agent spawn으로 소환하세요.");
                                            return 0;
                                        }
                                        var as = opt.get();
                                        var dim = as.getWorld().getRegistryKey().getValue().toString();
                                        var pos = as.getPos();
                                        double dist = pos.distanceTo(player.getPos());
                                        BlockPos bp = BlockPos.ofFloored(pos);
                                        send(src, String.format(
                                                "노예 위치: 차원=%s 좌표=%.2f/%.2f/%.2f | 플레이어와 거리=%.1f",
                                                dim, pos.x, pos.y, pos.z, dist
                                        ));
                                        return 1;
                                    })
                            )

                            // /agent despawn : 내 노예 전부 제거 (없으면 안내)
                            .then(literal("despawn")
                                    .executes(ctx -> {
                                        var src = ctx.getSource();
                                        var player = src.getPlayer();
                                        if (player == null) {
                                            send(src, "콘솔에서는 사용할 수 없어요");
                                            return 0;
                                        }

                                        var hasSlave = AgentSpawner.findNearestFor(player).isPresent();
                                        if (!hasSlave) {
                                            send(src, "노예가 없어요. /agent spawn으로 소환하세요.");
                                            return 0;
                                        }

                                        AgentSpawner.despawnAllFor(player);
                                        send(src, "노예를 제거했어요.");
                                        return 1;
                                    })
                            )
            );
        });
    }
}
