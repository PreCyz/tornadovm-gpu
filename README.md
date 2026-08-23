# GameOfLife

JavaFX simulation demos accelerated with TornadoVM. The application renders interactive pixel-based simulations in JavaFX while TornadoVM task graphs execute the compute-heavy kernels on an accelerated device when TornadoVM is configured.

The launcher currently supports:

- Conway's Game of Life, launched by default.
- Heat distribution with temporary mouse-injected heat sources.
- Heat distribution with permanent heater sources.
- Solar eclipse rendering with configurable animation duration and coverage.

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

## Requirements

- JDK 25
- Maven
- JavaFX 25
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

The launcher is `pawg.Launcher`:

- No arguments: `GameOfLifeInteractive`
- `1`: `HeatDistributionFX`
- `2`: `HeatDistributionConstantHeatersFX`
- `3 <durationSeconds> <coveragePercent>`: `SolarEclipseFX`

## Packaging

Build the shaded JAR:

```bash
mvn clean package
```

The shaded JAR manifest points to `pawg.Launcher`.

## Repository Notes

Generated build output and local IDE metadata are intentionally not tracked. The `.gitignore` excludes Maven `target/`, Gradle-style `build/`, Kotlin metadata, and project files for IntelliJ IDEA, Eclipse, NetBeans, VS Code, and macOS.
