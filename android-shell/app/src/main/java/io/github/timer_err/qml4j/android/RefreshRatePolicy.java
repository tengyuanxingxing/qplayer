package io.github.timer_err.qml4j.android;

/** Pick only rates explicitly exposed for the current resolution; never invent 165Hz. */
final class RefreshRatePolicy {
    static float choose(int width, int height, float current, int[] widths, int[] heights, float[] rates,
                        float[] alternatives, boolean powerSave) {
        if (powerSave) return 0f;
        float best = valid(current) ? current : 60f;
        for (int i = 0; i < rates.length; i++)
            if (widths[i] == width && heights[i] == height && valid(rates[i])) best = Math.max(best, rates[i]);
        for (float rate : alternatives) if (valid(rate)) best = Math.max(best, rate);
        return best;
    }
    private static boolean valid(float rate) { return Float.isFinite(rate) && rate >= 24f && rate <= 1000f; }
}
