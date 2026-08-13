package top.worldme.music.util;

import org.bukkit.ChatColor;

/**
 * 文本与颜色工具。
 *
 * @author Worldme
 * @since 1.0.0
 */
public final class TextUtil {

    private TextUtil() {
    }

    /**
     * 将 & 颜色代码替换为 Minecraft 颜色字符。
     */
    public static String color(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * 带前缀的彩色消息。
     */
    public static String message(String prefix, String text) {
        return color(prefix + text);
    }

    /**
     * 简单截断。
     */
    public static String limit(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 3) + "...";
    }
}
