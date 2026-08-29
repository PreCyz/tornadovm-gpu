package pawg.body;

import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

final class BodyPhysicsKernels {

    private BodyPhysicsKernels() {
    }

    static void computeAcceleration(
            FloatArray px, FloatArray py, FloatArray pz,
            FloatArray ax, FloatArray ay, FloatArray az,
            FloatArray mass, IntArray active,
            FloatArray params, IntArray state) {

        float g = params.get(0);
        float softening = params.get(2);
        int count = state.get(0);

        for (@Parallel int i = 0; i < count; i++) {
            ax.set(i, 0.0f);
            ay.set(i, 0.0f);
            az.set(i, 0.0f);
            if (active.get(i) == 0 || mass.get(i) <= 0.0f) {
                continue;
            }

            float pxi = px.get(i);
            float pyi = py.get(i);
            float pzi = pz.get(i);
            float axi = 0.0f;
            float ayi = 0.0f;
            float azi = 0.0f;

            for (int j = 0; j < count; j++) {
                if (i == j || active.get(j) == 0 || mass.get(j) <= 0.0f) {
                    continue;
                }

                float dx = px.get(j) - pxi;
                float dy = py.get(j) - pyi;
                float dz = pz.get(j) - pzi;
                float distSq = dx * dx + dy * dy + dz * dz + softening;
                float dist = TornadoMath.sqrt(distSq);
                float accel = g * mass.get(j) / distSq;

                axi += accel * dx / dist;
                ayi += accel * dy / dist;
                azi += accel * dz / dist;
            }

            ax.set(i, axi);
            ay.set(i, ayi);
            az.set(i, azi);
        }
    }

    static void updatePositions(
            FloatArray px, FloatArray py, FloatArray pz,
            FloatArray vx, FloatArray vy, FloatArray vz,
            FloatArray ax, FloatArray ay, FloatArray az,
            IntArray active, FloatArray params, IntArray state) {

        float dt = params.get(1);
        int count = state.get(0);

        for (@Parallel int i = 0; i < count; i++) {
            if (active.get(i) == 0) {
                continue;
            }

            px.set(i, px.get(i) + vx.get(i) * dt + 0.5f * ax.get(i) * dt * dt);
            py.set(i, py.get(i) + vy.get(i) * dt + 0.5f * ay.get(i) * dt * dt);
            pz.set(i, pz.get(i) + vz.get(i) * dt + 0.5f * az.get(i) * dt * dt);
        }
    }

    static void computeNextAccelerationAndUpdateVelocity(
            FloatArray px, FloatArray py, FloatArray pz,
            FloatArray vx, FloatArray vy, FloatArray vz,
            FloatArray ax, FloatArray ay, FloatArray az,
            FloatArray nextAx, FloatArray nextAy, FloatArray nextAz,
            FloatArray mass, IntArray active,
            FloatArray params, IntArray state) {

        float g = params.get(0);
        float dt = params.get(1);
        float softening = params.get(2);
        int count = state.get(0);

        for (@Parallel int i = 0; i < count; i++) {
            nextAx.set(i, 0.0f);
            nextAy.set(i, 0.0f);
            nextAz.set(i, 0.0f);
            if (active.get(i) == 0) {
                continue;
            }

            float axi = 0.0f;
            float ayi = 0.0f;
            float azi = 0.0f;
            if (mass.get(i) > 0.0f) {
                float pxi = px.get(i);
                float pyi = py.get(i);
                float pzi = pz.get(i);

                for (int j = 0; j < count; j++) {
                    if (i == j || active.get(j) == 0 || mass.get(j) <= 0.0f) {
                        continue;
                    }

                    float dx = px.get(j) - pxi;
                    float dy = py.get(j) - pyi;
                    float dz = pz.get(j) - pzi;
                    float distSq = dx * dx + dy * dy + dz * dz + softening;
                    float dist = TornadoMath.sqrt(distSq);
                    float accel = g * mass.get(j) / distSq;

                    axi += accel * dx / dist;
                    ayi += accel * dy / dist;
                    azi += accel * dz / dist;
                }
            }

            nextAx.set(i, axi);
            nextAy.set(i, ayi);
            nextAz.set(i, azi);

            vx.set(i, vx.get(i) + 0.5f * (ax.get(i) + nextAx.get(i)) * dt);
            vy.set(i, vy.get(i) + 0.5f * (ay.get(i) + nextAy.get(i)) * dt);
            vz.set(i, vz.get(i) + 0.5f * (az.get(i) + nextAz.get(i)) * dt);
            ax.set(i, nextAx.get(i));
            ay.set(i, nextAy.get(i));
            az.set(i, nextAz.get(i));
        }
    }

    static void simulateOnCpu(
            FloatArray px, FloatArray py, FloatArray pz,
            FloatArray vx, FloatArray vy, FloatArray vz,
            FloatArray ax, FloatArray ay, FloatArray az,
            FloatArray nextAx, FloatArray nextAy, FloatArray nextAz,
            FloatArray mass, IntArray active,
            FloatArray params, IntArray state) {
        computeAcceleration(px, py, pz, ax, ay, az, mass, active, params, state);
        updatePositions(px, py, pz, vx, vy, vz, ax, ay, az, active, params, state);
        computeNextAccelerationAndUpdateVelocity(
                px, py, pz, vx, vy, vz, ax, ay, az, nextAx, nextAy, nextAz,
                mass, active, params, state);
    }
}
