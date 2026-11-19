#!/usr/bin/env python3
"""
Writes the real recitation the unit tests align: Al-Fatihah recited by Mishari al-`Afasy,
as the speech model heard it, with the word timings Quran.com publishes for that recording.

Needs the model's raw output for the recording as a NumPy file (frames x symbols), made by
running the model (the same steps as core/.../Hearing.kt) over
https://download.quranicaudio.com/qdc/mishari_al_afasy/murattal/1.mp3 at 16 kHz mono:

    python3 tools/make_test_recitation.py emissions.npy

It keeps what the app keeps: for each frame that isn't a pause, the frame number and one
score per sound, in the order of core/.../Sounds.kt.
"""
import json, math, os, re, struct, sys, urllib.request
import numpy as np

here = os.path.dirname(os.path.abspath(__file__))
root = os.path.join(here, "..")
out = os.path.join(root, "core", "src", "test", "resources")

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    return json.load(urllib.request.urlopen(req, timeout=60))

vocab = json.load(open(os.path.join(root, "app", "src", "main", "assets", "vocab.json")))
kotlin = open(os.path.join(root, "core", "src", "main", "kotlin", "app", "recitation", "core", "Sounds.kt"), encoding="utf-8").read()
sounds = re.findall(r'^    [A-Z_]+\("([^"]+)"\)', kotlin, re.M)
assert len(sounds) == 36, len(sounds)

logits = np.load(sys.argv[1]).astype(np.float64)
logp = logits - logits.max(1, keepdims=True)
logp -= np.log(np.exp(logp).sum(1, keepdims=True))
keep = np.nonzero(logp[:, vocab["<pad>"]] < math.log(0.95))[0]
with open(os.path.join(out, "fatihah.rows"), "wb") as f:
    f.write(struct.pack("<i", len(keep)))
    for frame in keep:
        row = [logp[frame, vocab["<pad>"]]]
        for heard_as in sounds:
            ids = [vocab[s] for s in heard_as.split() if s in vocab]
            row.append(max(-40.0, math.log(np.exp(logp[frame, ids]).sum())))
        f.write(struct.pack("<i", int(frame)))
        f.write(struct.pack("<%df" % len(row), *row))

verses = get("https://api.quran.com/api/v4/verses/by_chapter/1?words=true&word_fields=text_uthmani&per_page=50")["verses"]
timing = get("https://api.quran.com/api/v4/chapter_recitations/7/1?segments=true")["audio_file"]["timestamps"]
with open(os.path.join(out, "fatihah.txt"), "w", encoding="utf-8") as f:
    for verse, times in zip(verses, timing):
        words = [w["text_uthmani"] for w in verse["words"] if w["char_type_name"] == "word"]
        starts = [int(s[1]) for s in times["segments"] if len(s) == 3]
        assert len(words) == len(starts)
        f.write(" ".join("%s|%d" % pair for pair in zip(words, starts)) + "\n")
print("kept", len(keep), "of", len(logits), "frames")
