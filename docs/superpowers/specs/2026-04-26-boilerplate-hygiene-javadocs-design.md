# Boilerplate Hygiene + JavaDoc Clarity Design

**Date**: 2026-04-26  
**Status**: Approved (pre-implementation)

## Goal

Apply minimal, boilerplate-friendly improvements:

1. Add repository agent ignore support.
2. Add a root agent guidance file.
3. Add concise JavaDoc comments in core runner code explaining class/method responsibilities.

No runtime behavior changes are included in this design.

## Scope

### In Scope

- Add `.agentignore` at repo root with practical local/generated entries.
- Add `AGENTS.md` at repo root with short guidance for future agent edits.
- Add class-level and selected method-level JavaDoc in:
  - `src/main/java/com/jmeter/suite/JMeterTestRunner.java`

### Out of Scope

- Refactoring class boundaries or moving logic across files.
- Functional behavior changes.
- CI/CD pipeline changes.
- Test plan (`.jmx`) behavior changes.

## Design

### 1) `.agentignore`

Create a simple ignore file for common generated/local artifacts so agent tooling does not treat them as meaningful project inputs.

Proposed entries include:
- `target/`
- `reports/`
- `logs/`
- `.idea/`
- `*.log`

### 2) `AGENTS.md`

Add a lightweight root guidance file with concise repo rules, focused on:
- Keep changes minimal and task-focused.
- Prefer no behavior changes unless requested.
- Validate only what is directly affected.

### 3) JavaDoc additions (`JMeterTestRunner`)

Add short JavaDocs that answer “what this class/method is responsible for”.

Planned comment targets:
- Class: `JMeterTestRunner`
- Methods:
  - `run()`
  - `performHealthCheck()`
  - `runPlan(PlanDefinition plan)`
  - `generateReport(PlanDefinition plan, ReportArtifactPaths artifacts, ResultCollector collector)`
  - `applyEnvironmentOverrides(HashTree tree)`
  - `readExecutionStats(Path jtlPath)`

Comment style:
- 1–3 lines each.
- Responsibility + important side effect.
- No placeholder text.

## Risks and Mitigations

- **Risk:** Over-commenting adds noise.  
  **Mitigation:** Limit to orchestration methods only.

- **Risk:** Comments drift from behavior later.  
  **Mitigation:** Keep wording descriptive and non-speculative.

## Validation Plan

- Ensure new files are present at root: `.agentignore`, `AGENTS.md`.
- Build/test run is optional because no behavior changes; if run, it should remain unchanged.
- Verify Java compiles if a build is executed later.

## Acceptance Criteria

- `.agentignore` exists with core generated/local patterns.
- `AGENTS.md` exists with concise repo guidance.
- `JMeterTestRunner` has clear JavaDoc at class level and for the listed key methods.
- No logic/behavioral diffs beyond comments and added text files.
