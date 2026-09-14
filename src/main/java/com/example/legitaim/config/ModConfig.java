package com.example.legitaim.config;

/**
 * Thread-safe config.
 * - volatile: đồng bộ giữa tick thread và render thread (đặc biệt quan trọng
 *   trên Apple Silicon / ARM).
 * - snapshot: lấy giá trị nhất quán tại một thời điểm.
 */
public final class ModConfig {

    private ModConfig() {}

    // ── Aim Assist ──
    public static volatile boolean aimAssistEnabled = false;
    public static volatile double  aimReach = 3.8;
    public static volatile double  aimFov = 30.0;
    public static volatile float   aimSpeed = 4.0f;
    public static volatile float   aimJitter = 0.15f;
    public static volatile int     aimUpdateInterval = 3;

    // Anticheat-safe rotation clamps
    public static volatile float maxYawPerTick   = 10.0f;
    public static volatile float maxPitchPerTick = 8.0f;

    // ── ESP ──
    public static volatile boolean espEnabled = false;
    public static volatile float   espRed = 1.0f;
    public static volatile float   espGreen = 0.0f;
    public static volatile float   espBlue = 0.0f;
    public static volatile float   espAlpha = 0.6f;
    public static volatile boolean espThroughWalls = true;

    // ── Triggerbot ──
    public static volatile boolean triggerbotEnabled = false;
    public static volatile long    triggerbotMinDelay = 20L;
    public static volatile long    triggerbotMaxDelay = 80L;

    // ── Snapshots (records, immutable) ──

    public record AimSnapshot(
        boolean enabled, double reach, double fov, float speed,
        float jitter, int updateInterval,
        float maxYawPerTick, float maxPitchPerTick
    ) {}

    public record EspSnapshot(
        boolean enabled, float r, float g, float b, float a,
        boolean throughWalls
    ) {}

    public record TriggerSnapshot(
        boolean enabled, long minDelay, long maxDelay
    ) {}

    public static AimSnapshot snapshotAim() {
        return new AimSnapshot(
            aimAssistEnabled, aimReach, aimFov, aimSpeed,
            aimJitter, aimUpdateInterval,
            maxYawPerTick, maxPitchPerTick
        );
    }

    public static EspSnapshot snapshotEsp() {
        return new EspSnapshot(
            espEnabled, espRed, espGreen, espBlue, espAlpha,
            espThroughWalls
        );
    }

    public static TriggerSnapshot snapshotTrigger() {
        return new TriggerSnapshot(
            triggerbotEnabled, triggerbotMinDelay, triggerbotMaxDelay
        );
    }
}
