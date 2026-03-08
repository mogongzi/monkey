package me.ryan.interpreter.eval

import me.ryan.interpreter.ast.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

val TRUE = MBoolean(true)
val FALSE = MBoolean(false)

// An anonymous object implementing (List<MObject>) -> MObject, which is Kotlin's
// functional interface with an invoke() method. This is the explicit, non-sugared
// form of a lambda: { args -> ... }. When stored in MBuiltinFunction and later
// called via fn.function(args), Kotlin translates that to fn.function.invoke(args).
val lenFunction = object : (List<MObject>) -> MObject {
    override fun invoke(args: List<MObject>): MObject {
        if (args.size != 1) {
            return MError("wrong number of arguments. got=${args.size}, want=1")
        }
        val arg = args[0]
        if (arg is MString) {
            return MInteger(arg.value.length.toLong())
        } else if (arg is MArray) {
            return MInteger(arg.elements.size.toLong())
        }
        return MError("argument to `len` not supported, got ${arg::class.simpleName}")
    }
}

@OptIn(ExperimentalTime::class)
var nowFunction = object : (List<MObject>) -> MObject {
    override fun invoke(args: List<MObject>): MObject {
        if (args.isNotEmpty()) {
            return MError("wrong number of arguments. got=${args.size}, want=0")
        }
        return MString("${Clock.System.now()}")
    }
}

val firstFunction = object : (List<MObject>) -> MObject {
    override fun invoke(args: List<MObject>): MObject {
        if (args.size != 1) {
            return MError("wrong number of arguments. got=${args.size},, want=1")
        }
        val arg = args[0]
        return if (arg is MArray) {
            if (arg.elements.isNotEmpty()) {
                arg.elements[0]
            } else {
                MNULL
            }
        } else {
            MError("argument to `first` must be MARRAY, got ${arg::class.simpleName}")
        }
    }
}

val lastFunction = object : (List<MObject>) -> MObject {
    override fun invoke(args: List<MObject>): MObject {
        if (args.size != 1) {
            return MError("wrong number of arguments. got=${args.size},, want=1")
        }
        val arg = args[0]
        return if (arg is MArray) {
            if (arg.elements.isNotEmpty()) {
                arg.elements[arg.elements.size - 1]
            } else {
                MNULL
            }
        } else {
            MError("argument to `last` must be MARRAY, got ${arg::class.simpleName}")
        }
    }
}

val restFunction = object : (List<MObject>) -> MObject {
    override fun invoke(args: List<MObject>): MObject {
        if (args.size != 1) {
            return MError("wrong number of arguments. got=${args.size},, want=1")
        }
        val arg = args[0]
        if (arg !is MArray) return MError("argument to `rest` must be MArray, got ${arg::class.simpleName}")
        if (arg.elements.isEmpty()) return MNULL
        val newElements = arg.elements.drop(1) // copies into a new List
        return MArray(newElements)
    }
}

val pushFunction = object : (List<MObject>) -> MObject {
    override fun invoke(args: List<MObject>): MObject {
        if (args.size != 2) {
            return MError("wrong number of arguments. got=${args.size},want=2")
        }
        val arg = args[0]
        if (arg !is MArray) return MError("argument to `push` must be MArray, got ${arg::class.simpleName}")
        return MArray(arg.elements + args[1])
    }
}

val putsFunction = object : (List<MObject>) -> MObject {
    override fun invoke(args: List<MObject>): MObject {
        args.forEach {
            println(it.inspect())
        }
        return MNULL
    }
}

// Registry of built-in functions. evalIdentifier checks this after the user environment,
// so user-defined bindings (e.g., let len = 42) shadow builtins.
val builtins = mapOf(
    "len" to MBuiltinFunction(function = lenFunction),
    "now" to MBuiltinFunction(function = nowFunction),
    "first" to MBuiltinFunction(function = firstFunction),
    "last" to MBuiltinFunction(function = lastFunction),
    "rest" to MBuiltinFunction(function = restFunction),
    "push" to MBuiltinFunction(function = pushFunction),
    "puts" to MBuiltinFunction(function = putsFunction),
)

class Evaluator {

    fun eval(node: Node, env: Environment): MObject {
        return when (node) {
            is Program -> evalProgram(node.statements, env)
            is ExpressionStatement -> eval(node.expression, env)
            is IntegerLiteral -> MInteger(node.value)
            is BooleanLiteral -> hostBoolToMBoolean(node.value)
            is PrefixExpression -> {
                val right = eval(node.right, env)
                if (right is MError) return right
                evalPrefixExpression(node.operator, right)
            }
            is InfixExpression -> {
                val left = eval(node.left, env)
                if (left is MError) return left
                val right = eval(node.right, env)
                if (right is MError) return right
                evalInfixExpression(node.operator, left, right)
            }
            is BlockStatement -> evalBlockStatement(node.statements, env)
            is IfExpression -> evalIfExpression(node, env)
            is ReturnStatement -> {
                val value = eval(node.returnValue, env)
                if (value is MError) return value
                MReturnValue(value)
            }
            is LetStatement -> {
                val value = eval(node.value, env)
                if (value is MError) return value
                env.set(node.name.value, value)
            }
            is Identifier -> {
                evalIdentifier(node, env)
            }
            is FunctionLiteral -> {
                MFunction(node.parameters, node.body, env)
            }
            is CallExpression -> {
                val function = eval(node.function, env)
                if (function is MError) return function
                val args = evalExpressions(node.arguments, env)
                if (args.size == 1 && args[0] is MError) args[0]
                applyFunction(function, args)
            }
            is StringLiteral -> MString(node.value)
            is ArrayLiteral -> {
                val elements = evalExpressions(node.elements, env)
                if (elements.size == 1 && elements[0] is MError) elements[0]
                MArray(elements)
            }
            is IndexExpression -> {
                val left = eval(node.left, env)
                if (left is MError) return left
                val index = eval(node.index, env)
                if (index is MError) return index
                evalIndexExpression(left, index)
            }
            is HashLiteral -> {
                evalHashLiteral(node, env)
            }
            // Fail fast: don't default to Monkey's NULL object (MNULL) here; it would hide missing evaluator cases.
            else -> error("unhandled node: ${node::class}")
        }
    }

    private fun evalProgram(statements: List<Statement>, env: Environment): MObject {
        var result: MObject = MNULL
        for (statement in statements) {
            result = eval(statement, env)
            when (result) {
                is MReturnValue -> return result.value
                is MError -> return result
            }
        }
        return result
    }

    private fun evalBlockStatement(statements: List<Statement>, env: Environment): MObject {
        var result: MObject = MNULL
        for (statement in statements) {
            result = eval(statement, env)
            if (result is MReturnValue || result is MError) return result
        }
        return result
    }

    private fun evalPrefixExpression(operator: String, right: MObject): MObject {
        return when (operator) {
            "!" -> evalBangOperatorExpression(right)
            "-" -> evalMinusPrefixOperatorExpression(right)
            else -> newMERROR("unknown operator: $operator ${right::class}")
        }
    }

    private fun evalBangOperatorExpression(right: MObject): MObject {
        return when (right) {
            TRUE -> FALSE
            FALSE -> TRUE
            MNULL -> TRUE
            else -> FALSE
        }
    }

    private fun evalMinusPrefixOperatorExpression(right: MObject): MObject {
        if (right !is MInteger) return newMERROR("unknown operator: -${right::class.simpleName}")
        return MInteger(value = (right.value).unaryMinus())
    }

    private fun evalInfixExpression(operator: String, left: MObject, right: MObject): MObject {
        return when {
            left is MInteger && right is MInteger -> evalIntegerInfixExpression(operator, left, right)
            left is MString && right is MString -> evalStringInfixExpression(operator, left, right)
            operator == "==" -> hostBoolToMBoolean(left == right)
            operator == "!=" -> hostBoolToMBoolean(left != right)
            left::class != right::class -> newMERROR("type mismatch: ${left::class.simpleName} $operator ${right::class.simpleName}")
            else -> newMERROR("unknown operator: ${left::class.simpleName} $operator ${right::class.simpleName}")
        }
    }

    private fun evalIntegerInfixExpression(operator: String, left: MInteger, right: MInteger): MObject {
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
            else -> newMERROR("unknown operator: ${left::class.simpleName} $operator ${right::class.simpleName}")
        }
    }

    private fun evalStringInfixExpression(operator: String, left: MString, right: MString): MObject {
        if (operator != "+") {
            return newMERROR("unknown operator: ${left::class.simpleName} $operator ${right::class.simpleName}")
        }

        return MString("${left.value}${right.value}")
    }

    private fun evalIfExpression(ie: IfExpression, env: Environment): MObject {
        val condition = eval(ie.condition, env)
        if (condition is MError) return condition
        return if (isTruthy(condition)) {
            eval(ie.consequence, env)
        } else if (ie.alternative != null) {
            eval(ie.alternative, env)
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

    private fun newMERROR(format: String, vararg args: Any): MError {
        return MError(message = String.format(format, *args))
    }

    private fun evalIdentifier(node: Identifier, env: Environment): MObject {
        env.get(node.value)?.let { return it }
        builtins[node.value]?.let { return it }
        return newMERROR("identifier not found: ${node.value}")

    }

    private fun evalExpressions(expressions: List<Expression>, env: Environment): List<MObject> {
        val results = mutableListOf<MObject>()
        for (exp in expressions) {
            val evaluated = eval(exp, env)
            if (evaluated is MError) return listOf(evaluated)
            results.add(evaluated)
        }

        return results
    }

    private fun applyFunction(fn: MObject, args: List<MObject>): MObject {
        when (fn) {
            is MFunction -> {
                val extendedEnv = extendFunctionEnv(fn, args)
                val evaluated = eval(fn.body, extendedEnv)
                return unWrapReturnValue(evaluated)
            }

            is MBuiltinFunction -> return fn.function(args)
            else -> return newMERROR("not a function: ${fn::class.simpleName}")
        }
    }

    private fun extendFunctionEnv(fn: MFunction, args: List<MObject>): Environment {
        val env = Environment(fn.env)
        fn.parameters.forEachIndexed { i, param -> env.set(param.value, args[i]) }
        return env
    }

    private fun unWrapReturnValue(obj: MObject): MObject {
        if (obj is MReturnValue) return obj.value
        return obj
    }

    private fun evalIndexExpression(left: MObject, index: MObject): MObject {
        return when {
            left is MArray && index is MInteger -> evalArrayIndexExpression(left, index)
            left is MHash -> evalHashIndexExpression(left, index)
            else -> MError("index operator not supported: ${left::class.simpleName}")
        }
    }

    private fun evalArrayIndexExpression(left: MArray, index: MInteger): MObject {
        val idx = index.value.toInt()
        val max = left.elements.size - 1
        if (idx !in 0..max) return MNULL
        return left.elements[idx]
    }

    private fun evalHashLiteral(node: HashLiteral, env: Environment): MObject {
        val pairs = mutableMapOf<HashKey, HashPair>();
        for ((keyNode, valueNode) in node.pairs) {
            val key = eval(keyNode, env)
            if (key is MError) return key

            val hashKey = key as? Hashable ?: return newMERROR("unusable as hash key: ${key::class.simpleName}")
            val value = eval(valueNode, env)
            if (value is MError) return value

            val hashed = hashKey.hashKey()
            pairs[hashed] = HashPair(key, value)
        }
        return MHash(pairs)
    }

    private fun evalHashIndexExpression(left: MHash, index: MObject): MObject {
        val key = index as? Hashable ?: return newMERROR("unusable as hash key: ${index::class.simpleName}")
        val pair = left.pairs[key.hashKey()] ?: return MNULL
        return pair.value
    }

    private fun hostBoolToMBoolean(input: Boolean): MBoolean = if (input) TRUE else FALSE
}