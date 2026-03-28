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

## 1.1 — What Needs to Cross the Bridge?

Before we design anything, let's look at what we already have. Our `ByteCode` class holds everything the VM will need:

```kotlin
class ByteCode(val instructions: Instructions, val constants: MutableList<MObject>)
```

Two things:

1. **`instructions`** — a `UByteArray` of raw bytecode. Opcodes and their operands, packed together. The compiler already encodes these in big-endian order via `make()`.
2. **`constants`** — a list of Monkey objects (`MObject`) that the bytecode references by index. When the VM encounters `OpConstant 0`, it means "push `constants[0]` onto the stack."

Right now, `constants` only contains `MInteger` values — 64-bit integers. Later we'll add strings, compiled functions, and other types. But we'll start with what we have.

So the question becomes: how do we serialize an `Instructions` byte array and a list of `MInteger` values into a binary file that C can read?

> **🏁 Checkpoint 1:** Before writing any code, make sure you can answer these:
> - What two things does `ByteCode` hold? (instructions and constants)
> - Why can't we just pass the `ByteCode` struct to the VM like the Go book does?
> - What's the Kotlin type of `instructions`? (`UByteArray`)
> - What's the only constant type we need to handle right now? (`MInteger`)

## 1.2 — Designing the Format

We need a format that is:

- **Simple** — we're not building a production format. We're learning.
- **Unambiguous** — the C reader must know exactly where each piece of data begins and ends.
- **Extensible** — when we add string constants or compiled functions later, the format should accommodate them without breaking.

Here's the layout:

```
Offset   Size       Field
──────   ────────   ─────────────────────────────
0        3 bytes    Magic number: "MKC"
3        1 byte     Version: 0x01
── Constants Pool ──
4        2 bytes    num_constants (uint16, big-endian)
  For each constant:
         1 byte     Type tag (0x01 = integer)
         N bytes    Payload (integer → 8 bytes, int64 big-endian)
── Instructions ──
?        4 bytes    num_instruction_bytes (uint32, big-endian)
?+4      N bytes    Raw instruction bytes (copied verbatim)
```

Let's walk through each piece.

**The magic number** (`"MKC"`) is three ASCII bytes at the start of the file. Its job is identity — if you accidentally feed a JPEG to the VM, it won't silently try to execute pixel data. It'll see that the first three bytes aren't `M`, `K`, `C` and bail out immediately. This is a convention as old as file formats themselves: Java uses `0xCAFEBABE`, PNG uses `0x89504E47`, ELF uses `0x7F454C46`. Ours spells "MKC" for "Monkey Compiled."

**The version byte** (`0x01`) is forward-thinking. When we change the format later — and we will — the C reader can check this byte and reject versions it doesn't understand, instead of silently misinterpreting data. We start at version 1.

**The constants pool** begins with a 2-byte count. Why 2 bytes? A `uint16` gives us up to 65,535 constants, which is plenty for a learning language. Each constant is then prefixed with a **type tag** — a single byte that tells the reader what follows. For integers, the tag is `0x01`, followed by 8 bytes of the value in big-endian. When we add strings later, we'll define tag `0x02`, followed by a length prefix and the string bytes. The tag-length-value pattern makes the format self-describing.

**The instructions section** is the simplest part. We write a 4-byte length (how many bytes of instructions follow), then dump the raw bytecode verbatim. The bytecode is already in the correct format — the compiler's `make()` function encodes everything in big-endian, which is exactly what the C reader expects.

**Why big-endian everywhere?** It's a deliberate choice. Big-endian is the network byte order convention and makes hex dumps readable (the number `256` appears as `0x01 0x00`, high byte first, just like you'd write it). More practically, Kotlin's `DataOutputStream` writes big-endian by default, and our `make()` function already encodes operands that way. Consistency across the entire file means fewer surprises.

> **🏁 Checkpoint 2:** Draw the format on paper or a whiteboard. Given the input `1 + 2` (two integer constants, two `OpConstant` instructions), calculate the exact file size by hand:
> - Header: 3 (magic) + 1 (version) = **4 bytes**
> - Constants: 2 (count) + 2 × (1 tag + 8 value) = **20 bytes**
> - Instructions: 4 (length) + 6 (two OpConstant instructions, 3 bytes each) = **10 bytes**
> - **Total: 34 bytes**
>
> Keep this number in mind — you'll verify it in Checkpoint 4.

## 1.3 — The Kotlin Serializer

Now let's write the code. We'll add a `writeTo()` extension function on `ByteCode` that writes the `.mkc` format to any `OutputStream`.

Why an extension function instead of a method on `ByteCode`? Because serialization is a concern that lives at the boundary between the compiler and the outside world. `ByteCode` is a data structure — it shouldn't need to know about file formats. The extension function keeps that separation clean.

Here's `Serializer.kt`:

```kotlin
package me.ryan.interpreter.compiler

import me.ryan.interpreter.eval.MInteger
import java.io.DataOutputStream
import java.io.OutputStream

const val FORMAT_VERSION: Byte = 1
const val TAG_INTEGER: Byte = 0x01

@OptIn(ExperimentalUnsignedTypes::class)
fun ByteCode.writeTo(out: OutputStream) {
    val dos = DataOutputStream(out)

    // --- Header ---
    dos.writeByte('M'.code)
    dos.writeByte('K'.code)
    dos.writeByte('C'.code)
    dos.writeByte(FORMAT_VERSION.toInt())

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

There's something nice about this code: it's almost a line-by-line transcription of the format table from section 1.2. The header is three character bytes and a version. The constants are a count followed by tagged values. The instructions are a length followed by raw bytes. `DataOutputStream` handles big-endian encoding for us — `writeShort`, `writeInt`, and `writeLong` all produce big-endian output, which matches what our `make()` function does for operands. No manual bit-shifting needed on the Kotlin side.

The `when` block on constant types currently only handles `MInteger`. That `else` branch with the exception is intentional — it's a loud failure that will tell us exactly what we forgot to handle when we add new constant types. Silent data corruption would be much worse.

The `toByteArray()` call on `instructions` converts `UByteArray` to `ByteArray`. This is a Kotlin-specific detail — `DataOutputStream.write()` expects signed bytes, but the underlying bits are identical. A `UByte` of `0xFF` becomes a signed `Byte` of `-1`, but both are the same eight bits: `11111111`. The C reader sees the same bits regardless.

> **🏁 Checkpoint 3 — Write it yourself:** Create `Serializer.kt` with `FORMAT_VERSION`, `TAG_INTEGER`, and the `writeTo()` extension function. Before moving on:
> - Does it compile? Run `./gradlew compileKotlin`.
> - Can you explain why we use `DataOutputStream` instead of writing raw bytes manually?
> - What happens if you pass an `MString` constant? (It should throw — verify this by writing a quick test.)

## 1.4 — Testing the Serializer

Before we touch C, let's make sure the Kotlin side produces correct output. We'll read back the bytes with a `ByteBuffer` and verify every field:

```kotlin
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

    // --- Header ---
    assertEquals('M'.code.toByte(), buf.get(), "magic[0]")
    assertEquals('K'.code.toByte(), buf.get(), "magic[1]")
    assertEquals('C'.code.toByte(), buf.get(), "magic[2]")
    assertEquals(FORMAT_VERSION, buf.get(), "version")

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
```

This is `ByteBuffer` acting as our test oracle. It wraps the raw bytes and lets us read them back in big-endian order — which is `ByteBuffer`'s default, and also what the format specifies. The final `assertFalse(buf.hasRemaining())` is a check that we consumed every byte — nothing extra was written.

Notice how the test reads the file the same way the C code will: magic, version, constant count, then each tagged constant, then instruction length and bytes. The test is essentially a Java implementation of the C reader, which gives us confidence that the C side will work.

We should also test edge cases:

```kotlin
@Test
fun testSerializeEmptyProgram() {
    val bc = ByteCode(ubyteArrayOf(), mutableListOf())
    val baos = ByteArrayOutputStream()
    bc.writeTo(baos)
    val buf = ByteBuffer.wrap(baos.toByteArray())

    // Header still present
    assertEquals('M'.code.toByte(), buf.get())
    assertEquals('K'.code.toByte(), buf.get())
    assertEquals('C'.code.toByte(), buf.get())
    assertEquals(FORMAT_VERSION, buf.get())

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

    buf.position(4)  // skip header
    assertEquals(1.toShort(), buf.getShort())
    assertEquals(TAG_INTEGER, buf.get())
    assertEquals(-42L, buf.getLong(), "negative integer round-trips")
}
```

> **🏁 Checkpoint 4 — Verify the byte count:** In `testSerializeIntegerConstants`, add a line after `bc.writeTo(baos)`:
> ```kotlin
> assertEquals(34, bytes.size, "total file size for '1 + 2'")
> ```
> This is the 34 bytes you calculated by hand in Checkpoint 2. If they match, your format implementation agrees with your format spec. If they don't — find the discrepancy before moving on.

The empty program test ensures the format handles the degenerate case — header, zero constants, zero instruction bytes. Ten bytes total, and the C reader must handle it without crashing.

The negative integer test verifies that two's complement representation survives the serialization round-trip. `DataOutputStream.writeLong(-42)` writes the 8-byte two's complement big-endian encoding, and the C reader's `read_i64()` interprets those same bits as a signed `int64_t`. The representation is identical — no conversion needed.

> **🏁 Checkpoint 5 — Write the tests yourself:** Create `SerializerTest.kt` with at least these tests:
> 1. `testSerializeIntegerConstants` — the "1 + 2" case with the 34-byte size assertion
> 2. `testSerializeEmptyProgram` — zero constants, zero instructions
> 3. `testSerializeNegativeInteger` — verify `-42` round-trips correctly
>
> Run `./gradlew test` and make sure all pass. **The Kotlin side is now done.** Everything from here on is C.

## 1.5 — The C Reader

Now we cross to the other side of the bridge. The C reader's job is to open an `.mkc` file and reconstruct the constants and instructions into C structures that the VM can use.

First, the data structures. These mirror the Kotlin `ByteCode` class, but in C idiom:

```c
/* mkc.h */
#ifndef MKC_H
#define MKC_H

#include <stdint.h>
#include <stddef.h>

#define TAG_INTEGER 0x01
#define MKC_VERSION 0x01

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

If you're coming from Kotlin, almost every line here has something unfamiliar. Let's unpack it.

### C Primer: Header Files and Include Guards

In C, code is split into **header files** (`.h`) and **source files** (`.c`). Headers declare *what exists* — types, function signatures — while source files define *how it works*. This is like having a Kotlin `interface` in one file and the `class` implementing it in another, except the separation is enforced by convention and the preprocessor, not by the language itself.

The `#ifndef MKC_H` / `#define MKC_H` / `#endif` triplet is called an **include guard**. C's `#include` is a literal text copy-paste — if two files both `#include "mkc.h"`, the compiler would see duplicate type definitions and complain. The include guard prevents this: the first time `mkc.h` is included, `MKC_H` is undefined, so the `#ifndef` passes and `#define MKC_H` marks it as seen. The second time, `MKC_H` is already defined, so everything between `#ifndef` and `#endif` is skipped. Every C header file you'll ever see has this pattern.

`#include <stdint.h>` and `#include <stddef.h>` bring in standard fixed-width integer types (`uint8_t`, `int64_t`, `uint16_t`, etc.) and `size_t`. In Kotlin, `Long` is always 64 bits. In C, `int` and `long` have platform-dependent sizes — `int` might be 16, 32, or 64 bits depending on the architecture. The `stdint.h` types guarantee exact widths, which is essential when reading a binary format where every byte matters.

### C Primer: `#define` vs `const val`

`#define TAG_INTEGER 0x01` is a **preprocessor macro**. Before the compiler even sees your code, the preprocessor replaces every occurrence of `TAG_INTEGER` with `0x01` — literally a text substitution. It's not a variable; it has no type, no address, no runtime existence. This is different from Kotlin's `const val TAG_INTEGER: Byte = 0x01`, which is a typed compile-time constant. C has `const` too, but `#define` is the traditional way to define constants in headers because `const` variables in C have some surprising scoping issues across files. For our purposes, just read `#define TAG_INTEGER 0x01` as "wherever you see `TAG_INTEGER`, substitute `0x01`."

### C Primer: `typedef struct`

```c
typedef struct {
    uint8_t tag;
    union { ... } as;
} MkcConstant;
```

In Kotlin, you'd write `data class MkcConstant(val tag: UByte, ...)`. In C, `struct` is the equivalent — a composite type that groups fields together. The `typedef ... MkcConstant;` part creates a type alias so we can write `MkcConstant` instead of `struct MkcConstant` everywhere. Without `typedef`, you'd have to type `struct MkcConstant` every time you declare a variable of this type.

Structs in C are **value types** — they live on the stack by default and are copied when assigned, like Kotlin's `data class` with `copy()`, but implicit. This matters: when you pass a struct to a function, C copies the whole thing. For large structs, you pass a pointer instead (more on this below).

### C Primer: Tagged Unions

```c
typedef struct {
    uint8_t tag;
    union {
        int64_t integer;
    } as;
} MkcConstant;
```

A `union` in C is a type where all members **share the same memory**. If you have a union with an `int64_t` (8 bytes) and later a `char*` (8 bytes on 64-bit), the union itself is 8 bytes — not 16. Only one member is valid at a time. The `tag` field tells you which one.

This is C's version of Kotlin's sealed interface with `when` dispatch:

```kotlin
// Kotlin approach
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
// C approach
switch (constant.tag) {
    case TAG_INTEGER: constant.as.integer; break;
    case TAG_STRING:  constant.as.string;  break;
}
```

The Kotlin compiler checks exhaustiveness for you. In C, if you forget a `case`, nothing warns you (unless you add compiler flags). The tradeoff is performance — tagged unions have zero overhead, no vtable, no heap allocation.

### C Primer: Pointers

```c
MkcConstant *constants;    /* pointer to heap-allocated array */
uint8_t     *instructions; /* pointer to heap-allocated byte array */
```

A **pointer** is a variable that holds a memory address. `MkcConstant *constants` means "`constants` holds the address of an `MkcConstant` somewhere in memory." In Kotlin terms, every object reference is implicitly a pointer — when you write `val list: MutableList<Int>`, `list` doesn't contain the list, it contains a reference (pointer) to the list on the heap. C just makes this explicit.

The `*` in a declaration means "pointer to." The `*` in an expression means "dereference" (follow the pointer to the value). The `->` operator is shorthand for dereferencing and accessing a field: `bc->constants` is equivalent to `(*bc).constants`.

Why pointers here? Because we don't know how many constants a program has until we read the file. In Kotlin, you'd use a `MutableList` that grows dynamically. In C, there are no dynamic lists built in. Instead, you ask the operating system for a chunk of memory at runtime:

```c
out->constants = calloc(out->num_constants, sizeof(MkcConstant));
```

`calloc(count, size)` allocates `count × size` bytes of zeroed memory on the heap and returns a pointer to it. It's like `Array(count) { MkcConstant() }` in Kotlin, except you must manually free it later with `free(out->constants)`. There is no garbage collector. If you forget to free, the memory leaks. If you free twice, the program may crash or corrupt memory. This manual memory management is the defining characteristic of C.

`sizeof(MkcConstant)` returns the size in bytes of the `MkcConstant` struct. The compiler computes this at compile time — it's not a runtime call.

`MkcBytecode` uses **heap-allocated arrays** (pointers + counts) rather than fixed-size arrays. We don't know how many constants or instructions a program will have until we read the file, so we `calloc` the right amount after reading the counts. The caller is responsible for calling `mkc_free()` when done — C doesn't have destructors or garbage collection.

> **🏁 Checkpoint 6 — Set up the C project:** Before writing the reader logic:
> 1. Create the directory `src/main/c/vm/`
> 2. Create `mkc.h` with the struct definitions, `#define` constants, and function declarations shown above
> 3. Create an empty `mkc.c` with just `#include "mkc.h"`
> 4. Create a `Makefile` (you can copy the one shown in section 1.6)
> 5. Run `make` — it won't build yet (no `test_mkc.c`), but it verifies your toolchain works. Try `cc --version` if it fails.
>
> This is scaffolding. Get it compiling before writing any logic.

Now the reader itself. We need three helper functions for reading big-endian integers from a byte buffer:

```c
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

### C Primer: `static` Functions

The `static` keyword before a function in C means **file-private** — the function is only visible within this `.c` file. It's like Kotlin's `private` at file scope. Without `static`, every function in C is globally visible to the linker, which means another `.c` file could call `read_u16` and you'd have naming conflicts. We mark helper functions `static` because they're implementation details, not part of the public API.

### C Primer: `const uint8_t *buf`

The `const` in `const uint8_t *buf` means "this function promises not to modify the data that `buf` points to." It's a read-only contract — the compiler will reject any attempt to write through this pointer. In Kotlin, this is roughly the difference between `List<Int>` (read-only view) and `MutableList<Int>`. The `const` doesn't change behavior; it prevents bugs by making intent explicit.

### C Primer: Bit Shifting for Big-Endian Reading

These functions are the inverse of what `DataOutputStream` does on the Kotlin side. Let's trace `read_u16` with a concrete example. Suppose the file contains the bytes `0x01 0x00` (the big-endian encoding of 256):

```
buf[0] = 0x01       →  0x01 << 8  = 0x0100  (256)
buf[1] = 0x00       →  0x0100 | 0x00 = 0x0100  (256)
```

`<<` is left-shift (multiply by power of 2), `|` is bitwise OR (combine bits). The first byte is the "high" byte — it represents the 256s place — so we shift it left by 8 bits to put it in the right position, then OR in the second byte (the "low" byte, the 1s place). This is the same bit manipulation your Kotlin `make()` function uses for encoding operands, just in reverse.

`read_i64` uses the same idea in a loop: start with 0, shift left by 8 to make room, OR in the next byte, repeat 8 times. The final cast from `uint64_t` to `int64_t` preserves the bit pattern — C's two's complement representation matches Java/Kotlin's.

We also need a small wrapper around `fread` that treats a short read as an error:

```c
static int read_exact(FILE *f, uint8_t *buf, size_t n) {
    return fread(buf, 1, n, f) == n ? 0 : -1;
}
```

### C Primer: File I/O

C's file I/O is lower-level than Kotlin's. There's no `FileInputStream` class — instead you get an opaque `FILE *` pointer from `fopen()` and pass it to functions like `fread()`, `fwrite()`, and `fclose()`.

`fread(buf, 1, n, f)` means "read `n` items of size 1 byte each from file `f` into buffer `buf`." It returns how many items were actually read. If the file ends early, `fread` returns less than `n` — it doesn't throw an exception like Kotlin would. Our `read_exact` wrapper checks for this and returns `-1` (error) if we got fewer bytes than expected.

The `"rb"` flag in `fopen(path, "rb")` means "read, binary." The `b` is important — without it, on some systems (mainly Windows), the C runtime translates newline characters, which would corrupt our binary data.

> **🏁 Checkpoint 7 — Write the helpers yourself:** Add `read_u16`, `read_u32`, `read_i64`, and `read_exact` to `mkc.c`. Test them in isolation before moving on — write a small `main()` in a scratch file:
> ```c
> #include <stdio.h>
> #include <stdint.h>
> // paste your read_u16 here
> int main(void) {
>     uint8_t buf[] = {0x01, 0x00};
>     printf("%u\n", read_u16(buf));  // should print 256
>     return 0;
> }
> ```
> Compile with `cc -o scratch scratch.c && ./scratch`. If you get 256, your bit shifting is correct. Try other values: `{0x00, 0x01}` → 1, `{0xFF, 0xFF}` → 65535.

With these helpers, the main `mkc_read()` function reads the file section by section, exactly following the format layout:

```c
int mkc_read(const char *path, MkcBytecode *out) {
    FILE *f = fopen(path, "rb");
    if (!f) {
        fprintf(stderr, "mkc: cannot open '%s'\n", path);
        return -1;
    }

    memset(out, 0, sizeof(*out));
    uint8_t header[4];

    /* ── Header ── */
    if (read_exact(f, header, 4) != 0) {
        fprintf(stderr, "mkc: truncated header\n");
        goto fail;
    }
    if (header[0] != 'M' || header[1] != 'K' || header[2] != 'C') {
        fprintf(stderr, "mkc: bad magic (expected MKC)\n");
        goto fail;
    }
    if (header[3] != MKC_VERSION) {
        fprintf(stderr, "mkc: unsupported version %d\n", header[3]);
        goto fail;
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

    fclose(f);
    return 0;

fail:
    fclose(f);
    mkc_free(out);
    return -1;
}
```

### C Primer: `goto fail` — Error Handling Without Exceptions

The `goto fail` pattern deserves a moment, because it solves a problem that doesn't exist in Kotlin.

In Kotlin, if something goes wrong during deserialization, you throw an exception. The runtime unwinds the stack, `use { }` blocks close resources, and the garbage collector cleans up any allocated objects. You don't think about it.

C has none of this. No exceptions. No `try`/`finally`. No garbage collector. If you've already `calloc`'d the constants array and then the instructions read fails, you need to manually free that array *and* close the file before returning. With multiple failure points, this quickly turns into a mess of duplicated cleanup code:

```c
// WITHOUT goto — repetitive and error-prone:
if (read_exact(f, header, 4) != 0) {
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

### C Primer: `memset` — Zeroing Memory

The `memset(out, 0, sizeof(*out))` at the top is important. `memset(pointer, value, num_bytes)` fills `num_bytes` of memory starting at `pointer` with the byte `value`. Here it zeroes the entire output struct.

Why? Because of `goto fail`. If we jump to the cleanup label before allocating anything, `mkc_free()` will call `free(out->constants)`. If `constants` contained garbage (uninitialized memory), `free` would try to free a random address and crash. But because we zeroed everything first, `constants` is `NULL`, and `free(NULL)` is defined by the C standard to be a no-op. Safe.

`sizeof(*out)` means "the size of whatever `out` points to" — in this case, `sizeof(MkcBytecode)`. The `*` dereferences the pointer at compile time to get the type, not at runtime to get the value.

> **🏁 Checkpoint 8 — Write `mkc_read` yourself:** This is the hardest part. Build it **incrementally**:
> 1. **First**, write only the header validation — open the file, read 4 bytes, check magic and version, close, return 0. Compile and test with a hand-crafted 4-byte file: `printf 'MKC\x01' > test.mkc`.
> 2. **Second**, add the constants pool reading. Test with an empty constants pool (the empty program from your Kotlin test — write it with a quick Kotlin main).
> 3. **Third**, add the instructions reading.
> 4. **Last**, add `mkc_free()` and the `goto fail` cleanup.
>
> At each step, make sure it compiles and doesn't crash. If you get a segfault, run with `lldb ./test_mkc` and type `run test.mkc` — it'll show you exactly which line crashed.

The cleanup function itself is straightforward:

```c
void mkc_free(MkcBytecode *bc) {
    free(bc->constants);
    free(bc->instructions);
    memset(bc, 0, sizeof(*bc));
}
```

The `memset` at the end zeros the struct again, so if someone accidentally uses a freed `MkcBytecode`, they'll get zeroed pointers (which will segfault predictably) rather than dangling pointers (which corrupt memory silently). Defensive, but cheap.

## 1.6 — Testing the C Reader (TDD Style)

On the Kotlin side, we tested by reading bytes back with `ByteBuffer`. On the C side, we'll do the inverse — **construct** the bytes by hand and feed them to `mkc_read`. If you can build a valid `.mkc` byte array from memory, you understand the format.

C has no built-in test framework. No `@Test` annotation, no JUnit, no test runner. But TDD doesn't require a framework — it requires assertions. The C standard library gives us `<assert.h>`, which provides a single macro: `assert(expression)`. If the expression is true, nothing happens. If it's false, the program prints the failed expression, the file name, and the line number, then aborts. That's all we need.

### C Primer: `assert()` — The Simplest Test Framework

```c
#include <assert.h>

assert(1 + 1 == 2);    // passes silently
assert(1 + 1 == 3);    // prints: "test_mkc.c:7: assertion failed: 1 + 1 == 3" and aborts
```

`assert` is a **macro**, not a function. The preprocessor expands it to something like:

```c
if (!(expression)) {
    fprintf(stderr, "%s:%d: assertion failed: %s\n", __FILE__, __LINE__, "expression");
    abort();
}
```

`__FILE__` and `__LINE__` are special preprocessor tokens that expand to the current file name and line number. This is why `assert` can tell you *where* the failure happened — information that's baked in at compile time.

In Kotlin, `assertEquals(expected, actual)` throws an `AssertionError` with a diff. C's `assert` is cruder — it just shows the expression that failed. But it's enough. When `assert(bc.num_constants == 2)` fails, you know exactly what went wrong.

One important detail: `assert` can be disabled by defining `NDEBUG` before including `<assert.h>`. Production code sometimes does this for performance. We'll never do this — our asserts *are* the tests.

### Building Test Data by Hand

Here's the key pedagogical insight: to test the C reader, we need to construct `.mkc` bytes manually. This forces you to think at the byte level — exactly the skill you need to debug binary format issues.

We'll write a helper that takes a raw byte array and writes it to a temp file, since `mkc_read` expects a file path:

```c
static const char *write_tmp(const uint8_t *data, size_t len) {
    static const char *path = "/tmp/test.mkc";
    FILE *f = fopen(path, "wb");
    fwrite(data, 1, len, f);
    fclose(f);
    return path;
}
```

### C Primer: `static` Local Variables

The `static const char *path` inside the function is a **static local variable**. Unlike normal local variables (which live on the stack and die when the function returns), a `static` local lives for the entire program. It's initialized once, and every call to `write_tmp` reuses the same `path`. This is different from `static` at file scope (which means "file-private") — same keyword, different meaning depending on context. In Kotlin, you'd achieve this with a companion object property.

Now the tests. Each one constructs a byte array that represents a specific `.mkc` file, feeds it to `mkc_read`, and asserts the results:

```c
/* test_mkc.c */
#include "mkc.h"

#include <assert.h>
#include <stdio.h>
#include <string.h>

static const char *write_tmp(const uint8_t *data, size_t len) {
    static const char *path = "/tmp/test.mkc";
    FILE *f = fopen(path, "wb");
    fwrite(data, 1, len, f);
    fclose(f);
    return path;
}
```

**Test 1: Reject bad magic.** The very first thing `mkc_read` checks. If the first three bytes aren't `MKC`, it should fail immediately. This is the simplest possible test — write it first:

```c
static void test_bad_magic(void) {
    uint8_t data[] = {
        'X','Y','Z', 0x01,         /* wrong magic, correct version */
        0x00, 0x00,                 /* 0 constants */
        0x00, 0x00, 0x00, 0x00      /* 0 instructions */
    };
    MkcBytecode bc;
    assert(mkc_read(write_tmp(data, sizeof(data)), &bc) != 0);
    printf("  PASS  test_bad_magic\n");
}
```

Notice what we're testing: the *return value*. `mkc_read` returns `0` on success and `-1` on failure. We assert that it returns non-zero — we don't care about the exact error, just that it rejected the input. The error message goes to `stderr` (which we'll see on the terminal), and the test checks the return code.

**Test 2: Empty program.** Header, zero constants, zero instructions — the degenerate case. This is the same empty program we tested on the Kotlin side in `testSerializeEmptyProgram`:

```c
static void test_empty_program(void) {
    uint8_t data[] = {
        'M','K','C', 0x01,         /* header */
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
```

Count the bytes: 4 (header) + 2 (constant count) + 4 (instruction length) = **10 bytes**. The same 10 bytes your Kotlin `testSerializeEmptyProgram` produces. If the C reader can parse these 10 bytes and produce an empty `MkcBytecode`, the format is working.

The `assert(bc.constants == NULL)` check is worth highlighting. When `num_constants` is 0, `mkc_read` doesn't allocate anything — `constants` stays `NULL` (because we `memset` the struct to zero at the top). This is intentional: `mkc_free` will call `free(NULL)`, which is a no-op. Clean.

**Test 3: One integer constant.** Now we exercise the constants pool parsing. We'll encode the integer `42` by hand:

```c
static void test_one_integer(void) {
    uint8_t data[] = {
        'M','K','C', 0x01,                          /* header */
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
```

Let's verify the byte encoding of `42`. In big-endian int64: the value `42` is `0x2A`, so the 8-byte encoding is `00 00 00 00 00 00 00 2A`. This is exactly what `DataOutputStream.writeLong(42)` produces on the Kotlin side. If you're unsure, open a Kotlin REPL: `42.toString(16)` gives `"2a"`.

**Test 4: Negative integer.** Two's complement must survive the crossing. This matches `testSerializeNegativeInteger` from the Kotlin side:

```c
static void test_negative_integer(void) {
    uint8_t data[] = {
        'M','K','C', 0x01,
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
```

Where does `FF FF FF FF FF FF FF D6` come from? Two's complement: `-42` is `~42 + 1`. `42` in binary is `00101010`. Flip all bits: `11010101`. Add 1: `11010110` = `0xD6`. The leading bytes are all `0xFF` because the sign bit propagates. This is identical on both sides — Java/Kotlin's `Long` and C's `int64_t` both use two's complement.

**Test 5: Two constants with instructions.** The full "1 + 2" case — our 34-byte file from Checkpoint 2:

```c
static void test_two_constants_with_instructions(void) {
    uint8_t data[] = {
        'M','K','C', 0x01,                          /* header: 4 bytes */
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
    assert(sizeof(data) == 34);

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
```

The `assert(sizeof(data) == 34)` line is a nice trick — it verifies at the C level the same 34-byte prediction you made in Checkpoint 2 and validated in Kotlin's Checkpoint 4. Three independent confirmations: hand calculation, Kotlin serializer, C test data. If they all agree, the format is correct.

**Test 6: Truncated file.** What happens when the file ends mid-read? The reader must fail gracefully, not crash or read garbage:

```c
static void test_truncated_constants(void) {
    uint8_t data[] = {
        'M','K','C', 0x01,
        0x00, 0x01,          /* claims 1 constant... */
        TAG_INTEGER,
        0x00, 0x00           /* ...but only 2 of 8 payload bytes */
    };
    MkcBytecode bc;
    assert(mkc_read(write_tmp(data, sizeof(data)), &bc) != 0);
    printf("  PASS  test_truncated_constants\n");
}
```

This is the kind of test you can't easily write on the Kotlin side — there, `DataOutputStream` always writes complete data. But the C reader must handle *any* input, including malformed files. This test verifies that the `read_exact` wrapper catches the short read and `goto fail` cleans up properly.

### The Test Runner

C doesn't have a test runner. We build one in three lines:

```c
int main(void) {
    printf("Running mkc tests...\n");
    test_bad_magic();
    test_empty_program();
    test_one_integer();
    test_negative_integer();
    test_two_constants_with_instructions();
    test_truncated_constants();
    printf("All tests passed.\n");
    return 0;
}
```

If any `assert` fails, the program aborts immediately — you'll see which assertion on which line failed. If all pass, you get a clean "All tests passed." This is the entire test infrastructure. No framework, no annotations, no XML configuration. Just functions that abort on failure.

Compare this with Kotlin's JUnit: there, the framework discovers test methods via `@Test`, runs each one independently, catches exceptions, and reports results. C's approach is manual but transparent — there's no magic, and you can see exactly what happens. The tradeoff: if `test_one_integer` fails, the tests after it don't run. In practice, this is fine for a small test suite. Fix the first failure, re-run, repeat.

### C Primer: `printf` Format Specifiers

C's `printf` uses **format specifiers** — placeholders in a string that get replaced with values. This is very different from Kotlin's string templates (`"value = $x"`). In C, the type of each value *must* match its specifier, or you get garbage output (or worse, undefined behavior):

| Specifier | Meaning | Kotlin equivalent |
|-----------|---------|-------------------|
| `%u` | unsigned integer | `"$x"` where x is `UInt` |
| `%d` | signed integer | `"$x"` where x is `Int` |
| `%lld` | signed long long (64-bit) | `"$x"` where x is `Long` |
| `%02x` | hexadecimal, zero-padded to 2 digits | `"%02x".format(x)` |
| `%s` | string (`char*`) | `"$s"` where s is `String` |
| `\n` | newline | `"\n"` (same) |

### C Primer: `&bc` — The Address-Of Operator

In `mkc_read(write_tmp(...), &bc)`, the `&` takes the **address** of the local variable `bc` and passes it as a pointer. This lets `mkc_read` fill in the struct directly. Without `&`, C would copy the struct into the function, and any modifications would be lost when the function returns (since structs are value types). Passing a pointer is like passing by reference in Kotlin — the function can modify the original.

> **🏁 Checkpoint 9 — Write the tests yourself (TDD style):**
>
> Build `test_mkc.c` **incrementally**, one test at a time:
>
> 1. Start with just `test_bad_magic` and `main`. Compile, run, see `PASS`.
> 2. Add `test_empty_program`. This is where you first construct valid `.mkc` bytes by hand. Count the bytes — should be 10.
> 3. Add `test_one_integer`. Encode `42` as big-endian int64 yourself. If you get it wrong, the assert will tell you.
> 4. Add `test_negative_integer`. Work out the two's complement of `-42` by hand before writing the bytes.
> 5. Add `test_two_constants_with_instructions`. Verify `sizeof(data) == 34`.
> 6. Add `test_truncated_constants`. This tests the error path — make sure the reader doesn't crash.
>
> At each step: `make clean && make && ./test_mkc`. If any assert fires, you have a bug — either in your test data (wrong bytes) or in `mkc_read`. Both are worth finding.

Build with a simple Makefile:

```makefile
CC      = cc
CFLAGS  = -Wall -Wextra -std=c11 -g

all: test_mkc

test_mkc: test_mkc.o mkc.o
	$(CC) $(CFLAGS) -o $@ $^

clean:
	rm -f *.o test_mkc

.PHONY: all clean
```

### C Primer: The Makefile

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

C compilation happens in two stages: **compile** (`.c` → `.o` object file) and **link** (`.o` files → executable). The Makefile handles both. `make` is smart enough to only recompile files that changed — if you edit `vm.c` but not `mkc.c`, only `vm.o` gets rebuilt.

> **🏁 Checkpoint 10 — Compile and verify the C side builds cleanly:**
> ```
> $ cd src/main/c/vm
> $ make clean && make
> $ ./test_mkc
> Running mkc tests...
>   PASS  test_bad_magic
>   PASS  test_empty_program
>   PASS  test_one_integer
>   PASS  test_negative_integer
>   PASS  test_two_constants_with_instructions
>   PASS  test_truncated_constants
> All tests passed.
> ```
> You should see no warnings from `make` and no assertion failures from `./test_mkc`. If you get warnings, fix them — `-Wall -Wextra` is there to help you. If an assert fires, read the file and line number it prints — that's your failing test.

## 1.7 — End-to-End: Across the Bridge

The TDD tests proved that `mkc_read` correctly parses hand-crafted byte arrays. But there's one thing they *don't* prove: that the Kotlin serializer and the C reader agree on the format. The tests on each side were written by the same person looking at the same spec — what if both sides have the same misunderstanding?

The end-to-end test closes that gap. We compile real Monkey code with the Kotlin compiler, serialize it to a `.mkc` file, and read it with the C reader. If the bytes that Kotlin writes are the same bytes that C expects, the bridge is proven.

On the Kotlin side, add a quick main function (or a test) that writes the file:

```kotlin
val input = "1 + 2"
val lexer = Lexer(input)
val parser = Parser(lexer)
val program = parser.parseProgram()

val compiler = Compiler()
compiler.compile(program)

FileOutputStream("test.mkc").use { fos ->
    compiler.byteCode().writeTo(fos)
}
```

Then verify on the C side. For this one-time check, we'll add a small `dump_mkc` tool — a separate program that reads a `.mkc` file and prints its contents for human inspection:

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

### C Primer: `fprintf(stderr, ...)` vs `printf`

`printf(...)` writes to **stdout** (standard output). `fprintf(stderr, ...)` writes to **stderr** (standard error). Error messages go to `stderr` so they don't get mixed into the program's actual output — if you pipe `./dump_mkc file.mkc > output.txt`, the errors still appear on your terminal while the normal output goes to the file. In Kotlin, this is `System.err.println()` vs `println()`.

### C Primer: `main(int argc, char **argv)`

Every C program starts at `main`. `argc` is the argument count, and `argv` is the argument vector — an array of strings. `argv[0]` is the program name itself, `argv[1]` is the first argument, etc. So `argc != 2` means "the user didn't provide exactly one argument."

`char **argv` reads as "pointer to pointer to char" — an array of C strings, where each C string is itself a `char*` (pointer to a sequence of characters terminated by a `'\0'` byte). In Kotlin terms, it's `Array<String>`.

Add a target to the Makefile for the dump tool:

```makefile
dump_mkc: dump_mkc.o mkc.o
	$(CC) $(CFLAGS) -o $@ $^
```

Then run the end-to-end:

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

The bridge works.

> **🏁 Checkpoint 11 — The final test:** Do the end-to-end yourself:
> 1. Write a Kotlin `main()` (or test) that compiles `1 + 2` and writes `test.mkc`
> 2. Run it: `./gradlew run` (or execute your test)
> 3. Verify the file size: `ls -la test.mkc` — should be **34 bytes** (your Checkpoint 2 prediction)
> 4. Hexdump it: `xxd test.mkc` — you should be able to identify every byte against the format table from section 1.2
> 5. Build and run the dump tool: `make dump_mkc && ./dump_mkc ../../../../test.mkc`
> 6. Verify the output shows constants `1` and `2`, and 6 bytes of instructions
>
> If all six steps pass, **congratulations — the bridge is built.** You wrote a binary file format, a serializer, and a deserializer in two different languages, and they agree on every byte.

## 1.8 — What's Next

We now have the infrastructure to get bytecode from the Kotlin compiler to the C VM. But `vm.h` and `vm.c` are still empty. The actual VM — the stack, the instruction pointer, the dispatch loop — is the subject of the next chapter, where we'll follow the book's `VM` struct and `Run()` method, translated into C.

We'll also come back to the serializer when we add new constant types. Adding strings will require a `TAG_STRING` with a length-prefixed payload. Adding compiled functions will require a `TAG_FUNCTION` containing its own instruction slice and metadata. Each time, the pattern is the same: add a tag, define the payload layout, update the Kotlin `when` and the C `switch`. The tagged-union format makes this mechanical.

But for now, constants and instructions can cross the bridge. That's enough to power on the machine.
