#!/usr/bin/env python3
# Verify SpacingTokens.kt contains correct Dp values only
import re

with open('desktop/src/main/kotlin/com/metrolist/music/ui/theme/SpacingTokens.kt', 'r') as f:
    content = f.read()

# Check for correct Dp values
issues = []

# Check if file contains Dp type annotations (should be "Dp = 4.dp" format)
dp_lines = re.findall(r'const val (\w+): (\w+) = (\d+\.?\d*)\.dp', content)

# Check specific spacing values that should be Dp
spacing_values = ['MD', 'XXXL', 'LG', 'XS', 'SM', 'XL', 'XXL', 'NONE']
for value in spacing_values:
    if f'const val {value}:' in content:
        # Check if the value has Dp type
        pattern = f'const val {value}: (\w+) = '
        match = re.search(pattern, content)
        if match and match.group(1) != 'Dp':
            issues.append(f"Value {value} has type {match.group(1)}, expected Dp")

if issues:
    print("Issues found:", issues)
    exit(1)
else:
    print("✓ SpacingTokens.kt has correct Dp values")

# Also check that there are no String type spacing tokens
if 'String = "' in content:
    issues.append("File contains String spacing values")
    print("Issues found:", issues)
    exit(1)