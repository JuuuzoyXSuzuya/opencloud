#!/usr/bin/env bash
# Build and inspect OpenCloud .oc plugin presets.
# Requires the same JDK the APK build uses (JAVA or a common location).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_BIN="${JAVA:-}"
if [[ -z "$JAVA_BIN" ]]; then
  for candidate in "$ROOT/opt/jdk-21.0.13+9/bin/java" "$HOME/opt/jdk-21.0.13+9/bin/java" "$(command -v java 2>/dev/null || true)"; do
    if [[ -n "$candidate" && -x "$candidate" ]]; then JAVA_BIN="$candidate"; break; fi
  done
fi
if [[ -z "$JAVA_BIN" ]]; then
  echo "ocplugin: java not found; set JAVA=/path/to/java" >&2
  exit 1
fi
JAVAC="${JAVAC:-$(dirname "$JAVA_BIN")/javac}"

WORK="${TMPDIR:-/tmp}/opencloud-ocplugin"
mkdir -p "$WORK/classes"
"$JAVAC" -encoding UTF-8 -d "$WORK/classes" "$ROOT/tools/OcPlugin.java"
exec "$JAVA_BIN" -cp "$WORK/classes" OcPlugin "$@"
