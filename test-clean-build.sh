#!/bin/bash
# Test clean build process
echo "Starting clean build process..."

# Ensure we're in the right directory
cd $(git rev-parse --show-toplevel)

# Run the clean build script
./build-cleanup.sh

# Attempt to compile
if ./gradlew :desktop:compileKotlin --warning-mode all; then
    echo "✓ Clean build successful"
    exit 0
else
    echo "✗ Clean build failed"
    exit 1
fi