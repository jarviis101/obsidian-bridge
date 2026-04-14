---
name: verify-plugin
description: >-
  Run ./gradlew verifyPlugin to validate IntelliJ plugin compatibility before committing.
  Checks binary compatibility against target IDE builds and reports any violations.
  Use before git commits, when the user says "verify plugin", "check compatibility",
  or "перевір плагін".
---

# Verify Plugin

Runs the IntelliJ Platform plugin verifier to catch binary compatibility issues
before they reach the repository. Always run this before `git commit`.

## When to Trigger

- User says "commit", "закомітити", "зробити коміт", or any variant
- User says "verify plugin", "перевір плагін", "check compatibility"
- Explicitly invoked as `/verify-plugin`

## Steps

1. Run the verifier:

```bash
./gradlew verifyPlugin
```

2. Parse the output:
   - `BUILD SUCCESSFUL` → proceed with the commit
   - `BUILD FAILED` → report errors, **do NOT commit**

## Output Format

### On success

```
verifyPlugin passed. Proceeding with commit.
```

### On failure

```
## verifyPlugin Failed — Commit Blocked

**Errors:**
[list each compatibility error with class/method name and IDE build]

Fix the issues above before committing.
```

## Rules

- Never skip this check, even for "small" or "trivial" changes.
- If `./gradlew verifyPlugin` exits non-zero, stop and report — do not run `git commit`.
- Do not suppress output — show the full error list to the user.