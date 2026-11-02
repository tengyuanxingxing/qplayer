package dev.t1m3.qplayer.android.ui;

import android.app.Application;
import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import java.io.File;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowDownloadManager;
import org.robolectric.shadows.ShadowPackageManager;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=27, application=Application.class)
public class UpdateDownloadsTest {
    private Context app;
    private SharedPreferences state;
    private ShadowDownloadManager downloads;
    private static final String RAW="https://github.com/tengyuanxingxing/qplayer/releases/download/v1.9.15-custom/QPlayer-1.9.15-custom-debug.apk";
    @Before public void setup() {
        app=RuntimeEnvironment.getApplication();
        state=app.getSharedPreferences("update-download", Context.MODE_PRIVATE);
        state.edit().clear().commit();
        downloads=Shadow.extract((DownloadManager)app.getSystemService(Context.DOWNLOAD_SERVICE));
    }
    private long id() { return state.getLong("id", -1); }
    private ShadowDownloadManager.ShadowRequest request() { return Shadow.extract(downloads.getRequest(id())); }

    @Test public void pausedSystemDownloadSurvivesNewContextAndKeepsSameJob() {
        UpdateDownloads.start(app,new String[]{RAW});long first=id();
        request().setStatus(DownloadManager.STATUS_PAUSED);request().setTotalSize(100);request().setBytesSoFar(42);
        // Reattach through only persisted state, as after leaving/recreating the Activity.
        Context recreated=app.createConfigurationContext(app.getResources().getConfiguration());
        assertEquals(42,UpdateDownloads.inspect(recreated).progress);
        UpdateDownloads.start(recreated,new String[]{RAW});
        assertEquals(first,id());assertEquals(1,downloads.getRequestCount());
        request().setStatus(DownloadManager.STATUS_RUNNING);request().setBytesSoFar(64);
        assertEquals(64,UpdateDownloads.inspect(recreated).progress);
    }
    @Test public void failedMirrorCreatesUniqueFileAndContinuesWithNextSource() {
        UpdateDownloads.start(app,new String[]{"https://gh-proxy.com/"+RAW,RAW});
        String firstPath=state.getString("path", "");long first=id();
        request().setStatus(DownloadManager.STATUS_FAILED);
        assertEquals(0,UpdateDownloads.inspect(app).progress);
        assertNotEquals(first,id());assertNotEquals(firstPath,state.getString("path", ""));
        assertEquals(RAW,request().getUri().toString());
        assertTrue(state.getString("path", "").contains("1.9.15-custom"));
    }
    @Test public void aNewerButWrongVersionNeverReachesInstaller() throws Exception {
        PackageInfo installed=info("1.9.12-custom-debug",111);
        ShadowPackageManager pm=Shadow.extract(app.getPackageManager());pm.installPackage(installed);
        UpdateDownloads.start(app,new String[]{"https://gh-proxy.com/"+RAW,RAW});
        File file=new File(state.getString("path", ""));file.getParentFile().mkdirs();file.createNewFile();
        pm.setPackageArchiveInfo(file.getAbsolutePath(),info("1.9.13-custom-debug",112));
        request().setStatus(DownloadManager.STATUS_SUCCESSFUL);long first=id();
        UpdateDownloads.Status status=UpdateDownloads.inspect(app);
        assertNull(status.ready);assertEquals(0,status.progress);assertNotEquals(first,id());
        assertFalse(UpdateDownloads.validReady(app,file));
    }
    @Test public void correctVersionIsSavedForForegroundInstallAndRechecked() throws Exception {
        ShadowPackageManager pm=Shadow.extract(app.getPackageManager());pm.installPackage(info("1.9.13-custom-debug",112));
        UpdateDownloads.start(app,new String[]{RAW});
        File file=new File(state.getString("path", ""));file.getParentFile().mkdirs();file.createNewFile();
        pm.setPackageArchiveInfo(file.getAbsolutePath(),info("1.9.15-custom-debug",114));
        request().setStatus(DownloadManager.STATUS_SUCCESSFUL);
        assertEquals(file,UpdateDownloads.inspect(app).ready);
        assertTrue(UpdateDownloads.needsInstall(app));assertTrue(UpdateDownloads.validReady(app,file));
        UpdateDownloads.delivered(app);assertFalse(UpdateDownloads.needsInstall(app));
        UpdateDownloads.requestInstall(app);assertTrue(UpdateDownloads.needsInstall(app));
        pm.setPackageArchiveInfo(file.getAbsolutePath(),info("1.9.13-custom-debug",112));
        assertFalse(UpdateDownloads.validReady(app,file));
    }
    private PackageInfo info(String name,int version) {
        PackageInfo p=new PackageInfo();p.packageName=app.getPackageName();p.versionName=name;p.versionCode=version;
        p.signatures=new Signature[]{new Signature(new byte[]{1,2,3})};return p;
    }
}
