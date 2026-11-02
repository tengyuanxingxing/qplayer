package dev.t1m3.qplayer.media;

import java.util.ArrayList;
import java.util.List;

/** Standard home contribution from one source provider. */
public final class ProviderHome {
    public List<Song> songs = new ArrayList<>();
    public List<Song> dailySongs = new ArrayList<>();
    public String recommendationStatus = "";
    public String dailyStatus = "";
    public List<Playlist> playlists = new ArrayList<>();
    /** Titled groups drawn before the plain recommendation grid. */
    public List<HomeSection> sections = new ArrayList<>();
}
