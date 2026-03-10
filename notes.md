## Numbers: Only Integers (No Floats/Hex/Octal)

Monkey intentionally supports only base-10 integer literals like 5 or 123.

The lexer treats a number as a contiguous sequence of digits 0–9 and returns an INT token. Anything outside that simple form is not supported:

- Floats (e.g. 1.23) are not recognized as a single numeric literal.
- Hex notation (e.g. 0xFF) is not supported.
- Octal notation (e.g. 077 or 0o77) is not supported.

Tasks Breakdown (Number Literal Support Scope)

- Define the spec (what we support)
    - Only base-10 integers: INT := [0-9]+
    - Explicitly not supported: floats (1.0), hex (0xFF), octal (077 / 0o77), binary (0b1010)
- Lexer behavior (what to do when we see digits)
    - Keep current readNumber() that consumes only 0..9
    - Return INT token with the consumed digits
- Decide how to “ignore” unsupported forms (error strategy)
    - Option A: Let lexer produce INT("1") then ILLEGAL('.') etc. and fail later
    - Option B: Detect patterns like . after digits or 0x/0o/0b and emit a single ILLEGAL token early
- Parser behavior (if needed)
    - If unsupported forms reach parser, ensure a clear parse error message
    - Confirm integer parsing uses only base-10 (toInt() / toLong())
- Tests to record (so behavior is fixed)
    - Accept: 0, 123, 99999
    - Reject (expected failure path): 1.23, 0x10, 077, 0o77, 0b10, .5, 5.

## REPL Multi-line Input Support

Current REPL uses `readlnOrNull()` which reads one line at a time — multi-line code (pasting or typing) fails.

Goal: IRB-style behavior — Enter on incomplete input shows continuation prompt, Enter on complete input submits.

### Key Concepts

- **Terminal cannot distinguish paste newlines from Enter** — both send the same byte (`\r`/`\n`)
- Two concerns to solve:
    1. **Mechanism**: intercepting Enter keypress to decide submit vs. newline insertion
    2. **Policy**: determining whether input is "complete"
- **Bracketed paste mode**: modern terminals wrap pasted text in escape sequences (`\e[200~`...`\e[201~`), allowing the app to treat pasted newlines as text insertion — but not universally supported

### Solution: JLine + Monkey Parser

- **JLine** (JVM equivalent of Python's `prompt_toolkit`): provides terminal control, key handling, continuation prompts, history, line editing
- JLine's `Parser` interface: `parse()` throws `EOFError` → keep reading (show continuation prompt); returns → submit
- **Completeness detection**: use the real Monkey `Parser` instead of simple bracket-counting
    - Parse the accumulated input; if errors contain `"got EOF instead"` → incomplete (throw `EOFError`)
    - Real syntax errors (not EOF-related) → submit and let REPL show the error
    - This is the same approach IRB uses (Ruby's Ripper parser) and IPython uses (`code.compile_command()`)

### Implementation Steps (deferred to after finishing Book 1)

1. Add dependency: `org.jline:jline:3.28.0`
2. Create `MonkeyLineParser` implementing JLine's `Parser` — delegates to Monkey `Parser` for completeness check
3. Replace `readlnOrNull()` with JLine `LineReader` (configured with `SECONDARY_PROMPT_PATTERN` = `".. "`)
4. Remove `standardInput = System.in` workaround from `build.gradle.kts`

## ast.Modify: Error Handling & Performance

### Error Handling (Type Casts)

- Go's `ast.Modify` uses `_` to ignore failed type assertions — silent failures the author acknowledges as a shortcut.
- Kotlin's `as` (unsafe cast) throws `ClassCastException` on failure — fail-fast, which is preferable.
- Safe cast (`as?`) with `?: error("...")` gives the same fail-fast with a descriptive message, but adds verbosity for little practical benefit here since the modifier contract ensures types are preserved.

### Performance: Unnecessary Object Allocation

- `modify` **visits every node** in the AST and **creates new objects for every node**, even when nothing changed.
- Optimization: check if children actually changed; if not, return the original node (structural sharing).
- Full traversal is unavoidable — `unquote` could appear anywhere in the tree, so every branch must be inspected.
- Acceptable tradeoff for a learning interpreter with small ASTs; production systems handle macro expansion differently (e.g., during parsing).

### quote / unquote Flow

1. `quote(...)` hits `CallExpression` in the evaluator → dispatches to `quote()` method.
2. `quote()` passes the AST node through `modify` before wrapping it in `MQuote`.
3. The modifier function looks for `CallExpression` nodes named `"unquote"` — evaluates their argument and replaces the node with the result.
4. All other nodes pass through unchanged (`else -> n`).