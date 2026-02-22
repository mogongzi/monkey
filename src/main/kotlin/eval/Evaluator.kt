package me.ryan.interpreter.eval

import me.ryan.interpreter.ast.*
import me.ryan.interpreter.token.MINUS

val TRUE = MBoolean(true)
val FALSE = MBoolean(false)

class Evaluator {

    fun eval(node : Node): MObject? {
        return when (node) {
            is Program -> evalStatements(node.statements)
            is ExpressionStatement -> node.expression?.let { eval(it) }
            is IntegerLiteral -> MInteger(node.value)
            is BooleanLiteral -> hostBoolToMBoolean(node.value)
            is PrefixExpression -> {
                val right = eval(node.right!!)
                evalPrefixExpression(node.operator, right)
            }
            is InfixExpression -> {
                val left = eval(node.left!!)
                val right = eval(node.right!!)
                evalInfixExpression(node.operator, left!!, right!!)
            }
            is BlockStatement -> evalStatements(node.statements)
            is IfExpression -> evalIfExpression(node)
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
            MNULL -> TRUE
            else -> FALSE
        }
    }

    private fun evalMinusPrefixOperatorExpression(right: MObject) : MObject {
        if (right !is MInteger) return MNULL
        return MInteger(value = (right.value).unaryMinus())
    }

    private fun evalInfixExpression(operator: String, left: MObject, right: MObject) : MObject {
        return when {
            left is MInteger && right is MInteger -> evalIntegerInfixExpression(operator, left, right)
            operator == "==" -> hostBoolToMBoolean(left == right)
            operator == "!=" -> hostBoolToMBoolean(left != right)
            else -> MNULL
        }
    }

    private fun evalIntegerInfixExpression(operator: String, left: MInteger, right: MInteger) : MObject {
        val leftValue = left.value
        val rightValue = right.value
        return when (operator) {
            "+" -> MInteger(leftValue + rightValue)
            "-" -> MInteger(leftValue - rightValue)
            "*" -> MInteger(leftValue * rightValue)
            "/" -> MInteger(leftValue / rightValue)
            "<" -> hostBoolToMBoolean(leftValue < rightValue)
            ">" -> hostBoolToMBoolean(leftValue > rightValue)
            "==" -> hostBoolToMBoolean(leftValue == rightValue)
            "!=" -> hostBoolToMBoolean(leftValue != rightValue)
            else -> MNULL
        }
    }

    private fun evalIfExpression(ie: IfExpression): MObject? {
        val condition = eval(ie.condition!!)
        if (isTruthy(condition!!)) {
            return eval(ie.consequence!!)
        } else if (ie.alternative != null) {
            return eval(ie.alternative!!)
        } else {
            return MNULL
        }
    }

    private fun isTruthy(obj: MObject): Boolean {
        return when (obj) {
            MNULL -> false
            TRUE -> true
            FALSE -> false
            else -> true
        }
    }

    private fun hostBoolToMBoolean(input: Boolean) : MBoolean = if (input) TRUE else FALSE
}