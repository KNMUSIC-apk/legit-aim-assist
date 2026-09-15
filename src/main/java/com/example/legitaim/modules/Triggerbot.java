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

    // Cache lại Reflection Method để tối ưu hiệu năng (không phải find method liên tục mỗi tick)
    private static Method doAttackMethod = null;

    static {
        try {
            // Tìm method doAttack trong MinecraftClient
            doAttackMethod = MinecraftClient.class.getDeclaredMethod("doAttack");
            doAttackMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            // Trường hợp chạy ở môi trường Obfuscated (Intermediary/Named mappings)
            for (Method method : MinecraftClient.class.getDeclaredMethods()) {
                if (method.getReturnType() == void.class && method.getParameterCount() == 0) {
                    // Trong Fabric Yarn mapping tên thường là doAttack, nếu dùng intermediary có thể là method_1536
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
    // 1. WEAPON FILTER
    // ============================================================
    // Chỉ đánh khi cầm Kiếm hoặc Rìu. Các item khác (Block, Food,
    // Pickaxe, Bow...) sẽ tự động ngắt để không đánh nhầm.

    // ============================================================
    // 2. REACH RANGE CONTROL (2.75 - 3.0 blocks)
    // ============================================================
    // Chỉ tung đòn khi khoảng cách nằm trong "sweet spot" của reach.
    // - Dưới 2.5m  : đối thủ đã áp sát → không đánh vội, ưu tiên W-tap
    // - 2.75-3.0m : vùng lý tưởng → đánh để out-range
    // - Trên 3.0m  : quá xa không với tới
    private static final double MIN_REACH = 2.75D;
    private static final double MAX_REACH = 3.0D;

    // Ngưỡng "nguy hiểm" - dưới ngưỡng này coi như bị áp sát
    private static final double DANGER_RANGE = 2.5D;

    // ============================================================
    // 3. REACTION DELAY (giảm mạnh để không bị combo)
    // ============================================================
    // Chỉ delay ở cú đánh ĐẦU TIÊN khi mục tiêu mới vào crosshair.
    // Các cú tiếp theo trong cùng mục tiêu sẽ đánh gần như tức thì
    // (chỉ chờ cooldown của game).
    private static final int FIRST_HIT_DELAY_MIN = 40;   // ms
    private static final int FIRST_HIT_DELAY_MAX = 90;   // ms

    // ============================================================
    // 4. W-TAP (Sprint Reset)
    // ============================================================
    // Sau mỗi cú đánh, tự động nhả sprint 1 tick để tăng knockback.
    // Đây là kỹ thuật PvP thật, giúp đẩy đối thủ ra xa, tránh bị combo.
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

        // --- Reset nếu thiếu điều kiện cơ bản ---
        if (!cfg.enabled() || player == null || mc.world == null || mc.interactionManager == null) {
            resetState();
            return;
        }

        // --- Xử lý W-tap đang chờ ---
        if (wTapPending) {
            if (wTapTicks > 0) {
                wTapTicks--;
            } else {
                // Nhả W-tap: cho sprint lại (nếu người chơi vẫn đang đi tới)
                wTapPending = false;
            }
            // Trong lúc W-tap, vẫn cho phép đánh (không return)
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
        // CROSSHAIR TARGET CHECK
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

        // ============================================================
        // REACH RANGE CONTROL
        // ============================================================
        double distance = player.distanceTo(target);

        // Trên 3.0m: ngoài tầm, không đánh
        if (distance > MAX_REACH) {
            resetState();
            return;
        }

        // Dưới 2.5m: đối thủ áp sát → ưu tiên W-tap để tạo khoảng cách
        // thay vì đánh trade (tránh bị combo ngược)
        boolean inDangerZone = distance < DANGER_RANGE;

        // ============================================================
        // REACTION DELAY (chỉ áp dụng cho cú đầu)
        // ============================================================
        long currentTime = System.currentTimeMillis();
        if (target != lastTarget) {
            // Mục tiêu mới → ghi nhận thời điểm + delay ngẫu nhiên
            lastTarget = target;
            targetEnterTime = currentTime;
            int delay = FIRST_HIT_DELAY_MIN
                      + random.nextInt(FIRST_HIT_DELAY_MAX - FIRST_HIT_DELAY_MIN + 1);
            // Cho phép đánh ngay nếu đang ở trong sweet spot
            if (distance >= MIN_REACH && distance <= MAX_REACH) {
                targetEnterTime = currentTime - delay; // bỏ qua delay
            }
            return;
        }

        // Áp delay cho cú đầu (chỉ khi target đã ổn định)
        if (targetEnterTime != 0L && currentTime - targetEnterTime < FIRST_HIT_DELAY_MIN) {
            return;
        }

        // ============================================================
        // ATTACK LOGIC
        // ============================================================
        // Cooldown check: chỉ đánh khi đã hồi đủ (>= 0.92)
        // Không random quá cao để tránh mất nhịp combo
        float cooldown = player.getAttackCooldownProgress(0.0f);
        if (cooldown < 0.92f) {
            return;
        }

        // Nếu đang trong danger zone VÀ chưa ở sweet spot
        // → không đánh vội, ưu tiên W-tap để đẩy ra
        if (inDangerZone) {
            // Nhưng nếu đang bị combo (health thấp + đối thủ đánh liên tục)
            // thì vẫn phải đánh để phá combo
            boolean mustFightBack = player.getHealth() < 8.0f
                                 && target.getAttackCooldownProgress(0.0f) > 0.9f;
            if (!mustFightBack) {
                return;
            }
        }

        // ============================================================
        // PRIMARY ATTACK SIMULATION (invoke doAttack via Reflection)
        // ============================================================
        if (mc.crosshairTarget != null
                && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {

            // W-tap: nhả sprint trước khi đánh (nếu đang sprint)
            if (W_TAP_ENABLED && player.isSprinting()) {
                player.setSprinting(false);
                wTapPending = true;
                wTapTicks = W_TAP_COOLDOWN_TICKS;
            }

            // Gọi doAttack qua Reflection (Bypass private access control)
            invokeDoAttack();

            // Reset timer cho cú tiếp theo (nhưng không delay lâu)
            targetEnterTime = currentTime;
        }
    }

    private static void invokeDoAttack() {
        if (doAttackMethod != null) {
            try {
                doAttackMethod.invoke(mc);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Fallback phòng trường hợp Reflection không lấy được method
            if (mc.interactionManager != null && mc.crosshairTarget instanceof EntityHitResult entityHit) {
                mc.interactionManager.attackEntity(mc.player, entityHit.getEntity());
                if (mc.player != null) {
                    mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                }
            }
        }
    }

    // ============================================================
    // HELPER
    // ============================================================
    private static void resetState() {
        lastTarget = null;
        targetEnterTime = 0L;
        wTapPending = false;
        wTapTicks = 0;
    }
}
