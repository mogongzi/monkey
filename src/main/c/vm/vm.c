#include "vm.h"

#include <assert.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "bytecode.h"
#include "bytes.h"
#include "frame.h"
#include "hash_table.h"
#include "object.h"
#include "opcodes.h"

VM *vm_init(const ByteCode *bc) {
  VM *vm = malloc(sizeof(VM));
  if (!vm) return NULL;
  vm->bc = bc;
  vm->sp = 0;
  vm->constants = NULL;

  vm->main_fn.instructions = bc->instructions;
  vm->main_fn.num_instructions = bc->num_instructions;
  vm->frames[0].fn = &vm->main_fn;
  vm->frames[0].ip = 0;
  vm->frames_index = 1;
  if (bc->num_constants > 0) {
    vm->constants = bc->constants;
  }

  vm->allocated_strings = NULL;
  vm->allocated_string_count = 0;
  vm->allocated_string_capacity = 0;

  vm->allocated_arrays = NULL;
  vm->allocated_array_count = 0;
  vm->allocated_array_capacity = 0;

  vm->allocated_hashes = NULL;
  vm->allocated_hash_count = 0;
  vm->allocated_hash_capacity = 0;

  return vm;
}

static Frame *current_frame(VM *vm) {
  return &vm->frames[vm->frames_index - 1];
}

static void push_frame(VM *vm, const MCompiledFunction *fn) {
  if (vm->frames_index >= MAX_FRAME_SIZE) {
    fprintf(stderr, "call stack overflow: exceeded maximum frames of %d\n",
            MAX_FRAME_SIZE);
    abort();
  }
  vm->frames[vm->frames_index++] = (Frame){.fn = fn, .ip = 0};
}

static Frame *pop_frame(VM *vm) { return &vm->frames[--vm->frames_index]; }

static int vm_track_allocated_string(VM *vm, char *value) {
  if (vm->allocated_string_count == vm->allocated_string_capacity) {
    size_t new_capacity = vm->allocated_string_capacity == 0
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

static int vm_track_allocated_array(VM *vm, MArray *array) {
  if (vm->allocated_array_count == vm->allocated_array_capacity) {
    size_t new_capacity = vm->allocated_array_capacity == 0
                              ? 16
                              : vm->allocated_array_capacity * 2;
    MArray **new_allocated_arrays =
        realloc(vm->allocated_arrays, sizeof(MArray *) * new_capacity);
    if (!new_allocated_arrays) {
      return -1;
    }

    vm->allocated_arrays = new_allocated_arrays;
    vm->allocated_array_capacity = new_capacity;
  }

  vm->allocated_arrays[vm->allocated_array_count] = array;
  vm->allocated_array_count++;
  return 0;
}

static int vm_track_allocated_hash(VM *vm, MHash *hash) {
  if (vm->allocated_hash_count == vm->allocated_hash_capacity) {
    size_t new_capacity =
        vm->allocated_hash_capacity == 0 ? 16 : vm->allocated_hash_capacity * 2;
    MHash **new_allocated_hashs =
        realloc(vm->allocated_hashes, sizeof(MHash *) * new_capacity);
    if (!new_allocated_hashs) {
      return -1;
    }

    vm->allocated_hashes = new_allocated_hashs;
    vm->allocated_hash_capacity = new_capacity;
  }

  vm->allocated_hashes[vm->allocated_hash_count] = hash;
  vm->allocated_hash_count++;
  return 0;
}

void vm_free(VM *vm) {
  if (!vm) return;
  for (size_t i = 0; i < vm->allocated_string_count; i++) {
    free(vm->allocated_strings[i]);
  }
  free(vm->allocated_strings);

  for (size_t i = 0; i < vm->allocated_array_count; i++) {
    free(vm->allocated_arrays[i]->elements);  // the MObject[] buffer
    free(vm->allocated_arrays[i]);            // the MArray struct itself
  }
  free(vm->allocated_arrays);

  for (size_t i = 0; i < vm->allocated_hash_count; i++) {
    free_hash(vm->allocated_hashes[i]);
  }
  free(vm->allocated_hashes);
  free(vm);
}

const MObject *vm_stack_top(const VM *vm) {
  if (vm->sp == 0) return NULL;
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

static VM_RESULT vm_pop(VM *vm, MObject *out) {
  if (vm->sp == 0) {
    return VM_ERR_STACK_UNDERFLOW;
  }
  vm->sp--;
  *out = vm->stack[vm->sp];
  return VM_OK;
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
  MObject right;
  MObject left;

  VM_RESULT r = vm_pop(vm, &right);
  if (r != VM_OK) return r;
  r = vm_pop(vm, &left);
  if (r != VM_OK) return r;

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

static VM_RESULT vm_execute_integer_comparison(VM *vm, uint8_t opcode,
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
  MObject right;
  MObject left;

  VM_RESULT r = vm_pop(vm, &right);
  if (r != VM_OK) return r;
  r = vm_pop(vm, &left);
  if (r != VM_OK) return r;

  if (left.type == MINTEGER && right.type == MINTEGER) {
    return vm_execute_integer_comparison(vm, opcode, left.as.integer,
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
  MObject operand;
  VM_RESULT r = vm_pop(vm, &operand);
  if (r != VM_OK) return r;
  if (operand.type != MINTEGER) {
    fprintf(stderr, "unsupported type for negation: %d\n", operand.type);
    return VM_ERR_UNSUPPORTED_TYPE_FOR_NEGATION;
  }
  int64_t value = -(operand.as.integer);
  return vm_push(vm, (MObject){.type = MINTEGER, .as.integer = value});
}

static VM_RESULT vm_exec_bang_operator(VM *vm) {
  MObject operand;
  VM_RESULT r = vm_pop(vm, &operand);
  if (r != VM_OK) return r;

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

static VM_RESULT vm_exec_array_index(VM *vm, MArray *array, uint64_t index) {
  if (index < 0 || index >= array->len)
    return vm_push(vm, (MObject){.type = MNULL});
  return vm_push(vm, array->elements[index]);
}

static VM_RESULT vm_exec_hash_index(VM *vm, MHash *hash, MObject index) {
  HashKey hash_key;
  if (!hashkey_from_mobject(&index, &hash_key)) {
    return VM_ERR_UNHASHABLE_KEY;
  }
  HashPair pair;
  if (hash_get(hash, hash_key, &pair)) {
    return vm_push(vm, pair.value);

  } else {
    return vm_push(vm, (MObject){.type = MNULL});
  }
}

static VM_RESULT vm_exec_index_expression(VM *vm) {
  MObject index;
  MObject left;
  VM_RESULT r = vm_pop(vm, &index);
  if (r != VM_OK) return r;
  r = vm_pop(vm, &left);
  if (r != VM_OK) return r;

  if (left.type == MARRAY && index.type == MINTEGER) {
    return vm_exec_array_index(vm, left.as.array, index.as.integer);
  } else if (left.type == MHASH) {
    return vm_exec_hash_index(vm, left.as.hash, index);
  } else {
    return VM_ERR_UNKNOWN_OPERATOR;
  }
}

static MObject build_array(VM *vm, uint32_t start, uint32_t end) {
  size_t len = end - start;
  MArray *arr = malloc(sizeof(MArray));
  arr->elements = malloc(sizeof(MObject) * len);
  arr->len = len;
  for (size_t i = 0; i < len; i++) {
    arr->elements[i] = vm->stack[start + i];
  }
  return (MObject){.type = MARRAY, .as.array = arr};
}

static VM_RESULT build_hash(VM *vm, uint32_t start, uint32_t end,
                            MObject *out) {
  MHash *hash = new_hash(0);
  for (size_t i = start; i < end; i += 2) {
    MObject key = vm->stack[i];
    MObject value = vm->stack[i + 1];
    HashKey hash_key;
    if (!hashkey_from_mobject(&key, &hash_key)) {
      free_hash(hash);
      return VM_ERR_UNHASHABLE_KEY;
    }

    HashPair pair;
    pair.original_key = key;
    pair.value = value;

    hash_set(hash, hash_key, pair);
  }
  *out = (MObject){.type = MHASH, .as.hash = hash};
  return VM_OK;
}

VM_RESULT vm_run(VM *vm) {
  while (current_frame(vm)->ip < current_frame(vm)->fn->num_instructions) {
    Frame *frame = current_frame(vm);
    const uint8_t *instructions = frame->fn->instructions;
    uint8_t opcode = instructions[frame->ip];
    frame->ip++;
    switch (opcode) {
      case OP_CONSTANT: {
        uint32_t idx = read_u16(&instructions[frame->ip]);
        VM_RESULT result = vm_push(vm, vm->constants[idx]);
        if (result != VM_OK) {
          return result;
        }
        frame->ip += 2;
        break;
      }
      case OP_POP: {
        MObject popped;
        VM_RESULT r = vm_pop(vm, &popped);
        if (r != VM_OK) return r;
        break;
      }
      case OP_ADD:
      case OP_SUB:
      case OP_MUL:
      case OP_DIV: {
        VM_RESULT r = vm_exec_binary_op(vm, opcode);
        if (r != VM_OK) return r;
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
        if (r != VM_OK) return r;
        break;
      }
      case OP_MINUS: {
        VM_RESULT r = vm_exec_minus_operator(vm);
        if (r != VM_OK) return r;
        break;
      }
      case OP_BANG: {
        VM_RESULT r = vm_exec_bang_operator(vm);
        if (r != VM_OK) return r;
        break;
      }
      case OP_JUMP: {
        uint32_t pos = read_u16(&instructions[frame->ip]);
        frame->ip = pos;
        break;
      }
      case OP_JUMP_NOT_TRUTHY: {
        uint32_t pos = read_u16(&instructions[frame->ip]);
        frame->ip += 2;
        MObject condition;
        VM_RESULT r = vm_pop(vm, &condition);
        if (r != VM_OK) return r;
        if (!mobject_is_truthy(
                &condition))  // skip to 'pos' if top-of-stack is falsy
        {
          frame->ip = pos;
        }
        break;
      }
      case OP_NULL: {
        VM_RESULT r = vm_push(vm, (MObject){.type = MNULL});
        if (r != VM_OK) return r;
        break;
      }
      case OP_SET_GLOBAL: {
        uint32_t global_index = read_u16(&instructions[frame->ip]);
        frame->ip += 2;
        VM_RESULT r = vm_pop(vm, &vm->globals[global_index]);
        if (r != VM_OK) return r;
        break;
      }
      case OP_GET_GLOBAL: {
        uint32_t global_index = read_u16(&instructions[frame->ip]);
        frame->ip += 2;
        VM_RESULT r = vm_push(vm, vm->globals[global_index]);
        if (r != VM_OK) return r;
        break;
      }
      case OP_ARRAY: {
        uint32_t num_elements = read_u16(&instructions[frame->ip]);
        frame->ip += 2;
        MObject obj = build_array(vm, (vm->sp - num_elements), vm->sp);
        vm->sp = vm->sp - num_elements;
        VM_RESULT r = vm_push(vm, obj);
        if (vm_track_allocated_array(vm, obj.as.array) != 0) {
          free(obj.as.array->elements);  // don't forget this one
          free(obj.as.array);
          return VM_ERR_OUT_OF_MEMORY;
        }
        if (r != VM_OK) return r;
        break;
      }
      case OP_HASH: {
        uint32_t num_hashes = read_u16(&instructions[frame->ip]);
        frame->ip += 2;
        MObject obj;
        VM_RESULT r = build_hash(vm, (vm->sp - num_hashes), vm->sp, &obj);
        if (r != VM_OK) return r;
        vm->sp = vm->sp - num_hashes;
        r = vm_push(vm, obj);
        if (vm_track_allocated_hash(vm, obj.as.hash) != 0) {
          free_hash(obj.as.hash);
          return VM_ERR_OUT_OF_MEMORY;
        }
        if (r != VM_OK) return r;
        break;
      }
      case OP_INDEX: {
        VM_RESULT r = vm_exec_index_expression(vm);
        if (r != VM_OK) return r;
        break;
      }
      case OP_CALL: {
        MObject callee = vm->stack[vm->sp - 1];
        if (callee.type != MCOMPILED_FUNCTION) return VM_ERR_NON_FUNCTION_CALL;
        MCompiledFunction *fn = callee.as.function;
        push_frame(vm, fn);
        break;
      }
      case OP_RETURN_VALUE: {
        MObject returned_value;
        VM_RESULT r = vm_pop(vm, &returned_value);
        if (r != VM_OK) return r;
        pop_frame(vm);
        /**
         * ─── before OpCall ──────────────────────────────
         * sp →                  (free)
         *                       CompiledFunction  ← pushed by OpGetGlobal
         *                       ...
         * ─── after OpCall (function body about to run) ──
         * sp →                  (free)
         *                       CompiledFunction  ← still here, untouched
         *                       ...
         * ─── after function body executes 5 + 10 ───────
         * sp →                  (free)
         *                       15                ← return value
         *                       CompiledFunction  ← STILL here!
         *                       ...
         * ─── OpReturnValue cleanup ─────────────────────
         * Step 1: pop returned_value           → 15
         * Step 2: pop_frame                    → discard call frame
         * Step 3: vm_pop(&_)  ← THIS ONE       → discards the leftover CompiledFunction
         * Step 4: push(returned_value)         → put 15 where the fn used to be
         * ─── final state ───────────────────────────────
         * sp →                  (free)
         *                       15                ← caller now sees the return value
         *                      ...                 in the slot the fn used to occupy
         */
        MObject leftover_compiled_function;
        r = vm_pop(vm, &leftover_compiled_function);
        if (r != VM_OK) return r;
        r = vm_push(vm, returned_value);
        if (r != VM_OK) return r;
        break;
      }
      case OP_RETURN: {
        pop_frame(vm);
        MObject _;
        VM_RESULT r;
        r = vm_pop(vm, &_);
        if (r != VM_OK) return r;
        r = vm_push(vm, (MObject){.type = MNULL});
        if (r != VM_OK) return r;
        break;
      }
      default:
        fprintf(stderr, "unknown opcode 0x%02x at ip=%u\n", opcode,
                frame->ip - 1);
        return VM_ERR_UNKNOWN_OPCODE;
    }
  }
  return VM_OK;
}
