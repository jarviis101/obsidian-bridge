# IntelliJ Platform Rules

## Target Platform

- Plugin ID: `dev.jarviis.obsidian.obsidian-bridge`
- `sinceBuild = 253` (IntelliJ IDEA 2025.3+)
- Compatible IDEs: IntelliJ IDEA, WebStorm, PyCharm, CLion, GoLand, PhpStorm, Rider, RubyMine, Android Studio
- Platform dependency: `com.intellij.modules.platform` only — no Java/Kotlin plugin dependency unless unavoidable
- JVM toolchain: 21
- Kotlin: 2.2.x

## Extension Points (plugin.xml)

Register all extensions in `plugin.xml`. Group them with XML comments by category. Required categories for this plugin:

```xml
<!-- Services -->
<applicationService />
<projectService />

<!-- PSI / References -->
<psi.referenceContributor />
<completion.contributor />
<lang.documentationProvider />
<gotoDeclarationHandler />
<annotator />

<!-- Tool Windows -->
<toolWindow /> <!-- one per panel: Backlinks, Graph, Search, Tags -->

<!-- Editor -->
<editorNotificationProvider />
<lineMarkerProvider />

<!-- Actions -->
<action /> <!-- Daily Note, Open in Obsidian, etc. -->

<!-- Settings -->
<applicationConfigurable />
<projectConfigurable />
```

## Deprecation Constraints

These APIs are banned — use the modern replacements:

| Banned | Use instead |
|--------|-------------|
| `ToolWindowManager.registerToolWindow(String, ...)` | Declarative `<toolWindow>` in plugin.xml |
| `ServiceManager.getService(...)` | `service<T>()` / `project.service<T>()` |
| `com.intellij.openapi.vfs.VirtualFileListener` (deprecated) | `AsyncFileListener` |
| Raw `javax.swing.*` layouts | `com.intellij.ui.components.*`, `UI DSL` (`panel { }`) |
| `Messages.showMessageDialog` on background thread | Always call from EDT |
| `StartupActivity` (old) | `StartupActivity.DumbAware` or `ProjectActivity` |
| `ContentFactory.SERVICE.getInstance()` | `ContentFactory.getInstance()` |

## UI Guidelines

- Use IntelliJ UI DSL (`com.intellij.ui.dsl.builder`) for settings panels — not manual `GridBagLayout`.
- Tool window panels extend `SimpleToolWindowPanel` or use `JBPanel`.
- Tree views use `com.intellij.ui.treeStructure.Tree` with `TreeModel`.
- Search/filter fields use `SearchTextField`.
- Lists use `JBList` with `CollectionListModel`.
- Icons: use `AllIcons.*` constants or place SVG icons in `src/main/resources/icons/` at 16×16 and 32×32 (dark variants suffixed `_dark`).

## Persistence

Settings stored via `@State` + `@Storage`. Use `SimplePersistentStateComponent<T>` with a typed state class. Store vault paths as strings, not `VirtualFile` (VF references don't survive restarts).

## Read/Write Actions

- Wrap all PSI reads in `ReadAction.compute { }` when off-EDT.
- Wrap all PSI writes in `WriteCommandAction.runWriteCommandAction(project) { }`.
- Never call `ApplicationManager.getApplication().runReadAction` inside a write action.

## JCEF (Graph View)

- Check `JBCefApp.isSupported()` before creating any `JBCefBrowser`; show a fallback message when unsupported.
- Load HTML from plugin resources (`javaClass.getResource("/graph/index.html")`), not from the filesystem.
- Bundle D3.js under `src/main/resources/graph/`. No external CDN calls.

## Testing

- Tests extend `BasePlatformTestCase` (light fixture) for PSI/reference tests.
- Heavy tests (full project fixture) extend `CodeInsightTestFixtureImpl` only when necessary.
- Test data goes in `src/test/testData/`.
- Mock vault directories via `myFixture.addFileToProject(...)` — no real filesystem paths in tests.
