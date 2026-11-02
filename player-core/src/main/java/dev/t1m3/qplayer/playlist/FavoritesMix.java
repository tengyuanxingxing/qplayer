package dev.t1m3.qplayer.playlist;

import dev.t1m3.qplayer.media.Song;
import java.util.*;

/** Non-destructive, provider-neutral range mixing over the deduplicated favorites view. */
public final class FavoritesMix {
    private FavoritesMix() {}
    public static final class Config {
        public String baseProvider="qq", insertProvider="netease", mode="off";
        public String baseStart="",baseEnd="",insertStart="",insertEnd="";
        public int baseRatio=1,insertRatio=1;
    }
    private static int find(List<Song> rows,String id,int fallback) {
        if(id==null||id.isEmpty()||"*".equals(id))return fallback;
        for(int i=0;i<rows.size();i++)if(id.equals(rows.get(i).id))return i;
        return -1;
    }
    public static boolean valid(Map<String,List<Song>> sources,Config c) {
        List<Song> base=sources.get(c.baseProvider),insert=sources.get(c.insertProvider);
        if(base==null||insert==null||base.isEmpty()||insert.isEmpty()||c.baseProvider.equals(c.insertProvider)||c.baseEnd.isEmpty())return false;
        int a=find(base,c.baseStart,0),b=find(base,c.baseEnd,base.size()-1);
        int x=find(insert,c.insertStart,0),y=find(insert,c.insertEnd,insert.size()-1);
        return a>=0&&b>=a&&x>=0&&y>=x;
    }
    public static List<Song> order(Map<String,List<Song>> sources,String preferred,Config c) {
        List<List<Song>> ordered=new ArrayList<>();
        if(sources.containsKey(c.baseProvider))ordered.add(sources.get(c.baseProvider));
        if(!c.baseProvider.equals(c.insertProvider)&&sources.containsKey(c.insertProvider))ordered.add(sources.get(c.insertProvider));
        for(Map.Entry<String,List<Song>> e:sources.entrySet())if(!e.getKey().equals(c.baseProvider)&&!e.getKey().equals(c.insertProvider))ordered.add(e.getValue());
        if("off".equals(c.mode)||!valid(sources,c))return UnifiedFavorites.merge(ordered,preferred);
        List<Song> base=sources.get(c.baseProvider),insert=sources.get(c.insertProvider);
        int a=find(base,c.baseStart,0),b=find(base,c.baseEnd,base.size()-1),x=find(insert,c.insertStart,0),y=find(insert,c.insertEnd,insert.size()-1);
        List<Song> prefix=new ArrayList<>(),selected=new ArrayList<>(),tail=new ArrayList<>(),mix=new ArrayList<>(),remaining=new ArrayList<>();
        for(UnifiedFavorites.Group g:UnifiedFavorites.groups(ordered,preferred)) {
            Integer pos=g.positions.get(c.baseProvider),other=g.positions.get(c.insertProvider);
            if(pos!=null) {if(pos<a)prefix.add(g.song);else if(pos<=b)selected.add(g.song);else tail.add(g.song);}
            else if(other!=null&&other>=x&&other<=y)mix.add(g.song);else remaining.add(g.song);
        }
        List<Song> result=new ArrayList<>(prefix);
        if("blocks".equals(c.mode)){result.addAll(selected);result.addAll(mix);}
        else {
            int i=0,j=0;int n=Math.max(1,Math.min(10,c.baseRatio)),m=Math.max(1,Math.min(10,c.insertRatio));
            while(i<selected.size()||j<mix.size()) {
                for(int k=0;k<n&&i<selected.size();k++)result.add(selected.get(i++));
                for(int k=0;k<m&&j<mix.size();k++)result.add(mix.get(j++));
            }
        }
        result.addAll(tail);result.addAll(remaining);return result;
    }
}
