# TornadoVM GPU JavaFX Simulations

This project is a collection of interactive physics and cellular-automata demos written in Java 25. JavaFX owns the user interface and rendering, while TornadoVM moves suitable data-parallel calculations to an OpenCL-capable accelerator. The repository also contains a CPU n-body implementation that acts as a readable reference for the accelerated gravity simulations.

The important design boundary is:

- **CPU / JavaFX:** input, animation scheduling, scene-graph updates, dashboards, collision orchestration, and other irregular control flow.
- **GPU / TornadoVM:** large independent loops such as grid updates, pairwise force accumulation, integration, projection, and per-pixel rendering.
- **Transfers:** primitive arrays and TornadoVM arrays cross the device boundary deliberately. Transfers and synchronization are part of the performance cost and should be measured.

## Available demos

`pawg.Launcher` still accepts direct numeric arguments for the legacy one-shot
simulation launch path. With no arguments, the Maven launcher and packaged JAR
open a BootstrapFX-based simulation chooser instead.

| Argument | Demo | What it shows | Main compute path |
| --- | --- | --- | --- |
| none | Launcher window | BootstrapFX chooser with device selection and nine simulation buttons | Starts one isolated child JVM per choice |
| `0` or unknown | Game of Life | Interactive 1200 x 880 toroidal cellular automaton | TornadoVM grid kernel |
| `1` | Temporary heat sources | 2D finite-difference heat diffusion; drag to inject heat | TornadoVM grid kernel |
| `2` | Permanent heaters | Heat diffusion with mouse-created fixed-temperature sources | TornadoVM grid kernel |
| `3 [seconds] [coverage]` | Solar eclipse | Animated Sun, Moon, corona, and sky rendering | TornadoVM per-pixel kernel |
| `4` | Earth orbit | Earth, atmosphere, orbit trace, and space background | TornadoVM per-pixel kernel |
| `5` | Solar System | Sun, eight planets, rings, orbits, and animated surfaces | TornadoVM per-pixel kernel |
| `6` | CPU gravity | Interactive n-body simulation with trails and body creation | CPU pairwise forces and Verlet integration |
| `7` | GPU gravity | Interactive n-body simulation, dashboard, editable bodies, and camera rotation | TornadoVM force, integration, and projection kernels |
| `8` | GPU body simulator | Editable/removable bodies, hover help, circular-orbit reference guides, photons, black-hole capture, trails, camera pan/rotation, and a curved gravity grid | TornadoVM integration with JavaFX visualization |

The launcher window uses BootstrapFX styling, follows the system light/dark
preference, and passes the chosen device into GPU child JVMs. CPU targets do
not receive a device selector property.

The heat equation uses the five-point stencil:

```text
next = current + alpha * (top + bottom + left + right - 4 * current)
```

The gravity demos initialize circular orbits from Kepler-style velocities and compensate the central body's momentum. They are educational simulations rather than high-precision ephemeris software.

## GPU body simulator interaction

Demo `8` is an interactive JavaFX view over the `pawg.body` n-body state. The
GPU task graph performs the regular acceleration and Velocity-Verlet update;
the JavaFX Application Thread owns body editing, collision orchestration,
device-plan lifecycle, dashboard refreshes, camera input, and drawing. Position
and velocity are transferred back after each executed simulation step so the
dashboard and canvas show the synchronized host snapshot.

- Use **+** to add an editable body. Editing while stopped still works as
  before; adding a body while running keeps the simulation running, fully
  initializes the new body, and the next device execution picks it up through
  the mutable-state upload without a plan rebuild or restart.
- Drag a body to reposition it. Drag empty canvas space to yaw/pitch; hold
  Shift or use the right mouse button on empty space to roll. Dragging a body
  pauses the elapsed clock until the drag ends. Scroll zooms.
- Arrow keys pan the camera along the visible plane; hold Shift for a larger
  pan and press Home to recenter. Right-clicking a body removes that exact body,
  compacts the simulation state, and makes the resulting state the new Reset
  snapshot.
- The dashboard edge buttons hide and show the drawer, and the device combo box
  highlights on hover so the accelerator choice is easier to spot.
- Hover **Guide**, **Unit description**, or the dashboard's **Unit
  calibration** label to read the associated explanation without permanently
  using sidebar space. These are ControlsFX PopOvers and close when the pointer
  leaves the label.
- Trails, full tracks, Schwarzschild-radius overlays, photon tracing, and
  circular-orbit reference guides are optional visualization aids. The current
  circular-orbit classifier is heuristic: it does not correctly account for
  gravitational softening, radial versus tangential relative velocity,
  candidate-body mass, or third-body perturbations. It can therefore draw a
  circle that a body will not follow; treat it as a visual reference, not an
  orbit prediction. The curved gravity grid uses the physical `SOFTENING`
  constant rather than zoom level, and collision merges invalidate the grid
  geometry so the next draw reflects the new mass distribution.

The elapsed label beside **Body Simulator** shows `Elapsed HH:MM:SS`. It starts
or resumes on **Start** or after a successful **Photon** shot, resets and stops
on **Reset** or a device reset, pauses while a body is being dragged, and is
unaffected by live body insertion or collision merges.

## Requirements

- Windows 10 or 11 (the checked-in launcher script and packaged JavaFX natives are Windows-specific)
- JDK 25
- Maven Daemon (`mvnd`) or Apache Maven (`mvn`)
- TornadoVM 5.2.0 built for JDK 25 with an available OpenCL backend/device
- PowerShell 7 (`pwsh`) when using `run.ps1`

JavaFX 25.0.2, ControlsFX 11.2.4, TornadoVM 5.2.0-jdk25, and JUnit 6.1.3 are resolved by Maven.

Set the environment before building. Replace the example paths with your installation paths:

```powershell
$env:JAVA_HOME = 'C:\Install\Java\jdk-25.0.2'
$env:TORNADOVM_HOME = 'C:\Install\Java\tornadovm-5.2.0-jdk25-opencl'
$env:PATH = "$env:JAVA_HOME\bin;$env:TORNADOVM_HOME\bin;$env:PATH"

java --version
tornado --devices
```

The Maven JavaFX configuration reads `TORNADOVM_HOME/bin/tornado-argfile`; a missing or incorrect variable causes the application launch to fail before a demo starts.

## Build and test

Use Maven Daemon when it is installed. Substitute `mvn` for `mvnd` in all examples if necessary.

```powershell
mvnd verify
```

`verify` compiles the application and runs all unit tests. Useful development commands are:

```powershell
mvnd test
mvnd '-Dtest=BodySimulatorGridRenderingTest' test
mvnd package
```

Avoid adding `clean` to every development command: a running JavaFX process or IDE can hold files in `target`. Use `mvnd clean verify` when a genuinely clean build is needed, after closing running instances.

## Run during development

### Maven JavaFX launcher

Run the BootstrapFX launcher window:

```powershell
mvnd javafx:run
```

Pass a numeric launcher argument through `javafx.args` to launch a specific demo directly:

```powershell
mvnd javafx:run '-Djavafx.args=8'
mvnd javafx:run '-Djavafx.args=3 12 100'
```

The second eclipse argument is its duration in seconds; the third is maximum coverage as a percentage. Unknown numeric values still fall back to Game of Life.

### Repository PowerShell launcher

`run.ps1` launches compiled classes through the TornadoVM command. Build at least once, then run:

```powershell
mvnd package
pwsh -File .\run.ps1 8
```

Before using the script on another machine, edit its local `TORNADO_HOME`, `TORNADO_SDK`, `JAVA_HOME`, and Maven-repository paths. They currently describe the original developer machine and are not auto-discovered.

If local script execution is blocked, allow only this invocation:

```powershell
pwsh -ExecutionPolicy Bypass -File .\run.ps1 8
```

The script's classpath does not include BootstrapFX, so running it with no
arguments still opens the legacy Game of Life path. Numeric arguments continue
to launch the direct simulations.

## TornadoVM device selection

GPU-backed demos normally show a device-selection dialog populated from `tornado --devices`. For automated tests, profiling, or an unattended startup, select TornadoVM's default device:

```powershell
$env:EXTRA_JVM_FLAGS = '-Dtornado.device.selector.default=true'
pwsh -File .\run.ps1 8
```

Device choice matters: a parallel loop is not automatically faster on a GPU once compilation, transfer, synchronization, and rendering costs are included.

## Run the packaged artifact

Create the shaded application JAR:

```powershell
mvnd clean package
```

The runnable artifact is:

```text
target/tornadovm-gpu-1.0-SNAPSHOT.jar
```

Run it with TornadoVM so the required runtime configuration is installed. This example starts demo 8 and skips the device dialog:

```powershell
$artifact = (Resolve-Path '.\target\tornadovm-gpu-1.0-SNAPSHOT.jar').Path
$jvmFlags = '--enable-native-access=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED -Dprism.verbose=false -Dtornado.device.selector.default=true'

tornado --jvm="$jvmFlags" --cp $artifact pawg.Launcher 8
```

Remove `-Dtornado.device.selector.default=true` to choose the accelerator interactively. Change the last argument using the demo table above.

Running the shaded JAR with no arguments opens the same BootstrapFX launcher window as `mvnd javafx:run`; numeric arguments keep the direct simulation path. Although the shaded JAR has `pawg.Launcher` in its manifest, the supported command is the TornadoVM launcher shown above rather than plain `java -jar`: TornadoVM supplies required JVM/module/runtime settings. The shaded JAR includes the project's Java dependencies and Windows JavaFX native libraries, but it does **not** replace the matching JDK, TornadoVM installation, or accelerator driver on the destination machine. `original-tornadovm-gpu-1.0-SNAPSHOT.jar` is the unshaded intermediate JAR and is not the distributable artifact.

## Project structure

```text
src/main/java/pawg/
|-- Launcher.java             demo selection
|-- gameoflife/               cellular automaton
|-- heatdistribution/         diffusion simulations
|-- eclipse/                  eclipse renderer
|-- gravity/                  CPU/GPU gravity applications
|-- nbody/                    Earth and Solar System renderers
`-- body/                     interactive GPU body simulator

src/test/java/                unit, numerical, rendering, and parity tests
.agents/                      project-specific Codex workflow and skills
pom.xml                       Java, JavaFX, TornadoVM, test, and shade configuration
run.ps1                       local Windows/TornadoVM development launcher
```

## Implementing a change

1. **State the model first.** Record the governing laws, units, assumptions, numerical method, invariants, and acceptable error before changing a physics kernel.
2. **Keep a correctness oracle.** Implement or preserve a deterministic CPU calculation for numerical checks. Compare GPU output with tolerances appropriate to floating-point order and precision.
3. **Partition intentionally.** Put regular, high-volume, independent calculations on TornadoVM. Keep JavaFX scene-graph mutation, UI state, allocation-heavy work, and irregular orchestration on the CPU.
4. **Use kernel-friendly data.** Prefer primitive/TornadoVM arrays, indexed loops, and `@Parallel`; keep object graphs, JavaFX types, streams, recursion, synchronization, and exception-driven flow outside kernels.
5. **Make transfers explicit.** Define `TaskGraph` inputs/outputs and transfer modes deliberately. Avoid per-frame full-state readback unless CPU behavior or visualization actually needs it.
6. **Protect the JavaFX thread.** TornadoVM execution and expensive CPU work must not block scene-graph mutation. Only update JavaFX nodes on the JavaFX Application Thread.
7. **Test in layers.** Add unit tests for equations and edge cases, CPU/GPU parity tests for kernels, and focused rendering/interaction tests where presentation has meaningful logic.
8. **Run quality gates.** Finish with `mvnd verify`; inspect compiler and static-analysis findings, clean only issues that improve correctness or maintainability, then rerun the tests after cleanup.

For substantial simulation work, follow [AGENTS.md](AGENTS.md) and the project workflow in `.agents/skills/simulation-development/`. The intended sequence is physics definition, independent physics/math validation, documented CPU/GPU partition, implementation, tests, worthwhile cleanup, independent retest, and finally release preparation.

## GravityGPU tuning and profiling

`GravityGPU` exposes JVM system properties for controlling device-to-host synchronization and diagnosing slow frames:

| Property | Default | Purpose |
| --- | ---: | --- |
| `gravitygpu.adaptive.readback.enabled` | `true` | Uses a fixed conservative render-readback interval for the run |
| `gravitygpu.adaptive.render.readback.max` | `4` | Render-readback interval in adaptive mode |
| `gravitygpu.render.readback.interval` | `2` | Fixed render interval when adaptive mode is disabled |
| `gravitygpu.state.readback.interval` | `30` | Full physical-state readback interval; never below the render interval |
| `gravitygpu.framebudget.skip.enabled` | `true` | Allows an optional snapshot to be skipped after an over-budget frame |
| `gravitygpu.framebudget.skip.ms` | `16.0` | Frame-budget threshold in milliseconds |
| `gravitygpu.orbit.guide.segments` | `96` | Segments per dynamic orbit guide; minimum 24 |
| `gravitygpu.timing` | `false` | Enables execute, state-sync, dashboard, and draw timing logs |
| `gravitygpu.timing.slow.ms` | `24.0` | Slow-frame logging threshold |
| `gravitygpu.timing.summary.frames` | `300` | Timing-summary interval |

Example profiling run:

```powershell
$env:EXTRA_JVM_FLAGS = '-Dtornado.device.selector.default=true -Dgravitygpu.timing=true -Dgravitygpu.timing.slow.ms=16 -Dgravitygpu.timing.summary.frames=300'
pwsh -File .\run.ps1 7
```

When custom bodies or collision experiments require fresher CPU-side state, reduce `gravitygpu.state.readback.interval`. When orbit guides are visible, the application intentionally synchronizes position and velocity at the render cadence so guides reflect perturbations.

## Troubleshooting

- **`tornado-argfile` is missing:** verify `$env:TORNADOVM_HOME` and confirm `Test-Path "$env:TORNADOVM_HOME\bin\tornado-argfile"`.
- **No accelerator appears:** run `tornado --devices` and verify the OpenCL/CUDA driver and the TornadoVM backend installation.
- **`run.ps1` cannot find JavaFX classes:** run `mvnd package` first and update the script's hard-coded Maven-repository paths.
- **A clean build cannot delete `target`:** close the running application and any process holding the JAR/classes, then retry.
- **GPU output differs slightly from CPU output:** compare with an explicit numerical tolerance; floating-point reduction order can differ. Large, unstable, or non-finite differences are failures, not expected GPU noise.
