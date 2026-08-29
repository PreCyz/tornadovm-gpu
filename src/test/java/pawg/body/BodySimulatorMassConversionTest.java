package pawg.body;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodySimulatorMassConversionTest {

    private static final double SI_LIGHT_SPEED = 299_792_458.0;
    private static final double SIMULATION_LIGHT_SPEED = 160.0;
    private static final double SIMULATION_GRAVITY = 100.0;
    private static final double SI_GRAVITATIONAL_CONSTANT = 6.67430e-11;
    private static final double SI_SOLAR_MASS_KILOGRAMS = 1.98847e30;

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
    }

    @Test
    void conversionSeamsAreReciprocalForRepresentativeMasses() {
        double[] simulationMasses = {0.0, 1.0, 1_000_000_000_000.0};
        for (double simulationMass : simulationMasses) {
            double solarMasses = BodySimulator.simulationMassUnitsToSolarMasses(simulationMass);
            double roundTrip = BodySimulator.solarMassesToSimulationMassUnits(solarMasses);
            assertEquals(simulationMass, roundTrip, Math.max(1.0, simulationMass) * 1.0e-12,
                    "mu -> M☉ -> mu must preserve " + simulationMass);
        }

        double[] solarMasses = {0.0, 1.0, 1_000_000_000_000.0};
        for (double solarMass : solarMasses) {
            double simulationMass = BodySimulator.solarMassesToSimulationMassUnits(solarMass);
            double roundTrip = BodySimulator.simulationMassUnitsToSolarMasses(simulationMass);
            assertEquals(solarMass, roundTrip, Math.max(1.0, solarMass) * 1.0e-12,
                    "M☉ -> mu -> M☉ must preserve " + solarMass);
        }
    }

    @Test
    void oneMassUnitUsesTheDocumentedCalibrationConstants() {
        double metersPerDistanceUnit = SI_LIGHT_SPEED / SIMULATION_LIGHT_SPEED;
        double kilogramsPerMassUnit = SIMULATION_GRAVITY
                * metersPerDistanceUnit * metersPerDistanceUnit * metersPerDistanceUnit
                / SI_GRAVITATIONAL_CONSTANT;
        double expectedSolarMassesPerMassUnit = kilogramsPerMassUnit / SI_SOLAR_MASS_KILOGRAMS;

        assertEquals(expectedSolarMassesPerMassUnit,
                BodySimulator.simulationMassUnitsToSolarMasses(1.0),
                expectedSolarMassesPerMassUnit * 1.0e-12);
        assertEquals(1.0 / expectedSolarMassesPerMassUnit,
                BodySimulator.solarMassesToSimulationMassUnits(1.0),
                1.0e-12);
    }

    @Test
    void dashboardShowsBidirectionalCalibrationAndSolarEquivalentForEveryBody() throws Exception {
        BodySimulator simulator = onFxThread(BodySimulator::new);
        float[] bodyMasses = {0.0f, 1.0f, 1_000_000.0f};
        String[] bodyNames = {"Zero", "One", "Large"};

        List<String> dashboardText = onFxThread(() -> {
            setField(simulator, "bodyCount", bodyMasses.length);
            FloatArray mass = floatArray(simulator, "mass");
            FloatArray posX = floatArray(simulator, "posX");
            String[] names = (String[]) field(simulator, "names");
            Color[] colors = (Color[]) field(simulator, "colors");
            for (int i = 0; i < bodyMasses.length; i++) {
                mass.set(i, bodyMasses[i]);
                posX.set(i, i);
                names[i] = bodyNames[i];
                colors[i] = Color.WHITE;
            }

            invoke(simulator, "updateDashboard");
            VBox dashboard = (VBox) field(simulator, "dashboard");
            return dashboard.getChildren().stream()
                    .filter(Label.class::isInstance)
                    .map(Label.class::cast)
                    .map(Label::getText)
                    .toList();
        });

        String unitCalibration = dashboardText.stream()
                .filter(text -> text.startsWith("Assuming 1 simulation second"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("unit calibration explanation was not displayed"));
        double solarMassesPerMassUnit = BodySimulator.simulationMassUnitsToSolarMasses(1.0);
        double massUnitsPerSolarMass = BodySimulator.solarMassesToSimulationMassUnits(1.0);
        assertTrue(unitCalibration.contains("1 mu"));
        assertTrue(unitCalibration.contains(String.format("%.6g solar masses (M☉)", solarMassesPerMassUnit)));
        assertTrue(unitCalibration.contains("1 solar mass (M☉)"));
        assertTrue(unitCalibration.contains(String.format("%.6g mu", massUnitsPerSolarMass)));

        for (int i = 0; i < bodyMasses.length; i++) {
            int bodyIndex = i;
            String bodyLine = dashboardText.stream()
                    .filter(text -> text.startsWith(bodyNames[bodyIndex] + "  "))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("dashboard line missing for " + bodyNames[bodyIndex]));
            assertTrue(bodyLine.contains(String.format("M %.6g mu", bodyMasses[i])));
            assertTrue(bodyLine.contains(String.format("(%.6g M☉)",
                    BodySimulator.simulationMassUnitsToSolarMasses(bodyMasses[i]))));
        }
    }

    private static FloatArray floatArray(BodySimulator simulator, String name) throws Exception {
        return (FloatArray) field(simulator, name);
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

    private static void invoke(BodySimulator simulator, String name) throws Exception {
        Method method = BodySimulator.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(simulator);
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
        assertTrue(completed.await(10, TimeUnit.SECONDS), "JavaFX operation timed out");
        if (failure.get() != null) {
            throw new AssertionError("JavaFX operation failed", failure.get());
        }
        return result.get();
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
