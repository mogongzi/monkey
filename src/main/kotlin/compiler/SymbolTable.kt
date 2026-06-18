package me.ryan.interpreter.compiler

enum class SymbolScope {
    GLOBAL,
    LOCAL,
    BUILTIN,
    FREE,
}

data class Symbol(val name: String, val scope: SymbolScope, val index: Int)

class SymbolTable(val outer: SymbolTable? = null) {
    private val store = mutableMapOf<String, Symbol>()
    internal var numDefinitions: Int = 0
    internal val freeSymbols = mutableListOf<Symbol>()

    fun define(name: String): Symbol {
        val scope = if (outer == null) SymbolScope.GLOBAL else SymbolScope.LOCAL
        val symbol = Symbol(name, scope, index = numDefinitions)
        store[name] = symbol
        numDefinitions++
        return symbol
    }

    fun resolve(name: String): Symbol? {
        val localSymbol = store[name]
        if (localSymbol != null) return localSymbol

        if (outer != null) {
            val outerSymbol = outer.resolve(name)
            if (outerSymbol != null) {
                if (outerSymbol.scope == SymbolScope.GLOBAL || outerSymbol.scope == SymbolScope.BUILTIN) {
                    return outerSymbol
                }

                return defineFree(outerSymbol)
            }
        }

        return null
    }

    fun defineBuiltin(index: Int, name: String): Symbol {
        val symbol = Symbol(name, SymbolScope.BUILTIN, index)
        store[name] = symbol
        return symbol
    }

    fun defineFree(original: Symbol): Symbol {
        freeSymbols.add(original)
        val symbol = Symbol(original.name, SymbolScope.FREE, freeSymbols.size - 1)
        store[original.name] = symbol
        return symbol
    }
}