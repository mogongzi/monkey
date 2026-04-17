#include "vm.h"
#include "opcodes.h"
#include <stdio.h>
#include <stdlib.h>

VM *vm_init(const MkcBytecode *bc)
{
  VM *vm = malloc(sizeof(VM));
  if (!vm)
    return NULL;
  vm->bc = bc;
  vm->sp = 0;
  return vm;
}

void vm_free(VM *vm)
{
  if (!vm)
    return;
  free(vm);
}

const MkcConstant *vm_stack_top(const VM *vm)
{
  if (vm->sp == 0)
    return NULL;
  return &(vm->stack[vm->sp - 1]);
}

void vm_run(VM *vm)
{
  for (uint32_t ip = 0; ip < vm->bc->num_instructions; ip++)
  {
    uint8_t op = vm->bc->instructions[ip];
    switch (op)
    {
    case OP_CONSTANT:
    {
      uint32_t idx = (vm->bc->instructions[ip + 1] << 8) | vm->bc->instructions[ip + 2];
      vm->stack[vm->sp++] = vm->bc->constants[idx];
      ip += 2;
      break;
    }
    default:
      fprintf(stderr, "unknown opcode 0x%02x at ip=%u\n", op, ip);
      return;
    }
  }
}