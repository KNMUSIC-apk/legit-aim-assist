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
    private static long lastCombatTime = 0;

    private static float lastYaw = 0.0f;
    private static float lastPitch = 0.0f;

    public static void onClientTick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null || client.interactionManager == null) {
            resetState();
            return;
        }

        // Chặn khi đang ăn uống hoặc mở giao diện
        if (client.player.isUsingItem() || client.currentScreen != null) {
            resetState();
            return;
        }

        // Kiểm tra vận tốc vẩy chuột tay
        float playerVelocityDelta = Math.abs(client.player.getYaw() - lastYaw) + Math.abs(client.player.getPitch() - lastPitch);
        lastYaw = client.player.getYaw();
        lastPitch = client.player.getPitch();

        if (playerVelocityDelta > 16.0f) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCombatTime > 10000) {
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
        
        // Kiểm tra hồi chiêu vũ khí (Attack Cooldown)
        float cooldown = client.player.getAttackCooldownProgress(0.5f);
        boolean isFallingForCrit = client.player.fallDistance > 0.0f && !client.player.isOnGround() && !client.player.isClimbing();
        float requiredCooldown = isFallingForCrit ? 0.88f : 0.94f;

        if (cooldown >= requiredCooldown) {
            currentTickCount++;

            if (nextActionDelay == 0) {
                // Tối ưu hóa độ trễ siêu ngắn để tăng khả năng tap mượt và bám sát nhịp đánh
                double poissonValue = -Math.log(1.0 - RANDOM.nextDouble()) * 0.8;
                int lutDelay = HumanMotionLUT.getClickDelay(comboCounter + RANDOM.nextInt(2));
                int tapBoost = isFallingForCrit ? -1 : 0;
                
                nextActionDelay = Math.max(0, (int) Math.round(poissonValue) + (lutDelay / 2) + tapBoost);
            }

            if (currentTickCount < nextActionDelay) {
                return;
            }

            currentTickCount = 0;
            nextActionDelay = 0;
            comboCounter++;

            // Giảm đáng kể tỷ lệ đánh hụt động để tập trung vào các phát đánh chất lượng cao
            float dynamicMissRate = Math.min(0.12f, 0.05f + (comboCounter * 0.005f));
            if (RANDOM.nextFloat() < dynamicMissRate) {
                float missYaw = (float) ((RANDOM.nextGaussian() * 1.5) + ((RANDOM.nextFloat() - 0.5f) * 3.0f));
                float missPitch = (float) ((RANDOM.nextGaussian() * 0.8) + ((RANDOM.nextFloat() - 0.5f) * 1.5f));

                client.player.setYaw(client.player.getYaw() + missYaw);
                client.player.setPitch(MathHelper.clamp(client.player.getPitch() + missPitch, -90.0f, 90.0f));
                
                client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                
                lastYaw = client.player.getYaw();
                lastPitch = client.player.getPitch();
                return;
            }

            // Tấn công trực diện với độ chuẩn xác cao
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
    }
}
