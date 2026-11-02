package dev.t1m3.qplayer.android.ui;

import android.app.Service;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-started app transfer: survives Home, but never restarts after task removal. */
public final class UpdateDownloadService extends Service {
    private static final Object LOCK=new Object();
    private static volatile Job current;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->new Thread(r,"app-update-download"));
    private final Handler main=new Handler(Looper.getMainLooper());
    private static final class Job {
        final String version; final File target; final AppUpdateTransfer transfer=new AppUpdateTransfer();
        volatile int progress; volatile int startId; volatile boolean finished;
        Job(String version,File target,int startId) {this.version=version;this.target=target;this.startId=startId;}
    }
    static boolean isDownloading(String version) {Job j=current;return j!=null && !j.finished && j.version.equals(version);}
    static UpdateDownloads.Status snapshot() {
        Job j=current;if(j==null)return null;
        return new UpdateDownloads.Status(j.progress,j.progress==100?j.target:null);
    }
    static void cancelCurrent() {
        synchronized(LOCK) {Job j=current;current=null;if(j!=null)j.transfer.cancel();}
    }
    @Override public void onCreate() {
        super.onCreate();
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
            new NotificationChannel(UpdateDownloads.CHANNEL,"软件更新",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null || "cancel".equals(intent.getAction())) {UpdateDownloads.cancel(this);stopSelf();return START_NOT_STICKY;}
        String[] urls=intent.getStringArrayExtra("urls");
        if(urls==null || urls.length==0) {stopSelf();return START_NOT_STICKY;}
        String version=UpdatePackagePolicy.targetVersion(urls[0]);
        if(version.isEmpty()) {stopSelf();return START_NOT_STICKY;}
        synchronized(LOCK) {
            if(isDownloading(version)) {current.startId=startId;return START_NOT_STICKY;}
            cancelCurrent();
            File target=new File(getExternalFilesDir(null),"updates/QPlayer-"+version+"-"+UUID.randomUUID()+".apk");
            Job job=new Job(version,target,startId);current=job;UpdateDownloads.serviceStarted();
            UpdateDownloads.prefs(this).edit().clear().putString("version",version).putString("path",target.getAbsolutePath())
                .putBoolean("running",true).putBoolean("delivered",false).commit();
            startForeground(UpdateDownloads.NOTIFICATION,notification(job));
            worker.execute(()->download(job,urls));
        }
        return START_NOT_STICKY;
    }
    private android.app.Notification notification(Job job) {
        Intent intent=new Intent(this,QPlayerActivity.class);
        PendingIntent open=PendingIntent.getActivity(this,7294,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent cancel=PendingIntent.getService(this,7295,new Intent(this,UpdateDownloadService.class).setAction("cancel"),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this,UpdateDownloads.CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("QPlayer 正在下载更新").setContentText("下载中 "+job.progress+"% · 清理后台会停止")
            .setProgress(100,job.progress,false).setOnlyAlertOnce(true).setOngoing(true).setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"取消下载",cancel).build();
    }
    private void download(Job job,String[] urls) {
        boolean saved=false;
        try {
            saved=job.transfer.download(urls,job.target,(bytes,total)->{
                synchronized(LOCK) {
                    if(current!=job)return;
                    job.progress=total>0?(int)Math.min(99,bytes*100/total):0;
                    ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(UpdateDownloads.NOTIFICATION,notification(job));
                }
            },file->UpdateDownloads.valid(this,file,job.version));
        }catch(Exception error) {
            dev.t1m3.qplayer.util.Logger.warn("app update transfer ended: {}",error.toString());
        }
        synchronized(LOCK) {
            if(current!=job)return;
            job.finished=true;job.progress=saved?100:-2;
            UpdateDownloads.prefs(this).edit().putBoolean("running",false).putBoolean("ready",saved).putBoolean("failed",!saved).commit();
        }
        main.post(()->{synchronized(LOCK){if(current==job){current=null;stopForeground(STOP_FOREGROUND_REMOVE);stopSelfResult(job.startId);}}});
    }
    @Override public void onTaskRemoved(Intent rootIntent) {UpdateDownloads.cancel(this);stopSelf();}
    @Override public void onDestroy() {
        synchronized(LOCK) {cancelCurrent();UpdateDownloads.prefs(this).edit().putBoolean("running",false).commit();}
        UpdateDownloads.deletePartial(this,UpdateDownloads.prefs(this).getString("path",""));
        worker.shutdownNow();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) {return null;}
}
