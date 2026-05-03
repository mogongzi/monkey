#ifndef VM_H
#define VM_H

#include "mkc.h"
#include <stdbool.h>

#define STACK_SIZE 2048
#define GLOBALS_SIZE 65535

typedef enum
{
  MINTEGER,
  MBOOLEAN,
  MNULL,
  MSTRING,
} MObjectType;

typedef struct
{
  MObjectType type;
  union
  {
    int64_t integer;
    bool boolean;
    char* string;
  } as;
} MObject;

typedef struct
{
  const MkcBytecode *bc;
  MObject *constants;
  MObject stack[STACK_SIZE];
  MObject globals[GLOBALS_SIZE];
  uint32_t sp;

  char **allocated_strings;
  size_t allocated_string_count;
  size_t allocated_string_capacity;
} VM;

typedef enum
{
  VM_OK = 0,
  VM_ERR_UNKNOWN_OPCODE,
  VM_ERR_STACK_OVERFLOW,
  VM_ERR_UNKNOWN_OPERATOR,
  VM_ERR_UNSUPPORT_TYPE_FOR_NEGATION,
  VM_ERR_OUT_OF_MEMORY,
} VM_RESULT;

VM *vm_init(const MkcBytecode *bc);
void vm_free(VM *vm);
const MObject *vm_stack_top(const VM *vm);
const MObject *vm_last_popped_stack_elem(const VM *vm);
VM_RESULT vm_run(VM *vm);
VM_RESULT vm_push(VM *vm, MObject obj);

#endif
