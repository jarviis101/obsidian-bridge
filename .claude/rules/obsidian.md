# Obsidian Domain

## Vault Structure

A vault is a directory containing `.md` files and a `.obsidian/` config folder.

- Canonical vault root marker: presence of `.obsidian/`
- Ignore filters: `.obsidian/app.json` → `userIgnoreFilters`
- Attachment folder and daily notes folder paths: `.obsidian/config`

## Wiki-Link Syntax

| Syntax | Meaning |
|--------|---------|
| `[[Note Name]]` | Link by filename (no extension) |
| `[[Note Name\|Alias]]` | Link with display alias |
| `[[Note Name#Heading]]` | Link to heading |
| `[[Note Name#Heading\|Alias]]` | Heading link with alias |
| `[[Note Name#^block-id]]` | Block reference |
| `![[Note Name]]` | Embed / transclusion |
| `![[image.png]]` | Image embed |

**Resolution rules:**
1. Exact filename match (case-insensitive on case-insensitive FSes).
2. Multiple matches → prefer same folder, then shortest path.
3. `.md` extension is implicit.
4. Attachments resolve against the configured attachments folder.

**Parser notes:**
- Use targeted regex/state-machine — not a full Markdown AST (performance on 10k+ vaults).
- Unescape `\|` in table cells before parsing: `replace("\\|", "|")`.
- Relative links (`./foo`, `../foo`): resolve via `contextPath.parent.resolve(target).normalize()` with `.md` fallback.

## Frontmatter

YAML block between `---` delimiters at the top of the file. Parsed with SnakeYAML (on IntelliJ classpath).

Key fields:
- `aliases:` — list; added to `VaultIndex` so `[[Alias]]` resolves to the note
- `tags:` — list or space-separated string
- `date:` / `created:` / `modified:`
- Custom fields stored as `Map<String, Any>`

Gracefully handle: missing frontmatter, malformed YAML, multi-document YAML — catch and log, never throw.

## Backlinks

A backlink from A to B exists when A contains `[[B]]`, `[[AliasOfB]]`, or `![[B]]`.

Index update rules:
- `upsert(note)` — updates outgoing links from that note; **must NOT** clear incoming backlinks (they are owned by other notes).
- `remove(path)` — removes the note and clears its slot in others' backlinks.
- Updates are incremental on file change; never rebuild the full index for a single file change.

## Tags

- Inline: `#tag`, `#parent/child` (hierarchical)
- Frontmatter: `tags: [tag1, tag2]`

Normalize to lowercase for indexing; preserve original casing for display. Tag tree supports `parent/child/grandchild` hierarchy.

## Path Indexing

All path suffixes are indexed. A note at `modules/platform/platform.md` is findable by:
- `platform`
- `platform/platform`
- `modules/platform/platform`

This matches Obsidian's resolution of path-qualified links like `[[platform/platform]]`.

## Daily Notes

Config in `.obsidian/daily-notes.json`:
```json
{ "folder": "Daily Notes", "format": "YYYY-MM-DD", "template": "Templates/Daily" }
```

Default format: `YYYY-MM-DD`. Use `java.time` for all date/time operations.

## Templates

Variables in template files:
- `{{date}}` — current date (default or configured format)
- `{{date:YYYY-MM-DD}}` — date with explicit format
- `{{time}}` — current time
- `{{title}}` — note title (filename without extension)

Template folder: `.obsidian/templates.json` → `folder`.

## TODO/FIXME Bridge

Pattern scanned in source code comments:
```
// TODO: [[Note Name]]
// FIXME: [[Note Name]]
```

`TodoBridgeLineMarkerProvider` renders a gutter icon on matching lines. Clicking opens the note in the IDE editor. Detection uses both PSI (`PsiComment`) and text-based line prefix heuristic (`//`, `#`, `*`, `/*`) for languages whose block-comment tokens don't implement `PsiComment`.

## Obsidian URI Protocol

```
obsidian://open?vault=<vault-name>&file=<encoded-path>
obsidian://new?vault=<vault-name>&name=<title>&content=<content>
```

Launch via `Desktop.getDesktop().browse(URI(...))`. Always check `Desktop.isDesktopSupported()` first.

## Multi-Vault

`VaultManager` holds one `VaultIndex` per vault, keyed by `rootPathString`. Resolution order: active project vault(s) first, then all registered vaults. Cross-vault links are unresolved by default.
