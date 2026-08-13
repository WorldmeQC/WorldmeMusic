package top.worldme.music.login;

import com.sun.net.httpserver.HttpServer;
import top.worldme.music.config.PluginConfig;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 临时二维码图片 HTTP 服务。
 *
 * @author Worldme
 * @since 1.0.0
 */
public class QrImageServer {

    private final PluginConfig config;
    private HttpServer server;
    private final Map<String, byte[]> images = new ConcurrentHashMap<>();

    public QrImageServer(PluginConfig config) {
        this.config = config;
    }

    public void start() throws Exception {
        if (server != null) {
            return;
        }
        String host = config.getQrServerHost();
        int port = config.getQrServerPort();
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/qr", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();
                // path 形如 /qr/a1b2c3d4.png
                int slash = path.lastIndexOf('/');
                String token = path.substring(slash + 1).replace(".png", "");
                byte[] image = images.get(token);
                if (image == null || image.length == 0) {
                    byte[] msg = "Not found".getBytes();
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                    exchange.sendResponseHeaders(404, msg.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(msg);
                    }
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "image/png");
                exchange.sendResponseHeaders(200, image.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(image);
                }
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, -1);
            }
        });
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        images.clear();
    }

    public String register(byte[] png) {
        String token = UUID.randomUUID().toString().replace("-", "");
        images.put(token, png);
        return token;
    }

    public void unregister(String token) {
        if (token != null) {
            images.remove(token);
        }
    }
}
