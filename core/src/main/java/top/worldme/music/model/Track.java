package top.worldme.music.model;

/**
 * 歌曲元数据。
 *
 * @author Worldme
 * @since 1.0.0
 */
public class Track {

    private final long id;
    private final String name;
    private final String artists;
    private final String album;
    private final long duration;
    private final String coverUrl;

    public Track(long id, String name, String artists, String album, long duration, String coverUrl) {
        this.id = id;
        this.name = name;
        this.artists = artists;
        this.album = album;
        this.duration = duration;
        this.coverUrl = coverUrl;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getArtists() {
        return artists;
    }

    public String getAlbum() {
        return album;
    }

    public long getDuration() {
        return duration;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    /**
     * 格式化显示：序号. 歌名 - 歌手。
     */
    public String toDisplay(int index) {
        return "&a" + index + ". &f" + name + " &7- " + artists;
    }
}
