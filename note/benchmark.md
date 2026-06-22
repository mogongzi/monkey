# Chapter 10 — "Taking Time" Benchmark

## Goal

Compare the tree-walking interpreter against the compiled bytecode path using `fib(35)`,
matching the book's measurement story but adapted to this project's split architecture.

## Measurement Story

| Path | What's timed | What's excluded |
|------|-------------|-----------------|
| **Interpreter** | `parse + eval` | macro expansion, JVM warm-up (native image) |
| **Compiled** | `parse + compile` (Kotlin) + `vm_run()` (C) | BytecodeWriter, mkc_read (serialization bridge) |

Both sides are **AOT-compiled** (GraalVM native image for Kotlin, `-O2` for C) — no JIT warm-up,
fair comparison, same spirit as Go in the book.

---

## Fibonacci Source (the benchmark input)

```monkey
let fibonacci = fn(x) {
  if (x == 0) { return 0; }
  if (x == 1) { return 1; }
  fibonacci(x - 1) + fibonacci(x - 2);
};
fibonacci(35);
```

Expected result: `9227465`

---

## Files to Create / Modify

| File | Action | Purpose |
|------|--------|---------|
| `src/main/kotlin/benchmark/Benchmark.kt` | Create | Kotlin benchmark main |
| `build.gradle.kts` | Modify | Add benchmark tasks + native-image target |
| `src/main/c/vm/benchmark_vm.c` | Create | C VM benchmark binary |
| `src/main/c/vm/Makefile` | Modify | Add `benchmark_vm` target |

---

## Step 1: Kotlin Benchmark (`Benchmark.kt`)

Create `src/main/kotlin/benchmark/Benchmark.kt` with a standalone `main()`.

### Requirements

- **No JLine dependency** — pure computation, no terminal UI
- **No macro expansion** — call `eval()` directly on the parsed program
- **Multiple iterations** — run N times (e.g., 5), discard the first, report min/avg/max
- **Write `.mkc`** — emit `build/benchmark_fib35.mkc` for the C benchmark to consume

### Pseudocode

```kotlin
package me.ryan.interpreter.benchmark

fun main() {
    val input = """
        let fibonacci = fn(x) { ... };
        fibonacci(35);
    """

    val program = Parser(Lexer(input)).parseProgram()

    // --- Interpreter benchmark ---
    val N = 5
    val evalTimes = mutableListOf<Long>()
    for (i in 0 until N) {
        val env = Environment()
        val start = System.nanoTime()
        val result = Evaluator.eval(program, env)
        val elapsed = System.nanoTime() - start
        evalTimes.add(elapsed)
        if (i == 0) println("result=$result") // verify correctness on first run
    }
    // Discard first (cold run), report stats on rest
    reportStats("interpreter", evalTimes.drop(1))

    // --- Compiler benchmark ---
    val compileTimes = mutableListOf<Long>()
    var bytecode: Bytecode? = null
    for (i in 0 until N) {
        val compiler = Compiler()
        val start = System.nanoTime()
        compiler.compile(program)
        val elapsed = System.nanoTime() - start
        compileTimes.add(elapsed)
        if (i == 0) bytecode = compiler.bytecode()
    }
    reportStats("compiler", compileTimes.drop(1))

    // --- Write .mkc for C VM benchmark ---
    val outPath = "build/benchmark_fib35.mkc"
    BytecodeWriter.write(bytecode!!, FileOutputStream(outPath))
    println("wrote $outPath")
}

fun reportStats(label: String, times: List<Long>) {
    val min = times.min() / 1_000_000.0  // ms
    val max = times.max() / 1_000_000.0
    val avg = times.average() / 1_000_000.0
    println("engine=$label, min=${min}ms, avg=${avg}ms, max=${max}ms")
}
```

### Key points

- `Evaluator.eval()` needs a fresh `Environment` each iteration to avoid state leakage
- `Compiler()` is a fresh instance each iteration (it has mutable state)
- The parse step is intentionally **inside** the timing if you want to match the book exactly
  (Thorsten times `parse + eval` and `parse + compile`). Decide which you prefer:
  - **Option A**: time `parse + eval` / `parse + compile` (book-faithful)
  - **Option B**: parse once, time only `eval` / `compile` (isolates engine speed)
  - Recommendation: Option A for the "official" comparison, maybe print both

---

## Step 2: Gradle Changes (`build.gradle.kts`)

### Add a JavaExec task (for JVM sanity checks)

```kotlin
tasks.register<JavaExec>("benchmark") {
    mainClass.set("me.ryan.interpreter.benchmark.BenchmarkKt")
    classpath = sourceSets["main"].runtimeClasspath
}
```

### Add a second native-image target

```kotlin
graalvmNative {
    binaries {
        named("main") {
            mainClass.set("me.ryan.interpreter.repl.ReplKt")
            imageName.set("monkey")
        }
        register("benchmark") {
            mainClass.set("me.ryan.interpreter.benchmark.BenchmarkKt")
            imageName.set("monkey-benchmark")
        }
    }
}
```

The benchmark native image is built with:
```bash
./gradlew nativeBenchmarkCompile
```

(The GraalVM plugin generates a task named `native<BinaryName>Compile` for each registered binary.)

---

## Step 3: C VM Benchmark (`benchmark_vm.c`)

Create `src/main/c/vm/benchmark_vm.c` (or in `src/test/c/vm/` alongside test_vm.c).

### Requirements

- Takes `.mkc` file path as `argv[1]`
- Loads with `mkc_read()` — **not timed**
- Runs `vm_run()` N times (e.g., 5), resets VM state each iteration
- Discards the first run, reports min/avg/max
- Prints the result from the first run for correctness verification
- Compiled with `-O2`, **no** `-fsanitize=address`

### Pseudocode

```c
#include <stdio.h>
#include <time.h>
#include "mkc.h"
#include "vm.h"

#define NUM_RUNS 5

int main(int argc, char *argv[]) {
    if (argc < 2) {
        fprintf(stderr, "Usage: %s <file.mkc>\n", argv[0]);
        return 1;
    }

    // Load bytecode (not timed)
    ByteCode *bc = mkc_read(argv[1]);
    if (!bc) {
        fprintf(stderr, "Failed to read %s\n", argv[1]);
        return 1;
    }

    double times_ms[NUM_RUNS];

    for (int i = 0; i < NUM_RUNS; i++) {
        VM *vm = vm_init(bc);

        struct timespec start, end;
        clock_gettime(CLOCK_MONOTONIC, &start);
        VM_RESULT result = vm_run(vm);
        clock_gettime(CLOCK_MONOTONIC, &end);

        double elapsed_ms = (end.tv_sec - start.tv_sec) * 1000.0
                          + (end.tv_nsec - start.tv_nsec) / 1_000_000.0;
        times_ms[i] = elapsed_ms;

        if (i == 0) {
            // Print result for verification
            MObject top = vm->stack[vm->sp - 1]; // or however you access the result
            printf("result=%lld\n", top.integer);
        }

        // Clean up VM for next iteration
        // (depends on how vm_init/free works — may need vm_free() or similar)
    }

    // Stats (skip first run)
    double min = times_ms[1], max = times_ms[1], sum = 0;
    for (int i = 1; i < NUM_RUNS; i++) {
        if (times_ms[i] < min) min = times_ms[i];
        if (times_ms[i] > max) max = times_ms[i];
        sum += times_ms[i];
    }
    double avg = sum / (NUM_RUNS - 1);

    printf("engine=vm, min=%.2fms, avg=%.2fms, max=%.2fms\n", min, avg, max);

    return 0;
}
```

### Things to figure out

- **VM reset**: Does `vm_init()` allocate fresh state? Or do you need a `vm_reset()`/`vm_free()`?
  Check how `test_vm.c` handles this — it likely creates a new VM per test case.
- **Result access**: How does the VM expose the final result after `vm_run()`?
  Look at how `test_vm.c` reads the "last popped" element.
- **Memory**: The arena allocator — do you need to free and recreate it between runs?

---

## Step 4: Makefile Changes

Add to `src/main/c/vm/Makefile`:

```makefile
BENCH_CFLAGS = -Wall -Wextra -std=c11 -O2

benchmark_vm:
	mkdir -p $(BUILD_DIR)
	$(CC) $(BENCH_CFLAGS) -o $(BUILD_DIR)/$@ $(TEST_DIR)/benchmark_vm.c $(LOADER_SRCS) $(RUNTIME_SRCS)
```

**Important**: No `-fsanitize=address`, no `-g` (debug info). Pure optimized build.

Optionally add to the `clean` target:
```makefile
clean:
	rm -f $(BUILD_DIR)/test_mkc $(BUILD_DIR)/test_vm $(BUILD_DIR)/dump_mkc $(BUILD_DIR)/benchmark_vm
```

---

## Step 5: Run the Benchmark

```bash
# 1. Build the native benchmark binary
./gradlew nativeBenchmarkCompile

# 2. Run it — interpreter timing + compile timing + writes .mkc
./build/native/nativeCompile/monkey-benchmark

# 3. Build C VM benchmark (optimized, no sanitizer)
cd src/main/c/vm && make benchmark_vm

# 4. Run VM benchmark
../../../../../../build/benchmark_vm ../../../../../../build/benchmark_fib35.mkc
# (or use absolute paths / adjust Makefile BUILD_DIR)

# Simpler with absolute path:
./build/benchmark_vm ./build/benchmark_fib35.mkc
```

---

## Expected Output

```
=== Monkey Benchmark: fib(35) ===

--- Interpreter (parse + eval) ---
result=9227465
engine=interpreter, min=XXXX.XXms, avg=XXXX.XXms, max=XXXX.XXms

--- Compiler (parse + compile) ---
engine=compiler, min=X.XXms, avg=X.XXms, max=X.XXms
wrote build/benchmark_fib35.mkc

--- VM (dispatch loop) ---
result=9227465
engine=vm, min=XXX.XXms, avg=XXX.XXms, max=XXX.XXms

--- Total compiled path ---
compile + vm = X.XX + XXX.XX = XXX.XXms
```

The compiled path (compile + VM) should be dramatically faster than the interpreter.
The compilation step itself should be negligible compared to VM execution for `fib(35)`.

---

## Implementation Order

1. **`Benchmark.kt`** — get it working under JVM first (`./gradlew benchmark`)
2. **`build.gradle.kts`** — add the tasks
3. **Test under JVM** — verify fib(35) = 9227465, timing prints correctly
4. **Native image** — `./gradlew nativeBenchmarkCompile`, run, compare to JVM numbers
5. **`benchmark_vm.c`** — write it, figure out VM reset between runs
6. **Makefile** — add target, build with `-O2`
7. **Run both sides** — combine the numbers, celebrate

---

## Notes

- On macOS, `clock_gettime(CLOCK_MONOTONIC)` is available since macOS 10.12
- GraalVM native-image may need `--no-fallback` flag if it can't resolve something at build time
- The GraalVM plugin task name might be `nativeBenchmarkCompile` or similar — check with
  `./gradlew tasks --group=native` after adding the config
- If fib(35) is too fast on the C VM to measure reliably, consider fib(40) instead
