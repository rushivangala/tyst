#!/usr/bin/env python3
"""Generates core/src/main/resources/tyst/en_unlisted.txt: rare entries of en_words.txt that no
spell-checker dictionary lists.

wordfreq counts what people write online, so its long tail mixes real rare words ("fir", "wry") with
fragments and common slips ("thr", "pn", "ib"). Autocorrect gives a rare word the benefit of the doubt
only if it is a real word, which this list lets it tell apart.

The reference is SymSpell's English dictionary (frequency_dictionary_en_82_765.txt, MIT,
https://github.com/mammothb/symspellpy), built from SCOWL and Google Books Ngram data (CC BY 3.0).
SCOWL is British-spelled here, which is why only words below Autocorrector.TRUSTED_FREQ are listed:
common American spellings ("favorite") stay trusted, and a rare unlisted word is still only changed
for a much likelier word one key-slip away.

Usage: pip download symspellpy==6.10.0 --no-deps && unzip -o symspellpy-*.whl 'symspellpy/*.txt'
       python3 tools/gen_unlisted.py symspellpy/frequency_dictionary_en_82_765.txt
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "core/src/main/resources/tyst"
TRUSTED_FREQ = 377  # keep in sync with Autocorrector.TRUSTED_FREQ


def main(src):
    listed = set()
    for line in pathlib.Path(src).read_text(encoding="utf-8").splitlines():
        if line.strip():
            listed.add(line.split()[0].lower())
    out = []
    for line in (RES / "en_words.txt").read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        word, freq = line.split("\t")
        if int(freq) < TRUSTED_FREQ and word not in listed and "'" not in word:
            out.append(word)
    header = "# Rare en_words.txt entries no spell-checker dictionary lists. Built by tools/gen_unlisted.py.\n"
    (RES / "en_unlisted.txt").write_text(header + "\n".join(sorted(out)) + "\n", encoding="utf-8")
    print(f"{len(out)} unlisted words")


if __name__ == "__main__":
    main(sys.argv[1])
