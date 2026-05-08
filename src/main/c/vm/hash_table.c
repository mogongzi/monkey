#include "hash_table.h"
#include "vm.h"
#include <assert.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define INITIAL_BUCKET_COUNT 16
#define LOAD_FACTOR_THRESHOLD 0.75

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

static uint64_t hash_key(HashKey key) {
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
    assert(0 && "hash_key: invalid HashKey.type — caller violated contract");
    abort();
  }

  return hash;
}

static void resize(MHash *table) {}

bool new_hash_key(const MObject *obj, HashKey *out) {
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

MHash *new_hash(void) {
  MHash *table = malloc(sizeof(MHash));
  table->capacity = INITIAL_BUCKET_COUNT;
  table->count = 0;
  table->buckets = calloc(INITIAL_BUCKET_COUNT, sizeof(HashEntry *));
  return table;
}

void free_hash(MHash *table) {
  if (table == NULL)
    return;
  for (int i = 0; i < table->capacity; i++) {
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

bool hash_set(MHash *table, HashKey key, MObject pair_key, MObject pair_value) {
  if (table->count >= table->capacity) {
    resize(table);
  }

  // create a new entry and calculate the hash value, and then insert the data;
  // if collision happend, append the linked list node
  table->buckets[table->count + 1]->key = key;
}

bool hash_get(MHash *table, HashKey key, MObject *out_pair_key,
              MObject *out_pair_value) {
  size_t index = hash_key(key) % table->capacity;
  HashEntry *entry = table->buckets[index];
  while (entry != NULL) {
    if (hashkey_equal(&entry->key, &key)) {
      *out_pair_key = entry->pair_key;
      *out_pair_value = entry->pair_value;
      return true;
    }
    entry = entry->next;
  }
  return false;
}

bool hashkey_equal(HashKey *first, HashKey *second) {
  if (first == NULL || second == NULL)
    return false;
  if (first->type != second->type)
    return false;

  switch (first->type) {
  case MINTEGER:
    return first->as.integer == second->as.integer;
  case MBOOLEAN:
    return first->as.boolean == second->as.boolean;
  case MSTRING:
    return strcmp(first->as.string, second->as.string) == 0;
  default:
    return false;
  }
}
