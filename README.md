# Writing an Interpreter & Compiler in Kotlin from scratch

## Build and Run

Option 1: Gradle (dev loop)

```bash
./gradlew build
./gradlew run --console=plain
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

## Phases

### Part 1: Interpreter

- [x] **Lexer** — Tokenize source code into tokens
- [ ] **Parser** — Build Abstract Syntax Tree (AST) from tokens
- [ ] **AST** — Define node types for expressions and statements
- [ ] **Evaluator** — Execute the AST (tree-walking interpreter)
- [ ] **REPL** — Interactive read-eval-print loop

### Part 2: Compiler (future)

- [ ] **Compiler** — Compile AST to bytecode
- [ ] **Virtual Machine** — Execute bytecode

## Language Features (Monkey)

- Variable bindings (`let`)
- Integers and booleans
- Arithmetic expressions
- Built-in functions
- First-class functions and closures
- Arrays and hash maps
