#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 interactive instruments GmbH
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Bereitet die Ressourcen der OKLABI im Image für die Gitterdateien des Image vor.

Entfernt die Gitterdateien aus ntv2/ und die Zeilen "Gitterdatei=" aus res/config.cfg
(das Release 1.6.5.0 enthält noch einige, spätere ggf. nicht mehr) und schaltet die
Benutzerkonfiguration ~/ntv2/gitter.cfg ein. Das Verzeichnis ntv2/ samt gitter.cfg stellt
vermdb.sh beim Start aus den eingeschalteten Gittern des Image (Container/gitter-image/,
VERMDB_GITTER_IMAGE) und den eigenen (Container/gitter/) zusammen.

    gitter.py <Ressourcenverzeichnis>
"""

import sys
from pathlib import Path

ZEILE = b"BenutzerKonfiguration=~/ntv2/gitter.cfg"


def main():
    ressourcen = Path(sys.argv[1])
    ntv2 = ressourcen / "ntv2"
    ntv2.mkdir(exist_ok=True)
    for datei in ntv2.iterdir():
        print(f"entfernt: {datei.name}")
        datei.unlink()

    cfg = ressourcen / "res" / "config.cfg"
    roh = cfg.read_bytes()
    umbruch = b"\r\n" if b"\r\n" in roh else b"\n"
    zeilen = roh.split(umbruch)
    neu = []
    for z in zeilen:
        s = z.strip()
        if s.startswith(b"Gitterdatei="):
            print(f"entfernt: {s.decode('latin-1')}")
            continue
        if s.lstrip(b"#").startswith(b"BenutzerKonfiguration="):
            continue
        neu.append(z)
    while neu and neu[-1] == b"":
        neu.pop()
    neu.append(ZEILE)
    cfg.write_bytes(umbruch.join(neu) + umbruch)
    print(f"{cfg}: {ZEILE.decode()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
