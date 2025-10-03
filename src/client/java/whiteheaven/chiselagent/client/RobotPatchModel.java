// src/client/java/whiteheaven/chiselagent/client/RobotPatchModel.java
package whiteheaven.chiselagent.client;

import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import whiteheaven.chiselagent.entity.AgentEntity;

/** 머리 오른쪽만 덮는 얇은 금속판 모델 (PlayerEntityModel의 필수 파츠들을 빈 파츠로 모두 포함) */
public class RobotPatchModel<T extends AgentEntity> extends PlayerEntityModel<T>{
    public RobotPatchModel(ModelPart root) { super(root, false); }

    public static TexturedModelData getTexturedModelData() {
        // PlayerEntityModel이 요구하는 모든 파츠 이름을 root에 추가해야 함
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();

        // 기본 파츠
        ModelPartData head = root.addChild("head", ModelPartBuilder.create(), ModelTransform.NONE);
        root.addChild("hat",          ModelPartBuilder.create(), ModelTransform.NONE);
        root.addChild("body",         ModelPartBuilder.create(), ModelTransform.NONE);
        root.addChild("right_arm",    ModelPartBuilder.create(), ModelTransform.NONE);
        root.addChild("left_arm",     ModelPartBuilder.create(), ModelTransform.NONE);
        root.addChild("right_leg",    ModelPartBuilder.create(), ModelTransform.NONE);
        root.addChild("left_leg",     ModelPartBuilder.create(), ModelTransform.NONE);

        // ▶ PlayerEntityModel 전용 추가 파츠 (없으면 NSEE: ear)
        root.addChild("ear",          ModelPartBuilder.create(), ModelTransform.NONE);
        root.addChild("cloak",        ModelPartBuilder.create(), ModelTransform.NONE);
        root.addChild("left_sleeve",  ModelPartBuilder.create(), ModelTransform.NONE);
        root.addChild("right_sleeve", ModelPartBuilder.create(), ModelTransform.NONE);
        root.addChild("left_pants",   ModelPartBuilder.create(), ModelTransform.NONE);
        root.addChild("right_pants",  ModelPartBuilder.create(), ModelTransform.NONE);
        root.addChild("jacket",       ModelPartBuilder.create(), ModelTransform.NONE);

        // 머리 오른쪽 금속판 (살짝 돌출)
        head.addChild("robot_right_plate",
                ModelPartBuilder.create()
                        .uv(0, 0)
                        .cuboid(0.0f, -8.0f, -4.0f, 4.0f, 8.0f, 0.6f, new Dilation(0.51f)),
                ModelTransform.NONE
        );

        return TexturedModelData.of(modelData, 64, 64);
    }
}
