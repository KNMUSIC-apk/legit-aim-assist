package com.example.legitaim.modules;

import com.example.legitaim.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Comparator;
import java.util.List;

public class AimAssist {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void tick() {}

    public static void onRender(WorldRenderContext context) {
        ModConfig.AimSnapshot cfg = ModConfig.snapshotAim();
        if (!cfg.enabled() || mc.player == null || mc.world == null) return;

        if (mc.currentScreen != null) return;

        float tickDelta = context.tickCounter().getTickDelta(true);

        AbstractClientPlayerEntity target = findBestTarget(cfg.reach(), cfg.fov(), tickDelta);
        if (target != null) {
            aimAt(target, cfg, tickDelta);
        }
    }

    private static void aimAt(AbstractClientPlayerEntity target, ModConfig.AimSnapshot cfg, float tickDelta) {
        if (mc.player == null) return;

        // Dự đoán hướng di chuyển ở mức vừa phải (0.28)
        double targetX = MathHelper.lerp(tickDelta, target.prevX, target.getX()) + (target.getVelocity().x * 0.28);
        double targetY = MathHelper.lerp(tickDelta, target.prevY, target.getY()) + (target.getHeight() * 0.62) + (target.getVelocity().y * 0.28);
        double targetZ = MathHelper.lerp(tickDelta, target.prevZ, target.getZ()) + (target.getVelocity().z * 0.28);

        Vec3d eyePos = mc.player.getCameraPosVec(tickDelta);

        double diffX = targetX - eyePos.x;
        double diffY = targetY - eyePos.y;
        double diffZ = targetZ - eyePos.z;
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float idealYaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float idealPitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));

        float yawDiff = MathHelper.wrapDegrees(idealYaw - mc.player.getYaw());
        float pitchDiff = MathHelper.wrapDegrees(idealPitch - mc.player.getPitch());

        // Deadzone 0.15f: Giúp tâm ổn định ở giữa ngực mục tiêu, không rung nhè nhẹ
        if (Math.abs(yawDiff) < 0.15f && Math.abs(pitchDiff) < 0.15f) {
            return;
        }

        // Tinh chỉnh lực hút cân bằng: Vừa bám sát, vừa không bị quá cứng tay
        float distanceToTargetAngle = (float) Math.hypot(yawDiff, pitchDiff);
        float speedMultiplier = MathHelper.clamp(distanceToTargetAngle / 7.5f, 0.35f, 1.0f);
        
        float smoothFactor = Math.max(1.2f, (21.0f - cfg.speed()) * 0.8f);
        
        float stepYaw = (yawDiff / smoothFactor) * speedMultiplier;
        float stepPitch = (pitchDiff / smoothFactor) * speedMultiplier;

        // Giới hạn góc xoay tối đa cân bằng chuẩn Legit
        float maxStepYaw = Math.max(1.2f, cfg.maxYawPerTick() * 1.6f * (tickDelta > 0 ? tickDelta : 1.0f));
        float maxStepPitch = Math.max(1.2f, cfg.maxPitchPerTick() * 1.6f * (tickDelta > 0 ? tickDelta : 1.0f));

        stepYaw = MathHelper.clamp(stepYaw, -maxStepYaw, maxStepYaw);
        stepPitch = MathHelper.clamp(stepPitch, -maxStepPitch, maxStepPitch);

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
            .filter(AimAssist::canSeeEntity)
            .min(Comparator.comparingDouble(p -> get3DAngleDifference(p, tickDelta)))
            .orElse(null);
    }

    private static double get3DAngleDifference(AbstractClientPlayerEntity target, float tickDelta) {
        if (mc.player == null) return 999.0;

        Vec3d eyePos = mc.player.getCameraPosVec(tickDelta);
        double diffX = target.getX() - eyePos.x;
        double diffY = (target.getY() + target.getHeight() * 0.62) - eyePos.y;
        double diffZ = target.getZ() - eyePos.z;
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));

        float yawDiff = MathHelper.wrapDegrees(yaw - mc.player.getYaw());
        float pitchDiff = MathHelper.wrapDegrees(pitch - mc.player.getPitch());

        return Math.hypot(yawDiff, pitchDiff);
    }

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
