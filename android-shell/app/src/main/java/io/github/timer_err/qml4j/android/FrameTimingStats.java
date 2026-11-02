package io.github.timer_err.qml4j.android;

import java.util.Arrays;

/** Rendering throughput is measured separately from the OS display-mode refresh rate. */
final class FrameTimingStats {
    static final class Sample {
        final float fps, p95Ms, workMs;
        Sample(float fps,float p95Ms,float workMs) { this.fps=fps;this.p95Ms=p95Ms;this.workMs=workMs; }
    }
    private long first, last, work;
    private int count, workCount;
    private final long[] gaps = new long[240];
    Sample frame(long now, long workNanos) {
        if (last == 0 || now <= last || now-last > 250_000_000L) {
            first=last=now;count=workCount=0;work=0;return null;
        }
        long gap=now-last;last=now;
        gaps[count%gaps.length]=gap;count++;work+=Math.max(0,workNanos);workCount++;
        if (now-first < 1_000_000_000L) return null;
        long[] sorted=Arrays.copyOf(gaps,Math.min(count,gaps.length));Arrays.sort(sorted);
        Sample sample=new Sample(count*1_000_000_000f/(now-first),
                sorted[Math.max(0,(int)Math.ceil(sorted.length*.95)-1)]/1_000_000f,
                work/(float)workCount/1_000_000f);
        first=now;count=workCount=0;work=0;
        return sample;
    }
}
