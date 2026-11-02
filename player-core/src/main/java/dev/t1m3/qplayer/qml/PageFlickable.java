package dev.t1m3.qplayer.qml;

import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.items.core.Flickable;

/** Two persistent pages with release-time snapping, using the engine's native axis arbitration. */
public class PageFlickable extends Flickable {
    private static final long SNAP_NANOS = 180_000_000L;
    public final Property<Number> pageIndex = new Property<>(0);
    public final Property<Boolean> pageActive = new Property<>(true);
    private boolean internalSelection;
    private boolean settling;
    private float snapFrom, snapTo;
    private long snapEpoch;

    public PageFlickable() {
        flickableDirection.set("HorizontalFlick");
        pageIndex.addListener(value -> {
            if (internalSelection) return;
            int clamped = clampPage(value == null ? 0 : value.intValue());
            if (clamped != pageIndex.peekInt()) setIndex(clamped);
            if (Boolean.TRUE.equals(moving.peek()) && !settling) resetPosition();
            else animateToPage();
        });
        width.addListener(value -> resetPosition());
        contentWidth.addListener(value -> resetPosition());
        visible.addListener(value -> resetPosition());
        pageActive.addListener(value -> resetPosition());
        interactive.addListener(value -> { if (!Boolean.TRUE.equals(value)) resetPosition(); });
    }

    private static int clampPage(int index) { return index <= 0 ? 0 : 1; }
    private void setIndex(int index) {
        internalSelection = true;
        try { pageIndex.set(index); } finally { internalSelection = false; }
    }

    @Override public void startFling(float velocityX, float velocityY) {
        float pageWidth = width.peekFloat();
        float distance = contentX.peekFloat() - pageIndex.peekInt() * pageWidth;
        int selected = pageIndex.peekInt();
        // A tiny accidental movement never flips a page, even with a noisy velocity sample.
        if (pageWidth > 0 && (Math.abs(distance) >= pageWidth * 0.25f
                || (Math.abs(distance) >= 12 && Math.abs(velocityX) >= 650
                    && Math.signum(distance) == Math.signum(velocityX)))) {
            selected += distance > 0 ? 1 : -1;
        }
        super.stopScroll();
        setIndex(clampPage(selected));
        animateToPage();
    }

    private void animateToPage() {
        super.stopScroll();
        snapFrom = contentX.peekFloat();
        snapTo = pageIndex.peekInt() * width.peekFloat();
        if (!Boolean.TRUE.equals(pageActive.peek()) || !Boolean.TRUE.equals(visible.peek())
                || Math.abs(snapFrom - snapTo) < 0.5f) {
            resetPosition();
            return;
        }
        snapEpoch = System.nanoTime();
        settling = true;
        moving.set(true);
    }

    /** Cancellation, viewport changes and hidden-page recovery cannot leave a fractional page. */
    public void resetPosition() {
        settling = false;
        super.stopScroll();
        contentX.setPaintOnly(pageIndex.peekInt() * width.peekFloat());
        contentY.setPaintOnly(0f);
        super.syncTarget();
    }

    @Override public void stopScroll() { resetPosition(); }

    @Override public void tick(long nowNanos) {
        if (!settling) return; // Native input translates content directly while a finger owns it.
        float t = Math.max(0f, Math.min(1f, (nowNanos - snapEpoch) / (float) SNAP_NANOS));
        float remaining = 1f - t;
        contentX.setPaintOnly(snapTo + (snapFrom - snapTo) * remaining * remaining * remaining);
        if (t >= 1f) resetPosition();
    }
}
