package com.example.legitaim.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

public class AimAssist {

    public static boolean enabled = false;

    private static final double RANGE = 4.2;
    private static final double MAX_FOV = 30.0;
    private static final Random RANDOM = new Random();

    private static AbstractClientPlayerEntity currentTarget = null;
    private static int targetLockTicks = 0;
    
    // Biến mô phỏng sự dao động tâm lý / độ trễ phản xạ tự nhiên của con người
    private static double humanReactionOffset = 0.0;

    public static void onClientTick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null) {
            currentTarget = null;
            targetLockTicks = 0;
            return;
        }

        PlayerEntity player = client.player;

        // Quản lý mục tiêu với độ trễ chuyển đổi (Target Acquisition Analysis)
        if (currentTarget == null || !currentTarget.isAlive() || currentTarget.isSpectator() || currentTarget.isRemoved() || player.distanceTo(currentTarget) > RANGE) {
            if (RANDOM.nextFloat() < 0.25f) { // Không switch ngay lập tức khi có người mới, tạo độ trễ nhận thức
                currentTarget = findBestTarget(player, client);
                targetLockTicks = 0;
                humanReactionOffset = (RANDOM.nextDouble() - 0.5) * 0.15;
            }
        }

        if (currentTarget == null) {
            targetLockTicks = 0;
            return;
        }

        targetLockTicks++;

        // Tính toán tọa độ vị trí mục tiêu (Aim vào thân/ngực với độ lệch tự nhiên thay vì tâm tuyệt đối)
        double dx = currentTarget.getX() - player.getX();
        double dy = (currentTarget.getY() + currentTarget.getHeight() * (0.55 + humanReactionOffset)) - player.getEyeY();
        double dz = currentTarget.getZ() - player.getZ();

        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDistance)));

        float yawDiff = MathHelper.wrapDegrees(targetYaw - player.getYaw());
        float pitchDiff = targetPitch - player.getPitch();

        double totalAngle = Math.abs(yawDiff) + Math.abs(pitchDiff);
        if (totalAngle > MAX_FOV || totalAngle < 0.2) {
            return;
        }

        // Chống phân tích Rotation Consistency & Smoothing tuyến tính:
        // Sử dụng hệ số mượt biến thiên theo thời gian khóa mục tiêu (Mô phỏng tay người bắt nhịp dần)
        float accelerationFactor = Math.min(1.0f, targetLockTicks / 15.0f);
        float baseSmoothing = (0.08f + RANDOM.nextFloat() * 0.07f) * accelerationFactor;

        // Thêm nhiễu vi mô (Micro-Jitter) để phá vỡ entropy thống kê góc quay
        float yawNoise = (RANDOM.nextFloat() - 0.5f) * 0.4f;
        float pitchNoise = (RANDOM.nextFloat() - 0.5f) * 0.4f;

        float appliedYaw = yawDiff * baseSmoothing + yawNoise;
        float appliedPitch = pitchDiff * baseSmoothing + pitchNoise;

        // Giới hạn Rotation Delta và Acceleration nghiêm ngặt
        appliedYaw = MathHelper.clamp(appliedYaw, -4.5f, 4.5f);
        appliedPitch = MathHelper.clamp(appliedPitch, -2.5f, 2.5f);

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
}
