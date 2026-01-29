# 2026-01-27: Token and Lexer Setup

## Overview

Started building a Monkey interpreter in Kotlin, translating from Go (Thorsten Ball's "Writing an Interpreter in Go").

## What I Did

### 1. Token Module (`token/Token.kt`)

Defined the token types and Token data class:

- `typealias TokenType = String` — weak typing approach (can refactor to enum later)
- `data class Token(val type: TokenType, val literal: String)` — holds token info
- Constants for token types: `ILLEGAL`, `EOF`, `IDENT`, `INT`, operators, delimiters, keywords

### 2. Lexer Class (`lexer/Lexer.kt`)

Created the Lexer skeleton with:

- `input: String` — source code to tokenize
- `position: Int` — current position (points to current char)
- `readPosition: Int` — next reading position (after current char)
- `ch: Char` — current character under examination
- `nextToken()` — stub returning placeholder token

### 3. Test Setup (`lexer/LexerTest.kt`)

Set up TDD with JUnit 6:

- Table-driven test pattern using `data class TestCase`
- Test input: `=+(){},;`
- Iterating with `withIndex()` for index tracking
- Using `fail()` for explicit error messages

## Go to Kotlin Translations Learned

| Go | Kotlin |
|---|---|
| `type TokenType string` | `typealias TokenType = String` |
| `type Token struct {...}` | `data class Token(...)` |
| `const (...)` | `const val` declarations |
| `type Lexer struct {...}` | `class Lexer(...)` |
| `l := New(input)` | `val l = Lexer(input)` |
| `for i, tt := range tests` | `for ((i, tt) in tests.withIndex())` |
| `t.Fatalf(...)` | `fail(...)` |

## Next Steps

- Implement `readChar()` to advance through input
- Implement `nextToken()` to return actual tokens based on current character
- Make the test pass
