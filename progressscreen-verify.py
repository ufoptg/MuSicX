#!/usr/bin/env python3
# Verify ProgressScreen.kt uses Spacing tokens correctly
import re

with open('desktop/src/main/kotlin/com/metrolist/music/ui/components/ProgressScreen.kt', 'r') as f:
    content = f.read()

# Similar verification as CompletionScreen.kt
issues = []

spacing_usage_checks = [
    ('Spacing.LG', 'Uses Spacing.LG'),
    ('Spacing.XXXL', 'Uses Spacing.XXXL'),
    ('.padding(horizontal = Spacing.LG', 'Uses horizontal = Spacing.LG in padding'),
    ('.padding(Spacing.XXL', 'Uses Spacing.XXL in padding')
]

for pattern, description in spacing_usage_checks:
    if pattern in content:
        print(f"✓ {description}")
    else:
        issues.append(f"Missing: {description}")

if issues:
    print("Issues found:", issues)
    exit(1)
else:
    print("✓ ProgressScreen.kt uses spacing tokens correctly")