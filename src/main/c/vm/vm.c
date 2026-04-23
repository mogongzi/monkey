#include "bytes.h"
#include "vm.h"
#include "opcodes.h"
#include <stdio.h>
#include <stdlib.h>

static int mobject_from_mkc_constant(const MkcConstant *constant, MObject *out)
{
  switch (constant->tag)
  {
  case TAG_INTEGER:
    out->type = MINTEGER;
    out->as.integer = constant->as.integer;
    return 0;
  default:
    return -1;
  }
}

VM *vm_init(const MkcBytecode *bc)
{
  VM *vm = malloc(sizeof(VM));
  if (!vm)
    return NULL;
  vm->bc = bc;
  vm->sp = 0;
  vm->constants = NULL;
  if (bc->num_constants > 0)
  {
    vm->constants = malloc(sizeof(MObject) * bc->num_constants);
    if (!vm->constants)
    {
      free(vm);
      return NULL;
    }

    for (uint16_t i = 0; i < bc->num_constants; i++)
    {
      if (mobject_from_mkc_constant(&bc->constants[i], &vm->constants[i]) != VM_OK)
      {
        free(vm->constants);
        free(vm);
        return NULL;
      }
    }
  }
  return vm;
}

void vm_free(VM *vm)
{
  if (!vm)
    return;
  free(vm->constants);
  free(vm);
}

const MObject *vm_stack_top(const VM *vm)
{
  if (vm->sp == 0)
    return NULL;
  return &(vm->stack[vm->sp - 1]);
}

VM_RESULT vm_push(VM *vm, MObject obj)
{
  if (vm->sp >= STACK_SIZE)
  {
    return VM_ERR_STACK_OVERFLOW;
  }
  vm->stack[vm->sp] = obj;
  vm->sp++;
  return VM_OK;
}

static MObject vm_pop(VM *vm) {
  MObject obj = vm->stack[vm->sp - 1];
  vm->sp--;
  return obj;
}

VM_RESULT vm_run(VM *vm)
{
  for (uint32_t ip = 0; ip < vm->bc->num_instructions; ip++)
  {
    uint8_t op = vm->bc->instructions[ip];
    switch (op)
    {
    case OP_CONSTANT:
    {
      uint32_t idx = read_u16(&vm->bc->instructions[ip + 1]);
      VM_RESULT result = vm_push(vm, vm->constants[idx]);
      if (result != VM_OK)
      {
        return result;
      }
      ip += 2;
      break;
    }
    case OP_ADD:{
      MObject right = vm_pop(vm);
      MObject left = vm_pop(vm);
      int64_t result = left.as.integer + right.as.integer;
      // the below 3 lines can be rewrite in C99: compound literal
      // (MObject){.type = MINTEGER, .as.integer = result}
      // and vm_push(vm, ...)
      MObject obj;
      obj.type = MINTEGER;
      obj.as.integer = result;
      vm_push(vm, obj);
      break;
    }
    default:
      fprintf(stderr, "unknown opcode 0x%02x at ip=%u\n", op, ip);
      return VM_ERR_UNKNOWN_OPCODE;
    }
  }

  return VM_OK;
}