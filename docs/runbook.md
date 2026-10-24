# Performance Test Runbook

Use `quick` for a baseline smoke test, `load` for the baseline plus stress plan,
and `all` only against an approved non-production target. Run a single plan with
`./scripts/run-java.sh <environment> <plan>`.

Each run writes a JTL and, when report templates are installed, an HTML dashboard
under `reports/`. A plan fails when its error rate, p95 latency, or throughput
breaches the selected environment's SLA thresholds.

CI enables baseline comparison with `BASELINE_COMPARE_ENABLED=true`. Update a
baseline only after verifying that target, load profile, and test data describe a
known-good run; do not change it merely to accept a failing build.
