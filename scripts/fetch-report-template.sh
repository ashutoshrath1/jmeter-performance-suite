#!/usr/bin/env bash
# Install JMeter's HTML report templates into bin/report-template.
#
# The dashboard exporter renders FreeMarker templates that ship with the JMeter distribution, not
# with the Maven artifacts this runner depends on. They are ~3MB of third-party web assets
# (jQuery, Bootstrap, Font Awesome), so they are fetched rather than committed.
#
# Uses a local JMeter installation when one is available, otherwise downloads the matching release.
# Without these templates the suite still runs and still enforces its SLA gates - only the HTML
# dashboard is skipped.

set -euo pipefail

JMETER_VERSION="${JMETER_VERSION:-5.6.3}"
TARGET_DIR="bin/report-template"

if [ -d "$TARGET_DIR" ] && [ -n "$(ls -A "$TARGET_DIR" 2>/dev/null)" ]; then
  echo "Report templates already present at $TARGET_DIR"
  exit 0
fi

mkdir -p bin

# Prefer a local install: no download, and guaranteed to match what the machine already runs.
if command -v jmeter >/dev/null 2>&1; then
  JMETER_BIN="$(dirname "$(readlink -f "$(command -v jmeter)" 2>/dev/null || command -v jmeter)")"
  for candidate in "$JMETER_BIN/report-template" "$JMETER_BIN/../libexec/bin/report-template"; do
    if [ -d "$candidate" ]; then
      echo "Copying report templates from $candidate"
      cp -R "$candidate" "$TARGET_DIR"
      echo "Installed $TARGET_DIR"
      exit 0
    fi
  done
fi

echo "No local JMeter found; downloading Apache JMeter $JMETER_VERSION templates..."
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

ARCHIVE="apache-jmeter-${JMETER_VERSION}.tgz"
URL="https://archive.apache.org/dist/jmeter/binaries/${ARCHIVE}"

curl -fsSL "$URL" -o "$TMP_DIR/$ARCHIVE"
tar -xzf "$TMP_DIR/$ARCHIVE" -C "$TMP_DIR" \
  "apache-jmeter-${JMETER_VERSION}/bin/report-template"
cp -R "$TMP_DIR/apache-jmeter-${JMETER_VERSION}/bin/report-template" "$TARGET_DIR"

echo "Installed $TARGET_DIR"
