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

    // Biến lưu trữ góc nhìn trước để tính vận tốc
    private static float lastYaw = 0.0f;
    private static float lastPitch = 0.0f;

    public static void onClientTick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null || client.interactionManager == null) {
            resetState();
            return;
        }

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
                    firstHitDelayTicks = 1 + RANDOM.nextInt(3);
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
                nextActionDelay = 1 + (RANDOM.nextDouble() < 0.7 ? RANDOM.nextInt(2) : RANDOM.nextInt(5));
            }

            if (currentTickCount < nextActionDelay) {
                return;
            }

            currentTickCount = 0;
            nextActionDelay = 0;
            comboCounter++;

            float dynamicMissRate = Math.min(0.28f, 0.16f + (comboCounter * 0.012f));
            if (RANDOM.nextFloat() < dynamicMissRate) {
                float missYaw = (float) ((RANDOM.nextGaussian() * 3.0) + ((RANDOM.nextFloat() - 0.5f) * 8.0f));
                float missPitch = (float) ((RANDOM.nextGaussian() * 1.5) + ((RANDOM.nextFloat() - 0.5f) * 4.0f));

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
