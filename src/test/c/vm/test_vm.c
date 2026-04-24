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
  const char *fixture_path;
  ExpectedType type;
  union 
  {
    int64_t integer;
    bool boolean;
  } value;
} VmTestCase;

static void test_expected_integer(const MObject *obj, int64_t expected)
{
  assert(obj != NULL);
  assert(obj->type == MINTEGER);
  assert(obj->as.integer == expected);
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
    test_expected_integer(vm_last_popped_stack_elem(vm), tests[i].expected);
    vm_free(vm);
    mkc_free(&bc);
    printf("  PASS  %s\n", tests[i].fixture_path);
  }
}

static void test_integer_arithmetic(void)
{
  VmTestCase tests[] = {
      {"src/test/fixtures/just_one.mkc", 1},
      {"src/test/fixtures/just_two.mkc", 2},
      {"src/test/fixtures/one_plus_two.mkc", 3},
      {"src/test/fixtures/one_minus_two.mkc", -1},
      {"src/test/fixtures/one_times_two.mkc", 2},
      {"src/test/fixtures/four_div_two.mkc", 2},
      {"src/test/fixtures/mixed_arithmetic_1.mkc", 55},
      {"src/test/fixtures/mixed_arithmetic_2.mkc", 10},
      {"src/test/fixtures/power_of_two.mkc", 32},
      {"src/test/fixtures/mul_then_add.mkc", 20},
      {"src/test/fixtures/add_then_mul.mkc", 25},
      {"src/test/fixtures/paren_expr.mkc", 60},
  };
  run_vm_tests(tests, sizeof(tests) / sizeof(tests[0]));
}

static void test_boolean_expressions(void) 
{
    VmTestCase tests[] = {
      {"src/test/fixtures/true.mkc", true},
      {"src/test/fixtures/false.mkc", false},
    };
    run_vm_tests(tests, sizeof(tests) / sizeof(tests[0]));
}

int main(void)
{
  test_integer_arithmetic();
  return 0;
}