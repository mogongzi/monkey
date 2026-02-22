package eval

import me.ryan.interpreter.eval.Evaluator
import me.ryan.interpreter.eval.MBoolean
import me.ryan.interpreter.eval.MInteger
import me.ryan.interpreter.eval.MNULL
import me.ryan.interpreter.eval.MObject
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.parser.Parser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EvaluatorTest {

    val evaluator = Evaluator()

    @Test
    fun testEvalIntegerExpression() {
        val tests = listOf(
            Pair("5", 5L),
            Pair("10", 10L),
            Pair("-5", -5L),
            Pair("-10", -10L),
            Pair("5 + 5 + 5 + 5 - 10", 10L),
            Pair("2 * 2 * 2 * 2 * 2", 32L),
            Pair("-50 + 100 + -50", 0L),
            Pair("5 * 2 + 10", 20L),
            Pair("5 + 2 * 10", 25L),
            Pair("20 + 2 * -10", 0L),
            Pair("50 / 2 * 2 + 10", 60L),
            Pair("2 * (5 + 10)", 30L),
            Pair("3 * 3 * 3 + 10", 37L),
            Pair("3 * (3 * 3) + 10", 37L),
            Pair("(5 + 10 * 2 + 15 / 3) * 2 + -10", 50L),
        )

        for ((input, expected) in tests) {
            val evaluated = testEval(input)
            testMIntegerObject(evaluated!!, expected)
        }
    }

    @Test
    fun testEvalBooleanExpression() {
        val tests = listOf(
            "true" to true,
            "false" to false,
            "1 < 2" to true,
            "1 > 2" to false,
            "1 < 1" to false,
            "1 > 1" to false,
            "1 == 1" to true,
            "1 != 1" to false,
            "1 == 2" to false,
            "1 != 2" to true,
            "true == true" to true,
            "false == false" to true,
            "true == false" to false,
            "true != false" to true,
            "false != true" to true,
            "(1 < 2) == true" to true,
            "(1 < 2) == false" to false,
            "(1 > 2) == true" to false,
            "(1 > 2) == false" to true,
        )

        for ((input, expected) in tests) {
            val evaluated = testEval(input)
            testMBooleanObject(evaluated!!, expected)
        }
    }

    @Test
    fun testBangOperator() {
        val tests = listOf(
            "!true" to false,
            "!false" to true,
            "!5" to false,
            "!!true" to true,
            "!!false" to false,
            "!!5" to true
        )

        for ((input, expected) in tests) {
            val evaluated = testEval(input)
            testMBooleanObject(evaluated!!, expected)
        }
    }

    @Test
    fun testIfElseExpressions() {
        val tests = listOf(
            "if (true) { 10 }" to 10L,
            "if (false) { 10 }" to null,
            "if (1) { 10 }" to 10L,
            "if (1 < 2) { 10 }" to 10L,
            "if (1 > 2) { 10 }" to null,
            "if (1 > 2) { 10 } else { 20 }" to 20L,
            "if (1 < 2) { 10 } else { 20 }" to 10L,
        )

        for ((input, expected) in tests) {
            val evaluated = testEval(input)
            if (expected != null) {
                testMIntegerObject(evaluated!!, expected)
            } else {
                testMNULLObject(evaluated!!)
            }
        }
    }

    private fun testMNULLObject(obj: MObject) {
        assertEquals(MNULL, obj, "object is not NULL. got=${obj::class} ($obj)")
    }

    private fun testEval(input: String): MObject? {
        val lexer = Lexer(input)
        val parser = Parser(lexer)
        val program = parser.parseProgram()

        return evaluator.eval(program)
    }

    private fun testMIntegerObject(obj: MObject, expected: Long) {
        assertInstanceOf(MInteger::class.java, obj)
        val result = obj as MInteger
        assertEquals(expected, result.value)
    }

    private fun testMBooleanObject(obj: MObject, expected: Boolean) {
        val result = obj as? MBoolean ?: fail("object is not MBoolean. got=${obj::class}")
        assertEquals(expected, result.value)
    }
}