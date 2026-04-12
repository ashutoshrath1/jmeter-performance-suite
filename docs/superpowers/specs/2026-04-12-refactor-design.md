# JMeter Performance Suite — Refactor Design

**Date**: 2026-04-12
**Status**: Approved

## Problem

`JMeterTestRunner.java` is a 517-line god class with 6+ distinct responsibilities: JMeter initialization, health checking, test plan execution, report generation, email delivery, ZIP archiving, browser opening, and JTL parsing. This makes the class hard to understand, modify, or test in isolation.

## Approach

**Thin Orchestrator + Extracted Services via Manual DI**

Break the monolith into a thin orchestrator (~50 lines) that wires 7 focused service classes through constructor injection. No DI framework — just constructors in `main()`.

## Extracted Classes

| Class | Package | Responsibility |
|---|---|---|
| `JMeterInitializer` | `execution` | JMeter engine setup, log4j config, property loading, SaveService init |
| `HealthChecker` | `execution` | HTTP GET against target host, respects `SKIP_HEALTH_CHECK` env var and config |
| `TestPlanExecutor` | `execution` | Load JMX, apply host/protocol overrides, remove result collectors, attach collector, run engine |
| `ExecutionStatsReader` | `execution` | Parse JTL file, count samples and errors, compute error rate |
| `ReportGenerator` | `report` | In-process HTML generation, CLI fallback, ZIP archiving, delegates email/browser |
| `EmailReporter` | `report` | Build SMTP session, send MIME message with ZIP attachment |
| `BrowserOpener` | `report` | OS-detect and open index.html in default browser |
| `ExecutionStats` | `model` | Promoted from inner class — value object for sample count, error count, error rate |

Unchanged classes: `RunnerArgs`, `EnvironmentConfig`, `PlanDefinition`, `ReportArtifactPaths`.

## Wiring (main method)

```java
public static void main(String[] args) {
    RunnerArgs runnerArgs = RunnerArgs.from(args);
    JMeterInitializer initializer = new JMeterInitializer();
    HealthChecker healthChecker = new HealthChecker();
    ExecutionStatsReader statsReader = new ExecutionStatsReader();
    EmailReporter emailReporter = new EmailReporter();
    BrowserOpener browserOpener = new BrowserOpener();
    ReportGenerator reportGen = new ReportGenerator(emailReporter, browserOpener);
    TestPlanExecutor executor = new TestPlanExecutor(statsReader);

    JMeterTestRunner runner = new JMeterTestRunner(
        runnerArgs, initializer, healthChecker, executor, reportGen
    );
    System.exit(runner.run());
}
```

## Package Structure

```
com.jmeter.suite/
  JMeterTestRunner.java          (orchestrator only)
  config/
    RunnerArgs.java               (unchanged)
    EnvironmentConfig.java        (unchanged)
  model/
    PlanDefinition.java           (unchanged)
    ExecutionStats.java            (promoted from inner class)
  report/
    ReportArtifactPaths.java      (unchanged)
    ReportGenerator.java          (NEW)
    EmailReporter.java            (NEW)
    BrowserOpener.java            (NEW)
  execution/
    JMeterInitializer.java        (NEW)
    TestPlanExecutor.java         (NEW)
    HealthChecker.java            (NEW)
    ExecutionStatsReader.java     (NEW)
```

## Testing Strategy

| Class | Test approach |
|---|---|
| `JMeterInitializer` | Integration test (needs JMeter classpath) |
| `HealthChecker` | Mock `EnvironmentConfig`, test healthy/unhealthy/disabled paths |
| `TestPlanExecutor` | Integration test with JMeter engine (hard to mock fully) |
| `ExecutionStatsReader` | Create temp JTL files, verify parsing and stats calculation |
| `ReportGenerator` | Test ZIP generation with temp dirs, mock email/browser deps |
| `EmailReporter` | Test config-driven skip logic, mock session/transport |
| `BrowserOpener` | Test OS detection, mock ProcessBuilder |

Existing tests (`RunnerArgsTest`, `PlanDefinitionTest`) remain unchanged.

## Design Principles

- **Single Responsibility**: Each class has one clear purpose
- **Manual DI**: Dependencies passed via constructors, no framework
- **Testable in isolation**: Core logic (stats parsing, email config, browser opening) fully unit-testable without JMeter
- **Minimal public API**: Each service exposes only the methods the orchestrator needs
- **Preserve behavior**: No functional changes — same run flow, same outputs, same CLI interface