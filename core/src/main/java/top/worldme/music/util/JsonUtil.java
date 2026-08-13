package top.worldme.music.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * JSON 安全取值工具。
 *
 * @author Worldme
 * @since 1.0.0
 */
public final class JsonUtil {

    private JsonUtil() {
    }

    public static JsonObject parseObject(String json) {
        if (json == null || json.isBlank()) {
            return new JsonObject();
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            if (element != null && element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        } catch (Exception ignored) {
        }
        return new JsonObject();
    }

    public static JsonArray parseArray(String json) {
        if (json == null || json.isBlank()) {
            return new JsonArray();
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            if (element != null && element.isJsonArray()) {
                return element.getAsJsonArray();
            }
        } catch (Exception ignored) {
        }
        return new JsonArray();
    }

    public static String getString(JsonObject obj, String key, String def) {
        if (obj == null || !obj.has(key)) {
            return def;
        }
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return def;
        }
        try {
            return element.getAsString();
        } catch (Exception e) {
            return def;
        }
    }

    public static int getInt(JsonObject obj, String key, int def) {
        if (obj == null || !obj.has(key)) {
            return def;
        }
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return def;
        }
        try {
            return element.getAsInt();
        } catch (Exception e) {
            return def;
        }
    }

    public static long getLong(JsonObject obj, String key, long def) {
        if (obj == null || !obj.has(key)) {
            return def;
        }
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return def;
        }
        try {
            return element.getAsLong();
        } catch (Exception e) {
            return def;
        }
    }

    public static boolean getBoolean(JsonObject obj, String key, boolean def) {
        if (obj == null || !obj.has(key)) {
            return def;
        }
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return def;
        }
        try {
            return element.getAsBoolean();
        } catch (Exception e) {
            return def;
        }
    }

    public static JsonObject getObject(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) {
            return new JsonObject();
        }
        JsonElement element = obj.get(key);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        return new JsonObject();
    }

    public static JsonArray getArray(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) {
            return new JsonArray();
        }
        JsonElement element = obj.get(key);
        if (element != null && element.isJsonArray()) {
            return element.getAsJsonArray();
        }
        return new JsonArray();
    }
}
