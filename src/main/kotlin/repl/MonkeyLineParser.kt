package me.ryan.interpreter.repl

import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.token.*
import org.jline.reader.EOFError
import org.jline.reader.ParsedLine
import org.jline.reader.impl.DefaultParser

class MonkeyLineParser : DefaultParser() {

    override fun parse(line: String, cursor: Int, context: org.jline.reader.Parser.ParseContext): ParsedLine {
        if (context == org.jline.reader.Parser.ParseContext.ACCEPT_LINE) {
            val openBraces = unclosedBraces(line)
            if (openBraces > 0) {
                throw EOFError(-1, cursor, "incomplete input", null, openBraces, null)
            }
        }
        return super.parse(line, cursor, context)
    }

    private fun unclosedBraces(line: String): Int {
        val lexer = Lexer(line)
        var parens = 0
        var braces = 0
        var brackets = 0
        while (true) {
            val tok = lexer.nextToken()
            if (tok.type == EOF) break
            when (tok.type) {
                LPAREN -> parens++
                RPAREN -> parens--
                LBRACE -> braces++
                RBRACE -> braces--
                LBRACKET -> brackets++
                RBRACKET -> brackets--
            }
        }
        return if (parens > 0 || braces > 0 || brackets > 0) braces.coerceAtLeast(0) else 0
    }
}
