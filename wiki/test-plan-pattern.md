---
category: Test Plans
---

# Test Plan Correlation Pattern

Test plans under `test-plans/*.jmx` are JMeter designs built with BlazeMeter's Concurrency Thread Group and Throughput Shaping Timer for load profiles. `host`/`protocol` are parameterized via properties injected at runtime (from [[environment-config]]) rather than hardcoded.

`baseline.jmx` demonstrates the correlation pattern followed across all plans: a JSON Extractor captures a value (e.g. `userId`) from one sampler's response, and a later sampler/assertion reuses it via `${varName}`. New extractors added to any plan should follow this same pattern.

See [[load-profile-suites]] for what each of the five plans stresses.
