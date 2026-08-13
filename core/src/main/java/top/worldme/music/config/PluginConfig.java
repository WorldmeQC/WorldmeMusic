package top.worldme.music.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * 插件配置封装。
 *
 * @author Worldme
 * @since 1.0.0
 */
public class PluginConfig {

    private final File configFile;
    private FileConfiguration config;

    public PluginConfig(File dataFolder) {
        this.configFile = new File(dataFolder, "config.yml");
        reload();
    }

    public void reload() {
        if (!configFile.exists()) {
            // 首次启动时让 Bukkit 保存默认 config.yml
            config = YamlConfiguration.loadConfiguration(configFile);
        } else {
            config = YamlConfiguration.loadConfiguration(configFile);
        }
    }

    public void save() {
        try {
            config.save(configFile);
        } catch (Exception e) {
            throw new RuntimeException("无法保存 config.yml", e);
        }
    }

    public String getPrefix() {
        return config.getString("prefix", "<aqua>[WorldmeMusic] <reset>");
    }

    public String getApiBaseUrl() {
        return config.getString("api.base-url", "http://localhost:3000");
    }

    public int getApiTimeoutSeconds() {
        return config.getInt("api.timeout-seconds", 10);
    }

    public int getApiSearchLimit() {
        return config.getInt("api.search-limit", 10);
    }

    public String getApiSoundQuality() {
        return config.getString("api.sound-quality", "exhigh");
    }

    public boolean isLoginEnabled() {
        return config.getBoolean("login.enabled", true);
    }

    public int getPollInterval() {
        return config.getInt("login.poll-interval", 3);
    }

    public int getQrTimeout() {
        return config.getInt("login.qr-timeout", 180);
    }

    public int getQrCellSize() {
        return config.getInt("login.qr-cell-size", 4);
    }

    public int getQrMargin() {
        return config.getInt("login.qr-margin", 6);
    }

    public int getVoteDurationSeconds() {
        return config.getInt("vote.duration-seconds", 10);
    }

    public double getVoteThresholdRatio() {
        return config.getDouble("vote.threshold-ratio", 0.5);
    }

    public int getQueueMaxSize() {
        return config.getInt("queue.max-size", 50);
    }

    public int getQueueMaxConsecutiveFails() {
        return config.getInt("queue.max-consecutive-fails", 3);
    }

    public String getChannelZmusic() {
        return config.getString("mod.channel-zmusic", "zmusic:channel");
    }

    public boolean isSendAllmusic() {
        return config.getBoolean("mod.send-allmusic", true);
    }

    public String getChannelAllmusic() {
        return config.getString("mod.channel-allmusic", "allmusic:channel");
    }

    public boolean isDebug() {
        return config.getBoolean("debug", false);
    }
}
