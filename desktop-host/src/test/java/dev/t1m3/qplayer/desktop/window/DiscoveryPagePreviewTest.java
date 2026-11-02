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
public class DiscoveryPagePreviewTest {
    @Test public void homeInfoAndArtistFiltersRenderAndRespondToPointers() throws Exception {
        String base=AppDirs.base(),cache=AppDirs.cacheBase(); PlayerController player=null; QmlView view=null;
        LyricCompositor compositor=new LyricCompositor();Path dir=Files.createTempDirectory("discovery-preview");
        try {
            AppDirs.setBase(dir.toString());AppDirs.setCacheBase(dir.resolve("cache").toString());
            AudioBackend backend=(AudioBackend)Proxy.newProxyInstance(AudioBackend.class.getClassLoader(),new Class<?>[]{AudioBackend.class},(p,m,a)->{
                if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==long.class)return 0L;return null;
            });
            player=new PlayerController(backend,t->{});player.sourceSetupRequired.set(false);player.sourceSetupPending.set(false);
            SettingsCore settings=new SettingsCore();settings.load(new JsonSettingsStore(),SettingsCatalog.DESKTOP);
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
                    assertEquals(2,prop(view.root(),"page").peekInt());
                    player.debugRouteType.set("tab");player.debugRouteId.set("0");player.debugRouteRevision.set(1L);settle(view);
                    // Finish the root page transition before installing the fixtures;
                    // entering Home legitimately initiates its first provider refresh.
                    for(int i=0;i<55;i++){view.tickAnimations(System.nanoTime()+1_000_000_000L);settle(view);Thread.sleep(8);}
                    assertEquals(0,prop(view.root(),"page").peekInt());
                    player.sourceContentActive.set(true);player.qqGuessSongs.set(songs);player.qqGuessStatus.set("QQ 音乐个性电台 · 界面预览");
                    player.qqDailySongs.set(songs);player.qqDailyStatus.set("QQ 音乐每日30首 · 界面预览");player.heartExploreSongs.set(songs);
                    player.heartExploreReason.set("根据红心收藏中的歌手探索 · 预览数据");player.title.set("在路上");player.artist.set("示例音乐人");
                    player.playbackRelatedSongs.set(songs);player.coverPath.set(art.toString());player.coverSeed.set("#618AC0");
                    snapshot(view,player,settings,compositor,"home-390",0);
                    assertEquals("source fixture survives home loading",30,player.qqGuessSongs.peek().size());
                    assertEquals("daily uses its own independent model",30,player.qqDailySongs.peek().size());
                    Item home=view.findByObjectName("homeDiscoveryPage");
                    Item section=view.findByObjectName("homeSongSection");
                    assertTrue("Home section enabled",Boolean.TRUE.equals(prop(section,"pageActive").peek()));
                    assertTrue("Home section renders actual song rows",hasSongRow(section));
                    Item carousel=view.findByObjectName("homeSongCarousel");
                    assertNotNull(carousel);
                    float columns=((Number)prop(carousel,"contentWidth").peek()).floatValue();
                    assertTrue("thirty songs span ten groups horizontally",columns>carousel.width.peekFloat()*8);
                    assertEquals("still only three visible rows",228f,carousel.height.peekFloat(),0.1f);
                    prop(carousel,"contentX").set(columns-carousel.width.peekFloat());settle(view);
                    assertTrue("last group is virtualized into the actual scene",hasSongRow(carousel));
                    assertTrue("virtualization bounds live row count",countNamed(carousel,"songRowTitle")<=9);
                    snapshot(view,player,settings,compositor,"home-last-group-390",0);
                    prop(carousel,"contentX").set(0f);settle(view);
                    player.qqGuessSongs.set(new ArrayList<>());settle(view);
                    assertTrue("empty recommendations collapse",section.height.peekFloat()<110);
                    snapshot(view,player,settings,compositor,"home-empty-390",0);
                    player.qqGuessSongs.set(songs);settle(view);
                    Item homeScroll=view.findByObjectName("homeRecommendationsScroll");assertNotNull(homeScroll);
                    prop(homeScroll,"contentY").set(600f);snapshot(view,player,settings,compositor,"home-hearts-390",0);
                    player.lyricsOpen.set(true);player.setCoverMode(true);player.swipePlaybackPage(-1);
                    player.playbackAlbumName.set("沿途 · 预览专辑");player.playbackRelease.set("2026-11-02");player.playbackCredits.set("词 / 曲：示例音乐人");
                    player.playbackGenre.set("流行");player.playbackRelatedSongs.set(songs.subList(0,4));
                    assertEquals(0,player.playbackPage.peek().intValue());
                    snapshot(view,player,settings,compositor,"song-info-390",0);
                    player.swipePlaybackPage(1);assertEquals(1,player.playbackPage.peek().intValue());
                    player.swipePlaybackPage(1);assertEquals(2,player.playbackPage.peek().intValue());
                    player.lyricsOpen.set(false);settle(view);
                    player.openSourceArtistId.set("qq:artist:preview");player.artistName.set("示例音乐人");player.artistCoverPath.set(art.toString());player.artistBriefDesc.set("歌手主页布局预览，歌曲与专辑来自测试数据。");
                    player.sourceArtistSongs.set(songs);List<Album> albums=new ArrayList<>();
                    String[] types={"album","live","ep","single"};for(int i=0;i<4;i++){Album a=new Album();a.id="qq:album:preview"+i;a.name=new String[]{"沿途","剧场现场","日落 EP","新的旅程"}[i];a.releaseType=types[i];a.coverUrl=art.toString();a.trackCount=4;a.publishTimeMs=1793577600000L-i*86400000L;albums.add(a);}
                    java.util.Collections.reverse(albums);
                    player.sourceArtistAlbums.set(albums);player.pageNavigationTarget.set("artist");player.pageNavigationEntityId.set("qq:artist:preview");player.pageNavigationRevision.set(1L);settle(view);
                    snapshot(view,player,settings,compositor,"artist-songs-390",0);
                    Item tab=view.findByObjectName("artistContentTabs");assertNotNull(tab);Item page=view.findByObjectName("artistDetailPage");
                    prop(page,"albumsTab").set(true);snapshot(view,player,settings,compositor,"artist-albums-390",0);
                    assertEquals("album order is newest first","沿途",prop(view.findByObjectName("artistAlbumTitle"),"text").peek());
                    Item artistScroll=view.findByObjectName("artistDetailScroll");
                    prop(artistScroll,"contentY").set(160f);settle(view);
                    java.util.Map<String,Object> heroPos=view.root().mapFromItem(view.findByObjectName("artistHero"),0,0);
                    assertTrue("artist hero scrolls off the screen",((Number)heroPos.get("y")).floatValue()<0);
                    snapshot(view,player,settings,compositor,"artist-scrolled-390",0);
                    prop(artistScroll,"contentY").set(0f);settle(view);
                    click(view,view.findByObjectName("artistAlbumFilterButton"));settle(view);
                    assertTrue(view.findByObjectName("artistAlbumFilterSheet").visible.peek());
                    snapshot(view,player,settings,compositor,"artist-filters-390",0);
                    prop(page,"albumFilter").set("ep");prop(page,"filtersOpen").set(false);settle(view);
                    Object filtered=prop(page,"albums").peek();assertNotNull(filtered);
                    assertEquals("EP filter contains only the EP release",1,countNamed(page,"artistAlbumRow"));
                    snapshot(view,player,settings,compositor,"artist-ep-390",0);
                } finally {q.uninstall();}
            }
        } finally {compositor.dispose();if(view!=null)view.dispose();if(player!=null)player.shutdownAsync().join();AppDirs.setBase(base);AppDirs.setCacheBase(cache);}
    }
    private static void snapshot(QmlView view,PlayerController player,SettingsCore settings,LyricCompositor c,String name,int unused)throws Exception {
        try(Surface surface=Surface.makeRasterN32Premul(390,844)){
            for(int i=0;i<45;i++){view.tickAnimations(System.nanoTime()+1_000_000_000L);settle(view);c.composite(surface.getCanvas(),view.renderer(),view,player,settings,null,1f,390,844);Thread.sleep(8);}
            String out=System.getProperty("qplayer.preview.dir");if(out!=null){Files.createDirectories(Paths.get(out));try(Image im=surface.makeImageSnapshot();Data data=im.encodeToData()){Files.write(Paths.get(out,name+".png"),data.getBytes());}}
        }
    }
    @SuppressWarnings("unchecked") private static Property<Object> prop(Item item,String name)throws Exception{return (Property<Object>)item.getClass().getField(name).get(item);}
    private static void settle(QmlView view){for(int i=0;i<4;i++){view.dirtyQueue().flush();view.renderer().layoutOnly(view.root());}view.dirtyQueue().flush();}
    private static boolean hasSongRow(Item item){if("songRowTitle".equals(item.objectName.peek()))return true;for(Item child:item.children)if(hasSongRow(child))return true;return false;}
    private static int countNamed(Item item,String name){int n=name.equals(item.objectName.peek())?1:0;for(Item child:item.children)n+=countNamed(child,name);return n;}
    private static void click(QmlView view,Item item){assertNotNull(item);Map<String,Object> p=view.root().mapFromItem(item,item.width.peekFloat()/2,item.height.peekFloat()/2);float x=((Number)p.get("x")).floatValue(),y=((Number)p.get("y")).floatValue();view.dispatchPointerDown(x,y);view.dispatchPointerUp(x,y);view.dirtyQueue().flush();}
}
