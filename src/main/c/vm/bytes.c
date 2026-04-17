#include "bytes.h"

uint16_t read_u16(const uint8_t *buf)
{
  return (uint16_t)((buf[0]) << 8 | buf[1]);
}

uint32_t read_u32(const uint8_t *buf)
{
  return ((uint32_t)buf[0] << 24) | ((uint32_t)buf[1] << 16) | ((uint32_t)buf[2] << 8) | ((uint32_t)buf[3]);
}

int64_t read_i64(const uint8_t *buf)
{
  uint64_t v = 0;
  for (int i = 0; i < 8; i++)
  {
    v = (v << 8) | buf[i];
  }
  return (int64_t)v;
}