package com.example.legitaim.modules;

import com.example.legitaim.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public final class Triggerbot {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // Giới hạn tầm đánh tối đa chuẩn Vanilla (3.0 khối). Đánh mọi khoảng cách từ 0.0m -> 3.0m.
    private static final double MAX_ATTACK_RANGE = 3.0D;

    private Triggerbot() {}

    public static void tick() {
        ModConfig.TriggerSnapshot cfg = ModConfig.snapshotTrigger();
        ClientPlayerEntity player = mc.player;

        if (!cfg.enabled() || player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        // 1. Chỉ kích hoạt khi cầm Kiếm hoặc Rìu
        ItemStack mainHandStack = player.getMainHandStack();
        if (!(mainHandStack.getItem() instanceof SwordItem) && !(mainHandStack.getItem() instanceof AxeItem)) {
            return;
        }

        // 2. Bắt mục tiêu trong tâm ngắm
        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) {
            return;
        }

        Entity entity = ((EntityHitResult) hit).getEntity();
        if (!(entity instanceof PlayerEntity target)) {
            return;
        }

        if (!target.isAlive() || target.isSpectator() || target.isRemoved()) {
            return;
        }

        // 3. Đánh trong mọi khoảng cách từ 0m đến 3.0m (Không bỏ sót khi đối thủ áp sát < 2.7m)
        double distance = player.distanceTo(target);
        if (distance > MAX_ATTACK_RANGE) {
            return;
        }

        // 4. Đạt 95% Cooldown là vung đòn ngay
        if (player.getAttackCooldownProgress(0.0f) >= 0.95f) {
            mc.interactionManager.attackEntity(player, target);
            player.swingHand(player.getActiveHand());
        }
    }
}
