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

    private Triggerbot() {}

    public static void tick() {
        ModConfig.TriggerSnapshot cfg = ModConfig.snapshotTrigger();
        ClientPlayerEntity player = mc.player;

        if (!cfg.enabled() || player == null || mc.world == null) {
            delayInitialized = false;
            return;
        }

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) return;

        Entity entity = ((EntityHitResult) hit).getEntity();
        if (!(entity instanceof PlayerEntity target)) return;
        if (!target.isAlive() || target.isSpectator() || target.isRemoved()) return;

        float cooldown = player.getAttackCooldownProgress(0.5f);
        if (cooldown < 0.999f) return;
        if (mc.interactionManager == null) return;

        long now = System.currentTimeMillis();

        if (!delayInitialized) {
            long range = Math.max(1L, cfg.maxDelay() - cfg.minDelay());
            nextAttackDelay = cfg.minDelay() + RANDOM.nextInt((int) range);
            delayInitialized = true;
        }

        if (now - lastAttackTime < nextAttackDelay) return;

        // Tấn công mục tiêu
        mc.interactionManager.attackEntity(player, target);

        player.swingHand(player.getActiveHand());

        lastAttackTime = now;
        delayInitialized = false;
    }
}
