package pawg.nbody;

import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.ac.manchester.tornado.api.common.TornadoDevice;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GravityGpuDeviceSelectionTest {

    private static final String DEVICE_SWITCH_TEST_PROPERTY = "gravitygpu.test.device-switch";

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
    void deviceDetectionProvidesUsableChoicesAndAStableDefault() {
        List<TornadoDeviceChoice> choices = TornadoDeviceSelector.deviceChoices();
        TornadoDeviceChoice initialChoice = TornadoDeviceSelector.initialDeviceChoice(choices);

        assertFalse(choices.isEmpty(), "device selection must always offer at least the Tornado default");
        assertTrue(choices.contains(initialChoice));
        assertTrue(choices.stream().allMatch(choice -> choice.title() != null && !choice.title().isBlank()));
        assertTrue(choices.stream().allMatch(choice -> choice.tornadoDeviceId().matches("\\d+:\\d+")));
    }

    @Test
    void deviceCellsRenderChoiceTitlesInWhite() throws Exception {
        GravityGPU simulation = new GravityGPU();
        TornadoDeviceChoice choice = new TornadoDeviceChoice(
                0, 1, "OpenCL device 0:1", "device info", "command output", false);
        ListCell<TornadoDeviceChoice> popupCell = deviceListCell(simulation);
        updateCell(popupCell, choice, false);

        assertEquals(choice.toString(), popupCell.getText());
        assertEquals(Color.WHITE, popupCell.getTextFill());
        assertTrue(popupCell.getStyle().contains("-fx-background-color: #1b2533"));

        updateCell(popupCell, null, true);
        assertNull(popupCell.getText());
        assertEquals(Color.WHITE, popupCell.getTextFill());
    }

    @Test
    void configuredDeviceComboSelectsDefaultAndUsesDashboardPresentation() throws Exception {
        assumeConfiguredTornadoRuntime();
        GravityGPU simulation = new GravityGPU();

        ComboBox<TornadoDeviceChoice> combo = createDeviceCombo(simulation);
        List<TornadoDeviceChoice> choices = List.copyOf(combo.getItems());
        TornadoDeviceChoice choice = combo.getItems().getFirst();
        ListCell<TornadoDeviceChoice> popupCell = combo.getCellFactory().call(null);
        updateCell(popupCell, choice, false);

        assertFalse(choices.isEmpty());
        assertEquals(TornadoDeviceSelector.initialDeviceChoice(choices), combo.getValue());
        assertSame(combo.getValue(), field(simulation, "selectedDeviceChoice"));
        assertEquals(385.0, combo.getPrefWidth());
        assertEquals(combo.getPrefWidth(), combo.getMinWidth());
        assertEquals(combo.getPrefWidth(), combo.getMaxWidth());
        assertNotNull(combo.getTooltip());
        assertEquals("GPU device", combo.getTooltip().getText());
        assertTrue(combo.getStyle().contains("-fx-background-color: #1b2533"));
        assertTrue(combo.getStyle().contains("-fx-border-color: #497aa5"));
        assertTrue(combo.getStyle().contains("-fx-text-fill: white"));
        assertEquals(choice.toString(), popupCell.getText());
        assertEquals(Color.WHITE, popupCell.getTextFill());
        assertTrue(popupCell.getStyle().contains("-fx-background-color: #1b2533"));

        ListCell<TornadoDeviceChoice> buttonCell = combo.getButtonCell();
        updateCell(buttonCell, choice, false);
        assertEquals(choice.toString(), buttonCell.getText());
        assertEquals(Color.WHITE, buttonCell.getTextFill());

    }

    @Test
    void selectingTheCurrentChoiceDoesNotResetOrRebuildPlans() throws Exception {
        assumeConfiguredTornadoRuntime();
        GravityGPU simulation = new GravityGPU();
        ComboBox<TornadoDeviceChoice> combo = createDeviceCombo(simulation);
        TornadoDeviceChoice currentChoice = combo.getValue();
        setField(simulation, "bodyCount", 1);
        setField(simulation, "simulationPlanReady", true);
        setField(simulation, "simulationPlanDirty", true);

        onFxThread(() -> {
            combo.setValue(currentChoice);
            return null;
        });

        assertEquals(1, (int) field(simulation, "bodyCount"));
        assertEquals(true, field(simulation, "simulationPlanReady"));
        assertEquals(true, field(simulation, "simulationPlanDirty"));
        assertSame(currentChoice, field(simulation, "selectedDeviceChoice"));
    }

    @Test
    void stoppingApplicationLeavesExecutionPlansClosed() throws Exception {
        GravityGPU simulation = new GravityGPU();
        setField(simulation, "simulationPlanReady", true);

        simulation.stop();

        assertNull(field(simulation, "executionPlan"));
        assertNull(field(simulation, "trailProjectionPlan"));
        assertEquals(false, field(simulation, "simulationPlanReady"));
    }

    @Test
    void selectingAnAlternateRuntimeDeviceRebuildsAndWarmsBothPlans() throws Exception {
        assumeConfiguredTornadoRuntime();
        assumeTrue(Boolean.getBoolean(DEVICE_SWITCH_TEST_PROPERTY),
                "enable with -D" + DEVICE_SWITCH_TEST_PROPERTY + "=true on a configured TornadoVM runtime");

        GravityGPU simulation = new GravityGPU();
        ComboBox<TornadoDeviceChoice> combo = createDeviceCombo(simulation);
        TornadoDeviceChoice initial = combo.getValue();
        TornadoDeviceChoice alternate = combo.getItems().stream()
                .filter(choice -> !choice.equals(initial))
                .findFirst()
                .orElse(null);
        assumeTrue(alternate != null, "an alternate TornadoVM device is not available");

        onFxThread(() -> {
            combo.setValue(alternate);
            return null;
        });

        assertEquals(alternate, combo.getValue());
        assertEquals(alternate, field(simulation, "selectedDeviceChoice"));
        TornadoDevice selectedDevice = field(simulation, "selectedTornadoDevice");
        assertNotNull(selectedDevice, "the alternate device must resolve through the TornadoVM API");
        assertFalse(selectedDevice.getDeviceName().isBlank());
        assertNotNull(field(simulation, "executionPlan"), "the warmed simulation plan must be recreated");
        assertNotNull(field(simulation, "trailProjectionPlan"), "the warmed trail plan must be recreated");
        assertEquals(true, field(simulation, "simulationPlanReady"));
        assertEquals(false, field(simulation, "simulationPlanDirty"));

        closePlans(simulation);
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<TornadoDeviceChoice> createDeviceCombo(GravityGPU simulation) throws Exception {
        return onFxThread(() -> {
            Method method = GravityGPU.class.getDeclaredMethod("createDeviceCombo", javafx.stage.Stage.class);
            method.setAccessible(true);
            return (ComboBox<TornadoDeviceChoice>) method.invoke(simulation, new Object[]{null});
        });
    }

    private static void updateCell(ListCell<TornadoDeviceChoice> cell, TornadoDeviceChoice choice, boolean empty)
            throws Exception {
        onFxThread(() -> {
            Method method = cell.getClass().getDeclaredMethod("updateItem", TornadoDeviceChoice.class, boolean.class);
            method.setAccessible(true);
            method.invoke(cell, choice, empty);
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private static ListCell<TornadoDeviceChoice> deviceListCell(GravityGPU simulation) throws Exception {
        return onFxThread(() -> {
            Method method = GravityGPU.class.getDeclaredMethod("tornadoDeviceListCell");
            method.setAccessible(true);
            return (ListCell<TornadoDeviceChoice>) method.invoke(simulation);
        });
    }

    private static void assumeConfiguredTornadoRuntime() {
        assumeTrue(System.getProperty("tornado.load.runtime.implementation") != null,
                "requires the installed TornadoVM @tornado-argfile");
    }

    private static void closePlans(GravityGPU simulation) throws Exception {
        Method method = GravityGPU.class.getDeclaredMethod("closeExecutionPlan");
        method.setAccessible(true);
        method.invoke(simulation);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(GravityGPU simulation, String name) throws Exception {
        Field field = GravityGPU.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(simulation);
    }

    private static void setField(GravityGPU simulation, String name, Object value) throws Exception {
        Field field = GravityGPU.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(simulation, value);
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
        assertTrue(completed.await(60, TimeUnit.SECONDS), "JavaFX action did not complete");
        if (failure.get() != null) {
            if (failure.get() instanceof Exception exception) {
                throw exception;
            }
            throw new AssertionError(failure.get());
        }
        return result.get();
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
