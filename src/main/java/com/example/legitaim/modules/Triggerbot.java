package com.example.legitaim.modules;

import com.example.legitaim.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.Random;

public final class Triggerbot {

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random random = new Random();
    
    // Tầm đánh an toàn để bypass Anticheat check reach post-packet
    private static final double MAX_ATTACK_RANGE = 2.95D; 
    
    // Biến đếm delay ngẫu nhiên giữa các đòn đánh
    private static int attackDelayTicks = 0;

    private Triggerbot() {}

    public static void tick() {
        ModConfig.TriggerSnapshot cfg = ModConfig.snapshotTrigger();
        ClientPlayerEntity player = mc.player;

        if (!cfg.enabled() || player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        // Đếm lùi delay ngẫu nhiên nếu có
        if (attackDelayTicks > 0) {
            attackDelayTicks--;
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

        // 3. Giới hạn khoảng cách an toàn (2.95m) tránh lệch vị trí giữa Client và Server
        double distance = player.distanceTo(target);
        if (distance > MAX_ATTACK_RANGE) {
            return;
        }

        // 4. Phải đạt đủ 100% Cooldown (1.0f) mới tấn công để tránh check Post-Attack Cooldown
        if (player.getAttackCooldownProgress(0.5f) >= 1.0f) {
            // Thực hiện vung tay và gửi packet đánh chuẩn thứ tự Vanilla
            player.swingHand(Hand.MAIN_HAND);
            mc.interactionManager.attackEntity(player, target);

            // Thêm ngẫu nhiên 0 đến 1 tick delay cho đòn đánh tiếp theo để giả lập phản xạ người chơi
            attackDelayTicks = random.nextInt(2); 
        }
    }
}
