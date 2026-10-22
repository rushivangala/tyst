#!/usr/bin/env python3
"""Generates core/src/main/resources/tyst/en_next.txt: the likeliest next words for common English words.

Source: SymSpell's English bigram dictionary (frequency_bigramdictionary_en_243_342.txt), shipped in the
symspellpy wheel (MIT, https://github.com/mammothb/symspellpy). SymSpell built it from Google Books
Ngram data, which Google publishes under CC BY 3.0. See LICENSES.md.

Usage: pip download symspellpy==6.10.0 --no-deps && unzip -o symspellpy-*.whl 'symspellpy/*.txt'
       python3 tools/gen_bigrams.py symspellpy/frequency_bigramdictionary_en_243_342.txt

Output, one line per word: `word<TAB>next:permille next:permille ...`, likeliest first. Per-mille values
are shares of everything that follows the word in the source, so they stay calibrated after pruning.
"""
import collections
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "core/src/main/resources/tyst"

VOCAB = 40000       # both words must be among the most frequent English words in en_words.txt
PER_WORD = 12       # followers kept per word
MIN_PERMILLE = 4    # drop followers rarer than 0.4 %
MIN_TOTAL = 2_000_000  # heads seen less often than this in the source carry too little signal
TOKEN = re.compile(r"^[a-z]+(?:'[a-z]+)?$")


def main(src):
    vocab = []
    for line in (RES / "en_words.txt").read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#"):
            vocab.append(line.split("\t")[0])
    vocab = set(vocab[:VOCAB])

    followers = collections.defaultdict(collections.Counter)
    for line in pathlib.Path(src).read_text(encoding="utf-8").splitlines():
        parts = line.split()
        if len(parts) != 3:
            continue
        a, b, n = parts[0].lower(), parts[1].lower(), int(parts[2])
        if a in vocab and b in vocab and TOKEN.match(a) and TOKEN.match(b):
            followers[a][b] += n

    out = []
    for head in sorted(followers):
        counts = followers[head]
        total = sum(counts.values())
        if total < MIN_TOTAL:
            continue
        kept = []
        for word, n in counts.most_common(PER_WORD):
            permille = round(1000 * n / total)
            if permille >= MIN_PERMILLE:
                kept.append(f"{word}:{permille}")
        if kept:
            out.append(head + "\t" + " ".join(kept))

    header = "# Next-word table from Google Books n-grams via SymSpell (CC BY 3.0 / MIT). Built by tools/gen_bigrams.py.\n"
    (RES / "en_next.txt").write_text(header + "\n".join(out) + "\n", encoding="utf-8")
    print(f"{len(out)} words, {sum(len(l.split(' ')) for l in out)} followers")


if __name__ == "__main__":
    main(sys.argv[1])
