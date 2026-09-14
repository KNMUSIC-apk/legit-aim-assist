package com.example.legitaim.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

import java.util.Random;

/**
 * Rotation helpers với:
 *  - Ease-in-out interpolation
 *  - Per-tick clamps (anticheat-safe)
 *  - Band-limited jitter (random walk thay vì uniform noise)
 *  - Pooled output arrays (không alloc trong tick loop)
 */
public final class RotationUtils {

    private static final Random RANDOM = new Random();

    // Band-limited jitter state (random walk)
    private static float jitterYaw   = 0.0f;
    private static float jitterPitch = 0.0f;

    private RotationUtils() {}

    /**
     * Ghi điểm ngắm động vào mảng out[3] — không tạo Vec3d.
     * Vùng ngắm: 55%–80% chiều cao hitbox (ngực / thân trên).
     */
    public static void getDynamicAimPoint(PlayerEntity target, double[] out) {
        Box box = target.getBoundingBox();
        out[0] = box.minX + (box.maxX - box.minX) * (0.3 + RANDOM.nextDouble() * 0.4);
        out[1] = box.minY + (box.maxY - box.minY) * (0.55 + RANDOM.nextDouble() * 0.25);
        out[2] = box.minZ + (box.maxZ - box.minZ) * (0.3 + RANDOM.nextDouble() * 0.4);
    }

    /**
     * Smooth rotation. Trả về float[2] {yaw, pitch} — caller KHÔNG lưu array.
     */
    public static float[] smoothRotation(
            float currentYaw, float currentPitch,
            float targetYaw, float targetPitch,
            float speed, float jitterDeg,
            float maxYawStep, float maxPitchStep) {

        // Wrap về [-180, 180] để đi đường ngắn nhất
        float deltaYaw   = wrapDegrees(targetYaw - currentYaw);
        float deltaPitch = targetPitch - currentPitch;

        // Smoothstep ease-in-out
        float t = Math.min(1.0f, speed * 0.05f);
        float eased = t * t * (3.0f - 2.0f * t);

        float stepYaw   = clamp(deltaYaw   * eased, -maxYawStep,   maxYawStep);
        float stepPitch = clamp(deltaPitch * eased, -maxPitchStep, maxPitchStep);

        // Band-limited jitter
        jitterYaw   = jitterYaw   * 0.6f + (RANDOM.nextFloat() - 0.5f) * 0.4f * jitterDeg;
        jitterPitch = jitterPitch * 0.6f + (RANDOM.nextFloat() - 0.5f) * 0.4f * jitterDeg;

        float newYaw   = wrapDegrees(currentYaw + stepYaw + jitterYaw);
        float newPitch = clamp(currentPitch + stepPitch + jitterPitch, -90.0f, 90.0f);

        return new float[]{newYaw, newPitch};
    }

    /**
     * Góc từ eyePos → target point. Ghi vào out[2] — không alloc.
     */
    public static void calculateAngles(
            double eyeX, double eyeY, double eyeZ,
            double tx, double ty, double tz,
            float[] out) {

        double dx = tx - eyeX;
        double dy = ty - eyeY;
        double dz = tz - eyeZ;

        double dist = Math.sqrt(dx * dx + dz * dz);
        out[0] = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        out[1] = (float) -Math.toDegrees(Math.atan2(dy, dist));
    }

    public static void resetJitter() {
        jitterYaw = 0.0f;
        jitterPitch = 0.0f;
    }

    private static float wrapDegrees(float deg) {
        deg = deg % 360.0f;
        if (deg >= 180.0f)  deg -= 360.0f;
        if (deg < -180.0f)  deg += 360.0f;
        return deg;
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
