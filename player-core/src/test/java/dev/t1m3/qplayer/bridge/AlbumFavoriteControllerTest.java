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
import dev.t1m3.qplayer.plugin.SourceAccountRow;
import dev.t1m3.qplayer.store.AppDirs;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
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

/** Real JS plugin promises exercise album navigation, writes and account isolation offline. */
public class AlbumFavoriteControllerTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    @Rule public final ControllerTestLifecycle lifecycle = new ControllerTestLifecycle();
    private String oldBase, oldCache, oldData;
    private Path base;
    private PlayerController controller;
    private PluginManager manager;

    @Before public void isolate() throws Exception {
        oldBase=AppDirs.base(); oldCache=AppDirs.cacheBase(); oldData=System.getProperty("qplayer.data.dir");
        base=temporary.newFolder("album-favorite").toPath();
        AppDirs.setBase(base.toString()); AppDirs.setCacheBase(base.resolve("cache").toString());
        System.setProperty("qplayer.data.dir",base.toString());
        install();
        controller=new PlayerController(new SilentBackend(),track->{},NeteaseClient.INSTANCE);
        Field field=PlayerController.class.getDeclaredField("pluginManager"); field.setAccessible(true);
        manager=(PluginManager)field.get(controller);
        await(()->loggedIn());
    }

    @After public void close() {
        try {
            if(manager!=null) {
                try { control("release",true); } catch(Exception ignored) { }
            }
            if(controller!=null)lifecycle.close(controller);
        } finally {
            AppDirs.setBase(oldBase); AppDirs.setCacheBase(oldCache);
            if(oldData==null)System.clearProperty("qplayer.data.dir"); else System.setProperty("qplayer.data.dir",oldData);
        }
    }

    @Test public void repeatedCollectionTabSwitchesReuseEmptyAndLoadedResults() throws Exception {
        int before=((Number)snapshot().get("albumLoads")).intValue();
        controller.ensureFavoriteAlbumsLoaded();
        controller.ensureFavoriteAlbumsLoaded();
        await(()->!controller.favoriteAlbumsLoading.peek());
        assertEquals(before+1,((Number)snapshot().get("albumLoads")).intValue());
        controller.ensureFavoriteAlbumsLoaded();barrier();controller.pump();
        assertEquals(before+1,((Number)snapshot().get("albumLoads")).intValue());
        control("saved",true);
        controller.refreshFavoriteAlbums();
        await(()->!controller.favoriteAlbumsLoading.peek() && !controller.favoriteAlbums.peek().isEmpty());
        controller.ensureFavoriteAlbumsLoaded();barrier();controller.pump();
        assertEquals(before+2,((Number)snapshot().get("albumLoads")).intValue());
    }

    @Test public void searchedAlbumCanBeFavoritedAndUnfavoritedWithConfirmedPluginState() throws Exception {
        controller.searchAlbums("Fixture");
        await(()->!controller.searchLoading.peek() && !controller.sourceSearchAlbumResults.peek().isEmpty());
        String id=controller.sourceSearchAlbumResults.peek().get(0).id;
        assertEquals("fixture:album:a1",id);
        controller.openMediaAlbum(id);
        await(()->controller.albumFavoriteSupported.peek() && !controller.albumFavoriteBusy.peek());
        assertFalse(controller.albumFavorite.peek());
        assertEquals("Fixture album",controller.albumName.peek());
        assertEquals("Fixture Records",controller.albumCompany.peek());
        assertEquals("Full introduction",controller.albumDescription.peek());
        assertEquals("录音室版",controller.albumReleaseType.peek());
        assertEquals("2020-01-02",controller.albumPublishYear.peek());

        controller.toggleAlbumFavorite();
        await(()->!controller.albumFavoriteBusy.peek() && controller.albumFavorite.peek());
        assertEquals(Boolean.TRUE,snapshot().get("saved"));
        assertEquals("add",snapshot().get("lastWrite"));

        controller.toggleAlbumFavorite();
        await(()->!controller.albumFavoriteBusy.peek() && !controller.albumFavorite.peek());
        assertEquals(Boolean.FALSE,snapshot().get("saved"));
        assertEquals("remove",snapshot().get("lastWrite"));
        assertEquals(2,((Number)snapshot().get("writes")).intValue());
    }

    @Test public void refusedOrMismatchedWritesCannotInventAFavorite() throws Exception {
        openReady();
        for(String mode:Arrays.asList("throw","mismatch")) {
            control("failure",mode);
            int previous=((Number)snapshot().get("writes")).intValue();
            controller.toggleAlbumFavorite();
            await(()->!controller.albumFavoriteBusy.peek());
            assertFalse("a failed write cannot optimistically add an album",controller.albumFavorite.peek());
            Map<?,?> state=snapshot();
            assertEquals(previous+1,((Number)state.get("writes")).intValue());
            assertEquals(Boolean.FALSE,state.get("saved"));
            assertTrue("an explicit retry remains available",controller.albumFavoriteSupported.peek());
        }
    }

    @Test public void reopeningTheSameAlbumDiscardsItsEarlierStatusResponse() throws Exception {
        control("hold","status");
        controller.openMediaAlbum("fixture:album:a1");
        await(()->controller.albumFavoriteBusy.peek());
        assertEquals(1,((Number)snapshot().get("pending")).intValue());

        // The first response captured false. Reopening the very same ID now returns true.
        control("saved",true);
        openReady();
        assertTrue(controller.albumFavorite.peek());
        control("release",true);
        barrier(); controller.pump();
        assertTrue("old false status must not overwrite a reopened album",controller.albumFavorite.peek());
        assertTrue(controller.albumFavoriteSupported.peek());
        assertFalse(controller.albumFavoriteBusy.peek());
    }

    @Test public void logoutDiscardsAPendingSuccessfulAlbumWrite() throws Exception {
        openReady(); control("hold","add");
        controller.toggleAlbumFavorite();
        assertTrue(controller.albumFavoriteBusy.peek());
        assertEquals(1,((Number)snapshot().get("pending")).intValue());

        controller.logoutSource("fixture");
        await(()->!loggedIn() && !controller.albumFavoriteSupported.peek());
        assertFalse(controller.albumFavorite.peek());
        control("release",true);
        barrier(); controller.pump();
        assertFalse("old account's confirmed write cannot repopulate a signed-out UI",controller.albumFavorite.peek());
        assertFalse(controller.albumFavoriteSupported.peek());
        assertFalse(controller.albumFavoriteBusy.peek());
    }

    private void openReady() throws Exception {
        controller.openMediaAlbum("fixture:album:a1");
        await(()->controller.albumFavoriteSupported.peek() && !controller.albumFavoriteBusy.peek());
    }
    private boolean loggedIn() {
        for(SourceAccountRow row:controller.sourceAccounts.peek())if("fixture".equals(row.providerId))return row.loggedIn;
        return false;
    }
    private Map<?,?> snapshot() throws Exception { return control("snapshot",true); }
    private Map<?,?> control(String key,Object value) throws Exception {
        return (Map<?,?>)manager.invoke("fixture","control",Collections.singletonMap(key,value)).get(5,TimeUnit.SECONDS);
    }
    private void barrier() throws Exception { snapshot(); }
    private void await(BooleanSupplier ready) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<deadline) {
            controller.pump(); if(ready.getAsBoolean())return; Thread.sleep(10L);
        }
        fail("timed out waiting for album favorite controller state");
    }

    private void install() throws Exception {
        PluginManifest manifest=new PluginManifest();
        manifest.schemaVersion=1; manifest.id="fixture"; manifest.name="Fixture";
        manifest.version="1.0.0"; manifest.apiVersion="1.0";
        manifest.capabilities.addAll(Arrays.asList("searchAlbums","albumDetails","albumFavorite","favoriteAlbums","account","login"));
        manifest.permissions.add("credentials");
        byte[] json=new Gson().toJson(manifest).getBytes(StandardCharsets.UTF_8);
        byte[] script=("var saved=false,signedOut=false,failure='',hold='',pending=[],writes=0,lastWrite='',albumLoads=0;"
            +"function album(){return {id:'a1',name:'Fixture album',company:'Fixture Records',description:'Full introduction',releaseType:'专辑',releaseTypeLabel:'录音室版',publishTimeMs:1577894400000,songs:[]};}"
            +"module.exports={handlers:{"
            +"account:function(){return signedOut?{loggedIn:false}:{loggedIn:true,id:'offline',displayName:'Fixture user'};},"
            +"login:function(a){if(a.operation==='methods')return [];if(a.operation==='logout')signedOut=true;return true;},"
            +"searchAlbums:function(){return {items:[album()],nextCursor:''};},"
            +"albumDetails:function(a){if(a.id!=='a1')throw new Error('wrong native album id');return album();},"
            +"favoriteAlbums:function(){albumLoads++;return {items:saved?[album()]:[],nextCursor:''};},"
            +"albumFavorite:function(a){if(a.id!=='a1')throw new Error('wrong native album id');"
            +"if(a.operation!=='status'){writes++;lastWrite=a.operation;"
            +"if(failure==='throw')throw new Error('platform refused write');"
            +"if(failure==='mismatch')return {favorited:saved};saved=a.operation==='add';}"
            +"var result={favorited:saved};if(hold===a.operation){hold='';return new Promise(function(resolve){pending.push(function(){resolve(result);});});}return result;},"
            +"control:function(a){if(a.hold)hold=a.hold;if(a.failure)failure=a.failure;"
            +"if(Object.prototype.hasOwnProperty.call(a,'saved'))saved=a.saved;"
            +"if(a.release){var callbacks=pending;pending=[];callbacks.forEach(function(f){f();});}"
            +"return {saved:saved,pending:pending.length,writes:writes,lastWrite:lastWrite,albumLoads:albumLoads};}}};")
            .getBytes(StandardCharsets.UTF_8);
        Map<String,String> hashes=new LinkedHashMap<>(); hashes.put("plugin.json",hex(json)); hashes.put("main.js",hex(script));
        Path archive=base.resolve("fixture.qplug");
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(archive))) {
            put(zip,"plugin.json",json); put(zip,"main.js",script);
            put(zip,PluginPackageVerifier.HASHES_PATH,new Gson().toJson(hashes).getBytes(StandardCharsets.UTF_8));
        }
        new PluginInstaller(new PluginRegistry()).install(new PluginPackageVerifier().verify(archive,null,true),Collections.singleton(PluginPermission.CREDENTIALS));
    }
    private static String hex(byte[] bytes) throws Exception {
        StringBuilder out=new StringBuilder(); for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format(java.util.Locale.ROOT,"%02x",b&255)); return out.toString();
    }
    private static void put(ZipOutputStream zip,String name,byte[] body) throws Exception {
        zip.putNextEntry(new ZipEntry(name));zip.write(body);zip.closeEntry();
    }
    private static final class SilentBackend implements AudioBackend {
        @Override public void play(String source,long startMs) { }
        @Override public void pause() { }
        @Override public void resume() { }
        @Override public boolean isPlaying() { return false; }
        @Override public void seek(long ms) { }
        @Override public long position() { return 0L; }
        @Override public long duration() { return 0L; }
        @Override public void setVolume(float volume) { }
        @Override public void setOnComplete(Runnable callback) { }
        @Override public void setOnStarted(Runnable callback) { }
        @Override public void release() { }
    }
}
