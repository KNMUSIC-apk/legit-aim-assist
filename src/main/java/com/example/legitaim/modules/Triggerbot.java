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
        // FIX 1: Xóa kiểm tra void.class để tương thích với Minecraft 1.20+ (doAttack trả về boolean)
        for (Method method : MinecraftClient.class.getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && 
               (method.getName().equals("doAttack") || method.getName().equals("method_1536"))) {
                doAttackMethod = method;
                doAttackMethod.setAccessible(true);
                break;
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
        
        // CHỈ ĐÁNH PLAYER (Theo đúng yêu cầu của bạn)
        if (!(entity instanceof PlayerEntity target) || !target.isAlive() || target.isSpectator() || target.isCreative() || target.isRemoved()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime < pauseUntilTime) {
            return;
        }

        // Tối ưu ngưỡng hồi chiêu xuống 0.90f để bù độ trễ của mạng (Ping)
        float humanizedCooldownThreshold = 0.90f + (random.nextFloat() * 0.05f);
        float cooldown = player.getAttackCooldownProgress(0.0f);
        
        ItemStack mainHandStack = player.getMainHandStack();
        int currentSlot = player.getInventory().selectedSlot;
        boolean targetIsBlocking = target.isBlocking();

        // ============================================================
        // TRICK FAST-SWAP TỰ ĐỘNG PHÁ KHIÊN BẰNG RÌU
        // ============================================================
        if (targetIsBlocking && mainHandStack.getItem() instanceof SwordItem) {
            int axeSlot = findAxeSlot(player);
            
            if (axeSlot != -1 && cooldown >= humanizedCooldownThreshold) {
                // Đổi sang rìu
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(axeSlot));
                player.getInventory().selectedSlot = axeSlot; 
                
                // Vung tay phá khiên
                mc.interactionManager.attackEntity(player, target);
                player.swingHand(Hand.MAIN_HAND);
                player.resetLastAttackedTicks(); // FIX 2: Bắt buộc reset hồi chiêu
                
                // Trả về kiếm
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
                player.getInventory().selectedSlot = currentSlot; 
                
                hitCount++;
                pauseUntilTime = currentTime + (150 + random.nextInt(100));
                return;
            }
        }

        // ============================================================
        // CHUỖI COMBO BÌNH THƯỜNG (BẮT BUỘC CẦM KIẾM/RÌU)
        // ============================================================
        boolean isWeapon = mainHandStack.getItem() instanceof SwordItem
                        || mainHandStack.getItem() instanceof AxeItem;
        if (!isWeapon) {
            return; 
        }

        if (cooldown < humanizedCooldownThreshold) {
            return;
        }

        // Tỷ lệ vung hụt tự nhiên 8%
        if (random.nextInt(100) < 8) {
            player.swingHand(Hand.MAIN_HAND);
            pauseUntilTime = currentTime + (120 + random.nextInt(100));
            return;
        }

        invokeDoAttack();
        hitCount++;

        // Nghỉ nhịp tay ngẫu nhiên để giống thật
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
                fallbackAttack();
            }
        } else {
            fallbackAttack();
        }
    }

    private static void fallbackAttack() {
        if (mc.interactionManager != null && mc.crosshairTarget instanceof EntityHitResult entityHit) {
            mc.interactionManager.attackEntity(mc.player, entityHit.getEntity());
            if (mc.player != null) {
                mc.player.swingHand(Hand.MAIN_HAND);
                mc.player.resetLastAttackedTicks(); // FIX 2: Chống lỗi spam click rỗng bị server chặn sát thương
            }
        }
    }

    private static void resetState() {
        hitCount = 0;
        pauseUntilTime = 0L;
    }
}
