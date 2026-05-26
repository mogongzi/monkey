package me.ryan.interpreter.compiler

enum class SymbolScope {
    GLOBAL
}

data class Symbol(val name: String, val scope: SymbolScope, val index: Int)

class SymbolTable(val outer: SymbolTable? = null) {
    private val store = mutableMapOf<String, Symbol>()
    private var numDefinitions: Int = 0

    fun define(name: String): Symbol {
        val symbol = Symbol(name, SymbolScope.GLOBAL, index = numDefinitions)
        store[name] = symbol
        numDefinitions++
        return symbol
    }

    fun resolve(name: String) : Symbol? {
        return store[name]
    }
}