#!/usr/bin/env python3
# Test the spacing tokens verification script
import subprocess
import sys

print("Testing spacing tokens verification script...")

# Run the verification script
result = subprocess.run([sys.executable, 'spacing-tokens-verify.py'], 
                       capture_output=True, text=True)

if result.returncode == 0:
    print("✓ Verification script runs successfully")
    if "✓ SpacingTokens.kt has correct Dp values" in result.stdout:
        print("✓ Verification script reports correct Dp values")
        print("\nAll tests passed!")
        exit(0)
    else:
        print("✗ Verification script did not report correct Dp values")
        print("Output:", result.stdout)
        exit(1)
else:
    print("✗ Verification script failed to run")
    print("Error:", result.stderr)
    exit(1)