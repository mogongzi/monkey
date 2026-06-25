# Chapter 10 — "Taking Time" Benchmark

## Goal

Compare three paths for the same Monkey program, `fib(35)`:

1. Kotlin tree-walking interpreter
2. Kotlin compiler / bytecode emitter
3. C bytecode VM

This follows the benchmarking story from *Writing A Compiler In Go*, but adapted to this
project's split architecture: Kotlin owns compile-time work, while the C VM owns runtime
bytecode execution.

---

## Benchmark Input

The benchmark source should match the book's Fibonacci shape:

```monkey
let fibonacci = fn(x) {
  if (x == 0) {
    0
  } else {
    if (x == 1) {
      return 1;
    } else {
      fibonacci(x - 1) + fibonacci(x - 2);
    }
  }
};
fibonacci(35);
```

Expected result:

```text
9227465
```

---

## What Is Timed

| Path | Current timing scope | Excluded |
|------|----------------------|----------|
| Kotlin interpreter | `Evaluator.eval(program, env)` | lexer/parser, macro expansion, `.mkc` writing |
| Kotlin compiler | `Compiler.compile(program)` | lexer/parser, `.mkc` writing |
| C VM | `vm_run(vm)` | `.mkc` loading via `mkc_read()` |

The current Kotlin benchmark parses the program once before timing. That means the interpreter
number is **eval-only**, not `parse + eval`. This is different from the book's exact benchmark,
which times `parse + eval` and `parse + compile`.

Both native executables are AOT-style builds:

- Kotlin benchmark: GraalVM native image
- C VM benchmark: `cc -O2`, without AddressSanitizer

---

## Files Involved

| File | Purpose |
|------|---------|
| `src/main/kotlin/benchmark/Benchmark.kt` | Runs Kotlin interpreter/compiler benchmark and writes `build/benchmark_fib35.mkc` |
| `build.gradle.kts` | Defines JVM benchmark task, GraalVM benchmark image, and optional native binary collection task |
| `src/test/c/vm/benchmark.c` | Runs the C VM benchmark against a `.mkc` file |
| `src/main/c/vm/Makefile` | Builds the optimized C benchmark binary as `build/benchmark` |

---

## Kotlin Benchmark

The Kotlin benchmark should:

- parse the Monkey source once
- run the interpreter multiple times with a fresh `Environment` each run
- run the compiler multiple times with a fresh `Compiler` each run
- discard the first run when reporting min/avg/max
- write `build/benchmark_fib35.mkc` for the C VM benchmark

Key details:

- The interpreter result prints as `MInteger(value=9227465)`.
- The compiler timing is only bytecode generation. It is **not** VM execution time.
- The `.mkc` write is intentionally outside the compiler timing.

---

## Gradle Tasks

Useful tasks:

```bash
./gradlew benchmark
./gradlew nativeBenchmarkCompile
./gradlew nativeBin
```

The GraalVM plugin derives task names from binary names:

```text
main       -> nativeCompile          -> build/native/nativeCompile/monkey
benchmark  -> nativeBenchmarkCompile -> build/native/nativeBenchmarkCompile/monkey-benchmark
```

The optional `nativeBin` copy task collects the built native executables into:

```text
build/native/bin/monkey
build/native/bin/monkey-benchmark
```

Because this machine cannot safely use `jenv` for GraalVM, run GraalVM tasks with direct
environment variables:

```bash
GRAALVM_HOME=/Users/I503354/jdks/graalvm-jdk-25.0.3+9.1/Contents/Home \
JAVA_HOME=/Users/I503354/jdks/graalvm-jdk-25.0.3+9.1/Contents/Home \
./gradlew nativeBin
```

---

## C VM Benchmark

The C benchmark should:

- take the `.mkc` path as `argv[1]`
- open the file and call `mkc_read(FILE *, ByteCode *)`
- load bytecode outside the timed region
- create a fresh `VM` for each run with `vm_init(&bc)`
- time only `vm_run(vm)`
- read the final value with `vm_last_popped_stack_elem(vm)`
- call `vm_free(vm)` after each run
- call `free_bytecode(&bc)` once, after all runs

Important pitfalls:

1. Do **not** read the result with `vm->stack[vm->sp - 1]` for top-level expression programs.
   The compiler emits a final `OpPop`, so after execution `sp` is usually `0`. The result is
   stored at `vm_last_popped_stack_elem(vm)`, matching `test_vm.c`.

2. Do **not** define `vm_last_popped_stack_elem()` in `benchmark.c`. It is already declared in
   `vm.h` and defined in `vm.c`. Defining it again in `benchmark.c` causes a duplicate-symbol
   linker error.

3. Do **not** call `free_bytecode(&bc)` inside the timing loop. The same `ByteCode` is reused
   across runs, so freeing it inside the loop makes later runs use freed/zeroed bytecode.

4. In C, use `1000000.0`, not Kotlin/Java-style numeric separators like `1_000_000.0`.

Result access should look like:

```c
const MObject *top = vm_last_popped_stack_elem(vm);
printf("result=%lld\n", top->as.integer);
```

---

## Makefile Target

The current Makefile target is named `benchmark`, not `benchmark_vm`:

```makefile
BENCH_CFLAGS = -Wall -Wextra -std=c11 -O2

benchmark:
	mkdir -p $(BUILD_DIR)
	$(CC) $(BENCH_CFLAGS) -o $(BUILD_DIR)/$@ $(TEST_DIR)/benchmark.c $(LOADER_SRCS) $(RUNTIME_SRCS)
```

It produces:

```text
build/benchmark
```

Build it from `src/main/c/vm`:

```bash
make benchmark
```

Run it from the repository root:

```bash
./build/benchmark build/benchmark_fib35.mkc
```

---

## Runbook

Build and run the Kotlin/GraalVM benchmark:

```bash
GRAALVM_HOME=/Users/I503354/jdks/graalvm-jdk-25.0.3+9.1/Contents/Home \
JAVA_HOME=/Users/I503354/jdks/graalvm-jdk-25.0.3+9.1/Contents/Home \
./gradlew nativeBin

./build/native/bin/monkey-benchmark
```

Build and run the C VM benchmark:

```bash
(cd src/main/c/vm && make benchmark)
./build/benchmark build/benchmark_fib35.mkc
```

---

## Verified Results

### M1 Max (MacBook Pro, Apple M1 Max)

#### Kotlin benchmark native image

Command:

```bash
./build/native/bin/monkey-benchmark
```

Output:

```text
result = MInteger(value=9227465)
engine=interpreter, min=7037.837333ms, avg=7122.498625ms, max=7228.665041ms
engine=compiler, min=0.002083ms, avg=0.0022705ms, max=0.002666ms
wrote build/benchmark_fib35.mkc
```

#### C VM benchmark

Command:

```bash
./build/benchmark build/benchmark_fib35.mkc
```

Output:

```text
result=9227465
engine=vm, min=1956.03ms, avg=1958.86ms, max=1961.00ms
```

---

### M4 Pro (MacBook Pro, Apple M4 Pro)

#### Kotlin benchmark native image

Command:

```bash
./build/native/bin/monkey-benchmark
```

Output:

```text
result = MInteger(value=9227465)
engine=interpreter, min=4831.492333ms, avg=4841.4736665ms, max=4851.723458ms
engine=compiler, min=0.001459ms, avg=0.00164625ms, max=0.002042ms
wrote build/benchmark_fib35.mkc
```

#### Kotlin benchmark JVM mode

Command:

```bash
./gradlew benchmark
```

Output:

```text
result = MInteger(value=9227465)
engine=interpreter, min=5192.824333ms, avg=5207.667156ms, max=5233.9085ms
engine=compiler, min=0.063584ms, avg=0.07417725ms, max=0.099167ms
wrote build/benchmark_fib35.mkc
```

#### C VM benchmark

Command:

```bash
./build/benchmark build/benchmark_fib35.mkc
```

Output:

```text
result=9227465
engine=vm, min=1080.12ms, avg=1091.18ms, max=1098.81ms
```

---

### Comparison

#### Per-machine summary

| Path | M1 Max | M4 Pro (native) | M4 Pro (JVM) |
|------|--------|-----------------|--------------|
| Kotlin interpreter | 7122.50ms | 4841.47ms | 5207.67ms |
| Kotlin compiler only | 0.0023ms | 0.0016ms | 0.074ms |
| C VM runtime | 1958.86ms | 1091.18ms | — |

#### M1 Max → M4 Pro speedup (native image)

| Path | M1 Max | M4 Pro | Speedup |
|------|--------|--------|---------|
| Kotlin interpreter | 7122.50ms | 4841.47ms | **1.47×** |
| C VM | 1958.86ms | 1091.18ms | **1.80×** |

The C VM benefits more from M4 Pro than the Kotlin interpreter — the tight dispatch loop maps
well onto M4 Pro's wider pipeline and improved branch prediction.

#### M4 Pro native image vs JVM mode

| Path | Native (AOT) | JVM (JIT) | Difference |
|------|-------------|-----------|------------|
| Interpreter | 4841ms | 5208ms | Native ~7% faster |
| Compiler | 0.0016ms | 0.074ms | Native ~46× faster |

Native image avoids JIT warmup overhead. The compiler microbenchmark is dominated by this
since the actual work is microsecond-scale.

#### VM vs interpreter ratio

| Machine | Interpreter | C VM | Ratio |
|---------|-------------|------|-------|
| M1 Max | 7122.50ms | 1958.86ms | **3.64×** |
| M4 Pro | 4841.47ms | 1091.18ms | **4.44×** |

#### vs book (Go, from *Writing A Compiler In Go*)

| Path | Book time |
|------|-----------|
| Go evaluator | 27204.28ms |
| Go VM | 8876.22ms |

Derived ratios (using M4 Pro native numbers):

```text
C VM (M4 Pro) vs Kotlin interpreter (M4 Pro): 4841.47 / 1091.18 ≈ 4.44x faster
Kotlin interpreter (M4 Pro) vs book Go evaluator: 27204.28 / 4841.47 ≈ 5.62x faster
C VM (M4 Pro) vs book Go VM: 8876.22 / 1091.18 ≈ 8.13x faster
C VM (M4 Pro) vs book Go evaluator: 27204.28 / 1091.18 ≈ 24.93x faster
```

The compiler timing is intentionally listed separately because it measures bytecode generation
only. The full compiled execution story is represented by the C VM runtime number.

---

## Notes

- On macOS, `clock_gettime(CLOCK_MONOTONIC)` is available since macOS 10.12.
- AddressSanitizer is useful for debugging the VM, but it should not be enabled for final
  benchmark numbers.
- If the C VM becomes too fast for stable measurements, increase the input to `fib(40)` or raise
  the number of benchmark iterations.
