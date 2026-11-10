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
    
    // Duy trì chiều cao ngắm theo thao tác kéo chuột thực tế của người chơi (Không tự động giật ngược lên đầu)
    private static double bodyHeightFactor = 0.75; 
    private static int reactionLagTicks = 0;
    private static int sustainedCombatTicks = 0;

    private static float lastYaw = 0.0f;
    private static float lastPitch = 0.0f;
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

        // Theo dõi chuyển động tay thực tế của người chơi
        float manualYawDelta = player.getYaw() - prevPlayerYaw;
        float manualPitchDelta = player.getPitch() - prevPlayerPitch;

        prevPlayerYaw = player.getYaw();
        prevPlayerPitch = player.getPitch();

        // Nếu người chơi tự kéo chuột, cập nhật mốc độ cao theo tay họ và giữ nguyên
        if (Math.abs(manualPitchDelta) > 0.15f && currentTarget != null) {
            bodyHeightFactor = MathHelper.clamp(bodyHeightFactor - (manualPitchDelta * 0.02), 0.25, 0.90);
        }

        float playerVelocityDelta = Math.abs(manualYawDelta) + Math.abs(manualPitchDelta);
        lastYaw = player.getYaw();
        lastPitch = player.getPitch();

        if (playerVelocityDelta > 16.0f) {
            return; // Nhường hoàn toàn quyền kiểm soát khi người chơi vẩy chuột mạnh
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

        targetLockTicks++;

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

        double totalAngle = Math.abs(yawDiff) + Math.abs(pitchDiff);
        if (totalAngle > dynamicFov || totalAngle < 0.12) {
            return;
        }

        // Lấy hệ số mượt và nhiễu từ bảng LUT tiền tính toán (Chống AI phân tích thuật toán sinh số giả)
        int lutIndex = targetLockTicks + RANDOM.nextInt(3);
        float baseSmoothing = HumanMotionLUT.getSmoothing(lutIndex) * Math.min(1.0f, targetLockTicks / 15.0f);

        float yawNoise = HumanMotionLUT.getJitterYaw(lutIndex);
        float pitchNoise = HumanMotionLUT.getJitterPitch(lutIndex);

        float appliedYaw = (yawDiff * baseSmoothing) + yawNoise;
        float appliedPitch = (pitchDiff * baseSmoothing) + pitchNoise;

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
        
        lastYaw = player.getYaw();
        lastPitch = player.getPitch();
    }

    private static AbstractClientPlayerEntity findBestTarget(PlayerEntity player, MinecraftClient client) {
        AbstractClientPlayerEntity best = null;
        double minAngle = 34.0;

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
    }
}
