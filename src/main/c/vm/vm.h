#ifndef VM_H
#define VM_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "arena.h"
#include "bytecode.h"
#include "frame.h"
#include "object.h"

#define STACK_SIZE 2048
#define GLOBALS_SIZE 65536
#define MAX_FRAME_SIZE 1024

typedef struct {
  const ByteCode *bc;
  MCompiledFunction main_fn;
  const MObject *constants;
  MObject stack[STACK_SIZE];
  MObject globals[GLOBALS_SIZE];
  uint32_t sp;

  Frame frames[MAX_FRAME_SIZE];
  uint32_t frames_index;

  Arena arena;
} VM;

typedef enum {
  VM_OK = 0,
  VM_ERR_UNKNOWN_OPCODE,
  VM_ERR_STACK_OVERFLOW,
  VM_ERR_STACK_UNDERFLOW,
  VM_ERR_UNKNOWN_OPERATOR,
  VM_ERR_UNSUPPORTED_TYPE_FOR_NEGATION,
  VM_ERR_OUT_OF_MEMORY,
  VM_ERR_INVALID_HASH_KEY,
  VM_ERR_NON_FUNCTION_CALL,
  VM_ERR_DIVISION_BY_ZERO,
  VM_ERR_UNSUPPORTED_TYPE_FOR_COMPARISION,
  VM_ERR_INTEGER_OVERFLOW,
  VM_ERR_WRONG_NUMBER_OF_ARGUMENTS,
} VM_RESULT;

VM *vm_init(const ByteCode *bc);
void vm_free(VM *vm);
const MObject *vm_stack_top(const VM *vm);
const MObject *vm_last_popped_stack_elem(const VM *vm);
VM_RESULT vm_run(VM *vm);
VM_RESULT vm_push(VM *vm, MObject obj);

#endif
