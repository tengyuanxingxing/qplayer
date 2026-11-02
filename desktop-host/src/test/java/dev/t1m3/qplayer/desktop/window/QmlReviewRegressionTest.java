package dev.t1m3.qplayer.desktop.window;

import dev.t1m3.qplayer.audio.AudioBackend;
import dev.t1m3.qplayer.bridge.PlayerController;
import dev.t1m3.qplayer.bridge.WindowChromeStub;
import dev.t1m3.qplayer.desktop.resources.ClasspathResourceLoader;
import dev.t1m3.qplayer.desktop.settings.JsonSettingsStore;
import dev.t1m3.qplayer.i18n.I18n;
import dev.t1m3.qplayer.plugin.SourceAccountRow;
import dev.t1m3.qplayer.settings.SettingsCatalog;
import dev.t1m3.qplayer.settings.SettingsCore;
import dev.t1m3.qplayer.store.AppDirs;
import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.QmlView;
import io.github.timer_err.qml4j.render.items.core.Flickable;
import io.github.timer_err.qml4j.render.items.core.Item;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.Assert.*;

/** Exercises production QML with the deferred bindings used by the desktop host. */
public class QmlReviewRegressionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void signedInSecondarySourceCanExposeCreateWithoutPrimaryLogin() throws Exception {
        try (Scene scene = new Scene(temporary.newFolder().toPath())) {
            SourceAccountRow primary = new SourceAccountRow();
            primary.providerId = "primary";
            primary.primary = true;
            SourceAccountRow secondary = new SourceAccountRow();
            secondary.providerId = "secondary";
            secondary.sourceName = "Secondary";
            secondary.loggedIn = true;
            secondary.canCreatePlaylist = true;
            scene.player.sourceAccounts.set(java.util.List.of(primary, secondary));
            scene.load("import QtQuick\nimport \"pages\"\nLibraryPage { width: 480; height: 700 }");
            Item fab = scene.view.findByObjectName("createPlaylistFab");
            assertNotNull(fab);
            assertFalse(scene.player.loggedIn.peek());
            assertTrue("secondary source capability controls creation", fab.visible.peek());
            scene.player.sourceAccounts.set(java.util.List.of(primary));
            scene.frames(4);
            assertFalse("no valid target means no create entry", fab.visible.peek());
        }
    }

    @Test
    public void legacyLocalTabPreferenceCannotChangeTheTwoDestinationShell() throws Exception {
        try (Scene scene = new Scene(temporary.newFolder().toPath())) {
            scene.settings.setValue("pageTransitionPreset", 4);
            scene.settings.setValue("showLocalTab", true);
            scene.loadMain();
            scene.set("testAction", 1);
            scene.frames(4);
            assertEquals(2, scene.number("page"));
            assertEquals(-1, scene.number("localPageIndex"));
            scene.set("testAction", 2); // keep the settings route open during reindexing
            scene.settings.setValue("showLocalTab", false);
            scene.frames(4);
            assertEquals(2, scene.number("page"));
            assertEquals(2, scene.number("nextPage"));
            assertEquals(Boolean.TRUE, scene.value("libraryLoaded"));
            scene.settings.setValue("showLocalTab", true);
            scene.frames(4);
            assertEquals(2, scene.number("page"));
            assertEquals(2, scene.number("nextPage"));
            scene.set("testAction", 3);
            scene.frames(4);
            assertEquals(2, scene.number("page"));
        }
    }

    @Test
    public void pendingLibraryDestinationKeepsItsIdentityAcrossLegacyPreferenceChanges() throws Exception {
        try (Scene scene = new Scene(temporary.newFolder().toPath())) {
            scene.settings.setValue("pageTransitionPreset", 1);
            scene.settings.setValue("showLocalTab", true);
            scene.loadMain();
            scene.set("testAction", 8);
            scene.frames(80);
            scene.set("testAction", 1);
            assertEquals(0, scene.number("page"));
            scene.settings.setValue("showLocalTab", false);
            scene.frames(2);
            assertEquals(2, scene.number("nextPage"));
            scene.frames(80);
            assertEquals(2, scene.number("page"));
            assertEquals(Boolean.TRUE, scene.value("libraryLoaded"));
        }
    }

    @Test
    public void routeHomeRestoresExistingRootForZoomAndInstantPresets() throws Exception {
        for (int preset : new int[]{0, 4}) {
            try (Scene scene = new Scene(temporary.newFolder().toPath())) {
                scene.settings.setValue("pageTransitionPreset", preset);
                scene.loadMain();
                scene.set("testAction", 1);
                scene.frames(80);
                int original = scene.number("page");
                scene.set("testAction", 2);
                scene.frames(80);
                scene.set("testAction", 3);
                scene.frames(80);
                assertEquals(original, scene.number("page"));
                assertEquals("", scene.value("currentOverlay"));
                assertEquals(1, ((Number) scene.value("testRootOpacity")).doubleValue(), 0.001);
            }
        }
    }

    @Test
    public void lastClickOnCurrentTabCancelsPendingNormalAndLiquidNavigation() throws Exception {
        for (boolean liquid : new boolean[]{false, true}) {
            try (Scene scene = new Scene(temporary.newFolder().toPath())) {
                scene.settings.setValue("pageTransitionPreset", 1);
                scene.settings.setValue("liquidGlassNavigation", liquid);
                scene.loadMain();
                scene.set("testAction", 4); // home -> search, before the swap
                scene.frames(3);
                if (liquid) {
                    Item navigation = scene.view.findByObjectName("liquidGlassNavigationSurface");
                    assertNotNull(navigation);
                    scene.click(navigation, 30, 28);
                } else {
                    Item home = scene.view.findByObjectName("miuixNavigationItem0");
                    assertNotNull(home);
                    scene.click(home, home.width.peekFloat() / 2, home.height.peekFloat() / 2);
                }
                scene.frames(80);
                assertEquals("last click wins, liquid=" + liquid, 0, scene.number("page"));
                assertEquals(0, scene.number("nextPage"));
            }
        }
    }

    @Test
    public void backConsumesPageLocalDialogBeforePoppingSettings() throws Exception {
        try (Scene scene = new Scene(temporary.newFolder().toPath())) {
            scene.loadMain();
            scene.set("testAction", 2);
            scene.frames(30);
            scene.settings.colorPickerKey.set("desktopLyricSungColor");
            scene.frames(30);
            scene.set("testAction", 5);
            scene.frames(30);
            assertEquals("settings", scene.value("currentOverlay"));
            assertEquals("", scene.settings.colorPickerKey.peek());
            scene.set("testAction", 6);
            scene.frames(30);
            assertEquals("", scene.value("currentOverlay"));
        }
    }

    @Test
    public void backClosesTheLyricOffsetPopupBeforeLeavingLyrics() throws Exception {
        try (Scene scene = new Scene(temporary.newFolder().toPath())) {
            scene.loadMain();
            scene.set("testAction", 7);
            scene.frames(20);
            assertTrue(scene.player.lyricsOpen.peek());
            assertEquals(Boolean.TRUE, scene.value("testOffsetOpen"));
            scene.set("testAction", 5);
            assertTrue("the first Back belongs to the offset popup", scene.player.lyricsOpen.peek());
            assertEquals(Boolean.FALSE, scene.value("testOffsetOpen"));
            scene.set("testAction", 6);
            assertTrue("its exit animation still consumes Back", scene.player.lyricsOpen.peek());
            scene.frames(20);
            scene.set("testAction", 5);
            assertFalse("Back can leave lyrics once the popup has retired",
                    scene.player.lyricsOpen.peek());
        }
    }

    @Test
    public void openDialogUsesSessionRevisionRatherThanCoalescedOpenFlag() throws Exception {
        try (Scene scene = new Scene(temporary.newFolder().toPath())) {
            scene.load("""
                    import QtQuick
                    import "dialogs"
                    Item { id: root; width: 480; height: 700
                        property int testAction: 0
                        property string input: form.slots[0].inputValue
                        onTestActionChanged: form.slots[0].inputValue = "editing"
                        PluginDialog { id: form }
                    }
                    """);
            scene.describeDialog("first", 1);
            assertEquals("first", scene.value("input"));
            scene.set("testAction", 1);
            scene.describeDialog("refresh", 1);
            assertEquals("editing", scene.value("input"));
            // The dialog remains open, so a bool-only observer sees no new edge.
            scene.describeDialog("second session", 2);
            assertEquals("second session", scene.value("input"));
        }
    }

    @Test
    public void pluginNodeSeedsTypeChangesAndResetsOnlyAtSessionBoundary() throws Exception {
        try (Scene scene = new Scene(temporary.newFolder().toPath())) {
            scene.load("""
                    import QtQuick
                    import "components"
                    Item { id: root; width: 480; height: 700
                        property int testAction: 0
                        property string submitted: ""
                        function submit(id) { root.submitted = JSON.stringify({enabled: node.switchValue}) }
                        onTestActionChanged: {
                            if (testAction === 1) node.node = ({type: "switch", id: "enabled", checked: true})
                            if (testAction === 2) root.submit("save")
                            if (testAction === 3) node.node = ({type: "input", id: "name", value: "first"})
                            if (testAction === 4) node.inputValue = "editing"
                            if (testAction === 5) node.node = ({type: "input", id: "name", value: "refresh"})
                            if (testAction === 6) {
                                node.resetSession()
                                node.node = ({type: "input", id: "name", value: "second session"})
                            }
                        }
                        PluginDialogNode { id: node; objectName: "node"; width: 400; host: root }
                    }
                    """);
            scene.set("testAction", 1);
            scene.set("testAction", 2);
            assertEquals("{\"enabled\":true}", scene.value("submitted"));
            scene.set("testAction", 3);
            Item node = scene.view.findByObjectName("node");
            assertEquals("first", property(node, "inputValue").peek());
            scene.set("testAction", 4);
            scene.set("testAction", 5);
            assertEquals("editing", property(node, "inputValue").peek());
            scene.set("testAction", 6);
            assertEquals("second session", property(node, "inputValue").peek());
        }
    }

    @Test
    public void colorSignalUsesTheChannelsChangedInThisEvent() throws Exception {
        try (Scene scene = new Scene(temporary.newFolder().toPath())) {
            scene.load("""
                    import QtQuick
                    import miuix.Core
                    Item { id: root; width: 480; height: 700
                        property int testAction: 0
                        property real red: -1
                        property real green: -1
                        property real blue: -1
                        property real alpha: -1
                        onTestActionChanged: {
                            picker._hue = 120
                            picker._saturation = 1
                            picker._value = 1
                            picker._alpha = 0.25
                            picker._emit()
                        }
                        ColorPicker { id: picker; width: 320; color: "#ff0000"
                            onColorSelected: (c) => { root.red = c.r; root.green = c.g; root.blue = c.b; root.alpha = c.a }
                        }
                    }
                    """);
            scene.set("testAction", 1);
            assertEquals(0, ((Number) scene.value("red")).doubleValue(), 0.01);
            assertEquals(1, ((Number) scene.value("green")).doubleValue(), 0.01);
            assertEquals(0, ((Number) scene.value("blue")).doubleValue(), 0.01);
            assertEquals(0.25, ((Number) scene.value("alpha")).doubleValue(), 0.01);
        }
    }

    @Test
    public void listModelReplacementAndCancellationNeverCommitStaleIndices() throws Exception {
        for (String type : new String[]{"VirtualSongList", "VirtualPlaylistList"}) {
            try (Scene scene = new Scene(temporary.newFolder().toPath())) {
                scene.load("""
                        import QtQuick
                        import "components"
                        Item { id: root; width: 480; height: 700
                            property int testAction: 0
                            property int commits: 0
                            property int moves: 0
                            onTestActionChanged: {
                                if (testAction === 1 || testAction === 3 || testAction === 5) {
                                    list._dragFrom = 0; list._dropIndex = 1; list._dragIdentity = "a"
                                }
                                if (testAction === 2) {
                                    list.list = [{id: "b", name: "B"}, {id: "a", name: "A"}]
                                    list._endDrag()
                                }
                                if (testAction === 4) { list._cancelDrag(); list._endDrag() }
                                if (testAction === 6) list._endDrag()
                                if (testAction === 7) {
                                    list._dragFrom = 0; list._dropIndex = 1; list._dragIdentity = "b"
                                    list._endDrag()
                                }
                            }
                            %s { id: list; width: 480; height: 600; reorderable: true
                                list: [{id: "a", name: "A"}, {id: "b", name: "B"}]
                                onMoveRequested: root.moves++
                                onReorderCommitted: root.commits++
                            }
                        }
                        """.formatted(type));
                scene.set("testAction", 1);
                scene.set("testAction", 2);
                assertEquals(type, 0, scene.number("commits"));
                scene.set("testAction", 3);
                scene.set("testAction", 4);
                assertEquals(type, 0, scene.number("commits"));
                // Original identity no longer occupies index zero, even without a
                // model-change notification at the moment of release.
                scene.set("testAction", 5);
                scene.set("testAction", 6);
                assertEquals(type, 0, scene.number("moves"));
                assertEquals(type, 0, scene.number("commits"));
                scene.set("testAction", 7);
                assertEquals(type, 1, scene.number("moves"));
                assertEquals(type, 1, scene.number("commits"));
            }
        }
    }

    @Test
    public void emptyArtworkClearsPreviousEntityAndPendingSizingKeepsNewSource() throws Exception {
        try (Scene scene = new Scene(temporary.newFolder().toPath())) {
            scene.load("""
                    import QtQuick
                    import "components"
                    Item { id: root; width: 480; height: 700
                        property int testAction: 0
                        onTestActionChanged: {
                            if (testAction === 1) cover.width = 160
                            if (testAction === 2) cover.source = ""
                            if (testAction === 3) cover.source = "next.png"
                        }
                        CoverImage { id: cover; width: 0; height: 160; source: "first.png" }
                    }
                    """);
            Item image = scene.view.findByObjectName("coverArtImage");
            assertEquals("non-empty input waits for its first decode size", "", property(image, "source").peek());
            scene.set("testAction", 1);
            assertEquals("first.png", property(image, "source").peek());
            scene.set("testAction", 2);
            assertEquals("", property(image, "source").peek());
            scene.set("testAction", 3);
            assertEquals("next.png", property(image, "source").peek());
        }
    }

    @Test
    public void viewportScrollbarTrackJumpDragsFromNewPositionAndHonorsContentStart() throws Exception {
        for (boolean horizontal : new boolean[]{false, true}) {
            try (Scene scene = new Scene(temporary.newFolder().toPath())) {
                scene.load("""
                        import QtQuick
                        import miuix.Core
                        Item { width: 480; height: 806
                            Flickable { id: target; objectName: "scrollTarget"
                                width: 400; height: 400
                                contentWidth: %d; contentHeight: %d
                                contentX: %d; contentY: %d
                                ViewportScrollBar {
                                    target: parent
                                    orientation: Qt.%s
                                    contentStart: 64
                                }
                            }
                        }
                        """.formatted(horizontal ? 2000 : 400, horizontal ? 400 : 2000,
                        horizontal ? 64 : 0, horizontal ? 0 : 64,
                        horizontal ? "Horizontal" : "Vertical"));
                Flickable target = (Flickable) scene.view.findByObjectName("scrollTarget");
                scene.frames(20);
                long time = scene.clock;
                float x = horizontal ? 260 : 396;
                float y = horizontal ? 396 : 260;
                scene.view.dispatchPointerDown(x, y, time);
                double jumped = scrollPosition(target, horizontal);
                assertTrue("track press must jump beyond the original position", jumped > 1000);

                // The same onPressed handler jumped and captured the drag. Its
                // baseline must read contentX/Y, not the pre-flush derived binding.
                scene.view.dispatchPointerMove(horizontal ? x + 10 : 450,
                        horizontal ? 450 : y + 10, time + 10_000_000L);
                double dragged = scrollPosition(target, horizontal);
                assertTrue("drag starts at the newly jumped position", dragged > jumped);
                assertTrue("one short drag does not jump to a boundary", dragged < 1600);
                scene.frames(30);
                assertEquals("holding a track grab stays still", dragged,
                        scrollPosition(target, horizontal), 0.01);
                scene.view.dispatchPointerMove(horizontal ? x + 10 : 450,
                        horizontal ? 450 : y + 10, scene.clock);
                assertEquals("thumb growth must not change an unchanged pointer position", dragged,
                        scrollPosition(target, horizontal), 0.01);

                scene.view.dispatchPointerMove(horizontal ? -1000 : 450,
                        horizontal ? 450 : -1000, scene.clock + 10_000_000L);
                assertEquals("PullToRefresh's resting position is the logical start", 64,
                        scrollPosition(target, horizontal), 0.01);
                scene.view.dispatchPointerUp(horizontal ? -1000 : 450,
                        horizontal ? 450 : -1000, scene.clock + 20_000_000L);
                scene.frames(30);
                assertEquals("release must not return to a stale target", 64,
                        scrollPosition(target, horizontal), 0.01);

                // Track-end clicks use the other bound, without losing capture.
                scene.view.dispatchPointerDown(horizontal ? 399 : 398,
                        horizontal ? 398 : 399, scene.clock);
                assertEquals(1600, scrollPosition(target, horizontal), 0.01);
                scene.view.dispatchPointerUp(horizontal ? 399 : 398,
                        horizontal ? 398 : 399, scene.clock + 10_000_000L);
                scene.frames(30);
                assertEquals(1600, scrollPosition(target, horizontal), 0.01);
            }
        }
    }

    @Test
    public void viewportScrollbarDragKeepsSceneDisplacementAcrossCoalescedMoves() throws Exception {
        for (boolean horizontal : new boolean[]{false, true}) {
            try (Scene scene = new Scene(temporary.newFolder().toPath())) {
                scene.load("""
                        import QtQuick
                        import miuix.Core
                        Item { width: 480; height: 806
                            Flickable { id: target; objectName: "scrollTarget"
                                width: 400; height: 400
                                contentWidth: %d; contentHeight: %d
                                contentX: %d; contentY: %d
                                ViewportScrollBar {
                                    target: parent
                                    orientation: Qt.%s
                                }
                            }
                        }
                        """.formatted(horizontal ? 6000 : 400, horizontal ? 400 : 6000,
                        horizontal ? 800 : 0, horizontal ? 0 : 800,
                        horizontal ? "Horizontal" : "Vertical"));
                Flickable target = (Flickable) scene.view.findByObjectName("scrollTarget");
                scene.frames(20);
                float x = horizontal ? 70 : 396;
                float y = horizontal ? 396 : 70;
                scene.view.dispatchPointerDown(x, y, scene.clock);

                DirtyQueue queue = scene.view.dirtyQueue();
                queue.install();
                try {
                    for (int sample = 1; sample <= 4; sample++) {
                        scene.view.dispatchPointerMove(horizontal ? x + sample * 5 : x,
                                horizontal ? y : y + sample * 5,
                                scene.clock + sample * 4_000_000L);
                    }
                    queue.flush();
                } finally {
                    queue.uninstall();
                }
                double expected = 800 + 20d / 360d * 5600d;
                assertEquals("batched history must not feed the content translation back into the drag",
                        expected, scrollPosition(target, horizontal), 0.02);
                scene.frames(30);
                scene.view.dispatchPointerMove(horizontal ? x + 25 : x,
                        horizontal ? y : y + 25, scene.clock);
                expected = 800 + 25d / 360d * 5600d;
                assertEquals("the next input batch must continue from the same scene origin",
                        expected, scrollPosition(target, horizontal), 0.02);
                scene.view.dispatchPointerUp(horizontal ? x + 25 : x,
                        horizontal ? y : y + 25, scene.clock + 1_000_000L);
                scene.frames(30);
                assertEquals(expected, scrollPosition(target, horizontal), 0.02);
            }
        }
    }

    private static double scrollPosition(Flickable target, boolean horizontal) {
        return horizontal ? target.contentX.peekFloat() : target.contentY.peekFloat();
    }

    @SuppressWarnings("unchecked")
    private static Property<Object> property(Item item, String name) throws Exception {
        return (Property<Object>) item.getClass().getField(name).get(item);
    }

    @Test public void accountGearsDoNotOverlapAndTargetTheirOwnProvider() throws Exception {
        for (String target : new String[]{"qq", "netease", "soda"}) {
            try (Scene scene = new Scene(temporary.newFolder().toPath())) {
                java.util.List<SourceAccountRow> rows = new java.util.ArrayList<>();
                for (String id : new String[]{"qq", "netease", "soda"}) {
                    SourceAccountRow row = new SourceAccountRow(); row.providerId=id; row.sourceName=id; rows.add(row);
                }
                scene.player.sourceAccounts.set(rows);
                scene.load("import QtQuick\nimport \"pages\"\nAccountPage { width:480; height:806 }");
                java.util.List<Item> rendered = named(scene.view.root(), "sourceAccountRow");
                assertEquals(3,rendered.size());
                for(int i=0;i<3;i++) {
                    Item row=rendered.get(i); assertEquals(i*88,row.y.peekFloat(),0.1);
                    assertEquals(rows.get(i).providerId,property(row,"providerId").peek());
                    if(target.equals(rows.get(i).providerId)) {
                        Item gear=named(row,"sourceAccountGear").get(0);
                        scene.click(gear,gear.width.peekFloat()/2,gear.height.peekFloat()/2);scene.frames(3);
                        SourceAccountRow chosen=(SourceAccountRow)property(scene.view.root(),"menuRow").peek();
                        assertEquals(target,chosen.providerId);
                    }
                }
            }
        }
    }

    @Test public void loginDialogDropsOldAppModeWhileNewSourceMetadataLoads() throws Exception {
        try (Scene scene = new Scene(temporary.newFolder().toPath())) {
            scene.player.pluginLoginActive.set(true);scene.player.loginMetadataReady.set(true);
            scene.player.pluginAppLoginAvailable.set(true);scene.player.loginProviderName.set("QQ");
            scene.load("import QtQuick\nimport \"dialogs\"\nItem { LoginDialog { active:true } }");
            Item dialog=scene.view.findByObjectName("sourceLoginDialog");
            assertEquals(3,property(dialog,"loginMode").peekInt());
            scene.player.loginMetadataReady.set(false);scene.player.loginMetadataLoading.set(true);
            scene.player.pluginAppLoginAvailable.set(false);scene.frames(4);
            assertEquals(-1,property(dialog,"loginMode").peekInt());
            scene.player.loginProviderName.set("汽水");scene.player.pluginQrLoginAvailable.set(true);
            scene.player.pluginCredentialLoginAvailable.set(true);scene.player.loginMetadataLoading.set(false);
            scene.player.loginMetadataReady.set(true);scene.player.loginMetadataRevision.set(1L);scene.frames(4);
            assertEquals("Soda cannot retain QQ App login mode",0,property(dialog,"loginMode").peekInt());
            assertEquals("",scene.player.webLoginError.peek());
        }
    }

    @Test public void miniPlayerGapsCannotActivateCoveredSongs() throws Exception {
        try (Scene scene = new Scene(temporary.newFolder().toPath())) {
            scene.load("""
                import QtQuick
                import "components"
                Item { id: root; property int behind:0; property int opened:0
                    MouseArea { anchors.fill:parent; onClicked:root.behind++ }
                    MiniPlayer { objectName:"testMini"; x:12;y:200;width:440;height:80;z:1000
                        onLyricsRequested:root.opened++
                    }
                }
                """);
            Item mini=scene.view.findByObjectName("testMini");
            scene.click(mini,5,75);scene.frames(2);
            assertEquals(0,scene.number("behind"));assertEquals(1,scene.number("opened"));
            scene.click(mini,220,1);scene.frames(2);
            assertEquals(0,scene.number("behind"));assertEquals(2,scene.number("opened"));
            int mode=scene.player.playMode.peek();
            Item play=scene.view.findByObjectName("miniPlayerPlayButton");
            scene.click(play,play.width.peekFloat()/2,play.height.peekFloat()/2);scene.frames(2);
            assertEquals("transport has its own action",2,scene.number("opened"));assertEquals(mode,scene.player.playMode.peek().intValue());
        }
    }

    @Test public void draggingThePlayerHeaderDownClosesItWithoutTreatingHorizontalMovementAsDismiss() throws Exception {
        try (Scene scene = new Scene(temporary.newFolder().toPath())) {
            scene.player.lyricsOpen.set(true);scene.player.lyricSlide.set(1.0);
            scene.load("import QtQuick\nimport \"components\"\nItem { LyricOverlay { anchors.fill:parent; onCloseRequested:player.setLyricsOpen(false) } }");
            DirtyQueue q=scene.view.dirtyQueue();q.install();
            try {
                scene.view.dispatchPointerDown(160,30);scene.view.dispatchPointerMove(270,35);scene.view.dispatchPointerUp(270,35);q.flush();
                assertTrue(scene.player.lyricsOpen.peek());
                scene.view.dispatchPointerDown(160,30);scene.view.dispatchPointerMove(164,80);scene.view.dispatchPointerMove(168,345);
                assertTrue("finger owns position before release",scene.player.playbackDismissDragging.peek());
                assertTrue(scene.player.playbackDismissOffset.peek()>0.3);
                scene.view.dispatchPointerUp(168,345);q.flush();
                assertFalse("header swipe collapses player",scene.player.lyricsOpen.peek());
            } finally {q.uninstall();}
        }
    }

    @Test public void unifiedPlaylistCoverTracksTheActualFirstSongAndControlsLiveInOverflow() throws Exception {
        try(Scene scene=new Scene(temporary.newFolder().toPath())) {
            scene.player.openSourcePlaylistId.set("qplayer:playlist:unified-favorites");
            scene.player.playlistTitle.set("我喜欢 · 全部平台");scene.player.playlistLoading.set(false);
            dev.t1m3.qplayer.media.Song a=new dev.t1m3.qplayer.media.Song();a.id="qq:song:a";a.name=a.title="第一首";a.coverThumbPath="first.png";
            dev.t1m3.qplayer.media.Song b=new dev.t1m3.qplayer.media.Song();b.id="netease:song:b";b.name=b.title="第二首";b.coverThumbPath="second.png";
            scene.player.sourcePlaylistTracks.set(java.util.Arrays.asList(a,b));
            scene.load("import QtQuick\nimport \"pages\"\nPlaylistDetailPage { width:480; height:806 }");
            Item cover=scene.view.findByObjectName("playlistFirstSongCover");assertNotNull(cover);
            assertEquals("first.png",property(cover,"source").peek());
            var position=scene.view.root().mapFromItem(cover,0,0);assertTrue("no permanent filter block above cover",((Number)position.get("y")).floatValue()<110);
            scene.player.sourcePlaylistTracks.set(java.util.Arrays.asList(b,a));scene.frames(5);
            assertEquals("second.png",property(cover,"source").peek());
            Item more=scene.view.findByObjectName("playlistDetailOverflowButton");assertTrue(more.visible.peek());
            scene.click(more,more.width.peekFloat()/2,more.height.peekFloat()/2);scene.frames(5);
            Item menu=scene.view.findByObjectName("favoritesOverflowMenu");assertNotNull(menu);
            assertEquals("overflow is available even in the unified view",true,property(menu,"opened").peek());
        }
    }

    private static java.util.List<Item> named(Item parent,String name) {
        java.util.List<Item> result=new java.util.ArrayList<>();
        if(name.equals(parent.objectName.peek()))result.add(parent);
        for(Item child:parent.children)result.addAll(named(child,name));return result;
    }

    private static final class Scene implements AutoCloseable {
        final String oldBase = AppDirs.base();
        final String oldCache = AppDirs.cacheBase();
        final PlayerController player;
        final SettingsCore settings = new SettingsCore();
        final ClasspathResourceLoader resources = new ClasspathResourceLoader();
        QmlView view;
        long clock = 1_000_000_000L;

        Scene(Path base) {
            AppDirs.setBase(base.toString());
            AppDirs.setCacheBase(base.resolve("cache").toString());
            AudioBackend backend = (AudioBackend) Proxy.newProxyInstance(
                    AudioBackend.class.getClassLoader(), new Class<?>[]{AudioBackend.class},
                    (proxy, method, args) -> {
                        if (method.getReturnType() == boolean.class) return false;
                        if (method.getReturnType() == long.class) return 0L;
                        return null;
                    });
            player = new PlayerController(backend, track -> { });
            player.sourceSetupRequired.set(false);
            player.sourceSetupPending.set(false);
            settings.load(new JsonSettingsStore(), SettingsCatalog.DESKTOP);
        }

        void loadMain() throws Exception {
            String qml = new String(resources.load("Main.qml"), StandardCharsets.UTF_8);
            load(qml.replaceFirst("\\bid:\\s*app\\b", java.util.regex.Matcher.quoteReplacement("""
                    id: app
                    property int testAction: 0
                    property real testRootOpacity: rootPageMotion.contentOpacity
                    property bool testOffsetOpen: lyricOverlay.offsetPanelOpen
                    onTestActionChanged: {
                        if (testAction === 1) app.switchTo(app.libraryPageIndex)
                        if (testAction === 2) app.replacePage("settings", 0)
                        if (testAction === 3) app.goHome()
                        if (testAction === 4) app.switchTo(1)
                        if (testAction === 8) app.switchTo(0)
                        if (testAction === 5 || testAction === 6) app.handleBack()
                        if (testAction === 7) {
                            player.lyricsOpen = true
                            player.lyricSlide = 1
                            lyricOverlay.offsetPanelOpen = true
                        }
                    }
                    """)));
        }

        void load(String qml) {
            view = QmlView.withStockTypes(new QmlEngine()).resources(resources)
                    .context("player", player).context("settings", settings)
                    .context("i18n", I18n.instance()).context("hostWindow", new WindowChromeStub());
            view.load(qml);
            view.root().width.set(480);
            view.root().height.set(806);
            frames(4);
        }

        void describeDialog(String input, long session) {
            DirtyQueue queue = view.dirtyQueue();
            queue.install();
            try {
                player.pluginDialogJson.set("{\"body\":[{\"type\":\"input\",\"id\":\"name\",\"value\":\""
                        + input + "\"}]}");
                player.pluginDialogOpen.set(true);
                player.pluginDialogSessionRevision.set(session);
                queue.flush();
            } finally { queue.uninstall(); }
            frames(3);
        }

        void set(String name, Object value) throws Exception {
            DirtyQueue queue = view.dirtyQueue();
            queue.install();
            try { property(view.root(), name).set(value); queue.flush(); }
            finally { queue.uninstall(); }
            frames(2);
        }

        Object value(String name) throws Exception { return property(view.root(), name).peek(); }
        int number(String name) throws Exception { return ((Number) value(name)).intValue(); }

        void click(Item item, float localX, float localY) {
            var point = view.root().mapFromItem(item, localX, localY);
            float x = ((Number) point.get("x")).floatValue();
            float y = ((Number) point.get("y")).floatValue();
            DirtyQueue queue = view.dirtyQueue();
            queue.install();
            try { view.dispatchPointerDown(x, y); view.dispatchPointerUp(x, y); queue.flush(); }
            finally { queue.uninstall(); }
        }

        void frames(int count) {
            DirtyQueue queue = view.dirtyQueue();
            queue.install();
            try {
                for (int i = 0; i < count; i++) {
                    clock += 16_000_000L;
                    view.tickAnimations(clock);
                    queue.flush();
                    view.renderer().layoutOnly(view.root());
                    queue.flush();
                }
            } finally { queue.uninstall(); }
        }

        @Override public void close() {
            try { if (view != null) view.dispose(); }
            finally {
                try { player.shutdownAsync().join(); }
                finally { AppDirs.setBase(oldBase); AppDirs.setCacheBase(oldCache); }
            }
        }
    }
}
