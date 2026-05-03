#include "vm.h"
#include "bytes.h"
#include "mkc.h"
#include "opcodes.h"
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int mobject_from_mkc_constant(const MkcConstant *constant,
                                     MObject *out) {
  switch (constant->tag) {
  case TAG_INTEGER:
    out->type = MINTEGER;
    out->as.integer = constant->as.integer;
    return 0;
  case TAG_STRING:
    out->type = MSTRING;
    out->as.string = constant->as.string.value;
    return 0;
  default:
    return -1;
  }
}

VM *vm_init(const MkcBytecode *bc) {
  VM *vm = malloc(sizeof(VM));
  if (!vm)
    return NULL;
  vm->bc = bc;
  vm->sp = 0;
  vm->constants = NULL;
  if (bc->num_constants > 0) {
    vm->constants = malloc(sizeof(MObject) * bc->num_constants);
    if (!vm->constants) {
      free(vm);
      return NULL;
    }

    for (uint16_t i = 0; i < bc->num_constants; i++) {
      if (mobject_from_mkc_constant(&bc->constants[i], &vm->constants[i]) !=
          VM_OK) {
        free(vm->constants);
        free(vm);
        return NULL;
      }
    }
  }

  vm->allocated_strings = NULL;
  vm->allocated_string_count = 0;
  vm->allocated_string_capacity = 0;

  return vm;
}

static int vm_track_allocated_string(VM *vm, char *value) {
  if (vm->allocated_string_count == vm->allocated_string_capacity) {
    uint32_t new_capacity = vm->allocated_string_capacity == 0
                                ? 16
                                : vm->allocated_string_capacity * 2;
    char **new_allocated_strings =
        realloc(vm->allocated_strings, sizeof(char *) * new_capacity);
    if (!new_allocated_strings) {
      return -1;
    }

    vm->allocated_strings = new_allocated_strings;
    vm->allocated_string_capacity = new_capacity;
  }

  vm->allocated_strings[vm->allocated_string_count] = value;
  vm->allocated_string_count++;
  return 0;
}

void vm_free(VM *vm) {
  if (!vm)
    return;
  for (uint32_t i = 0; i < vm->allocated_string_count; i++) {
    free(vm->allocated_strings[i]);
  }

  free(vm->allocated_strings);
  free(vm->constants);
  free(vm);
}

const MObject *vm_stack_top(const VM *vm) {
  if (vm->sp == 0)
    return NULL;
  return &(vm->stack[vm->sp - 1]);
}

const MObject *vm_last_popped_stack_elem(const VM *vm) {
  return &vm->stack[vm->sp];
}

VM_RESULT vm_push(VM *vm, MObject obj) {
  if (vm->sp >= STACK_SIZE) {
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

static VM_RESULT vm_exec_binary_integer_operation(VM *vm, uint8_t opcode,
                                                  int64_t left, int64_t right) {
  int64_t result;

  switch (opcode) {
  case OP_ADD:
    result = left + right;
    break;
  case OP_SUB:
    result = left - right;
    break;
  case OP_MUL:
    result = left * right;
    break;
  case OP_DIV:
    result = left / right;
    break;
  default:
    fprintf(stderr, "unknown integer operator: %d\n", opcode);
    return VM_ERR_UNKNOWN_OPERATOR;
  }
  // the below 3 lines can be rewrite in C99: compound literal
  // MObject obj;
  // obj.type = MINTEGER;
  // obj.as.integer = result;
  MObject obj = {.type = MINTEGER, .as.integer = result};
  return vm_push(vm, obj);
}

static VM_RESULT vm_exec_string_concat(VM *vm, MObject left, MObject right) {
  size_t left_len = strlen(left.as.string);
  size_t right_len = strlen(right.as.string);
  size_t result_len = left_len + right_len;

  char *result = malloc(result_len + 1);
  if (!result) {
    return VM_ERR_OUT_OF_MEMORY;
  }

  memcpy(result, left.as.string, left_len);
  memcpy(result + left_len, right.as.string, right_len);
  result[result_len] = '\0';

  if (vm_track_allocated_string(vm, result) != 0) {
    free(result);
    return VM_ERR_OUT_OF_MEMORY;
  }

  return vm_push(vm, (MObject){.type = MSTRING, .as.string = result});
}

static VM_RESULT vm_exec_binary_op(VM *vm, uint8_t opcode) {
  MObject right = vm_pop(vm);
  MObject left = vm_pop(vm);

  if (left.type == MINTEGER && right.type == MINTEGER) {
    return vm_exec_binary_integer_operation(vm, opcode, left.as.integer,
                                            right.as.integer);
  }

  if (left.type == MSTRING && right.type == MSTRING) {
    if (opcode != OP_ADD) {
      fprintf(stderr, "unknown string operator: %d\n", opcode);
      return VM_ERR_UNKNOWN_OPERATOR;
    }
    return vm_exec_string_concat(vm, left, right);
  }

  fprintf(stderr, "unsupported types for binary operator: %d %d\n", left.type,
          right.type);
  return VM_ERR_UNKNOWN_OPERATOR;
}

static VM_RESULT vm_execute_integer_comparision(VM *vm, uint8_t opcode,
                                                int64_t left, int64_t right) {
  bool result;
  switch (opcode) {
  case OP_EQUAL:
    result = (left == right);
    break;
  case OP_NOT_EQUAL:
    result = (left != right);
    break;
  case OP_GREATER_THAN:
    result = (left > right);
    break;
  default:
    fprintf(stderr, "unknown operator: %d\n", opcode);
    return VM_ERR_UNKNOWN_OPERATOR;
  }
  return vm_push(vm, (MObject){.type = MBOOLEAN, .as.boolean = result});
}

static VM_RESULT vm_execute_comparison(VM *vm, uint8_t opcode) {
  MObject right = vm_pop(vm);
  MObject left = vm_pop(vm);

  if (left.type == MINTEGER && right.type == MINTEGER) {
    return vm_execute_integer_comparision(vm, opcode, left.as.integer,
                                          right.as.integer);
  }

  bool result;
  switch (opcode) {
  case OP_EQUAL:
    result = (left.as.boolean == right.as.boolean);
    break;
  case OP_NOT_EQUAL:
    result = (left.as.boolean != right.as.boolean);
    break;
  default:
    fprintf(stderr, "unknown operator: %d (%d %d)\n", opcode, left.type,
            right.type);
    return VM_ERR_UNKNOWN_OPERATOR;
  }
  return vm_push(vm, (MObject){.type = MBOOLEAN, .as.boolean = result});
}

static VM_RESULT vm_exec_minus_operator(VM *vm) {
  MObject operand = vm_pop(vm);
  if (operand.type != MINTEGER) {
    fprintf(stderr, "unsupported type for negation: %d", operand.type);
    return VM_ERR_UNSUPPORT_TYPE_FOR_NEGATION;
  }
  int64_t value = -(operand.as.integer);
  return vm_push(vm, (MObject){.type = MINTEGER, .as.integer = value});
}

static VM_RESULT vm_exec_bang_operator(VM *vm) {
  MObject operand = vm_pop(vm);
  bool result;
  switch (operand.type) {
  case MBOOLEAN:
    result = !operand.as.boolean;
    break;
  case MNULL:
    result = true;
    break;
  default:
    result = false;
    break;
  }
  return vm_push(vm, (MObject){.type = MBOOLEAN, .as.boolean = result});
}

static bool is_truthy(MObject *obj) {
  switch (obj->type) {
  case MBOOLEAN:
    return obj->as.boolean;
  case MNULL:
    return false;
  case MINTEGER:
    return true;
  default:
    return true;
  }
}

VM_RESULT vm_run(VM *vm) {
  for (uint32_t ip = 0; ip < vm->bc->num_instructions; ip++) {
    uint8_t opcode = vm->bc->instructions[ip];
    switch (opcode) {
    case OP_CONSTANT: {
      uint32_t idx = read_u16(&vm->bc->instructions[ip + 1]);
      VM_RESULT result = vm_push(vm, vm->constants[idx]);
      if (result != VM_OK) {
        return result;
      }
      ip += 2;
      break;
    }
    case OP_POP: {
      vm_pop(vm);
      break;
    }
    case OP_ADD:
    case OP_SUB:
    case OP_MUL:
    case OP_DIV: {
      VM_RESULT r = vm_exec_binary_op(vm, opcode);
      if (r != VM_OK)
        return r;
      break;
    }
    case OP_TRUE: {
      MObject obj = {.type = MBOOLEAN, .as.boolean = true};
      VM_RESULT result = vm_push(vm, obj);
      if (result != VM_OK) {
        return result;
      }
      break;
    }
    case OP_FALSE: {
      MObject obj = {.type = MBOOLEAN, .as.boolean = false};
      VM_RESULT result = vm_push(vm, obj);
      if (result != VM_OK) {
        return result;
      }
      break;
    }
    case OP_EQUAL:
    case OP_NOT_EQUAL:
    case OP_GREATER_THAN: {
      VM_RESULT r = vm_execute_comparison(vm, opcode);
      if (r != VM_OK)
        return r;
      break;
    }
    case OP_MINUS:
      vm_exec_minus_operator(vm);
      break;
    case OP_BANG:
      vm_exec_bang_operator(vm);
      break;
    case OP_JUMP: {
      uint32_t pos = read_u16(&vm->bc->instructions[ip + 1]);
      ip = pos - 1;
      break;
    }
    case OP_JUMP_NOT_TRUTHY: {
      uint32_t pos = read_u16(&vm->bc->instructions[ip + 1]);
      ip += 2;
      MObject condition = vm_pop(vm);
      if (!is_truthy(&condition)) // skip to 'pos' if top-of-stack is falsy
      {
        ip = pos - 1;
      }
      break;
    }
    case OP_NULL: {
      VM_RESULT r = vm_push(vm, (MObject){.type = MNULL});
      if (r != VM_OK)
        return r;
      break;
    }
    case OP_SET_GLOBAL: {
      uint32_t global_index = read_u16(vm->bc->instructions + ip + 1);
      ip += 2;
      vm->globals[global_index] = vm_pop(vm);
      break;
    }
    case OP_GET_GLOBAL: {
      uint32_t global_index = read_u16(&vm->bc->instructions[ip + 1]);
      ip += 2;
      VM_RESULT r = vm_push(vm, vm->globals[global_index]);
      if (r != VM_OK)
        return r;
      break;
    }
    default:
      fprintf(stderr, "unknown opcode 0x%02x at ip=%u\n", opcode, ip);
      return VM_ERR_UNKNOWN_OPCODE;
    }
  }

  return VM_OK;
}
