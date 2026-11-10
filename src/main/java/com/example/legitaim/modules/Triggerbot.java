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

    // Quản lý độ trễ phát đánh đầu tiên và hệ thống reset sau 10 giây
    private static boolean hasFirstHitOccurred = false;
    private static int firstHitDelayTicks = 0;
    private static int firstHitCounter = 0;
    private static long lastCombatTime = 0;

    public static void onClientTick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null || client.interactionManager == null) {
            resetState();
            return;
        }

        // Chặn tuyệt đối khi đang ăn uống hoặc mở giao diện kho đồ (tránh bị hệ thống packet check bắt)
        if (client.player.isUsingItem() || client.currentScreen != null) {
            resetState();
            return;
        }

        // Reset trạng thái sau 10 giây (200 ticks) không giao tranh để mô phỏng sự thả lỏng tâm lý
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCombatTime > 10000) {
            hasFirstHitOccurred = false;
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

        lastCombatTime = currentTime;
        float cooldown = client.player.getAttackCooldownProgress(0.5f);
        
        if (cooldown >= 0.95f) {
            // Xử lý độ trễ nhận thức cho cú đánh đầu tiên khi vừa tiếp cận địch
            if (!hasFirstHitOccurred) {
                if (firstHitDelayTicks == 0) {
                    firstHitDelayTicks = 5 + RANDOM.nextInt(7); // Trễ từ 5 đến 11 ticks cho lần đánh mở màn
                    firstHitCounter = 0;
                }

                firstHitCounter++;
                if (firstHitCounter < firstHitDelayTicks) {
                    return;
                }

                hasFirstHitOccurred = true;
                firstHitDelayTicks = 0;
                firstHitCounter = 0;
            }

            currentTickCount++;

            if (nextActionDelay == 0) {
                // Phân phối entropy click ngẫu nhiên mô phỏng sự dao động CPS thần kinh con người
                nextActionDelay = 1 + (RANDOM.nextDouble() < 0.65 ? RANDOM.nextInt(3) : RANDOM.nextInt(6));
            }

            if (currentTickCount < nextActionDelay) {
                return;
            }

            currentTickCount = 0;
            nextActionDelay = 0;
            comboCounter++;

            // DYNAMIC DATA POISONING / HUMAN MISS RATE:
            // Tỷ lệ đánh hụt tăng dần theo chuỗi combo (từ 16% lên tối đa 28%) để qua mặt AI phân tích độ chính xác tuyệt đối (Hit Rate Consistency)
            float dynamicMissRate = Math.min(0.28f, 0.16f + (comboCounter * 0.012f));
            if (RANDOM.nextFloat() < dynamicMissRate) {
                float missYaw = (float) ((RANDOM.nextGaussian() * 3.5) + ((RANDOM.nextFloat() - 0.5f) * 9.0f));
                float missPitch = (float) ((RANDOM.nextGaussian() * 2.0) + ((RANDOM.nextFloat() - 0.5f) * 5.0f));

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
    }
}
