// src/main/java/whiteheaven/chiselagent/agent/AgentSpawner.java
package whiteheaven.chiselagent.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * 에이전트(ArmorStand) 스폰 & 관리 유틸
 *
 * 정책(최신):
 * - JOIN(접속)     : 해당 플레이어의 에이전트를 전부 정리(0마리 보장)  ← 자동 스폰 없음
 * - /agent spawn  : **멱등 스폰** — 있으면 1마리만 유지하고 위치/차원 갱신, 없으면 새로 1마리 스폰
 * - DISCONNECT(종료): 전부 정리(월드에 잔여 엔티티 남지 않음)
 *
 * 태그:
 *  - 종류:  "chisel_agent"
 *  - 소유:  "owner:<플레이어UUID>"
 */
public final class AgentSpawner {
    private AgentSpawner() {}

    private static final Logger LOG = LoggerFactory.getLogger("chisel-agent");
    private static final String TAG_AGENT = "chisel_agent";
    private static final String TAG_OWNER_PREFIX = "owner:";
    private static final Box WORLD_BOX = new Box(-3.0E7, -512, -3.0E7, 3.0E7, 512, 3.0E7);
    private static final int SEARCH_RADIUS = 2; // 안전 위치 탐색 반경(고정)

    /* ===================== 퍼블릭 API ===================== */

    /** (레거시 호환용) 접속 시 호출하던 메서드 — 현재는 "정리만" 수행 */
    @Deprecated
    public static void ensureAgentNear(ServerPlayerEntity player) {
        despawnAllFor(player);
    }

    /** 해당 플레이어 소유 에이전트를 "전 월드"에서 모두 제거(0마리 보장) */
    public static void despawnAllFor(ServerPlayerEntity player) {
        ServerWorld anyWorld = (ServerWorld) player.getWorld();
        UUID owner = player.getUuid();
        var list = findOwnedAgentsAcrossServer(anyWorld.getServer(), owner);
        for (var as : list) as.discard();
        LOG.info("despawnAll owner={} ownerUuid={} removed={}",
                player.getGameProfile().getName(), owner, list.size());
    }

    /**
     * 스폰(멱등): 있으면 1마리 유지+위치/차원 갱신, 없으면 1마리 새로 스폰
     * @return 실제로 배치/유지 성공 시 true, 스폰 실패 등 예외적 상황 시 false
     */
    public static boolean spawnOneFor(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        UUID owner = player.getUuid();

        var owned = findOwnedAgentsAcrossServer(world.getServer(), owner);

        // 안전 위치
        Vec3d pos = findSafeNearbyPos(world, player.getBlockPos());
        if (pos == null) pos = player.getPos().add(0, 0.1, 0);

        if (!owned.isEmpty()) {
            var keep = pickNearest(owned, player.getPos());
            int removed = 0;
            for (var as : owned) if (as != keep) { as.discard(); removed++; }

            // 차원 불일치 시 이동(실패 시 재소환)
            if (keep.getWorld() != world) {
                var fromDim = keep.getWorld().getRegistryKey().getValue();
                var toDim   = world.getRegistryKey().getValue();
                var moved = keep.moveToWorld(world);
                if (moved instanceof ArmorStandEntity movedAs) {
                    movedAs.refreshPositionAndAngles(pos.x, pos.y, pos.z, player.getYaw(), 0);
                    LOG.info("spawn(reuse-move) owner={} ownerUuid={} agentUuid={} fromDim={} toDim={} removed={} target={},{},{}",
                            player.getGameProfile().getName(), owner, movedAs.getUuid(),
                            fromDim, toDim, removed, pos.x, pos.y, pos.z);
                    return true;
                } else {
                    keep.discard();
                    boolean ok = spawnNewAgent(world, player, pos); // 내부에서 로그
                    LOG.info("spawn(reuse-move-fallback) owner={} ownerUuid={} fallback=respawn removed={} result={}",
                            player.getGameProfile().getName(), owner, removed, ok);
                    return ok;
                }
            } else {
                keep.refreshPositionAndAngles(pos.x, pos.y, pos.z, player.getYaw(), 0);
                LOG.info("spawn(reuse) owner={} ownerUuid={} agentUuid={} dim={} removed={} target={},{},{}",
                        player.getGameProfile().getName(), owner, keep.getUuid(),
                        world.getRegistryKey().getValue(), removed, pos.x, pos.y, pos.z);
                return true;
            }
        }

        // 없으면 새로 1마리 생성
        return spawnNewAgent(world, player, pos); // 성공 여부 그대로 반환(경고 해소)
    }

    /** 내 에이전트를 현재 위치 근처로 이동(여러 마리면 1마리만 유지) */
    public static boolean callToPlayer(ServerPlayerEntity player) {
        ServerWorld playerWorld = (ServerWorld) player.getWorld();
        UUID owner = player.getUuid();

        var owned = findOwnedAgentsAcrossServer(playerWorld.getServer(), owner);
        if (owned.isEmpty()) return false;

        var keep = pickNearest(owned, player.getPos());
        int removed = 0;
        for (ArmorStandEntity as : owned) if (as != keep) { as.discard(); removed++; }

        Vec3d pos = findSafeNearbyPos(playerWorld, player.getBlockPos());
        if (pos == null) pos = player.getPos().add(0, 0.1, 0);

        if (keep.getWorld() != playerWorld) {
            var fromDim = keep.getWorld().getRegistryKey().getValue();
            var toDim   = playerWorld.getRegistryKey().getValue();
            var moved = keep.moveToWorld(playerWorld);
            if (moved instanceof ArmorStandEntity movedAs) {
                movedAs.refreshPositionAndAngles(pos.x, pos.y, pos.z, player.getYaw(), 0);
                LOG.info("call(move) owner={} ownerUuid={} agentUuid={} fromDim={} toDim={} removed={} target={},{},{}",
                        player.getGameProfile().getName(), owner, movedAs.getUuid(),
                        fromDim, toDim, removed, pos.x, pos.y, pos.z);
            } else {
                keep.discard();
                boolean ok = spawnNewAgent(playerWorld, player, pos);
                LOG.info("call(move-fallback) owner={} ownerUuid={} fallback=respawn removed={} result={}",
                        player.getGameProfile().getName(), owner, removed, ok);
                return ok;
            }
        } else {
            keep.refreshPositionAndAngles(pos.x, pos.y, pos.z, player.getYaw(), 0);
            LOG.info("call(reposition) owner={} ownerUuid={} agentUuid={} dim={} removed={} target={},{},{}",
                    player.getGameProfile().getName(), owner, keep.getUuid(),
                    playerWorld.getRegistryKey().getValue(), removed, pos.x, pos.y, pos.z);
        }
        return true;
    }

    /** (조회 전용) 플레이어의 에이전트 한 마리(가장 가까운 개체) 반환 — 없으면 Optional.empty() */
    public static Optional<ArmorStandEntity> findNearestFor(ServerPlayerEntity player) {
        ServerWorld anyWorld = (ServerWorld) player.getWorld();
        UUID owner = player.getUuid();
        var owned = findOwnedAgentsAcrossServer(anyWorld.getServer(), owner);
        if (owned.isEmpty()) return Optional.empty();
        return Optional.ofNullable(pickNearest(owned, player.getPos()));
    }

    /* ===================== 내부 유틸 ===================== */

    /** 서버의 모든 월드에서 owner 소유 에이전트를 태그로 탐색/수집 */
    private static List<ArmorStandEntity> findOwnedAgentsAcrossServer(MinecraftServer server, UUID owner) {
        String ownerTag = TAG_OWNER_PREFIX + owner.toString();
        List<ArmorStandEntity> all = new ArrayList<>();
        for (ServerWorld w : server.getWorlds()) {
            List<ArmorStandEntity> found = w.getEntitiesByClass(
                    ArmorStandEntity.class, WORLD_BOX,
                    as -> as.isAlive()
                            && as.getCommandTags().contains(TAG_AGENT)
                            && as.getCommandTags().contains(ownerTag)
            );
            all.addAll(found);
        }
        return all;
    }

    /** 플레이어 기준 가장 가까운 에이전트 선택(동거리 시 첫 요소) */
    private static ArmorStandEntity pickNearest(List<ArmorStandEntity> list, Vec3d ref) {
        return list.stream()
                .min(Comparator.comparingDouble(e -> e.getPos().squaredDistanceTo(ref)))
                .orElse(null);
    }

    /** 반경(SEARCH_RADIUS) 내 공기 2블록(머리 공간 포함) 안전 지점 탐색 → 중앙(Vec3d) 반환 */
    private static Vec3d findSafeNearbyPos(ServerWorld world, BlockPos center) {
        int r = SEARCH_RADIUS;
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos base = center.add(dx, dy, dz);
                    if (isTwoHighAir(world, base)) {
                        return Vec3d.ofCenter(base);
                    }
                }
            }
        }
        return null;
    }

    /** 머리 공간 포함 2블록이 공기인지 체크 */
    private static boolean isTwoHighAir(ServerWorld world, BlockPos base) {
        return world.getBlockState(base).isAir()
                && world.getBlockState(base.up()).isAir();
    }

    /** 실제 스폰 로직(태그/속성 세팅 포함). 성공 여부 반환 */
    private static boolean spawnNewAgent(ServerWorld world, ServerPlayerEntity player, Vec3d pos) {
        ArmorStandEntity as = new ArmorStandEntity(world, pos.x, pos.y, pos.z);
        as.setCustomName(Text.literal(player.getGameProfile().getName() + "의 Agent"));
        as.setCustomNameVisible(true);
        as.setNoGravity(true);
        as.setSilent(true);
        as.setInvulnerable(true);
        as.setShowArms(true);

        // 태그 기록(서버 재시작 후에도 유지)
        as.addCommandTag(TAG_AGENT);
        as.addCommandTag(TAG_OWNER_PREFIX + player.getUuid().toString());

        boolean added = world.spawnEntity(as);
        if (added) {
            LOG.info("spawn(new) owner={} ownerUuid={} agentUuid={} dim={} pos={},{},{}",
                    player.getGameProfile().getName(), player.getUuid(), as.getUuid(),
                    world.getRegistryKey().getValue(), pos.x, pos.y, pos.z);
        } else {
            LOG.warn("spawn(new-failed) owner={} ownerUuid={} dim={} pos={},{},{}",
                    player.getGameProfile().getName(), player.getUuid(),
                    world.getRegistryKey().getValue(), pos.x, pos.y, pos.z);
        }
        return added;
    }
}