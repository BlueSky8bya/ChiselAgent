// src/main/java/whiteheaven/chiselagent/ChiselAgentMod.java

package whiteheaven.chiselagent;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

import whiteheaven.chiselagent.entity.AgentEntity;
import whiteheaven.chiselagent.registry.ModEntities;

public class ChiselAgentMod implements ModInitializer {
    public static final String MODID = "chisel-agent";

    @Override
    public void onInitialize() {
        // 엔티티 속성 등록
        FabricDefaultAttributeRegistry.register(ModEntities.AGENT, AgentEntity.createAttributes());

        // 간단한 테스트 커맨드: /agent spawn
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            dispatcher.register(literal("agent").then(literal("spawn").executes(ctx -> {
                ServerPlayerEntity p = ctx.getSource().getPlayer();
                if (p == null) return 0;

                var world = p.getServerWorld();
                var e = new AgentEntity(ModEntities.AGENT, world);
                e.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), 0f);
                e.setOwner(p.getUuid(), p.getGameProfile().getName());
                world.spawnEntity(e);

                ctx.getSource().sendFeedback(() -> Text.literal("Spawned Agent for " + p.getName().getString()), false);
                return 1;
            })));
        });
    }
}
