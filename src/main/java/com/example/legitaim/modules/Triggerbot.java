package com.example.legitaim.modules;

import com.example.legitaim.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.concurrent.ThreadLocalRandom;

public final class Triggerbot {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // Tunable humanization constants.
    private static final double MAX_RANGE = 3.0D;
    private static final double MIN_COOLDOWN = 0.88D;
    private static final double MAX_COOLDOWN = 1.00D;
    private static final int MIN_REACT_TICKS = 2;
    private static final int MAX_REACT_TICKS = 5;
    private static final int MIN_LOCK_TICKS = 2;
    private static final double BASE_MISS = 0.05D;
    private static final double MAX_MISS = 0.30D;
    private static final boolean REQUIRE_CRIT = false;
    private static final boolean IGNORE_BLOCKING = false;

    // Runtime state.
    private static int reactionTimer = -1;
    private static int lastTargetId = -1;
    private static int lastAttackTick = -100;
    private static int targetLockTicks = 0;
    private static int pauseTicks = 0;
    private static int switchPenaltyTicks = 0;
    private static double requiredCooldown = 0.95D;
    private static int missStreak = 0;
    private static int hitStreak = 0;

    private Triggerbot() {}

    public static void tick() {
        ModConfig.TriggerSnapshot cfg = ModConfig.snapshotTrigger();
        ClientPlayerEntity player = mc.player;

        if (!cfg.enabled() || player == null || mc.world == null || mc.interactionManager == null) {
            reset();
            return;
        }

        if (mc.currentScreen != null || player.isUsingItem() || player.isSpectator()) {
            reset();
            return;
        }

        Item item = player.getMainHandStack().getItem();
        if (!(item instanceof SwordItem) && !(item instanceof AxeItem)) {
            reset();
            return;
        }

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) {
            reset();
            return;
        }

        Entity entity = ((EntityHitResult) hit).getEntity();
        if (!(entity instanceof PlayerEntity target) || !isValidTarget(player, target)) {
            reset();
            return;
        }

        // Human-like target acquisition delay.
        if (target.getId() != lastTargetId) {
            lastTargetId = target.getId();
            reactionTimer = rand(MIN_REACT_TICKS, MAX_REACT_TICKS);
            targetLockTicks = 0;
            switchPenaltyTicks = rand(1, 3);
            requiredCooldown = randDouble(MIN_COOLDOWN, MAX_COOLDOWN);
        }

        targetLockTicks++;

        // Short delay when switching targets.
        if (switchPenaltyTicks > 0) {
            switchPenaltyTicks--;
            return;
        }

        // Reaction time before first attack on a target.
        if (reactionTimer > 0) {
            reactionTimer--;
            return;
        }

        // Random pause after attack or miss.
        if (pauseTicks > 0) {
            pauseTicks--;
            return;
        }

        if (targetLockTicks < MIN_LOCK_TICKS) {
            return;
        }

        if (player.distanceTo(target) > MAX_RANGE) {
            return;
        }

        // Do not attack before a randomized cooldown threshold.
        if (player.getAttackCooldownProgress(0.5f) < requiredCooldown) {
            return;
        }

        // Optional critical-hit requirement.
        if (REQUIRE_CRIT && !canCrit(player)) {
            return;
        }

        // Prevent multiple attacks within the same short window.
        if (player.age - lastAttackTick < rand(1, 2)) {
            return;
        }

        // Adaptive miss chance based on distance and recent hit streak.
        double distanceFactor = Math.min(1.0D, player.distanceTo(target) / MAX_RANGE);
        double missChance = Math.min(
                MAX_MISS,
                BASE_MISS + hitStreak * 0.025D + distanceFactor * 0.10D
        );

        if (ThreadLocalRandom.current().nextDouble() < missChance) {
            missStreak++;
            hitStreak = 0;
            pauseTicks = rand(2, 4);
            reactionTimer = rand(1, 2);
            requiredCooldown = randDouble(MIN_COOLDOWN, MAX_COOLDOWN);
            return;
        }

        mc.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);

        lastAttackTick = player.age;
        hitStreak++;
        missStreak = 0;
        requiredCooldown = randDouble(MIN_COOLDOWN, MAX_COOLDOWN);
        pauseTicks = rand(0, 2);
    }

    private static boolean isValidTarget(ClientPlayerEntity self, PlayerEntity target) {
        if (target == self || !target.isAlive() || target.isRemoved() || target.isSpectator()) {
            return false;
        }
        if (target.isCreative()) {
            return false;
        }
        if (self.isTeammate(target)) {
            return false;
        }
        if (IGNORE_BLOCKING && target.isBlocking()) {
            return false;
        }
        return self.canSee(target);
    }

    private static boolean canCrit(ClientPlayerEntity player) {
        return player.fallDistance > 0.0f
                && !player.isOnGround()
                && !player.isClimbing()
                && !player.isTouchingWater()
                && !player.hasVehicle();
    }

    private static int rand(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static double randDouble(double min, double max) {
        return min + ThreadLocalRandom.current().nextDouble() * (max - min);
    }

    private static void reset() {
        lastTargetId = -1;
        reactionTimer = -1;
        targetLockTicks = 0;
        pauseTicks = 0;
        switchPenaltyTicks = 0;
    }
}
