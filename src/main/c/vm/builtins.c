#include "builtins.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static MObject builtin_len(MObject *args, size_t num_args) {
  if (num_args != 1) {
    return (MObject){.type = MERROR,
                     .as.error = "wrong number of arguments. want=1"};
  }
  if (args[0].type == MSTRING) {
    return (MObject){.type = MINTEGER,
                     .as.integer = (int64_t)strlen(args[0].as.string)};
  }
  if (args[0].type == MARRAY) {
    return (MObject){.type = MINTEGER,
                     .as.integer = (int64_t)args[0].as.array->len};
  }
  return (MObject){.type = MERROR,
                   .as.error = "argument to `len` not supported"};
}

static MObject builtin_first(MObject *args, size_t num_args) {
  if (num_args != 1) {
    return (MObject){.type = MERROR,
                     .as.error = "wrong number of arguments. want=1"};
  }
  if (args[0].type != MARRAY) {
    return (MObject){.type = MERROR,
                     .as.error = "argument to `first` must be MARRAY"};
  }
  MArray *arr = args[0].as.array;
  if (arr->len == 0) {
    return (MObject){.type = MNULL};
  }
  return arr->elements[0];
}

static MObject builtin_last(MObject *args, size_t num_args) {
  if (num_args != 1) {
    return (MObject){.type = MERROR,
                     .as.error = "wrong number of arguments. want=1"};
  }
  if (args[0].type != MARRAY) {
    return (MObject){.type = MERROR,
                     .as.error = "argument to `last` must be MARRAY"};
  }
  MArray *arr = args[0].as.array;
  if (arr->len == 0) {
    return (MObject){.type = MNULL};
  }
  return arr->elements[arr->len - 1];
}

static MObject builtin_puts(MObject *args, size_t num_args) {
  for (size_t i = 0; i < num_args; i++) {
    switch (args[i].type) {
      case MINTEGER:
        printf("%lld\n", args[i].as.integer);
        break;
      case MBOOLEAN:
        printf("%s\n", args[i].as.boolean ? "true" : "false");
        break;
      case MSTRING:
        printf("%s\n", args[i].as.string);
        break;
      case MNULL:
        printf("null\n");
        break;
      default:
        printf("[object]\n");
        break;
    }
  }
  return (MObject){.type = MNULL};
}

static MObject builtin_rest(MObject *args, size_t num_args) {
  if (num_args != 1) {
    return (MObject){.type = MERROR,
                     .as.error = "wrong number of arguments. want=1"};
  }
  if (args[0].type != MARRAY) {
    return (MObject){.type = MERROR,
                     .as.error = "argument to `rest` must be MARRAY"};
  }
  MArray *arr = args[0].as.array;
  if (arr->len == 0) {
    return (MObject){.type = MNULL};
  }
  size_t new_len = arr->len - 1;
  MObject *new_elems = malloc(new_len * sizeof(MObject));
  memcpy(new_elems, &arr->elements[1], new_len * sizeof(MObject));
  MArray *new_arr = malloc(sizeof(MArray));
  *new_arr = (MArray){.elements = new_elems, .len = new_len};
  return (MObject){.type = MARRAY, .as.array = new_arr};
}

static MObject builtin_push(MObject *args, size_t num_args) {
  if (num_args != 2) {
    return (MObject){.type = MERROR,
                     .as.error = "wrong number of arguments. want=2"};
  }
  if (args[0].type != MARRAY) {
    return (MObject){.type = MERROR,
                     .as.error = "argument to `push` must be MARRAY"};
  }
  MArray *arr = args[0].as.array;
  size_t new_len = arr->len + 1;
  MObject *new_elems = malloc(new_len * sizeof(MObject));
  memcpy(new_elems, arr->elements, arr->len * sizeof(MObject));
  new_elems[arr->len] = args[1];
  MArray *new_arr = malloc(sizeof(MArray));
  *new_arr = (MArray){.elements = new_elems, .len = new_len};
  return (MObject){.type = MARRAY, .as.array = new_arr};
}

BuiltinFn builtin_fns[] = {
    builtin_len,  builtin_puts, builtin_first,
    builtin_last, builtin_rest, builtin_push,
};
#define NUM_BUILTINS (sizeof(builtin_fns) / sizeof(builtin_fns[0]))
