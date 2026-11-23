# HisnLabs Tajweed v2 (int8)

`model-int8.onnx` is the speech model the app's "Recite and be corrected" listens with. The app fetches it
from here once, the first time a recitation is to be checked, and keeps it on the phone.

| | |
|---|---|
| What it is | A Whisper-base encoder with a CTC head that writes a recitation in the Quran phonetic script, a long vowel once per count, with signs for a hidden nun, a nun turned to a mim and the echo of a qalqalah |
| Made by | HisnLabs (HISNLABS CYBER AI - FZCO) |
| From | <https://huggingface.co/Hisnlabs/tajweed-v2>, file `onnx/model-int8.onnx`, unchanged |
| Size | 23,173,931 bytes |
| SHA-256 | `79cca1a2fc7ef81e325a3071a5f26cfec69203ef2c63177549065cdc1022c373` |
| Licence | Apache-2.0 ([text](../../licenses/Apache-2.0.txt)) |

Its makers credit: the encoder of [tarteel-ai/whisper-base-ar-quran](https://huggingface.co/tarteel-ai/whisper-base-ar-quran)
(Apache-2.0) as its base; the labels of [Quran Muaalem](https://huggingface.co/obadx/muaalem-model-v3_2) as its
teacher; [quran-transcript](https://github.com/obadx/quran-transcript) (MIT) as its phonetizer; and recitations
from everyayah.com and the RetaSy quranic_audio_dataset.

The model's own card says it can be wrong, that it is an aid for self-study and review, and that it does not
replace a qualified teacher. The app says the same on the screen that uses it.
