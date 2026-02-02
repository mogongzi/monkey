package parser

import me.ryan.interpreter.ast.LetStatement
import me.ryan.interpreter.ast.Statement
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.parser.Parser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.test.fail

class ParserTest {
    @Test
    fun parseProgram() {
        val input = """
            let x = 5;
            let y = 10;
            let foobar = 838383;
        """.trimIndent()

        val lexer = Lexer(input)
        val parser = Parser(lexer)

        val program = parser.parseProgram()

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


}