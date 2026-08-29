package pawg.body;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.event.Event;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.controlsfx.control.PopOver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BodySimulatorInteractionFeatureTest {

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
    void hoverLabelsUseSeparateWrappedPopoversWhichShowOnEnterHideOnExitAndCloseOnStop() throws Exception {
        onFxThread(() -> { withSimulator((simulator, stage) -> {
            Label guide = labelInScene(simulator, "Guide");
            Label unitDescription = labelInScene(simulator, "Unit description");
            invoke(simulator, "updateDashboard");
            Label calibration = (Label) field(simulator, "unitCalibrationHeader");

            PopOver guidePopover = (PopOver) field(simulator, "guidePopover");
            PopOver unitDescriptionPopover = (PopOver) field(simulator, "unitDescriptionPopover");
            PopOver calibrationPopover = (PopOver) field(simulator, "unitCalibrationPopover");
            assertNotNull(guidePopover);
            assertNotNull(unitDescriptionPopover);
            assertNotNull(calibrationPopover);
            assertTrue(guidePopover != unitDescriptionPopover && guidePopover != calibrationPopover
                    && unitDescriptionPopover != calibrationPopover, "each help trigger needs its own PopOver");

            assertPopover(guide, guidePopover, "Right-click a body");
            assertPopover(unitDescription, unitDescriptionPopover, "Photon bending");
            assertPopover(calibration, calibrationPopover, "Assuming 1 simulation second");

            Label guideContent = (Label) guidePopover.getContentNode();
            assertTrue(guideContent.isWrapText());
            assertTrue(guideContent.getMaxWidth() > 0.0);
            guideContent.applyCss();
            assertTrue(((Color) guideContent.getTextFill()).getBrightness() > 0.85,
                    "hover help must retain a high-contrast readable foreground");

            List<String> permanentText = labelsIn(((Canvas) field(simulator, "canvas")).getScene().getRoot()).stream()
                    .map(Label::getText).toList();
            assertTrue(permanentText.contains("Guide"));
            assertTrue(permanentText.contains("Unit description"));
            assertTrue(((VBox) field(simulator, "dashboard")).getChildren().contains(calibration));
            assertTrue(permanentText.stream().noneMatch(text -> text.startsWith("Assuming 1 simulation second")),
                    "the calibration explanation must not remain in the sidebar dashboard");
            assertTrue(permanentText.stream().noneMatch(text -> text.startsWith("Units: distance =")),
                    "the unit description must be shown on demand rather than permanently");

            Event.fireEvent(guide, mouse(MouseEvent.MOUSE_ENTERED, 0.0, 0.0, MouseButton.NONE));
            assertTrue(guidePopover.isShowing());
            simulator.stop();
            assertFalse(guidePopover.isShowing(), "application stop must close visible hover popovers");
            assertFalse(unitDescriptionPopover.isShowing());
            assertFalse(calibrationPopover.isShowing());
        }); return null; });
    }

    @Test
    void calibrationHoverTargetAndPopoverStayIdenticalAcrossDashboardRefreshes() throws Exception {
        onFxThread(() -> { withSimulator((simulator, stage) -> {
            invoke(simulator, "updateDashboard");
            Label header = (Label) field(simulator, "unitCalibrationHeader");
            PopOver popover = (PopOver) field(simulator, "unitCalibrationPopover");
            for (int update = 0; update < 4; update++) {
                invoke(simulator, "updateDashboard");
                assertSame(header, field(simulator, "unitCalibrationHeader"));
                assertSame(popover, field(simulator, "unitCalibrationPopover"));
            }
        }); return null; });
    }

    @Test
    void calibrationPopoverRemainsVisibleWhileDynamicDashboardRowsRefresh() throws Exception {
        HoverTestContext context = onFxThread(() -> {
            BodySimulator simulator = new BodySimulator();
            Stage stage = new Stage();
            Canvas canvas = new Canvas(800.0, 600.0);
            simulator.configureCanvasInteractions(canvas);
            VBox dashboard = (VBox) field(simulator, "dashboard");
            stage.setScene(new Scene(new VBox(canvas, dashboard)));
            stage.show();
            invoke(simulator, "updateDashboard");
            return new HoverTestContext(simulator, stage,
                    (Label) field(simulator, "unitCalibrationHeader"),
                    (PopOver) field(simulator, "unitCalibrationPopover"));
        });
        AtomicBoolean triggerWasDetached = new AtomicBoolean();
        try {
            onFxThread(() -> {
                VBox dashboard = (VBox) field(context.simulator(), "dashboard");
                dashboard.getChildren().addListener((ListChangeListener<Node>) change -> {
                    while (change.next()) {
                        if (change.wasRemoved() && change.getRemoved().contains(context.trigger())) {
                            triggerWasDetached.set(true);
                        }
                    }
                });
                Event.fireEvent(context.trigger(), mouse(MouseEvent.MOUSE_ENTERED, 0.0, 0.0, MouseButton.NONE));
                assertTrue(context.popover().isShowing());
                return null;
            });

            for (int refresh = 0; refresh < 8; refresh++) {
                onFxThread(() -> {
                    invoke(context.simulator(), "updateDashboard");
                    assertSame(context.trigger(), field(context.simulator(), "unitCalibrationHeader"));
                    assertTrue(context.popover().isShowing(),
                            "dynamic dashboard refresh must not close calibration help");
                    return null;
                });
                awaitFxPulses(2);
            }

            onFxThread(() -> {
                assertFalse(triggerWasDetached.get(),
                        "the hovered calibration label must remain attached while dynamic rows refresh");
                Bounds triggerBounds = context.trigger().localToScreen(context.trigger().getBoundsInLocal());
                Bounds popoverBounds = new BoundingBox(context.popover().getX(), context.popover().getY(),
                        context.popover().getWidth(), context.popover().getHeight());
                assertFalse(popoverBounds.intersects(triggerBounds));
                Event.fireEvent(context.trigger(), mouse(MouseEvent.MOUSE_EXITED, 0.0, 0.0, MouseButton.NONE));
                assertFalse(context.popover().isShowing());
                return null;
            });
        } finally {
            onFxThread(() -> {
                context.simulator().stop();
                context.stage().hide();
                return null;
            });
        }
    }

    @Test
    void stagedHoverPopoverStaysOpenWithoutCoveringItsTriggerAndClosesAfterExit() throws Exception {
        HoverTestContext context = onFxThread(() -> {
            BodySimulator simulator = new BodySimulator();
            Stage stage = new Stage();
            Canvas canvas = new Canvas(800.0, 600.0);
            simulator.configureCanvasInteractions(canvas);
            Object[] trails = (Object[]) field(simulator, "trails");
            Object[] fullTracks = (Object[]) field(simulator, "fullTracks");
            for (int i = 0; i < trails.length; i++) {
                trails[i] = new ArrayDeque<>();
                fullTracks[i] = new ArrayList<>();
            }
            BodySimulator.SidebarHoverTriggers triggers = simulator.createSidebarHoverTriggers();
            stage.setScene(new Scene(new VBox(canvas, triggers.guide(), triggers.unitDescription(),
                    (VBox) field(simulator, "dashboard"))));
            stage.show();
            return new HoverTestContext(simulator, stage, triggers.guide(),
                    (PopOver) field(simulator, "guidePopover"));
        });
        try {
            onFxThread(() -> {
                Event.fireEvent(context.trigger(), mouse(MouseEvent.MOUSE_ENTERED, 0.0, 0.0, MouseButton.NONE));
                assertTrue(context.popover().isShowing());
                return null;
            });
            awaitFxPulses(3);

            onFxThread(() -> {
                PopOver popover = context.popover();
                Bounds triggerBounds = context.trigger().localToScreen(context.trigger().getBoundsInLocal());
                Bounds popoverBounds = new BoundingBox(popover.getX(), popover.getY(), popover.getWidth(), popover.getHeight());
                assertNotNull(triggerBounds, "shown trigger must have screen bounds");
                assertTrue(popover.isShowing(), "help must remain open while the pointer has not exited its trigger");
                assertTrue(popover.getWidth() > 0.0 && popover.getHeight() > 0.0,
                        "shown PopOver must have laid out bounds");
                assertFalse(popoverBounds.intersects(triggerBounds),
                        "the popup must not cover its trigger, or real mouse hover will oscillate between enter and exit");
                Event.fireEvent(context.trigger(), mouse(MouseEvent.MOUSE_EXITED, 0.0, 0.0, MouseButton.NONE));
                assertFalse(popover.isShowing(), "help must close after the pointer leaves its trigger");
                return null;
            });
        } finally {
            onFxThread(() -> {
                context.simulator().stop();
                context.stage().hide();
                return null;
            });
        }
    }

    @Test
    void secondaryBodyClicksRemoveExactSlotsCompactStateAndPersistTheNewResetSnapshot() throws Exception {
        for (int removedIndex : new int[]{0, 1, 2}) {
            onFxThread(() -> { withSimulator((simulator, stage) -> {
                configureBodies(simulator, 3);
                setField(simulator, "running", true);
                setField(simulator, "planDirty", false);
                Canvas canvas = (Canvas) field(simulator, "canvas");
                double[] target = projection(simulator, removedIndex);
                MouseEvent click = mouse(MouseEvent.MOUSE_PRESSED, target[0], target[1], MouseButton.SECONDARY);
                Event.fireEvent(canvas, click);

                assertEquals(2, intField(simulator, "bodyCount"));
                assertTrue((boolean) field(simulator, "running"), "deleting a body must preserve the prior run state");
                assertTrue((boolean) field(simulator, "planDirty"), "deletion invalidates any device plan built for the old arrays");
                assertEquals(-1, intField(simulator, "draggedBodyIndex"));
                assertFalse((boolean) field(simulator, "rotatingCamera"),
                        "a secondary click on a body must not fall through to camera dragging");
                assertFalse((boolean) field(simulator, "rotatingRoll"),
                        "a secondary click on a body must not fall through to roll dragging");
                assertRemovedSlotState(simulator, removedIndex, 3);

                assertEquals(2, ((VBox) field(simulator, "editorList")).getChildren().size(),
                        "the editor must no longer contain the deleted body");
                assertEquals(2, dashboardBodyLines(simulator).size(),
                        "the dashboard must no longer contain the deleted body");
                invoke(simulator, "resetToInitialState");
                assertEquals(2, intField(simulator, "bodyCount"), "Reset must restore the post-delete snapshot, not resurrect the body");
                assertRemovedSlotState(simulator, removedIndex, 3);
            }); return null; });
        }

        onFxThread(() -> { withSimulator((simulator, stage) -> {
            configureBodies(simulator, 1);
            double[] target = projection(simulator, 0);
            Event.fireEvent((Canvas) field(simulator, "canvas"),
                    mouse(MouseEvent.MOUSE_PRESSED, target[0], target[1], MouseButton.SECONDARY));
            assertEquals(0, intField(simulator, "bodyCount"), "the last remaining body must also be removable");
            assertEquals(0, ((IntArray) field(simulator, "state")).get(0));
            assertTrue(dashboardBodyLines(simulator).isEmpty());
        }); return null; });
    }

    @Test
    void secondaryEmptySpaceRollsPrimaryDragMovesABodyAndHitTestingUsesTheActiveCamera() throws Exception {
        onFxThread(() -> { withSimulator((simulator, stage) -> {
            configureBodies(simulator, 2);
            setField(simulator, "viewScale", 113.0);
            setField(simulator, "viewCenterX", 1.75);
            setField(simulator, "viewCenterY", -0.85);
            setField(simulator, "viewCenterZ", 0.35);
            setField(simulator, "cameraYaw", 0.69);
            setField(simulator, "cameraPitch", -0.37);
            setField(simulator, "cameraRoll", 0.31);
            Canvas canvas = (Canvas) field(simulator, "canvas");

            for (int i = 0; i < 2; i++) {
                double[] point = projection(simulator, i);
                assertEquals(i, (int) invoke(simulator, "bodyAt", new Class<?>[]{double.class, double.class}, point[0], point[1]),
                        "hit testing must use the same pan/zoom/rotation transform as drawing");
            }

            double rollBefore = (double) field(simulator, "cameraRoll");
            Event.fireEvent(canvas, mouse(MouseEvent.MOUSE_PRESSED, 8.0, 8.0, MouseButton.SECONDARY));
            Event.fireEvent(canvas, mouse(MouseEvent.MOUSE_DRAGGED, 38.0, 8.0, MouseButton.SECONDARY));
            assertTrue(Math.abs((double) field(simulator, "cameraRoll") - rollBefore) > 1.0e-6,
                    "secondary drag on empty space retains roll interaction");

            double[] body = projection(simulator, 0);
            float originalX = array(simulator, "posX").get(0);
            Event.fireEvent(canvas, mouse(MouseEvent.MOUSE_PRESSED, body[0], body[1], MouseButton.PRIMARY));
            Event.fireEvent(canvas, mouse(MouseEvent.MOUSE_DRAGGED, body[0] + 24.0, body[1] - 14.0, MouseButton.PRIMARY));
            Event.fireEvent(canvas, mouse(MouseEvent.MOUSE_RELEASED, body[0] + 24.0, body[1] - 14.0, MouseButton.PRIMARY));
            assertTrue(Math.abs(array(simulator, "posX").get(0) - originalX) > 1.0e-4,
                    "primary drag must remain available for body repositioning");
        }); return null; });
    }

    private static void assertPopover(Label trigger, PopOver popover, String requiredText) {
        Label content = (Label) popover.getContentNode();
        assertTrue(content.getText().contains(requiredText));
        Event.fireEvent(trigger, mouse(MouseEvent.MOUSE_ENTERED, 0.0, 0.0, MouseButton.NONE));
        assertTrue(popover.isShowing(), "hover enter must show help immediately");
        Event.fireEvent(trigger, mouse(MouseEvent.MOUSE_EXITED, 0.0, 0.0, MouseButton.NONE));
        assertFalse(popover.isShowing(), "hover exit must hide help immediately");
    }

    private static void configureBodies(BodySimulator simulator, int count) throws Exception {
        setField(simulator, "bodyCount", count);
        String[] names = (String[]) field(simulator, "names");
        Color[] colors = (Color[]) field(simulator, "colors");
        IntArray active = (IntArray) field(simulator, "active");
        for (int i = 0; i < count; i++) {
            array(simulator, "posX").set(i, -2.4f + i * 2.4f);
            array(simulator, "posY").set(i, 0.55f * i);
            array(simulator, "posZ").set(i, -0.35f * i);
            array(simulator, "velX").set(i, 10.0f + i);
            array(simulator, "mass").set(i, 20.0f + i);
            active.set(i, 1);
            names[i] = "Body-" + i;
            colors[i] = new Color(0.2 + i * 0.2, 0.3, 0.8 - i * 0.2, 1.0);
        }
        ((IntArray) field(simulator, "state")).set(0, count);
        invoke(simulator, "snapshotInitialState");
        invoke(simulator, "rebuildEditors");
        invoke(simulator, "updateDashboard");
    }

    private static void assertRemovedSlotState(BodySimulator simulator, int removed, int originalCount) throws Exception {
        FloatArray positions = array(simulator, "posX");
        FloatArray velocities = array(simulator, "velX");
        FloatArray masses = array(simulator, "mass");
        IntArray active = (IntArray) field(simulator, "active");
        String[] names = (String[]) field(simulator, "names");
        Color[] colors = (Color[]) field(simulator, "colors");
        for (int target = 0; target < originalCount - 1; target++) {
            int original = target < removed ? target : target + 1;
            assertEquals(-2.4f + original * 2.4f, positions.get(target), 1.0e-6);
            assertEquals(10.0f + original, velocities.get(target), 1.0e-6);
            assertEquals(20.0f + original, masses.get(target), 1.0e-6);
            assertEquals(1, active.get(target));
            assertEquals("Body-" + original, names[target]);
            assertEquals(new Color(0.2 + original * 0.2, 0.3, 0.8 - original * 0.2, 1.0), colors[target]);
        }
        int cleared = originalCount - 1;
        assertEquals(0.0f, positions.get(cleared), 0.0f);
        assertEquals(0.0f, velocities.get(cleared), 0.0f);
        assertEquals(0.0f, masses.get(cleared), 0.0f);
        assertEquals(0, active.get(cleared));
        assertNull(names[cleared]);
        assertNull(colors[cleared]);
        assertEquals(originalCount - 1, ((IntArray) field(simulator, "state")).get(0));
    }

    private static List<Label> dashboardBodyLines(BodySimulator simulator) throws Exception {
        return labelsIn((VBox) field(simulator, "dashboard")).stream()
                .filter(label -> label.getText().startsWith("Body-")).toList();
    }

    private static double[] projection(BodySimulator simulator, int index) throws Exception {
        return (double[]) invoke(simulator, "projectPoint", new Class<?>[]{double.class, double.class, double.class},
                (double) array(simulator, "posX").get(index), (double) array(simulator, "posY").get(index),
                (double) array(simulator, "posZ").get(index));
    }

    private static Label labelInScene(BodySimulator simulator, String text) throws Exception {
        Scene scene = ((Canvas) field(simulator, "canvas")).getScene();
        return labelsIn(scene.getRoot()).stream().filter(label -> text.equals(label.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("missing visible label " + text));
    }

    private static List<Label> labelsIn(Node node) {
        List<Label> labels = new ArrayList<>();
        if (node instanceof Label label) {
            labels.add(label);
        }
        if (node instanceof Pane pane) {
            for (Node child : pane.getChildren()) {
                labels.addAll(labelsIn(child));
            }
        }
        return labels;
    }

    private static void withSimulator(SimulatorAction action) throws Exception {
        BodySimulator simulator = new BodySimulator();
        Stage stage = new Stage();
        try {
            Canvas canvas = new Canvas(800.0, 600.0);
            simulator.configureCanvasInteractions(canvas);
            Object[] trails = (Object[]) field(simulator, "trails");
            Object[] fullTracks = (Object[]) field(simulator, "fullTracks");
            for (int i = 0; i < trails.length; i++) {
                trails[i] = new ArrayDeque<>();
                fullTracks[i] = new ArrayList<>();
            }
            BodySimulator.SidebarHoverTriggers triggers = simulator.createSidebarHoverTriggers();
            VBox root = new VBox(canvas, triggers.guide(), triggers.unitDescription(),
                    (VBox) field(simulator, "dashboard"));
            stage.setScene(new Scene(root));
            stage.show();
            invoke(simulator, "updateDashboard");
            action.run(simulator, stage);
        } finally {
            simulator.stop();
            stage.hide();
        }
    }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> eventType, double x, double y, MouseButton button) {
        boolean primary = button == MouseButton.PRIMARY;
        boolean secondary = button == MouseButton.SECONDARY;
        return new MouseEvent(eventType, x, y, x, y, button, 1,
                false, false, false, false, primary, false, secondary,
                false, secondary, false, null);
    }

    private static FloatArray array(BodySimulator simulator, String name) throws Exception {
        return (FloatArray) field(simulator, name);
    }

    private static int intField(BodySimulator simulator, String name) throws Exception {
        return (int) field(simulator, name);
    }

    private static Object field(BodySimulator simulator, String name) throws Exception {
        Field field = BodySimulator.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(simulator);
    }

    private static void setField(BodySimulator simulator, String name, Object value) throws Exception {
        Field field = BodySimulator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(simulator, value);
    }

    private static Object invoke(BodySimulator simulator, String name, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        Method method = BodySimulator.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(simulator, arguments);
    }

    private static void invoke(BodySimulator simulator, String name) throws Exception {
        invoke(simulator, name, new Class<?>[0]);
    }

    private static void awaitFxPulses(int count) throws Exception {
        for (int pulse = 0; pulse < count; pulse++) {
            onFxThread(() -> null);
        }
    }

    private static <T> T onFxThread(ThrowingSupplier<T> supplier) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return supplier.get();
        }
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(supplier.get());
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

    @FunctionalInterface
    private interface SimulatorAction {
        void run(BodySimulator simulator, Stage stage) throws Exception;
    }

    private record HoverTestContext(BodySimulator simulator, Stage stage, Label trigger, PopOver popover) {
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
