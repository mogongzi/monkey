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
