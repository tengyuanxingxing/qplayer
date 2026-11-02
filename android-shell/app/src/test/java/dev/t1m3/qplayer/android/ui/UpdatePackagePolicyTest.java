package dev.t1m3.qplayer.android.ui;
import org.junit.Test;
import static org.junit.Assert.*;
public class UpdatePackagePolicyTest {
    private static final byte[][] SIGNERS={{1,2,3}};
    @Test public void targetVersionMustMatchTheDownloadedPackageExactly(){
        assertTrue(UpdatePackagePolicy.matchesTarget("1.9.14-custom", "1.9.14-custom-debug"));
        assertFalse(UpdatePackagePolicy.matchesTarget("1.9.14-custom", "1.9.13-custom-debug"));
        assertFalse(UpdatePackagePolicy.matchesTarget("1.9.14-custom", "1.9.15-custom-debug"));
        assertFalse(UpdatePackagePolicy.matchesTarget("", "1.9.14-custom-debug"));
    }
    @Test public void versionComesFromTheRequestedReleaseNotTheSharedFilename(){
        String url="https://github.com/user/app/releases/download/v1.9.14-custom/QPlayer-1.9.14-custom-debug.apk";
        assertEquals("1.9.14-custom", UpdatePackagePolicy.targetVersion(url));
        assertEquals("1.9.14-custom", UpdatePackagePolicy.targetVersion("https://gh-proxy.com/"+url));
        assertEquals("", UpdatePackagePolicy.targetVersion("https://example.com/qplayer-update.apk"));
    }
    @Test public void sameAppAndSignerCanUpgrade(){assertTrue(UpdatePackagePolicy.compatible("custom",110,SIGNERS,"custom",111,SIGNERS));}
    @Test public void otherPackagesAndDowngradesAreRejected(){
        assertFalse(UpdatePackagePolicy.compatible("custom",110,SIGNERS,"official",111,SIGNERS));
        assertFalse(UpdatePackagePolicy.compatible("custom",110,SIGNERS,"custom",110,SIGNERS));
        assertFalse(UpdatePackagePolicy.compatible("custom",110,SIGNERS,"custom",109,SIGNERS));
    }
    @Test public void unsignedOrDifferentlySignedDownloadsAreRejected(){
        assertFalse(UpdatePackagePolicy.compatible("custom",110,SIGNERS,"custom",111,new byte[][]{{9}}));
        assertFalse(UpdatePackagePolicy.compatible("custom",110,SIGNERS,"custom",111,new byte[0][]));
        assertFalse(UpdatePackagePolicy.compatible("custom",110,SIGNERS,"custom",111,null));
    }
}
