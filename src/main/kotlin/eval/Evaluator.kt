package me.ryan.interpreter.eval

import me.ryan.interpreter.ast.*
import me.ryan.interpreter.token.MINUS

val TRUE = MBoolean(true)
val FALSE = MBoolean(false)
val NULL = MNULL()

class Evaluator {

    fun eval(node : Node): MObject? {
        return when (node) {
            is Program -> evalStatements(node.statements)
            is ExpressionStatement -> node.expression?.let { eval(it) }
            is IntegerLiteral -> MInteger(node.value)
            is BooleanLiteral -> hostBoolToMBoolean(node.value)
            is PrefixExpression -> {
                val right = eval(node.right!!)
                return evalPrefixExpression(node.operator, right)
            }
            // Fail fast: don't default to Monkey's NULL object (MNULL) here; it would hide missing evaluator cases.
            else -> error("unhandled node: ${node::class}")
        }
    }

    private fun evalStatements(statements : List<Statement>) : MObject? {
        var result: MObject? = null
        for (statement in statements) {
            result = eval(statement)
        }
        return result
    }

    private fun evalPrefixExpression(operator : String, right: MObject?) : MObject? {
        return when(operator) {
            "!" -> evalBangOperatorExpression(right!!)
            "-" -> evalMinusPrefixOperatorExpression(right!!)
            else -> null
        }
    }

    private fun evalBangOperatorExpression(right: MObject) : MObject {
        return when(right) {
            TRUE -> FALSE
            FALSE -> TRUE
            NULL -> TRUE
            else -> FALSE
        }
    }

    private fun evalMinusPrefixOperatorExpression(right: MObject) : MObject {
        if (right !is MInteger) return NULL
        return MInteger(-(right.value))
    }

    private fun hostBoolToMBoolean(input: Boolean) : MBoolean = if (input) TRUE else FALSE
}