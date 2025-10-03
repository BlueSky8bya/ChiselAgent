// src/main/java/whiteheaven/chiselagent/registry/ModEntities.java

package whiteheaven.chiselagent.registry;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import whiteheaven.chiselagent.ChiselAgent;
import whiteheaven.chiselagent.entity.AgentEntity;

public class ModEntities {
    public static final EntityType<AgentEntity> AGENT = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(ChiselAgent.MOD_ID, "agent"),
            EntityType.Builder.create(AgentEntity::new, SpawnGroup.MISC)

                    // [히트박스/충돌 상자] 폭 0.6, 키 1.8 (플레이어와 동일)
                    //  - 충돌·피격·질식·스폰 가능 공간 체크 등 "물리적 판정"에 직접 사용됨
                    .setDimensions(0.6f, 1.8f)

                    // [클라 추적 범위] 플레이어가 이 거리(블록) 이내에 들어오면
                    //  - 서버가 해당 클라이언트에 엔티티 스폰/업데이트를 전송함
                    //  - 렌더거리/시뮬레이션거리와는 별개인 "네트워크 전송 기준"
                    .maxTrackingRange(64)

                    // [업데이트 전송 주기] 서버→클라 상태 동기화 빈도(틱)
                    //  - 1=20Hz(매 틱), 2≈10Hz, 3≈6.67Hz …
                    //  - 움직임이 거의 없다면 10~20으로 올려 네트워크 부하를 줄일 수 있음
                    .trackingTickInterval(3)

                    // [최종 빌드] 식별자 문자열은 보통 "<modid>:<path>" 형태로 넘김
                    .build(new Identifier(ChiselAgent.MOD_ID, "agent").toString())
    );
}