package pawg.body;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.controlsfx.control.PopOver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodySimulatorDrawerFeatureTest {

    private static final double SIDEBAR_WIDTH = 520.0;
    private static final double ENDPOINT_TOLERANCE = 3.0;
    private static final long ANIMATION_MILLIS = 240L;

    @BeforeAll
    static void startJavaFx() throws Exception {
        if (!Platform.isFxApplicationThread()) {
            CountDownLatch started = new CountDownLatch(1);
            try {
                Platform.startup(started::countDown);
            } catch (IllegalStateException alreadyStarted) {
                started.countDown();
            }
            assertTrue(started.await(10, TimeUnit.SECONDS), "JavaFX toolkit did not start");
        }
        Platform.setImplicitExit(false);
    }

    @Test
    void matchingEdgeButtonsHideAndRestoreDashboardFromTheSameLocation() throws Exception {
        DrawerContext context = createContext();
        AtomicReference<Bounds> hideButtonBounds = new AtomicReference<>();
        try {
            onFxThread(() -> {
                assertVisibleEndpoint(context);
                hideButtonBounds.set(context.drawer().hideButton().localToScene(
                        context.drawer().hideButton().getBoundsInLocal()));
                // Populate a hover PopOver through the trigger's actual event handler.
                Node guide = findText(context.stage().getScene().getRoot(), "Guide");
                Event.fireEvent(guide, new javafx.scene.input.MouseEvent(
                        javafx.scene.input.MouseEvent.MOUSE_ENTERED, 0, 0, 0, 0,
                        javafx.scene.input.MouseButton.NONE, 0, false, false, false, false,
                        false, false, false, false, false, false, null));
                assertTrue(((PopOver) field(context.simulator(), "guidePopover")).isShowing(),
                        "test precondition: the dashboard help popup is open");
                context.drawer().hideButton().fire();
                return null;
            });
            waitForAnimation();

            onFxThread(() -> {
                assertHiddenEndpoint(context);
                assertFalse(((PopOver) field(context.simulator(), "guidePopover")).isShowing(),
                        "hiding the dashboard must close its hover help");
                Bounds restoreButtonBounds = context.drawer().restoreButton().localToScene(
                        context.drawer().restoreButton().getBoundsInLocal());
                assertEquals(hideButtonBounds.get().getMinX(), restoreButtonBounds.getMinX(), ENDPOINT_TOLERANCE);
                assertEquals(hideButtonBounds.get().getMinY(), restoreButtonBounds.getMinY(), ENDPOINT_TOLERANCE);
                assertEquals(hideButtonBounds.get().getWidth(), restoreButtonBounds.getWidth(), ENDPOINT_TOLERANCE);
                assertEquals(hideButtonBounds.get().getHeight(), restoreButtonBounds.getHeight(), ENDPOINT_TOLERANCE);
                Button restore = context.drawer().restoreButton();
                restore.fire();
                return null;
            });
            waitForAnimation();
            onFxThread(() -> {
                assertVisibleEndpoint(context);
                return null;
            });
        } finally {
            close(context);
        }
    }

    @Test
    void canvasTracksStageResizesAtBothDrawerEndpointsAndInterruptedTransitionsSettleCleanly() throws Exception {
        DrawerContext context = createContext();
        try {
            onFxThread(() -> {
                context.drawer().hideButton().fire();
                return null;
            });
            waitForAnimation();
            onFxThread(() -> {
                context.stage().setWidth(1180.0);
                context.stage().setHeight(720.0);
                return null;
            });
            awaitPulses(3);
            onFxThread(() -> {
                assertHiddenEndpoint(context);
                assertEquals(context.stage().getScene().getRoot().getLayoutBounds().getWidth(), context.canvas().getWidth(), ENDPOINT_TOLERANCE,
                        "hidden drawer must give the canvas the full root width after resizing");
                assertEquals(context.stage().getScene().getRoot().getLayoutBounds().getHeight(), context.canvas().getHeight(), ENDPOINT_TOLERANCE);
                context.drawer().restoreButton().fire();
                return null;
            });
            waitForAnimation();
            onFxThread(() -> {
                context.stage().setWidth(1060.0);
                context.stage().setHeight(680.0);
                return null;
            });
            awaitPulses(3);
            onFxThread(() -> {
                assertVisibleEndpoint(context);
                double rootWidth = context.stage().getScene().getRoot().getLayoutBounds().getWidth();
                assertEquals(rootWidth - SIDEBAR_WIDTH, context.canvas().getWidth(), ENDPOINT_TOLERANCE,
                        "visible drawer must reserve its full fixed width after resizing");

                context.drawer().hideButton().fire();
                return null;
            });
            waitMillis(70L);
            onFxThread(() -> {
                assertEdgeButtonInactive(context.drawer().hideButton());
                assertEdgeButtonInactive(context.drawer().restoreButton());
                invoke(context.simulator(), "setDashboardDrawerHidden", new Class<?>[]{boolean.class}, false);
                return null;
            });
            waitForAnimation();
            onFxThread(() -> {
                assertVisibleEndpoint(context);
                assertFalse(context.drawer().restoreButton().isVisible(),
                        "a reversed hide must not leave a stale restore button");
                return null;
            });
        } finally {
            close(context);
        }
    }

    private static DrawerContext createContext() throws Exception {
        return onFxThread(() -> {
            BodySimulator simulator = new BodySimulator();
            Stage stage = new Stage();
            Canvas canvas = new Canvas();
            BodySimulator.SidebarHoverTriggers hoverTriggers = simulator.createSidebarHoverTriggers();
            Button hideButton = simulator.createDashboardHideButton();

            VBox dashboard = (VBox) field(simulator, "dashboard");
            VBox side = new VBox(10.0, hoverTriggers.guide(), hoverTriggers.unitDescription(), dashboard);
            side.setPadding(new Insets(14.0));
            side.setPrefWidth(SIDEBAR_WIDTH);
            side.setMinWidth(SIDEBAR_WIDTH);
            side.setMaxWidth(SIDEBAR_WIDTH);
            BodySimulator.DrawerLayout drawer = simulator.createDrawerLayout(canvas, side, hideButton);

            stage.setScene(new Scene(drawer.root(), 1060.0, 680.0));
            stage.show();
            drawer.root().applyCss();
            drawer.root().layout();
            return new DrawerContext(simulator, stage, canvas, drawer);
        });
    }

    private static void assertVisibleEndpoint(DrawerContext context) {
        VBox dashboard = context.drawer().sidebar();
        Button hide = context.drawer().hideButton();
        Button restore = context.drawer().restoreButton();
        double rootWidth = context.stage().getScene().getRoot().getLayoutBounds().getWidth();
        assertEquals(0.0, dashboard.getTranslateX(), ENDPOINT_TOLERANCE);
        assertEquals(SIDEBAR_WIDTH, dashboard.getWidth(), ENDPOINT_TOLERANCE);
        assertEquals(rootWidth - SIDEBAR_WIDTH, context.canvas().getWidth(), ENDPOINT_TOLERANCE);
        assertEdgeButtonActive(hide, "▶", "Hide dashboard");
        assertEdgeButtonInactive(restore);
        assertMatchingEdgeButtonPresentation(hide, restore);
    }

    private static void assertHiddenEndpoint(DrawerContext context) {
        VBox dashboard = context.drawer().sidebar();
        Button hide = context.drawer().hideButton();
        Button restore = context.drawer().restoreButton();
        double rootWidth = context.stage().getScene().getRoot().getLayoutBounds().getWidth();
        assertEquals(SIDEBAR_WIDTH, dashboard.getTranslateX(), ENDPOINT_TOLERANCE,
                "hidden dashboard must be fully translated past the right edge");
        assertEquals(rootWidth, context.canvas().getWidth(), ENDPOINT_TOLERANCE,
                "hidden dashboard must give its width to the simulation canvas");
        assertEdgeButtonInactive(hide);
        assertEdgeButtonActive(restore, "◀", "Show dashboard");
        assertMatchingEdgeButtonPresentation(hide, restore);
    }

    private static void assertEdgeButtonActive(Button button, String arrow, String description) {
        assertEquals(arrow, button.getText());
        assertNotNull(button.getTooltip());
        assertEquals(description, button.getTooltip().getText());
        assertEquals(description, button.getAccessibleText());
        assertTrue(button.isVisible() && button.isManaged() && !button.isDisabled() && !button.isMouseTransparent(),
                description + " control must be available at its drawer endpoint");
        assertTrue(button.isFocusTraversable(), description + " control must be keyboard reachable");
    }

    private static void assertEdgeButtonInactive(Button button) {
        assertFalse(button.isVisible());
        assertFalse(button.isManaged());
        assertTrue(button.isDisabled());
        assertTrue(button.isMouseTransparent());
    }

    private static void assertMatchingEdgeButtonPresentation(Button hide, Button restore) {
        assertEquals(hide.getStyle(), restore.getStyle());
        assertEquals(hide.getMinWidth(), restore.getMinWidth(), 0.0);
        assertEquals(hide.getMinHeight(), restore.getMinHeight(), 0.0);
        assertEquals(StackPane.getAlignment(hide), StackPane.getAlignment(restore));
        assertEquals(StackPane.getMargin(hide), StackPane.getMargin(restore));
    }

    private static Node findText(Node root, String text) {
        return descendants(root).stream()
                .filter(node -> node instanceof javafx.scene.control.Labeled labeled && text.equals(labeled.getText()))
                .findFirst().orElseThrow(() -> new AssertionError("node not found: " + text));
    }

    private static List<Node> descendants(Node root) {
        List<Node> nodes = new ArrayList<>();
        nodes.add(root);
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                nodes.addAll(descendants(child));
            }
        }
        return nodes;
    }

    private static void waitForAnimation() throws Exception {
        waitMillis(ANIMATION_MILLIS + 160L);
    }

    private static void waitMillis(long millis) throws Exception {
        Thread.sleep(millis);
        awaitPulses(3);
    }

    private static void awaitPulses(int count) throws Exception {
        for (int pulse = 0; pulse < count; pulse++) {
            onFxThread(() -> null);
        }
    }

    private static void close(DrawerContext context) throws Exception {
        onFxThread(() -> {
            context.simulator().stop();
            context.stage().close();
            return null;
        });
    }

    private static Object field(BodySimulator simulator, String name) throws Exception {
        Field field = BodySimulator.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(simulator);
    }

    private static void invoke(BodySimulator simulator, String name, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        Method method = BodySimulator.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        method.invoke(simulator, arguments);
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
        assertTrue(completed.await(20, TimeUnit.SECONDS), "JavaFX operation timed out");
        if (failure.get() != null) {
            throw new AssertionError("JavaFX operation failed", failure.get());
        }
        return result.get();
    }

    private record DrawerContext(BodySimulator simulator, Stage stage, Canvas canvas,
                                 BodySimulator.DrawerLayout drawer) {
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
