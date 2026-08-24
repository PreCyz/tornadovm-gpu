package pawg.gravity;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

class PhysicsKernels {

    static void clearCollisionTargets(IntArray collisionTarget, IntArray simulationState) {
        int numBodies = simulationState.get(0);

        for (@Parallel int i = 0; i < numBodies; i++) {
            collisionTarget.set(i, -1);
        }
    }

    static void computeAccelerations(
            FloatArray px, FloatArray py,
            FloatArray ax, FloatArray ay,
            FloatArray m, IntArray active,
            FloatArray params,
            IntArray simulationState) {

        float gConst = params.get(0);
        int numBodies = simulationState.get(0);

        for (@Parallel int i = 0; i < numBodies; i++) {
            ax.set(i, 0.0f);
            ay.set(i, 0.0f);
            if (active.get(i) == 0) {
                continue;
            }

            float fx = 0.0f;
            float fy = 0.0f;

            float pxi = px.get(i);
            float pyi = py.get(i);
            float mi = m.get(i);

            for (int j = 0; j < numBodies; j++) {
                if (i == j || active.get(j) == 0) continue;

                float dx = px.get(j) - pxi;
                float dy = py.get(j) - pyi;

                float distSq = dx * dx + dy * dy + 35.0f;
                float dist = TornadoMath.sqrt(distSq);

                float force = (gConst * mi * m.get(j)) / distSq;

                fx += force * (dx / dist);
                fy += force * (dy / dist);
            }

            float axi = fx / mi;
            float ayi = fy / mi;

            ax.set(i, axi);
            ay.set(i, ayi);
        }
    }

    static void integrateVerletPosition(
            FloatArray srcPx, FloatArray srcPy,
            FloatArray srcVx, FloatArray srcVy,
            FloatArray srcAx, FloatArray srcAy,
            FloatArray dstPx, FloatArray dstPy,
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
        }
    }

    static void integrateVerletVelocity(
            FloatArray srcVx, FloatArray srcVy,
            FloatArray srcAx, FloatArray srcAy,
            FloatArray dstVx, FloatArray dstVy,
            FloatArray dstAx, FloatArray dstAy,
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
        }
    }

    static void detectCollisions(
            FloatArray px, FloatArray py,
            IntArray active,
            IntArray collisionTarget,
            float centerCollisionEpsilon,
            IntArray simulationState) {

        int numBodies = simulationState.get(0);

        for (@Parallel int i = 0; i < numBodies; i++) {
            if (active.get(i) == 0) continue;

            float pxi = px.get(i);
            float pyi = py.get(i);
            int target = -1;
            float centerCollisionEpsilonSq = centerCollisionEpsilon * centerCollisionEpsilon;

            for (int j = 0; j < numBodies; j++) {
                if (i == j || active.get(j) == 0) continue;

                float dx = px.get(j) - pxi;
                float dy = py.get(j) - pyi;

                if (dx * dx + dy * dy <= centerCollisionEpsilonSq) {
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
            FloatArray px, FloatArray py,
            FloatArray vx, FloatArray vy,
            FloatArray ax, FloatArray ay,
            IntArray active,
            FloatArray speed,
            FloatArray acceleration,
            IntArray nearestIndex,
            FloatArray nearestDistance,
            IntArray simulationState) {

        int numBodies = simulationState.get(0);

        for (@Parallel int i = 0; i < numBodies; i++) {
            nearestIndex.set(i, -1);
            nearestDistance.set(i, 0.0f);
            speed.set(i, 0.0f);
            acceleration.set(i, 0.0f);
            if (active.get(i) == 0) continue;

            float vxi = vx.get(i);
            float vyi = vy.get(i);
            float axi = ax.get(i);
            float ayi = ay.get(i);
            speed.set(i, TornadoMath.sqrt(vxi * vxi + vyi * vyi));
            acceleration.set(i, TornadoMath.sqrt(axi * axi + ayi * ayi));

            float pxi = px.get(i);
            float pyi = py.get(i);
            int closestIndex = -1;
            float closestDistanceSq = 3.4028235e38f;

            for (int j = 0; j < numBodies; j++) {
                if (i == j || active.get(j) == 0) continue;

                float dx = px.get(j) - pxi;
                float dy = py.get(j) - pyi;
                float distanceSq = dx * dx + dy * dy;
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
}
