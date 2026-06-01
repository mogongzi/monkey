package me.ryan.interpreter.compiler

enum class SymbolScope {
    GLOBAL,
    LOCAL,
}

data class Symbol(val name: String, val scope: SymbolScope, val index: Int)

class SymbolTable(val outer: SymbolTable? = null) {
    private val store = mutableMapOf<String, Symbol>()
    private var numDefinitions: Int = 0

    fun define(name: String): Symbol {
        val scope = if (outer == null) SymbolScope.GLOBAL else SymbolScope.LOCAL
        val symbol = Symbol(name, scope, index = numDefinitions)
        store[name] = symbol
        numDefinitions++
        return symbol
    }

    fun resolve(name: String): Symbol? {
        val local = store[name]
        if (local != null) {
            return local
        }
        return outer?.resolve(name)
    }
}