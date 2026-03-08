package me.ryan.interpreter.repl

import me.ryan.interpreter.eval.Environment
import me.ryan.interpreter.eval.Evaluator
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.parser.Parser
import me.ryan.interpreter.token.EOF
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException

private const val PROMPT = ">> "

fun main(args: Array<String>) {
    val lexerMode = args.contains("--lexer")
    val parserMode = args.contains("--parser")
    val env = Environment()

    val reader = LineReaderBuilder.builder()
        .parser(MonkeyLineParser())
        .highlighter(MonkeyHighlighter())
        .variable(org.jline.reader.LineReader.SECONDARY_PROMPT_PATTERN, "%N.. ")
        .variable(org.jline.reader.LineReader.INDENTATION, 2)
        .build()

    println("🐒  Monkey REPL")
    while (true) {
        val line: String
        try {
            line = reader.readLine(PROMPT)
        } catch (_: UserInterruptException) {
            continue
        } catch (_: EndOfFileException) {
            return
        }
        if (line == "exit") return

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

        if (parserMode) println("--- Evaluator ---")
        val evaluated = Evaluator().eval(program, env)
        println(evaluated.inspect())
    }
}

private fun printParserErrors(errors: List<String>) {
    println("❌ Whoops! We ran into some monkey business here!")
    println(" Parser errors:")
    for (msg in errors) {
        println("\t$msg")
    }
}
