package pawg.body;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pawg.nbody.TornadoDeviceChoice;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BodySimulatorCameraFeatureTest {

    private static final double EPSILON = 1.0e-9;
    private static final double MIN_VIEW_SCALE = 16.0;
    private static final double MAX_VIEW_SCALE = 180.0;

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
    void combinedRotationProjectsEveryWorldBasisConsistently() {
        double centerX = 3.5;
        double centerY = -1.25;
        double centerZ = 4.75;
        double yaw = 0.63;
        double pitch = -0.41;
        double roll = 0.28;
        double scale = 37.0;
        double width = 900.0;
        double height = 540.0;

        double[] center = BodySimulator.projectWorldToScreen(
                centerX, centerY, centerZ,
                centerX, centerY, centerZ,
                yaw, pitch, roll, scale, width, height);
        assertArrayEquals(new double[]{width * 0.5, height * 0.5}, center, EPSILON);

        double[][] axes = {{1.0, 0.0, 0.0}, {0.0, 1.0, 0.0}, {0.0, 0.0, 1.0}};
        for (double[] axis : axes) {
            double[] viewBasis = BodySimulator.rotateWorldToView(
                    axis[0], axis[1], axis[2], yaw, pitch, roll);
            double[] projected = BodySimulator.projectWorldToScreen(
                    centerX + axis[0], centerY + axis[1], centerZ + axis[2],
                    centerX, centerY, centerZ,
                    yaw, pitch, roll, scale, width, height);

            assertEquals(center[0] + viewBasis[0] * scale, projected[0], EPSILON,
                    "horizontal projection must use the rotated basis");
            assertEquals(center[1] - viewBasis[1] * scale, projected[1], EPSILON,
                    "screen Y must be the inverse of the rotated view Y basis");
        }
    }

    @Test
    void projectionAndInverseRoundTripAtPreservedViewDepth() {
        double centerX = -2.25;
        double centerY = 6.5;
        double centerZ = 1.75;
        double yaw = -0.72;
        double pitch = 0.46;
        double roll = -0.31;
        double scale = 73.0;
        double width = 1024.0;
        double height = 640.0;
        double[][] worldPoints = {
                {8.0, -2.0, 5.0},
                {-11.0, 4.5, -3.0},
                {centerX, centerY, centerZ}
        };

        for (double[] world : worldPoints) {
            double[] view = BodySimulator.rotateWorldToView(
                    world[0] - centerX, world[1] - centerY, world[2] - centerZ,
                    yaw, pitch, roll);
            double[] screen = BodySimulator.projectWorldToScreen(
                    world[0], world[1], world[2],
                    centerX, centerY, centerZ,
                    yaw, pitch, roll, scale, width, height);
            double[] roundTrip = BodySimulator.unprojectScreenAtViewDepth(
                    screen[0], screen[1], view[2],
                    centerX, centerY, centerZ,
                    yaw, pitch, roll, scale, width, height);

            assertArrayEquals(world, roundTrip, 1.0e-10,
                    "unprojection must preserve the selected body's view depth");
        }
    }

    @Test
    void arrowsPanTenPercentOfViewportAtMinimumAndMaximumZoom() throws Exception {
        assertPanAtScale(MIN_VIEW_SCALE, false);
        assertPanAtScale(MAX_VIEW_SCALE, false);
    }

    @Test
    void shiftArrowsPanFourTimesFasterInTheRotatedViewPlane() throws Exception {
        assertPanAtScale(MIN_VIEW_SCALE, true);
        assertPanAtScale(MAX_VIEW_SCALE, true);
    }

    @Test
    void homeRecentersAndResetRestoresTheInitialCamera() throws Exception {
        onFxThread(() -> {
            BodySimulator simulator = initializedSimulator(260.0, 160.0);
            setField(simulator, "viewCenterX", 7.0);
            setField(simulator, "viewCenterY", -8.0);
            setField(simulator, "viewCenterZ", 9.0);
            setField(simulator, "cameraYaw", 0.7);
            setField(simulator, "cameraPitch", -0.4);
            setField(simulator, "cameraRoll", 0.2);
            setField(simulator, "viewScale", 90.0);

            KeyEvent home = keyEvent(KeyCode.HOME, false);
            invoke(simulator, "handleCameraKeyPressed", new Class<?>[]{KeyEvent.class}, home);
            assertCameraCenter(simulator, 0.0, 0.0, 0.0);
            assertEquals(0.7, doubleField(simulator, "cameraYaw"), EPSILON);
            assertEquals(-0.4, doubleField(simulator, "cameraPitch"), EPSILON);
            assertEquals(0.2, doubleField(simulator, "cameraRoll"), EPSILON);
            assertEquals(90.0, doubleField(simulator, "viewScale"), EPSILON);
            assertTrue(home.isConsumed());

            setField(simulator, "viewCenterX", -5.0);
            setField(simulator, "viewCenterY", 4.0);
            setField(simulator, "viewCenterZ", 3.0);
            invoke(simulator, "resetToInitialState", new Class<?>[0]);
            assertCameraCenter(simulator, 0.0, 0.0, 0.0);
            assertEquals(0.0, doubleField(simulator, "cameraYaw"), EPSILON);
            assertEquals(0.0, doubleField(simulator, "cameraPitch"), EPSILON);
            assertEquals(0.0, doubleField(simulator, "cameraRoll"), EPSILON);
            assertEquals(45.0, doubleField(simulator, "viewScale"), EPSILON);
            return null;
        });
    }

    @Test
    void arrowKeysDoNotMoveTheCameraWhileAControlHasFocus() throws Exception {
        onFxThread(() -> {
            BodySimulator simulator = initializedSimulator(260.0, 160.0);
            Canvas canvas = (Canvas) field(simulator, "canvas");
            TextField editor = new TextField("12.5");
            ComboBox<String> deviceSelector = new ComboBox<>();
            deviceSelector.getItems().addAll("Device 0", "Device 1");
            deviceSelector.getSelectionModel().selectFirst();
            Stage stage = new Stage();
            try {
                stage.setScene(new Scene(new VBox(canvas, editor, deviceSelector), 300.0, 240.0));
                stage.show();
                for (Control control : new Control[]{editor, deviceSelector}) {
                    control.requestFocus();
                    assertSame(control, stage.getScene().getFocusOwner(),
                            "test precondition: the control owns focus");

                    setField(simulator, "viewCenterX", 1.0);
                    setField(simulator, "viewCenterY", 2.0);
                    setField(simulator, "viewCenterZ", 3.0);
                    KeyEvent event = keyEvent(KeyCode.RIGHT, true);
                    invoke(simulator, "handleCameraKeyPressed", new Class<?>[]{KeyEvent.class}, event);

                    assertCameraCenter(simulator, 1.0, 2.0, 3.0);
                    assertFalse(event.isConsumed(), "a focused control must retain normal arrow-key handling");
                }
            } finally {
                stage.close();
            }
            return null;
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void devicePopupCellUsesDistinctHoverBackgroundAndRestoresItAfterMouseExit() throws Exception {
        onFxThread(() -> {
            BodySimulator simulator = new BodySimulator();
            ListCell<TornadoDeviceChoice> cell = (ListCell<TornadoDeviceChoice>) invoke(
                    simulator, "tornadoDeviceListCell", new Class<?>[0]);
            TornadoDeviceChoice choice = new TornadoDeviceChoice(
                    0, 1, "OpenCL device 0:1", "device info", "command output", false);
            Stage stage = showDevicePopupCell(cell);
            try {
                updateDeviceCell(cell, choice, false);
                cell.applyCss();
                Color normalBackground = cellBackgroundColor(cell);
                assertEquals(Color.web("#1b2533"), normalBackground,
                        "a populated device popup cell must start with the dashboard background");

                fireMouseTransition(cell, MouseEvent.MOUSE_ENTERED);
                cell.applyCss();
                Color hoverBackground = cellBackgroundColor(cell);
                assertEquals(Color.web("#365f85"), hoverBackground,
                        "hovering a populated device popup cell must make the candidate selection visible");
                assertFalse(normalBackground.equals(hoverBackground));

                fireMouseTransition(cell, MouseEvent.MOUSE_EXITED);
                cell.applyCss();
                assertEquals(normalBackground, cellBackgroundColor(cell),
                        "leaving the popup item must restore its normal background");
            } finally {
                stage.close();
            }
            return null;
        });
    }

    @Test
    void gridStaysFiniteBoundedAndVisibleAcrossZoomAndCombinedRotation() throws Exception {
        onFxThread(() -> {
            BodySimulator simulator = initializedSimulator(360.0, 220.0);
            addBodyState(simulator, 0, 1.5f, -0.75f, 2.0f, 120.0f, Color.ORANGE);
            setField(simulator, "bodyCount", 1);

            double[][] rotations = {
                    {0.0, 0.0, 0.0},
                    {0.71, -0.46, 0.33},
                    {1.18, Math.PI * 0.479, -0.82}
            };
            double[] scales = {MIN_VIEW_SCALE, 45.0, MAX_VIEW_SCALE};
            for (double scale : scales) {
                for (double[] rotation : rotations) {
                    setField(simulator, "viewScale", scale);
                    setField(simulator, "cameraYaw", rotation[0]);
                    setField(simulator, "cameraPitch", rotation[1]);
                    setField(simulator, "cameraRoll", rotation[2]);
                    invokeGridRebuild(simulator, 360.0, 220.0);

                    assertFiniteBoundedGrid(simulator);
                    WritableImage rendered = renderGravityGrid(simulator);
                    assertTrue(countVisiblePixels(rendered) > 0,
                            "the grid must cover the canvas at scale=" + scale + " rotation=" + rotation[0]);
                }
            }
            return null;
        });
    }

    @Test
    void clippingKeepsCrossingSegmentsInBoundsAndRejectsInvalidOrDisjointSegments() {
        assertArrayEquals(new double[]{0.0, 5.0, 10.0, 5.0},
                BodySimulator.clipLineToRectangle(-20.0, 5.0, 30.0, 5.0, 0.0, 0.0, 10.0, 10.0), EPSILON);
        assertArrayEquals(new double[]{5.0, 0.0, 5.0, 10.0},
                BodySimulator.clipLineToRectangle(5.0, -4.0, 5.0, 14.0, 0.0, 0.0, 10.0, 10.0), EPSILON);
        assertNull(BodySimulator.clipLineToRectangle(-3.0, -2.0, -1.0, -4.0, 0.0, 0.0, 10.0, 10.0));
        assertNull(BodySimulator.clipLineToRectangle(Double.NaN, 1.0, 2.0, 3.0, 0.0, 0.0, 10.0, 10.0));
    }

    @Test
    void gridCacheTracksPanAndRotationAndAddBodyRefreshesDeformation() throws Exception {
        onFxThread(() -> {
            BodySimulator simulator = initializedSimulator(320.0, 180.0);
            invokeGridRebuild(simulator, 320.0, 180.0);
            float[] backing = (float[]) field(simulator, "gridPointX");
            invokeGridRebuild(simulator, 320.0, 180.0);
            assertSame(backing, field(simulator, "gridPointX"), "an unchanged camera must reuse cached storage");

            setField(simulator, "viewCenterX", 2.5);
            setField(simulator, "viewCenterY", -1.25);
            setField(simulator, "viewCenterZ", 0.75);
            invokeGridRebuild(simulator, 320.0, 180.0);
            assertEquals(2.5, doubleField(simulator, "cachedGridViewCenterX"), EPSILON);
            assertEquals(-1.25, doubleField(simulator, "cachedGridViewCenterY"), EPSILON);
            assertEquals(0.75, doubleField(simulator, "cachedGridViewCenterZ"), EPSILON);

            setField(simulator, "cameraYaw", 0.54);
            setField(simulator, "cameraPitch", -0.37);
            setField(simulator, "cameraRoll", 0.22);
            invokeGridRebuild(simulator, 320.0, 180.0);
            assertEquals(0.54, doubleField(simulator, "cachedGridCameraYaw"), EPSILON);
            assertEquals(-0.37, doubleField(simulator, "cachedGridCameraPitch"), EPSILON);
            assertEquals(0.22, doubleField(simulator, "cachedGridCameraRoll"), EPSILON);

            float[] beforeBody = ((float[]) field(simulator, "gridPointZ")).clone();
            Color[] colors = (Color[]) field(simulator, "colors");
            colors[0] = Color.GOLD;
            invoke(simulator, "addBody", new Class<?>[0]);
            float[] afterBody = (float[]) field(simulator, "gridPointZ");
            int count = (int) field(simulator, "gridPointCount");
            boolean changed = false;
            for (int i = 0; i < Math.min(count, beforeBody.length); i++) {
                if (Float.compare(beforeBody[i], afterBody[i]) != 0) {
                    changed = true;
                    break;
                }
            }
            assertTrue(changed, "adding the default body must refresh cached gravitational deformation");
            assertFalse((boolean) field(simulator, "gridGeometryDirty"),
                    "the add-body redraw should leave the refreshed cache ready for reuse");
            return null;
        });
    }

    @Test
    void curvedGridAndPhotonPointsUseTheSameWorldProjectionAtEveryZoom() throws Exception {
        onFxThread(() -> {
            BodySimulator simulator = initializedSimulator(640.0, 360.0);
            setField(simulator, "bodyCount", 1);
            addBodyState(simulator, 0, 0.0f, 0.0f, 0.0f, 200.0f, Color.ORANGE);
            double yaw = 0.68;
            double pitch = -0.43;
            double roll = 0.29;
            setField(simulator, "cameraYaw", yaw);
            setField(simulator, "cameraPitch", pitch);
            setField(simulator, "cameraRoll", roll);

            for (double scale : new double[]{MIN_VIEW_SCALE, 45.0, MAX_VIEW_SCALE}) {
                setField(simulator, "viewScale", scale);
                double[] bentWorldPoint = pointCoordinates(invoke(
                        simulator, "bentGridPoint", new Class<?>[]{double.class, double.class}, 3.0, 1.25));
                double[] curvedRenderPoint = pointCoordinates(invoke(
                        simulator, "curvedSpaceRenderPoint", new Class<?>[]{double.class, double.class}, 3.0, 1.25));
                double[] expected = BodySimulator.projectWorldToScreen(
                        bentWorldPoint[0], bentWorldPoint[1], bentWorldPoint[2],
                        0.0, 0.0, 0.0, yaw, pitch, roll, scale, 640.0, 360.0);
                double[] actual = BodySimulator.projectWorldToScreen(
                        curvedRenderPoint[0], curvedRenderPoint[1], curvedRenderPoint[2],
                        0.0, 0.0, 0.0, yaw, pitch, roll, scale, 640.0, 360.0);

                assertArrayEquals(expected, actual, 1.0e-6,
                        "curved grid/photon points must not rescale only world Z before the shared projection");
            }
            return null;
        });
    }

    @Test
    void rotationIndicatorUsesBodyColorsCountDirectionCommonScaleAndClamping() throws Exception {
        onFxThread(() -> {
            BodySimulator simulator = initializedSimulator(400.0, 200.0);
            setField(simulator, "bodyCount", 3);
            addBodyState(simulator, 0, 1.0f, 0.0f, 0.0f, 1.0f, Color.RED);
            addBodyState(simulator, 1, -2.0f, 0.0f, 0.0f, 1.0f, Color.BLUE);
            addBodyState(simulator, 2, 0.0f, 1.5f, 0.0f, 1.0f, Color.LIME);

            WritableImage image = renderIndicatorBodies(simulator);
            PixelCluster red = coloredCluster(image, DominantColor.RED);
            PixelCluster blue = coloredCluster(image, DominantColor.BLUE);
            PixelCluster green = coloredCluster(image, DominantColor.GREEN);
            assertNotNull(red);
            assertNotNull(blue);
            assertNotNull(green);

            double indicatorCenterX = 400.0 - 92.0;
            double indicatorCenterY = 76.0;
            assertTrue(red.centerX() > indicatorCenterX, "positive world X must appear right of center");
            assertTrue(blue.centerX() < indicatorCenterX, "negative world X must appear left of center");
            assertTrue(green.centerY() < indicatorCenterY, "positive world Y must appear above center");
            assertEquals(0.5,
                    (red.centerX() - indicatorCenterX) / (indicatorCenterX - blue.centerX()),
                    0.12, "all dots must use a common world-to-indicator scale");

            position(simulator, "posX").set(0, 1_000_000.0f);
            WritableImage clampedImage = renderIndicatorBodies(simulator);
            PixelCluster clampedRed = coloredCluster(clampedImage, DominantColor.RED);
            assertNotNull(clampedRed);
            assertTrue(clampedRed.maximumX() <= indicatorCenterX + 68.0 + 1.0,
                    "a distant body dot must remain within the indicator's horizontal inset");
            assertTrue(clampedRed.centerX() > indicatorCenterX + 60.0,
                    "the distant body must clamp at the right edge, not collapse toward center");
            return null;
        });
    }

    @Test
    void rotationIndicatorFollowsLiveBodyMotion() throws Exception {
        onFxThread(() -> {
            BodySimulator simulator = initializedSimulator(400.0, 200.0);
            setField(simulator, "bodyCount", 1);
            addBodyState(simulator, 0, 0.5f, 0.0f, 0.0f, 1.0f, Color.RED);
            PixelCluster before = coloredCluster(renderIndicatorBodies(simulator), DominantColor.RED);

            position(simulator, "posX").set(0, 1.5f);
            position(simulator, "posY").set(0, 0.75f);
            PixelCluster after = coloredCluster(renderIndicatorBodies(simulator), DominantColor.RED);

            assertNotNull(before);
            assertNotNull(after);
            assertTrue(after.centerX() > before.centerX() + 5.0,
                    "the indicator must read the current body X position on every frame");
            assertTrue(after.centerY() < before.centerY() - 3.0,
                    "the indicator must read the current body Y position on every frame");
            return null;
        });
    }

    private static void assertPanAtScale(double scale, boolean shift) throws Exception {
        onFxThread(() -> {
            double yaw = 0.67;
            double pitch = -0.39;
            double roll = 0.24;
            double width = 240.0;
            double height = 140.0;
            double multiplier = shift ? 4.0 : 1.0;
            for (KeyCode code : new KeyCode[]{KeyCode.LEFT, KeyCode.RIGHT, KeyCode.UP, KeyCode.DOWN}) {
                BodySimulator simulator = initializedSimulator(width, height);
                setField(simulator, "viewScale", scale);
                setField(simulator, "cameraYaw", yaw);
                setField(simulator, "cameraPitch", pitch);
                setField(simulator, "cameraRoll", roll);

                double viewX = code == KeyCode.LEFT ? -width / scale * 0.10 * multiplier
                        : code == KeyCode.RIGHT ? width / scale * 0.10 * multiplier : 0.0;
                double viewY = code == KeyCode.DOWN ? -height / scale * 0.10 * multiplier
                        : code == KeyCode.UP ? height / scale * 0.10 * multiplier : 0.0;
                double[] expected = BodySimulator.rotateViewToWorld(viewX, viewY, 0.0, yaw, pitch, roll);

                KeyEvent event = keyEvent(code, shift);
                invoke(simulator, "handleCameraKeyPressed", new Class<?>[]{KeyEvent.class}, event);
                assertCameraCenter(simulator, expected[0], expected[1], expected[2]);
                assertTrue(event.isConsumed());
            }
            return null;
        });
    }

    private static BodySimulator initializedSimulator(double width, double height) throws Exception {
        BodySimulator simulator = new BodySimulator();
        setField(simulator, "canvas", new Canvas(width, height));
        Object[] trails = (Object[]) field(simulator, "trails");
        Object[] fullTracks = (Object[]) field(simulator, "fullTracks");
        for (int i = 0; i < trails.length; i++) {
            trails[i] = new ArrayDeque<>();
            fullTracks[i] = new ArrayList<>();
        }
        return simulator;
    }

    private static void addBodyState(BodySimulator simulator, int index,
                                     float x, float y, float z, float mass, Color color) throws Exception {
        position(simulator, "posX").set(index, x);
        position(simulator, "posY").set(index, y);
        position(simulator, "posZ").set(index, z);
        position(simulator, "mass").set(index, mass);
        Color[] colors = (Color[]) field(simulator, "colors");
        colors[index] = color;
        String[] names = (String[]) field(simulator, "names");
        names[index] = "Body " + (index + 1);
    }

    private static WritableImage renderGravityGrid(BodySimulator simulator) throws Exception {
        Canvas canvas = (Canvas) field(simulator, "canvas");
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.clearRect(0.0, 0.0, canvas.getWidth(), canvas.getHeight());
        invoke(simulator, "drawGravityGrid",
                new Class<?>[]{GraphicsContext.class, double.class, double.class},
                graphics, canvas.getWidth(), canvas.getHeight());
        return snapshot(canvas);
    }

    private static WritableImage renderIndicatorBodies(BodySimulator simulator) throws Exception {
        Canvas canvas = (Canvas) field(simulator, "canvas");
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.clearRect(0.0, 0.0, canvas.getWidth(), canvas.getHeight());
        invoke(simulator, "drawRotationIndicatorBodies",
                new Class<?>[]{GraphicsContext.class, double.class, double.class},
                graphics, canvas.getWidth() - 92.0, 76.0);
        return snapshot(canvas);
    }

    private static WritableImage snapshot(Canvas canvas) {
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        return canvas.snapshot(parameters, null);
    }

    private static int countVisiblePixels(WritableImage image) {
        int count = 0;
        for (int y = 0; y < (int) image.getHeight(); y++) {
            for (int x = 0; x < (int) image.getWidth(); x++) {
                if (image.getPixelReader().getColor(x, y).getOpacity() > 0.0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static PixelCluster coloredCluster(WritableImage image, DominantColor dominant) {
        double sumX = 0.0;
        double sumY = 0.0;
        int count = 0;
        int maximumX = -1;
        for (int y = 0; y < (int) image.getHeight(); y++) {
            for (int x = 0; x < (int) image.getWidth(); x++) {
                Color color = image.getPixelReader().getColor(x, y);
                if (color.getOpacity() < 0.10 || !dominant.matches(color)) {
                    continue;
                }
                sumX += x;
                sumY += y;
                maximumX = Math.max(maximumX, x);
                count++;
            }
        }
        return count == 0 ? null : new PixelCluster(sumX / count, sumY / count, maximumX, count);
    }

    private static void assertFiniteBoundedGrid(BodySimulator simulator) throws Exception {
        int pointCount = (int) field(simulator, "gridPointCount");
        int lineCount = (int) field(simulator, "gridLineCount");
        assertTrue(pointCount > 0 && pointCount <= 2 * 240 * 600,
                "grid point count must stay within its production cap");
        assertTrue(lineCount > 0 && lineCount <= 2 * 240,
                "grid line count must stay within its production cap");

        float[] x = (float[]) field(simulator, "gridPointX");
        float[] y = (float[]) field(simulator, "gridPointY");
        float[] z = (float[]) field(simulator, "gridPointZ");
        int[] starts = (int[]) field(simulator, "gridLineStarts");
        int[] lengths = (int[]) field(simulator, "gridLineLengths");
        for (int point = 0; point < pointCount; point++) {
            assertTrue(Float.isFinite(x[point]) && Float.isFinite(y[point]) && Float.isFinite(z[point]),
                    "grid point " + point + " must be finite");
        }
        for (int line = 0; line < lineCount; line++) {
            assertTrue(starts[line] >= 0);
            assertTrue(lengths[line] >= 2 && lengths[line] <= 600,
                    "each line must respect the per-line sample bound");
            assertTrue(starts[line] + lengths[line] <= pointCount,
                    "each line range must stay inside the point arrays");
        }
    }

    private static KeyEvent keyEvent(KeyCode code, boolean shift) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, false, false, false);
    }

    private static void assertCameraCenter(BodySimulator simulator,
                                           double expectedX, double expectedY, double expectedZ) throws Exception {
        assertEquals(expectedX, doubleField(simulator, "viewCenterX"), 1.0e-10);
        assertEquals(expectedY, doubleField(simulator, "viewCenterY"), 1.0e-10);
        assertEquals(expectedZ, doubleField(simulator, "viewCenterZ"), 1.0e-10);
    }

    private static void invokeGridRebuild(BodySimulator simulator, double width, double height) throws Exception {
        invoke(simulator, "rebuildGridGeometryIfNeeded",
                new Class<?>[]{double.class, double.class}, width, height);
    }

    private static FloatArray position(BodySimulator simulator, String name) throws Exception {
        return (FloatArray) field(simulator, name);
    }

    private static double doubleField(BodySimulator simulator, String name) throws Exception {
        return (double) field(simulator, name);
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

    private static Object invoke(BodySimulator simulator, String name, Class<?>[] parameterTypes,
                                 Object... arguments) throws Exception {
        Method method = BodySimulator.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(simulator, arguments);
    }

    private static void updateDeviceCell(ListCell<TornadoDeviceChoice> cell,
                                         TornadoDeviceChoice choice, boolean empty) throws Exception {
        Method method = cell.getClass().getDeclaredMethod("updateItem", TornadoDeviceChoice.class, boolean.class);
        method.setAccessible(true);
        method.invoke(cell, choice, empty);
    }

    private static void fireMouseTransition(ListCell<?> cell, javafx.event.EventType<MouseEvent> eventType) {
        cell.fireEvent(new MouseEvent(eventType, 1.0, 1.0, 1.0, 1.0, MouseButton.NONE, 0,
                false, false, false, false, false, false, false, false, false, false, null));
    }

    private static Stage showDevicePopupCell(ListCell<?> cell) {
        Stage stage = new Stage();
        stage.setScene(new Scene(new VBox(cell), 400.0, 60.0));
        stage.show();
        stage.getScene().getRoot().applyCss();
        return stage;
    }

    private static Color cellBackgroundColor(ListCell<?> cell) {
        assertFalse(cell.getBackground().getFills().isEmpty(), "the cell must have a rendered background");
        assertTrue(cell.getBackground().getFills().getFirst().getFill() instanceof Color,
                "the device popup cell background must be a solid color");
        return (Color) cell.getBackground().getFills().getFirst().getFill();
    }

    private static double[] pointCoordinates(Object point) throws Exception {
        Method x = point.getClass().getDeclaredMethod("x");
        Method y = point.getClass().getDeclaredMethod("y");
        Method z = point.getClass().getDeclaredMethod("z");
        x.setAccessible(true);
        y.setAccessible(true);
        z.setAccessible(true);
        return new double[]{
                ((Number) x.invoke(point)).doubleValue(),
                ((Number) y.invoke(point)).doubleValue(),
                ((Number) z.invoke(point)).doubleValue()
        };
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

    private enum DominantColor {
        RED {
            @Override
            boolean matches(Color color) {
                return color.getRed() > 0.65 && color.getGreen() < 0.35 && color.getBlue() < 0.35;
            }
        },
        GREEN {
            @Override
            boolean matches(Color color) {
                return color.getGreen() > 0.65 && color.getRed() < 0.35 && color.getBlue() < 0.35;
            }
        },
        BLUE {
            @Override
            boolean matches(Color color) {
                return color.getBlue() > 0.65 && color.getRed() < 0.35 && color.getGreen() < 0.35;
            }
        };

        abstract boolean matches(Color color);
    }

    private record PixelCluster(double centerX, double centerY, int maximumX, int count) {
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
