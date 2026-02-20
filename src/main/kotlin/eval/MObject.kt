package me.ryan.interpreter.eval

// MObject (Monkey Object) is the internal representation of values
// produced during evaluation of Monkey programs.
// Every value the evaluator produces (integers, booleans, null, etc.) implements this interface.
typealias MObjectType = String

// Object type constants — used to distinguish between different kinds of evaluated values.
const val INTEGER_OBJ: MObjectType = "INTEGER"
const val BOOLEAN_OBJ: MObjectType = "BOOLEAN"
const val NULL_OBJ: MObjectType = "NULL"

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
class MInteger(val value: Long) : MObject {
    override fun type(): MObjectType = INTEGER_OBJ

    override fun inspect(): String = value.toString()
}

class MBoolean(val value: Boolean) : MObject {
    override fun type(): MObjectType = BOOLEAN_OBJ

    override fun inspect(): String = value.toString()
}

class MNULL() : MObject {
    override fun type(): MObjectType = NULL_OBJ

    override fun inspect(): String = "null"
}


