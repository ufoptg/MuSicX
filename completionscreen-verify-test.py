#!/usr/bin/env python3
# Test the CompletionScreen verification script
import subprocess
import sys

print("Testing CompletionScreen verification script...")

# Run the verification script
result = subprocess.run([sys.executable, 'completionscreen-verify.py'], 
                       capture_output=True, text=True)

if result.returncode == 0:
    print("✓ Verification script runs successfully")
    if "✓ CompletionScreen.kt uses spacing tokens correctly" in result.stdout:
        print("✓ Verification script reports correct spacing token usage")
        print("\nAll tests passed!")
        exit(0)
    else:
        print("✗ Verification script did not report correct spacing token usage")
        print("Output:", result.stdout)
        exit(1)
else:
    print("✗ Verification script failed to run")
    print("Error:", result.stderr)
    exit(1)