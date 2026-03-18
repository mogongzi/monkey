package me.ryan.interpreter.code

@OptIn(ExperimentalUnsignedTypes::class)
typealias Instructions = UByteArray
typealias Opcode = UByte

const val OpConstant: Opcode = 0u

data class Definition(val name: String, val operandWidths: List<Int>)

// mapOf<Opcode, Definition>
val definitions = mapOf(
    OpConstant to Definition("OpConstant", listOf(2))
)

fun lookup(op: Opcode): Definition? = definitions[op]

@OptIn(ExperimentalUnsignedTypes::class)
fun make(opcode: Opcode, vararg operands: Int): Instructions {
    val def = lookup(opcode) ?: return ubyteArrayOf()
    val instructionLen = 1 + def.operandWidths.sumOf { it }
    val instruction = UByteArray(instructionLen)
    instruction[0] = opcode
    // offset starts at 1 because byte 0 is the opcode itself; operands begin at byte 1
    var offset = 1
    operands.forEachIndexed { index, op ->
        val width = def.operandWidths[index]
        when (width) {
            2 -> {
                // Big-endian: high byte first, then low byte
                // Equivalent to Java's: (op >> 8) & 0xFF and op & 0xFF
                instruction[offset] = (op shr 8 and 0xFF).toUByte()
                instruction[offset + 1] = (op and 0xFF).toUByte()
            }
        }
        offset += width
    }
    return instruction
}

@OptIn(ExperimentalUnsignedTypes::class)
fun Instructions.string(): String {
    val out = StringBuilder()
    var i = 0
    while (i < this.size) {
        val def = lookup(this[i]) ?: run {
            out.appendLine("ERROR: unknown opcode ${this[i]}")
            i++
            continue
        }

        val (operands, read) = readOperands(def, this.sliceArray(i + 1 until this.size))
        out.appendLine("%04d %s".format(i, fmtInstruction(def, operands)))
        i += 1 + read
    }
    return out.toString().trimEnd('\n')
}

fun fmtInstruction(def: Definition, operands: IntArray): String {
    val operandCount = def.operandWidths.size
    if (operands.size != operandCount) {
        return "ERROR: operand len ${operands.size} does not match defined $operandCount\n"
    }
    return when (operandCount) {
        1 -> "${def.name} ${operands[0]}"
        else -> "ERROR: unhandled operandCount for ${def.name}\n"
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
fun readOperands(def: Definition, ins: Instructions): Pair<IntArray, Int> {
    val operands = IntArray(def.operandWidths.size)
    var offset = 0
    def.operandWidths.forEachIndexed { index, width ->
        when(width) {
            2 -> operands[index] = readUint16(ins.sliceArray(offset until offset + 2))
        }
        offset += width
    }
    return Pair(operands, offset)
}

@OptIn(ExperimentalUnsignedTypes::class)
fun readUint16(ins: Instructions): Int{
    return (ins[0].toInt() shl 8) or ins[1].toInt()
}