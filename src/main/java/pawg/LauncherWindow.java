package pawg;

import javafx.animation.PauseTransition;
import javafx.application.*;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;
import pawg.nbody.TornadoDeviceChoice;
import pawg.nbody.TornadoDeviceSelector;

import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Minimal process-isolating launcher.  Device detection and JVM startup never block the FX pulse thread. */
public final class LauncherWindow extends Application {
    private static final Duration CHILD_START_GRACE = Duration.millis(450);
    private static final String FALLBACK_STYLESHEET = """
            .btn { -fx-font-size: 14px; -fx-padding: 8 14 8 14; }
            .btn-primary { -fx-background-color: #0d6efd; -fx-text-fill: white; }
            .btn-primary:hover { -fx-background-color: #0b5ed7; }
            .combo-box { -fx-min-width: 330px; }
            """;

    interface ProcessStarter {
        Process start(SimulationTarget target, String selectedDevice) throws Exception;
    }

    private final ProcessStarter processStarter;
    private final List<Button> targetButtons = new ArrayList<>();
    private ComboBox<TornadoDeviceChoice> deviceChooser;
    private Stage stage;

    public LauncherWindow() {
        this(ChildJvmLauncher::start);
    }

    LauncherWindow(ProcessStarter processStarter) {
        this.processStarter = processStarter;
    }

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        VBox root = new VBox(8);
        root.setPadding(new Insets(16));
        deviceChooser = new ComboBox<>();
        deviceChooser.setMaxWidth(Double.MAX_VALUE);
        root.getChildren().add(deviceChooser);
        for (SimulationTarget target : SimulationTarget.values()) {
            Button button = new Button(target.label());
            button.getStyleClass().addAll("btn", "btn-primary");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(_ -> startTarget(target));
            targetButtons.add(button);
            root.getChildren().add(button);
        }

        Scene scene = new Scene(root);
        scene.getStylesheets().add(bootstrapStylesheet());
        installPlatformPreferences(root);
        primaryStage.setTitle("Simulation launcher");
        primaryStage.setScene(scene);
        primaryStage.show();
        detectDevices();
    }

    private void detectDevices() {
        Task<List<TornadoDeviceChoice>> task = new Task<>() {
            @Override
            protected List<TornadoDeviceChoice> call() {
                return TornadoDeviceSelector.deviceChoices();
            }
        };
        task.setOnSucceeded(_ -> {
            List<TornadoDeviceChoice> devices = task.getValue();
            deviceChooser.getItems().setAll(devices);
            if (devices != null && !devices.isEmpty()) {
                deviceChooser.setValue(TornadoDeviceSelector.initialDeviceChoice(devices));
            }
        });
        task.setOnFailed(_ -> showError("Could not detect TornadoVM devices", task.getException()));
        Thread thread = new Thread(task, "tornado-device-detection");
        thread.setDaemon(true);
        thread.start();
    }

    private void startTarget(SimulationTarget target) {
        TornadoDeviceChoice choice = deviceChooser.getValue();
        if (target.gpuTarget() && choice == null) {
            showError("No TornadoVM device is available", null);
            return;
        }
        setButtonsDisabled(true);
        String selectedDevice = target.gpuTarget() ? TornadoDeviceSelector.encodeDeviceChoice(choice) : null;
        Task<Process> task = new Task<>() {
            @Override
            protected Process call() throws Exception {
                return processStarter.start(target, selectedDevice);
            }
        };
        task.setOnSucceeded(_ -> waitForChildStart(task.getValue()));
        task.setOnFailed(_ -> {
            setButtonsDisabled(false);
            showError("Could not start " + target.label(), task.getException());
        });
        Thread thread = new Thread(task, "simulation-child-start");
        thread.setDaemon(true);
        thread.start();
    }

    private void waitForChildStart(Process child) {
        PauseTransition grace = new PauseTransition(CHILD_START_GRACE);
        grace.setOnFinished(_ -> {
            if (child != null && child.isAlive()) {
                stage.close();
            } else {
                setButtonsDisabled(false);
                showError("Simulation exited before it could start", null);
            }
        });
        grace.play();
    }

    private void setButtonsDisabled(boolean disabled) {
        targetButtons.forEach(button -> button.setDisable(disabled));
        deviceChooser.setDisable(disabled);
    }

    private void showError(String message, Throwable cause) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle("Simulation launcher");
        alert.setHeaderText(message);
        alert.setContentText(cause == null ? null : cause.getMessage());
        alert.show();
    }

    private void installPlatformPreferences(VBox root) {
        Platform.Preferences preferences = Platform.getPreferences();
        Runnable apply = () -> applyPlatformPreferences(root, preferences);
        apply.run();
        preferences.backgroundColorProperty().addListener((_, _, _) -> apply.run());
        preferences.foregroundColorProperty().addListener((_, _, _) -> apply.run());
        preferences.colorSchemeProperty().addListener((_, _, _) -> apply.run());
    }

    static void applyPlatformPreferences(VBox root, Platform.Preferences preferences) {
        Color background = preferences.getBackgroundColor();
        Color foreground = preferences.getForegroundColor();
        ColorScheme colorScheme = preferences.getColorScheme();
        root.setStyle("-fx-background-color: " + cssColor(background) + "; -fx-text-fill: " + cssColor(foreground) + ";");
        root.getStyleClass().removeAll("launcher-light", "launcher-dark");
        root.getStyleClass().add(colorScheme == ColorScheme.DARK ? "launcher-dark" : "launcher-light");
    }

    private static String bootstrapStylesheet() {
        try {
            Class<?> bootstrapFx = Class.forName("org.kordamp.bootstrapfx.BootstrapFX");
            Method method = bootstrapFx.getMethod("bootstrapFXStylesheet");
            return (String) method.invoke(null);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return "data:text/css," + URLEncoder.encode(FALLBACK_STYLESHEET, StandardCharsets.UTF_8);
        }
    }

    private static String cssColor(Color color) {
        return color == null ? "transparent" : color.toString().replace("0x", "#");
    }
}
