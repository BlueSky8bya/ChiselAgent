// src/client/java/whiteheaven/chiselagent/client/RobotPatchFeatureRenderer.java
package whiteheaven.chiselagent.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import whiteheaven.chiselagent.ChiselAgent;
import whiteheaven.chiselagent.entity.AgentEntity;

import java.util.Optional;
import java.util.UUID;

public class RobotPatchFeatureRenderer<T extends AgentEntity>
        extends FeatureRenderer<T, PlayerEntityModel<T>> {

    private static final Identifier PATCH_TEXTURE =
            new Identifier(ChiselAgent.MOD_ID, "textures/entity/agent/robot_patch.png");

    private final RobotPatchModel<T> maskNormal;
    private final RobotPatchModel<T> maskSlim;

    public RobotPatchFeatureRenderer(FeatureRendererContext<T, PlayerEntityModel<T>> ctx,
                                     RobotPatchModel<T> maskNormal,
                                     RobotPatchModel<T> maskSlim) {
        super(ctx);
        this.maskNormal = maskNormal;
        this.maskSlim = maskSlim;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light,
                       T entity, float limbAngle, float limbDistance, float tickDelta,
                       float animationProgress, float headYaw, float headPitch) {

        PlayerEntityModel<T> ctx = this.getContextModel();
        RobotPatchModel<T> mask = usesThinArms(entity) ? maskSlim : maskNormal;

        // 1) 현재 프레임의 포즈/가시성 복사
        ctx.copyStateTo(mask);
        // 2) 현재 프레임의 애니메이션/각도 적용(팔 스윙, 고개 회전 등)
        mask.animateModel(entity, limbAngle, limbDistance, tickDelta); // 안전용
        mask.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);

        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(PATCH_TEXTURE));

        // 머리 오버레이 + 오른팔 오버레이만 렌더 (기본 hat/rightSleeve는 꺼둔 상태)
        mask.hat.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f);
        mask.rightSleeve.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f);
    }

    /** 현재 스킨이 슬림(Alex)인지 — 1.20.4에서는 enum 비교가 안전 */
    private boolean usesThinArms(T entity) {
        SkinTextures st = AgentSkin.findSkinTextures(entity);
        return st != null && st.model() == SkinTextures.Model.SLIM;
    }

    /** 스킨 조회 헬퍼 (AgentRenderer와 동일 로직) */
    static final class AgentSkin {
        static SkinTextures findSkinTextures(AgentEntity entity) {
            MinecraftClient mc = MinecraftClient.getInstance();
            Optional<UUID> uuid = entity.getOwnerUuid();

            if (uuid.isPresent() && mc.world != null) {
                PlayerEntity pe = mc.world.getPlayerByUuid(uuid.get());
                if (pe instanceof AbstractClientPlayerEntity acp) return acp.getSkinTextures();
            }
            if (uuid.isPresent() && mc.getNetworkHandler() != null) {
                var entry = mc.getNetworkHandler().getPlayerListEntry(uuid.get());
                if (entry != null) return mc.getSkinProvider().getSkinTextures(entry.getProfile());
            }
            String name = entity.getOwnerName();
            GameProfile gp = uuid
                    .map(id -> new GameProfile(id, (name == null || name.isEmpty()) ? "Agent" : name))
                    .orElseGet(() -> new GameProfile(UUID.nameUUIDFromBytes(("agent-" + entity.getId()).getBytes()), "Agent"));
            return mc.getSkinProvider().getSkinTextures(gp);
        }
    }
}
