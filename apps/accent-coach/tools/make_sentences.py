#!/usr/bin/env python3
"""Builds app/src/main/assets/sentences.json from tools/sentences.txt.

Every word gets its native General American phonemes from espeak-ng (en-us), spelt
in the recogniser's own symbols (app/src/main/assets/vocab.json), so the app compares
like with like. Needs espeak-ng on the PATH (brew install espeak-ng).

    python3 tools/make_sentences.py
"""
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VOCAB = ROOT / "app/src/main/assets/vocab.json"
SOURCE = ROOT / "tools/sentences.txt"
OUT = ROOT / "app/src/main/assets/sentences.json"

MARKS = set("ˈˌ.-_' ")
# Must match Phonemes.SPLIT in core: compound symbols compared as their parts.
SPLIT = {
    "ɚ": ["ə", "ɹ"], "ɑːɹ": ["ɑː", "ɹ"], "ɔːɹ": ["ɔː", "ɹ"], "oːɹ": ["oː", "ɹ"],
    "ɪɹ": ["ɪ", "ɹ"], "ɛɹ": ["ɛ", "ɹ"], "ʊɹ": ["ʊ", "ɹ"], "aɪɚ": ["aɪ", "ə", "ɹ"],
    "aɪə": ["aɪ", "ə"], "əl": ["ə", "l"], "ju": ["j", "u"],
}


def espeak(text):
    out = subprocess.run(["espeak-ng", "-v", "en-us", "-q", "--ipa", text], capture_output=True, text=True, check=True)
    return out.stdout.strip()


def tokenize(ipa, vocab, max_len):
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
            print(f"  skipping unknown symbol {s[i]!r} in {ipa!r}", file=sys.stderr)
            i += 1
    return [p for t in out for p in SPLIT.get(t, [t])]


def group(lengths, targets):
    """Splits words (by their lengths) into len(targets) runs whose totals best match targets."""
    n, m = len(lengths), len(targets)
    inf = float("inf")
    best = [[inf] * (m + 1) for _ in range(n + 1)]
    back = [[0] * (m + 1) for _ in range(n + 1)]
    best[0][0] = 0
    for i in range(1, n + 1):
        for j in range(1, min(i, m) + 1):
            for k in range(j - 1, i):
                c = best[k][j - 1] + abs(sum(lengths[k:i]) - targets[j - 1])
                if c < best[i][j]:
                    best[i][j], back[i][j] = c, k
    runs, i = [], n
    for j in range(m, 0, -1):
        k = back[i][j]
        runs.append((k, i))
        i = k
    return runs[::-1]


def phonemize(sentence, vocab, max_len):
    words = sentence.split()
    bare = [re.sub(r"[^\w']", "", w) for w in words]
    chunks = [tokenize(c, vocab, max_len) for c in espeak(sentence).split()]
    if len(chunks) == len(words):
        return list(zip(words, chunks))
    # espeak ran some words together ("there are" → one chunk): share each chunk out
    # among its words in proportion to how long each word is on its own.
    alone = [tokenize(espeak(b), vocab, max_len) for b in bare]
    result = []
    for (start, end), chunk in zip(group([len(a) for a in alone], [len(c) for c in chunks]), chunks):
        lens = [len(a) for a in alone[start:end]]
        total, pos, acc = sum(lens) or 1, 0, 0
        for w, l in zip(range(start, end), lens):
            acc += l
            cut = len(chunk) if w == end - 1 else round(acc * len(chunk) / total)
            result.append((words[w], chunk[pos:cut]))
            pos = cut
    return result


def main():
    vocab = set(json.loads(VOCAB.read_text()))
    max_len = max(len(v) for v in vocab)
    sentences, focus, n = [], None, 0
    for line in SOURCE.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("# "):
            continue
        if line.startswith("## "):
            focus = line[3:]
            continue
        n += 1
        words = phonemize(line, vocab, max_len)
        sentences.append({
            "id": f"s{n:03d}",
            "focus": focus,
            "text": line,
            "words": [{"text": w, "phonemes": p} for w, p in words],
        })
    OUT.write_text(json.dumps(sentences, ensure_ascii=False, indent=1) + "\n")
    print(f"wrote {len(sentences)} sentences to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
