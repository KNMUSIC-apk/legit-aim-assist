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
    private static boolean needWTapReset = false;

    private Triggerbot() {}

    public static void tick() {
        ModConfig.TriggerSnapshot cfg = ModConfig.snapshotTrigger();
        ClientPlayerEntity player = mc.player;

        if (!cfg.enabled() || player == null || mc.world == null || mc.interactionManager == null) {
            resetState();
            return;
        }

        if (needWTapReset) {
            if (player.isOnGround() && mc.options.forwardKey.isPressed()) {
                player.setSprinting(true);
            }
            needWTapReset = false;
        }

        ItemStack mainHandStack = player.getMainHandStack();
        boolean isWeapon = mainHandStack.getItem() instanceof SwordItem
                        || mainHandStack.getItem() instanceof AxeItem;
        if (!isWeapon) {
            resetState();
            return;
        }

        // TỐI ƯU REACH: Sử dụng trực tiếp hệ thống Raycast gốc của Minecraft.
        // Chỉ cần Entity nằm trong crosshairTarget (chuẩn bounding box và góc ngắm) là cho phép đánh.
        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) {
            return;
        }

        Entity entity = ((EntityHitResult) hit).getEntity();
        if (!(entity instanceof PlayerEntity target) || !target.isAlive() || target.isSpectator() || target.isRemoved()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime < pauseUntilTime) {
            return;
        }

        // TỐI ƯU COOLDOWN: Đảm bảo đánh đúng nhịp (0.92f - 1.0f) không bị delay thêm tick nào.
        float cooldown = player.getAttackCooldownProgress(0.0f);
        if (cooldown < 0.95f) { // 0.95f để đảm bảo max sát thương và knockback
            return;
        }

        // TUNG ĐÒN NGAY LẬP TỨC (Không cần kiểm tra targetEnterTime hay angleDelta)
        if (player.isOnGround() && player.isSprinting()) {
            player.setSprinting(false);
            needWTapReset = true;
        }

        invokeDoAttack();
        hitCount++;

        // Nghỉ 1 nhịp siêu ngắn sau 6-9 hits để làm mới chuỗi combo
        if (hitCount >= targetHitsToPause) {
            pauseUntilTime = currentTime + (80 + random.nextInt(70)); // Nghỉ 80ms - 150ms
            hitCount = 0;
            targetHitsToPause = getRandomPauseThreshold();
        }
    }

    private static int getRandomPauseThreshold() {
        return 6 + random.nextInt(4); // 6 đến 9 đòn
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
                    mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                }
            }
        }
    }

    private static void resetState() {
        needWTapReset = false;
        hitCount = 0;
        pauseUntilTime = 0L;
    }
}
