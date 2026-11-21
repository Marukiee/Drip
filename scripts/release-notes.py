#!/usr/bin/env python3
"""Prints the release notes for one version from changelog.json, as Markdown: English, then the Dutch part.

Usage: scripts/release-notes.py 0.1.0
What is new comes first, the way the app shows it under What's new, and the file to download after a line. The update card of
the app (markupdate) shows the first two points of the part in the app's language: the Dutch part has a heading that ends in
"(Nederlands)", which is how it finds it.
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
    def part(text, title, heads):
        print(f"## {title}\n")
        for key, head in zip(("new", "better", "fixed"), heads):
            items = text.get(key) or []
            if items:
                print(f"**{head}**")
                for item in items:
                    print(f"- {item}")
                print()

    en = entry["en"]
    part(en, en.get("title") or f"Drip {version}", ("New", "Better", "Fixed"))
    nl = entry.get("nl")
    if nl:
        part(nl, f"{nl.get('title') or 'Drip ' + version} (Nederlands)", ("Nieuw", "Beter", "Opgelost"))
print("---\n")
print(f"Install `Drip-v{version}.apk` on your phone. `Drip.apk` is the same file under the name the app uses to update itself.")
