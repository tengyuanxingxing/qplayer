package dev.t1m3.qplayer.lyric.skia;

/** Direction lock shared by the two hosts. A swipe can never become a lyric tap. */
public final class PlaybackPageGesture {
    public static final int TAP = 0, VERTICAL = 1, HORIZONTAL = 2;
    private float x, y;
    private int direction;
    public void begin(float x, float y) { this.x = x; this.y = y; direction = TAP; }
    public int move(float x, float y) {
        if (direction == TAP && Math.max(Math.abs(x - this.x), Math.abs(y - this.y)) > 10f)
            direction = Math.abs(x - this.x) > Math.abs(y - this.y) * 1.25f
                    ? HORIZONTAL : VERTICAL;
        return direction;
    }
    public int finish(float x, float y) {
        move(x, y);
        return direction == HORIZONTAL && Math.abs(x - this.x) >= 48f
                ? (x < this.x ? 1 : -1) : 0;
    }
    public int direction() { return direction; }
}
