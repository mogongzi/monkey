package me.ryan.interpreter.ast

import me.ryan.interpreter.token.Token

interface Node {
    fun tokenLiteral(): String
}

sealed interface Statement : Node
sealed interface Expression : Node

class Program (val statements: MutableList<Statement>) : Node {

    override fun tokenLiteral(): String {
        return if (statements.isEmpty()) {
            ""
        } else {
            statements[0].tokenLiteral()
        }
    }
}

class Identifier(val token: Token, val value: String) : Expression {
    override fun tokenLiteral(): String {
        return token.literal
    }

}

class LetStatement(val token: Token, val name: Identifier, val value: Expression) : Expression {
    override fun tokenLiteral(): String {
        return token.literal
    }
}

