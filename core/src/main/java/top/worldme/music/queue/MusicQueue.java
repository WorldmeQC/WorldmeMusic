package top.worldme.music.queue;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.worldme.music.WorldmeMusicPlugin;
import top.worldme.music.api.NeteaseClient;
import top.worldme.music.config.PluginConfig;
import top.worldme.music.model.Lyrics;
import top.worldme.music.model.QueuedTrack;
import top.worldme.music.model.Track;
import top.worldme.music.mod.ZMusicMessenger;
import top.worldme.music.util.TextUtil;

import java.util.Deque;
import java.util.LinkedList;

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

    private BossBar bossBar;
    private int bossBarTaskId = -1;
    private long trackStartTime;
    private long trackDuration;
    private Lyrics lyrics;

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
        queue.add(queued);
        if (!playing || currentTrack == null) {
            Bukkit.broadcast(TextUtil.message(config.getPrefix(), "<yellow>玩家 ")
                            .append(Component.text(requester.getName(), NamedTextColor.WHITE))
                            .append(TextUtil.parse("<yellow> 点了 <green>" + track.getName() + " <yellow>- <gray>" + track.getArtists() + " <yellow>，即将播放。")));
            playNext();
        } else {
            Bukkit.broadcast(TextUtil.message(config.getPrefix(), "<yellow>玩家 ")
                            .append(Component.text(requester.getName(), NamedTextColor.WHITE))
                            .append(TextUtil.parse("<yellow> 点了 <green>" + track.getName() + " <yellow>- <gray>" + track.getArtists() + " <yellow>，当前排在第 <white>" + queue.size() + " <yellow>位。")));
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
        Bukkit.broadcast(TextUtil.message(config.getPrefix(), "<yellow>管理员强制切歌。"));
        playNext();
    }

    public void stop() {
        cancelNextTask();
        hideBossBar();
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
                        .append(Component.text(track.getRequesterName(), NamedTextColor.WHITE)));

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
                Lyrics fetchedLyrics = neteaseClient.getLyrics(track.getId()).join();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    currentUrl = url;
                    trackStartTime = System.currentTimeMillis();
                    trackDuration = finalDuration;
                    lyrics = fetchedLyrics;
                    messenger.broadcastPlay(url);
                    scheduleNext(finalDuration);
                    showBossBar(track, finalDuration);
                });
            } catch (Exception e) {
                plugin.getLogger().warning("获取歌曲 URL 异常: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> handleUrlFailed("获取歌曲 URL 异常"));
            }
        });
    }

    private void handleUrlFailed(String reason) {
        consecutiveFails++;
        Bukkit.broadcast(TextUtil.message(config.getPrefix(), "<red>" + reason + "，跳过当前歌曲。"));
        if (consecutiveFails >= config.getQueueMaxConsecutiveFails()) {
            Bukkit.broadcast(TextUtil.message(config.getPrefix(), "<red>连续获取失败次数过多，停止播放。"));
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

    private void showBossBar(QueuedTrack track, long durationMs) {
        hideBossBar();
        if (!config.isBossBarEnabled()) {
            return;
        }
        Component name = Component.text(track.getName(), NamedTextColor.WHITE)
                .append(Component.text(" - " + track.getArtists(), NamedTextColor.GRAY));
        bossBar = BossBar.bossBar(name, 0f, config.getBossBarColor(), BossBar.Overlay.PROGRESS);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addViewer(player);
        }
        long intervalTicks = Math.max(1L, config.getBossBarUpdateInterval() * 20L);
        bossBarTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::updateBossBar, 0L, intervalTicks).getTaskId();
    }

    private void updateBossBar() {
        if (bossBar == null || currentTrack == null || trackDuration <= 0) {
            return;
        }
        long elapsed = System.currentTimeMillis() - trackStartTime;
        float progress = Math.min(1f, Math.max(0f, (float) elapsed / (float) trackDuration));
        bossBar.progress(progress);

        Component name;
        if (lyrics != null && !lyrics.isEmpty()) {
            String currentLine = lyrics.getCurrentLine(elapsed);
            String nextLine = lyrics.getNextLine(elapsed);
            Component lyricComponent = Component.text(currentLine.isEmpty() ? "..." : currentLine, NamedTextColor.YELLOW);
            if (!nextLine.isEmpty()) {
                lyricComponent = lyricComponent
                        .append(Component.text("  »  ", NamedTextColor.GRAY))
                        .append(Component.text(nextLine, NamedTextColor.GRAY));
            }
            name = lyricComponent;
        } else {
            name = Component.text(currentTrack.getName(), NamedTextColor.WHITE)
                    .append(Component.text(" - " + currentTrack.getArtists(), NamedTextColor.GRAY));
        }
        bossBar.name(name);

        if (elapsed >= trackDuration) {
            hideBossBar();
        }
    }

    private void hideBossBar() {
        if (bossBarTaskId != -1) {
            Bukkit.getScheduler().cancelTask(bossBarTaskId);
            bossBarTaskId = -1;
        }
        if (bossBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                bossBar.removeViewer(player);
            }
            bossBar = null;
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
        if (bossBar != null) {
            bossBar.addViewer(player);
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
