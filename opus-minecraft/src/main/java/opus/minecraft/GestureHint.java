package opus.minecraft;

import opus.core.SwipeDirection;

/** Ephemeral edge feedback, independent of layout and composition updates. */
final class GestureHint {
    record Frame(SwipeDirection direction, float progress, float opacity, boolean confirmed, boolean available) {}
    private SwipeDirection direction;
    private float progress;
    private boolean confirmed, available;
    private boolean dismissed;
    private long updatedAt;

    void update(SwipeDirection direction, float progress, boolean confirmed, boolean available, long now) {
        this.direction = direction;
        this.progress = Math.clamp(progress, 0f, 1f);
        this.confirmed = confirmed;
        this.available = available;
        dismissed = false;
        updatedAt = now;
    }

    void clear() { direction = null; }

    void dismiss(long now) { dismissed = true; updatedAt = now; }

    Frame sample(long now, boolean reducedMotion) {
        if (direction == null) return null;
        float elapsed = Math.max(0, now - updatedAt) / 1_000_000f;
        float hold = confirmed || dismissed ? 0f : 140f;
        float fade = confirmed ? 100f : 80f;
        float opacity = reducedMotion ? (elapsed < hold ? 1f : 0f)
            : 1f - Math.clamp((elapsed - hold) / fade, 0f, 1f);
        if (opacity <= 0) { clear(); return null; }
        return new Frame(direction, progress, opacity, confirmed, available);
    }
}
