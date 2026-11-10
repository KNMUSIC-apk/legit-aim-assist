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
    private static final double MAX_FOV = 35.0;
    private static final Random RANDOM = new Random();

    private static AbstractClientPlayerEntity currentTarget = null;
    private static int targetLockTicks = 0;
    
    // Giảm biên độ trôi và siết chặt tâm ngắm hơn một chút so với trước
    private static double dynamicOffsetX = 0.0;
    private static double dynamicOffsetY = 0.0;
    private static int swayTimer = 0;

    public static void onClientTick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null) {
            resetAimState();
            return;
        }

        PlayerEntity player = client.player;

        String itemId = Registries.ITEM
                .getId(player.getMainHandStack().getItem())
                .getPath();

        boolean validWeapon = itemId.endsWith("_sword") || itemId.endsWith("_axe");
        if (!validWeapon) {
            resetAimState();
            return;
        }

        if (currentTarget == null || !currentTarget.isAlive() || currentTarget.isSpectator() || currentTarget.isRemoved() || player.distanceTo(currentTarget) > RANGE) {
            if (RANDOM.nextFloat() < 0.3f) {
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

        // Thu nhỏ biên độ lệch để lực hút tâm mạnh hơn nhưng vẫn giữ độ tự nhiên
        if (swayTimer > 15 + RANDOM.nextInt(10)) {
            swayTimer = 0;
            dynamicOffsetX = (RANDOM.nextDouble() - 0.5) * 0.18; 
            dynamicOffsetY = (RANDOM.nextDouble() - 0.5) * 0.12; 
        }

        double targetX = currentTarget.getX() + dynamicOffsetX;
        double targetY = (currentTarget.getY() + currentTarget.getHeight() * 0.62) + dynamicOffsetY;
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
        if (totalAngle > MAX_FOV || totalAngle < 0.15) {
            return;
        }

        // Tăng nhẹ hệ số mượt (baseSmoothing) để phản hồi nhanh và chặt chẽ hơn
        float accelerationFactor = Math.min(1.0f, targetLockTicks / 12.0f);
        float baseSmoothing = (0.12f + RANDOM.nextFloat() * 0.08f) * accelerationFactor;

        float yawNoise = (RANDOM.nextFloat() - 0.5f) * 0.25f;
        float pitchNoise = (RANDOM.nextFloat() - 0.5f) * 0.20f;

        float appliedYaw = yawDiff * baseSmoothing + yawNoise;
        float appliedPitch = pitchDiff * baseSmoothing + pitchNoise;

        appliedYaw = MathHelper.clamp(appliedYaw, -5.5f, 5.5f);
        appliedPitch = MathHelper.clamp(appliedPitch, -3.2f, 3.2f);

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
