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

    // Biến quản lý Human Reaction Delay (Post-Aim Check)
    private static Entity lastTarget = null;
    private static long targetAcquiredTime = 0L;
    private static long reactionDelay = 0L;

    private Triggerbot() {}

    public static void tick() {
        ModConfig.TriggerSnapshot cfg = ModConfig.snapshotTrigger();
        ClientPlayerEntity player = mc.player;

        if (!cfg.enabled() || player == null || mc.world == null) {
            resetTargetState();
            return;
        }

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) {
            resetTargetState();
            return;
        }

        Entity entity = ((EntityHitResult) hit).getEntity();
        if (!(entity instanceof PlayerEntity target)) {
            resetTargetState();
            return;
        }

        if (!target.isAlive() || target.isSpectator() || target.isRemoved()) {
            resetTargetState();
            return;
        }

        long now = System.currentTimeMillis();

        // 1. Human Reaction Delay: Độ trễ phản ứng ngẫu nhiên khi rê tâm trúng đối thủ (120ms - 180ms)
        if (lastTarget != target) {
            lastTarget = target;
            targetAcquiredTime = now;
            reactionDelay = 120L + RANDOM.nextInt(60); 
            return;
        }

        // Bắt buộc chờ đủ thời gian phản xạ người chơi trước khi đánh
        if (now - targetAcquiredTime < reactionDelay) {
            return;
        }

        // 2. Cooldown Check
        float cooldown = player.getAttackCooldownProgress(0.5f);
        if (cooldown < 0.999f) return;
        if (mc.interactionManager == null) return;

        // 3. Attack Interval Delay
        if (!delayInitialized) {
            long range = Math.max(1L, cfg.maxDelay() - cfg.minDelay());
            nextAttackDelay = cfg.minDelay() + RANDOM.nextInt((int) range);
            delayInitialized = true;
        }

        if (now - lastAttackTime < nextAttackDelay) return;

        // 4. Miss Chance (Xác suất 10% đánh trượt)
        boolean isMiss = RANDOM.nextInt(100) < 10; // 10% cơ hội miss

        if (isMiss) {
            // Chỉ vung tay đánh gió, không gửi packet attackEntity tới target
            player.swingHand(player.getActiveHand());
        } else {
            // Tấn công thật
            mc.interactionManager.attackEntity(player, target);
            player.swingHand(player.getActiveHand());
        }

        lastAttackTime = now;
        delayInitialized = false;
    }

    private static void resetTargetState() {
        lastTarget = null;
        targetAcquiredTime = 0L;
        delayInitialized = false;
    }
}
