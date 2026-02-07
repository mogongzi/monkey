package me.ryan.interpreter.parser

import me.ryan.interpreter.ast.Expression
import me.ryan.interpreter.ast.ExpressionStatement
import me.ryan.interpreter.ast.Identifier
import me.ryan.interpreter.ast.IntegerLiteral
import me.ryan.interpreter.ast.LetStatement
import me.ryan.interpreter.ast.Program
import me.ryan.interpreter.ast.ReturnStatement
import me.ryan.interpreter.ast.Statement
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.token.ASSIGN
import me.ryan.interpreter.token.EOF
import me.ryan.interpreter.token.IDENT
import me.ryan.interpreter.token.Token
import me.ryan.interpreter.token.ILLEGAL
import me.ryan.interpreter.token.INT
import me.ryan.interpreter.token.LET
import me.ryan.interpreter.token.RETURN
import me.ryan.interpreter.token.SEMICOLON
import me.ryan.interpreter.token.TokenType
import java.lang.Integer.parseInt

typealias PrefixParseFn = () -> Expression?
typealias InfixParseFn = (Expression) -> Expression?

enum class Precedence {
    LOWEST,
    EQUALS, // ==
    LESS_GREATER, // > or <
    SUM, // +
    PRODUCT, // *
    PREFIX, // -X or !X
    CALL // myFunction(X)
}

class Parser(private val lexer: Lexer) {

    // We keep two tokens around:
    // - curToken:  the token currently being parsed
    // - peekToken: one-token lookahead (helps with decisions like "is the next token an IDENT?")
    //
    // This mirrors the Monkey book's parser design and makes "expectPeek(...)" straightforward.
    private var curToken: Token = Token(ILLEGAL, "")
    private var peekToken: Token = Token(ILLEGAL, "")
    private val errors = mutableListOf<String>()

    private val prefixParseFns: MutableMap<TokenType, PrefixParseFn> = mutableMapOf()
    private val infixParseFns: MutableMap<TokenType, InfixParseFn> = mutableMapOf()

    init {
        // Read two tokens, so curToken and peekToken are both set.
        // This is the Kotlin equivalent of the book's Go constructor:
        //   p.nextToken()
        //   p.nextToken()
        nextToken()
        nextToken()

        registerPrefix(IDENT, ::parseIdentifier)
        registerPrefix(INT, ::parseIntegerLiteral)
    }

    fun registerPrefix(tokenType: TokenType, prefixParseFn: PrefixParseFn) {
        prefixParseFns[tokenType] = prefixParseFn
    }

    fun registerInfix(tokenType: TokenType, infixParseFn: InfixParseFn) {
       infixParseFns[tokenType] = infixParseFn
    }


    /**
     * Advance the token window by one:
     *   curToken <- peekToken
     *   peekToken <- lexer.nextToken()
     */
    fun nextToken() {
        curToken = peekToken
        peekToken = lexer.nextToken()
    }

    fun parseProgram(): Program {
        // Parse until EOF, producing a top-level Program node.
        // Each loop iteration tries to parse exactly one statement.
        val program = Program(mutableListOf())

        while (!curTokenIs(EOF)) {
            val stmt = parseStatement()
            if (stmt !=null) {
                program.statements.add(stmt)
            }
            nextToken()
        }

        return program
    }

    // Statement dispatch based on the current token type.
    private fun parseStatement(): Statement? = when (curToken.type) {
        LET -> parseLetStatement()
        RETURN -> parseReturnStatement()
        else -> parseExpressionStatement()
    }

    private fun parseReturnStatement(): Statement? {
        val token = curToken
        nextToken()

        // TODO: we're skipping the expressions until we encounter a semicolon
        while (!curTokenIs(SEMICOLON) && !curTokenIs(EOF)) {
            nextToken()
        }

        return ReturnStatement(token, null)
    }

    fun parseLetStatement(): LetStatement? {
        // Grammar (simplified): let <ident> = <expression> ;
        //
        // At this stage we don't implement full expression parsing yet, so after seeing '=' we
        // skip tokens until we hit ';' (the book does the same). Later this becomes:
        //   nextToken()
        //   val value = parseExpression(LOWEST)
        //   optionally consume SEMICOLON
        val token = curToken

        if (!expectPeek(IDENT)) return null
        val name = Identifier(curToken, curToken.literal)
        if (!expectPeek(ASSIGN)) return null
        nextToken()

        while (!curTokenIs(SEMICOLON) && curToken.type != EOF) {
            nextToken()
        }

        // RHS value is unknown for now (Expression parsing comes later), so we leave it null.
        return LetStatement(token, name)
    }

    // Convenience predicates to make parser code read like English:
    //   curTokenIs(LET), peekTokenIs(IDENT), expectPeek(ASSIGN), etc.
    fun curTokenIs(tokenType: TokenType): Boolean {
        return curToken.type == tokenType
    }

    fun peekTokenIs(tokenType: TokenType): Boolean {
        return peekToken.type == tokenType
    }

    /**
     * Assert that the next token is [tokenType].
     * If yes: advance (so the expected token becomes curToken) and return true.
     * If no:  return false (callers typically abort parsing that construct).
     */
    fun expectPeek(tokenType: TokenType): Boolean {
        if (peekTokenIs(tokenType)) {
            nextToken()
            return true
        } else {
            peekError(tokenType)
            return false
        }
    }

    fun peekError(tokenType: TokenType) {
        val msg = "expected next token to be $tokenType, got ${peekToken.type} instead."
        errors.add(msg)
    }

    fun errors(): List<String> {
        return errors.toList()
    }

    fun parseExpressionStatement(): ExpressionStatement {
        var stmt = ExpressionStatement(curToken)
        stmt.expression = parseExpression(Precedence.LOWEST)

        if (peekTokenIs(SEMICOLON)) {
            nextToken()
        }

        return stmt
    }

    fun parseIdentifier(): Expression? {
        return Identifier(curToken, curToken.literal)
    }

    fun parseIntegerLiteral(): Expression? {
        val token = curToken
        val value = curToken.literal.toLongOrNull()
        if (value == null) {
            errors.add("could not parse \"${token.literal}\" as integer")
            return null
        }
        return IntegerLiteral(token, value)
    }


    fun parseExpression(precedence: Precedence): Expression? {
        val prefix = prefixParseFns[curToken.type] ?: return null
        return prefix()
    }

    fun parseOperatorExpression() {}
}
