#!/usr/bin/env bash
set -euo pipefail

echo "=== Cleaning fixtures ==="
rm -f src/test/fixtures/*.mkc

echo "=== Generating fixtures ==="
./gradlew generateFixtures