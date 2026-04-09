# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Obsidian IDE Bridge** — a JetBrains plugin that integrates an Obsidian vault into the IDE. Features: wiki-link navigation/autocompletion, backlinks panel, tag tree, vault-wide full-text search, YAML frontmatter display, interactive D3.js graph view, TODO/FIXME ↔ note bridging, daily notes, and templates. Compatible with all major JetBrains IDEs.

Plugin ID: `dev.jarviis.obsidian.obsidian-bridge` | Package: `dev.jarviis.obsidian`

## Commands

```bash
./gradlew runIde          # Launch sandboxed IDE with plugin loaded
./gradlew build           # Compile + package plugin ZIP
./gradlew test            # Run all tests
./gradlew test --tests "dev.jarviis.obsidian.parser.WikiLinkParserTest"  # Single test class
./gradlew verifyPlugin    # Check compatibility against target IDEs
./gradlew publishPlugin   # Publish to JetBrains Marketplace (requires token)
```

Pre-configured Run/Debug configurations are in `.run/`.

## Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.2.x, JVM 21 |
| Build | Gradle 9.2.1 + IntelliJ Platform Gradle Plugin v2 |
| Platform | IntelliJ Platform SDK, `sinceBuild=253` (2025.3+) |
| Graph UI | JCEF (`JBCefBrowser`) + D3.js (bundled, no CDN) |
| YAML | SnakeYAML (on IntelliJ classpath — no extra dep needed) |
| Date/Time | `java.time` only |

## Architecture

Detailed rules live in `.claude/rules/`:
- **`architecture.md`** — package layout, service design, threading model
- **`intellij-platform.md`** — extension points, banned APIs, UI components, testing
- **`obsidian-domain.md`** — wiki-link syntax, frontmatter, backlinks, daily notes, templates, TODO bridge

### Layer Summary

```
model/ → parser/ → vault/ → psi/ / bridge/ / toolwindow/ / actions/ / settings/
```

- **`model/`** — pure data classes (`ObsidianNote`, `WikiLink`, `Frontmatter`). No IntelliJ or IO dependencies.
- **`parser/`** — stateless: `WikiLinkParser`, `FrontmatterParser`, `TagParser`, `TemplateEngine`. Input strings, output model objects.
- **`vault/`** — `VaultManager` (app service), `VaultIndex` (per-vault, thread-safe), `VaultScanner`, `VaultWatcher`.
- **`psi/`** — `WikiLinkReferenceContributor`, `WikiLinkReference`, `WikiLinkCompletionContributor`. References resolve via `VaultIndex`, never live FS scans.
- **`bridge/`** — `TodoBridgeLineMarker` (gutter icons on TODO/FIXME lines linking to notes).
- **`toolwindow/`** — four tool windows: `backlinks/`, `graph/` (JCEF+D3), `search/`, `tags/`.
- **`settings/`** — `VaultSettings` (`PersistentStateComponent`), `SettingsConfigurable`.
- **`actions/`** — `DailyNoteAction`, `OpenInObsidianAction`, etc.

### Key Design Decisions

- `VaultIndex` is rebuilt on vault open and updated **incrementally** (not full rebuild) on file change events from `VaultWatcher`.
- Wiki-link resolution follows Obsidian's rules: case-insensitive match → same-folder preference → shortest path. Aliases from frontmatter are indexed.
- Graph view checks `JBCefApp.isSupported()` and shows a fallback message if JCEF is unavailable.
- Obsidian URI (`obsidian://open?vault=...&file=...`) used for "Open in Obsidian" actions via `Desktop.browse()`.
- All user-visible strings go through `ObsidianBundle` (replaces the template's `MyMessageBundle`).

## plugin.xml Extension Point Categories

Extensions are grouped in `plugin.xml` by: Services → PSI/References → Tool Windows → Editor → Actions → Settings.
