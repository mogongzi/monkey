package parser

import me.ryan.interpreter.ast.LetStatement
import me.ryan.interpreter.ast.ReturnStatement
import me.ryan.interpreter.ast.Statement
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.parser.Parser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
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
            val returnStmt = assertInstanceOf(ReturnStatement::class.java, stmt, "stmt not ReturnStatement. got=${stmt::class}")
            assertEquals("return", returnStmt.tokenLiteral(), "returnStmt.tokenLiteral() not 'return', got=${returnStmt.tokenLiteral()}")
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