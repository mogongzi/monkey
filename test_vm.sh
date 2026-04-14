#!/usr/bin/env bash
set -euo pipefail

echo "=== Generating fixtures ==="
./gradlew generateFixtures

echo "=== Compiling test_vm ==="
make -C src/main/c/vm test_vm

echo "=== Running test_vm ==="
./src/main/c/vm/test_vm
