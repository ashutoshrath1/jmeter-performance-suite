---
category: Test Plans
---

# Load Profile Suites

Five `.jmx` plans under `test-plans/`, selectable individually or via the `quick`/`load`/`all` suite groupings in [[plan-definition]]:

- `baseline.jmx` — establishes the reference load profile and the [[test-plan-pattern|correlation pattern]] used elsewhere.
- `spike-test.jmx` — sudden load spikes.
- `stress-test.jmx` — sustained load past normal capacity.
- `endurance-test.jmx` — extended-duration soak testing.
- `breakpoint-test.jmx` — incrementally increasing load until failure.

Run via [[run-java-script]] with a suite name, or directly with `java -jar target/jmeter-performance-suite-1.0.0.jar <environment> <plan>`.
