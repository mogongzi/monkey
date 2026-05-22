#ifndef HASH_TABLE_H
#define HASH_TABLE_H

#include "object.h"

#define INITIAL_BUCKET_COUNT 16

typedef struct {
  MObjectType type;
  union {
    int64_t integer;
    bool boolean;
    const char *string;
  } as;
} HashKey;

typedef struct {
    MObject original_key;
    MObject value;
} HashPair ;

typedef struct HashEntry HashEntry;
struct HashEntry {
  HashKey hash_key;
  HashPair pair;
  HashEntry *next; // chaining for hash collision
};

struct MHash {
  HashEntry **buckets; // array of pointers
  size_t capacity;
  size_t count;
};

MHash *new_hash(int capacity);
void free_hash(MHash *table);
bool hash_set(MHash *table, HashKey key, HashPair pair);
bool hash_get(MHash *table, HashKey key, HashPair *out_pair);
bool hashkey_equal(HashKey first, HashKey second);
bool hashkey_from_mobject(const MObject *obj, HashKey *out);

#endif
