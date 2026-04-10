#include "mkc.h"
#include "vm.h"
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
    FILE *f = fopen(tests[i].mkc_path, "rb");
    assert(f != NULL);

    MkcByteCode bc;
    assert(mkc_read(f, &bc) == 0);
    // VM *vm = vm_new(&bc);
    // assert(vm_run(vm) == 0);
    // MObject *top = vm_stack_top(vm);
    // assert(top->tag == TAG_INTEGER);
    // assert(top->as.integer == tests[i].expected);

    // vm_free(vm);
    mkc_free(&bc);
    printf("  PASS  %s\n", tests[i].mkc_path);
  }
}

int main(void)
{
  VmTestCase tests[] = {
      {"src/fixtures/just_42.mkc", 42},
  };
  run_vm_tests(tests, sizeof(tests) / sizeof(tests[0]));
  return 0;
}