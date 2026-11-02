package dev.t1m3.qplayer.desktop.window;

import dev.t1m3.qplayer.audio.AudioBackend;
import dev.t1m3.qplayer.bridge.PlayerController;
import dev.t1m3.qplayer.desktop.resources.ClasspathResourceLoader;
import dev.t1m3.qplayer.desktop.settings.JsonSettingsStore;
import dev.t1m3.qplayer.i18n.I18n;
import dev.t1m3.qplayer.media.Song;
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

/** Protect both scrolling work and pointer identity after row recycling. */
public class VirtualSongListReuseTest {
    @Test public void tenThousandSongsOnlyRebindTheEnteringRowAndKeepCorrectTapIndices() throws Exception {
        String base=AppDirs.base(),cache=AppDirs.cacheBase(); PlayerController player=null; QmlView view=null;
        try {
            Path dir=Files.createTempDirectory("row-reuse"); AppDirs.setBase(dir.toString());AppDirs.setCacheBase(dir.resolve("cache").toString());
            AudioBackend backend=(AudioBackend)Proxy.newProxyInstance(AudioBackend.class.getClassLoader(),new Class<?>[]{AudioBackend.class},(p,m,a)->{
                if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==long.class)return 0L;return null;
            });
            player=new PlayerController(backend,t->{}); SettingsCore settings=new SettingsCore();settings.load(new JsonSettingsStore(),SettingsCatalog.DESKTOP);
            List<Song> data=new ArrayList<>();
            for(int i=0;i<10000;i++){Song s=new Song();s.id="qq:song:"+i;s.name=s.title="Song "+i;s.artist="Artist";data.add(s);}
            view=QmlView.withStockTypes(new QmlEngine()).resources(new ClasspathResourceLoader())
                .context("player",player).context("settings",settings).context("i18n",I18n.instance()).context("fixtureRows",data);
            view.load("import QtQuick\nimport \"components\"\nVirtualSongList { width:390; height:416; list:fixtureRows; songMenu:false }");
            DirtyQueue q=view.dirtyQueue();q.install();
            try {
                settle(view); List<Item> pool=new ArrayList<>();collect(view.root(),pool);
                assertTrue("bounded by viewport, independent of 10k records",pool.size()<=14 && pool.size()>=7);
                Map<Item,Integer> before=new IdentityHashMap<>();for(Item row:pool)before.put(row,prop(row,"sourceIndex").peekInt());
                prop(view.root(),"contentY").set(256f);settle(view);
                int changed=0;
                for(Item row:pool)if(before.get(row)!=prop(row,"sourceIndex").peekInt())changed++;
                assertEquals("crossing one boundary changes one song/cover, not every row",1,changed);
                List<Item> after=new ArrayList<>();collect(view.root(),after);assertEquals(pool,after);
                for(float offset:new float[]{320,6400,630000,12,0}) {
                    prop(view.root(),"contentY").set(offset);settle(view);
                    float actual=prop(view.root(),"contentY").peekFloat();int expected=(int)((actual+30)/64);
                    view.dispatchPointerDown(120,30);view.dispatchPointerUp(120,30);q.flush();
                    assertEquals("tap after recycling uses global result index",expected,prop(view.root(),"activatedIndex").peekInt());
                }
            } finally {q.uninstall();}
        } finally {if(view!=null)view.dispose();if(player!=null)player.shutdownAsync().join();AppDirs.setBase(base);AppDirs.setCacheBase(cache);}
    }
    @Test public void playlistListAndGridReuseTheirPoolsAndKeepTheCorrectTapTarget() throws Exception {
        String base=AppDirs.base(),cache=AppDirs.cacheBase();PlayerController player=null;
        try {
            Path dir=Files.createTempDirectory("playlist-pools");AppDirs.setBase(dir.toString());AppDirs.setCacheBase(dir.resolve("cache").toString());
            AudioBackend backend=(AudioBackend)Proxy.newProxyInstance(AudioBackend.class.getClassLoader(),new Class<?>[]{AudioBackend.class},(p,m,a)->{
                if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==long.class)return 0L;return null;
            });
            player=new PlayerController(backend,t->{});SettingsCore settings=new SettingsCore();settings.load(new JsonSettingsStore(),SettingsCatalog.DESKTOP);
            List<dev.t1m3.qplayer.media.Playlist> data=new ArrayList<>();
            for(int i=0;i<5000;i++){dev.t1m3.qplayer.media.Playlist p=new dev.t1m3.qplayer.media.Playlist();p.id="qq:playlist:"+i;p.name="Playlist "+i;p.trackCount=i;data.add(p);}
            for(boolean grid:new boolean[]{false,true}) {
                QmlView view=QmlView.withStockTypes(new QmlEngine()).resources(new ClasspathResourceLoader())
                    .context("player",player).context("settings",settings).context("i18n",I18n.instance()).context("fixtureRows",data);
                try {
                    view.load("import QtQuick\nimport \"components\"\n"+(grid?"PlaylistGrid":"VirtualPlaylistList")
                        +" {width:390;height:416;list:fixtureRows;property string tappedId: \"\";onOpenPlaylist:tappedId=pendingPlaylist.id}");
                    DirtyQueue q=view.dirtyQueue();q.install();
                    try {
                        settle(view);String marker=grid?"virtualPlaylistCard":"virtualPlaylistListRow";
                        List<Item> pool=new ArrayList<>();collectNamed(view.root(),pool,marker);assertTrue(pool.size()>0 && pool.size()<20);
                        Map<Item,Integer> before=new IdentityHashMap<>();for(Item item:pool)before.put(item,prop(item,"sourceIndex").peekInt());
                        float rowH=grid?prop(view.root(),"cardRowH").peekFloat():64f;
                        float offset=grid?16f+2.1f*rowH:256f;prop(view.root(),"contentY").set(offset);settle(view);
                        int changed=0;for(Item item:pool)if(before.get(item)!=prop(item,"sourceIndex").peekInt())changed++;
                        assertEquals("only the entering row of playlists rebinds",grid?2:1,changed);
                        List<Item> after=new ArrayList<>();collectNamed(view.root(),after,marker);assertEquals(pool,after);
                        for(Item item:pool) {
                            Map<String,Object> point=view.root().mapFromItem(item,50,30);
                            float x=((Number)point.get("x")).floatValue(),y=((Number)point.get("y")).floatValue();
                            if(y<20 || y>390)continue;
                            String expected="qq:playlist:"+prop(item,"sourceIndex").peekInt();
                            assertEquals(expected,prop(item,"playlistId").peek());
                            view.dispatchPointerDown(x,y);view.dispatchPointerUp(x,y);q.flush();
                            assertEquals("recycled playlist keeps its actual action target",expected,prop(view.root(),"tappedId").peek());break;
                        }
                    } finally {q.uninstall();}
                } finally {view.dispose();}
            }
        } finally {if(player!=null)player.shutdownAsync().join();AppDirs.setBase(base);AppDirs.setCacheBase(cache);}
    }
    private static void collectNamed(Item node,List<Item> rows,String marker){if(marker.equals(node.objectName.peek()))rows.add(node);for(Item child:node.children)collectNamed(child,rows,marker);}
    @SuppressWarnings("unchecked") private static Property<Object> prop(Item item,String name)throws Exception{return (Property<Object>)item.getClass().getField(name).get(item);}
    private static void collect(Item node,List<Item> rows){if("virtualSongRow".equals(node.objectName.peek()))rows.add(node);for(Item child:node.children)collect(child,rows);}
    private static void settle(QmlView view){for(int i=0;i<4;i++){view.dirtyQueue().flush();view.renderer().layoutOnly(view.root());}view.dirtyQueue().flush();}
}
