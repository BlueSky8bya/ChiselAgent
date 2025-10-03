// src/client/java/whiteheaven/chiselagent/client/AgentRenderer.java
package whiteheaven.chiselagent.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import whiteheaven.chiselagent.entity.AgentEntity;

import java.util.Optional;
import java.util.UUID;

public class AgentRenderer extends LivingEntityRenderer<AgentEntity, PlayerEntityModel<AgentEntity>> {
    private final PlayerSkinProvider skinProvider = MinecraftClient.getInstance().getSkinProvider();

    // 베이스 모델 2종(일반/슬림)
    private final PlayerEntityModel<AgentEntity> modelNormal;
    private final PlayerEntityModel<AgentEntity> modelSlim;

    public AgentRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);

        modelNormal = this.getModel();
        modelSlim   = new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true);

        // 기본 외피 끄기(겹침 방지) — 두 모델 모두
        modelNormal.hat.visible = false;
        modelNormal.rightSleeve.visible = false;
        modelSlim.hat.visible = false;
        modelSlim.rightSleeve.visible = false;

        // 금속 오버레이(마스크)도 일반/슬림 2종 전달
        var maskNormal = new RobotPatchModel<>(
                ctx.getPart(whiteheaven.chiselagent.ChiselAgentClient.AGENT_MASK_LAYER), false);
        var maskSlim   = new RobotPatchModel<>(
                ctx.getPart(whiteheaven.chiselagent.ChiselAgentClient.AGENT_MASK_LAYER_SLIM), true);
        this.addFeature(new RobotPatchFeatureRenderer<>(this, maskNormal, maskSlim));
    }

    // 렌더 직전에 베이스 모델을 스킨 규격에 맞게 교체
    @Override
    public void render(AgentEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light) {
        boolean thin = findSkinTextures(entity).model() == SkinTextures.Model.SLIM;
        this.model = thin ? modelSlim : modelNormal;
        super.render(entity, yaw, tickDelta, matrices, vcp, light);
    }

    @Override
    public Identifier getTexture(AgentEntity entity) {
        return findSkinTextures(entity).texture();
    }

    // ── 스킨 조회(슬림/일반 판별 포함) ───────────────────────
    private SkinTextures findSkinTextures(AgentEntity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Optional<UUID> uuidOpt = entity.getOwnerUuid();

        if (uuidOpt.isPresent()) {
            UUID owner = uuidOpt.get();

            if (mc.player != null && mc.player.getUuid().equals(owner)) {
                return mc.player.getSkinTextures();
            }
            if (mc.world != null) {
                PlayerEntity pe = mc.world.getPlayerByUuid(owner);
                if (pe instanceof AbstractClientPlayerEntity acp) return acp.getSkinTextures();
            }
            if (mc.getNetworkHandler() != null) {
                PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(owner);
                if (entry != null) return skinProvider.getSkinTextures(entry.getProfile());
            }
        }
        String name = entity.getOwnerName();
        GameProfile gp = uuidOpt
                .map(id -> new GameProfile(id, (name == null || name.isEmpty()) ? "Agent" : name))
                .orElseGet(() -> new GameProfile(UUID.nameUUIDFromBytes(("agent-" + entity.getId()).getBytes()), "Agent"));
        return skinProvider.getSkinTextures(gp);
    }
}
