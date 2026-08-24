---
name: use-intellij-pwsh
description: Prefer running PowerShell commands through IntelliJ IDEA's integrated terminal when the IntelliJ MCP terminal tool is available. Use for terminal commands in this project when IDE environment fidelity matters.
---

# Use IntelliJ PowerShell Terminal

When terminal work is needed in this project, prefer IntelliJ IDEA's integrated terminal through the IntelliJ MCP terminal tool if it is available.

Use `mcp__idea.execute_terminal_command` with:

- `projectPath` set to the current project path when known.
- `executeInShell: true` so the command runs inside the user's configured shell.
- `reuseExistingTerminalWindow: true` for ordinary commands to avoid opening unnecessary terminal tabs.
- PowerShell syntax and commands. If a command must explicitly select a shell, invoke `pwsh`.

If the IntelliJ MCP terminal tool is not available, fall back to normal command execution using PowerShell conventions.

Do not use the IntelliJ terminal for commands that need sandbox escalation or special approval handling unless the user has explicitly authorized that route. Keep destructive-action checks and approval requirements unchanged.
