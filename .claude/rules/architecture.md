---
description: Architecture patterns, directory structure logic, and service naming
globs: ["src/**/*.kt", "src/**/*.java"]
---

# Architecture

## 1. Directory Structure

```
src/main/
├── java/dev/jarviis/obsidian/
│   └── toolwindow/
│       ├── graph/GraphToolWindowFactory.java
│       └── backlinks/BacklinksToolWindowFactory.java
│
├── kotlin/dev/jarviis/obsidian/
│   ├── model/          # ObsidianNote, WikiLink, Frontmatter, VaultDescriptor
│   ├── parser/         # WikiLinkParser, FrontmatterParser
│   ├── vault/          # VaultManager, VaultIndex, VaultScanner, VaultWatcher, VaultDetector
│   ├── psi/            # Completion, references, folding, typed handler
│   ├── bridge/         # TodoBridgeLineMarkerProvider
│   ├── toolwindow/
│   │   ├── backlinks/  # BacklinksToolWindowFactory, BacklinkCellRenderer
│   │   └── graph/      # GraphPanel (Swing/Java2D)
│   ├── actions/        # OpenInObsidianAction
│   ├── settings/       # AppVaultSettings, ProjectVaultSettings, Configurables
│   ├── startup/        # ObsidianStartupActivity
│   ├── ObsidianBundle.kt
│   └── ObsidianIcons.kt
│
└── resources/
    ├── META-INF/plugin.xml
    ├── messages/ObsidianBundle.properties
    └── icons/
```

## 2. Layer Dependencies (Strict)

```
model → parser → vault → psi / bridge / toolwindow / actions / settings
```

- `model` and `parser`: zero IntelliJ dependencies. Pure Kotlin/Java only.
- Never import from a higher layer into a lower one.
- `psi`, `bridge`, `toolwindow`, `actions`, `settings` may all import from `vault` and below — not from each other.

## 3. Class Responsibilities

| Type            | Responsibility                                                                       |
|-----------------|--------------------------------------------------------------------------------------|
| `*Manager`      | App-level service. Owns lifecycle and coordinates indices. Only one: `VaultManager`. |
| `*Index`        | Per-vault in-memory store. Reads safe from any thread; writes via `ReadWriteLock`.   |
| `*Scanner`      | One-shot file system traversal. Returns data, holds no state.                        |
| `*Watcher`      | Long-lived listener. Fires incremental updates into `VaultIndex`.                    |
| `*Detector`     | Heuristic discovery logic (e.g. find vault root). Stateless.                         |
| `*Parser`       | Stateless. Input: `String`. Output: model objects. No IO, no IntelliJ.               |
| `*Contributor`  | IntelliJ extension. Registers references or completions into the platform.           |
| `*Provider`     | IntelliJ extension. Supplies line markers, documentation, etc.                       |
| `*Configurable` | IntelliJ settings UI. Thin — delegates to `*Settings` for persistence.               |

## 4. Key Services

| Service                | Level     | Role                                                                            |
|------------------------|-----------|---------------------------------------------------------------------------------|
| `VaultManager`         | APP       | Single source of truth for vault registrations; produces `VaultIndex` per vault |
| `VaultIndex`           | per-vault | Notes, backlinks, tags, frontmatter cache                                       |
| `AppVaultSettings`     | APP       | Persists global vault list                                                      |
| `ProjectVaultSettings` | PROJECT   | Persists which vault is linked to this project                                  |

Services are always retrieved via `service<T>()` or `project.service<T>()` — never instantiated directly.

## 5. Critical Invariants

- **`VaultIndex.upsert()` must NOT call `backlinks.remove(path)`** — backlinks to a note are owned by other notes. Removing them on upsert wipes backlinks every time IntelliJ fires a file-change event.
- **All path suffixes are indexed.** A note at `a/b/c.md` is findable by `c`, `b/c`, and `a/b/c`. Matches Obsidian's path-qualified link resolution.
- **`VaultManager.indices` is keyed by `rootPathString`**, not vault name — two projects with same-named vaults get independent indices.
- **Wiki-link `\|` in Markdown table cells** must be unescaped before parsing: `replace("\\|", "|")`.

## 6. Threading

- Index builds: `AppExecutorUtil.getAppExecutorService()` (background).
- All PSI/UI mutations: EDT via `ApplicationManager.getApplication().invokeLater { }`.
- `VaultIndex` reads: safe from any thread. Writes: guarded by `ReadWriteLock`.

## 7. Localization

All user-visible strings go through `ObsidianBundle`. No hardcoded English strings in UI code.
Resource file: `src/main/resources/messages/ObsidianBundle.properties`.
