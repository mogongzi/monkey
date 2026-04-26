#include "../../../main/c/vm/mkc.h"
#include "../../../main/c/vm/vm.h"
#include <assert.h>
#include <stdio.h>

typedef struct
{
  MObjectType type;
  union
  {
    int64_t integer;
    bool boolean;
  } value;
} ExpectedObject;

typedef struct
{
  const char *fixture_path;
  ExpectedObject expected;
} VmTestCase;

static ExpectedObject expected_integer(int64_t value)
{
  return (ExpectedObject){.type = MINTEGER, .value.integer = value};
}

static ExpectedObject expected_boolean(bool value)
{
  return (ExpectedObject){.type = MBOOLEAN, .value.boolean = value};
}

static void test_integer_object(const MObject *obj, int64_t expected)
{
  assert(obj != NULL && "expected integer object, got NULL");
  assert(obj->type == MINTEGER && "object is not an Integer");
  assert(obj->as.integer == expected && "integer value mismatch");
}

static void test_boolean_object(const MObject *obj, bool expected)
{
  assert(obj != NULL && "expected boolean object, got NULL");
  assert(obj->type == MBOOLEAN && "object is not a Boolean");
  assert(obj->as.boolean == expected && "boolean value mismatch");
}

static void test_expected_object(ExpectedObject expected, const MObject *actual)
{
  switch (expected.type)
  {
  case MINTEGER:
    test_integer_object(actual, expected.value.integer);
    break;
  case MBOOLEAN:
    test_boolean_object(actual, expected.value.boolean);
    break;
  default:
    assert(false && "unhandled expected object type");
  }
}

static void run_vm_tests(VmTestCase *tests, int count)
{
  for (int i = 0; i < count; i++)
  {
    FILE *f = fopen(tests[i].fixture_path, "rb");
    assert(f != NULL);

    MkcBytecode bc;
    assert(mkc_read(f, &bc) == 0);
    fclose(f);
    VM *vm = vm_init(&bc);
    assert(vm_run(vm) == VM_OK && "vm_run failed");
    test_expected_object(tests[i].expected, vm_last_popped_stack_elem(vm));
    vm_free(vm);
    mkc_free(&bc);
    printf("  PASS  %s\n", tests[i].fixture_path);
  }
}

static void test_integer_arithmetic(void)
{
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
  };
  run_vm_tests(tests, sizeof(tests) / sizeof(tests[0]));
}

static void test_boolean_expressions(void)
{
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
  };
  run_vm_tests(tests, sizeof(tests) / sizeof(tests[0]));
}

int main(void)
{
  test_integer_arithmetic();
  test_boolean_expressions();
  return 0;
}