# Architecture Overview

## Runtime Flow
```mermaid
flowchart TD
  A[Run scripts/run-java.sh] --> B[Build shaded JAR]
  B --> C[Load jmeter.properties and environment config]
  C --> D[Push env vars into JMeter properties]
  D --> E[Health check target host]
  E --> F[Execute JMX via embedded JMeter engine]
  F --> G[JTL written to reports/<plan>-<ts>.jtl]
  G --> H[Generate HTML report]
  H --> I[Zip + optional email + optional auto-open]
```

## CI Pipeline (GitHub Actions / Jenkins)
```mermaid
flowchart LR
  A[Checkout] --> B[Build shaded JAR]
  B --> C[Run baseline suite]
  C --> D[Publish artifacts (JTL/HTML/zip)]
```

## Key Components
- **Java runner**: `src/main/java/com/jmeter/suite/JMeterTestRunner.java`
- **Runner/config model**: `src/main/java/com/jmeter/suite/config/*`, `src/main/java/com/jmeter/suite/model/*`
- **Configs**: `config/environments/*.properties`, `config/jmeter.properties`, `bin/*` (log4j2, saveservice)
- **Plans**: `test-plans/*` (baseline + templates)
- **CI**: `.github/workflows/jmeter.yml`, `ci-cd/Jenkinsfile`
- **Scripts**: `scripts/run-java.sh`
