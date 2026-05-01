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
