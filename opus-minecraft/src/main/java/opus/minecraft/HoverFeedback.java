package opus.minecraft;

import opus.core.Easing;

/** Small renderer-owned transition; hovering does not recompose the UI tree. */
final class HoverFeedback {
    private float value;
    private float from;
    private float target;
    private long started;

    float sample(boolean hovered, long now, float durationScale) {
        float next = hovered ? 1f : 0f;
        if (durationScale <= 0f) {
            value = from = target = next;
            started = now;
            return value;
        }
        float t = Math.max(0f, Math.min(1f, (now - started) / (120_000_000f * durationScale)));
        value = from + (target - from) * Easing.EASE_OUT.transform(t);
        if (next != target) {
            from = value;
            target = next;
            started = now;
        }
        return value;
    }
}
