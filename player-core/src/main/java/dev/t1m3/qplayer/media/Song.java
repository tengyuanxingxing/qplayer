package dev.t1m3.qplayer.media;

import java.util.ArrayList;
import java.util.List;

/** Source-neutral song DTO exposed to QML and persisted by the host. */
public final class Song {
    public String id = "";
    public String title = "";
    /** QML compatibility alias used by every remote-song list. */
    public String name = "";
    public List<MediaRef> artists = new ArrayList<>();
    public MediaRef album;
    public long durationMs;
    public String artworkUrl = "";
    /** Source-neutral artwork aliases consumed by the existing cards/rows. */
    public String coverUrl = "";
    public String coverThumbPath = "";
    public String artist = "";
    public String artistMediaId = "";
    public String artistIdsCsv = "";
    public String artistNamesCsv = "";
    public String isrc = "";
    public long publishTimeMs;
    public String credits = "";
    public String genre = "";
    /** Host-only label on the unified favorites projection; IDs still address their own provider. */
    public String favoriteSources = "";
    public boolean playable = true;
    public boolean trial;
    public boolean restricted;
    public boolean cachedOffline;
}
