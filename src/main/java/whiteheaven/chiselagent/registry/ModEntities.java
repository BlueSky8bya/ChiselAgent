// src/main/java/whiteheaven/chiselagent/registry/ModEntities.java

package whiteheaven.chiselagent.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import whiteheaven.chiselagent.ChiselAgentMod;
import whiteheaven.chiselagent.entity.AgentEntity;

public class ModEntities {
    public static final EntityType<AgentEntity> AGENT = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(ChiselAgentMod.MODID, "agent"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, AgentEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                    .trackRangeBlocks(64).trackedUpdateRate(3)
                    .build()
    );
}
