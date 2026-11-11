package com.example.legitaim.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

public class AimAssist {

    public static boolean enabled = false;

    private static final double RANGE = 4.5;
    private static final Random RANDOM = new Random();

    private static AbstractClientPlayerEntity currentTarget = null;
    private static int targetLockTicks = 0;
    
    private static double bodyHeightFactor = 0.75; 
    private static int reactionLagTicks = 0;
    private static int sustainedCombatTicks = 0;

    // Biến cho cơ chế Overshoot & Correction (Vẩy quá đà và tự sửa tâm)
    private static float overshootYaw = 0f;
    private static float overshootPitch = 0f;
    private static int overshootTicksLeft = 0;

    // Biến theo dõi vận tốc và đổi hướng của đối thủ (Target Strafing Disruption)
    private static double lastTargetX = 0.0;
    private static double lastTargetZ = 0.0;
    private static double lastTargetVelX = 0.0;
    private static double lastTargetVelZ = 0.0;
    private static int strafeSlipTicks = 0;

    private static float prevPlayerYaw = 0.0f;
    private static float prevPlayerPitch = 0.0f;

    public static void onClientTick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null) {
            resetAimState();
            return;
        }

        PlayerEntity player = client.player;

        if (player.isUsingItem() || client.currentScreen != null) {
            resetAimState();
            return;
        }

        float manualYawDelta = player.getYaw() - prevPlayerYaw;
        float manualPitchDelta = player.getPitch() - prevPlayerPitch;

        prevPlayerYaw = player.getYaw();
        prevPlayerPitch = player.getPitch();

        if (Math.abs(manualPitchDelta) > 0.15f && currentTarget != null) {
            bodyHeightFactor = MathHelper.clamp(bodyHeightFactor - (manualPitchDelta * 0.02), 0.25, 0.90);
        }

        float playerVelocityDelta = Math.abs(manualYawDelta) + Math.abs(manualPitchDelta);
        if (playerVelocityDelta > 16.0f) {
            return; 
        }

        String itemId = Registries.ITEM
                .getId(player.getMainHandStack().getItem())
                .getPath();

        boolean validWeapon = itemId.endsWith("_sword") || itemId.endsWith("_axe");
        if (!validWeapon) {
            resetAimState();
            return;
        }

        boolean isLowHealth = player.getHealth() < 10.0f;
        int maxCombatTicks = isLowHealth ? 90 : 160;

        if (sustainedCombatTicks > maxCombatTicks) {
            if (RANDOM.nextFloat() < 0.15f) {
                sustainedCombatTicks = -50;
                currentTarget = null;
                return;
            }
        } else if (sustainedCombatTicks < 0) {
            sustainedCombatTicks++;
            return;
        }
        sustainedCombatTicks++;

        if (currentTarget == null || !currentTarget.isAlive() || currentTarget.isSpectator() || currentTarget.isRemoved() || player.distanceTo(currentTarget) > RANGE) {
            if (RANDOM.nextFloat() < 0.3f) {
                currentTarget = findBestTarget(player, client);
                targetLockTicks = 0;
                reactionLagTicks = 2 + RANDOM.nextInt(3);
                bodyHeightFactor = 0.70 + (RANDOM.nextDouble() * 0.15);
                
                // Khởi tạo thông số vị trí ban đầu của mục tiêu
                if (currentTarget != null) {
                    lastTargetX = currentTarget.getX();
                    lastTargetZ = currentTarget.getZ();
                    lastTargetVelX = 0;
                    lastTargetVelZ = 0;
                }
            }
        }

        if (currentTarget == null) {
            targetLockTicks = 0;
            sustainedCombatTicks = Math.max(0, sustainedCombatTicks - 2);
            return;
        }

        if (reactionLagTicks > 0) {
            reactionLagTicks--;
            return;
        }

        // --- TARGET STRAFING DISRUPTION (Độ trễ quán tính khi địch đảo chiều) ---
        double targetVelX = currentTarget.getX() - lastTargetX;
        double targetVelZ = currentTarget.getZ() - lastTargetZ;
        lastTargetX = currentTarget.getX();
        lastTargetZ = currentTarget.getZ();

        double dotProduct = (targetVelX * lastTargetVelX) + (targetVelZ * lastTargetVelZ);
        if (dotProduct < -0.0001 && (targetVelX * targetVelX + targetVelZ * targetVelZ) > 0.001) {
            strafeSlipTicks = 2 + RANDOM.nextInt(3); // Mất dấu tâm trong 2-4 ticks khi địch đảo hướng gấp
        }
        lastTargetVelX = targetVelX;
        lastTargetVelZ = targetVelZ;

        if (strafeSlipTicks > 0) {
            strafeSlipTicks--;
            return; 
        }

        targetLockTicks++;

        // --- OVERSHOOT & CORRECTION (Vẩy quá đà khi bắt đầu khóa mục tiêu) ---
        if (targetLockTicks == 1) {
            overshootTicksLeft = 2 + RANDOM.nextInt(2); // Kéo dài trong 2-3 ticks
            overshootYaw = (float)((RANDOM.nextDouble() - 0.5) * 3.0);
            overshootPitch = (float)((RANDOM.nextDouble() - 0.5) * 1.5);
        }

        float currentOvershootYaw = 0f;
        float currentOvershootPitch = 0f;
        if (overshootTicksLeft > 0) {
            overshootTicksLeft--;
            currentOvershootYaw = overshootYaw * (overshootTicksLeft / 3.0f);
            currentOvershootPitch = overshootPitch * (overshootTicksLeft / 3.0f);
        }

        double targetX = currentTarget.getX();
        double targetY = currentTarget.getY() + (currentTarget.getHeight() * bodyHeightFactor);
        double targetZ = currentTarget.getZ();

        double dx = targetX - player.getX();
        double dy = targetY - player.getEyeY();
        double dz = targetZ - player.getZ();

        double distance = player.distanceTo(currentTarget);
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        
        double dynamicFov = Math.max(15.0, 34.0 - (distance * 3.0));

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDistance)));

        float yawDiff = MathHelper.wrapDegrees(targetYaw - player.getYaw());
        float pitchDiff = targetPitch - player.getPitch();

        // --- ATTENTION GATES (Vùng thị giác tập trung / Vùng mù biên giới) ---
        // Nếu mục tiêu nằm ở góc quá rộng ngoài vùng tập trung trực diện (> 20 độ), mod bỏ qua hoàn toàn
        if (Math.abs(yawDiff) > 20.0) {
            return;
        }

        double totalAngle = Math.abs(yawDiff) + Math.abs(pitchDiff);
        if (totalAngle > dynamicFov || totalAngle < 0.12) {
            return;
        }

        int lutIndex = targetLockTicks + RANDOM.nextInt(3);
        float baseSmoothing = HumanMotionLUT.getSmoothing(lutIndex) * Math.min(1.0f, targetLockTicks / 15.0f);

        float yawNoise = HumanMotionLUT.getJitterYaw(lutIndex);
        float pitchNoise = HumanMotionLUT.getJitterPitch(lutIndex);

        // Cộng thêm thành phần Overshoot vào công thức điều hướng
        float appliedYaw = (yawDiff * baseSmoothing) + yawNoise + currentOvershootYaw;
        float appliedPitch = (pitchDiff * baseSmoothing) + pitchNoise + currentOvershootPitch;

        appliedYaw = MathHelper.clamp(appliedYaw, -3.2f, 3.2f);
        appliedPitch = MathHelper.clamp(appliedPitch, -1.8f, 1.8f);

        player.setYaw(player.getYaw() + appliedYaw);
        player.setPitch(
                MathHelper.clamp(
                        player.getPitch() + appliedPitch,
                        -90.0f,
                        90.0f
                )
        );
    }

    private static AbstractClientPlayerEntity findBestTarget(PlayerEntity player, MinecraftClient client) {
        AbstractClientPlayerEntity best = null;
        double minAngle = 30.0; // Giới hạn tầm nhìn hẹp hơn để giống góc nhìn thực tế của người chơi

        for (AbstractClientPlayerEntity candidate : client.world.getPlayers()) {
            if (candidate == player || !candidate.isAlive() || candidate.isSpectator() || candidate.isRemoved()) {
                continue;
            }

            double distance = player.distanceTo(candidate);
            if (distance > RANGE) {
                continue;
            }

            double dx = candidate.getX() - player.getX();
            double dz = candidate.getZ() - player.getZ();
            float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float yawDiff = Math.abs(MathHelper.wrapDegrees(targetYaw - player.getYaw()));

            if (yawDiff < minAngle) {
                minAngle = yawDiff;
                best = candidate;
            }
        }
        return best;
    }

    private static void resetAimState() {
        currentTarget = null;
        targetLockTicks = 0;
        reactionLagTicks = 0;
        sustainedCombatTicks = 0;
        overshootTicksLeft = 0;
        strafeSlipTicks = 0;
    }
}
