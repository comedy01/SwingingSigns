package dev.swingingsigns.core;

public final class SignSpring {
    public static final float OMEGA = 0.24f;
    public static final float DAMPING = 0.11f;
    public static final float MAX_ANGLE = 0.85f;
    public static final float MAX_SPEED = 0.25f;
    private static final float REST_ANGLE = 0.0015f;
    private static final float REST_SPEED = 0.0004f;

    private float prev;
    private float angle;
    private float speed;

    public float angle() {
        return angle;
    }

    public float speed() {
        return speed;
    }

    public float angle(float partialTick) {
        return prev + (angle - prev) * partialTick;
    }

    public void kick(float deltaSpeed) {
        if (Float.isFinite(deltaSpeed)) {
            speed = clamp(speed + deltaSpeed, MAX_SPEED);
        }
    }

    public void drag(float targetSpeed, float coupling) {
        if (Float.isFinite(targetSpeed) && Float.isFinite(coupling)) {
            float c = Math.max(0f, Math.min(1f, coupling));
            speed = clamp(speed + (targetSpeed - speed) * c, MAX_SPEED);
        }
    }

    public void step() {
        prev = angle;
        float accel = -OMEGA * OMEGA * angle - 2f * DAMPING * OMEGA * speed;
        speed += accel;
        angle += speed;
        if (Math.abs(angle) > MAX_ANGLE) {
            angle = Math.copySign(MAX_ANGLE, angle);
            speed *= -0.3f;
        }
    }

    public boolean atRest() {
        return Math.abs(angle) < REST_ANGLE && Math.abs(speed) < REST_SPEED
                && Math.abs(prev) < REST_ANGLE;
    }

    private static float clamp(float value, float limit) {
        return Math.max(-limit, Math.min(limit, value));
    }
}
