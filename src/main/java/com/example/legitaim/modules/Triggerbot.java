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

    private static boolean hasFirstHitOccurred = false;
    private static int firstHitDelayTicks = 0;
    private static int firstHitCounter = 0;
    private static long lastCombatTime = 0;

    private static float lastYaw = 0.0f;
    private static float lastPitch = 0.0f;

    public static void onClientTick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null || client.interactionManager == null) {
            resetState();
            return;
        }

        // Chặn tuyệt đối khi đang ăn uống, mở hòm đồ hoặc dịch chuyển giao diện
        if (client.player.isUsingItem() || client.currentScreen != null) {
            resetState();
            return;
        }

        // Kiểm tra vận tốc vẩy chuột tay
        float playerVelocityDelta = Math.abs(client.player.getYaw() - lastYaw) + Math.abs(client.player.getPitch() - lastPitch);
        lastYaw = client.player.getYaw();
        lastPitch = client.player.getPitch();

        if (playerVelocityDelta > 15.0f) {
            return;
        }

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
        
        // Kiểm tra trạng thái hồi chiêu vũ khí (Attack Cooldown)
        float cooldown = client.player.getAttackCooldownProgress(0.5f);
        
        // --- PRO-PLAYER COMBAT RHYTHM ---
        // Nếu người chơi đang trong trạng thái rơi (nhảy chém / crit), tối ưu hóa độ nhạy tấn công
        boolean isFallingForCrit = client.player.fallDistance > 0.0f && !client.player.isOnGround() && !client.player.isClimbing();
        float requiredCooldown = isFallingForCrit ? 0.90f : 0.95f; // Rơi chém có thể linh hoạt sớm hơn một chút để bắt nhịp crit

        if (cooldown >= requiredCooldown) {
            if (!hasFirstHitOccurred) {
                if (firstHitDelayTicks == 0) {
                    firstHitDelayTicks = 1 + RANDOM.nextInt(2); // Phản xạ cực nhanh cho nhát đánh mở màn
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
                // Mô phỏng nhịp điệu tay chuyên nghiệp: kết hợp Poisson entropy và LUT cadence
                double poissonValue = -Math.log(1.0 - RANDOM.nextDouble()) * 1.2;
                int lutDelay = HumanMotionLUT.getClickDelay(comboCounter + RANDOM.nextInt(3));
                
                // Nếu đang nhảy chém (crit), rút ngắn một chút độ trễ để tạo cảm giác dồn dập, chuyên nghiệp
                int critBonus = isFallingForCrit ? -1 : 0;
                nextActionDelay = Math.max(1, (int) Math.round(poissonValue) + lutDelay + critBonus);
            }

            if (currentTickCount < nextActionDelay) {
                return;
            }

            currentTickCount = 0;
            nextActionDelay = 0;
            comboCounter++;

            // Tỷ lệ đánh hụt động (Dynamic Miss Rate) được tinh chỉnh mượt mà theo chuỗi combo
            float dynamicMissRate = Math.min(0.28f, 0.14f + (comboCounter * 0.012f));
            if (RANDOM.nextFloat() < dynamicMissRate) {
                float missYaw = (float) ((RANDOM.nextGaussian() * 3.0) + ((RANDOM.nextFloat() - 0.5f) * 7.5f));
                float missPitch = (float) ((RANDOM.nextGaussian() * 1.5) + ((RANDOM.nextFloat() - 0.5f) * 4.0f));

                client.player.setYaw(client.player.getYaw() + missYaw);
                client.player.setPitch(MathHelper.clamp(client.player.getPitch() + missPitch, -90.0f, 90.0f));
                
                client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                
                lastYaw = client.player.getYaw();
                lastPitch = client.player.getPitch();
                return;
            }

            // Thực hiện tấn công mượt mà
            client.interactionManager.attackEntity(client.player, target);
            client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            
            lastYaw = client.player.getYaw();
            lastPitch = client.player.getPitch();
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
