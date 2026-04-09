# Obsidian Lens

A JetBrains plugin that brings your Obsidian vault into the IDE — navigate, link, and explore notes without leaving the editor.

## Features

- **Wiki-link rendering** — `[[Note|Alias]]` folds inline to its display name; click to open, Cmd+Click to edit
- **Wiki-link autocompletion** — type `[[` to get note name suggestions from the active vault
- **Backlinks panel** — shows outgoing links (this note → others) and incoming backlinks (others → this note)
- **Code ↔ Notes bridge** — gutter icons on `TODO/FIXME: [[Note]]` comments link directly to vault notes
- **Open in Obsidian** — jump from any note to the Obsidian app in one click
- **Per-project vault** — each project is linked to exactly one vault; auto-detected on first open, configurable via Settings

## Vault Setup

**Automatic** — when you open a project, the plugin scans the project directory for a `.obsidian/` folder and links it automatically. No manual configuration needed.

**Manual** — go to **Settings → Tools → Obsidian Lens** to register vaults globally, then **Settings → Tools → Obsidian Lens → Obsidian Vault** to select which vault is active for the current project.

## Stack

| Layer    | Technology                                              |
|----------|---------------------------------------------------------|
| Language | Kotlin 2.2.x + Java (factory layer), JVM 21             |
| Build    | Gradle 9.2.1 + IntelliJ Platform Gradle Plugin v2       |
| Platform | IntelliJ Platform SDK, `sinceBuild=253` (2025.3+)       |
| YAML     | SnakeYAML (on IntelliJ classpath — no extra dep needed) |

Compatible with IntelliJ IDEA, WebStorm, PyCharm, CLion, GoLand, PhpStorm, Rider, RubyMine, and Android Studio.

## Project Structure

```
src/
├── main/
│   ├── java/dev/jarviis/obsidian/
│   │   └── toolwindow/backlinks/   # Java factory (avoids Kotlin bridge warnings)
│   ├── kotlin/dev/jarviis/obsidian/
│   │   ├── model/                  # Pure data classes (ObsidianNote, WikiLink, Frontmatter)
│   │   ├── parser/                 # Stateless parsers (WikiLinkParser, FrontmatterParser)
│   │   ├── vault/                  # VaultManager, VaultIndex, VaultScanner, VaultWatcher
│   │   ├── psi/                    # Wiki-link references, completion, folding
│   │   ├── bridge/                 # TODO/FIXME gutter icon line markers
│   │   ├── toolwindow/backlinks/   # Backlinks panel (Swing)
│   │   ├── actions/                # OpenInObsidianAction
│   │   ├── startup/                # Auto-detection of vault on project open
│   │   └── settings/               # App/project settings (PersistentStateComponent)
│   └── resources/
│       ├── META-INF/plugin.xml
│       ├── messages/ObsidianBundle.properties
│       └── icons/
```

## Development

```bash
# Run sandboxed IDE with plugin loaded
./gradlew runIde

# Build plugin ZIP
./gradlew buildPlugin -x buildSearchableOptions

# Run tests
./gradlew test

# Check compatibility against target IDEs
./gradlew verifyPlugin
```

Pre-configured Run/Debug configurations are in `.run/`.
