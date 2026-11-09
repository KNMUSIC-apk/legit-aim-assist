
package com.example.legitaim.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

public class AimAssist {

    public static boolean enabled = false;

    private static final double RANGE = 5.0;
    private static final float SMOOTHING = 0.18f;

    public static void onClientTick(MinecraftClient client) {

        if (!enabled || client.player == null || client.world == null) {
            return;
        }

        PlayerEntity player = client.player;

        AbstractClientPlayerEntity target = null;
        double nearestDistance = RANGE;

        for (AbstractClientPlayerEntity candidate : client.world.getPlayers()) {

            if (candidate == player
                    || !candidate.isAlive()
                    || candidate.isSpectator()) {
                continue;
            }

            double distance = player.distanceTo(candidate);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                target = candidate;
            }
        }

        if (target == null) {
            return;
        }

        double dx = target.getX() - player.getX();
        double dy = target.getY() + target.getHeight() * 0.85
                - player.getEyeY();
        double dz = target.getZ() - player.getZ();

        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (
                Math.toDegrees(Math.atan2(dz, dx)) - 90.0
        );

        float targetPitch = (float) (
                -Math.toDegrees(Math.atan2(dy, horizontalDistance))
        );

        float yawDifference = MathHelper.wrapDegrees(
                targetYaw - player.getYaw()
        );

        float pitchDifference = targetPitch - player.getPitch();

        player.setYaw(
                player.getYaw() + yawDifference * SMOOTHING
        );

        player.setPitch(
                MathHelper.clamp(
                        player.getPitch() + pitchDifference * SMOOTHING,
                        -90.0f,
                        90.0f
                )
        );
    }
}
