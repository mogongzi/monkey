package eval

import me.ryan.interpreter.eval.Evaluator
import me.ryan.interpreter.eval.MInteger
import me.ryan.interpreter.eval.MObject
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.parser.Parser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class EvaluatorTest {

    val evaluator = Evaluator()

    @Test
    fun testEvalIntegerExpression() {
        val tests = listOf(
            Pair("5", 5L),
            Pair("10", 10L),
        )

        for ((input, expected) in tests) {
            val evaluated = testEval(input)
            testMIntegerObject(evaluated!!, expected)
        }
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
}