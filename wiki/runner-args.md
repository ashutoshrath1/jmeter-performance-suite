---
category: Architecture
---

# RunnerArgs

`src/main/java/com/jmeter/suite/config/RunnerArgs.java` — parses and defaults the CLI arguments consumed by [[jmeter-test-runner]]: environment (`dev`/`staging`/`prod`), suite (`all`/`quick`/`load`/a single plan name), and the health-check skip flag.

Direct invocation after building: `java -jar target/jmeter-performance-suite-1.0.0.jar <environment> <suite>`. [[run-java-script]] wraps this with the Maven build step.
