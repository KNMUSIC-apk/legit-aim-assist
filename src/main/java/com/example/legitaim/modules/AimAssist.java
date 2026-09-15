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

    private static float prevPlayerYaw = 0.0f;
    private static float prevPlayerPitch = 0.0f;

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
            prevPlayerYaw = player.getYaw();
            prevPlayerPitch = player.getPitch();
            return;
        }

        AbstractClientPlayerEntity target = targetOpt.get();
        currentTarget = target;
        
        // Bắt tâm nhanh hơn, khóa chặt hơn (Tăng từ 0.35 lên 0.45)
        disengageFactor = Math.min(1.0f, disengageFactor + 0.45f);

        Vec3d targetPos = target.getPos();
        Vec3d targetVel = target.getVelocity();

        // Ghim vào giữa thân người
        double targetY = targetPos.y + (target.getHeight() * 0.45D);
        double targetX = targetPos.x;
        double targetZ = targetPos.z;

        if (!target.isOnGround()) {
            // Khóa chặt trục Y khi đối thủ nhảy
            targetY += (targetVel.y * 1.15D);
            targetX += (targetVel.x * 1.30D);
            targetZ += (targetVel.z * 1.30D);
        } else {
            // Giảm độ rung lắc đi dạo (Sway) để aim cứng cáp hơn (0.15 xuống 0.05)
            double swayAmount = 0.05D; 
            targetX += Math.cos(tickCounter * 0.3) * swayAmount;
            targetZ += Math.sin(tickCounter * 0.3) * swayAmount;
            
            targetX += (targetVel.x * 1.05D);
            targetZ += (targetVel.z * 1.05D);
        }

        RotationUtils.calculateAngles(
            eyePos.x, eyePos.y, eyePos.z,
            targetX, targetY, targetZ,
            ANGLE_OUT
        );

        float userYawMovement = Math.abs(player.getYaw() - prevPlayerYaw);
        float userPitchMovement = Math.abs(player.getPitch() - prevPlayerPitch);
        
        // TIGHTER AIM: Khi dùng chuột tay, vẫn giữ lại 65% sức mạnh của AimAssist (thay vì 25% như trước)
        // Tạo cảm giác chuột có "nam châm" hút vào người
        float mouseInterferenceMultiplier = 1.0f;
        if (userYawMovement > 3.0f || userPitchMovement > 3.0f) {
            mouseInterferenceMultiplier = 0.65f; 
        }

        // Tăng hệ số tốc độ cơ sở lên 1.35x để bám dính tốt hơn khi đối thủ strafe
        float effectiveSpeed = cfg.speed() * 1.35f * disengageFactor * mouseInterferenceMultiplier;

        float[] result = RotationUtils.smoothRotation(
            player.getYaw(), player.getPitch(),
            ANGLE_OUT[0], ANGLE_OUT[1],
            effectiveSpeed,
            cfg.jitter() * (mouseInterferenceMultiplier == 1.0f ? 1.0f : 0.5f), // Giảm jitter một nửa nếu đang cầm chuột
            cfg.maxYawPerTick(),
            cfg.maxPitchPerTick()
        );

        lastYawDelta   = wrapDegrees(result[0] - player.getYaw());
        lastPitchDelta = result[1] - player.getPitch();

        player.setYaw(result[0]);
        player.setPitch(result[1]);

        prevPlayerYaw = player.getYaw();
        prevPlayerPitch = player.getPitch();
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
            disengageFactor = Math.max(0.0f, disengageFactor - 0.25f); // Ngắt mục tiêu dứt khoát hơn
            float decay = disengageFactor * 0.5f;
            
            player.setYaw(player.getYaw() + lastYawDelta * decay);
            player.setPitch(player.getPitch() + lastPitchDelta * decay);

            lastYawDelta   *= 0.50f;
            lastPitchDelta *= 0.50f;
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
