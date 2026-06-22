package me.ryan.interpreter.benchmark

import me.ryan.interpreter.compiler.Bytecode
import me.ryan.interpreter.compiler.BytecodeWriter
import me.ryan.interpreter.compiler.Compiler
import me.ryan.interpreter.eval.Environment
import me.ryan.interpreter.eval.Evaluator
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.parser.Parser
import java.io.FileOutputStream

fun main() {
    val input = """
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
    """.trimIndent()

    val program = Parser(Lexer(input)).parseProgram()

    val N = 5
    val evalTimes = mutableListOf<Long>()
    for (i in 0 until N) {
        val env = Environment()
        val start = System.nanoTime()
        val result = Evaluator().eval(program, env)
        val elapsed = System.nanoTime() - start
        evalTimes.add(elapsed)
        if (i == 0) println("result = $result")
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
