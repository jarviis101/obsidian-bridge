# AI Assistant Guide

You are working on **Obsidian Lens** — a JetBrains IDE plugin that bridges an Obsidian vault into the editor.
Your goal is to maintain architectural integrity, clean Kotlin code, and correct use of the IntelliJ Platform SDK.

Plugin ID: `dev.jarviis.obsidian.obsidian-bridge` | Package: `dev.jarviis.obsidian`

## 1. Core Rules 

- **Architecture**: @.claude/rules/architecture.md — package layout, layer dependencies, class responsibilities, critical invariants
- **Code standards**: @.claude/rules/code-standards.md — Kotlin style, naming, null safety, what to avoid
- **IntelliJ Plugin SDK**: @.claude/rules/plugin-sdk.md — when touching `plugin.xml`, extension points, IntelliJ APIs, or UI
- **Obsidian domain**: @.claude/rules/obsidian.md — when touching vault logic, wiki-link parsing, backlinks, or frontmatter

## 2. Commands

```bash
./gradlew runIde          # Launch sandboxed IDE with plugin loaded
./gradlew build           # Compile + package plugin ZIP
./gradlew test            # Run all tests
./gradlew verifyPlugin    # Check compatibility against target IDEs
./gradlew publishPlugin   # Publish to JetBrains Marketplace (requires token)
```

## 4. Stack

| Layer     | Technology                                        |
|-----------|---------------------------------------------------|
| Language  | Kotlin 2.2.x, JVM 21                              |
| Build     | Gradle 9.2.1 + IntelliJ Platform Gradle Plugin v2 |
| Platform  | IntelliJ Platform SDK, `sinceBuild=253` (2025.3+) |
| Graph UI  | Pure Swing/Java2D, Fruchterman–Reingold layout    |
| YAML      | SnakeYAML (bundled with IntelliJ, no extra dep)   |
| Date/Time | `java.time` only                                  |
