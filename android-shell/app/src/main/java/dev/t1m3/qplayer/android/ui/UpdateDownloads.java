package dev.t1m3.qplayer.android.ui;

import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The OS owns the transfer. Activity/process recreation only reattaches to its saved ID. */
final class UpdateDownloads {
    static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> new Thread(r, "update-status"));
    private static final String PREFS = "update-download";
    private static final int NOTIFICATION = 7294;
    private static final String CHANNEL = "app-updates";
    static final class Status {
        final int progress;
        final File ready;
        Status(int progress, File ready) { this.progress = progress; this.ready = ready; }
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    private static DownloadManager manager(Context c) { return (DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE); }

    static synchronized void start(Context c, String[] urls) {
        if (urls == null || urls.length == 0) throw new IllegalArgumentException("No update URL");
        String version = UpdatePackagePolicy.targetVersion(urls[0]);
        if (version.isEmpty()) throw new IllegalArgumentException("No target version");
        for (String url : urls) if (!version.equals(UpdatePackagePolicy.targetVersion(url)))
            throw new IllegalArgumentException("Mixed update versions");
        SharedPreferences p = prefs(c);
        if (version.equals(p.getString("version", "")) && p.getLong("id", -1) >= 0) {
            Status old = inspect(c);
            if (old.progress >= 0) {
                p.edit().putBoolean("delivered", false).commit();
                return;
            }
        }
        long old = p.getLong("id", -1);
        if (old >= 0) manager(c).remove(old);
        p.edit().clear().putString("version", version).putString("urls", String.join("\n", urls))
                .putInt("index", 0).commit();
        enqueue(c);
    }

    private static void enqueue(Context c) {
        SharedPreferences p = prefs(c);
        String[] urls = p.getString("urls", "").split("\n");
        int index = p.getInt("index", 0);
        while (index < urls.length) {
            try {
                // Never reuse a provider URI: package installers can cache metadata by URI.
                String relative = "updates/QPlayer-" + p.getString("version", "") + "-"
                        + UUID.randomUUID() + ".apk";
                File out = new File(c.getExternalFilesDir(null), relative);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(urls[index]))
                        .setTitle("QPlayer " + p.getString("version", ""))
                        .setDescription("正在下载更新，可切换到其他软件")
                        .setMimeType("application/vnd.android.package-archive")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                        .setAllowedOverMetered(true).setAllowedOverRoaming(false)
                        .addRequestHeader("User-Agent", "qplayer-updater")
                        .addRequestHeader("Cache-Control", "no-cache")
                        .setDestinationInExternalFilesDir(c, null, relative);
                long id = manager(c).enqueue(request);
                p.edit().putLong("id", id).putString("path", out.getAbsolutePath())
                        .putInt("index", index).putBoolean("ready", false)
                        .putBoolean("delivered", false).commit();
                return;
            } catch (RuntimeException e) {
                dev.t1m3.qplayer.util.Logger.warn("enqueue update failed: {}", e.toString());
                p.edit().putInt("index", ++index).commit();
            }
        }
        p.edit().putLong("id", -1).putBoolean("failed", true).commit();
    }

    static synchronized Status inspect(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getBoolean("failed", false)) return new Status(-2, null);
        long id = p.getLong("id", -1);
        if (id < 0) return new Status(-1, null);
        File file = new File(p.getString("path", ""));
        if (p.getBoolean("ready", false)) {
            if (!valid(c, file, p.getString("version", ""))) {
                // An installed upgrade (or removed/tampered file) must not keep
                // relaunching the installer or refetching an obsolete release.
                manager(c).remove(id);
                p.edit().clear().commit();
                return new Status(-1, null);
            }
            return new Status(100, file);
        }
        try (Cursor cursor = manager(c).query(new DownloadManager.Query().setFilterById(id))) {
            if (cursor == null || !cursor.moveToFirst()) return next(c, id);
            int state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (state == DownloadManager.STATUS_FAILED) return next(c, id);
            if (state == DownloadManager.STATUS_SUCCESSFUL) {
                if (!valid(c, file, p.getString("version", ""))) return next(c, id);
                p.edit().putBoolean("ready", true).commit();
                notifyReady(c, p.getString("version", ""));
                return new Status(100, file);
            }
            long bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            // A paused transfer is still pending, not a failed update. The OS resumes it.
            return new Status(total > 0 ? (int)Math.min(99, bytes * 100 / total) : 0, null);
        } catch (RuntimeException e) {
            dev.t1m3.qplayer.util.Logger.warn("query update failed: {}", e.toString());
            return new Status(0, null);
        }
    }

    private static Status next(Context c, long id) {
        manager(c).remove(id);
        SharedPreferences p = prefs(c);
        p.edit().putInt("index", p.getInt("index", 0) + 1).commit();
        enqueue(c);
        return new Status(p.getBoolean("failed", false) ? -2 : 0, null);
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
    static synchronized boolean owns(Context c, long id) { return id >= 0 && prefs(c).getLong("id", -1) == id; }
    static synchronized void requestInstall(Context c) { prefs(c).edit().putBoolean("delivered", false).commit(); }

    private static void notifyReady(Context c, String version) {
        NotificationManager nm = (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "软件更新", NotificationManager.IMPORTANCE_DEFAULT));
        Intent intent = new Intent(c, QPlayerActivity.class).setAction("dev.t1m3.qplayer.INSTALL_UPDATE");
        PendingIntent open = PendingIntent.getActivity(c, 7294, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            nm.notify(NOTIFICATION, new NotificationCompat.Builder(c, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("QPlayer " + version + " 已下载")
                    .setContentText("点击返回 QPlayer 安装更新").setContentIntent(open).setAutoCancel(true).build());
        } catch (SecurityException ignored) { /* Returning to the app also discovers the ready package. */ }
    }
}
