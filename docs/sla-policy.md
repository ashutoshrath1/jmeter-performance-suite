# SLA and Baseline Policy

The runner gates every plan on total error rate, p95 latency, and throughput.
Set a threshold to `0` to disable that individual gate. Development thresholds
against JSONPlaceholder validate the runner; they are not production SLOs.

When enabled, baseline comparison reads `config/baselines/<environment>-<plan>.json`.
Error rate and p95 latency may increase by at most `baseline.tolerance.percent`;
throughput may decrease by at most that percentage. A missing or malformed
baseline fails the run.
