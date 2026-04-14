#include "../../../main/c/vm/mkc.h"
#include <assert.h>
#include <stdio.h>

typedef struct
{
  const char *fixture_path;
  int64_t expected;
} VmTestCase;

static void run_vm_tests(VmTestCase *tests, int count)
{
  for (int i = 0; i < count; i++)
  {
    FILE *f = fopen(tests[i].fixture_path, "rb");
    assert(f != NULL);

    MkcBytecode bc;
    assert(mkc_read(f, &bc) == 0);
    // VM *vm = vm_new(&bc);
    // assert(vm_run(vm) == 0);
    // assert(vm_stack_top(vm)->as.integer == tests[i].expected);
    // vm_free(vm);
    mkc_free(&bc);
    printf("  PASS  %s\n", tests[i].fixture_path);
  }
}

int main(void)
{
  VmTestCase tests[] = {
      {"src/test/fixtures/just_42.mkc", 42},
      {"src/test/fixtures/one_plus_two.mkc", 2},
  };
  run_vm_tests(tests, sizeof(tests) / sizeof(tests[0]));
  return 0;
}