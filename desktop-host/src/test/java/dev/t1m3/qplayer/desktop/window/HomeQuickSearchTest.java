package dev.t1m3.qplayer.desktop.window;

import dev.t1m3.qplayer.desktop.resources.ClasspathResourceLoader;
import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.QmlView;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** Exercises the production HomePage; reverse scrolling must not reload its data. */
public class HomeQuickSearchTest {
    public static final class Player {
        public final Property<List<Object>> qqGuessSongs = new Property<>(Collections.emptyList());
        public final Property<List<Object>> qqDailySongs = new Property<>(Collections.emptyList());
        public final Property<List<Object>> heartExploreSongs = new Property<>(Collections.emptyList());
        public final Property<List<Object>> playbackRelatedSongs = new Property<>(Collections.emptyList());
        public final Property<String> qqGuessStatus = new Property<>("");
        public final Property<String> qqDailyStatus = new Property<>("");
        public final Property<String> heartExploreReason = new Property<>("");
        public final Property<String> title = new Property<>("");
        public final Property<String> artist = new Property<>("");
        public final Property<String> playbackInfoStatus = new Property<>("");
        public final Property<Double> pixelRatio = new Property<>(1.0);
        public int ensures, refreshes, played;
        public void ensureHomeLoaded() { ensures++; }
        public void loadHome() { refreshes++; }
        public void refreshHeartExplore() { refreshes++; }
        public void refreshPlaybackInfo() { refreshes++; }
        public void playQqGuess(int index) { played++; }
        public void playQqDaily(int index) { played++; }
    }

    @Test
    public void aSmallReverseScrollRevealsSearchAtTheCurrentPosition() throws Exception {
        Player player = new Player();
        try (Scene scene = new Scene(player)) {
            Item home = scene.view.findByObjectName("homeDiscoveryPage");
            Item scroll = scene.view.findByObjectName("homeRecommendationsScroll");
            Item header = scene.view.findByObjectName("homeQuickSearchHeader");
            assertEquals(0f, header.y.peekFloat(), 0.1f);
            scene.scroll(220f);
            assertFalse((Boolean) property(home, "searchExpanded").peek());
            assertEquals(-68f, header.y.peekFloat(), 0.1f);
            scene.scroll(217f); // slight noise must not repeatedly toggle the header
            assertFalse((Boolean) property(home, "searchExpanded").peek());
            scene.scroll(213f);
            assertTrue((Boolean) property(home, "searchExpanded").peek());
            assertEquals(0f, header.y.peekFloat(), 0.1f);
            assertEquals("search reveal preserves reading position",213f, property(scroll,"contentY").peekFloat(),0.1f);
            int refreshes = player.refreshes;
            scene.click(scene.view.findByObjectName("homeQuickSearchButton"));
            assertEquals(1, property(scene.view.root(), "searches").peekInt());
            assertEquals(refreshes, player.refreshes);
            assertEquals(0, player.played);
            scene.scroll(225f);
            assertFalse((Boolean) property(home,"searchExpanded").peek());
            assertEquals(-68f,header.y.peekFloat(),0.1f);
            scene.scroll(25f);
            assertEquals("at the top search follows its natural scroll position",-25f,header.y.peekFloat(),0.1f);
        }
    }

    @Test
    public void canceledPointerDoesNotActivateAControlAndTheNextTapWorks() throws Exception {
        QmlView view=QmlView.withStockTypes(new QmlEngine());
        try {
            view.load("import QtQuick\nItem { width:300; height:300; property int clicks:0; property int cancels:0\n"
                    + "MouseArea { objectName:\"control\"; width:300; height:300; onClicked:parent.clicks++; onCanceled:parent.cancels++ } }");
            view.renderer().layoutOnly(view.root());
            view.dispatchPointerDown(100,100);
            assertTrue((Boolean)property(view.findByObjectName("control"),"pressed").peek());
            view.dispatchPointerCancel();
            assertEquals(0,property(view.root(),"clicks").peekInt());
            assertEquals(1,property(view.root(),"cancels").peekInt());
            assertFalse((Boolean)property(view.findByObjectName("control"),"pressed").peek());
            view.dispatchPointerDown(100,100);view.dispatchPointerUp(100,100);
            assertEquals(1,property(view.root(),"clicks").peekInt());
        } finally {view.dispose();}
    }

    private static final class Scene implements AutoCloseable {
        final QmlView view;
        long clock=1_000_000_000L;
        Scene(Player player) {
            view=QmlView.withStockTypes(new QmlEngine()).resources(new ClasspathResourceLoader()).context("player",player);
            view.load("import QtQuick\nimport \"pages\"\nItem { width:390; height:400; property int searches:0\n"
                    + "HomePage { anchors.fill:parent; bottomContentInset:800; onOpenSearch:parent.searches++ } }");
            frames();
        }
        void scroll(float y)throws Exception {
            DirtyQueue queue=view.dirtyQueue();queue.install();
            try {
                Item scroll=view.findByObjectName("homeRecommendationsScroll");
                property(scroll,"contentY").set(y);
                ((io.github.timer_err.qml4j.render.items.core.Flickable)scroll).syncTarget();
                queue.flush();
            }
            finally {queue.uninstall();}
            frames();
        }
        void frames() {
            DirtyQueue queue=view.dirtyQueue();queue.install();
            try {
                for(int i=0;i<20;i++) {
                    clock+=16_000_000L;view.tickAnimations(clock);queue.flush();
                    view.renderer().layoutOnly(view.root());queue.flush();
                }
            } finally {queue.uninstall();}
        }
        void click(Item item) {
            assertNotNull(item);
            Map<String,Object> point=view.root().mapFromItem(item,item.width.peekFloat()/2,item.height.peekFloat()/2);
            float x=((Number)point.get("x")).floatValue(),y=((Number)point.get("y")).floatValue();
            view.dispatchPointerDown(x,y);view.dispatchPointerUp(x,y);frames();
        }
        @Override public void close(){view.dispose();}
    }
    @SuppressWarnings("unchecked") private static Property<Object> property(Item item,String name)throws Exception {
        return (Property<Object>)item.getClass().getField(name).get(item);
    }
}
