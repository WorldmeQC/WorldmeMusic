package top.worldme.music.login;

import org.bukkit.scheduler.BukkitTask;

/**
 * 一次二维码登录会话。
 *
 * @author Worldme
 * @since 1.0.0
 */
public class QRLoginSession {

    private final String key;
    private final String qrUrl;
    private final long createdAt;
    private int mapId = -1;
    private BukkitTask pollTask;
    private volatile boolean completed = false;

    public QRLoginSession(String key, String qrUrl) {
        this.key = key;
        this.qrUrl = qrUrl;
        this.createdAt = System.currentTimeMillis();
    }

    public String getKey() {
        return key;
    }

    public String getQrUrl() {
        return qrUrl;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public int getMapId() {
        return mapId;
    }

    public void setMapId(int mapId) {
        this.mapId = mapId;
    }

    public BukkitTask getPollTask() {
        return pollTask;
    }

    public void setPollTask(BukkitTask pollTask) {
        this.pollTask = pollTask;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void cancel() {
        this.completed = true;
        if (pollTask != null && !pollTask.isCancelled()) {
            pollTask.cancel();
        }
    }
}
