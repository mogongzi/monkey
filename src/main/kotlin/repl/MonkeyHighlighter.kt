package me.ryan.interpreter.repl

import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.token.*
import org.jline.reader.Highlighter
import org.jline.reader.LineReader
import org.jline.utils.AttributedString
import org.jline.utils.AttributedStringBuilder
import org.jline.utils.AttributedStyle

class MonkeyHighlighter : Highlighter {

    override fun highlight(reader: LineReader, buffer: String): AttributedString {
        val sb = AttributedStringBuilder()
        val lexer = Lexer(buffer)
        var cursor = 0

        while (cursor < buffer.length) {
            // Skip whitespace — append unstyled
            val wsStart = cursor
            while (cursor < buffer.length && buffer[cursor].isWhitespace()) {
                cursor++
            }
            if (cursor > wsStart) {
                sb.append(buffer.substring(wsStart, cursor))
            }

            if (cursor >= buffer.length) break

            val tok = lexer.nextToken()
            if (tok.type == EOF) break

            val style = styleFor(tok.type)

            if (tok.type == STRING) {
                // String token: literal is processed (no quotes, escaped chars resolved).
                // Find the raw string in the buffer: from opening " to closing " (or end).
                val start = cursor // should be at '"'
                cursor++ // skip opening quote
                while (cursor < buffer.length && buffer[cursor] != '"') {
                    if (buffer[cursor] == '\\') cursor++ // skip escaped char
                    cursor++
                }
                if (cursor < buffer.length) cursor++ // skip closing quote
                sb.styled(style, buffer.substring(start, cursor))
            } else {
                sb.styled(style, tok.literal)
                cursor += tok.literal.length
            }
        }

        return sb.toAttributedString()
    }

    private fun styleFor(tokenType: TokenType): AttributedStyle = when (tokenType) {
        MACRO, QUOTE, UNQUOTE, FUNCTION, LET, IF, ELSE, RETURN -> AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA).bold()
        TRUE, FALSE -> AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN)
        EXIT, STRING -> AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN)
        INT -> AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW)
        PLUS, MINUS, ASTERISK, SLASH, ASSIGN, BANG, LT, GT, EQ, NOT_EQ -> AttributedStyle.DEFAULT.foreground(AttributedStyle.RED)
        ILLEGAL -> AttributedStyle.DEFAULT.foreground(AttributedStyle.RED).bold()
        else -> AttributedStyle.DEFAULT
    }
}
