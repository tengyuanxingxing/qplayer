package dev.t1m3.qplayer.android.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Cancellable bounded transfer. Every candidate starts a clean file: no cross-mirror byte mixing. */
final class AppUpdateTransfer {
    interface Progress {void changed(long bytes,long total);}
    interface Validator {boolean valid(File file);}
    private static final long MAX_APK=256L*1024*1024;
    private final OkHttpClient client;
    private volatile boolean cancelled;
    private volatile Call active;
    AppUpdateTransfer() {this(new OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(12,TimeUnit.SECONDS)
            .writeTimeout(12,TimeUnit.SECONDS).build());}
    AppUpdateTransfer(OkHttpClient client) {this.client=client;}
    void cancel() {cancelled=true;Call call=active;if(call!=null)call.cancel();}
    private void check() throws IOException {if(cancelled || Thread.currentThread().isInterrupted())throw new IOException("Download cancelled");}
    boolean download(String[] urls,File target,Progress progress,Validator validator) throws IOException {
        if(!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs())throw new IOException("No update directory");
        File partial=new File(target.getPath()+".part");
        try {
            for(String url:urls) {
                check();long lastReport=0;long windowAt=System.nanoTime(),windowBytes=0;long received=0;
                try {
                    Call call=client.newCall(new Request.Builder().url(url).header("User-Agent","qplayer-updater")
                        .header("Cache-Control","no-cache").header("Accept-Encoding","identity").build());active=call;check();
                    try(Response response=call.execute()) {
                        if(!response.isSuccessful() || response.body()==null)throw new IOException("HTTP "+response.code());
                        long total=response.body().contentLength();if(total>MAX_APK)throw new IOException("APK too large");
                        try(InputStream input=response.body().byteStream();FileOutputStream output=new FileOutputStream(partial,false)) {
                            byte[] buffer=new byte[32*1024];int count;
                            while((count=input.read(buffer))!=-1) {
                                check();received+=count;if(received>MAX_APK)throw new IOException("APK too large");output.write(buffer,0,count);
                                long now=System.nanoTime();
                                if(now-lastReport>=TimeUnit.SECONDS.toNanos(1)) {progress.changed(received,total);lastReport=now;}
                                if(tooSlow(now-windowAt,received-windowBytes))throw new IOException("Download source too slow");
                                if(now-windowAt>=TimeUnit.SECONDS.toNanos(30)) {windowAt=now;windowBytes=received;}
                            }
                            output.getFD().sync();
                        }
                        if(total>0 && received!=total)throw new IOException("Truncated APK");check();
                        // Package identity/signature/version are validated before committing a ready file.
                        if(!validator.valid(partial))throw new IOException("APK verification failed");
                        check();Files.move(partial.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);progress.changed(received,received);return true;
                    }
                }catch(IOException error) {check();}
                finally {active=null;partial.delete();}
            }
            return false;
        }finally {active=null;partial.delete();}
    }
    static boolean tooSlow(long elapsedNanos,long bytes) {
        return elapsedNanos>=TimeUnit.SECONDS.toNanos(30) && bytes<8L*1024*30;
    }
}
