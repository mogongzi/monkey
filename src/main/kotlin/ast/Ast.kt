package me.ryan.interpreter.ast

import me.ryan.interpreter.token.Token

/**
 * AST notes (Monkey book, Kotlin edition):
 *
 * - "Statement" vs "Expression":
 *   - A [Statement] is something that appears in a program's statement list (e.g. `let x = 5;`).
 *   - An [Expression] is something that produces a value when evaluated (e.g. `x + 1`, `5`, `add(1, 2)`).
 *
 * - `tokenLiteral()` returns the *source literal* of the node's token (e.g. `"let"`, `"x"`, `"5"`),
 *   not the token *type* (e.g. `"LET"`, `"IDENT"`, `"INT"`).
 */
interface Node {
    fun tokenLiteral(): String
    fun string(): String
}

sealed interface Statement : Node
sealed interface Expression : Node

/**
 * The root node of the AST.
 * The parser appends each parsed statement into [statements] until it reaches EOF.
 */
class Program (val statements: MutableList<Statement>) : Node {

    override fun tokenLiteral(): String {
        return if (statements.isEmpty()) {
            ""
        } else {
            statements[0].tokenLiteral()
        }
    }

    override fun string(): String = buildString {
        statements.forEach { append(it.string()) }
    }
}

/**
 * Identifiers are modeled as expressions so the same node type can be reused:
 *
 * - Binding position: `let x = 5;` (here `x` is a name, not evaluated)
 * - Value position:   `let y = x;` or `x + 1` (here `x` evaluates to a value)
 *
 * This keeps the number of node types small (matching the book's approach).
 */
class Identifier(val token: Token, val value: String) : Expression {
    override fun tokenLiteral(): String = token.literal

    override fun string(): String = value
    override fun toString(): String = string()
}

class IntegerLiteral(val token: Token, val value: Long) : Expression {
    override fun tokenLiteral(): String = token.literal

    override fun string(): String = value.toString()
    override fun toString(): String = string()
}

/**
 * A `let` binding is a statement in Monkey.
 *
 * `value` is nullable during early parser chapters: before expression parsing exists we skip tokens
 * until the semicolon (like the book), so there may be no parsed RHS yet. Later, when
 * `parseExpression(...)` is implemented, `value` should be set to a real expression node.
 */
class LetStatement(val token: Token, val name: Identifier, val value: Expression? = null) : Statement {
    override fun tokenLiteral(): String = token.literal

    override fun string(): String = buildString {
        append(tokenLiteral())
        append(" ")
        append(name.string())
        append(" = ")
        value?.let { append(it.string()) }
        append(";")
    }
}

class ReturnStatement(val token: Token, val returnValue: Expression? = null) : Statement {
    override fun tokenLiteral(): String = token.literal

    override fun string(): String = buildString {
       append(tokenLiteral())
       append(" ")
       returnValue?.let { append(it.string()) }
       append(";")
    }
}

class ExpressionStatement(val token: Token, var expression: Expression? = null) : Statement {
    override fun tokenLiteral(): String = token.literal
    override fun string(): String = expression?.string().orEmpty()
}

class PrefixExpression(val token: Token, val operator: String, val right: Expression) : Expression {
    override fun tokenLiteral(): String = token.literal

    override fun string(): String = "(${operator}${right?.string() ?: ""})"
}