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
typealias MObjectType = String

// Object type constants — used to distinguish between different kinds of evaluated values.
const val INTEGER_OBJ: MObjectType = "INTEGER"
const val BOOLEAN_OBJ: MObjectType = "BOOLEAN"
const val NULL_OBJ: MObjectType = "NULL"
const val RETURN_VALUE_OBJ: MObjectType = "RETURN_VALUE"
const val ERROR_OBJ: MObjectType = "ERROR"
const val FUNCTION_OBJ: MObjectType = "FUNCTION"
const val STRING_OBJ: MObjectType = "STRING"

/**
 * The base interface for all Monkey object types.
 * Equivalent to Go's `object.Object` interface in the book.
 */
interface MObject {
    /** Returns the type tag of this object (e.g., "INTEGER"). */
    fun type(): MObjectType

    /** Returns a human-readable string representation for debugging and REPL output. */
    fun inspect(): String
}

/** Wraps a 64-bit integer value produced by evaluating an integer literal or expression. */
data class MInteger(val value: Long) : MObject {
    override fun type(): MObjectType = INTEGER_OBJ

    override fun inspect(): String = value.toString()
}

data class MBoolean(val value: Boolean) : MObject {
    override fun type(): MObjectType = BOOLEAN_OBJ

    override fun inspect(): String = value.toString()
}

data object MNULL : MObject {
    override fun type(): MObjectType = NULL_OBJ

    override fun inspect(): String = "null"
}

data class MString(val value: String) : MObject {
    override fun type(): MObjectType = STRING_OBJ
    override fun inspect(): String = value
}

data class MReturnValue(val value: MObject) : MObject {
    override fun type(): MObjectType = RETURN_VALUE_OBJ

    override fun inspect(): String = value.inspect()
}

class MERROR(val message: String) : MObject {
    override fun type(): MObjectType = ERROR_OBJ

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
    override fun type(): MObjectType = FUNCTION_OBJ

    override fun inspect(): String = buildString {
        append("fn(${parameters.joinToString(", ") { it.string() }}) {\n")
        append(body.string())
        append("\n}")
    }

}
