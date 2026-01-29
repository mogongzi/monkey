package me.ryan.interpreter

import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.token.EOF

private const val PROMPT = ">> "

fun main() {
    println("🐒")
    while (true) {
        print(PROMPT)
        System.out.flush()

        val line = readlnOrNull() ?: return

        val lexer = Lexer(line)
        var tok = lexer.nextToken()
        while (tok.type != EOF) {
            println(tok)
            tok = lexer.nextToken()
        }
    }
}