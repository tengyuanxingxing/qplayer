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
    @SuppressWarnings("unchecked") private static Property<Object> prop(Item item,String name)throws Exception{return (Property<Object>)item.getClass().getField(name).get(item);}
    private static void collect(Item node,List<Item> rows){if("virtualSongRow".equals(node.objectName.peek()))rows.add(node);for(Item child:node.children)collect(child,rows);}
    private static void settle(QmlView view){for(int i=0;i<4;i++){view.dirtyQueue().flush();view.renderer().layoutOnly(view.root());}view.dirtyQueue().flush();}
}
