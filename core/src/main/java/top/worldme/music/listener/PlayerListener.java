package top.worldme.music.listener;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import top.worldme.music.WorldmeMusicPlugin;
import top.worldme.music.command.MusicCommand;
import top.worldme.music.queue.MusicQueue;

/**
 * 玩家事件监听。
 *
 * @author Worldme
 * @since 1.0.0
 */
public class PlayerListener implements Listener {

    private final WorldmeMusicPlugin plugin;
    private final MusicQueue musicQueue;

    public PlayerListener(WorldmeMusicPlugin plugin, MusicQueue musicQueue) {
        this.plugin = plugin;
        this.musicQueue = musicQueue;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // 延迟 1 秒后给新玩家补发当前播放
        Bukkit.getScheduler().runTaskLater(plugin, () -> musicQueue.resendToPlayer(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        MusicCommand.removeSession(event.getPlayer().getUniqueId());
    }
}
