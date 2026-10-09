#!/usr/bin/env python3
# Verify WelcomeScreen.kt uses Spacing tokens correctly
import re

with open('desktop/src/main/kotlin/com/metrolist/music/ui/components/WelcomeScreen.kt', 'r') as f:
    content = f.read()

# Similar verification as previous screens
issues = []

spacing_usage_checks = [
    ('Spacing.MD', 'Uses Spacing.MD'),
    ('Spacing.LG', 'Uses Spacing.LG'),
    ('Spacing.SM', 'Uses Spacing.SM'),
    ('.padding(horizontal = Spacing.MD', 'Uses horizontal = Spacing.MD in padding')
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
    print("✓ WelcomeScreen.kt uses spacing tokens correctly")