#!/usr/bin/env python3
"""MuSicX rebrand remap (single source of truth).

Modes:
  text             apply content rules to stdin -> stdout
  patch            apply content rules + rewrite diff header paths
  --selftest       run built-in assertions, exit 0 on success

Rules are derived from the current upstream layout:
  androidApp/src/main/kotlin/com/metrolist/android/...
  desktopApp/src/jvmMain/kotlin/com/metrolist/desktop/...
  shared/src/commonMain/kotlin/com/metrolist/shared/...
  listenTogetherProto/...
"""
import sys

GUARDS = [
    ("com.metrolist.innertubex", "\x00ITX\x00"),
    ("com.github.MetrolistGroup", "\x00MG\x00"),
]

PATH_RULES = [
    ("com/metrolist/android/", "dev/musicx/android/"),
    ("com/metrolist/desktop/", "dev/musicx/desktop/"),
    ("com/metrolist/shared/", "dev/musicx/shared/"),
    ("com/metrolist/listentogethere/", "dev/musicx/listentogethere/"),
]

CONTENT_RULES = [
    ("com.metrolist.android", "dev.musicx.android"),
    ("com.metrolist.desktop", "dev.musicx.desktop"),
    ("com.metrolist.shared", "dev.musicx.shared"),
    ("com.metrolist.listentogethere", "dev.musicx.listentogethere"),
    ("com.metrolist.music", "dev.musicx.music"),
    ("metrolist_strings.xml", "musicx_strings.xml"),
    ("METROLIST_", "MUSICX_"),
    ("Metrolist", "MuSicX"),
]


def apply_guards(text):
    for a, b in GUARDS:
        text = text.replace(a, b)
    return text


def remove_guards(text):
    for a, b in GUARDS:
        text = text.replace(b, a)
    return text


def content(text):
    text = apply_guards(text)
    for a, b in CONTENT_RULES:
        text = text.replace(a, b)
    return remove_guards(text)


def paths(text):
    for a, b in PATH_RULES:
        text = text.replace(a, b)
    return text


def patch(text):
    out = []
    for line in text.splitlines(keepends=True):
        if line.startswith(
            (
                "diff --git ",
                "--- ",
                "+++ ",
                "rename from ",
                "rename to ",
            )
        ):
            out.append(content(paths(line)))
        else:
            out.append(content(paths(line)))
    return "".join(out)


SELFTESTS = [
    ("content", "package com.metrolist.shared", "package dev.musicx.shared"),
    ("content", "package com.metrolist.android", "package dev.musicx.android"),
    ("content", "package com.metrolist.desktop", "package dev.musicx.desktop"),
    (
        "content",
        "import com.metrolist.innertubex.InnerTube",
        "import com.metrolist.innertubex.InnerTube",
    ),
    (
        "content",
        'implementation("com.github.MetrolistGroup.innertubex:v0.7.4")',
        'implementation("com.github.MetrolistGroup.innertubex:v0.7.4")',
    ),
    ("content", "MetrolistTheme", "MuSicXTheme"),
    ("content", "MetrolistWidgetManager", "MuSicXWidgetManager"),
    ("content", "metrolist_strings.xml", "musicx_strings.xml"),
    ("content", "METROLIST_APPLICATION_ID", "MUSICX_APPLICATION_ID"),
    ("patch", "shared/src/commonMain/kotlin/com/metrolist/shared/A.kt",
     "shared/src/commonMain/kotlin/dev/musicx/shared/A.kt"),
    (
        "patch",
        "+++ b/androidApp/src/main/res/values-de/metrolist_strings.xml",
        "+++ b/androidApp/src/main/res/values-de/musicx_strings.xml",
    ),
    (
        "patch",
        "diff --git a/desktopApp/src/jvmMain/kotlin/com/metrolist/desktop/B.kt b/desktopApp/src/jvmMain/kotlin/com/metrolist/desktop/B.kt",
        "diff --git a/desktopApp/src/jvmMain/kotlin/dev/musicx/desktop/B.kt b/desktopApp/src/jvmMain/kotlin/dev/musicx/desktop/B.kt",
    ),
]


def selftest() -> int:
    failures = 0
    for mode, given, expected in SELFTESTS:
        got = patch(given) if mode == "patch" else content(given)
        if got != expected:
            print(f"FAIL: {given!r}\n  got:      {got!r}\n  expected: {expected!r}")
            failures += 1

    sample = (
        "diff --git a/shared/src/commonMain/kotlin/com/metrolist/shared/A.kt "
        "b/shared/src/commonMain/kotlin/com/metrolist/shared/A.kt\n"
        "--- a/shared/src/commonMain/kotlin/com/metrolist/shared/A.kt\n"
        "+++ b/shared/src/commonMain/kotlin/com/metrolist/shared/A.kt\n"
        "+import com.metrolist.innertubex.InnerTube\n"
    )
    expected = (
        "diff --git a/shared/src/commonMain/kotlin/dev/musicx/shared/A.kt "
        "b/shared/src/commonMain/kotlin/dev/musicx/shared/A.kt\n"
        "--- a/shared/src/commonMain/kotlin/dev/musicx/shared/A.kt\n"
        "+++ b/shared/src/commonMain/kotlin/dev/musicx/shared/A.kt\n"
        "+import com.metrolist.innertubex.InnerTube\n"
    )
    got = patch(sample)
    if got != expected:
        print(f"FAIL patch-mode:\n--- got ---\n{got}\n--- expected ---\n{expected}")
        failures += 1
    if failures:
        print(f"selftest FAILED ({failures})")
        return 1
    print("selftest ok")
    return 0


def main() -> int:
    args = sys.argv[1:]
    if args == ["--selftest"]:
        return selftest()
    data = sys.stdin.read()
    if args == ["text"]:
        sys.stdout.write(content(data))
        return 0
    if args == ["patch"]:
        sys.stdout.write(patch(data))
        return 0
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
