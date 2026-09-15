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

    // Cache lại Reflection Method để bypass private access doAttack()
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
    // 1. PVP REACH RANGE (Chuẩn PvP Sword 3.0 blocks)
    // ============================================================
    private static final double MAX_REACH = 3.0D;

    // ============================================================
    // 2. REACTION DELAY (Delay phản xạ cú đầu tiên: 15-45ms cực nhạy)
    // ============================================================
    private static final int FIRST_HIT_DELAY_MIN = 15;
    private static final int FIRST_HIT_DELAY_MAX = 45;

    // ============================================================
    // 3. PRO PVP COMBO PAUSE SYSTEM (Nghỉ 1 nhịp sau 5-8 hits)
    // ============================================================
    private static int hitCount = 0;
    private static int targetHitsToPause = getRandomPauseThreshold();
    private static long pauseUntilTime = 0L;

    // ============================================================
    // 4. W-TAP (Sprint Reset)
    // ============================================================
    private static final boolean W_TAP_ENABLED = true;
    private static final int W_TAP_COOLDOWN_TICKS = 1;

    // ============================================================
    // 5. STATE MACHINE
    // ============================================================
    private static PlayerEntity lastTarget = null;
    private static long targetEnterTime = 0L;
    private static int wTapTicks = 0;
    private static boolean wTapPending = false;

    private Triggerbot() {}

    public static void tick() {
        ModConfig.TriggerSnapshot cfg = ModConfig.snapshotTrigger();
        ClientPlayerEntity player = mc.player;

        // --- Reset trạng thái nếu thiếu điều kiện cơ bản ---
        if (!cfg.enabled() || player == null || mc.world == null || mc.interactionManager == null) {
            resetState();
            return;
        }

        // --- Xử lý W-tap đang chờ ---
        if (wTapPending) {
            if (wTapTicks > 0) {
                wTapTicks--;
            } else {
                wTapPending = false;
            }
        }

        // ============================================================
        // WEAPON FILTER (Kiểm tra Kiếm / Rìu)
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

        // Kiểm tra tầm đánh (Trong khoảng 3.0 blocks)
        double distance = player.distanceTo(target);
        if (distance > MAX_REACH) {
            resetState();
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Check xem có đang trong nhịp "khử/chậm lại 1 nhịp" (Pro PVP Pause) hay không
        if (currentTime < pauseUntilTime) {
            return;
        }

        // ============================================================
        // REACTION DELAY (Chỉ áp dụng rất nhẹ cho đòn mở đầu)
        // ============================================================
        if (target != lastTarget) {
            lastTarget = target;
            targetEnterTime = currentTime;
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
        // ATTACK COOLDOWN CHECK (Nhịp đánh Pro PVP)
        // ============================================================
        // 0.95f đảm bảo đòn đánh tung ra luôn đạt full sát thương và knockback
        float cooldown = player.getAttackCooldownProgress(0.0f);
        if (cooldown < 0.95f) {
            return;
        }

        // ============================================================
        // EXECUTE ATTACK & W-TAP
        // ============================================================
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {

            // Nhả sprint 1 tick ngay khi tung đòn để tạo W-Tap knockback chuẩn
            if (W_TAP_ENABLED && player.isSprinting()) {
                player.setSprinting(false);
                wTapPending = true;
                wTapTicks = W_TAP_COOLDOWN_TICKS;
            }

            // Gọi doAttack qua Reflection
            invokeDoAttack();

            // Tăng số đòn đánh thành công
            hitCount++;

            // Kiểm tra xem đã đạt ngưỡng 5-8 đòn để tạo nhịp "tạm hoãn/chậm 1 nhịp" chưa
            if (hitCount >= targetHitsToPause) {
                // Tạm dừng từ 120ms đến 220ms (tương đương chậm lại đúng 1 nhịp đánh)
                int pauseDuration = 120 + random.nextInt(101);
                pauseUntilTime = currentTime + pauseDuration;

                // Reset bộ đếm nhịp cho chuỗi combo tiếp theo
                hitCount = 0;
                targetHitsToPause = getRandomPauseThreshold();
            }
        }
    }

    private static int getRandomPauseThreshold() {
        // Trả về ngẫu nhiên số đòn từ 5 đến 8
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
        wTapPending = false;
        wTapTicks = 0;
        hitCount = 0;
        pauseUntilTime = 0L;
    }
}
