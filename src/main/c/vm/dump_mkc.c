#include "mkc.h"
#include <stdio.h>
#include <inttypes.h>

static void dump(const MkcBytecode *bc)
{
  printf("constants: %u\n", bc->num_constants);
  for (uint16_t i = 0; i < bc->num_constants; i++)
  {
    const MkcConstant *c = &bc->constants[i];
    switch (c->tag)
    {
    case TAG_INTEGER:
      printf("  [%u] INTEGER %" PRId64 "\n", i, c->as.integer);
      break;
    default:
      printf("  [%u] UNKNOW tag=0x%02x\n", i, c->tag);
      break;
    }
  }
  printf("instructions: %u bytes\n", bc->num_instructions);
  printf("  ");
  for (uint32_t i = 0; i < bc->num_instructions; i++)
  {
    printf("%02x ", bc->instructions[i]);
  }
  printf("\n");
}

int main(void)
{
  MkcBytecode bc;
  if (mkc_read(stdin, &bc) != 0)
    return 1;
  dump(&bc);
  mkc_free(&bc);
  return 0;
}