package dev.t1m3.qplayer.settings;

import dev.t1m3.qplayer.lyric.skia.LyricConfig;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlayerTextSizeTest {
    @Test public void presetsApplyImmediatelyAndRestoreFromTheStore() {
        Map<String,Object> values=new HashMap<>();
        SettingsStore store=new SettingsStore() {
            public boolean has(String k){return values.containsKey(k);}
            public boolean getBool(String k,boolean d){return values.containsKey(k)?(Boolean)values.get(k):d;}
            public int getInt(String k,int d){return values.containsKey(k)?((Number)values.get(k)).intValue():d;}
            public String getString(String k,String d){return values.containsKey(k)?(String)values.get(k):d;}
            public void putBool(String k,boolean v){values.put(k,v);}
            public void putInt(String k,int v){values.put(k,v);}
            public void putString(String k,String v){values.put(k,v);}
        };
        SettingsCore settings=new SettingsCore(); settings.load(store,SettingsCatalog.ANDROID);
        assertEquals(20,settings.lyricFontSize());
        for(int i=0;i<3;i++) {
            settings.setValue("playerTextSize",i);
            assertEquals(16+4*i,LyricConfig.instance.lyricFontSize.getValue().intValue());
        }
        SettingsCore reopened=new SettingsCore(); reopened.load(store,SettingsCatalog.ANDROID);
        assertEquals(24,reopened.lyricFontSize());
        settings.setValue("playerTextSize",1);
    }
}
