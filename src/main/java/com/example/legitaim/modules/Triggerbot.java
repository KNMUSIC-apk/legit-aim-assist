package com.example.legitaim.modules;

import com.example.legitaim.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
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
        for (Method method : MinecraftClient.class.getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && 
               (method.getName().equals("doAttack") || method.getName().equals("method_1536"))) {
                doAttackMethod = method;
                doAttackMethod.setAccessible(true);
                break;
            }
        }
    }

    private static long pauseUntilTime = 0L;

    private Triggerbot() {}

    public static void tick() {
        ModConfig.TriggerSnapshot cfg = ModConfig.snapshotTrigger();
        ClientPlayerEntity player = mc.player;

        if (!cfg.enabled() || player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) {
            pauseUntilTime = 0L;
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

        float cooldown = player.getAttackCooldownProgress(0.0f);
        ItemStack mainHandStack = player.getMainHandStack();
        int currentSlot = player.getInventory().selectedSlot;
        
        boolean targetIsBlocking = target.isBlocking();
        
        // Điều kiện Smash: Không ở mặt đất VÀ vận tốc rơi trục Y phải nhỏ hơn 0 (đang rơi xuống)
        boolean isFalling = !player.isOnGround() && player.getVelocity().y < 0.0;

        // ============================================================
        // 1. FAST-SWAP TỰ ĐỘNG PHÁ KHIÊN BẰNG AXE
        // ============================================================
        if (targetIsBlocking && mainHandStack.getItem() instanceof SwordItem) {
            int axeSlot = findAxeSlot(player);
            
            if (axeSlot != -1 && cooldown >= 0.99f) {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(axeSlot));
                player.getInventory().selectedSlot = axeSlot; 
                
                mc.interactionManager.attackEntity(player, target);
                player.swingHand(Hand.MAIN_HAND);
                player.resetLastAttackedTicks(); 
                
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
                player.getInventory().selectedSlot = currentSlot; 
                
                pauseUntilTime = currentTime + 50;
                return;
            }
        }

        // ============================================================
        // 2. FAST-SWAP TỰ ĐỘNG ĐẬP MACE (CHỈ KHI ĐANG RƠI XUỐNG)
        // ============================================================
        if (isFalling && mainHandStack.getItem() instanceof SwordItem) {
            int maceSlot = findMaceSlot(player);
            
            if (maceSlot != -1 && cooldown >= 0.99f) {
                // Chuyển sang Mace
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(maceSlot));
                player.getInventory().selectedSlot = maceSlot;
                
                // Đánh gây sát thương Mace
                mc.interactionManager.attackEntity(player, target);
                
                // Chuyển lại Sword
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
                player.getInventory().selectedSlot = currentSlot;
                
                // Hiển thị hoạt ảnh vung Sword
                player.swingHand(Hand.MAIN_HAND);
                player.resetLastAttackedTicks();
                
                pauseUntilTime = currentTime + 50;
                return;
            }
        }

        // ============================================================
        // 3. CHUỖI COMBO TỐI ƯU (BÌNH THƯỜNG / ĐỨNG DƯỚI ĐẤT / NHẢY LÊN)
        // ============================================================
        boolean isWeapon = mainHandStack.getItem() instanceof SwordItem
                        || mainHandStack.getItem() instanceof AxeItem;
        if (!isWeapon) {
            return; 
        }

        if (cooldown < 0.99f) {
            return;
        }

        invokeDoAttack();
        
        pauseUntilTime = currentTime + (10 + random.nextInt(10));
    }

    private static int findAxeSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                return i;
            }
        }
        return -1;
    }

    private static int findMaceSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            Item item = player.getInventory().getStack(i).getItem();
            if (item != null && item.toString().toLowerCase().contains("mace")) {
                return i;
            }
        }
        return -1;
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
                mc.player.resetLastAttackedTicks(); 
            }
        }
    }
}
