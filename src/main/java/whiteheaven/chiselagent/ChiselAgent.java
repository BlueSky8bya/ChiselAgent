// src/main/java/whiteheaven/chiselagent/ChiselAgent.java
package whiteheaven.chiselagent;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import whiteheaven.chiselagent.command.AgentCommand;
import whiteheaven.chiselagent.agent.AgentSpawner;
import whiteheaven.chiselagent.entity.AgentEntity;
import whiteheaven.chiselagent.registry.ModEntities;

public class ChiselAgent implements ModInitializer {
    public static final String MOD_ID = "chisel-agent";

    @Override
    public void onInitialize() {
        // 커스텀 엔티티 속성 등록 (원래 ChiselAgentMod가 하던 것)
        FabricDefaultAttributeRegistry.register(ModEntities.AGENT, AgentEntity.createAttributes());

        AgentCommand.register(); // "/agent" 명령어

        //  ① 접속 시: 남아있는 에이전트 정리해서 아무도 안 남게 함
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> AgentSpawner.despawnAllFor(handler.getPlayer())));

        // ② 종료 시: 해당 플레이어 소유 에이전트 전부 제거(월드에 안 남게)
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
            var player = handler.getPlayer();
            if (player != null) {
                AgentSpawner.despawnAllFor(player);
            }
        }));
    }
}
