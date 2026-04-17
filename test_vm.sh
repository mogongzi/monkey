#!/usr/bin/env bash
set -euo pipefail

echo "=== Cleaning fixtures ==="
rm -f src/test/fixtures/*.mkc

echo "=== Generating fixtures ==="
./gradlew generateFixtures

echo "=== Compiling test_vm ==="
make -C src/main/c/vm test_vm

echo "=== Running test_vm ==="
./src/main/c/vm/test_vm
