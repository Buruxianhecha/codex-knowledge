# Codex Plugin Runtime Verification Workflow

Use this workflow when installing, repairing, or validating Codex Desktop plugins on this Windows machine.

## Verification Levels

Do not collapse these into one status:

1. Marketplace entry exists.
2. CLI says installed/enabled.
3. Plugin cache files exist.
4. Skill appears in the current Codex session.
5. Runtime tool/MCP entrypoint is exposed in the current thread.
6. The requested UI action is verified with screenshot or output evidence.

Only levels 5 and 6 prove a runtime plugin is actually usable in the current thread.

## Commands

```powershell
rtk codex plugin marketplace list
rtk codex plugin list --marketplace openai-bundled
rtk codex plugin list --marketplace openai-curated
```

For runtime tool discovery, search for the expected tool family:

- Computer Use: `node_repl`, `js`, or a Computer Use runtime tool.
- Chrome: Chrome/browser control tools.
- Screenshot: working screenshot helper plus a real image capture.

## Windows GUI Rules

- Verify GUI actions with screenshots. A command exit code is not enough.
- Set `TEMP` and `TMP` to `D:\Codex-Knowledge\tmp` before PowerShell `Add-Type`.
- Make coordinate scripts DPI-aware before clicking or dragging.
- Record window rectangle, cursor position, and screenshot path in JSON.
- For privacy, keep chat screenshots local unless the user explicitly asks to publish them.

## WeChat-Specific Lesson

On this machine, WeChat's logged-in File Transfer Assistant window can be restored and screenshot-captured, but its rendered input layer may reject script-driven keyboard, clipboard, window-message, file-button, or drag/drop automation from the current Codex thread.

Therefore:

- Do not claim a WeChat send succeeded unless the final screenshot shows the new sent content.
- If a separate QR-login window appears, distinguish it from the existing logged-in main window.
- Prefer official Computer Use runtime tools when exposed; PowerShell/Win32 fallback is useful for diagnostics but may not complete protected input workflows.
