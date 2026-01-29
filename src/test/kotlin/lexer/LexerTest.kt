package lexer

import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.token.*
import org.junit.jupiter.api.Test
import kotlin.test.fail

data class TestCase(val expectedType: TokenType, val expectedLiteral: String)

class LexerTest {

    @Test
    fun nextToken() {
        val input = "=+(){},;"

        val tests = listOf(
            TestCase(ASSIGN, "="),
            TestCase(PLUS, "+"),
            TestCase(LPAREN, "("),
            TestCase(RPAREN, ")"),
            TestCase(LBRACE, "{"),
            TestCase(RBRACE, "}"),
            TestCase(COMMA, ","),
            TestCase(SEMICOLON, ";")
        )

        val l = Lexer(input)

        for ((i, tt) in tests.withIndex()) {
            val tok = l.nextToken()

            if (tok.type != tt.expectedType) {
                fail("tests[$i] - tokentype wrong. expected=${tt.expectedType}, got=${tok.type}")
            }

            if (tok.literal != tt.expectedLiteral) {
                fail("tests[$i] - literal wrong. expected=${tt.expectedLiteral}, got=${tok.literal}")
            }

        }
    }

    @Test
    fun nextTokenMultipleLines() {
        val input = """ let five = 5;
            let ten = 10;
            
            let add = fn(x, y) {
                x + y;
            };
            
            let result = add(five, ten);
        """.trimIndent()

        val tests = listOf(
            TestCase(LET, "let"),
            TestCase(IDENT, "five"),
            TestCase(ASSIGN, "="),
            TestCase(INT, "5"),
            TestCase(SEMICOLON, ";"),
            TestCase(LET, "let"),
            TestCase(IDENT, "ten"),
            TestCase(ASSIGN, "="),
            TestCase(INT, "10"),
            TestCase(SEMICOLON, ";"),
            TestCase(LET, "let"),
            TestCase(IDENT, "add"),
            TestCase(ASSIGN, "="),
            TestCase(FUNCTION, "fn"),
            TestCase(LPAREN, "("),
            TestCase(IDENT, "x"),
            TestCase(COMMA, ","),
            TestCase(IDENT, "y"),
            TestCase(RPAREN, ")"),
            TestCase(LBRACE, "{"),
            TestCase(IDENT, "x"),
            TestCase(PLUS, "+"),
            TestCase(IDENT, "y"),
            TestCase(SEMICOLON, ";"),
            TestCase(RBRACE, "}"),
            TestCase(SEMICOLON, ";"),
            TestCase(LET, "let"),
            TestCase(IDENT, "result"),
            TestCase(ASSIGN, "="),
            TestCase(IDENT, "add"),
            TestCase(LPAREN, "("),
            TestCase(IDENT, "five"),
            TestCase(COMMA, ","),
            TestCase(IDENT, "ten"),
            TestCase(RPAREN, ")"),
            TestCase(SEMICOLON, ";"),
            TestCase(EOF, "")
        )

        val l = Lexer(input)

        for ((i, tt) in tests.withIndex()) {
            val tok = l.nextToken()

            if (tok.type != tt.expectedType) {
                fail("tests[$i] - tokentype wrong. expected=${tt.expectedType}, got=${tok.type}")
            }

            if (tok.literal != tt.expectedLiteral) {
                fail("tests[$i] - literal wrong. expected=${tt.expectedLiteral}, got=${tok.literal}")
            }

        }
    }

}