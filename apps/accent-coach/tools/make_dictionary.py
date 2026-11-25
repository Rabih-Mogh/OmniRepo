#!/usr/bin/env python3
"""Builds app/src/main/assets/dictionary.txt: the native General American phonemes of
every word in a word list, for the words people add themselves.

The words come from the system's word list (/usr/share/dict/words, Webster's Second,
in the public domain) and their phonemes from espeak-ng (en-us), spelt in the
recogniser's own symbols like the sentences'. Each line is a word, a tab, and its
phonemes with spaces between; the lines are sorted, so the app can stop reading at the
first word past the one it is looking for. Needs espeak-ng on the PATH.

    python3 tools/make_dictionary.py [word list]
"""
import json
import subprocess
import sys
import tempfile
from pathlib import Path

from make_sentences import MARKS, SPLIT, VOCAB

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/assets/dictionary.txt"
WORDS = Path("/usr/share/dict/words")


def tokenize(ipa, vocab, max_len):
    """The word's phonemes, or None if espeak wrote a symbol the recogniser doesn't have."""
    s = "".join(c for c in ipa if c not in MARKS)
    out, i = [], 0
    while i < len(s):
        for n in range(min(max_len, len(s) - i), 0, -1):
            # Don't take a match that would leave a length mark on its own ("ju" + "ː").
            if s[i:i + n] in vocab and s[i + n:i + n + 1] != "ː":
                out.append(s[i:i + n])
                i += n
                break
        else:
            return None
    return [p for t in out for p in SPLIT.get(t, [t])]


def main():
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else WORDS
    vocab = set(json.loads(VOCAB.read_text()))
    max_len = max(len(v) for v in vocab)
    words = sorted({w.lower() for w in source.read_text().split() if w.isascii() and w.isalpha()})

    # A full stop after each word makes it a sentence of its own, so espeak says each as
    # it would alone and writes one line for it.
    with tempfile.NamedTemporaryFile("w", suffix=".txt") as listed:
        listed.write("".join(f"{w}.\n" for w in words))
        listed.flush()
        said = subprocess.run(
            ["espeak-ng", "-v", "en-us", "-q", "--ipa", "-f", listed.name], capture_output=True, text=True, check=True,
        ).stdout.splitlines()
    if len(said) != len(words):
        sys.exit(f"espeak-ng wrote {len(said)} lines for {len(words)} words")

    lines, left_out = [], 0
    for word, ipa in zip(words, said):
        # "(fr)" and the like mark a word espeak said in another language's voice.
        phonemes = None if "(" in ipa else tokenize(ipa.strip(), vocab, max_len)
        if phonemes:
            lines.append(f"{word}\t{' '.join(phonemes)}\n")
        else:
            left_out += 1
    OUT.write_text("".join(lines))
    print(f"wrote {len(lines)} words to {OUT.relative_to(ROOT)} ({left_out} left out)")


if __name__ == "__main__":
    main()
