package dev.swingingsigns.core;

public final class SwayMath {
    public static final float IDLE_AMPLITUDE = (float) Math.toRadians(1.4);
    public static final float RAIN_AMPLITUDE = (float) Math.toRadians(5.0);
    public static final float THUNDER_AMPLITUDE = (float) Math.toRadians(13.0);
    public static final float SHELTER_FACTOR = 0.25f;
    private static final double TAU = Math.PI * 2.0;

    private SwayMath() {
    }

    public static int seed(long pos) {
        long h = pos * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (int) h;
    }

    public static double unit(int seed, int salt) {
        int h = seed * 0x27D4EB2D + salt * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    public static float gust(double time) {
        double g = 0.5
                + 0.28 * Math.sin(time * TAU / 410.0)
                + 0.15 * Math.sin(time * TAU / 157.0 + 1.3)
                + 0.07 * Math.sin(time * TAU / 61.0 + 4.1);
        return (float) Math.max(0.0, Math.min(1.0, g));
    }

    public static float windHeading(double time) {
        return (float) (0.9 + 1.1 * Math.sin(time * TAU / 9600.0) + 0.35 * Math.sin(time * TAU / 2300.0 + 2.0));
    }

    public static float weatherAmplitude(float rain, float thunder) {
        float r = clamp01(rain);
        float t = clamp01(thunder);
        return r * RAIN_AMPLITUDE + t * (THUNDER_AMPLITUDE - RAIN_AMPLITUDE) * r + t * (1f - r) * RAIN_AMPLITUDE;
    }

    public static float sway(int seed, double time, float yawRadians, float rain, float thunder,
                             boolean sheltered, float strength) {
        if (strength <= 0f || !Float.isFinite(strength)) {
            return 0f;
        }
        if (!Float.isFinite(yawRadians)) {
            yawRadians = 0f;
        }
        double rate = 0.85 + 0.3 * unit(seed, 1);
        double phase = unit(seed, 2) * TAU;
        double phase2 = unit(seed, 3) * TAU;
        double lag = unit(seed, 4) * 25.0;
        double t = time * rate;

        double idle = 0.7 * Math.sin(t * TAU / 72.0 + phase) + 0.3 * Math.sin(t * TAU / 41.0 + phase2);
        float result = (float) (IDLE_AMPLITUDE * idle);

        float weather = weatherAmplitude(rain, thunder);
        if (weather > 0f) {
            if (sheltered) {
                weather *= SHELTER_FACTOR;
            }
            float g = gust(time - lag);
            float heading = windHeading(time);
            float facing = (float) -Math.cos(heading - yawRadians);
            double flutter = 0.65 * Math.sin(t * TAU / 34.0 + phase) + 0.35 * Math.sin(t * TAU / 19.0 + phase2);
            result += weather * g * (0.55f * facing + 0.45f * (float) flutter);
            result += weather * 0.25f * (float) Math.sin(t * TAU / 88.0 + phase2);
        }
        return result * strength;
    }

    public static float pushTarget(double moveX, double moveZ, float yawRadians, float strength) {
        if (!Float.isFinite(yawRadians) || !Double.isFinite(moveX) || !Double.isFinite(moveZ)) {
            return 0f;
        }
        double nx = -Math.sin(yawRadians);
        double nz = Math.cos(yawRadians);
        double along = moveX * nx + moveZ * nz;
        return (float) (along * 0.55 * strength);
    }

    private static float clamp01(float v) {
        return Float.isFinite(v) ? Math.max(0f, Math.min(1f, v)) : 0f;
    }
}
