package dev.t1m3.qplayer.android.ui;

import java.util.Arrays;

/** Pure policy shared by the downloaded-APK check and its regression tests. */
final class UpdatePackagePolicy {
    static boolean compatible(String installedId, long installedVersion, byte[][] installedSigners,
                              String archiveId, long archiveVersion, byte[][] archiveSigners) {
        if(installedId == null || !installedId.equals(archiveId) || archiveVersion <= installedVersion)
            return false;
        if(installedSigners == null || archiveSigners == null || installedSigners.length == 0
                || installedSigners.length != archiveSigners.length) return false;
        for(byte[] installed : installedSigners) {
            boolean found=false;
            for(byte[] archived : archiveSigners) if(installed != null && Arrays.equals(installed,archived)) {found=true;break;}
            if(!found) return false;
        }
        return true;
    }
}
