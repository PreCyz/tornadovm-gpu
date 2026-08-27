package pawg.nbody;

final class GravityGpuFramePolicy {

    static final int DASHBOARD_UPDATE_FRAMES = 5;
    static final int CURRENT_MANDATORY_TORNADO_EXECUTIONS_PER_FRAME = 1;
    static final int LEGACY_MANDATORY_TORNADO_EXECUTIONS_PER_FRAME = 2;
    static final int CURRENT_STABLE_COLLISION_KERNELS_PER_FRAME = 0;
    static final int LEGACY_STABLE_COLLISION_KERNELS_PER_FRAME = 2;
    static final int CURRENT_STABLE_PHYSICS_KERNELS_PER_FRAME = 1;
    static final int LEGACY_STABLE_PHYSICS_KERNELS_PER_FRAME = 37;

    private GravityGpuFramePolicy() {
    }

    static boolean shouldUpdateDashboard(int frameNumber) {
        return frameNumber > 0 && frameNumber % DASHBOARD_UPDATE_FRAMES == 0;
    }

    static boolean shouldExecuteSimulationSnapshotFrame(int frameNumber, int readbackIntervalFrames) {
        int interval = Math.max(1, readbackIntervalFrames);
        return frameNumber > 0 && (frameNumber - 1) % interval == 0;
    }

    static boolean shouldCheckCollisions(int bodyCount, int editableBodyCount) {
        return bodyCount > 1 && editableBodyCount > 0;
    }

    static boolean shouldProjectTrails(boolean showTrails, boolean hasTrailPoints, boolean trailsNeedProjection,
                                       boolean hasCachedProjection, float cameraYaw, float cameraPitch,
                                       float cameraRoll, float projectedTrailYaw, float projectedTrailPitch,
                                       float projectedTrailRoll) {
        return showTrails
                && hasTrailPoints
                && (trailsNeedProjection
                || !hasCachedProjection
                || Math.abs(cameraYaw - projectedTrailYaw) > 0.0001f
                || Math.abs(cameraPitch - projectedTrailPitch) > 0.0001f
                || Math.abs(cameraRoll - projectedTrailRoll) > 0.0001f);
    }

    static int currentStableSolarSystemTornadoExecutions(int frameCount) {
        return Math.max(0, frameCount) * CURRENT_MANDATORY_TORNADO_EXECUTIONS_PER_FRAME;
    }

    static int throttledStableSolarSystemTornadoExecutions(int frameCount, int readbackIntervalFrames) {
        int frames = Math.max(0, frameCount);
        int interval = Math.max(1, readbackIntervalFrames);
        return (frames + interval - 1) / interval;
    }

    static int legacyStableSolarSystemTornadoExecutions(int frameCount) {
        return Math.max(0, frameCount) * LEGACY_MANDATORY_TORNADO_EXECUTIONS_PER_FRAME;
    }

    static int currentStableSolarSystemCollisionKernels(int frameCount) {
        return Math.max(0, frameCount) * CURRENT_STABLE_COLLISION_KERNELS_PER_FRAME;
    }

    static int legacyStableSolarSystemCollisionKernels(int frameCount) {
        return Math.max(0, frameCount) * LEGACY_STABLE_COLLISION_KERNELS_PER_FRAME;
    }

    static int currentStableSolarSystemPhysicsKernels(int frameCount) {
        return Math.max(0, frameCount) * CURRENT_STABLE_PHYSICS_KERNELS_PER_FRAME;
    }

    static int legacyStableSolarSystemPhysicsKernels(int frameCount) {
        return Math.max(0, frameCount) * LEGACY_STABLE_PHYSICS_KERNELS_PER_FRAME;
    }
}
