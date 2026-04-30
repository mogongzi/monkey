# Study Notes: Bytecode, Arrays, and Encoding

## 1. Bytecode operand is an index, not a value

`OpConstant` does **not** carry the integer value in its operand. It carries an **index** into the constant pool — a separate table the VM owns. The real value lives there.

```
              instructions                  constants
              ────────────                  ─────────
ip → [OpConstant][0x00][0x05] ─────┐       [0]: Integer(7)
     [OpConstant][0x00][0x06]      │       [1]: String("hi")
     ...                           │       [2]: Integer(99)
                                   │       [3]: Integer(-1)
                                   │       [4]: Integer(0)
                                   └─────→ [5]: Integer(42)   ← the real value
                                           [6]: String("ok")
```

The dispatch is a two-step lookup:

```c
uint32_t idx = read_u16(&vm->bc->instructions[ip + 1]); // 1. decode the index
vm->stack[vm->sp++] = vm->bc->constants[idx];           // 2. fetch the value
```

### Why this indirection exists

1. **Compactness.** Reusing constant `1` a hundred times costs one entry in the pool + one hundred 2-byte indices, instead of one hundred 8-byte inlined values.
2. **Type uniformity in the instruction stream.** Operands are always small integers (index / jump offset / local slot). The dispatch loop never decodes heterogeneous payloads — tag decoding happened once at load time in `mkc_read`.
3. **Separation of responsibilities.**
   - Compile time (Kotlin): decides constants, assigns indices, serializes the pool with `TAG_*` bytes.
   - Run time (C): holds the pool as `MkcConstant constants[]`, instructions reference it by index.
4. **Late binding via tables.** The same pattern generalizes:
   - `OpGetGlobal idx` → `globals[idx]`
   - `OpGetLocal idx`  → `frame->locals[idx]`
   - `OpJump offset`   → `ip = offset`

   Every opcode is "a small integer that indexes into some table the VM owns." Stack-based vs. register-based VMs differ mostly in *how many* such tables there are.

---

## 2. `read_u16` reads **2 bytes**, not 4

The `16` in `u16` means 16 bits = 2 bytes. Only `buf[0]` and `buf[1]` are touched.

```c
return (uint16_t)((buf[0]) << 8 | buf[1]);
//                  ^^^^^^         ^^^^^^
//                  byte 0         byte 1
//                  (high)         (low)
```

Given bytes `[01] [00] [05]` starting at `ip`:

```
buf[0] = 0x00  →  << 8  →  0x0000
buf[1] = 0x05  →  as-is →  0x0005
                       OR  0x0005   (= 5)
```

Hence `ip += 2` — two operand bytes consumed.

Assigning `uint16_t` into `uint32_t idx` is a safe widening; no data is lost.

---

## 3. Hex literals are just integer literals

`0x0005` and `5` are the **same integer** — the compiler sees no difference. The `0x` prefix is a formatting hint for the human reader ("this came from bytes"), not a different type.

```kotlin
val i = 1
val i = 0x1
val i = 0x0001
val i = 0b1
```

```go
i := 0x0001   // identical to i := 1
```

```c
int i = 0x0001;
```

So `array[0x0005]` and `array[5]` are indistinguishable after parsing. The hex form is a VM-coding convention that makes operand values self-documenting.

---

## 4. Contiguous arrays → O(1) random access

Contiguity is the **reason** arbitrary indexing is cheap, not a restriction.

```
address:  1000   1008   1016   1024   1032   1040
          ────   ────   ────   ────   ────   ────
elements: [0]    [1]    [2]    [3]    [4]    [5]
```

The CPU computes the address in one multiply + add:

```
address_of(array[idx]) = base + idx * sizeof(element)
                       = 1000 + 5 * 8 = 1040
```

One formula, any index. That's **random access** — arbitrary position at uniform cost.

A **linked list**, by contrast, is *not* contiguous: reaching node 5 requires walking node 0 → 1 → 2 → 3 → 4 → 5. Contiguity is what separates random access from sequential access.

### The real constraint: bounds

| Language | `array[999]` when length is 10 |
|----------|--------------------------------|
| Kotlin   | throws `ArrayIndexOutOfBoundsException` at runtime |
| Go       | runtime panic: `index out of range` |
| C        | **undefined behavior** — silently reads adjacent memory or segfaults |

C relies on the programmer; Kotlin/Go enforce bounds at runtime.

### Why the VM's index is always valid

There is a **trust contract** between compiler and VM:

- The Kotlin compiler assigns each constant an index in `0..N-1` and only emits `OpConstant k` for valid `k`.
- It writes exactly `N` constants into `.mkc`; the C loader allocates `constants[N]`.
- Therefore every index appearing in a well-formed `.mkc` is in range.

This is why the book's VM does not bounds-check. Production VMs (JVM) *do* validate bytecode at load time because `.class` files can come from untrusted sources.

---

## 5. Encoding vs. in-memory representation

The key mental model: **a serialized format and a runtime array are two layers, connected by a loader.**

```
         ON DISK                          IN MEMORY
         (.mkc file)                      (after loading)
         ─────────────                    ─────────────────

         sequence of bytes      ──────→   contiguous byte buffer
         no random access                 = an array
         must be read sequentially        = O(1) index access
```

- **Encoded format** (`.mkc`): a stream of bytes defined by a spec — magic, version, constant pool count, each constant as `TAG + payload`, instruction count, instruction bytes. At this layer there is no `file[17]`; you walk top-to-bottom with a cursor.
- **In-memory representation** (`uint8_t *instructions` / `[]byte` / `ByteArray`): a contiguous byte buffer — *which is an array* at the hardware level. The language gives you indexing for free.
- **Loader / deserializer** (`mkc_read`): translates from format to structure by advancing a cursor and populating C structs.

### Why this layering matters

A fundamental principle: **separate the wire format from the in-memory structure.**

- **Format** → optimized for compactness, forward/backward compatibility, unambiguous parsing. Tags, length prefixes, varints, checksums.
- **Structure** → optimized for fast execution. Arrays, hash maps, pointers.
- **Loader** → one-time translation cost paid at startup, so the hot path never re-parses.

### Same pattern elsewhere

- **`.class` → loaded JVM classes.** Encoded constant pool with tags and length prefixes becomes an in-memory `ConstantPool` indexed by slot.
- **Protobuf wire → language objects.** Tagged varints on the wire become normal struct fields in memory.
- **ELF / Mach-O → process memory.** File sections with headers become flat, executable pages the CPU addresses directly.

> Bytecode is **encoded as a sequence of bytes** in the file and **decoded into an array** in memory. The `.mkc` is a *serialized* array of instructions; `vm->bc->instructions` is that same array *deserialized* into a buffer the VM can random-access.

---

## 6. Go→C `switch` translation gotcha

Go's `switch` does **not** fall through by default. C's **does**.

```c
switch (op) {
case OP_CONSTANT: {
    // ... handle ...
    ip += 2;
    break;           // <- without this, falls into default
}
default:
    fprintf(stderr, "unknown opcode 0x%02x\n", op);
    return;
}
```

Drop `break` and a successful `OP_CONSTANT` would fall into `default`, print "unknown opcode," and return. Keep both `break` and the `default` — the default is a safety net for malformed bytecode.

Go requires the explicit keyword `fallthrough` to get C's behavior. This is a classic Go→C porting bug.

---

## 7. Inline vs. helper — the `read_u16` choice

```c
// Inline form
uint32_t idx = (vm->bc->instructions[ip + 1] << 8) | vm->bc->instructions[ip + 2];

// Helper form
uint32_t idx = read_u16(&vm->bc->instructions[ip + 1]);
```

- The helper form wins on **consistency** — the `.mkc` reader already uses `read_u16`, so one place defines "big-endian uint16 decode."
- Performance difference is largely theoretical: any C compiler at `-O2` will inline a tiny function like `read_u16`. Verify with `objdump -d`.
- If you ever want to *guarantee* inlining (hot dispatch loop, no optimizer dependency), put it in the header as `static inline`:

```c
// bytes.h
static inline uint16_t read_u16(const uint8_t *buf) {
    return ((uint16_t)buf[0] << 8) | (uint16_t)buf[1];
}
```

This is how Lua, CPython, and most tight interpreters expose byte-decode helpers.

---

## 8. The three-place contract for a new opcode

Adding a new opcode always touches three coordinated places:

1. **Kotlin `code/`** — opcode constant + `Make` entry (operand widths).
2. **Kotlin `BytecodeWriter` / C `mkc_read`** — only if it introduces a new *constant type* (new `TAG_*`).
3. **C `vm_run`** — new `case` in the dispatch switch, reading operands with matching widths.

If Kotlin writes 2 bytes and C reads 4, the IP falls out of sync with instruction boundaries and everything after becomes garbage. `OpConstant` is the minimal example of all three pieces cooperating; every future opcode is a variation on this theme.

---

## 9. Pratt parsing, expression trees, and tree walking

Three concepts to keep separate:

1. **Pratt `prefix` / `infix` is about parser roles, not traversal order.**

   In Pratt parsing, a prefix parse function answers:

   > Can this token start an expression?

   Examples:

   - `5` -> `IntegerLiteral`
   - `foo` -> `Identifier`
   - `-x` -> `PrefixExpression`
   - `if (...) { ... }` -> `IfExpression`
   - `fn(...) { ... }` -> `FunctionLiteral`

   An infix parse function answers:

   > Can this token extend the expression on its left?

   Examples:

   - `1 + 2` -> `InfixExpression`
   - `add(1, 2)` -> `CallExpression`
   - `arr[0]` -> `IndexExpression`

2. **Source code is a linear token stream, often with infix expression syntax.**

   Source code is not a tree yet. The lexer produces tokens, then the parser builds the AST.

   ```text
   1 + 2 * 3
   ```

   Pratt parsing turns that linear input into the correct tree:

   ```text
       +
      / \
     1   *
        / \
       2   3
   ```

   The precedence table decides that `*` binds tighter than `+`, so the AST becomes:

   ```text
   1 + (2 * 3)
   ```

3. **A tree-walking interpreter is postorder-like for expressions.**

   Once the AST exists, the evaluator walks it. For normal operators, it evaluates children first, then applies the parent operation.

   For:

   ```text
   1 + 2 * 3
   ```

   evaluation order is roughly:

   ```text
   1
   2
   3
   *
   +
   ```

   That is postorder-like: left child, right child, parent.

   The caveat is that a real interpreter is not only a blind tree traversal. Some nodes have special semantics:

   - `if` evaluates only the selected branch.
   - `let` evaluates the right-hand side, then mutates the environment.
   - `return` can stop evaluation early.
   - function calls evaluate callee and arguments, then apply the function.

   So the clean mental model is:

   ```text
   Parser builds the tree.
   Evaluator/compiler walks the tree.
   Pratt parsing controls how infix-looking source becomes the right tree shape.
   ```
