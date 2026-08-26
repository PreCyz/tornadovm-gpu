package pawg.nbody;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

final class NBodyStageIcons {

    private static final int ICON_SIZE = 64;
    private static final Image JUPITER_ICON = createJupiterIcon();

    private NBodyStageIcons() {
    }

    static void addJupiterIcon(Stage stage) {
        stage.getIcons().add(JUPITER_ICON);
    }

    private static Image createJupiterIcon() {
        WritableImage image = new WritableImage(ICON_SIZE, ICON_SIZE);
        var writer = image.getPixelWriter();
        double center = (ICON_SIZE - 1) / 2.0;
        double radius = ICON_SIZE * 0.42;

        for (int y = 0; y < ICON_SIZE; y++) {
            for (int x = 0; x < ICON_SIZE; x++) {
                double dx = x - center;
                double dy = y - center;
                double distance = Math.sqrt(dx * dx + dy * dy);
                if (distance > radius) {
                    writer.setColor(x, y, Color.TRANSPARENT);
                    continue;
                }

                double normalizedY = (dy / radius + 1.0) * 0.5;
                Color bandColor = jupiterBandColor(normalizedY);
                double shade = 0.72 + 0.28 * Math.max(0.0, 1.0 - distance / radius);
                double highlight = Math.max(0.0, 1.0 - Math.hypot(dx + radius * 0.28, dy + radius * 0.24) / (radius * 0.55));
                writer.setColor(x, y, bandColor.interpolate(Color.WHITE, highlight * 0.20).deriveColor(0, 1, shade, 1.0));
            }
        }

        drawGreatRedSpot(image);
        return image;
    }

    private static Color jupiterBandColor(double normalizedY) {
        double stripe = Math.sin(normalizedY * Math.PI * 13.0);
        if (stripe > 0.55) {
            return Color.rgb(179, 112, 64);
        }
        if (stripe < -0.45) {
            return Color.rgb(238, 205, 154);
        }
        return Color.rgb(211, 169, 112);
    }

    private static void drawGreatRedSpot(WritableImage image) {
        var writer = image.getPixelWriter();
        var reader = image.getPixelReader();
        double spotCenterX = ICON_SIZE * 0.62;
        double spotCenterY = ICON_SIZE * 0.58;
        double spotRadiusX = ICON_SIZE * 0.14;
        double spotRadiusY = ICON_SIZE * 0.08;

        for (int y = 0; y < ICON_SIZE; y++) {
            for (int x = 0; x < ICON_SIZE; x++) {
                double normalizedDistance = Math.pow((x - spotCenterX) / spotRadiusX, 2.0)
                        + Math.pow((y - spotCenterY) / spotRadiusY, 2.0);
                if (normalizedDistance <= 1.0) {
                    Color current = reader.getColor(x, y);
                    if (current.getOpacity() > 0.0) {
                        double edge = Math.max(0.0, Math.min(1.0, normalizedDistance));
                        Color spot = Color.rgb(178, 76, 45).interpolate(Color.rgb(238, 154, 108), edge * 0.35);
                        writer.setColor(x, y, current.interpolate(spot, 0.82));
                    }
                }
            }
        }
    }
}
