package pawg.body;

import javafx.application.Platform;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import javafx.util.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BodySimulatorRuntimeContinuityTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @Test
    void addingABodyWhileRunningKeepsItDrawableAndReadyForTheNextUpload() throws Exception {
        onFx(() -> {
            BodySimulator simulator = simulatorWithCanvas();
            set(simulator, "running", true);
            set(simulator, "planDirty", false);
            invoke(simulator, "addBody");

            assertTrue((boolean) get(simulator, "running"));
            assertEquals(!Boolean.getBoolean("pawg.body.cpu"), (boolean) get(simulator, "planDirty"),
                    "only the GPU path must invalidate device-resident state after adding a running body");
            assertEquals(1, (int) get(simulator, "bodyCount"));
            assertEquals(1, ((IntArray) get(simulator, "active")).get(0));
            assertNotNull(((Color[]) get(simulator, "colors"))[0], "a new body must have a visible sphere color");
            assertTrue(((FloatArray) get(simulator, "mass")).get(0) > 0.0f);
            assertEquals(0.0f, array(simulator, "accX").get(0));
            assertEquals(0.0f, array(simulator, "accY").get(0));
            assertEquals(0.0f, array(simulator, "accZ").get(0));
            assertFalse((boolean) get(simulator, "gridGeometryDirty"),
                    "the immediate draw must rebuild the grid for the inserted body's current state");
            assertTrue((int) get(simulator, "gridPointCount") > 0);
            return null;
        });
    }

    @Test
    void startReusesAPreparedExecutionPlan() throws Exception {
        onFx(() -> {
            BodySimulator simulator = simulatorWithCanvas();
            set(simulator, "bodyCount", 1);
            ((IntArray) get(simulator, "state")).set(0, 1);
            ((IntArray) get(simulator, "active")).set(0, 1);
            ((FloatArray) get(simulator, "mass")).set(0, 10.0f);
            set(simulator, "planDirty", false);

            invoke(simulator, "startSimulation");

            assertTrue((boolean) get(simulator, "running"));
            assertFalse((boolean) get(simulator, "planDirty"),
                    "Start must reuse a prepared GPU plan instead of forcing first-frame rebuild");
            return null;
        });
    }

    @Test
    void stoppedWarmupLeavesAReusablePlanForTheNextStart() throws Exception {
        assumeTrue(Boolean.getBoolean("bodygpu.test.gpu"),
                "Set -Dbodygpu.test.gpu=true to run real-device stopped-state warm-up verification");
        onFx(() -> {
            BodySimulator simulator = simulatorWithCanvas();
            set(simulator, "bodyCount", 1);
            ((IntArray) get(simulator, "state")).set(0, 1);
            ((IntArray) get(simulator, "active")).set(0, 1);
            ((FloatArray) get(simulator, "mass")).set(0, 10.0f);
            ((FloatArray) get(simulator, "params")).set(0, 1.0f);
            ((FloatArray) get(simulator, "params")).set(1, 0.01f);
            ((FloatArray) get(simulator, "params")).set(2, 0.001f);
            set(simulator, "selectedDevice", TornadoExecutionPlan.getDevice(0, 0));

            invoke(simulator, "runStoppedWarmup");

            assertTrue((boolean) get(simulator, "gpuWarmupReady"));
            assertFalse((boolean) get(simulator, "planDirty"),
                    "a completed stopped-state warm-up must leave the plan reusable by Start");
            assertNotNull(get(simulator, "executionPlan"),
                    "a completed stopped-state warm-up must retain the prepared plan");
            return null;
        });
    }

    @Test
    void runningGpuCollisionSwitchesToCpuBridgeAndKeepsMergedStateMoving() throws Exception {
        assumeTrue(Boolean.getBoolean("bodygpu.test.gpu"),
                "Set -Dbodygpu.test.gpu=true to run real-device post-merge bridge verification");
        onFx(() -> {
            BodySimulator simulator = simulatorWithCanvas();
            set(simulator, "bodyCount", 2);
            ((IntArray) get(simulator, "state")).set(0, 2);
            ((IntArray) get(simulator, "active")).set(0, 1);
            ((IntArray) get(simulator, "active")).set(1, 1);
            ((FloatArray) get(simulator, "params")).set(0, 1.0f);
            ((FloatArray) get(simulator, "params")).set(1, 0.01f);
            ((FloatArray) get(simulator, "params")).set(2, 0.001f);
            ((FloatArray) get(simulator, "params")).set(3, 0.5f);
            ((FloatArray) get(simulator, "mass")).set(0, 10.0f);
            ((FloatArray) get(simulator, "mass")).set(1, 12.0f);
            array(simulator, "velX").set(0, 2.0f);
            array(simulator, "velX").set(1, 2.0f);
            ((Color[]) get(simulator, "colors"))[0] = Color.RED;
            ((Color[]) get(simulator, "colors"))[1] = Color.BLUE;
            set(simulator, "selectedDevice", TornadoExecutionPlan.getDevice(0, 0));

            invoke(simulator, "runStoppedWarmup");
            set(simulator, "running", true);
            set(simulator, "planDirty", false);
            int originalStep = (int) get(simulator, "simulationStepCounter");

            invoke(simulator, "resolveBodyCollisions");

            assertEquals(1, (int) get(simulator, "bodyCount"));
            assertTrue((boolean) get(simulator, "gpuPostMergeCpuBridgeActive"));
            assertFalse((boolean) get(simulator, "gpuPostMergeWarmupPending"));
            assertFalse((boolean) get(simulator, "gpuPostMergeWarmupInProgress"));
            assertTrue((boolean) get(simulator, "planDirty"));

            assertTrue((boolean) invoke(simulator, "stepPostMergeCpuBridge", System.nanoTime()));

            assertFalse((boolean) get(simulator, "gpuPostMergeWarmupPending"));
            assertFalse((boolean) get(simulator, "gpuPostMergeWarmupInProgress"));
            assertTrue((boolean) get(simulator, "gpuPostMergeCpuBridgeActive"));
            assertTrue((boolean) get(simulator, "planDirty"));
            assertEquals(originalStep + 1, (int) get(simulator, "simulationStepCounter"),
                    "post-merge bridge must keep simulation time advancing");
            assertTrue(array(simulator, "posX").get(0) > 0.0f,
                    "merged body must move immediately on the CPU bridge instead of waiting for GPU recovery");
            return null;
        });
    }

    @Test
    void cpuFallbackKeepsMergedSimulationOnTheCpuWithoutQueuingGpuWarmup() throws Exception {
        assumeTrue(Boolean.getBoolean("pawg.body.cpu"),
                "Run with -Dpawg.body.cpu=true to verify the CPU fallback path");
        onFx(() -> {
            BodySimulator simulator = simulatorWithCanvas();
            set(simulator, "bodyCount", 2);
            set(simulator, "running", true);
            ((IntArray) get(simulator, "state")).set(0, 2);
            ((FloatArray) get(simulator, "params")).set(1, 0.5f);
            for (int i = 0; i < 2; i++) {
                ((IntArray) get(simulator, "active")).set(i, 1);
                ((FloatArray) get(simulator, "mass")).set(i, 10.0f);
                array(simulator, "velX").set(i, 2.0f);
                ((Color[]) get(simulator, "colors"))[i] = i == 0 ? Color.RED : Color.BLUE;
            }

            invoke(simulator, "resolveBodyCollisions");
            invoke(simulator, "stepSimulation");

            assertEquals(1, (int) get(simulator, "bodyCount"));
            assertEquals(1.0f, array(simulator, "posX").get(0), 1.0e-6f);
            assertEquals(1, (int) get(simulator, "simulationStepCounter"));
            assertFalse((boolean) get(simulator, "gpuPostMergeWarmupPending"));
            assertFalse((boolean) get(simulator, "gpuPostMergeWarmupInProgress"));
            assertNull(get(simulator, "executionPlan"));
            return null;
        });
    }

    @Test
    void snapshotCollisionCallbackReturnsWhilePostMergeBridgeOwnsLiveMotion() throws Exception {
        BodySimulator simulator = onFx(() -> {
            BodySimulator configured = simulatorWithCanvas();
            set(configured, "bodyCount", 2);
            ((IntArray) get(configured, "state")).set(0, 2);
            for (int i = 0; i < 2; i++) {
                ((IntArray) get(configured, "active")).set(i, 1);
                ((FloatArray) get(configured, "mass")).set(i, 10.0f);
                ((Color[]) get(configured, "colors"))[i] = i == 0 ? Color.RED : Color.BLUE;
            }
            set(configured, "gpuPostMergeCpuBridgeActive", true);
            return configured;
        });
        ReentrantLock simulationLock = (ReentrantLock) get(simulator, "simulationLock");
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        Thread warmup = new Thread(() -> {
            simulationLock.lock();
            try {
                lockHeld.countDown();
                try {
                    releaseLock.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                simulationLock.unlock();
            }
        });
        warmup.start();
        assertTrue(lockHeld.await(1, TimeUnit.SECONDS), "warm-up worker did not acquire the simulation lock");
        try {
            onFx(() -> {
                invoke(simulator, "resolveBodyCollisionsFromSnapshot");
                assertEquals(2, (int) get(simulator, "bodyCount"),
                        "the frame callback must defer collision processing while bridge mode owns live motion");
                return null;
            });
        } finally {
            releaseLock.countDown();
            warmup.join(TimeUnit.SECONDS.toMillis(1));
        }
        assertFalse(warmup.isAlive(), "warm-up lock holder did not finish");
    }

    @Test
    void snapshotCollisionCallbackSkipsInsteadOfBlockingWhenWorkerOwnsState() throws Exception {
        BodySimulator simulator = onFx(() -> {
            BodySimulator configured = simulatorWithCanvas();
            set(configured, "bodyCount", 2);
            set(configured, "running", true);
            ((IntArray) get(configured, "state")).set(0, 2);
            ((FloatArray) get(configured, "params")).set(3, 0.5f);
            for (int i = 0; i < 2; i++) {
                ((IntArray) get(configured, "active")).set(i, 1);
                ((FloatArray) get(configured, "mass")).set(i, 10.0f);
                ((Color[]) get(configured, "colors"))[i] = i == 0 ? Color.RED : Color.BLUE;
            }
            return configured;
        });
        ReentrantLock simulationLock = (ReentrantLock) get(simulator, "simulationLock");
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            simulationLock.lock();
            try {
                lockHeld.countDown();
                try {
                    releaseLock.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                simulationLock.unlock();
            }
        });
        worker.start();
        assertTrue(lockHeld.await(1, TimeUnit.SECONDS), "worker did not acquire the simulation lock");
        long startedAtNanos = System.nanoTime();
        try {
            onFx(() -> {
                invoke(simulator, "resolveBodyCollisionsFromSnapshot");
                assertEquals(2, (int) get(simulator, "bodyCount"),
                        "the frame callback must skip collision processing while the worker owns state");
                assertFalse((boolean) get(simulator, "gpuPostMergeCpuBridgeActive"));
                return null;
            });
        } finally {
            releaseLock.countDown();
            worker.join(TimeUnit.SECONDS.toMillis(1));
        }
        assertTrue(System.nanoTime() - startedAtNanos < TimeUnit.MILLISECONDS.toNanos(500),
                "collision callback should return within the frame budget instead of waiting for the worker");
        assertFalse(worker.isAlive(), "worker lock holder did not finish");
    }

    @Test
    void gpuWorkerDoesNotAdvanceStateWhilePostMergeCpuBridgeOwnsMotion() throws Exception {
        BodySimulator simulator = new BodySimulator();
        set(simulator, "bodyCount", 1);
        set(simulator, "running", true);
        set(simulator, "gpuPostMergeCpuBridgeActive", true);
        ((IntArray) get(simulator, "state")).set(0, 1);
        ((IntArray) get(simulator, "active")).set(0, 1);
        ((FloatArray) get(simulator, "mass")).set(0, 10.0f);

        try {
            invoke(simulator, "startSimulationWorker");
            Thread.sleep(60L);

            assertEquals(0, (int) get(simulator, "simulationStepCounter"),
                    "the GPU worker must not advance the merged state while the CPU bridge owns motion");
        } finally {
            invoke(simulator, "stopSimulationWorker");
        }
    }

    @Test
    void renderStateInterpolatesBetweenPublishedSnapshots() throws Exception {
        onFx(() -> {
            BodySimulator simulator = simulatorWithCanvas();
            set(simulator, "bodyCount", 1);
            ((IntArray) get(simulator, "state")).set(0, 1);
            ((IntArray) get(simulator, "active")).set(0, 1);
            array(simulator, "posX").set(0, 0.0f);
            set(simulator, "simulationStepCounter", 1);
            invoke(simulator, "publishRenderSnapshot", 100_000_000L);

            array(simulator, "posX").set(0, 10.0f);
            set(simulator, "simulationStepCounter", 2);
            invoke(simulator, "publishRenderSnapshot", 130_000_000L);

            assertTrue((boolean) invoke(simulator, "updateInterpolatedRenderState", 130_000_000L));
            assertEquals(5.0f, ((float[]) get(simulator, "renderPosX"))[0], 1.0e-6f);
            assertEquals(1, (int) get(simulator, "renderBodyCount"));
            return null;
        });
    }

    @Test
    void renderStateRemainsBoundedBySnapshotsAcrossInterpolationBoundaries() throws Exception {
        onFx(() -> {
            BodySimulator simulator = simulatorWithCanvas();
            set(simulator, "bodyCount", 1);
            ((IntArray) get(simulator, "state")).set(0, 1);
            ((IntArray) get(simulator, "active")).set(0, 1);
            array(simulator, "posX").set(0, 2.0f);
            set(simulator, "simulationStepCounter", 1);
            invoke(simulator, "publishRenderSnapshot", 100_000_000L);

            set(simulator, "bodyCount", 2);
            ((IntArray) get(simulator, "state")).set(0, 2);
            ((IntArray) get(simulator, "active")).set(1, 1);
            array(simulator, "posX").set(0, 10.0f);
            array(simulator, "posX").set(1, 20.0f);
            set(simulator, "simulationStepCounter", 2);
            invoke(simulator, "publishRenderSnapshot", 130_000_000L);

            assertTrue((boolean) invoke(simulator, "updateInterpolatedRenderState", 100_000_000L));
            assertEquals(2.0f, ((float[]) get(simulator, "renderPosX"))[0], 1.0e-6f);

            assertFalse((boolean) invoke(simulator, "updateInterpolatedRenderState", 200_000_000L));
            assertEquals(10.0f, ((float[]) get(simulator, "renderPosX"))[0], 1.0e-6f);
            assertEquals(20.0f, ((float[]) get(simulator, "renderPosX"))[1], 1.0e-6f);
            assertEquals(2, (int) get(simulator, "renderBodyCount"));
            return null;
        });
    }

    @Test
    void collisionKeepsSimulationRunningAndAllowsAColoredReplacementWithoutPlanRebuild() throws Exception {
        onFx(() -> {
            BodySimulator simulator = simulatorWithCanvas();
            set(simulator, "bodyCount", 2);
            set(simulator, "running", true);
            set(simulator, "planDirty", false);
            for (int i = 0; i < 2; i++) {
                array(simulator, "mass").set(i, 10.0f + i);
                ((IntArray) get(simulator, "active")).set(i, 1);
                ((Color[]) get(simulator, "colors"))[i] = i == 0 ? Color.RED : Color.BLUE;
            }
            ((IntArray) get(simulator, "state")).set(0, 2);

            invoke(simulator, "resolveBodyCollisions");

            assertEquals(1, (int) get(simulator, "bodyCount"));
            assertTrue((boolean) get(simulator, "running"));
            assertTrue((boolean) get(simulator, "planDirty"));
            assertTrue((boolean) get(simulator, "gridGeometryDirty"));
            invoke(simulator, "addBody");
            assertEquals(2, (int) get(simulator, "bodyCount"));
            assertNotNull(((Color[]) get(simulator, "colors"))[1], "a reused collision slot must render a sphere");
            return null;
        });
    }

    @Test
    void collisionDefersExpensiveVisualRefreshWhenGridCacheExists() throws Exception {
        onFx(() -> {
            BodySimulator simulator = simulatorWithCanvas();
            set(simulator, "bodyCount", 2);
            set(simulator, "running", true);
            set(simulator, "planDirty", false);
            set(simulator, "gridGeometryDirty", false);
            set(simulator, "cachedGridImage", new WritableImage(8, 8));
            set(simulator, "cachedGridImageDirty", false);
            for (int i = 0; i < 2; i++) {
                array(simulator, "mass").set(i, 10.0f);
                ((IntArray) get(simulator, "active")).set(i, 1);
                ((Color[]) get(simulator, "colors"))[i] = i == 0 ? Color.RED : Color.BLUE;
            }
            ((IntArray) get(simulator, "state")).set(0, 2);

            invoke(simulator, "resolveBodyCollisions");

            assertEquals(1, (int) get(simulator, "bodyCount"));
            assertTrue((boolean) get(simulator, "running"));
            assertTrue((boolean) get(simulator, "planDirty"));
            assertTrue((boolean) get(simulator, "gridCacheRefreshPending"),
                    "a merge should keep the cached grid for the current frame");
            assertFalse((boolean) get(simulator, "gridGeometryDirty"),
                    "the collision frame must not rebuild the grid immediately when a cache exists");
            assertTrue((boolean) get(simulator, "editorRebuildPending"),
                    "editor node rebuilding should be scheduled after the collision frame");

            invoke(simulator, "consumeDeferredGridRefresh");
            assertTrue((boolean) get(simulator, "gridGeometryDirty"));
            assertTrue((boolean) get(simulator, "cachedGridImageDirty"));
            return null;
        });
    }

    @Test
    void liveCollisionDoesNotRebuildExistingEditorNodes() throws Exception {
        AtomicReference<VBox> firstEditor = new AtomicReference<>();
        AtomicReference<VBox> editorListReference = new AtomicReference<>();
        onFx(() -> {
            BodySimulator simulator = simulatorWithCanvas();
            VBox editorList = (VBox) get(simulator, "editorList");
            set(simulator, "bodyCount", 2);
            set(simulator, "running", true);
            ((IntArray) get(simulator, "state")).set(0, 2);
            String[] names = (String[]) get(simulator, "names");
            for (int i = 0; i < 2; i++) {
                ((IntArray) get(simulator, "active")).set(i, 1);
                ((FloatArray) get(simulator, "mass")).set(i, 10.0f);
                names[i] = "Body " + (i + 1);
                ((Color[]) get(simulator, "colors"))[i] = i == 0 ? Color.RED : Color.BLUE;
                editorList.getChildren().add((VBox) invoke(simulator, "createEditor", i));
            }
            firstEditor.set((VBox) editorList.getChildren().getFirst());
            editorListReference.set(editorList);

            invoke(simulator, "resolveBodyCollisions");

            assertEquals(1, (int) get(simulator, "bodyCount"));
            assertTrue((boolean) get(simulator, "editorRebuildPending"));
            return null;
        });
        onFx(() -> {
            VBox editorList = editorListReference.get();
            VBox survivorEditor = firstEditor.get();

            assertEquals(2, editorList.getChildren().size(),
                    "a live collision should avoid removing editor nodes during the running frame");
            assertTrue(editorList.getChildren().contains(survivorEditor),
                    "the survivor editor node must be updated in place instead of fully rebuilt");
            assertTrue(survivorEditor.isManaged());
            assertTrue(survivorEditor.isVisible());
            assertEquals("Merged Body 1", ((Label) survivorEditor.getChildren().getFirst()).getText());
            VBox absorbedEditor = (VBox) editorList.getChildren().get(1);
            assertFalse(absorbedEditor.isManaged(), "absorbed body editor row must disappear from layout");
            assertFalse(absorbedEditor.isVisible(), "absorbed body editor row must disappear visually");
            return null;
        });
    }

    @Test
    void liveCollisionDoesNotCreateEditorRowsDuringRunningSync() throws Exception {
        onFx(() -> {
            BodySimulator simulator = simulatorWithCanvas();
            set(simulator, "bodyCount", 1);
            set(simulator, "running", true);

            invoke(simulator, "syncLiveEditorsAfterCollision");

            VBox editorList = (VBox) get(simulator, "editorList");
            assertEquals(0, editorList.getChildren().size(),
                    "live collision sync must not instantiate editor controls while animation is running");
            assertFalse((boolean) get(simulator, "editorRebuildPending"));
            return null;
        });
    }

    @Test
    void dashboardShowsMergedBodyAfterLiveCollision() throws Exception {
        onFx(() -> {
            BodySimulator simulator = simulatorWithCanvas();
            set(simulator, "bodyCount", 2);
            set(simulator, "running", true);
            set(simulator, "planDirty", false);
            ((IntArray) get(simulator, "state")).set(0, 2);
            String[] names = (String[]) get(simulator, "names");
            for (int i = 0; i < 2; i++) {
                ((IntArray) get(simulator, "active")).set(i, 1);
                ((FloatArray) get(simulator, "mass")).set(i, 10.0f);
                names[i] = "Body " + (i + 1);
                ((Color[]) get(simulator, "colors"))[i] = i == 0 ? Color.RED : Color.BLUE;
            }

            invoke(simulator, "resolveBodyCollisions");
            invoke(simulator, "copyCurrentStateToRenderState");
            invoke(simulator, "updateDashboard");

            VBox dynamicContent = (VBox) get(simulator, "dashboardDynamicContent");
            assertEquals(1, dynamicContent.getChildren().size(),
                    "dashboard must stop showing absorbed bodies after a live merge");
            String rowText = ((Label) dynamicContent.getChildren().getFirst()).getText();
            assertTrue(rowText.startsWith("Merged Body 1  "),
                    "dashboard must show the new merged body row instead of initial body rows");
            return null;
        });
    }

    @Test
    void visualBodyCountDoesNotExposeClearedCollisionSlots() throws Exception {
        onFx(() -> {
            BodySimulator simulator = simulatorWithCanvas();
            set(simulator, "bodyCount", 1);
            set(simulator, "renderBodyCount", 2);

            assertEquals(1, invoke(simulator, "visualBodyCount"),
                    "stale snapshots must not draw body slots cleared by collision compaction");
            return null;
        });
    }

    @Test
    void elapsedClockAccumulatesOnlyWhileResumedAndFormatsWholeSeconds() throws Exception {
        onFx(() -> {
            BodySimulator simulator = new BodySimulator();
            set(simulator, "elapsedTimeLabel", new Label());
            invoke(simulator, "resumeElapsedClock");
            invoke(simulator, "updateElapsedClock", 1_000_000_000L);
            invoke(simulator, "updateElapsedClock", 3_500_000_000L);
            invoke(simulator, "pauseElapsedClock");
            assertEquals(2_500_000_000L, (long) get(simulator, "elapsedAccumulatedNanos"));
            invoke(simulator, "updateElapsedClock", 8_000_000_000L);
            assertEquals(2_500_000_000L, (long) get(simulator, "elapsedAccumulatedNanos"));
            invoke(simulator, "resetElapsedClock");
            assertEquals(0L, (long) get(simulator, "elapsedAccumulatedNanos"));
            assertEquals("00:00:00", BodySimulator.formatElapsedNanos(-1L));
            assertEquals("01:01:01", BodySimulator.formatElapsedNanos(3_661_999_999_999L));
            return null;
        });
    }

    @Test
    void stopCancelsDeferredWarmupAndPreventsStaleWorkersFromRestartingIt() throws Exception {
        onFx(() -> {
            BodySimulator simulator = simulatorWithCanvas();
            PauseTransition debounce = new PauseTransition(Duration.seconds(5));
            debounce.play();
            set(simulator, "gpuWarmupDebounceTimer", debounce);
            set(simulator, "warmupGeneration", 7);

            simulator.stop();

            assertEquals(Animation.Status.STOPPED, debounce.getStatus());
            assertTrue((boolean) get(simulator, "stopped"));
            assertEquals(8, (int) get(simulator, "warmupGeneration"));
            return null;
        });
    }

    private static BodySimulator simulatorWithCanvas() {
        BodySimulator simulator = new BodySimulator();
        simulator.configureCanvasInteractions(new Canvas(800.0, 600.0));
        try {
            Object[] trails = (Object[]) get(simulator, "trails");
            Object[] fullTracks = (Object[]) get(simulator, "fullTracks");
            for (int i = 0; i < trails.length; i++) {
                trails[i] = new ArrayDeque<>();
                fullTracks[i] = new ArrayList<>();
            }
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
        return simulator;
    }

    private static FloatArray array(BodySimulator simulator, String name) throws Exception {
        return (FloatArray) get(simulator, name);
    }

    private static Object get(BodySimulator simulator, String name) throws Exception {
        Field field = BodySimulator.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(simulator);
    }

    private static void set(BodySimulator simulator, String name, Object value) throws Exception {
        Field field = BodySimulator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(simulator, value);
    }

    private static Object invoke(BodySimulator simulator, String name, Object... arguments) throws Exception {
        Class<?>[] types = new Class<?>[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] instanceof Integer) {
                types[i] = int.class;
            } else if (arguments[i] instanceof Long) {
                types[i] = long.class;
            } else {
                types[i] = arguments[i].getClass();
            }
        }
        Method method = BodySimulator.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(simulator, arguments);
    }

    private static <T> T onFx(ThrowingSupplier<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) return action.get();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(action.get());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
        return result.get();
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
