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

import java.lang.reflect.Method;
import java.util.Random;

public final class Triggerbot {

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random random = new Random();
    private static Method doAttackMethod = null;

    static {
        try {
            doAttackMethod = MinecraftClient.class.getDeclaredMethod("doAttack");
            doAttackMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            for (Method method : MinecraftClient.class.getDeclaredMethods()) {
                if (method.getReturnType() == void.class && method.getParameterCount() == 0) {
                    if (method.getName().equals("doAttack") || method.getName().equals("method_1536")) {
                        doAttackMethod = method;
                        doAttackMethod.setAccessible(true);
                        break;
                    }
                }
            }
        }
    }

    private static int hitCount = 0;
    private static int targetHitsToPause = getRandomPauseThreshold();
    private static long pauseUntilTime = 0L;
    
    private static int originalSwordSlot = -1;
    private static int lostTargetTicks = 0; // Bộ đếm chống kẹt slot

    private Triggerbot() {}

    public static void tick() {
        ModConfig.TriggerSnapshot cfg = ModConfig.snapshotTrigger();
        ClientPlayerEntity player = mc.player;

        if (!cfg.enabled() || player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) {
            resetState(player);
            return;
        }

        if (player.isUsingItem()) {
            return;
        }

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) {
            handleLostTarget(player);
            return;
        }

        Entity entity = ((EntityHitResult) hit).getEntity();
        if (!(entity instanceof PlayerEntity target) || !target.isAlive() || target.isSpectator() || target.isCreative() || target.isRemoved()) {
            handleLostTarget(player);
            return;
        }

        // Đã tìm thấy mục tiêu hợp lệ, reset bộ đếm mất mục tiêu
        lostTargetTicks = 0;

        // ============================================================
        // AUTO-AXE SHIELD BREAKER (Safe Mode)
        // ============================================================
        boolean targetIsBlocking = target.isBlocking();
        int currentSlot = player.getInventory().selectedSlot;

        if (targetIsBlocking) {
            if (player.getMainHandStack().getItem() instanceof SwordItem) {
                int axeSlot = findAxeSlot(player);
                if (axeSlot != -1 && axeSlot != currentSlot) {
                    originalSwordSlot = currentSlot;
                    player.getInventory().selectedSlot = axeSlot;
                    return; 
                }
            }
        } else {
            if (originalSwordSlot != -1) {
                if (player.getMainHandStack().getItem() instanceof AxeItem) {
                    player.getInventory().selectedSlot = originalSwordSlot;
                }
                originalSwordSlot = -1;
                return;
            }
        }

        // Người chơi tự lăn chuột sang vũ khí khác -> Hủy lưu slot
        if (originalSwordSlot != -1 && !(player.getMainHandStack().getItem() instanceof AxeItem)) {
            originalSwordSlot = -1;
        }

        ItemStack mainHandStack = player.getMainHandStack();
        boolean isWeapon = mainHandStack.getItem() instanceof SwordItem
                        || mainHandStack.getItem() instanceof AxeItem;
        if (!isWeapon) {
            return; 
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime < pauseUntilTime) {
            return;
        }

        float humanizedCooldownThreshold = 0.93f + (random.nextFloat() * 0.06f);
        float cooldown = player.getAttackCooldownProgress(0.0f);
        if (cooldown < humanizedCooldownThreshold) {
            return;
        }

        // 8% Miss Chance
        if (random.nextInt(100) < 8) {
            player.swingHand(Hand.MAIN_HAND);
            pauseUntilTime = currentTime + (120 + random.nextInt(100));
            return;
        }

        invokeDoAttack();
        hitCount++;

        if (hitCount >= targetHitsToPause) {
            pauseUntilTime = currentTime + (100 + random.nextInt(120));
            hitCount = 0;
            targetHitsToPause = getRandomPauseThreshold();
        }
    }

    private static void handleLostTarget(ClientPlayerEntity player) {
        if (originalSwordSlot != -1) {
            lostTargetTicks++;
            // Nếu mất mục tiêu quá 10 ticks (0.5s), tự động trả về kiếm để tránh kẹt
            if (lostTargetTicks > 10) {
                resetAutoAxe(player);
                lostTargetTicks = 0;
            }
        }
    }

    private static int findAxeSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                return i;
            }
        }
        return -1;
    }

    private static void resetAutoAxe(ClientPlayerEntity player) {
        if (originalSwordSlot != -1) {
            if (player != null && player.getMainHandStack().getItem() instanceof AxeItem) {
                player.getInventory().selectedSlot = originalSwordSlot;
            }
            originalSwordSlot = -1;
        }
    }

    private static int getRandomPauseThreshold() {
        return 5 + random.nextInt(5);
    }

    private static void invokeDoAttack() {
        if (doAttackMethod != null) {
            try {
                doAttackMethod.invoke(mc);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            if (mc.interactionManager != null && mc.crosshairTarget instanceof EntityHitResult entityHit) {
                mc.interactionManager.attackEntity(mc.player, entityHit.getEntity());
                if (mc.player != null) {
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            }
        }
    }

    private static void resetState(ClientPlayerEntity player) {
        hitCount = 0;
        pauseUntilTime = 0L;
        lostTargetTicks = 0;
        resetAutoAxe(player);
    }
}
