package me.ryan.interpreter.token

import me.ryan.interpreter.eval.MArray

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
            "false" to FALSE,
            "macro" to MACRO,
            "quote" to QUOTE,
            "unquote" to UNQUOTE,
            "exit" to EXIT,
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
const val STRING = "STRING"

// Marco
const val MACRO = "MACRO"
const val QUOTE = "QUOTE"
const val UNQUOTE = "UNQUOTE"

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
const val COLON = ":"

const val LPAREN = "("
const val RPAREN = ")"
const val LBRACE = "{"
const val RBRACE = "}"
const val LBRACKET = "["
const val RBRACKET = "]"

// Keywords
const val FUNCTION = "FUNCTION"
const val LET = "LET"
const val IF = "IF"
const val ELSE = "ELSE"
const val RETURN = "RETURN"
const val TRUE = "TRUE"
const val FALSE = "FALSE"
const val EXIT = "EXIT"

