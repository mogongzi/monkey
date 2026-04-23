package me.ryan.interpreter.compiler

import me.ryan.interpreter.ast.*
import me.ryan.interpreter.code.Instructions
import me.ryan.interpreter.code.OpAdd
import me.ryan.interpreter.code.OpConstant
import me.ryan.interpreter.code.Opcode
import me.ryan.interpreter.code.make
import me.ryan.interpreter.eval.MInteger
import me.ryan.interpreter.eval.MObject

@OptIn(ExperimentalUnsignedTypes::class)
class Bytecode(val instructions: Instructions, val constants: MutableList<MObject>)

@OptIn(ExperimentalUnsignedTypes::class)
class Compiler() {
    private var instructions: Instructions = ubyteArrayOf()
    private val constants: MutableList<MObject> = mutableListOf()

    fun compile(node: Node) {
        when (node) {
            is Program -> node.statements.forEach { compile(it) }
            is ExpressionStatement -> compile(node.expression)
            is InfixExpression -> {
                compile(node.left)
                compile(node.right)
                when (node.operator) {
                    "+" -> emit(OpAdd)
                    else -> error("unknown operator ${node.operator}")
                }
            }
            is IntegerLiteral -> {
                // todo: evaluate first and add to constant pool
                val integer = MInteger(node.value)
                emit(OpConstant, addConstant(integer))
            }
        }
    }

    fun bytecode(): Bytecode {
        return Bytecode(instructions, constants)
    }

    fun addConstant(obj: MObject): Int {
        constants.add(obj)
        return constants.size - 1
    }

    fun emit(op: Opcode, vararg operands: Int): Int {
        val instructions = make(op, *operands)
        val pos = addInstruction(instructions)
        return pos
    }

    fun addInstruction(ints: Instructions): Int {
        val newPos = instructions.size
        instructions += ints
        return newPos
    }
}