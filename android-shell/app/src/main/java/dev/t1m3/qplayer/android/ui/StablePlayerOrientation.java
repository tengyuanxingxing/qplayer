package dev.t1m3.qplayer.android.ui;

/** Hysteresis and dwell time independent of frames and sensor frequency. */
final class StablePlayerOrientation {
    static final int PORTRAIT = 1, LANDSCAPE = 0, REVERSE_LANDSCAPE = 8;
    private int current = PORTRAIT, candidate = PORTRAIT;
    private long since;
    int update(int degrees, long now) {
        int next = degrees >= 60 && degrees <= 120 ? REVERSE_LANDSCAPE
                : degrees >= 240 && degrees <= 300 ? LANDSCAPE
                : degrees >= 0 && (degrees <= 35 || degrees >= 325 || (degrees >= 145 && degrees <= 215)) ? PORTRAIT : -1;
        // Flat/diagonal angles cancel a pending turn but keep the displayed page.
        if (next < 0) { candidate = current; return current; }
        if (next != candidate) { candidate = next; since = now; }
        if (next == PORTRAIT || now - since >= 1000) current = next;
        return current;
    }
    long remaining(long now) {
        return candidate != current ? Math.max(1,1000-(now-since)) : 0;
    }
    void reset() { candidate = current; since = 0; }
    void portrait() { current = candidate = PORTRAIT; since = 0; }
}
