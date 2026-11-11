package com.example.legitaim.modules;

public class HumanMotionLUT {

    public static final float[] SMOOTHING_LUT = {
        0.035f, 0.042f, 0.051f, 0.068f, 0.085f, 0.092f, 0.088f, 0.075f, 0.060f, 0.045f,
        0.052f, 0.071f, 0.094f, 0.102f, 0.090f, 0.072f, 0.051f, 0.040f, 0.048f, 0.065f,
        0.082f, 0.098f, 0.089f, 0.070f, 0.053f, 0.038f, 0.045f, 0.062f, 0.080f, 0.095f
    };

    public static final float[] JITTER_YAW_LUT = {
        0.15f, -0.08f, 0.22f, -0.18f, 0.05f, -0.32f, 0.18f, 0.12f, -0.15f, 0.25f,
        -0.05f, 0.10f, -0.22f, 0.30f, -0.12f, 0.08f, -0.20f, 0.14f, 0.05f, -0.10f
    };

    public static final float[] JITTER_PITCH_LUT = {
        0.08f, -0.04f, 0.12f, -0.10f, 0.03f, -0.18f, 0.10f, 0.06f, -0.08f, 0.15f,
        -0.03f, 0.05f, -0.12f, 0.18f, -0.07f, 0.04f, -0.11f, 0.08f, 0.03f, -0.05f
    };

    public static final float[] CLICK_DELAY_LUT = {
        2, 1, 3, 2, 4, 1, 2, 3, 5, 2, 1, 2, 3, 1, 4, 2, 3, 1, 2, 4
    };

    public static float getSmoothing(int index) {
        return SMOOTHING_LUT[Math.abs(index) % SMOOTHING_LUT.length];
    }

    public static float getJitterYaw(int index) {
        return JITTER_YAW_LUT[Math.abs(index) % JITTER_YAW_LUT.length];
    }

    public static float getJitterPitch(int index) {
        return JITTER_PITCH_LUT[Math.abs(index) % JITTER_PITCH_LUT.length];
    }

    public static int getClickDelay(int index) {
        return CLICK_DELAY_LUT[Math.abs(index) % CLICK_DELAY_LUT.length];
    }
}
