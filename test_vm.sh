#!/usr/bin/env bash
set -euo pipefail

echo "=== Compiling test_vm ==="
make -C src/main/c/vm test_vm

echo "=== Running test_vm ==="
./build/test_vm
