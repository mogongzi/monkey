package me.ryan.interpreter.eval

import me.ryan.interpreter.ast.LetStatement
import me.ryan.interpreter.ast.MacroLiteral
import me.ryan.interpreter.ast.Program
import me.ryan.interpreter.ast.Statement

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
        private fun addMacro(node: Statement) {

        }

        // Checks if a statement is a let statement whose value is a macro literal
        private fun isMacroDefinition(node: Statement): Boolean {
            val letStatement = node as? LetStatement ?: return false
            return letStatement.value is MacroLiteral
        }
    }
}