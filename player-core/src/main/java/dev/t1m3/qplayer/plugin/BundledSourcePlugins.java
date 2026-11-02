package dev.t1m3.qplayer.plugin;

import dev.t1m3.qplayer.store.AppDirs;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Offline first-run installation. Signed by the same publisher as catalog updates. */
public final class BundledSourcePlugins {
    private static final String KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEM6OV1qocPEv9y68aeFzJQuoJ0sEShM3TK0TtsNc9cwch696qpq2SVk579xJ6sDZsISjUGNpwnpiEPbQ+CnxyZA==";
    private BundledSourcePlugins() {}
    public static void install() {
        PluginRegistry registry = new PluginRegistry();
        for (String id : new String[]{"qq", "netease"}) {
            Path temporary = null;
            try (InputStream input = BundledSourcePlugins.class.getResourceAsStream("bundled/"+id+".qplug")) {
                if (input == null) throw new IllegalStateException("missing bundled source "+id);
                Files.createDirectories(AppDirs.cacheDir());
                temporary = Files.createTempFile(AppDirs.cacheDir(), "bundled-"+id, ".qplug");
                Files.copy(input, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                java.security.PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(KEY)));
                VerifiedPluginPackage pkg = new PluginPackageVerifier().verify(temporary, key, false);
                PluginRegistry.Entry previous = registry.get(id);
                // Never downgrade a newer plugin or replace an independent publisher.
                if (previous != null && PluginCompatibility.compare(previous.activeVersion,pkg.manifest().version)>=0) continue;
                if (previous != null && !new String(Files.readAllBytes(AppDirs.pluginsDir().resolve(id)
                        .resolve(previous.activeVersion).resolve("plugin.json")), java.nio.charset.StandardCharsets.UTF_8)
                        .contains("tengyuanxingxing Custom")) continue;
                new PluginInstaller(registry).install(pkg,pkg.manifest().permissionSet());
                if (previous != null && !previous.enabled) registry.setEnabled(id,false);
            } catch (Exception error) {
                dev.t1m3.qplayer.util.Logger.warn("bundled plugin {} installation failed: {}",id,error.toString());
            } finally {
                if (temporary != null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) {}
            }
        }
    }
}
