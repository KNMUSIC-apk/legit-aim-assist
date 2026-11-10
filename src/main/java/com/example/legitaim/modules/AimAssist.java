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
    private static final double MAX_FOV = 32.0;
    private static final Random RANDOM = new Random();

    private static AbstractClientPlayerEntity currentTarget = null;
    private static int targetLockTicks = 0;
    
    // Các biến giả lập độ trễ nhận thức và trôi tự nhiên của cơ bắp tay người
    private static double dynamicOffsetX = 0.0;
    private static double dynamicOffsetY = 0.0;
    private static int swayTimer = 0;
    private static int reactionLagTicks = 0; // Độ trễ phản xạ khi đổi mục tiêu

    public static void onClientTick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null) {
            resetAimState();
            return;
        }

        PlayerEntity player = client.player;

        // Chỉ kích hoạt khi cầm Kiếm hoặc Rìu
        String itemId = Registries.ITEM
                .getId(player.getMainHandStack().getItem())
                .getPath();

        boolean validWeapon = itemId.endsWith("_sword") || itemId.endsWith("_axe");
        if (!validWeapon) {
            resetAimState();
            return;
        }

        // Quản lý mục tiêu thông minh với độ trễ chuyển đổi (Cognitive Switch Lag)
        if (currentTarget == null || !currentTarget.isAlive() || currentTarget.isSpectator() || currentTarget.isRemoved() || player.distanceTo(currentTarget) > RANGE) {
            if (RANDOM.nextFloat() < 0.25f) {
                currentTarget = findBestTarget(player, client);
                targetLockTicks = 0;
                reactionLagTicks = 2 + RANDOM.nextInt(5); // Mô phỏng thời gian não bộ nhận diện mục tiêu mới (2-6 ticks)
                resetOffsets();
            }
        }

        if (currentTarget == null) {
            targetLockTicks = 0;
            return;
        }

        // Nếu đang trong độ trễ phản xạ nhận thức, tạm thời chưa can thiệp aim để đánh lừa AI phân tích Target Acquisition
        if (reactionLagTicks > 0) {
            reactionLagTicks--;
            return;
        }

        targetLockTicks++;
        swayTimer++;

        // Tiêm nhiễu trôi dạt tự nhiên (Data Poisoning / Natural Drift) để phá vỡ cấu trúc AI feature windows
        if (swayTimer > 12 + RANDOM.nextInt(14)) {
            swayTimer = 0;
            dynamicOffsetX = (RANDOM.nextDouble() - 0.5) * 0.22; 
            dynamicOffsetY = (RANDOM.nextDouble() - 0.5) * 0.15; 
        }

        double targetX = currentTarget.getX() + dynamicOffsetX;
        double targetY = (currentTarget.getY() + currentTarget.getHeight() * 0.58) + dynamicOffsetY;
        double targetZ = currentTarget.getZ() + dynamicOffsetX;

        double dx = targetX - player.getX();
        double dy = targetY - player.getEyeY();
        double dz = targetZ - player.getZ();

        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDistance)));

        float yawDiff = MathHelper.wrapDegrees(targetYaw - player.getYaw());
        float pitchDiff = targetPitch - player.getPitch();

        double totalAngle = Math.abs(yawDiff) + Math.abs(pitchDiff);
        if (totalAngle > MAX_FOV || totalAngle < 0.18) {
            return;
        }

        // Mô phỏng biến thiên phân phối chuẩn (Gaussian-like variation) cho hệ thống mượt thay vì hằng số tuyến tính
        double gaussianLikeFactor = RANDOM.nextGaussian() * 0.03 + 0.11;
        float accelerationFactor = Math.min(1.0f, targetLockTicks / 15.0f);
        float baseSmoothing = (float) Math.max(0.06, gaussianLikeFactor) * accelerationFactor;

        // Nhiễu vi mô sinh lý (High-Entropy Micro-Jitter) bẻ gãy đạo hàm cấp 2 và gia tốc góc (Rotation Acceleration)
        float yawNoise = (float) ((RANDOM.nextGaussian() * 0.25) + ((RANDOM.nextFloat() - 0.5f) * 0.35f));
        float pitchNoise = (float) ((RANDOM.nextGaussian() * 0.15) + ((RANDOM.nextFloat() - 0.5f) * 0.25f));

        float appliedYaw = yawDiff * baseSmoothing + yawNoise;
        float appliedPitch = pitchDiff * baseSmoothing + pitchNoise;

        // Giới hạn Delta nghiêm ngặt chống quét bất thường
        appliedYaw = MathHelper.clamp(appliedYaw, -4.8f, 4.8f);
        appliedPitch = MathHelper.clamp(appliedPitch, -2.8f, 2.8f);

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
        double minAngle = MAX_FOV;

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
        resetOffsets();
    }

    private static void resetOffsets() {
        dynamicOffsetX = 0.0;
        dynamicOffsetY = 0.0;
        swayTimer = 0;
    }
}
