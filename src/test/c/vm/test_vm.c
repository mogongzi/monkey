#include "../../../main/c/vm/mkc.h"
#include "../../../main/c/vm/vm.h"
#include <assert.h>
#include <stdio.h>

typedef struct
{
  const char *fixture_path;
  int64_t expected;
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
    test_expected_integer(vm_stack_top(vm), tests[i].expected);
    vm_free(vm);
    mkc_free(&bc);
    printf("  PASS  %s\n", tests[i].fixture_path);
  }
}

int main(void)
{
  VmTestCase tests[] = {
      {"src/test/fixtures/just_one.mkc", 1},
      {"src/test/fixtures/just_two.mkc", 2},
      {"src/test/fixtures/one_plus_two.mkc", 2},
  };
  run_vm_tests(tests, sizeof(tests) / sizeof(tests[0]));
  return 0;
}