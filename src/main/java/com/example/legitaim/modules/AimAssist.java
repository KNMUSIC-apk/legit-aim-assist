package com.example.legitaim.modules;

import com.example.legitaim.config.ModConfig;
import com.example.legitaim.util.RotationUtils;
import com.example.legitaim.util.TargetUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

public final class AimAssist {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static AbstractClientPlayerEntity currentTarget = null;
    private static final double[] AIM_POINT = new double[3];
    private static int tickCounter = 0;
    private static float disengageFactor = 0.0f;
    private static float lastYawDelta   = 0.0f;
    private static float lastPitchDelta = 0.0f;

    private static final float[] ANGLE_OUT = new float[2];

    private AimAssist() {}

    public static void tick() {
        ModConfig.AimSnapshot cfg = ModConfig.snapshotAim();

        ClientPlayerEntity player = mc.player;
        if (!cfg.enabled() || player == null || mc.world == null) {
            smoothlyDisengage(player);
            return;
        }

        tickCounter++;

        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = player.getRotationVec(1.0f);

        Optional<AbstractClientPlayerEntity> targetOpt = TargetUtils.findTarget(
            cfg.reach(), cfg.fov(), eyePos, lookVec
        );

        if (targetOpt.isEmpty()) {
            smoothlyDisengage(player);
            return;
        }

        AbstractClientPlayerEntity target = targetOpt.get();
        currentTarget = target;
        
        // Khóa tâm cực nhanh khi vừa phát hiện mục tiêu
        disengageFactor = Math.min(1.0f, disengageFactor + 0.40f);

        if (tickCounter % cfg.updateInterval() == 0) {
            RotationUtils.getDynamicAimPoint(target, AIM_POINT);
        }

        // ============================================================
        // TARGET MOTION PREDICTION (Dự đoán hướng di chuyển)
        // ============================================================
        Vec3d targetVel = target.getVelocity();
        // Bù góc đón đầu dựa trên vận tốc hiện tại của đối thủ (đặc biệt hiệu quả khi combo/strafe)
        double predictedX = AIM_POINT[0] + (targetVel.x * 1.85D);
        double predictedY = AIM_POINT[1] + (targetVel.y * 0.85D);
        double predictedZ = AIM_POINT[2] + (targetVel.z * 1.85D);

        RotationUtils.calculateAngles(
            eyePos.x, eyePos.y, eyePos.z,
            predictedX, predictedY, predictedZ,
            ANGLE_OUT
        );

        float effectiveSpeed = cfg.speed() * 1.30f * disengageFactor;

        float[] result = RotationUtils.smoothRotation(
            player.getYaw(), player.getPitch(),
            ANGLE_OUT[0], ANGLE_OUT[1],
            effectiveSpeed,
            cfg.jitter(),
            cfg.maxYawPerTick() * 1.25f,
            cfg.maxPitchPerTick() * 1.25f
        );

        lastYawDelta   = wrapDegrees(result[0] - player.getYaw());
        lastPitchDelta = result[1] - player.getPitch();

        player.setYaw(result[0]);
        player.setPitch(result[1]);
    }

    private static void smoothlyDisengage(ClientPlayerEntity player) {
        if (player == null) {
            currentTarget = null;
            disengageFactor = 0.0f;
            lastYawDelta = 0.0f;
            lastPitchDelta = 0.0f;
            RotationUtils.resetJitter();
            return;
        }

        if (disengageFactor > 0.0f) {
            disengageFactor = Math.max(0.0f, disengageFactor - 0.15f);

            float decay = disengageFactor * 0.5f;
            player.setYaw(player.getYaw() + lastYawDelta * decay);
            player.setPitch(player.getPitch() + lastPitchDelta * decay);

            lastYawDelta   *= 0.75f;
            lastPitchDelta *= 0.75f;
        } else {
            currentTarget = null;
            lastYawDelta = 0.0f;
            lastPitchDelta = 0.0f;
            RotationUtils.resetJitter();
        }
    }

    public static AbstractClientPlayerEntity getCurrentTarget() {
        return currentTarget;
    }

    private static float wrapDegrees(float deg) {
        deg = deg % 360.0f;
        if (deg >= 180.0f)  deg -= 360.0f;
        if (deg < -180.0f)  deg += 360.0f;
        return deg;
    }
}
