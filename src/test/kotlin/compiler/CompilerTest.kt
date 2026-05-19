package compiler

import me.ryan.interpreter.ast.Node
import me.ryan.interpreter.code.*
import me.ryan.interpreter.compiler.Compiler
import me.ryan.interpreter.eval.MCompiledFunction
import me.ryan.interpreter.eval.MInteger
import me.ryan.interpreter.eval.MObject
import me.ryan.interpreter.eval.MString
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.parser.Parser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

@OptIn(ExperimentalUnsignedTypes::class)
data class CompilerTestCase(val input: String, val expectedConstants: List<Any>, val expectedInstructions: List<Instructions>)

@JvmInline
@OptIn(ExperimentalUnsignedTypes::class)
value class FunctionInstructions(val instructions: List<Instructions>)

@OptIn(ExperimentalUnsignedTypes::class)
class CompilerTest {

    @Test
    fun testIntegerArithmetic() {
        val tests = listOf(
            CompilerTestCase(
                input = "1 + 2",
                expectedConstants = listOf(1, 2),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpConstant, 1),
                    make(OpAdd),
                    make(OpPop),
                ),
            ),
            CompilerTestCase(
                input = "1;2",
                expectedConstants = listOf(1, 2),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpPop),
                    make(OpConstant, 1),
                    make(OpPop),
                )
            ),
            CompilerTestCase(
                input = "1 - 2",
                expectedConstants = listOf(1, 2),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpConstant, 1),
                    make(OpSub),
                    make(OpPop),
                ),
            ),
            CompilerTestCase(
                input = "1 * 2",
                expectedConstants = listOf(1, 2),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpConstant, 1),
                    make(OpMul),
                    make(OpPop),
                ),
            ),
            CompilerTestCase(
                input = "2 / 1",
                expectedConstants = listOf(2, 1),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpConstant, 1),
                    make(OpDiv),
                    make(OpPop),
                ),
            ),
            CompilerTestCase(
                input = "-1",
                expectedConstants = listOf(1),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpMinus),
                    make(OpPop),
                )
            )
        )

        runCompilerTests(tests)
    }

    @Test
    fun testBooleanExpressions() {
        val tests = listOf(
            CompilerTestCase(
                input = "true",
                expectedConstants = emptyList(),
                expectedInstructions = listOf(
                    make(OpTrue),
                    make(OpPop),
                ),
            ),
            CompilerTestCase(
                input = "false",
                expectedConstants = emptyList(),
                expectedInstructions = listOf(
                    make(OpFalse),
                    make(OpPop),
                ),
            ),
            CompilerTestCase(
                input = "1 > 2",
                expectedConstants = listOf(1, 2),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpConstant, 1),
                    make(OpGreaterThan),
                    make(OpPop),
                ),
            ),
            CompilerTestCase(
                input = "1 < 2",
                expectedConstants = listOf(2, 1),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpConstant, 1),
                    make(OpGreaterThan),
                    make(OpPop),
                ),
            ),
            CompilerTestCase(
                input = "1 == 2",
                expectedConstants = listOf(1, 2),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpConstant, 1),
                    make(OpEqual),
                    make(OpPop),
                ),
            ),
            CompilerTestCase(
                input = "1 != 2",
                expectedConstants = listOf(1, 2),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpConstant, 1),
                    make(OpNotEqual),
                    make(OpPop),
                ),
            ),
            CompilerTestCase(
                input = "true == false",
                expectedConstants = emptyList(),
                expectedInstructions = listOf(
                    make(OpTrue),
                    make(OpFalse),
                    make(OpEqual),
                    make(OpPop),
                ),
            ),
            CompilerTestCase(
                input = "true != false",
                expectedConstants = emptyList(),
                expectedInstructions = listOf(
                    make(OpTrue),
                    make(OpFalse),
                    make(OpNotEqual),
                    make(OpPop),
                ),
            ),
            CompilerTestCase(
                input = "!true",
                expectedConstants = emptyList(),
                expectedInstructions = listOf(
                    make(OpTrue),
                    make(OpBang),
                    make(OpPop),
                )
            ),
        )

        runCompilerTests(tests)
    }

    @Test
    fun testConditionals() {
        val tests = listOf(
            CompilerTestCase(
                input = """
                if (true) { 10 }; 3333;
            """.trimIndent(),
                expectedConstants = listOf(10, 3333),
                expectedInstructions = listOf(
                    make(OpTrue),
                    make(OpJumpNotTruthy, 10),
                    make(OpConstant, 0),
                    make(OpJump, 11),
                    make(OpNull),
                    make(OpPop),
                    make(OpConstant, 1),
                    make(OpPop),
                ),
            ),
            CompilerTestCase(
                input = """
                if (true) { 10 } else { 20 }; 3333;
            """.trimIndent(),
                expectedConstants = listOf(10, 20, 3333),
                expectedInstructions = listOf(
                    make(OpTrue),
                    make(OpJumpNotTruthy, 10),
                    make(OpConstant, 0),
                    make(OpJump, 13),
                    make(OpConstant, 1),
                    make(OpPop),
                    make(OpConstant, 2),
                    make(OpPop),
                ),
            ),
        )

        runCompilerTests(tests)
    }

    @Test
    fun testGlobalLetStatements() {
        val tests = listOf(
            CompilerTestCase(
                input = """
                let one = 1;
                let two = 2;
            """.trimIndent(),
                expectedConstants = listOf(1, 2),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpSetGlobal, 0),
                    make(OpConstant, 1),
                    make(OpSetGlobal, 1),
                ),
            ),
            CompilerTestCase(
                input = """
                let one = 1;
                one;
            """.trimIndent(),
                expectedConstants = listOf(1),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpSetGlobal, 0),
                    make(OpGetGlobal, 0),
                    make(OpPop),
                ),
            ),
            CompilerTestCase(
                input = """
                let one = 1;
                let two = one;
                two;
            """.trimIndent(),
                expectedConstants = listOf(1),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpSetGlobal, 0),
                    make(OpGetGlobal, 0),
                    make(OpSetGlobal, 1),
                    make(OpGetGlobal, 1),
                    make(OpPop),
                ),
            ),
        )

        runCompilerTests(tests)
    }

    @Test
    fun testStringExpressions() {
        val tests = listOf(
            CompilerTestCase(
                input = "\"monkey\"",
                expectedConstants = listOf("monkey"),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpPop),
                )
            ),
            CompilerTestCase(
                input = "\"mon\" + \"key\"",
                expectedConstants = listOf("mon", "key"),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpConstant, 1),
                    make(OpAdd),
                    make(OpPop),
                )
            ),
        )

        runCompilerTests(tests)
    }

    @Test
    fun testArrayLiterals() {
        val tests = listOf(
            CompilerTestCase(
                input = "[]",
                expectedConstants = emptyList(),
                expectedInstructions = listOf(
                    make(OpArray, 0),
                    make(OpPop),
                )
            ),
            CompilerTestCase(
                input = "[1, 2, 3]",
                expectedConstants = listOf(1, 2, 3),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpConstant, 1),
                    make(OpConstant, 2),
                    make(OpArray, 3),
                    make(OpPop),
                )
            ),
            CompilerTestCase(
                input = "[1 + 2, 3 - 4, 5 * 6]",
                expectedConstants = listOf(1, 2, 3, 4, 5, 6),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpConstant, 1),
                    make(OpAdd),
                    make(OpConstant, 2),
                    make(OpConstant, 3),
                    make(OpSub),
                    make(OpConstant, 4),
                    make(OpConstant, 5),
                    make(OpMul),
                    make(OpArray, 3),
                    make(OpPop),
                )
            ),
        )

        runCompilerTests(tests)
    }

    @Test
    fun testHashLiterals() {
        val tests = listOf(
            CompilerTestCase(
                input = "{}",
                expectedConstants = emptyList(),
                expectedInstructions = listOf(
                    make(OpHash, 0),
                    make(OpPop),
                )
            ),
            CompilerTestCase(
                input = "{1: 2, 3: 4, 5: 6}",
                expectedConstants = listOf(1, 2, 3, 4, 5, 6),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpConstant, 1),
                    make(OpConstant, 2),
                    make(OpConstant, 3),
                    make(OpConstant, 4),
                    make(OpConstant, 5),
                    make(OpHash, 6),
                    make(OpPop),
                )
            ),
            CompilerTestCase(
                input = "{1: 2 + 3, 4: 5 * 6}",
                expectedConstants = listOf(1, 2, 3, 4, 5, 6),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpConstant, 1),
                    make(OpConstant, 2),
                    make(OpAdd),
                    make(OpConstant, 3),
                    make(OpConstant, 4),
                    make(OpConstant, 5),
                    make(OpMul),
                    make(OpHash, 4),
                    make(OpPop),
                )
            )
        )

        runCompilerTests(tests)
    }

    @Test
    fun testIndexExpressions() {
        val tests = listOf(
            CompilerTestCase(
                input = "[1, 2, 3][1 + 1]",
                expectedConstants = listOf(1, 2, 3, 1, 1),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpConstant, 1),
                    make(OpConstant, 2),
                    make(OpArray, 3),
                    make(OpConstant, 3),
                    make(OpConstant, 4),
                    make(OpAdd),
                    make(OpIndex),
                    make(OpPop),
                )
            ),
            CompilerTestCase(
                input = "{1 :2}[2 - 1]",
                expectedConstants = listOf(1, 2, 2, 1),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpConstant, 1),
                    make(OpHash, 2),
                    make(OpConstant, 2),
                    make(OpConstant, 3),
                    make(OpSub),
                    make(OpIndex),
                    make(OpPop),
                )
            )
        )

        runCompilerTests(tests)
    }

    @Test
    fun testFunctions() {
        val tests = listOf(
            CompilerTestCase(
                input = "fn() { return 5 + 10 }",
                expectedConstants = listOf(
                    5, 10,
                    FunctionInstructions(
                        listOf(
                            make(OpConstant, 0),
                            make(OpConstant, 1),
                            make(OpAdd),
                            make(OpReturnValue),
                        )
                    )
                ),
                expectedInstructions = listOf(
                    make(OpConstant, 2),
                    make(OpPop),
                )
            ),
            CompilerTestCase(
                input = "fn() { 5 + 10 }",
                expectedConstants = listOf(
                    5, 10,
                    FunctionInstructions(
                        listOf(
                            make(OpConstant, 0),
                            make(OpConstant, 1),
                            make(OpAdd),
                            make(OpReturnValue),
                        )
                    )
                ),
                expectedInstructions = listOf(
                    make(OpConstant, 2),
                    make(OpPop),
                )
            ),
            CompilerTestCase(
                input = "fn() { 1; 2 }",
                expectedConstants = listOf(
                    1, 2,
                    FunctionInstructions(
                        listOf(
                            make(OpConstant, 0),
                            make(OpPop),
                            make(OpConstant, 1),
                            make(OpReturnValue),
                        )
                    )
                ),
                expectedInstructions = listOf(
                    make(OpConstant, 2),
                    make(OpPop),
                )
            ),
        )

        runCompilerTests(tests)
    }

    @Test
    fun testCompilerScope() {
        val compiler = Compiler()
        assertEquals(0, compiler.scopeIndex, "scopeIndex wrong. got=$compiler.scopeIndex, want=0")
        compiler.emit(OpMul)
        compiler.enterScope()
        assertEquals(1, compiler.scopeIndex, "scopeIndex wrong. got=$compiler.scopeIndex, want=1")
        compiler.emit(OpSub)
        assertEquals(
            1,
            compiler.scopes[compiler.scopeIndex].instructions.size,
            "instructions length wrong. got=${compiler.scopes[compiler.scopeIndex].instructions.size}"
        )
        var last = compiler.scopes[compiler.scopeIndex].lastInstruction
        assertEquals(OpSub, last!!.opcode, "lastInstruction.Opcode wrong. got=${last.opcode}, want=$OpSub")
        compiler.leaveScope()
        assertEquals(0, compiler.scopeIndex, "scopeIndex wrong. got=$compiler.scopeIndex")
        compiler.emit(OpAdd)
        assertEquals(
            2,
            compiler.scopes[compiler.scopeIndex].instructions.size,
            "instructions length wrong. got=${compiler.scopes[compiler.scopeIndex].instructions.size}"
        )
        last = compiler.scopes[compiler.scopeIndex].lastInstruction
        assertEquals(OpAdd, last!!.opcode, "lastInstruction.Opcode wrong. got=${last.opcode}, want=$OpAdd")
        val previous = compiler.scopes[compiler.scopeIndex].previousInstruction
        assertEquals(OpMul, previous!!.opcode, "previousInstruction.Opcode wrong. got=${previous.opcode}, want=$OpMul")
    }

    @Test
    fun testFunctionsWithoutReturnValue() {
        val tests = listOf(
            CompilerTestCase(
                input = "fn() { }",
                expectedConstants = listOf(
                    FunctionInstructions(
                        listOf(
                            make(OpReturn),
                        )
                    )
                ),
                expectedInstructions = listOf(
                    make(OpConstant, 0),
                    make(OpPop),
                )
            ),
        )

        runCompilerTests(tests)
    }

    private fun runCompilerTests(tests: List<CompilerTestCase>) {
        for (test in tests) {
            val program = parse(test.input)
            val compiler = Compiler()
            compiler.compile(program)
            val bytecode = compiler.bytecode()

            testInstructions(test.expectedInstructions, bytecode.instructions)
            testConstants(test.expectedConstants, bytecode.constants)
        }
    }

    private fun parse(input: String): Node {
        val lexer = Lexer(input)
        val parser = Parser(lexer)
        return parser.parseProgram()
    }

    private fun testInstructions(expected: List<Instructions>, actual: Instructions) {
        val concatted = concatInstructions(expected)
        assertEquals(
            concatted.size, actual.size,
            "wrong instructions length. \nwant=\n${concatted.string()}\ngot=\n${actual.string()}"
        )

        concatted.forEachIndexed { i, ins ->
            if (actual[i] != ins) {
                throw AssertionError(
                    "wrong instruction at $i.\nwant=\n${concatted.string()}\ngot=\n${actual.string()}"
                )
            }
        }
    }

    private fun concatInstructions(s: List<Instructions>): Instructions {
        return s.reduce { acc, ins -> acc + ins }
    }

    private fun testConstants(expected: List<Any>, actual: List<MObject>) {
        assertEquals(
            expected.size, actual.size,
            "wrong number of constants. got=$actual, want=$expected"
        )

        expected.forEachIndexed { i, constant ->
            when (constant) {
                is Int -> testMIntegerObject(constant.toLong(), actual[i])
                is String -> testMStringObject(constant, actual[i])
                is FunctionInstructions -> {
                    val fn = assertInstanceOf(
                        MCompiledFunction::class.java, actual[i],
                        "constant $i - not a function: ${actual[i]::class.simpleName}"
                    )
                    testInstructions(constant.instructions, fn.instructions)
                }
            }
        }
    }

    private fun testMIntegerObject(expected: Long, actual: MObject) {
        val result = assertInstanceOf(
            MInteger::class.java, actual,
            "MObject is not MInteger. got=${actual::class.simpleName} (${actual.inspect()})"
        )
        assertEquals(
            expected, result.value,
            "MObject has wrong value. got=${result.value}, want=$expected"
        )
    }

    private fun testMStringObject(expected: String, actual: MObject) {
        val result = assertInstanceOf(
            MString::class.java, actual,
            "MObject is not MString. got=${actual::class.simpleName} (${actual.inspect()})"
        )
        assertEquals(
            expected, result.value,
            "MObject has wrong value. got=${result.value}, want=$expected"
        )
    }

    private fun fnInstructions(ins: List<UByteArray>): List<Instructions> = ins
}
