#include <stdio.h>
#include <time.h>

#include "../../../main/c/vm/mkc.h"
#include "../../../main/c/vm/vm.h"

#define NUM_RUNS 5

int main(int argc, char *argv[]) {
  if (argc < 2) {
    fprintf(stderr, "Usage: %s <file.mkc>\n", argv[0]);
    return 1;
  }

  // load bytecode (not timed)
  FILE *f = fopen(argv[1], "rb");
  ByteCode bc;
  mkc_read(f, &bc);

  double times_ms[NUM_RUNS];

  for (int i = 0; i < NUM_RUNS; i++) {
    VM *vm = vm_init(&bc);

    struct timespec start, end;
    clock_gettime(CLOCK_MONOTONIC, &start);
    VM_RESULT result = vm_run(vm);
    if (result != VM_OK) printf("%d\n", result);
    clock_gettime(CLOCK_MONOTONIC, &end);

    double elapsed_ms = (end.tv_sec - start.tv_sec) * 1000.0 +
                        (end.tv_nsec - start.tv_nsec) / 1000000.0;
    times_ms[i] = elapsed_ms;

    if (i == 0) {
      // Print result for verification
      const MObject *top = vm_last_popped_stack_elem(vm);
      printf("result=%lld\n", top->as.integer);
    }

    vm_free(vm);
  }

  free_bytecode(&bc);

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