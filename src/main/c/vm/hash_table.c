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
    state *= 0x100000001b3ULL; // FNV prime
  }
  return state;
}

static uint64_t fnv1a_hash(const void *data, size_t len) {
  return fnv1a_step(0xcbf29ce484222325ULL, data, len); // FNV offset basis
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

static void resize(MHash *table) {
  // 1. create a new array of buckets which size is twice of the current one.
  size_t new_capacity = table->capacity * 2;
  HashEntry **new_bucket = calloc(new_capacity, sizeof(HashEntry *));

  // 2. iterate over the old array and recalculate the hash key and insert it
  // into new one.
  for (size_t i = 0; i < table->capacity; i++) {
    HashEntry *entry = table->buckets[i];
    while (entry) {
      size_t index = compute_hash(entry->hash_key) % new_capacity;
      // what if two entries from different old buckets land in the same new
      // bucket
      HashEntry *next = entry->next;   // save old chain
      entry->next = new_bucket[index]; // prepend to new chain
      new_bucket[index] = entry;
      entry = next;
    }
  }

  // 3. free old bucket. change the pointer to the new one.
  free(table->buckets);
  table->buckets = new_bucket;
  table->capacity = new_capacity;
}

bool hashkey_equal(HashKey first, HashKey second) {
  if (first.type != second.type)
    return false;

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

MHash *new_hash(int capacity) {
  if (capacity == 0) {
    capacity = INITIAL_BUCKET_COUNT;
  }
  MHash *table = malloc(sizeof(MHash));
  table->capacity = capacity;
  table->count = 0;
  table->buckets = calloc(capacity, sizeof(HashEntry *));
  return table;
}

void free_hash(MHash *table) {
  if (table == NULL)
    return;
  for (size_t i = 0; i < table->capacity; i++) {
    HashEntry *entry = table->buckets[i];
    while (entry) {
      HashEntry *next = entry->next;
      free(entry);
      entry = next;
    }
  }
  free(table->buckets);
  table->buckets = NULL;
  table->capacity = 0;
  table->count = 0;
  free(table);
}

bool hash_set(MHash *table, HashKey key, HashPair pair) {
  if (table->count * 4 >=
      table->capacity * 3) { // equivalent to count >= capacity * 0.75
    resize(table);
  }
  size_t index = compute_hash(key) % table->capacity;
  HashEntry *entry = table->buckets[index];
  while (entry != NULL) {
    if (hashkey_equal(entry->hash_key, key)) {
      // same key -> update existing entry
      entry->pair = pair;
      return true;
    }
    entry = entry->next; // different key, same bucket, hash collision -> keep
                         // walking
  }
  entry = malloc(sizeof(HashEntry));
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
