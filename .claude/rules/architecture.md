# Architecture

## Package Layout

```
dev.jarviis.obsidian
├── model/        # Pure data classes: ObsidianNote, WikiLink, Frontmatter
├── parser/       # Stateless parsers: WikiLinkParser, FrontmatterParser, TemplateEngine
├── vault/        # VaultManager, VaultIndex, VaultScanner, VaultWatcher, VaultDetector
├── psi/          # Completion, references, folding, editor listeners
├── bridge/       # TodoBridgeLineMarkerProvider — gutter icons for TODO/FIXME [[links]]
├── toolwindow/
│   ├── backlinks/  # Backlinks panel
│   └── graph/      # Force-directed graph (pure Swing/Java2D)
├── actions/      # AnAction subclasses: DailyNoteAction, OpenInObsidianAction
├── settings/     # AppVaultSettings, ProjectVaultSettings, ProjectSettingsConfigurable
├── startup/      # ObsidianStartupActivity
└── ObsidianBundle.kt
```

**Dependency direction (strict):** `model` ← `parser` ← `vault` ← `psi / bridge / toolwindow / actions / settings`

Never import from a higher layer into a lower one. `model` and `parser` must have zero IntelliJ dependencies.

## Key Services

| Service | Level | Role |
|---|---|---|
| `VaultManager` | APP | Owns all vault registrations, produces `VaultIndex` per vault |
| `VaultIndex` | per-vault | Notes, backlinks, tags, frontmatter cache; thread-safe via `ReadWriteLock` |
| `ProjectVaultSettings` | PROJECT | Stores which vault is linked to this project |

Services are always retrieved via `service<T>()` or `project.service<T>()` — never instantiated directly.

## Critical Invariants

- **`VaultIndex.upsert()` must NOT call `backlinks.remove(path)`** — backlinks to a note are owned by other notes. Calling remove wipes backlinks every time IntelliJ fires a file-change event on open.
- **All path suffixes are indexed** in `allKeys()`. A note at `a/b/c.md` is findable by `c`, `b/c`, and `a/b/c`. This matches Obsidian's path-qualified resolution.
- `VaultManager.indices` is keyed by `rootPathString`, not vault name — two projects with identically-named vaults get independent indices.
- Wiki-link `\|` in Markdown table cells must be unescaped before parsing: `replace("\\|", "|")`.

## Threading

- Index builds run on `AppExecutorUtil.getAppExecutorService()` (background thread).
- All PSI/UI mutations run on EDT via `ApplicationManager.getApplication().invokeLater { }`.
- `VaultIndex` reads are safe from any thread; writes are guarded by `ReadWriteLock`.

## Localization

All user-visible strings go through `ObsidianBundle` (resource: `messages/ObsidianBundle.properties`). No hardcoded English strings in UI code.
