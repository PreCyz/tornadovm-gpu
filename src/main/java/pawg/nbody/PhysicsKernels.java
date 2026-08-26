package pawg.nbody;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

final class PhysicsKernels {

    private static final int DASHBOARD_METRIC_STRIDE = 7;
    private static final int DASHBOARD_DISTANCE_FROM_SUN_AU = 0;
    private static final int DASHBOARD_VELOCITY_X_KILOMETERS_PER_SECOND = 1;
    private static final int DASHBOARD_VELOCITY_Y_KILOMETERS_PER_SECOND = 2;
    private static final int DASHBOARD_VELOCITY_Z_KILOMETERS_PER_SECOND = 3;
    private static final int DASHBOARD_ACCELERATION_X_METERS_PER_SECOND_SQUARED = 4;
    private static final int DASHBOARD_ACCELERATION_Y_METERS_PER_SECOND_SQUARED = 5;
    private static final int DASHBOARD_ACCELERATION_Z_METERS_PER_SECOND_SQUARED = 6;
    private static final int TRAIL_CAPACITY = 180;

    private PhysicsKernels() {
    }

    static void clearCollisionTargets(IntArray collisionTarget, IntArray simulationState) {
        int numBodies = simulationState.get(0);

        for (@Parallel int i = 0; i < numBodies; i++) {
            collisionTarget.set(i, -1);
        }
    }

    static void computeAccelerations(
            FloatArray px, FloatArray py, FloatArray pz,
            FloatArray ax, FloatArray ay, FloatArray az,
            FloatArray m, IntArray active,
            FloatArray params,
            IntArray simulationState) {

        float gConst = params.get(0);
        int numBodies = simulationState.get(0);

        for (@Parallel int i = 0; i < numBodies; i++) {
            ax.set(i, 0.0f);
            ay.set(i, 0.0f);
            az.set(i, 0.0f);
            if (active.get(i) == 0) {
                continue;
            }

            float fx = 0.0f;
            float fy = 0.0f;
            float fz = 0.0f;

            float pxi = px.get(i);
            float pyi = py.get(i);
            float pzi = pz.get(i);
            float mi = m.get(i);
            if (mi <= 0.0f) {
                continue;
            }

            for (int j = 0; j < numBodies; j++) {
                if (i == j || active.get(j) == 0) continue;

                float dx = px.get(j) - pxi;
                float dy = py.get(j) - pyi;
                float dz = pz.get(j) - pzi;

                float distSq = dx * dx + dy * dy + dz * dz + 35.0f;
                float dist = TornadoMath.sqrt(distSq);

                float force = (gConst * mi * m.get(j)) / distSq;

                fx += force * (dx / dist);
                fy += force * (dy / dist);
                fz += force * (dz / dist);
            }

            float axi = fx / mi;
            float ayi = fy / mi;
            float azi = fz / mi;

            ax.set(i, axi);
            ay.set(i, ayi);
            az.set(i, azi);
        }
    }

    static void integrateVerletPosition(
            FloatArray srcPx, FloatArray srcPy, FloatArray srcPz,
            FloatArray srcVx, FloatArray srcVy, FloatArray srcVz,
            FloatArray srcAx, FloatArray srcAy, FloatArray srcAz,
            FloatArray dstPx, FloatArray dstPy, FloatArray dstPz,
            IntArray active,
            FloatArray params,
            IntArray simulationState) {

        float dt = params.get(1);
        float halfDtSq = 0.5f * dt * dt;
        int numBodies = simulationState.get(0);

        for (@Parallel int i = 0; i < numBodies; i++) {
            if (active.get(i) == 0) {
                continue;
            }

            dstPx.set(i, srcPx.get(i) + srcVx.get(i) * dt + srcAx.get(i) * halfDtSq);
            dstPy.set(i, srcPy.get(i) + srcVy.get(i) * dt + srcAy.get(i) * halfDtSq);
            dstPz.set(i, srcPz.get(i) + srcVz.get(i) * dt + srcAz.get(i) * halfDtSq);
        }
    }

    static void integrateVerletVelocity(
            FloatArray srcVx, FloatArray srcVy, FloatArray srcVz,
            FloatArray srcAx, FloatArray srcAy, FloatArray srcAz,
            FloatArray dstVx, FloatArray dstVy, FloatArray dstVz,
            FloatArray dstAx, FloatArray dstAy, FloatArray dstAz,
            IntArray active,
            FloatArray params,
            IntArray simulationState) {

        float halfDt = 0.5f * params.get(1);
        int numBodies = simulationState.get(0);

        for (@Parallel int i = 0; i < numBodies; i++) {
            if (active.get(i) == 0) {
                continue;
            }

            dstVx.set(i, srcVx.get(i) + (srcAx.get(i) + dstAx.get(i)) * halfDt);
            dstVy.set(i, srcVy.get(i) + (srcAy.get(i) + dstAy.get(i)) * halfDt);
            dstVz.set(i, srcVz.get(i) + (srcAz.get(i) + dstAz.get(i)) * halfDt);
        }
    }

    static void detectCollisions(
            FloatArray px, FloatArray py, FloatArray pz, FloatArray m,
            IntArray active,
            IntArray collisionTarget,
            float centerCollisionEpsilon,
            IntArray simulationState) {

        int numBodies = simulationState.get(0);

        for (@Parallel int i = 0; i < numBodies; i++) {
            if (active.get(i) == 0) continue;
            if (m.get(i) <= 0.0f) continue;

            float pxi = px.get(i);
            float pyi = py.get(i);
            float pzi = pz.get(i);
            int target = -1;
            float centerCollisionEpsilonSq = centerCollisionEpsilon * centerCollisionEpsilon;

            for (int j = 0; j < numBodies; j++) {
                if (i == j || active.get(j) == 0) continue;
                if (m.get(j) <= 0.0f) continue;

                float dx = px.get(j) - pxi;
                float dy = py.get(j) - pyi;
                float dz = pz.get(j) - pzi;

                if (dx * dx + dy * dy + dz * dz <= centerCollisionEpsilonSq) {
                    if (target == -1 || j < target) {
                        target = j;
                    }
                }
            }

            if (target != -1) {
                collisionTarget.set(i, target);
            }
        }
    }

    static void computeDashboardMetrics(
            FloatArray px, FloatArray py, FloatArray pz,
            FloatArray vx, FloatArray vy, FloatArray vz,
            FloatArray ax, FloatArray ay, FloatArray az,
            IntArray active,
            FloatArray speed,
            FloatArray acceleration,
            IntArray nearestIndex,
            FloatArray nearestDistance,
            FloatArray dashboardMetrics,
            FloatArray dashboardParams,
            IntArray simulationState) {

        int numBodies = simulationState.get(0);
        float physicsUnitsPerAu = dashboardParams.get(0);
        float velocityConversion = dashboardParams.get(1);
        float accelerationConversion = dashboardParams.get(2);
        float sunX = 0.0f;
        float sunY = 0.0f;
        float sunZ = 0.0f;
        if (numBodies > 0) {
            sunX = px.get(0);
            sunY = py.get(0);
            sunZ = pz.get(0);
        }

        for (@Parallel int i = 0; i < numBodies; i++) {
            nearestIndex.set(i, -1);
            nearestDistance.set(i, 0.0f);
            speed.set(i, 0.0f);
            acceleration.set(i, 0.0f);
            int metricBase = i * DASHBOARD_METRIC_STRIDE;
            for (int metricOffset = 0; metricOffset < DASHBOARD_METRIC_STRIDE; metricOffset++) {
                dashboardMetrics.set(metricBase + metricOffset, 0.0f);
            }
            if (active.get(i) == 0) continue;

            float vxi = vx.get(i);
            float vyi = vy.get(i);
            float vzi = vz.get(i);
            float axi = ax.get(i);
            float ayi = ay.get(i);
            float azi = az.get(i);
            speed.set(i, TornadoMath.sqrt(vxi * vxi + vyi * vyi + vzi * vzi) * velocityConversion);
            acceleration.set(i, TornadoMath.sqrt(axi * axi + ayi * ayi + azi * azi) * accelerationConversion);
            dashboardMetrics.set(metricBase + DASHBOARD_VELOCITY_X_KILOMETERS_PER_SECOND, vxi * velocityConversion);
            dashboardMetrics.set(metricBase + DASHBOARD_VELOCITY_Y_KILOMETERS_PER_SECOND, vyi * velocityConversion);
            dashboardMetrics.set(metricBase + DASHBOARD_VELOCITY_Z_KILOMETERS_PER_SECOND, vzi * velocityConversion);
            dashboardMetrics.set(metricBase + DASHBOARD_ACCELERATION_X_METERS_PER_SECOND_SQUARED, axi * accelerationConversion);
            dashboardMetrics.set(metricBase + DASHBOARD_ACCELERATION_Y_METERS_PER_SECOND_SQUARED, ayi * accelerationConversion);
            dashboardMetrics.set(metricBase + DASHBOARD_ACCELERATION_Z_METERS_PER_SECOND_SQUARED, azi * accelerationConversion);

            float pxi = px.get(i);
            float pyi = py.get(i);
            float pzi = pz.get(i);
            if (i != 0 && physicsUnitsPerAu > 0.0f) {
                float sunDx = pxi - sunX;
                float sunDy = pyi - sunY;
                float sunDz = pzi - sunZ;
                dashboardMetrics.set(metricBase + DASHBOARD_DISTANCE_FROM_SUN_AU,
                        TornadoMath.sqrt(sunDx * sunDx + sunDy * sunDy + sunDz * sunDz) / physicsUnitsPerAu);
            }

            int closestIndex = -1;
            float closestDistanceSq = 3.4028235e38f;

            for (int j = 0; j < numBodies; j++) {
                if (i == j || active.get(j) == 0) continue;

                float dx = px.get(j) - pxi;
                float dy = py.get(j) - pyi;
                float dz = pz.get(j) - pzi;
                float distanceSq = dx * dx + dy * dy + dz * dz;
                if (distanceSq < closestDistanceSq) {
                    closestDistanceSq = distanceSq;
                    closestIndex = j;
                }
            }

            if (closestIndex >= 0) {
                nearestIndex.set(i, closestIndex);
                nearestDistance.set(i, TornadoMath.sqrt(closestDistanceSq));
            }
        }
    }

    static void projectBodies(
            FloatArray px, FloatArray py, FloatArray pz,
            IntArray active,
            FloatArray renderParams,
            FloatArray screenX, FloatArray screenY, FloatArray depthScale,
            IntArray simulationState) {

        float yaw = renderParams.get(0);
        float pitch = renderParams.get(1);
        float canvasWidth = renderParams.get(2);
        float canvasHeight = renderParams.get(3);
        float physicsUnitsPerAu = renderParams.get(4);
        float mercuryAu = renderParams.get(5);
        float neptuneAu = renderParams.get(6);
        float minPlanetOrbitRadius = renderParams.get(7);
        float orbitEdgePadding = renderParams.get(8);
        float depthScaleDenominator = renderParams.get(9);
        int numBodies = simulationState.get(0);

        float cosYaw = TornadoMath.cos(yaw);
        float sinYaw = TornadoMath.sin(yaw);
        float cosPitch = TornadoMath.cos(pitch);
        float sinPitch = TornadoMath.sin(pitch);
        float centerX = canvasWidth * 0.5f;
        float centerY = canvasHeight * 0.5f;
        float maxOrbitRadius = TornadoMath.min(canvasWidth, canvasHeight) * 0.5f - orbitEdgePadding;
        if (maxOrbitRadius < minPlanetOrbitRadius + 1.0f) {
            maxOrbitRadius = minPlanetOrbitRadius + 1.0f;
        }
        float minLog = TornadoMath.log(mercuryAu);
        float maxLog = TornadoMath.log(neptuneAu);

        for (@Parallel int i = 0; i < numBodies; i++) {
            screenX.set(i, centerX);
            screenY.set(i, centerY);
            depthScale.set(i, 1.0f);
            if (active.get(i) == 0) {
                continue;
            }

            float physicsX = px.get(i);
            float physicsY = py.get(i);
            float physicsZ = pz.get(i);
            float yawX = physicsX * cosYaw + physicsZ * sinYaw;
            float yawZ = -physicsX * sinYaw + physicsZ * cosYaw;
            float viewX = yawX;
            float viewY = physicsY * cosPitch - yawZ * sinPitch;
            float viewZ = physicsY * sinPitch + yawZ * cosPitch;
            float physicsDistance = TornadoMath.sqrt(physicsX * physicsX + physicsY * physicsY + physicsZ * physicsZ);

            float scale = 1.0f + viewZ / depthScaleDenominator;
            if (scale < 0.55f) {
                scale = 0.55f;
            } else if (scale > 1.45f) {
                scale = 1.45f;
            }
            depthScale.set(i, scale);

            if (physicsDistance <= 0.000001f) {
                continue;
            }

            float projectedDistance = TornadoMath.sqrt(viewX * viewX + viewY * viewY);
            if (projectedDistance <= 0.000001f) {
                continue;
            }

            float au = physicsDistance / physicsUnitsPerAu;
            float screenDistance;
            if (au <= mercuryAu) {
                screenDistance = minPlanetOrbitRadius * au / mercuryAu;
            } else {
                float orbitLog = TornadoMath.log(Math.max(au, mercuryAu));
                float normalized = (orbitLog - minLog) / (maxLog - minLog);
                screenDistance = minPlanetOrbitRadius + normalized * (maxOrbitRadius - minPlanetOrbitRadius);
            }

            screenX.set(i, centerX + (viewX / projectedDistance) * screenDistance * scale);
            screenY.set(i, centerY + (viewY / projectedDistance) * screenDistance * scale);
        }
    }

    static void projectTrails(
            FloatArray trailX, FloatArray trailY, FloatArray trailZ,
            IntArray trailSize,
            IntArray active,
            FloatArray renderParams,
            FloatArray screenX, FloatArray screenY,
            IntArray simulationState) {

        float yaw = renderParams.get(0);
        float pitch = renderParams.get(1);
        float canvasWidth = renderParams.get(2);
        float canvasHeight = renderParams.get(3);
        float physicsUnitsPerAu = renderParams.get(4);
        float mercuryAu = renderParams.get(5);
        float neptuneAu = renderParams.get(6);
        float minPlanetOrbitRadius = renderParams.get(7);
        float orbitEdgePadding = renderParams.get(8);
        float depthScaleDenominator = renderParams.get(9);
        int numBodies = simulationState.get(0);
        int totalTrailPoints = numBodies * TRAIL_CAPACITY;

        float cosYaw = TornadoMath.cos(yaw);
        float sinYaw = TornadoMath.sin(yaw);
        float cosPitch = TornadoMath.cos(pitch);
        float sinPitch = TornadoMath.sin(pitch);
        float centerX = canvasWidth * 0.5f;
        float centerY = canvasHeight * 0.5f;
        float maxOrbitRadius = TornadoMath.min(canvasWidth, canvasHeight) * 0.5f - orbitEdgePadding;
        if (maxOrbitRadius < minPlanetOrbitRadius + 1.0f) {
            maxOrbitRadius = minPlanetOrbitRadius + 1.0f;
        }
        float minLog = TornadoMath.log(mercuryAu);
        float maxLog = TornadoMath.log(neptuneAu);

        for (@Parallel int index = 0; index < totalTrailPoints; index++) {
            screenX.set(index, centerX);
            screenY.set(index, centerY);

            int bodyIndex = index / TRAIL_CAPACITY;
            int slot = index - bodyIndex * TRAIL_CAPACITY;
            if (active.get(bodyIndex) == 0 || slot >= trailSize.get(bodyIndex)) {
                continue;
            }

            float physicsX = trailX.get(index);
            float physicsY = trailY.get(index);
            float physicsZ = trailZ.get(index);
            float yawX = physicsX * cosYaw + physicsZ * sinYaw;
            float yawZ = -physicsX * sinYaw + physicsZ * cosYaw;
            float viewX = yawX;
            float viewY = physicsY * cosPitch - yawZ * sinPitch;
            float viewZ = physicsY * sinPitch + yawZ * cosPitch;
            float physicsDistance = TornadoMath.sqrt(physicsX * physicsX + physicsY * physicsY + physicsZ * physicsZ);

            if (physicsDistance <= 0.000001f) {
                continue;
            }

            float projectedDistance = TornadoMath.sqrt(viewX * viewX + viewY * viewY);
            if (projectedDistance <= 0.000001f) {
                continue;
            }

            float scale = 1.0f + viewZ / depthScaleDenominator;
            if (scale < 0.55f) {
                scale = 0.55f;
            } else if (scale > 1.45f) {
                scale = 1.45f;
            }

            float au = physicsDistance / physicsUnitsPerAu;
            float screenDistance;
            if (au <= mercuryAu) {
                screenDistance = minPlanetOrbitRadius * au / mercuryAu;
            } else {
                float orbitLog = TornadoMath.log(Math.max(au, mercuryAu));
                float normalized = (orbitLog - minLog) / (maxLog - minLog);
                screenDistance = minPlanetOrbitRadius + normalized * (maxOrbitRadius - minPlanetOrbitRadius);
            }

            screenX.set(index, centerX + (viewX / projectedDistance) * screenDistance * scale);
            screenY.set(index, centerY + (viewY / projectedDistance) * screenDistance * scale);
        }
    }
}
