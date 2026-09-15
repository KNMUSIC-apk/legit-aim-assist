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

    // Biến lưu trữ góc nhìn cũ để phát hiện thao tác chuột của người dùng
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
        disengageFactor = Math.min(1.0f, disengageFactor + 0.35f);

        // ============================================================
        // HỆ THỐNG MỤC TIÊU ĐỘNG (Body Aim & Movement Sway)
        // ============================================================
        Vec3d targetPos = target.getPos();
        Vec3d targetVel = target.getVelocity();

        // Mặc định: Ngắm vào ngực/bụng (Khoảng 45% chiều cao) thay vì đầu
        double targetY = targetPos.y + (target.getHeight() * 0.45D);
        double targetX = targetPos.x;
        double targetZ = targetPos.z;

        if (!target.isOnGround()) {
            // Khi nhảy: Lia tâm bám sát chuyển động dọc (Y axis) theo vận tốc
            targetY += (targetVel.y * 1.25D);
            targetX += (targetVel.x * 1.50D);
            targetZ += (targetVel.z * 1.50D);
        } else {
            // Khi trên mặt đất: Tạo hiệu ứng dao động nhẹ xung quanh thân (Lia tâm ngẫu nhiên)
            double swayAmount = 0.15D;
            targetX += Math.cos(tickCounter * 0.25) * swayAmount;
            targetZ += Math.sin(tickCounter * 0.25) * swayAmount;
            // Vẫn đón đầu một chút nếu họ đang đi bộ
            targetX += (targetVel.x * 1.10D);
            targetZ += (targetVel.z * 1.10D);
        }

        RotationUtils.calculateAngles(
            eyePos.x, eyePos.y, eyePos.z,
            targetX, targetY, targetZ,
            ANGLE_OUT
        );

        // ============================================================
        // TƯƠNG THÍCH CHUỘT TAY (Mouse Override Detection)
        // ============================================================
        // Tính toán xem người chơi có đang chủ động vẩy chuột trong tick này không
        float userYawMovement = Math.abs(player.getYaw() - prevPlayerYaw);
        float userPitchMovement = Math.abs(player.getPitch() - prevPlayerPitch);
        
        // Nếu người chơi đang vẩy chuột mạnh (gốc lệch lớn), tạm thời giảm lực AimAssist
        float mouseInterferenceMultiplier = 1.0f;
        if (userYawMovement > 3.0f || userPitchMovement > 3.0f) {
            mouseInterferenceMultiplier = 0.25f; // Giảm 75% lực can thiệp để không cản trở tay
        }

        float effectiveSpeed = cfg.speed() * disengageFactor * mouseInterferenceMultiplier;

        float[] result = RotationUtils.smoothRotation(
            player.getYaw(), player.getPitch(),
            ANGLE_OUT[0], ANGLE_OUT[1],
            effectiveSpeed,
            cfg.jitter() * mouseInterferenceMultiplier, // Bỏ jitter nếu đang lia chuột tay
            cfg.maxYawPerTick(),
            cfg.maxPitchPerTick()
        );

        lastYawDelta   = wrapDegrees(result[0] - player.getYaw());
        lastPitchDelta = result[1] - player.getPitch();

        player.setYaw(result[0]);
        player.setPitch(result[1]);

        // Lưu lại vị trí để so sánh tick tiếp theo
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
            disengageFactor = Math.max(0.0f, disengageFactor - 0.20f);
            float decay = disengageFactor * 0.5f;
            
            // Tôn trọng di chuyển chuột khi đang nhả tâm
            player.setYaw(player.getYaw() + lastYawDelta * decay);
            player.setPitch(player.getPitch() + lastPitchDelta * decay);

            lastYawDelta   *= 0.60f;
            lastPitchDelta *= 0.60f;
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
