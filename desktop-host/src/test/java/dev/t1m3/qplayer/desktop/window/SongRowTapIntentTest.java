package dev.t1m3.qplayer.desktop.window;

import dev.t1m3.qplayer.audio.AudioBackend;
import dev.t1m3.qplayer.bridge.PlayerController;
import dev.t1m3.qplayer.desktop.resources.ClasspathResourceLoader;
import dev.t1m3.qplayer.desktop.settings.JsonSettingsStore;
import dev.t1m3.qplayer.i18n.I18n;
import dev.t1m3.qplayer.settings.SettingsCatalog;
import dev.t1m3.qplayer.settings.SettingsCore;
import dev.t1m3.qplayer.store.AppDirs;
import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.QmlView;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class SongRowTapIntentTest {
    @Test public void dragReturnLongHoldAndCanceledStreamNeverPlayASong() throws Exception {
        String oldBase=AppDirs.base(), oldCache=AppDirs.cacheBase();
        PlayerController player=null;QmlView view=null;
        try {
            java.nio.file.Path dir=Files.createTempDirectory("tap-intent");
            AppDirs.setBase(dir.toString());AppDirs.setCacheBase(dir.resolve("cache").toString());
            AudioBackend backend=(AudioBackend)Proxy.newProxyInstance(AudioBackend.class.getClassLoader(),new Class<?>[]{AudioBackend.class},(p,m,a)->{
                if(m.getReturnType()==boolean.class)return false;
                if(m.getReturnType()==long.class)return 0L;return null;
            });
            player=new PlayerController(backend,t->{});
            SettingsCore settings=new SettingsCore();settings.load(new JsonSettingsStore(),SettingsCatalog.DESKTOP);
            view=QmlView.withStockTypes(new QmlEngine()).resources(new ClasspathResourceLoader())
                .context("player",player).context("settings",settings).context("i18n",I18n.instance());
            view.load("import QtQuick\nimport \"components\"\nSongRow { width:390; height:72; property int activations:0; onActivated: activations=activations+1 }");
            DirtyQueue q=view.dirtyQueue();q.install();
            try {
                q.flush();view.renderer().layoutOnly(view.root());q.flush();
                view.dispatchPointerDown(100,30);view.dispatchPointerUp(100,30);q.flush();
                assertEquals(1,((io.github.timer_err.qml4j.engine.binding.Property<?>)view.root().getClass().getField("activations").get(view.root())).peekInt());
                view.dispatchPointerDown(100,30);view.dispatchPointerMove(100,48);view.dispatchPointerMove(100,30);view.dispatchPointerUp(100,30);q.flush();
                assertEquals("returning to the original point is still a drag",1,((io.github.timer_err.qml4j.engine.binding.Property<?>)view.root().getClass().getField("activations").get(view.root())).peekInt());
                view.dispatchPointerDown(100,30);view.dispatchPointerCancel();view.dispatchPointerUp(100,30);q.flush();
                assertEquals("canceled touch does not play",1,((io.github.timer_err.qml4j.engine.binding.Property<?>)view.root().getClass().getField("activations").get(view.root())).peekInt());
                view.dispatchPointerDown(100,30);Thread.sleep(430);view.dispatchPointerUp(100,30);q.flush();
                assertEquals("stationary hold does not play on release",1,((io.github.timer_err.qml4j.engine.binding.Property<?>)view.root().getClass().getField("activations").get(view.root())).peekInt());
                view.dispatchPointerDown(100,30);view.dispatchPointerUp(100,30);q.flush();
                assertEquals("a fresh short tap still plays",2,((io.github.timer_err.qml4j.engine.binding.Property<?>)view.root().getClass().getField("activations").get(view.root())).peekInt());
            } finally {q.uninstall();}
        } finally {
            if(view!=null)view.dispose();if(player!=null)player.shutdownAsync().join();AppDirs.setBase(oldBase);AppDirs.setCacheBase(oldCache);
        }
    }
}
