---
name: install-debug
description: Build BatchIt debug APK and adb-install it on USB or wireless-debugging devices. Use when the user asks to install, deploy debug, flash phones, wireless debugging, or Option A/B device install.
---

# Install BatchIt debug APK

Automates **Option A** (local/LAN `adb`) for BatchIt: build (optional) → connect wireless endpoints → `adb install -r` → launch.

## When to use

- User asks to install/deploy the debug APK on devices
- User gives wireless debugging `IP:port` (and optional pairing codes)
- After a successful `:app:assembleDebug` when devices should be updated

## Prerequisites

- Run on a machine that can reach the phones (same Wi‑Fi / USB). **Cloud agent VMs usually cannot** reach `192.168.x.x`.
- `adb` on `PATH` or `ANDROID_HOME`/`ANDROID_SDK_ROOT` set
- Local `secrets.properties` + `app/google-services.json` for a usable auth build (gitignored)

## Device targets

Prefer writing endpoints once (gitignored):

```text
# .cursor/devices.local
192.168.1.19:43155
192.168.1.6:45863
```

Or pass env / flags:

```bash
export BATCHIT_ADB_DEVICES="192.168.1.19:43155 192.168.1.6:45863"
# Optional short-lived pairing:
export BATCHIT_ADB_PAIR="192.168.1.19:41459:688540 192.168.1.6:42025:601349"
```

Copy from `.cursor/devices.local.example` if needed.

## Instructions for the agent

1. If the user provided new wireless **connect** ports, update `.cursor/devices.local` (do **not** commit it).
2. If they provided **pairing** `IP:port` + 6-digit codes, export `BATCHIT_ADB_PAIR=host:pairPort:code ...` for this run only (codes expire quickly).
3. Run the script from the repo root:

```bash
chmod +x .agents/skills/install-debug/scripts/install-debug.sh
.agents/skills/install-debug/scripts/install-debug.sh --build
```

Install existing APK only (used by the after-build hook):

```bash
.agents/skills/install-debug/scripts/install-debug.sh --install-only
```

4. Interpret exit codes:
   - `0` — installed on ≥1 device (or build-only OK)
   - `1` — hard failure (missing SDK/adb/build error)
   - `2` — no reachable devices → tell the user to run the same script on their PC (Option A), or refresh wireless ports / pairing codes
5. Report `adb devices -l` serials and install results. Do not print secrets from `secrets.properties`.

## Hook (automatic)

Project hook `.cursor/hooks.json` runs `--install-only` after shell commands matching `assembleDebug` when the build output looks successful. It no-ops cleanly when no devices are online (typical for cloud agents).
