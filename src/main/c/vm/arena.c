#include "arena.h"
#include <stdlib.h>

static Block *block_new(size_t capacity) {
  Block *b = malloc(sizeof(Block) + capacity);
  if (!b) return NULL;
  b->next = NULL;
  b->offset = 0;
  b->capacity = capacity;
  return b;
}

Arena arena_new(size_t block_size) {
  // lazy init: allocate nothing now. the first arena_alloc hits the "no current block" path below and creates block 1.
  return (Arena){.current = NULL, .head = NULL, .block_size = block_size};
}

void *arena_alloc(Arena *a, size_t size) {
  size = (size + 7) & ~(size_t)7;
  // current block has room?
  if (a->current && a->current->offset + size <= a->current->capacity) {
    void *ptr = a->current->data + a->current->offset;
    a->current->offset += size;
    return ptr;
  }

  // need a new block - at least block_size, but bigger if the single allocation is larger than a whole block
  size_t capacity = a->block_size;
  if (size > capacity) capacity = size;

  Block *b = block_new(capacity);
  if (!b) return NULL;

  if (a->current) {
    a->current->next = b;  // link into chain (current is always the tail)
  } else {
    a->head = b;  // first block in the chain (lazy init)
  }
  a->current = b;

  void *ptr = b->data + b->offset;
  b->offset += size;
  return ptr;
}

void arena_free_all(Arena *a) {
  Block *b = a->head;
  while (b) {
    Block *next = b->next;
    free(b);  // one free per block
    b = next;
  }
  a->head = NULL;
  a->current = NULL;
}