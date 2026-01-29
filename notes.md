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