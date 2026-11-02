package dev.t1m3.qplayer.desktop.window;

import dev.t1m3.qplayer.audio.AudioBackend;
import dev.t1m3.qplayer.bridge.PlayerController;
import dev.t1m3.qplayer.bridge.WindowChromeStub;
import dev.t1m3.qplayer.desktop.resources.ClasspathResourceLoader;
import dev.t1m3.qplayer.desktop.settings.JsonSettingsStore;
import dev.t1m3.qplayer.i18n.I18n;
import dev.t1m3.qplayer.lyric.LrcParser;
import dev.t1m3.qplayer.lyric.skia.LyricCompositor;
import dev.t1m3.qplayer.qml.BundledQml;
import dev.t1m3.qplayer.settings.SettingsCatalog;
import dev.t1m3.qplayer.settings.SettingsCore;
import dev.t1m3.qplayer.store.AppDirs;
import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.QmlView;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.humbleui.skija.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

/** Render the production compiled scene and host lyric compositor, rather than an HTML mockup. */
public class PlaybackPagePreviewTest {
    @Test public void portraitPagesFitAtPhoneSizesAndTextOnlyHitTestingWorks() throws Exception {
        String oldBase=AppDirs.base(), oldCache=AppDirs.cacheBase();
        Path profile=Files.createTempDirectory("playback-preview");
        PlayerController player=null; QmlView view=null;
        LyricCompositor compositor=new LyricCompositor();
        try {
            AppDirs.setBase(profile.toString()); AppDirs.setCacheBase(profile.resolve("cache").toString());
            AudioBackend backend=(AudioBackend) Proxy.newProxyInstance(AudioBackend.class.getClassLoader(),new Class<?>[]{AudioBackend.class},(p,m,a)-> {
                if(m.getReturnType()==boolean.class)return false;
                if(m.getReturnType()==long.class)return 0L;
                return null;
            });
            player=new PlayerController(backend,t->{});
            player.sourceSetupPending.set(false); player.sourceSetupRequired.set(false);
            player.title.set("在路上"); player.artist.set("QPlayer · 播放界面预览");
            player.durationMs.set(197000L); player.positionMs.set(12000L);
            player.currentLikeable.set(true); player.currentLiked.set(true);
            player.coverSeed.set("#618AC0");
            player.lyrics.set(LrcParser.parse("[00:00.00]让音乐陪你走过每一天\n[00:06.00]沿着心里的方向\n[00:12.00]我们在路上\n[00:18.00]把每个瞬间收藏\n[00:24.00]直到远方亮起微光"));
            player.lyricsCoverOnly.set(false); player.lyricsOpen.set(true);
            SettingsCore settings=new SettingsCore(); settings.load(new JsonSettingsStore(),SettingsCatalog.DESKTOP);
            ClasspathResourceLoader resources=new ClasspathResourceLoader();
            Path cover=profile.resolve("cover.png");
            try(Surface surface=Surface.makeRasterN32Premul(480,480); Paint paint=new Paint()) {
                Canvas c=surface.getCanvas(); c.clear(0xFF9AB8D8);
                paint.setColor(0xFF355D87); c.drawCircle(240,240,155,paint);
                paint.setColor(0xFFDEEBF7); c.drawCircle(240,240,100,paint);
                paint.setColor(0xFF355D87); c.drawCircle(240,240,18,paint);
                try(Image img=surface.makeImageSnapshot(); Data data=img.encodeToData()) {Files.write(cover,data.getBytes());}
            }
            player.coverPath.set(cover.toString());
            try(BundledQml bundle=new BundledQml("Main.qml")) {
                view=bundle.configure(QmlView.withStockTypes(new QmlEngine(bundle)),resources);
                view.context("player",player).context("settings",settings).context("i18n",I18n.instance()).context("hostWindow",new WindowChromeStub());
                DesktopWindow.loadFonts(view,resources); bundle.load(view);
                DirtyQueue queue=view.dirtyQueue(); queue.install();
                try {
                    for(int[] size:new int[][]{{390,844},{320,640}}) {
                        int w=size[0],h=size[1];view.root().width.set(w);view.root().height.set(h);
                        for(boolean coverPage:new boolean[]{true,false}) {
                            player.setCoverMode(coverPage);
                            try(Surface surface=Surface.makeRasterN32Premul(w,h)) {
                                for(int frame=0;frame<60;frame++) {
                                    view.tickAnimations(System.nanoTime());
                                    queue.flush(); view.renderer().layoutOnly(view.root());
                                    compositor.composite(surface.getCanvas(),view.renderer(),view,player,settings,null,1f,w,h);
                                    Thread.sleep(10);
                                }
                                Item pause=view.findByObjectName("lyricPauseButton");assertNotNull(pause);
                                assertTrue("large play button",pause.width.peekFloat()>=55);
                                String out=System.getProperty("qplayer.preview.dir");
                                if(out!=null) {
                                    Path dir=Paths.get(out);Files.createDirectories(dir);
                                    try(Image img=surface.makeImageSnapshot();Data data=img.encodeToData()) {
                                        Files.write(dir.resolve((coverPage?"cover-":"lyrics-")+w+".png"),data.getBytes());
                                    }
                                }
                                if(!coverPage) {
                                    assertEquals(-1L,compositor.lyricRenderer().timeAtPoint(w-2,h/2f));
                                    boolean anyTextHit=false;
                                    for(int y=150;y<h-192;y+=3)for(int x=28;x<w-28;x+=8)
                                        if(compositor.lyricRenderer().timeAtPoint(x,y)>=0) anyTextHit=true;
                                    assertTrue("visible lyrics are clickable",anyTextHit);
                                }
                            }
                        }
                    }
                    view.root().width.set(390);view.root().height.set(844);
                    player.commentsOpen.set(true);player.commentsTitle.set("在路上 · 评论");
                    dev.t1m3.qplayer.media.SongComment comment=new dev.t1m3.qplayer.media.SongComment();
                    comment.id="preview";comment.author="评论界面示例";comment.content="这里展示音源返回的真实评论内容。此条仅用于界面预览。";comment.time="2026-11-02";comment.likeCount=12;
                    player.songComments.set(java.util.Arrays.asList(comment));
                    player.commentsStatus.set("评论页布局预览");
                    try(Surface surface=Surface.makeRasterN32Premul(390,844)) {
                        for(int i=0;i<6;i++){queue.flush();view.renderer().layoutOnly(view.root());compositor.composite(surface.getCanvas(),view.renderer(),view,player,settings,null,1f,390,844);}
                        String out=System.getProperty("qplayer.preview.dir");
                        if(out!=null)try(Image img=surface.makeImageSnapshot();Data data=img.encodeToData()){Files.write(Paths.get(out,"comments-390.png"),data.getBytes());}
                    }
                } finally {queue.uninstall();}
            }
        } finally {
            compositor.dispose(); if(view!=null)view.dispose();
            if(player!=null)player.shutdownAsync().join();AppDirs.setBase(oldBase);AppDirs.setCacheBase(oldCache);
        }
    }
}
