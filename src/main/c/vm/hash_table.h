#ifndef HASH_TABLE_H
#define HASH_TABLE_H

#include "vm.h"

typedef struct {
  MObjectType type;
  union {
    int64_t integer;
    bool boolean;
    const char *string;
  } as;
} HashKey;

typedef struct HashEntry HashEntry;
struct HashEntry {
  HashKey key;
  MObject pair_key;
  MObject pair_value;
  HashEntry *next; // chaining for hash collision
};

typedef struct MHash {
  HashEntry **buckets; // array of pointers
  size_t capacity;
  size_t count;
} MHash;

MHash *new_hash(void);
void free_hash(MHash *table);
bool hash_set(MHash *table, HashKey key, MObject value);
bool hash_get(MHash *table, HashKey key, MObject *out_value);
bool hashkey_equal(HashKey *first, HashKey *second);

#endif
