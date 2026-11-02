package dev.t1m3.qplayer.desktop.window;

import dev.t1m3.qplayer.audio.AudioBackend;
import dev.t1m3.qplayer.bridge.*;
import dev.t1m3.qplayer.desktop.resources.ClasspathResourceLoader;
import dev.t1m3.qplayer.desktop.settings.JsonSettingsStore;
import dev.t1m3.qplayer.i18n.I18n;
import dev.t1m3.qplayer.media.*;
import dev.t1m3.qplayer.qml.*;
import dev.t1m3.qplayer.settings.*;
import dev.t1m3.qplayer.store.AppDirs;
import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.*;
import io.github.timer_err.qml4j.render.QmlView;
import io.github.timer_err.qml4j.render.items.core.Item;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual production scene and EventDispatcher input; no direct synthetic startFling calls. */
public class TwoPagePagerInteractionTest {
    @Test public void collectionAndArtistUseTheSameReleaseCancelAndPersistentPaging() throws Exception {
        String oldBase=AppDirs.base(),oldCache=AppDirs.cacheBase();
        Path base=Files.createTempDirectory("pager-interaction");PlayerController player=null;QmlView view=null;
        try {
            AppDirs.setBase(base.toString());AppDirs.setCacheBase(base.resolve("cache").toString());
            AudioBackend backend=(AudioBackend)Proxy.newProxyInstance(AudioBackend.class.getClassLoader(),new Class<?>[]{AudioBackend.class},(p,m,a)->{if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==long.class)return 0L;return null;});
            player=new PlayerController(backend,t->{});player.sourceSetupRequired.set(false);player.sourceSetupPending.set(false);
            SettingsCore settings=new SettingsCore();settings.load(new JsonSettingsStore(),SettingsCatalog.ANDROID);
            try(BundledQml bundle=new BundledQml("Main.qml")) {
                ClasspathResourceLoader resources=new ClasspathResourceLoader();
                view=bundle.configure(QPlayerQmlTypes.newView(new QmlEngine(bundle)),resources);
                view.context("player",player).context("settings",settings).context("i18n",I18n.instance()).context("hostWindow",new WindowChromeStub());
                DesktopWindow.loadFonts(view,resources);bundle.load(view);DirtyQueue queue=view.dirtyQueue();queue.install();
                try {
                    view.root().width.set(390);view.root().height.set(844);settle(view);
                    List<Song> songs=new ArrayList<>();List<Album> albums=new ArrayList<>();
                    for(int i=0;i<40;i++){Song s=new Song();s.id="qq:song:"+i;s.name=s.title="歌曲 "+i;s.artist="示例歌手";songs.add(s);Album a=new Album();a.id="qq:album:"+i;a.name="专辑 "+i;a.releaseType="album";albums.add(a);}
                    player.openSourcePlaylistId.set("qplayer:playlist:unified-favorites");player.sourcePlaylistTracks.set(songs);player.playlistLoading.set(false);
                    route(view,player,"detail","qplayer:playlist:unified-favorites");
                    Item collection=view.findByObjectName("playlistDetailPage");PageFlickable pager=(PageFlickable)view.findByObjectName("collectionSwipePager");
                    assertNotNull(pager);shortReleaseAndCancel(view,pager,430);fastShortSwipes(view,pager,430);
                    Item tracks=view.findByObjectName("playlistDetailTracks");prop(tracks,"contentY").set(160f);settle(view);
                    drag(view,pager,430,-180,false);assertEquals(1,pager.pageIndex.peekInt());advance(view,240);
                    assertEquals(390f,pager.contentX.peekFloat(),0.01f);
                    player.favoriteAlbums.set(albums);settle(view);
                    Item favorites=view.findByObjectName("favoriteAlbumsList");prop(favorites,"contentY").set(350f);settle(view);
                    Item firstAlbumRow=view.findByObjectName("favoriteAlbumRow");
                    for(int i=0;i<3;i++){
                        drag(view,pager,430,180,false);advance(view,240);assertEquals(0,pager.pageIndex.peekInt());
                        assertEquals(160f,((Number)prop(tracks,"contentY").peek()).floatValue(),0.01f);
                        drag(view,pager,430,-180,false);advance(view,240);assertEquals(1,pager.pageIndex.peekInt());
                        assertEquals(350f,((Number)prop(favorites,"contentY").peek()).floatValue(),0.01f);
                        assertSame("tab changes retain album delegates",firstAlbumRow,view.findByObjectName("favoriteAlbumRow"));
                        assertSame("tab changes retain album model",albums,player.favoriteAlbums.peek());
                    }
                    long hiddenDrag=System.nanoTime();view.dispatchPointerDown(60,430,hiddenDrag);view.dispatchPointerMove(125,430,hiddenDrag+100_000_000L);settle(view);
                    assertTrue(pager.contentX.peekFloat()<390f);
                    prop(collection,"visible").set(false);settle(view);view.dispatchPointerCancel();prop(collection,"visible").set(true);settle(view);
                    assertEquals("hide/show never leaves half a page",390f,pager.contentX.peekFloat(),0.01f);
                    player.openSourceArtistId.set("qq:artist:fixture");player.artistName.set("示例歌手");player.sourceArtistSongs.set(songs);player.sourceArtistAlbums.set(albums);player.artistLoading.set(false);
                    player.pageNavigationTarget.set("artist");player.pageNavigationEntityId.set("qq:artist:fixture");player.pageNavigationRevision.set(player.pageNavigationRevision.peek()+1);settle(view);advance(view,500);
                    Item artist=view.findByObjectName("artistDetailPage"),artistScroll=view.findByObjectName("artistDetailScroll");
                    PageFlickable artistPager=(PageFlickable)view.findByObjectName("artistSwipePager");assertNotNull(artistPager);
                    shortReleaseAndCancel(view,artistPager,450);
                    prop(artistScroll,"contentY").set(170f);settle(view);
                    drag(view,artistPager,450,-180,false);advance(view,240);
                    assertTrue((Boolean)prop(artist,"albumsTab").peek());assertEquals(390f,artistPager.contentX.peekFloat(),0.01f);
                    prop(artistScroll,"contentY").set(220f);settle(view);
                    drag(view,artistPager,450,180,false);advance(view,240);
                    assertFalse((Boolean)prop(artist,"albumsTab").peek());
                    assertEquals("artist song position is preserved",170f,((Number)prop(artistScroll,"contentY").peek()).floatValue(),0.01f);
                    drag(view,artistPager,450,-180,false);advance(view,240);
                    assertEquals("artist album position is preserved",220f,((Number)prop(artistScroll,"contentY").peek()).floatValue(),0.01f);
                    assertSame(albums,player.sourceArtistAlbums.peek());
                } finally {queue.uninstall();}
            }
        } finally {if(view!=null)view.dispose();if(player!=null)player.shutdownAsync().join();AppDirs.setBase(oldBase);AppDirs.setCacheBase(oldCache);}
    }
    private static void shortReleaseAndCancel(QmlView view,PageFlickable pager,float y)throws Exception {
        drag(view,pager,y,-60,false);assertEquals("short slow drag keeps selected page",0,pager.pageIndex.peekInt());
        assertTrue("release starts a return motion immediately",pager.moving.peek());advance(view,240);
        assertEquals("short drag is fully back before 250ms",0f,pager.contentX.peekFloat(),0.01f);
        drag(view,pager,y,-70,true);assertEquals("cancel rolls back immediately",0f,pager.contentX.peekFloat(),0.01f);
    }
    private static void fastShortSwipes(QmlView view,PageFlickable pager,float y) {
        long t=System.nanoTime();view.dispatchPointerDown(280,y,t);view.dispatchPointerMove(260,y,t+8_000_000L);view.dispatchPointerMove(240,y,t+16_000_000L);view.dispatchPointerUp(240,y,t+20_000_000L);settle(view);
        assertEquals("fast short left swipe uses release velocity to select albums",1,pager.pageIndex.peekInt());advance(view,240);
        t=System.nanoTime();view.dispatchPointerDown(60,y,t);view.dispatchPointerMove(80,y,t+8_000_000L);view.dispatchPointerMove(100,y,t+16_000_000L);view.dispatchPointerUp(100,y,t+20_000_000L);settle(view);
        assertEquals("fast short right swipe returns to songs",0,pager.pageIndex.peekInt());advance(view,240);
    }
    private static void drag(QmlView view,PageFlickable pager,float y,float dx,boolean cancel)throws Exception {
        float start=dx<0?280:60;long time=System.nanoTime();
        view.dispatchPointerDown(start,y,time);
        view.dispatchPointerMove(start+dx/2,y,time+100_000_000L);settle(view);
        view.dispatchPointerMove(start+dx,y,time+200_000_000L);settle(view);
        assertTrue("page follows touch before release",pager.contentX.peekFloat()>0 && pager.contentX.peekFloat()<pager.width.peekFloat());
        if(cancel)view.dispatchPointerCancel();else view.dispatchPointerUp(start+dx,y,time+340_000_000L);
        settle(view);
    }
    private static void route(QmlView view,PlayerController player,String type,String id)throws Exception{player.debugRouteType.set(type);player.debugRouteId.set(id);player.debugRouteRevision.set(player.debugRouteRevision.peek()+1);settle(view);advance(view,500);}
    private static void advance(QmlView view,int milliseconds){long start=System.nanoTime();for(int elapsed=0;elapsed<=milliseconds;elapsed+=8){view.tickAnimations(start+elapsed*1_000_000L);settle(view);}}
    private static void settle(QmlView view){for(int i=0;i<4;i++){view.dirtyQueue().flush();view.renderer().layoutOnly(view.root());}view.dirtyQueue().flush();}
    @SuppressWarnings("unchecked")private static Property<Object> prop(Item item,String name)throws Exception{return (Property<Object>)item.getClass().getField(name).get(item);}
}
