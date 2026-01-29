package me.ryan.interpreter.token

typealias TokenType = String

data class Token(val type: TokenType, val literal: String)

const val ILLEGAL = "ILLEGAL"
const val EOF = "EOF"

// Identifier + literals
const val IDENT = "IDENT"
const val INT = "INT"

// Operators
const val ASSIGN = "="
const val PLUS = "+"

// Delimiters
const val COMMA = ","
const val SEMICOLON = ";"

const val LPAREN = "("
const val RPAREN = ")"
const val LBRACE = "{"
const val RBRACE = "}"

// Keywords
const val FUNCTION = "FUNCTION"
const val LET = "LET"

val keywords = mapOf(
    "fn" to FUNCTION,
    "let" to LET
)

fun lookupIdent(ident: String): TokenType {
    return keywords[ident] ?: IDENT
}

