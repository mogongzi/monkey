# Compiler & VM Theory — Learning Notes

Notes from studying Thorsten Ball's *Writing a Compiler in Go*, implemented in Kotlin (compiler) and C (VM).

---

## 1. Operational Semantics: The Theoretical Relationship Between Tree-Walking Interpreters and Bytecode VMs

### What Operational Semantics Is

Operational semantics defines a language by specifying *how programs execute* as a set of formal rules. The rules look intimidating but are essentially typed pseudocode for an evaluator. Two flavors matter.

### Big-Step (Natural) Semantics: `e ⇓ v`

Read as "expression `e` evaluates to value `v`." Rules are written as inference:

```
   e1 ⇓ n1    e2 ⇓ n2
  ─────────────────────
   e1 + e2 ⇓ n1 + n2
```

*If* the premises above the line hold, *then* the conclusion below holds. The rule collapses a whole sub-evaluation into one conceptual step.

**A tree-walking interpreter is literally an implementation of big-step rules.** Look at Monkey's evaluator:

```kotlin
is InfixExpression -> {
    val left  = eval(node.left, env)   // premise: e1 ⇓ n1
    val right = eval(node.right, env)  // premise: e2 ⇓ n2
    applyOp(node.op, left, right)      // conclusion: e1+e2 ⇓ n1+n2
}
```

Recursive calls *are* the premises, the return *is* the conclusion. Kotlin's call stack does the derivation-tree bookkeeping for free. Every tree-walker is a big-step rule set in disguise.

### Small-Step (Structural) Semantics: `e → e'`

Read as "expression `e` takes *one small step* to `e'`." For `+`:

```
     e1 → e1'                e2 → e2'
  ──────────────        ──────────────────        ──────────────────
  e1+e2 → e1'+e2         n1+e2 → n1+e2'           n1+n2 → (n1 plus n2)
```

Reduce the left, then the right, then apply the primitive. A program runs as a sequence of states until it hits a value:

```
e0 → e1 → e2 → ... → v
```

**A bytecode VM is closer to small-step semantics.** Its state is `(code, pc, stack)`; each fetch-decode-execute cycle is *one* transition:

```
(code, pc, stack) → (code, pc', stack')
```

The main loop is a small-step transition function.

### The Equivalence Theorem — The Punchline

For any well-behaved language you can prove:

> `e ⇓ v`   iff   `e →* v`

(where `→*` = zero or more small steps). Both semantics describe the *same* language — two viewpoints on one meaning. One collapses the derivation into a single tree, the other unfolds it into a sequence of machine states.

If the tree-walker implements the big-step rules correctly and the VM implements the small-step transitions correctly, **both will give the same answer for every program**, provably so. That equivalence is why we can trust compilation didn't change our language.

### What Compilation Is Really Doing

Compiling AST → bytecode is **making implicit control explicit**.

- In the tree-walker, "what to do after this sub-expression finishes" lives *implicitly* in Kotlin's call stack. When `eval(node.left)` returns, Kotlin's runtime *already knows* to continue with `node.right`.
- In the VM, there is no host call stack to lean on. So the compiler *emits* the control flow as a list of opcodes, and the program counter + operand stack take the role the host runtime used to play.

The compiler is **defunctionalizing the evaluator** — turning recursive function calls into data (opcodes) that a dumb loop can traverse. A *correct* compiler is a theorem: for every source `e`, running the compiled bytecode produces the same value as `e ⇓ v`. This is called **semantic preservation**, and it's the central property of any compiler. Projects like CompCert formally *prove* it.

### Concrete Example: `1 + 2 * 3`

**Big-step derivation (what the tree-walker traces via recursion):**

```
      ────  ────  ────
      1⇓1   2⇓2   3⇓3
            ───────────
             2*3 ⇓ 6
      ────────────────
         1 + 2*3 ⇓ 7
```

**Small-step trace (what the VM actually does):**

```
pc=0  stack=[]         OpConstant 1
pc=2  stack=[1]        OpConstant 2
pc=4  stack=[1,2]      OpConstant 3
pc=6  stack=[1,2,3]    OpMul
pc=7  stack=[1,6]      OpAdd
pc=8  stack=[7]        ← done
```

Five small steps, each a transition over `(pc, stack)`. The operand stack *is* the partial-evaluation state that previously lived in recursive call frames. Same expression, same answer, different shapes of the same semantics.

### Why This Perspective Pays Off

1. **Demystifies the compiler.** Compilation isn't magic; it's a translation between two forms of operational semantics for one language.
2. **Tells you what to test.** Since both executors implement the same semantics, any Monkey program should give identical results through both — the basis of golden tests.
3. **Previews what comes next.** Register machines, SSA, JIT, native code generation — all more of the same: translate to a lower abstract machine, preserve semantics.

---

## 2. Abstract Machines: Stack vs. Register

An **abstract machine** is an idealized computing model — a specification of *state* plus *transitions over that state*. It isn't tied to any real CPU; it exists to give a language a clean execution target. The JVM, the CLR, Python's bytecode interpreter, WebAssembly, and Monkey's VM are all abstract machines.

### Stack Machine

**State:** an operand stack + a frame pointer for locals + a program counter.
**Instructions:** take implicit operands from the top of the stack; push results back.

```
; a + b * c
OpGetLocal a      ; stack: [a]
OpGetLocal b      ; stack: [a, b]
OpGetLocal c      ; stack: [a, b, c]
OpMul             ; stack: [a, b*c]
OpAdd             ; stack: [a + b*c]
```

Opcodes are *tiny* because they don't need to name operands — the stack knows.

### Register Machine

**State:** a fixed (or unbounded) set of named registers + PC + call stack.
**Instructions:** explicitly name source and destination registers.

```
; a + b * c
Load  r1, a
Load  r2, b
Load  r3, c
Mul   r4, r2, r3    ; r4 = b * c
Add   r5, r1, r4    ; r5 = a + b*c
```

Opcodes are bigger (must encode register numbers) but there are fewer of them.

### The Trade-Off

| Concern | Stack | Register |
| --- | --- | --- |
| Compiler complexity | trivial (post-order walk) | hard (register allocation) |
| Code density | very compact | less compact per op |
| Instructions per operation | many (push, push, op) | few |
| Dispatch overhead | higher (more opcodes fetched) | lower |
| Map to real CPU | indirect (via JIT) | direct |
| Verification | easy (local stack effects) | harder |

The Lua 5.0 paper ("The Implementation of Lua 5.0", Ierusalimschy et al.) showed a careful register VM beats a comparable stack VM by ~25% *interpreted*, because fewer instructions means less dispatch — and dispatch is the dominant cost in an interpreter.

### Who Picks Which?

- **Stack-based**: JVM, .NET CLR, Python (CPython), Ruby YARV, WebAssembly, Monkey.
- **Register-based**: Lua, Dalvik/ART (Android), V8 Ignition, LuaJIT.

Most production systems blur the line: a stack interpreter with a register-based JIT (JVM), or a register interpreter with a register JIT (V8).

### Why Monkey Picks Stack — The Deep Reason

It's not just "stack is simpler" (though it is). The real reason is a structural correspondence:

> **For expression-only AST fragments without control flow, post-order traversal naturally produces correct stack-machine code: children leave their results on the operand stack, then the parent consumes them. No register allocation. No scratch management.**

An AST is a tree. Post-order visits children before parents. The operand stack is exactly the place to park pending sub-results until the parent is ready to consume them. So the compiler is just:

```kotlin
fun compile(node: Node) {
    when (node) {
        is IntegerLiteral -> emit(OpConstant, addConstant(node.value))
        is InfixExpression -> {
            compile(node.left)    // leaves result on stack
            compile(node.right)   // leaves result on stack
            emit(opFor(node.op))  // consumes two, pushes one
        }
    }
}
```

That's the entire infix-expression compiler. Compare with a register machine, where you'd need register allocation (linear scan or graph coloring) — a whole subsystem with nothing to do with the Monkey language.

### Stack Effects

Every opcode has a **stack effect** — how many values it pops and pushes:

| Opcode | Pops | Pushes | Net |
| --- | --- | --- | --- |
| `OpConstant` | 0 | 1 | +1 |
| `OpAdd`, `OpMul`, `OpSub`, `OpDiv` | 2 | 1 | −1 |
| `OpPop` | 1 | 0 | −1 |
| `OpJumpNotTruthy` | 1 | 0 | −1 |
| `OpJump` | 0 | 0 | 0 |

You can *statically* compute the stack depth at every program point. If it ever goes negative, or if different control-flow paths disagree on the expected stack shape at a join, the bytecode is malformed. This is the same basic principle the JVM verifier uses, though the JVM also tracks types and local-variable state across control-flow merges.

The post-order compile rule plus stack effects gives an invariant: *after compiling any expression node, the stack is exactly one taller than before*. That invariant is why `compile(left); compile(right); emit(OpAdd)` is guaranteed correct.

### "Stack Machine" vs "Call Stack"

Stack machines have *two* stacks:

1. **Operand stack** — holds intermediate values during expression evaluation. *This* is what makes it a stack machine.
2. **Call stack** (frame stack) — holds activation records for function calls: locals, return address, base pointer.

Register machines also have a call stack. The distinguishing feature of a stack machine is the *operand* stack.

### How This Connects Back to Semantics

The abstract-machine choice is *which* low-level language the VM interprets:

- Stack machine state: `(code, pc, operand_stack, frame)`
- Register machine state: `(code, pc, registers, frame)`

Both are valid targets for semantic-preserving compilation from the source AST. The equivalence theorem still holds: tree-walker ⇔ stack VM ⇔ register VM, all compute the same values for the same programs.

---

## 3. Intermediate Representations: The Pipeline Inside Every Compiler

### What an IR Actually Is

An intermediate representation is a data structure that holds the program *in between* source and final execution. The crucial thing:

> **An IR is a language designed for *transformation*, not for humans and not for execution.**

Source code is convenient to write. Machine code is efficient to run. *Neither* is convenient to *analyze or rewrite*. An IR is a middle form chosen specifically to make some class of analysis or transformation cheap and correct.

Monkey's AST and bytecode are both IRs:

| IR | Good at | Bad at |
| --- | --- | --- |
| AST | preserving source structure, pattern-matching on expressions | fast execution, control-flow analysis |
| Bytecode | fast dispatch, compact storage, portable execution | structural reasoning, optimization |

### Why Real Compilers Use Several IRs

Different optimizations need different representations:

- **Inlining** is easy on a *tree* (splice in the callee's body).
- **Constant propagation** is easy on **three-address code in SSA form** (every variable has exactly one definition, so you follow the def).
- **Register allocation** is easy on a **linear low-level IR** (walk the instruction sequence and compute live ranges).
- **Instruction scheduling** needs something close to the **target ISA**.

So real compilers *lower* — translate from one IR to a lower one, with each pass operating at the right level:

```
Source
  │  parse
  ▼
AST                ← structure preserved, types resolved
  │  lower
  ▼
High-level IR      ← simplified, desugared, still typed
  │  lower
  ▼
Mid-level IR       ← 3-address, SSA, the optimizer's playground
  │  lower
  ▼
Low-level IR       ← near-machine, register-allocated, scheduled
  │  emit
  ▼
Machine code / bytecode
```

Each downward arrow throws away high-level information *in exchange for* proximity to the target. You can't un-lower.

### Three-Address Code

Each instruction has at most two sources and one destination: `t3 = t1 + t2`.

Compare with what we already know:

| Style | Example | Operand naming |
| --- | --- | --- |
| **Zero-address** (stack, Monkey) | `OpAdd` | both operands implicit on stack |
| **Two-address** (x86) | `add eax, ebx` | destination = one of the sources |
| **Three-address** | `add t3, t1, t2` | destination is separate |

For `(a + b) * (c - d)`:

```
t1 = a + b
t2 = c - d
t3 = t1 * t2
```

Clean, uniform, every instruction looks the same. The temporaries have unlimited supply. This matches register-machine ISAs and is trivial to translate to real hardware.

### SSA — Static Single Assignment

**Every variable is assigned exactly once in the entire program.** When source code reassigns, you invent a fresh name:

```
// Source                // SSA
x = 10                   x1 = 10
x = x + 5                x2 = x1 + 5
y = x * 2                y1 = x2 * 2
```

Same program, same behavior — but now every name has exactly one definition site.

**"Static"** because it's a property of the *program text* (compile-time), not runtime. A loop body still executes many times dynamically.

**What SSA buys you.** Without SSA, "where does the value of `x` at line 42 come from?" requires data-flow analysis — walking backward through every branch and loop. With SSA, `x42` was defined in exactly one place. Follow the pointer. Done in constant time.

Constant propagation becomes trivial:

```
x1 = 10            ; x1 is the constant 10
x2 = x1 + 5        ; x1 is 10, so x2 is 15 — rewrite
y1 = x2 * 2        ; x2 is 15, so y1 is 30 — rewrite
```

**Phi (φ) nodes** handle merging branches:

```
if (cond):  x1 = 1
else:       x2 = 2
x3 = φ(x1, x2)     ; "x3 is x1 if from then-branch, x2 if from else"
print(x3)
```

The φ node is a pseudo-instruction — a bookkeeping device for the SSA form. At code-generation time it gets resolved into copies at the ends of predecessor blocks.

SSA was invented around 1988–91 by Cytron, Ferrante, Rosen, Wegman, and Zadeck at IBM. **Essentially every modern optimizing compiler uses SSA**: LLVM IR, GIMPLE (GCC), V8 TurboFan, HotSpot C2, Cranelift, Go's SSA backend.

### The "Optimizer's Playground"

The middle-end IR is where almost all interesting optimizations happen. Three-address SSA is the representation optimizers prefer because it's simple to rewrite, easy to analyze, and still abstract enough to transform freely.

The AST is too structured. Machine code is too rigid. Three-address SSA is the sweet spot.

### Worked Example: CSE on SSA

Take `result = (a + b) * (a + b - 1)`.

**Naive three-address:**

```
t1 = a + b
t2 = a + b          ; same computation again!
t3 = t2 - 1
t4 = t1 * t3
result = t4
```

**Convert to SSA** (fresh names):

```
t1_1 = a + b
t2_1 = a + b
t3_1 = t2_1 - 1
t4_1 = t1_1 * t3_1
result_1 = t4_1
```

**Common-subexpression elimination (CSE).** Hash each right-hand side. `t1_1` and `t2_1` both hash to `(a + b)`. Replace uses of `t2_1` with `t1_1`, delete the redundant definition:

```
t1_1 = a + b
t3_1 = t1_1 - 1
t4_1 = t1_1 * t3_1
result_1 = t4_1
```

One computation saved. Now try doing this on Monkey's stack bytecode:

```
OpGetGlobal a        ┐
OpGetGlobal b        │ first (a+b)
OpAdd                ┘
OpGetGlobal a        ┐
OpGetGlobal b        │ second (a+b) — same!
OpAdd                ┘
OpConstant 1
OpSub
OpMul
```

Much harder — no names to refer to, no way to "reuse" an earlier result without rearranging the whole sequence. **Stack bytecode is great for delivery and execution but terrible for transformation.** This is the core reason middle-end IRs exist.

### Linear vs Graph IRs

- **Linear IRs**: lists of instructions in basic blocks with explicit jumps. LLVM IR, GIMPLE, Monkey bytecode.
- **Graph IRs** ("sea of nodes"): program as a graph where nodes are operations and edges are data/control dependencies. Order is only constrained where it matters. V8 TurboFan and GraalVM use this.

### Real Compilers, Mapped

**GCC:**
```
Source → AST → GENERIC → GIMPLE (SSA) → RTL → Machine code
```
- GENERIC: language-agnostic tree IR.
- GIMPLE: 3-address SSA, the main optimizer workbench.
- RTL (Register Transfer Language): near-machine, for register allocation and scheduling.

**LLVM:**
```
Source → (frontend AST, e.g. Clang's) → LLVM IR → ... → LLVM IR → Machine code
```
- LLVM IR: typed, SSA, three-address, machine-independent. Every optimization pass takes LLVM IR in and emits LLVM IR out. Clang, Rust, Swift, Julia, Zig all emit LLVM IR; LLVM targets x86, ARM, RISC-V, Wasm. A true **narrow waist**.

**JVM toolchain:**
```
Java source → AST → class file (stack bytecode) → [JIT: HIR → LIR → machine code]
```
- Class file: the *delivery* IR — portable, verified, stable.
- HotSpot's C2 JIT builds its own sea-of-nodes HIR, then a linear LIR.

**V8 (JavaScript):**
```
Source → AST → Ignition bytecode (register-based) → [TurboFan: sea-of-nodes → machine code]
```
- Ignition: register-based bytecode interpreter (baseline).
- Hot code gets promoted to TurboFan (sea-of-nodes graph IR, aggressive optimization).

### Where Monkey Fits

```
Source → Tokens → AST → Bytecode → Stack-VM execution
```

- AST is the high-level IR.
- Bytecode is the low-level IR.
- **No middle end.** No SSA, no data-flow analysis, no optimization passes. The compiler goes straight from AST to bytecode in a single post-order walk.

This is *exactly* a real compiler pipeline — just with the middle portion removed. To add optimizations, you'd insert more IRs between AST and bytecode.

### The Narrow-Waist Principle

> **A good IR is a narrow waist: many things can translate *to* it, many things can translate *from* it.**

```
   Kotlin compiler  ─┐              ┌─  JVM
   C# compiler      ─┤              ├─  x86 backend
   Scala compiler   ─┤──►  IR  ───►─┤─  ARM backend
   Groovy compiler  ─┤              ├─  RISC-V backend
   ...              ─┘              └─  ...
```

Monkey's bytecode is a tiny narrow waist. The Kotlin compiler and C VM agree on the `.mkc` format and nothing else. You could write a second compiler (different frontend language) that emits Monkey bytecode, and the existing C VM would run it unchanged. The bytecode is the contract; everything else is negotiable.

---

## 4. JIT Compilation: Compiling While the Program Runs

### The One-Sentence Definition

> **A JIT (Just-In-Time) compiler translates hot bytecode into native machine code *at runtime*, then redirects execution to the native code so subsequent invocations skip the interpreter entirely.**

### Why Bother — The Interpreter Tax

Each bytecode opcode in Monkey's VM costs a fetch-decode-execute cycle. For a tight loop that increments `i` from 0 to 1,000,000:

```
OpGetGlobal i       ; push i
OpConstant 1        ; push 1
OpAdd               ; pop 2, add, push result
OpSetGlobal i       ; pop, store
OpGetGlobal i       ; push i for comparison
OpConstant 1000000  ; push limit
OpLessThan          ; pop 2, compare, push bool
OpJumpNotTruthy     ; pop, maybe branch
```

Per iteration: 8 bytecode ops, each paying ~15–30 host instructions in the VM loop. Total: ~120–240 host instructions per loop iteration.

A C compiler would compile the same loop to **2 instructions** (`add`, `cmp+jl`). That's a 60–100× gap, essentially *all* interpretation overhead.

JITs close that gap by eliminating the interpreter loop for code that runs often enough to matter.

### What "Hot" Means

**Hot = executed many times.** The Pareto observation:

> A small fraction of the code (inner loops, hot functions, critical paths) accounts for a large fraction of the runtime.

You only benefit from JIT-compiling code that runs often enough to pay back the compilation cost.

**Counter-based profiling:** Every function (or loop header) has a counter. The VM bumps it on each entry. When the counter crosses a threshold, the JIT fires:

```kotlin
fn.invocationCount++
if (fn.invocationCount > JIT_THRESHOLD && fn.compiledCode == null) {
    fn.compiledCode = jit.compile(fn.bytecode)
}
if (fn.compiledCode != null) {
    callNative(fn.compiledCode, args)
} else {
    interpretBytecode(fn.bytecode, args)
}
```

**Sampling:** A timer fires periodically and records the current PC. Over time you build a histogram. Cheaper but noisier; used as a secondary signal.

**Thresholds matter.** HotSpot C1 fires around 1,500 invocations; C2 around 10,000. Too low: waste time compiling code that runs once. Too high: leave performance on the table.

### Tiered Compilation

No single strategy is best. A function that runs 100 times deserves a *cheap* JIT; one that runs a billion times deserves an *expensive* optimizing JIT:

| Tier | What it is | Compile cost | Code quality |
| --- | --- | --- | --- |
| 0 | Interpreter | 0 | baseline (slow) |
| 1 | Baseline JIT (no optimization) | very low | decent |
| 2 | Mid-tier optimizer | moderate | good |
| 3 | Aggressive optimizer | high | excellent |

Every function starts at tier 0. As its counter climbs, it gets *promoted* upward. HotSpot has C1 and C2. V8 has Ignition → Sparkplug → Maglev → TurboFan.

Tiered compilation responds to two conflicting demands: **startup speed** (don't make me wait) and **peak performance** (be fast eventually).

### What's Inside the JIT?

> **An optimizing JIT is structurally similar to a static optimizing compiler. It lifts bytecode into an IR (often CFG/SSA), runs optimization passes, and lowers to machine code. Baseline and template JITs deliberately skip most or all of this pipeline.**

Same broad pipeline as LLVM or HotSpot C2, just run at runtime with a tight time budget and with profile data:

```
Bytecode   →   CFG / SSA IR   →   optimize   →   low-level IR   →   machine code
  ↑                                                                       ↓
(loaded)                                                           (installed into
                                                                    code cache)
```

What *is* different: the JIT has two advantages a static compiler doesn't:

1. **Profile data**: the JIT has watched the program run. It *knows* which branches are taken, which types flow through which variables.
2. **Speculation**: the JIT can compile *under assumptions* that might be wrong, and bail out if they're wrong.

### Speculation and Deoptimization

This is where a JIT earns its performance on dynamic languages. Consider JavaScript:

```js
function add(a, b) { return a + b; }
```

`+` is polymorphic: int addition, float addition, string concatenation, etc. But the JIT has seen 10,000 calls where both arguments were int32. It *speculates*:

```asm
; Specialized add(int32, int32) → int32
cmp    [rdi], int32_tag     ; guard: is a really int32?
jne    deoptimize
cmp    [rsi], int32_tag     ; guard: is b really int32?
jne    deoptimize
mov    eax, [rdi+8]
add    eax, [rsi+8]
ret
```

Three properties make this huge:

1. **Guards** check the assumption cheaply.
2. The **body** is native code — no dispatch, no boxing, just an add instruction.
3. If a guard fails, **deoptimize**: abandon the JIT code mid-execution, reconstruct the interpreter's state, resume interpretation at the equivalent bytecode position.

Deoptimization is surprisingly subtle. The JIT maintains a side table mapping every guard point in machine code back to interpreter state (locals, operand stack, PC). When a guard fails, the runtime materializes the interpreter state from that side table and transfers control back to tier 0.

This **speculate-then-deopt** cycle is the defining feature of modern optimizing JITs.

### On-Stack Replacement (OSR)

A function gets hot *while it's running*. You don't want to wait for the current execution to return — the current call might never return (think `while (true) { ... }`).

OSR detects that a loop inside an interpreted function has gotten hot, pauses execution, materializes the current interpreter state into the layout the JIT code expects, and resumes execution *inside the JIT-compiled version at the equivalent point*.

OSR is used in both directions:
- **Tier-up OSR**: interpreter → JIT mid-function.
- **Deopt OSR**: JIT → interpreter mid-function.

Both require the same mechanism: a mapping between machine-code state and interpreter state at every point where the runtime might switch.

### JIT Styles

- **Method JIT** (HotSpot, V8, CoreCLR): compile whole functions. Dominant design.
- **Trace JIT** (LuaJIT, old PyPy): follow actual execution paths at runtime, compile *that trace* as a linear block with guards at every branch. Great for predictable loops.
- **Template / copy-and-patch JIT** (CPython 3.13's experimental JIT): for each opcode, copy a pre-compiled machine-code snippet into a buffer. No IR, no optimization — just removes dispatch overhead.
- **Baseline JIT** (V8 Sparkplug, JVM C1): simple method JIT with no optimization.
- **Optimizing JIT** (V8 TurboFan, HotSpot C2, Graal): full SSA pipeline.

### What a JIT Would Look Like on Monkey

Minimal sketch:

1. Add a counter to each `CompiledFunction`.
2. Bump on every function entry.
3. On threshold crossing, call a JIT module that reads the function's bytecode and emits native code.
4. Store the native code pointer.
5. On subsequent calls, if native code exists, jump to it; otherwise interpret.

Simplest strategy: **template JIT / copy-and-patch**. For each Monkey opcode, pre-compile a machine-code snippet that does what the VM's interpreter case does but without fetch-and-decode. Concatenate snippets. Patch immediates. No SSA, no optimization — just dispatch elimination. ~3–5× speedup, under 1000 lines of C.

### Where JIT Fits

> **A JIT doesn't replace the interpreter — it collaborates with it.** The interpreter is always the tier-0 safety net. The JIT accelerates code that has proven it's worth optimizing.

*Interpretation is correctness, compilation is speed.* The equivalence theorem applies: interpreter output == JIT output for every program, because the JIT is a semantic-preserving translation from bytecode to machine code, just like the compiler was from AST to bytecode.

Everything composes: tree-walker ⇔ stack VM ⇔ JIT'd native. Same semantics, three shapes.

---

## 5. Garbage Collection: Who Owns the Memory?

### The Fundamental Question

Every GC answers:

> **Which objects can the running program still reach, and which can't it?**

Reachable = **live** (must not be freed). Unreachable = **garbage** (can be reclaimed).

### Roots, Reachability, and the Mutator

The running program (the **mutator**) holds **root pointers** — references not in the heap but in special locations:

- **Globals**: every binding in the VM's globals table.
- **The operand stack**: every value currently pushed.
- **Call frames**: locals in every active function frame.
- **CPU registers**: in a JIT, values in machine registers are roots too.

From roots, the GC traces forward through pointers. The heap is a directed graph; the GC does a graph traversal. An object is live iff there is a chain `root → p1 → p2 → ... → object`.

Note: "live" is about *reachability*, not *usefulness*. The mutator might never touch a reachable object again, but the GC can't prove that. This is why even with GC you can leak memory — by keeping pointers you don't need.

### The Core Algorithms

#### Mark-Sweep (McCarthy, 1960)

```
1. Mark:  traverse from roots, set a "mark bit" on every reachable object.
2. Sweep: walk the entire heap, free every object without a mark bit,
          clear mark bits on survivors.
```

Pros: simple, handles cycles naturally, doesn't move objects.
Cons: fragmentation, pause time proportional to heap size, cache-unfriendly.

#### Copying / Semispace (Cheney, 1970)

Split the heap into two halves: **from-space** and **to-space**. Allocate from from-space by bumping a pointer. When full:

```
1. Trace from roots, copying each reachable object to to-space.
2. Leave a forwarding pointer in the old location.
3. For pointers to already-copied objects, follow forwarding pointer and rewrite.
4. Swap: to-space becomes from-space.
```

Pros: allocation is a pointer bump (as fast as stack allocation!); GC time proportional to *live* data; no fragmentation; excellent cache locality.
Cons: wastes half the heap; all objects move, so pointers must be rewritten.

#### Mark-Compact

Mark like mark-sweep, then slide live objects to one end, rewriting pointers. No wasted half-heap, no fragmentation, but compaction is slow.

#### Reference Counting

Every object stores a count of pointers referencing it. Every pointer assignment bumps old target down, new target up. Count zero → free immediately.

Pros: no stop-the-world pauses; predictable.
Cons: can't reclaim **cycles** without a cycle collector; every pointer write does bookkeeping.

CPython and Swift use reference counting with extra cycle-handling machinery. Most other runtimes use tracing (mark-sweep or copying).

### Generational GC — The Single Biggest Win

> **The generational hypothesis: most objects die young.** Typically 80–99% of newly allocated objects are unreachable by the next GC cycle.

Split the heap into **generations**:

- **Young generation** (nursery): where everything new goes. Collected often, fast.
- **Old generation**: where survivors get promoted.

A **minor GC** collects only young. A **major GC** collects everything.

The **write barrier**: when an old-generation object gets a pointer to a young-generation object, that cross-generational reference must be tracked in a **remembered set** or **card table**. At minor GC time, those are treated as additional roots. Every pointer write pays a small tax — almost always worth it.

Many successful modern collectors are generational: HotSpot's G1, Generational ZGC (JDK 21+), V8's Orinoco, .NET's GC, and OCaml's runtime. But generational design is not universal; Go's runtime is a prominent non-generational counterexample.

### Tri-Color Marking (Concurrent/Incremental GC)

For GC running *concurrently* with the mutator:

- **White**: not yet reached. Presumed dead.
- **Gray**: reached, but children not yet scanned.
- **Black**: reached, and all children scanned.

Marking: start all white, roots gray. Repeatedly pick gray → color black, color its white children gray. When no gray remains, all white is garbage.

Critical invariant:

> **Black objects must never directly point to white objects.**

If they could, the GC would miss a live object. Concurrent GCs use write barriers to maintain this invariant under mutation.

### How the Abstract Machine Choice Shows Up

#### Root Identification

- **Stack VM (Monkey)**: operand stack is a tidy, linear array. Walk from `stack[0]` to `sp-1`. Easy.
- **Register VM**: roots include values in virtual registers. Still manageable.
- **JIT**: roots include machine registers, stack slots, spill locations. *Which* hold pointers varies by PC. Need **stack maps** — per-safe-point side tables saying "at this instruction, RDI and [RBP-16] hold pointers."

Stack maps are **the same side-table technology JIT uses for deoptimization**. One mechanism, two consumers.

#### Safe Points

GC can't collect at any instruction. Mid-computation, pointers can be in temporary registers the GC can't see. **Safe points** are designated locations where the mutator's state is fully described by a stack map (loop back-edges, function calls, allocation sites).

#### Precise vs Conservative

- **Precise GC**: knows exactly which locations hold pointers. Requires compiler cooperation (stack maps). Can move objects.
- **Conservative GC** (Boehm): treats anything that *looks like* a pointer as possibly one. Works in C with zero compiler help. Can't move objects, may keep dead memory alive.

### A Baby GC for Monkey's VM

Simplest working implementation — mark-sweep, ~200 lines of C:

1. **Object header:** type tag, mark bit, `next` pointer:
   ```c
   typedef struct Obj {
       ObjType type;
       bool marked;
       struct Obj *next;   // linked into vm.objects
   } Obj;
   ```

2. **Track allocations:** VM keeps a linked list `vm.objects`.

3. **Mark:** from roots (operand stack, globals, active frames), walk reachable pointers, set `marked = true`. In the closure-based VM, frames point at active `Closure*` objects, not just bare `CompiledFunction*`, so the marker must trace the closure's `free[]` array too.
   ```c
   void mark_roots(VM *vm) {
       for (Value *slot = vm->stack; slot < vm->sp; slot++) mark_value(*slot);
       for (int i = 0; i < vm->frame_count; i++) mark_closure(vm->frames[i].closure);
       mark_table(&vm->globals);
   }
   ```

4. **Sweep:** walk `vm.objects`, free unmarked, clear marks on survivors.

5. **Trigger:** collect when allocation exceeds some threshold.

Upgrades from there:
- **Generational**: young/old split, add write barrier.
- **Copying for young gen**: pointer-bump allocation, collection proportional to live data only.
- **Incremental marking**: tri-color + write barriers.
- **Concurrent marking**: run marker on another thread.

### Connecting Back

- **Semantics**: in a language where GC is unobservable, GC is *invisible* at the level of operational semantics. In real runtimes with things like finalizers or weak references, semantic preservation becomes a property to maintain, not something to assume automatically.
- **Abstract machines**: the machine choice determines the root set and how precisely you can identify it.
- **IRs**: the compiler decides what type/pointer information to preserve across lowering. Precise GC needs that info to survive to machine code, via stack maps.
- **JIT**: stack maps, safe points, and deoptimization's side tables share the same mechanism GC uses for root identification.

---

## 6. Closures and Environments: Where the Semantics/Implementation Gap Is Widest

### The Source of All the Trouble

```
let makeAdder = fn(x) {
    fn(y) { x + y };
};
let add5 = makeAdder(5);
add5(10);  // 15
```

When `add5(10)` runs, it needs `x` — a parameter of `makeAdder`. But `makeAdder` has *returned*. Its frame is gone. Yet the inner function still uses `x`.

This is the **upward funarg problem**: a function value that *outlives the scope that created it*. The solution:

> **A closure is a function value = code + captured environment.**

The pair *closes over* the function's free variables. That's where the name comes from.

### Free Variables, Precisely

Inside a function body, a variable is one of three kinds:

- **Bound**: a parameter or local `let`-binding of this function.
- **Free**: used in the body but neither a parameter nor a local — it comes from an enclosing scope.
- **Global/builtin**: lives at the top level.

Free variables are a purely *syntactic* property computable by walking the AST at compile time. For `fn(y) { x + y }`: bound = `y`, free = `x`.

### Lexical Scoping in Semantic Terms

The reason `x` means "the `x` from `makeAdder`" and not "whatever `x` is in the caller's environment" is **lexical scoping**. In big-step semantics:

```
    env ⊢ (fn (x) e) ⇓ ⟨λx.e, env⟩
```

When a lambda is evaluated, the current environment `env` is captured *into* the resulting value. Application:

```
    env  ⊢ f   ⇓ ⟨λx.e, env_captured⟩     env ⊢ arg ⇓ v
    env_captured [x ↦ v] ⊢ e ⇓ result
    ───────────────────────────────────────────────────
                   env ⊢ f(arg) ⇓ result
```

The body `e` evaluates in `env_captured`, *not* `env`. That single choice distinguishes lexical from dynamic scoping. Every implementation strategy is machinery for this one rule.

### The Tree-Walker Version

In Monkey's tree-walking evaluator, closures are trivial:

```kotlin
class Function(
    val params: List<Identifier>,
    val body: BlockStatement,
    val env: Environment     // ← captured environment
)
```

Creating a function value captures a reference to the current `Environment`. Calling it creates a fresh environment whose parent is the *captured* environment. Variable lookup walks the chain. Kotlin's GC keeps the captured environment alive.

This mirrors the big-step rule one-to-one.

### The VM Version — Where It Gets Interesting

A bytecode VM doesn't have environment objects. It has stack frames, a globals table, and raw stack slots. When a function returns, its frame is popped. Variables are `OpGetLocal 3` — an offset into the frame.

If a closure captured `x` from an enclosing frame, where does `x` live after that frame is gone? Four strategies:

#### Strategy A: Environment Objects on the Heap

Mirror the tree-walker — allocate Environment objects on the heap for every scope, chain them. Correct and easy, but slow. Variable access becomes a heap-chasing traversal. No production VM does this for hot code.

#### Strategy B: Upvalues (Lua, Crafting Interpreters)

A closure carries an array of **upvalues** — small cells referencing captured variables. Each upvalue starts as a pointer *into the live stack frame* and *migrates to the heap* when the frame pops ("closing" the upvalue).

Handles mutable shared state: two closures capturing the same variable share an upvalue, so mutation through one is visible through the other.

#### Strategy C: Closure Conversion (ML, OCaml, Haskell Compilers)

A compile-time transformation: every nested function is rewritten to take its environment as an *extra explicit argument*. Closures become plain structs: `{ code_pointer, env_pointer }`. The whole notion of "nested function" is eliminated from the IR.

More work upfront but produces simpler IR that's easier to optimize.

#### Strategy D: Static Links / Displays (Old-School)

Pascal, Algol, and early Fortran used a *display* (array of frame pointers by lexical depth) or *static links* (each frame points to its lexically enclosing frame). These solve the *addressing* problem: how to find a variable in an enclosing lexical scope. What breaks when closures become first-class is a pure **stack-allocation** strategy for activation records. If frames or environments are heap-allocated, static links still work.

### What the Monkey Book Does

Ball's VM takes a simplified version of Strategy B. Two critical observations:

1. **Monkey has no variable reassignment.** `let count = 0` creates a binding; there's no `count = count + 1` (that would require a reassignment operator). Captured variables can never be mutated after capture.

2. **Capture-by-value is therefore sufficient.** The closure holds a *copy* of each free variable's value at the moment of creation. No upvalue cells, no "closing," no shared mutable state. Just snapshot and go.

The closure object:

```
Closure { fn: CompiledFunction, free: Array<Object> }
```

New opcodes:
- `OpGetFree index` — push `free[index]` onto the operand stack.
- `OpClosure constIndex, numFree` — pop `numFree` values off the stack, wrap them with the compiled function, push the Closure.
- `OpCurrentClosure` — push the currently-executing closure (for recursion).

Compilation of a nested function:

```
; Code that creates the closure:
OpGetLocal 0            ; push first free variable
OpGetLocal 2            ; push second free variable
OpClosure 7, 2          ; wrap compiled-fn-at-constant-7 with top 2 stack values

; Code inside the nested function's body:
OpGetFree 0             ; use captured first free variable
OpGetFree 1             ; use captured second free variable
```

For JavaScript or Python, where captured variables are mutable and shared, you'd need full Strategy B (upvalues).

### The Compiler's Bookkeeping: The Symbol Table

The compiler tracks, at every name resolution, which scope the name lives in:

```kotlin
class SymbolTable(val outer: SymbolTable? = null) {
    val symbols = mutableMapOf<String, Symbol>()
    val freeSymbols = mutableListOf<Symbol>()

    fun resolve(name: String): Symbol? {
        symbols[name]?.let { return it }
        val outerSym = outer?.resolve(name) ?: return null
        if (outerSym.scope == GLOBAL || outerSym.scope == BUILTIN)
            return outerSym                          // passes through unchanged
        return defineFree(outerSym)                  // capture it as a free variable
    }
}
```

The `defineFree` call is the key: when resolving a name found in an enclosing, non-global scope, it:

1. Adds it to the current function's free-variable list.
2. Returns a symbol with scope = `FREE` and an index.
3. The emitter translates references to `OpGetFree index`.

### Two-Phase Compilation Dance

Closures are compiled in two phases:

- **Phase 1**: compile the inner function body, accumulating free variables as a side effect of resolution.
- **Phase 2**: at the outer function, when emitting `OpClosure`, arrange to copy those free variables' current values onto the stack.

Resolving a free variable while compiling the inner body *teaches the compiler what needs to be captured* when the outer code reaches the closure creation point.

### Recursive Closures: `OpCurrentClosure`

```
let fib = fn(n) {
    if (n < 2) { n } else { fib(n-1) + fib(n-2) }
};
```

Inside `fib`'s body, `fib` is used — but the `let` binding hasn't completed yet. The compiler recognizes this as self-reference and emits `OpCurrentClosure` instead of a normal lookup. At runtime, this pushes the currently-executing closure itself onto the stack.

This sidesteps the **knot-tying problem** (create a dummy binding → compile the body → backfill) used by implementations with mutable environment cells.

### Why This Is the Hard Chapter

1. **Two-phase compilation**: resolving free variables in the *inner* function, but emitting the capture code in the *outer* function. Both phases must be held simultaneously.
2. **Three kinds of variable access**: local, global/builtin, and free — each resolved differently, each using a different opcode and runtime mechanism.
3. **Side-effectful resolution**: `resolve()` doesn't just look up — when it finds a non-local, non-global binding, it *creates* a new free symbol. This side effect is how the free-variable list gets built.

Once all three are loaded simultaneously, the rest of the chapter is mechanical.

### Connecting Back

- **Semantics (§1)**: closures directly implement the lexical-scoping rule from big-step semantics. Everything else is machinery.
- **Abstract machine (§2)**: the stack VM's operand stack can't hold captured values after a frame pops — the *whole reason* closures need their own representation.
- **IR (§3)**: closure conversion is a real compiler IR pass in ML-family languages — "eliminate nested functions before lowering further."
- **JIT (§4)**: specializing closures is one of the biggest JIT speedups. Inlining a closure call site eliminates the environment lookup entirely.
- **GC (§5)**: closures are *the* reason object lifetimes don't match lexical lifetimes. Without closures, stack-based memory would suffice. Closures force heap allocation of captured state, which forces a real GC.

---

## Summary: The Complete Frame

The whole story is a layered system where each layer preserves the language's semantics:

1. **Parsing and AST** — building the input to the compiler.
2. **Compilation to bytecode** — semantic-preserving translation from big-step (tree) to small-step (linear) form.
3. **Stack VM** — small-step interpretation of bytecode on a chosen abstract machine.
4. **Constants, globals, locals** — memory model of the VM.
5. **Conditionals and jumps** — making control flow explicit.
6. **Functions and call frames** — stack discipline for the common case.
7. **Closures** — breaking the stack discipline for the hard case.
8. **(Beyond the book)** — optimization (SSA middle-end), GC (memory management), JIT (native compilation at runtime).

> A compiler is a semantic-preserving translator; a VM is a small-step interpreter of the translator's output; optimization happens on middle-end IRs; hot code can be promoted to native; the whole thing composes with automatic memory management; and closures are where all these concerns intersect most visibly.
