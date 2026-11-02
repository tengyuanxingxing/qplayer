package dev.t1m3.qplayer.bridge;

import com.google.gson.Gson;
import dev.t1m3.qplayer.audio.AudioBackend;
import dev.t1m3.qplayer.netease.NeteaseClient;
import dev.t1m3.qplayer.plugin.PluginInstaller;
import dev.t1m3.qplayer.plugin.PluginManager;
import dev.t1m3.qplayer.plugin.PluginManifest;
import dev.t1m3.qplayer.plugin.PluginPackageVerifier;
import dev.t1m3.qplayer.plugin.PluginPermission;
import dev.t1m3.qplayer.plugin.PluginRegistry;
import dev.t1m3.qplayer.store.AppDirs;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

/** Navigation reuses the process batch; explicit refresh and identity changes use new work. */
public class HomeRecommendationCacheTest {
    @Rule public final TemporaryFolder temporary=new TemporaryFolder();
    @Rule public final ControllerTestLifecycle lifecycle=new ControllerTestLifecycle();
    private String oldBase,oldCache,oldData;
    private Path base;
    private PlayerController controller;
    private PluginManager manager;

    @Before public void isolate() throws Exception {
        oldBase=AppDirs.base();oldCache=AppDirs.cacheBase();oldData=System.getProperty("qplayer.data.dir");
        base=temporary.newFolder("home-cache").toPath();AppDirs.setBase(base.toString());AppDirs.setCacheBase(base.resolve("cache").toString());
        System.setProperty("qplayer.data.dir",base.toString());
    }
    @After public void close() {
        try {
            if(manager!=null)for(PluginManifest manifest:manager.enabledProviders())try { control(manifest.id,"release",true); }catch(Exception ignored) { }
            if(controller!=null)lifecycle.close(controller);
        } finally {
            AppDirs.setBase(oldBase);AppDirs.setCacheBase(oldCache);
            if(oldData==null)System.clearProperty("qplayer.data.dir");else System.setProperty("qplayer.data.dir",oldData);
        }
    }

    @Test public void repeatedHomeNavigationPreservesGuessDailyAndHeartBatches() throws Exception {
        install("qq");start();
        controller.refreshUnifiedFavorites();
        await(()->!controller.unifiedFavoritesSyncing.peek() && !controller.heartExploreSongs.peek().isEmpty());
        Object guesses=controller.qqGuessSongs.peek(),daily=controller.qqDailySongs.peek(),hearts=controller.heartExploreSongs.peek();
        int calls=count("qq","homes"),searches=count("qq","searches");
        for(int i=0;i<15;i++)controller.ensureHomeLoaded();
        control("qq","snapshot",true);controller.pump();
        assertEquals(calls,count("qq","homes"));assertEquals(searches,count("qq","searches"));
        assertSame(guesses,controller.qqGuessSongs.peek());assertSame(daily,controller.qqDailySongs.peek());assertSame(hearts,controller.heartExploreSongs.peek());
        assertFalse(controller.homeLoading.peek());

        controller.setPrimarySourcePlugin("qq");
        control("qq","snapshot",true);controller.pump();
        assertEquals("reselecting the same primary is navigation, not a new batch",calls,count("qq","homes"));
    }

    @Test public void manualRefreshFetchesANewBatchButReturningWhileItLoadsDoesNotDuplicateIt() throws Exception {
        install("qq");start();int previous=count("qq","homes");String before=controller.qqGuessSongs.peek().get(0).name;
        control("qq","holdHome",true);controller.loadHome();
        assertEquals(1,count("qq","pending"));
        for(int i=0;i<10;i++)controller.ensureHomeLoaded();
        assertEquals(previous+1,count("qq","homes"));assertEquals(before,controller.qqGuessSongs.peek().get(0).name);
        assertTrue(controller.homeLoading.peek());
        control("qq","release",true);
        await(()->!controller.homeLoading.peek() && !before.equals(controller.qqGuessSongs.peek().get(0).name));
        assertEquals(previous+1,count("qq","homes"));
    }

    @Test public void changingTheActualPrimaryInvalidatesTheBatchOnlyOnce() throws Exception {
        install("qq");install("netease");new PluginRegistry().setPrimaryProvider("qq");start();
        int qq=count("qq","homes");controller.setPrimarySourcePlugin("netease");
        await(()->!controller.homeLoading.peek() && !controller.sourceRecommendations.peek().isEmpty()
                && controller.sourceRecommendations.peek().get(0).id.startsWith("netease:"));
        assertEquals(qq+1,count("qq","homes"));assertEquals(1,count("netease","homes"));
        controller.setPrimarySourcePlugin("netease");controller.ensureHomeLoaded();
        control("netease","snapshot",true);controller.pump();
        assertEquals(qq+1,count("qq","homes"));assertEquals(1,count("netease","homes"));
    }

    @Test public void changingAccountsRejectsAnOlderHeldHomeResponse() throws Exception {
        install("qq");start();control("qq","holdHome",true);controller.loadHome();
        assertEquals(1,count("qq","pending"));
        control("qq","account","second");refreshAccounts();
        await(()->!controller.homeLoading.peek() && !controller.qqGuessSongs.peek().isEmpty()
                && controller.qqGuessSongs.peek().get(0).name.startsWith("second"));
        String current=controller.qqGuessSongs.peek().get(0).name;
        control("qq","release",true);control("qq","snapshot",true);controller.pump();
        assertEquals(current,controller.qqGuessSongs.peek().get(0).name);
        controller.ensureHomeLoaded();assertEquals(current,controller.qqGuessSongs.peek().get(0).name);
    }

    @Test public void logoutCannotRestoreTheOldUsersRecommendationsWhenAResponseArrivesLate() throws Exception {
        install("qq");start();control("qq","holdHome",true);controller.loadHome();assertEquals(1,count("qq","pending"));
        controller.logoutSource("qq");
        await(()->controller.sourceAccounts.peek().stream().anyMatch(row->"qq".equals(row.providerId)&&!row.loggedIn));
        controller.ensureHomeLoaded();await(()->!controller.homeLoading.peek());
        control("qq","release",true);control("qq","snapshot",true);controller.pump();
        assertTrue(controller.qqGuessSongs.peek().isEmpty());assertTrue(controller.qqDailySongs.peek().isEmpty());assertTrue(controller.heartExploreSongs.peek().isEmpty());
        assertFalse(controller.homeLoading.peek());
    }

    private void start() throws Exception {
        controller=new PlayerController(new SilentBackend(),track->{},NeteaseClient.INSTANCE);
        Field field=PlayerController.class.getDeclaredField("pluginManager");field.setAccessible(true);manager=(PluginManager)field.get(controller);
        await(()->!controller.homeLoading.peek()&&!controller.qqGuessSongs.peek().isEmpty()
                &&controller.sourceAccounts.peek().stream().anyMatch(row->"qq".equals(row.providerId)&&row.loggedIn));
    }
    private void refreshAccounts() throws Exception {
        Method method=PlayerController.class.getDeclaredMethod("refreshSourceAccounts");method.setAccessible(true);
        synchronized(dev.t1m3.qplayer.qml.QmlRuntimeLock.MONITOR){method.invoke(controller);}
    }
    private int count(String provider,String key) throws Exception {return ((Number)control(provider,"snapshot",true).get(key)).intValue();}
    private Map<?,?> control(String provider,String key,Object value) throws Exception {
        return (Map<?,?>)manager.invoke(provider,"control",Collections.singletonMap(key,value)).get(5,TimeUnit.SECONDS);
    }
    private void await(BooleanSupplier ready) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<deadline){controller.pump();if(ready.getAsBoolean())return;Thread.sleep(10L);}
        fail("timed out waiting for home batch state");
    }
    private void install(String id) throws Exception {
        PluginManifest manifest=new PluginManifest();manifest.schemaVersion=1;manifest.id=id;manifest.name=id;manifest.version="1.0.0";manifest.apiVersion="1.0";
        manifest.capabilities.addAll(Arrays.asList("home","account","login","like","songDetails","searchSongs"));manifest.permissions.add("credentials");
        byte[] json=new Gson().toJson(manifest).getBytes(StandardCharsets.UTF_8);
        byte[] script=("var account='first',homes=0,searches=0,hold=false,pending=[];module.exports={handlers:{"
            +"account:function(){return account?{loggedIn:true,id:account,displayName:account}:{loggedIn:false};},"
            +"login:function(a){if(a.operation==='methods')return [];if(a.operation==='logout')account='';return true;},"
            +"like:function(){return account?['seed']:[];},"
            +"songDetails:function(){return [{id:'seed',title:'Seed',artists:[{id:'artist',name:'Artist'}]}];},"
            +"searchSongs:function(){searches++;return {items:[{id:'heart'+searches,title:'Explore '+searches}],nextCursor:''};},"
            +"home:function(){homes++;var n=homes;var result={songs:account?[{id:'guess'+n,title:account+' guess '+n}]:[],"
            +"dailySongs:account?[{id:'daily'+n,title:account+' daily '+n}]:[],recommendationStatus:'fixture',dailyStatus:'fixture',playlists:[],sections:[]};"
            +"if(hold){hold=false;return new Promise(function(resolve){pending.push(function(){resolve(result);});});}return result;},"
            +"control:function(a){if(a.holdHome)hold=true;if(Object.prototype.hasOwnProperty.call(a,'account'))account=a.account;"
            +"if(a.release){var copy=pending;pending=[];copy.forEach(function(f){f();});}return {homes:homes,searches:searches,pending:pending.length};}}};")
            .getBytes(StandardCharsets.UTF_8);
        Map<String,String> hashes=new LinkedHashMap<>();hashes.put("plugin.json",hex(json));hashes.put("main.js",hex(script));
        Path archive=base.resolve(id+".qplug");
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(archive))){put(zip,"plugin.json",json);put(zip,"main.js",script);put(zip,PluginPackageVerifier.HASHES_PATH,new Gson().toJson(hashes).getBytes(StandardCharsets.UTF_8));}
        new PluginInstaller(new PluginRegistry()).install(new PluginPackageVerifier().verify(archive,null,true),Collections.singleton(PluginPermission.CREDENTIALS));
    }
    private static String hex(byte[] bytes)throws Exception {StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
    private static void put(ZipOutputStream zip,String name,byte[] data)throws Exception {zip.putNextEntry(new ZipEntry(name));zip.write(data);zip.closeEntry();}
    private static final class SilentBackend implements AudioBackend {
        @Override public void play(String source,long startMs) { }
        @Override public void pause() { }
        @Override public void resume() { }
        @Override public boolean isPlaying() {return false;}
        @Override public void seek(long ms) { }
        @Override public long position() {return 0L;}
        @Override public long duration() {return 0L;}
        @Override public void setVolume(float volume) { }
        @Override public void setOnComplete(Runnable callback) { }
        @Override public void setOnStarted(Runnable callback) { }
        @Override public void release() { }
    }
}
