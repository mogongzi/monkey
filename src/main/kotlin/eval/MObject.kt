package me.ryan.interpreter.eval

import me.ryan.interpreter.ast.BlockStatement
import me.ryan.interpreter.ast.Identifier

// MObject (Monkey Object) is the internal representation of values
// produced during evaluation of Monkey programs.
// Every value the evaluator produces (integers, booleans, null, etc.) implements this interface.
//
// Value types (MInteger, MBoolean, MString) are data classes, giving them structural equality —
// two instances with the same value are considered equal (e.g., MInteger(5) == MInteger(5)).
// Reference types (MFunction) remain regular classes with identity-based equality,
// since each function definition creates a distinct value regardless of its body.

/**
 * The base interface for all Monkey object types.
 * Equivalent to Go's `object.Object` interface in the book.
 */
interface MObject {
    /** Returns a human-readable string representation for debugging and REPL output. */
    fun inspect(): String
}

/** Wraps a 64-bit integer value produced by evaluating an integer literal or expression. */
data class MInteger(val value: Long) : MObject {
    override fun inspect(): String = value.toString()
}

data class MBoolean(val value: Boolean) : MObject {
    override fun inspect(): String = value.toString()
}

data object MNULL : MObject {
    override fun inspect(): String = "null"
}

data class MString(val value: String) : MObject {
    override fun inspect(): String = value
}

data class MReturnValue(val value: MObject) : MObject {
    override fun inspect(): String = value.inspect()
}

class MERROR(val message: String) : MObject {
    override fun inspect(): String = "ERROR: $message"
}

class Environment(private val outer: Environment? = null) {
    private val store = mutableMapOf<String, MObject>()

    fun get(name: String): MObject? = store[name] ?: outer?.get(name)

    fun set(name: String, value: MObject): MObject {
        store[name] = value
        return value
    }
}

class MFunction(val parameters: List<Identifier>, val body: BlockStatement, val env: Environment) : MObject {
    override fun inspect(): String = buildString {
        append("fn(${parameters.joinToString(", ") { it.string() }}) {\n")
        append(body.string())
        append("\n}")
    }

}

class MBuiltinFunction(val function: (List<MObject>) -> MObject) : MObject {
    override fun inspect(): String = "builtin function"
}
