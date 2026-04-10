package me.ryan.interpreter.compiler

import me.ryan.interpreter.ast.Node
import me.ryan.interpreter.eval.MInteger
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.parser.Parser
import java.io.DataOutputStream
import java.io.File

private const val TAG_INTEGER: Byte = 0x01

@OptIn(ExperimentalUnsignedTypes::class)
fun main() {
    val cases = listOf(
        "src/test/fixtures/one_plus_two.mkc" to "1 + 2",
        "src/test/fixtures/just_42.mkc" to "42",
    )

    for ((path, source) in cases) {
        val program = parse(source)
        val compiler = Compiler()
        compiler.compile(program)
        val bc = compiler.byteCode()

        DataOutputStream(File(path).outputStream()).use { dos ->
            // Constants pool
            dos.writeShort(bc.constants.size)
            for (obj in bc.constants) {
                when (obj) {
                    is MInteger -> {
                        dos.writeByte(TAG_INTEGER.toInt())
                        dos.writeLong(obj.value)
                    }
                    else -> error("Unsupported constant type: ${obj::class.simpleName}")
                }
            }
            // Instructions
            val bytes = bc.instructions.toByteArray()
            dos.writeInt(bytes.size)
            dos.write(bytes)
        }
        println("  generated: $path")
    }
    println("Done. ${cases.size} fixture(s) generated.")
}

private fun parse(input: String): Node {
    val lexer = Lexer(input)
    val parser = Parser(lexer)
    return parser.parseProgram()
}
