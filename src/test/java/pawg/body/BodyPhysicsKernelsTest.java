package pawg.body;

import org.junit.jupiter.api.Test;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BodyPhysicsKernelsTest {

    private static final String GPU_TEST_PROPERTY = "bodygpu.test.gpu";
    private static final float ABSOLUTE_TOLERANCE = 5.0e-5f;
    private static final float RELATIVE_TOLERANCE = 2.0e-5f;

    @Test
    void oneBodyMovesAtConstantVelocity() {
        SimulationState state = new SimulationState(1, 100.0f, 0.015f, 25.0f);
        state.setBody(0, 1.0f, 2.0f, 3.0f, 0.5f, -0.25f, 1.0f, 7.0f, 1);

        BodyPhysicsKernels.simulateOnCpu(state.px, state.py, state.pz,
                state.vx, state.vy, state.vz,
                state.ax, state.ay, state.az,
                state.nextAx, state.nextAy, state.nextAz,
                state.mass, state.active, state.params, state.count);

        assertEquals(1.0075f, state.px.get(0), 1.0e-6f);
        assertEquals(1.99625f, state.py.get(0), 1.0e-6f);
        assertEquals(3.015f, state.pz.get(0), 1.0e-6f);
        assertEquals(0.5f, state.vx.get(0));
        assertEquals(-0.25f, state.vy.get(0));
        assertEquals(1.0f, state.vz.get(0));
        assertEquals(0.0f, state.ax.get(0));
        assertEquals(0.0f, state.ay.get(0));
        assertEquals(0.0f, state.az.get(0));
    }

    @Test
    void twoBodyAccelerationMatchesSoftenedInverseSquareLaw() {
        SimulationState state = new SimulationState(2, 100.0f, 0.015f, 25.0f);
        state.setBody(0, -1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 2.0f, 1);
        state.setBody(1, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 3.0f, 1);

        BodyPhysicsKernels.computeAcceleration(state.px, state.py, state.pz,
                state.ax, state.ay, state.az, state.mass, state.active, state.params, state.count);

        float distanceSquared = 29.0f;
        float distance = (float) Math.sqrt(distanceSquared);
        assertEquals(100.0f * 3.0f * 2.0f / (distanceSquared * distance), state.ax.get(0), 1.0e-6f);
        assertEquals(-100.0f * 2.0f * 2.0f / (distanceSquared * distance), state.ax.get(1), 1.0e-6f);
        assertEquals(0.0f, state.ay.get(0));
        assertEquals(0.0f, state.az.get(1));
    }

    @Test
    void symmetricThreeBodySystemLeavesCenterAccelerationAtZero() {
        SimulationState state = new SimulationState(3, 10.0f, 0.01f, 0.25f);
        state.setBody(0, -1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1);
        state.setBody(1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1);
        state.setBody(2, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1);

        BodyPhysicsKernels.computeAcceleration(state.px, state.py, state.pz,
                state.ax, state.ay, state.az, state.mass, state.active, state.params, state.count);

        assertEquals(0.0f, state.ax.get(1), 1.0e-6f);
        assertEquals(-state.ax.get(0), state.ax.get(2), 1.0e-6f);
        assertTrue(state.ax.get(0) > 0.0f);
    }

    @Test
    void inactiveAndZeroMassBodiesStayFiniteAndDoNotExertGravity() {
        SimulationState state = new SimulationState(4, 100.0f, 0.015f, 0.0f);
        state.setBody(0, 0.0f, 0.0f, 0.0f, 2.0f, 3.0f, 4.0f, 10.0f, 1);
        state.setBody(1, 0.0f, 0.0f, 0.0f, 9.0f, 9.0f, 9.0f, 20.0f, 0);
        state.setBody(2, 0.0f, 0.0f, 0.0f, -2.0f, -3.0f, -4.0f, 0.0f, 1);
        state.setBody(3, 10_000.0f, -10_000.0f, 5_000.0f, 0.0f, 0.0f, 0.0f, 1_000.0f, 1);

        BodyPhysicsKernels.simulateOnCpu(state.px, state.py, state.pz,
                state.vx, state.vy, state.vz,
                state.ax, state.ay, state.az,
                state.nextAx, state.nextAy, state.nextAz,
                state.mass, state.active, state.params, state.count);

        assertEquals(0.0f, state.px.get(1), "inactive body position must not change");
        assertEquals(9.0f, state.vx.get(1), "inactive body velocity must not change");
        assertEquals(-0.03f, state.px.get(2), 1.0e-6f, "zero-mass body follows its velocity only");
        assertEquals(0.0f, state.ax.get(2), "zero-mass body has no acceleration under the model policy");
        assertAllFinite(state);
    }

    @Test
    void seededCpuAndOpenClGpuRemainWithinContractTolerance() throws Exception {
        assumeTrue(Boolean.getBoolean(GPU_TEST_PROPERTY),
                () -> "Set -D" + GPU_TEST_PROPERTY + "=true to run real-device parity");

        assertGpuParity(randomState(16, 0x16B0D1L), 100);
        assertGpuParity(randomState(64, 0x64B0D1L), 30);
        assertGpuParity(randomState(256, 0x256B0D1L), 10);
        assertGpuParity(edgeCaseState(), 20);
    }

    @Test
    void bodySimulatorKeepsDeviceResidencyUntilExplicitInvalidation() throws Exception {
        assumeTrue(Boolean.getBoolean(GPU_TEST_PROPERTY),
                () -> "Set -D" + GPU_TEST_PROPERTY + "=true to run real-device lifecycle verification");

        BodySimulator simulator = new BodySimulator();
        FloatArray posX = floatArray(simulator, "posX");
        FloatArray velX = floatArray(simulator, "velX");
        FloatArray mass = floatArray(simulator, "mass");
        FloatArray params = floatArray(simulator, "params");
        IntArray active = intArray(simulator, "active");
        IntArray state = intArray(simulator, "state");
        params.set(0, 100.0f);
        params.set(1, 0.015f);
        params.set(2, 25.0f);
        state.set(0, 1);
        active.set(0, 1);
        mass.set(0, 1.0f);
        velX.set(0, 1.0f);
        setSelectedDevice(simulator, TornadoExecutionPlan.getDevice(0, 0));

        invoke(simulator, "stepSimulation");
        assertEquals(0.015f, posX.get(0), 1.0e-6f);

        posX.set(0, 50.0f);
        velX.set(0, 3.0f);
        invoke(simulator, "stepSimulation");
        assertEquals(0.030f, posX.get(0), 1.0e-6f,
                "FIRST_EXECUTION must keep the device-resident state between frames");

        posX.set(0, 10.0f);
        velX.set(0, 2.0f);
        invoke(simulator, "invalidateExecutionPlan");
        assertNull(field(simulator, "executionPlan"));
        assertEquals(true, field(simulator, "planDirty"));

        invoke(simulator, "stepSimulation");
        assertEquals(10.03f, posX.get(0), 1.0e-5f,
                "a rebuilt plan must import the edited host state");

        simulator.stop();
        assertNull(field(simulator, "executionPlan"));
    }

    private static void assertGpuParity(SimulationState initial, int steps) throws Exception {
        SimulationState cpu = initial.copy();
        SimulationState gpu = initial.copy();
        double initialMomentumX = momentumX(cpu);

        TaskGraph graph = new TaskGraph("body-kernel-parity-" + initial.size)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION,
                        gpu.px, gpu.py, gpu.pz, gpu.vx, gpu.vy, gpu.vz,
                        gpu.ax, gpu.ay, gpu.az, gpu.nextAx, gpu.nextAy, gpu.nextAz,
                        gpu.mass, gpu.active, gpu.params, gpu.count)
                .task("current-acceleration", BodyPhysicsKernels::computeAcceleration,
                        gpu.px, gpu.py, gpu.pz, gpu.ax, gpu.ay, gpu.az,
                        gpu.mass, gpu.active, gpu.params, gpu.count)
                .task("position-update", BodyPhysicsKernels::updatePositions,
                        gpu.px, gpu.py, gpu.pz, gpu.vx, gpu.vy, gpu.vz,
                        gpu.ax, gpu.ay, gpu.az, gpu.active, gpu.params, gpu.count)
                .task("next-acceleration-velocity-update",
                        BodyPhysicsKernels::computeNextAccelerationAndUpdateVelocity,
                        gpu.px, gpu.py, gpu.pz, gpu.vx, gpu.vy, gpu.vz,
                        gpu.ax, gpu.ay, gpu.az, gpu.nextAx, gpu.nextAy, gpu.nextAz,
                        gpu.mass, gpu.active, gpu.params, gpu.count)
                .transferToHost(DataTransferMode.EVERY_EXECUTION,
                        gpu.px, gpu.py, gpu.pz, gpu.vx, gpu.vy, gpu.vz,
                        gpu.ax, gpu.ay, gpu.az);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())
                .withDevice(TornadoExecutionPlan.getDevice(0, 0))) {
            for (int step = 0; step < steps; step++) {
                BodyPhysicsKernels.simulateOnCpu(cpu.px, cpu.py, cpu.pz,
                        cpu.vx, cpu.vy, cpu.vz, cpu.ax, cpu.ay, cpu.az,
                        cpu.nextAx, cpu.nextAy, cpu.nextAz,
                        cpu.mass, cpu.active, cpu.params, cpu.count);
                plan.execute();
            }
        }

        assertArrayClose("px", cpu.px, gpu.px, initial.size);
        assertArrayClose("py", cpu.py, gpu.py, initial.size);
        assertArrayClose("pz", cpu.pz, gpu.pz, initial.size);
        assertArrayClose("vx", cpu.vx, gpu.vx, initial.size);
        assertArrayClose("vy", cpu.vy, gpu.vy, initial.size);
        assertArrayClose("vz", cpu.vz, gpu.vz, initial.size);
        assertArrayClose("ax", cpu.ax, gpu.ax, initial.size);
        assertArrayClose("ay", cpu.ay, gpu.ay, initial.size);
        assertArrayClose("az", cpu.az, gpu.az, initial.size);
        assertAllFinite(cpu);
        assertAllFinite(gpu);

        double momentumScale = Math.max(1.0, Math.abs(initialMomentumX));
        assertEquals(initialMomentumX, momentumX(cpu), 2.0e-3 * momentumScale,
                "CPU oracle should approximately conserve x momentum");
    }

    private static SimulationState randomState(int size, long seed) {
        SimulationState state = new SimulationState(size, 2.0f, 0.001f, 0.25f);
        Random random = new Random(seed);
        for (int i = 0; i < size; i++) {
            state.setBody(i,
                    (random.nextFloat() - 0.5f) * 80.0f + i * 0.01f,
                    (random.nextFloat() - 0.5f) * 80.0f,
                    (random.nextFloat() - 0.5f) * 20.0f,
                    (random.nextFloat() - 0.5f) * 0.4f,
                    (random.nextFloat() - 0.5f) * 0.4f,
                    (random.nextFloat() - 0.5f) * 0.4f,
                    1.0f + random.nextFloat() * 20.0f,
                    1);
        }
        return state;
    }

    private static SimulationState edgeCaseState() {
        SimulationState state = new SimulationState(8, 0.25f, 0.0005f, 1.0e-3f);
        state.setBody(0, 0.0f, 0.0f, 0.0f, 0.01f, 0.02f, 0.03f, 10.0f, 1);
        state.setBody(1, 0.0f, 0.0f, 0.0f, -0.01f, -0.02f, 0.01f, 11.0f, 1);
        state.setBody(2, 1.0e4f, -1.0e4f, 5.0e3f, 0.0f, 0.0f, 0.0f, 1.0e3f, 1);
        state.setBody(3, -1.0e4f, 1.0e4f, -5.0e3f, 0.0f, 0.0f, 0.0f, 8.0e2f, 1);
        state.setBody(4, 3.0f, 4.0f, 5.0f, 9.0f, 8.0f, 7.0f, 20.0f, 0);
        state.setBody(5, -2.0f, 1.0f, 0.5f, 0.1f, -0.1f, 0.0f, 0.0f, 1);
        state.setBody(6, 12.0f, -3.0f, 4.0f, -0.2f, 0.05f, 0.1f, 0.001f, 1);
        state.setBody(7, -8.0f, 7.0f, -6.0f, 0.15f, -0.05f, -0.1f, 50.0f, 1);
        return state;
    }

    private static void assertArrayClose(String name, FloatArray expected, FloatArray actual, int size) {
        double squaredError = 0.0;
        for (int i = 0; i < size; i++) {
            float reference = expected.get(i);
            float tolerance = ABSOLUTE_TOLERANCE
                    + RELATIVE_TOLERANCE * Math.max(1.0f, Math.abs(reference));
            float error = Math.abs(reference - actual.get(i));
            squaredError += (double) error * error;
            assertTrue(error <= tolerance,
                    name + "[" + i + "] expected " + reference + " but was " + actual.get(i)
                            + " (error " + error + ", tolerance " + tolerance + ")");
        }
        double rms = Math.sqrt(squaredError / size);
        assertTrue(Double.isFinite(rms), name + " RMS error must be finite");
    }

    private static void assertAllFinite(SimulationState state) {
        FloatArray[] arrays = {state.px, state.py, state.pz, state.vx, state.vy, state.vz,
                state.ax, state.ay, state.az};
        for (FloatArray array : arrays) {
            for (int i = 0; i < state.size; i++) {
                assertTrue(Float.isFinite(array.get(i)), "state must remain finite at index " + i);
            }
        }
    }

    private static double momentumX(SimulationState state) {
        double momentum = 0.0;
        for (int i = 0; i < state.size; i++) {
            if (state.active.get(i) != 0 && state.mass.get(i) > 0.0f) {
                momentum += state.mass.get(i) * state.vx.get(i);
            }
        }
        return momentum;
    }

    private static FloatArray floatArray(BodySimulator simulator, String name) throws Exception {
        return (FloatArray) field(simulator, name);
    }

    private static IntArray intArray(BodySimulator simulator, String name) throws Exception {
        return (IntArray) field(simulator, name);
    }

    private static Object field(BodySimulator simulator, String name) throws Exception {
        Field field = BodySimulator.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(simulator);
    }

    private static void setSelectedDevice(BodySimulator simulator, Object value) throws Exception {
        Field field = BodySimulator.class.getDeclaredField("selectedDevice");
        field.setAccessible(true);
        field.set(simulator, value);
    }

    private static void invoke(BodySimulator simulator, String name) throws Exception {
        Method method = BodySimulator.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(simulator);
    }

    private static final class SimulationState {
        private final int size;
        private final FloatArray px;
        private final FloatArray py;
        private final FloatArray pz;
        private final FloatArray vx;
        private final FloatArray vy;
        private final FloatArray vz;
        private final FloatArray ax;
        private final FloatArray ay;
        private final FloatArray az;
        private final FloatArray nextAx;
        private final FloatArray nextAy;
        private final FloatArray nextAz;
        private final FloatArray mass;
        private final IntArray active;
        private final FloatArray params;
        private final IntArray count;

        private SimulationState(int size, float gravity, float dt, float softening) {
            this.size = size;
            px = new FloatArray(size);
            py = new FloatArray(size);
            pz = new FloatArray(size);
            vx = new FloatArray(size);
            vy = new FloatArray(size);
            vz = new FloatArray(size);
            ax = new FloatArray(size);
            ay = new FloatArray(size);
            az = new FloatArray(size);
            nextAx = new FloatArray(size);
            nextAy = new FloatArray(size);
            nextAz = new FloatArray(size);
            mass = new FloatArray(size);
            active = new IntArray(size);
            params = new FloatArray(3);
            count = new IntArray(1);
            params.set(0, gravity);
            params.set(1, dt);
            params.set(2, softening);
            count.set(0, size);
        }

        private void setBody(int i, float x, float y, float z,
                             float velocityX, float velocityY, float velocityZ,
                             float bodyMass, int isActive) {
            px.set(i, x);
            py.set(i, y);
            pz.set(i, z);
            vx.set(i, velocityX);
            vy.set(i, velocityY);
            vz.set(i, velocityZ);
            mass.set(i, bodyMass);
            active.set(i, isActive);
        }

        private SimulationState copy() {
            SimulationState result = new SimulationState(size, params.get(0), params.get(1), params.get(2));
            for (int i = 0; i < size; i++) {
                result.setBody(i, px.get(i), py.get(i), pz.get(i),
                        vx.get(i), vy.get(i), vz.get(i), mass.get(i), active.get(i));
                result.ax.set(i, ax.get(i));
                result.ay.set(i, ay.get(i));
                result.az.set(i, az.get(i));
                result.nextAx.set(i, nextAx.get(i));
                result.nextAy.set(i, nextAy.get(i));
                result.nextAz.set(i, nextAz.get(i));
            }
            return result;
        }
    }
}
