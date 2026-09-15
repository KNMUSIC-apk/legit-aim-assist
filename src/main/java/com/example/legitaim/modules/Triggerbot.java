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

    // Ngưỡng khoảng cách vàng để Out-range đối thủ (Vanilla Reach tối đa là 3.0m)
    private static final double MIN_ATTACK_RANGE = 2.70D;
    private static final double MAX_ATTACK_RANGE = 3.0D;

    private Triggerbot() {}

    public static void tick() {
        ModConfig.TriggerSnapshot cfg = ModConfig.snapshotTrigger();
        ClientPlayerEntity player = mc.player;

        if (!cfg.enabled() || player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        // 1. Chỉ kích hoạt khi đang cầm Kiếm (Sword) hoặc Rìu (Axe)
        ItemStack mainHandStack = player.getMainHandStack();
        if (!(mainHandStack.getItem() instanceof SwordItem) && !(mainHandStack.getItem() instanceof AxeItem)) {
            return;
        }

        // 2. Bắt mục tiêu trong crosshair
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

        // 3. Tính toán khoảng cách (Distance Check) để đánh Spacing tối ưu
        double distance = player.distanceTo(target);
        if (distance < MIN_ATTACK_RANGE || distance > MAX_ATTACK_RANGE) {
            // Nếu quá xa (> 3.0m) hoặc quá gần (< 2.7m), giữ nhịp di chuyển để căn Spacing
            return;
        }

        // 4. Kiểm tra Cooldown hồi vũ khí (tối ưu 0.95f để ra đòn ngay trước khi đạt 100% nhằm ưu tiên gán Knockback)
        if (player.getAttackCooldownProgress(0.0f) >= 0.95f) {
            // 5. Thực hiện đòn đánh theo chuẩn luồng Input của game (tránh bị desync packet / bị khựng)
            mc.interactionManager.attackEntity(player, target);
            player.swingHand(player.getActiveHand());
        }
    }
}
