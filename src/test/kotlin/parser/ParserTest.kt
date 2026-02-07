package parser

import me.ryan.interpreter.ast.Expression
import me.ryan.interpreter.ast.ExpressionStatement
import me.ryan.interpreter.ast.Identifier
import me.ryan.interpreter.ast.IntegerLiteral
import me.ryan.interpreter.ast.LetStatement
import me.ryan.interpreter.ast.PrefixExpression
import me.ryan.interpreter.ast.ReturnStatement
import me.ryan.interpreter.ast.Statement
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.parser.Parser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.jvm.java
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
            Triple("-15;", "-", 15L)
        )

        for ((input, operator, integerValue) in prefixTests) {
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

            if (!testIntegerLiteral(exp.right, integerValue)) return
        }
    }

    private fun testIntegerLiteral(exp: Expression?, value: Long): Boolean {
        val integ = exp as? IntegerLiteral
        if (integ == null) {
            fail("exp not IntegerLiteral. got=${exp?.let { it::class }}")
            return false
        }

        if (integ.value != value) {
            fail("integ.value not ${value}. got=${integ.value}")
            return false
        }

        if (integ.tokenLiteral() != value.toString()) {
            fail("integ.tokenLiteral() not ${value}. got=${integ.tokenLiteral()}")
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