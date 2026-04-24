package me.ryan.interpreter.compiler

import me.ryan.interpreter.ast.Node
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.parser.Parser
import java.io.File

@OptIn(ExperimentalUnsignedTypes::class)
fun main() {
    val cases = listOf(
        "src/test/fixtures/just_one.mkc" to "1",
        "src/test/fixtures/just_two.mkc" to "2",
        "src/test/fixtures/one_plus_two.mkc" to "1 + 2",
        "src/test/fixtures/one_minus_two.mkc" to "1 - 2",
        "src/test/fixtures/one_times_two.mkc" to "1 * 2",
        "src/test/fixtures/four_div_two.mkc" to "4 / 2",
        "src/test/fixtures/mixed_arithmetic_1.mkc" to "50 / 2 * 2 + 10 - 5",
        "src/test/fixtures/mixed_arithmetic_2.mkc" to "5 + 5 + 5 + 5 - 10",
        "src/test/fixtures/power_of_two.mkc" to "2 * 2 * 2 * 2 * 2",
        "src/test/fixtures/mul_then_add.mkc" to "5 * 2 + 10",
        "src/test/fixtures/add_then_mul.mkc" to "5 + 2 * 10",
        "src/test/fixtures/paren_expr.mkc" to "5 * (2 + 10)",
    )

    for ((path, source) in cases) {
        val program = parse(source)
        val compiler = Compiler()
        compiler.compile(program)
        val bytecode = compiler.bytecode()
        val file = File(path)
        file.parentFile?.mkdirs()
        file.outputStream().use { out ->
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
