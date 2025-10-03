// src/client/java/whiteheaven/chiselagent/client/AgentRenderer.java

package whiteheaven.chiselagent.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import whiteheaven.chiselagent.entity.AgentEntity;

import java.util.Optional;
import java.util.UUID;

public class AgentRenderer extends LivingEntityRenderer<AgentEntity, PlayerEntityModel<AgentEntity>> {
    private final PlayerSkinProvider skinProvider = MinecraftClient.getInstance().getSkinProvider();

    public AgentRenderer(EntityRendererFactory.Context ctx) {
        // 기본(비-슬림) 플레이어 모델 사용
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);

        // 오른쪽 머리 금속 패치 feature 부착
        this.addFeature(new RobotPatchFeatureRenderer<>(this,
                new RobotPatchModel<>(ctx.getPart(whiteheaven.chiselagent.ChiselAgentClient.AGENT_MASK_LAYER))));
    }

    @Override
    public Identifier getTexture(AgentEntity entity) {
        // 소유자 UUID/이름으로 스킨 획득 (없으면 기본 스킨)
        Optional<UUID> uuidOpt = entity.getOwnerUuid();
        String name = entity.getOwnerName();

        GameProfile profile = uuidOpt
                .map(uuid -> new GameProfile(uuid, (name == null || name.isEmpty()) ? "Agent" : name))
                .orElseGet(() -> new GameProfile(
                        UUID.nameUUIDFromBytes(("agent-" + entity.getId()).getBytes()), "Agent"));

        SkinTextures skin = skinProvider.getSkinTextures(profile);
        return skin.texture(); // 기본 스킨 텍스처
    }
}

