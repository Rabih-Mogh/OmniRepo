"""Writes app/src/main/assets/tajweed_reference.bin: what a correct recitation of every word of the Quran sounds
like, in the Quran phonetic script, for the app's "recite and be corrected".

(The file is gzipped text. It isn't named ".gz" because Android's build unpacks an asset with that name and drops
the ending, and the app would then not find it.)

The script comes from quran-transcript (MIT, https://github.com/obadx/quran-transcript) through the rule engine
that ships with the HisnLabs Tajweed v2 model (imam.py, Apache-2.0, https://huggingface.co/Hisnlabs/tajweed-v2).
How a word sounds depends on whether the reciter begins a breath on it and whether they stop on it, and on
nothing else outside the word's own verse (checked: 4,432 random stretches of verses, stitched from these forms,
all came out as the phonetizer's own). So each word is written in up to four forms:

    inside a breath | first of a breath | last of a breath | a breath of its own

    python tools/make_tajweed_reference.py <folder holding imam.py>      (needs: pip install quran-transcript)

One line per verse: "surah:verse", then a tab and a word, and so on. A word is its four forms parted by "|"; a
form left empty is the same as the first (for the fourth: as the third). A form is the script, then "~" if the
word ends in a nun or tanween that merges into the next word, then "@" and its long vowels that fall under a
rule of madd, each as start-end and a letter for the rule:
    n natural  f munfasil  t muttasil  T muttasil at a stop  z lazim  r 'arid  l lin
"""
import gzip, os, sys

sys.path.insert(0, sys.argv[1])
import imam  # noqa: E402
from quran_transcript import Aya  # noqa: E402

RULE = {'NormalMaddRule': 'n', 'MonfaselMaddRule': 'f', 'MottaselMaddRule': 't', 'MottaselMaddPauseRule': 'T',
        'LazemMaddRule': 'z', 'AaredMaddRule': 'r', 'LeenMaddRule': 'l'}


def vanishing(words, ref):
    """imam.expected's own test for a final nun or tanween that disappears into the next word."""
    out = imam.quran_phonetizer(' '.join(words), imam.default_moshaf(), remove_spaces=True)
    mp = out.mappings
    starts = [sum(len(x) + 1 for x in words[:k]) for k in range(len(words))]
    return [any(m.deleted for m in mp[p0:p0 + len(w)][-3:]) and (w.rstrip('ْۡ').endswith('ن') or any(c in w for c in 'ًࣰٌࣱٍࣲ'))
            for w, p0 in zip(words, starts)]


def forms(words):
    """For each word of a breath: (script, vanishing, [(start, end, rule letter)]), positions counted from the word's own start."""
    ref = imam.expected(words)
    gone = vanishing(words, ref)
    out = []
    for k, (a, b) in enumerate(ref.spans):
        madd = []
        for u in ref.units:
            if u.kind == 'madd' and a <= u.s < b:
                assert u.e <= b, (words, u)
                madd.append((u.s - a, u.e - a, RULE[u.rule]))
        out.append((ref.phonemes[a:b], gone[k], madd))
    return out


def write(form):
    script, gone, madd = form
    return script + ('~' if gone else '') + ('@' + ','.join(f'{s}-{e}{r}' for s, e, r in madd) if madd else '')


def main():
    lines = []
    for surah in range(1, 115):
        verse = 1
        while True:
            info = Aya(surah, verse).get()
            words = info.uthmani.split(' ')
            n = len(words)
            inside = forms(words)
            cells = []
            for i in range(n):
                first = forms(words[i:])[0] if i > 0 else inside[0]
                last = forms(words[:i + 1])[-1] if i < n - 1 else inside[-1]
                alone = forms(words[i:i + 1])[0]
                a = write(inside[i])
                b = write(first)
                c = write(last)
                d = write(alone)
                cells.append('|'.join([a, '' if b == a else b, '' if c == a else c, '' if d == c else d]))
            lines.append(f'{surah}:{verse}\t' + '\t'.join(cells))
            if verse >= info.num_ayat_in_sura:
                break
            verse += 1
        print(surah, end=' ', flush=True)
    text = '\n'.join(lines) + '\n'
    here = os.path.dirname(os.path.abspath(__file__))
    path = os.path.join(here, '..', 'app', 'src', 'main', 'assets', 'tajweed_reference.bin')
    with gzip.GzipFile(path, 'wb', compresslevel=9, mtime=0) as f:
        f.write(text.encode('utf-8'))
    print(f'\n{len(lines)} verses, {len(text.encode("utf-8"))} bytes, {os.path.getsize(path)} gzipped')


if __name__ == '__main__':
    main()
