package dev.t1m3.qplayer.settings;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackQualitySettingsTest {
    @Test public void legacyBooleanMigratesOnceAndNewSelectionSurvivesRestart() {
        MemoryStore store=new MemoryStore();store.putBool("highQuality",false);
        SettingsCore first=new SettingsCore();first.load(store,SettingsCatalog.ANDROID);
        assertEquals(0,((Number)first.value(SettingsCatalog.PLAYBACK_QUALITY_KEY)).intValue());
        first.setValue(SettingsCatalog.PLAYBACK_QUALITY_KEY,2);
        SettingsCore next=new SettingsCore();next.load(store,SettingsCatalog.ANDROID);
        assertEquals(2,((Number)next.value(SettingsCatalog.PLAYBACK_QUALITY_KEY)).intValue());
        assertTrue(next.bool(SettingsCatalog.AUTO_ROTATE_KEY));
        next.setValue(SettingsCatalog.AUTO_ROTATE_KEY,false);
        SettingsCore restarted=new SettingsCore();restarted.load(store,SettingsCatalog.ANDROID);
        assertFalse(restarted.bool(SettingsCatalog.AUTO_ROTATE_KEY));
    }
    private static class MemoryStore implements SettingsStore {
        final Map<String,Object> data=new HashMap<>();
        public boolean has(String k){return data.containsKey(k);}
        public boolean getBool(String k,boolean d){return has(k)?(Boolean)data.get(k):d;}
        public int getInt(String k,int d){return has(k)?((Number)data.get(k)).intValue():d;}
        public String getString(String k,String d){return has(k)?(String)data.get(k):d;}
        public void putBool(String k,boolean v){data.put(k,v);}
        public void putInt(String k,int v){data.put(k,v);}
        public void putString(String k,String v){data.put(k,v);}
    }
}
