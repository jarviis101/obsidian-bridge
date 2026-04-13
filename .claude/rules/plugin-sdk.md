# IntelliJ Plugin SDK

## Target

- `sinceBuild = 253` (2025.3+)
- Platform dependency: `com.intellij.modules.platform` only
- Compatible IDEs: IntelliJ IDEA, PhpStorm, WebStorm, PyCharm, CLion, GoLand, Rider, RubyMine, Android Studio
- JVM 21, Kotlin 2.2.x

## Plugin Structure

```
src/main/
├── kotlin/dev/jarviis/obsidian/   # All Kotlin source
└── resources/
    ├── META-INF/plugin.xml        # Extension point registrations
    ├── messages/ObsidianBundle.properties
    └── icons/                     # SVG icons 16×16 + @2x, dark variants suffixed _dark
```

## plugin.xml — Extension Point Groups

Always group extensions with XML comments in this order:

```xml
<!-- Startup -->
<postStartupActivity />

<!-- Services -->
<applicationService />
<projectService />

<!-- PSI / References -->
<psi.referenceContributor language="Markdown" />
<completion.contributor order="first" />   <!-- no language attr = all languages -->
<lang.foldingBuilder language="Markdown" />
<typedHandler />                           <!-- fires on every keypress, no language filter -->

<!-- Tool Windows -->
<toolWindow />

<!-- Editor -->
<lineMarkerProvider language="..." />      <!-- one entry per language -->

<!-- Settings -->
<projectConfigurable />
```

**Completion contributor registration:** `language="any"` attribute may be silently ignored by the platform for non-bundled languages. Register without language and use `extend(CompletionType.BASIC, psiElement(), provider)` to match all PSI elements; filter inside the provider.

**Line marker provider:** requires explicit `language` attribute per language.

## Banned APIs → Modern Replacements

| Banned | Use instead |
|--------|-------------|
| `ServiceManager.getService(...)` | `service<T>()` / `project.service<T>()` |
| `ToolWindowManager.registerToolWindow(String, ...)` | Declarative `<toolWindow>` in plugin.xml |
| `StartupActivity` (old) | `ProjectActivity` |
| `ContentFactory.SERVICE.getInstance()` | `ContentFactory.getInstance()` |
| `VirtualFileListener` (deprecated) | `AsyncFileListener` |
| Raw `javax.swing.*` layouts | IntelliJ UI DSL `panel { }` or `JBPanel` |
| `Messages.showMessageDialog` off-EDT | Always call UI from EDT |

## Services

```kotlin
// Application-level
@Service(Service.Level.APP)
class VaultManager : Disposable { ... }

// Project-level
@Service(Service.Level.PROJECT)
class ProjectVaultSettings(val project: Project) : SimplePersistentStateComponent<State>(State()) { ... }

// Retrieval
val manager = service<VaultManager>()
val settings = project.service<ProjectVaultSettings>()
```

## Completion Contributor

```kotlin
class MyContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(p: CompletionParameters, ctx: ProcessingContext, result: CompletionResultSet) {
                    // filter early, then result.addElement(...)
                    result.stopHere()
                }
            }
        )
    }
}
```

## Typed Handler (auto-popup)

```kotlin
class MyTypedHandler : TypedHandlerDelegate() {
    override fun checkAutoPopup(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (/* condition */) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        }
        return Result.CONTINUE  // never STOP — allows other handlers to process
    }
}
```

## Read/Write Actions

```kotlin
// PSI read off-EDT
val result = ReadAction.compute<ReturnType, Throwable> { /* PSI access */ }

// PSI write
WriteCommandAction.runWriteCommandAction(project) { /* mutations */ }
```

## Line Marker Provider

```kotlin
class MyProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.firstChild != null) return null  // only leaf elements
        // ... return LineMarkerInfo(...) or null
    }
}
```

## Persistence

```kotlin
@State(name = "MySettings", storages = [Storage("my-settings.xml")])
class MySettings : SimplePersistentStateComponent<MySettings.State>(State()) {
    class State : BaseState() {
        var vaultPath: String by string()
    }
}
```

Store paths as `String`, not `VirtualFile` — VF references don't survive restarts.

## UI

- Settings panels: IntelliJ UI DSL (`panel { row { ... } }`)
- Tool windows: extend `SimpleToolWindowPanel` or use `JBPanel`
- Lists: `JBList` + `CollectionListModel`
- Trees: `com.intellij.ui.treeStructure.Tree` + `TreeModel`

## Graph View (Swing/Java2D)

The graph tool window is **pure Swing/Java2D** — no JCEF dependency. `GraphPanel` renders nodes and edges directly using `Graphics2D`. Force layout runs on a background thread; rendering happens on EDT.

## Testing

```kotlin
// Light fixture (most tests)
class MyTest : BasePlatformTestCase() {
    fun `test something`() {
        myFixture.addFileToProject("Note.md", "# Note\n[[Other]]")
        // ...
    }
}

// Pure logic — plain JUnit, no fixture needed
class ParserTest {
    @Test fun `parses link`() { ... }
}
```
