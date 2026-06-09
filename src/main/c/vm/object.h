#ifndef OBJECT_H
#define OBJECT_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// forward declarations - just names, no layout yet for resolving circular type
// problem in C.
typedef struct MObject MObject;
typedef struct MArray MArray;
typedef struct MHash MHash; /* full layout lives in hash_table.h */
typedef struct MCompiledFunction MCompiledFunction;
typedef MObject (*BuiltinFn)(MObject *params, size_t num_params);

typedef enum {
  MINTEGER,
  MBOOLEAN,
  MNULL,
  MSTRING,
  MARRAY,
  MHASH,
  MCOMPILED_FUNCTION,
  MBUILTIN,
  MERROR,
} MObjectType;

struct MArray {
  MObject *elements;
  size_t len;
};

struct MCompiledFunction {
  const uint8_t *instructions;
  uint32_t num_instructions;
  uint16_t num_locals;
  uint16_t num_params;
};

struct MObject {
  MObjectType type;
  union {
    int64_t integer;
    bool boolean;
    char *string;
    MArray *array;
    MHash *hash;
    MCompiledFunction *function;
    BuiltinFn builtin;
    char *error;
  } as;
};

bool mobject_is_truthy(MObject *obj);

#endif
