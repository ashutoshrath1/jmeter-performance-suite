#!/usr/bin/env bash
# Build and execute the Java JMeter runner.

set -euo pipefail

ENVIRONMENT=${1:-dev}
SUITE=${2:-quick}
HEALTH_FLAG=${3:-}

if [ "$HEALTH_FLAG" = "--skip-health-check" ]; then
  export SKIP_HEALTH_CHECK=true
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven is required to build the Java runner."
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Java 11+ is required to run the suite."
  exit 1
fi

echo "Building shaded JAR..."
mvn -q clean verify

echo "Running suite '$SUITE' for environment '$ENVIRONMENT'..."
JAR_PATH="target/jmeter-performance-suite-1.0.0.jar"
if [ ! -f "$JAR_PATH" ]; then
  echo "Built jar not found at $JAR_PATH"
  exit 1
fi

java -jar "$JAR_PATH" "$ENVIRONMENT" "$SUITE"
