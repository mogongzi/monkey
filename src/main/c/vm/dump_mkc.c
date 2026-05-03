#include "bytes.h"
#include "mkc.h"
#include "opcodes.h"
#include <inttypes.h>
#include <stdint.h>
#include <stdio.h>

static void dump(const MkcBytecode *bc) {
  // decode constant pool
  printf("constants: %u\n", bc->num_constants);
  for (uint16_t i = 0; i < bc->num_constants; i++) {
    const MkcConstant *c = &bc->constants[i];
    switch (c->tag) {
    case TAG_INTEGER:
      printf("  [%u] INTEGER %" PRId64 "\n", i, c->as.integer);
      break;
    case TAG_STRING:
      printf("  [%u] STRING len=%u value=\"%.*s\"\n", i, c->as.string.byte_len,
             (int)c->as.string.byte_len, c->as.string.value);
      break;
    default:
      printf("  [%u] UNKNOW tag=0x%02x\n", i, c->tag);
      break;
    }
  }

  // decode instructions
  printf("instructions: %u bytes\n", bc->num_instructions);
  uint32_t ip = 0;
  while (ip < bc->num_instructions) {
    uint8_t op = bc->instructions[ip];
    switch (op) {
    case OP_CONSTANT: {
      if (ip + 2 >= bc->num_instructions) {
        printf("  %04u OpConstant <truncated>\n", ip);
        return;
      }

      uint16_t index = read_u16(&bc->instructions[ip + 1]);
      printf("  %04u OpConstant %u\n", ip, index);
      ip += 3;
      break;
    }
    case OP_POP:
      printf("  %04u OpPop\n", ip);
      ip += 1;
      break;
    case OP_ADD:
      printf("  %04u OpAdd\n", ip);
      ip += 1;
      break;
    case OP_SUB:
      printf("  %04u OpSub\n", ip);
      ip += 1;
      break;
    case OP_MUL:
      printf("  %04u OpMul\n", ip);
      ip += 1;
      break;
    case OP_DIV:
      printf("  %04u OpDiv\n", ip);
      ip += 1;
      break;
    case OP_TRUE:
      printf("  %04u OpTrue\n", ip);
      ip += 1;
      break;
    case OP_FALSE:
      printf("  %04u OpFalse\n", ip);
      ip += 1;
      break;
    case OP_EQUAL:
      printf("  %04u OpEqual\n", ip);
      ip += 1;
      break;
    case OP_NOT_EQUAL:
      printf("  %04u OpNotEqual\n", ip);
      ip += 1;
      break;
    case OP_GREATER_THAN:
      printf("  %04u OpGreaterThan\n", ip);
      ip += 1;
      break;
    case OP_MINUS:
      printf("  %04u OpMinus\n", ip);
      ip += 1;
      break;
    case OP_BANG:
      printf("  %04u OpBang\n", ip);
      ip += 1;
      break;
    case OP_JUMP_NOT_TRUTHY: {
      if (ip + 2 >= bc->num_instructions) {
        printf("  %04u OpJumpNotTruthy <truncated>\n", ip);
        return;
      }

      uint16_t pos = read_u16(&bc->instructions[ip + 1]);
      printf("  %04u OpJumpNotTruthy %u\n", ip, pos);
      ip += 3;
      break;
    }
    case OP_JUMP: {
      if (ip + 2 >= bc->num_instructions) {
        printf("  %04u OpJump <truncated>\n", ip);
        return;
      }

      uint16_t pos = read_u16(&bc->instructions[ip + 1]);
      printf("  %04u OpJump %u\n", ip, pos);
      ip += 3;
      break;
    }
    case OP_NULL:
      printf("  %04u OpNull\n", ip);
      ip += 1;
      break;
    case OP_GET_GLOBAL: {
      if (ip + 2 >= bc->num_instructions) {
        printf("  %04u OpGetGlobal <truncated>\n", ip);
        return;
      }

      uint16_t index = read_u16(&bc->instructions[ip + 1]);
      printf("  %04u OpGetGlobal %u\n", ip, index);
      ip += 3;
      break;
    }
    case OP_SET_GLOBAL: {
      if (ip + 2 >= bc->num_instructions) {
        printf("  %04u OpSetGlobal <truncated>\n", ip);
        return;
      }

      uint16_t index = read_u16(&bc->instructions[ip + 1]);
      printf("  %04u OpSetGlobal %u\n", ip, index);
      ip += 3;
      break;
    }
    default:
      printf("  %04u UNKNOWN opcode=0x%02x\n", ip, op);
      ip += 1;
      break;
    }
  }
}

int main(void) {
  MkcBytecode bc;
  if (mkc_read(stdin, &bc) != 0)
    return 1;
  dump(&bc);
  mkc_free(&bc);
  return 0;
}
