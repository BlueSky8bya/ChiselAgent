// src/client/java/whiteheaven/chiselagent/ChiselAgentClient.java

package whiteheaven.chiselagent;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;
import whiteheaven.chiselagent.client.AgentRenderer;
import whiteheaven.chiselagent.client.RobotPatchModel;
import whiteheaven.chiselagent.registry.ModEntities;

public class ChiselAgentClient implements ClientModInitializer {
    public static final EntityModelLayer AGENT_MASK_LAYER =
            new EntityModelLayer(new Identifier(ChiselAgentMod.MODID, "agent_mask"), "main");

    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(AGENT_MASK_LAYER, RobotPatchModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.AGENT, AgentRenderer::new);

    }
}