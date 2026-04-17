#ifndef BYTES_H
#define BYTES_H

#include <stdint.h>

uint16_t read_u16(const uint8_t *buf);
uint32_t read_u32(const uint8_t *buf);
int64_t read_i64(const uint8_t *buf);

#endif