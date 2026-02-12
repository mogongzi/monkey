package me.ryan.interpreter.token

typealias TokenType = String

data class Token(val type: TokenType, val literal: String) {
    override fun toString(): String = "Token(type=>$type, literal=>$literal)"

    companion object {
        val keywords = mapOf(
            "fn" to FUNCTION,
            "let" to LET,
            "if" to IF,
            "else" to ELSE,
            "return" to RETURN,
            "true" to TRUE,
            "false" to FALSE
        )

        fun lookupIdent(ident: String): TokenType {
            return keywords[ident] ?: IDENT
        }
    }
}

const val ILLEGAL = "ILLEGAL"
const val EOF = "EOF"

// Identifier + literals
const val IDENT = "IDENT"
const val INT = "INT"

// Operators
const val ASSIGN = "="
const val PLUS = "+"
const val MINUS = "-"
const val BANG = "!"
const val ASTERISK = "*"
const val SLASH = "/"

const val LT = "<"
const val GT = ">"

const val EQ = "=="
const val NOT_EQ = "!="

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
const val IF = "IF"
const val ELSE = "ELSE"
const val RETURN = "RETURN"
const val TRUE = "TRUE"
const val FALSE = "FALSE"

