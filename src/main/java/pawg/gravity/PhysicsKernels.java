package pawg.gravity;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

class PhysicsKernels {

    static void computeForces(
            FloatArray px, FloatArray py,
            FloatArray ax, FloatArray ay,
            FloatArray m, IntArray active,
            FloatArray params) {

        float gConst = params.get(0);
        int numBodies = px.getSize();

        for (@Parallel int i = 0; i < numBodies; i++) {
            int actI = active.get(i);
            if (actI == 0) continue;

            float fx = 0.0f;
            float fy = 0.0f;

            float pxi = px.get(i);
            float pyi = py.get(i);
            float mi = m.get(i);

            for (int j = 0; j < numBodies; j++) {
                if (active.get(j) == 0) continue;

                float dx = px.get(j) - pxi;
                float dy = py.get(j) - pyi;

                float distSq = dx * dx + dy * dy + 35.0f;
                float dist = TornadoMath.sqrt(distSq);

                float force = (gConst * mi * m.get(j)) / distSq;

                fx += force * (dx / dist);
                fy += force * (dy / dist);
            }

            ax.set(i, fx / mi);
            ay.set(i, fy / mi);
        }
    }

    static void integrateMotion(
            FloatArray px, FloatArray py,
            FloatArray vx, FloatArray vy,
            FloatArray ax, FloatArray ay,
            IntArray active, FloatArray params) {

        float dt = params.get(1);
        int numBodies = px.getSize();

        for (@Parallel int i = 0; i < numBodies; i++) {
            if (active.get(i) == 0) continue;

            float vxi = vx.get(i) + ax.get(i) * dt;
            float vyi = vy.get(i) + ay.get(i) * dt;

            vx.set(i, vxi);
            vy.set(i, vyi);

            px.set(i, px.get(i) + vxi * dt);
            py.set(i, py.get(i) + vyi * dt);
        }
    }
}