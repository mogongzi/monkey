#include "../../../main/c/vm/mkc.h"
#include "../../../main/c/vm/vm.h"
#include <assert.h>
#include <stdio.h>

typedef enum
{
  EXP_INT,
  EXP_BOOL,
} ExpectedType;

typedef struct
{
  ExpectedType type;
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
  return (ExpectedObject){.type = EXP_INT, .value.integer = value};
}

static ExpectedObject expected_boolean(bool value)
{
  return (ExpectedObject){.type = EXP_BOOL, .value.boolean = value};
}

static void test_integer_object(const MObject *obj, int64_t expected)
{
  assert(obj != NULL);
  assert(obj->type == MINTEGER);
  assert(obj->as.integer == expected);
}

static void test_boolean_object(const MObject *obj, bool expected)
{
  assert(obj != NULL);
  assert(obj->type == MBOOLEAN);
  assert(obj->as.boolean == expected);
}

static void test_expected_object(ExpectedObject expected, const MObject *actual)
{
  switch (expected.type)
  {
  case EXP_INT:
    test_integer_object(actual, expected.value.integer);
    break;
  case EXP_BOOL:
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
    assert(vm_run(vm) == VM_OK);
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
  };
  run_vm_tests(tests, sizeof(tests) / sizeof(tests[0]));
}

int main(void)
{
  test_integer_arithmetic();
  test_boolean_expressions();
  return 0;
}