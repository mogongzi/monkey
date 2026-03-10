package modify

import me.ryan.interpreter.ast.*
import me.ryan.interpreter.modify.modify
import me.ryan.interpreter.token.INT
import me.ryan.interpreter.token.Token
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

data class TestCase(val input: Node, val expected: Node)

class ModifyTest {

    // Shared dummy token — only used as a placeholder, no test inspects or modifies it
    private val token = Token(INT, "")

    @Test
    fun testModify() {
        // Factory functions that produce fresh IntegerLiteral nodes each time they're called,
        // so each test case gets its own independent instances
        val one: () -> Expression = { IntegerLiteral(token, 1L) }
        val two: () -> Expression = { IntegerLiteral(token, 2L) }

        // The modifier function: replaces any IntegerLiteral with value 1 → value 2,
        // leaves all other nodes untouched
        val turnOneIntoTwo: (Node) -> Node = { node ->
            val integer = node as? IntegerLiteral
            if (integer == null || integer.value != 1L) {
                node
            } else {
                IntegerLiteral(integer.token, 2L)
            }
        }

        val tests = listOf(
            // Case 1: modify a single IntegerLiteral(1) → should become IntegerLiteral(2)
            TestCase(input = one(), expected = two()),
            // Case 2: modify a full AST tree (Program → ExpressionStatement → IntegerLiteral(1))
            // Verifies that modify recursively walks into nested nodes
            TestCase(
                input = Program(mutableListOf(ExpressionStatement(token, one()))),
                expected = Program(mutableListOf(ExpressionStatement(token, two()))),
            ),
            TestCase(
                input = InfixExpression(token, left = one(), right = two(), operator = "+"),
                expected = InfixExpression(token, left = two(), right = two(), operator = "+")
            ),
            TestCase(
                input = InfixExpression(token, left = two(), right = one(), operator = "+"),
                expected = InfixExpression(token, left = two(), right = two(), operator = "+")
            ),
            TestCase(
                input = PrefixExpression(token, operator = "-", right = one()),
                expected = PrefixExpression(token, operator = "-", right = two()),
            ),
            TestCase(
                input = IndexExpression(token, left = one(), index = one()),
                expected = IndexExpression(token, left = two(), index = two()),
            ),
            TestCase(
                input = IfExpression(
                    token,
                    condition = one(),
                    consequence = BlockStatement(token, mutableListOf(ExpressionStatement(token, one()))),
                    alternative = BlockStatement(token, mutableListOf(ExpressionStatement(token, one())))
                ),
                expected = IfExpression(
                    token,
                    condition = two(),
                    consequence = BlockStatement(token, mutableListOf(ExpressionStatement(token, two()))),
                    alternative = BlockStatement(token, mutableListOf(ExpressionStatement(token, two())))
                ),
            ),
            TestCase(
                input = ReturnStatement(token, one()),
                expected = ReturnStatement(token, two())
            ),
            TestCase(
                input = LetStatement(token, Identifier(token, "x"), one()),
                expected = LetStatement(token, Identifier(token, "x"), two()),
            ),
            TestCase(
                input = FunctionLiteral(
                    token,
                    emptyList(),
                    BlockStatement(token, mutableListOf(ExpressionStatement(token, one())))
                ),
                expected = FunctionLiteral(
                    token,
                    emptyList(),
                    BlockStatement(token, mutableListOf(ExpressionStatement(token, two())))
                )
            ),
            TestCase(
                input = ArrayLiteral(token, mutableListOf(one(), one())),
                expected = ArrayLiteral(token, mutableListOf(two(), two())),
            ),
        )

        for (test in tests) {
            val modified = modify(test.input, turnOneIntoTwo)
            assertEquals(
                test.expected.string(),
                modified.string(),
                "not equal. got=${test.input}, want=${test.expected}"
            )
        }

        val hashLiteral = HashLiteral(
            token,
            mapOf(
                one() to one(),
                one() to one(),
            )
        )

        val modified = modify(hashLiteral, turnOneIntoTwo) as HashLiteral

        for ((key, value) in modified.pairs) {
            val k = key as IntegerLiteral
            assertEquals(2L, k.value, "key value is not 2, got=${k.value}")
            val v = value as IntegerLiteral
            assertEquals(2L, v.value, "value is not 2, got=${v.value}")

        }
    }
}