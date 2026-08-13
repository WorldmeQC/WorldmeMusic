package top.worldme.music.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 歌词解析器。
 *
 * @author Worldme
 * @since 1.0.0
 */
public class Lyrics {

    private static final Pattern TIME_PATTERN = Pattern.compile("\\[(\\d{2}):(?<seconds>\\d{2}\\.?\\d*)\\]");

    private final List<LyricLine> lines;

    public Lyrics(List<LyricLine> lines) {
        List<LyricLine> sorted = new ArrayList<>(lines);
        sorted.sort(Comparator.comparingLong(LyricLine::getTimestamp));
        this.lines = Collections.unmodifiableList(sorted);
    }

    /**
     * 从 LRC 格式文本解析歌词。
     */
    public static Lyrics parse(String lrcText) {
        List<LyricLine> result = new ArrayList<>();
        if (lrcText == null || lrcText.isBlank()) {
            return new Lyrics(result);
        }
        for (String rawLine : lrcText.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            List<Long> timestamps = new ArrayList<>();
            Matcher matcher = TIME_PATTERN.matcher(line);
            while (matcher.find()) {
                timestamps.add(parseTimestamp(matcher.group(1), matcher.group("seconds")));
            }
            if (timestamps.isEmpty()) {
                continue;
            }
            String text = line.replaceAll("\\[\\d{2}:\\d{2}\\.?\\d*\\]", "").trim();
            for (long timestamp : timestamps) {
                result.add(new LyricLine(timestamp, text));
            }
        }
        return new Lyrics(result);
    }

    private static long parseTimestamp(String minutes, String seconds) {
        try {
            long min = Long.parseLong(minutes);
            double sec = Double.parseDouble(seconds);
            return min * 60_000L + (long) (sec * 1000);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 获取指定时间点应显示的歌词。
     */
    public String getCurrentLine(long timestampMs) {
        if (lines.isEmpty()) {
            return "";
        }
        int index = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getTimestamp() <= timestampMs) {
                index = i;
            } else {
                break;
            }
        }
        if (index == -1) {
            return "";
        }
        return lines.get(index).getText();
    }

    /**
     * 获取下一行歌词（用于 lookahead 显示）。
     */
    public String getNextLine(long timestampMs) {
        if (lines.isEmpty()) {
            return "";
        }
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getTimestamp() > timestampMs) {
                return lines.get(i).getText();
            }
        }
        return "";
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
