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

        long currentTime = System.currentTimeMillis();
        if (currentTime < pauseUntilTime) {
            return;
        }

        float humanizedCooldownThreshold = 0.93f + (random.nextFloat() * 0.06f);
        float cooldown = player.getAttackCooldownProgress(0.0f);
        
        boolean targetIsBlocking = target.isBlocking();
        int currentSlot = player.getInventory().selectedSlot;
        ItemStack mainHandStack = player.getMainHandStack();

        // ============================================================
        // TRICK MỚI: FAST-SWAP SHIELD BREAKER (KIẾM -> ĐÁNH -> RÌU)
        // ============================================================
        if (targetIsBlocking) {
            if (mainHandStack.getItem() instanceof SwordItem) {
                int axeSlot = findAxeSlot(player);
                if (axeSlot != -1) {
                    // 1. Chỉ thực hiện khi thanh Kiếm đã hồi đủ (nhanh hơn Rìu rất nhiều)
                    if (cooldown >= humanizedCooldownThreshold) {
                        // 2. Click chém bằng Kiếm
                        invokeDoAttack();
                        hitCount++;
                        
                        // 3. Swap sang Rìu ngay lập tức trong cùng 1 tick
                        originalSwordSlot = currentSlot;
                        player.getInventory().selectedSlot = axeSlot;
                        
                        // 4. Nghỉ một nhịp nhẹ sau pha xử lý
                        pauseUntilTime = currentTime + (150 + random.nextInt(100));
                        return; 
                    } else {
                        return; // Chờ Kiếm nạp chiêu (không được chém bừa)
                    }
                }
            }
        } else {
            // Khi khiên đối thủ bị vỡ (bất hoạt), tự động thu Rìu về lại Kiếm
            if (originalSwordSlot != -1) {
                if (player.getMainHandStack().getItem() instanceof AxeItem) {
                    player.getInventory().selectedSlot = originalSwordSlot;
                }
                originalSwordSlot = -1;
                // Có thể cho cooldown hồi lại để bắt đầu chuỗi combo mới
                return;
            }
        }

        // Tự do chuyển vũ khí bằng tay (chống lỗi kẹt slot)
        if (originalSwordSlot != -1 && !(player.getMainHandStack().getItem() instanceof AxeItem)) {
            originalSwordSlot = -1;
        }

        // CHUỖI COMBO THÔNG THƯỜNG
        boolean isWeapon = mainHandStack.getItem() instanceof SwordItem
                        || mainHandStack.getItem() instanceof AxeItem;
        if (!isWeapon) {
            return; 
        }

        if (cooldown < humanizedCooldownThreshold) {
            return;
        }

        // Tỷ lệ vung hụt 8% (Chỉ áp dụng khi đánh thường, KHÔNG áp dụng khi đang xài Trick phá khiên)
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
