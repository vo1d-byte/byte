#!/usr/bin/env python3
"""Download each artifact whose sha256 is PIN_ME and write the real hash back.

Usage: python3 tools/pin_manifest.py toolchains/*.toml
Review the diff before committing: you are trusting whatever the URL serves today.
"""
import hashlib
import re
import sys
import tomllib
import urllib.request


def sha256_of(url: str) -> str:
    h = hashlib.sha256()
    with urllib.request.urlopen(url) as r:
        while chunk := r.read(1 << 20):
            h.update(chunk)
    return h.hexdigest()


def pin(path: str) -> None:
    text = open(path, encoding="utf-8").read()
    data = tomllib.loads(text)
    for art in data.get("artifacts", []):
        if art["sha256"].upper() != "PIN_ME":
            continue
        print(f"{path}: hashing {art['url']}")
        digest = sha256_of(art["url"])
        pattern = re.compile(
            r'(url\s*=\s*"' + re.escape(art["url"]) + r'"\s*\nsha256\s*=\s*")PIN_ME(")',
            re.IGNORECASE,
        )
        text, n = pattern.subn(r"\g<1>" + digest + r"\g<2>", text, count=1)
        if n != 1:
            sys.exit(f"{path}: could not rewrite hash for {art['url']}")
    open(path, "w", encoding="utf-8").write(text)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    for p in sys.argv[1:]:
        pin(p)
