# Learning C — Notes from the Monkey VM

## Why `uint16_t i` instead of `int i` in a loop counter

Context — this loop in `src/main/c/mkc.c`:

```c
for (uint16_t i = 0; i < out->num_constants; i++) {
    ...
}
```

`i` is being compared against `out->num_constants`, which is itself a `uint16_t`. The loop bound comes from the `.mkc` file format, where the constant pool count is serialized as a 16-bit unsigned integer (max 65535). So `num_constants` is declared `uint16_t` to match the wire format exactly.

If you wrote `int i`, two things happen:

1. **Signed/unsigned comparison warning.** `i < out->num_constants` mixes `int` (signed) and `uint16_t`. On most platforms `uint16_t` promotes to `int` and it's fine — but on a system where `int` is 16 bits, `uint16_t` would promote to `unsigned int` and you'd get `-Wsign-compare`.

2. **Type-intent mismatch.** Using `int` says "this counter could be negative or huge." Using `uint16_t` says "this counter has exactly the same range as the thing it's iterating over." It's self-documenting: a reader instantly knows the upper bound is ≤ 65535.

**The deeper principle: in C, integer types encode *intent*, not just storage.** Choosing `uint16_t` here tells future readers (and the compiler) "this index lives in the same domain as the file-format field." It's the C equivalent of using a domain-specific type in Kotlin instead of `Int` everywhere.

### A footgun to remember

`uint16_t` has a subtle trap — if `num_constants` were ever `65535` and you wrote `i <= num_constants`, the loop would never terminate (overflow wraps to 0). With `<` it's safe. Always prefer `<` over `<=` when the upper bound sits at the type's max.

---

## How Go and Kotlin handle the same situation

### Go: pragmatic, but loose

```go
for i := uint16(0); i < out.NumConstants; i++ {
    ...
}
```

Go *has* `uint16`, `uint32`, etc., and the standard library uses them in binary formats (`encoding/binary` returns exact-width types). **But** Go's idiomatic loop counter is almost always `int`:

```go
for i := 0; i < int(out.NumConstants); i++ { ... }
```

Why? Go forbids implicit numeric conversion — `int` and `uint16` cannot be compared without an explicit cast. So Go programmers tend to **convert at the boundary** (when reading the file) and then use `int` everywhere internally. The exact-width type lives only at the serialization layer.

The tradeoff: you lose the "self-documenting range" property the C version had, but you gain uniformity — every loop in your codebase looks the same.

### Kotlin: the type system pushes you even harder

Kotlin has `UShort` (the equivalent of `uint16_t`), but it's still marked `@ExperimentalUnsignedTypes` in many contexts, and **the language actively discourages mixing signed and unsigned**. There's no implicit promotion at all — not even between `Int` and `Long`. You must write `.toInt()`, `.toLong()`, `.toUShort()` explicitly.

In practice, a Kotlin compiler reading the same `.mkc` file would look like:

```kotlin
val numConstants = stream.readUnsignedShort()  // returns Int (0..65535)
for (i in 0 until numConstants) { ... }
```

Notice: `readUnsignedShort()` returns **`Int`**, not `UShort`. The JVM doesn't have unsigned types natively, so the standard idiom is "read the unsigned 16-bit value into a wider signed type that can hold it without ambiguity." The 16-bit-ness is enforced by the *reader function*, not the variable type.

This is the JVM heritage: Java made the deliberate choice to omit unsigned integers entirely, and Kotlin inherited that pragmatism.

### The conceptual takeaway

Three different answers to "how do you encode the range of a value":

| Language | Mechanism | Philosophy |
|---|---|---|
| **C** | Pick the exact-width type and live with the footguns | Types document hardware/wire reality |
| **Go** | Exact widths at I/O boundaries, `int` everywhere else | Convert once, then forget |
| **Kotlin/JVM** | Use a wider signed type; enforce range in the *reader* | Trust functions, not type names |

For a **bytecode VM written in C**, the C approach is exactly right: you're close to the file format, close to the hardware, and the type *is* the documentation. If the same VM were written in Kotlin, you'd never see `UShort` in the dispatch loop — you'd read the value once and treat it as `Int` forever.

This is also why the **compiler half** of this project (Kotlin) emits bytecode through a `BytecodeWriter` abstraction, while the **VM half** (C) reads raw bytes with `read_exact` + manual width tracking. Each side uses the idioms its language rewards.

---

## What `extern` really means in C

Context — the function prototypes in `src/main/c/vm/mkc.h`:

```c
int mkc_read(FILE *f, MkcBytecode *out);
void mkc_free(MkcBytecode *bc);
```

and the private helpers in `src/main/c/vm/mkc.c`:

```c
static int read_exact(FILE *f, uint8_t *buf, size_t n) { ... }
static uint16_t read_u16(const uint8_t *buf) { ... }
```

The header *declares*, the source *defines*, and `static` *hides*. `extern` is the keyword that makes all three behaviors make sense — even when it's not written explicitly.

### The C compilation model (the reason `extern` exists)

C builds a program in two distinct phases:

1. **Compile** — each `.c` file becomes a `.o` independently. The compiler processing `test_mkc.c` has **zero knowledge** of what's inside `mkc.c`; it only sees what `#include "mkc.h"` drags in.
2. **Link** — the linker stitches all the `.o` files together and resolves cross-file references.

This split creates a problem: how does `test_mkc.c` call `mkc_read` when the compiler can't see the body? The answer is a **declaration** — a promise that a name exists with a given type, with the actual *definition* living somewhere else. `extern` is how you make that promise without accidentally creating a second definition.

### Declaration vs definition — the one rule that matters

|  | Declaration | Definition |
|---|---|---|
| Answers | "does this name exist, and what's its type?" | "where is the actual storage/body?" |
| Needed by | the **compiler** | the **linker** |
| How many allowed | many (one per file that uses the name) | **exactly one** across the whole program |
| Variable example | `extern int counter;` | `int counter = 0;` |
| Function example | `int foo(int);` | `int foo(int x) { return x + 1; }` |

**Rule:** any name can have many declarations, but exactly one definition. `extern` lets you declare without defining.

### The classic example — sharing a global across files

Naive attempt to share a `verbose` flag:

```c
// config.h
int verbose = 0;   // DEFINITION — allocates storage
```

Every `.c` file that includes `config.h` now contains `int verbose = 0;`, so the linker sees multiple definitions and refuses to link. The fix:

```c
// config.h
extern int verbose;   // declaration only — no storage

// config.c
int verbose = 0;      // the single definition
```

Now every file gets a declaration; exactly one file provides the definition; the linker resolves all reads/writes to that single memory location.

### Functions are implicitly `extern`

These two are identical:

```c
int mkc_read(FILE *f, MkcBytecode *out);         // implicitly extern
extern int mkc_read(FILE *f, MkcBytecode *out);  // explicitly extern
```

A function declaration *can't* be a definition — there's no body — so the compiler treats it as a declaration automatically. That's why `mkc.h` doesn't bother writing `extern` on its prototypes.

The asymmetry: **variables default to definitions, functions default to declarations.** `extern` is load-bearing for variables, ceremonial for functions.

### The counterpart: `static` (internal linkage)

`static` at file scope is the *opposite* of `extern` — it narrows a name's visibility to just this one `.c` file. Other translation units can't see it, can't link against it, and can happily define their own `read_exact` without collision. It's C's closest equivalent to "file-private."

The full picture at file scope:

| Written | Linkage | Allocates storage? |
|---|---|---|
| `int x;` | external | yes (tentative definition) |
| `int x = 5;` | external | yes |
| `extern int x;` | external | **no** — declaration only |
| `static int x;` | internal | yes (private to this file) |

### A footgun to remember

Putting a variable *definition* in a header (`int x = 0;` instead of `extern int x;`) compiles fine on its own but explodes at link time the moment a second `.c` file includes the same header. The error message mentions "multiple definition of..." and points at files that never touched the variable directly — they just pulled in the header. If you ever see a linker error you can't explain, check your headers for accidental definitions.

---

## How Go and Kotlin handle separate compilation

### Go: no `extern`, no header files

Go sidesteps the whole issue by **eliminating the programmer-visible compile/link split**. There are no header files — the compiler builds *packages* as whole units, and every `.go` file in the same package can see every top-level name in every other file. Cross-package visibility is controlled by capitalization:

```go
// file: mkc/reader.go
package mkc

func Read(r io.Reader) (*Bytecode, error) { ... }   // exported (capital R)
func readExact(r io.Reader, n int) []byte { ... }   // unexported (lowercase r)
```

`Read` is visible to other packages (≈ C's external linkage); `readExact` is only visible inside `package mkc` (≈ `static`). There's no declaration/definition split because the Go compiler never looks at one file in isolation — it processes the whole package at once and builds its own symbol table. The problem `extern` solves simply doesn't exist.

### Kotlin: module-wide compilation with visibility keywords

Kotlin compiles each file into JVM bytecode (`.class`), and the "linker" is the JVM class loader at runtime. Visibility is controlled by keywords, not linkage specifiers:

```kotlin
// file: mkc/Reader.kt
package mkc

fun read(stream: InputStream): Bytecode { ... }            // public by default
internal fun readExact(stream: InputStream, n: Int): ByteArray { ... }
private fun readU16(buf: ByteArray, offset: Int): Int { ... }
```

- `public` ≈ C's external linkage (visible everywhere).
- `internal` ≈ visible within the same Gradle *module* — no direct C analogue; coarser than `static`, finer than `extern`.
- `private` (at top level) ≈ C's `static` — visible only within the same file.

Kotlin never needs forward declarations at all, because the compiler reads all files in a module before generating bytecode. It's the Go model with finer-grained visibility tiers.

### The conceptual takeaway

Three different answers to "how do I tell the compiler about a name defined elsewhere?":

| Language | Mechanism | Philosophy |
|---|---|---|
| **C** | Explicit `extern` declarations in `.h`, definitions in one `.c` | You manage symbol visibility by hand |
| **Go** | Whole-package compilation; capitalization controls exports | The compiler sees everything in a package at once |
| **Kotlin/JVM** | Module-wide compilation; `public` / `internal` / `private` | Layered visibility tiers, no linker-level plumbing |

`extern` feels archaic because it's a direct exposure of the 1970s **separate compilation** model — a model designed for machines that couldn't hold a whole program's symbol table in memory, so each `.c` file had to be compilable in isolation with nothing but a handful of `#include`d promises. Go and Kotlin were designed in an era where compilers can read entire packages/modules at once, so they eliminated programmer-visible declarations entirely.

For a **C VM** this pain is the price of admission — you're building something that must fit the Unix toolchain, link against libc, and be callable from any language that speaks the C ABI via a small, stable header. The `.h`/`.c` split isn't obsolete; it's the reason your VM can be embedded anywhere a C compiler exists.

### One-line takeaway

> **`extern` lets you tell the compiler a name exists without defining it, so the linker can resolve every use of that name to a single definition living in exactly one translation unit.**
