package me.ryan.interpreter.compiler

import me.ryan.interpreter.ast.Node
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.parser.Parser
import java.io.File

@OptIn(ExperimentalUnsignedTypes::class)
fun main() {
    val cases = listOf(
        "src/test/fixtures/one_plus_two.mkc" to "1 + 2",
        "src/test/fixtures/just_42.mkc" to "42",
    )

    for ((path, source) in cases) {
        val program = parse(source)
        val compiler = Compiler()
        compiler.compile(program)
        val bytecode = compiler.bytecode()

        File(path).outputStream().use { out ->
            BytecodeWriter.write(bytecode, out)
        }
        println("  generated: $path")
    }
    println("Done. ${cases.size} fixture(s) generated.")
}

private fun parse(input: String): Node {
    val lexer = Lexer(input)
    val parser = Parser(lexer)
    return parser.parseProgram()
}
