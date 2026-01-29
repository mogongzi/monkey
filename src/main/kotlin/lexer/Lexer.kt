package me.ryan.interpreter.lexer

import me.ryan.interpreter.token.*

class Lexer (private val input: String) {
    private var position: Int = 0 // current position in input (points to current char)
    private var readPosition: Int = 0 // current reading position in input (after current char)
    private var ch: Char = '\u0000' // current char under examination

    init {
        readChar()
    }

    fun nextToken(): Token {

        skipWhiteSpace()

        val tok = when (ch) {
            '=' -> newToken(ASSIGN, ch)
            '+' -> newToken(PLUS, ch)
            '(' -> newToken(LPAREN, ch)
            ')' -> newToken(RPAREN, ch)
            '{' -> newToken(LBRACE, ch)
            '}' -> newToken(RBRACE, ch)
            ',' -> newToken(COMMA, ch)
            ';' -> newToken(SEMICOLON, ch)
            '\u0000' -> Token(EOF, "")
            else -> {
                if (isLetter(ch)) {
                    val literal = readIdentifier()
                    val type = Token.lookupIdent(literal)
                    return Token(type, literal)
                } else if (isDigit(ch)) {
                    val literal = readNumber()
                    val type = INT
                    return Token(type, literal)
                } else {
                    newToken(ILLEGAL, ch)
                }
            }
        }

        readChar()
        return tok
    }

    private fun newToken(tokenType: TokenType, ch: Char): Token {
        return Token(tokenType, ch.toString())
    }

    private fun skipWhiteSpace() {
        while(ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
            readChar()
        }
    }

    private fun isLetter(ch: Char): Boolean {
        return ch in 'a'..'z' || ch in 'A'..'Z' || ch == '_'
    }

    private fun isDigit(ch: Char): Boolean {
        return ch in '0'..'9'
    }

    private fun readIdentifier(): String {
        val startPosition = position
        while(isLetter(ch)) {
            readChar()
        }
        return input.substring(startPosition, position)
    }

    private fun readNumber(): String {
        val startPosition = position
        while(isDigit(ch)) {
            readChar()
        }
        return input.substring(startPosition, position)
    }

    // the purpose of readChar is to give us the next character and advance our position in the input string.
    private fun readChar() {
        ch = if (readPosition >= input.length) {
            '\u0000'
        } else {
            input[readPosition]
        }

        position = readPosition
        readPosition += 1
    }
}