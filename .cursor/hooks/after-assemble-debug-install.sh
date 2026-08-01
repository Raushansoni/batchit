#!/usr/bin/env bash
# afterShellExecution hook: after a successful assembleDebug, install onto online ADB devices.
# Fail-open: always exit 0 so cloud agents / machines without phones are not blocked.

set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT/.agents/skills/install-debug/scripts/install-debug.sh"

payload="$(cat || true)"
command="$(printf '%s' "$payload" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("command",""))' 2>/dev/null || true)"
output="$(printf '%s' "$payload" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("output",""))' 2>/dev/null || true)"

# Matcher in hooks.json already filters; double-check.
if ! printf '%s' "$command" | grep -Eq 'assembleDebug|:app:assembleDebug'; then
  exit 0
fi

if ! printf '%s' "$output" | grep -Eq 'BUILD SUCCESSFUL'; then
  echo "[after-assemble-debug-install] skip: build not successful"
  exit 0
fi

if [[ ! -x "$SCRIPT" ]]; then
  chmod +x "$SCRIPT" 2>/dev/null || true
fi

if [[ ! -f "$SCRIPT" ]]; then
  echo "[after-assemble-debug-install] skip: missing $SCRIPT"
  exit 0
fi

echo "[after-assemble-debug-install] installing debug APK on online devices..."
set +e
"$SCRIPT" --install-only
rc=$?
set -e

case "$rc" in
  0) echo "[after-assemble-debug-install] install ok" ;;
  2) echo "[after-assemble-debug-install] no devices online (ok on cloud / offline LAN)" ;;
  *) echo "[after-assemble-debug-install] install script exit $rc (ignored)" ;;
esac

exit 0
