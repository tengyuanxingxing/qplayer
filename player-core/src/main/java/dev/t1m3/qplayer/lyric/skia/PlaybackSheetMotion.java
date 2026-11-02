package dev.t1m3.qplayer.lyric.skia;

/** Finger owns sheet position during a drag; a time-based spring owns only settling. */
public final class PlaybackSheetMotion {
    private float offset=1f,velocity;
    public float offset(){return offset;}
    public void dragTo(float position){offset=Math.max(0f,Math.min(1f,position));velocity=0f;}
    public void releaseAt(float position,float speed){dragTo(position);velocity=Math.max(-4f,Math.min(4f,speed));}
    public float advance(boolean open,float dt) {
        float target=open?0f:1f;dt=Math.max(0f,Math.min(0.05f,dt));
        float omega=20f,delta=offset-target,c=velocity+omega*delta,e=(float)Math.exp(-omega*dt);
        offset=target+(delta+c*dt)*e;velocity=(velocity-omega*c*dt)*e;
        if(offset<0f){offset=0f;velocity=0f;}if(offset>1f){offset=1f;velocity=0f;}
        if(Math.abs(offset-target)<0.0005f&&Math.abs(velocity)<0.005f){offset=target;velocity=0f;}
        return offset;
    }
    public static boolean shouldDismiss(double offset,double speed) {
        return speed> -0.5 && (offset>=0.24 || (offset>=0.04&&speed>=0.9));
    }
}
