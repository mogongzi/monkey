package code

import me.ryan.interpreter.code.OpConstant
import me.ryan.interpreter.code.Opcode
import me.ryan.interpreter.code.lookup
import me.ryan.interpreter.code.make
import me.ryan.interpreter.code.readOperands
import me.ryan.interpreter.code.string
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalUnsignedTypes::class)
class TestCase(val op: Opcode, val operands: IntArray, val expected: UByteArray)

@OptIn(ExperimentalUnsignedTypes::class)
class CodeTest {

    @Test
    fun testMake() {
        val tests = listOf(
            TestCase(
                OpConstant,
                intArrayOf(65534),
                ubyteArrayOf(OpConstant, 255u, 254u)
            )
        )

        for (test in tests) {
            val instruction = make(test.op, *(test.operands))
            assertEquals(
                test.expected.size,
                instruction.size,
                "instruction has wrong length. want=${test.expected.size}, got=${instruction.size}"
            )

            test.expected.forEachIndexed { index, b ->
                assertEquals(
                    test.expected[index], instruction[index],
                    "wrong byte at pos $index. want=$b, got=${instruction[index]}"
                )
            }
        }
    }

    @Test
    fun testInstructionsString() {
        val instructions = listOf(
            make(OpConstant, 1),
            make(OpConstant, 2),
            make(OpConstant, 65535),
        )

        val expected = """
            0000 OpConstant 1
            0003 OpConstant 2
            0006 OpConstant 65535
        """.trimIndent()

        val concatted = instructions.reduce { acc, ins -> acc + ins }
        assertEquals(expected, concatted.string(),
            "instructions wrongly formatted.\nwant=$expected\ngot=${concatted.string()}"
        )
    }

    @Test
    fun testReadOperands() {
        val tests = listOf(
            Triple(OpConstant, intArrayOf(65535), 2),
        )

        for ((op, operands, bytesRead) in tests) {
            val instruction = make(op, *operands)
            val def = lookup(op)
            assertNotNull(def, "definition not found")
            // drop the first element - opcode (sliceArray 1 ..instruction.size - 1)
            val (operands, n) = readOperands(def, instruction.sliceArray(1 until instruction.size))
            assertEquals(bytesRead, n, "n wrong. want=$bytesRead, got=$n")

            operands.forEachIndexed { index, want ->
                assertEquals(want, operands[index],
                    "operand wrong. want=$want, got=${operands[index]}")
            }
        }
    }
}