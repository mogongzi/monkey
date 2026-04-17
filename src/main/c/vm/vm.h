#ifndef VM_H
#define VM_H

#include "mkc.h"

#define STACK_SIZE 2048

typedef struct
{
  const MkcBytecode *bc;
  MkcConstant stack[STACK_SIZE];
  uint32_t sp;
} VM;

VM *vm_init(const MkcBytecode *bc);
void vm_free(VM *vm);
const MkcConstant *vm_stack_top(const VM *vm);
void vm_run(VM *vm);

#endif