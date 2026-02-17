package me.ryan.interpreter.parser

import me.ryan.interpreter.ast.*
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.token.*

typealias PrefixParseFn = () -> Expression?
typealias InfixParseFn = (Expression) -> Expression?

enum class Precedence {
    LOWEST,
    EQUALS, // ==
    LESS_GREATER, // > or <
    SUM, // +
    PRODUCT, // *
    PREFIX, // -X or !X
    CALL; // myFunction(X)

    operator fun minus(n: Int): Precedence {
        return entries[(ordinal - n).coerceAtLeast(0)]
    }
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

    private val precedences: Map<TokenType, Precedence> = mapOf(
        EQ to Precedence.EQUALS,
        NOT_EQ to Precedence.EQUALS,
        LT to Precedence.LESS_GREATER,
        GT to Precedence.LESS_GREATER,
        PLUS to Precedence.SUM,
        MINUS to Precedence.SUM,
        SLASH to Precedence.PRODUCT,
        ASTERISK to Precedence.PRODUCT,
        LPAREN to Precedence.CALL,
    )

    init {
        // Read two tokens, so curToken and peekToken are both set.
        // This is the Kotlin equivalent of the book's Go constructor:
        //   p.nextToken()
        //   p.nextToken()
        nextToken()
        nextToken()

        registerPrefix(IDENT, ::parseIdentifier)
        registerPrefix(INT, ::parseIntegerLiteral)
        registerPrefix(BANG, ::parsePrefixExpression)
        registerPrefix(MINUS, ::parsePrefixExpression)
        registerPrefix(TRUE, ::parseBoolean)
        registerPrefix(FALSE, ::parseBoolean)
        registerPrefix(LPAREN, ::parseGroupedExpression)
        registerPrefix(IF, ::parseIfExpression)
        registerPrefix(FUNCTION, ::parseFunctionLiteral)

        registerInfix(PLUS, ::parseInfixExpression)
        registerInfix(MINUS, ::parseInfixExpression)
        registerInfix(SLASH, ::parseInfixExpression)
        registerInfix(ASTERISK, ::parseInfixExpression)
        registerInfix(EQ, ::parseInfixExpression)
        registerInfix(NOT_EQ, ::parseInfixExpression)
        registerInfix(LT, ::parseInfixExpression)
        registerInfix(GT, ::parseInfixExpression)
        registerInfix(LPAREN, ::parseCallExpression)
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
        val stmt = ReturnStatement(curToken, null)
        nextToken()
        stmt.returnValue = parseExpression(Precedence.LOWEST)
        if(peekTokenIs(SEMICOLON)) {
            nextToken()
        }
        return stmt
    }

    fun parseLetStatement(): LetStatement? {
        // Grammar (simplified): let <ident> = <expression> ;
        val token = curToken
        if (!expectPeek(IDENT)) return null
        val name = Identifier(curToken, curToken.literal)
        val stmt = LetStatement(token, name, null)
        if (!expectPeek(ASSIGN)) return null
        nextToken()
        stmt.value = parseExpression(Precedence.LOWEST)
        if (peekTokenIs(SEMICOLON)) {
            nextToken()
        }
        return stmt
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

    fun parseBoolean(): Expression? {
        return BooleanLiteral(curToken, curTokenIs(TRUE))
    }


//     Parse the first token as a "prefix" — could be a literal (1), identifier (x),
//     or a prefix operator (-x, !x) which itself recurses into parseExpression.
//
//     Then, repeatedly try to combine leftExp with the next infix operator.
//
//	 precedence = "how tightly my caller holds onto me"
//	   - LOWEST: anyone can take me (top-level call from parseExpressionStatement)
//	   - SUM: only operators with higher precedence than SUM can take me
//	   - PREFIX: almost nobody can take me (prefix binds tighter than infix ops;
//	             CALL would bind even tighter once implemented)
//
//	 The loop STOPS when precedence >= peekPrecedence():
//	   → leftExp is returned to the caller, becoming THEIR operand.
//	 The loop CONTINUES when precedence < peekPrecedence():
//	   → the next operator grabs leftExp as its left child.
//
//     Example: "1 + 2 + 3" (left associativity via loop iteration)
//       parseExpression(LOWEST):
//         leftExp = 1
//         iteration 1: peek=+, LOWEST < SUM → true
//           infix(1) → parseInfixExpression calls parseExpression(SUM)
//             leftExp = 2, peek=+, SUM < SUM → false → returns 2
//           → returns InfixExpression(1 + 2)
//           leftExp = (1 + 2)
//         iteration 2: peek=+, LOWEST < SUM → true
//           infix((1+2)) → parseInfixExpression calls parseExpression(SUM)
//             leftExp = 3, peek=EOF → stops → returns 3
//           → returns InfixExpression((1+2) + 3)
//           leftExp = ((1+2) + 3)
//         loop ends → returns ((1+2) + 3)
//
//     Example: "1 + 2 * 3" (higher precedence recurses deeper)
//       parseExpression(LOWEST):
//         leftExp = 1
//         iteration 1: peek=+, LOWEST < SUM → true
//           infix(1) → parseInfixExpression calls parseExpression(SUM)
//             leftExp = 2, peek=*, SUM < PRODUCT → true
//               infix(2) → parseInfixExpression calls parseExpression(PRODUCT)
//                 leftExp = 3, peek=EOF → stops → returns 3
//               → returns InfixExpression(2 * 3)
//               leftExp = (2 * 3), loop ends → returns (2 * 3)
//           → returns InfixExpression(1 + (2 * 3))
//           leftExp = (1 + (2 * 3))
//         loop ends → returns (1 + (2 * 3))
//
//     Example: "-1 + 2" (PREFIX precedence prevents stealing)
//       parsePrefixExpression calls parseExpression(PREFIX):
//         leftExp = 1, peek=+, PREFIX < SUM → false → returns 1 immediately
//       So "-" grabs only 1, result is ((-1) + 2), not (-(1 + 2))
    fun parseExpression(precedence: Precedence): Expression? {
        val prefix = prefixParseFns[curToken.type]
        if (prefix == null) {
            noPrefixParseFnError(curToken.type)
            return null
        }
        var leftExp: Expression = prefix() ?: return null

        while (!peekTokenIs(SEMICOLON) && precedence < peekPrecedence()) {
            val infix = infixParseFns[peekToken.type] ?: return leftExp
            nextToken()
            leftExp = infix(leftExp) ?: return null
        }

        return leftExp
    }

    fun parsePrefixExpression(): Expression? {
        val exp = PrefixExpression(curToken, curToken.literal)
        nextToken()
        exp.right = parseExpression(Precedence.PREFIX)
        return exp
    }

    fun peekPrecedence(): Precedence {
        return precedences[peekToken.type] ?: Precedence.LOWEST
    }

    fun currPrecedence(): Precedence {
        return precedences[curToken.type] ?: Precedence.LOWEST
    }

    fun parseInfixExpression(left: Expression): Expression? {
        val exp = InfixExpression(curToken, curToken.literal, left)
        val precedence = currPrecedence()
        nextToken()
        exp.right = parseExpression(precedence)
        return exp
    }

    fun parseGroupedExpression() : Expression? {
        nextToken()

        val exp = parseExpression(Precedence.LOWEST)
        if (!expectPeek(RPAREN)) {
            return null
        }
        return exp
    }

    fun parseBlockStatment(): BlockStatement? {
        val block = BlockStatement(curToken, mutableListOf())
        nextToken()
        while (!curTokenIs(RBRACE) && !curTokenIs(EOF)) {
            val stmt = parseStatement()
            stmt?.let { block.statements.add(it) }
            nextToken()
        }

        return block
    }

    fun parseIfExpression() : Expression? {
        val exp = IfExpression(curToken)
        if (!expectPeek(LPAREN)) return null
        nextToken()
        exp.condition = parseExpression(Precedence.LOWEST)
        if (!expectPeek(RPAREN)) return null
        if (!expectPeek(LBRACE)) return null
        exp.consequence = parseBlockStatment()

        if(peekTokenIs(ELSE)) {
            nextToken()
            if(!expectPeek(LBRACE)) return null
            exp.alternative = parseBlockStatment()
        }

        return exp
    }

    fun parseFunctionLiteral(): Expression? {
        val lit = FunctionLiteral(curToken)

        if (!expectPeek(LPAREN)) return null
        lit.parameters = parseFunctionParameters()
        if (!expectPeek(LBRACE)) return null
        lit.body = parseBlockStatment()
        return lit
    }

    fun parseFunctionParameters(): List<Identifier>? {
        val identifiers = mutableListOf<Identifier>()

        if(peekTokenIs(RPAREN)) {
            nextToken()
            return identifiers
        }
        nextToken()
        var ident = Identifier(curToken, curToken.literal)
        identifiers.add(ident)
        while (peekTokenIs(COMMA)) {
            nextToken()
            nextToken()
            ident = Identifier(curToken, curToken.literal)
            identifiers.add(ident)
        }

        if (!expectPeek(RPAREN)) return null
        return identifiers
    }

    fun parseCallExpression(function: Expression): Expression {
        val exp = CallExpression(curToken, function)
        exp.arguments = parseCallArguments()
        return exp
    }

    fun parseCallArguments(): List<Expression>? {
        val args = mutableListOf<Expression>()
        if (peekTokenIs(RPAREN)) {
            nextToken()
            return args
        }
        nextToken()
        args.add(parseExpression(Precedence.LOWEST) ?: return null)
        while (peekTokenIs(COMMA)) {
            nextToken()
            nextToken()
            args.add(parseExpression(Precedence.LOWEST) ?: return null)
        }
        if (!expectPeek(RPAREN)) return null
        return args
    }

    fun noPrefixParseFnError(tokenType: TokenType) {
        val msg = "no prefix parse function for ${curToken.type} found"
        errors.add(msg)
    }
}
