// src/client/java/whiteheaven/chiselagent/ChiselAgentDataGenerator.java
// 개발 시 gradlew runDatagen 등으로 레시피/태그/루트테이블/모델 JSON을 코드로 생성하는 데이터 생성 엔트리포인트

package whiteheaven.chiselagent;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

public class ChiselAgentDataGenerator implements DataGeneratorEntrypoint {
	@Override
    public void onInitializeDataGenerator(FabricDataGenerator gen) {
        var pack = gen.createPack();
        // pack.addProvider(MyEntityTypeTagProvider::new);
        // pack.addProvider(MyLootTableProvider::new);
        // pack.addProvider(MyRecipeProvider::new);
    }
}
