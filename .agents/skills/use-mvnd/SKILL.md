---
name: use-mvnd
description: Prefer Maven Daemon (`mvnd`) for every Maven command, with Apache Maven (`mvn`) as the fallback only when `mvnd` is not installed or unavailable on PATH. Use whenever Codex needs to run Maven lifecycle phases, goals, plugins, tests, builds, dependency operations, or other Maven CLI work in this project.
---

# Use Maven Daemon

Before running a Maven command, resolve the executable in PowerShell:

```powershell
$mavenCommand = if (Get-Command mvnd -ErrorAction SilentlyContinue) { 'mvnd' } else { 'mvn' }
& $mavenCommand <arguments>
```

Pass the intended Maven goals, profiles, properties, options, and other arguments through unchanged.

Reuse the resolved command for subsequent Maven invocations in the same terminal session when the environment has not changed.

Fall back to `mvn` only when `mvnd` is not installed or cannot be resolved on `PATH`. Do not retry a failed `mvnd` build with `mvn`; treat build, test, configuration, and dependency failures as Maven task failures rather than evidence that `mvnd` is unavailable.
