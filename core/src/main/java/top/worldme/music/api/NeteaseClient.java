package top.worldme.music.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import top.worldme.music.config.PluginConfig;
import top.worldme.music.login.LoginManager;
import top.worldme.music.model.Lyrics;
import top.worldme.music.model.Track;
import top.worldme.music.util.JsonUtil;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 网易云自建 API 客户端。
 *
 * @author Worldme
 * @since 1.0.0
 */
public class NeteaseClient {

    private final HttpClient httpClient;
    private final PluginConfig config;
    private LoginManager loginManager;

    public NeteaseClient(PluginConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getApiTimeoutSeconds()))
                .build();
    }

    public void setLoginManager(LoginManager loginManager) {
        this.loginManager = loginManager;
    }

    /**
     * 搜索单曲。
     */
    public CompletableFuture<List<Track>> search(String keyword, int limit, int offset) {
        String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        String path = "/search?keywords=" + encoded
                + "&limit=" + limit
                + "&offset=" + offset
                + "&type=1";
        return get(path).thenApply(this::parseSearchResult);
    }

    /**
     * 获取歌曲播放 URL。
     */
    public CompletableFuture<String> getSongUrl(long songId) {
        String path = "/song/url/v1?id=" + songId
                + "&level=" + config.getApiSoundQuality();
        return get(path).thenApply(this::parseSongUrl);
    }

    /**
     * 获取歌曲时长（毫秒）。
     */
    public CompletableFuture<Long> getSongDuration(long songId) {
        String path = "/song/detail?ids=" + songId;
        return get(path).thenApply(this::parseSongDuration);
    }

    /**
     * 获取歌词。
     */
    public CompletableFuture<Lyrics> getLyrics(long songId) {
        String path = "/lyric?id=" + songId;
        return get(path).thenApply(this::parseLyrics);
    }

    /**
     * 获取二维码 key。
     */
    public CompletableFuture<String> qrLoginKey() {
        return get("/login/qr/key").thenApply(json -> {
            JsonObject data = JsonUtil.getObject(json, "data");
            return JsonUtil.getString(data, "unikey", "");
        });
    }

    /**
     * 创建二维码，返回 qrurl。
     */
    public CompletableFuture<String> qrLoginCreate(String key) {
        return get("/login/qr/create?key=" + encode(key) + "&qrimg=false").thenApply(json -> {
            JsonObject data = JsonUtil.getObject(json, "data");
            return JsonUtil.getString(data, "qrurl", "");
        });
    }

    /**
     * 检查二维码登录状态。
     */
    public CompletableFuture<QrCheckResult> qrLoginCheck(String key) {
        return get("/login/qr/check?key=" + encode(key)).thenApply(this::parseQrCheck);
    }

    /**
     * 刷新登录。
     */
    public CompletableFuture<Boolean> refreshLogin() {
        return get("/login/refresh").thenApply(json -> {
            int code = JsonUtil.getInt(json, "code", -1);
            return code == 200;
        });
    }

    /**
     * 通用 GET。
     */
    public CompletableFuture<JsonObject> get(String path) {
        String url = buildUrl(path);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(config.getApiTimeoutSeconds()))
                .header("User-Agent", "WorldmeMusic/1.0.0")
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new RuntimeException("HTTP " + response.statusCode());
                    }
                    String body = response.body();
                    if (body == null || body.isBlank()) {
                        return new JsonObject();
                    }
                    return JsonUtil.parseObject(body);
                });
    }

    private String buildUrl(String path) {
        String baseUrl = config.getApiBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        StringBuilder sb = new StringBuilder(baseUrl);
        if (path.startsWith("/")) {
            sb.append(path);
        } else {
            sb.append("/").append(path);
        }
        char sep = path.contains("?") ? '&' : '?';
        sb.append(sep).append("timestamp=").append(System.currentTimeMillis());
        String cookie = loginManager == null ? "" : loginManager.getCookie();
        if (cookie != null && !cookie.isBlank()) {
            sb.append("&cookie=").append(encode(cookie));
        }
        return sb.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private List<Track> parseSearchResult(JsonObject json) {
        List<Track> result = new ArrayList<>();
        JsonObject resultObj = JsonUtil.getObject(json, "result");
        JsonArray songs = JsonUtil.getArray(resultObj, "songs");
        for (JsonElement e : songs) {
            if (!e.isJsonObject()) {
                continue;
            }
            JsonObject song = e.getAsJsonObject();
            Track track = parseTrack(song);
            if (track != null) {
                result.add(track);
            }
        }
        return result;
    }

    private Track parseTrack(JsonObject song) {
        long id = JsonUtil.getLong(song, "id", 0);
        if (id == 0) {
            return null;
        }
        String name = JsonUtil.getString(song, "name", "未知歌曲");
        String artists = parseArtists(song);
        String album = parseAlbum(song);
        long duration = JsonUtil.getLong(song, "dt", 0);
        String coverUrl = parseCoverUrl(song);
        return new Track(id, name, artists, album, duration, coverUrl);
    }

    private String parseArtists(JsonObject song) {
        JsonArray ar = JsonUtil.getArray(song, "ar");
        if (ar.isEmpty()) {
            ar = JsonUtil.getArray(song, "artists");
        }
        StringBuilder sb = new StringBuilder();
        for (JsonElement e : ar) {
            if (!e.isJsonObject()) {
                continue;
            }
            String name = JsonUtil.getString(e.getAsJsonObject(), "name", "");
            if (!name.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append(name);
            }
        }
        return sb.isEmpty() ? "未知歌手" : sb.toString();
    }

    private String parseAlbum(JsonObject song) {
        JsonObject al = JsonUtil.getObject(song, "al");
        if (!al.has("name")) {
            al = JsonUtil.getObject(song, "album");
        }
        return JsonUtil.getString(al, "name", "未知专辑");
    }

    private String parseCoverUrl(JsonObject song) {
        JsonObject al = JsonUtil.getObject(song, "al");
        if (al.has("picUrl")) {
            return JsonUtil.getString(al, "picUrl", "");
        }
        JsonObject album = JsonUtil.getObject(song, "album");
        if (album.has("picUrl")) {
            return JsonUtil.getString(album, "picUrl", "");
        }
        if (album.has("blurPicUrl")) {
            return JsonUtil.getString(album, "blurPicUrl", "");
        }
        return "";
    }

    private String parseSongUrl(JsonObject json) {
        JsonArray data = JsonUtil.getArray(json, "data");
        if (data.isEmpty()) {
            return "";
        }
        JsonObject first = data.get(0).getAsJsonObject();
        return JsonUtil.getString(first, "url", "");
    }

    private Long parseSongDuration(JsonObject json) {
        JsonArray songs = JsonUtil.getArray(json, "songs");
        if (songs.isEmpty()) {
            return 0L;
        }
        JsonObject first = songs.get(0).getAsJsonObject();
        return JsonUtil.getLong(first, "dt", 0L);
    }

    private Lyrics parseLyrics(JsonObject json) {
        JsonObject lrc = JsonUtil.getObject(json, "lrc");
        String lyric = JsonUtil.getString(lrc, "lyric", "");
        return Lyrics.parse(lyric);
    }

    private QrCheckResult parseQrCheck(JsonObject json) {
        int code = JsonUtil.getInt(json, "code", 800);
        String cookie = JsonUtil.getString(json, "cookie", "");
        JsonObject profile = JsonUtil.getObject(json, "profile");

        // 某些 API 会把二维码状态包装在 data 字段中
        JsonObject data = JsonUtil.getObject(json, "data");
        if (data.has("code")) {
            int dataCode = JsonUtil.getInt(data, "code", code);
            if (dataCode == 200) {
                code = 803; // 登录成功
            } else if (dataCode == 800 || dataCode == 801 || dataCode == 802 || dataCode == 803) {
                code = dataCode;
            }
        }
        if (cookie.isBlank()) {
            cookie = JsonUtil.getString(data, "cookie", "");
        }
        JsonObject dataProfile = JsonUtil.getObject(data, "profile");
        if (dataProfile.has("nickname") || dataProfile.has("userId")) {
            profile = dataProfile;
        }

        String nickname = JsonUtil.getString(profile, "nickname", "");
        long userId = JsonUtil.getLong(profile, "userId", 0);
        if (userId == 0) {
            JsonObject account = JsonUtil.getObject(data, "account");
            userId = JsonUtil.getLong(account, "id", 0);
        }
        return new QrCheckResult(code, cookie, nickname, userId);
    }

    /**
     * 二维码检查结果。
     */
    public static class QrCheckResult {
        private final int code;
        private final String cookie;
        private final String nickname;
        private final long userId;

        public QrCheckResult(int code, String cookie, String nickname, long userId) {
            this.code = code;
            this.cookie = cookie;
            this.nickname = nickname;
            this.userId = userId;
        }

        public int getCode() {
            return code;
        }

        public String getCookie() {
            return cookie;
        }

        public String getNickname() {
            return nickname;
        }

        public long getUserId() {
            return userId;
        }
    }
}
