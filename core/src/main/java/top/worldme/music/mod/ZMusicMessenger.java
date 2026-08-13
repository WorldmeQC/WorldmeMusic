package top.worldme.music.mod;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.worldme.music.WorldmeMusicPlugin;
import top.worldme.music.config.PluginConfig;

import java.nio.charset.StandardCharsets;

/**
 * 与 zmusic-mod 客户端 Mod 通信。
 *
 * @author Worldme
 * @since 1.0.0
 */
public class ZMusicMessenger {

    private final WorldmeMusicPlugin plugin;
    private final PluginConfig config;

    public ZMusicMessenger(WorldmeMusicPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void broadcastPlay(String url) {
        byte[] payload = buildPayload("[Play]" + url);
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendRaw(player, payload);
        }
    }

    public void broadcastStop() {
        byte[] payload = buildPayload("[Stop]");
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendRaw(player, payload);
        }
    }

    public void sendPlay(Player player, String url) {
        sendRaw(player, buildPayload("[Play]" + url));
    }

    public void sendStop(Player player) {
        sendRaw(player, buildPayload("[Stop]"));
    }

    private void sendRaw(Player player, byte[] payload) {
        player.sendPluginMessage(plugin, config.getChannelZmusic(), payload);
        if (config.isSendAllmusic()) {
            player.sendPluginMessage(plugin, config.getChannelAllmusic(), payload);
        }
    }

    private byte[] buildPayload(String text) {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[body.length + 1];
        payload[0] = (byte) 0x9A;
        System.arraycopy(body, 0, payload, 1, body.length);
        return payload;
    }
}
