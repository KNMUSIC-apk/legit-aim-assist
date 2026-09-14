package com.example.legitaim.modules;

import com.example.legitaim.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.Random;

public final class Triggerbot {

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random RANDOM = new Random();

    private static long lastAttackTime = 0L;
    private static long nextAttackDelay = 0L;
    private static boolean delayInitialized = false;

    // Quản lý Target & Reaction
    private static Entity lastTarget = null;
    private static long targetAcquiredTime = 0L;
    private static long reactionDelay = 0L;
    private static int offTargetTicks = 0; // Giúp giữ nhịp khi rê tâm trượt nhẹ

    private Triggerbot() {}

    public static void tick() {
        ModConfig.TriggerSnapshot cfg = ModConfig.snapshotTrigger();
        ClientPlayerEntity player = mc.player;

        if (!cfg.enabled() || player == null || mc.world == null) {
            resetTargetState();
            return;
        }

        HitResult hit = mc.crosshairTarget;
        Entity targetEntity = null;

        if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) hit).getEntity();
            if (entity instanceof PlayerEntity p && p.isAlive() && !p.isSpectator() && !p.isRemoved()) {
                targetEntity = p;
            }
        }

        long now = System.currentTimeMillis();

        // Nếu không ngắm vào mục tiêu, cho phép du di 3 tick trước khi reset hẳn
        if (targetEntity == null) {
            offTargetTicks++;
            if (offTargetTicks > 3) {
                resetTargetState();
            }
            return;
        }

        offTargetTicks = 0; // Đã ngắm lại trúng target

        // 1. Phản xạ nhanh (40ms - 80ms) thay vì 120ms - 180ms
        if (lastTarget != targetEntity) {
            lastTarget = targetEntity;
            targetAcquiredTime = now;
            reactionDelay = 40L + RANDOM.nextInt(40); 
            return;
        }

        if (now - targetAcquiredTime < reactionDelay) {
            return;
        }

        // 2. Hạ ngưỡng Cooldown xuống 0.92f để bắt nhịp vung tay chuẩn xác
        float cooldown = player.getAttackCooldownProgress(0.0f);
        if (cooldown < 0.92f) return;
        if (mc.interactionManager == null) return;

        // 3. Xử lý khoảng cách thời gian giữa các cú đánh
        if (!delayInitialized) {
            long minD = Math.max(1L, cfg.minDelay());
            long maxD = Math.max(minD + 1, cfg.maxDelay());
            nextAttackDelay = minD + RANDOM.nextInt((int) (maxD - minD));
            delayInitialized = true;
        }

        if (now - lastAttackTime < nextAttackDelay) return;

        // 4. Đánh thật 100% nhịp khi đã đủ điều kiện
        mc.interactionManager.attackEntity(player, targetEntity);
        player.swingHand(player.getActiveHand());

        lastAttackTime = now;
        delayInitialized = false;
    }

    private static void resetTargetState() {
        lastTarget = null;
        targetAcquiredTime = 0L;
        offTargetTicks = 0;
        delayInitialized = false;
    }
}
