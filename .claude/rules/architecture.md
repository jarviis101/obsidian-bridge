# Architecture Rules

## Package Structure

All production code lives under `dev.jarviis.obsidian` in `src/main/kotlin/`. The package is organized into strict vertical layers — never import downward from a higher layer into a lower one.

```
dev.jarviis.obsidian
├── model/          # Pure data classes. No IntelliJ or IO dependencies.
├── parser/         # Stateless parsers. Input: String/File. Output: model objects.
├── vault/          # Vault lifecycle: discovery, indexing, file watching.
├── psi/            # IntelliJ PSI/reference/completion extensions.
├── bridge/         # TODO/FIXME ↔ note linking (LineMarkerProvider, etc.).
├── toolwindow/     # Tool window factories and their Swing/JCEF panels.
│   ├── backlinks/
│   ├── graph/      # JCEF + D3.js graph view
│   ├── search/
│   └── tags/
├── actions/        # AnAction subclasses (Daily Note, etc.).
├── settings/       # PersistentStateComponent + Configurable.
└── ObsidianBundle.kt  # Single DynamicBundle for all localized strings.
```

**Dependency direction**: `model` ← `parser` ← `vault` ← `psi/bridge/toolwindow/actions/settings`

## Service Architecture

- **`VaultManager`** – Application-level service (`@Service(Service.Level.APP)`). Owns multi-vault registration, produces `VaultIndex` per vault. Single source of truth for vault locations.
- **`VaultIndex`** – Per-vault index. Holds all notes, resolved backlinks, tag maps, frontmatter cache. Rebuilt on vault open; updated incrementally via `VaultWatcher`.
- **`VaultWatcher`** – Uses IntelliJ `VirtualFileManager.addVirtualFileListener` (not raw `java.nio.file.WatchService`) so IDE-aware events fire correctly.
- **`ProjectVaultSettings`** – Project-level service (`@Service(Service.Level.PROJECT)`) storing which vault(s) are associated with this project. Persisted via `@State`/`@Storage`.

Services are retrieved only via `service<T>()` or `project.service<T>()` — never instantiated directly.

## PSI / Language Extensions

Wiki-links (`[[target|alias]]`, `[[target#heading]]`) are treated as a custom `PsiReference` injected into Markdown files via `PsiReferenceContributor`. The `TextRange` covers only the inner content (excluding `[[` / `]]`). Resolution walks the `VaultIndex`; it never does live filesystem scans.

## Graph View

The graph tool window uses **JCEF** (`JBCefBrowser`). D3.js is bundled as a plugin resource (not fetched from CDN at runtime). Communication between Kotlin and JS uses `JBCefJSQuery` for Kotlin→JS and `window.__bridge.postMessage` for JS→Kotlin callbacks.

## Threading

- Index builds run on `AppExecutorUtil.getAppExecutorService()` (background).
- All IntelliJ PSI/UI mutations happen on the EDT via `ApplicationManager.getApplication().invokeLater { ... }`.
- `VaultIndex` reads are safe from any thread; writes are guarded by `ReadWriteLock`.

## Localization

All user-visible strings go through `ObsidianBundle`. No hardcoded English strings in UI code. Resource file: `src/main/resources/messages/ObsidianBundle.properties`.
