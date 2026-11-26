#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 interactive instruments GmbH
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Ersetzt die proj.db in den Ressourcen der OKLABI durch die aus PROJ 9.8.1.

Die Ressourcen der OKLABI 1.6.5.0, wie sie bis zum 02.11.2026 ausgeliefert wurden, führen in
gdal_data/ eine proj.db im Layout 1.2 mit; die mitgelieferte Bibliothek PROJ 9.8.1 (Linux und
Windows) verlangt mindestens 1.6. Sonst scheitert jede Koordinatentransformation, ohne dass
es auffällt. Seit dem 03.11.2026 enthält die Ressourcen.zip des Release eine passende
proj.db; gebraucht wird das Skript nur noch für ältere Ressourcen, etwa einer bestehenden
Installation. Eine passende proj.db lässt es unverändert. Das Skript lädt
das Debian-Paket proj-data 9.8.1 aus dem dauerhaften Archiv snapshot.debian.org, prüft
seine Prüfsumme, nimmt die proj.db heraus und legt sie an die Stelle der alten; die alte
bleibt als proj.db.orig erhalten. Nur Standardbibliothek, unter Linux und Windows.

    projdb.py <Ressourcenverzeichnis der OKLABI> [--deb <proj-data_9.8.1-1_all.deb>]
"""

import argparse
import hashlib
import io
import shutil
import sqlite3
import sys
import tarfile
import tempfile
import time
import urllib.request
from pathlib import Path

URL = ("https://snapshot.debian.org/file/965ab0cef161987e75396c25841364f8ae44162d/"
       "proj-data_9.8.1-1_all.deb")
SHA256 = "72e102a2677eaa3cc70273300177157b5047fb507bc453f8b1b6457b8a19f877"
MITGLIED = "./usr/share/proj/proj.db"
MINDESTLAYOUT = 6


def laden(url, versuche=8):
    anfrage = urllib.request.Request(url, headers={"User-Agent": "vermdb-projdb"})
    for versuch in range(1, versuche + 1):
        try:
            with urllib.request.urlopen(anfrage, timeout=120) as antwort:
                return antwort.read()
        except OSError as fehler:
            # snapshot.debian.org antwortet zeitweise mit 504
            if versuch == versuche:
                raise
            print(f"  {fehler}; neuer Versuch in 15 s", file=sys.stderr, flush=True)
            time.sleep(15)


def daten_aus_deb(deb):
    """Liefert das Archiv data.tar.* aus einem Debian-Paket (ar-Format)."""
    if not deb.startswith(b"!<arch>\n"):
        raise ValueError("kein Debian-Paket")
    pos = 8
    while pos + 60 <= len(deb):
        kopf = deb[pos:pos + 60]
        name = kopf[0:16].decode("ascii").strip().rstrip("/")
        groesse = int(kopf[48:58].decode("ascii").strip())
        inhalt = deb[pos + 60:pos + 60 + groesse]
        if name.startswith("data.tar"):
            return inhalt
        pos += 60 + groesse + (groesse % 2)
    raise ValueError("data.tar fehlt im Debian-Paket")


def layout(pfad):
    with sqlite3.connect(f"file:{pfad}?mode=ro", uri=True) as db:
        zeile = db.execute("SELECT value FROM metadata "
                           "WHERE key = 'DATABASE.LAYOUT.VERSION.MINOR'").fetchone()
    return int(zeile[0]) if zeile else 0


def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("ressourcen", type=Path,
                   help="Ressourcenverzeichnis der OKLABI (mit gdal_data/)")
    p.add_argument("--deb", type=Path, help="schon geladenes Paket proj-data_9.8.1-1_all.deb")
    a = p.parse_args()

    ziel = a.ressourcen / "gdal_data" / "proj.db"
    if not ziel.parent.is_dir():
        print(f"{ziel.parent} fehlt; ist {a.ressourcen} das Ressourcenverzeichnis?",
              file=sys.stderr)
        return 1
    if ziel.is_file() and layout(ziel) >= MINDESTLAYOUT:
        print(f"{ziel} hat schon Layout 1.{layout(ziel)}; nichts zu tun")
        return 0

    if a.deb:
        deb = a.deb.read_bytes()
    else:
        print(f"Lade {URL}", flush=True)
        deb = laden(URL)
    ist = hashlib.sha256(deb).hexdigest()
    if ist != SHA256:
        print(f"Prüfsumme {ist}, erwartet {SHA256}", file=sys.stderr)
        return 1

    with tarfile.open(fileobj=io.BytesIO(daten_aus_deb(deb)), mode="r:*") as tar:
        quelle = tar.extractfile(MITGLIED)
        with tempfile.NamedTemporaryFile(dir=ziel.parent, delete=False) as tmp:
            shutil.copyfileobj(quelle, tmp)
    neu = Path(tmp.name)
    if layout(neu) < MINDESTLAYOUT:
        neu.unlink()
        print("proj.db aus dem Paket hat ein zu altes Layout", file=sys.stderr)
        return 1
    if ziel.is_file() and not ziel.with_suffix(".db.orig").exists():
        ziel.replace(ziel.with_suffix(".db.orig"))
    neu.replace(ziel)
    ziel.chmod(0o644)
    print(f"{ziel}: proj.db aus PROJ 9.8.1 (Layout 1.{layout(ziel)})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
