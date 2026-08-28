package pawg.body;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

final class BodyStageIcons {

    private static final int ICON_SIZE = 64;
    private static final Image BODY_ICON = createBodyIcon();

    private BodyStageIcons() {
    }

    static void addBodyIcon(Stage stage) {
        stage.getIcons().add(BODY_ICON);
    }

    private static Image createBodyIcon() {
        WritableImage image = new WritableImage(ICON_SIZE, ICON_SIZE);
        var writer = image.getPixelWriter();
        double center = (ICON_SIZE - 1) * 0.5;
        double bodyRadius = ICON_SIZE * 0.21;
        double horizonRadius = ICON_SIZE * 0.43;

        for (int y = 0; y < ICON_SIZE; y++) {
            for (int x = 0; x < ICON_SIZE; x++) {
                double dx = x - center;
                double dy = y - center;
                double distance = Math.hypot(dx, dy);
                Color pixel = horizonPixel(dx, dy, distance, horizonRadius);
                if (distance <= bodyRadius + 0.5) {
                    pixel = bodyPixel(dx, dy, distance, bodyRadius);
                }
                writer.setColor(x, y, pixel);
            }
        }
        return image;
    }

    private static Color horizonPixel(double dx, double dy, double distance, double radius) {
        double coverage = Math.clamp(1.0 - Math.abs(distance - radius) / 1.25, 0.0, 1.0);
        if (coverage <= 0.0) {
            return Color.TRANSPARENT;
        }
        double angle = Math.atan2(dy, dx) + Math.PI;
        double dashPhase = angle / (Math.PI * 2.0) * 14.0;
        if (dashPhase - Math.floor(dashPhase) > 0.62) {
            return Color.TRANSPARENT;
        }
        return Color.rgb(255, 190, 198, coverage * 0.92);
    }

    private static Color bodyPixel(double dx, double dy, double distance, double radius) {
        double nx = dx / radius;
        double ny = dy / radius;
        double nz = Math.sqrt(Math.max(0.0, 1.0 - nx * nx - ny * ny));
        double light = Math.clamp(-0.42 * nx - 0.50 * ny + 0.76 * nz, 0.0, 1.0);
        double highlight = Math.max(0.0, 1.0 - Math.hypot(dx + radius * 0.34, dy + radius * 0.38)
                / (radius * 0.52));
        double brightness = 0.32 + light * 0.68;
        int red = channel(235.0 * brightness + 20.0 * highlight);
        int green = channel(38.0 * brightness + 175.0 * highlight);
        int blue = channel(48.0 * brightness + 170.0 * highlight);
        double opacity = Math.clamp(radius + 0.5 - distance, 0.0, 1.0);
        return Color.rgb(red, green, blue, opacity);
    }

    private static int channel(double value) {
        return (int) Math.round(Math.clamp(value, 0.0, 255.0));
    }
}
