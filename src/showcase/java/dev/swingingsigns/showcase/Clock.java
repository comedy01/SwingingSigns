package dev.swingingsigns.showcase;

import net.minecraft.util.Util;

final class Clock {
    private static final long FRAME = 1_000_000_000L / Recorder.FPS;
    private static volatile long nanos;
    private static boolean fixed;

    private Clock() {
    }

    static void fix() {
        if (!fixed) {
            fixed = true;
            nanos = System.nanoTime();
            Util.setTimeSource(() -> nanos);
        }
    }

    static void release() {
        if (fixed) {
            fixed = false;
            Util.setTimeSource(System::nanoTime);
        }
    }

    static void step() {
        if (fixed) {
            nanos += FRAME;
        }
    }

    static long nanos() {
        return fixed ? nanos : System.nanoTime();
    }
}
