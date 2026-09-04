---
category: Architecture
---

# ReportArtifactPaths

`src/main/java/com/jmeter/suite/report/ReportArtifactPaths.java` — computes the per-run output paths under `reports/`: the `.jtl` results file, the `-html/` report directory, and the zip bundle, all timestamped per plan (`reports/<plan>-<timestamp>.jtl`, `reports/<plan>-<timestamp>-html/`).

Used by [[jmeter-test-runner]] at the end of a run, and by [[ci-cd-pipeline]] to know what to publish as build artifacts.
