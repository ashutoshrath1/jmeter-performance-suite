# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Build and run tests:
```bash
mvn -q clean verify          # compiles, runs unit tests, and builds the shaded runner JAR
mvn -q test                  # unit tests only
mvn -q test -Dtest=RunnerArgsTest        # single test class
mvn -q test -Dtest=RunnerArgsTest#methodName   # single test method
```

Run the performance suite (builds the JAR via `mvn clean verify`, then executes it):
```bash
chmod +x scripts/run-java.sh              # first time only
./scripts/run-java.sh dev quick           # baseline plan only, dev environment
./scripts/run-java.sh staging all         # every plan
./scripts/run-java.sh dev baseline --skip-health-check
```
- Environments: `dev`, `staging`, `prod` (copy `config/environments/prod.properties.example` to `prod.properties` and fill in values before using).
- Suites: `all`, `quick` (baseline), `load` (baseline + stress), or a single plan name (`baseline`, `spike`, `stress`, `endurance`, `breakpoint`).
- Direct invocation after building: `java -jar target/jmeter-performance-suite-1.0.0.jar <environment> <suite>`.

Outputs land in `reports/<plan>-<timestamp>.jtl` and `reports/<plan>-<timestamp>-html/` (plus a zip). `auto_open_reports=true` in an environment properties file opens the HTML report automatically.

## Architecture

The suite is a Java runner (not the JMeter GUI) that drives Apache JMeter's embedded engine against parameterized `.jmx` test plans.

Flow: `scripts/run-java.sh` builds a shaded JAR via Maven → `JMeterTestRunner` loads `config/jmeter.properties` plus the selected `config/environments/<env>.properties` → env values (host, protocol, load params, `auto_open_reports`, SMTP settings) are pushed into JMeter properties → optional HTTP health check against the target host (`SKIP_HEALTH_CHECK=true` or `--skip-health-check` bypasses it) → JMX plan executes through the embedded engine → JTL results written → HTML report generated (falls back to invoking `jmeter` CLI in-process generation fails) → report is zipped, optionally emailed via `jakarta.mail` (`SMTP_HOST`/`SMTP_TO`/`SMTP_USER`/`SMTP_PASS`), optionally auto-opened.

Key classes (`src/main/java/com/jmeter/suite/`):
- `JMeterTestRunner` — entry point (`main(environment, suite)`), orchestrates the flow above and resolves the shaded JAR's main class.
- `config/EnvironmentConfig` — loads/merges `jmeter.properties` with the environment-specific properties file.
- `config/RunnerArgs` — parses/defaults CLI args (environment, suite, health-check flag).
- `model/PlanRegistry` — discovers `test-plans/*.jmx` and resolves suite names using `config/suites.properties`. Plan ids come from the filename minus the extension and any trailing `-test`. Adding a plan needs no code change.
- `model/PlanDefinition` — a plan's id plus its JMX path.
- `metrics/BackendListenerFactory` — builds a `BackendListener` from `metrics.*` environment properties so runs can stream into a time-series backend. Backend-agnostic: `metrics.arg.*` is passed to the client verbatim. Off unless `metrics.enabled=true`.
- `report/ReportArtifactPaths` — computes per-run JTL/HTML/zip paths under `reports/`.
- `report/JtlAnalyzer` + `report/ExecutionStats` — parse the JTL (columns resolved by header name, RFC4180 quoting) into sample/error counts, p95 latency, and throughput. Never split JTL lines on bare commas; JMeter quotes fields that contain them.

Test plans (`test-plans/*.jmx`) are JMeter designs using BlazeMeter's Concurrency Thread Group and Throughput Shaping Timer for load profiles, parameterized via JMeter property functions. Samplers read `${host}`/`${protocol}`/`${__P(port,)}`, and each thread group reads `${__P(<plan>.target_level,<default>)}` (plus `ramp_up`, `steps`, `hold_time`), so `config/environments/*.properties` can override load per plan while the JMX default preserves current behaviour. This requires the `ApacheJMeter_functions` dependency — without it every `${__...}` stays literal text. `baseline.jmx` demonstrates the correlation pattern used across plans: a JSON Extractor captures a value (e.g. `userId`) from one sampler's response and reuses it via `${varName}` in a later sampler/assertion — follow this pattern when adding new extractors.

`.github/workflows/jmeter.yml` runs unit tests, then a smoke job that starts `scripts/mock-target.py` on localhost and executes the baseline plan through the `ci` environment. Never point CI load at a real or third-party host.

Runtime files under `bin/` (JMeter home is the project root): `saveservice.properties` and `log4j2.xml` are committed and required — without them no `.jmx` will load. `reportgenerator.properties` is committed too. `bin/report-template/` is ~3MB of third-party assets, so it is gitignored and fetched via `scripts/fetch-report-template.sh`; without it the suite still runs and still gates, only the HTML dashboard is skipped.

A plan's pass/fail comes from its SLA gates alone. Report generation failures are logged and do not fail the plan.

## Cross-tool run rules (from AGENTS.md — apply to Claude Code too)

- Keep changes minimal and task-focused; touch only files needed for the requested task.
- Do not change runtime behavior unless explicitly requested.
- Share a short plan before major edits, then execute.
- Run only the smallest relevant checks for changed files (e.g. a single test class), not the full suite, unless asked otherwise.
- Use a feature branch, keep commits focused, open PRs against `main`.
- Never run destructive commands (`git reset --hard`, broad `rm`) unless explicitly asked.
