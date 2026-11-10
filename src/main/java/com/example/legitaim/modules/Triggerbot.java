package com.example.legitaim.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

public class Triggerbot {

    public static boolean enabled = false;
    private static final Random RANDOM = new Random();

    // Biến quản lý khoảng thời gian giữa các lần click (Click Interval & Entropy)
    private static int nextActionDelay = 0;
    private static int currentTickCount = 0;
    private static int comboCounter = 0;

    public static void onClientTick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null || client.interactionManager == null) {
            resetState();
            return;
        }

        // Kiểm tra loại vũ khí hợp lệ (Kiếm / Rìu)
        String itemId = Registries.ITEM
                .getId(client.player.getMainHandStack().getItem())
                .getPath();

        boolean validWeapon = itemId.endsWith("_sword") || itemId.endsWith("_axe");
        if (!validWeapon) {
            resetState();
            return;
        }

        if (!(client.crosshairTarget instanceof EntityHitResult hit)) {
            resetState();
            return;
        }

        Entity target = hit.getEntity();
        if (!(target instanceof LivingEntity living) || !living.isAlive() || living == client.player) {
            resetState();
            return;
        }

        // Phân tích Combat Pattern & Attack Timing Distribution:
        // Đảm bảo thời điểm tấn công phụ thuộc vào nhịp cooldown hoàn tất cộng với độ trễ ngẫu nhiên có phân phối cao (High Entropy)
        float cooldown = client.player.getAttackCooldownProgress(0.5f);
        
        if (cooldown >= 0.95f) {
            currentTickCount++;

            if (nextActionDelay == 0) {
                // Tạo độ trễ ngẫu nhiên mô phỏng phản xạ thần kinh con người trước khi nhấp chuột (tránh CPS đều đặn)
                // Phân phối lệch để tập trung vào 1-3 ticks chờ
                nextActionDelay = 1 + (RANDOM.nextDouble() < 0.7 ? RANDOM.nextInt(3) : RANDOM.nextInt(5));
            }

            if (currentTickCount < nextActionDelay) {
                return; // Đang trong khoảng trễ ngẫu nhiên, chưa tấn công vội
            }

            // Reset bộ đếm cho chu kỳ tiếp theo
            currentTickCount = 0;
            nextActionDelay = 0;
            comboCounter++;

            // Nâng cấp tỷ lệ đánh hụt liên kết giữa Aim và Triggerbot (Tăng lên 18% ở các chuỗi combo dài để đánh lừa thuật toán mẫu hành vi)
            float missProbability = Math.min(0.25f, 0.15f + (comboCounter * 0.01f));
            if (RANDOM.nextFloat() < missProbability) {
                // Lia lệch tâm giả lập tình huống mất kiểm soát góc nhìn khi combat dồn dập
                float missYaw = (RANDOM.nextFloat() - 0.5f) * 10.0f;
                float missPitch = (RANDOM.nextFloat() - 0.5f) * 5.0f;

                client.player.setYaw(client.player.getYaw() + missYaw);
                client.player.setPitch(MathHelper.clamp(client.player.getPitch() + missPitch, -90.0f, 90.0f));
                
                client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                return;
            }

            // Thực hiện tấn công hợp lệ
            client.interactionManager.attackEntity(client.player, target);
            client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        } else {
            // Nếu hồi chiêu chưa tới, hạ bớt bộ đếm combo nếu người chơi hụt nhịp
            if (cooldown < 0.2f && comboCounter > 0) {
                comboCounter = Math.max(0, comboCounter - 1);
            }
        }
    }

    private static void resetState() {
        nextActionDelay = 0;
        currentTickCount = 0;
        if (RANDOM.nextFloat() < 0.1f) {
            comboCounter = 0; // Giảm dần chuỗi combo khi không nhìn vào mục tiêu
        }
    }
}
