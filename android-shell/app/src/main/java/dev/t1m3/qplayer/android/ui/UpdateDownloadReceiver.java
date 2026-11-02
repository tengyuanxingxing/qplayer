package dev.t1m3.qplayer.android.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Legacy class retained for upgrades. It may clean old state, never start a download. */
public final class UpdateDownloadReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {UpdateDownloads.migrateLegacy(context);}
}
