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
import net.minecraft.util.math.Vec3d;

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

    private static final double MAX_REACH = 3.0D;
    private static final int FIRST_HIT_DELAY_MIN = 10;
    private static final int FIRST_HIT_DELAY_MAX = 30;

    private static int hitCount = 0;
    private static int targetHitsToPause = getRandomPauseThreshold();
    private static long pauseUntilTime = 0L;

    private static PlayerEntity lastTarget = null;
    private static long targetEnterTime = 0L;
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

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) {
            resetState();
            return;
        }

        Entity entity = ((EntityHitResult) hit).getEntity();
        if (!(entity instanceof PlayerEntity target)) {
            resetState();
            return;
        }

        if (!target.isAlive() || target.isSpectator() || target.isRemoved()) {
            resetState();
            return;
        }

        if (player.distanceTo(target) > MAX_REACH) {
            resetState();
            return;
        }

        // ============================================================
        // TRIGGER SAFETY CHECK (Kiểm tra góc lệch tâm chống đánh hụt)
        // ============================================================
        Vec3d targetEye = target.getEyePos();
        Vec3d playerLook = player.getRotationVec(1.0f);
        Vec3d toTarget = targetEye.subtract(player.getEyePos()).normalize();

        double dotProduct = playerLook.dotProduct(toTarget);
        double angleDelta = Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dotProduct))));

        // Nếu tâm đang lệch quá 12 độ, chờ AimAssist lia tới chuẩn xác mới vung kiếm
        if (angleDelta > 12.0) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        if (currentTime < pauseUntilTime) {
            return;
        }

        if (target != lastTarget) {
            lastTarget = target;
            hitCount = 0;
            targetHitsToPause = getRandomPauseThreshold();
            int firstDelay = FIRST_HIT_DELAY_MIN + random.nextInt(FIRST_HIT_DELAY_MAX - FIRST_HIT_DELAY_MIN + 1);
            targetEnterTime = currentTime + firstDelay;
            return;
        }

        if (currentTime < targetEnterTime) {
            return;
        }

        float cooldown = player.getAttackCooldownProgress(0.0f);
        if (cooldown < 0.92f) {
            return;
        }

        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {

            // W-Tap mượt khi ở dưới đất, giữ nguyên đà khi nhảy Crit
            if (player.isOnGround() && player.isSprinting()) {
                player.setSprinting(false);
                needWTapReset = true;
            }

            invokeDoAttack();

            hitCount++;

            if (hitCount >= targetHitsToPause) {
                int pauseDuration = 100 + random.nextInt(80); // 100ms - 180ms
                pauseUntilTime = currentTime + pauseDuration;

                hitCount = 0;
                targetHitsToPause = getRandomPauseThreshold();
            }
        }
    }

    private static int getRandomPauseThreshold() {
        return 5 + random.nextInt(4);
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
        lastTarget = null;
        targetEnterTime = 0L;
        needWTapReset = false;
        hitCount = 0;
        pauseUntilTime = 0L;
    }
}
