#ifndef OPCODES_H
#define OPCODES_H

#include <stdint.h>

#define OP_CONSTANT 0x00
#define OP_ADD 0x01
#define OP_POP 0x02
#define OP_SUB 0x03
#define OP_MUL 0x04
#define OP_DIV 0x05
#define OP_TRUE 0x06
#define OP_FALSE 0x07
#define OP_EQUAL 0x08
#define OP_NOT_EQUAL 0x09
#define OP_GREATER_THAN 0x0A
#define OP_MINUS 0x0B
#define OP_BANG 0x0C
#define OP_JUMP_NOT_TRUTHY 0x0D
#define OP_JUMP 0x0E
#define OP_NULL 0x0F
#define OP_GET_GLOBAL 0x10
#define OP_SET_GLOBAL 0x11
#define OP_ARRAY 0x12

#endif
