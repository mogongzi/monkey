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

---

## Why `case` labels need braces to hold declarations

Context — this compiler warning in `src/main/c/vm/vm.c`:

```
vm.c:38:7: warning: label followed by a declaration is a C23 extension [-Wc23-extensions]
   38 |       uint16_t idx = (vm->bc->instructions[ip + 1] << 8) | ...
      |       ^
```

triggered by a `case` body that declares a variable without wrapping the body in braces:

```c
switch (op) {
case OP_CONSTANT:
    uint16_t idx = read_u16(&vm->bc->instructions[ip + 1]);  // ← warning here
    vm->stack[vm->sp++] = vm->bc->constants[idx];
    ip += 2;
    break;
}
```

The warning is confusingly worded. It's not about the *type* `uint16_t` — it's about the grammatical role of what comes after `case OP_CONSTANT:`.

### The grammar rule (C11 §6.8)

A `case` label is officially a **labeled statement**:

```
labeled-statement:
    case constant-expression : statement
```

So `case OP_CONSTANT:` must be followed by a **statement**, not a declaration. That distinction matters because C's grammar has two top-level categories inside a function body:

| Category | Examples |
|---|---|
| **Statement** | `if (...)`, `while (...)`, `x = 1;`, `{ ... }`, `break;`, `return;` |
| **Declaration** | `uint16_t idx = 0;`, `int x;`, `struct Foo f;` |

Pre-C23, declarations are **not** statements — they're a separate grammar production. C++ merged them decades ago; C23 finally catches up. That's why the warning says "C23 extension" — compilers allow it as a forward-compatibility freebie, but strictly speaking it was illegal in C11.

### Why a switch body normally allows mixing — but a `case` doesn't

The body of a `switch` is a compound statement `{ ... }`. Inside `{ }` the grammar permits a **block-item-list**, where each item is *either* a declaration or a statement:

```
block-item:
    declaration | statement
```

So declarations and statements can intermix freely inside `{ }`. But the moment you write `case X:`, you've entered a labeled-statement, which demands a *single* statement right after the colon. A bare declaration can't satisfy that rule.

### Why `{ ... }` fixes it

A compound statement `{ ... }` is itself classified as a statement (grammar rule `statement: compound-statement`). So wrapping the case body in braces satisfies the labeled-statement rule — and inside the braces, you're back in block-item-list land where declarations are legal:

```c
case OP_CONSTANT: {                                         // label attached to { } — a statement ✓
    uint16_t idx = read_u16(&vm->bc->instructions[ip + 1]); // inside { }, declarations are legal ✓
    vm->stack[vm->sp++] = vm->bc->constants[idx];
    ip += 2;
    break;
}
```

The mental model: **`case X:` is a name tag on exactly one statement.** Without braces, you're tagging a single line — and that line had better grammatically be a statement. With braces, you're tagging a whole block that can contain whatever you want.

### Bonus benefit — per-case scope

Without braces, all case labels share the switch's outer scope. Two cases declaring `uint16_t idx` would collide at the same scope level:

```c
switch (op) {
case OP_CONSTANT:
    uint16_t idx = ...;   // if this were legal
    ...
    break;
case OP_JUMP:
    uint16_t idx = ...;   // error: redeclaration of 'idx' in same scope
    ...
    break;
}
```

With braces, each `idx` lives in its own nested scope. So even once C23 lets you drop the braces for the grammar reason, you'll still want them for this reason. **Per-case braces are almost always what you mean.**

### A footgun to remember

The fall-through semantics of `switch` make brace discipline extra important. A `case` without braces that declares `idx`, forgets `break`, and falls into the next `case` would leave `idx` in scope for the next label's body — creating an invisible dependency between cases. Per-case `{ }` makes that impossible: `idx` is dead the moment control leaves the block.

---

## How Go and Kotlin handle the same situation

### Go: no braces needed, and fall-through is opt-in

```go
switch op {
case OpConstant:
    idx := readU16(ins[ip+1:])   // declaration — no braces required
    vm.stack[vm.sp] = vm.constants[idx]
    vm.sp++
    ip += 2
case OpJump:
    idx := readU16(ins[ip+1:])   // legal — each case has its own implicit scope
    ip = int(idx)
}
```

Go makes two design choices that eliminate C's pain:

1. **Each `case` has an implicit scope.** You don't need `{ }` to declare locals; the language gives you a fresh scope per case automatically. The same name `idx` can appear in every case without collision.
2. **No fall-through by default.** Go's `case` ends at the next `case` — you write explicit `fallthrough` if you want C-style fall-in. That removes the footgun of accidentally using a name from the previous case.

Under the hood, Go still implements `switch` as a jump table just like C, but the surface syntax hides the labeled-statement machinery entirely.

### Kotlin: `when` is an expression, not a statement

```kotlin
val result = when (op) {
    OpConstant -> {
        val idx = readU16(instructions, ip + 1)
        vm.stack[vm.sp++] = vm.constants[idx]
        ip += 2
    }
    OpJump -> {
        val idx = readU16(instructions, ip + 1)
        ip = idx
    }
    else -> error("unknown opcode $op")
}
```

Kotlin's `when` has no `case` keyword, no fall-through, no labeled-statement grammar at all. Each branch is an arrow `->` followed by either a single expression or a block `{ ... }`. Blocks create their own scope automatically — same as any Kotlin block. And because `when` is an *expression*, it can produce a value, which forces exhaustiveness (every branch must return the same type).

There's no footgun equivalent to C's "declaration after label" because there's no label, and no `break` because there's no fall-through. The entire category of bugs is designed out of the language.

### The conceptual takeaway

Three different answers to "how does a multi-way branch handle locals?":

| Language | Branch scope | Fall-through | Where locals live |
|---|---|---|---|
| **C** | Shared outer scope unless you add `{ }` | Default (silent) | Need explicit `{ }` per case for isolation |
| **Go** | Implicit per-case scope | Opt-in via `fallthrough` | Fresh scope per `case`, no ceremony |
| **Kotlin** | Each branch is an expression or block | None — no such concept | Block `{ }` is the only shape; scope comes free |

C's `switch` is really a **computed goto with sugar** — labels + fall-through are a thin syntactic layer over jump instructions. That lets it be fast and predictable, but also exposes the grammar quirk: because `case X:` is literally a label on a statement, the rules for what can follow it are the rules for any labeled statement. Go and Kotlin both treat the multi-way branch as a higher-level construct with its own semantics, hiding the underlying jump table and the scope/declaration issues along with it.

For a **bytecode dispatch loop** this is actually fine — you *want* the C `switch` to be a thin wrapper over a jump table, because that's what makes dispatch fast. You just pay for that performance with an extra pair of braces per case.

### One-line takeaway

> **`case X:` is a label attached to a single statement; wrap the body in `{ ... }` whenever you need to declare locals, both to satisfy the grammar and to give each case its own scope.**

---

## What type is a `#define` macro in a `switch` statement?

Context — this kind of code in `src/main/c/vm/vm.c`:

```c
#define OP_CONSTANT 0x00

uint8_t op = vm->bc->instructions[ip];
switch (op) {
case OP_CONSTANT:
    ...
}
```

Natural question: what's the type of `OP_CONSTANT` here — is it `uint8_t` because `op` is `uint8_t`? Is it `uint32_t`? The answer is neither — it's `int` — and the path to that answer touches three separate layers of C.

### Layer 1: `#define` has no type

`#define OP_CONSTANT 0x00` is handled by the **preprocessor**, which runs *before* the compiler does any type analysis. It's pure textual substitution:

```c
case OP_CONSTANT:
```

becomes

```c
case 0x00:
```

before the compiler ever sees the line. So "what's the type of `OP_CONSTANT`" is the wrong question — the macro itself has no type. The real question is: **what's the type of the literal `0x00`?**

### Layer 2: The type of `0x00` is `int`

C11 §6.4.4.1 spells out the type of an unsuffixed integer constant. For a **hexadecimal** literal, the compiler picks the first type the value fits in, from this ordered list:

```
int → unsigned int → long → unsigned long → long long → unsigned long long
```

`0x00` fits in `int`, so its type is `int` (signed, typically 32 bits on your platform). If you wrote `0x80000000`, `int` couldn't hold it and the type would become `unsigned int`. If you want to force a width, use suffixes: `0x00U`, `0x00L`, `0x00UL`, `0x00LL`, etc.

Note that **decimal** literals follow a different rule — they skip the unsigned types: `int → long → long long`. So `128` is always `int`, but `0x80` is also `int` only because it fits; larger hex values become unsigned first.

### Layer 3: The `switch` applies integer promotion anyway

Even if `op` is declared:

```c
uint8_t op = vm->bc->instructions[ip];
```

the controlling expression of a `switch` undergoes **integer promotion** (C11 §6.3.1.1): any integer type narrower than `int` is promoted to `int` (if `int` can represent all its values) or to `unsigned int` otherwise. `uint8_t` fits in `int`, so at the `switch`, `op` is promoted to `int` before comparison.

The `case` labels are then converted to the same promoted type. So the comparison actually happening inside the dispatch loop is:

```c
(int)op == (int)0x00
```

Three narrow types (`uint8_t op`, the macro `OP_CONSTANT`, and the case label) all collapse into one: `int`.

### The footgun: macros bypass the type system entirely

`#define` has no type, which means it has no type checking. For opcodes this is harmless, but in other contexts it bites:

```c
#define MAX(a, b) ((a) > (b) ? (a) : (b))
int x = MAX(f(), g());   // f() and g() each called TWICE
```

The preprocessor doesn't know `f()` has side effects — it just pastes the text. For function-like behavior with real type checking, prefer `static inline` functions in modern C:

```c
static inline int max_int(int a, int b) { return a > b ? a : b; }
```

For a closed set of constants like opcodes, the safer alternative is an `enum`:

```c
typedef enum {
    OP_CONSTANT = 0x00,
    OP_ADD      = 0x01,
    ...
} Opcode;
```

Enumerators *do* have a type (they're `int` in C), and `-Wswitch` will warn you if your `switch` forgets a case. With `#define`, you lose that safety — the compiler sees naked integer constants and can't tell they belong to a closed set.

---

## How Go and Kotlin handle the same situation

### Go: no preprocessor, constants have real types

Go has no preprocessor at all. The equivalent of `#define OP_CONSTANT 0x00` is:

```go
const OpConstant = 0x00
```

Go constants are **typed at the declaration site**, or *untyped* and inferred at each use. An untyped constant takes on whatever type the surrounding context needs:

```go
var op uint8 = 0x00
switch op {
case OpConstant:  // OpConstant adapts to uint8 in this context
    ...
}
```

For a closed set of opcodes, Go programmers reach for `iota`:

```go
type Opcode uint8
const (
    OpConstant Opcode = iota  // 0
    OpAdd                     // 1
    OpPop                     // 2
)
```

Now `Opcode` is a distinct named type, the compiler tracks it separately from `uint8`, and tools like `go vet` and `exhaustive` give you switch-exhaustiveness checks. No preprocessor ambiguity, no integer-promotion surprises — just a typed constant set.

### Kotlin: enum classes, with compile-time exhaustiveness

Kotlin has no preprocessor either. The rough equivalent of `#define OP_CONSTANT 0x00` is:

```kotlin
const val OP_CONSTANT: Byte = 0x00
```

`const val` evaluates at compile time, but it's still fully typed — there's no way to write a constant whose type depends on context the way C's integer literals do. For opcodes, Kotlin's idiomatic choice is an `enum class`:

```kotlin
enum class Opcode(val code: Byte) {
    CONSTANT(0x00),
    ADD(0x01),
    POP(0x02);
}
```

And the `when` expression gives you **compile-time exhaustiveness checking** — if you add a new opcode and forget to handle it, the compiler refuses to build. That's a guarantee C's `switch` + `#define` fundamentally cannot provide, because `#define` lives outside the type system.

### The conceptual takeaway

Three different answers to "how do I name a constant integer":

| Language | Mechanism | Type story |
|---|---|---|
| **C** | `#define`: preprocessor text substitution | No type; literal's type (`int` for hex that fits) wins |
| **Go** | `const`: typed or context-typed constants | Untyped literals adapt; `iota` + named types for closed sets |
| **Kotlin** | `const val`, `enum class` | Fully typed at declaration; exhaustiveness checking free |

The C approach has one great virtue: **zero runtime cost and zero dependency on the type system.** `#define OP_CONSTANT 0x00` produces *identical* assembly to writing `0x00` inline. For a bytecode dispatch loop that runs millions of times per second, that matters.

But you pay for it with footguns: macros bypass type checking, integer-literal rules surprise newcomers, and `switch` exhaustiveness is not enforceable. Go and Kotlin made the opposite trade: slightly heavier constructs, but the type system does the bookkeeping for you.

For a **C VM** the `#define` + `switch` + `uint8_t op` combination is idiomatic: it's fast, it matches how the opcode byte lives in the `.mkc` file, and the promotion machinery that collapses everything to `int` is exactly what the CPU wants anyway — registers are int-width, and comparison instructions operate on int-width operands.

### One-line takeaway

> **A `#define` has no type — it's textual substitution. The expression that survives substitution has the type of the underlying literal (`int` for hex values that fit), and inside a `switch`, everything narrower is promoted to `int` before comparison.**
