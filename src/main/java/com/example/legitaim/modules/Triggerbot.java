package com.example.legitaim.modules;

import com.example.legitaim.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
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

    private Triggerbot() {}

    public static void tick() {
        ModConfig.TriggerSnapshot cfg = ModConfig.snapshotTrigger();
        ClientPlayerEntity player = mc.player;

        if (!cfg.enabled() || player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) {
            resetState();
            return;
        }

        if (player.isUsingItem()) {
            return;
        }

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) {
            return;
        }

        Entity entity = ((EntityHitResult) hit).getEntity();
        if (!(entity instanceof PlayerEntity target) || !target.isAlive() || target.isSpectator() || target.isCreative() || target.isRemoved()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime < pauseUntilTime) {
            return;
        }

        float humanizedCooldownThreshold = 0.93f + (random.nextFloat() * 0.06f);
        float cooldown = player.getAttackCooldownProgress(0.0f);
        
        ItemStack mainHandStack = player.getMainHandStack();
        int currentSlot = player.getInventory().selectedSlot;
        boolean targetIsBlocking = target.isBlocking();

        // ============================================================
        // TRICK FAST-SWAP THEO VIDEO (PACKET-BASED SHIELD BREAKER)
        // ============================================================
        if (targetIsBlocking && mainHandStack.getItem() instanceof SwordItem) {
            int axeSlot = findAxeSlot(player);
            
            // Chỉ thi triển Fast-Swap khi Kiếm đã nạp đầy chiêu
            if (axeSlot != -1 && cooldown >= humanizedCooldownThreshold) {
                
                // Bước 1: ÉP GỬI GÓI TIN BÁO SERVER TA ĐÃ CẦM RÌU (Không cần chờ Vanilla tự check)
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(axeSlot));
                player.getInventory().selectedSlot = axeSlot; // Đồng bộ Client
                
                // Bước 2: TUNG ĐÒN ĐÁNH NGAY LẬP TỨC (Lúc này Server ghi nhận ta đang đánh bằng Rìu)
                mc.interactionManager.attackEntity(player, target);
                player.swingHand(Hand.MAIN_HAND);
                
                // Bước 3: ÉP GỬI GÓI TIN TRẢ VỀ KIẾM NGAY TRONG CÙNG 1 TICK
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
                player.getInventory().selectedSlot = currentSlot; // Đồng bộ Client
                
                // Bước 4: Reset nhịp độ combo
                hitCount++;
                pauseUntilTime = currentTime + (150 + random.nextInt(100)); // Delay một nhịp tay
                return;
            }
        }

        // ============================================================
        // CHUỖI COMBO BÌNH THƯỜNG
        // ============================================================
        boolean isWeapon = mainHandStack.getItem() instanceof SwordItem
                        || mainHandStack.getItem() instanceof AxeItem;
        if (!isWeapon) {
            return; 
        }

        if (cooldown < humanizedCooldownThreshold) {
            return;
        }

        // Tỷ lệ đánh hụt tự nhiên 8%
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

    private static int findAxeSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                return i;
            }
        }
        return -1;
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

    private static void resetState() {
        hitCount = 0;
        pauseUntilTime = 0L;
    }
}
