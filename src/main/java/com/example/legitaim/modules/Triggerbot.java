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

    private static int nextActionDelay = 0;
    private static int currentTickCount = 0;
    private static int comboCounter = 0;

    // Quản lý delay cho phát đánh đầu tiên và thời gian reset (10 giây = 200 ticks)
    private static boolean hasFirstHitOccurred = false;
    private static int firstHitDelayTicks = 0;
    private static int firstHitCounter = 0;
    private static long lastCombatTime = 0;

    public static void onClientTick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null || client.interactionManager == null) {
            resetState();
            return;
        }

        // CHẶN KHI ĐANG ĂN UỐNG HOẶC MỞ KHO ĐỒ (Inventory / Container Screen)
        if (client.player.isUsingItem() || client.currentScreen != null) {
            resetState();
            return;
        }

        // Kiểm tra thời gian không giao tranh (10 giây = 200 ticks hệ thống)
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCombatTime > 10000) {
            hasFirstHitOccurred = false; // Reset trạng thái phát đánh đầu tiên sau 10 giây im ắng
            comboCounter = 0;
        }

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

        // Cập nhật thời điểm có mục tiêu trong tầm ngắm
        lastCombatTime = currentTime;

        float cooldown = client.player.getAttackCooldownProgress(0.5f);
        
        if (cooldown >= 0.95f) {
            // XỬ LÝ DELAY CHO PHÁT ĐÁNH ĐẦU TIÊN (Khi mới bắt đầu lia trúng địch)
            if (!hasFirstHitOccurred) {
                if (firstHitDelayTicks == 0) {
                    // Random độ trễ từ 4 đến 9 ticks trước khi tung đòn đầu tiên để mô phỏng thời gian phản xạ nhận thức
                    firstHitDelayTicks = 4 + RANDOM.nextInt(6);
                    firstHitCounter = 0;
                }

                firstHitCounter++;
                if (firstHitCounter < firstHitDelayTicks) {
                    return; // Đang chờ hết thời gian delay phát đánh đầu tiên
                }

                hasFirstHitOccurred = true;
                firstHitDelayTicks = 0;
                firstHitCounter = 0;
            }

            currentTickCount++;

            if (nextActionDelay == 0) {
                nextActionDelay = 1 + (RANDOM.nextDouble() < 0.7 ? RANDOM.nextInt(3) : RANDOM.nextInt(4));
            }

            if (currentTickCount < nextActionDelay) {
                return;
            }

            currentTickCount = 0;
            nextActionDelay = 0;
            comboCounter++;

            // Tỷ lệ đánh hụt ngẫu nhiên liên kết Aim & Triggerbot
            float missProbability = Math.min(0.22f, 0.12f + (comboCounter * 0.01f));
            if (RANDOM.nextFloat() < missProbability) {
                float missYaw = (RANDOM.nextFloat() - 0.5f) * 8.0f;
                float missPitch = (RANDOM.nextFloat() - 0.5f) * 4.0f;

                client.player.setYaw(client.player.getYaw() + missYaw);
                client.player.setPitch(MathHelper.clamp(client.player.getPitch() + missPitch, -90.0f, 90.0f));
                
                client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                return;
            }

            client.interactionManager.attackEntity(client.player, target);
            client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        } else {
            if (cooldown < 0.2f && comboCounter > 0) {
                comboCounter = Math.max(0, comboCounter - 1);
            }
        }
    }

    private static void resetState() {
        nextActionDelay = 0;
        currentTickCount = 0;
        firstHitDelayTicks = 0;
        firstHitCounter = 0;
        // Không reset hoàn toàn hasFirstHitOccurred ngay lập tức ở đây để tránh giật cục khi nhấc tâm ngắm ra khỏi người địch trong tích tắc
    }
}
