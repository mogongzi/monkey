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

---

## 10. Flat → Tree: parsing is the same shape as "build a tree from an array"

Turning a linear input into a tree is one fundamental operation. What differs across problems isn't the operation — it's *where the rule for the tree's shape comes from.*

### Three flavors, distinguished by source of structure

| Problem | Input | Source of structure | Difficulty |
|---------|-------|---------------------|------------|
| Sorted `int[]` → balanced BST | values only | **Algorithm** (pick middle, recurse on halves) | trivial |
| Level-order `Integer[]` (with nulls) → tree | values + position markers | **Input** (positions and nulls *are* the structure) | trivial |
| Tokens → AST | tokens | **Grammar + precedence** (inferred from context) | hard (this is parsing) |

All three are deserializers — flat → tree. The LeetCode "build a tree from an array" exercise is a tiny parser whose grammar is so trivial you can write it without ever saying the word *grammar*. Real parsing is the same shape, but with a non-trivial grammar.

### Why lists don't have this problem

`arrayToListNode` needs no rule. Each new element has exactly one place to go: after the previous one. Input order *is* the structure.

A tree breaks that. For each element you must answer *left or right? whose child?* The input alone cannot tell you — you need a rule. That gap is the entire job of a parser. A parser is the apparatus that turns a linear input into a hierarchical structure when the structure isn't trivially position-based.

### Same input, different rule, different tree

Run two algorithms on the same flat input `{1, 2, 3, 4, 5}`:

- **`sortedArrayToBST`** — rule: *middle is root, recurse on halves*

  ```text
       3
      / \
     2   5
    /   /
    1   4
  ```

  Balanced BST; in-order traversal yields the input.

- **`buildTree`** — rule: *BFS level-order, nulls = absent*

  ```text
        1
       / \
      2   3
     / \
    4   5
  ```

  Not a BST. Not balanced. The shape comes straight from the input layout.

Same flat input, two different trees, because **the rule for interpreting it differs**. The rule decides the structure; the input is just material.

### Recursion vs. iteration is a side effect

Control flow follows the algorithm shape, not the other way around:

| Algorithm | Natural fit |
|-----------|-------------|
| Divide-and-conquer (split a range, do halves) | Recursion |
| BFS (process node, enqueue children for later) | Queue + loop |
| Recursive-descent grammar (Pratt: prefix/infix call each other) | Recursion |

Either form can be rewritten in the other style — it's just awkward. The interesting axis is "where does structure come from?", not "is it recursive?"

### The dual lives all over this project

```
                 flat                              tree
                  │                                 │
  tokens ─────────┼──── parser ────────────────────→│  AST        (hard: grammar + precedence)
                  │                                 │
                  │←─── compiler (AST visitor) ─────│  bytecode   (tree → flat: lowering)
                  │                                 │
  .mkc bytes ─────┴──── VM dispatch ────────────────┘  (stays flat — that's the *point* of bytecode)
```

- **Parser**: the hard end. Tokens carry no positional structure; the grammar invents the tree.
- **Compiler**: tree → flat (the inverse). The AST visitor decides emission order so a stack VM produces the right value.
- **`mkc_read`**: trivial flat → flat with framing. Positions in the file *are* the indices; no grammar needed.
- **VM dispatch**: never reconstructs a tree. The whole reason for bytecode is to flatten the tree once, ahead of time, so the runtime is a tight `switch` over opcodes — no pointer-chasing children, no dynamic dispatch on node types.

> The recursive shape — *walk input, maintain small state, build a node, recurse/loop* — is identical across building a BST from a sorted array, reconstructing a tree from a level-order array, and parsing tokens into an AST. Only the rule changes. Once you see flat → tree as one operation with a configurable rule, "LeetCode tree problems" and "writing a parser" stop looking like different activities.

---

## 11. Compilers are LeetCode in disguise

The pattern from §10 generalizes beyond parsing. Most compiler subsystems are dressed-up versions of standard algorithm exercises:

| Compiler subsystem | Algorithm exercise |
|--------------------|--------------------|
| AST traversal (visitor passes, evaluator, code emission) | Tree problems (DFS, postorder, in-order) |
| Register allocation | Graph coloring |
| Dependency analysis (build order, instruction scheduling) | Topological sort |
| Constant folding, common subexpression elimination | DP / memoization |
| Symbol tables, constant pools, string interning | Hash map exercises |
| Reachability, dead-code elimination | Graph traversal (BFS/DFS) |
| Loop detection in control-flow graphs | Cycle detection in graphs |

> The interview-grind framing — "do 500 LeetCode problems" — is the wrong reason to learn these patterns. The right reason: they are the canonical shapes of solutions to recurring problems in computing, and a compiler is a domain where five or six of them collide in a single codebase. Without the vocabulary, reading compiler code (yours, the book's, or AI-generated) is wading through fog. With it, every subsystem becomes "oh, this is just *that* again."

---

## 12. How math relates to compiler theory

Compiler theory is not a branch of programming practice that happens to use a little math. It is a direct application of several mathematical fields, each of which gives a specific compiler subsystem its theoretical backbone. The compiler isn't "inspired by" these fields — it *is* an executable instance of them.

### The five pillars

**1. Formal language theory** — the bedrock of lexing and parsing.

- **Regular languages** + **finite automata** → lexing. A tokenizer is a DFA in disguise: it consumes characters, transitions between states, and emits tokens at accepting states. By Kleene's theorem, regular expressions, regular grammars, and finite automata are all equivalent — three notations for the same class of languages.
- **Context-free grammars** + **pushdown automata** → parsing. Monkey's grammar (`expression = expression "+" expression`) is context-free; recognizing it requires a stack, which is exactly what recursive-descent and Pratt parsers use implicitly via the call stack.
- The **Chomsky hierarchy** (regular ⊂ context-free ⊂ context-sensitive ⊂ recursively enumerable) tells you *what kind of machine* you need to recognize each class of language. This is the deep reason lexers and parsers are separate phases — they sit at different levels of the hierarchy, and conflating them costs both clarity and efficiency.

**2. Tree and graph theory** — the structures the compiler manipulates.

- ASTs are trees; the choice of traversal order (pre-, in-, post-order) determines evaluation semantics. Postorder for expressions is not a stylistic preference — it's what makes a stack VM work.
- Later compiler phases use graphs heavily: control-flow graphs for analysis, dominator trees for SSA construction, and **register allocation as graph coloring** (NP-complete in the general case, hence the heuristics in real compilers like Chaitin-Briggs).

**3. Type theory and lambda calculus** — the formal core of "what is a program."

- Functions, scoping, and closures (which Monkey supports) trace directly back to Church's λ-calculus. Variable capture, α-conversion, and β-reduction are not academic curiosities — they're what your evaluator implements.
- Type systems are formal logics. The **Curry–Howard correspondence** says programs *are* proofs and types *are* propositions. This is why dependently typed languages (Idris, Agda, Lean) can serve as proof assistants.

**4. Lattice theory and fixed-point theorems** — the math of static analysis.

- Optimizations like constant folding, dead-code elimination, and abstract interpretation are formulated as fixed-point computations over lattices. The **Knaster–Tarski theorem** guarantees that monotone functions on complete lattices have least fixed points — which is what a dataflow analysis is computing when it iterates to convergence.
- You won't hit this in Monkey, but it's where industrial compiler optimization lives.

**5. Algebra and formal semantics** — the math of program meaning.

- **Operational semantics** describes execution as a transition relation between states. Your VM dispatch loop *is* small-step operational semantics, made executable.
- **Denotational semantics** maps programs to mathematical objects (typically domains in domain theory).
- **Axiomatic semantics** (Hoare logic) describes programs via pre- and post-conditions — the foundation of program verification.
- Monoids and semirings appear in parsing algorithms (CYK, Earley) and in the algebra of regular expressions.

### Where Monkey sits today

Your current build exercises the first two pillars heavily and a slice of the fifth:

```
╭─────────────────────╮     ╭─────────────────────────────────────╮
│ Lexer (Kotlin)      │ ──→ │ Pillar 1: regular languages / DFA   │
╰─────────────────────╯     ╰─────────────────────────────────────╯
╭─────────────────────╮     ╭─────────────────────────────────────╮
│ Parser (Pratt)      │ ──→ │ Pillar 1: context-free grammar      │
╰─────────────────────╯     ╰─────────────────────────────────────╯
╭─────────────────────╮     ╭─────────────────────────────────────╮
│ Compiler (visitor)  │ ──→ │ Pillar 2: tree traversal            │
╰─────────────────────╯     ╰─────────────────────────────────────╯
╭─────────────────────╮     ╭─────────────────────────────────────╮
│ VM dispatch (C)     │ ──→ │ Pillar 5: operational semantics     │
╰─────────────────────╯     ╰─────────────────────────────────────╯
```

The compiler→VM split you're building is a concrete instance of operational semantics: the bytecode is an intermediate language with its own formally definable transition rules, and `vm_run` is the interpreter for those rules.

### Why this framing matters

Each pillar tells you something the code alone cannot:

- **What is possible.** The Chomsky hierarchy tells you why you cannot parse balanced parentheses with a regex — it's not a tooling limitation, it's a theorem.
- **What is hard.** Register allocation being NP-complete is *why* every production compiler uses heuristics rather than searching for the optimal coloring.
- **What is correct.** Operational semantics gives you a way to *prove* that your compiler preserves meaning — that the bytecode for `1 + 2` evaluates to the same value as the AST for `1 + 2`.

> Compiler theory is the application layer of formal language theory, graph theory, type theory, lattice theory, and formal semantics. The Monkey project is a small, executable tour of the first two and a taste of the fifth. The same math scales up to LLVM, GHC, and the JVM — only the engineering effort grows.

---

## 13. The call stack on real machines — what a "frame" actually is

Before designing the VM's frame system, it helps to be precise about what "the stack" and "a frame" mean on real hardware. Most of the design freedom we have in our VM exists because real CPUs *don't* have that freedom.

### The stack is just RAM — with hardware assistance

Physically, there is no special "stack chip." The call stack is a region of the process's virtual address space that the OS reserves at startup. What makes it feel special is the hardware that knows about it:

- The CPU has a dedicated **stack pointer register** (`rsp` on x86-64, `sp` on ARM64) that always points to the top of the stack.
- `push`, `pop`, `call`, and `ret` implicitly read/modify the stack pointer.
- The hot top of the stack almost always lives in L1 cache, so accesses are nearly as fast as register access.
- ABIs (System V AMD64, AAPCS, etc.) standardize argument passing, return-address layout, frame-pointer use, alignment, and unwinding info — all relative to `rsp`/`rbp`.

### Stack-pointer conventions: four flavors

"Top of stack" needs disambiguation. There are two axes — two questions about SP — and they combine into four conventions:

| Convention | SP points to… | Grows toward… |
|---|---|---|
| **Full Descending** (x86-64, ARM64) | last item | lower addresses |
| Full Ascending | last item | higher addresses |
| Empty Descending | next free slot | lower addresses |
| Empty Ascending | next free slot | higher addresses |

On x86-64, `push rax` is:

```
rsp = rsp - 8       ; decrement first
[rsp] = rax         ; then store — so rsp now points AT the new value
```

ARM32 actually supports all four via `LDM`/`STM` variants (`FD`, `FA`, `ED`, `EA`); the standard ABI picks Full Descending.

Our VM's `vm->sp` is **empty ascending** — it points to the next free slot and `push` writes then increments. Neither convention is "right"; you just have to be consistent.

### Why `rbp` is named `rbp`

`bp` = **base pointer**. It marks the *base* (stable anchor) of the current frame, in contrast to `sp` which drifts as the function pushes and pops within itself. With `rbp` fixed, the compiler can address locals at constant negative offsets (`[rbp-8]`, `[rbp-16]`) and incoming stack arguments at constant positive offsets (`[rbp+16]`, `[rbp+24]`), regardless of what `rsp` has done in between.

The `r` prefix is just the x86 64-bit naming convention, inherited from a long history:

| ISA | Width | Name |
|---|---|---|
| 8086 | 16-bit | `bp` |
| 80386 | 32-bit | `ebp` (extended) |
| x86-64 | 64-bit | `rbp` (register-extended) |

### What a frame contains, in plain language

When a function is called, the CPU must remember three things for that call:

1. **Where to return to** — the address of the instruction *after* the `call`. This is a snapshot of the **instruction pointer**, not the stack pointer. `call` pushes it; `ret` pops it back into `rip`.
2. **The arguments** the caller passed in.
3. **The local variables** the function declares.

All three are bundled into one chunk of memory called a **frame** (or *activation record*). The frame is not a separate gadget beside the stack — it *is* a slice of the stack, marked off for this one call. Because function calls are perfectly nested (most recently called returns first), a stack — LIFO — is the natural container.

That nesting property is why a *generic* stack data structure earns the definite article and becomes **the** call stack: a stack of anything becomes *the* stack once it's dedicated to frames.

### Why real machines can't easily put frames elsewhere

In principle, RAM is RAM — you could maintain frames in any region. In practice, two things pin you down:

- **`call` and `ret` are hardcoded to use `rsp`.** `call` writes the return address to `[rsp]`; `ret` reads it back. You can't redirect them without giving up those instructions.
- **The ABI** specifies stack-argument layout, the red zone, alignment, and unwinding info at `rsp`/`rbp` offsets. Violate it and you can't call libc, the kernel, or any normally-compiled code.

People do find loopholes:

- **Coroutines / fibers / goroutines** — each task has its own stack region in a separate buffer; the runtime swaps `rsp` on context switch. Many stacks, one active at a time.
- **Intel CET shadow stacks** (2020+) — a *second* hardware-tracked stack just for return addresses, write-protected, to defeat ROP attacks. Intel literally added silicon for a separate stack.
- **CPS-converted Scheme/ML compilers** — abandon `call`/`ret`, allocate activation records on the heap, use indirect jumps. Frames outlive their callers, so a real stack wouldn't work.

The constraint is never physical memory — it's the `call`/`ret` instructions and the ABI built around them.

### Why our VM splits into two stacks

Our VM has neither a hardware `call`/`ret` nor an ABI to honor. That freedom lets us split frame data and operand data into **two separate arrays**:

```diagram
╭──────────────────────────╮       ╭──────────────────────╮
│  vm->stack[STACK_SIZE]   │       │ vm->frames[MAX_...]  │
│  (value stack)           │       │ (frame stack)        │
│                          │       │                      │
│  holds operands:         │       │  holds Frame*:       │
│  - OpConstant pushes     │       │  - one per active    │
│  - OpAdd pops two/pushes │       │    function call     │
│  - locals live here too  │       │  - push on call      │
│                          │       │  - pop on return     │
╰──────────────────────────╯       ╰──────────────────────╯
```

The frame struct itself stays minimal: the compiled function, the `ip`, and a `basePointer` — an index into the value stack marking where this call's locals begin. The locals and arguments don't live *inside* the frame — they live in the value stack, and the frame just *points* to where they start.

A small but common confusion: **constants ≠ locals.** They're different layers entirely:

| | Lives in | Created when | Per-call? |
|---|---|---|---|
| Constant | Constant pool (`vm->constants[]`, loaded from `.mkc`) | Compile time | No — shared |
| Local variable | Value stack slots | Runtime, on each call | Yes — fresh each call |

`5` as a literal becomes an `OpConstant 0` that pushes `vm->constants[0]`. `let x = 5` is a *local variable* that *happens to be initialized from* that constant. The frame references neither directly: it points into the value stack where locals live, and constants are reached via the VM-wide `constants[]` array, independent of any frame.

> On a real CPU, all of this — return address, arguments, locals, spilled temporaries — must coexist in one region tracked by `rsp`, because that's where `call`/`ret` and the ABI force everything. In our VM, there is no such instruction and no such ABI, so we deliberately split frames out into their own array. Simpler to implement, easier to reason about, and the entire reason Ball can say in Chapter 4: *"We can store frames anywhere we like."*

---

## 14. The theory of virtualization — why we can build a machine on top of another

It's worth pausing on the deeper question: why does any of this work at all? Why can a C program pretend to be a Monkey VM, which can in turn pretend to be… anything? The answer isn't a clever engineering trick — it's a direct consequence of what computation fundamentally *is*.

### The foundational principle: Turing universality

In 1936, Alan Turing proved that a single **Universal Turing Machine** can, given the *description* of any other Turing machine on its tape, simulate that machine exactly — step for step, output for output.

Generalized as the **Church–Turing thesis**:

> Any system capable of expressing certain basic operations (read, write, conditional branch, unbounded memory) is **computationally universal** — it can simulate any other such system.

Once you accept that, virtualization stops being mysterious. A C program on x86 can simulate a Monkey VM. The Monkey VM can simulate anything Monkey is expressive enough to describe. You could write a JVM in Monkey, or an x86 emulator in JavaScript (people have). It's all the same trick, applied at different layers.

### The mechanical "how" — encode guest state as host data

To run a "guest machine" on a "host machine," you only need to do one thing:

> Represent every piece of the guest machine's state as data the host can read and modify, and represent the guest's instructions as host code that performs the corresponding state transitions.

Look at exactly what `vm.c` does:

| Guest (Monkey VM) | Host (C / real CPU) |
|---|---|
| Operand stack | `Object* stack[STACK_SIZE]` array |
| Stack pointer | `int sp` variable |
| Instruction pointer | `int ip` field in `Frame` |
| Frame stack | `Frame* frames[MAX_FRAMES]` array |
| Bytecode dispatch | `switch (opcode) { ... }` |
| Constants pool | `Object** constants` array |

There is nothing magical here. The Monkey VM has no *real* registers — it has C variables that *play the role of* registers. It has no *real* memory — it has C arrays that *play the role of* memory. The host has more than enough power to represent and update the guest's state.

### It's turtles all the way down

The most underappreciated fact: **every layer of a real computer is already a virtual machine** for the layer above it. Virtualization isn't something you *add* on top — it's how the whole stack already works.

```diagram
╭──────────────────────────────────╮
│  Monkey source program           │   ← runs on
├──────────────────────────────────┤
│  Monkey VM (our C code)          │   ← runs on
├──────────────────────────────────┤
│  C runtime + standard library    │   ← runs on
├──────────────────────────────────┤
│  OS processes & syscalls         │   ← runs on
├──────────────────────────────────┤
│  ARM64 / x86-64 ISA              │   ← runs on
├──────────────────────────────────┤
│  CPU microarchitecture (µops)    │   ← runs on
├──────────────────────────────────┤
│  Logic gates                     │   ← runs on
├──────────────────────────────────┤
│  Transistors & physics           │
╰──────────────────────────────────╯
```

Each layer **defines an abstract machine** — a set of primitives and a model of state — and **implements it using the primitives of the layer below**. The x86 ISA you program against is itself a virtualization: modern Intel CPUs internally decode x86 into completely different *micro-operations* and execute those. The "x86 machine" you think you're running on hasn't physically existed since the Pentium Pro in 1995. You've been running on a VM the whole time.

So "why can we build a machine on top of another machine?" inverts: **there is no bottom**. Every machine humans build is built on top of another. Our Monkey VM is just one more layer.

### Two flavors of virtualization — don't conflate them

- **Language VMs** (JVM, .NET CLR, V8, our Monkey VM): the "machine" is *invented* to be virtualized — its ISA is chosen for portability, safety, and ease of implementation. Stack-based, garbage-collected, with high-level opcodes like `OpCall`. No real chip has this ISA.

- **System VMs / emulators** (VMware, KVM, QEMU, DOSBox): the "machine" is a *real* one being mimicked in software (or with hardware assistance like Intel VT-x). The guest OS thinks it has real hardware.

Different goals, same underlying principle.

### What you give up, and what you gain

- **Cost.** Each guest instruction takes many host instructions to dispatch and execute. A naive switch-based VM is typically 10–100× slower than native code. JITs (V8, HotSpot, LuaJIT) reclaim most of this by compiling hot guest code to host code at runtime — at which point the "VM" is gradually erasing itself.

- **Gain.** Portability (same `.mkc` runs on any host with the VM), safety (the VM mediates every operation), observability (you can inspect every instruction), and decoupling of language design from hardware. None of these are possible if you compile straight to a real ISA.

> The theoretical answer: **Turing universality + layered abstraction.** Any sufficiently powerful machine can pretend to be any other machine by encoding the other machine's state as data and its instructions as behavior. We're doing it; the CPU underneath is doing it; the OS in the middle is doing it. The whole computing stack is virtualization, recursively applied — and our Monkey VM is just the next layer up.
