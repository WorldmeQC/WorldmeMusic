package top.worldme.music.login;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitTask;
import top.worldme.music.WorldmeMusicPlugin;
import top.worldme.music.api.NeteaseClient;
import top.worldme.music.config.PluginConfig;
import top.worldme.music.model.AccountInfo;
import top.worldme.music.util.TextUtil;
import top.mrxiaom.qrcode.QRCode;
import top.mrxiaom.qrcode.enums.ErrorCorrectionLevel;
import top.mrxiaom.qrcode.utils.ImageUtil;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 网易云登录管理器（全局一个 VIP 账号）。
 *
 * @author Worldme
 * @since 1.0.0
 */
public class LoginManager {

    private final WorldmeMusicPlugin plugin;
    private final PluginConfig config;
    private final NeteaseClient neteaseClient;
    private final File loginFile;
    private final AccountInfo accountInfo;

    private QRLoginSession activeSession;

    public LoginManager(WorldmeMusicPlugin plugin, PluginConfig config,
                        NeteaseClient neteaseClient) {
        this.plugin = plugin;
        this.config = config;
        this.neteaseClient = neteaseClient;
        this.loginFile = new File(plugin.getDataFolder(), "login.yml");
        this.accountInfo = new AccountInfo();
        load();
    }

    public void load() {
        if (!loginFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(loginFile);
        accountInfo.setCookie(yaml.getString("cookie", ""));
        accountInfo.setLoginTime(yaml.getLong("login-time", 0));
        accountInfo.setNickname(yaml.getString("nickname", ""));
        accountInfo.setUserId(yaml.getLong("user-id", 0));
    }

    public void save() {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("cookie", accountInfo.getCookie());
            yaml.set("login-time", accountInfo.getLoginTime());
            yaml.set("nickname", accountInfo.getNickname());
            yaml.set("user-id", accountInfo.getUserId());
            yaml.save(loginFile);
        } catch (Exception e) {
            plugin.getLogger().warning("保存登录数据失败: " + e.getMessage());
        }
    }

    public boolean isLoggedIn() {
        return accountInfo.isLoggedIn();
    }

    public String getCookie() {
        return accountInfo.getCookie();
    }

    public AccountInfo getAccountInfo() {
        return accountInfo;
    }

    public void startQrLogin(Player admin) {
        if (!config.isLoginEnabled()) {
            admin.sendMessage(TextUtil.message(config.getPrefix(), "<red>登录模块未启用。"));
            return;
        }
        // 取消旧会话
        if (activeSession != null) {
            activeSession.cancel();
            activeSession = null;
        }

        admin.sendMessage(TextUtil.message(config.getPrefix(), "<yellow>正在生成二维码，请稍候..."));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String key = neteaseClient.qrLoginKey().join();
                if (key == null || key.isBlank()) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            admin.sendMessage(TextUtil.message(config.getPrefix(), "<red>获取二维码 key 失败。")));
                    return;
                }
                String qrUrl = neteaseClient.qrLoginCreate(key).join();
                if (qrUrl == null || qrUrl.isBlank()) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            admin.sendMessage(TextUtil.message(config.getPrefix(), "<red>生成二维码失败。")));
                    return;
                }

                BufferedImage qrImage = generateQrImage(qrUrl);
                BufferedImage mapImage = scaleToMapSize(qrImage, 128);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    QRLoginSession session = new QRLoginSession(key, qrUrl);
                    activeSession = session;
                    int mapId = giveQrMap(admin, mapImage);
                    session.setMapId(mapId);
                    admin.sendMessage(TextUtil.message(config.getPrefix(),
                            "<green>已发放二维码地图，请打开背包查看并使用网易云 App 扫码。"));
                    admin.sendMessage(TextUtil.message(config.getPrefix(),
                            "<gray>二维码有效期 <yellow>" + (config.getQrTimeout() / 60) + " <gray>分钟。"));
                    startPolling(session, admin);
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        admin.sendMessage(TextUtil.message(config.getPrefix(), "<red>生成二维码异常: ")
                                .append(Component.text(e.getMessage(), NamedTextColor.RED))));
                plugin.getLogger().warning("生成二维码异常: " + e.getMessage());
                if (config.isDebug()) {
                    e.printStackTrace();
                }
            }
        });
    }

    private BufferedImage generateQrImage(String qrUrl) {
        QRCode qr = QRCode.create(qrUrl, ErrorCorrectionLevel.H);
        int cellSize = Math.max(1, config.getQrCellSize());
        int margin = Math.max(0, config.getQrMargin());
        return ImageUtil.createImage(qr, cellSize, margin);
    }

    private BufferedImage scaleToMapSize(BufferedImage source, int size) {
        if (source.getWidth() == size && source.getHeight() == size) {
            return source;
        }
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(source, 0, 0, size, size, null);
        g.dispose();
        return scaled;
    }

    private int giveQrMap(Player player, BufferedImage image) {
        MapView view = Bukkit.createMap(player.getWorld());
        view.getRenderers().forEach(view::removeRenderer);
        view.addRenderer(new QrMapRenderer(image));

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        if (meta != null) {
            meta.setMapId(view.getId());
            mapItem.setItemMeta(meta);
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), mapItem);
            player.sendMessage(TextUtil.message(config.getPrefix(), "<yellow>背包已满，二维码地图已掉落在脚下。"));
        } else {
            player.getInventory().addItem(mapItem);
        }
        return view.getId();
    }

    private void removeQrMap(Player player, int mapId) {
        if (mapId < 0 || player == null) {
            return;
        }
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() != Material.FILLED_MAP) {
                continue;
            }
            if (!(item.getItemMeta() instanceof MapMeta meta)) {
                continue;
            }
            if (meta.hasMapId() && meta.getMapId() == mapId) {
                inv.setItem(i, null);
                return;
            }
        }
    }

    private void startPolling(QRLoginSession session, Player admin) {
        long intervalTicks = Math.max(20L, config.getPollInterval() * 20L);
        BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (session.isCompleted()) {
                session.cancel();
                return;
            }
            if (System.currentTimeMillis() - session.getCreatedAt() > config.getQrTimeout() * 1000L) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    admin.sendMessage(TextUtil.message(config.getPrefix(), "<red>二维码已过期，请重新执行 /music login qr。"));
                });
                session.cancel();
                return;
            }
            try {
                NeteaseClient.QrCheckResult result = neteaseClient.qrLoginCheck(session.getKey()).join();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (session.isCompleted()) {
                        return;
                    }
                    switch (result.getCode()) {
                        case 801 -> {
                            // 等待扫码，无需提示
                        }
                        case 802 -> {
                            admin.sendMessage(TextUtil.message(config.getPrefix(), "<yellow>等待确认..."));
                        }
                        case 800 -> {
                            session.cancel();
                            admin.sendMessage(TextUtil.message(config.getPrefix(), "<red>二维码已过期，请重新生成。"));
                        }
                        case 803 -> {
                            session.cancel();
                            removeQrMap(admin, session.getMapId());
                            saveLogin(result.getCookie(), result.getNickname(), result.getUserId());
                            admin.sendMessage(TextUtil.message(config.getPrefix(), "<green>登录成功！昵称: ")
                                    .append(Component.text(result.getNickname(), NamedTextColor.WHITE)));
                        }
                        default -> {
                            // 未知状态，忽略
                        }
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("轮询二维码状态异常: " + e.getMessage());
            }
        }, 0L, intervalTicks);
        session.setPollTask(task);
    }

    private void saveLogin(String cookie, String nickname, long userId) {
        accountInfo.setCookie(cookie);
        accountInfo.setLoginTime(System.currentTimeMillis());
        accountInfo.setNickname(nickname);
        accountInfo.setUserId(userId);
        save();
    }

    public void cancelActiveSession() {
        if (activeSession != null) {
            activeSession.cancel();
            activeSession = null;
        }
    }

    public void printStatus(Player admin) {
        if (!isLoggedIn()) {
            admin.sendMessage(TextUtil.message(config.getPrefix(), "<gray>当前未登录。"));
            return;
        }
        admin.sendMessage(TextUtil.message(config.getPrefix(), "<green>已登录账号"));
        admin.sendMessage(Component.text("昵称: ", NamedTextColor.GRAY)
                .append(Component.text(accountInfo.getNickname(), NamedTextColor.WHITE)));
        admin.sendMessage(Component.text("用户 ID: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(accountInfo.getUserId()), NamedTextColor.WHITE)));
        admin.sendMessage(Component.text("登录时间: ", NamedTextColor.GRAY)
                .append(Component.text(formatTime(accountInfo.getLoginTime()), NamedTextColor.WHITE)));
    }

    public void refresh(Player admin) {
        if (!isLoggedIn()) {
            admin.sendMessage(TextUtil.message(config.getPrefix(), "<red>当前未登录，无法刷新。"));
            return;
        }
        admin.sendMessage(TextUtil.message(config.getPrefix(), "<yellow>正在刷新登录..."));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean ok = neteaseClient.refreshLogin().join();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ok) {
                        accountInfo.setLoginTime(System.currentTimeMillis());
                        save();
                        admin.sendMessage(TextUtil.message(config.getPrefix(), "<green>刷新成功。"));
                    } else {
                        admin.sendMessage(TextUtil.message(config.getPrefix(), "<red>刷新失败，请重新登录。"));
                    }
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        admin.sendMessage(TextUtil.message(config.getPrefix(), "<red>刷新异常: ")
                                .append(Component.text(e.getMessage(), NamedTextColor.RED))));
            }
        });
    }

    public void logout(CommandSender admin) {
        accountInfo.setCookie("");
        accountInfo.setNickname("");
        accountInfo.setUserId(0);
        accountInfo.setLoginTime(0);
        save();
        if (admin != null) {
            admin.sendMessage(TextUtil.message(config.getPrefix(), "<green>已清除登录状态。"));
        }
    }

    private String formatTime(long millis) {
        if (millis <= 0) {
            return "未知";
        }
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new java.util.Date(millis));
    }
}
