package me.ryan.interpreter.code

@OptIn(ExperimentalUnsignedTypes::class)
typealias Instructions = UByteArray
typealias Opcode = UByte

const val OpConstant: Opcode = 0x00u
const val OpAdd: Opcode = 0x01u
const val OpPop: Opcode = 0x02u
const val OpSub: Opcode = 0x03u
const val OpMul: Opcode = 0x04u
const val OpDiv: Opcode = 0x05u
const val OpTrue: Opcode = 0x06u
const val OpFalse: Opcode = 0x07u
const val OpEqual: Opcode = 0x08u
const val OpNotEqual: Opcode = 0x09u
const val OpGreaterThan: Opcode = 0x0Au
const val OpMinus: Opcode = 0xBu
const val OpBang: Opcode = 0xCu
const val OpJumpNotTruthy: Opcode = 0xDu
const val OpJump: Opcode = 0xEu
const val OpNull: Opcode = 0xFu
const val OpGetGlobal: Opcode = 0x10u
const val OpSetGlobal: Opcode = 0x11u
const val OpArray: Opcode = 0x12u
const val OpHash: Opcode = 0x13u
const val OpIndex: Opcode = 0x14u
const val OpCall: Opcode = 0x15u
const val OpReturnValue: Opcode = 0x16u
const val OpReturn: Opcode = 0x17u
const val OpGetLocal: Opcode = 0x18u
const val OpSetLocal: Opcode = 0x19u
const val OpGetBuiltin: Opcode = 0x1Au
const val OpClosure: Opcode = 0x1Bu
const val OpGetFree: Opcode = 0x1Cu


data class Definition(val name: String, val operandWidths: List<Int>)

// mapOf<Opcode, Definition>
val definitions = mapOf(
    OpConstant to Definition("OpConstant", listOf(2)),
    OpAdd to Definition("OpAdd", emptyList()),
    OpPop to Definition("OpPop", emptyList()), // only job is to tell the VM to pop the topmost element off the stack
    OpSub to Definition("OpSub", emptyList()),
    OpMul to Definition("OpMul", emptyList()),
    OpDiv to Definition("OpDiv", emptyList()),
    OpTrue to Definition("OpTrue", emptyList()),
    OpFalse to Definition("OpFalse", emptyList()),
    OpEqual to Definition("OpEqual", emptyList()),
    OpNotEqual to Definition("OpNotEqual", emptyList()),
    OpGreaterThan to Definition("OpGreaterThan", emptyList()),
    OpMinus to Definition("OpMinus", emptyList()),
    OpBang to Definition("OpBang", emptyList()),
    OpJumpNotTruthy to Definition("OpJumpNotTruthy", listOf(2)),
    OpJump to Definition("OpJump", listOf(2)),
    OpNull to Definition("OpNull", emptyList()),
    OpGetGlobal to Definition("OpGetGlobal", listOf(2)),
    OpSetGlobal to Definition("OpSetGlobal", listOf(2)),
    OpArray to Definition("OpArray", listOf(2)),
    OpHash to Definition("OpHash", listOf(2)),
    OpIndex to Definition("OpIndex", emptyList()),
    OpCall to Definition("OpCall", listOf(1)),
    OpReturnValue to Definition("OpReturnValue", emptyList()),
    OpReturn to Definition("OpReturn", emptyList()),
    OpGetLocal to Definition("OpGetLocal", listOf(1)),
    OpSetLocal to Definition("OpSetLocal", listOf(1)),
    OpGetBuiltin to Definition("OpGetBuiltin", listOf(1)),
    OpClosure to Definition("OpClosure", listOf(2, 1)),
    OpGetFree to Definition("OpGetFree", listOf(1)),
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
            1 -> {
                instruction[offset] = op.toUByte()
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
        0 -> def.name
        1 -> "${def.name} ${operands[0]}"
        2 -> "${def.name} ${operands[0]} ${operands[1]}"
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
            1 -> operands[index] = ins[offset].toInt()
        }
        offset += width
    }
    return Pair(operands, offset)
}

@OptIn(ExperimentalUnsignedTypes::class)
fun readUint16(ins: Instructions): Int{
    return (ins[0].toInt() shl 8) or ins[1].toInt()
}