# Spark's packs

This branch holds the daily packs of reels and lessons for the
[Spark](https://github.com/Rabih-Mogh/OmniRepo/tree/main/apps/spark) app, and nothing else.
It shares no history with `main`.

```
index.json            The packs on offer: {"format": 1, "packs": ["2026-11-26", ...]}
packs/<day>.json      One pack, named after the day it was made
history.md            One line per reel and lesson ever published, to avoid repeats
```

The app reads `index.json` each time it opens and fetches the packs it doesn't have. A pack
is never changed once published; packs older than 45 days are taken out of the index and
deleted. How a pack is written, checked and published is in
[GENERATE.md](https://github.com/Rabih-Mogh/OmniRepo/blob/main/apps/spark/content/GENERATE.md)
on `main`.
