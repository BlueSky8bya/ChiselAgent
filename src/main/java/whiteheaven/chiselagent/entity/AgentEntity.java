// src/main/java/whiteheaven/chiselagent/entity/AgentEntity.java

package whiteheaven.chiselagent.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import net.minecraft.text.Text;

import java.util.Optional;
import java.util.UUID;

public class AgentEntity extends MobEntity {
    private static final TrackedData<Optional<UUID>> OWNER_UUID =
            DataTracker.registerData(AgentEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<String> OWNER_NAME =
            DataTracker.registerData(AgentEntity.class, TrackedDataHandlerRegistry.STRING);

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0);
    }

    public AgentEntity(EntityType<? extends MobEntity> type, World world) {
        super(type, world);
        this.setHealth(20f);
        this.setInvulnerable(true); // 대부분 피해 무시
        this.setAiDisabled(true);   // AI 비활성
        this.setNoGravity(true);    // 중력 없음
        this.setPersistent();       // 언로드 방지
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(OWNER_UUID, Optional.empty());
        this.dataTracker.startTracking(OWNER_NAME, "");
    }

    public void setOwner(UUID uuid, String name) {
        this.dataTracker.set(OWNER_UUID, Optional.ofNullable(uuid));
        this.dataTracker.set(OWNER_NAME, name == null ? "" : name);

        // (플레이어가 나중에 이름을 바꿔주면 그대로 존중하기 위해 hasCustomName() 체크)
        if (!this.hasCustomName()){
            String owner = this.getOwnerName();
            String label = (owner == null || owner.isBlank()) ? "Agent" : owner + "의 노예";
            this.setCustomName(Text.literal(label));
            this.setCustomNameVisible(true);    // 항상 보이게
        }
    }

    public Optional<UUID> getOwnerUuid() { return this.dataTracker.get(OWNER_UUID); }
    public String getOwnerName() { return this.dataTracker.get(OWNER_NAME); }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("OwnerUUID")) {
            this.dataTracker.set(OWNER_UUID, Optional.of(nbt.getUuid("OwnerUUID")));
        }
        this.dataTracker.set(OWNER_NAME, nbt.getString("OwnerName"));

        // 월드 로드시도, 커스텀 이름이 비어 있다면 소유자명 기반으로 기본 이름을 재구성
        if (!this.hasCustomName()) {
            String owner = this.getOwnerName();
            String label = (owner == null || owner.isBlank()) ? "Agent" : owner + "의 노예";
            this.setCustomName(Text.literal(label));
            this.setCustomNameVisible(true);
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        this.getOwnerUuid().ifPresent(uuid -> nbt.putUuid("OwnerUUID", uuid));
        nbt.putString("OwnerName", this.getOwnerName());
    }

    @Override public boolean isPushable() { return false; }

    // 완전 무적 (OutOfWorld/마법 등 포함)
    @Override public boolean isInvulnerableTo(DamageSource source) { return true; }
    @Override public boolean damage(DamageSource source, float amount) { return false; }

}