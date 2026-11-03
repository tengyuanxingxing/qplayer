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
public class FavoritesSearchFilterTest {
    @Test public void searchAndPersonalFacetsOperateOnlyOnTheOpenCollection() throws Exception {
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
                    List<Song> songs=new ArrayList<>();
                    for(int i=0;i<40;i++) {
                        Song song=new Song();song.id=(i%2==0?"qq":"netease")+":song:"+i;
                        song.name=song.title="收藏歌曲 "+i;song.artist=i%2==0?"歌手甲":"歌手乙";
                        song.artists.add(new MediaRef("artist:"+(i%2),song.artist));
                        song.genre=i%3==0?"Hip-Hop":"";song.publishTimeMs=java.time.Instant.parse("2022-01-01T00:00:00Z").toEpochMilli();songs.add(song);
                    }
                    player.openSourcePlaylistId.set("qplayer:playlist:unified-favorites");player.sourcePlaylistTracks.set(songs);player.playlistLoading.set(false);
                    route(view,player,"detail","qplayer:playlist:unified-favorites");
                    Item page=view.findByObjectName("playlistDetailPage"),tracks=view.findByObjectName("playlistDetailTracks");
                    prop(tracks,"contentY").set(160f);settle(view);
                    click(view,view.findByObjectName("favoriteSearchButton"));
                    assertTrue("header search opens collection overlay",(Boolean)prop(page,"favoriteSearchOpen").peek());
                    Item search=view.findByObjectName("favoriteSongSearch");
                    assertEquals(40,resultCount(search));snapshot(view,player,settings,"favorites-search-options-390");
                    Item artist=findChip(view.root(),"artist","歌手甲");assertNotNull("chips come from actual collected artists",artist);
                    Object oldColor=prop(artist,"color").peek();click(view,artist);assertEquals(20,resultCount(search));assertNotEquals(oldColor,prop(artist,"color").peek());
                    assertEquals("歌手甲",prop(search,"artist").peek());
                    // A real input property edit drives the search binding; hidden catalog data is never consulted.
                    prop(view.findByObjectName("favoriteSearchInput"),"text").set("收藏歌曲 12");settle(view);
                    assertEquals("query and facet combine",1,resultCount(search));
                    assertSame(songs.get(12),results(search).get(0));
                    prop(search,"genre").set("Hip-Hop");settle(view);assertEquals(1,resultCount(search));
                    prop(search,"provider").set("netease");settle(view);assertEquals(0,resultCount(search));
                    click(view,view.findByObjectName("favoriteSearchClear"));
                    assertEquals(40,resultCount(search));assertEquals("",prop(search,"artist").peek());assertEquals("clear also resets the visible editable input","",prop(view.findByObjectName("favoriteSearchInput"),"text").peek());
                    prop(view.findByObjectName("favoriteSearchInput"),"text").set("收藏歌曲 12");settle(view);
                    Item resultList=view.findByObjectName("favoriteSearchResults");
                    prop(resultList,"contentY").set(((Number)prop(resultList,"headerHeight").peek()).floatValue());settle(view);
                    Item row=findRow(resultList,"收藏歌曲 12");assertNotNull(row);snapshot(view,player,settings,"favorites-search-filter-390");
                    // The fixture deliberately has no remote providers. Record the initial selection,
                    // before a missing-stream error can auto-advance the real playback controller.
                    List<String> selectedIds=new ArrayList<>();player.currentMediaId.addListener(id->{if(id!=null && !id.isEmpty())selectedIds.add(id);});
                    click(view,row);
                    assertFalse("the real row click selects a song",selectedIds.isEmpty());
                    assertEquals("filtered result maps to its original playlist index",songs.get(12).id,selectedIds.get(0));
                    player.backTick.set(player.backTick.peek()+1);settle(view);
                    assertFalse((Boolean)prop(page,"favoriteSearchOpen").peek());
                    assertEquals("closing search keeps collection reading position",160f,((Number)prop(tracks,"contentY").peek()).floatValue(),0.01f);
                } finally {queue.uninstall();}
            }
        } finally {if(view!=null)view.dispose();if(player!=null)player.shutdownAsync().join();AppDirs.setBase(oldBase);AppDirs.setCacheBase(oldCache);}
    }
    private static void snapshot(QmlView view,PlayerController player,SettingsCore settings,String name)throws Exception {
        String out=System.getProperty("qplayer.preview.dir");if(out==null)return;
        dev.t1m3.qplayer.lyric.skia.LyricCompositor c=new dev.t1m3.qplayer.lyric.skia.LyricCompositor();
        try(io.github.humbleui.skija.Surface surface=io.github.humbleui.skija.Surface.makeRasterN32Premul(390,844)) {
            for(int i=0;i<10;i++){view.tickAnimations(System.nanoTime()+1_000_000_000L);settle(view);c.composite(surface.getCanvas(),view.renderer(),view,player,settings,null,1f,390,844);}
            Files.createDirectories(Paths.get(out));try(io.github.humbleui.skija.Image image=surface.makeImageSnapshot();io.github.humbleui.skija.Data data=image.encodeToData()){Files.write(Paths.get(out,name+".png"),data.getBytes());}
        } finally {c.dispose();}
    }
    @SuppressWarnings("unchecked") private static List<Song> results(Item search)throws Exception { return (List<Song>)prop(search,"results").peek(); }
    private static int resultCount(Item search)throws Exception {return results(search).size();}
    private static Item findChip(Item item,String group,String label)throws Exception {
        if("favoriteFilterChip".equals(item.objectName.peek())) {
            Object g=prop(item,"group").peek(),o=prop(item,"option").peek();
            if(g instanceof Map && o instanceof Map && group.equals(((Map<?,?>)g).get("key")) && label.equals(((Map<?,?>)o).get("label")))return item;
        }
        for(Item child:item.children){Item found=findChip(child,group,label);if(found!=null)return found;}return null;
    }
    private static Item findRow(Item item,String title)throws Exception {
        if("virtualSongRow".equals(item.objectName.peek()) && title.equals(prop(item,"rowTitle").peek()))return item;
        for(Item child:item.children){Item found=findRow(child,title);if(found!=null)return found;}return null;
    }
    private static void click(QmlView view,Item item){assertNotNull(item);Map<String,Object> p=view.root().mapFromItem(item,item.width.peekFloat()/2,item.height.peekFloat()/2);float x=((Number)p.get("x")).floatValue(),y=((Number)p.get("y")).floatValue();view.dispatchPointerDown(x,y);view.dispatchPointerUp(x,y);settle(view);}
    private static void route(QmlView view,PlayerController player,String type,String id)throws Exception{player.debugRouteType.set(type);player.debugRouteId.set(id);player.debugRouteRevision.set(player.debugRouteRevision.peek()+1);settle(view);advance(view,500);}
    private static void advance(QmlView view,int milliseconds){long start=System.nanoTime();for(int elapsed=0;elapsed<=milliseconds;elapsed+=8){view.tickAnimations(start+elapsed*1_000_000L);settle(view);}}
    private static void settle(QmlView view){for(int i=0;i<4;i++){view.dirtyQueue().flush();view.renderer().layoutOnly(view.root());}view.dirtyQueue().flush();}
    @SuppressWarnings("unchecked")private static Property<Object> prop(Item item,String name)throws Exception{return (Property<Object>)item.getClass().getField(name).get(item);}
}
