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

    // Reflection bypass private access doAttack()
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

    // ============================================================
    // 1. CONFIG PVP
    // ============================================================
    private static final double MAX_REACH = 3.0D;
    private static final int FIRST_HIT_DELAY_MIN = 15;
    private static final int FIRST_HIT_DELAY_MAX = 35;

    // ============================================================
    // 2. PRO PVP PAUSE SYSTEM (Nghỉ 1 nhịp sau 5-8 hits)
    // ============================================================
    private static int hitCount = 0;
    private static int targetHitsToPause = getRandomPauseThreshold();
    private static long pauseUntilTime = 0L;

    // ============================================================
    // 3. STATE MACHINE & W-TAP CONTROL
    // ============================================================
    private static PlayerEntity lastTarget = null;
    private static long targetEnterTime = 0L;
    private static boolean needWTapReset = false;

    private Triggerbot() {}

    public static void tick() {
        ModConfig.TriggerSnapshot cfg = ModConfig.snapshotTrigger();
        ClientPlayerEntity player = mc.player;

        // --- Reset trạng thái ---
        if (!cfg.enabled() || player == null || mc.world == null || mc.interactionManager == null) {
            resetState();
            return;
        }

        // --- Xử lý W-Tap khôi phục Sprint an toàn ---
        if (needWTapReset) {
            if (player.isOnGround() && mc.options.forwardKey.isPressed()) {
                player.setSprinting(true);
            }
            needWTapReset = false;
        }

        // ============================================================
        // WEAPON FILTER
        // ============================================================
        ItemStack mainHandStack = player.getMainHandStack();
        boolean isWeapon = mainHandStack.getItem() instanceof SwordItem
                        || mainHandStack.getItem() instanceof AxeItem;
        if (!isWeapon) {
            resetState();
            return;
        }

        // ============================================================
        // TARGET CHECK
        // ============================================================
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

        // Kiểm tra khoảng cách
        if (player.distanceTo(target) > MAX_REACH) {
            resetState();
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Kiểm tra nhịp nghỉ 5-8 đòn
        if (currentTime < pauseUntilTime) {
            return;
        }

        // ============================================================
        // REACTION DELAY (Đòn đầu tiên)
        // ============================================================
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

        // ============================================================
        // ATTACK COOLDOWN CHECK
        // ============================================================
        float cooldown = player.getAttackCooldownProgress(0.0f);
        if (cooldown < 0.92f) {
            return;
        }

        // ============================================================
        // EXECUTE ATTACK & SMOOTH CRIT / W-TAP
        // ============================================================
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {

            // CHỈ W-TAP KHI Ở TRÊN ĐẤT: Tránh làm giật/khựng đà khi đang nhảy Crit
            if (player.isOnGround() && player.isSprinting()) {
                player.setSprinting(false);
                needWTapReset = true;
            }

            // Gọi doAttack qua Reflection
            invokeDoAttack();

            hitCount++;

            // Kiểm tra ngưỡng hoãn 1 nhịp (5-8 hits)
            if (hitCount >= targetHitsToPause) {
                int pauseDuration = 110 + random.nextInt(90); // 110ms - 200ms
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
