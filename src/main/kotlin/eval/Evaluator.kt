package me.ryan.interpreter.eval

import me.ryan.interpreter.ast.*

class Evaluator {

    fun eval(node : Node): MObject? {
        return when (node) {
            is Program -> evalStatements(node.statements)
            is ExpressionStatement -> node.expression?.let { eval(it) }
            is IntegerLiteral -> MInteger(node.value)
            // Fail fast: don't default to Monkey's NULL object (MNULL) here; it would hide missing evaluator cases.
            else -> error("unhandled node: ${node::class}")
        }
    }

    fun evalStatements(statements : List<Statement>) : MObject? {
        var result: MObject? = null
        for (statement in statements) {
            result = eval(statement)
        }
        return result
    }
}