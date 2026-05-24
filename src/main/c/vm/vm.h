#ifndef VM_H
#define VM_H

#include "bytecode.h"
#include "frame.h"
#include "object.h"
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define STACK_SIZE 2048
#define GLOBALS_SIZE 65536
#define MAX_FRAME_SIZE 1024

typedef struct
{
  const ByteCode *bc;
  MCompiledFunction main_fn;
  const MObject *constants;
  MObject stack[STACK_SIZE];
  MObject globals[GLOBALS_SIZE];
  uint32_t sp;

  Frame frames[MAX_FRAME_SIZE];
  uint32_t frames_index;

  char **allocated_strings;
  size_t allocated_string_count;
  size_t allocated_string_capacity;

  MArray **allocated_arrays;
  size_t allocated_array_count;
  size_t allocated_array_capacity;

  MHash **allocated_hashes;
  size_t allocated_hash_count;
  size_t allocated_hash_capacity;
} VM;

typedef enum
{
  VM_OK = 0,
  VM_ERR_UNKNOWN_OPCODE,
  VM_ERR_STACK_OVERFLOW,
  VM_ERR_STACK_UNDERFLOW,
  VM_ERR_UNKNOWN_OPERATOR,
  VM_ERR_UNSUPPORTED_TYPE_FOR_NEGATION,
  VM_ERR_OUT_OF_MEMORY,
  VM_ERR_UNHASHABLE_KEY,
} VM_RESULT;

VM *vm_init(const ByteCode *bc);
void vm_free(VM *vm);
const MObject *vm_stack_top(const VM *vm);
const MObject *vm_last_popped_stack_elem(const VM *vm);
VM_RESULT vm_run(VM *vm);
VM_RESULT vm_push(VM *vm, MObject obj);

#endif
