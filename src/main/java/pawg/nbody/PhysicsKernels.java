package pawg.nbody;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

final class PhysicsKernels {

    private static final int TRAIL_CAPACITY = 180;

    private PhysicsKernels() {
    }

    static void simulateVerletFrame(
            FloatArray px, FloatArray py, FloatArray pz,
            FloatArray vx, FloatArray vy, FloatArray vz,
            FloatArray ax, FloatArray ay, FloatArray az,
            FloatArray nextAx, FloatArray nextAy, FloatArray nextAz,
            FloatArray m, IntArray active,
            FloatArray params,
            IntArray simulationState,
            int subSteps) {

        float gConst = params.get(0);
        float dt = params.get(1);
        float halfDt = 0.5f * dt;
        float halfDtSq = 0.5f * dt * dt;
        int numBodies = simulationState.get(0);

        for (int i = 0; i < numBodies; i++) {
            ax.set(i, 0.0f);
            ay.set(i, 0.0f);
            az.set(i, 0.0f);
            if (active.get(i) == 0 || m.get(i) <= 0.0f) {
                continue;
            }

            float pxi = px.get(i);
            float pyi = py.get(i);
            float pzi = pz.get(i);
            float mi = m.get(i);
            float fx = 0.0f;
            float fy = 0.0f;
            float fz = 0.0f;

            for (int j = 0; j < numBodies; j++) {
                if (i == j || active.get(j) == 0) {
                    continue;
                }

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

            ax.set(i, fx / mi);
            ay.set(i, fy / mi);
            az.set(i, fz / mi);
        }

        for (int step = 0; step < subSteps; step++) {
            for (int i = 0; i < numBodies; i++) {
                if (active.get(i) == 0) {
                    continue;
                }

                px.set(i, px.get(i) + vx.get(i) * dt + ax.get(i) * halfDtSq);
                py.set(i, py.get(i) + vy.get(i) * dt + ay.get(i) * halfDtSq);
                pz.set(i, pz.get(i) + vz.get(i) * dt + az.get(i) * halfDtSq);
            }

            for (int i = 0; i < numBodies; i++) {
                nextAx.set(i, 0.0f);
                nextAy.set(i, 0.0f);
                nextAz.set(i, 0.0f);
                if (active.get(i) == 0 || m.get(i) <= 0.0f) {
                    continue;
                }

                float pxi = px.get(i);
                float pyi = py.get(i);
                float pzi = pz.get(i);
                float mi = m.get(i);
                float fx = 0.0f;
                float fy = 0.0f;
                float fz = 0.0f;

                for (int j = 0; j < numBodies; j++) {
                    if (i == j || active.get(j) == 0) {
                        continue;
                    }

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

                nextAx.set(i, fx / mi);
                nextAy.set(i, fy / mi);
                nextAz.set(i, fz / mi);
            }

            for (int i = 0; i < numBodies; i++) {
                if (active.get(i) == 0) {
                    continue;
                }

                vx.set(i, vx.get(i) + (ax.get(i) + nextAx.get(i)) * halfDt);
                vy.set(i, vy.get(i) + (ay.get(i) + nextAy.get(i)) * halfDt);
                vz.set(i, vz.get(i) + (az.get(i) + nextAz.get(i)) * halfDt);
                ax.set(i, nextAx.get(i));
                ay.set(i, nextAy.get(i));
                az.set(i, nextAz.get(i));
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
        float roll = renderParams.get(2);
        float canvasWidth = renderParams.get(3);
        float canvasHeight = renderParams.get(4);
        float physicsUnitsPerAu = renderParams.get(5);
        float mercuryAu = renderParams.get(6);
        float neptuneAu = renderParams.get(7);
        float minPlanetOrbitRadius = renderParams.get(8);
        float orbitEdgePadding = renderParams.get(9);
        float depthScaleDenominator = renderParams.get(10);
        int numBodies = simulationState.get(0);

        float cosYaw = TornadoMath.cos(yaw);
        float sinYaw = TornadoMath.sin(yaw);
        float cosPitch = TornadoMath.cos(pitch);
        float sinPitch = TornadoMath.sin(pitch);
        float cosRoll = TornadoMath.cos(roll);
        float sinRoll = TornadoMath.sin(roll);
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
            float pitchedX = yawX;
            float pitchedY = physicsY * cosPitch - yawZ * sinPitch;
            float viewZ = physicsY * sinPitch + yawZ * cosPitch;
            float viewX = pitchedX * cosRoll - pitchedY * sinRoll;
            float viewY = pitchedX * sinRoll + pitchedY * cosRoll;
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
        float roll = renderParams.get(2);
        float canvasWidth = renderParams.get(3);
        float canvasHeight = renderParams.get(4);
        float physicsUnitsPerAu = renderParams.get(5);
        float mercuryAu = renderParams.get(6);
        float neptuneAu = renderParams.get(7);
        float minPlanetOrbitRadius = renderParams.get(8);
        float orbitEdgePadding = renderParams.get(9);
        float depthScaleDenominator = renderParams.get(10);
        int numBodies = simulationState.get(0);
        int totalTrailPoints = numBodies * TRAIL_CAPACITY;

        float cosYaw = TornadoMath.cos(yaw);
        float sinYaw = TornadoMath.sin(yaw);
        float cosPitch = TornadoMath.cos(pitch);
        float sinPitch = TornadoMath.sin(pitch);
        float cosRoll = TornadoMath.cos(roll);
        float sinRoll = TornadoMath.sin(roll);
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
            float pitchedX = yawX;
            float pitchedY = physicsY * cosPitch - yawZ * sinPitch;
            float viewZ = physicsY * sinPitch + yawZ * cosPitch;
            float viewX = pitchedX * cosRoll - pitchedY * sinRoll;
            float viewY = pitchedX * sinRoll + pitchedY * cosRoll;
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
