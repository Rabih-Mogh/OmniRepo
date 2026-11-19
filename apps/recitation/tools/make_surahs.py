#!/usr/bin/env python3
"""Writes core/.../Surahs.kt: the 114 surahs' names and verse counts, from Quran.com."""
import json, os, urllib.request

here = os.path.dirname(os.path.abspath(__file__))
out = os.path.join(here, "..", "core", "src", "main", "kotlin", "app", "recitation", "core", "Surahs.kt")
req = urllib.request.Request("https://api.quran.com/api/v4/chapters?language=en", headers={"User-Agent": "Mozilla/5.0"})
chapters = json.load(urllib.request.urlopen(req, timeout=60))["chapters"]
assert len(chapters) == 114

lines = [
    "package app.recitation.core",
    "",
    "/** A surah: its number, its name in Arabic and in Latin letters, what the name means, and how many verses it has. */",
    "data class Surah(val number: Int, val arabic: String, val latin: String, val meaning: String, val verses: Int)",
    "",
    "/** All 114, in order. Written by tools/make_surahs.py from Quran.com; don't edit by hand. */",
    "val SURAHS: List<Surah> = listOf(",
]
def quoted(text):
    return '"' + text.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'

for c in chapters:
    lines.append("    Surah(%d, %s, %s, %s, %d)," % (
        c["id"], quoted(c["name_arabic"]), quoted(c["name_simple"]), quoted(c["translated_name"]["name"]), c["verses_count"]))
lines.append(")")
open(out, "w", encoding="utf-8").write("\n".join(lines) + "\n")
print("wrote", os.path.normpath(out))
