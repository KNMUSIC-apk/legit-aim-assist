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

import java.util.Random;

public final class Triggerbot {

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random random = new Random();

    // Cấu hình để tạo sự bất định
    private static final double MAX_ATTACK_RANGE = 3.0D;
    private static final double MIN_COOLDOWN_PERCENT = 0.85D; // Cooldown tối thiểu (85%)
    private static final double MAX_COOLDOWN_PERCENT = 0.98D; // Cooldown tối đa (98%)
    private static final int MIN_REACTION_DELAY_MS = 50;    // Độ trễ phản ứng tối thiểu (ms)
    private static final int MAX_REACTION_DELAY_MS = 150;   // Độ trễ phản ứng tối đa (ms)

    // Biến trạng thái để theo dõi mục tiêu và thời gian
    private static PlayerEntity lastTarget = null;
    private static long targetEnterTime = 0L;

    private Triggerbot() {}

    public static void tick() {
        ModConfig.TriggerSnapshot cfg = ModConfig.snapshotTrigger();
        ClientPlayerEntity player = mc.player;

        if (!cfg.enabled() || player == null || mc.world == null || mc.interactionManager == null) {
            resetState();
            return;
        }

        // 1. Chỉ kích hoạt khi cầm Kiếm hoặc Rìu
        ItemStack mainHandStack = player.getMainHandStack();
        if (!(mainHandStack.getItem() instanceof SwordItem) && !(mainHandStack.getItem() instanceof AxeItem)) {
            resetState();
            return;
        }

        // 2. Bắt mục tiêu trong tâm ngắm
        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) {
            resetState();
            return;
        }

        Entity entity = ((EntityHitResult) hit).getEntity();
        if (!(entity instanceof PlayerEntity target)) {
            resetState();
            return;
        }

        if (!target.isAlive() || target.isSpectator() || target.isRemoved()) {
            resetState();
            return;
        }

        // 3. Đánh trong mọi khoảng cách từ 0m đến 3.0m
        double distance = player.distanceTo(target);
        if (distance > MAX_ATTACK_RANGE) {
            resetState();
            return;
        }

        // 4. Logic thêm độ trễ phản ứng khi mục tiêu mới vào tâm ngắm
        long currentTime = System.currentTimeMillis();
        if (target != lastTarget) {
            // Mục tiêu mới, đặt lại thời gian và tạo độ trễ ngẫu nhiên
            lastTarget = target;
            targetEnterTime = currentTime;
            return; // Bỏ qua tick này để mô phỏng thời gian phản ứng
        }

        // Kiểm tra xem đã đủ thời gian phản ứng chưa
        long reactionDelay = MIN_REACTION_DELAY_MS + random.nextInt(MAX_REACTION_DELAY_MS - MIN_REACTION_DELAY_MS + 1);
        if (currentTime - targetEnterTime < reactionDelay) {
            return; // Chưa đủ thời gian phản ứng, chờ thêm
        }

        // 5. Đánh khi cooldown đạt ngưỡng ngẫu nhiên (từ 85% đến 98%)
        double cooldownThreshold = MIN_COOLDOWN_PERCENT + (random.nextDouble() * (MAX_COOLDOWN_PERCENT - MIN_COOLDOWN_PERCENT));
        if (player.getAttackCooldownProgress(0.0f) >= cooldownThreshold) {
            mc.interactionManager.attackEntity(player, target);
            player.swingHand(player.getActiveHand());
            
            // Đặt lại thời gian để tạo độ trễ cho cú đánh tiếp theo
            targetEnterTime = currentTime + random.nextInt(100); // Thêm một chút ngẫu nhiên
        }
    }

    // Hàm hỗ trợ reset trạng thái khi mất mục tiêu
    private static void resetState() {
        lastTarget = null;
        targetEnterTime = 0L;
    }
}
