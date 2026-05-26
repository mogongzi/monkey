#include "bytes.h"

#include <stdint.h>

uint16_t read_u16(const uint8_t *buf) {
  return (uint16_t)((buf[0]) << 8 | buf[1]);
}

uint32_t read_u32(const uint8_t *buf) {
  return ((uint32_t)buf[0] << 24) | ((uint32_t)buf[1] << 16) |
         ((uint32_t)buf[2] << 8) | ((uint32_t)buf[3]);
}

int64_t read_i64(const uint8_t *buf) {
  uint64_t u = 0;
  for (int i = 0; i < 8; i++) {
    u = (u << 8) | (uint64_t)buf[i];
  }

  const uint64_t sign_bit = UINT64_C(1) << 63;
  if ((u & sign_bit) == 0) {
    return (int64_t)u;
  }
  return INT64_MIN + (int64_t)(u - sign_bit);
}
