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
        "src/test/fixtures/true.mkc" to "true",
        "src/test/fixtures/false.mkc" to "false",
        // integer comparisons
        "src/test/fixtures/one_lt_two.mkc" to "1 < 2",
        "src/test/fixtures/one_gt_two.mkc" to "1 > 2",
        "src/test/fixtures/one_lt_one.mkc" to "1 < 1",
        "src/test/fixtures/one_gt_one.mkc" to "1 > 1",
        "src/test/fixtures/one_eq_one.mkc" to "1 == 1",
        "src/test/fixtures/one_neq_one.mkc" to "1 != 1",
        "src/test/fixtures/one_eq_two.mkc" to "1 == 2",
        "src/test/fixtures/one_neq_two.mkc" to "1 != 2",
        // boolean equality
        "src/test/fixtures/true_eq_true.mkc" to "true == true",
        "src/test/fixtures/false_eq_false.mkc" to "false == false",
        "src/test/fixtures/true_eq_false.mkc" to "true == false",
        "src/test/fixtures/true_neq_false.mkc" to "true != false",
        "src/test/fixtures/false_neq_true.mkc" to "false != true",
        // mixed: comparison result compared with boolean
        "src/test/fixtures/lt_eq_true.mkc" to "(1 < 2) == true",
        "src/test/fixtures/lt_eq_false.mkc" to "(1 < 2) == false",
        "src/test/fixtures/gt_eq_true.mkc" to "(1 > 2) == true",
        "src/test/fixtures/gt_eq_false.mkc" to "(1 > 2) == false",
        // prefix: minus
        "src/test/fixtures/minus_five.mkc" to "-5",
        "src/test/fixtures/minus_ten.mkc" to "-10",
        "src/test/fixtures/minus_sum.mkc" to "-50 + 100 + -50",
        "src/test/fixtures/complex_arithmetic.mkc" to "(5 + 10 * 2 + 15 / 3) * 2 + -10",
        // prefix: bang (logical not)
        "src/test/fixtures/bang_true.mkc" to "!true",
        "src/test/fixtures/bang_false.mkc" to "!false",
        "src/test/fixtures/bang_five.mkc" to "!5",
        "src/test/fixtures/bang_bang_true.mkc" to "!!true",
        "src/test/fixtures/bang_bang_false.mkc" to "!!false",
        "src/test/fixtures/bang_bang_five.mkc" to "!!5",
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
