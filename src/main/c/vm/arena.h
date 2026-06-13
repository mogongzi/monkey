#ifndef ARENA_H
#define ARENA_H

#include <stddef.h>
#include <stdint.h>

typedef struct Block {
  struct Block *next;
  size_t offset;
  size_t capacity;
  uint8_t data[];  // c99 flexible array member
} Block;

typedef struct Arena {
  Block *current;
  Block *head;
  size_t block_size;
} Arena;

Arena arena_new(size_t block_size);
void *arena_alloc(Arena *a, size_t size);
void arena_free_all(Arena *a);

#endif