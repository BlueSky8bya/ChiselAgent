// src/client/java/whiteheaven/chiselagent/client/RobotPatchModel.java
package whiteheaven.chiselagent.client;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

import whiteheaven.chiselagent.entity.AgentEntity;

/** 플레이어 전체 모델을 그대로 쓰되, hat/rightSleeve만 선택 렌더용으로 사용하는 마스크 모델 */
public class RobotPatchModel<T extends AgentEntity> extends PlayerEntityModel<T> {

    public RobotPatchModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    /** 바닐라 PlayerEntityModel 레이아웃을 그대로 반환 (피벗/오프셋/UV 모두 일치) */
    public static TexturedModelData getTexturedModelData(boolean slim) {
        //★ 이 매핑에선 PlayerEntityModel#getTexturedModelData(Dilation, boolean)가 ModelData를 반환함
        ModelData md = PlayerEntityModel.getTexturedModelData(new Dilation(0.0F), slim);
        return TexturedModelData.of(md, 64, 64); // 64x64 스킨 캔버스
    }
}
