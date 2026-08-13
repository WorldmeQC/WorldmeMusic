package top.worldme.music.queue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
            requester.sendMessage(TextUtil.message(config.getPrefix(), "<red>播放队列已满。"));
            return;
        }
        QueuedTrack queued = new QueuedTrack(track, requester);
        if (!playing || currentTrack == null) {
            queue.add(queued);
            playNext();
        } else {
            queue.add(queued);
            requester.sendMessage(TextUtil.message(config.getPrefix(), "<green>已加入队列，当前排在第 <yellow>" + queue.size() + " <green>位。"));
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
        Bukkit.broadcast(TextUtil.message(config.getPrefix(), "<yellow>管理员强制切歌。"), null);
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

        Bukkit.broadcast(TextUtil.message(config.getPrefix(), "<yellow>正在播放：")
                        .append(Component.text(track.getName(), NamedTextColor.WHITE))
                        .append(Component.text(" - " + track.getArtists(), NamedTextColor.GRAY))
                        .append(Component.text(" 点歌人：", NamedTextColor.YELLOW))
                        .append(Component.text(track.getRequesterName(), NamedTextColor.WHITE)),
                null);

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
        Bukkit.broadcast(TextUtil.message(config.getPrefix(), "<red>" + reason + "，跳过当前歌曲。"), null);
        if (consecutiveFails >= config.getQueueMaxConsecutiveFails()) {
            Bukkit.broadcast(TextUtil.message(config.getPrefix(), "<red>连续获取失败次数过多，停止播放。"), null);
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
        player.sendMessage(TextUtil.message(config.getPrefix(), "<yellow>===== 播放队列 ====="));
        if (currentTrack != null) {
            player.sendMessage(Component.text("▶ ", NamedTextColor.GREEN)
                    .append(Component.text(currentTrack.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" - " + currentTrack.getArtists(), NamedTextColor.GRAY))
                    .append(Component.text(" 点歌人：", NamedTextColor.YELLOW))
                    .append(Component.text(currentTrack.getRequesterName(), NamedTextColor.WHITE)));
        } else {
            player.sendMessage(TextUtil.parse("<gray>当前没有播放中的歌曲。"));
        }
        int index = 1;
        for (QueuedTrack t : queue) {
            player.sendMessage(Component.text(index + ". ", NamedTextColor.YELLOW)
                    .append(Component.text(t.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" - " + t.getArtists(), NamedTextColor.GRAY))
                    .append(Component.text(" 点歌人：", NamedTextColor.YELLOW))
                    .append(Component.text(t.getRequesterName(), NamedTextColor.WHITE)));
            index++;
        }
        if (queue.isEmpty()) {
            player.sendMessage(TextUtil.parse("<gray>队列为空。"));
        }
        player.sendMessage(TextUtil.message(config.getPrefix(), "<yellow>=================="));
    }

    public void printNow(Player player) {
        if (currentTrack == null) {
            player.sendMessage(TextUtil.message(config.getPrefix(), "<gray>当前没有播放中的歌曲。"));
            return;
        }
        player.sendMessage(TextUtil.message(config.getPrefix(), "<yellow>当前播放：")
                .append(Component.text(currentTrack.getName(), NamedTextColor.WHITE))
                .append(Component.text(" - " + currentTrack.getArtists(), NamedTextColor.GRAY)));
        player.sendMessage(Component.text("点歌人：", NamedTextColor.YELLOW)
                .append(Component.text(currentTrack.getRequesterName(), NamedTextColor.WHITE)));
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
