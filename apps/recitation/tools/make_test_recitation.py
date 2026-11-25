#!/usr/bin/env python3
"""
Writes the real recitation the unit tests align: Al-Fatihah recited by Mishari al-`Afasy,
as the speech model heard it, with the word timings Quran.com publishes for that recording.

Needs the model's raw output for the recording as a NumPy file (frames x 43: for every
20 ms, the log of the chance of "nothing here" and of each symbol of the phonetic script),
made by running the model (models/tajweed-v2/model-int8.onnx, the same steps as
core/.../Hearing.kt: ten seconds at a time with half a second either side) over
https://download.quranicaudio.com/qdc/mishari_al_afasy/murattal/1.mp3 at 16 kHz mono:

    python3 tools/make_test_recitation.py emissions.npy

It keeps what the app keeps: for each frame that isn't a pause, the frame number and one
score per sound, as core/.../Sounds.kt works them out. With "timings" as a second word
the published word timings are fetched again too.
"""
import json, math, os, re, struct, sys, urllib.request
import numpy as np

here = os.path.dirname(os.path.abspath(__file__))
root = os.path.join(here, "..")
out = os.path.join(root, "core", "src", "test", "resources")

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    return json.load(urllib.request.urlopen(req, timeout=60))

core = os.path.join(root, "core", "src", "main", "kotlin", "app", "recitation", "core")
alphabet = re.search(r'const val ALPHABET = "([^"]+)"', open(os.path.join(core, "HeardScript.kt"), encoding="utf-8").read()).group(1)
kotlin = open(os.path.join(core, "Sounds.kt"), encoding="utf-8").read()
sounds = re.findall(r'^    [A-Z_]+\("([^"]+)"(?:, "([^"]*)")?\)', kotlin, re.M)
silent = re.search(r'const val SILENT = "([^"]+)"', kotlin).group(1)
assert len(sounds) == 36 and len(alphabet) == 42, (len(sounds), len(alphabet))
def ids(signs): return [alphabet.index(c) + 1 for c in signs]

logits = np.load(sys.argv[1]).astype(np.float64)
logp = logits - logits.max(1, keepdims=True)
logp -= np.log(np.exp(logp).sum(1, keepdims=True))
p = np.exp(logp)
quiet = p[:, [0] + ids(silent)].sum(1)
keep = np.nonzero(quiet < 0.95)[0]
with open(os.path.join(out, "fatihah.rows"), "wb") as f:
    f.write(struct.pack("<i", len(keep)))
    for frame in keep:
        row = [math.log(quiet[frame]) if quiet[frame] > 0 else -40.0]
        for written, also in sounds:
            chance = p[frame, ids(written)].sum() + 0.5 * p[frame, ids(also)].sum()
            row.append(math.log(chance) if chance > 1e-17 else -40.0)
        f.write(struct.pack("<i", int(frame)))
        f.write(struct.pack("<%df" % len(row), *row))

if sys.argv[2:] == ["timings"]:
    verses = get("https://api.quran.com/api/v4/verses/by_chapter/1?words=true&word_fields=text_uthmani&per_page=50")["verses"]
    timing = get("https://api.quran.com/api/v4/chapter_recitations/7/1?segments=true")["audio_file"]["timestamps"]
    with open(os.path.join(out, "fatihah.txt"), "w", encoding="utf-8") as f:
        for verse, times in zip(verses, timing):
            words = [w["text_uthmani"] for w in verse["words"] if w["char_type_name"] == "word"]
            starts = [int(s[1]) for s in times["segments"] if len(s) == 3]
            assert len(words) == len(starts)
            f.write(" ".join("%s|%d" % pair for pair in zip(words, starts)) + "\n")
print("kept", len(keep), "of", len(logits), "frames")
