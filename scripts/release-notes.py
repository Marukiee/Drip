#!/usr/bin/env python3
"""Prints the release notes for one version from changelog.json, as Markdown, in English.

Usage: scripts/release-notes.py 0.1.0
What is new comes first, the way the app shows it under What's new, and the file to download after a line. The update banner
of the app shows the part above that line only.
"""
import json
import sys
from pathlib import Path

version = sys.argv[1].lstrip("v") if len(sys.argv) > 1 else ""
entries = json.loads((Path(__file__).resolve().parent.parent / "changelog.json").read_text())
entry = next((e for e in entries if e["version"] == version), None)

if entry is None:
    print(f"Drip {version}")
else:
    en = entry["en"]
    print(f"## {en.get('title') or 'Drip ' + version}\n")
    for key, head in (("new", "New"), ("better", "Better"), ("fixed", "Fixed")):
        items = en.get(key) or []
        if items:
            print(f"**{head}**\n")
            for item in items:
                print(f"- {item}")
            print()
print("---\n")
print(f"Install `Drip-v{version}.apk` on your phone. `Drip.apk` is the same file under the name the app uses to update itself.")
