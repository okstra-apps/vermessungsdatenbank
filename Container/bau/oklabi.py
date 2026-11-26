#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 interactive instruments GmbH
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Stellt die Dateien des OKLABI-Release für den Image-Bau bereit.

Für jede Zeile der Liste (SHA-256, Pfad im Release) wird die Datei unter ihrem Namen aus
einem der lokalen Verzeichnisse übernommen oder, falls dort nicht vorhanden, aus dem
Release geladen; danach wird die Prüfsumme geprüft. Nur Standardbibliothek.

    oklabi.py --release <URL> --liste <Datei> --ziel <Verzeichnis> [lokales Verzeichnis ...]
"""

import argparse
import hashlib
import shutil
import sys
import time
import urllib.request
from pathlib import Path


def sha256(pfad):
    h = hashlib.sha256()
    with open(pfad, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def laden(url, ziel, versuche=5):
    anfrage = urllib.request.Request(url, headers={"User-Agent": "vermdb-image-bau"})
    for versuch in range(1, versuche + 1):
        try:
            with urllib.request.urlopen(anfrage, timeout=120) as antwort, open(ziel, "wb") as f:
                shutil.copyfileobj(antwort, f, 1 << 20)
            return
        except OSError as fehler:
            if versuch == versuche:
                raise
            print(f"  {fehler}; neuer Versuch in {10 * versuch} s", file=sys.stderr)
            time.sleep(10 * versuch)


def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--release", required=True, help="Adresse des Release")
    p.add_argument("--liste", required=True, type=Path, help="Prüfsummen und Pfade im Release")
    p.add_argument("--ziel", required=True, type=Path)
    p.add_argument("lokal", nargs="*", type=Path, help="Verzeichnisse mit schon vorhandenen Dateien")
    a = p.parse_args()

    a.ziel.mkdir(parents=True, exist_ok=True)
    fehler = 0
    for zeile in a.liste.read_text(encoding="utf-8").splitlines():
        zeile = zeile.strip()
        if not zeile or zeile.startswith("#"):
            continue
        soll, pfad = zeile.split(maxsplit=1)
        name = pfad.rsplit("/", 1)[-1]
        ziel = a.ziel / name
        vorhanden = next((d / name for d in a.lokal if (d / name).is_file()), None)
        if vorhanden:
            print(f"{name}: aus {vorhanden.parent}", flush=True)
            shutil.copyfile(vorhanden, ziel)
        else:
            url = a.release.rstrip("/") + "/" + pfad
            print(f"{name}: aus {url}", flush=True)
            laden(url, ziel)
        ist = sha256(ziel)
        if ist != soll:
            print(f"{name}: Prüfsumme {ist}, erwartet {soll}", file=sys.stderr)
            fehler += 1
    return 1 if fehler else 0


if __name__ == "__main__":
    sys.exit(main())
