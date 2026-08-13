package top.worldme.music.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import top.worldme.music.WorldmeMusicPlugin;
import top.worldme.music.api.NeteaseClient;
import top.worldme.music.config.PluginConfig;
import top.worldme.music.login.LoginManager;
import top.worldme.music.model.PlayerSession;
import top.worldme.music.model.Track;
import top.worldme.music.queue.MusicQueue;
import top.worldme.music.queue.VoteSkipManager;
import top.worldme.music.util.TextUtil;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * /music 命令处理器。
 *
 * @author Worldme
 * @since 1.0.0
 */
public class MusicCommand implements CommandExecutor, TabExecutor {

    private static final Map<UUID, PlayerSession> SESSIONS = new HashMap<>();

    private final WorldmeMusicPlugin plugin;
    private final PluginConfig config;
    private final NeteaseClient neteaseClient;
    private final MusicQueue musicQueue;
    private final VoteSkipManager voteSkipManager;
    private final LoginManager loginManager;

    public MusicCommand(WorldmeMusicPlugin plugin, PluginConfig config,
                        NeteaseClient neteaseClient, MusicQueue musicQueue,
                        VoteSkipManager voteSkipManager, LoginManager loginManager) {
        this.plugin = plugin;
        this.config = config;
        this.neteaseClient = neteaseClient;
        this.musicQueue = musicQueue;
        this.voteSkipManager = voteSkipManager;
        this.loginManager = loginManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "search" -> handleSearch(sender, args);
            case "add" -> handleAdd(sender, args);
            case "queue" -> handleQueue(sender);
            case "skip" -> handleSkip(sender, args);
            case "stop" -> handleStop(sender);
            case "now" -> handleNow(sender);
            case "login" -> handleLogin(sender, args);
            case "logout" -> handleLogout(sender);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleSearch(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldmemusic.search")) {
            sender.sendMessage(noPermission());
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.message(config.getPrefix(), "<red>该命令只能由玩家执行。"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(TextUtil.message(config.getPrefix(), "<red>用法：/music search <关键词>"));
            return;
        }
        String keyword = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (keyword.isBlank()) {
            sender.sendMessage(TextUtil.message(config.getPrefix(), "<red>关键词不能为空。"));
            return;
        }
        sender.sendMessage(TextUtil.message(config.getPrefix(), "<yellow>正在搜索：<white>" + keyword));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<Track> tracks = neteaseClient.search(keyword, config.getApiSearchLimit(), 0).join();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    PlayerSession session = getSession(player.getUniqueId());
                    session.setLastSearch(tracks);
                    session.setLastKeyword(keyword);
                    session.setCurrentOffset(0);
                    if (tracks.isEmpty()) {
                        player.sendMessage(TextUtil.message(config.getPrefix(), "<red>未找到相关歌曲。"));
                        return;
                    }
                    player.sendMessage(TextUtil.message(config.getPrefix(), "<green>搜索 ")
                            .append(Component.text(keyword, NamedTextColor.WHITE))
                            .append(TextUtil.parse("<green> 结果如下，点击 [<green>+<yellow>] 或输入 /music add <序号> 点歌：")));
                    int index = 1;
                    for (Track track : tracks) {
                        Component addButton = Component.text("[", NamedTextColor.YELLOW)
                                .append(Component.text("+", NamedTextColor.GREEN))
                                .append(Component.text("]", NamedTextColor.YELLOW))
                                .clickEvent(ClickEvent.runCommand("/music add " + index))
                                .hoverEvent(HoverEvent.showText(Component.text("点击添加这首歌", NamedTextColor.GREEN)));
                        Component line = track.toDisplay(index)
                                .append(Component.text(" ", NamedTextColor.YELLOW))
                                .append(addButton);
                        player.sendMessage(line);
                        index++;
                    }
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String cause = e.getCause() != null ? e.getCause().getClass().getSimpleName() : e.getClass().getSimpleName();
                    String msg = e.getCause() != null && e.getCause().getMessage() != null
                            ? e.getCause().getMessage()
                            : e.getMessage();
                    plugin.getLogger().warning("搜索失败: " + cause + " - " + msg);
                    player.sendMessage(TextUtil.message(config.getPrefix(), "<red>搜索失败，请检查 API 配置或网络连接。 (<gray>" + cause + "<red>)"));
                    if (config.isDebug()) {
                        e.printStackTrace();
                    }
                });
            }
        });
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldmemusic.add")) {
            sender.sendMessage(noPermission());
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.message(config.getPrefix(), "<red>该命令只能由玩家执行。"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(TextUtil.message(config.getPrefix(), "<red>用法：/music add <序号>"));
            return;
        }
        int index;
        try {
            index = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(TextUtil.message(config.getPrefix(), "<red>序号必须是数字。"));
            return;
        }
        PlayerSession session = getSession(player.getUniqueId());
        Track track = session.getTrackByIndex(index);
        if (track == null) {
            sender.sendMessage(TextUtil.message(config.getPrefix(), "<red>序号无效，请先搜索。"));
            return;
        }
        musicQueue.add(track, player);
    }

    private void handleQueue(CommandSender sender) {
        if (!sender.hasPermission("worldmemusic.queue")) {
            sender.sendMessage(noPermission());
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.message(config.getPrefix(), "<red>该命令只能由玩家执行。"));
            return;
        }
        musicQueue.printQueue(player);
    }

    private void handleSkip(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.message(config.getPrefix(), "<red>该命令只能由玩家执行。"));
            return;
        }
        if (args.length > 1 && "force".equalsIgnoreCase(args[1])) {
            if (!player.hasPermission("worldmemusic.admin")) {
                sender.sendMessage(noPermission());
                return;
            }
            musicQueue.forceSkip();
            return;
        }
        if (!player.hasPermission("worldmemusic.skip")) {
            sender.sendMessage(noPermission());
            return;
        }
        voteSkipManager.vote(player);
    }

    private void handleStop(CommandSender sender) {
        if (!sender.hasPermission("worldmemusic.admin")) {
            sender.sendMessage(noPermission());
            return;
        }
        musicQueue.stop();
        Bukkit.broadcast(TextUtil.message(config.getPrefix(), "<yellow>管理员已停止播放。"), null);
    }

    private void handleNow(CommandSender sender) {
        if (!sender.hasPermission("worldmemusic.now")) {
            sender.sendMessage(noPermission());
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.message(config.getPrefix(), "<red>该命令只能由玩家执行。"));
            return;
        }
        musicQueue.printNow(player);
    }

    private void handleLogin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldmemusic.admin")) {
            sender.sendMessage(noPermission());
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.message(config.getPrefix(), "<red>该命令只能由玩家执行。"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(TextUtil.message(config.getPrefix(), "<red>用法：/music login <qr|status|refresh>"));
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "qr" -> loginManager.startQrLogin(player);
            case "status" -> loginManager.printStatus(player);
            case "refresh" -> loginManager.refresh(player);
            default -> sender.sendMessage(TextUtil.message(config.getPrefix(), "<red>用法：/music login <qr|status|refresh>"));
        }
    }

    private void handleLogout(CommandSender sender) {
        if (!sender.hasPermission("worldmemusic.admin")) {
            sender.sendMessage(noPermission());
            return;
        }
        if (sender instanceof Player player) {
            loginManager.logout(player);
        } else {
            loginManager.logout(null);
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("worldmemusic.admin")) {
            sender.sendMessage(noPermission());
            return;
        }
        config.reload();
        sender.sendMessage(TextUtil.message(config.getPrefix(), "<green>配置已重载。"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(TextUtil.message(config.getPrefix(), "<yellow>===== WorldmeMusic ====="));
        sender.sendMessage(TextUtil.parse("<yellow>/music search <关键词> <gray>- 搜索歌曲"));
        sender.sendMessage(TextUtil.parse("<yellow>/music add <序号> <gray>- 点歌"));
        sender.sendMessage(TextUtil.parse("<yellow>/music queue <gray>- 查看队列"));
        sender.sendMessage(TextUtil.parse("<yellow>/music skip <gray>- 投票切歌"));
        sender.sendMessage(TextUtil.parse("<yellow>/music now <gray>- 当前歌曲"));
        if (sender.hasPermission("worldmemusic.admin")) {
            sender.sendMessage(TextUtil.parse("<yellow>/music skip force <gray>- 强制切歌"));
            sender.sendMessage(TextUtil.parse("<yellow>/music stop <gray>- 停止播放"));
            sender.sendMessage(TextUtil.parse("<yellow>/music login qr <gray>- 扫码登录"));
            sender.sendMessage(TextUtil.parse("<yellow>/music logout <gray>- 退出登录"));
            sender.sendMessage(TextUtil.parse("<yellow>/music reload <gray>- 重载配置"));
        }
        sender.sendMessage(TextUtil.message(config.getPrefix(), "<yellow>======================"));
    }

    private Component noPermission() {
        return TextUtil.message(config.getPrefix(), "<red>你没有权限执行此命令。");
    }

    private PlayerSession getSession(UUID uuid) {
        return SESSIONS.computeIfAbsent(uuid, k -> new PlayerSession());
    }

    public static void removeSession(UUID uuid) {
        SESSIONS.remove(uuid);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>(Arrays.asList("search", "add", "queue", "skip", "now"));
            if (sender.hasPermission("worldmemusic.admin")) {
                list.addAll(Arrays.asList("stop", "login", "logout", "reload"));
            }
            return list.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }
        if (args.length == 2 && "login".equalsIgnoreCase(args[0]) && sender.hasPermission("worldmemusic.admin")) {
            return Stream.of("qr", "status", "refresh")
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
