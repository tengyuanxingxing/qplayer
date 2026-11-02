package dev.t1m3.qplayer.plugin;

import dev.t1m3.qplayer.store.AppDirs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class BundledSourcePluginsTest {
    @Test public void offlineInstallIsSignedAndReopeningPreservesUserState() throws Exception {
        String oldBase=AppDirs.base(),oldCache=AppDirs.cacheBase(),version=PluginCompatibility.hostVersion();
        Path profile=Files.createTempDirectory("bundled-sources-test");
        try {
            AppDirs.setBase(profile.toString());AppDirs.setCacheBase(profile.resolve("cache").toString());
            PluginCompatibility.setHostVersion("1.9.18-custom");
            BundledSourcePlugins.install();
            PluginRegistry registry=new PluginRegistry();
            assertEquals(2,registry.entries().size());
            for (String id:Arrays.asList("qq","netease")) {
                PluginRegistry.Entry entry=registry.get(id);
                assertNotNull(entry);assertTrue(entry.enabled);assertTrue(entry.signed);
                assertTrue(Files.isRegularFile(AppDirs.pluginsDir().resolve(id).resolve(entry.activeVersion).resolve("src/main.js")));
            }
            registry.setEnabled("qq",false);registry.setPrimaryProvider("netease");
            Path canary=AppDirs.configFile("plugin-data").resolve("qq").resolve("login-canary");
            Files.createDirectories(canary.getParent());Files.write(canary,new byte[]{1,2,3});
            BundledSourcePlugins.install();registry=new PluginRegistry();
            assertFalse(registry.get("qq").enabled);assertEquals("netease",registry.primaryProvider());
            assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(canary));
        } finally {AppDirs.setBase(oldBase);AppDirs.setCacheBase(oldCache);PluginCompatibility.setHostVersion(version);}
    }
}
