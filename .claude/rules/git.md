# Git Commit Rules

## Pre-Commit Checklist

Before every `git commit`, run `/verify-plugin` to validate plugin compatibility.
Do not commit if `./gradlew verifyPlugin` fails.

## Commit Message Format

```
<type>: <short summary in imperative mood>

[optional body]
```

**Types:**
- `feat` — new feature
- `fix` — bug fix
- `refactor` — code restructure without behaviour change
- `test` — add or update tests
- `docs` — documentation only
- `chore` — build scripts, dependencies, config

**Rules:**
- Summary line: max 72 characters, no trailing period
- Imperative mood: "Add X", not "Added X" or "Adding X"
- English only
- No `Co-Authored-By` trailer unless explicitly requested

## Version Bumping

Version is defined in `build.gradle.kts` → `version`. Update it before release commits only.
Format: `MAJOR.MINOR.PATCH` (e.g. `1.2.0`).

## What Not to Commit

- Hardcoded local paths or IDE sandbox artifacts
- `build/`, `.idea/`, `*.iml` (covered by `.gitignore`)
- Credentials, tokens, or API keys
- Commented-out code or debug `println` / `System.err`
