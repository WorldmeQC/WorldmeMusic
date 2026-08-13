package top.worldme.music.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家搜索会话。
 *
 * @author Worldme
 * @since 1.0.0
 */
public class PlayerSession {

    private String lastKeyword = "";
    private int currentOffset = 0;
    private final List<Track> lastSearch = new ArrayList<>();

    public String getLastKeyword() {
        return lastKeyword;
    }

    public void setLastKeyword(String lastKeyword) {
        this.lastKeyword = lastKeyword;
    }

    public int getCurrentOffset() {
        return currentOffset;
    }

    public void setCurrentOffset(int currentOffset) {
        this.currentOffset = currentOffset;
    }

    public List<Track> getLastSearch() {
        return lastSearch;
    }

    public void setLastSearch(List<Track> tracks) {
        lastSearch.clear();
        if (tracks != null) {
            lastSearch.addAll(tracks);
        }
    }

    public Track getTrackByIndex(int index) {
        if (index < 1 || index > lastSearch.size()) {
            return null;
        }
        return lastSearch.get(index - 1);
    }
}
