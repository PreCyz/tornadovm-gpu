package pawg.nbody;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GravityGpuFramePolicyTest {

    @Test
    void stableSolarSystemAvoidsLegacyProjectionPlanExecutionEveryFrame() {
        int tenSecondsAtSixtyFps = 600;

        int currentExecutions = GravityGpuFramePolicy.currentStableSolarSystemTornadoExecutions(tenSecondsAtSixtyFps);
        int legacyExecutions = GravityGpuFramePolicy.legacyStableSolarSystemTornadoExecutions(tenSecondsAtSixtyFps);

        assertEquals(600, currentExecutions);
        assertEquals(1200, legacyExecutions);
        assertEquals(600, legacyExecutions - currentExecutions);
    }

    @Test
    void stableSolarSystemUpdatesDashboardOnlyOnThrottledFrames() {
        int dashboardUpdates = 0;

        for (int frame = 1; frame <= 60; frame++) {
            if (GravityGpuFramePolicy.shouldUpdateDashboard(frame)) {
                dashboardUpdates++;
            }
        }

        assertEquals(12, dashboardUpdates);
        assertFalse(GravityGpuFramePolicy.shouldUpdateDashboard(1));
        assertTrue(GravityGpuFramePolicy.shouldUpdateDashboard(5));
        assertTrue(GravityGpuFramePolicy.shouldUpdateDashboard(60));
    }

    @Test
    void stableSolarSystemSkipsLegacyCollisionKernels() {
        int tenSecondsAtSixtyFps = 600;

        int currentCollisionKernels = GravityGpuFramePolicy.currentStableSolarSystemCollisionKernels(tenSecondsAtSixtyFps);
        int legacyCollisionKernels = GravityGpuFramePolicy.legacyStableSolarSystemCollisionKernels(tenSecondsAtSixtyFps);

        assertEquals(0, currentCollisionKernels);
        assertEquals(1200, legacyCollisionKernels);
        assertFalse(GravityGpuFramePolicy.shouldCheckCollisions(9, 0));
        assertTrue(GravityGpuFramePolicy.shouldCheckCollisions(10, 1));
    }

    @Test
    void stableSolarSystemUsesOneFusedPhysicsKernelPerFrame() {
        int tenSecondsAtSixtyFps = 600;

        int currentPhysicsKernels = GravityGpuFramePolicy.currentStableSolarSystemPhysicsKernels(tenSecondsAtSixtyFps);
        int legacyPhysicsKernels = GravityGpuFramePolicy.legacyStableSolarSystemPhysicsKernels(tenSecondsAtSixtyFps);

        assertEquals(600, currentPhysicsKernels);
        assertEquals(22_200, legacyPhysicsKernels);
        assertEquals(21_600, legacyPhysicsKernels - currentPhysicsKernels);
    }

    @Test
    void stableSolarSystemDoesNotRunOptionalTrailProjection() {
        assertFalse(GravityGpuFramePolicy.shouldProjectTrails(
                false,
                false,
                false,
                false,
                0.0f,
                0.0f,
                Float.NaN,
                Float.NaN));
    }

    @Test
    void trailProjectionRunsOnlyWhenVisibleTrailsNeedProjection() {
        assertFalse(GravityGpuFramePolicy.shouldProjectTrails(
                false,
                true,
                true,
                false,
                0.0f,
                0.0f,
                Float.NaN,
                Float.NaN));

        assertTrue(GravityGpuFramePolicy.shouldProjectTrails(
                true,
                true,
                true,
                true,
                0.0f,
                0.0f,
                0.0f,
                0.0f));

        assertTrue(GravityGpuFramePolicy.shouldProjectTrails(
                true,
                true,
                false,
                true,
                0.01f,
                0.0f,
                0.0f,
                0.0f));
    }
}
