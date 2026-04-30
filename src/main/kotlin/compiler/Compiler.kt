package me.ryan.interpreter.compiler

import me.ryan.interpreter.ast.*
import me.ryan.interpreter.code.*
import me.ryan.interpreter.eval.MInteger
import me.ryan.interpreter.eval.MObject

@OptIn(ExperimentalUnsignedTypes::class)
class Bytecode(val instructions: Instructions, val constants: MutableList<MObject>)

data class EmittedInstruction(val opcode: Opcode, val position: Int)

@OptIn(ExperimentalUnsignedTypes::class)
class Compiler() {
    private var instructions: Instructions = ubyteArrayOf()
    private val constants: MutableList<MObject> = mutableListOf()
    private var lastInstruction: EmittedInstruction? = null
    private var previousInstruction: EmittedInstruction? = null

    fun compile(node: Node) {
        when (node) {
            is Program -> node.statements.forEach { compile(it) }
            is ExpressionStatement -> {
                compile(node.expression)
                emit(OpPop)
            }

            is PrefixExpression -> {
                compile(node.right)
                when (node.operator) {
                    "!" -> emit(OpBang)
                    "-" -> emit(OpMinus)
                    else -> error("unknown operator ${node.operator}")
                }
            }

            is InfixExpression -> {
                if (node.operator == "<") {
                    compile(node.right)
                    compile(node.left)
                    emit(OpGreaterThan)
                    return
                }
                compile(node.left)
                compile(node.right)
                when (node.operator) {
                    "+" -> emit(OpAdd)
                    "-" -> emit(OpSub)
                    "*" -> emit(OpMul)
                    "/" -> emit(OpDiv)
                    ">" -> emit(OpGreaterThan)
                    "==" -> emit(OpEqual)
                    "!=" -> emit(OpNotEqual)
                    else -> error("unknown operator ${node.operator}")
                }
            }

            is IntegerLiteral -> {
                val integer = MInteger(node.value)
                emit(OpConstant, addConstant(integer))
            }

            is BooleanLiteral -> {
                if (node.value) {
                    emit(OpTrue)
                } else {
                    emit(OpFalse)
                }
            }

            is IfExpression -> {
                compile(node.condition)
                val jumpNotTruthPos = emit(OpJumpNotTruthy, 9999)
                compile(node.consequence)
                if (lastInstructionIsPop()) {
                    removeLastPop()
                }

                val jumpPos = emit(OpJump, 9999)
                val afterConsequencePos = instructions.size
                changeOperand(jumpNotTruthPos, afterConsequencePos)
                if (node.alternative == null) {
                    emit(OpNull)
                } else {
                    compile(node.alternative)
                    if (lastInstructionIsPop()) {
                        removeLastPop()
                    }
                }
                val afterAlternativePos = instructions.size
                changeOperand(jumpPos, afterAlternativePos)
            }

            is BlockStatement -> {
                for (statement in node.statements) {
                    compile(statement)
                }
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

    private fun emit(op: Opcode, vararg operands: Int): Int {
        val instructions = make(op, *operands)
        val pos = addInstruction(instructions)
        setLastInstruction(op, pos)
        return pos
    }

    private fun addInstruction(ints: Instructions): Int {
        val newPos = instructions.size
        instructions += ints
        return newPos
    }

    private fun setLastInstruction(op: Opcode, pos: Int) {
        val previous = lastInstruction
        val last = EmittedInstruction(op, pos)
        previousInstruction = previous
        lastInstruction = last
    }

    private fun lastInstructionIsPop(): Boolean {
        return lastInstruction?.opcode == OpPop
    }

    private fun removeLastPop() {
        val last = lastInstruction ?: return
        instructions = instructions.copyOf(last.position)
        lastInstruction = previousInstruction
    }

    private fun replaceInstruction(pos: Int, newInstruction: Instructions) {
        for (i in newInstruction.indices) {
            instructions[pos + i] = newInstruction[i]
        }
    }

    private fun changeOperand(opPos: Int, operand: Int) {
        val op = instructions[opPos]
        val newInstruction = make(op, operand)
        replaceInstruction(opPos, newInstruction)
    }
}