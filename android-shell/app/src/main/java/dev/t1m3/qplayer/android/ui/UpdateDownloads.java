package dev.t1m3.qplayer.android.ui;

import android.app.DownloadManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Update state belongs to QPlayer, never a detached system download job. */
final class UpdateDownloads {
    static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> new Thread(r,"update-status"));
    static final int NOTIFICATION=7294;
    static final String CHANNEL="app-updates";
    private static volatile String startingVersion="";
    private static volatile long startingAt;
    static void serviceStarted() {startingVersion="";}
    static final class Status {
        final int progress; final File ready;
        Status(int progress, File ready) { this.progress=progress;this.ready=ready; }
    }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("update-download",Context.MODE_PRIVATE); }
    static synchronized void migrateLegacy(Context c) {
        SharedPreferences p=prefs(c);
        if(p.contains("id")) {
            long id=p.getLong("id",-1);
            if(id>=0) ((DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE)).remove(id);
            p.edit().clear().commit();
            ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFICATION);
        }
    }
    static synchronized void start(Context c,String[] urls) {
        migrateLegacy(c);
        if(urls==null || urls.length==0)throw new IllegalArgumentException("No update URL");
        String version=UpdatePackagePolicy.targetVersion(urls[0]);
        if(version.isEmpty())throw new IllegalArgumentException("No target version");
        for(String url:urls)if(!version.equals(UpdatePackagePolicy.targetVersion(url)))throw new IllegalArgumentException("Mixed update versions");
        if(UpdateDownloadService.isDownloading(version) || (version.equals(startingVersion) && android.os.SystemClock.elapsedRealtime()-startingAt<10000))return;
        Status previous=inspect(c);
        if(previous.ready!=null && version.equals(prefs(c).getString("version",""))) { requestInstall(c);return; }
        Intent request=new Intent(c,UpdateDownloadService.class).putExtra("urls",urls);
        startingVersion=version;startingAt=android.os.SystemClock.elapsedRealtime();
        try {if(Build.VERSION.SDK_INT>=26)c.startForegroundService(request);else c.startService(request);}
        catch(RuntimeException error) {serviceStarted();throw error;}
    }
    static synchronized Status inspect(Context c) {
        migrateLegacy(c);
        Status live=UpdateDownloadService.snapshot();if(live!=null)return live;
        if(!startingVersion.isEmpty() && android.os.SystemClock.elapsedRealtime()-startingAt<10000)return new Status(0,null);
        SharedPreferences p=prefs(c);
        if(p.getBoolean("running",false)) {
            // Process killed: cancel stale status, don't resurrect the task.
            deletePartial(c,p.getString("path",""));p.edit().putBoolean("running",false).commit();
        }
        if(p.getBoolean("failed",false))return new Status(-2,null);
        if(p.getBoolean("ready",false)) {
            File file=new File(p.getString("path",""));
            if(valid(c,file,p.getString("version","")))return new Status(100,file);
            p.edit().clear().commit();
        }
        return new Status(-1,null);
    }
    static void deletePartial(Context c,String path) {
        if(path==null || path.isEmpty())return;
        try {
            File root=new File(c.getExternalFilesDir(null),"updates").getCanonicalFile();
            File file=new File(path).getCanonicalFile();
            if(file.getParentFile().equals(root)) { new File(file.getPath()+".part").delete();if(!prefs(c).getBoolean("ready",false))file.delete(); }
        }catch(Exception ignored){}
    }
    static void cancel(Context c) {
        migrateLegacy(c);serviceStarted();UpdateDownloadService.cancelCurrent();c.stopService(new Intent(c,UpdateDownloadService.class));
        SharedPreferences p=prefs(c);deletePartial(c,p.getString("path",""));
        p.edit().putBoolean("running",false).putBoolean("failed",false).commit();
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFICATION);
    }
    static boolean valid(Context c, File archive, String target) {
        try {
            if (!archive.isFile()) return false;
            PackageManager pm = c.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo before = pm.getPackageInfo(c.getPackageName(), flags);
            PackageInfo after = pm.getPackageArchiveInfo(archive.getAbsolutePath(), flags);
            return after != null && UpdatePackagePolicy.matchesTarget(target, after.versionName)
                    && UpdatePackagePolicy.compatible(before.packageName, version(before), signers(before),
                            after.packageName, version(after), signers(after));
        } catch (Exception e) { return false; }
    }

    private static long version(PackageInfo p) { return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode; }
    private static byte[][] signers(PackageInfo p) {
        android.content.pm.Signature[] s = Build.VERSION.SDK_INT >= 28 && p.signingInfo != null
                ? p.signingInfo.getApkContentsSigners() : p.signatures;
        if (s == null) return new byte[0][];
        byte[][] out = new byte[s.length][];
        for (int i = 0; i < s.length; i++) out[i] = s[i].toByteArray();
        return out;
    }

    static synchronized boolean needsInstall(Context c) { return !prefs(c).getBoolean("delivered", false); }
    static synchronized boolean validReady(Context c, File file) {
        return prefs(c).getBoolean("ready", false) && file.getAbsolutePath().equals(prefs(c).getString("path", ""))
                && valid(c, file, prefs(c).getString("version", ""));
    }
    static synchronized void delivered(Context c) {
        prefs(c).edit().putBoolean("delivered", true).commit();
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFICATION);
    }

    static synchronized void requestInstall(Context c) { prefs(c).edit().putBoolean("delivered", false).commit(); }

}
