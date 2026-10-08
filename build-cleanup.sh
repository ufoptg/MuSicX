#!/bin/bash
# Clean all build artifacts and caches
rm -rf .gradle
echo "Cleaned Gradle cache"
rm -rf desktop/build
echo "Cleaned desktop build artifacts"
rm -rf app/build/outputs
echo "Cleaned Android build outputs"
rm -rf ~/.gradle/caches/*
echo "Cleaned global Gradle caches"

# Reset configuration cache
git checkout HEAD -- .gitignore 2>/dev/null || true
echo "Build environment cleaned"
# Note: workspaces are git-ignored, so we don't delete them
# The ledger and artifacts are stored in .superpowers/sdd/2026-10-08-windows-desktop-build-fixes/