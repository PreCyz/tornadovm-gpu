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

    static void integrateStep(
            FloatArray srcPx, FloatArray srcPy,
            FloatArray srcVx, FloatArray srcVy,
            FloatArray dstPx, FloatArray dstPy,
            FloatArray dstVx, FloatArray dstVy,
            FloatArray ax, FloatArray ay,
            FloatArray m, IntArray active,
            FloatArray params, IntArray simulationState) {

        float gConst = params.get(0);
        float dt = params.get(1);
        int numBodies = simulationState.get(0);

        for (@Parallel int i = 0; i < numBodies; i++) {
            if (active.get(i) == 0) continue;

            float fx = 0.0f;
            float fy = 0.0f;

            float pxi = srcPx.get(i);
            float pyi = srcPy.get(i);
            float mi = m.get(i);

            for (int j = 0; j < numBodies; j++) {
                if (active.get(j) == 0) continue;

                float dx = srcPx.get(j) - pxi;
                float dy = srcPy.get(j) - pyi;

                float distSq = dx * dx + dy * dy + 35.0f;
                float dist = TornadoMath.sqrt(distSq);

                float force = (gConst * mi * m.get(j)) / distSq;

                fx += force * (dx / dist);
                fy += force * (dy / dist);
            }

            float axi = fx / mi;
            float ayi = fy / mi;
            float vxi = srcVx.get(i) + axi * dt;
            float vyi = srcVy.get(i) + ayi * dt;

            ax.set(i, axi);
            ay.set(i, ayi);
            dstVx.set(i, vxi);
            dstVy.set(i, vyi);
            dstPx.set(i, pxi + vxi * dt);
            dstPy.set(i, pyi + vyi * dt);
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
}
