package me.ryan.interpreter.modify

import me.ryan.interpreter.ast.*

typealias ModifierFunc = (Node) -> Node

fun modify(node: Node, modifier: ModifierFunc): Node {
    val newNode = when (node) {
        is Program -> {
            val newStatements = node.statements.map { modify(it, modifier) as Statement }
            Program(newStatements.toMutableList())
        }

        is ExpressionStatement -> {
            ExpressionStatement(node.token, modify(node.expression, modifier) as Expression)
        }

        is InfixExpression -> {
            InfixExpression(
                token = node.token,
                operator = node.operator,
                left = modify(node.left, modifier) as Expression,
                right = modify(node.right, modifier) as Expression
            )
        }

        is PrefixExpression -> {
            PrefixExpression(
                token = node.token,
                operator = node.operator,
                right = modify(node.right, modifier) as Expression
            )
        }

        is IndexExpression -> {
            IndexExpression(
                token = node.token,
                left = modify(node.left, modifier) as Expression,
                index = modify(node.index, modifier) as Expression
            )
        }

        is IfExpression -> {
            IfExpression(
                node.token,
                condition = modify(node.condition, modifier) as Expression,
                consequence = modify(node.consequence, modifier) as BlockStatement,
                alternative = node.alternative?.let { modify(it, modifier) as BlockStatement },
            )
        }

        is BlockStatement -> {
            val newStatements = node.statements.map { modify(it, modifier) as Statement }
            BlockStatement(node.token, newStatements.toMutableList())
        }

        is ReturnStatement -> {
            ReturnStatement(node.token, modify(node.returnValue, modifier) as Expression)
        }

        is LetStatement -> {
            LetStatement(node.token, node.name, modify(node.value, modifier) as Expression)
        }

        is FunctionLiteral -> {
            val newParams = node.parameters.map { modify(it, modifier) as Identifier }.toMutableList()
            val newBody = modify(node.body, modifier) as BlockStatement
            FunctionLiteral(node.token, newParams, newBody)
        }

        is ArrayLiteral -> {
            ArrayLiteral(node.token, node.elements.map { modify(it, modifier) as Expression })
        }

        is HashLiteral -> {
            val newPairs = node.pairs.map { (key, value) ->
                modify(key, modifier) as Expression to modify(value, modifier) as Expression
            }.toMap()
            HashLiteral(node.token, newPairs)
        }

        else -> node
    }

    return modifier(newNode)
}