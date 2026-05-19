package me.ryan.interpreter.compiler

import me.ryan.interpreter.ast.*
import me.ryan.interpreter.code.*
import me.ryan.interpreter.eval.MCompiledFunction
import me.ryan.interpreter.eval.MInteger
import me.ryan.interpreter.eval.MObject
import me.ryan.interpreter.eval.MString

@OptIn(ExperimentalUnsignedTypes::class)
class Bytecode(val instructions: Instructions, val constants: MutableList<MObject>)

data class EmittedInstruction(var opcode: Opcode, var position: Int)

@OptIn(ExperimentalUnsignedTypes::class)
class CompilationScope(
    var instructions: Instructions = ubyteArrayOf(),
    var lastInstruction: EmittedInstruction? = null,
    var previousInstruction: EmittedInstruction? = null
)

@OptIn(ExperimentalUnsignedTypes::class)
class Compiler() {
    internal val scopes = arrayListOf(CompilationScope())
    internal var scopeIndex: Int = 0

    private val constants: MutableList<MObject> = mutableListOf()
    private val symbolTable = SymbolTable()

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

            is IndexExpression -> {
                compile(node.left)
                compile(node.index)
                emit(OpIndex)
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

            is StringLiteral -> {
                val string = MString(node.value)
                emit(OpConstant, addConstant(string))
            }

            is ArrayLiteral -> {
                for (element in node.elements) {
                    compile(element)
                }

                emit(OpArray, node.elements.size)
            }

            is HashLiteral -> {
                for (k in node.pairs.keys.sortedBy { it.string() }) {
                    compile(k)
                    compile(node.pairs[k]!!)
                }
                emit(OpHash, node.pairs.size * 2)
            }

            is FunctionLiteral -> {
                enterScope()
                compile(node.body)
                if (lastInstructionIs(OpPop)) {
                    replaceLastPopWithReturn()
                }
                if (!lastInstructionIs(OpReturnValue)) {
                    emit(OpReturn)
                }
                val instructions = leaveScope()
                val compiledFn = MCompiledFunction(instructions)
                emit(OpConstant, addConstant(compiledFn))
            }

            is IfExpression -> {
                compile(node.condition)
                val jumpNotTruthPos = emit(OpJumpNotTruthy, 9999)
                compile(node.consequence)
                if (lastInstructionIs(OpPop)) {
                    removeLastPop()
                }

                val jumpPos = emit(OpJump, 9999)
                val afterConsequencePos = currentScope().instructions.size
                changeOperand(jumpNotTruthPos, afterConsequencePos)
                if (node.alternative == null) {
                    emit(OpNull)
                } else {
                    compile(node.alternative)
                    if (lastInstructionIs(OpPop)) {
                        removeLastPop()
                    }
                }
                val afterAlternativePos = currentScope().instructions.size
                changeOperand(jumpPos, afterAlternativePos)
            }

            is CallExpression -> {
                compile(node.function)
                emit(OpCall)
            }

            is BlockStatement -> {
                for (statement in node.statements) {
                    compile(statement)
                }
            }

            is LetStatement -> {
                compile(node.value)
                val symbol = symbolTable.define(node.name.value)
                emit(OpSetGlobal, symbol.index)
            }

            is ReturnStatement -> {
                compile(node.returnValue)
                emit(OpReturnValue)
            }

            is Identifier -> {
                val symbol = symbolTable.resolve(node.value)
                    ?: error("undefined variable ${node.value}")
                emit(OpGetGlobal, symbol.index)
            }
        }
    }

    fun bytecode(): Bytecode {
        return Bytecode(currentScope().instructions, constants)
    }

    fun addConstant(obj: MObject): Int {
        constants.add(obj)
        return constants.size - 1
    }

    internal fun emit(op: Opcode, vararg operands: Int): Int {
        val instructions = make(op, *operands)
        val pos = addInstruction(instructions)
        setLastInstruction(op, pos)
        return pos
    }

    private fun currentScope(): CompilationScope = scopes[scopeIndex]


    private fun addInstruction(ints: Instructions): Int {
        val newPos = currentScope().instructions.size
        currentScope().instructions += ints
        return newPos
    }

    private fun setLastInstruction(op: Opcode, pos: Int) {
        val previous = currentScope().lastInstruction
        val last = EmittedInstruction(op, pos)
        currentScope().previousInstruction = previous
        currentScope().lastInstruction = last
    }

    private fun lastInstructionIs(opCode: Opcode): Boolean {
        if (currentScope().instructions.isEmpty()) return false
        return currentScope().lastInstruction?.opcode == opCode
    }

    private fun removeLastPop() {
        val last = currentScope().lastInstruction ?: return
        currentScope().instructions = currentScope().instructions.copyOf(last.position)
        currentScope().lastInstruction = currentScope().previousInstruction
    }

    private fun replaceLastPopWithReturn() {
        val lastPos = currentScope().lastInstruction?.position
        replaceInstruction(lastPos!!, make(OpReturnValue))
        currentScope().lastInstruction?.opcode = OpReturnValue
    }

    private fun replaceInstruction(pos: Int, newInstruction: Instructions) {
        for (i in newInstruction.indices) {
            currentScope().instructions[pos + i] = newInstruction[i]
        }
    }

    private fun changeOperand(opPos: Int, operand: Int) {
        val op = currentScope().instructions[opPos]
        val newInstruction = make(op, operand)
        replaceInstruction(opPos, newInstruction)
    }

    internal fun enterScope() {
        val scope = CompilationScope()
        scopes += scope
        scopeIndex++
    }

    internal fun leaveScope(): Instructions {
        val instructions = currentScope().instructions
        scopes.removeLast()
        scopeIndex--
        return instructions
    }
}