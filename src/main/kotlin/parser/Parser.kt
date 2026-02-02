package me.ryan.interpreter.parser

import me.ryan.interpreter.ast.Identifier
import me.ryan.interpreter.ast.LetStatement
import me.ryan.interpreter.ast.Program
import me.ryan.interpreter.ast.Statement
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.token.ASSIGN
import me.ryan.interpreter.token.EOF
import me.ryan.interpreter.token.IDENT
import me.ryan.interpreter.token.Token
import me.ryan.interpreter.token.ILLEGAL
import me.ryan.interpreter.token.LET
import me.ryan.interpreter.token.SEMICOLON
import me.ryan.interpreter.token.TokenType

class Parser(private val lexer: Lexer) {

    // We keep two tokens around:
    // - curToken:  the token currently being parsed
    // - peekToken: one-token lookahead (helps with decisions like "is the next token an IDENT?")
    //
    // This mirrors the Monkey book's parser design and makes "expectPeek(...)" straightforward.
    private var curToken: Token = Token(ILLEGAL, "")
    private var peekToken: Token = Token(ILLEGAL, "")

    init {
        // Read two tokens, so curToken and peekToken are both set.
        // This is the Kotlin equivalent of the book's Go constructor:
        //   p.nextToken()
        //   p.nextToken()
        nextToken()
        nextToken()
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

        while (curToken.type != EOF) {
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
        else -> null
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
            return false
        }
    }


    fun parseIdentifier() {}

    fun parseExpression() {}

    fun parseOperatorExpression() {}
}
