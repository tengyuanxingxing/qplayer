package dev.t1m3.qplayer.desktop.window;
import dev.t1m3.qplayer.audio.AudioBackend;
import dev.t1m3.qplayer.bridge.*;
import dev.t1m3.qplayer.desktop.resources.ClasspathResourceLoader;
import dev.t1m3.qplayer.desktop.settings.JsonSettingsStore;
import dev.t1m3.qplayer.i18n.I18n;
import dev.t1m3.qplayer.lyric.LrcParser;
import dev.t1m3.qplayer.lyric.skia.LyricCompositor;
import dev.t1m3.qplayer.media.Song;
import dev.t1m3.qplayer.qml.BundledQml;
import dev.t1m3.qplayer.settings.*;
import dev.t1m3.qplayer.store.AppDirs;
import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.render.QmlView;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.humbleui.skija.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Reproducible CPU/raster comparison, not a claim about a phone's GPU frame rate. */
public class PlaybackSwipePerformanceTest {
 @Test public void measureBothDirectionsInTheProductionScene() throws Exception {
  String base=AppDirs.base(),cache=AppDirs.cacheBase();Path dir=Files.createTempDirectory("swipe-profile");
  PlayerController p=null;QmlView v=null;LyricCompositor c=new LyricCompositor();
  try {
   AppDirs.setBase(dir.toString());AppDirs.setCacheBase(dir.resolve("cache").toString());
   AudioBackend backend=(AudioBackend)Proxy.newProxyInstance(AudioBackend.class.getClassLoader(),new Class<?>[]{AudioBackend.class},(obj,m,a)->{if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==long.class)return 0L;return null;});
   p=new PlayerController(backend,t->{});p.sourceSetupPending.set(false);p.sourceSetupRequired.set(false);
   p.title.set("测试一首比较长的歌曲标题 · Swipe Performance");p.artist.set("示例歌手 · Performance");p.coverSeed.set("#47694A");p.durationMs.set(180000L);
   p.lyrics.set(LrcParser.parse("[00:00.00]让音乐陪你走过每一天\n[00:12.00]沿着心里的方向\n[00:24.00]把每个瞬间收藏"));
   List<Song> songs=new ArrayList<>();for(int i=0;i<20;i++){Song s=new Song();s.id="qq:song:fixture"+i;s.name=s.title="示例推荐歌曲 "+i;s.artist="示例音乐人";songs.add(s);}p.playbackRelatedSongs.set(songs);
   Path art=dir.resolve("cover.png");try(Surface s=Surface.makeRasterN32Premul(600,600)){s.getCanvas().clear(0xFF47694A);try(Image im=s.makeImageSnapshot();Data data=im.encodeToData()){Files.write(art,data.getBytes());}}p.coverPath.set(art.toString());
   SettingsCore settings=new SettingsCore();settings.load(new JsonSettingsStore(),SettingsCatalog.DESKTOP);
   try(BundledQml b=new BundledQml("Main.qml")) {
    ClasspathResourceLoader resources=new ClasspathResourceLoader();v=b.configure(QmlView.withStockTypes(new QmlEngine(b)),resources);v.context("player",p).context("settings",settings).context("i18n",I18n.instance()).context("hostWindow",new WindowChromeStub());DesktopWindow.loadFonts(v,resources);b.load(v);
    v.renderer().setPictureCache(true);v.root().width.set(390);v.root().height.set(844);p.lyricsOpen.set(true);p.lyricSlide.set(1.0);
    DirtyQueue q=v.dirtyQueue();q.install();try(Surface surface=Surface.makeRasterN32Premul(390,844)) {
     q.flush();v.renderer().layoutOnly(v.root());
     for(int i=0;i<50;i++){v.tickAnimations(System.nanoTime());q.flush();c.composite(surface.getCanvas(),v.renderer(),v,p,settings,null,1f,390,844);}
     Map<String,Object> results=new LinkedHashMap<>();
     for(int target:new int[]{0,2}) {
      p.setCoverMode(true);p.updatePlaybackDrag(0);q.flush();c.composite(surface.getCanvas(),v.renderer(),v,p,settings,null,1f,390,844);
      double[] elapsed=new double[90];
      for(int frame=0;frame<elapsed.length;frame++) {
       double offset=(target==0?1:-1)*(frame+1.0)/elapsed.length;long before=System.nanoTime();
       p.updatePlaybackDrag(offset);v.tickAnimations(System.nanoTime());q.flush();c.composite(surface.getCanvas(),v.renderer(),v,p,settings,null,1f,390,844);
       elapsed[frame]=(System.nanoTime()-before)/1e6;
      }
      Arrays.sort(elapsed);Map<String,Double> times=new LinkedHashMap<>();times.put("medianMs",elapsed[45]);times.put("p95Ms",elapsed[85]);times.put("maxMs",elapsed[89]);results.put(target==0?"coverToInfo":"coverToLyrics",times);
      p.finishPlaybackDrag(0);
     }
     String out=System.getProperty("qplayer.swipe.report");String json=new com.google.gson.Gson().toJson(results);System.out.println("SWIPE_RASTER_PROFILE "+json);if(out!=null)Files.writeString(Paths.get(out),json);
     assertNotNull(v.findByObjectName("lyricChrome"));
    } finally {q.uninstall();}
   }
  } finally {c.dispose();if(v!=null)v.dispose();if(p!=null)p.shutdownAsync().join();AppDirs.setBase(base);AppDirs.setCacheBase(cache);}
 }
}
