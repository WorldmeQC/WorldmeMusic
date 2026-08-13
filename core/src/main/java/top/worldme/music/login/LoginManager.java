package top.worldme.music.login;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import top.worldme.music.WorldmeMusicPlugin;
import top.worldme.music.api.NeteaseClient;
import top.worldme.music.config.PluginConfig;
import top.worldme.music.model.AccountInfo;
import top.worldme.music.util.TextUtil;
import top.worldmeqc.qrcode.QRCode;
import top.worldmeqc.qrcode.enums.ErrorCorrectionLevel;
import top.worldmeqc.qrcode.utils.ImageUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

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
    private final QrImageServer qrImageServer;
    private final File loginFile;
    private final AccountInfo accountInfo;

    private QRLoginSession activeSession;

    public LoginManager(WorldmeMusicPlugin plugin, PluginConfig config,
                        NeteaseClient neteaseClient, QrImageServer qrImageServer) {
        this.plugin = plugin;
        this.config = config;
        this.neteaseClient = neteaseClient;
        this.qrImageServer = qrImageServer;
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
            admin.sendMessage(TextUtil.message(config.getPrefix(), "&c登录模块未启用。"));
            return;
        }
        // 取消旧会话
        if (activeSession != null) {
            activeSession.cancel();
            qrImageServer.unregister(activeSession.getToken());
            activeSession = null;
        }

        admin.sendMessage(TextUtil.message(config.getPrefix(), "&e正在生成二维码，请稍候..."));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String key = neteaseClient.qrLoginKey().join();
                if (key == null || key.isBlank()) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            admin.sendMessage(TextUtil.message(config.getPrefix(), "&c获取二维码 key 失败。")));
                    return;
                }
                String qrUrl = neteaseClient.qrLoginCreate(key).join();
                if (qrUrl == null || qrUrl.isBlank()) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            admin.sendMessage(TextUtil.message(config.getPrefix(), "&c生成二维码失败。")));
                    return;
                }

                BufferedImage image = generateQrImage(qrUrl);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                byte[] png = baos.toByteArray();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    String token = qrImageServer.register(png);
                    QRLoginSession session = new QRLoginSession(key, qrUrl, token);
                    activeSession = session;
                    String publicUrl = getPublicUrl(token);
                    admin.sendMessage(TextUtil.message(config.getPrefix(),
                            "&a请点击下方链接，使用网易云 App 扫码登录："));
                    admin.sendMessage("&b" + publicUrl);
                    admin.sendMessage(TextUtil.message(config.getPrefix(),
                            "&7二维码有效期 &e" + (config.getQrTimeout() / 60) + " &7分钟。"));
                    startPolling(session, admin);
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        admin.sendMessage(TextUtil.message(config.getPrefix(), "&c生成二维码异常: " + e.getMessage())));
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

    private void startPolling(QRLoginSession session, Player admin) {
        long intervalTicks = Math.max(20L, config.getPollInterval() * 20L);
        BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (session.isCompleted()) {
                session.cancel();
                return;
            }
            if (System.currentTimeMillis() - session.getCreatedAt() > config.getQrTimeout() * 1000L) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    admin.sendMessage(TextUtil.message(config.getPrefix(), "&c二维码已过期，请重新执行 /music login qr。"));
                });
                session.cancel();
                qrImageServer.unregister(session.getToken());
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
                            admin.sendMessage(TextUtil.message(config.getPrefix(), "&e等待确认..."));
                        }
                        case 800 -> {
                            session.cancel();
                            qrImageServer.unregister(session.getToken());
                            admin.sendMessage(TextUtil.message(config.getPrefix(), "&c二维码已过期，请重新生成。"));
                        }
                        case 803 -> {
                            session.cancel();
                            qrImageServer.unregister(session.getToken());
                            saveLogin(result.getCookie(), result.getNickname(), result.getUserId());
                            admin.sendMessage(TextUtil.message(config.getPrefix(), "&a登录成功！昵称: " + result.getNickname()));
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
            qrImageServer.unregister(activeSession.getToken());
            activeSession = null;
        }
    }

    public void printStatus(Player admin) {
        if (!isLoggedIn()) {
            admin.sendMessage(TextUtil.message(config.getPrefix(), "&7当前未登录。"));
            return;
        }
        admin.sendMessage(TextUtil.message(config.getPrefix(), "&a已登录账号"));
        admin.sendMessage("&7昵称: &f" + accountInfo.getNickname());
        admin.sendMessage("&7用户 ID: &f" + accountInfo.getUserId());
        admin.sendMessage("&7登录时间: &f" + formatTime(accountInfo.getLoginTime()));
    }

    public void refresh(Player admin) {
        if (!isLoggedIn()) {
            admin.sendMessage(TextUtil.message(config.getPrefix(), "&c当前未登录，无法刷新。"));
            return;
        }
        admin.sendMessage(TextUtil.message(config.getPrefix(), "&e正在刷新登录..."));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean ok = neteaseClient.refreshLogin().join();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ok) {
                        accountInfo.setLoginTime(System.currentTimeMillis());
                        save();
                        admin.sendMessage(TextUtil.message(config.getPrefix(), "&a刷新成功。"));
                    } else {
                        admin.sendMessage(TextUtil.message(config.getPrefix(), "&c刷新失败，请重新登录。"));
                    }
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        admin.sendMessage(TextUtil.message(config.getPrefix(), "&c刷新异常: " + e.getMessage())));
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
            admin.sendMessage(TextUtil.message(config.getPrefix(), "&a已清除登录状态。"));
        }
    }

    private String getPublicUrl(String token) {
        String host = config.getQrPublicHost();
        if (host == null || host.isBlank()) {
            host = getLocalIp();
        }
        return "http://" + host + ":" + config.getQrServerPort() + "/qr/" + token + ".png";
    }

    private String getLocalIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && !addr.isLinkLocalAddress() && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            InetAddress fallback = InetAddress.getLocalHost();
            return fallback.getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
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
