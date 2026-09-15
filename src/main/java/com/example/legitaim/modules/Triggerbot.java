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

        // ANTI-CHEAT: Không bao giờ đánh khi đang ăn táo, uống thuốc, hoặc giơ khiên
        if (player.isUsingItem()) {
            return;
        }

        ItemStack mainHandStack = player.getMainHandStack();
        boolean isWeapon = mainHandStack.getItem() instanceof SwordItem
                        || mainHandStack.getItem() instanceof AxeItem;
        if (!isWeapon) {
            resetState();
            return;
        }

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

        // ANTI-CHEAT: Randomize Cooldown (0.93f - 0.99f)
        // Giả lập sai số phản xạ của con người, không phải lúc nào cũng click ở đúng 1 tick cố định
        float humanizedCooldownThreshold = 0.93f + (random.nextFloat() * 0.06f);
        float cooldown = player.getAttackCooldownProgress(0.0f);
        if (cooldown < humanizedCooldownThreshold) {
            return;
        }

        if (player.isOnGround() && player.isSprinting()) {
            player.setSprinting(false);
            needWTapReset = true;
        }

        invokeDoAttack();
        hitCount++;

        if (hitCount >= targetHitsToPause) {
            // ANTI-CHEAT: Độ trễ ngẫu nhiên mô phỏng việc khựng tay hoặc di chuyển chuột lại
            pauseUntilTime = currentTime + (100 + random.nextInt(120)); // Nghỉ 100ms - 220ms
            hitCount = 0;
            targetHitsToPause = getRandomPauseThreshold();
        }
    }

    private static int getRandomPauseThreshold() {
        return 5 + random.nextInt(5); // 5 đến 9 đòn
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
