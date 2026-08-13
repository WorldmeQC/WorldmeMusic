package top.worldme.music.model;

/**
 * 一行歌词。
 *
 * @author Worldme
 * @since 1.0.0
 */
public class LyricLine {

    private final long timestamp;
    private final String text;

    public LyricLine(long timestamp, String text) {
        this.timestamp = timestamp;
        this.text = text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getText() {
        return text;
    }
}
