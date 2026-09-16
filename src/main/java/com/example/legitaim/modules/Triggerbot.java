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
    private static int lostTargetTicks = 0;
    
    // Cờ trạng thái: Đã rút Rìu thì phải chém xong mới được cất
    private static boolean isBreakingShield = false; 

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

        lostTargetTicks = 0;

        // ============================================================
        // AUTO-AXE SHIELD BREAKER (Fixed: Commit Strike)
        // ============================================================
        boolean targetIsBlocking = target.isBlocking();
        int currentSlot = player.getInventory().selectedSlot;

        // 1. Kích hoạt phá khiên: Nếu đang cầm kiếm và mục tiêu đỡ đòn, lập tức rút rìu
        if (targetIsBlocking && !isBreakingShield) {
            if (player.getMainHandStack().getItem() instanceof SwordItem) {
                int axeSlot = findAxeSlot(player);
                if (axeSlot != -1 && axeSlot != currentSlot) {
                    originalSwordSlot = currentSlot;
                    player.getInventory().selectedSlot = axeSlot;
                    isBreakingShield = true; // Bật cờ cam kết chém rìu
                    return; 
                }
            }
        }

        // 2. Thu rìu về: Chỉ được đổi lại Kiếm nếu ĐÃ CHÉM XONG rìu (!isBreakingShield) và mục tiêu đã hạ khiên
        if (!isBreakingShield && originalSwordSlot != -1 && !targetIsBlocking) {
            if (player.getMainHandStack().getItem() instanceof AxeItem) {
                player.getInventory().selectedSlot = originalSwordSlot;
            }
            originalSwordSlot = -1;
            return;
        }

        // Nếu người chơi tự cuộn chuột sang vũ khí khác, hủy bộ đếm
        if (originalSwordSlot != -1 && !(player.getMainHandStack().getItem() instanceof AxeItem)) {
            originalSwordSlot = -1;
            isBreakingShield = false;
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
            return; // Đang chờ hồi chiêu (của Kiếm hoặc Rìu)
        }

        // Chỉ áp dụng tỷ lệ Miss 8% cho những đòn chém Kiếm thông thường.
        // Bỏ qua Miss Chance nếu đang thực hiện đòn đập Rìu phá khiên để đảm bảo luôn trúng.
        if (!isBreakingShield && random.nextInt(100) < 8) {
            player.swingHand(Hand.MAIN_HAND);
            pauseUntilTime = currentTime + (120 + random.nextInt(100));
            return;
        }

        // TUNG ĐÒN ĐÁNH
        invokeDoAttack();
        hitCount++;

        // Nếu đòn vừa tung ra là đòn Rìu phá khiên, tắt cờ cam kết để tick sau thu vũ khí về Kiếm
        if (isBreakingShield && mainHandStack.getItem() instanceof AxeItem) {
            isBreakingShield = false;
            // Tạo độ trễ nghỉ tay nhỉnh hơn một chút sau khi đập rìu nặng (giả lập phản xạ con người)
            pauseUntilTime = currentTime + (150 + random.nextInt(100));
            return;
        }

        if (hitCount >= targetHitsToPause) {
            pauseUntilTime = currentTime + (100 + random.nextInt(120));
            hitCount = 0;
            targetHitsToPause = getRandomPauseThreshold();
        }
    }

    private static void handleLostTarget(ClientPlayerEntity player) {
        if (originalSwordSlot != -1) {
            lostTargetTicks++;
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
        isBreakingShield = false;
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
