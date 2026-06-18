package me.ryan.interpreter.compiler

import me.ryan.interpreter.ast.Node
import me.ryan.interpreter.lexer.Lexer
import me.ryan.interpreter.parser.Parser
import java.io.File

@OptIn(ExperimentalUnsignedTypes::class)
fun main() {
    val cases = listOf(
        "src/test/fixtures/just_one.mkc" to "1",
        "src/test/fixtures/just_two.mkc" to "2",
        "src/test/fixtures/one_plus_two.mkc" to "1 + 2",
        "src/test/fixtures/one_minus_two.mkc" to "1 - 2",
        "src/test/fixtures/one_times_two.mkc" to "1 * 2",
        "src/test/fixtures/four_div_two.mkc" to "4 / 2",
        "src/test/fixtures/mixed_arithmetic_1.mkc" to "50 / 2 * 2 + 10 - 5",
        "src/test/fixtures/mixed_arithmetic_2.mkc" to "5 + 5 + 5 + 5 - 10",
        "src/test/fixtures/power_of_two.mkc" to "2 * 2 * 2 * 2 * 2",
        "src/test/fixtures/mul_then_add.mkc" to "5 * 2 + 10",
        "src/test/fixtures/add_then_mul.mkc" to "5 + 2 * 10",
        "src/test/fixtures/paren_expr.mkc" to "5 * (2 + 10)",
        "src/test/fixtures/true.mkc" to "true",
        "src/test/fixtures/false.mkc" to "false",
        // integer comparisons
        "src/test/fixtures/one_lt_two.mkc" to "1 < 2",
        "src/test/fixtures/one_gt_two.mkc" to "1 > 2",
        "src/test/fixtures/one_lt_one.mkc" to "1 < 1",
        "src/test/fixtures/one_gt_one.mkc" to "1 > 1",
        "src/test/fixtures/one_eq_one.mkc" to "1 == 1",
        "src/test/fixtures/one_neq_one.mkc" to "1 != 1",
        "src/test/fixtures/one_eq_two.mkc" to "1 == 2",
        "src/test/fixtures/one_neq_two.mkc" to "1 != 2",
        // boolean equality
        "src/test/fixtures/true_eq_true.mkc" to "true == true",
        "src/test/fixtures/false_eq_false.mkc" to "false == false",
        "src/test/fixtures/true_eq_false.mkc" to "true == false",
        "src/test/fixtures/true_neq_false.mkc" to "true != false",
        "src/test/fixtures/false_neq_true.mkc" to "false != true",
        // mixed: comparison result compared with boolean
        "src/test/fixtures/lt_eq_true.mkc" to "(1 < 2) == true",
        "src/test/fixtures/lt_eq_false.mkc" to "(1 < 2) == false",
        "src/test/fixtures/gt_eq_true.mkc" to "(1 > 2) == true",
        "src/test/fixtures/gt_eq_false.mkc" to "(1 > 2) == false",
        // prefix: minus
        "src/test/fixtures/minus_five.mkc" to "-5",
        "src/test/fixtures/minus_ten.mkc" to "-10",
        "src/test/fixtures/minus_sum.mkc" to "-50 + 100 + -50",
        "src/test/fixtures/complex_arithmetic.mkc" to "(5 + 10 * 2 + 15 / 3) * 2 + -10",
        // prefix: bang (logical not)
        "src/test/fixtures/bang_true.mkc" to "!true",
        "src/test/fixtures/bang_false.mkc" to "!false",
        "src/test/fixtures/bang_five.mkc" to "!5",
        "src/test/fixtures/bang_bang_true.mkc" to "!!true",
        "src/test/fixtures/bang_bang_false.mkc" to "!!false",
        "src/test/fixtures/bang_bang_five.mkc" to "!!5",
        "src/test/fixtures/bang_if_false_5.mkc" to "!(if (false) { 5; })",
        // conditionals
        "src/test/fixtures/if_true_10.mkc" to "if (true) { 10 }",
        "src/test/fixtures/if_true_10_else_20.mkc" to "if (true) { 10 } else { 20 }",
        "src/test/fixtures/if_false_10_else_20.mkc" to "if (false) { 10 } else { 20 }",
        "src/test/fixtures/if_one_10.mkc" to "if (1) { 10 }",
        "src/test/fixtures/if_one_lt_two_10.mkc" to "if (1 < 2) { 10 }",
        "src/test/fixtures/if_one_lt_two_10_else_20.mkc" to "if (1 < 2) { 10 } else { 20 }",
        "src/test/fixtures/if_one_gt_two_10_else_20.mkc" to "if (1 > 2) { 10 } else { 20 }",
        "src/test/fixtures/if_one_gt_two_10.mkc" to "if (1 > 2) { 10 }",
        "src/test/fixtures/if_false_10.mkc" to "if (false) { 10 }",
        "src/test/fixtures/if_null_cond_10_else_20.mkc" to "if ((if (false) { 10 })) { 10 } else { 20 }",
        // global let statements
        "src/test/fixtures/global_let_one.mkc" to "let one = 1; one",
        "src/test/fixtures/global_let_one_two_sum.mkc" to "let one = 1; let two = 2; one + two",
        "src/test/fixtures/global_let_two_from_one.mkc" to "let one = 1; let two = one + one; one + two",
        // string expressions
        "src/test/fixtures/string_monkey.mkc" to "\"monkey\"",
        "src/test/fixtures/string_mon_plus_key.mkc" to "\"mon\" + \"key\"",
        "src/test/fixtures/string_mon_plus_key_plus_banana.mkc" to "\"mon\" + \"key\" + \"banana\"",
        // array literals
        "src/test/fixtures/array_empty.mkc" to "[]",
        "src/test/fixtures/array_one_two_three.mkc" to "[1, 2, 3]",
        "src/test/fixtures/array_with_arithmetic.mkc" to "[1 + 2, 3 * 4, 5 + 6]",
        // hash literals
        "src/test/fixtures/hash_empty.mkc" to "{}",
        "src/test/fixtures/hash_one_two.mkc" to "{1 : 2, 2 : 3}",
        "src/test/fixtures/hash_with_arithmetic.mkc" to "{1 + 1: 2 * 2, 3 + 3: 4 * 4}",
        "src/test/fixtures/hash_resize.mkc" to """
           {
             1: 10, 2: 20, 3: 30, 4: 40, 5: 50, 6: 60, 7: 70, 8: 80, 9: 90, 10: 100, 11: 110, 12: 120, 
             13: 130, 14: 140, 15: 150, 16: 160, 17: 170, 18: 180, 19: 190, 20: 200
           }
        """.trimIndent(),
        // index expressions
        "src/test/fixtures/index_array_simple.mkc" to "[1, 2, 3][1]",
        "src/test/fixtures/index_array_expr.mkc" to "[1, 2, 3][0 + 2]",
        "src/test/fixtures/index_nested_array.mkc" to "[[1, 1, 1]][0][0]",
        "src/test/fixtures/index_empty_array.mkc" to "[][0]",
        "src/test/fixtures/index_array_oob.mkc" to "[1, 2, 3][99]",
        "src/test/fixtures/index_array_negative.mkc" to "[1][-1]",
        "src/test/fixtures/index_hash_one.mkc" to "{1: 1, 2: 2}[1]",
        "src/test/fixtures/index_hash_two.mkc" to "{1: 1, 2: 2}[2]",
        "src/test/fixtures/index_hash_missing.mkc" to "{1: 1}[0]",
        "src/test/fixtures/index_empty_hash.mkc" to "{}[0]",
        // function calls
        "src/test/fixtures/function_call_no_args.mkc" to """
            let fivePlusTen = fn() { 5 + 10; };
            fivePlusTen();
        """.trimIndent(),
        "src/test/fixtures/function_call_multiple_no_args.mkc" to """
            let one = fn() { 1; };
            let two = fn() { 2; };
            one() + two()
        """.trimIndent(),
        "src/test/fixtures/function_call_nested_no_args.mkc" to """
            let a = fn() { 1 };
            let b = fn() { a() + 1 };
            let c = fn() { b() + 1 };
            c();
        """.trimIndent(),
        "src/test/fixtures/function_early_return.mkc" to """
            let earlyExit = fn() { return 99; 100; };
            earlyExit();
        """.trimIndent(),
        "src/test/fixtures/function_double_return.mkc" to """
            let earlyExit = fn() { return 99; return 100; };
            earlyExit();
        """.trimIndent(),
        "src/test/fixtures/function_no_return_value.mkc" to """
            let noReturn = fn() { };
            noReturn();
        """.trimIndent(),
        "src/test/fixtures/function_calls_void_function.mkc" to """
            let noReturn = fn() { };
            let noReturnTwo = fn() { noReturn(); };
            noReturn();
            noReturnTwo();
        """.trimIndent(),
        "src/test/fixtures/function_first_class.mkc" to """
            let returnsOne = fn() { 1; };
            let returnsOneReturner = fn() { returnsOne; };
            returnsOneReturner()();
        """.trimIndent(),
        // function calls with local bindings
        "src/test/fixtures/function_local_binding.mkc" to """
            let one = fn() { let one = 1; one };
            one();
        """.trimIndent(),
        "src/test/fixtures/function_local_bindings_sum.mkc" to """
            let oneAndTwo = fn() { let one = 1; let two = 2; one + two; };
            oneAndTwo();
        """.trimIndent(),
        "src/test/fixtures/function_multiple_with_locals.mkc" to """
            let oneAndTwo = fn() { let one = 1; let two = 2; one + two; };
            let threeAndFour = fn() { let three = 3; let four = 4; three + four; };
            oneAndTwo() + threeAndFour();
        """.trimIndent(),
        "src/test/fixtures/function_same_local_name.mkc" to """
            let firstFoobar = fn() { let foobar = 50; foobar; };
            let secondFoobar = fn() { let foobar = 100; foobar; };
            firstFoobar() + secondFoobar();
        """.trimIndent(),
        "src/test/fixtures/function_global_and_local.mkc" to """
            let globalSeed = 50;
            let minusOne = fn() {
                let num = 1;
                globalSeed - num;
            }
            let minusTwo = fn() {
                let num = 2;
                globalSeed - num;
            }
            minusOne() + minusTwo();
        """.trimIndent(),
        "src/test/fixtures/function_first_class_local.mkc" to """
            let returnsOneReturner = fn() {
                let returnsOne = fn() { 1; };
                returnsOne;
            };
            returnsOneReturner()();
        """.trimIndent(),
        // function calls with arguments and bindings
        "src/test/fixtures/function_arg_identity.mkc" to """
            let identity = fn(a) { a; };
            identity(4);
        """.trimIndent(),
        "src/test/fixtures/function_arg_sum.mkc" to """
            let sum = fn(a, b) { a + b; };
            sum(1, 2);
        """.trimIndent(),
        "src/test/fixtures/function_arg_sum_local.mkc" to """
            let sum = fn(a, b) {
                let c = a + b;
                c;
            };
            sum(1, 2);
        """.trimIndent(),
        "src/test/fixtures/function_arg_sum_twice.mkc" to """
            let sum = fn(a, b) {
                let c = a + b;
                c;
            };
            sum(1, 2) + sum(3, 4);
        """.trimIndent(),
        "src/test/fixtures/function_arg_outer.mkc" to """
            let sum = fn(a, b) {
                let c = a + b;
                c;
            };
            let outer = fn() {
                sum(1, 2) + sum(3, 4);
            };
            outer();
        """.trimIndent(),
        "src/test/fixtures/function_arg_globals.mkc" to """
            let globalNum = 10;
            let sum = fn(a, b) {
                let c = a + b;
                c + globalNum;
            };
            let outer = fn() {
                sum(1, 2) + sum(3, 4) + globalNum;
            };
            outer() + globalNum;
        """.trimIndent(),
        // function calls with wrong number of arguments (runtime errors)
        "src/test/fixtures/function_wrong_args_0_1.mkc" to """
            fn() { 1; }(1);
        """.trimIndent(),
        "src/test/fixtures/function_wrong_args_1_0.mkc" to """
            fn(a) { a; }();
        """.trimIndent(),
        "src/test/fixtures/function_wrong_args_2_1.mkc" to """
            fn(a, b) { a + b; }(1);
        """.trimIndent(),
        // builtin functions
        "src/test/fixtures/builtin_len_empty_string.mkc" to "len(\"\")",
        "src/test/fixtures/builtin_len_four.mkc" to "len(\"four\")",
        "src/test/fixtures/builtin_len_hello_world.mkc" to "len(\"hello world\")",
        "src/test/fixtures/builtin_len_int.mkc" to "len(1)",
        "src/test/fixtures/builtin_len_two_args.mkc" to "len(\"one\", \"two\")",
        "src/test/fixtures/builtin_len_array.mkc" to "len([1, 2, 3])",
        "src/test/fixtures/builtin_len_empty_array.mkc" to "len([])",
        "src/test/fixtures/builtin_puts.mkc" to "puts(\"hello\", \"world!\")",
        "src/test/fixtures/builtin_first_array.mkc" to "first([1, 2, 3])",
        "src/test/fixtures/builtin_first_empty.mkc" to "first([])",
        "src/test/fixtures/builtin_first_int.mkc" to "first(1)",
        "src/test/fixtures/builtin_last_array.mkc" to "last([1, 2, 3])",
        "src/test/fixtures/builtin_last_empty.mkc" to "last([])",
        "src/test/fixtures/builtin_last_int.mkc" to "last(1)",
        "src/test/fixtures/builtin_rest_array.mkc" to "rest([1, 2, 3])",
        "src/test/fixtures/builtin_rest_empty.mkc" to "rest([])",
        "src/test/fixtures/builtin_push_empty.mkc" to "push([], 1)",
        "src/test/fixtures/builtin_push_int.mkc" to "push(1, 1)",
        // builtin functions (cover double free issues)
        "src/test/fixtures/builtin_first_string_in_array.mkc" to """first(["hello"])""",
        "src/test/fixtures/builtin_last_string_in_array.mkc" to """last(["hello"])""",
        "src/test/fixtures/builtin_first_nested_array.mkc" to "first([[1]])",
        "src/test/fixtures/builtin_last_nested_array.mkc" to "last([[1]])",
        "src/test/fixtures/builtin_first_concat_in_array.mkc" to """first(["a" + "b"])""",
        "src/test/fixtures/builtin_rest_single.mkc" to "rest([42])",
        // closures
        "src/test/fixtures/closures.mkc" to """
            let newClosure = fn(a) {
                fn() { a; };
            };
            let closure = newClosure(99);
            closure();
        """.trimIndent(),
        "src/test/fixtures/closures_with_args.mkc" to """
                let newAdder = fn(a, b) {
                    fn(c) { a + b + c };
                };
                let adder = newAdder(1, 2);
                adder(8);
            """.trimIndent(),
        "src/test/fixtures/closures_with_locals.mkc" to """
                let newAdder = fn(a, b) {
                    let c = a + b;
                    fn(d) { c + d };
                };
                let adder = newAdder(1, 2);
                adder(8);
            """.trimIndent(),
        // recursion (currently fails to compile — see SymbolTable.resolve;
//        "src/test/fixtures/function_recursion_global.mkc" to """
//              let countDown = fn(x) {
//                  if (x == 0) {
//                      return 0;
//                  } else {
//                      countDown(x - 1);
//                  }
//              };
//              countDown(1);
//        """.trimIndent(),
//        "src/test/fixtures/function_recursion_local.mkc" to """
//              let wrapper = fn() {
//                  let countDown = fn(x) {
//                      if (x == 0) {
//                          return 0;
//                      } else {
//                          countDown(x - 1);
//                      }
//                  };
//                  countDown(1);
//              };
//              wrapper();
//        """.trimIndent(),
    )

    for ((path, source) in cases) {
        val program = parse(source)
        val compiler = Compiler()
        compiler.compile(program)
        val bytecode = compiler.bytecode()
        val file = File(path)
        file.parentFile?.mkdirs()
        file.outputStream().use { out ->
            BytecodeWriter.write(bytecode, out)
        }
        println("  generated: $path")
    }
    println("Done. ${cases.size} fixture(s) generated.")
}

private fun parse(input: String): Node {
    val lexer = Lexer(input)
    val parser = Parser(lexer)
    return parser.parseProgram()
}
