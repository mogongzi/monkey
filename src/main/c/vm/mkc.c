#include "bytes.h"
#include "mkc.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int read_exact(FILE *f, uint8_t *buf, size_t n)
{
  return fread(buf, 1, n, f) == n ? 0 : -1;
}

int mkc_read(FILE *f, MkcBytecode *out)
{
  if (!f || !out)
  {
    fprintf(stderr, "mkc: NULL file or output pointer\n");
    return -1;
  }

  memset(out, 0, sizeof(*out));

  // constants pool
  uint8_t const_count_bytes[2];
  if (read_exact(f, const_count_bytes, 2) != 0)
  {
    fprintf(stderr, "mkc: truncated constant count\n");
    goto fail;
  }

  out->num_constants = read_u16(const_count_bytes);
  if (out->num_constants > 0)
  {
    out->constants = calloc(out->num_constants, sizeof(MkcConstant));
    if (!out->constants)
    {
      fprintf(stderr, "mkc: out of memory (constants)\n");
      goto fail;
    }
  }

  for (uint16_t i = 0; i < out->num_constants; i++)
  {
    uint8_t tag;
    if (read_exact(f, &tag, 1) != 0)
    {
      fprintf(stderr, "mkc: truncated constant tag at index %u\n", i);
      goto fail;
    }
    out->constants[i].tag = tag;

    switch (tag)
    {
    case TAG_INTEGER:
    {
      uint8_t int_bytes[8];
      if (read_exact(f, int_bytes, 8) != 0)
      {
        fprintf(stderr, "mkc: truncated integer at index %u\n", i);
        goto fail;
      }
      out->constants[i].as.integer = read_i64(int_bytes);
      break;
    }
    default:
      fprintf(stderr, "mkc: unknow tag 0x%02x at index %u\n", tag, i);
      goto fail;
    }
  }

  // instructions
  uint8_t insn_len_bytes[4];
  if (read_exact(f, insn_len_bytes, 4) != 0)
  {
    fprintf(stderr, "mkc: truncated instruction length\n");
    goto fail;
  }
  out->num_instructions = read_u32(insn_len_bytes);
  if (out->num_instructions > 0)
  {
    out->instructions = malloc(out->num_instructions);
    if (!out->instructions)
    {
      fprintf(stderr, "mkc: out of memory (instructions)\n");
      goto fail;
    }

    if (read_exact(f, out->instructions, out->num_instructions) != 0)
    {
      fprintf(stderr, "mkc: truncated instructions\n");
      goto fail;
    }
  }

  int extra = fgetc(f);
  if (extra != EOF)
  {
    fprintf(stderr, "mkc: trailing bytes after instructions\n");
    goto fail;
  }
  if (ferror(f))
  {
    fprintf(stderr, "mkc: read error after instructions\n");
    goto fail;
  }

  fclose(f);
  return 0;

fail:
  fclose(f);
  mkc_free(out);
  return -1;
}

void mkc_free(MkcBytecode *bc)
{
  free(bc->constants);
  free(bc->instructions);
  memset(bc, 0, sizeof(*bc));
}