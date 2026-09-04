---
category: Architecture
---

# PlanDefinition

`src/main/java/com/jmeter/suite/model/PlanDefinition.java` — maps suite names to the `.jmx` files under `test-plans/`:

- `all` → every plan
- `quick` → baseline only
- `load` → baseline + stress
- a single plan name (`baseline`, `spike`, `stress`, `endurance`, `breakpoint`) → just that one

Consumed by [[jmeter-test-runner]] after [[runner-args]] resolves the requested suite. See [[load-profile-suites]] for what each plan actually does and [[test-plan-pattern]] for how the plans are built.
