---
category: Architecture
---

# JMeterTestRunner

Entry point of the suite: `main(environment, suite)` in `src/main/java/com/jmeter/suite/JMeterTestRunner.java`.

Orchestrates the full run:

1. Parses CLI args via [[runner-args]] (environment, suite, health-check flag).
2. Loads `config/jmeter.properties` merged with the selected environment file through [[environment-config]].
3. Pushes environment values (host, protocol, load params, `auto_open_reports`, SMTP settings) into JMeter properties.
4. Runs an optional HTTP health check against the target host (`SKIP_HEALTH_CHECK=true` or `--skip-health-check` bypasses it).
5. Resolves which `.jmx` plan(s) to execute via [[plan-definition]] and runs them through JMeter's embedded engine.
6. Writes JTL results, generates the HTML report (falling back to invoking the `jmeter` CLI if in-process generation fails), and computes paths via [[report-artifact-paths]].
7. Zips the report, optionally emails it via `jakarta.mail` (`SMTP_HOST`/`SMTP_TO`/`SMTP_USER`/`SMTP_PASS`), and optionally auto-opens it.

Invoked by [[run-java-script]] after Maven builds the shaded JAR, and by [[ci-cd-pipeline]] in CI.
