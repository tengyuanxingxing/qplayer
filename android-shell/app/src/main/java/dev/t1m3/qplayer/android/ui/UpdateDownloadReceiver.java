package dev.t1m3.qplayer.android.ui;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The download and mirror fallback survive the player Activity and its process. */
public final class UpdateDownloadReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(intent.getAction())) {
            long[] ids = intent.getLongArrayExtra(DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS);
            if (ids != null) for (long id : ids) if (UpdateDownloads.owns(context, id)) {
                context.startActivity(new Intent(context, QPlayerActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
            return;
        }
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        if (!UpdateDownloads.owns(context, id)) return;
        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        UpdateDownloads.IO.execute(() -> {
            try { UpdateDownloads.inspect(app); }
            finally { pending.finish(); }
        });
    }
}
