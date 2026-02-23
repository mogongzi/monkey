# Writing an Interpreter & Compiler in Kotlin from scratch (Handcrafted)

## Build and Run

Option 1: Gradle (dev loop)

```bash
./gradlew build
./gradlew run --console=plain
./gradlew run --console=plain --args="--lexer"  # Start REPL in lexer-only mode
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
```

## Phases

### Part 1: Interpreter

- [x] **Lexer** — Tokenize source code into tokens
- [x] **Parser** — Build Abstract Syntax Tree (AST) from tokens
- [x] **AST** — Define node types for expressions and statements
- [ ] **Evaluator** — Execute the AST (tree-walking interpreter)
- [x] **REPL** — Interactive read-eval-print loop

### Part 2: Compiler (future)

- [ ] **Compiler** — Compile AST to bytecode
- [ ] **Virtual Machine** — Execute bytecode

## Language Features (Monkey)

- [ ] Variable bindings (`let x = 5;`, `let name = "hello";`)
- [x] Integers and booleans (`42`, `true`)
- [x] Arithmetic expressions (`1 + 2 * 3`, `-5 + 10`)
- [x] If/else expressions (`if (x > 5) { 1 }`, `if (x) { x } else { 0 }`)
- [x] Return statements (`return 10;`, `return add(1, 2);`)
- [x] Error handling (type mismatches, unknown operators)
- [ ] Strings (`"hello world"`, `"hello" + " " + "world"`)
- [ ] Built-in functions (`len("hello")`, `puts("hi")`)
- [ ] First-class functions and closures (`let add = fn(x, y) { x + y };`, `fn(x) { fn(y) { x + y } }`)
- [ ] Arrays and hash maps (`[1, 2, 3]`, `{"key": "value"}`)
