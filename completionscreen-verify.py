#!/usr/bin/env python3
# Verify CompletionScreen.kt uses Spacing tokens correctly
import re

with open('desktop/src/main/kotlin/com/metrolist/music/ui/components/CompletionScreen.kt', 'r') as f:
    content = f.read()

# Check for proper usage of Spacing tokens
issues = []

# Should use Spacing.MD, Spacing.XXXL, etc. (not string versions)
if 'Spacing.MD' in content:
    print("✓ Uses Spacing.MD")
else:
    issues.append("Does not use Spacing.MD")

if 'Spacing.XXXL' in content:
    print("✓ Uses Spacing.XXXL")
else:
    issues.append("Does not use Spacing.XXXL")

# Check for correct padding usage
if '.padding(horizontal = Spacing.MD' in content:
    print("✓ Uses horizontal = Spacing.MD in padding")
else:
    issues.append("Padding may not use Spacing.MD correctly")

if issues:
    print("Issues found:", issues)
    exit(1)
else:
    print("✓ CompletionScreen.kt uses spacing tokens correctly")