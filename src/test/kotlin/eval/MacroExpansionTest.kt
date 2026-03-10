package eval

import me.ryan.interpreter.ast.Program
import me.ryan.interpreter.eval.Environment
import me.ryan.interpreter.eval.MMacro
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.parser.Parser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MacroExpansionTest {
    @Test
    fun testDefineMacros() {
        val input = """
            let number = 1;
            let function = fn(x, y) { x + y };
            let mymacro = macro(x, y) { x + y; };
        """.trimIndent()

        val env = Environment()
        val program = testParseProgram(input)

        DefineMacros(program, env)

        assertEquals(
            2, program.statements.size,
            "Wrong number of statements. got=${program.statements.size}"
        )

        assertNull(env.get("number"), "number should not be defined")
        assertNull(env.get("function"), "function should not be defined")

        val obj = env.get("mymacro") ?: fail("macro not in environment.")

        val macro = obj as? MMacro ?: fail("object is not MMacro. got=${obj::class} ($obj)")

        assertEquals(
            2, macro.parameters.size,
            "Wrong number of macro parameters. got=${macro.parameters.size}"
        )
        assertEquals(
            "x", macro.parameters[0].string(),
            "parameter is not 'x'. got=${macro.parameters[0]}"
        )
        assertEquals(
            "y", macro.parameters[1].string(),
            "parameter is not 'y'. got=${macro.parameters[1]}"
        )

        val expectedBody = "(x + y)"
        assertEquals(
            expectedBody, macro.body.string(),
            "body is not $expectedBody. got=${macro.body.string()}"
        )
    }

    private fun testParseProgram(input: String): Program {
        val lexer = Lexer(input)
        val parser = Parser(lexer)
        return parser.parseProgram()
    }
}