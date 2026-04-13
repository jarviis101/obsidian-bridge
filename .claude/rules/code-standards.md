# Kotlin Code Standards

## Style

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- 4-space indent, no tabs. Max line length: 120 characters.
- `internal` visibility for cross-package helpers; `private` for everything else.
- Prefer `val` over `var`. Use `var` only when mutation is unavoidable.
- Prefer expression bodies for single-expression functions.
- No trailing blank lines inside functions or classes.

## Naming

- Classes: `PascalCase`. Functions and properties: `camelCase`. Constants: `UPPER_SNAKE_CASE`.
- Private top-level vals that act as constants: `UPPER_SNAKE_CASE` (e.g. `private val LOG = logger<Foo>()`).
- Boolean properties/functions: `is*`, `has*`, `can*` prefix.

## Null Safety

- Never use `!!` — use `?: return`, `?: return null`, or `?.let { }` instead.
- Prefer early returns over deep nesting.
- Use `?.let { }` chains only for 1–2 levels; extract to named functions beyond that.

## Functions

- Single responsibility: one function does one thing.
- Max ~30 lines per function. Extract if longer.
- Do not add parameters for hypothetical future use.
- `internal fun` for shared package-level utilities; avoid creating utility objects/classes for one-time use.

## Collections & Data

- Prefer `List` / `Map` / `Set` immutable interfaces over mutable ones in return types and parameters.
- Use `data class` for value objects. Do not put business logic in data classes.
- Destructuring (`val (a, b) = pair`) is encouraged for readability, not required.

## Error Handling

- Only validate at system boundaries (user input, external APIs, file I/O).
- Don't add try/catch for exceptions that cannot happen.
- Log and return `null`/empty rather than throwing in parser/index code — never crash the IDE.
- Use `logger<T>()` (IntelliJ's logger) for all logging. No `println` or `System.err`.

## What to Avoid

- No inline comments inside method bodies — code should be self-explanatory.
- No speculative abstractions or premature generalizations.
- No backwards-compatibility stubs, `_unused` variables, or `// removed` comments.
- No hardcoded English strings in UI code — use `ObsidianBundle`.
- No duplicated logic — extract shared functions before copy-pasting.
- Do not add features, refactoring, or error handling beyond what was asked.

## Tests

- Pure-logic classes (parsers, utilities): plain JUnit, no platform fixture.
- PSI/reference/completion tests: extend `BasePlatformTestCase`.
- Test data in `src/test/testData/`. No real filesystem paths.
- Test method names: backtick sentences describing behaviour — `` `returns null when link is closed` ``.
