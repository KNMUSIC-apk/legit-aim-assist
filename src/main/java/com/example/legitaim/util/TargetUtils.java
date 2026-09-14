package com.example.legitaim.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;
import java.util.Optional;

public final class TargetUtils {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private TargetUtils() {}

    public static Optional<AbstractClientPlayerEntity> findTarget(
            double reach, double fovDegrees,
            Vec3d eyePos, Vec3d lookVec) {

        ClientPlayerEntity self = mc.player;
        if (self == null || mc.world == null) return Optional.empty();

        double cosFov = Math.cos(Math.toRadians(fovDegrees));
        List<AbstractClientPlayerEntity> players = mc.world.getPlayers();
        int size = players.size();

        AbstractClientPlayerEntity best = null;
        double bestDistSq = reach * reach;

        for (int i = 0; i < size; i++) {
            if (i >= players.size()) break;
            AbstractClientPlayerEntity p = players.get(i);
            if (!isValidTarget(self, p)) continue;

            double distSq = self.squaredDistanceTo(p);
            if (distSq > bestDistSq) continue;

            double tx = p.getX() - eyePos.x;
            double ty = (p.getY() + p.getHeight() * 0.5) - eyePos.y;
            double tz = p.getZ() - eyePos.z;
            double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (len < 1e-6) continue;

            double dot = (lookVec.x * tx + lookVec.y * ty + lookVec.z * tz) / len;
            if (dot < cosFov) continue;

            if (!hasLineOfSight(self, eyePos, p)) continue;

            best = p;
            bestDistSq = distSq;
        }

        return Optional.ofNullable(best);
    }

    private static boolean isValidTarget(ClientPlayerEntity self, PlayerEntity p) {
        if (p == self) return false;
        if (!p.isAlive()) return false;
        if (p.isSpectator()) return false;
        if (p.isRemoved()) return false;
        if (p.isInvisibleTo(self)) return false;
        return p.getHealth() > 0.0f;
    }

    public static boolean hasLineOfSight(
            ClientPlayerEntity self, Vec3d eyePos, PlayerEntity target) {

        if (mc.world == null) return false;

        double tx = target.getX();
        double ty = target.getY() + target.getHeight() * 0.5;
        double tz = target.getZ();

        BlockHitResult hit = mc.world.raycast(new RaycastContext(
            eyePos,
            new Vec3d(tx, ty, tz),
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            self
        ));

        return hit.getType() == HitResult.Type.MISS;
    }

    public static Box getHitbox(PlayerEntity target) {
        return target.getBoundingBox();
    }
}
