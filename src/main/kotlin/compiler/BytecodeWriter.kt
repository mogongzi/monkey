package me.ryan.interpreter.compiler

import me.ryan.interpreter.eval.MInteger
import me.ryan.interpreter.eval.MString
import java.io.DataOutputStream
import java.io.OutputStream
import kotlin.text.Charsets.UTF_8

private const val TAG_INTEGER: Byte = 0x01
private const val TAG_STRING: Byte = 0x02

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

                is MString -> {
                    val bytes = obj.value.toByteArray(UTF_8)
                    dos.writeByte(TAG_STRING.toInt())
                    dos.writeInt(bytes.size)
                    dos.write(bytes)
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
