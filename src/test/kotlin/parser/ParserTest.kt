package parser

import me.ryan.interpreter.ast.*
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.parser.Parser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import kotlin.test.fail

class ParserTest {
    @Test
    fun testLetStatement() {
        val input = """
            let x = 5;
            let y = 10;
            let foobar = 838383;
        """.trimIndent()
//        val input = """
//            let x 5;
//            let = 10;
//            let 838383;
//        """.trimIndent()
//        parser has 3 errors
//        parser error: "expected next token to be =, got INT instead."
//        parser error: "expected next token to be IDENT, got = instead."
//        parser error: "expected next token to be IDENT, got INT instead."

        val lexer = Lexer(input)
        val parser = Parser(lexer)
        val program = parser.parseProgram()
        checkParserErrors(parser)

        if (program.statements.isEmpty()) {
            fail("ParserProgram() returned empty")
        }

        if (program.statements.size != 3) {
            fail("ParserProgram() doesn't contain 3 statements. got ${program.statements.size}")
        }

        val tests = listOf(
            "x",
            "y",
            "foobar"
        )

        tests.forEachIndexed { i, expectedName ->
            val stmt = program.statements[i]
            testLetStatement(stmt, expectedName)
        }
    }

    @Test
    fun testReturnStatement() {
        val input = """
            return 5;
            return 10;
            return add(15);
        """.trimIndent()

        val lexer = Lexer(input)
        val parser = Parser(lexer)
        val program = parser.parseProgram()
        checkParserErrors(parser)

        if (program.statements.size != 3) {
            fail("ParserProgram() doesn't contain 3 statements. got ${program.statements.size}")
        }

        for (stmt in program.statements) {
            val returnStmt =
                assertInstanceOf(ReturnStatement::class.java, stmt, "stmt not ReturnStatement. got=${stmt::class}")
            assertEquals(
                "return",
                returnStmt.tokenLiteral(),
                "returnStmt.tokenLiteral() not 'return', got=${returnStmt.tokenLiteral()}"
            )
        }
    }

    private fun testLetStatement(stmt: Statement, expectedName: String) {
        val letStmt = stmt as? LetStatement ?: fail("stmt not LetStatement. got=${stmt::class}")
        assertEquals("let", letStmt.tokenLiteral())
        assertEquals(expectedName, letStmt.name.value, "letStmt.name.value mismatch. got=${letStmt.name.value}")
        assertEquals(
            expectedName,
            letStmt.name.tokenLiteral(),
            "letStmt.name.tokenLiteral() mismatch. got=${letStmt.name.tokenLiteral()}"
        )
    }

    @Test
    fun testIdentifierExpression() {
        val input = "foobar;"
        val lexer = Lexer(input)
        val parser = Parser(lexer)
        val program = parser.parseProgram()
        checkParserErrors(parser)

        if (program.statements.size != 1) {
            fail("program has not enough statements. got ${program.statements.size}")
        }

        val stmt = assertInstanceOf(
            ExpressionStatement::class.java,
            program.statements[0],
            "program.statements[0] is a not ExpressionStatement. got=${program.statements[0]::class}"
        )

        val ident = assertInstanceOf(
            Identifier::class.java,
            stmt.expression!!,
            "exp not Identifier. got=${stmt.expression!!::class}"
        )

        assertEquals("foobar", ident.value, "ident.value not foobar. got=${ident.value}")
        assertEquals("foobar", ident.tokenLiteral(), "ident.tokenLiteral() not foobar. got=${ident.tokenLiteral()}")
    }

    @Test
    fun testIntegerLiteralExpression() {
        val input = "5;"
        val lexer = Lexer(input)
        val parser = Parser(lexer)
        val program = parser.parseProgram()
        checkParserErrors(parser)

        if (program.statements.size != 1) {
            fail("program has not enough statements. got ${program.statements.size}")
        }

        val stmt = assertInstanceOf(
            ExpressionStatement::class.java,
            program.statements[0],
            "program.statements[0] is a not ExpressionStatement. got=${program.statements[0]::class}"
        )

        val literal = assertInstanceOf(
            IntegerLiteral::class.java,
            stmt.expression!!,
            "exp not IntegerLiteral. got=${stmt.expression!!::class}"
        )

        assertEquals(5L, literal.value, "ident.value not 5. got=${literal.value}")
        assertEquals("5", literal.tokenLiteral(), "literal.tokenLiteral not 5. got=${literal.tokenLiteral()}")
    }

    @Test
    fun testParsingPrefixExpressions() {
        val prefixTests = listOf(
            Triple("!5;", "!", 5L),
            Triple("-15;", "-", 15L),
            Triple("!true;", "!", true),
            Triple("!false;", "!", false),
        )

        for ((input, operator, literalValue) in prefixTests) {
            val lexer = Lexer(input)
            val parser = Parser(lexer)
            val program = parser.parseProgram()
            checkParserErrors(parser)

            if (program.statements.size != 1) {
                fail("program has not enough statements. got ${program.statements.size}")
            }

            val stmt = assertInstanceOf(
                ExpressionStatement::class.java,
                program.statements[0],
                "program.statements[0] is a not ExpressionStatement. got=${program.statements[0]::class}"
            )

            val exp = assertInstanceOf(
                PrefixExpression::class.java,
                stmt.expression!!,
                "exp not PrefixExpression. got=${stmt.expression!!::class}"
            )

            if (exp.operator != operator) {
                fail("exp.Operator is not ${operator}. got=${exp.operator}")
            }

            if (!testLiteralExpression(exp.right, literalValue)) return
        }
    }

    data class InfixTestCase(val input: String, val leftValue: Any, val operator: String, val rightValue: Any)

    @Test
    fun testParsingInfixExpressions() {
        val infixTests = listOf(
            InfixTestCase("5 + 5;", 5L, "+", 5L),
            InfixTestCase("5 - 5;", 5L, "-", 5L),
            InfixTestCase("5 * 5;", 5L, "*", 5L),
            InfixTestCase("5 / 5;", 5L, "/", 5L),
            InfixTestCase("5 > 5;", 5L, ">", 5L),
            InfixTestCase("5 < 5;", 5L, "<", 5L),
            InfixTestCase("5 == 5;", 5L, "==", 5L),
            InfixTestCase("5 != 5;", 5L, "!=", 5L),
            InfixTestCase("true == true;", true, "==", true),
            InfixTestCase("true != false;", true, "!=", false),
            InfixTestCase("false == false;", false, "==", false),
        )

        for (infixTest in infixTests) {
            val lexer = Lexer(infixTest.input)
            val parser = Parser(lexer)
            val program = parser.parseProgram()
            checkParserErrors(parser)

            if (program.statements.size != 1) {
                fail("program has not enough statements. got ${program.statements.size}")
            }

            val stmt = assertInstanceOf(
                ExpressionStatement::class.java,
                program.statements[0],
                "program.statements[0] is a not ExpressionStatement. got=${program.statements[0]::class}"
            )

            if (!testInfixExpression(
                    stmt.expression,
                    infixTest.leftValue,
                    infixTest.operator,
                    infixTest.rightValue
                )
            ) return
        }
    }

    @Test
    fun testOperatorPrecedenceParsing() {
        data class Test(val input: String, val expected: String)

        val tests = listOf(
            Test("-a * b", "((-a) * b)"),
            Test("!-a", "(!(-a))"),
            Test("a + b + c", "((a + b) + c)"),
            Test("a + b - c", "((a + b) - c)"),
            Test("a * b * c", "((a * b) * c)"),
            Test("a * b / c", "((a * b) / c)"),
            Test("a + b / c", "(a + (b / c))"),
            Test("a + b * c + d / e - f", "(((a + (b * c)) + (d / e)) - f)"),
            Test("3 + 4; -5 * 5", "(3 + 4)((-5) * 5)"),
            Test("5 > 4 == 3 < 4", "((5 > 4) == (3 < 4))"),
            Test("5 < 4 != 3 > 4", "((5 < 4) != (3 > 4))"),
            Test("3 + 4 * 5 == 3 * 1 + 4 * 5", "((3 + (4 * 5)) == ((3 * 1) + (4 * 5)))"),
            Test("true", "true"),
            Test("false", "false"),
            Test("3 > 5 == false", "((3 > 5) == false)"),
            Test("3 < 5 == true", "((3 < 5) == true)"),
        )

        assertAll(
            tests.map { test ->
                {
                    val lexer = Lexer(test.input)
                    val parser = Parser(lexer)
                    val program = parser.parseProgram()
                    checkParserErrors(parser)
                    assertEquals(test.expected, program.string(), "input: ${test.input}")
                }
            }
        )
    }

    @Test
    fun testBooleanExpressions() {
        val input = "true;"
        val lexer = Lexer(input)
        val parser = Parser(lexer)
        val program = parser.parseProgram()
        checkParserErrors(parser)

        if (program.statements.size != 1) {
            fail("program has not enough statements. got ${program.statements.size}")
        }

        val stmt = assertInstanceOf(
            ExpressionStatement::class.java,
            program.statements[0],
            "program.statements[0] is a not ExpressionStatement. got=${program.statements[0]::class}"
        )

        val literal = assertInstanceOf(
            BooleanLiteral::class.java,
            stmt.expression!!,
            "exp not IntegerLiteral. got=${stmt.expression!!::class}"
        )

        assertEquals(true, literal.value, "Boolean.value not true. got=${literal.value}")
        assertEquals("true", literal.tokenLiteral(), "Boolean.tokenLiteral not 5. got=${literal.tokenLiteral()}")
    }


    @Test
    fun testDebug() {
        val lexer = Lexer("1 + 2 + 3")
        val parser = Parser(lexer)
        val program = parser.parseProgram()
        println(program.string())


    }

    private fun testIntegerLiteral(exp: Expression?, value: Long): Boolean {
        val integ = exp as? IntegerLiteral ?: fail("exp not IntegerLiteral. got=${exp?.let { it::class }}")
        if (integ.value != value) {
            fail("integ.value not ${value}. got=${integ.value}")
        }
        if (integ.tokenLiteral() != value.toString()) {
            fail("integ.tokenLiteral() not ${value}. got=${integ.tokenLiteral()}")
        }
        return true
    }

    private fun testBooleanLiteral(exp: Expression?, value: Boolean): Boolean {
        val bool = exp as? BooleanLiteral ?: fail("exp not BooleanLiteral. got=${exp?.let { it::class }}")
        if (bool.value != value) {
            fail("bool.value not ${value}. got=${bool.value}")
        }
        if (bool.tokenLiteral() != value.toString()) {
            fail("bool.tokenLiteral() not ${value}. got=${bool.tokenLiteral()}")
        }

        return true
    }

    private fun testIdentifier(exp: Expression?, value: String): Boolean {
        val ident = exp as? Identifier ?: fail("exp not Identifier. got=${exp?.let { it::class }}")
        if (ident.value != value) {
            fail("ident.ident() not ${value}. got=${ident.value}")
        }
        if (ident.tokenLiteral() != value) {
            fail("ident.tokenLiteral() not ${value}. got=${ident.tokenLiteral()}")
        }
        return false
    }

    private fun testLiteralExpression(exp: Expression?, expected: Any): Boolean {
        return when (expected) {
            is Int -> testIntegerLiteral(exp, expected.toLong())
            is Long -> testIntegerLiteral(exp, expected)
            is String -> testIdentifier(exp, expected)
            is Boolean -> testBooleanLiteral(exp, expected)
            else -> {
                fail("type of exp not handled. got ${exp?.let { it::class }}")
            }
        }
    }

    private fun testInfixExpression(exp: Expression?, left: Any, operator: String, right: Any): Boolean {
        val opExp = exp as? InfixExpression ?: fail("exp is not InfixExpression. got=${exp?.let { it::class }}")

        if (!testLiteralExpression(opExp.left, left)) {
            return false
        }

        if (opExp.operator != operator) {
            fail("exp.operator is not ${operator}. got=${opExp.operator}")
        }

        if (!testLiteralExpression(opExp.right, right)) {
            return false
        }

        return true
    }

    private fun checkParserErrors(parser: Parser) {
        val errors = parser.errors()
        if (errors.isNotEmpty()) {
            fail(
                buildString {
                    append("parser has ${errors.size} errors\n")
                    for (msg in errors) append("parser error: \"$msg\"\n")
                }
            )
        }
    }
}