package me.ryan.interpreter.eval

import me.ryan.interpreter.ast.CallExpression
import me.ryan.interpreter.ast.Identifier
import me.ryan.interpreter.ast.LetStatement
import me.ryan.interpreter.ast.MacroLiteral
import me.ryan.interpreter.ast.Node
import me.ryan.interpreter.ast.Program
import me.ryan.interpreter.ast.Statement
import me.ryan.interpreter.modify.modify

class MacroExpansion {
    // companion object holds class-level methods (like Go's package-level functions).
    // All methods here are called on the class itself: MacroExpansion.defineMacros(...)
    companion object {
        // Walks the AST, extracts macro definitions into the environment,
        // and removes them from the program so they're not evaluated as regular code.
        fun defineMacros(program: Program, env: Environment) {
            // removeAll iterates over all statements;
            // the lambda returns true to remove, false to keep
            program.statements.removeAll { statement ->
                if (isMacroDefinition(statement)) {
                    addMacro(statement, env)   // store macro in env
                    true                       // remove from AST
                } else {
                    false                      // keep in AST
                }
            }
        }

        // Extracts the macro literal from a let statement and stores it in the environment
        private fun addMacro(node: Statement, env: Environment) {
            val letStatement = node as LetStatement
            val macroLiteral = letStatement.value as MacroLiteral
            val macro = MMacro(macroLiteral.parameters, macroLiteral.body, env)
            env.set(letStatement.name.value, macro)
        }

        // Checks if a statement is a let statement whose value is a macro literal
        private fun isMacroDefinition(node: Statement): Boolean {
            val letStatement = node as? LetStatement ?: return false
            return letStatement.value is MacroLiteral
        }

        fun expandMacros(program: Program, env: Environment): Node {
            return modify(program, { node ->
                val exp = node as? CallExpression ?: return@modify node

                val macro = isMacroCall(exp, env) ?: return@modify node

                val args = quoteArgs(exp)
                val evalEnv = extendMacroEnv(macro, args)
                val evaluated = Evaluator().eval(macro.body, evalEnv)
                val quote = evaluated as MQuote
                quote.node
            })
        }

        private fun isMacroCall(exp: CallExpression, env: Environment): MMacro? {
            val identifier = exp.function as? Identifier ?: return null
            val obj = env.get(identifier.value) ?: return null
            return obj as? MMacro
        }

        private fun quoteArgs(exp: CallExpression): List<MQuote> {
            return exp.arguments.map { arg -> MQuote(arg) }
        }

        private fun extendMacroEnv(macro: MMacro, args: List<MQuote>): Environment {
            val extended = Environment(macro.env)
            macro.parameters.forEachIndexed { index, identifier -> extended.set(identifier.value, args[index])}
            return extended
        }
    }
}