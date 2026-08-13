package top.worldme.music.queue;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.worldme.music.WorldmeMusicPlugin;
import top.worldme.music.api.NeteaseClient;
import top.worldme.music.config.PluginConfig;
import top.worldme.music.model.QueuedTrack;
import top.worldme.music.model.Track;
import top.worldme.music.mod.ZMusicMessenger;
import top.worldme.music.util.TextUtil;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/**
 * 服务器播放队列核心。
 *
 * @author Worldme
 * @since 1.0.0
 */
public class MusicQueue {

    private final WorldmeMusicPlugin plugin;
    private final PluginConfig config;
    private final NeteaseClient neteaseClient;
    private final ZMusicMessenger messenger;

    private final Deque<QueuedTrack> queue = new LinkedList<>();
    private QueuedTrack currentTrack;
    private String currentUrl;
    private boolean playing = false;
    private int consecutiveFails = 0;
    private int nextTaskId = -1;
    private VoteSkipManager voteSkipManager;

    public MusicQueue(WorldmeMusicPlugin plugin, PluginConfig config,
                      NeteaseClient neteaseClient, ZMusicMessenger messenger) {
        this.plugin = plugin;
        this.config = config;
        this.neteaseClient = neteaseClient;
        this.messenger = messenger;
    }

    public void setVoteSkipManager(VoteSkipManager voteSkipManager) {
        this.voteSkipManager = voteSkipManager;
    }

    public void add(Track track, Player requester) {
        if (queue.size() >= config.getQueueMaxSize()) {
            requester.sendMessage(TextUtil.message(config.getPrefix(), "&c播放队列已满。"));
            return;
        }
        QueuedTrack queued = new QueuedTrack(track, requester);
        if (!playing || currentTrack == null) {
            queue.add(queued);
            playNext();
        } else {
            queue.add(queued);
            requester.sendMessage(TextUtil.message(config.getPrefix(), "&a已加入队列，当前排在第 &e" + queue.size() + " &a位。"));
        }
    }

    public void playNext() {
        cancelNextTask();
        QueuedTrack next = queue.poll();
        if (next == null) {
            stop();
            return;
        }
        playTrack(next);
    }

    public void forceSkip() {
        if (!playing) {
            return;
        }
        if (voteSkipManager != null && voteSkipManager.isVoting()) {
            // 强制切歌时直接结束投票
        }
        Bukkit.broadcastMessage(TextUtil.message(config.getPrefix(), "&e管理员强制切歌。"));
        playNext();
    }

    public void stop() {
        cancelNextTask();
        queue.clear();
        currentTrack = null;
        currentUrl = null;
        playing = false;
        consecutiveFails = 0;
        messenger.broadcastStop();
    }

    public boolean isPlaying() {
        return playing;
    }

    public QueuedTrack getCurrentTrack() {
        return currentTrack;
    }

    public Deque<QueuedTrack> getQueue() {
        return queue;
    }

    private void playTrack(QueuedTrack track) {
        currentTrack = track;
        playing = true;
        consecutiveFails = 0;

        Bukkit.broadcastMessage(TextUtil.message(config.getPrefix(),
                "&e正在播放：&f" + track.getName() + " &7- " + track.getArtists() + " &e点歌人：&f" + track.getRequesterName()));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String url = neteaseClient.getSongUrl(track.getId()).join();
                if (url == null || url.isBlank()) {
                    Bukkit.getScheduler().runTask(plugin, () -> handleUrlFailed("无法获取播放链接"));
                    return;
                }
                Long duration = neteaseClient.getSongDuration(track.getId()).join();
                if (duration == null || duration <= 0) {
                    duration = track.getDuration();
                }
                long finalDuration = duration;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    currentUrl = url;
                    messenger.broadcastPlay(url);
                    scheduleNext(finalDuration);
                });
            } catch (Exception e) {
                plugin.getLogger().warning("获取歌曲 URL 异常: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> handleUrlFailed("获取歌曲 URL 异常"));
            }
        });
    }

    private void handleUrlFailed(String reason) {
        consecutiveFails++;
        Bukkit.broadcastMessage(TextUtil.message(config.getPrefix(), "&c" + reason + "，跳过当前歌曲。"));
        if (consecutiveFails >= config.getQueueMaxConsecutiveFails()) {
            Bukkit.broadcastMessage(TextUtil.message(config.getPrefix(), "&c连续获取失败次数过多，停止播放。"));
            stop();
            return;
        }
        playNext();
    }

    private void scheduleNext(long durationMs) {
        long ticks = Math.max(20L, durationMs / 50L);
        nextTaskId = Bukkit.getScheduler().runTaskLater(plugin, this::playNext, ticks).getTaskId();
    }

    private void cancelNextTask() {
        if (nextTaskId != -1) {
            Bukkit.getScheduler().cancelTask(nextTaskId);
            nextTaskId = -1;
        }
    }

    public void printQueue(Player player) {
        player.sendMessage(TextUtil.message(config.getPrefix(), "&e===== 播放队列 ====="));
        if (currentTrack != null) {
            player.sendMessage("&a▶ &f" + currentTrack.getName() + " &7- " + currentTrack.getArtists() + " &e点歌人：&f" + currentTrack.getRequesterName());
        } else {
            player.sendMessage("&7当前没有播放中的歌曲。");
        }
        int index = 1;
        for (QueuedTrack t : queue) {
            player.sendMessage("&e" + index + ". &f" + t.getName() + " &7- " + t.getArtists() + " &e点歌人：&f" + t.getRequesterName());
            index++;
        }
        if (queue.isEmpty()) {
            player.sendMessage("&7队列为空。");
        }
        player.sendMessage(TextUtil.message(config.getPrefix(), "&e=================="));
    }

    public void printNow(Player player) {
        if (currentTrack == null) {
            player.sendMessage(TextUtil.message(config.getPrefix(), "&7当前没有播放中的歌曲。"));
            return;
        }
        player.sendMessage(TextUtil.message(config.getPrefix(), "&e当前播放：&f" + currentTrack.getName() + " &7- " + currentTrack.getArtists()));
        player.sendMessage("&e点歌人：&f" + currentTrack.getRequesterName());
    }

    public void resendToPlayer(Player player) {
        if (!playing || currentTrack == null) {
            return;
        }
        if (currentUrl != null && !currentUrl.isBlank()) {
            messenger.sendPlay(player, currentUrl);
            return;
        }
        // 如果没有缓存 URL，重新获取
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String url = neteaseClient.getSongUrl(currentTrack.getId()).join();
                if (url != null && !url.isBlank()) {
                    String finalUrl = url;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        currentUrl = finalUrl;
                        messenger.sendPlay(player, finalUrl);
                    });
                }
            } catch (Exception ignored) {
            }
        });
    }
}
