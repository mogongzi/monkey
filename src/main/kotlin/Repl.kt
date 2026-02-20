package me.ryan.interpreter

import me.ryan.interpreter.eval.Evaluator
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.parser.Parser
import me.ryan.interpreter.token.EOF

private const val PROMPT = ">> "

fun main(args: Array<String>) {
    val lexerMode = args.contains("--lexer")
    val parserMode = args.contains("--parser")
    println("🐒 Monkey REPL")
    while (true) {
        print(PROMPT)
        System.out.flush()

        val line = readlnOrNull() ?: return

        if (lexerMode) {
            println("--- Lexer ---")
            val lexer = Lexer(line)
            var tok = lexer.nextToken()
            while (tok.type != EOF) {
                println(tok)
                tok = lexer.nextToken()
            }
        }

        val lexer = Lexer(line)
        val parser = Parser(lexer)
        val program = parser.parseProgram()
        if (parser.errors().isNotEmpty()) {
            printParserErrors(parser.errors())
            continue
        }

        if (parserMode) {
            println("--- Parser ---")
            println(program.string())
        }

        println("--- Evaluator ---")
        val evaluated = Evaluator().eval(program)
        if (evaluated != null) {
            println(evaluated.inspect())
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
