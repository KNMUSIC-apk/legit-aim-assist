package com.example.legitaim.modules;

import com.example.legitaim.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext; // Đã sửa đường dẫn import tại đây

import java.util.Comparator;
import java.util.List;

public class AimAssist {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void onRender(WorldRenderContext context) {
        ModConfig.AimSnapshot cfg = ModConfig.snapshotAim();
        if (!cfg.enabled() || mc.player == null || mc.world == null) return;

        // Bỏ qua khi đang trong Inventory/Menu
        if (mc.currentScreen != null) return;

        // Lấy tickDelta chuẩn từ Render Context
        float tickDelta = context.tickCounter().getTickDelta(true);

        AbstractClientPlayerEntity target = findBestTarget(cfg.reach(), cfg.fov(), tickDelta);
        if (target != null) {
            aimAt(target, cfg, tickDelta);
        }
    }

    private static void aimAt(AbstractClientPlayerEntity target, ModConfig.AimSnapshot cfg, float tickDelta) {
        if (mc.player == null) return;

        // 1. Nội suy vị trí chuẩn của mục tiêu (kết hợp Motion Prediction - dự đoán hướng di chuyển)
        double targetX = MathHelper.lerp(tickDelta, target.prevX, target.getX()) + (target.getVelocity().x * 0.25);
        double targetY = MathHelper.lerp(tickDelta, target.prevY, target.getY()) + (target.getHeight() * 0.65) + (target.getVelocity().y * 0.25);
        double targetZ = MathHelper.lerp(tickDelta, target.prevZ, target.getZ()) + (target.getVelocity().z * 0.25);

        // Vị trí mắt người chơi
        Vec3d eyePos = mc.player.getCameraPosVec(tickDelta);

        double diffX = targetX - eyePos.x;
        double diffY = targetY - eyePos.y;
        double diffZ = targetZ - eyePos.z;
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float idealYaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float idealPitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));

        float yawDiff = MathHelper.wrapDegrees(idealYaw - mc.player.getYaw());
        float pitchDiff = MathHelper.wrapDegrees(idealPitch - mc.player.getPitch());

        // 2. Deadzone chống rung (Tâm đã vào vùng ngực thì dừng can thiệp)
        if (Math.abs(yawDiff) < 0.25f && Math.abs(pitchDiff) < 0.25f) {
            return;
        }

        // 3. Dynamic Smooth Easing (Nội suy mượt giảm dần theo khoảng cách tâm)
        float distanceToTargetAngle = (float) Math.hypot(yawDiff, pitchDiff);
        float speedMultiplier = MathHelper.clamp(distanceToTargetAngle / 10.0f, 0.2f, 1.0f);
        
        float smoothFactor = Math.max(1.5f, (21.0f - cfg.speed()) * 1.2f);
        
        float stepYaw = (yawDiff / smoothFactor) * speedMultiplier;
        float stepPitch = (pitchDiff / smoothFactor) * speedMultiplier;

        // 4. Giới hạn gia tốc tối đa mỗi Frame
        float maxStep = Math.max(0.5f, cfg.maxYawPerTick() * (tickDelta > 0 ? tickDelta : 1.0f));
        stepYaw = MathHelper.clamp(stepYaw, -maxStep, maxStep);
        stepPitch = MathHelper.clamp(stepPitch, -cfg.maxPitchPerTick(), cfg.maxPitchPerTick());

        // Áp dụng trực tiếp góc xoay mượt vào người chơi
        mc.player.setYaw(mc.player.getYaw() + stepYaw);
        mc.player.setPitch(mc.player.getPitch() + stepPitch);
    }

    private static AbstractClientPlayerEntity findBestTarget(double maxReach, double maxFov, float tickDelta) {
        if (mc.world == null || mc.player == null) return null;

        List<AbstractClientPlayerEntity> players = mc.world.getPlayers();

        return players.stream()
            .filter(p -> p != mc.player)
            .filter(AbstractClientPlayerEntity::isAlive)
            .filter(p -> !p.isSpectator())
            .filter(p -> mc.player.distanceTo(p) <= maxReach)
            .filter(p -> get3DAngleDifference(p, tickDelta) <= maxFov)
            .filter(AimAssist::canSeeEntity) // Chỉ ngắm mục tiêu không bị che bởi khối
            .min(Comparator.comparingDouble(p -> get3DAngleDifference(p, tickDelta)))
            .orElse(null);
    }

    // Tính khoảng cách góc 3D chuẩn xác (kết hợp cả Yaw và Pitch)
    private static double get3DAngleDifference(AbstractClientPlayerEntity target, float tickDelta) {
        if (mc.player == null) return 999.0;

        Vec3d eyePos = mc.player.getCameraPosVec(tickDelta);
        double diffX = target.getX() - eyePos.x;
        double diffY = (target.getY() + target.getHeight() * 0.65) - eyePos.y;
        double diffZ = target.getZ() - eyePos.z;
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));

        float yawDiff = MathHelper.wrapDegrees(yaw - mc.player.getYaw());
        float pitchDiff = MathHelper.wrapDegrees(pitch - mc.player.getPitch());

        return Math.hypot(yawDiff, pitchDiff);
    }

    // Kiểm tra tầm nhìn (Raycast) để tránh ngắm xuyên tường
    private static boolean canSeeEntity(AbstractClientPlayerEntity target) {
        if (mc.player == null || mc.world == null) return false;

        Vec3d start = mc.player.getCameraPosVec(1.0F);
        Vec3d end = new Vec3d(target.getX(), target.getY() + target.getEyeHeight(target.getPose()), target.getZ());

        HitResult result = mc.world.raycast(new RaycastContext(
            start,
            end,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            mc.player
        ));

        return result.getType() == HitResult.Type.MISS;
    }
}
