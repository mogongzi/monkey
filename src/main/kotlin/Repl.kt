package me.ryan.interpreter

import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.parser.Parser
import me.ryan.interpreter.token.EOF

private const val PROMPT = ">> "

fun main(args: Array<String>) {
    val lexerMode = args.contains("--lexer")
    if (lexerMode) println("🐒 Lexer") else println("🐵 Parser")
    while (true) {
        print(PROMPT)
        System.out.flush()

        val line = readlnOrNull() ?: return

        val lexer = Lexer(line)

        if (lexerMode) {
            var tok = lexer.nextToken()
            while (tok.type != EOF) {
                println(tok)
                tok = lexer.nextToken()
            }
        } else {
            val parser = Parser(lexer)
            val program = parser.parseProgram()
            if (parser.errors().isNotEmpty()) {
                printParserErrors(parser.errors())
                continue
            }
            println(program.string())
        }
    }
}

private fun printParserErrors(errors: List<String>) {
    println("❌ Woops! We ran into some monkey business here!")
    println(" Parser errors:")
    for (msg in errors) {
        println("\t$msg")
    }
}
