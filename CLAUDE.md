# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Obsidian IDE Bridge** — a JetBrains plugin that integrates an Obsidian vault into the IDE. Features: wiki-link navigation/autocompletion, backlinks panel (outgoing links + incoming backlinks), YAML frontmatter display, TODO/FIXME ↔ note bridging, daily notes, and templates. Compatible with all major JetBrains IDEs.

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
| Graph UI | JCEF (`JBCefBrowser`) + D3.js (bundled, no CDN) — **disabled, code preserved** |
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
- **`parser/`** — stateless: `WikiLinkParser`, `FrontmatterParser`, `TemplateEngine`. Input strings, output model objects.
- **`vault/`** — `VaultManager` (app service), `VaultIndex` (per-vault, thread-safe), `VaultScanner`, `VaultWatcher`.
- **`psi/`** — `WikiLinkReferenceContributor`, `WikiLinkReference`, `WikiLinkCompletionContributor`. References resolve via `VaultIndex`, never live FS scans.
- **`bridge/`** — `TodoBridgeLineMarker` (gutter icons on TODO/FIXME lines linking to notes).
- **`toolwindow/backlinks/`** — the only active tool window. Shows two sections: **Links** (outgoing — notes this file links to) and **Backlinks** (incoming — notes that link to this file). Both lists are clickable.
- **`toolwindow/graph/`** — JCEF + D3.js graph view. Code exists but the tool window is **commented out** in `plugin.xml`. Re-enable when ready.
- **`settings/`** — `AppVaultSettings` / `ProjectVaultSettings` (`PersistentStateComponent`), `SettingsConfigurable`.
- **`actions/`** — `DailyNoteAction`, `OpenInObsidianAction`.

### Key Design Decisions

- `VaultIndex` is rebuilt on vault open and updated **incrementally** on file change events from `VaultWatcher`.
- **`upsert()` must NOT call `backlinks.remove(path)`** — that slot is owned by other notes that link to this note. Only the actual `remove()` (file deletion) clears it. Violating this causes backlinks to drop to zero whenever IntelliJ triggers a file-change event on open.
- **All path suffixes are indexed** in `allKeys()`. A note at `modules/platform/platform.md` is findable by `platform`, `platform/platform`, and `modules/platform/platform`. This matches Obsidian's resolution of path-qualified links like `[[platform/platform]]`.
- Wiki-link `\|` in Markdown table cells is unescaped before parsing: `replace("\\|", "|")` in `WikiLinkParser`.
- Relative links (`./foo`, `../foo`) are resolved using `contextPath.parent.resolve(target).normalize()` with `.md` extension fallback.
- Graph view checks `JBCefApp.isSupported()` and shows a fallback message if JCEF is unavailable.
- Obsidian URI (`obsidian://open?vault=...&file=...`) used for "Open in Obsidian" actions via `Desktop.browse()`.
- All user-visible strings go through `ObsidianBundle`.

## Active Tool Windows (plugin.xml)

Only `Obsidian Backlinks` is registered. Tags, Search, and Graph tool windows are commented out.

## plugin.xml Extension Point Categories

Extensions are grouped in `plugin.xml` by: Services → PSI/References → Tool Windows → Editor → Actions → Settings.
