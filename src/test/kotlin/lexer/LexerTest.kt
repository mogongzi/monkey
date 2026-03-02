package lexer

import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.token.*
import org.junit.jupiter.api.Test
import kotlin.test.fail

data class TestCase(val expectedType: TokenType, val expectedLiteral: String)

class LexerTest {

    @Test
    fun testNextToken() {
        val input = "=+(){},;"

        val tests = listOf(
            TestCase(ASSIGN, "="),
            TestCase(PLUS, "+"),
            TestCase(LPAREN, "("),
            TestCase(RPAREN, ")"),
            TestCase(LBRACE, "{"),
            TestCase(RBRACE, "}"),
            TestCase(COMMA, ","),
            TestCase(SEMICOLON, ";"),
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
    fun testNextTokenMultipleLines() {
        val input = """ let five = 5;
            let ten = 10;
            
            let add = fn(x, y) {
                x + y;
            };
            
            let result = add(five, ten);
            !-/*5;
            5 < 10 > 5;
            
            if (5 < 10) {
                return true;
            } else {
                return false;
            }
            
            10 == 10;
            10 != 9;
            "foobar"
            "foo bar"
            "hello\nworld"
            "say \"hi\""
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
            TestCase(BANG, "!"),
            TestCase(MINUS, "-"),
            TestCase(SLASH, "/"),
            TestCase(ASTERISK, "*"),
            TestCase(INT, "5"),
            TestCase(SEMICOLON, ";"),
            TestCase(INT, "5"),
            TestCase(LT, "<"),
            TestCase(INT, "10"),
            TestCase(GT, ">"),
            TestCase(INT, "5"),
            TestCase(SEMICOLON, ";"),
            TestCase(IF, "if"),
            TestCase(LPAREN, "("),
            TestCase(INT, "5"),
            TestCase(LT, "<"),
            TestCase(INT, "10"),
            TestCase(RPAREN, ")"),
            TestCase(LBRACE, "{"),
            TestCase(RETURN, "return"),
            TestCase(TRUE, "true"),
            TestCase(SEMICOLON, ";"),
            TestCase(RBRACE, "}"),
            TestCase(ELSE, "else"),
            TestCase(LBRACE, "{"),
            TestCase(RETURN, "return"),
            TestCase(FALSE, "false"),
            TestCase(SEMICOLON, ";"),
            TestCase(RBRACE, "}"),
            TestCase(INT, "10"),
            TestCase(EQ, "=="),
            TestCase(INT, "10"),
            TestCase(SEMICOLON, ";"),
            TestCase(INT, "10"),
            TestCase(NOT_EQ, "!="),
            TestCase(INT, "9"),
            TestCase(SEMICOLON, ";"),
            TestCase(STRING, "foobar"),
            TestCase(STRING, "foo bar"),
            TestCase(STRING, "hello\nworld"),
            TestCase(STRING, "say \"hi\""),
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