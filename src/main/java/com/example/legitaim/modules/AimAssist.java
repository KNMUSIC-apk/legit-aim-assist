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
    
    private static double dynamicOffsetX = 0.0;
    private static double dynamicOffsetY = 0.0;
    private static int swayTimer = 0;
    private static int reactionLagTicks = 0;
    private static int sustainedCombatTicks = 0;

    // Biến lưu trữ góc nhìn của tick trước để tính vận tốc quay chuột
    private static float lastYaw = 0.0f;
    private static float lastPitch = 0.0f;

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

        // Tính delta vận tốc dựa trên góc nhìn tick trước
        float playerVelocityDelta = Math.abs(player.getYaw() - lastYaw) + Math.abs(player.getPitch() - lastPitch);
        lastYaw = player.getYaw();
        lastPitch = player.getPitch();

        if (playerVelocityDelta > 14.0f) {
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

        if (sustainedCombatTicks > 160) {
            if (RANDOM.nextFloat() < 0.1f) {
                sustainedCombatTicks = -45;
                currentTarget = null;
                return;
            }
        } else if (sustainedCombatTicks < 0) {
            sustainedCombatTicks++;
            return;
        }
        sustainedCombatTicks++;

        if (currentTarget == null || !currentTarget.isAlive() || currentTarget.isSpectator() || currentTarget.isRemoved() || player.distanceTo(currentTarget) > RANGE) {
            if (RANDOM.nextFloat() < 0.25f) {
                currentTarget = findBestTarget(player, client);
                targetLockTicks = 0;
                reactionLagTicks = 2 + RANDOM.nextInt(4);
                resetOffsets();
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
        swayTimer++;

        if (swayTimer > 12 + RANDOM.nextInt(12)) {
            swayTimer = 0;
            dynamicOffsetX = (RANDOM.nextDouble() - 0.5) * 0.20; 
            dynamicOffsetY = (RANDOM.nextDouble() - 0.5) * 0.14; 
        }

        double targetX = currentTarget.getX() + dynamicOffsetX;
        double targetY = (currentTarget.getY() + currentTarget.getHeight() * 0.58) + dynamicOffsetY;
        double targetZ = currentTarget.getZ() + dynamicOffsetX;

        double dx = targetX - player.getX();
        double dy = targetY - player.getEyeY();
        double dz = targetZ - player.getZ();

        double distance = player.distanceTo(currentTarget);
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        
        double dynamicFov = Math.max(16.0, 36.0 - (distance * 3.5));

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDistance)));

        float yawDiff = MathHelper.wrapDegrees(targetYaw - player.getYaw());
        float pitchDiff = targetPitch - player.getPitch();

        double totalAngle = Math.abs(yawDiff) + Math.abs(pitchDiff);
        if (totalAngle > dynamicFov || totalAngle < 0.18) {
            return;
        }

        double gaussianLikeFactor = RANDOM.nextGaussian() * 0.025 + 0.12;
        float accelerationFactor = Math.min(1.0f, targetLockTicks / 14.0f);
        float baseSmoothing = (float) Math.max(0.06, gaussianLikeFactor) * accelerationFactor;

        float yawNoise = (float) ((RANDOM.nextGaussian() * 0.22) + ((RANDOM.nextFloat() - 0.5f) * 0.3f));
        float pitchNoise = (float) ((RANDOM.nextGaussian() * 0.12) + ((RANDOM.nextFloat() - 0.5f) * 0.2f));

        float appliedYaw = yawDiff * baseSmoothing + yawNoise;
        float appliedPitch = pitchDiff * baseSmoothing + pitchNoise;

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
        
        // Cập nhật lại lastYaw/lastPitch sau khi mod thay đổi góc nhìn
        lastYaw = player.getYaw();
        lastPitch = player.getPitch();
    }

    private static AbstractClientPlayerEntity findBestTarget(PlayerEntity player, MinecraftClient client) {
        AbstractClientPlayerEntity best = null;
        double minAngle = 35.0;

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
        resetOffsets();
    }

    private static void resetOffsets() {
        dynamicOffsetX = 0.0;
        dynamicOffsetY = 0.0;
        swayTimer = 0;
    }
}
