package dev.t1m3.qplayer.android.ui;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class AppUpdateTransferTest {
    @Test public void invalidCandidateFallsBackAndOnlyVerifiedBytesAreCommitted()throws Exception {
        try(ServerSocket server=new ServerSocket(0)) {
            ExecutorService peer=Executors.newSingleThreadExecutor();
            Future<?> producer=peer.submit(()->{try {for(int i=0;i<2;i++)try(Socket socket=server.accept()) {
                readHeaders(socket);byte[] body=i==0?new byte[]{1}:new byte[]{2,3,4};
                socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().write(body);
            }}catch(Exception e){throw new RuntimeException(e);}});
            try {
                String url="http://127.0.0.1:"+server.getLocalPort();File target=Files.createTempDirectory("update-transfer").resolve("new.apk").toFile();
                AppUpdateTransfer task=new AppUpdateTransfer();
                assertTrue(task.download(new String[]{url+"/invalid",url+"/valid"},target,(b,t)->{},f->f.length()==3));
                assertArrayEquals(new byte[]{2,3,4},Files.readAllBytes(target.toPath()));assertFalse(new File(target.getPath()+".part").exists());
                producer.get(3,TimeUnit.SECONDS);
            }finally{peer.shutdownNow();}
        }
    }
    @Test public void cancellationAbortsSocketAndDoesNotTryNextMirror()throws Exception {
        CountDownLatch accepted=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger attempts=new AtomicInteger();
        ExecutorService io=Executors.newFixedThreadPool(2);AppUpdateTransfer task=new AppUpdateTransfer();
        try(ServerSocket server=new ServerSocket(0)) {
            io.submit(()->{try(Socket socket=server.accept()){readHeaders(socket);attempts.incrementAndGet();accepted.countDown();release.await(5,TimeUnit.SECONDS);}catch(Exception ignored){}});
            File target=Files.createTempDirectory("update-cancel").resolve("new.apk").toFile();String url="http://127.0.0.1:"+server.getLocalPort()+"/slow";
            try {
                Future<Boolean> result=io.submit(()->task.download(new String[]{url,url},target,(b,t)->{},f->true));
                assertTrue(accepted.await(3,TimeUnit.SECONDS));task.cancel();
                try{result.get(3,TimeUnit.SECONDS);fail("cancel must not complete successfully");}catch(ExecutionException expected){}
                assertEquals(1,attempts.get());assertFalse(target.exists());assertFalse(new File(target.getPath()+".part").exists());
            }finally{release.countDown();}
        }finally{io.shutdownNow();}
    }
    private static void readHeaders(Socket socket)throws Exception {
        socket.setSoTimeout(3000);BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));
        String line;while((line=input.readLine())!=null && !line.isEmpty()){}
    }
    @Test public void slowSourcePolicyHasThirtySecondGraceAndAllowsNormalTraffic() {
        assertFalse(AppUpdateTransfer.tooSlow(TimeUnit.SECONDS.toNanos(29),0));
        assertTrue(AppUpdateTransfer.tooSlow(TimeUnit.SECONDS.toNanos(30),100));
        assertFalse(AppUpdateTransfer.tooSlow(TimeUnit.SECONDS.toNanos(30),1024*1024));
    }
}
