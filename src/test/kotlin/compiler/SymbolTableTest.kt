package compiler

import me.ryan.interpreter.compiler.Symbol
import me.ryan.interpreter.compiler.SymbolScope
import me.ryan.interpreter.compiler.SymbolTable
import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test

class SymbolTableTest {
    @Test
    fun testDefine() {
        val expected = mapOf(
            "a" to Symbol("a", SymbolScope.GLOBAL, 0),
            "b" to Symbol("b", SymbolScope.GLOBAL, 1),
        )
        val global = SymbolTable()
        val a = global.define("a")
        assertEquals(expected["a"], a, "expected a=${expected["a"]}, got=$a")
        val b = global.define("b")
        assertEquals(expected["b"], b, "expected b=${expected["b"]}, got=$b")
    }

    @Test
    fun testResolveGlobal() {
        val global = SymbolTable()
        global.define("a")
        global.define("b")

        val expected = listOf(
            Symbol("a", SymbolScope.GLOBAL, 0),
            Symbol("b", SymbolScope.GLOBAL, 1),
        )

        for (sym in expected) {
            val result = global.resolve(sym.name)
            assertNotNull(result, "name ${sym.name} not resolvable")
            assertEquals(sym, result, "expected ${sym.name} to resolve to $sym, got=$result")
        }
    }

}