#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 interactive instruments GmbH
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Wandelt ein Höhengitter im NTv2-Format (.gsb) in das GTX-Format (.gtx) für PROJ.

HOETRA2016 (DHHN92 → DHHN2016) der Bezirksregierung Köln, Geobasis NRW, wird als
NTv2-Datei Hoetra2016.gsb abgegeben; die Höhendifferenz in Metern steht darin im Feld der
Breitenverschiebung. PROJ nimmt Höhengitter aber nur als .gtx oder GeoTIFF an. Das Skript
übernimmt dieses Feld unverändert in eine .gtx-Datei, wie
`gdal_translate -b 1 -of GTX Hoetra2016.gsb Hoetra2016.gtx` (Ergebnis byte-gleich), ohne
dass GDAL installiert sein muss. Nur Standardbibliothek, unter Linux und Windows.

    gsb2gtx.py <Hoetra2016.gsb oder Zip-Archiv mit der .gsb> <Ziel.gtx>
"""

import argparse
import struct
import sys
import zipfile
from pathlib import Path


def lies_gsb(quelle):
    if zipfile.is_zipfile(quelle):
        with zipfile.ZipFile(quelle) as z:
            namen = [n for n in z.namelist() if n.lower().endswith(".gsb")]
            if len(namen) != 1:
                raise ValueError(f"{quelle}: erwartet genau eine .gsb-Datei, gefunden {namen}")
            return z.read(namen[0])
    return Path(quelle).read_bytes()


def kopf(daten, start, anzahl):
    """Liest anzahl Kopfsätze (8 Byte Name, 8 Byte Wert) ab start."""
    werte = {}
    for i in range(anzahl):
        satz = daten[start + 16 * i:start + 16 * (i + 1)]
        werte[satz[:8].decode("ascii").strip()] = satz[8:]
    return werte


def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("quelle", type=Path)
    p.add_argument("ziel", type=Path)
    a = p.parse_args()

    d = lies_gsb(a.quelle)
    if d[:8] != b"NUM_OREC":
        print(f"{a.quelle}: keine NTv2-Datei", file=sys.stderr)
        return 1
    ende = "<" if struct.unpack("<i", d[8:12])[0] == 11 else ">"
    ganz = lambda b: struct.unpack(ende + "i", b[:4])[0]
    zahl = lambda b: struct.unpack(ende + "d", b)[0]

    ueber = kopf(d, 0, ganz(d[8:16]))
    if ganz(ueber["NUM_FILE"]) != 1:
        print("Nur NTv2-Dateien mit genau einem Teilgitter werden unterstützt", file=sys.stderr)
        return 1
    if ueber["GS_TYPE"].strip() != b"SECONDS":
        print("Nur Gitter in Bogensekunden (GS_TYPE SECONDS) werden unterstützt", file=sys.stderr)
        return 1
    start = 16 * ganz(ueber["NUM_OREC"])
    teil = kopf(d, start, ganz(ueber["NUM_SREC"]))
    s_lat, n_lat = zahl(teil["S_LAT"]), zahl(teil["N_LAT"])
    e_long, w_long = zahl(teil["E_LONG"]), zahl(teil["W_LONG"])
    lat_inc, long_inc = zahl(teil["LAT_INC"]), zahl(teil["LONG_INC"])
    anzahl = ganz(teil["GS_COUNT"])
    zeilen = int(round((n_lat - s_lat) / lat_inc)) + 1
    spalten = int(round((w_long - e_long) / long_inc)) + 1
    if zeilen * spalten != anzahl:
        print(f"Gittergröße {zeilen} × {spalten} passt nicht zu GS_COUNT {anzahl}", file=sys.stderr)
        return 1
    print(f"{ueber['SYSTEM_F'].decode().strip()} → {ueber['SYSTEM_T'].decode().strip()}, "
          f"{zeilen} × {spalten} Knoten")

    # Ursprung und Schrittweite in Grad, Länge positiv nach Osten, gerechnet wie GDAL
    # (Pixelmitte aus der Pixelecke), damit das Ergebnis byte-gleich ist
    dx, dy = long_inc / 3600.0, -lat_inc / 3600.0
    x0 = -(w_long + long_inc * 0.5) / 3600.0
    y0 = (n_lat + lat_inc * 0.5) / 3600.0
    lon0 = x0 + dx * 0.5
    lat0 = y0 + dy * (zeilen - 0.5)

    # NTv2: Sätze zu je vier float32 (Breite, Länge, Genauigkeiten), Zeilen von Süden nach
    # Norden, in der Zeile von Osten nach Westen. GTX: float32 big-endian, Zeilen von Süden
    # nach Norden, in der Zeile von Westen nach Osten.
    satz = struct.Struct(ende + "4f")
    beginn = start + 16 * ganz(ueber["NUM_SREC"])
    werte = bytearray()
    for z in range(zeilen):
        zeile = [satz.unpack_from(d, beginn + 16 * (z * spalten + s))[0] for s in range(spalten)]
        werte += struct.pack(f">{spalten}f", *reversed(zeile))
    with open(a.ziel, "wb") as f:
        f.write(struct.pack(">4d2i", lat0, lon0, -dy, dx, zeilen, spalten))
        f.write(werte)
    print(f"{a.ziel} geschrieben")
    return 0


if __name__ == "__main__":
    sys.exit(main())
