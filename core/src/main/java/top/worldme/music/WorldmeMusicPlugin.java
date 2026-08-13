package top.worldme.music;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import top.worldme.music.api.NeteaseClient;
import top.worldme.music.command.MusicCommand;
import top.worldme.music.config.PluginConfig;
import top.worldme.music.listener.PlayerListener;
import top.worldme.music.login.LoginManager;
import top.worldme.music.mod.ZMusicMessenger;
import top.worldme.music.queue.MusicQueue;
import top.worldme.music.queue.VoteSkipManager;

/**
 * WorldmeMusic 插件主入口。
 *
 * @author Worldme
 * @since 1.0.0
 */
public class WorldmeMusicPlugin extends JavaPlugin {

    private static WorldmeMusicPlugin instance;

    private PluginConfig pluginConfig;
    private NeteaseClient neteaseClient;
    private LoginManager loginManager;
    private ZMusicMessenger zMusicMessenger;
    private MusicQueue musicQueue;
    private VoteSkipManager voteSkipManager;
    private MusicCommand musicCommand;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        instance = this;

        saveDefaultConfig();
        this.pluginConfig = new PluginConfig(getDataFolder());

        this.neteaseClient = new NeteaseClient(pluginConfig);
        this.zMusicMessenger = new ZMusicMessenger(this, pluginConfig);
        this.musicQueue = new MusicQueue(this, pluginConfig, neteaseClient, zMusicMessenger);
        this.voteSkipManager = new VoteSkipManager(this, pluginConfig, musicQueue);
        this.musicQueue.setVoteSkipManager(voteSkipManager);
        this.loginManager = new LoginManager(this, pluginConfig, neteaseClient);
        this.neteaseClient.setLoginManager(loginManager);
        this.musicCommand = new MusicCommand(this, pluginConfig, neteaseClient, musicQueue, voteSkipManager, loginManager);

        registerChannels();
        registerCommands();
        registerListeners();

        getLogger().info("WorldmeMusic 已加载，耗时 " + (System.currentTimeMillis() - start) + " ms");
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if (musicQueue != null) {
            musicQueue.stop();
        }
        if (loginManager != null) {
            loginManager.cancelActiveSession();
        }
        getLogger().info("WorldmeMusic 已卸载");
    }

    private void registerChannels() {
        try {
            getServer().getMessenger().registerOutgoingPluginChannel(this, pluginConfig.getChannelZmusic());
            if (pluginConfig.isSendAllmusic()) {
                getServer().getMessenger().registerOutgoingPluginChannel(this, pluginConfig.getChannelAllmusic());
            }
            getLogger().info("已注册 Mod 通信通道");
        } catch (Exception e) {
            getLogger().warning("注册 Mod 通信通道失败: " + e.getMessage());
        }
    }

    private void registerCommands() {
        var music = getCommand("music");
        if (music != null) {
            music.setExecutor(musicCommand);
            music.setTabCompleter(musicCommand);
        }
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(this, musicQueue), this);
    }

    public static WorldmeMusicPlugin getInstance() {
        return instance;
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public NeteaseClient getNeteaseClient() {
        return neteaseClient;
    }

    public LoginManager getLoginManager() {
        return loginManager;
    }

    public ZMusicMessenger getZMusicMessenger() {
        return zMusicMessenger;
    }

    public MusicQueue getMusicQueue() {
        return musicQueue;
    }

    public VoteSkipManager getVoteSkipManager() {
        return voteSkipManager;
    }

    public MusicCommand getMusicCommand() {
        return musicCommand;
    }
}
