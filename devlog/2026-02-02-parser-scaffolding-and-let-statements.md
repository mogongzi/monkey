# 2026-02-02 — Parser scaffolding + `let` statements

## What I built
- Added a basic parser skeleton that keeps `curToken` + `peekToken` (1-token lookahead) and advances via `nextToken()`.
- Implemented `parseProgram()` as a loop: parse one statement per iteration until `EOF`, append to `Program.statements`.
- Implemented `parseStatement()` dispatch with a `LET -> parseLetStatement()` branch (will grow with more statement types).
- Implemented an early-stage `parseLetStatement()` that parses `let <ident> = ... ;` and **skips tokens until `;`** (placeholder until expression parsing exists).

## AST modeling decisions
- `LetStatement` is a `Statement` (not an `Expression`), so tests can type-check/cast statements as in the book.
- `LetStatement.value` is `Expression?` (nullable) for now, because the parser chapter builds `let` statements before expression parsing is implemented.
- `Identifier` implements `Expression` so it can be reused both:
  - as a binding name (`let x = ...`) and
  - as a value-producing expression (`x`, `x + 1`, `let y = x`).
- `tokenLiteral()` returns the token **literal** from the source (e.g. `"let"`), not the token type (e.g. `"LET"`).

## Testing / sanity checks
- Ported the book’s table-driven `let` tests into Kotlin style (JUnit assertions).
- Confirmed `./gradlew test --no-daemon` passes.

## Next steps
- Add `parseReturnStatement()`.
- Add error collection (like the book) so failed `expectPeek(...)` calls are diagnosable.
- Implement expression parsing (Pratt parser) and replace the “skip until semicolon” placeholder with `parseExpression(...)`.

