package com.example.legitaim.modules;

import com.example.legitaim.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/**
 * Legit Triggerbot.
 * - Phím R: Bật / Tắt (Toggle) Triggerbot độc lập.
 * - Tự động đánh khi tâm ngắm chạm vào mục tiêu hợp lệ.
 */
public final class Triggerbot {

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random RANDOM = new Random();

    // Khai báo phím tắt R
    public static final KeyBinding TOGGLE_KEY = new KeyBinding(
        "key.legitaim.triggerbot",
        InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_R, // Gán phím R
        "category.legitaim"
    );

    private static boolean enabled = false;
    private static boolean keyPressedLastTick = false;

    private static long lastAttackTime = 0L;
    private static long nextAttackDelay = 0L;
    private static boolean delayInitialized = false;

    private Triggerbot() {}

    public static void tick() {
        ClientPlayerEntity player = mc.player;

        // Xử lý sự kiện nhấn phím R để Bật/Tắt
        boolean isPressed = TOGGLE_KEY.isPressed();
        if (isPressed && !keyPressedLastTick) {
            enabled = !enabled;
            if (player != null) {
                player.sendMessage(
                    Text.of("§a[LegitAim] Triggerbot: " + (enabled ? "§2ON" : "§cOFF")),
                    true
                );
            }
        }
        keyPressedLastTick = isPressed;

        // Nếu chưa bật hoặc player/world null thì bỏ qua
        if (!enabled || player == null || mc.world == null) {
            delayInitialized = false;
            return;
        }

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) return;

        Entity entity = ((EntityHitResult) hit).getEntity();
        if (!(entity instanceof PlayerEntity target)) return;
        if (!target.isAlive() || target.isSpectator() || target.isRemoved()) return;

        // Kiểm tra cooldown
        float cooldown = player.getAttackCooldownProgress(0.5f);
        if (cooldown < 0.999f) return;
        if (mc.interactionManager == null) return;

        long now = System.currentTimeMillis();

        ModConfig.TriggerSnapshot cfg = ModConfig.snapshotTrigger();
        if (!delayInitialized) {
            long range = Math.max(1L, cfg.maxDelay() - cfg.minDelay());
            nextAttackDelay = cfg.minDelay() + RANDOM.nextInt((int) range);
            delayInitialized = true;
        }

        if (now - lastAttackTime < nextAttackDelay) return;

        // Thực hiện đánh mục tiêu
        mc.interactionManager.attackEntity(player, target);
        player.swingHand(player.getActiveHand());

        lastAttackTime = now;
        delayInitialized = false;
    }
}
