package dev.swingingsigns.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreTest {
    @Test
    void kickedSpringOvershootsAndSettles() {
        SignSpring spring = new SignSpring();
        spring.kick(0.1f);
        float max = 0f;
        float min = 0f;
        int ticks = 0;
        while (!spring.atRest() && ticks < 2000) {
            spring.step();
            max = Math.max(max, spring.angle());
            min = Math.min(min, spring.angle());
            ticks++;
        }
        assertTrue(max > 0.1f, "swing " + max);
        assertTrue(min < 0f, "no overshoot");
        assertTrue(Math.abs(min) < max, "overshoot larger than swing");
        assertTrue(spring.atRest(), "still moving after " + ticks);
        assertTrue(ticks > 40 && ticks < 600, "settled in " + ticks);
    }

    @Test
    void springStaysWithinLimits() {
        SignSpring spring = new SignSpring();
        for (int i = 0; i < 200; i++) {
            spring.kick(10f);
            spring.drag(10f, 1f);
            spring.step();
            assertTrue(Math.abs(spring.angle()) <= SignSpring.MAX_ANGLE);
            assertTrue(Math.abs(spring.speed()) <= SignSpring.MAX_SPEED + 1e-4f);
        }
    }

    @Test
    void springIgnoresNaN() {
        SignSpring spring = new SignSpring();
        spring.kick(Float.NaN);
        spring.drag(Float.NaN, 0.5f);
        spring.drag(0.1f, Float.NaN);
        spring.step();
        assertTrue(spring.atRest());
        assertEquals(0f, spring.angle(0.5f));
    }

    @Test
    void partialTickInterpolates() {
        SignSpring spring = new SignSpring();
        spring.kick(0.1f);
        spring.step();
        spring.step();
        float a = spring.angle(0f);
        float b = spring.angle(1f);
        float mid = spring.angle(0.5f);
        assertEquals((a + b) / 2f, mid, 1e-6f);
    }

    @Test
    void swayIsDeterministicPerSeed() {
        int seed = SwayMath.seed(123456789L);
        assertEquals(SwayMath.sway(seed, 500, 0f, 0f, 0f, false, 1f),
                SwayMath.sway(seed, 500, 0f, 0f, 0f, false, 1f));
        int other = SwayMath.seed(123456790L);
        assertNotEquals(seed, other);
        boolean differs = false;
        for (int t = 0; t < 100; t += 7) {
            if (SwayMath.sway(seed, t, 0f, 0f, 0f, false, 1f) != SwayMath.sway(other, t, 0f, 0f, 0f, false, 1f)) {
                differs = true;
            }
        }
        assertTrue(differs, "neighbouring signs move in sync");
    }

    @Test
    void stormsSwingHarder() {
        int seed = SwayMath.seed(42L);
        float clear = peak(seed, 0f, 0f, false);
        float rain = peak(seed, 1f, 0f, false);
        float thunder = peak(seed, 1f, 1f, false);
        assertTrue(clear < rain, clear + " vs " + rain);
        assertTrue(rain < thunder, rain + " vs " + thunder);
        assertTrue(clear < (float) Math.toRadians(2), "idle too strong " + clear);
        assertTrue(peak(seed, 1f, 1f, true) < thunder, "shelter has no effect");
    }

    @Test
    void weatherAmplitudeOrdersAndClamps() {
        assertEquals(0f, SwayMath.weatherAmplitude(0f, 0f));
        assertEquals(SwayMath.RAIN_AMPLITUDE, SwayMath.weatherAmplitude(1f, 0f), 1e-6f);
        assertEquals(SwayMath.THUNDER_AMPLITUDE, SwayMath.weatherAmplitude(1f, 1f), 1e-6f);
        assertEquals(SwayMath.THUNDER_AMPLITUDE, SwayMath.weatherAmplitude(5f, 5f), 1e-6f);
        assertEquals(0f, SwayMath.weatherAmplitude(Float.NaN, Float.NaN));
    }

    @Test
    void zeroStrengthIsStill() {
        int seed = SwayMath.seed(7L);
        for (int t = 0; t < 500; t += 13) {
            assertEquals(0f, SwayMath.sway(seed, t, 1f, 1f, 1f, false, 0f));
            assertEquals(0f, SwayMath.sway(seed, t, 1f, 1f, 1f, false, Float.NaN));
        }
        assertEquals(0f, SwayMath.pushTarget(1, 1, 0f, 0f));
    }

    @Test
    void pushFollowsMovement() {
        float yaw = 0f;
        float forward = SwayMath.pushTarget(0, 0.2, yaw, 1f);
        float back = SwayMath.pushTarget(0, -0.2, yaw, 1f);
        assertTrue(forward > 0f);
        assertEquals(-forward, back, 1e-6f);
        assertEquals(0f, SwayMath.pushTarget(0.2, 0, yaw, 1f), 1e-6f);
        float turned = SwayMath.pushTarget(0.2, 0, (float) (Math.PI / 2), 1f);
        assertTrue(Math.abs(turned) > 0.05f);
    }

    @Test
    void gustStaysInRange() {
        for (int t = -5000; t < 20000; t += 3) {
            float g = SwayMath.gust(t);
            assertTrue(g >= 0f && g <= 1f, "gust " + g + " at " + t);
        }
    }

    @Test
    void unitStaysInRange() {
        for (int s = 0; s < 1000; s++) {
            double u = SwayMath.unit(SwayMath.seed(s), s & 7);
            assertTrue(u >= 0.0 && u < 1.0);
        }
    }

    @Test
    void nanInputsStayFinite() {
        int seed = SwayMath.seed(99L);
        assertTrue(Float.isFinite(SwayMath.sway(seed, 100, Float.NaN, Float.NaN, Float.NaN, false, 1f)));
        assertEquals(0f, SwayMath.pushTarget(Double.NaN, 1, 0f, 1f));
        assertEquals(0f, SwayMath.pushTarget(1, 1, Float.NaN, 1f));
        assertFalse(Float.isNaN(SwayMath.sway(seed, 100, 0f, Float.NaN, Float.NaN, false, 1f)));
        assertFalse(Float.isInfinite(SwayMath.sway(seed, 100, 0f, Float.POSITIVE_INFINITY, 1f, true, 1f)));
    }

    private static float peak(int seed, float rain, float thunder, boolean sheltered) {
        float peak = 0f;
        for (int t = 0; t < 4000; t++) {
            peak = Math.max(peak, Math.abs(SwayMath.sway(seed, t, 0.3f, rain, thunder, sheltered, 1f)));
        }
        return peak;
    }
}
