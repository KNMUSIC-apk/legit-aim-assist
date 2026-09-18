package com.example.legitaim.modules;

import com.example.legitaim.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class AimAssist {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // Gọi hàm này bên trong WorldRenderEvents.START hoặc WorldRenderEvents.BEFORE_ENTITIES
    public static void onRender(WorldRenderContext context) {
        ModConfig.AimSnapshot cfg = ModConfig.snapshotAim();
        if (!cfg.enabled() || mc.player == null || mc.world == null) return;

        // Chỉ chạy khi không mở Menu/Inventory
        if (mc.currentScreen != null) return;

        AbstractClientPlayerEntity target = findBestTarget(cfg.reach(), cfg.fov());
        if (target != null) {
            // Lấy tickDelta để đồng bộ tốc độ quay theo FPS thực tế của màn hình
            float tickDelta = context.tickCounter().getTickDelta(true);
            aimAt(target, cfg, tickDelta);
        }
    }

    private static void aimAt(AbstractClientPlayerEntity target, ModConfig.AimSnapshot cfg, float tickDelta) {
        if (mc.player == null) return;

        // Nội suy vị trí thực tế của mục tiêu theo Frame để tránh giật khi nhảy
        double targetX = MathHelper.lerp(tickDelta, target.lastRenderX, target.getX());
        double targetY = MathHelper.lerp(tickDelta, target.lastRenderY, target.getY()) + (target.getHeight() * 0.65);
        double targetZ = MathHelper.lerp(tickDelta, target.lastRenderZ, target.getZ());

        // Mắt người chơi theo thời gian thực (Render Position)
        Vec3d eyePos = mc.player.getCameraPosVec(tickDelta);

        double diffX = targetX - eyePos.x;
        double diffY = targetY - eyePos.y;
        double diffZ = targetZ - eyePos.z;
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float idealYaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float idealPitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));

        float yawDiff = MathHelper.wrapDegrees(idealYaw - mc.player.getYaw());
        float pitchDiff = MathHelper.wrapDegrees(idealPitch - mc.player.getPitch());

        // Deadzone chống rung nhỏ
        if (Math.abs(yawDiff) < 0.3f && Math.abs(pitchDiff) < 0.3f) {
            return;
        }

        // Tính toán độ mượt dựa theo hệ số Render Frame
        float smoothFactor = Math.max(1.0f, (20.0f - cfg.speed()) * 0.8f);
        float stepYaw = (yawDiff / smoothFactor) * tickDelta;
        float stepPitch = (pitchDiff / smoothFactor) * tickDelta;

        // Giới hạn gia tốc quay
        stepYaw = MathHelper.clamp(stepYaw, -cfg.maxYawPerTick(), cfg.maxYawPerTick());
        stepPitch = MathHelper.clamp(stepPitch, -cfg.maxPitchPerTick(), cfg.maxPitchPerTick());

        // Áp dụng trực tiếp vào góc quay người chơi
        mc.player.setYaw(mc.player.getYaw() + stepYaw);
        mc.player.setPitch(mc.player.getPitch() + stepPitch);
    }

    private static AbstractClientPlayerEntity findBestTarget(double maxReach, double maxFov) {
        if (mc.world == null || mc.player == null) return null;

        List<AbstractClientPlayerEntity> players = mc.world.getPlayers();

        Optional<AbstractClientPlayerEntity> bestTarget = players.stream()
            .filter(p -> p != mc.player)
            .filter(AbstractClientPlayerEntity::isAlive)
            .filter(p -> !p.isSpectator())
            .filter(p -> mc.player.distanceTo(p) <= maxReach)
            .filter(p -> getAngleDifference(p) <= maxFov)
            .min(Comparator.comparingDouble(AimAssist::getAngleDifference));

        return bestTarget.orElse(null);
    }

    private static double getAngleDifference(AbstractClientPlayerEntity target) {
        if (mc.player == null) return 999.0;

        double diffX = target.getX() - mc.player.getX();
        double diffZ = target.getZ() - mc.player.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;

        return Math.abs(MathHelper.wrapDegrees(yaw - mc.player.getYaw()));
    }
}
