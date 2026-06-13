#include "hash_table.h"

#include <assert.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static uint64_t fnv1a_step(uint64_t state, const void *data, size_t len) {
  const uint8_t *bytes = (const uint8_t *)data;
  for (size_t i = 0; i < len; i++) {
    state ^= bytes[i];
    state *= 0x100000001b3ULL;  // FNV prime
  }
  return state;
}

static uint64_t fnv1a_hash(const void *data, size_t len) {
  return fnv1a_step(0xcbf29ce484222325ULL, data, len);  // FNV offset basis
}

static uint64_t compute_hash(HashKey key) {
  uint64_t hash = fnv1a_hash(&key.type, sizeof(key.type));
  switch (key.type) {
    case MINTEGER:
      hash ^= fnv1a_hash(&key.as.integer, sizeof(key.as.integer));
      break;
    case MBOOLEAN:
      hash ^= fnv1a_hash(&key.as.boolean, sizeof(key.as.boolean));
      break;
    case MSTRING:
      hash ^= fnv1a_hash(key.as.string, strlen(key.as.string));
      break;
    default:
      assert(0 &&
             "compute_hash: invalid HashKey.type — caller violated contract");
      abort();
  }

  return hash;
}

static bool resize(MHash *table, Arena *arena) {
  // 1. create a new array of buckets which size is twice of the current one.
  size_t new_capacity = table->capacity * 2;
  HashEntry **new_bucket = arena_alloc(arena, sizeof(HashEntry *));
  if (!new_bucket) {
    fprintf(stderr, "out of memory (Hash)\n");
    return false;
  }
  memset(new_bucket, 0, new_capacity * sizeof(HashEntry *));

  // 2. iterate over the old array and recalculate the hash key and insert it
  // into new one.
  for (size_t i = 0; i < table->capacity; i++) {
    HashEntry *entry = table->buckets[i];
    while (entry) {
      size_t index = compute_hash(entry->hash_key) % new_capacity;
      // what if two entries from different old buckets land in the same new
      // bucket
      HashEntry *next = entry->next;    // save old chain
      entry->next = new_bucket[index];  // prepend to new chain
      new_bucket[index] = entry;
      entry = next;
    }
  }

  // 3. change the pointer to the new one.
  table->buckets = new_bucket;
  table->capacity = new_capacity;
  return true;
}

bool hashkey_equal(HashKey first, HashKey second) {
  if (first.type != second.type) return false;

  switch (first.type) {
    case MINTEGER:
      return first.as.integer == second.as.integer;
    case MBOOLEAN:
      return first.as.boolean == second.as.boolean;
    case MSTRING:
      return strcmp(first.as.string, second.as.string) == 0;
    default:
      return false;
  }
}

bool hashkey_from_mobject(const MObject *obj, HashKey *out) {
  out->type = obj->type;
  switch (obj->type) {
    case MINTEGER:
      out->as.integer = obj->as.integer;
      return true;
    case MBOOLEAN:
      out->as.boolean = obj->as.boolean;
      return true;
    case MSTRING:
      out->as.string = obj->as.string;
      return true;
    default:
      return false;
  }
}

MHash *new_hash(int capacity, Arena *arena) {
  if (capacity == 0) {
    capacity = INITIAL_BUCKET_COUNT;
  }
  MHash *table = arena_alloc(arena, sizeof(MHash));
  if (!table) {
    fprintf(stderr, "out of memory (Hash)\n");
    return NULL;
  }
  table->capacity = capacity;
  table->count = 0;
  table->buckets = arena_alloc(arena, capacity * sizeof(HashEntry *));
  if (!table->buckets) {
    fprintf(stderr, "out of memory (Entries in Hash)\n");
    return NULL;
  }
  memset(table->buckets, 0, capacity * sizeof(HashEntry *));
  return table;
}

bool hash_set(MHash *table, HashKey key, HashPair pair, Arena *arena) {
  if (table->count * 4 >=
      table->capacity * 3) {  // equivalent to count >= capacity * 0.75
    if (!resize(table, arena)) return false;
  }
  size_t index = compute_hash(key) % table->capacity;
  HashEntry *entry = table->buckets[index];
  while (entry != NULL) {
    if (hashkey_equal(entry->hash_key, key)) {
      // same key -> update existing entry
      entry->pair = pair;
      return true;
    }
    entry = entry->next;  // different key, same bucket, hash collision -> keep
                          // walking
  }
  entry = arena_alloc(arena, sizeof(HashEntry));
  if (!entry) {
    fprintf(stderr, "out of memory (Hash Entry)\n");
    return false;
  }
  entry->hash_key = key;
  entry->pair = pair;
  entry->next = table->buckets[index];
  table->buckets[index] = entry;
  table->count++;
  return true;
}

bool hash_get(MHash *table, HashKey key, HashPair *out_pair) {
  size_t index = compute_hash(key) % table->capacity;
  HashEntry *entry = table->buckets[index];
  while (entry != NULL) {
    if (hashkey_equal(entry->hash_key, key)) {
      *out_pair = entry->pair;
      return true;
    }
    entry = entry->next;
  }
  return false;
}
