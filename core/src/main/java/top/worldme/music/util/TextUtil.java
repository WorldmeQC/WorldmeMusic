package top.worldme.music.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * 文本与颜色工具。
 *
 * @author Worldme
 * @since 1.0.0
 */
public final class TextUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Map<Character, String> LEGACY_TO_MINI_MESSAGE = new HashMap<>();

    static {
        LEGACY_TO_MINI_MESSAGE.put('0', "<black>");
        LEGACY_TO_MINI_MESSAGE.put('1', "<dark_blue>");
        LEGACY_TO_MINI_MESSAGE.put('2', "<dark_green>");
        LEGACY_TO_MINI_MESSAGE.put('3', "<dark_aqua>");
        LEGACY_TO_MINI_MESSAGE.put('4', "<dark_red>");
        LEGACY_TO_MINI_MESSAGE.put('5', "<dark_purple>");
        LEGACY_TO_MINI_MESSAGE.put('6', "<gold>");
        LEGACY_TO_MINI_MESSAGE.put('7', "<gray>");
        LEGACY_TO_MINI_MESSAGE.put('8', "<dark_gray>");
        LEGACY_TO_MINI_MESSAGE.put('9', "<blue>");
        LEGACY_TO_MINI_MESSAGE.put('a', "<green>");
        LEGACY_TO_MINI_MESSAGE.put('A', "<green>");
        LEGACY_TO_MINI_MESSAGE.put('b', "<aqua>");
        LEGACY_TO_MINI_MESSAGE.put('B', "<aqua>");
        LEGACY_TO_MINI_MESSAGE.put('c', "<red>");
        LEGACY_TO_MINI_MESSAGE.put('C', "<red>");
        LEGACY_TO_MINI_MESSAGE.put('d', "<light_purple>");
        LEGACY_TO_MINI_MESSAGE.put('D', "<light_purple>");
        LEGACY_TO_MINI_MESSAGE.put('e', "<yellow>");
        LEGACY_TO_MINI_MESSAGE.put('E', "<yellow>");
        LEGACY_TO_MINI_MESSAGE.put('f', "<white>");
        LEGACY_TO_MINI_MESSAGE.put('F', "<white>");
        LEGACY_TO_MINI_MESSAGE.put('k', "<obfuscated>");
        LEGACY_TO_MINI_MESSAGE.put('K', "<obfuscated>");
        LEGACY_TO_MINI_MESSAGE.put('l', "<bold>");
        LEGACY_TO_MINI_MESSAGE.put('L', "<bold>");
        LEGACY_TO_MINI_MESSAGE.put('m', "<strikethrough>");
        LEGACY_TO_MINI_MESSAGE.put('M', "<strikethrough>");
        LEGACY_TO_MINI_MESSAGE.put('n', "<underlined>");
        LEGACY_TO_MINI_MESSAGE.put('N', "<underlined>");
        LEGACY_TO_MINI_MESSAGE.put('o', "<italic>");
        LEGACY_TO_MINI_MESSAGE.put('O', "<italic>");
        LEGACY_TO_MINI_MESSAGE.put('r', "<reset>");
        LEGACY_TO_MINI_MESSAGE.put('R', "<reset>");
    }

    private TextUtil() {
    }

    /**
     * 将字符串解析为 Adventure Component，支持 MiniMessage 标签与 legacy & 颜色代码。
     */
    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(legacyToMiniMessage(text));
    }

    /**
     * 带前缀的彩色消息。
     */
    public static Component message(String prefix, String text) {
        return parse(prefix).append(parse(text));
    }

    /**
     * 创建纯文本 Component（不会解析 MiniMessage 标签）。
     */
    public static Component text(String text) {
        return Component.text(text == null ? "" : text);
    }

    /**
     * 创建纯文本 Component（不会解析 MiniMessage 标签）。
     */
    public static Component text(Object text) {
        return Component.text(text == null ? "" : text.toString());
    }

    /**
     * 空 Component。
     */
    public static Component empty() {
        return Component.empty();
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

    /**
     * 将 legacy & 颜色代码替换为对应的 MiniMessage 标签，保留已有的 MiniMessage 标签不变。
     */
    private static String legacyToMiniMessage(String text) {
        StringBuilder result = new StringBuilder(text.length());
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '&' && i + 1 < chars.length) {
                String tag = LEGACY_TO_MINI_MESSAGE.get(chars[i + 1]);
                if (tag != null) {
                    result.append(tag);
                    i++;
                    continue;
                }
            }
            result.append(chars[i]);
        }
        return result.toString();
    }
}
