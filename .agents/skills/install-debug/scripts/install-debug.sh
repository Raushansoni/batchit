#!/usr/bin/env bash
# Build (optional) and install BatchIt debug APK onto connected / wireless ADB devices.
#
# Usage:
#   .agents/skills/install-debug/scripts/install-debug.sh [--build] [--no-launch] [--apk PATH]
#
# Devices (first match wins for wireless connect list):
#   1) CLI:  --devices "192.168.1.19:43155,192.168.1.6:45863"
#   2) Env:  BATCHIT_ADB_DEVICES="192.168.1.19:43155 192.168.1.6:45863"
#   3) File: .cursor/devices.local  (one host:port per line; # comments ok)
#
# Optional pairing (codes expire quickly):
#   BATCHIT_ADB_PAIR="192.168.1.19:41459:688540 192.168.1.6:42025:601349"
#
# Exit codes:
#   0  installed on at least one device, or --build-only succeeded
#   1  hard failure (missing tools / build failed)
#   2  no reachable devices (common on cloud VMs outside your LAN)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "$ROOT"

DO_BUILD=0
DO_LAUNCH=1
APK_OVERRIDE=""
DEVICES_CLI=""
INSTALL_ONLY=0
BUILD_ONLY=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build) DO_BUILD=1; shift ;;
    --install-only) INSTALL_ONLY=1; DO_BUILD=0; shift ;;
    --build-only) BUILD_ONLY=1; shift ;;
    --no-launch) DO_LAUNCH=0; shift ;;
    --apk) APK_OVERRIDE="${2:-}"; shift 2 ;;
    --devices) DEVICES_CLI="${2:-}"; shift 2 ;;
    -h|--help)
      sed -n '2,20p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown arg: $1" >&2
      exit 1
      ;;
  esac
done

log() { printf '[install-debug] %s\n' "$*"; }
warn() { printf '[install-debug] WARN: %s\n' "$*" >&2; }
die() { printf '[install-debug] ERROR: %s\n' "$*" >&2; exit 1; }

resolve_adb() {
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return
  fi
  if [[ -n "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]]; then
    local sdk="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
    if [[ -x "$sdk/platform-tools/adb" ]]; then
      echo "$sdk/platform-tools/adb"
      return
    fi
  fi
  return 1
}

load_devices() {
  local raw=""
  if [[ -n "$DEVICES_CLI" ]]; then
    raw="$DEVICES_CLI"
  elif [[ -n "${BATCHIT_ADB_DEVICES:-}" ]]; then
    raw="$BATCHIT_ADB_DEVICES"
  elif [[ -f "$ROOT/.cursor/devices.local" ]]; then
    raw="$(grep -vE '^\s*(#|$)' "$ROOT/.cursor/devices.local" | tr '\n' ' ')"
  fi
  # Normalize commas/newlines to spaces
  raw="${raw//,/ }"
  # shellcheck disable=SC2206
  DEVICES=($raw)
}

pair_devices() {
  local entry hostport code
  [[ -n "${BATCHIT_ADB_PAIR:-}" ]] || return 0
  for entry in $BATCHIT_ADB_PAIR; do
    # host:port:code  (code is last colon-separated field)
    code="${entry##*:}"
    hostport="${entry%:$code}"
    if [[ -z "$hostport" || -z "$code" || "$hostport" == "$entry" ]]; then
      warn "skip invalid BATCHIT_ADB_PAIR entry: $entry"
      continue
    fi
    log "Pairing $hostport ..."
    if ! "$ADB" pair "$hostport" "$code"; then
      warn "pair failed for $hostport (code expired or host unreachable)"
    fi
  done
}

connect_devices() {
  local d
  for d in "${DEVICES[@]:-}"; do
    [[ -n "$d" ]] || continue
    log "Connecting $d ..."
    "$ADB" connect "$d" >/dev/null 2>&1 || warn "connect failed: $d"
  done
}

online_serials() {
  "$ADB" devices 2>/dev/null | awk 'NR>1 && $2=="device" {print $1}'
}

find_apk() {
  if [[ -n "$APK_OVERRIDE" ]]; then
    [[ -f "$APK_OVERRIDE" ]] || die "APK not found: $APK_OVERRIDE"
    echo "$APK_OVERRIDE"
    return
  fi
  local candidate="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
  if [[ -f "$candidate" ]]; then
    echo "$candidate"
    return
  fi
  # Newest debug apk under outputs
  local found
  found="$(find "$ROOT/app/build/outputs/apk/debug" -name '*.apk' -type f 2>/dev/null | head -n 1 || true)"
  [[ -n "$found" ]] || die "No debug APK found. Run with --build first."
  echo "$found"
}

build_apk() {
  [[ -f "$ROOT/gradlew" ]] || die "gradlew missing at repo root"
  [[ -f "$ROOT/secrets.properties" ]] || warn "secrets.properties missing — auth/Stream may not work"
  [[ -f "$ROOT/app/google-services.json" ]] || warn "app/google-services.json missing — Firebase may not work"
  if [[ ! -f "$ROOT/local.properties" ]] && [[ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]]; then
    die "Set sdk.dir in local.properties or ANDROID_HOME"
  fi
  log "Building :app:assembleDebug ..."
  chmod +x "$ROOT/gradlew"
  "$ROOT/gradlew" :app:assembleDebug --quiet
  log "Build finished"
}

install_on_devices() {
  local apk serial ok=0 fail=0
  apk="$(find_apk)"
  log "APK: $apk ($(du -h "$apk" | awk '{print $1}'))"

  mapfile -t SERIALS < <(online_serials)
  if [[ ${#SERIALS[@]} -eq 0 ]]; then
    warn "No ADB devices in 'device' state."
    warn "On your LAN machine: put wireless endpoints in .cursor/devices.local or BATCHIT_ADB_DEVICES,"
    warn "then re-run. Cloud agents usually cannot reach 192.168.x.x phones."
    exit 2
  fi

  for serial in "${SERIALS[@]}"; do
    log "Installing on $serial ..."
    if "$ADB" -s "$serial" install -r "$apk"; then
      ok=$((ok + 1))
      if [[ "$DO_LAUNCH" -eq 1 ]]; then
        "$ADB" -s "$serial" shell monkey -p com.batchit.app -c android.intent.category.LAUNCHER 1 \
          >/dev/null 2>&1 || true
      fi
    else
      fail=$((fail + 1))
      warn "install failed on $serial"
    fi
  done

  log "Installed on $ok device(s); failed=$fail"
  [[ "$ok" -gt 0 ]] || exit 2
}

# --- main ---
if [[ "$DO_BUILD" -eq 1 || "$BUILD_ONLY" -eq 1 ]]; then
  build_apk
fi
if [[ "$BUILD_ONLY" -eq 1 ]]; then
  find_apk >/dev/null
  log "Build-only done"
  exit 0
fi

ADB="$(resolve_adb)" || die "adb not found (install platform-tools or set ANDROID_HOME)"
load_devices
pair_devices
connect_devices
sleep 1
"$ADB" devices -l || true
install_on_devices
