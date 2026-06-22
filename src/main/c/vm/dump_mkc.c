#include <inttypes.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include "bytecode.h"
#include "bytes.h"
#include "mkc.h"
#include "object.h"
#include "opcodes.h"

static void dump_instructions(const uint8_t *instructions,
                              uint32_t num_instructions, const char *indent) {
  uint32_t ip = 0;
  while (ip < num_instructions) {
    uint8_t op = instructions[ip];

    switch (op) {
      case OP_CONSTANT: {
        if (ip + 2 >= num_instructions) {
          printf("%s%04u OpConstant <truncated>\n", indent, ip);
          return;
        }

        uint16_t index = read_u16(&instructions[ip + 1]);
        printf("%s%04u OpConstant %u\n", indent, ip, index);
        ip += 3;
        break;
      }
      case OP_POP:
        printf("%s%04u OpPop\n", indent, ip);
        ip += 1;
        break;
      case OP_ADD:
        printf("%s%04u OpAdd\n", indent, ip);
        ip += 1;
        break;
      case OP_SUB:
        printf("%s%04u OpSub\n", indent, ip);
        ip += 1;
        break;
      case OP_MUL:
        printf("%s%04u OpMul\n", indent, ip);
        ip += 1;
        break;
      case OP_DIV:
        printf("%s%04u OpDiv\n", indent, ip);
        ip += 1;
        break;
      case OP_TRUE:
        printf("%s%04u OpTrue\n", indent, ip);
        ip += 1;
        break;
      case OP_FALSE:
        printf("%s%04u OpFalse\n", indent, ip);
        ip += 1;
        break;
      case OP_EQUAL:
        printf("%s%04u OpEqual\n", indent, ip);
        ip += 1;
        break;
      case OP_NOT_EQUAL:
        printf("%s%04u OpNotEqual\n", indent, ip);
        ip += 1;
        break;
      case OP_GREATER_THAN:
        printf("%s%04u OpGreaterThan\n", indent, ip);
        ip += 1;
        break;
      case OP_MINUS:
        printf("%s%04u OpMinus\n", indent, ip);
        ip += 1;
        break;
      case OP_BANG:
        printf("%s%04u OpBang\n", indent, ip);
        ip += 1;
        break;
      case OP_JUMP_NOT_TRUTHY: {
        if (ip + 2 >= num_instructions) {
          printf("%s%04u OpJumpNotTruthy <truncated>\n", indent, ip);
          return;
        }

        uint16_t pos = read_u16(&instructions[ip + 1]);
        printf("%s%04u OpJumpNotTruthy %u\n", indent, ip, pos);
        ip += 3;
        break;
      }
      case OP_JUMP: {
        if (ip + 2 >= num_instructions) {
          printf("%s%04u OpJump <truncated>\n", indent, ip);
          return;
        }

        uint16_t pos = read_u16(&instructions[ip + 1]);
        printf("%s%04u OpJump %u\n", indent, ip, pos);
        ip += 3;
        break;
      }
      case OP_NULL:
        printf("%s%04u OpNull\n", indent, ip);
        ip += 1;
        break;
      case OP_GET_GLOBAL: {
        if (ip + 2 >= num_instructions) {
          printf("%s%04u OpGetGlobal <truncated>\n", indent, ip);
          return;
        }

        uint16_t index = read_u16(&instructions[ip + 1]);
        printf("%s%04u OpGetGlobal %u\n", indent, ip, index);
        ip += 3;
        break;
      }
      case OP_SET_GLOBAL: {
        if (ip + 2 >= num_instructions) {
          printf("%s%04u OpSetGlobal <truncated>\n", indent, ip);
          return;
        }

        uint16_t index = read_u16(&instructions[ip + 1]);
        printf("%s%04u OpSetGlobal %u\n", indent, ip, index);
        ip += 3;
        break;
      }
      case OP_GET_LOCAL: {
        if (ip + 1 >= num_instructions) {
          printf("%s%04u OpGetLocal <truncated>\n", indent, ip);
          return;
        }

        uint8_t index = read_u8(&instructions[ip + 1]);
        printf("%s%04u OpGetLocal %u\n", indent, ip, index);
        ip += 2;
        break;
      }
      case OP_SET_LOCAL: {
        if (ip + 1 >= num_instructions) {
          printf("%s%04u OpSetLocal <truncated>\n", indent, ip);
          return;
        }

        uint8_t index = read_u8(&instructions[ip + 1]);
        printf("%s%04u OpSetLocal %u\n", indent, ip, index);
        ip += 2;
        break;
      }
      case OP_ARRAY: {
        if (ip + 2 >= num_instructions) {
          printf("%s%04u OpArray <truncated>\n", indent, ip);
          return;
        }
        uint16_t n = read_u16(&instructions[ip + 1]);
        printf("%s%04u OpArray %u\n", indent, ip, n);
        ip += 3;
        break;
      }
      case OP_HASH: {
        if (ip + 2 >= num_instructions) {
          printf("%s%04u OpHash <truncated>\n", indent, ip);
          return;
        }
        uint16_t n = read_u16(&instructions[ip + 1]);
        printf("%s%04u OpHash %u\n", indent, ip, n);
        ip += 3;
        break;
      }
      case OP_INDEX:
        printf("%s%04u OpIndex\n", indent, ip);
        ip += 1;
        break;
      case OP_CALL: {
        if (ip + 1 >= num_instructions) {
          printf("%s%04u OpCall <truncated>\n", indent, ip);
          return;
        }
        uint8_t n = read_u8(&instructions[ip + 1]);
        printf("%s%04u OpCall %u\n", indent, ip, n);
        ip += 2;
        break;
      }
      case OP_RETURN_VALUE:
        printf("%s%04u OpReturnValue\n", indent, ip);
        ip += 1;
        break;
      case OP_RETURN:
        printf("%s%04u OpReturn\n", indent, ip);
        ip += 1;
        break;
      case OP_GET_BUILTIN: {
        if (ip + 1 >= num_instructions) {
          printf("%s%04u OpGetBuiltin <truncated>\n", indent, ip);
          return;
        }

        uint8_t index = read_u8(&instructions[ip + 1]);
        printf("%s%04u OpGetBuiltin %u\n", indent, ip, index);
        ip += 2;
        break;
      }
      case OP_CLOSURE: {
        if (ip + 3 >= num_instructions) {
          printf("%s%04u OpClosure <truncated>\n", indent, ip);
          return;
        }

        uint16_t constant_index = read_u16(&instructions[ip + 1]);
        uint8_t num_free = read_u8(&instructions[ip + 3]);

        printf("%s%04u OpClosure %u %u\n", indent, ip, constant_index,
               num_free);
        ip += 4;
        break;
      }
      case OP_GET_FREE: {
        if (ip + 1 >= num_instructions) {
          printf("%s%04u OpGetFree <truncated>\n", indent, ip);
          return;
        }
        uint8_t index = read_u8(&instructions[ip + 1]);
        printf("%s%04u OpGetFree %u\n", indent, ip, index);
        ip += 2;
        break;
      }
      case OP_CURRENT_CLOSURE: {
        printf("%s%04u OpCurrentClosure\n", indent, ip);
        ip += 1;
        break;
      }
      default:
        printf("%s%04u UNKNOWN opcode=0x%02x\n", indent, ip, op);
        ip += 1;
        break;
    }
  }
}

static void dump(const ByteCode *bc) {
  // decode constant pool
  printf("constants: %u\n", bc->num_constants);
  for (uint16_t i = 0; i < bc->num_constants; i++) {
    const MObject *c = &bc->constants[i];
    switch (c->type) {
      case MINTEGER:
        printf("  [%u] INTEGER %" PRId64 "\n", i, c->as.integer);
        break;
      case MSTRING:
        printf("  [%u] STRING value=\"%s\"\n", i, c->as.string);
        break;
      case MCOMPILED_FUNCTION:
        printf("  [%u] FUNCTION num_locals=%u len=%u\n", i,
               c->as.function->num_locals, c->as.function->num_instructions);
        dump_instructions(c->as.function->instructions,
                          c->as.function->num_instructions, "    ");
        break;
      default:
        printf("  [%u] UNKNOWN tag=0x%02x\n", i, c->type);
        break;
    }
  }

  // decode instructions
  printf("instructions: %u bytes\n", bc->num_instructions);
  dump_instructions(bc->instructions, bc->num_instructions, "  ");
}

int main(void) {
  ByteCode bc = {0};
  if (mkc_read(stdin, &bc) != 0) return 1;
  dump(&bc);
  free_bytecode(&bc);
  return 0;
}
