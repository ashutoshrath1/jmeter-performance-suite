---
category: Architecture
---

# EnvironmentConfig

`src/main/java/com/jmeter/suite/config/EnvironmentConfig.java` — loads and merges `config/jmeter.properties` (shared defaults) with `config/environments/<env>.properties` (per-environment overrides).

Feeds [[jmeter-test-runner]] the resolved property set that gets pushed into the JMeter engine before a plan runs. See [[environments-dev-staging-prod]] for the concrete environment files and [[runner-args]] for how the environment name is selected on the command line.
