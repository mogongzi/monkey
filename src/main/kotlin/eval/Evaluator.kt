package me.ryan.interpreter.eval

import me.ryan.interpreter.ast.*

val TRUE = MBoolean(true)
val FALSE = MBoolean(false)

class Evaluator {

    fun eval(node : Node): MObject? {
        return when (node) {
            is Program -> evalProgram(node.statements)
            is ExpressionStatement -> node.expression?.let { eval(it) }
            is IntegerLiteral -> MInteger(node.value)
            is BooleanLiteral -> hostBoolToMBoolean(node.value)
            is PrefixExpression -> {
                val right = eval(node.right!!)
                if (isMERROR(right!!)) return right
                evalPrefixExpression(node.operator, right)
            }
            is InfixExpression -> {
                val left = eval(node.left!!)
                if (isMERROR(left!!)) return left
                val right = eval(node.right!!)
                if (isMERROR(right!!)) return right
                evalInfixExpression(node.operator, left, right)
            }
            is BlockStatement -> evalBlockStatement(node.statements)
            is IfExpression -> evalIfExpression(node)
            is ReturnStatement -> {
                val value = eval(node.returnValue!!)
                if (isMERROR(value!!)) return value
                MReturnValue(value)
            }
            // Fail fast: don't default to Monkey's NULL object (MNULL) here; it would hide missing evaluator cases.
            else -> error("unhandled node: ${node::class}")
        }
    }

    private fun evalProgram(statements: List<Statement>): MObject? {
        var result: MObject? = null
        for (statement in statements) {
            result = eval(statement)
            when (result) {
                is MReturnValue -> return result.value
                is MERROR -> return result
            }
        }
        return result
    }

    private fun evalBlockStatement(statements: List<Statement>): MObject? {
        var result: MObject? = null
        for (statement in statements) {
            result = eval(statement)
            if (result is MReturnValue || result is MERROR) return result
        }
        return result
    }

    private fun evalPrefixExpression(operator : String, right: MObject?) : MObject? {
        return when(operator) {
            "!" -> evalBangOperatorExpression(right!!)
            "-" -> evalMinusPrefixOperatorExpression(right!!)
            else -> newMERROR("unknown operator: $operator ${right?.type()}")
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
        if (right !is MInteger) return newMERROR("unknown operator: -${right.type()}")
        return MInteger(value = (right.value).unaryMinus())
    }

    private fun evalInfixExpression(operator: String, left: MObject, right: MObject) : MObject {
        return when {
            left is MInteger && right is MInteger -> evalIntegerInfixExpression(operator, left, right)
            operator == "==" -> hostBoolToMBoolean(left == right)
            operator == "!=" -> hostBoolToMBoolean(left != right)
            left.type() != right.type() -> newMERROR("type mismatch: ${left.type()} $operator ${right.type()}")
            else -> newMERROR("unknown operator: ${left.type()} $operator ${right.type()}")
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
            else -> newMERROR("unknown operator: ${left.type()} $operator ${right.type()}")
        }
    }

    private fun evalIfExpression(ie: IfExpression): MObject? {
        val condition = eval(ie.condition!!)
        if (isMERROR(condition!!)) return condition
        return if (isTruthy(condition)) {
            eval(ie.consequence!!)
        } else if (ie.alternative != null) {
            eval(ie.alternative!!)
        } else {
            MNULL
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

    private fun isMERROR(obj: MObject): Boolean {
        return obj.type() == ERROR_OBJ
    }

    private fun newMERROR(format: String, vararg args: Any): MERROR {
        return MERROR(message = String.format(format, *args))
    }

    private fun hostBoolToMBoolean(input: Boolean) : MBoolean = if (input) TRUE else FALSE
}