package me.ryan.interpreter.compiler

import me.ryan.interpreter.eval.MInteger
import java.io.DataOutputStream
import java.io.OutputStream

private const val TAG_INTEGER: Byte = 0x01

@OptIn(ExperimentalUnsignedTypes::class)
object BytecodeWriter {
    fun write(bytecode: Bytecode, out: OutputStream) {
        val dos = DataOutputStream(out)

        dos.writeShort(bytecode.constants.size)
        for (obj in bytecode.constants) {
            when (obj) {
                is MInteger -> {
                    dos.writeByte(TAG_INTEGER.toInt())
                    dos.writeLong(obj.value)
                }

                else -> error("Unsupported constant type: ${obj::class.simpleName}")
            }
        }

        val bytes = bytecode.instructions.toByteArray()
        dos.writeInt(bytes.size)
        dos.write(bytes)
        dos.flush()
    }
}
