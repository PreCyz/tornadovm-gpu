package pawg;

import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pawg.nbody.TornadoDeviceChoice;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherWindowTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(10, TimeUnit.SECONDS), "JavaFX toolkit did not start");
        Platform.setImplicitExit(false);
    }

    @Test
    void launcherBuildsAChooserAndOneStyledButtonForEachRegisteredSimulation() throws Exception {
        LauncherWindow launcher = new LauncherWindow((_, _) -> new StubProcess(true));
        Stage stage = onFxThread(() -> {
            Stage value = new Stage();
            launcher.start(value);
            return value;
        });
        try {
            VBox root = onFxThread(() -> (VBox) stage.getScene().getRoot());
            List<Button> buttons = onFxThread(() -> root.getChildren().stream()
                    .filter(Button.class::isInstance).map(Button.class::cast).toList());
            ComboBox<?> chooser = onFxThread(() -> (ComboBox<?>) root.getChildren().getFirst());

            assertNotNull(chooser);
            assertEquals(9, buttons.size());
            assertEquals(List.of(SimulationTarget.values()).stream().map(SimulationTarget::label).toList(),
                    buttons.stream().map(Button::getText).toList());
            assertTrue(buttons.stream().allMatch(button -> button.getStyleClass().containsAll(List.of("btn", "btn-primary"))));
            assertEquals(16.0, root.getPadding().getTop());
        } finally {
            onFxThread(() -> {
                stage.close();
                return null;
            });
        }
    }

    @Test
    void injectedStarterReceivesSelectedGpuCoordinatesAndRapidClicksStartOnlyOneChild() throws Exception {
        AtomicReference<String> selectedDevice = new AtomicReference<>();
        AtomicInteger starts = new AtomicInteger();
        LauncherWindow launcher = new LauncherWindow((target, device) -> {
            assertEquals(SimulationTarget.HEAT_DISTRIBUTION, target);
            selectedDevice.set(device);
            starts.incrementAndGet();
            return new StubProcess(true);
        });
        Stage stage = start(launcher);
        try {
            onFxThread(() -> {
                chooser(launcher).setValue(new TornadoDeviceChoice(2, 5, "test device", "", "", true));
                buttons(launcher).getFirst().fire();
                buttons(launcher).getFirst().fire();
                return null;
            });
            await(() -> starts.get() == 1, "process starter was not called");
            assertEquals("2:5", selectedDevice.get());
            assertEquals(1, starts.get());
            assertTrue(onFxThread(() -> buttons(launcher).stream().allMatch(Button::isDisable)));
        } finally {
            onFxThread(() -> {
                stage.close();
                return null;
            });
        }
    }

    @Test
    void immediatelyDeadChildReenablesLauncherControls() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        LauncherWindow launcher = new LauncherWindow((_, _) -> {
            starts.incrementAndGet();
            return new StubProcess(false);
        });
        Stage stage = start(launcher);
        try {
            onFxThread(() -> {
                chooser(launcher).setValue(new TornadoDeviceChoice(0, 0, "default", "", "", true));
                buttons(launcher).getFirst().fire();
                return null;
            });
            await(() -> starts.get() == 1, "process starter was not called");
            await(() -> onFxThread(() -> !chooser(launcher).isDisable()), "dead child did not re-enable the chooser");
            assertFalse(onFxThread(() -> buttons(launcher).stream().anyMatch(Button::isDisable)));
        } finally {
            onFxThread(() -> {
                stage.close();
                return null;
            });
        }
    }

    @Test
    void platformThemeHelperAppliesCurrentColorSchemeAndColors() throws Exception {
        onFxThread(() -> {
            VBox root = new VBox();
            LauncherWindow.applyPlatformPreferences(root, Platform.getPreferences());
            assertTrue(root.getStyle().contains("-fx-background-color:"));
            assertTrue(root.getStyle().contains("-fx-text-fill:"));
            String expectedStyleClass = Platform.getPreferences().getColorScheme() == ColorScheme.DARK
                    ? "launcher-dark" : "launcher-light";
            assertTrue(root.getStyleClass().contains(expectedStyleClass));
            return null;
        });
    }

    private static Stage start(LauncherWindow launcher) throws Exception {
        return onFxThread(() -> {
            Stage stage = new Stage();
            launcher.start(stage);
            return stage;
        });
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<TornadoDeviceChoice> chooser(LauncherWindow launcher) throws Exception {
        Field field = LauncherWindow.class.getDeclaredField("deviceChooser");
        field.setAccessible(true);
        return (ComboBox<TornadoDeviceChoice>) field.get(launcher);
    }

    @SuppressWarnings("unchecked")
    private static List<Button> buttons(LauncherWindow launcher) throws Exception {
        Field field = LauncherWindow.class.getDeclaredField("targetButtons");
        field.setAccessible(true);
        return (List<Button>) field.get(launcher);
    }

    private static void await(CheckedCondition condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (condition.value()) {
                return;
            }
            Thread.sleep(20);
        }
        assertTrue(condition.value(), message);
    }

    private static <T> T onFxThread(ThrowingSupplier<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return action.get();
        }
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(action.get());
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS), "JavaFX action did not complete");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get();
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> { T get() throws Exception; }
    @FunctionalInterface
    private interface CheckedCondition { boolean value() throws Exception; }

    private static final class StubProcess extends Process {
        private final boolean alive;
        private StubProcess(boolean alive) { this.alive = alive; }
        @Override public ByteArrayOutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public ByteArrayInputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public ByteArrayInputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() { return 0; }
        @Override public int exitValue() { return alive ? 1 : 0; }
        @Override public void destroy() { }
        @Override public boolean isAlive() { return alive; }
    }
}
