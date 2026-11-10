package com.example.legitaim.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

public class AimAssist {

    public static boolean enabled = false;

    private static final double RANGE = 4.2;
    private static final double MAX_FOV = 30.0;
    private static final Random RANDOM = new Random();

    private static AbstractClientPlayerEntity currentTarget = null;
    private static int targetLockTicks = 0;
    
    // Các biến mô phỏng sự trôi tự nhiên và điểm ngắm không cố định trên cơ thể đối thủ
    private static double dynamicOffsetX = 0.0;
    private static double dynamicOffsetY = 0.0;
    private static int swayTimer = 0;

    public static void onClientTick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null) {
            resetAimState();
            return;
        }

        PlayerEntity player = client.player;

        // Chỉ aim khi cầm Kiếm hoặc Rìu trên tay chính
        String itemId = Registries.ITEM
                .getId(player.getMainHandStack().getItem())
                .getPath();

        boolean validWeapon = itemId.endsWith("_sword") || itemId.endsWith("_axe");
        if (!validWeapon) {
            resetAimState();
            return;
        }

        // Quản lý mục tiêu thông minh
        if (currentTarget == null || !currentTarget.isAlive() || currentTarget.isSpectator() || currentTarget.isRemoved() || player.distanceTo(currentTarget) > RANGE) {
            if (RANDOM.nextFloat() < 0.25f) {
                currentTarget = findBestTarget(player, client);
                targetLockTicks = 0;
                resetOffsets();
            }
        }

        if (currentTarget == null) {
            targetLockTicks = 0;
            return;
        }

        targetLockTicks++;
        swayTimer++;

        // Tạo điểm lệch động (Dynamic Offset): Không bao giờ aim chính xác 100% vào tâm tuyệt đối,
        // giúp tâm ngắm lướt qua lại quanh vùng ngực/vai giống tay người thật cầm chuột.
        if (swayTimer > 12 + RANDOM.nextInt(15)) {
            swayTimer = 0;
            dynamicOffsetX = (RANDOM.nextDouble() - 0.5) * 0.35; // Lệch ngang nhẹ
            dynamicOffsetY = (RANDOM.nextDouble() - 0.5) * 0.25; // Lệch dọc nhẹ quanh thân trên
        }

        // Tọa độ mục tiêu cộng thêm độ lệch trôi tự nhiên
        double targetX = currentTarget.getX() + dynamicOffsetX;
        double targetY = (currentTarget.getY() + currentTarget.getHeight() * 0.60) + dynamicOffsetY;
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
        if (totalAngle > MAX_FOV || totalAngle < 0.25) {
            return;
        }

        // Độ mượt biến thiên theo thời gian khóa (Mô phỏng gia tốc cổ tay con người)
        float accelerationFactor = Math.min(1.0f, targetLockTicks / 20.0f);
        float baseSmoothing = (0.07f + RANDOM.nextFloat() * 0.06f) * accelerationFactor;

        // Nhiễu vi mô sinh lý (Physiological Tremor) làm rung nhẹ hướng nhìn
        float yawNoise = (RANDOM.nextFloat() - 0.5f) * 0.45f;
        float pitchNoise = (RANDOM.nextFloat() - 0.5f) * 0.35f;

        float appliedYaw = yawDiff * baseSmoothing + yawNoise;
        float appliedPitch = pitchDiff * baseSmoothing + pitchNoise;

        // Giới hạn delta góc quay tránh các bước nhảy góc thô
        appliedYaw = MathHelper.clamp(appliedYaw, -4.0f, 4.0f);
        appliedPitch = MathHelper.clamp(appliedPitch, -2.2f, 2.2f);

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
        resetOffsets();
    }

    private static void resetOffsets() {
        dynamicOffsetX = 0.0;
        dynamicOffsetY = 0.0;
        swayTimer = 0;
    }
}
