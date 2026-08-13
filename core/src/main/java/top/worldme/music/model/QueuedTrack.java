package top.worldme.music.model;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 队列中的一首歌。
 *
 * @author Worldme
 * @since 1.0.0
 */
public class QueuedTrack {

    private final Track track;
    private final UUID requester;
    private final String requesterName;
    private final long queuedAt;

    public QueuedTrack(Track track, Player requester) {
        this.track = track;
        this.requester = requester.getUniqueId();
        this.requesterName = requester.getName();
        this.queuedAt = System.currentTimeMillis();
    }

    public Track getTrack() {
        return track;
    }

    public UUID getRequester() {
        return requester;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public long getQueuedAt() {
        return queuedAt;
    }

    public long getId() {
        return track.getId();
    }

    public String getName() {
        return track.getName();
    }

    public String getArtists() {
        return track.getArtists();
    }

    public long getDuration() {
        return track.getDuration();
    }
}
