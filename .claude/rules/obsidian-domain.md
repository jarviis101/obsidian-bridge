# Obsidian Domain Rules

## Vault Structure

An Obsidian vault is a directory containing `.md` files and a `.obsidian/` config folder. The plugin must:
- Treat the presence of `.obsidian/` as the canonical vault root marker.
- Ignore files/folders listed in `.obsidian/app.json` → `userIgnoreFilters`.
- Respect `.obsidian/config` for attachment folder and daily notes folder paths.

## Wiki-Link Syntax

All wiki-link variants must be parsed correctly:

| Syntax | Meaning |
|--------|---------|
| `[[Note Name]]` | Link to note by name (no extension) |
| `[[Note Name\|Alias]]` | Link with display alias |
| `[[Note Name#Heading]]` | Link to specific heading |
| `[[Note Name#Heading\|Alias]]` | Heading link with alias |
| `[[Note Name#^block-id]]` | Link to block reference |
| `![[Note Name]]` | Embed (inline transclusion) |
| `![[image.png]]` | Image embed |

Resolution rules (matches Obsidian's own resolver):
1. Exact filename match (case-insensitive on case-insensitive filesystems).
2. If multiple matches, prefer the note in the same folder, then shortest path.
3. Extension `.md` is implicit; never require the user to type it.
4. Attachments (images, PDFs) resolve against the configured attachments folder.

## Frontmatter

YAML frontmatter is delimited by `---` on the first line. Parse with a YAML library (SnakeYAML, already on the IntelliJ classpath). Key fields to extract and index:

- `tags:` (list or space-separated inline `#tag` string)
- `aliases:` (list — must be included in wiki-link resolution)
- `date:` / `created:` / `modified:`
- Any custom fields (store as `Map<String, Any>`)

**Aliases** must be added to the `VaultIndex` so that `[[My Alias]]` resolves to the note even if the filename differs.

## Tags

Two forms:
- Inline: `#tag`, `#parent/child` (hierarchical)
- Frontmatter: `tags: [tag1, tag2]`

The tag tree must support hierarchical tags (`parent/child/grandchild`). Normalize tags to lowercase for indexing; preserve original casing for display.

## Backlinks

A backlink from note A to note B exists when A contains `[[B]]` (or an alias of B, or `![[B]]`). The index must update backlinks incrementally when a file changes — not rebuild the entire index.

## Daily Notes

Daily notes follow a configurable date format (default `YYYY-MM-DD`) stored in `.obsidian/daily-notes.json`:
```json
{ "folder": "Daily Notes", "format": "YYYY-MM-DD", "template": "Templates/Daily" }
```
The "Open/Create Daily Note" action resolves today's note path from this config.

## Templates

Template files contain variables:
- `{{date}}` — current date (default format or configured)
- `{{date:YYYY-MM-DD}}` — date with explicit format
- `{{time}}` — current time
- `{{title}}` — note title (filename without extension)

Use `java.time` for all date/time operations. Template folder path comes from `.obsidian/templates.json` → `folder`.

## TODO/FIXME Bridge

The bridge scans source code comments for patterns:
- `// TODO: [[Note Name]]` — link comment to an existing note
- `// FIXME: [[Note Name]]` — same
- `// TODO: #tag` — link to tag view

A `LineMarkerProvider` renders a gutter icon on these lines. Clicking navigates to the note in a preview panel or opens it in an external Obsidian URI (`obsidian://open?vault=...&file=...`).

## Obsidian URI Protocol

External "open in Obsidian" actions use the URI scheme:
```
obsidian://open?vault=<vault-name>&file=<encoded-path>
obsidian://new?vault=<vault-name>&name=<title>&content=<content>
```
Use `Desktop.getDesktop().browse(URI(...))` to launch. Check `Desktop.isDesktopSupported()` first.

## Multi-Vault

`VaultManager` holds a list of `VaultDescriptor(name, rootPath, settings)`. When resolving a wiki-link, search the active project's associated vault(s) first, then fall back to all registered vaults. Never silently merge indices across vaults — cross-vault links are unresolved by default unless the user enables cross-vault search.

## Parser Implementation Notes

- Do NOT use a full Markdown AST parser for wiki-link extraction — use a targeted regex/state-machine scanner for performance on large vaults (10k+ notes).
- Frontmatter extraction must handle missing frontmatter, malformed YAML, and multi-document YAML gracefully (catch and log, never throw to the user).
- The index must handle note renames: update all backlinks pointing to the old name.
