# AGENTS Guidance

- Keep changes minimal and task-focused.
- Do not change runtime behavior unless explicitly requested.
- Prefer focused validation for touched areas only.

## Cross-Tool Run Rules (Cursor, Codex, Claude Code)

- Scope: touch only files needed for the requested task.
- Planning: share a short plan before major edits; then execute.
- Safety: never run destructive commands (`git reset --hard`, broad `rm`) unless explicitly asked.
- Validation: run the smallest relevant checks for changed files only.
- Git hygiene: use a feature branch, keep commits focused, and open PRs against `main`.
- Diffs: keep patches reviewable; avoid unrelated formatting/refactor churn.
- Secrets: never commit credentials, tokens, or local machine artifacts.
- Handoff: include what changed, what was validated, and any known risks/gaps.
