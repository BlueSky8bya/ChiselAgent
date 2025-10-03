// src/client/java/whiteheaven/chiselagent/client/RobotPatchFeatureRenderer.java

package whiteheaven.chiselagent.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import whiteheaven.chiselagent.ChiselAgentMod;
import whiteheaven.chiselagent.entity.AgentEntity;

public class RobotPatchFeatureRenderer<T extends AgentEntity>
        extends FeatureRenderer<T, PlayerEntityModel<T>> {

    private static final Identifier PATCH_TEXTURE =
            new Identifier(ChiselAgentMod.MODID, "textures/entity/agent/robot_patch.png");

    private final RobotPatchModel<T> maskModel;

    public RobotPatchFeatureRenderer(FeatureRendererContext<T, PlayerEntityModel<T>> ctx,
                                     RobotPatchModel<T> maskModel) {
        super(ctx);
        this.maskModel = maskModel;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light,
                       T entity, float limbAngle, float limbDistance, float tickDelta,
                       float animationProgress, float headYaw, float headPitch) {

        // 기본 플레이어 포즈를 마스크 모델에 복사
        this.getContextModel().copyStateTo(maskModel);

        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(PATCH_TEXTURE));
        maskModel.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f);
    }
}
