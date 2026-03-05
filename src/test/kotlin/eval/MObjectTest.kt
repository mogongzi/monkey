package eval

import me.ryan.interpreter.eval.MString
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MObjectTest {
    @Test
    fun testStringHashKey() {
        val hello1 = MString("Hello World")
        val hello2 = MString("Hello World")
        val diff1 = MString("My name is johnny")
        val diff2 = MString("My name is johnny")

        assertEquals(hello1.hashKey(), hello2.hashKey(), "strings with same content have different hash keys")
        assertEquals(diff1.hashKey(), diff2.hashKey(), "strings with same content have different hash keys")
        assertNotEquals(hello1.hashKey(), diff1.hashKey(), "strings with different content have same hash keys")
    }
}