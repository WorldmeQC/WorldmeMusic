package top.worldme.music.queue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.worldme.music.WorldmeMusicPlugin;
import top.worldme.music.config.PluginConfig;
import top.worldme.music.util.TextUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 投票切歌管理器。
 *
 * @author Worldme
 * @since 1.0.0
 */
public class VoteSkipManager {

    private final WorldmeMusicPlugin plugin;
    private final PluginConfig config;
    private final MusicQueue musicQueue;

    private final Set<UUID> voters = new HashSet<>();
    private boolean voting = false;
    private long startTime;
    private int taskId = -1;

    public VoteSkipManager(WorldmeMusicPlugin plugin, PluginConfig config, MusicQueue musicQueue) {
        this.plugin = plugin;
        this.config = config;
        this.musicQueue = musicQueue;
    }

    public void vote(Player player) {
        if (!musicQueue.isPlaying()) {
            player.sendMessage(TextUtil.message(config.getPrefix(), "<red>当前没有正在播放的歌曲。"));
            return;
        }
        if (!voting) {
            startVote(player);
        } else {
            if (voters.contains(player.getUniqueId())) {
                player.sendMessage(TextUtil.message(config.getPrefix(), "<red>你已经投过票了。"));
                return;
            }
            voters.add(player.getUniqueId());
            broadcastProgress();
            if (checkThreshold()) {
                passVote();
            }
        }
    }

    public boolean isVoting() {
        return voting;
    }

    private void startVote(Player starter) {
        voting = true;
        voters.clear();
        voters.add(starter.getUniqueId());
        startTime = System.currentTimeMillis();

        Bukkit.broadcast(TextUtil.message(config.getPrefix(), "<yellow>玩家 ")
                        .append(Component.text(starter.getName(), NamedTextColor.WHITE))
                        .append(TextUtil.parse("<yellow> 发起了切歌投票，<green>" + config.getVoteDurationSeconds() + " <yellow>秒内输入 /music skip 同意。")));
        broadcastProgress();

        if (checkThreshold()) {
            passVote();
            return;
        }

        long ticks = Math.max(20L, config.getVoteDurationSeconds() * 20L);
        taskId = Bukkit.getScheduler().runTaskLater(plugin, this::endVote, ticks).getTaskId();
    }

    private void broadcastProgress() {
        int online = Bukkit.getOnlinePlayers().size();
        int need = getRequiredVotes(online);
        Bukkit.broadcast(TextUtil.message(config.getPrefix(), "<yellow>当前 <white>" + voters.size() + "/" + online + " <yellow>票，还需 <white>" + Math.max(0, need - voters.size()) + " <yellow>票切歌。"));
    }

    private int getRequiredVotes(int online) {
        // 赞成 > 一半，向上取整
        return online / 2 + 1;
    }

    private boolean checkThreshold() {
        int online = Bukkit.getOnlinePlayers().size();
        return voters.size() >= getRequiredVotes(online);
    }

    private void passVote() {
        if (!voting) {
            return;
        }
        voting = false;
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        Bukkit.broadcast(TextUtil.message(config.getPrefix(), "<green>投票通过，切换下一首！"));
        musicQueue.playNext();
    }

    private void endVote() {
        if (!voting) {
            return;
        }
        voting = false;
        taskId = -1;
        Bukkit.broadcast(TextUtil.message(config.getPrefix(), "<red>投票未通过，继续播放当前歌曲。"));
    }
}
