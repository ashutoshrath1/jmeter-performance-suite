---
category: Configuration
---

# Environments (dev / staging / prod)

Three environment property files live under `config/environments/`: `dev`, `staging`, and `prod`. `prod.properties` is not checked in — copy `prod.properties.example` to `prod.properties` and fill in real values before using it.

Each file supplies the host/protocol/load parameters and operational flags (`auto_open_reports`, SMTP settings) that [[environment-config]] merges with the shared `config/jmeter.properties` before [[jmeter-test-runner]] pushes them into JMeter.

Selected on the command line via [[runner-args]], e.g. `./scripts/run-java.sh staging all` — see [[run-java-script]].
