# The Bridge: Serializing Bytecode

In Thorsten Ball's *Writing a Compiler in Go*, the compiler and the VM live in the same Go process. The compiler produces a `Bytecode` struct, the VM receives it directly in memory, and that's that:

```go
compiler.Compile(program)
vm := vm.New(compiler.Bytecode())
vm.Run()
```

No file I/O, no format to define, no bytes to worry about. Go hands a struct to Go. Simple.

But we made a different architectural choice. Our compiler is written in Kotlin. Our VM is written in C. They don't share memory. They don't share a runtime. They are two separate programs in two separate languages, and we need a way to get the bytecode from one to the other.

We need a bridge.

This chapter is about building that bridge: a binary file format — `.mkc` — that the Kotlin compiler writes and the C VM reads. If the compiler-to-VM relationship in the book is a function call, ours is a file on disk. Think `javac` producing a `.class` file that `java` later executes. Same idea.

The Go book tests the compiler and VM together in a single process:

```go
func parse(input string) *ast.Program {
    l := lexer.New(input)
    p := parser.New(l)
    return p.ParseProgram()
}

func testIntegerObject(expected int64, actual object.Object) error {
    result, ok := actual.(*object.Integer)
    if !ok {
        return fmt.Errorf("object is not Integer. got=%T (%+v)", actual, actual)
    }
    if result.Value != expected {
        return fmt.Errorf("object has wrong value. got=%d, want=%d",
            result.Value, expected)
    }
    return nil
}
```

We can't do that — our parser lives in Kotlin, our VM lives in C. But we can get close. Our testing pipeline works in two steps:

1. **Kotlin compiles** Monkey source → `.mkc` file (a glue script automates this)
2. **C tests load** the `.mkc` file → feed to VM → assert results

The C tests mirror the Go tests conceptually — they verify that a Monkey program produces the expected result — but the "parse and compile" step happens externally, before the C code runs. A shell script ties the two halves together.

---

## 1 — What Needs to Cross the Bridge?

Before we design anything, let's look at what we already have. Our `ByteCode` class holds everything the VM will need:

```kotlin
class ByteCode(val instructions: Instructions, val constants: MutableList<MObject>)
```

Two things:

1. **`instructions`** — a `UByteArray` of raw bytecode. Opcodes and their operands, packed together. The compiler already encodes these in big-endian order via `make()`.
2. **`constants`** — a list of Monkey objects (`MObject`) that the bytecode references by index. When the VM encounters `OpConstant 0`, it means "push `constants[0]` onto the stack."

Right now, `constants` only contains `MInteger` values — 64-bit integers. Later we'll add strings, compiled functions, and other types. But we'll start with what we have.

So the question becomes: how do we serialize an `Instructions` byte array and a list of `MInteger` values into a binary file that C can read?

> **🏁 Checkpoint 1:** Before writing any code, answer these without scrolling back:
> - What two things does `ByteCode` hold?
> - Why can't we just pass the `ByteCode` struct to the VM like the Go book does?
> - What's the Kotlin type of `instructions`?
> - What's the only constant type we need to handle right now?
>
> *(Answers: instructions and constants; because the compiler is Kotlin and the VM is C — different languages, different processes, no shared memory; `UByteArray`; `MInteger`.)*

---

## 2 — Designing the Format

We need a format that is:

- **Simple** — we're not building a production format. We're learning.
- **Unambiguous** — the C reader must know exactly where each piece of data begins and ends.
- **Extensible** — when we add string constants or compiled functions later, the format should accommodate them without breaking.

Here's the layout:

```
Offset   Size       Field
──────   ────────   ─────────────────────────────
── Constants Pool ──
0        2 bytes    num_constants (uint16, big-endian)
  For each constant:
         1 byte     Type tag (0x01 = integer)
         N bytes    Payload (integer → 8 bytes, int64 big-endian)
── Instructions ──
?        4 bytes    num_instruction_bytes (uint32, big-endian)
?+4      N bytes    Raw instruction bytes (copied verbatim)
```

Let's walk through each piece.

**The constants pool** begins with a 2-byte count. Why 2 bytes? A `uint16` gives us up to 65,535 constants, which is plenty for a learning language. That limit is part of the format, so the serializer should reject larger constant pools rather than silently wrapping. Each constant is then prefixed with a **type tag** — a single byte that tells the reader what follows. For integers, the tag is `0x01`, followed by 8 bytes of the value in big-endian. When we add strings later, we'll define tag `0x02`, followed by a length prefix and the string bytes. The tag-length-value pattern makes the format self-describing.

**The instructions section** is the simplest part. We write a 4-byte length (how many bytes of instructions follow), then dump the raw bytecode verbatim. After those `N` bytes, the file should end; trailing junk is a format error. The bytecode is already in the correct format — the compiler's `make()` function encodes everything in big-endian, which is exactly what the C reader expects.

**Why no magic number or version header?** Production formats like `.class` (Java's `0xCAFEBABE`) or ELF (`0x7F454C46`) use magic bytes for file identification and version fields for forward compatibility. We don't need either. Our `.mkc` files are ephemeral artifacts — generated by the Kotlin compiler, immediately consumed by the C VM, never shared or stored long-term. The glue script that ties the pipeline together already knows what it's producing and what it's feeding to the VM. Adding a header would be engineering for a problem we don't have. We can always add one later if the format needs to be self-identifying.

**Why big-endian everywhere?** It's a deliberate choice. Big-endian is the network byte order convention and makes hex dumps readable (the number `256` appears as `0x01 0x00`, high byte first, just like you'd write it). More practically, Kotlin's `DataOutputStream` writes big-endian by default, and our `make()` function already encodes operands that way. Consistency across the entire file means fewer surprises.

> **🏁 Checkpoint 2:** Draw the format on paper. Given the input `1 + 2` (two integer constants, two `OpConstant` instructions), **predict the exact file size** before reading further. Count each section. Write down your number.
>
> Then check: Constants: 2 (count) + 2 × (1 tag + 8 value) = 20. Instructions: 4 (length) + 6 (two OpConstant instructions, 3 bytes each) = 10. **Total: 30 bytes.** Keep this number — you'll verify it three more times.

### The `1 + 2` File, Byte by Byte

Before writing any code, let's see what 30 bytes actually looks like. This is the complete `.mkc` file for the Monkey program `1 + 2`:

```
Offset  Hex                                        Decoded
──────  ─────────────────────────────────────────  ─────────────────────────────
 0..1   00 02                                      2 constants
 2      01                                         constant[0]: tag=INTEGER
 3..10  00 00 00 00 00 00 00 01                    constant[0]: value=1
11      01                                         constant[1]: tag=INTEGER
12..19  00 00 00 00 00 00 00 02                    constant[1]: value=2
20..23  00 00 00 06                                6 instruction bytes follow
24..26  00 00 00                                   OpConstant 0  (push constants[0])
27..29  00 00 01                                   OpConstant 1  (push constants[1])
```

Walk through it:

- **Bytes 0–1:** `00 02` — big-endian uint16. Two constants follow.
- **Byte 2:** `01` — the type tag for `INTEGER`. This tells the reader: "the next 8 bytes are a signed 64-bit integer."
- **Bytes 3–10:** `00 00 00 00 00 00 00 01` — the integer `1` in big-endian int64. Seven zero bytes, then `0x01`.
- **Byte 11:** Another `01` tag. Second integer.
- **Bytes 12–19:** `00 00 00 00 00 00 00 02` — the integer `2`.
- **Bytes 20–23:** `00 00 00 06` — big-endian uint32. Six bytes of instructions follow.
- **Bytes 24–26:** `00 00 00` — opcode `0x00` is `OpConstant`, followed by operand `0x0000` (index 0). Three bytes total because `OpConstant` has one 2-byte operand.
- **Bytes 27–29:** `00 00 01` — `OpConstant` with operand `0x0001` (index 1).

This is the artifact that crosses the bridge. The Kotlin serializer will produce exactly these bytes. The C reader will consume exactly these bytes. Both sides must agree on every offset, every width, every byte order. If your implementation produces a different sequence, something is wrong.

> **🏁 Checkpoint 3:** Cover the hex column above. Given just the format table and the input `1 + 2`, reconstruct the byte sequence yourself on paper. Pay special attention to:
> - How many zero bytes pad the integer `1`? (seven — it's int64, not int8)
> - What's the big-endian encoding of `-42`? (Work it out: `~42 + 1` = `0xFFFFFFFFFFFFFFD6`)
> - What byte does the instructions section start at? (byte 20 — after the constant count and two 9-byte constants)

---

## 3 — The Kotlin Serializer (Test First)

We follow the same discipline we've used throughout both books: **write the test before writing the code**. The test tells us exactly what the serializer must produce. We read the bytes back with a `ByteBuffer` and verify every field.

### 3.1 — The Test

Create `src/test/kotlin/compiler/SerializerTest.kt`:

```kotlin
package compiler

import me.ryan.interpreter.code.OpConstant
import me.ryan.interpreter.code.make
import me.ryan.interpreter.compiler.ByteCode
import me.ryan.interpreter.compiler.TAG_INTEGER
import me.ryan.interpreter.compiler.writeTo
import me.ryan.interpreter.eval.MInteger
import me.ryan.interpreter.eval.MObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

@OptIn(ExperimentalUnsignedTypes::class)
class SerializerTest {

    @Test
    fun testSerializeIntegerConstants() {
        val constants = mutableListOf<MObject>(
            MInteger(1),
            MInteger(2),
        )
        val instructions = make(OpConstant, 0) + make(OpConstant, 1)
        val bc = ByteCode(instructions, constants)

        val baos = ByteArrayOutputStream()
        bc.writeTo(baos)
        val bytes = baos.toByteArray()
        val buf = ByteBuffer.wrap(bytes)  // big-endian by default

        // Total file size: 20 (constants) + 10 (instructions) = 30
        assertEquals(30, bytes.size, "total file size for '1 + 2'")

        // --- Constants pool ---
        assertEquals(2.toShort(), buf.getShort(), "num_constants")

        assertEquals(TAG_INTEGER, buf.get(), "constant[0] tag")
        assertEquals(1L, buf.getLong(), "constant[0] value")

        assertEquals(TAG_INTEGER, buf.get(), "constant[1] tag")
        assertEquals(2L, buf.getLong(), "constant[1] value")

        // --- Instructions ---
        val expectedInstructions = instructions.toByteArray()
        assertEquals(expectedInstructions.size, buf.getInt(), "num_instruction_bytes")
        val actualInstructions = ByteArray(expectedInstructions.size)
        buf.get(actualInstructions)
        assertArrayEquals(expectedInstructions, actualInstructions, "instruction bytes")

        assertFalse(buf.hasRemaining(), "unexpected trailing bytes")
    }

    @Test
    fun testSerializeEmptyProgram() {
        val bc = ByteCode(ubyteArrayOf(), mutableListOf())
        val baos = ByteArrayOutputStream()
        bc.writeTo(baos)
        val buf = ByteBuffer.wrap(baos.toByteArray())

        // 0 constants, 0 instructions
        assertEquals(0.toShort(), buf.getShort())
        assertEquals(0, buf.getInt())
        assertFalse(buf.hasRemaining())
    }

    @Test
    fun testSerializeNegativeInteger() {
        val bc = ByteCode(make(OpConstant, 0), mutableListOf(MInteger(-42)))
        val baos = ByteArrayOutputStream()
        bc.writeTo(baos)
        val buf = ByteBuffer.wrap(baos.toByteArray())

        assertEquals(1.toShort(), buf.getShort())
        assertEquals(TAG_INTEGER, buf.get())
        assertEquals(-42L, buf.getLong(), "negative integer round-trips")
    }
}
```

This is `ByteBuffer` acting as our test oracle. It wraps the raw bytes and lets us read them back in big-endian order — which is `ByteBuffer`'s default, and also what the format specifies. The final `assertFalse(buf.hasRemaining())` is a check that we consumed every byte — nothing extra was written.

Notice how the test reads the file the same way the C code will: constant count, then each tagged constant, then instruction length and bytes. The test is essentially a Java implementation of the C reader, which gives us confidence that the C side will work.

The three tests cover:

1. **The happy path** — `1 + 2`, two constants, two instructions, 30 bytes total. The 30-byte assertion cross-references the hand calculation from Checkpoint 2.
2. **The degenerate case** — empty program, zero constants, zero instructions. Six bytes total, and the C reader must handle it without crashing.
3. **Two's complement** — negative integer `-42` must survive serialization. `DataOutputStream.writeLong(-42)` writes the 8-byte two's complement big-endian encoding, and the C reader's `read_i64()` interprets those same bits as a signed `int64_t`. On the two's-complement platforms we target, the representation is identical, so no extra conversion code is needed in practice.

Run it now. It won't compile — `TAG_INTEGER` and `writeTo` don't exist yet:

```
$ ./gradlew test
> Compilation failed: Unresolved reference: TAG_INTEGER
```

Good. Red. Now make it green.

### 3.2 — The Implementation

Create `src/main/kotlin/compiler/Serializer.kt`:

```kotlin
package me.ryan.interpreter.compiler

import me.ryan.interpreter.eval.MInteger
import java.io.DataOutputStream
import java.io.OutputStream

const val TAG_INTEGER: Byte = 0x01

@OptIn(ExperimentalUnsignedTypes::class)
fun ByteCode.writeTo(out: OutputStream) {
    require(constants.size <= 0xFFFF) {
        "Serializer: too many constants (${constants.size}); format supports at most 65535"
    }

    val dos = DataOutputStream(out)

    // --- Constants Pool ---
    dos.writeShort(constants.size)
    for (obj in constants) {
        when (obj) {
            is MInteger -> {
                dos.writeByte(TAG_INTEGER.toInt())
                dos.writeLong(obj.value)
            }
            else -> throw IllegalArgumentException(
                "Serializer: unsupported constant type ${obj::class.simpleName}"
            )
        }
    }

    // --- Instructions ---
    val bytes = instructions.toByteArray()
    dos.writeInt(bytes.size)
    dos.write(bytes)

    dos.flush()
}
```

There's something nice about this code: it's almost a line-by-line transcription of the format table from section 2. The constants are a count followed by tagged values. The instructions are a length followed by raw bytes.

**Why an extension function?** Serialization is a concern that lives at the boundary between the compiler and the outside world. `ByteCode` is a data structure — it shouldn't need to know about file formats. The extension function keeps that separation clean.

**Why `DataOutputStream`?** It handles big-endian encoding for us — `writeShort`, `writeInt`, and `writeLong` all produce big-endian output, which matches what our `make()` function does for operands. No manual bit-shifting needed on the Kotlin side.

**The `require(constants.size <= 0xFFFF)` check** enforces the file format's 16-bit constant-count limit. Without it, `writeShort(constants.size)` would quietly write only the low 16 bits, producing a corrupt file instead of failing loudly.

**The `when` block** currently only handles `MInteger`. That `else` branch with the exception is intentional — it's a loud failure that will tell us exactly what we forgot to handle when we add new constant types. Silent data corruption would be much worse.

**The `toByteArray()` call** on `instructions` converts `UByteArray` to `ByteArray`. This is a Kotlin-specific detail — `DataOutputStream.write()` expects signed bytes, but the underlying bits are identical. A `UByte` of `0xFF` becomes a signed `Byte` of `-1`, but both are the same eight bits: `11111111`. The C reader sees the same bits regardless.

Now run:

```
$ ./gradlew test --tests "compiler.SerializerTest"
BUILD SUCCESSFUL
```

Green. Three tests pass. The Kotlin side is done.

> **🏁 Checkpoint 4:** Before running the test, **predict what would happen** if you removed the `dos.flush()` call at the end of `writeTo`. Would the test fail? Why or why not?
>
> In this chapter's examples, probably not:
> - `ByteArrayOutputStream` stores bytes in memory immediately.
> - `FileOutputStream` writes directly to the file descriptor.
> - In the end-to-end example, `.use { ... }` closes the stream, and `close()` flushes first.
>
> `flush()` matters when there's an actual buffering layer and you need bytes pushed through *before* the stream is closed, such as a `BufferedOutputStream` or a network stream. It's still fine to call here, but it's not the reason these tests pass.
>
> Run `./gradlew test --tests "compiler.SerializerTest"`. All three tests should pass, including the 30-byte size assertion — confirming your format spec from Checkpoint 2.

---

## 4 — The C Reader (Test First)

Now we cross to the other side of the bridge. The C reader's job is to open an `.mkc` file and reconstruct the constants and instructions into C structures that the VM can use.

### 4.1 — Data Structures

First, the data structures. These mirror the Kotlin `ByteCode` class, but in C idiom.

Create `src/main/c/vm/mkc.h`:

```c
#ifndef MKC_H
#define MKC_H

#include <stdint.h>
#include <stddef.h>

#define TAG_INTEGER 0x01

typedef struct {
    uint8_t tag;
    union {
        int64_t integer;
    } as;
} MkcConstant;

typedef struct {
    uint16_t     num_constants;
    MkcConstant *constants;
    uint32_t     num_instructions;
    uint8_t     *instructions;
} MkcBytecode;

int  mkc_read(const char *path, MkcBytecode *out);
void mkc_free(MkcBytecode *bc);

#endif
```

If you're coming from Kotlin, several things here will be unfamiliar: include guards, `#define` vs `const val`, `typedef struct`, tagged unions, and pointers with heap allocation. These are all explained in [Appendix A](#appendix-a--c-primer) at the end of this chapter — refer to it whenever a C construct is unclear. The key idea for now: `MkcConstant` uses a tagged union (C's version of a Kotlin `sealed interface`) and `MkcBytecode` stores a count plus pointers to heap-allocated arrays. That's closer to "manual array management" than to `MutableList`: nothing resizes automatically, and you must free the memory yourself.

### 4.2 — The Tests

We write the C tests **before** writing `mkc_read`. C has no JUnit. But TDD doesn't require a framework — it requires assertions. The C standard library gives us `<assert.h>`: `assert(expression)` does nothing if true, prints the failed expression with file/line and aborts if false. That's all we need.

Each test constructs a byte array that represents a specific `.mkc` file, writes it to a temp file, feeds it to `mkc_read`, and asserts the results. This forces you to think at the byte level — exactly the skill you need to debug binary format issues.

Create `src/main/c/vm/test_mkc.c`:

```c
#include "mkc.h"

#include <assert.h>
#include <stdio.h>
#include <string.h>

/* ── Helper: write raw bytes to a temp file ── */

static const char *write_tmp(const uint8_t *data, size_t len) {
    static const char *path = "/tmp/test.mkc";
    FILE *f = fopen(path, "wb");
    fwrite(data, 1, len, f);
    fclose(f);
    return path;
}

/* ── Test 1: Empty program ── */

static void test_empty_program(void) {
    uint8_t data[] = {
        0x00, 0x00,                 /* 0 constants */
        0x00, 0x00, 0x00, 0x00      /* 0 instruction bytes */
    };
    MkcBytecode bc;
    assert(mkc_read(write_tmp(data, sizeof(data)), &bc) == 0);
    assert(bc.num_constants == 0);
    assert(bc.constants == NULL);
    assert(bc.num_instructions == 0);
    assert(bc.instructions == NULL);
    mkc_free(&bc);
    printf("  PASS  test_empty_program\n");
}

/* ── Test 2: One integer constant ── */

static void test_one_integer(void) {
    uint8_t data[] = {
        0x00, 0x01,                                  /* 1 constant */
        TAG_INTEGER,                                 /* constant[0]: tag */
        0x00,0x00,0x00,0x00, 0x00,0x00,0x00,0x2A,   /* constant[0]: 42 */
        0x00, 0x00, 0x00, 0x00                       /* 0 instructions */
    };
    MkcBytecode bc;
    assert(mkc_read(write_tmp(data, sizeof(data)), &bc) == 0);
    assert(bc.num_constants == 1);
    assert(bc.constants[0].tag == TAG_INTEGER);
    assert(bc.constants[0].as.integer == 42);
    assert(bc.num_instructions == 0);
    mkc_free(&bc);
    printf("  PASS  test_one_integer\n");
}

/* ── Test 3: Negative integer (two's complement) ── */

static void test_negative_integer(void) {
    uint8_t data[] = {
        0x00, 0x01,                                  /* 1 constant */
        TAG_INTEGER,
        0xFF,0xFF,0xFF,0xFF, 0xFF,0xFF,0xFF,0xD6,   /* -42 in two's complement */
        0x00, 0x00, 0x00, 0x00
    };
    MkcBytecode bc;
    assert(mkc_read(write_tmp(data, sizeof(data)), &bc) == 0);
    assert(bc.constants[0].as.integer == -42);
    mkc_free(&bc);
    printf("  PASS  test_negative_integer\n");
}

/* ── Test 4: Two constants with instructions ("1 + 2") ── */

static void test_two_constants_with_instructions(void) {
    uint8_t data[] = {
        0x00, 0x02,                                  /* 2 constants */
        TAG_INTEGER,                                 /* constant[0]: tag */
        0x00,0x00,0x00,0x00, 0x00,0x00,0x00,0x01,   /* constant[0]: 1 */
        TAG_INTEGER,                                 /* constant[1]: tag */
        0x00,0x00,0x00,0x00, 0x00,0x00,0x00,0x02,   /* constant[1]: 2 */
        0x00, 0x00, 0x00, 0x06,                      /* 6 instruction bytes */
        0x00, 0x00, 0x00,                            /* OpConstant 0 */
        0x00, 0x00, 0x01                             /* OpConstant 1 */
    };

    /* Verify size matches Checkpoint 2 prediction */
    assert(sizeof(data) == 30);

    MkcBytecode bc;
    assert(mkc_read(write_tmp(data, sizeof(data)), &bc) == 0);

    /* Constants */
    assert(bc.num_constants == 2);
    assert(bc.constants[0].as.integer == 1);
    assert(bc.constants[1].as.integer == 2);

    /* Instructions */
    assert(bc.num_instructions == 6);
    assert(bc.instructions[0] == 0x00);  /* OpConstant opcode */
    assert(bc.instructions[1] == 0x00);  /* operand high byte */
    assert(bc.instructions[2] == 0x00);  /* operand low byte → index 0 */
    assert(bc.instructions[3] == 0x00);  /* OpConstant opcode */
    assert(bc.instructions[4] == 0x00);  /* operand high byte */
    assert(bc.instructions[5] == 0x01);  /* operand low byte → index 1 */

    mkc_free(&bc);
    printf("  PASS  test_two_constants_with_instructions\n");
}

/* ── Test 5: Truncated file ── */

static void test_truncated_constants(void) {
    uint8_t data[] = {
        0x00, 0x01,          /* claims 1 constant... */
        TAG_INTEGER,
        0x00, 0x00           /* ...but only 2 of 8 payload bytes */
    };
    MkcBytecode bc;
    assert(mkc_read(write_tmp(data, sizeof(data)), &bc) != 0);
    printf("  PASS  test_truncated_constants\n");
}

/* ── Test 6: Reject trailing bytes ── */

static void test_trailing_bytes(void) {
    uint8_t data[] = {
        0x00, 0x00,                 /* 0 constants */
        0x00, 0x00, 0x00, 0x00,     /* 0 instruction bytes */
        0xFF                        /* unexpected trailing garbage */
    };
    MkcBytecode bc;
    assert(mkc_read(write_tmp(data, sizeof(data)), &bc) != 0);
    printf("  PASS  test_trailing_bytes\n");
}

/* ── Runner ── */

int main(void) {
    printf("Running mkc tests...\n");
    test_empty_program();
    test_one_integer();
    test_negative_integer();
    test_two_constants_with_instructions();
    test_truncated_constants();
    test_trailing_bytes();
    printf("All tests passed.\n");
    return 0;
}
```

Let's trace the thinking behind a few of these tests:

**Test 1 (empty program)** — zero constants, zero instructions. Six bytes total. `assert(bc.constants == NULL)` verifies that when `num_constants` is 0, the reader doesn't allocate anything — `constants` stays `NULL` (because we `memset` the struct to zero). `mkc_free` will call `free(NULL)`, which is a no-op by the C standard. Safe.

**Test 2 (one integer)** — exercises the constants pool. The value `42` is `0x2A`, so the 8-byte big-endian encoding is `00 00 00 00 00 00 00 2A`. This is exactly what `DataOutputStream.writeLong(42)` produces.

**Test 3 (negative integer)** — two's complement: `-42` is `~42 + 1`. `42` in binary is `00101010`. Flip: `11010101`. Add 1: `11010110` = `0xD6`. Leading bytes are `0xFF` because the sign bit propagates. On the mainstream platforms we're targeting, Java/Kotlin's `Long` and C's `int64_t` use the same two's-complement bit pattern, so the bytes round-trip cleanly.

**Test 4 (full case)** — the 30-byte `1 + 2` file from Checkpoint 2. The `assert(sizeof(data) == 30)` is a runtime check — `assert` is always a runtime mechanism in C (and can be disabled entirely by defining `NDEBUG`). However, `sizeof(data)` itself is computed by the compiler, so if the array literal has the wrong number of bytes, the mismatch will show up the moment you run the test. If you wanted a true compile-time guarantee, C11 provides `_Static_assert(sizeof(data) == 30, "wrong size")`, which fails the build rather than waiting for runtime. We use `assert` here because it's consistent with the rest of the test, but it's worth knowing the distinction.

**Test 5 (truncated)** — the file claims 1 constant but provides only 2 of 8 payload bytes. The reader must fail gracefully, not crash. This is the kind of test you can't easily write on the Kotlin side — there, `DataOutputStream` always writes complete data. But the C reader must handle *any* input.

**Test 6 (trailing bytes)** — the file is otherwise valid, but has one extra byte after the declared instruction payload. The reader should reject it. Binary formats are much easier to debug when the parser is strict: if the format says the file ends here, then any extra bytes mean the producer and consumer disagree.

> **🏁 Checkpoint 5:** Before reading the implementation:
> - The `test_truncated_constants` test feeds a file that claims 1 constant but provides only 2 of 8 payload bytes. **Predict what must happen** inside `mkc_read` for this to not crash. What cleanup is needed? What if the constants array was already allocated?
> - This won't compile yet — `mkc_read` and `mkc_free` are declared but not defined. Create an empty `mkc.c` with `#include "mkc.h"` and try building. You'll get linker errors. Good — red. Now write the implementation.

### 4.3 — The Implementation

Create `src/main/c/vm/mkc.c`:

#### Helpers

Three helper functions for reading big-endian integers from a byte buffer, plus a wrapper around `fread`:

```c
#include "mkc.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int read_exact(FILE *f, uint8_t *buf, size_t n) {
    return fread(buf, 1, n, f) == n ? 0 : -1;
}

static uint16_t read_u16(const uint8_t *buf) {
    return (uint16_t)((buf[0] << 8) | buf[1]);
}

static uint32_t read_u32(const uint8_t *buf) {
    return ((uint32_t)buf[0] << 24)
         | ((uint32_t)buf[1] << 16)
         | ((uint32_t)buf[2] <<  8)
         | ((uint32_t)buf[3]);
}

static int64_t read_i64(const uint8_t *buf) {
    uint64_t v = 0;
    for (int i = 0; i < 8; i++) {
        v = (v << 8) | buf[i];
    }
    return (int64_t)v;
}
```

These helpers are the inverse of what `DataOutputStream` does on the Kotlin side — same bit manipulation your `make()` function uses for encoding operands, just in reverse. The `static` keyword makes them file-private (like Kotlin's `private` at file scope). See [Appendix A](#appendix-a--c-primer) for details on bit shifting, `fread`, and `static`.

#### The Reader

The main `mkc_read()` function reads the file section by section, exactly following the format layout:

```c
int mkc_read(const char *path, MkcBytecode *out) {
    if (!out) {
        fprintf(stderr, "mkc: output pointer is NULL\n");
        return -1;
    }

    memset(out, 0, sizeof(*out));

    FILE *f = fopen(path, "rb");
    if (!f) {
        fprintf(stderr, "mkc: cannot open '%s'\n", path);
        return -1;
    }

    /* ── Constants pool ── */
    uint8_t buf2[2];
    if (read_exact(f, buf2, 2) != 0) {
        fprintf(stderr, "mkc: truncated constant count\n");
        goto fail;
    }
    out->num_constants = read_u16(buf2);

    if (out->num_constants > 0) {
        out->constants = calloc(out->num_constants, sizeof(MkcConstant));
        if (!out->constants) {
            fprintf(stderr, "mkc: out of memory (constants)\n");
            goto fail;
        }
    }

    for (uint16_t i = 0; i < out->num_constants; i++) {
        uint8_t tag;
        if (read_exact(f, &tag, 1) != 0) {
            fprintf(stderr, "mkc: truncated constant tag at index %u\n", i);
            goto fail;
        }
        out->constants[i].tag = tag;

        switch (tag) {
            case TAG_INTEGER: {
                uint8_t buf8[8];
                if (read_exact(f, buf8, 8) != 0) {
                    fprintf(stderr, "mkc: truncated integer at index %u\n", i);
                    goto fail;
                }
                out->constants[i].as.integer = read_i64(buf8);
                break;
            }
            default:
                fprintf(stderr, "mkc: unknown tag 0x%02x at index %u\n", tag, i);
                goto fail;
        }
    }

    /* ── Instructions ── */
    uint8_t buf4[4];
    if (read_exact(f, buf4, 4) != 0) {
        fprintf(stderr, "mkc: truncated instruction length\n");
        goto fail;
    }
    out->num_instructions = read_u32(buf4);

    if (out->num_instructions > 0) {
        out->instructions = malloc(out->num_instructions);
        if (!out->instructions) {
            fprintf(stderr, "mkc: out of memory (instructions)\n");
            goto fail;
        }
        if (read_exact(f, out->instructions, out->num_instructions) != 0) {
            fprintf(stderr, "mkc: truncated instructions\n");
            goto fail;
        }
    }

    int extra = fgetc(f);
    if (extra != EOF) {
        fprintf(stderr, "mkc: trailing bytes after instructions\n");
        goto fail;
    }
    if (ferror(f)) {
        fprintf(stderr, "mkc: read error after instructions\n");
        goto fail;
    }

    fclose(f);
    return 0;

fail:
    fclose(f);
    mkc_free(out);
    return -1;
}

void mkc_free(MkcBytecode *bc) {
    free(bc->constants);
    free(bc->instructions);
    memset(bc, 0, sizeof(*bc));
}
```

Two patterns in this code deserve a note:

- **`goto fail`** centralizes cleanup (closing the file, freeing memory) at one label instead of duplicating it at every error site. This is idiomatic C — it appears everywhere in the Linux kernel. It's the closest C gets to `finally`. See [Appendix A](#a5--goto-fail--error-handling-without-exceptions) for the full explanation.
- **`memset(out, 0, ...)`** zeroes the struct before we even try `fopen()`, so the caller can safely call `mkc_free(out)` after *any* failure path. If we later `goto fail` before allocating, `mkc_free()` calls `free(NULL)` — which is a safe no-op by the C standard. See [Appendix A](#a6--memset--zeroing-memory).
- **The `fgetc()` check at the end** makes the reader strict: after the declared instruction bytes, we expect EOF. That catches trailing junk early instead of silently accepting malformed files.

### 4.4 — Build and Run

Create `src/main/c/vm/Makefile`:

```makefile
CC      = cc
CFLAGS  = -Wall -Wextra -std=c11 -g

all: test_mkc

test_mkc: test_mkc.o mkc.o
	$(CC) $(CFLAGS) -o $@ $^

clean:
	rm -f *.o test_mkc dump_mkc

.PHONY: all clean
```

If you haven't used `make` before, see [Appendix A](#a7--the-makefile). The short version: `-Wall -Wextra` turns on warnings, `-std=c11` gives us modern C, `-g` adds debug symbols. `$@` is the target name, `$^` is all dependencies.

Build and run:

```
$ cd src/main/c/vm
$ make clean && make && ./test_mkc
Running mkc tests...
  PASS  test_empty_program
  PASS  test_one_integer
  PASS  test_negative_integer
  PASS  test_two_constants_with_instructions
  PASS  test_truncated_constants
  PASS  test_trailing_bytes
All tests passed.
```

Green. The C side is done.

> **🏁 Checkpoint 6:** Before running the tests, **predict which test will fail first** if you accidentally swap the byte order in `read_u16` (i.e., `buf[1] << 8 | buf[0]` instead of `buf[0] << 8 | buf[1]`). Which test's assertion would catch it? Why?
>
> Then build and run. You should see no warnings from `make` and no assertion failures. If you get warnings, fix them. If an assert fires, read the file and line number it prints — that's your failing test.

---

## 5 — End-to-End: Across the Bridge

The TDD tests proved that each side works in isolation. But there's one thing they don't prove: that the Kotlin serializer and the C reader agree on the format. Both sides were written by the same person looking at the same spec — what if both have the same misunderstanding?

The end-to-end test closes that gap. We compile real Monkey code with the Kotlin compiler, serialize it to a `.mkc` file, and read it with the C reader.

### 5.1 — Write the File (Kotlin)

Add a test to `src/test/kotlin/compiler/SerializerTest.kt` that writes a `.mkc` file to disk. This is throwaway scaffolding — we only need it once to produce the file. After verifying, you can delete it:

```kotlin
@Test
fun writeTestFile() {
    val input = "1 + 2"
    val lexer = Lexer(input)
    val parser = Parser(lexer)
    val program = parser.parseProgram()

    val compiler = Compiler()
    compiler.compile(program)

    // Writes to the project root. Adjust the path if needed.
    FileOutputStream("test.mkc").use { fos ->
        compiler.byteCode().writeTo(fos)
    }
}
```

Run it with `./gradlew test --tests "compiler.SerializerTest.writeTestFile"`. This produces `test.mkc` in the project root.

### 5.2 — Read the File (C)

Add a `dump_mkc` tool — a separate program that reads a `.mkc` file and prints its contents:

```c
/* dump_mkc.c */
#include "mkc.h"
#include <stdio.h>

static void dump(const MkcBytecode *bc) {
    printf("constants: %u\n", bc->num_constants);
    for (uint16_t i = 0; i < bc->num_constants; i++) {
        const MkcConstant *c = &bc->constants[i];
        switch (c->tag) {
            case TAG_INTEGER:
                printf("  [%u] INTEGER %lld\n", i, (long long)c->as.integer);
                break;
            default:
                printf("  [%u] UNKNOWN tag=0x%02x\n", i, c->tag);
                break;
        }
    }
    printf("instructions: %u bytes\n", bc->num_instructions);
    printf("  ");
    for (uint32_t i = 0; i < bc->num_instructions; i++) {
        printf("%02x ", bc->instructions[i]);
    }
    printf("\n");
}

int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "usage: %s <file.mkc>\n", argv[0]);
        return 1;
    }
    MkcBytecode bc;
    if (mkc_read(argv[1], &bc) != 0) return 1;
    dump(&bc);
    mkc_free(&bc);
    return 0;
}
```

Add to the Makefile:

```makefile
dump_mkc: dump_mkc.o mkc.o
	$(CC) $(CFLAGS) -o $@ $^
```

### 5.3 — Verify

```
$ cd src/main/c/vm
$ make dump_mkc
$ ./dump_mkc ../../../../test.mkc
constants: 2
  [0] INTEGER 1
  [1] INTEGER 2
instructions: 6 bytes
  00 00 00 00 00 01
```

The `00` opcode is `OpConstant`. The two bytes after each are the big-endian operand — `00 00` is constant index 0 (the integer `1`), and `00 01` is constant index 1 (the integer `2`). The constants survived the journey from Kotlin `MInteger` objects → binary bytes on disk → C `int64_t` values. The instructions survived as a verbatim copy.

You can also hexdump the file directly:

```
$ xxd test.mkc
```

You should be able to identify every byte against the format table from section 2.

The bridge works.

> **🏁 Checkpoint 7:** Before running `xxd test.mkc`, **write down the first 4 bytes you expect to see** in hex. Then run it and compare. If your prediction matches, you understand the format at the byte level — which is the whole point of this chapter.
>
> Also verify `ls -la test.mkc` shows **30 bytes**. Four independent confirmations: hand calculation (Checkpoint 2), byte-level walkthrough (Checkpoint 3), Kotlin test assertion, and now the actual file on disk.

---

## 6 — What's Next

We now have the infrastructure to get bytecode from the Kotlin compiler to the C VM. But `vm.h` and `vm.c` are still empty. The actual VM — the stack, the instruction pointer, the dispatch loop — is the subject of the next chapter, where we'll follow the book's `VM` struct and `Run()` method, translated into C.

### The Testing Pipeline

When we start testing the VM, we'll mirror the Go book's test structure — but across the language boundary. The Go book tests look like this:

```go
tests := []struct {
    input    string
    expected int64
}{
    {"1", 1},
    {"2", 2},
    {"1 + 2", 3},
}
for _, tt := range tests {
    program := parse(tt.input)
    comp := compiler.New()
    comp.Compile(program)
    vm := vm.New(comp.Bytecode())
    vm.Run()
    testIntegerObject(tt.expected, vm.LastPoppedStackElem())
}
```

We can't call `parse()` from C, but a glue script can orchestrate the same pipeline:

1. For each test case, the script invokes the Kotlin compiler to compile the Monkey source into a `.mkc` file.
2. The C test loads the `.mkc` file, feeds it to the VM, and asserts the result on the stack.

The script ties the two halves together — Kotlin handles `parse → compile → serialize`, C handles `deserialize → execute → assert`. Same coverage as the Go tests, just split across a process boundary.

We'll also come back to the serializer when we add new constant types. Adding strings will require a `TAG_STRING` with a length-prefixed payload. Adding compiled functions will require a `TAG_FUNCTION` containing its own instruction slice and metadata. Each time, the pattern is the same: add a tag, define the payload layout, update the Kotlin `when` and the C `switch`. The tagged-union format makes this mechanical.

But for now, constants and instructions can cross the bridge. That's enough to power on the machine.

---

## Appendix A — C Primer

This appendix collects the C-specific concepts referenced throughout the chapter. If you're comfortable with C, skip it. If you're coming from Kotlin/Java, read the sections as needed — they're cross-referenced from the main text.

### A.1 — Header Files and Include Guards

In C, code is split into **header files** (`.h`) and **source files** (`.c`). Headers declare *what exists* — types, function signatures — while source files define *how it works*. This is like having a Kotlin `interface` in one file and the `class` implementing it in another, except the separation is enforced by convention and the preprocessor, not by the language itself.

The `#ifndef MKC_H` / `#define MKC_H` / `#endif` triplet is called an **include guard**. C's `#include` is a literal text copy-paste — if two files both `#include "mkc.h"`, the compiler would see duplicate type definitions and complain. The include guard prevents this: the first time `mkc.h` is included, `MKC_H` is undefined, so the `#ifndef` passes and `#define MKC_H` marks it as seen. The second time, `MKC_H` is already defined, so everything between `#ifndef` and `#endif` is skipped.

### A.2 — `#define` vs `const val`

`#define TAG_INTEGER 0x01` is a **preprocessor macro**. Before the compiler even sees your code, the preprocessor replaces every occurrence of `TAG_INTEGER` with `0x01` — literally a text substitution. It's not a variable; it has no type, no address, no runtime existence. This is different from Kotlin's `const val TAG_INTEGER: Byte = 0x01`, which is a typed compile-time constant. C has `const` too, but header-level constants are a little trickier: if you put a definition like `const int TAG_INTEGER = 1;` in a header and include it from multiple `.c` files, you can run into linkage problems unless you use `extern` declarations carefully. For small integer tags like this, `#define` (or an `enum`) is the traditional lightweight choice.

### A.3 — `typedef struct` and Tagged Unions

```c
typedef struct {
    uint8_t tag;
    union {
        int64_t integer;
    } as;
} MkcConstant;
```

In Kotlin, you'd write `data class MkcConstant(val tag: UByte, ...)`. In C, `struct` groups fields together. The `typedef` creates a type alias so we can write `MkcConstant` instead of `struct MkcConstant` everywhere.

A `union` in C is a type where all members **share the same memory**. If you have a union with an `int64_t` (8 bytes) and later a `char*` (8 bytes on 64-bit), the union itself is 8 bytes — not 16. Only one member is valid at a time. The `tag` field tells you which one. This is C's version of Kotlin's `sealed interface` with `when` dispatch:

```kotlin
// Kotlin
sealed interface Constant {
    data class IntConst(val value: Long) : Constant
    data class StrConst(val value: String) : Constant
}
when (constant) {
    is IntConst -> constant.value
    is StrConst -> constant.value
}
```

```c
// C
switch (constant.tag) {
    case TAG_INTEGER: constant.as.integer; break;
    case TAG_STRING:  constant.as.string;  break;
}
```

The Kotlin compiler checks exhaustiveness for you. In C, if you forget a `case`, nothing warns you (unless you add compiler flags like `-Wswitch-enum`).

### A.4 — Pointers and Heap Allocation

```c
MkcConstant *constants;    /* pointer to heap-allocated array */
uint8_t     *instructions; /* pointer to heap-allocated byte array */
```

A **pointer** is a variable that holds a memory address. `MkcConstant *constants` means "`constants` holds the address of an `MkcConstant` somewhere in memory." In Kotlin terms, every object reference is implicitly a pointer — when you write `val list: MutableList<Int>`, `list` doesn't contain the list, it contains a reference (pointer) to the list on the heap. C just makes this explicit.

The `*` in a declaration means "pointer to." The `*` in an expression means "dereference" (follow the pointer to the value). The `->` operator is shorthand: `bc->constants` is equivalent to `(*bc).constants`.

We use pointers here because we don't know how many constants a program has until we read the file. In Kotlin, you might reach for a `MutableList`, which owns its storage and resizes automatically. In C, there is no built-in dynamic list here. What we have is a pointer plus a count, and we manage the backing array ourselves:

```c
out->constants = calloc(out->num_constants, sizeof(MkcConstant));
```

`calloc(count, size)` allocates `count × size` bytes of zeroed memory on the heap and returns a pointer to it. You must manually free it later with `free()`. There is no garbage collector. If you forget to free, the memory leaks. If you free twice, the program may crash or corrupt memory.

The `&` operator takes the **address** of a variable. In `mkc_read(path, &bc)`, `&bc` passes a pointer to the local variable `bc`, letting `mkc_read` fill in the struct directly. Without `&`, C would copy the struct into the function, and modifications would be lost on return.

### A.5 — `goto fail` — Error Handling Without Exceptions

The `goto fail` pattern solves a problem that doesn't exist in Kotlin. In Kotlin, if something goes wrong during deserialization, you throw an exception. The runtime unwinds the stack, `use { }` blocks close resources, and the garbage collector cleans up. C has none of this.

If you've already `calloc`'d the constants array and then the instructions read fails, you need to manually free that array *and* close the file. With multiple failure points, this quickly turns into a mess of duplicated cleanup code:

```c
// WITHOUT goto — repetitive and error-prone:
if (read_exact(f, buf2, 2) != 0) {
    fclose(f);
    return -1;
}
// ... allocate constants ...
if (read_exact(f, buf, 2) != 0) {
    free(out->constants);   // must remember to free!
    fclose(f);
    return -1;
}
```

The `goto fail` pattern centralizes cleanup at the bottom of the function:

```c
fail:
    fclose(f);
    mkc_free(out);
    return -1;
```

Every error site just says `goto fail;` — one line, and all cleanup happens in one place. This pattern is idiomatic C and appears everywhere in the Linux kernel. It's the closest C gets to `finally`.

`goto` has a bad reputation (Dijkstra's "Go To Statement Considered Harmful"), but this specific pattern — jumping forward to a cleanup label at the end of a function — is universally accepted as good C style. It's jumping *backwards* (creating loops) that's the problem.

### A.6 — `memset` — Zeroing Memory

`memset(pointer, value, num_bytes)` fills `num_bytes` of memory starting at `pointer` with the byte `value`. In `mkc_read`, we use `memset(out, 0, sizeof(*out))` to zero the entire output struct before opening the file or allocating anything.

Why? Because of `goto fail`. If we jump to the cleanup label before allocating anything, `mkc_free()` will call `free(out->constants)`. If `constants` contained garbage (uninitialized memory), `free` would try to free a random address and crash. But because we zeroed everything first, `constants` is `NULL`, and `free(NULL)` is defined by the C standard to be a no-op. Safe.

`sizeof(*out)` means "the size of whatever `out` points to" — in this case, `sizeof(MkcBytecode)`. The `*` dereferences the pointer at compile time to get the type, not at runtime to get the value.

The `memset` at the end of `mkc_free` zeroes the struct again, so if someone accidentally uses a freed `MkcBytecode`, they'll get zeroed pointers (which will segfault predictably) rather than dangling pointers (which corrupt memory silently). Defensive, but cheap.

### A.7 — The Makefile

If Gradle is Kotlin's build system, `make` is C's. A Makefile defines **targets** (things to build), their **dependencies** (what they need), and **recipes** (how to build them).

- `CC = cc` — the C compiler to use (`cc` is usually a symlink to `gcc` or `clang`).
- `CFLAGS` — compiler flags:
  - `-Wall` — enable most warnings ("Warning all"). C compilers are silent about many problems by default. Turn this on always.
  - `-Wextra` — even more warnings. Together with `-Wall`, these catch most common mistakes.
  - `-std=c11` — use the C11 standard (from 2011). This gives us features like declaring variables mid-block, which older C standards don't allow.
  - `-g` — include debug symbols so you can use `gdb` or `lldb` to step through the code.
- `test_mkc: test_mkc.o mkc.o` — "to build `test_mkc`, we need `test_mkc.o` and `mkc.o` (object files)."
- `$(CC) $(CFLAGS) -o $@ $^` — link the object files into the final executable. `$@` is the target name (`test_mkc`), `$^` is all dependencies (`test_mkc.o mkc.o`). These are Make's automatic variables.
- `.PHONY: all clean` — tells Make that `all` and `clean` aren't real files. Without this, if you happened to have a file called `clean` in the directory, `make clean` would say "nothing to do."

C compilation happens in two stages: **compile** (`.c` → `.o` object file) and **link** (`.o` files → executable). The Makefile handles both. `make` is smart enough to only recompile files that changed.

### A.8 — `static` Functions

The `static` keyword before a function in C means **file-private** — the function is only visible within this `.c` file. It's like Kotlin's `private` at file scope. Without `static`, every function in C is globally visible to the linker, which means another `.c` file could call `read_u16` and you'd have naming conflicts. We mark helper functions `static` because they're implementation details, not part of the public API.

When `static` appears on a *local variable* inside a function (like `static const char *path` in `write_tmp`), it means something different: the variable lives for the entire program rather than being destroyed when the function returns. Same keyword, different meaning depending on context.

### A.9 — Bit Shifting for Big-Endian Reading

The `read_u16`, `read_u32`, and `read_i64` helpers are the inverse of what `DataOutputStream` does on the Kotlin side. Let's trace `read_u16` with a concrete example. Suppose the buffer contains `0x01 0x00` (the big-endian encoding of 256):

```
buf[0] = 0x01       →  0x01 << 8  = 0x0100  (256)
buf[1] = 0x00       →  0x0100 | 0x00 = 0x0100  (256)
```

`<<` is left-shift (multiply by power of 2), `|` is bitwise OR (combine bits). The first byte is the "high" byte — it represents the 256s place — so we shift it left by 8 bits to put it in the right position, then OR in the second byte (the "low" byte, the 1s place). This is the same bit manipulation your Kotlin `make()` function uses for encoding operands, just in reverse.

`read_i64` uses the same idea in a loop: start with 0, shift left by 8 to make room, OR in the next byte, repeat 8 times. On the two's-complement platforms we target, the final cast from `uint64_t` to `int64_t` preserves the bit pattern we want, matching Java/Kotlin's `Long` representation.

### A.10 — File I/O

C's file I/O is lower-level than Kotlin's. There's no `FileInputStream` class — instead you get an opaque `FILE *` pointer from `fopen()` and pass it to functions like `fread()`, `fwrite()`, and `fclose()`.

`fread(buf, 1, n, f)` means "read `n` items of size 1 byte each from file `f` into buffer `buf`." It returns how many items were actually read. If the file ends early, `fread` returns less than `n` — it doesn't throw an exception like Kotlin would. Our `read_exact` wrapper checks for this and returns `-1` (error) if we got fewer bytes than expected.

The `"rb"` flag in `fopen(path, "rb")` means "read, binary." The `b` is important — without it, on some systems (mainly Windows), the C runtime translates newline characters, which would corrupt our binary data.

### A.11 — `assert()` vs JUnit

C's `assert` (from `<assert.h>`) is a **macro**, not a function. If the expression is false, it prints the failed expression, file name, and line number, then aborts:

```c
assert(1 + 1 == 2);    // passes silently
assert(1 + 1 == 3);    // prints: "test_mkc.c:7: assertion failed: 1 + 1 == 3" and aborts
```

In Kotlin, `assertEquals(expected, actual)` throws an `AssertionError` with a diff. C's `assert` is cruder — it just shows the expression. But when `assert(bc.num_constants == 2)` fails, you know exactly what went wrong.

One important detail: `assert` can be **disabled** by defining `NDEBUG` before including `<assert.h>`. Production code sometimes does this for performance. We never do this — our asserts *are* the tests. If you want a check that **cannot** be disabled and fails at build time rather than runtime, C11 provides `_Static_assert(expr, "message")`.
