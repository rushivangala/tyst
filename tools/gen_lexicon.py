#!/usr/bin/env python3
"""Generates core/src/main/resources/tyst/{en,sv}_words.txt from wordfreq.

wordfreq (https://github.com/rspeer/wordfreq) data is CC BY-SA 4.0; the generated lists are
shipped under the same licence (see LICENSES.md).

Usage: pip install wordfreq==3.1.1 && python3 tools/gen_lexicon.py
Output format: one `word<TAB>zipf*100` per line, most frequent first.
"""
import pathlib
import re

import wordfreq

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "core/src/main/resources/tyst"

EN_SIZE = 60000
SV_SIZE = 40000
WORD = re.compile(r"^[a-zåäöéü]+(?:'[a-z]+)?$")


def curated_keys(name):
    keys = set()
    for line in (RES / name).read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#"):
            keys.add(line.split("\t")[0].lower())
    return keys


def main():
    typos = curated_keys("misspellings.txt")
    typos.discard("i")
    en = []
    for w in wordfreq.top_n_list("en", EN_SIZE * 2, wordlist="large"):
        if len(en) >= EN_SIZE:
            break
        if w in typos or not WORD.match(w) or len(w) > 24:
            continue
        # Single letters other than a/i are noise in English.
        if len(w) == 1 and w not in ("a", "i"):
            continue
        en.append((w, round(wordfreq.zipf_frequency(w, "en", wordlist="large") * 100)))
    (RES / "en_words.txt").write_text("".join(f"{w}\t{z}\n" for w, z in en), encoding="utf-8")

    sv = []
    for w in wordfreq.top_n_list("sv", SV_SIZE * 2, wordlist="large"):
        if len(sv) >= SV_SIZE:
            break
        if w in typos or not WORD.match(w) or len(w) < 2 or len(w) > 28:
            continue
        sv.append((w, round(wordfreq.zipf_frequency(w, "sv", wordlist="large") * 100)))
    (RES / "sv_words.txt").write_text("".join(f"{w}\t{z}\n" for w, z in sv), encoding="utf-8")
    print(f"en={len(en)} sv={len(sv)}")


if __name__ == "__main__":
    main()
