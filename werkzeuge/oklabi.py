#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 interactive instruments GmbH
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Richtet die native OKLABI für einen eigenen Tomcat ein (Linux und Windows).

Nimmt aus dem OKLABI-Release 1.6.5.0 die native Bibliothek der Plattform und die
Ressourcen, prüft sie gegen Prüfsummen und packt sie nach <Ziel>/lib und <Ziel>/Ressourcen
aus. Die Archive werden
aus oklabi-release/ im Repository genommen; fehlen sie dort, lädt das Skript sie von
okstra.de und legt sie dort ab. Ohne Zugang zu okstra.de sind sie von Hand dorthin zu legen
(oklabi-release/README.md). Nur Standardbibliothek.

    oklabi.py <Zielverzeichnis> [--plattform windows|linux] [--nativ-archiv <Datei>]
"""

import argparse
import hashlib
import platform
import shutil
import sys
import tarfile
import tempfile
import time
import urllib.request
import zipfile
from pathlib import Path

RELEASE = "https://www.okstra.de/oklabi/Release-20260629"
REPOSITORY = Path(__file__).resolve().parent.parent
ABLAGE = REPOSITORY / "oklabi-release"
WEITERE_ABLAGEN = [REPOSITORY / "Container" / "oklabi"]  # dort legt sie der Image-Bau ab

# Pfad im Release und SHA-256; die Summen für Linux und die Ressourcen stehen gleich
# in Container/bau/oklabi.sha256.
RESSOURCEN = ("Oklabi/Ressourcen/Ressourcen.zip",
              "c2fec8e0dfd61578c6aa2259c5f85b31741f8f02f27f5760c5968fbdd2c0c669")
NATIV = {
    "linux": ("Oklabi/Bibliothek/Linux/ubuntu-2404-1-6-5-0.tbz",
              "460458f2619f440862b9a352528bc8bbb58496f5d27fc01e0bc3ee638bd8c15a"),
    "windows": ("Oklabi/Bibliothek/Windows/VS2022-Lib64-1-6-5-0.zip",
                "84b54f1691da5fb7165155442f44eb327265795c6b442c419b09523692c08c49"),
}


def sha256(pfad):
    h = hashlib.sha256()
    with open(pfad, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def laden(url, ziel, versuche=2):
    anfrage = urllib.request.Request(url, headers={"User-Agent": "vermdb-oklabi"})
    teil = ziel.with_name(ziel.name + ".teil")
    for versuch in range(1, versuche + 1):
        try:
            with urllib.request.urlopen(anfrage, timeout=30) as antwort, open(teil, "wb") as f:
                shutil.copyfileobj(antwort, f, 1 << 20)
            teil.replace(ziel)
            return True
        except OSError as fehler:
            print(f"  {fehler}", file=sys.stderr)
            if versuch < versuche:
                print(f"  neuer Versuch in {10 * versuch} s", file=sys.stderr)
                time.sleep(10 * versuch)
    teil.unlink(missing_ok=True)
    return False


def bereitstellen(pfad_im_release, soll):
    """Archiv aus oklabi-release/ oder von okstra.de, geprüft; None bei Fehler."""
    name = pfad_im_release.rsplit("/", 1)[-1]
    datei = next((d / name for d in [ABLAGE] + WEITERE_ABLAGEN if (d / name).is_file()),
                 ABLAGE / name)
    if datei.is_file():
        print(f"{name}: aus {datei.parent}")
    else:
        url = f"{RELEASE}/{pfad_im_release}"
        print(f"{name}: lade {url}", flush=True)
        if not laden(url, datei):
            print(f"{name} ließ sich nicht laden. Ist okstra.de nicht erreichbar, die Datei "
                  f"anderswo beschaffen (Ort im Release: {pfad_im_release}) und nach "
                  f"{ABLAGE} legen.", file=sys.stderr)
            return None
    ist = sha256(datei)
    if ist != soll:
        print(f"{datei}: Prüfsumme {ist}, erwartet {soll}. Datei löschen und neu laden.",
              file=sys.stderr)
        return None
    return datei


def auspacken(archiv, ziel):
    """Packt ein Zip- oder Tar-Archiv nach ziel aus; ein einzelner oberster Ordner entfällt."""
    with tempfile.TemporaryDirectory(dir=ziel.parent) as tmp:
        tmp = Path(tmp)
        if zipfile.is_zipfile(archiv):
            with zipfile.ZipFile(archiv) as z:
                z.extractall(tmp)
        else:
            with tarfile.open(archiv) as t:
                t.extractall(tmp, filter="data")
        inhalt = list(tmp.iterdir())
        quelle = inhalt[0] if len(inhalt) == 1 and inhalt[0].is_dir() else tmp
        shutil.move(str(quelle), str(ziel))


def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("ziel", type=Path, help="Verzeichnis für die OKLABI, etwa C:\\Vermessungsdatenbank\\OKLABI")
    p.add_argument("--plattform", choices=sorted(NATIV),
                   default="windows" if platform.system() == "Windows" else "linux")
    p.add_argument("--nativ-archiv", type=Path,
                   help="anderes Paket der nativen Bibliothek aus dem Release, etwa für eine "
                        "andere Linux-Distribution (ohne Prüfsumme)")
    a = p.parse_args()

    ziel = a.ziel.resolve()
    lib, ressourcen = ziel / "lib", ziel / "Ressourcen"
    vorhanden = [str(d) for d in (lib, ressourcen) if d.exists()]
    if vorhanden:
        print(f"Schon vorhanden: {', '.join(vorhanden)}. Für ein neues Release das Verzeichnis "
              f"vorher umbenennen (danach Gitterdateien und res/user.cfg übernehmen).",
              file=sys.stderr)
        return 1
    pfad, soll = NATIV[a.plattform]
    if a.nativ_archiv and not a.nativ_archiv.is_file():
        print(f"{a.nativ_archiv} fehlt", file=sys.stderr)
        return 1
    if not a.nativ_archiv and soll is None:
        print(f"Für {pfad} ist noch keine Prüfsumme hinterlegt; das Archiv mit "
              f"--nativ-archiv angeben.", file=sys.stderr)
        return 1
    ziel.mkdir(parents=True, exist_ok=True)
    ABLAGE.mkdir(exist_ok=True)

    nativ = a.nativ_archiv or bereitstellen(pfad, soll)
    zip_ressourcen = bereitstellen(*RESSOURCEN)
    if nativ is None or zip_ressourcen is None:
        return 1

    print(f"Packe aus nach {ziel}", flush=True)
    auspacken(nativ, lib)
    auspacken(zip_ressourcen, ressourcen)

    print()
    print("Fertig. Werte für den Tomcat:")
    print(f"  Native Bibliothek (java.library.path"
          f"{', unter Windows auch PATH' if a.plattform == 'windows' else ', LD_LIBRARY_PATH'}): {lib}")
    print(f"  OKLABI_HOME: {ressourcen}")
    print(f"  PROJ_LIB:    {ressourcen / 'gdal_data'}")
    print(f"  Fachbedeutungslisten für Datenbank/einrichten.py: {zip_ressourcen}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
