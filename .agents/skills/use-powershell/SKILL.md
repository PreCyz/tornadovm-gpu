---
name: use-powershell
description: Prefer PowerShell (`pwsh`) as the default shell for every command-line operation. Use whenever Codex needs to run terminal commands, scripts, build tools, tests, version-control commands, file operations, or other CLI instructions in this project.
---

# Use PowerShell

Use `pwsh` as the shell whenever the command-execution tool permits choosing a shell. If the tool already runs PowerShell, execute the command directly without nesting another `pwsh` process.

Write shell logic with PowerShell syntax and semantics:

- Use PowerShell cmdlets for shell-level file, process, environment, and pipeline operations.
- Use PowerShell quoting, variables, conditionals, pipelines, and command separators.
- Invoke cross-platform executables such as `git`, `mvn`, `npm`, or `python` directly from PowerShell.
- Do not assume Bash, `cmd.exe`, WSL, or POSIX-only syntax.

Use another shell only when the user explicitly requests it or an unavoidable script or tool requires that shell. Keep any exception as narrow as possible and return to `pwsh` for subsequent commands.
