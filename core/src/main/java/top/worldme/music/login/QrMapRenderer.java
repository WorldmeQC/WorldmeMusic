package top.worldme.music.login;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * 将二维码图片渲染到地图上的渲染器。
 *
 * @author Worldme
 * @since 1.0.0
 */
public class QrMapRenderer extends MapRenderer {

    private final BufferedImage image;
    private boolean rendered;

    public QrMapRenderer(BufferedImage image) {
        super(false);
        this.image = image;
    }

    @Override
    public void render(MapView mapView, MapCanvas mapCanvas, Player player) {
        if (rendered || image == null) {
            return;
        }
        int width = Math.min(128, image.getWidth());
        int height = Math.min(128, image.getHeight());
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                mapCanvas.setPixelColor(x, y, new Color(image.getRGB(x, y)));
            }
        }
        rendered = true;
    }
}
