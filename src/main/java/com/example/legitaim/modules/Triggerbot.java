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

        // Chặn tuyệt đối khi đang ăn uống hoặc mở hòm đồ/inventory
        if (client.player.isUsingItem() || client.currentScreen != null) {
            resetState();
            return;
        }

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
        float cooldown = client.player.getAttackCooldownProgress(0.5f);
        
        if (cooldown >= 0.95f) {
            if (!hasFirstHitOccurred) {
                if (firstHitDelayTicks == 0) {
                    firstHitDelayTicks = 1 + RANDOM.nextInt(3); // Delay siêu ngắn cho phát đánh đầu tiên
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
                // Kết hợp phân phối Poisson và Lookup Table cho độ trễ click chuẩn con người
                double poissonValue = -Math.log(1.0 - RANDOM.nextDouble()) * 1.5;
                int lutDelay = HumanMotionLUT.getClickDelay(comboCounter + RANDOM.nextInt(3));
                nextActionDelay = Math.max(1, (int) Math.round(poissonValue) + lutDelay);
            }

            if (currentTickCount < nextActionDelay) {
                return;
            }

            currentTickCount = 0;
            nextActionDelay = 0;
            comboCounter++;

            // Tỷ lệ đánh hụt động (Dynamic Miss Rate) để đánh lừa AI Anti-Cheat
            float dynamicMissRate = Math.min(0.32f, 0.18f + (comboCounter * 0.015f));
            if (RANDOM.nextFloat() < dynamicMissRate) {
                float missYaw = (float) ((RANDOM.nextGaussian() * 3.5) + ((RANDOM.nextFloat() - 0.5f) * 9.0f));
                float missPitch = (float) ((RANDOM.nextGaussian() * 1.8) + ((RANDOM.nextFloat() - 0.5f) * 4.5f));

                client.player.setYaw(client.player.getYaw() + missYaw);
                client.player.setPitch(MathHelper.clamp(client.player.getPitch() + missPitch, -90.0f, 90.0f));
                
                client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                
                lastYaw = client.player.getYaw();
                lastPitch = client.player.getPitch();
                return;
            }

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
