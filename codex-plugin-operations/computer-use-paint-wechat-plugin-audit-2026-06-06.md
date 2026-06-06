# Computer Use / Paint / WeChat / Plugin Audit - 2026-06-06

## Summary

- User task: draw Pikachu through a visible desktop drawing workflow, verify the drawing/window, send it to WeChat File Transfer Assistant, audit similar plugin/runtime issues, and save reusable lessons.
- Final drawing file: `D:\Codex-Knowledge\artifacts\paint-pikachu\pikachu-handdrawn-gui.png`
- Final drawing status: PASS.
- WeChat final resend status: NOT VERIFIED. The logged-in File Transfer Assistant window was restored and tested, but no new final image/message appeared in the verification screenshots.
- Security: a GitHub credential was provided during the run. It was not written to files, remotes, reports, or commits.

## Final Drawing Verification

Final image:

- Path: `D:\Codex-Knowledge\artifacts\paint-pikachu\pikachu-handdrawn-gui.png`
- Dimensions: `1000x700`
- Verification JSON: `D:\Codex-Knowledge\artifacts\paint-pikachu\pikachu-verification.json`
- Status: `pass`
- Verified features: yellow body, pointed ears with black tips, black eyes, red cheeks, small mouth, zigzag tail, and `PIKA!` label.

Window verification:

- Verification screenshot: `D:\Codex-Knowledge\artifacts\paint-pikachu\pikachu-window-verification.png`
- Verification JSON: `D:\Codex-Knowledge\artifacts\paint-pikachu\pikachu-window-verification.json`
- Actual window size: `1120x820`
- Expected window size: `1120x820`
- Expected canvas size: `1000x700`
- Status: `pass`

Rejected earlier image:

- A later auto-save polluted the previously sent file with random lines.
- The polluted file was rejected and backed up as `pikachu-handdrawn-gui.rejected-20260606131737.png`.
- It must not be used as the final deliverable.

## WeChat Verification

What was verified:

- The real installed WeChat executable is on `D:` under the user's WeChat install directory, ending in `Weixin\Weixin.exe`.
- The logged-in File Transfer Assistant window was recovered from a hidden off-screen window.
- Restored chat screenshot: `D:\Codex-Knowledge\artifacts\paint-pikachu\weixin-restored-window.png`
- The visible small QR login window was a separate window and was hidden to reduce interference.

Attempts that did not produce a new verified send:

- Clipboard image/text paste with `Ctrl+V`.
- `WScript.Shell.SendKeys`.
- low-level `keybd_event` and `SendInput`.
- `WM_PASTE` and `WM_CHAR` against the WeChat render child window.
- right-click context menu paste.
- bottom toolbar file button click.
- Explorer drag/drop of the selected final PNG into the chat.
- Windows on-screen keyboard coordinate test.

Important evidence:

- WeChat foreground check showed the logged-in window was foreground: `D:\Codex-Knowledge\artifacts\paint-pikachu\wechat-foreground-check.ps1`
- Child-window diagnostics showed the input area is rendered by `WeChatAppEx` / `Chrome_WidgetWin_0`: `D:\Codex-Knowledge\artifacts\paint-pikachu\wechat-child-window-diagnostics.json`
- Direct send screenshot did not show a new final message: `D:\Codex-Knowledge\artifacts\paint-pikachu\wechat-direct-send-verification.png`
- Drag/drop screenshot did not show a new final image: `D:\Codex-Knowledge\artifacts\paint-pikachu\wechat-drag-file-send-verification.png`

Conclusion:

- The final image exists and is verified.
- The WeChat window was restored and visually inspected.
- The final WeChat notification/send cannot be claimed complete because no screenshot showed a new final image or final text message.
- Root cause class: current desktop automation path can see and move WeChat windows, but cannot reliably inject accepted input into the WeChat rendered input layer from this Codex thread.

## Plugin Runtime Audit

Bundled plugin CLI status:

- `chrome@openai-bundled`: installed, enabled.
- `computer-use@openai-bundled`: installed, enabled.
- `latex@openai-bundled`: installed, enabled.
- `canva@openai-bundled`: installed, enabled.
- `picsart@openai-bundled`: installed, enabled.
- `hyperframes@openai-bundled`: installed, enabled.
- `remotion@openai-bundled`: installed, enabled.
- `biorender@openai-bundled`: installed, enabled.
- `sites@openai-bundled`: installed, enabled.
- `browser@openai-bundled`: not installed.

Runtime finding:

- CLI installed/enabled, plugin panel visibility, Skill loading, and runtime tool exposure are separate states.
- In this thread, `computer-use` Skill/plugin metadata was present, but the expected runtime entrypoint such as `node_repl/js` or equivalent Computer Use JS tool was not exposed.
- Result: official Computer Use API calls could not be used in this thread; desktop work had to rely on PowerShell, screenshots, and Win32 automation.

## Fixes Applied

Screenshot helper:

- Fixed `%USERPROFILE%\.codex\skills\screenshot\scripts\take_screenshot.ps1` to route `TEMP` and `TMP` to `D:\Codex-Knowledge\tmp` when `D:` exists.
- Backup created before patch: `%USERPROFILE%\.codex\skills\screenshot\scripts\take_screenshot.ps1.bak-tempfix-20260606080530`

Codex plugin installer Skill:

- Updated `%USERPROFILE%\.codex\skills\codex-plugin-installer\SKILL.md` with runtime verification lessons.
- Validation command passed with `quick_validate.py`.

## Reusable Lessons

- Do not treat command success as UI success. Verify GUI operations with screenshots.
- For drawing tasks, verify the final image file and the live drawing window separately.
- For Windows GUI work, set `TEMP/TMP` to a valid D-drive temp directory before `Add-Type`.
- For coordinate automation, make scripts DPI-aware and record actual cursor/window coordinates.
- For Codex plugins, report four states separately: CLI installed/enabled, plugin panel visible, Skill loaded, runtime tool exposed.
- For WeChat, a restored visible chat window is not enough. A send is complete only if the final screenshot shows the newly sent content.

## Final State

- Final drawing file is ready and verified.
- WeChat final resend is blocked/not verified.
- Plugin/runtime lessons were written locally.
- This report is safe to commit; it contains no credentials.
