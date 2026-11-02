package dev.t1m3.qplayer.media;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
public class AlbumCategoryTest {
    @Test public void providerTypesKeepReleaseVersionsDistinct() {
        assertEquals("album",AlbumCategory.of("录音室专辑","唱游"));
        assertEquals("live",AlbumCategory.of("现场专辑","演唱会"));
        assertEquals("ep",AlbumCategory.of("EP","某张唱片"));
        assertEquals("single",AlbumCategory.of("Single","单曲标题"));
        assertEquals("live",AlbumCategory.of("","Live at the Theatre"));
        assertEquals("unknown",AlbumCategory.of("","普通名称"));
        assertEquals("unknown",AlbumCategory.of("","Shepherd"));
    }
}
