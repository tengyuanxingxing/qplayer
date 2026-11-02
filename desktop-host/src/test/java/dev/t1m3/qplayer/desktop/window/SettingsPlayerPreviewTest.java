package dev.t1m3.qplayer.desktop.window;

import dev.t1m3.qplayer.audio.AudioBackend;
import dev.t1m3.qplayer.bridge.*;
import dev.t1m3.qplayer.desktop.resources.ClasspathResourceLoader;
import dev.t1m3.qplayer.desktop.settings.JsonSettingsStore;
import dev.t1m3.qplayer.i18n.I18n;
import dev.t1m3.qplayer.lyric.skia.LyricCompositor;
import dev.t1m3.qplayer.media.*;
import dev.t1m3.qplayer.qml.BundledQml;
import dev.t1m3.qplayer.settings.*;
import dev.t1m3.qplayer.store.AppDirs;
import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.*;
import io.github.timer_err.qml4j.render.QmlView;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.humbleui.skija.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Phone-size production AOT scene, with visibly identified fixtures, not remote account data. */
public class SettingsPlayerPreviewTest {
    @Test public void settingsAndQualityRespondInBothOrientations() throws Exception {
        String base=AppDirs.base(),cache=AppDirs.cacheBase(); PlayerController player=null; QmlView view=null;
        LyricCompositor compositor=new LyricCompositor();Path dir=Files.createTempDirectory("discovery-preview");
        try {
            AppDirs.setBase(dir.toString());AppDirs.setCacheBase(dir.resolve("cache").toString());
            AudioBackend backend=(AudioBackend)Proxy.newProxyInstance(AudioBackend.class.getClassLoader(),new Class<?>[]{AudioBackend.class},(p,m,a)->{
                if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==long.class)return 0L;return null;
            });
            player=new PlayerController(backend,t->{});player.sourceSetupRequired.set(false);player.sourceSetupPending.set(false);
            SettingsCore settings=new SettingsCore();settings.load(new JsonSettingsStore(),SettingsCatalog.ANDROID);
            Path art=dir.resolve("sample.png");
            try(Surface s=Surface.makeRasterN32Premul(256,256);Paint p=new Paint()){
                s.getCanvas().clear(0xFF8DAFD5);p.setColor(0xFF345E88);s.getCanvas().drawCircle(128,128,92,p);
                p.setColor(0xFFB8D7E9);s.getCanvas().drawCircle(128,128,38,p);
                try(Image im=s.makeImageSnapshot();Data data=im.encodeToData()){Files.write(art,data.getBytes());}
            }
            List<Song> songs=new ArrayList<>();
            for(int i=0;i<30;i++){Song s=new Song();s.id="qq:song:preview"+i;s.name=s.title=new String[]{"在路上","远方的微光","晚风","晴天日记"}[i%4];s.artist="示例音乐人 · 界面预览";s.coverUrl=s.artworkUrl=s.coverThumbPath=art.toString();songs.add(s);}
            try(BundledQml bundle=new BundledQml("Main.qml")) {
                ClasspathResourceLoader resources=new ClasspathResourceLoader();
                view=bundle.configure(QmlView.withStockTypes(new QmlEngine(bundle)),resources);
                view.context("player",player).context("settings",settings).context("i18n",I18n.instance()).context("hostWindow",new WindowChromeStub());
                DesktopWindow.loadFonts(view,resources);bundle.load(view);DirtyQueue q=view.dirtyQueue();q.install();
                try {
                    view.root().width.set(390);view.root().height.set(844);settle(view);
                    player.debugRouteType.set("settings");player.debugRouteRevision.set(1L);settle(view);
                    frame(view,player,settings,compositor,"settings-home-390");
                    Item home=view.findByObjectName("settingsHome");assertNotNull(home);
                    assertNull("horizontal category tabs removed",view.findByObjectName("settingsCategoryTabs"));
                    click(view,view.findByObjectName("settingsCategory_playback"));settle(view);
                    assertEquals("playback",prop(home,"activeCategory").peek());
                    frame(view,player,settings,compositor,"settings-playback-390");
                    player.backTick.set(player.backTick.peek()+1);settle(view);assertEquals("",prop(home,"activeCategory").peek());
                    // Android-only switch must mirror stored state and be reachable.
                    Item scroll=view.findByObjectName("settingsHomeScroll");
                    prop(scroll,"contentY").set(200f);settle(view);
                    Item rotate=view.findByObjectName("settingsAutoRotate");
                    // There are three delegates; select the visible one.
                    rotate=findVisible(home,"settingsAutoRotate");assertNotNull(rotate);
                    boolean before=settings.bool(SettingsCatalog.AUTO_ROTATE_KEY);
                    click(view,rotate);settle(view);assertEquals(!before,settings.bool(SettingsCatalog.AUTO_ROTATE_KEY));
                    player.title.set("在路上");player.artist.set("示例音乐人");player.coverPath.set(art.toString());player.coverSeed.set("#618AC0");
                    player.lyricsOpen.set(true);player.setCoverMode(true);settle(view);
                    frame(view,player,settings,compositor,"player-cover-390");
                    java.lang.reflect.Field sessionField=PlayerController.class.getDeclaredField("playbackSession");sessionField.setAccessible(true);
                    Object session=sessionField.get(player);
                    java.lang.reflect.Field queueField=session.getClass().getDeclaredField("queue");queueField.setAccessible(true);
                    java.util.List<dev.t1m3.qplayer.model.Track> queue=(java.util.List<dev.t1m3.qplayer.model.Track>)queueField.get(session);
                    dev.t1m3.qplayer.model.Track fixture=new dev.t1m3.qplayer.model.Track();fixture.applyCanonicalId("qq:song:fixture");fixture.source=dev.t1m3.qplayer.model.Track.Source.PLUGIN;queue.clear();queue.add(fixture);
                    java.lang.reflect.Field indexField=session.getClass().getDeclaredField("index");indexField.setAccessible(true);indexField.setInt(session,0);
                    player.currentMediaId.set("qq:song:fixture");settle(view);
                    click(view,view.findByObjectName("playbackQualityButton"));settle(view);
                    Item sheet=view.findByObjectName("playbackQualitySheet");assertTrue(sheet.visible.peek());
                    frame(view,player,settings,compositor,"player-quality-390");
                    player.backTick.set(player.backTick.peek()+1);settle(view);assertFalse(sheet.visible.peek());assertTrue(player.lyricsOpen.peek());
                    click(view,view.findByObjectName("playbackQualityButton"));settle(view);
                    assertTrue((Boolean)prop(sheet,"available").peek());
                    click(view,view.findByObjectName("playbackQualityChoice"));settle(view);
                    assertEquals(2,((Number)settings.value("playbackQuality")).intValue());assertFalse(sheet.visible.peek());
                    view.root().width.set(844);view.root().height.set(390);settle(view);
                    frame(view,player,settings,compositor,"player-landscape-844");
                    click(view,view.findByObjectName("playbackQualityButton"));settle(view);assertTrue(sheet.visible.peek());
                    Item card=view.findByObjectName("playbackQualityCard");assertTrue(card.height.peekFloat()>280);
                    frame(view,player,settings,compositor,"player-quality-landscape-844");
                    player.backTick.set(player.backTick.peek()+1);settle(view);assertFalse(sheet.visible.peek());
                    player.lyricsOpen.set(false);settle(view);view.root().width.set(390);view.root().height.set(844);
                    player.openSourcePlaylistId.set("qplayer:playlist:unified-favorites");player.sourcePlaylistTracks.set(songs);player.playlistLoading.set(false);
                    player.debugRouteType.set("detail");player.debugRouteId.set("qplayer:playlist:unified-favorites");player.debugRouteRevision.set(2L);settle(view);
                    frame(view,player,settings,compositor,"favorites-songs-390");
                    Item tabs=view.findByObjectName("favoritesContentTabs");assertNotNull(tabs);assertTrue(tabs.visible.peek());
                    Item detail=view.findByObjectName("playlistDetailPage");
                    assertEquals(0,((Number)prop(view.findByObjectName("playlistDetailTracks"),"headerHeight").peek()).intValue());
                    Map<String,Object> pos=view.root().mapFromItem(tabs,tabs.width.peekFloat()*0.75f,tabs.height.peekFloat()/2);
                    float tx=((Number)pos.get("x")).floatValue(),ty=((Number)pos.get("y")).floatValue();
                    view.dispatchPointerDown(tx,ty);view.dispatchPointerUp(tx,ty);settle(view);assertTrue((Boolean)prop(detail,"albumsTab").peek());
                    Album favorite=new Album();favorite.id="qq:album:favorite";favorite.name="真正收藏的专辑 · 预览";favorite.coverThumbPath=art.toString();favorite.artistName="示例音乐人";favorite.trackCount=12;
                    player.favoriteAlbums.set(java.util.Arrays.asList(favorite));player.favoriteAlbumsStatus.set("");settle(view);
                    frame(view,player,settings,compositor,"favorites-albums-390");assertNotNull(view.findByObjectName("favoriteAlbumRow"));
                } finally {q.uninstall();}
            }
        } finally {compositor.dispose();if(view!=null)view.dispose();if(player!=null)player.shutdownAsync().join();AppDirs.setBase(base);AppDirs.setCacheBase(cache);}
    }
    private static Item findVisible(Item node,String name){if(name.equals(node.objectName.peek()) && node.visible.peek())return node;for(Item child:node.children){Item found=findVisible(child,name);if(found!=null)return found;}return null;}
    private static void frame(QmlView view,PlayerController player,SettingsCore settings,LyricCompositor c,String name)throws Exception {
        int w=Math.round(view.root().width.peekFloat()),h=Math.round(view.root().height.peekFloat());
        try(Surface surface=Surface.makeRasterN32Premul(w,h)){
            for(int i=0;i<45;i++){view.tickAnimations(System.nanoTime()+1_000_000_000L);settle(view);c.composite(surface.getCanvas(),view.renderer(),view,player,settings,null,1f,w,h);Thread.sleep(4);}
            String out=System.getProperty("qplayer.preview.dir");if(out!=null){Files.createDirectories(Paths.get(out));try(Image im=surface.makeImageSnapshot();Data data=im.encodeToData()){Files.write(Paths.get(out,name+".png"),data.getBytes());}}
        }
    }
    @SuppressWarnings("unchecked") private static Property<Object> prop(Item item,String name)throws Exception{return (Property<Object>)item.getClass().getField(name).get(item);}
    private static void settle(QmlView view){for(int i=0;i<4;i++){view.dirtyQueue().flush();view.renderer().layoutOnly(view.root());}view.dirtyQueue().flush();}
    private static void click(QmlView view,Item item){assertNotNull(item);Map<String,Object> p=view.root().mapFromItem(item,item.width.peekFloat()/2,item.height.peekFloat()/2);float x=((Number)p.get("x")).floatValue(),y=((Number)p.get("y")).floatValue();view.dispatchPointerDown(x,y);view.dispatchPointerUp(x,y);view.dirtyQueue().flush();}
}
