package me.ryan.interpreter.eval

import me.ryan.interpreter.ast.BlockStatement
import me.ryan.interpreter.ast.Identifier
import me.ryan.interpreter.ast.Node

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

data class HashKey(val type: String, val value: Long)

interface Hashable {
    fun hashKey(): HashKey
}

/** Wraps a 64-bit integer value produced by evaluating an integer literal or expression. */
data class MInteger(val value: Long) : MObject, Hashable {
    override fun inspect(): String = value.toString()
    override fun hashKey(): HashKey = HashKey("MInteger", value)
}

data class MBoolean(val value: Boolean) : MObject, Hashable {
    override fun inspect(): String = value.toString()
    override fun hashKey(): HashKey = HashKey("MBoolean", if (value) 1 else 0)
}

data object MNULL : MObject {
    override fun inspect(): String = "null"
}

data class MString(val value: String) : MObject, Hashable {
    override fun inspect(): String = value
    override fun hashKey(): HashKey = HashKey("MString", value.hashCode().toLong())
}

data class MReturnValue(val value: MObject) : MObject {
    override fun inspect(): String = value.inspect()
}

class MError(val message: String) : MObject {
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

/** Wraps a Monkey array value. Uses `List` (immutable view) so structural equality is safe. */
data class MArray(val elements: List<MObject> = emptyList()) : MObject {
    override fun inspect(): String = buildString {
        append("[")
        append(elements.joinToString(", ") { it.inspect() })
        append("]")
    }
}

data class HashPair(val key: MObject, val value: MObject)

data class MHash(val pairs: Map<HashKey, HashPair>) : MObject {
    override fun inspect(): String = buildString {
        append("{")
        val entries = pairs.values.joinToString(", ") {
            "${it.key.inspect()}: ${it.value.inspect()}"
        }
        append(entries)
        append("}")
    }
}

class MQuote(val node: Node) : MObject {
    override fun inspect(): String = "QUOTE(${node.string()})"
}

class MMacro(val parameters: List<Identifier>, val body: BlockStatement, val env: Environment) : MObject {
    override fun inspect(): String = buildString {
        append("macro(${parameters.joinToString(", ") { it.string() }}) {\n")
        append(body.string())
        append("\n}")
    }
}
