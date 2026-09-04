---
category: Operations
---

# scripts/run-java.sh

Convenience wrapper: builds the shaded JAR (`mvn -q clean verify`) then executes it as `java -jar target/jmeter-performance-suite-1.0.0.jar <environment> <suite>`.

Usage:
```
chmod +x scripts/run-java.sh   # first time only
./scripts/run-java.sh dev quick
./scripts/run-java.sh staging all
./scripts/run-java.sh dev baseline --skip-health-check
```

Delegates argument parsing to [[runner-args]] and the run itself to [[jmeter-test-runner]]. [[ci-cd-pipeline]] invokes the same runner path rather than this script directly.
