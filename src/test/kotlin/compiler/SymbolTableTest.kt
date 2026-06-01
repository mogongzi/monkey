package compiler

import me.ryan.interpreter.compiler.Symbol
import me.ryan.interpreter.compiler.SymbolScope
import me.ryan.interpreter.compiler.SymbolTable
import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test

data class TestCase(val table: SymbolTable, val expectedSymbols: List<Symbol>)

class SymbolTableTest {
    @Test
    fun testDefine() {
        val expected = mapOf(
            "a" to Symbol("a", SymbolScope.GLOBAL, 0),
            "b" to Symbol("b", SymbolScope.GLOBAL, 1),
            "c" to Symbol("c", SymbolScope.LOCAL, 0),
            "d" to Symbol("d", SymbolScope.LOCAL, 1),
            "e" to Symbol("e", SymbolScope.LOCAL, 0),
            "f" to Symbol("f", SymbolScope.LOCAL, 1),
        )
        val global = SymbolTable()
        val a = global.define("a")
        assertEquals(expected["a"], a, "expected a=${expected["a"]}, got=$a")
        val b = global.define("b")
        assertEquals(expected["b"], b, "expected b=${expected["b"]}, got=$b")
        val firstLocal = SymbolTable(global)
        val c = firstLocal.define("c")
        assertEquals(expected["c"], c, "expected c=${expected["c"]}, got=$c")
        val d = firstLocal.define("d")
        assertEquals(expected["d"], d, "expected d=${expected["d"]}, got=$d")
        val secondLocal = SymbolTable(firstLocal)
        val e = secondLocal.define("e")
        assertEquals(expected["e"], e, "expected e=${expected["e"]}, got=$e")
        val f = secondLocal.define("f")
        assertEquals(expected["f"], f, "expected f=${expected["f"]}, got=$f")
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

    @Test
    fun testResolveLocal() {
        val global = SymbolTable()
        global.define("a")
        global.define("b")

        val local = SymbolTable(global)
        local.define("c")
        local.define("d")

        val expected = listOf(
            Symbol("a", SymbolScope.GLOBAL, 0),
            Symbol("b", SymbolScope.GLOBAL, 1),
            Symbol("c", SymbolScope.LOCAL, 0),
            Symbol("d", SymbolScope.LOCAL, 1),
        )

        for (sym in expected) {
            val result = local.resolve(sym.name)
            assertNotNull(result, "name ${sym.name} not resolvable")
            assertEquals(sym, result, "expected ${sym.name} to resolve to $sym, got=$result")
        }
    }

    @Test
    fun testResolveNestedLocal() {
        val global = SymbolTable()
        global.define("a")
        global.define("b")

        val firstLocal = SymbolTable(global)
        firstLocal.define("c")
        firstLocal.define("d")

        val secondLocal = SymbolTable(firstLocal)
        secondLocal.define("e")
        secondLocal.define("f")

        val tests = listOf(
            TestCase(
                firstLocal, listOf(
                    Symbol("a", SymbolScope.GLOBAL, 0),
                    Symbol("b", SymbolScope.GLOBAL, 1),
                    Symbol("c", SymbolScope.LOCAL, 0),
                    Symbol("d", SymbolScope.LOCAL, 1),
                )
            ),
            TestCase(
                secondLocal, listOf(
                    Symbol("a", SymbolScope.GLOBAL, 0),
                    Symbol("b", SymbolScope.GLOBAL, 1),
                    Symbol("e", SymbolScope.LOCAL, 0),
                    Symbol("f", SymbolScope.LOCAL, 1),
                )
            ),
        )

        for (test in tests) {
            for (sym in test.expectedSymbols) {
                val result = test.table.resolve(sym.name)
                assertNotNull(result, "name ${sym.name} not resolvable")
                assertEquals(sym, result, "expected ${sym.name} to resolve to $sym, got=$result")
            }
        }
    }
}