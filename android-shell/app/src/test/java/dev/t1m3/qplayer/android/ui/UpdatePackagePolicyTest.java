package dev.t1m3.qplayer.android.ui;
import org.junit.Test;
import static org.junit.Assert.*;
public class UpdatePackagePolicyTest {
    private static final byte[][] SIGNERS={{1,2,3}};
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
