package compiler

import me.ryan.interpreter.ast.Node
import me.ryan.interpreter.code.Instructions
import me.ryan.interpreter.code.OpAdd
import me.ryan.interpreter.code.OpConstant
import me.ryan.interpreter.code.OpDiv
import me.ryan.interpreter.code.OpMul
import me.ryan.interpreter.code.OpPop
import me.ryan.interpreter.code.OpSub
import me.ryan.interpreter.code.make
import me.ryan.interpreter.compiler.Compiler
import me.ryan.interpreter.eval.MInteger
import me.ryan.interpreter.eval.MObject
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.parser.Parser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

@OptIn(ExperimentalUnsignedTypes::class)
data class TestCase(val input: String, val expectedConstants: List<Any>, val expectedInstructions: List<Instructions>)

@OptIn(ExperimentalUnsignedTypes::class)
fun Instructions.toHexString(): String =
    joinToString("") { "\\x%02x".format(it.toInt()) }

@OptIn(ExperimentalUnsignedTypes::class)
class CompilerTest {

    @Test
    fun testIntegerArithmetic() {
        val tests = listOf(
            TestCase(
                input = "1 + 2",
                expectedConstants = listOf(1, 2),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpConstant, 1),
                    make(OpAdd),
                    make(OpPop),
                ),
            ),
            TestCase(
                input = "1;2",
                expectedConstants = listOf(1, 2),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpPop),
                    make(OpConstant, 1),
                    make(OpPop),
                )
            ),
            TestCase(
                input = "1 - 2",
                expectedConstants = listOf(1, 2),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpConstant, 1),
                    make(OpSub),
                    make(OpPop),
                ),
            ),
            TestCase(
                input = "1 * 2",
                expectedConstants = listOf(1, 2),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpConstant, 1),
                    make(OpMul),
                    make(OpPop),
                ),
            ),
            TestCase(
                input = "2 / 1",
                expectedConstants = listOf(2, 1),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpConstant, 1),
                    make(OpDiv),
                    make(OpPop),
                ),
            ),
        )

        runCompilerTests(tests)
    }

    private fun runCompilerTests(tests: List<TestCase>) {
        for (test in tests) {
            val program = parse(test.input)
            val compiler = Compiler()
            compiler.compile(program)
            val bytecode = compiler.bytecode()

            testInstructions(test.expectedInstructions, bytecode.instructions)
            testConstants(test.expectedConstants, bytecode.constants)
        }
    }

    private fun parse(input: String): Node {
        val lexer = Lexer(input)
        val parser = Parser(lexer)
        return parser.parseProgram()
    }

    private fun testInstructions(expected: List<Instructions>, actual: Instructions) {
        val concatted = concatInstructions(expected)
        assertEquals(
            concatted.size, actual.size,
            "wrong instructions length. \nwant=${concatted.toHexString()}\ngot=${actual.toHexString()}"
        )

        concatted.forEachIndexed { i, ins ->
            if (actual[i] != ins) {
                throw AssertionError(
                    "wrong instruction at $i.\nwant=${concatted.toHexString()}\ngot =${actual.toHexString()}"
                )
            }
        }
    }

    private fun concatInstructions(s: List<Instructions>): Instructions {
        return s.reduce { acc, ins -> acc + ins }
    }

    private fun testConstants(expected: List<Any>, actual: List<MObject>) {
        assertEquals(
            expected.size, actual.size,
            "wrong number of constants. got=$actual, want=$expected"
        )

        expected.forEachIndexed { i, constant ->
            when (constant) {
                is Int -> testMIntegerObject(constant.toLong(), actual[i])
            }
        }
    }

    private fun testMIntegerObject(expected: Long, actual: MObject) {
        val result = assertInstanceOf(
            MInteger::class.java, actual,
            "MObject is not MInteger. got=${actual::class.simpleName} (${actual.inspect()})"
        )
        assertEquals(
            expected, result.value,
            "MObject has wrong value. got=${result.value}, want=$expected"
        )
    }
}
