
package com.example.legitaim.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.registry.Registries;

public class Triggerbot {

    public static boolean enabled = false;

    public static void onClientTick(MinecraftClient client) {

        if (!enabled
                || client.player == null
                || client.world == null
                || client.interactionManager == null) {
            return;
        }

        if (!(client.crosshairTarget instanceof EntityHitResult hit)) {
            return;
        }

        Entity target = hit.getEntity();

        if (!(target instanceof LivingEntity living)
                || !living.isAlive()
                || living == client.player) {
            return;
        }

        String itemId = Registries.ITEM
                .getId(client.player.getMainHandStack().getItem())
                .getPath();

        boolean validWeapon = itemId.endsWith("_sword")
                || itemId.endsWith("_axe");

        if (!validWeapon) {
            return;
        }

        if (client.player.getAttackCooldownProgress(0.5f) >= 1.0f) {
            client.interactionManager.attackEntity(client.player, target);
            client.player.swingHand(
                    net.minecraft.util.Hand.MAIN_HAND
            );
        }
    }
}
