#!/usr/bin/env python3
# Verify build debugging summary exists and is complete
import os

if os.path.exists('BUILD_DEBUG_SUMMARY.md'):
    with open('BUILD_DEBUG_SUMMARY.md', 'r') as f:
        content = f.read()
    
    # Check for key sections
    required_sections = [
        '## Issue Summary',
        '## Investigation Process',
        '## Resolution Steps',
        '## Files Modified'
    ]
    
    missing_sections = []
    for section in required_sections:
        if section not in content:
            missing_sections.append(section)
    
    if missing_sections:
        print("Missing sections:", missing_sections)
        exit(1)
    else:
        print("✓ Build debugging summary is complete")
else:
    print("✗ BUILD_DEBUG_SUMMARY.md not found")
    exit(1)