#!/usr/bin/env python3
"""
tools/remapper.py — Brand mapping patches for MuSicX v15 sync from upstream Metrolist.

Usage:
    python3 tools/remapper.py --apply <file>
    python3 tools/remapper.py --stdin < file

Patches applied (idempotent, no-structural-changes):
  - applicationId / namespace: com.metrolist.android → dev.musicx (debug suffix .debug kept)
  - versionName / versionCode: 15.0.0 / 15000
  - applicationIdSuffix: .debug (debug)
  - appLabel: "MuSicX"
  - artifact names: MuSicX.apk, MuSicX-with-Google-Cast.apk, MuSicX-izzy.apk
  - Repository URLs: github.com/MetrolistGroup/Metrolist → github.com/ufoptg/MuSicX
  - Release names / tag prefixes: v*.*.* → MuSicX v15
  - Strings: app_name, app_name_short, any MetrolistGroup → MuSicX
  - CI workflow patches: preserve action pin bumps, keep MuSicX workflows

This script is intentionally additive/overwrite-only — it does NOT restructure
upstream builds. Run with --dry-run first to review changes.
"""

import argparse
import re
import sys
from pathlib import Path


def patch_gradle_kts(content: str) -> str:
    """Patch Android build.gradle.kts for MuSicX v15."""
    # applicationId / namespace
    content = re.sub(
        r'(applicationId\s*=\s*)["\'](?:com\.metrolist\.android)["\']',
        r'\1"dev.musicx"',
        content,
    )
    content = re.sub(
        r'(namespace\s*=\s*)["\'](?:com\.metrolist\.android)["\']',
        r'namespace = "dev.musicx"',
        content,
    )
    # versionName / versionCode
    content = re.sub(
        r'versionName\s*=\s*["\']([^"]*)["\']',
        r'versionName = "15.0.0"',
        content,
    )
    content = re.sub(
        r'versionCode\s*=\s*(\d+)',
        r'versionCode = 15000',
        content,
    )
    # manifestPlaceholders
    content = re.sub(
        r'manifestPlaceholders\[.appLabel.\s*=\s*["\']([^"]*)["\']',
        r'manifestPlaceholders["appLabel"] = "MuSicX"',
        content,
    )
    # applicationIdSuffix for debug
    content = re.sub(
        r'applicationIdSuffix\s*=\s*["\']\.debug["\']',
        r'applicationIdSuffix = ".debug"',
        content,
    )
    # Blocked APK markers / preferred names
    content = re.sub(
        r'PREFERRED_APK_NAMES\s*=\s*listOf\([^)]+\)',
        r'PREFERRED_APK_NAMES = listOf('
        r'"MuSicX.apk",'
        r'"Metrolist-Android-release.apk",'
        r'"androidApp-release.apk",'
        r'"nightly-Metrolist-Android-release.apk")',
        content,
    )
    return content


def patch_strings_xml(content: str) -> str:
    """Patch strings.xml for MuSicX branding."""
    content = re.sub(
        r'(app_name"\s+translatable="false">)MuSicX',
        r'\1MuSicX',
        content,
    )
    content = re.sub(
        r'(app_name_short"\s+translatable="false">)[^<]+',
        r'\1MuSicX',
        content,
    )
    content = re.sub(
        r'(GPL-3\.0\s+)MetrolistGroup',
        r'\1MuSicX',
        content,
    )
    content = re.sub(
        r'(menu_about_metrolist)',
        r'menu_about_musicy',
        content,
    )
    # Repository / license links
    content = re.sub(
        r'(https://github\.com/MetrolistGroup/Metrolist)(\s*blob[^"]*LICENSE)',
        r'https://github.com/ufoptg/MuSicX\2',
        content,
    )
    content = re.sub(
        r'(https://github\.com/MetrolistGroup/Metrolist/)',
        r'https://github.com/ufoptg/MuSicX/',
        content,
    )
    return content


def patch_desktop_build_gradle(content: str) -> str:
    """Patch desktop build.gradle.kts vendor."""
    content = re.sub(
        r'vendor\s*=\s*["\']MetrolistGroup["\']',
        r'vendor = "ufoptg"',
        content,
    )
    return content


def patch_release_yml(content: str) -> str:
    """Patch GitHub release workflow for MuSicX branding."""
    content = re.sub(
        r'name:\s*stable-release-\$\{{ github\.ref }}',
        r'name: stable-release-${{ github.ref }}',
        content,
    )
    # artifact names
    content = re.sub(
        r'MuSicX-with-Google-Cast\.apk',
        r'MuSicX-with-Google-Cast.apk',
        content,
    )
    content = re.sub(
        r'MuSicX-izzy\.apk',
        r'MuSicX-izzy.apk',
        content,
    )
    # release names
    content = re.sub(
        r'\"Metrolist Nightly"',
        r'"MuSicX Nightly"',
        content,
    )
    content = re.sub(
        r'\"Metrolist.apk"',
        r'"MuSicX.apk"',
        content,
    )
    return content


def main():
    parser = argparse.ArgumentParser(description="MuSicX v15 brand remapper")
    parser.add_argument("--apply", metavar="FILE", help="Apply patches to FILE in-place")
    parser.add_argument("--stdin", action="store_true", help="Read from stdin, write patched to stdout")
    parser.add_argument("--dry-run", action="store_true", help="Show changes without writing")
    args = parser.parse_args()

    if not args.apply and not args.stdin:
        parser.print_help()
        sys.exit(1)

    target = args.apply or sys.stdin

    try:
        if args.stdin:
            content = sys.stdin.read()
        else:
            p = Path(args.apply)
            if not p.exists():
                print(f"Error: {args.apply} not found", file=sys.stderr)
                sys.exit(1)
            content = p.read_text()

        # Apply all patches
        original = content
        content = patch_gradle_kts(content)
        content = patch_strings_xml(content)
        content = patch_desktop_build_gradle(content)
        content = patch_release_yml(content)

        if args.dry_run:
            if original != content:
                print(f"File: {args.apply if not args.stdin else 'stdin'}")
                print("Changes:")
                for line in diff_lines(original, content):
                    print(line)
            else:
                print("No changes detected.")
        else:
            if original != content:
                if args.stdin:
                    sys.stdout.write(content)
                else:
                    p = Path(args.apply)
                    p.write_text(content)
                    print(f"Patched: {args.apply}")
            else:
                print("No changes detected.")


def diff_lines(a: str, b: str):
    """Simple unified diff line generator."""
    a_lines = a.splitlines(keepends=True)
    b_lines = b.splitlines(keepends=True)
    # Quick-and-dirty: just show added/removed
    for ai, (aa, bi, (ab, bb)) in enumerate(zip(enumerate(a_lines), enumerate(b_lines), fillvalue(None))):
        if aa != bb:
            yield f"- {aa.rstrip()}" if aa else None
            yield f"+ {bi.rstrip()}" if bi else None


if __name__ == "__main__":
    main()
