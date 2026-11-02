package dev.t1m3.qplayer.lyric.skia;

import java.util.ArrayList;
import java.util.List;

/** Hit boxes of the actually painted text, including line animation transforms. */
final class LyricHitRegions {
    private final List<float[]> boxes = new ArrayList<>();
    private final List<Long> times = new ArrayList<>();
    void clear() { boxes.clear(); times.clear(); }
    void add(float left, float top, float right, float bottom, float cx, float cy, float scale, long time) {
        if (right <= left || bottom <= top) return;
        boxes.add(new float[]{cx + (left - cx) * scale, cy + (top - cy) * scale,
                cx + (right - cx) * scale, cy + (bottom - cy) * scale});
        times.add(time);
    }
    long timeAt(float x, float y) {
        for (int i = boxes.size() - 1; i >= 0; i--) {
            float[] b = boxes.get(i);
            if (x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3]) return times.get(i);
        }
        return -1L;
    }
}
