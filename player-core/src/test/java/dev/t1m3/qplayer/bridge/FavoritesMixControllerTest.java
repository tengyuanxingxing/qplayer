package dev.t1m3.qplayer.bridge;
import dev.t1m3.qplayer.audio.AudioBackend;
import dev.t1m3.qplayer.media.Song;
import dev.t1m3.qplayer.playlist.UnifiedFavorites;
import dev.t1m3.qplayer.store.AppDirs;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class FavoritesMixControllerTest {
 @SuppressWarnings("unchecked") @Test public void persistedOptionsAreIsolatedByTheAccountPair() throws Exception {
  String old=AppDirs.base(),cache=AppDirs.cacheBase();Path base=Files.createTempDirectory("favorites-mix");PlayerController c=null;
  try{AppDirs.setBase(base.toString());AppDirs.setCacheBase(base.resolve("cache").toString());
   AudioBackend backend=(AudioBackend)Proxy.newProxyInstance(AudioBackend.class.getClassLoader(),new Class<?>[]{AudioBackend.class},(p,m,a)->{if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==long.class)return 0L;return null;});
   c=new PlayerController(backend,t->{});c.openSourcePlaylistId.set(UnifiedFavorites.ID);
   Field f=PlayerController.class.getDeclaredField("favoritesBySource");f.setAccessible(true);Map<String,List<Song>> sources=(Map<String,List<Song>>)f.get(c);
   List<Song> qq=new ArrayList<>(),ne=new ArrayList<>();for(int i=0;i<4;i++){Song s=new Song();s.id=s.title=s.name="qq:song:"+i;qq.add(s);}for(int i=0;i<2;i++){Song s=new Song();s.id=s.title=s.name="netease:song:"+i;ne.add(s);}sources.put("qq",qq);sources.put("netease",ne);
   Field a=PlayerController.class.getDeclaredField("favoriteAccountIds");a.setAccessible(true);Map<String,String> accounts=(Map<String,String>)a.get(c);accounts.put("qq","qq:user:a");accounts.put("netease","netease:user:a");
   Method load=PlayerController.class.getDeclaredMethod("reloadFavoriteMix");load.setAccessible(true);load.invoke(c);
   c.setFavoriteMixBoundary("qq:song:1",false);assertEquals("interleave",c.favoriteMixMode.peek());assertEquals("netease:song:0",c.sourcePlaylistTracks.peek().get(1).id);
   c.setFavoriteMixRatio(2,1);assertEquals("2∶1",c.favoriteMixRatio.peek());assertEquals("qq:song:1",c.sourcePlaylistTracks.peek().get(1).id);
   accounts.put("qq","qq:user:b");load.invoke(c);assertEquals("off",c.favoriteMixMode.peek());
   accounts.put("qq","qq:user:a");load.invoke(c);assertEquals("interleave",c.favoriteMixMode.peek());assertEquals("2∶1",c.favoriteMixRatio.peek());
   assertTrue(Files.isRegularFile(AppDirs.configFile("favorite-mix-v1.json")));
  } finally {if(c!=null)c.shutdownAsync().join();AppDirs.setBase(old);AppDirs.setCacheBase(cache);}
 }
}
