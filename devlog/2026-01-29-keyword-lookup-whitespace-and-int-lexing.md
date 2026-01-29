# 2026-01-29: Keyword Lookup, Whitespace Skipping, and INT Lexing

## Overview

Extended the Kotlin lexer so it can:

- Skip whitespace before producing a token
- Distinguish identifiers from reserved keywords (e.g. `fn`, `let`)
- Tokenize integer literals (`INT`) by reading digit runs

These changes mirror the next steps in the Monkey lexer from the Go version, but adapted to Kotlin idioms.

## What I Did

### 1. Keyword Lookup in Token (`src/main/kotlin/token/Token.kt`)

- Added a `companion object` on `Token` to host "static-like" members.
- Defined `keywords: Map<String, TokenType>` with entries for `fn` and `let`.
- Implemented `lookupIdent(ident: String): TokenType`:
  - Returns the keyword token type if present in `keywords`.
  - Falls back to `IDENT` otherwise.

### 2. Lexer Improvements (`src/main/kotlin/lexer/Lexer.kt`)

- Added `skipWhiteSpace()` and called it at the start of `nextToken()`.
- Updated identifier lexing:
  - Read the full identifier once (`val literal = readIdentifier()`).
  - Resolve its type via `Token.lookupIdent(literal)`.
  - Early-return the token so we don't call `readChar()` an extra time (same control flow as the Go lexer).
- Added integer lexing:
  - `isDigit(ch)` checks `ch in '0'..'9'`.
  - `readNumber()` consumes a contiguous run of digits and returns the substring.
  - In `nextToken()`, if the current `ch` is a digit, read the full number and return `Token(INT, literal)`.
- Aligned `isLetter()` with the Monkey book's ASCII rule (`a-z`, `A-Z`, `_`) instead of Kotlin's Unicode `Char.isLetter()`.

## Notes / Learnings

- Kotlin doesn't have Go's `map[string]TokenType` literal; `mapOf("fn" to FUNCTION, ...)` is the closest equivalent.
- `companion object` is Kotlin's analogue of Java/Go "static-ish" members when you want `Token.lookupIdent(...)`.
- For lexer routines like `readIdentifier()` / `readNumber()` that advance the cursor internally, the `nextToken()` branch should `return` immediately to avoid an extra `readChar()`.

## Next Steps

- Add/extend lexer tests to cover:
  - Whitespace skipping
  - Identifiers vs keywords
  - Integer literals
- Continue implementing the remaining Monkey tokens (e.g. `!`, `-`, `/`, `*`, `<`, `>`, and multi-char operators like `==`, `!=`).
