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

    // ▶ 분리 텍스처
    private static final Identifier CLASSIC_TEX =
            new Identifier(ChiselAgent.MOD_ID, "textures/entity/agent/robot_patch_classic.png");
    private static final Identifier SLIM_TEX =
            new Identifier(ChiselAgent.MOD_ID, "textures/entity/agent/robot_patch_slim.png");

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

        // 1) 소유자 스킨 규격 감지 (슬림/와이드)
        SkinTextures st = AgentSkin.findSkinTextures(entity);
        boolean thin = st != null && st.model() == SkinTextures.Model.SLIM;

        // 2) 소스(본체) 포즈를 마스크로 복사
        PlayerEntityModel<T> src = this.getContextModel();
        RobotPatchModel<T> mask = thin ? maskSlim : maskNormal;
        src.copyStateTo(mask);

        // 3) 마스크에도 같은 프레임의 애니메이션/각도 적용
        //    (이걸 안 하면 본체와 분리되어 보입니다)
        mask.animateModel(entity, limbAngle, limbDistance, tickDelta);
        mask.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);

        // 4) 텍스처 선택 + 컷아웃 렌더 (반투명 블렌딩 금지)
        Identifier tex = thin ? SLIM_TEX : CLASSIC_TEX;
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(tex));

        // 5) 필요한 파츠만 렌더
        mask.hat.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 1f,1f,1f,1f);
        mask.rightSleeve.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 1f,1f,1f,1f);
    }

    /** 스킨 헬퍼: AgentRenderer와 동일 로직 */
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
            // 폴백: 이름/UUID로 캐시 조회
            String name = entity.getOwnerName();
            GameProfile gp = uuid
                    .map(id -> new GameProfile(id, (name == null || name.isEmpty()) ? "Agent" : name))
                    .orElseGet(() -> new GameProfile(UUID.nameUUIDFromBytes(("agent-" + entity.getId()).getBytes()), "Agent"));
            return mc.getSkinProvider().getSkinTextures(gp);
        }
    }
}
