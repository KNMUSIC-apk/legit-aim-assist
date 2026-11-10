package com.example.legitaim.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

public class AimAssist {

    public static boolean enabled = false;

    private static final double RANGE = 4.5;
    private static final double MAX_FOV = 35.0; // Giới hạn vùng quét tầm nhìn để tránh xoay người góc rộng bất thường
    private static final Random RANDOM = new Random();

    private static AbstractClientPlayerEntity currentTarget = null;

    public static void onClientTick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null) {
            currentTarget = null;
            return;
        }

        PlayerEntity player = client.player;

        // 1. Quản lý mục tiêu thông minh, chống đổi mục tiêu đột ngột (Target Switching Smoothing)
        if (currentTarget == null || !currentTarget.isAlive() || currentTarget.isSpectator() || currentTarget.isRemoved() || player.distanceTo(currentTarget) > RANGE) {
            currentTarget = findBestTarget(player, client);
        }

        if (currentTarget == null) {
            return;
        }

        // 2. Tính toán góc lệch hướng tới mục tiêu (Vùng ngực/thân trên để tự nhiên hơn)
        double dx = currentTarget.getX() - player.getX();
        double dy = (currentTarget.getY() + currentTarget.getHeight() * 0.65) - player.getEyeY();
        double dz = currentTarget.getZ() - player.getZ();

        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDistance)));

        float yawDiff = MathHelper.wrapDegrees(targetYaw - player.getYaw());
        float pitchDiff = targetPitch - player.getPitch();

        // Kiểm tra góc nhìn FOV: Không can thiệp nếu mục tiêu quá lệch tâm hoặc đã ngắm rất sát
        double totalAngle = Math.abs(yawDiff) + Math.abs(pitchDiff);
        if (totalAngle > MAX_FOV || totalAngle < 0.4) {
            return;
        }

        // 3. Phá vỡ các đặc điểm thống kê và mẫu lặp lại bằng Dynamic Smoothing & Noise Jitter
        // Thay vì dùng một giá trị cố định[span_2](start_span)[span_2](end_span),smoothing dao động ngẫu nhiên theo thời gian thực
        float baseSmoothing = 0.11f + RANDOM.nextFloat() * 0.09f; 

        // Thêm nhiễu ngẫu nhiên mô phỏng tay người thật (Human Jitter) chống phát hiện thống kê rotation
        float yawNoise = (RANDOM.nextFloat() - 0.5f) * 0.3f;
        float pitchNoise = (RANDOM.nextFloat() - 0.5f) * 0.3f;

        float appliedYaw = yawDiff * baseSmoothing + yawNoise;
        float appliedPitch = pitchDiff * baseSmoothing + pitchNoise;

        // 4. Giới hạn Rotation Delta và Acceleration để chống thay đổi góc nhìn/flick đột ngột
        appliedYaw = MathHelper.clamp(appliedYaw, -5.5f, 5.5f);
        appliedPitch = MathHelper.clamp(appliedPitch, -3.5f, 3.5f);

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
            if (candidate == player
                    || !candidate.isAlive()
                    || candidate.isSpectator()
                    || candidate.isRemoved()) {
                continue;
            }

            double distance = player.distanceTo(candidate);
            if (distance > RANGE) {
                continue;
            }

            // Ưu tiên chọn mục tiêu có góc lệch tâm màn hình nhỏ nhất thay vì chỉ dựa vào khoảng cách thuần túy
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
