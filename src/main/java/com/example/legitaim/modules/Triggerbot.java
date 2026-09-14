package com.example.legitaim.modules;

import com.example.legitaim.config.ModConfig;
import java.util.Random;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_239;
import net.minecraft.class_310;
import net.minecraft.class_3966;
import net.minecraft.class_746;

public final class Triggerbot {

    private static final class_310 mc = class_310.method_1551();
    private static final Random RANDOM = new Random();

    private static long lastAttackTime = 0L;
    private static long nextAttackDelay = 0L;
    private static boolean delayInitialized = false;

    private Triggerbot() {}

    public static void tick() {
        ModConfig.TriggerSnapshot cfg = ModConfig.snapshotTrigger();
        class_746 player = mc.field_1724;

        if (!cfg.enabled() || player == null || mc.field_1687 == null) {
            delayInitialized = false;
            return;
        }

        class_239 hit = mc.field_1765;
        if (hit == null || hit.method_17783() != class_239.class_240.field_1331) return;

        class_1297 entity = ((class_3966) hit).method_17782();
        if (!(entity instanceof class_1657 target)) return;
        if (!target.method_5805() || target.method_7325() || target.method_31481()) return;

        float cooldown = player.method_7261(0.5f);
        if (cooldown < 0.999f) return;
        if (mc.field_1761 == null) return;

        long now = System.currentTimeMillis();

        if (!delayInitialized) {
            long range = Math.max(1L, cfg.maxDelay() - cfg.minDelay());
            nextAttackDelay = cfg.minDelay() + RANDOM.nextInt((int) range);
            delayInitialized = true;
        }

        if (now - lastAttackTime < nextAttackDelay) return;

        // Tấn công mục tiêu
        mc.field_1761.method_2918(player, target);

        player.method_6104(player.method_6058());

        lastAttackTime = now;
        delayInitialized = false;
    }
}
