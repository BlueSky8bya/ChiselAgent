// src/main/java/whiteheaven/chiselagent/entity/AgentEntity.java
package whiteheaven.chiselagent.entity;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;

/**
 * AgentEntity - 플레이어 전용 AI 동반자
 * <p>
 * 핵심 기능:
 * - 주인 플레이어의 이동 상태(지상/수영/비행/탑승)에 맞춰 자동 추적
 * - AI/Pathfinding 없이 직접 위치 제어로 부드러운 동행
 * - 뒤쪽 130°~170° 궤도 영역에서 랜덤 위치 유지
 * - 수영 시 수평 누운 자세 + 허우적거림 애니메이션 완벽 구현
 * - 물가 턱 자동 오르기, 차원 이동 자동 추적
 * - 무적 처리 및 충돌 무시
 * </p>
 * MC 1.20.4 / Yarn 1.20.4+build.3 / Fabric Loader 0.16.9 / Fabric API 0.97.2+1.20.4
 */
public class AgentEntity extends MobEntity {

    // ── 속도 프로파일(틱당 최대 이동량; ≒블록/틱) ─────────────────────────
    private static final double WALK_SPEED = 0.2;   // ≈ 4블록/초 (플레이어 보통 걷기 ≈4.3블록/초; 에이전트를 살짝 느리게 설정)
    private static final double SWIM_SPEED = 0.27;  // ≈ 5.4블록/초 (수영 시 속도; 스프린트 수영 ≈5.6블록/초 수준)
    private static final double FLY_SPEED  = 0.43;  // ≈ 8.6블록/초 (비행 추격 속도; 엘리트라 활공 시 수평 속도 ≈8~10블록/초 참고)
    private static final double BOAT_SPEED = 0.45;  // ≈ 9.0블록/초 (보트 탑승 플레이어 추격용; 보트 일반 속도 ≈8블록/초)
    private static final double MOUNT_SPEED= 0.4;   // ≈ 8블록/초 (그 외 탈것 추격용; 말 평균 질주 ≈7~9블록/초)

    private static final double SLOW_RADIUS = 1.6; // 너무 가까우면 (1.6블록 이내) 감속
    private static final double FAST_RADIUS = 8.0; // 멀리 떨어지면 (8블록 이상) 가속

    private static final double TELEPORT_DISTANCE = 30.0; // 너무 멀면 (≈30블록) 텔레포트로 따라잡기
    private static final double HEIGHT_LERP = 0.25;       // 수직 보정 비율 (높이 차의 25%만 즉시 반영하여 부드럽게 고도 맞춤)

    // 시야 동기화 임계값: 에이전트가 플레이어 앞/옆에 있으면 같은 방향 유지, 뒤에 있으면 플레이어 쪽 바라봄
    private static final double AHEAD_SAME_DIR   = 0.15;
    private static final double LATERAL_SAME_DIR = 0.75;
    // (앞쪽 판정: forward 방향 내적 >0.15, 옆쪽 판정: 오른쪽 방향 내적 절대값 >0.75)

    // ── 플레이어 뒤쪽 궤도(Orbital) 추종 위치 설정 ───────────────────────
    // 정면을 0°로 할 때 뒤쪽 130°~170° 범위에서 랜덤 각도로 일정 거리 유지
    private static final double BACK_MIN_DEG   = 130.0;
    private static final double BACK_MAX_DEG   = 170.0;
    private static final double ORBIT_MIN_RADIUS = 2.6;
    private static final double ORBIT_MAX_RADIUS = 4.2;
    private static final int    ORBIT_MIN_TICKS  = 60;
    private static final int    ORBIT_MAX_TICKS  = 140;
    // (랜덤 각도 130°~170°, 거리 2.6~4.2블록 설정하여 ORBIT_MIN_TICKS~MAX_TICKS 틱 동안 유지 후 새 위치 선정)
    // 이렇게 약간씩 좌우 위치를 바꿔가며 뒤를 따라다녀 항상 같은 위치에 있지 않도록 함.
    // (플레이어 1인칭 시야에서 가끔 보일 수 있도록 좌/우 교대 배치 효과)

    // 플레이어 움직임 판정 임계값
    private static final double PLAYER_MOVING_THRESHOLD = 0.02;  // 이 속도 미만이면 정지로 간주 (블록/틱)
    private static final double MOVING_BACK_THRESHOLD   = 0.005; // -0.005 이하의 전진 속도 성분이면 뒤로 이동 중으로 간주

    // 플레이어 수중 판정 비율
    private static final double SWIM_DEPTH_RATIO = 0.8; // 신장 대비 80% 이상 물에 잠기면 수영 상황으로 판단

    // ── 궤도 상태 관리 변수 ────────────────────────────────────────────
    private float  orbitAngleRad = Float.NaN; // 현재 유지 중인 궤도 각도 (라디안)
    private double orbitRadius   = 3.5;       // 현재 유지 중인 궤도 거리
    private int    orbitTicksLeft = 0;        // 현재 궤도 위치 유지 남은 틱 수

    // ── 수영 시 기울기 (Leaning) 값 ─────────────────────────────────────
    private float lean    = 0.0f; // 현재 틱에서의 기울기 (0=똑바로 섬, 1=완전히 누움)
    private float prevLean= 0.0f; // 이전 틱의 기울기 (보간용)

    // ────────────────────────────────────────────────────────────────
    // 데이터 트래커 (클라이언트 동기화)
    // ────────────────────────────────────────────────────────────────
    private static final TrackedData<Boolean> FOLLOWING =
            DataTracker.registerData(AgentEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Optional<UUID>> OWNER_UUID =
            DataTracker.registerData(AgentEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<String> OWNER_NAME =
            DataTracker.registerData(AgentEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Boolean> SIDE_RIGHT =
            DataTracker.registerData(AgentEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    // ────────────────────────────────────────────────────────────────
    // 엔티티 초기화
    // ────────────────────────────────────────────────────────────────

    /**
     * 기본 엔티티 속성 설정
     * - 체력 20 (플레이어와 동일)
     * - 이동 속도 0 (AI 미사용, 직접 제어)
     */
    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0);
    }

    /**
     * 엔티티 생성자
     * 무적, AI 비활성화, 중력 무시, 영구 존재 설정
     */
    public AgentEntity(EntityType<? extends MobEntity> type, World world) {
        super(type, world);
        setHealth(20f);
        setInvulnerable(true);
        setAiDisabled(true);
        setNoGravity(true);
        setPersistent();
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(OWNER_UUID, Optional.empty());
        dataTracker.startTracking(OWNER_NAME, "");
        dataTracker.startTracking(FOLLOWING, true);
        dataTracker.startTracking(SIDE_RIGHT, true);
    }

    // ────────────────────────────────────────────────────────────────
    // Getter/Setter
    // ────────────────────────────────────────────────────────────────

    public boolean isFollowing() { return dataTracker.get(FOLLOWING); }
    public void setFollowing(boolean on) { dataTracker.set(FOLLOWING, on); }
    public boolean isSideRight() { return dataTracker.get(SIDE_RIGHT); }
    public void setSideRight(boolean right) { dataTracker.set(SIDE_RIGHT, right); }
    public Optional<UUID> getOwnerUuid() { return dataTracker.get(OWNER_UUID); }
    public String getOwnerName() { return dataTracker.get(OWNER_NAME); }

    // ────────────────────────────────────────────────────────────────
    // 수영 애니메이션 오버라이드 (핵심 수정 부분)
    // ────────────────────────────────────────────────────────────────

    /**
     * 수영 포즈 여부 반환
     * 렌더러가 이 값으로 수영 애니메이션 활성화 여부 판단
     */
    @Override
    public boolean isInSwimmingPose() {
        return this.getPose() == EntityPose.SWIMMING;
    }

    /**
     * 수영 시 몸 기울기 각도 반환 (0=수직, 1=수평 누움)
     * LivingEntityRenderer가 이 값으로 모델 회전 적용
     */
    @Override
    public float getLeaningPitch(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.prevLean, this.lean);
    }

    // ────────────────────────────────────────────────────────────────
    // 메인 틱 처리
    // ────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();

        // 클라이언트에서는 실행 안 함 (서버 전용)
        if (getWorld().isClient) return;

        // 추적 비활성화 상태면 동작 안 함
        if (!isFollowing()) return;

        followOwnerTick();
    }

    // ────────────────────────────────────────────────────────────────
    // 수영 비주얼 적용 (수정된 핵심 로직)
    // ────────────────────────────────────────────────────────────────

    /**
     * 수영 애니메이션 및 자세 강제 적용
     *
     * @param horizontalPose true면 수평 누운 자세, false면 수직 서있는 자세
     * <p>
     * 수정 사항:
     * 1. setSwimming(true) 호출 - swimAmount 증가시켜 허우적거림 애니메이션 활성화
     * 2. EntityPose.SWIMMING 설정 - 렌더러가 수평 누운 모델 렌더링
     * 3. lean 값 부드럽게 보간 - 자세 전환 시 부드러운 애니메이션
     */
    private void applySwimVisuals(boolean horizontalPose) {
        if (horizontalPose) {
            // 완전 수영 자세: 수평으로 누워서 헤엄치는 모습
            if (!this.isSwimming()) {
                this.setSwimming(true); // swimAmount 증가 시작 (허우적거림)
            }
            if (this.getPose() != EntityPose.SWIMMING) {
                this.setPose(EntityPose.SWIMMING); // 수평 누운 포즈
            }
        } else {
            // 서있는 자세: 물에 둥둥 떠있거나 머리만 잠긴 상태
            if (this.isSwimming()) {
                this.setSwimming(false); // swimAmount 감소 시작
            }
            if (this.getPose() != EntityPose.STANDING) {
                this.setPose(EntityPose.STANDING); // 수직 서있는 포즈
            }
        }

        // 기울기 값 부드럽게 보간 (0.3 = 30% 속도로 목표값에 접근)
        this.prevLean = this.lean;
        float targetLean = horizontalPose ? 1.0f : 0.0f;
        this.lean += (targetLean - this.lean) * 0.3f;
    }

    // ────────────────────────────────────────────────────────────────
    // 주인 추적 메인 로직
    // ────────────────────────────────────────────────────────────────

    /**
     * 매 틱마다 주인 플레이어 추적 처리
     * - 차원 이동 대응
     * - 상태별 속도 선택
     * - 위치 업데이트
     * - 시선 동기화
     */
    private void followOwnerTick() {
        ServerWorld myWorld = (ServerWorld) getWorld();
        Optional<UUID> ownerIdOpt = getOwnerUuid();
        if (ownerIdOpt.isEmpty()) return;

        // 주인 플레이어 찾기
        ServerPlayerEntity owner = myWorld.getServer().getPlayerManager().getPlayer(ownerIdOpt.get());
        if (owner == null) return;

        // 차원 이동 대응 (주인이 다른 월드에 있으면 따라감)
        if (owner.getWorld() != myWorld) {
            var movedEntity = this.moveToWorld((ServerWorld) owner.getWorld());
            if (movedEntity != null) {
                movedEntity.refreshPositionAndAngles(
                        owner.getX(), owner.getY(), owner.getZ(), owner.getYaw(), 0
                );
            }
            return;
        }

        // ──────────────────────────────────────────────────────────
        // 주인 상태 판정
        // ──────────────────────────────────────────────────────────

        boolean ownerFlying   = owner.getAbilities().flying || owner.isFallFlying();
        boolean ownerSwimming = owner.isSwimming();
        boolean headUnder     = this.isHeadUnderwater();
        boolean fullyUnder    = this.isFullyUnderwater();

        // 수영 모드: 플레이어나 에이전트가 물속에 있는 경우
        boolean swimMode = headUnder || fullyUnder || ownerSwimming;

        // 수평 포즈: 완전히 잠수한 경우에만 수평 누운 자세
        boolean horizontalPose = ownerSwimming || fullyUnder;

        // 수영 비주얼 강제 적용 (수정된 로직)
        applySwimVisuals(horizontalPose);

        // ──────────────────────────────────────────────────────────
        // 목표 위치 계산
        // ──────────────────────────────────────────────────────────

        Vec3d targetPos = computeFollowerTarget(owner);

        // 너무 멀면 텔레포트
        if (this.getPos().distanceTo(targetPos) > TELEPORT_DISTANCE) {
            this.refreshPositionAfterTeleport(targetPos.x, targetPos.y, targetPos.z);
            syncYawTo(owner, swimMode, horizontalPose, targetPos);
            return;
        }

        // 현재 위치와 목표 위치 차이 계산
        Vec3d currentPos = this.getPos();
        Vec3d diff = targetPos.subtract(currentPos);
        double distHorizontal = Math.hypot(diff.x, diff.z);

        // ──────────────────────────────────────────────────────────
        // 상태별 기본 속도 선정
        // ──────────────────────────────────────────────────────────

        double baseSpeed;
        if (owner.hasVehicle()) {
            // 탈것 탑승 중
            baseSpeed = BOAT_SPEED;
            var vehicle = owner.getVehicle();
            String vehicleName = (vehicle == null ? "" : vehicle.getType().toString().toLowerCase());
            if (!vehicleName.contains("boat")) {
                baseSpeed = MOUNT_SPEED;
            }
        } else if (ownerFlying) {
            baseSpeed = FLY_SPEED;
        } else if (swimMode) {
            baseSpeed = SWIM_SPEED;
        } else {
            baseSpeed = WALK_SPEED;
        }

        // 거리 기반 가감속
        double maxStep = baseSpeed;
        if (distHorizontal > FAST_RADIUS) {
            maxStep *= 1.6;  // 가속
        } else if (distHorizontal < SLOW_RADIUS) {
            maxStep *= 0.35; // 감속
        }

        // 수평 이동 벡터 계산
        Vec3d horizMove = new Vec3d(diff.x, 0, diff.z);
        if (horizMove.lengthSquared() > maxStep * maxStep) {
            horizMove = horizMove.normalize().multiply(maxStep);
        }

        // 수직 이동 계산 (부드러운 보간)
        double newY = currentPos.y + MathHelper.clamp(diff.y * HEIGHT_LERP, -maxStep, maxStep);
        Vec3d nextPos = new Vec3d(currentPos.x + horizMove.x, newY, currentPos.z + horizMove.z);

        // ──────────────────────────────────────────────────────────
        // 상황별 이동 처리
        // ──────────────────────────────────────────────────────────

        if (!ownerFlying && !owner.hasVehicle() && !swimMode) {
            // 1) 지상 보행
            this.noClip = false;
            try { this.setStepHeight(1.25F); } catch (Throwable ignored) {}
            this.move(MovementType.SELF, nextPos.subtract(currentPos));
            if (this.isOnGround()) {
                this.setVelocity(this.getVelocity().multiply(1.0, 0.0, 1.0));
            }
            this.setNoGravity(true);

        } else if (swimMode) {
            // 2) 수중 이동 (물가 턱 오르기 처리 포함)
            this.noClip = false;
            try { this.setStepHeight(1.25F); } catch (Throwable ignored) {}

            // 물가 턱 해소: 물 속이고 머리 위가 공기면 점프 시도
            boolean inWaterHere = this.isTouchingWater();
            boolean airAboveHead = this.getWorld().getBlockState(this.getBlockPos().up()).isAir();

            if (inWaterHere && airAboveHead) {
                Vec3d horizontalDir = new Vec3d(nextPos.x - currentPos.x, 0, nextPos.z - currentPos.z);
                if (horizontalDir.lengthSquared() > 1e-6) {
                    Vec3d dirNorm = horizontalDir.normalize();
                    BlockPos frontBlockPos = BlockPos.ofFloored(
                            this.getX() + dirNorm.x * 0.7,
                            this.getY(),
                            this.getZ() + dirNorm.z * 0.7
                    );
                    boolean solidFront = !this.getWorld().getBlockState(frontBlockPos)
                            .getCollisionShape(this.getWorld(), frontBlockPos).isEmpty();

                    if (solidFront) {
                        // 앞에 벽이 있으면 위로 올라가기 시도
                        nextPos = nextPos.add(dirNorm.multiply(0.25)).add(0, 0.9, 0);
                    }
                }
            }

            this.move(MovementType.SELF, nextPos.subtract(currentPos));
            this.setNoGravity(true);

        } else {
            // 3) 공중 비행/빠른 이동
            this.noClip = true;
            try {
                this.setPosition(nextPos.x, nextPos.y, nextPos.z);
            } catch (Throwable t) {
                this.updatePosition(nextPos.x, nextPos.y, nextPos.z);
            }
        }

        // 시선 동기화
        syncYawTo(owner, swimMode, horizontalPose, targetPos);
    }

    // ────────────────────────────────────────────────────────────────
    // 시선 동기화
    // ────────────────────────────────────────────────────────────────

    /**
     * 주인 플레이어의 시선 및 이동 상태에 맞춰 에이전트 시선 조정
     *
     * @param owner 주인 플레이어
     * @param swimMode 수영 모드 여부
     * @param horizontalPose 수평 자세 여부
     * @param targetPos 목표 위치
     */
    private void syncYawTo(ServerPlayerEntity owner, boolean swimMode,
                           boolean horizontalPose, Vec3d targetPos) {
        // 눈 위치 계산
        double ax = this.getX();
        double ay = this.getY() + this.getStandingEyeHeight();
        double az = this.getZ();
        double bx = owner.getX();
        double by = owner.getY() + owner.getStandingEyeHeight();
        double bz = owner.getZ();

        // 플레이어 방향 벡터
        double yawRad = Math.toRadians(owner.getYaw());
        Vec3d playerForward = new Vec3d(-Math.sin(yawRad), 0, Math.cos(yawRad));
        Vec3d playerRight   = new Vec3d( Math.cos(yawRad), 0, Math.sin(yawRad));

        // 상대 위치
        Vec3d relPos = this.getPos().subtract(owner.getPos());
        double ahead   = relPos.dotProduct(playerForward);
        double lateral = relPos.dotProduct(playerRight);

        // 플레이어 이동 상태
        Vec3d playerHorizVel = owner.getVelocity().multiply(1, 0, 1);
        double playerSpeed   = playerHorizVel.length();
        double forwardSpeed  = playerHorizVel.dotProduct(playerForward);

        boolean standing   = playerSpeed < PLAYER_MOVING_THRESHOLD;
        boolean movingBack = forwardSpeed < -MOVING_BACK_THRESHOLD;
        boolean movingFwd  = forwardSpeed > 0.05;

        float targetYaw;
        float targetPitch;

        // ──────────────────────────────────────────────────────────
        // 시선 방향 결정
        // ──────────────────────────────────────────────────────────

        if (movingFwd) {
            // 플레이어가 전진 중: 같은 방향 바라봄
            targetYaw   = owner.getYaw();
            targetPitch = owner.getPitch() * 0.25f;

        } else if (standing || (movingBack && ahead > -0.5)) {
            // 정지 또는 후진 중: 주인 바라봄
            double dx = bx - ax;
            double dz = bz - az;
            double dy = by - ay;
            targetYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx*dx + dz*dz)))) * 0.5f;

        } else {
            // 그 외: 위치에 따라 판단
            boolean frontOrSide = (ahead > AHEAD_SAME_DIR) || (Math.abs(lateral) > LATERAL_SAME_DIR);
            if (frontOrSide) {
                targetYaw   = owner.getYaw();
                targetPitch = owner.getPitch() * 0.25f;
            } else {
                double dx = bx - ax;
                double dz = bz - az;
                double dy = by - ay;
                targetYaw   = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx*dx + dz*dz)))) * 0.5f;
            }
        }

        // 수영 시 목표 위치 방향으로 시선 조정
        if (swimMode && targetPos != null) {
            double dy = targetPos.y - this.getY();
            double horizDist = Math.hypot(targetPos.x - this.getX(), targetPos.z - this.getZ());

            if (horizDist > 0.1) {
                float swimPitch = (float)(-Math.toDegrees(Math.atan2(dy, horizDist)));
                if (horizontalPose) {
                    // 수평 자세: ±35° 범위
                    targetPitch = MathHelper.clamp(swimPitch, -35f, 35f);
                } else {
                    // 서있는 자세: ±12° 범위
                    targetPitch = MathHelper.clamp(swimPitch * 0.4f, -12f, 12f);
                }
            } else {
                targetPitch = 0f;
            }
        }

        // 부드럽게 보간
        float newYaw   = MathHelper.lerpAngleDegrees(0.35f, this.getYaw(), targetYaw);
        float newPitch = MathHelper.lerp(0.35f, this.getPitch(), targetPitch);

        this.setYaw(newYaw);
        this.setBodyYaw(newYaw);
        this.setHeadYaw(newYaw);
        this.setPitch(newPitch);
    }

    // ────────────────────────────────────────────────────────────────
    // 궤도 위치 재설정
    // ────────────────────────────────────────────────────────────────

    /**
     * 뒤쪽 따라다닐 새로운 각도/거리 생성
     * 130°~170° 범위에서 랜덤 선택
     */
    private void reseedOrbit() {
        double minRad = Math.toRadians(BACK_MIN_DEG);
        double maxRad = Math.toRadians(BACK_MAX_DEG);
        double angle = minRad + this.random.nextDouble() * (maxRad - minRad);

        // 좌/우 선택 (15% 확률로 방향 전환)
        double sign = isSideRight() ? -1.0 : 1.0;
        if (this.random.nextFloat() < 0.15f) {
            sign = -sign;
        }

        this.orbitAngleRad = (float)(sign * angle);
        this.orbitRadius = ORBIT_MIN_RADIUS +
                this.random.nextDouble() * (ORBIT_MAX_RADIUS - ORBIT_MIN_RADIUS);
        this.orbitTicksLeft = ORBIT_MIN_TICKS +
                this.random.nextInt(ORBIT_MAX_TICKS - ORBIT_MIN_TICKS + 1);
    }

    // ────────────────────────────────────────────────────────────────
    // 목표 위치 계산
    // ────────────────────────────────────────────────────────────────

    /**
     * 플레이어 주변 따라다닐 목표 좌표 계산
     * - 뒤쪽 궤도 영역에서 랜덤 위치 선정
     * - 이동 속도에 따른 전방 리드 적용
     * - 상태별 수직 오프셋 적용
     *
     * @param p 주인 플레이어
     * @return 목표 위치 벡터
     */
    private Vec3d computeFollowerTarget(ServerPlayerEntity p) {
        boolean flying   = p.getAbilities().flying || p.isFallFlying();
        boolean swimming = isPlayerDeepEnough(p) && (p.isSwimming() || p.isTouchingWater());

        // 수직 오프셋 (에이전트를 플레이어보다 약간 아래 배치)
        double verticalOffset = -0.20;
        if (flying) {
            verticalOffset = -0.80;  // 비행 시 더 아래
        } else if (swimming) {
            verticalOffset = -0.30;  // 수영 시 약간 아래
        }

        // 궤도 갱신 체크
        if (orbitTicksLeft <= 0 || Double.isNaN(orbitAngleRad)) {
            reseedOrbit();
        } else {
            orbitTicksLeft--;
        }

        // 뒤쪽 궤도 위치 계산
        double baseYaw = Math.toRadians(p.getYaw());
        double yawOffset = baseYaw + orbitAngleRad;
        double offsetX = -Math.sin(yawOffset) * orbitRadius;
        double offsetZ =  Math.cos(yawOffset) * orbitRadius;

        // 이동 속도에 따른 전방 리드
        double playerSpeed = p.getVelocity().horizontalLength();
        double forwardLead = 0.0;
        if (!flying && !p.hasVehicle()) {
            if      (playerSpeed < 0.05) forwardLead = 0.5;
            else if (playerSpeed < 0.12) forwardLead = 0.3;
            else if (playerSpeed < 0.20) forwardLead = 0.1;
        }

        // 전방 방향 벡터
        double fx = -Math.sin(baseYaw);
        double fz =  Math.cos(baseYaw);

        // 최종 목표 좌표
        double targetX = p.getX() + offsetX + forwardLead * fx;
        double targetZ = p.getZ() + offsetZ + forwardLead * fz;
        double targetY = p.getY() + verticalOffset;

        if (flying) {
            targetY = p.getY() + 0.5 + verticalOffset;
        } else if (swimming) {
            targetY = p.getY() + 0.2 + verticalOffset;
        }

        // 탈것 탑승 시 더 가깝게 보간
        if (p.hasVehicle()) {
            targetX = MathHelper.lerp(0.45, p.getX(), targetX);
            targetZ = MathHelper.lerp(0.45, p.getZ(), targetZ);
            targetY = MathHelper.lerp(0.25, p.getY(), targetY);
        }

        return new Vec3d(targetX, targetY, targetZ);
    }

    // ────────────────────────────────────────────────────────────────
    // 수중 판정 유틸리티
    // ────────────────────────────────────────────────────────────────

    /**
     * 에이전트의 머리(눈)가 물에 잠겼는지 판정
     * @return true면 머리가 물속
     */
    private boolean isHeadUnderwater() {
        double eyeY = this.getEyeY();
        BlockPos eyePos = BlockPos.ofFloored(this.getX(), eyeY, this.getZ());
        var fs = this.getWorld().getFluidState(eyePos);
        if (!fs.isIn(FluidTags.WATER)) return false;

        double surfaceY = eyePos.getY() + fs.getHeight(this.getWorld(), eyePos);
        return eyeY <= surfaceY + 1.0e-3;
    }

    /**
     * 에이전트가 거의 완전히 물에 잠겼는지 판정
     * @return true면 몸 전체가 물속
     */
    private boolean isFullyUnderwater() {
        double entityHeight = this.getDimensions(this.getPose()).height;
        double waterHeight  = this.getFluidHeight(FluidTags.WATER);
        return entityHeight > 0.0 && waterHeight >= (entityHeight - 0.05);
    }

    /**
     * 플레이어가 충분히 깊은 물에 있는지 판정
     * @param p 플레이어
     * @return true면 수영 가능한 깊이
     */
    private boolean isPlayerDeepEnough(ServerPlayerEntity p) {
        double waterHeight  = p.getFluidHeight(FluidTags.WATER);
        double entityHeight = p.getDimensions(p.getPose()).height;
        return entityHeight > 0.0 && (waterHeight / entityHeight) >= SWIM_DEPTH_RATIO;
    }

    // ────────────────────────────────────────────────────────────────
    // 주인 설정 및 NBT 저장/로드
    // ────────────────────────────────────────────────────────────────

    /**
     * 에이전트의 주인 설정
     * @param uuid 주인 UUID
     * @param name 주인 이름
     */
    public void setOwner(UUID uuid, String name) {
        dataTracker.set(OWNER_UUID, Optional.ofNullable(uuid));
        dataTracker.set(OWNER_NAME, (name == null ? "" : name));

        if (!this.hasCustomName()) {
            String ownerName = (name == null || name.isBlank()) ? null : name;
            String label = (ownerName == null) ? "Agent" : ownerName + "의 노예";
            this.setCustomName(Text.literal(label));
            this.setCustomNameVisible(true);
        }
    }

    /**
     * NBT에서 데이터 로드
     */
    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        if (nbt.containsUuid("OwnerUUID")) {
            dataTracker.set(OWNER_UUID, Optional.of(nbt.getUuid("OwnerUUID")));
        }
        dataTracker.set(OWNER_NAME, nbt.getString("OwnerName"));

        if (!this.hasCustomName()) {
            String owner = getOwnerName();
            String label = (owner == null || owner.isBlank()) ? "Agent" : owner + "의 노예";
            this.setCustomName(Text.literal(label));
            this.setCustomNameVisible(true);
        }

        setFollowing(nbt.getBoolean("Following"));
        if (nbt.contains("SideRight")) {
            setSideRight(nbt.getBoolean("SideRight"));
        }
    }

    /**
     * NBT에 데이터 저장
     */
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);

        getOwnerUuid().ifPresent(uuid -> nbt.putUuid("OwnerUUID", uuid));
        nbt.putString("OwnerName", getOwnerName());
        nbt.putBoolean("Following", isFollowing());
        nbt.putBoolean("SideRight", isSideRight());
    }

    // ────────────────────────────────────────────────────────────────
    // 피해 및 충돌 무시
    // ────────────────────────────────────────────────────────────────

    /**
     * 다른 엔티티에게 밀리지 않음
     */
    @Override
    public boolean isPushable() {
        return false;
    }

    /**
     * 모든 피해 무시
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    /**
     * 피해 입지 않음
     */
    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }
}