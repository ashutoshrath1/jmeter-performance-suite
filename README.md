# JMeter Performance Suite

JMeter performance test boilerplate with a Java runner, parameterized plans, and per-run artifacts.

## Overview
- Baseline plan: `test-plans/baseline.jmx` (provided real test using BlazeMeter Concurrency Thread Group, Throughput Shaping Timer, JSON extractor, and assertions).
- Templates: spike, stress, endurance, and breakpoint plans with Concurrency Thread Group, load profiles, and working HTTP samplers.
- Java runner: executes JMX via embedded JMeter + plugin libraries with health checks, report packaging, and optional email delivery.
- CI/CD: not included yet. See "CI/CD integration" below.

## Folder structure
- `test-plans/` JMX files (baseline + templates)
- `data/` CSV or feeders (sample `sample.csv` included)
- `reports/` JTL outputs and generated HTML reports (kept empty with .gitkeep)
- `scripts/` Java runner wrapper (`run-java.sh`)
- `config/environments/` env properties (dev/staging provided, plus `prod.properties.example`)
- `src/test/` unit tests for suite resolution and CLI argument defaults
- `docs/` architecture overview (Mermaid diagrams)

## Prerequisites
- Java 11+ and Maven 3.9+ on PATH.
- Bash shell for the wrapper script.
- Network access to target hosts (baseline hits `jsonplaceholder.typicode.com` by default).
- Optional email of reports: set `SMTP_HOST`, `SMTP_TO`, and if needed `SMTP_USER`/`SMTP_PASS` before running.

## Quickstart (about 60 seconds)
1) Ensure Java 11+ and Maven 3.9+ are on PATH.
2) Make the wrapper executable: `chmod +x scripts/run-java.sh`
3) Run a baseline smoke on dev: `./scripts/run-java.sh dev quick`
4) Open the report: `reports/<plan>-<timestamp>-html/index.html` (auto-opens locally; in CI, grab the artifact).

## Run tests from CLI (Java runner)
- `./scripts/run-java.sh dev quick` (baseline only)
- `./scripts/run-java.sh staging all` (run every plan)
- Suites: `all`, `quick` (baseline), `load` (baseline + stress), or a single plan (`baseline`, `spike`, `stress`, `endurance`, `breakpoint`).

Outputs: `reports/<plan>-<timestamp>.jtl` and `reports/<plan>-<timestamp>-html/` plus a zipped HTML report. Reports can auto-open locally if `auto_open_reports=true` in the environment config.

## Modify load profiles
- All test plans are parameterized for `host` and `protocol` through environment properties pushed into JMeter at runtime.
- All test plans use Concurrency Thread Group with configurable target levels, ramp-up, steps, and hold times.
- Each plan includes Throughput Shaping Timer with load profiles tailored to the test type (spike, stress, endurance, breakpoint).
- Edit thread group settings and throughput profiles in each `*-test.jmx` to match your requirements.
- For CSV data, point CSV Data Set Config to files in `data/`.

## View reports
- Open `reports/<test>-html/index.html` in a browser after a run.
- For quick stats, read the corresponding `reports/<test>.jtl` in JMeter’s Summary Report or command-line parsers.

## Correlation approach
- Example in baseline: JSON Extractor captures `userId` from the first sampler and reuses it as a query param in the second sampler.
- Add extractors (JSONPath/Regex) immediately after samplers; store variables; reference via `${varName}` in subsequent requests or assertions.
- Validate correlations with assertions on both response codes and key payload fields.

## CI/CD integration
No pipeline ships with this repository yet.

When adding one, build and unit-test in CI (`mvn clean verify`) but think twice before generating
load from a shared CI runner: results are noisy and the runner is not sized for it. Run load from a
dedicated host and publish `reports/` from there.

## Best practices
- Keep tests deterministic: control data with CSVs, set think times explicitly, and avoid hidden retries.
- Warm up systems before measuring steady-state metrics.
- Use realistic pacing and arrival patterns (Throughput Shaping Timer) instead of pure thread counts.
- Track SLAs (latency percentiles, error rate, throughput) and assert them in tests.
- Separate smoke/baseline/load/stress plans; parameterize environment URLs and credentials via user-defined variables. Avoid running load on production; use dev/staging configs.
- Version control test data and plugins; pin JMeter/Plugin versions across local and CI.

## Troubleshooting
- Log4j warnings: ensure `bin/log4j2.xml` is present and `log4j2.configurationFile` resolves to it.
- Report generation: if in-process generation fails, a CLI fallback is attempted; ensure `jmeter` is on PATH or fix in-process configs.
- Plugin dependencies: the shaded JAR bundles required plugins; if samplers fail to load, verify Maven build completes successfully.

## Why this boilerplate
- One-command Java runner with environment-driven configuration, health checks, and deterministic suite resolution.
- Per-run artifacts (JTL/HTML/zip) ready for CI publishing.
- Template plans plus a working baseline plan.
- Optional SMTP reporting; auto-open reports locally for fast feedback.

## Docs
- Architecture: `docs/architecture.md`
- Changelog: `CHANGELOG.md`
- Contributing: `CONTRIBUTING.md`
- License: `LICENSE`

## After you publish
- Add GitHub topics: `jmeter`, `performance-testing`, `load-testing`, `java`, `jmeter-plugins`.
- Consider adding a sample report screenshot in `docs/` for visitors to preview without running.

## Contributing / Stars
- Open issues/PRs are welcome; add small reproducible cases for failures.
- If this saved you time, star the repo and share a short write-up (blog/LinkedIn) linking back here. It helps others find it.
