#include <assert.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include "../../../main/c/vm/hash_table.h"
#include "../../../main/c/vm/mkc.h"
#include "../../../main/c/vm/opcodes.h"
#include "../../../main/c/vm/vm.h"

typedef struct {
  HashKey key;
  int64_t value;
} ExpectedHashPair;

typedef struct {
  MObjectType type;
  union {
    int64_t integer;
    bool boolean;
    const char *string;
    struct {
      const int64_t *elements;
      size_t len;
    } array;
    struct {
      const ExpectedHashPair *pairs;
      size_t len;
    } hash;
  } value;
} ExpectedObject;

typedef struct {
  const char *fixture_path;
  ExpectedObject expected;
} VmTestCase;

typedef struct {
  const char *fixture_path;
  VM_RESULT expected_err;
} VmErrorTestCase;

static ExpectedObject expected_integer(int64_t value) {
  return (ExpectedObject){.type = MINTEGER, .value.integer = value};
}

static ExpectedObject expected_boolean(bool value) {
  return (ExpectedObject){.type = MBOOLEAN, .value.boolean = value};
}

static ExpectedObject expected_null(void) {
  return (ExpectedObject){.type = MNULL};
}

static ExpectedObject expected_string(const char *value) {
  return (ExpectedObject){.type = MSTRING, .value.string = value};
}

static ExpectedObject expected_array(const int64_t *elements, size_t len) {
  return (ExpectedObject){
      .type = MARRAY, .value.array.elements = elements, .value.array.len = len};
}

static ExpectedObject expected_hash(const ExpectedHashPair *pairs, size_t len) {
  return (ExpectedObject){
      .type = MHASH,
      .value.hash = {.pairs = pairs, .len = len},
  };
}

static ExpectedObject expected_error(const char *message) {
  return (ExpectedObject){.type = MERROR, .value.string = message};
}

static void test_integer_object(const MObject *obj, int64_t expected) {
  assert(obj != NULL && "expected integer object, got NULL");
  assert(obj->type == MINTEGER && "object is not an Integer");
  assert(obj->as.integer == expected && "integer value mismatch");
}

static void test_boolean_object(const MObject *obj, bool expected) {
  assert(obj != NULL && "expected boolean object, got NULL");
  assert(obj->type == MBOOLEAN && "object is not a Boolean");
  assert(obj->as.boolean == expected && "boolean value mismatch");
}

static void test_null_object(const MObject *obj) {
  assert(obj != NULL && "expected null object, got NULL");
  assert(obj->type == MNULL && "object is not MNull");
}

static void test_string_object(const MObject *obj, const char *expected) {
  assert(obj != NULL && "expected string object, got NULL");
  assert(obj->type == MSTRING && "object is not a String");
  assert(strcmp(obj->as.string, expected) == 0 && "string value mismatch");
}

static void test_array_object(const MObject *obj, const int64_t *expected,
                              size_t expected_len) {
  assert(obj != NULL && "expected array object, got NULL");
  assert(obj->type == MARRAY && "object is not an Array");
  assert(obj->as.array != NULL && "array pointer is NULL");
  assert(obj->as.array->len == expected_len && "array length mismatch");
  for (size_t i = 0; i < expected_len; i++) {
    test_integer_object(&obj->as.array->elements[i], expected[i]);
  }
}

static void test_hash_object(const MObject *obj,
                             const ExpectedHashPair *expected,
                             size_t expected_len) {
  assert(obj != NULL && "expected hash object, got NULL");
  assert(obj->type == MHASH && "object is not a Hash");
  assert(obj->as.hash != NULL && "hash pointer is NULL");
  assert(obj->as.hash->count == expected_len &&
         "hash has wrong number of Pairs");

  for (size_t i = 0; i < expected_len; i++) {
    HashPair pair;
    bool ok = hash_get(obj->as.hash, expected[i].key, &pair);
    assert(ok && "no pair for given key in hash");
    test_integer_object(&pair.value, expected[i].value);
  }
}

static void test_error_object(const MObject *obj, const char *expected) {
  assert(obj != NULL && "expected error object, got NULL");
  assert(obj->type == MERROR && "object is not an Error");
  assert(strcmp(obj->as.error, expected) == 0 && "error message mismatch");
}

static void test_expected_object(ExpectedObject expected,
                                 const MObject *actual) {
  switch (expected.type) {
    case MINTEGER:
      test_integer_object(actual, expected.value.integer);
      break;
    case MBOOLEAN:
      test_boolean_object(actual, expected.value.boolean);
      break;
    case MNULL:
      test_null_object(actual);
      break;
    case MSTRING:
      test_string_object(actual, expected.value.string);
      break;
    case MARRAY:
      test_array_object(actual, expected.value.array.elements,
                        expected.value.array.len);
      break;
    case MHASH:
      test_hash_object(actual, expected.value.hash.pairs,
                       expected.value.hash.len);
      break;
    case MERROR:
      test_error_object(actual, expected.value.string);
      break;
    default:
      assert(false && "unhandled expected object type");
  }
}

static const char *vm_err_name(VM_RESULT r) {
  switch (r) {
    case VM_OK:
      return "VM_OK";
    case VM_ERR_UNKNOWN_OPCODE:
      return "VM_ERR_UNKNOW_OPCODE";
    case VM_ERR_STACK_OVERFLOW:
      return "VM_ERR_STACK_OVERFLOW";
    case VM_ERR_STACK_UNDERFLOW:
      return "VM_ERR_STACK_UNDERFLOW";
    case VM_ERR_UNKNOWN_OPERATOR:
      return "VM_ERR_UNKNOWN_OPERATOR";
    case VM_ERR_UNSUPPORTED_TYPE_FOR_NEGATION:
      return "VM_ERR_UNSUPPORTED_TYPE_FOR_NEGATION";
    case VM_ERR_OUT_OF_MEMORY:
      return "VM_ERR_OUT_OF_MEMORY";
    case VM_ERR_INVALID_HASH_KEY:
      return "VM_ERR_INVALID_HASH_KEY";
    case VM_ERR_NON_FUNCTION_CALL:
      return "VM_ERR_NON_FUNCTION_CALL";
    case VM_ERR_DIVISION_BY_ZERO:
      return "VM_ERR_DIVISION_BY_ZERO";
    case VM_ERR_UNSUPPORTED_TYPE_FOR_COMPARISION:
      return "VM_ERR_UNSUPPORTED_TYPE_FOR_COMPARISION";
    case VM_ERR_INTEGER_OVERFLOW:
      return "VM_ERR_INTEGER_OVERFLOW";
    case VM_ERR_INVALID_BUILTIN:
      return "VM_ERR_INVALID_BUILTIN";
    default:
      return "VM_ERR_???";
  }
}

static void run_vm_tests(VmTestCase *tests, int count) {
  for (int i = 0; i < count; i++) {
    FILE *f = fopen(tests[i].fixture_path, "rb");
    assert(f != NULL);

    ByteCode bc;
    assert(mkc_read(f, &bc) == 0);
    fclose(f);
    VM *vm = vm_init(&bc);
    assert(vm != NULL && "vm_init failed");
    VM_RESULT r = vm_run(vm);
    if (r != VM_OK) {
      fprintf(stderr, "FAIL %s: vm_run -> %s (%d)\n", tests[i].fixture_path,
              vm_err_name(r), r);
    }
    assert(r == VM_OK);
    test_expected_object(tests[i].expected, vm_last_popped_stack_elem(vm));
    vm_free(vm);
    free_bytecode(&bc);
    printf("  PASS  %s\n", tests[i].fixture_path);
  }
}

static void run_vm_error_tests(VmErrorTestCase *tests, int count) {
  for (int i = 0; i < count; i++) {
    FILE *f = fopen(tests[i].fixture_path, "rb");
    assert(f != NULL);

    ByteCode bc;
    assert(mkc_read(f, &bc) == 0);  // must still compile cleanly
    fclose(f);

    VM *vm = vm_init(&bc);
    assert(vm != NULL && "vm_init failed");

    VM_RESULT r = vm_run(vm);
    if (r != tests[i].expected_err) {
      fprintf(stderr, "FAIL %s: vm_run -> %s, want %s\n", tests[i].fixture_path,
              vm_err_name(r), vm_err_name(tests[i].expected_err));
    }
    assert(r == tests[i].expected_err);

    vm_free(vm);
    free_bytecode(&bc);
    printf("  PASS  %s\n", tests[i].fixture_path);
  }
}

static void test_integer_arithmetic(void) {
  VmTestCase tests[] = {
      {"src/test/fixtures/just_one.mkc", expected_integer(1)},
      {"src/test/fixtures/just_two.mkc", expected_integer(2)},
      {"src/test/fixtures/one_plus_two.mkc", expected_integer(3)},
      {"src/test/fixtures/one_minus_two.mkc", expected_integer(-1)},
      {"src/test/fixtures/one_times_two.mkc", expected_integer(2)},
      {"src/test/fixtures/four_div_two.mkc", expected_integer(2)},
      {"src/test/fixtures/mixed_arithmetic_1.mkc", expected_integer(55)},
      {"src/test/fixtures/mixed_arithmetic_2.mkc", expected_integer(10)},
      {"src/test/fixtures/power_of_two.mkc", expected_integer(32)},
      {"src/test/fixtures/mul_then_add.mkc", expected_integer(20)},
      {"src/test/fixtures/add_then_mul.mkc", expected_integer(25)},
      {"src/test/fixtures/paren_expr.mkc", expected_integer(60)},
      {"src/test/fixtures/minus_five.mkc", expected_integer(-5)},
      {"src/test/fixtures/minus_ten.mkc", expected_integer(-10)},
      {"src/test/fixtures/minus_sum.mkc", expected_integer(0)},
      {"src/test/fixtures/complex_arithmetic.mkc", expected_integer(50)},
  };
  run_vm_tests(tests, sizeof(tests) / sizeof(tests[0]));
}

static void test_boolean_expressions(void) {
  VmTestCase tests[] = {
      {"src/test/fixtures/true.mkc", expected_boolean(true)},
      {"src/test/fixtures/false.mkc", expected_boolean(false)},
      {"src/test/fixtures/one_lt_two.mkc", expected_boolean(true)},
      {"src/test/fixtures/one_gt_two.mkc", expected_boolean(false)},
      {"src/test/fixtures/one_lt_one.mkc", expected_boolean(false)},
      {"src/test/fixtures/one_gt_one.mkc", expected_boolean(false)},
      {"src/test/fixtures/one_eq_one.mkc", expected_boolean(true)},
      {"src/test/fixtures/one_neq_one.mkc", expected_boolean(false)},
      {"src/test/fixtures/one_eq_two.mkc", expected_boolean(false)},
      {"src/test/fixtures/one_neq_two.mkc", expected_boolean(true)},
      {"src/test/fixtures/true_eq_true.mkc", expected_boolean(true)},
      {"src/test/fixtures/false_eq_false.mkc", expected_boolean(true)},
      {"src/test/fixtures/true_eq_false.mkc", expected_boolean(false)},
      {"src/test/fixtures/true_neq_false.mkc", expected_boolean(true)},
      {"src/test/fixtures/false_neq_true.mkc", expected_boolean(true)},
      {"src/test/fixtures/lt_eq_true.mkc", expected_boolean(true)},
      {"src/test/fixtures/lt_eq_false.mkc", expected_boolean(false)},
      {"src/test/fixtures/gt_eq_true.mkc", expected_boolean(false)},
      {"src/test/fixtures/gt_eq_false.mkc", expected_boolean(true)},
      {"src/test/fixtures/bang_true.mkc", expected_boolean(false)},
      {"src/test/fixtures/bang_false.mkc", expected_boolean(true)},
      {"src/test/fixtures/bang_five.mkc", expected_boolean(false)},
      {"src/test/fixtures/bang_bang_true.mkc", expected_boolean(true)},
      {"src/test/fixtures/bang_bang_false.mkc", expected_boolean(false)},
      {"src/test/fixtures/bang_bang_five.mkc", expected_boolean(true)},
      {"src/test/fixtures/bang_if_false_5.mkc", expected_boolean(true)},
  };
  run_vm_tests(tests, sizeof(tests) / sizeof(tests[0]));
}

static void test_conditionals(void) {
  VmTestCase tests[] = {
      {"src/test/fixtures/if_true_10.mkc", expected_integer(10)},
      {"src/test/fixtures/if_true_10_else_20.mkc", expected_integer(10)},
      {"src/test/fixtures/if_false_10_else_20.mkc", expected_integer(20)},
      {"src/test/fixtures/if_one_10.mkc", expected_integer(10)},
      {"src/test/fixtures/if_one_lt_two_10.mkc", expected_integer(10)},
      {"src/test/fixtures/if_one_lt_two_10_else_20.mkc", expected_integer(10)},
      {"src/test/fixtures/if_one_gt_two_10_else_20.mkc", expected_integer(20)},
      {"src/test/fixtures/if_one_gt_two_10.mkc", expected_null()},
      {"src/test/fixtures/if_false_10.mkc", expected_null()},
      {"src/test/fixtures/if_null_cond_10_else_20.mkc", expected_integer(20)},
  };
  run_vm_tests(tests, sizeof(tests) / sizeof(tests[0]));
}

static void test_global_let_statements(void) {
  VmTestCase tests[] = {
      {"src/test/fixtures/global_let_one.mkc", expected_integer(1)},
      {"src/test/fixtures/global_let_one_two_sum.mkc", expected_integer(3)},
      {"src/test/fixtures/global_let_two_from_one.mkc", expected_integer(3)},
  };
  run_vm_tests(tests, sizeof(tests) / sizeof(tests[0]));
}

static void test_string_expressions(void) {
  VmTestCase tests[] = {
      {"src/test/fixtures/string_monkey.mkc", expected_string("monkey")},
      {"src/test/fixtures/string_mon_plus_key.mkc", expected_string("monkey")},
      {"src/test/fixtures/string_mon_plus_key_plus_banana.mkc",
       expected_string("monkeybanana")},

  };
  run_vm_tests(tests, sizeof(tests) / sizeof(tests[0]));
}

static void test_array_literals(void) {
  VmTestCase tests[] = {
      {"src/test/fixtures/array_empty.mkc", expected_array(NULL, 0)},
      {"src/test/fixtures/array_one_two_three.mkc",
       expected_array((int64_t[]){1, 2, 3}, 3)},
      {"src/test/fixtures/array_with_arithmetic.mkc",
       expected_array((int64_t[]){3, 12, 11}, 3)},
  };

  run_vm_tests(tests, sizeof(tests) / sizeof(tests[0]));
}

static void test_hash_literals(void) {
  static const ExpectedHashPair one_two[] = {
      {.key = (HashKey){.type = MINTEGER, .as.integer = 1}, .value = 2},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 2}, .value = 3},
  };

  static const ExpectedHashPair arithmetic[] = {
      {.key = (HashKey){.type = MINTEGER, .as.integer = 2}, .value = 4},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 6}, .value = 16},
  };

  static const ExpectedHashPair resize[] = {
      {.key = (HashKey){.type = MINTEGER, .as.integer = 1}, .value = 10},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 2}, .value = 20},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 3}, .value = 30},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 4}, .value = 40},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 5}, .value = 50},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 6}, .value = 60},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 7}, .value = 70},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 8}, .value = 80},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 9}, .value = 90},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 10}, .value = 100},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 11}, .value = 110},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 12}, .value = 120},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 13}, .value = 130},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 14}, .value = 140},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 15}, .value = 150},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 16}, .value = 160},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 17}, .value = 170},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 18}, .value = 180},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 19}, .value = 190},
      {.key = (HashKey){.type = MINTEGER, .as.integer = 20}, .value = 200},
  };

  VmTestCase tests[] = {
      {"src/test/fixtures/hash_empty.mkc", expected_hash(NULL, 0)},
      {"src/test/fixtures/hash_one_two.mkc", expected_hash(one_two, 2)},
      {"src/test/fixtures/hash_with_arithmetic.mkc",
       expected_hash(arithmetic, 2)},
      {"src/test/fixtures/hash_resize.mkc", expected_hash(resize, 20)},
  };

  run_vm_tests(tests, sizeof(tests) / sizeof(tests[0]));
}

static void test_index_expressions(void) {
  VmTestCase tests[] = {
      {"src/test/fixtures/index_array_simple.mkc", expected_integer(2)},
      {"src/test/fixtures/index_array_expr.mkc", expected_integer(3)},
      {"src/test/fixtures/index_nested_array.mkc", expected_integer(1)},
      {"src/test/fixtures/index_empty_array.mkc", expected_null()},
      {"src/test/fixtures/index_array_oob.mkc", expected_null()},
      {"src/test/fixtures/index_array_negative.mkc", expected_null()},
      {"src/test/fixtures/index_hash_one.mkc", expected_integer(1)},
      {"src/test/fixtures/index_hash_two.mkc", expected_integer(2)},
      {"src/test/fixtures/index_hash_missing.mkc", expected_null()},
      {"src/test/fixtures/index_empty_hash.mkc", expected_null()},
  };
  run_vm_tests(tests, sizeof(tests) / sizeof(tests[0]));
}

static void test_calling_functions_without_arguments(void) {
  VmTestCase tests[] = {
      {"src/test/fixtures/function_call_no_args.mkc", expected_integer(15)},
      {"src/test/fixtures/function_call_multiple_no_args.mkc",
       expected_integer(3)},
      {"src/test/fixtures/function_call_nested_no_args.mkc",
       expected_integer(3)},
      {"src/test/fixtures/function_early_return.mkc", expected_integer(99)},
      {"src/test/fixtures/function_double_return.mkc", expected_integer(99)},
      {"src/test/fixtures/function_first_class.mkc", expected_integer(1)},
  };

  run_vm_tests(tests, sizeof(tests) / sizeof(tests[0]));
}

static void test_calling_functions_with_bindings(void) {
  VmTestCase tests[] = {
      {"src/test/fixtures/function_local_binding.mkc", expected_integer(1)},
      {"src/test/fixtures/function_local_bindings_sum.mkc",
       expected_integer(3)},
      {"src/test/fixtures/function_multiple_with_locals.mkc",
       expected_integer(10)},
      {"src/test/fixtures/function_same_local_name.mkc", expected_integer(150)},
      {"src/test/fixtures/function_global_and_local.mkc", expected_integer(97)},
      {"src/test/fixtures/function_first_class_local.mkc", expected_integer(1)},
  };

  run_vm_tests(tests, sizeof(tests) / sizeof(tests[0]));
}

static void test_calling_functions_with_arguments_and_bindings(void) {
  VmTestCase tests[] = {
      {"src/test/fixtures/function_arg_identity.mkc", expected_integer(4)},
      {"src/test/fixtures/function_arg_sum.mkc", expected_integer(3)},
      {"src/test/fixtures/function_arg_sum_local.mkc", expected_integer(3)},
      {"src/test/fixtures/function_arg_sum_twice.mkc", expected_integer(10)},
      {"src/test/fixtures/function_arg_outer.mkc", expected_integer(10)},
      {"src/test/fixtures/function_arg_globals.mkc", expected_integer(50)},
  };

  run_vm_tests(tests, sizeof(tests) / sizeof(tests[0]));
}

static void test_calling_functions_with_wrong_arguments(void) {
  VmErrorTestCase tests[] = {
      {"src/test/fixtures/function_wrong_args_0_1.mkc",
       VM_ERR_WRONG_NUMBER_OF_ARGUMENTS},  // fn(){1}(1);
      {"src/test/fixtures/function_wrong_args_1_0.mkc",
       VM_ERR_WRONG_NUMBER_OF_ARGUMENTS},  // fn(a){a}();
      {"src/test/fixtures/function_wrong_args_2_1.mkc",
       VM_ERR_WRONG_NUMBER_OF_ARGUMENTS},  // fn(a,b){a+b}(1);
  };

  run_vm_error_tests(tests, sizeof(tests) / sizeof(tests[0]));
}

static void test_builtin_functions(void) {
  VmTestCase tests[] = {
      {"src/test/fixtures/builtin_len_empty_string.mkc", expected_integer(0)},
      {"src/test/fixtures/builtin_len_four.mkc", expected_integer(4)},
      {"src/test/fixtures/builtin_len_hello_world.mkc", expected_integer(11)},
      {"src/test/fixtures/builtin_len_int.mkc",
       expected_error("argument to `len` not supported")},
      {"src/test/fixtures/builtin_len_two_args.mkc",
       expected_error("wrong number of arguments. want=1")},
      {"src/test/fixtures/builtin_len_array.mkc", expected_integer(3)},
      {"src/test/fixtures/builtin_len_empty_array.mkc", expected_integer(0)},
      {"src/test/fixtures/builtin_puts.mkc", expected_null()},
      {"src/test/fixtures/builtin_first_array.mkc", expected_integer(1)},
      {"src/test/fixtures/builtin_first_empty.mkc", expected_null()},
      {"src/test/fixtures/builtin_first_int.mkc",
       expected_error("argument to `first` must be MARRAY")},
      {"src/test/fixtures/builtin_last_array.mkc", expected_integer(3)},
      {"src/test/fixtures/builtin_last_empty.mkc", expected_null()},
      {"src/test/fixtures/builtin_last_int.mkc",
       expected_error("argument to `last` must be MARRAY")},
      {"src/test/fixtures/builtin_rest_array.mkc",
       expected_array((int64_t[]){2, 3}, 2)},
      {"src/test/fixtures/builtin_rest_empty.mkc", expected_null()},
      {"src/test/fixtures/builtin_push_empty.mkc",
       expected_array((int64_t[]){1}, 1)},
      {"src/test/fixtures/builtin_push_int.mkc",
       expected_error("argument to `push` must be MARRAY")},
      // double-free regression: first/last return already-tracked objects
      {"src/test/fixtures/builtin_first_string_in_array.mkc",
       expected_string("hello")},
      {"src/test/fixtures/builtin_last_string_in_array.mkc",
       expected_string("hello")},
      {"src/test/fixtures/builtin_first_nested_array.mkc",
       expected_array((int64_t[]){1}, 1)},
      {"src/test/fixtures/builtin_last_nested_array.mkc",
       expected_array((int64_t[]){1}, 1)},
      {"src/test/fixtures/builtin_first_concat_in_array.mkc",
       expected_string("ab")},
      // malloc(0) regression: rest of single-element array
      {"src/test/fixtures/builtin_rest_single.mkc",
       expected_array((int64_t[]){}, 0)},
  };
  run_vm_tests(tests, sizeof(tests) / sizeof(tests[0]));
}

static void test_stack_underflow(void) {
  uint8_t instructions[] = {
      OP_POP,
  };

  ByteCode bc = {
      .instructions = instructions,
      .num_instructions = sizeof(instructions),
      .constants = NULL,
      .num_constants = 0,
  };

  VM *vm = vm_init(&bc);
  assert(vm != NULL);
  assert(vm_run(vm) == VM_ERR_STACK_UNDERFLOW);
  vm_free(vm);
}

int main(void) {
  test_stack_underflow();
  test_integer_arithmetic();
  test_boolean_expressions();
  test_conditionals();
  test_global_let_statements();
  test_string_expressions();
  test_array_literals();
  test_hash_literals();
  test_index_expressions();
  test_calling_functions_without_arguments();
  test_calling_functions_with_bindings();
  test_calling_functions_with_arguments_and_bindings();
  test_calling_functions_with_wrong_arguments();
  test_builtin_functions();
  return 0;
}
