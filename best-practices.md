# JavaFX and TornadoVM Best Practices

This guide summarizes the CPU/GPU split used in this project and the practices
that kept the JavaFX simulations responsive while using TornadoVM.

## Project-Based Code Examples

The following abridged examples come from the current project. They show the
patterns in isolation; follow the linked source files when changing the
production implementation.

### Write kernels as indexed data-parallel loops

`BodyPhysicsKernels.updatePositions` keeps JavaFX objects and allocation out of
the kernel. Each work item updates one body stored in TornadoVM arrays:

```java
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
        px.set(i, px.get(i) + vx.get(i) * dt
                + 0.5f * ax.get(i) * dt * dt);
        py.set(i, py.get(i) + vy.get(i) * dt
                + 0.5f * ay.get(i) * dt * dt);
        pz.set(i, pz.get(i) + vz.get(i) * dt
                + 0.5f * az.get(i) * dt * dt);
    }
}
```

See [`BodyPhysicsKernels.java`](src/main/java/pawg/body/BodyPhysicsKernels.java).

### Match transfer modes to data ownership

The body simulator uploads structural simulation arrays when the plan is first
executed, uploads runtime parameters on every step, and declares physical-state
readback as on demand:

```java
TaskGraph graph = new TaskGraph("body-simulator")
        .transferToDevice(DataTransferMode.FIRST_EXECUTION,
                posX, posY, posZ, velX, velY, velZ,
                accX, accY, accZ, mass, active, state)
        .transferToDevice(DataTransferMode.EVERY_EXECUTION, params)
        .task("current-acceleration",
                BodyPhysicsKernels::computeAcceleration,
                posX, posY, posZ, accX, accY, accZ,
                mass, active, params, state)
        .task("position-update",
                BodyPhysicsKernels::updatePositions,
                posX, posY, posZ, velX, velY, velZ,
                accX, accY, accZ, active, params, state)
        .transferToHost(DataTransferMode.UNDER_DEMAND,
                posX, posY, posZ, velX, velY, velZ);

TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
```

This arrangement is suitable when topology changes invalidate the plan but
small values such as timestep or softening may change between executions. In
contrast, the interactive Game of Life uploads its editable grid with
`EVERY_EXECUTION` so mouse edits reach the device. See
[`BodySimulator.java`](src/main/java/pawg/body/BodySimulator.java) and
[`GameOfLifeInteractive.java`](src/main/java/pawg/gameoflife/GameOfLifeInteractive.java).

### Read device state only when a consumer needs it

An `UNDER_DEMAND` declaration lets the worker execute several GPU steps without
stalling for a host copy after every step. The body simulator requests a copy
only at its configured render-snapshot cadence:

```java
TornadoExecutionResult result = executionPlan.execute();

if (publishSnapshot) {
    result.transferToHost(
            posX, posY, posZ,
            velX, velY, velZ,
            accX, accY, accZ);
    publishRenderSnapshot(System.nanoTime());
}
```

The UI can then render the latest published snapshot instead of forcing every
simulation step to synchronize with the host.

### Keep execution off the JavaFX Application Thread

The body simulator owns a daemon worker for plan rebuilds, GPU execution, and
readback. Its critical section protects shared simulation state and ends before
the JavaFX frame performs drawing or control updates:

```java
Thread worker = new Thread(() -> {
    while (!stopRequested.get()) {
        simulationLock.lock();
        try {
            rebuildPlanIfNeeded();
            boolean publish = simulationStepCounter
                    % RENDER_READBACK_INTERVAL_FRAMES == 0;
            executeGpuStep(publish);
        } finally {
            simulationLock.unlock();
        }
        LockSupport.parkNanos(SIMULATION_STEP_INTERVAL_NANOS);
    }
}, "body-simulator-gpu-worker");
worker.setDaemon(true);
worker.start();
```

This is an abridged lifecycle example: the production worker also handles stop,
pause, empty-state, collision-bridge, timing, and failure conditions. JavaFX
node mutation must still occur on the JavaFX Application Thread.

## What Runs on the GPU

### Body Simulator

The body simulator uses TornadoVM for the dense, regular physics work over
primitive arrays:

- Current acceleration calculation for each body against all other bodies.
- Position integration.
- Next acceleration calculation.
- Velocity update using the current and next acceleration values.

Those steps are wired in `BodySimulator.rebuildPlanIfNeeded()` as three
TornadoVM tasks:

- `BodyPhysicsKernels.computeAcceleration`
- `BodyPhysicsKernels.updatePositions`
- `BodyPhysicsKernels.computeNextAccelerationAndUpdateVelocity`

The data sent to the device is simulation state: position, velocity,
acceleration, next-acceleration, mass, active flags, and body count. The physics
parameters are transferred every execution so runtime controls can affect the
next GPU step.

The GPU path keeps most simulation arrays device-resident and reads state back
only when a render snapshot is needed. Readback is `UNDER_DEMAND` in the task
graph and is triggered from `executeGpuStep(...)` according to the configured
render readback interval.

### N-Body Solar System

The n-body GPU simulation uses TornadoVM for:

- Verlet simulation substeps in `PhysicsKernels.simulateVerletFrame`.
- Body projection into screen coordinates in `PhysicsKernels.projectBodies`.
- Trail projection in `PhysicsKernels.projectTrails`.

This is a useful pattern: compute simulation and projection on the GPU, then
transfer only compact projected render data to JavaFX every frame.

### Heat Distribution

The heat demos use TornadoVM for:

- Grid stencil evolution with `computeHeatStep`.
- Pixel color generation with `renderHeatPixels`.

The JavaFX side only handles input and writes the already-rendered pixel buffer
to the image view.

### Game of Life

The Game of Life demo uses TornadoVM for:

- Two simulation generations per execution.
- Cell-to-pixel rendering.

Because the user can draw into the grid, the editable grid is transferred to
the device every execution, while the second buffer is allocated on the device
once.

### GPU Pixel Renderers

The orbit and eclipse demos use TornadoVM for full-frame pixel generation. CPU
code updates a small parameter array, executes the render kernel, and JavaFX
copies the resulting `int[]` pixel buffer into a `PixelWriter`.

## What Stays on the CPU

CPU code owns work that is irregular, UI-bound, or transfer-heavy relative to
its compute cost:

- JavaFX scene graph creation, mutation, layout, event handling, and drawing.
- Device selection and TornadoVM plan lifecycle.
- Simulation start, stop, reset, drag, mouse, keyboard, and sidebar controls.
- Collision detection and body merging in the body simulator.
- Body-slot compaction, names, colors, dashboard rows, editor rows, trails, and
  photon-path state.
- Render snapshots, interpolation, frame pacing, and profiling printouts.
- CPU fallback/reference execution.

The key rule is simple: keep large regular loops on the GPU; keep JavaFX and
irregular topology changes on the CPU.

## Partitioning Rules

1. Move only regular, data-parallel work to TornadoVM.

   Good GPU candidates in this project are O(n^2) force loops, grid stencils,
   per-cell automata, projection loops, and per-pixel render kernels.

2. Keep mutation-heavy control flow on the CPU.

   Collision merge handling changes body count, shifts slots, clears UI state,
   changes labels/colors, and dirties execution plans. That is not a good
   TornadoVM kernel target unless the data model is redesigned around fixed-size
   active masks and deferred compaction.

3. Use primitive arrays and TornadoVM array types at the boundary.

   Kernels should operate on `FloatArray`, `IntArray`, or primitive arrays, not
   JavaFX objects, collections, labels, colors, or application models.

4. Treat the CPU implementation as the correctness oracle.

   GPU results should be compared against CPU/reference behavior on
   hand-checkable cases, deterministic seeded cases, and realistic workloads.

5. Measure transfer cost separately from execution cost.

   A GPU step is not just kernel time. It also includes plan rebuilds, device
   transfers, host readbacks, and synchronization.

## TornadoVM Plan Lifecycle

1. Build execution plans outside the hot frame path whenever possible.

   First execution often includes compilation and device setup. The body
   simulator warms stopped-state GPU plans before Start so the first visible
   motion is not delayed by TornadoVM setup.

2. Keep plans reusable.

   Avoid marking the plan dirty for changes that do not affect device-resident
   structure. Rebuild only when the array/state contract actually changes.

3. Use transfer modes deliberately.

   - `FIRST_EXECUTION`: mostly static state or arrays that should become
     device-resident.
   - `EVERY_EXECUTION`: small parameters or CPU-edited input that must be
     uploaded every step.
   - `UNDER_DEMAND`: host readback that should happen only when rendering or
     telemetry actually needs it.

4. Close stale plans explicitly.

   Device changes, reset, shutdown, and incompatible topology changes should
   close old `TornadoExecutionPlan` instances so stale device state does not
   survive accidentally.

5. Warm up with visually unchanged state.

   Warm-up executions should use zero timestep or restore host state afterward
   so the user does not see hidden simulation motion before pressing Start.

## JavaFX Threading Rules

1. Never block the JavaFX Application Thread on GPU work.

   `executionPlan.execute()`, readback, plan rebuild, and warm-up can all be
   long enough to miss frames. Keep them off the FX pulse when the simulation is
   running.

2. Use a background worker for GPU stepping.

   The body simulator advances GPU steps on a worker thread, publishes immutable
   render snapshots, and lets JavaFX draw from those snapshots.

3. Use non-blocking reads from the frame path.

   If the FX pulse needs simulation state that may be owned by the worker, use a
   non-blocking lock attempt and skip that optional work for one frame if the
   lock is busy. A skipped collision check is better than a frozen UI.

4. Keep JavaFX scene graph mutations on the FX thread.

   Background workers may update primitive arrays and publish snapshots, but
   they must not create, remove, or mutate JavaFX nodes.

5. Keep locks small and never hold them while doing UI work.

   Lock only around shared simulation arrays and counters. Do not hold the
   simulation lock while laying out controls, writing pixels, formatting labels,
   or printing large logs.

## Rendering Smoothness

1. Decouple simulation cadence from render cadence.

   A GPU worker can step at a fixed interval while JavaFX renders at the display
   pulse. JavaFX should render the newest available snapshot instead of driving
   all physics directly.

2. Interpolate between snapshots.

   Rendering from previous/current snapshots hides uneven worker completion
   times and makes motion smoother when readback happens less often than the
   JavaFX pulse.

3. Reduce readback frequency.

   Read back enough state to render smoothly, not necessarily after every GPU
   execution. The body simulator uses a configurable render readback interval.

4. Cache expensive static or slowly-changing visuals.

   Gravity grid rendering can dominate `draw()`. Cache it and invalidate only
   when camera, scale, body topology, or relevant visual settings change.

5. Keep dashboard and editor refresh out of the hot path.

   Telemetry rows should update at a lower cadence than rendering. Editor
   controls should not be created, removed, or mass-refreshed during active
   simulation frames.

6. Avoid JavaFX control creation during animation.

   Creating labels, fields, layout panes, and listeners can cost hundreds of
   milliseconds under load. Reuse nodes, hide absorbed rows while running, and
   rebuild controls only when the simulation is stopped or when a user action
   demands it.

## Collision and Topology Changes

1. Keep collision merge resolution on the CPU unless the data model changes.

   Current body merging changes body count, shifts body slots, updates colors
   and names, clears trails, refreshes editor/dashboard state, and invalidates
   GPU residency. That is a CPU orchestration problem.

2. Do not rebuild GPU plans synchronously at the collision frame.

   A merge should update host state, publish a render snapshot, and let motion
   continue. The body simulator uses a CPU bridge after GPU-path merges so the
   merged body keeps moving while GPU state is recovered later.

3. Prefer bridge/fallback behavior over visible pauses.

   When device state is dirty after a merge, continue the simulation on CPU or
   another cheap path until the GPU plan can be safely reused.

4. Make collision checks optional on a busy frame.

   If the GPU worker owns the state lock, skip collision processing for that
   JavaFX pulse and try again on a later snapshot. This protects frame pacing.

5. Update user-visible topology cheaply.

   The dashboard should reflect the new body count promptly. Editor rows should
   be updated with minimal work: rename the survivor row, hide absorbed rows,
   and defer full rebuilds.

## Profiling Practice

1. Print separate timings for each stage.

   Useful categories in this project are frame total, simulation/render-state
   sync, draw, grid, grid rebuild, grid snapshot, bodies, dashboard, worker
   execute, readback, plan rebuild, collision, and editor sync.

2. Profile before optimizing.

   The collision pause looked like GPU stepping at first, but profiling showed
   collision itself was only a few milliseconds while JavaFX editor sync was
   hundreds of milliseconds.

3. Keep profiling switches runtime-configurable.

   System properties such as timing enablement, slow-frame threshold, summary
   cadence, readback interval, and profiling duration let the same binary serve
   normal use and measurement.

4. Treat first-execution numbers separately.

   TornadoVM first execution can include compilation and setup. Separate warm-up
   cost from steady-state GPU execution cost.

5. Record both averages and maxima.

   Smoothness problems usually come from max frame time and rare stalls, not
   average throughput.

## Testing Practice

1. Test CPU/GPU parity where a real TornadoVM device is available.

   Device-specific tests may be skipped by default, but they should be runnable
   with explicit properties in a configured TornadoVM environment.

2. Test JavaFX frame-path behavior without relying on screenshots.

   Regression tests can hold the simulation lock from a worker thread and prove
   the FX collision callback returns instead of blocking.

3. Test topology updates independently from rendering.

   Body count, active flags, names, colors, dashboard rows, editor visibility,
   and snapshot body count can all be verified without visual inspection.

4. Keep profiling tests opt-in.

   Long-running timing tests are valuable, but they should be gated behind
   explicit properties so normal builds stay fast and deterministic.

5. Run focused tests before full verification.

   Use narrow body-simulator tests after each change, then run broader Maven
   verification once the focused behavior is stable.

## Common Anti-Patterns

- Running `executionPlan.execute()` directly from `AnimationTimer.handle()` for
  complex or first-execution-heavy simulations.
- Reading all simulation arrays back to the host every frame when only projected
  render data is needed.
- Rebuilding TornadoVM plans after every small parameter change.
- Creating JavaFX controls during active animation.
- Holding a simulation lock while mutating the scene graph.
- Assuming GPU acceleration improves smoothness without measuring transfer,
  readback, and UI work.
- Treating average FPS as proof of smoothness while ignoring max frame time.
- Letting collision/topology changes synchronously rebuild GPU state.

## Practical Checklist

Before adding a new TornadoVM-backed JavaFX simulation:

- Define a CPU reference implementation first.
- Put only regular loops into kernels.
- Store kernel inputs and outputs in primitive/TornadoVM arrays.
- Choose transfer modes intentionally.
- Warm the first execution before visible animation.
- Step GPU work on a background thread for non-trivial simulations.
- Publish immutable render snapshots.
- Interpolate on the JavaFX side.
- Keep JavaFX node work off the simulation frame path.
- Add timing printouts before tuning.
- Validate with focused unit/parity tests and a real-device GPU run.
