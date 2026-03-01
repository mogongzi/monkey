package me.ryan.interpreter.eval

import me.ryan.interpreter.ast.*

val TRUE = MBoolean(true)
val FALSE = MBoolean(false)

class Evaluator {

    fun eval(node : Node, env: Environment): MObject? {
        return when (node) {
            is Program -> evalProgram(node.statements, env)
            is ExpressionStatement -> node.expression?.let { eval(it, env) }
            is IntegerLiteral -> MInteger(node.value)
            is BooleanLiteral -> hostBoolToMBoolean(node.value)
            is PrefixExpression -> {
                val right = eval(node.right!!, env)
                if (isMERROR(right!!)) return right
                evalPrefixExpression(node.operator, right)
            }
            is InfixExpression -> {
                val left = eval(node.left!!, env)
                if (isMERROR(left!!)) return left
                val right = eval(node.right!!, env)
                if (isMERROR(right!!)) return right
                evalInfixExpression(node.operator, left, right)
            }
            is BlockStatement -> evalBlockStatement(node.statements, env)
            is IfExpression -> evalIfExpression(node, env)
            is ReturnStatement -> {
                val value = eval(node.returnValue!!, env)
                if (isMERROR(value!!)) return value
                MReturnValue(value)
            }
            is LetStatement -> {
                val value = eval(node.value!!, env)
                if (isMERROR(value!!)) return value
                env.set(node.name.value, value)
            }
            is Identifier -> {
                evalIdentifier(node, env)
            }
            is FunctionLiteral -> {
                MFunction(node.parameters!!, node.body!!, env)
            }
            is CallExpression -> {
                val function = eval(node.function!!, env)
                if (isMERROR(function!!)) function
                val args = evalExpression(node.arguments!!, env)
                if (args.size == 1 && args[0] is MERROR) args[0]
                return applyFunction(function, args)
            }
            // Fail fast: don't default to Monkey's NULL object (MNULL) here; it would hide missing evaluator cases.
            else -> error("unhandled node: ${node::class}")
        }
    }

    private fun evalProgram(statements: List<Statement>, env: Environment): MObject? {
        var result: MObject? = null
        for (statement in statements) {
            result = eval(statement, env)
            when (result) {
                is MReturnValue -> return result.value
                is MERROR -> return result
            }
        }
        return result
    }

    private fun evalBlockStatement(statements: List<Statement>, env: Environment): MObject? {
        var result: MObject? = null
        for (statement in statements) {
            result = eval(statement, env)
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

    private fun evalIfExpression(ie: IfExpression, env: Environment): MObject? {
        val condition = eval(ie.condition!!, env)
        if (isMERROR(condition!!)) return condition
        return if (isTruthy(condition)) {
            eval(ie.consequence!!, env)
        } else if (ie.alternative != null) {
            eval(ie.alternative!!, env)
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

    private fun evalIdentifier(node: Identifier, env: Environment): MObject {
        val value = env.get(node.value) ?: return newMERROR("identifier not found: ${node.value}")
        return value
    }

    private fun evalExpression(exps: List<Expression>, env: Environment): List<MObject> {
        val results = mutableListOf<MObject>()
        for (exp in exps) {
            val evaluated = eval(exp, env)
            if (isMERROR(evaluated!!)) return listOf(evaluated)
            results.add(evaluated)
        }

        return results
    }

    private fun applyFunction(fn : MObject, args : List<MObject>): MObject {
        if (fn !is MFunction) return newMERROR("not a function: ${fn.type()}")
        val extendedEnv = extendFunctionEnv(fn, args)
        val evaluated = eval(fn.body, extendedEnv)
        return unWrapReturnValue(evaluated)
    }

    private fun extendFunctionEnv(fn: MFunction, args: List<MObject>): Environment {
        val env = Environment(fn.env)
        fn.parameters.forEachIndexed { i, param -> env.set(param.value, args[i])  }
        return env
    }

    private fun unWrapReturnValue(obj: MObject?): MObject {
        if (obj is MReturnValue) return obj.value
        return obj ?: MNULL
    }

    private fun hostBoolToMBoolean(input: Boolean) : MBoolean = if (input) TRUE else FALSE
}