package eval

import me.ryan.interpreter.eval.*
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.parser.Parser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable

class EvaluatorTest {

    val evaluator = Evaluator()

    private val mInteger = MInteger::class.simpleName
    private val mBoolean = MBoolean::class.simpleName
    private val mString = MString::class.simpleName

    @Test
    fun testEvalIntegerExpression() {
        val tests = listOf(
            Pair("5", 5L),
            Pair("10", 10L),
            Pair("-5", -5L),
            Pair("-10", -10L),
            Pair("5 + 5 + 5 + 5 - 10", 10L),
            Pair("2 * 2 * 2 * 2 * 2", 32L),
            Pair("-50 + 100 + -50", 0L),
            Pair("5 * 2 + 10", 20L),
            Pair("5 + 2 * 10", 25L),
            Pair("20 + 2 * -10", 0L),
            Pair("50 / 2 * 2 + 10", 60L),
            Pair("2 * (5 + 10)", 30L),
            Pair("3 * 3 * 3 + 10", 37L),
            Pair("3 * (3 * 3) + 10", 37L),
            Pair("(5 + 10 * 2 + 15 / 3) * 2 + -10", 50L),
        )

        for ((input, expected) in tests) {
            val evaluated = testEval(input)
            testMIntegerObject(evaluated, expected)
        }
    }

    @Test
    fun testEvalBooleanExpression() {
        val tests = listOf(
            "true" to true,
            "false" to false,
            "1 < 2" to true,
            "1 > 2" to false,
            "1 < 1" to false,
            "1 > 1" to false,
            "1 == 1" to true,
            "1 != 1" to false,
            "1 == 2" to false,
            "1 != 2" to true,
            "true == true" to true,
            "false == false" to true,
            "true == false" to false,
            "true != false" to true,
            "false != true" to true,
            "(1 < 2) == true" to true,
            "(1 < 2) == false" to false,
            "(1 > 2) == true" to false,
            "(1 > 2) == false" to true,
        )

        for ((input, expected) in tests) {
            val evaluated = testEval(input)
            testMBooleanObject(evaluated, expected)
        }
    }

    @Test
    fun testBangOperator() {
        val tests = listOf(
            "!true" to false,
            "!false" to true,
            "!5" to false,
            "!!true" to true,
            "!!false" to false,
            "!!5" to true
        )

        for ((input, expected) in tests) {
            val evaluated = testEval(input)
            testMBooleanObject(evaluated, expected)
        }
    }

    @Test
    fun testIfElseExpressions() {
        val tests = listOf(
            "if (true) { 10 }" to 10L,
            "if (false) { 10 }" to null,
            "if (1) { 10 }" to 10L,
            "if (1 < 2) { 10 }" to 10L,
            "if (1 > 2) { 10 }" to null,
            "if (1 > 2) { 10 } else { 20 }" to 20L,
            "if (1 < 2) { 10 } else { 20 }" to 10L,
        )

        for ((input, expected) in tests) {
            val evaluated = testEval(input)
            if (expected != null) {
                testMIntegerObject(evaluated, expected)
            } else {
                testMNULLObject(evaluated)
            }
        }
    }

    @Test
    fun testReturnStatements() {
        val tests = listOf(
            "return 10;" to 10L,
            "return 10; 9;" to 10L,
            "return 2 * 5; 9;" to 10L,
            "9; return 2 * 5; 9;" to 10L,
            """
            if (10 > 1) {
              if (10 > 1) {
                return 10;
              }
                  
              return 1;
            }
            """.trimIndent() to 10L,
        )

        for ((input, expected) in tests) {
            val evaluated = testEval(input)
            testMIntegerObject(evaluated, expected)
        }
    }

    @Test
    fun testErrorHandling() {
        val tests = listOf(
            "5 + true;" to "type mismatch: $mInteger + $mBoolean",
            "5 + true; 5;" to "type mismatch: $mInteger + $mBoolean",
            "-true" to "unknown operator: -$mBoolean",
            "true + false;" to "unknown operator: $mBoolean + $mBoolean",
            "5; true + false; 5" to "unknown operator: $mBoolean + $mBoolean",
            "if (10 > 1) { true + false; }" to "unknown operator: $mBoolean + $mBoolean",
            """
            if (10 > 1) {
              if (10 > 1) {
                return true + false;
              }
              return 1;
            }
            """.trimIndent() to "unknown operator: $mBoolean + $mBoolean",
            "foobar" to "identifier not found: foobar",
            "\"Hello\" - \"World!\"" to "unknown operator: $mString - $mString",
            "{\"name\": \"Monkey\"}[fn(x) { x }];" to "unusable as hash key: MFunction",
        )

        assertAll(tests.map { (input, expectedMsg) ->
            Executable {
                val evaluated = testEval(input)
                assertInstanceOf(
                    MError::class.java, evaluated,
                    "No error object returned for '$input'. got=${evaluated.let { it::class.java }}($evaluated)"
                )
                assertEquals(expectedMsg, (evaluated as MError).message)
            }
        })
    }

    @Test
    fun testLetStatements() {
        val tests = listOf(
            "let a = 5; a;" to 5L,
            "let a = 5 * 5; a;" to 25L,
            "let a = 5; let b = a; b;" to 5L,
            "let a = 5; let b = a; let c = a + b + 5; c;" to 15L,
        )

        for ((input, expected) in tests) {
            testMIntegerObject(testEval(input), expected)
        }
    }

    @Test
    fun testFunctionObject() {
        val input = "fn(x) { x + 2; };"

        val evaluated = testEval(input)
        assertInstanceOf(
            MFunction::class.java, evaluated, "object is not Function. got=${evaluated::class.java}"
        )
        val fn = evaluated as MFunction
        assertEquals(1, fn.parameters.size, "function has wrong parameters. Parameters=${fn.parameters.size}")
        assertEquals("x", fn.parameters.first().string(), "parameter is not 'x'. got=${fn.parameters.first().string()}")
        assertEquals("(x + 2)", fn.body.string(), "body is not (x + 2). got=${fn.body.string()}")
    }

    @Test
    fun testFunctionApplication() {
        val tests = listOf(
            "let identity = fn(x) { x; }; identity(5);" to 5L,
            "let identity = fn(x) { return x; }; identity(5);" to 5L,
            "let double = fn(x) { x * 2; }; double(5);" to 10L,
            "let add = fn(x, y) { x + y; }; add(5, 5);" to 10L,
            "let add = fn(x, y) { x + y; }; add(5 + 5, add(5, 5));" to 20L,
            "fn(x) { x; }(5)" to 5L,
            """
                let newAdder = fn(x) {
                  fn(y) { x + y };
                };
                let addTwo = newAdder(2);
                addTwo(2);
            """.trimIndent() to 4L,
            """
                let add = fn(a, b) { a + b };
                let applyFunc = fn(a, b, func) { func(a, b) };
                applyFunc(2, 2, add);
            """.trimIndent() to 4L,
        )

        for ((input, expected) in tests) {
            testMIntegerObject(testEval(input), expected)
        }
    }

    @Test
    fun testStringLiteral() {
        val input = "\"Hello World!\""
        val evaluated = testEval(input)
        assertInstanceOf(MString::class.java, evaluated)
        val str = evaluated as MString
        assertEquals("Hello World!", str.value)
    }

    @Test
    fun testStringConcatenation() {
        val input = "\"Hello\" + \" \" + \"World!\""
        val evaluated = testEval(input)
        assertInstanceOf(MString::class.java, evaluated)
        val str = evaluated as MString
        assertEquals("Hello World!", str.value)
    }

    @Test
    fun testBuiltinFunctions() {
        val tests = listOf(
            "len(\"\")" to 0L,
            "len(\"four\")" to 4L,
            "len(\"hello world\")" to 11L,
            "len(1)" to "argument to `len` not supported, got $mInteger",
            "len(\"one\", \"two\")" to "wrong number of arguments. got=2, want=1",
            "len([1, 2, 3])" to 3L,
        )

        for ((input, expected) in tests) {
            val evaluated = testEval(input)

            when (expected) {
                is Long -> testMIntegerObject(evaluated, expected)
                is String -> {
                    assertInstanceOf(
                        MError::class.java,
                        evaluated,
                        "object is not Error. got=${evaluated.let { it::class.java }} ($evaluated)"
                    )
                    assertEquals(expected, (evaluated as MError).message, "wrong error message.")
                }
            }
        }
    }

    @Test
    fun testNowBuiltin() {
        val evaluated = testEval("now()")
        assertInstanceOf(MString::class.java, evaluated)
        val result = (evaluated as MString).value
        assertTrue(result.endsWith("Z"), "expected UTC timestamp, got=$result")
    }

    @Test
    fun testNowWrongArgs() {
        val evaluated = testEval("now(1)")
        assertInstanceOf(MError::class.java, evaluated)
        assertEquals("wrong number of arguments. got=1, want=0", (evaluated as MError).message)
    }

    @Test
    fun testArrayLiterals() {
        val input = "[1, 2 * 2, 3 + 3]"
        val evaluated = testEval(input)
        val result = evaluated as MArray
        assertInstanceOf(MArray::class.java, result, "object is not Array. got=${result::class.simpleName}")
        assertEquals(3, result.elements.size, "array has wrong num of elements. got=${result.elements.size}")

        testMIntegerObject(result.elements[0], 1L)
        testMIntegerObject(result.elements[1], 4L)
        testMIntegerObject(result.elements[2], 6L)
    }

    @Test
    fun testArrayIndexExpressions() {
        val tests = listOf(
            "[1, 2, 3][0]" to 1L,
            "[1, 2, 3][1]" to 2L,
            "[1, 2, 3][2]" to 3L,
            "let i = 0; [1][i];" to 1L,
            "[1, 2, 3][1 + 1];" to 3L,
            "let myArray = [1, 2, 3]; myArray[2];" to 3L,
            "let myArray = [1, 2, 3]; myArray[0] + myArray[1] + myArray[2];" to 6L,
            "let myArray = [1, 2, 3]; let i = myArray[0]; myArray[i]" to 2L,
            "[1, 2, 3][3]" to null,
            "[1, 2, 3][-1]" to null,
        )

        for ((input, expected) in tests) {
            val evaluated = testEval(input)
            if (expected != null) {
                testMIntegerObject(evaluated, expected)
            } else {
                testMNULLObject(evaluated)
            }
        }
    }

    @Test
    fun testHashLiterals() {
        val input = """
            let two = "two";
            {
                "one": 10 - 9,
                two: 1 + 1,
                "thr" + "ee": 6 / 2,
                4: 4,
                true: 5,
                false: 6
            }
        """.trimIndent()
        val evaluated = testEval(input)
        val result = evaluated as MHash
        assertInstanceOf(MHash::class.java, evaluated, "eval didn't return MHash. got=${result::class.simpleName}")
        val expected = mapOf(
            MString("one").hashKey() to 1L,
            MString("two").hashKey() to 2L,
            MString("three").hashKey() to 3L,
            MInteger(4).hashKey() to 4L,
            TRUE.hashKey() to 5L,
            FALSE.hashKey() to 6L,
        )

        assertEquals(expected.size, result.pairs.size, "Hash has wrong num of pairs. got=${result.pairs.size}")
        for ((expectedKey, expectedValue) in expected) {
            // Using kotlin.test.assertNotNull instead of JUnit's org.junit.jupiter.api.Assertions.assertNotNull:
            // JUnit's version returns Unit (void), so casting its result (e.g., `as HashPair`) always fails.
            // Kotlin's version is generic — fun <T : Any> assertNotNull(actual: T?): T — and returns the
            // non-null value directly, giving us smart-cast to HashPair without an explicit cast.
            val pair = kotlin.test.assertNotNull(result.pairs[expectedKey], "no pair for given key in Pairs")
            testMIntegerObject(pair.value, expectedValue)
        }
    }

    @Test
    fun testHashIndexExpressions() {
        val tests = listOf(
            "{\"foo\": 5}[\"foo\"]" to 5L,
            "{\"foo\": 5}[\"bar\"]" to null,
            "let key = \"foo\"; {\"foo\": 5}[key]" to 5L,
            "{}[\"foo\"]" to null,
            "{5: 5}[5]" to 5L,
            "{true: 5}[true]" to 5L,
            "{false: 5}[false]" to 5L,
        )

        for ((input, expected) in tests) {
            val evaluated = testEval(input)
            if (expected != null) {
                testMIntegerObject(evaluated, expected)
            } else {
                testMNULLObject(evaluated)
            }
        }
    }

    @Test
    fun testQuote() {
        val tests = listOf(
            "quote(5)" to "5",
            "quote(foobar)" to "foobar",
            "quote(5 + 8)" to "(5 + 8)",
            "quote(foobar + barfoo)" to "(foobar + barfoo)",
        )

        for ((input, expected) in tests) {
            val evaluated = testEval(input)
            val quote = assertInstanceOf(
                MQuote::class.java,
                evaluated,
                "expected MQuote. got=${evaluated::class.simpleName}"
            )
            assertNotNull(quote.node, "quote.node is null")
            assertEquals(
                expected,
                quote.node.string(),
                "not equal. got=${quote.node.string()}, want=${quote.node.string()}"
            )
        }
    }

    @Test
    fun testQuoteUnQuote() {
        val tests = listOf(
            "quote(unquote(4))" to "4",
            "quote(unquote(4 + 4))" to "8",
            "quote(8 + unquote(4 + 4))" to "(8 + 8)",
            "quote(unquote(4 + 4) + 8)" to "(8 + 8)",
            "let foobar = 8;quote(foobar)" to "foobar",
            "let foobar = 8;quote(unquote(foobar))" to "8",
            "quote(unquote(true))" to "true",
            "quote(unquote(true == false))" to "false",
            "quote(unquote(quote(4 + 4)))" to "(4 + 4)",
            "let quotedInfixExpression = quote(4 + 4);quote(unquote(4 + 4) + unquote(quotedInfixExpression))" to "(8 + (4 + 4))",
        )

        for ((input, expected) in tests) {
            val evaluated = testEval(input)
            val quote = assertInstanceOf(
                MQuote::class.java,
                evaluated,
                "expected MQuote. got=${evaluated::class.simpleName}"
            )
            assertNotNull(quote.node, "quote.node is null")
            assertEquals(
                expected,
                quote.node.string(),
                "not equal. got=${quote.node.string()}, want=${expected}"
            )
        }
    }

    private fun testMNULLObject(obj: MObject) {
        assertEquals(MNULL, obj, "object is not NULL. got=${obj::class} ($obj)")
    }

    private fun testEval(input: String): MObject {
        val lexer = Lexer(input)
        val parser = Parser(lexer)
        val program = parser.parseProgram()
        val env = Environment()

        return evaluator.eval(program, env)
    }

    private fun testMIntegerObject(obj: MObject, expected: Long) {
        assertInstanceOf(MInteger::class.java, obj)
        val result = obj as MInteger
        assertEquals(expected, result.value)
    }

    private fun testMBooleanObject(obj: MObject, expected: Boolean) {
        val result = obj as? MBoolean ?: fail("object is not MBoolean. got=${obj::class}")
        assertEquals(expected, result.value)
    }
}