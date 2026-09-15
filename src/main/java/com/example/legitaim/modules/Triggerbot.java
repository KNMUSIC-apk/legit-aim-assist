package com.example.legitaim.modules;

import com.example.legitaim.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * High-Performance Zero-Latency Triggerbot cho PvP.
 * Tự động đánh ngay khi vũ khí hồi đủ 100% Cooldown và tâm ngắm chạm mục tiêu.
 */
public final class Triggerbot {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private Triggerbot() {}

    public static void tick() {
        ModConfig.TriggerSnapshot cfg = ModConfig.snapshotTrigger();
        ClientPlayerEntity player = mc.player;

        // Kiểm tra điều kiện cơ bản
        if (!cfg.enabled() || player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        // Bắt mục tiêu nằm trong tâm ngắm (Crosshair)
        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) {
            return;
        }

        Entity entity = ((EntityHitResult) hit).getEntity();
        if (!(entity instanceof PlayerEntity target)) {
            return;
        }

        // Bỏ qua nếu đối thủ đã chết, ở chế độ Spectator hoặc đã bị xóa khỏi thế giới
        if (!target.isAlive() || target.isSpectator() || target.isRemoved()) {
            return;
        }

        // Kiểm tra thanh hồi chiêu vũ khí (1.0f = 100% Cooldown)
        if (player.getAttackCooldownProgress(0.0f) >= 1.0f) {
            // Thực hiện tấn công mục tiêu ngay trong tick hiện tại
            mc.interactionManager.attackEntity(player, target);
            player.swingHand(player.getActiveHand());
        }
    }
}
