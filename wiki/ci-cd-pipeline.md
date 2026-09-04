---
category: Operations
---

# CI/CD

**No CI pipeline ships with this repository.** Earlier revisions of `README.md`, `CLAUDE.md` and
`docs/architecture.md` described `.github/workflows/jmeter.yml` and `ci-cd/Jenkinsfile`, but neither
file has ever existed in the history — the documentation was aspirational. Those claims have since
been corrected.

Runs are started manually through [[run-java-script]], which invokes [[jmeter-test-runner]].

When a pipeline is added, build and unit-test in CI (`mvn clean verify`), but generate load from a
dedicated host rather than a shared CI runner — shared runners are noisy neighbours and are not
sized for load generation. Publish the artifacts described in [[report-artifact-paths]] from that
host.
