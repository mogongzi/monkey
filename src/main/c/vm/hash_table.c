#include "hash_table.h"
#include "vm.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define INITIAL_BUCKET_COUNT 16
#define LOAD_FACTOR_THRESHOLD 0.75

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

bool hash_set(MHash *table, HashKey key, MObject value) {
  if (table->count >= table->capacity) {
    resize(table);
  }

  table->buckets[table->count + 1]->key = key;
}

bool hash_get(MHash *table, HashKey key, MObject *out_value) {}

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
