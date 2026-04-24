#!/usr/bin/env bash
# Usage: ./.dump_mkc.sh <file.mkc>
set -euo pipefail

BIN="$(dirname "$0")/build/dump_mkc"

# Build if needed
if [ ! -f "$BIN" ]; then
  cc -o "$BIN" \
    src/main/c/vm/dump_mkc.c \
    src/main/c/vm/mkc.c \
    src/main/c/vm/bytes.c \

fi

exec "$BIN" < "${1:?usage: $0 <file.mkc>}"