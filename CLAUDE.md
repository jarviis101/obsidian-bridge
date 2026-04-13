# CLAUDE.md

**Obsidian Lens** — JetBrains plugin that integrates an Obsidian vault into the IDE.
Plugin ID: `dev.jarviis.obsidian.obsidian-bridge` | Package: `dev.jarviis.obsidian`

## Rules

Detailed rules are in `.claude/rules/`:
- **`architecture.md`** — package layout, layer dependencies, critical invariants
- **`code-standards.md`** — Kotlin style, patterns, what to avoid
- **`plugin-sdk.md`** — IntelliJ Platform APIs, extension points, banned APIs
- **`obsidian.md`** — Obsidian vault structure, wiki-link syntax, domain logic

## Commands

```bash
./gradlew runIde          # Launch sandboxed IDE with plugin loaded
./gradlew build           # Compile + package plugin ZIP
./gradlew test            # Run all tests
./gradlew verifyPlugin    # Check compatibility against target IDEs
./gradlew publishPlugin   # Publish to JetBrains Marketplace (requires token)
```

## Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.2.x, JVM 21 |
| Build | Gradle 9.2.1 + IntelliJ Platform Gradle Plugin v2 |
| Platform | IntelliJ Platform SDK, `sinceBuild=253` (2025.3+) |
| Graph UI | Pure Swing/Java2D, Fruchterman–Reingold layout |
| YAML | SnakeYAML (bundled with IntelliJ, no extra dep) |
| Date/Time | `java.time` only |
