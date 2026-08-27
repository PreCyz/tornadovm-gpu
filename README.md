# TornadoVM GPU JavaFX Demos

JavaFX simulation demos accelerated with TornadoVM. The application renders interactive pixel-based simulations in JavaFX while TornadoVM task graphs execute the compute-heavy kernels on an accelerated device when TornadoVM is configured.

The launcher currently supports:

- Conway's Game of Life, launched by default.
- Heat distribution with temporary mouse-injected heat sources.
- Heat distribution with permanent heater sources.
- Solar eclipse rendering with configurable animation duration and coverage.
- Earth orbit and full Solar System GPU renderers.
- CPU and GPU n-body gravity simulators with interactive body creation.

## Demos

### Game of Life

The default launch mode runs an interactive Conway's Game of Life simulation on a 1200 x 880 grid. The grid starts with a random pattern, and mouse clicks or drags make cells alive using a small brush.

The simulation uses toroidal wrapping at the grid edges, so cells at one edge treat cells on the opposite edge as neighbors.

### Heat Distribution

`HeatDistributionFX` simulates two-dimensional heat diffusion on a 512 x 512 grid. It starts with a circular heat source in the center of the grid and repeatedly applies a finite-difference heat equation:

```text
next = current + alpha * (top + bottom + left + right - 4 * current)
```

The visualization maps temperature to a color gradient from black through blue and red to yellow/white. Holding or dragging the mouse injects temporary heat into the simulation, so you can add new hot spots and watch them diffuse over time.

The simulator uses double buffering (`gridA` and `gridB`) and executes multiple simulation steps per rendered frame for smoother motion.

### Heat Distribution With Constant Heaters

`HeatDistributionConstantHeatersFX` uses the same heat diffusion model, but mouse interaction places permanent heater sources. These heaters are held at 100 degrees before each simulation step, so they continue warming the grid instead of fading away.

The demo starts with one permanent heater in the center of the grid. Holding or dragging the mouse adds more heater sources.

### Solar Eclipse

`SolarEclipseFX` renders an animated solar eclipse on an 800 x 600 canvas. A TornadoVM kernel computes the Sun, Moon, corona, and sky colors for each pixel.

The eclipse animation accepts a duration in seconds and a maximum coverage percentage. Clicking the image resets the animation timer.

### Gravity And Orbital Demos

`EarthOrbitGPU` renders a focused Earth-orbit scene on an 800 x 800 canvas. A TornadoVM kernel computes the Sun glow, Earth disk, rotating land/ocean pattern, atmosphere, orbit trace, and space background for each pixel.

`SolarSystemGPU` extends that GPU-rendered approach to an 880 x 880 full Solar System view. It renders the Sun glow, all eight planets, subtle orbit lines, Earth land rotation, Jupiter atmospheric bands, and Saturn rings while the JavaFX animation loop updates each planet with a different orbital speed.

`GravitySystemCPU` and `GravityGPU` are interactive n-body gravity simulators. They start with a Sun and planets initialized from Kepler-style circular orbit velocities, compensate the Sun's momentum, draw trails, and expose a side dashboard with mass, radius, distance, velocity, acceleration, and nearest-body details. Mouse interaction creates custom bodies in two steps: drag to size the mass, then click to choose the initial velocity vector. The GPU version also has editable custom-body fields and a `ROTATE` popup for setting camera X/Y/Z rotation directly. The CPU version uses pairwise force accumulation with Verlet integration; the GPU version runs force calculation, motion integration, and projection through TornadoVM kernels over fixed-size body arrays.

## Requirements

- JDK 25
- Maven
- JavaFX 25
- ControlsFX 11.2.4
- TornadoVM 5.2.0 for JDK 25
- `TORNADOVM_HOME` set to a valid TornadoVM installation

The Maven configuration passes TornadoVM runtime options through the JavaFX Maven plugin.

## Running

### Windows PowerShell Launcher

On Windows, use `run.ps1` to launch the demos through TornadoVM directly. The script sets the TornadoVM and JDK environment variables for the current session, builds the JavaFX classpath, and passes all script arguments to `pawg.Launcher`.

Use the latest stable version of PowerShell (`pwsh`) rather than legacy Windows PowerShell. Newer `pwsh` releases handle modern command-line parsing and script execution more consistently.

Run the default Game of Life demo:

```powershell
pwsh -File .\run.ps1
```

Run the temporary heat distribution demo:

```powershell
pwsh -File .\run.ps1 1
```

Run the permanent-heater heat distribution demo:

```powershell
pwsh -File .\run.ps1 2
```

Run the solar eclipse demo. The second argument is animation duration in seconds, and the third argument is maximum coverage percent:

```powershell
pwsh -File .\run.ps1 3 12 100
```

Run the GPU-rendered Solar System demo:

```powershell
pwsh -File .\run.ps1 5
```

Run the CPU n-body gravity simulator:

```powershell
pwsh -File .\run.ps1 6
```

Run the GPU n-body gravity simulator:

```powershell
pwsh -File .\run.ps1 7
```

### TornadoVM Device Selection

Every TornadoVM-backed demo shows a ControlsFX device-selection dialog before the simulation starts. The dialog is populated from:

```powershell
tornado --devices
```

The popup shows the concise command output first and has an on-demand details panel for extra Tornado API information such as memory limits, platform, backend, workgroup dimensions, and OpenCL C version.

For unattended profiling or scripted runs, skip the popup and use TornadoVM's default device:

```powershell
$env:EXTRA_JVM_FLAGS = "-Dtornado.device.selector.default=true"
pwsh -File .\run.ps1 7
```

This flag applies to Game of Life, heat distribution, solar eclipse, Earth orbit, Solar System, and `GravityGPU`.

### GravityGPU Runtime Flags

Tune `GravityGPU` readback pacing with JVM system properties. These flags are useful when the GPU computation is quick overall, but TornadoVM/OpenCL execution or host synchronization occasionally causes visible pauses.

- `gravitygpu.render.readback.interval` controls how often `GravityGPU` executes the main Tornado simulation/projection graph and reads projected screen coordinates back to JavaFX when adaptive readback is disabled. The default fixed value is `2`, unless `gravitygpu.readback.interval` is set, in which case that older flag is used as the default value. Lower values update rendered positions more often, but can increase GPU/host synchronization cost.
- `gravitygpu.state.readback.interval` controls how often `GravityGPU` explicitly reads full physical positions (`posX`, `posY`, `posZ`) back from the device. The default is `30`, and it is clamped so it can never be lower than the active render readback interval. Higher values reduce large device-to-host transfers, but CPU-side collision checks and dashboard metrics see the physical state less often.
- `gravitygpu.readback.interval` is the older compatibility flag. Prefer `gravitygpu.render.readback.interval` for new runs.
- `gravitygpu.adaptive.readback.enabled` enables conservative readback pacing. The default is `true`. In this mode `GravityGPU` builds the Tornado task graph once at `gravitygpu.adaptive.render.readback.max` and keeps that interval fixed for the run, avoiding mid-animation task-graph rebuilds and the compile pauses they can cause. Set it to `false` to use the fixed `gravitygpu.render.readback.interval` value instead.
- `gravitygpu.adaptive.render.readback.max` sets the fixed render readback interval used when adaptive readback is enabled. The default is `4`.
- `gravitygpu.framebudget.skip.enabled` allows `GravityGPU` to skip the next optional simulation/render snapshot after an over-budget frame. The default is `true`. Forced sync frames, collision-check frames, and orbit-guide frames are not skipped.
- `gravitygpu.framebudget.skip.ms` sets the over-budget threshold used by optional snapshot skipping. The default is `16.0`.
- Static JavaFX overlays such as the habitable zone, asteroid belt, and weak Sun gravity boundary are cached until their inputs change, for example when the camera moves, the canvas size changes, bodies are reset, or a related checkbox changes.
- `gravitygpu.orbit.guide.segments` controls how many line segments are used per dynamic orbit guide. The default is `96`, with a minimum of `24`. Lower values reduce JavaFX path rasterization work while `Show orbits` is enabled.
- `gravitygpu.timing` enables per-frame diagnostic logging. The default is `false`.
- `gravitygpu.timing.slow.ms` sets the slow-frame threshold used by timing logs. The default is `24.0`.
- `gravitygpu.timing.summary.frames` controls how often average/max timing summaries are printed. The default is `300`.

The `GravityGPU` dashboard is populated immediately after startup and refreshes after full state readbacks when the dashboard update interval has elapsed. It no longer depends on the full-state sync frame landing on an exact frame-number multiple, so planet and custom-body details should remain visible even with conservative GPU readback pacing.

The `ROTATE` button opens a camera-rotation dialog. Values are entered in degrees: `X` maps to pitch, `Y` maps to yaw, and `Z` maps to roll. Out-of-range values are clamped to the supported bounds before being applied. The rotation is used consistently by JavaFX overlay drawing, TornadoVM body projection, trail projection, and the axis widget.

When `Show orbits` is enabled in `GravityGPU`, the orbit guides are drawn dynamically from the current synced position and velocity instead of being snapshotted into the static overlay cache, so they can change when another body perturbs a planet without forcing an expensive cached-image rebuild every frame. This intentionally forces full state and velocity sync at the render readback cadence while orbit guides are visible. `Align planets on reset` also refreshes CPU-side projection state before rebuilding the static overlays, so the habitable zone, asteroid belt, and weak-gravity indicator stay centered after reset.

When `gravitygpu.timing=true`, slow-frame logs include separate `execute`, `stateSync`, `dashboard`, and JavaFX draw timings. `execute` measures `executionPlan.execute()`, while `stateSync` measures explicit full position/velocity `transferToHost(...)`. The same line also reports the active render `interval`, whether the optional simulation snapshot was skipped as `skippedSim`, and whether static overlays were rebuilt.

For a stable Solar System without custom bodies, the current default strategy is conservative fixed readback at interval `4`:

```powershell
$env:EXTRA_JVM_FLAGS = "-Dgravitygpu.adaptive.readback.enabled=true -Dgravitygpu.adaptive.render.readback.max=4 -Dgravitygpu.state.readback.interval=30"
pwsh -File .\run.ps1 7
```

For lower render latency, disable adaptive readback and choose a fixed render interval directly:

```powershell
$env:EXTRA_JVM_FLAGS = "-Dgravitygpu.adaptive.readback.enabled=false -Dgravitygpu.render.readback.interval=2 -Dgravitygpu.state.readback.interval=30"
pwsh -File .\run.ps1 7
```

When adding custom bodies or testing collisions, reduce the full state interval so CPU-side collision handling observes the simulation more frequently:

```powershell
$env:EXTRA_JVM_FLAGS = "-Dgravitygpu.adaptive.readback.enabled=false -Dgravitygpu.render.readback.interval=2 -Dgravitygpu.state.readback.interval=6"
pwsh -File .\run.ps1 7
```

For profiling, enable timing logs and skip the device popup:

```powershell
$env:EXTRA_JVM_FLAGS = "-Dtornado.device.selector.default=true -Dgravitygpu.timing=true -Dgravitygpu.timing.slow.ms=16 -Dgravitygpu.timing.summary.frames=300"
pwsh -File .\run.ps1 7
```

For JVM and GC profiling, add standard JDK diagnostics:

```powershell
$env:EXTRA_JVM_FLAGS = "-Dtornado.device.selector.default=true -Dgravitygpu.timing=true -Dgravitygpu.timing.slow.ms=16 -Xlog:gc*,safepoint:file=profile-gc-safepoint.log:tags,uptime,time,level -XX:StartFlightRecording=filename=profile-gravitygpu.jfr,settings=profile,dumponexit=true"
pwsh -File .\run.ps1 7
```

Recent profiling after the dashboard and camera-rotation changes showed that the dashboard is not the slow-frame bottleneck. In that run, slow frames were still dominated mainly by TornadoVM/OpenCL `execute` stalls, with smaller `stateSync` and occasional JavaFX overlay-draw spikes. GC pauses were short compared with the largest execution stalls.

If PowerShell blocks local script execution, run it for the current command only:

```powershell
pwsh -ExecutionPolicy Bypass -File .\run.ps1
```

Before using the script on another machine, update the local paths inside `run.ps1` for `TORNADO_HOME`, `TORNADO_SDK`, `JAVA_HOME`, and the local Maven JavaFX repository if they differ.

### Maven

Run the default Game of Life demo with no launcher arguments:

```bash
mvn clean javafx:run
```

Run the temporary heat distribution demo:

```bash
mvn clean javafx:run -Djavafx.args=1
```

Run the permanent-heater heat distribution demo:

```bash
mvn clean javafx:run -Djavafx.args=2
```

Run the solar eclipse demo. The second argument is animation duration in seconds, and the third argument is maximum coverage percent:

```bash
mvn clean javafx:run -Djavafx.args="3 12 100"
```

Run the GPU-rendered Solar System demo:

```bash
mvn clean javafx:run -Djavafx.args=5
```

Run the CPU n-body gravity simulator:

```bash
mvn clean javafx:run -Djavafx.args=6
```

Run the GPU n-body gravity simulator:

```bash
mvn clean javafx:run -Djavafx.args=7
```

The launcher is `pawg.Launcher`:

- No arguments: `GameOfLifeInteractive`
- `1`: `HeatDistributionFX`
- `2`: `HeatDistributionConstantHeatersFX`
- `3 <durationSeconds> <coveragePercent>`: `SolarEclipseFX`
- `4`: `EarthOrbitGPU`
- `5`: `SolarSystemGPU`
- `6`: `GravitySystemCPU`
- `7`: `GravityGPU`

## Packaging

Build the shaded JAR:

```bash
mvn clean package
```

The shaded JAR manifest points to `pawg.Launcher`.

## Repository Notes

Generated build output and local IDE metadata are intentionally not tracked. The `.gitignore` excludes Maven `target/`, Gradle-style `build/`, Kotlin metadata, and project files for IntelliJ IDEA, Eclipse, NetBeans, VS Code, and macOS.
