# Project context

## Current simulation areas

- `pawg.nbody.GravitySystemCPU`: CPU n-body simulation and JavaFX application.
- `pawg.nbody.GravityGPU`: TornadoVM n-body simulation, synchronization, and UI.
- `pawg.nbody.PhysicsKernels`: Verlet simulation and projection kernels.
- `pawg.body.*`: body simulation and collision behavior.
- `pawg.heatdistribution.*`: finite-difference heat simulations.
- `pawg.gravity.*` and `pawg.eclipse.*`: GPU pixel renderers.
- `pawg.gameoflife.*`: cellular automaton kernel and UI.
- `pawg.nbody.TornadoDevice*`: accelerator discovery and selection.

Do not assume these boundaries are ideal. Trace actual call paths and state
ownership before proposing a change.

## Baseline stack and sources

Read versions from `pom.xml`. At the time this guide was written the project
uses JDK 25, JavaFX 25, JUnit 6, and TornadoVM 5.2.0 for JDK 25.

- Core programming: https://tornadovm.readthedocs.io/en/latest/programming.html
- Unsupported Java: https://tornadovm.readthedocs.io/en/latest/unsupported.html
- Off-heap data: https://tornadovm.readthedocs.io/en/latest/offheap-types.html
- Benchmarking: https://tornadovm.readthedocs.io/en/latest/benchmarking.html
- Source/releases: https://github.com/beehive-lab/TornadoVM

Prefer documentation matching the dependency version. If `latest` differs,
use the matching release/tag or disclose the mismatch.

## Verification

Use `use-powershell` and `use-mvnd`. Run focused tests first, then the broader
suite when warranted. GPU runtime tests may require `TORNADOVM_HOME` and an
available device; distinguish an unavailable runtime from a code failure.

When IntelliJ MCP tools are available, prefer IDE-native file problems, lint,
build, reformat, safe refactoring, and integrated-terminal tools. Use SpotBugs
through a pinned project plugin or installed IDE integration. If neither exists,
resolve a compatible version from official SpotBugs sources and use fully
versioned Maven plugin coordinates without changing `pom.xml` unless ownership
was assigned. Do not run an unversioned plugin or claim a pass when unavailable.
