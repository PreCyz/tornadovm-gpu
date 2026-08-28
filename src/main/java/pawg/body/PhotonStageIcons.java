package pawg.body;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

final class PhotonStageIcons {

    private static final int ICON_SIZE = 64;
    private static final Image PHOTON_ICON = createPhotonIcon();

    private PhotonStageIcons() {
    }

    static void addPhotonIcon(Stage stage) {
        stage.getIcons().add(PHOTON_ICON);
    }

    private static Image createPhotonIcon() {
        WritableImage image = new WritableImage(ICON_SIZE, ICON_SIZE);
        var writer = image.getPixelWriter();

        for (int y = 0; y < ICON_SIZE; y++) {
            for (int x = 0; x < ICON_SIZE; x++) {
                double progress = (x + y - 10.0) / 44.0;
                double lineY = 52.0 - progress * 38.0;
                double distance = Math.abs(y - lineY);
                double glow = Math.max(0.0, 1.0 - distance / 8.0);
                if (progress >= 0.0 && progress <= 1.0 && glow > 0.0) {
                    Color color = Color.rgb(255, 245, 120, glow * 0.28)
                            .interpolate(Color.rgb(255, 255, 220), Math.min(1.0, glow));
                    writer.setColor(x, y, color);
                } else {
                    writer.setColor(x, y, Color.TRANSPARENT);
                }
            }
        }

        for (int y = 0; y < ICON_SIZE; y++) {
            for (int x = 0; x < ICON_SIZE; x++) {
                double distance = Math.hypot(x - 51.0, y - 18.0);
                if (distance <= 6.0) {
                    double intensity = 1.0 - distance / 6.0;
                    writer.setColor(x, y, Color.rgb(255, 255, 190, intensity));
                }
            }
        }
        return image;
    }
}
