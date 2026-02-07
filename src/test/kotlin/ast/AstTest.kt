package ast

import me.ryan.interpreter.ast.Identifier
import me.ryan.interpreter.ast.LetStatement
import me.ryan.interpreter.ast.Program
import me.ryan.interpreter.ast.ReturnStatement
import me.ryan.interpreter.ast.Statement
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.parser.Parser
import me.ryan.interpreter.token.IDENT
import me.ryan.interpreter.token.LET
import me.ryan.interpreter.token.Token
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.test.fail

class AstTest {
    @Test
    fun `program string`() {
        val program = Program(
            mutableListOf(
                LetStatement(
                    token = Token(LET, "let"),
                    name = Identifier(
                        token = Token(IDENT, "myVar"),
                        value = "myVar",
                    ),
                    value = Identifier(
                        token = Token(IDENT, "anotherVar"),
                        value = "anotherVar",
                    )
                )
            )
        )

        assertEquals("let myVar = anotherVar;", program.string(), "program.string() wrong. got=${program.string()}")
    }
}