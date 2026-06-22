# Writing an Interpreter & Compiler in Kotlin from scratch (Handcrafted)

## The Monkey Language

Monkey is a small, expressive programming language designed for learning how interpreters and compilers work. It supports **integers**, **booleans**, **strings**, **arrays**, **hashmaps**, **first-class functions with closures**, and a **macro system**.

### Variables and expressions

```monkey
let age = 25;
let name = "Monkey";
let result = 10 * (20 / 2) - 5 + 1;
```

### Control flow

`if/else` is an expression — it returns a value:

```monkey
let max = if (a > b) { a } else { b };
```

### Functions and closures

Functions are first-class values. They can be assigned to variables, passed as arguments, and returned from other functions:

```monkey
let add = fn(x, y) { x + y };
add(1, 2); // 3

let newAdder = fn(x) {
  fn(y) { x + y };
};
let addTwo = newAdder(2);
addTwo(3); // 5
```

Closures capture their surrounding environment. Here a counter function returns two closures that share the same `count` variable:

```monkey
let counter = fn() {
  let count = 0;
  let increment = fn() {
    let count = count + 1;
    count;
  };
  increment;
};
let myCounter = counter();
myCounter(); // 1
myCounter(); // 2
myCounter(); // 3
```

### Strings

```monkey
let greeting = "Hello" + " " + "World";
len(greeting); // 11
```

### Arrays

```monkey
let arr = [1, 2, 3, 4, 5];
arr[0];          // 1
len(arr);        // 5
first(arr);      // 1
last(arr);       // 5
rest(arr);       // [2, 3, 4, 5]
push(arr, 6);   // [1, 2, 3, 4, 5, 6]
```

### Hashmaps

```monkey
let people = {"name": "Monkey", "age": 25};
people["name"]; // "Monkey"
```

### Higher-order functions

```monkey
let map = fn(arr, f) {
  if (len(arr) == 0) {
    []
  } else {
    push(map(rest(arr), f), f(first(arr)));
  }
};
map([1, 2, 3], fn(x) { x * 2 }); // [2, 4, 6]
```

### Macros

Monkey supports AST-level macros with `quote` and `unquote`:

```monkey
let unless = macro(condition, body) {
  quote(if (!(unquote(condition))) {
    unquote(body);
  });
};
unless(false, puts("this runs!"));
```

### Closure example

```monkey
let makeGreeter = fn(greeting) {
  fn(name) {
    greeting + ", " + name + "!";
  };
};

let sayHello = makeGreeter("Hello");
sayHello("Monkey"); // "Hello, Monkey!"
```

### Recursion example

```monkey
let factorial = fn(n) {
  if (n == 0) {
    1
  } else {
    n * factorial(n - 1)
  }
};

factorial(5); // 120
```

## Build and Run

Option 1: Gradle (dev loop)

```bash
./gradlew build
./gradlew run --console=plain
./gradlew run --console=plain --args="--lexer"    # Start REPL in lexer-only mode
./gradlew run --console=plain --args="--parser"   # Start REPL in parser-only mode
```

Option 2: Installable app distribution (zip/tar + launch script)

```bash
./gradlew installDist   # or: ./gradlew distZip
./build/install/monkey/bin/monkey
```

Option 3: GraalVM native image (native binary)

This is a common approach for distributing CLIs without requiring users to install a JVM.
It requires additional setup (GraalVM + the Gradle plugin `org.graalvm.buildtools.native`).
Once configured, the flow typically looks like:

```bash
./gradlew nativeCompile
./build/native/nativeCompile/monkey
```

## Test

```bash
./gradlew test
./gradlew generateFixtures   # Generate .mkc bytecode fixtures for C VM tests
```

## Phases

### Part 1: Interpreter (in Kotlin)

- [x] **Lexer** — Tokenize source code into tokens
- [x] **Parser** — Build Abstract Syntax Tree (AST) from tokens
- [x] **AST** — Define node types for expressions and statements
- [x] **Evaluator** — Execute the AST (tree-walking interpreter)
- [x] **REPL** — Interactive read-eval-print loop with JLine (syntax highlighting, multi-line input, auto-indentation)
- [x] **Parser Debug Mode** — Print AST as a tree structure in real-time during parsing (`--parser` flag)
- [x] **Extending the Interpreter** — String, built-in functions, array, and hashmap
- [x] **Macro System** — Quote/unquote, macro definitions, and AST-level macro expansion

![Monkey Interpreter](screenshots/monkey_interpreter.png)

### Part 2: Compiler + VM (Kotlin front-end, C back-end)

> **Approach:** Reuse the existing Kotlin lexer/parser/AST and write the **compiler** in Kotlin (it's just an AST visitor that emits bytecode). Serialize the bytecode to a binary format. Then write **only the VM** in C — a tight bytecode dispatch loop where C shines. This mirrors how real systems work (e.g., `javac` produces `.class` files, the JVM executes them).

- [x] **Bytecode format** — Define opcodes, operand encoding, and serialization format (the contract between Kotlin and C)
- [x] **Compiler** (Kotlin) — Walk the AST and emit bytecode
- [x] **BytecodeWriter** (Kotlin) — Write bytecode to a binary file/stream
- [x] **Virtual Machine** (C) — Read and execute bytecode
- [ ] **Tail-call optimization (TCO)** — Optimize tail-recursive calls (e.g., `return f(...)`) with a trampoline/loop to avoid JVM stack overflow

## Language Features (Monkey)

- [x] Variable bindings (`let x = 5;`, `let name = "hello";`)
- [x] Integers and booleans (`42`, `true`)
- [x] Arithmetic expressions (`1 + 2 * 3`, `-5 + 10`)
- [x] If/else expressions (`if (x > 5) { 1 }`, `if (x) { x } else { 0 }`)
- [x] Return statements (`return 10;`, `return add(1, 2);`)
- [x] Error handling (type mismatches, unknown operators)
- [x] String (`"hello world"`, `"hello" + " " + "world"`)
- [x] Built-in functions (`len("hello")`, `len([1,2])`, `now()`)
- [x] First-class functions and closures (`let add = fn(x, y) { x + y };`, `fn(x) { fn(y) { x + y } }`)
- [x] Array and index expressions (`[1, 2, 3]`, `arr[0]`)
- [x] Hashmap (`{"key": "value"}`)
- [x] Built-in functions (`puts`, `first`, `last`, `rest`, `push`)
- [x] Macros (`let unless = macro(condition, body) { quote(if (!(unquote(condition))) { unquote(body) }); };`)
