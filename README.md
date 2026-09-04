# JMeter Performance Suite

JMeter performance test boilerplate with a Java runner, parameterized plans, and per-run artifacts.

## Overview
- Baseline plan: `test-plans/baseline.jmx` (provided real test using BlazeMeter Concurrency Thread Group, Throughput Shaping Timer, JSON extractor, and assertions).
- Templates: spike, stress, endurance, and breakpoint plans with Concurrency Thread Group, load profiles, and working HTTP samplers.
- Java runner: executes JMX via embedded JMeter + plugin libraries with health checks, report packaging, and optional email delivery.
- CI/CD: not included yet. See "CI/CD integration" below.

## Folder structure
- `test-plans/` JMX files (baseline + templates)
- `reports/` JTL outputs and generated HTML reports (kept empty with .gitkeep)
- `scripts/` Java runner wrapper (`run-java.sh`)
- `config/environments/` env properties (dev/staging/ci provided, plus `prod.properties.example`)
- `config/suites.properties` suite groupings (`all`, `quick`, `load`)
- `bin/` JMeter runtime config; `bin/report-template/` is fetched, not committed
- `.github/workflows/` CI pipeline
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
`.github/workflows/jmeter.yml` runs two jobs on every push and pull request:

- **build** — `mvn clean verify` (compile + unit tests), uploading the runnable jar.
- **smoke** — starts `scripts/mock-target.py` on localhost, runs the baseline plan against it via
  the `ci` environment, and asserts that a JTL with samples and an HTML report were produced.
  Results are uploaded as artifacts.

Load is generated against a local mock, never a third-party host. A shared runner is not sized for
real load generation, so the smoke job proves the pipeline works rather than measuring performance
— its SLA gates are correctness-only (`p95_response_time_ms=0`, `min_throughput_tps=0`). For real
numbers, run from a dedicated host and publish `reports/` from there.

## Adding a test plan
Drop a `.jmx` into `test-plans/`. The id is the filename without the extension and without any
trailing `-test`, so `soak-test.jmx` becomes `soak` and is immediately runnable:

```
./scripts/run-java.sh dev soak
```

To include it in a grouping, add its id to `config/suites.properties`. No code change, no rebuild.

## Tuning load per environment
Thread groups read `${__P(<plan>.<setting>,<default>)}`, so any environment file can override load
without touching the JMX:

```
stress.target_level=250
stress.hold_time=600
```

Settings are `target_level`, `ramp_up`, `steps`, and `hold_time`. The JMX default applies when a
property is unset.

## Trending results
Each run writes a standalone JTL, which means no cross-run comparison by default. To accumulate
results into a time series, enable metric streaming in an environment file:

```
metrics.enabled=true
metrics.classname=org.apache.jmeter.visualizers.backend.influxdb.InfluxdbBackendListenerClient
metrics.arg.influxdbUrl=http://localhost:8086/write?db=jmeter
metrics.arg.application=checkout-api
```

Every `metrics.arg.*` entry is passed to the backend client verbatim, so Graphite or a custom
client works the same way. Runs are tagged with the plan and environment.

## HTML reports
The dashboard needs JMeter's FreeMarker templates, which ship with the JMeter distribution rather
than the Maven artifacts. Install them once:

```
./scripts/fetch-report-template.sh
```

Without them the suite still runs and still enforces its SLA gates; only the HTML dashboard is
skipped.

## Scaling past one machine
The runner executes in a single JVM by default, which tops out in the low thousands of threads.
Beyond that you are measuring the load generator rather than the target. Point the run at remote
hosts running `jmeter-server`:

```
remote.hosts=10.0.0.1:1099,10.0.0.2:1099
server.rmi.ssl.disable=true
```

Every property in the environment file is forwarded to the remote engines, so `host`, `port` and
the per-plan load settings apply there too. Results still stream back to this machine and land in
`reports/` as usual.

Note that the remote hosts need any JMeter plugins your plan uses. `test-plans/smoke.jmx` uses only
built-in elements, so it runs against a stock `jmeter-server` with nothing extra installed; the
other plans need the BlazeMeter plugins.

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
