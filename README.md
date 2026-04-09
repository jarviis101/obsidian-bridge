# Obsidian Lens

A JetBrains plugin that brings your Obsidian vault into the IDE — navigate, link, and explore notes without leaving the editor.

## Features

- **Wiki-link rendering** — `[[Note|Alias]]` folds inline to its display name; click to open, Cmd+Click to edit
- **Wiki-link autocompletion** — type `[[` to get note name suggestions from your vault
- **Backlinks panel** — shows outgoing links (this note → others) and incoming backlinks (others → this note)
- **Code ↔ Notes bridge** — gutter icons on `TODO/FIXME: [[Note]]` comments link directly to vault notes
- **Open in Obsidian** — jump from any note to the Obsidian app in one click
- **Multi-vault support** — register multiple vaults, all indexed and searchable

Compatible with IntelliJ IDEA, WebStorm, PyCharm, CLion, GoLand, PhpStorm, Rider, RubyMine, and Android Studio.

## Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.2.x + Java (factory layer), JVM 21 |
| Build | Gradle 9.2.1 + IntelliJ Platform Gradle Plugin v2 |
| Platform | IntelliJ Platform SDK, `sinceBuild=253` (2025.3+) |
| Serialization | kotlinx.serialization (JSON for graph data) |
| YAML | SnakeYAML (on IntelliJ classpath — no extra dep needed) |

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

## Deployment

### Manual upload

1. Build the ZIP: `./gradlew buildPlugin -x buildSearchableOptions`
2. Output: `build/distributions/obsidian-lens-<version>.zip`
3. Upload at [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/upload)

### Automated publish

```bash
./gradlew publishPlugin
```

Requires `PUBLISH_TOKEN` set in `gradle.properties` or as an environment variable.

### Versioning

Bump `version` in `build.gradle.kts` before each release.

## Configuration

After installing, go to **Settings → Tools → Obsidian Lens** to register vault paths.  
Per-project vault override: **Settings → Tools → Obsidian Lens → Obsidian Vault**.
