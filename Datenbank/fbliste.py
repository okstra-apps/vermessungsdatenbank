#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 interactive instruments GmbH
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Erzeugt aus den Fachbedeutungslisten der OKLABI ein SQL-Skript, das die Tabellen
oks_meta.efbversion und oks_meta.fbliste einer Vermessungsdatenbank füllt.

Eingabe sind Verzeichnisse oder Zip-Archive mit den CSV-Dateien der OKLABI:

* fb-<Bundesland>-<Haupt><Unter>.csv, etwa fb-Sachsen-Anhalt-35.csv für die Liste 3.5
  (Ordner fbliste/ im Release, auch in Ressourcen.zip),
* OKSTRA_FB-13.csv bis OKSTRA_FB_16.csv für die bundesweiten Listen 1.3 bis 1.6.

Das erzeugte Skript läuft in einer Transaktion: Es ergänzt in oks_meta.efbversion die
Versionen, die dort noch fehlen (vorhandene Kennungen bleiben unverändert, neue erhalten
die nächste freie), und lädt oks_meta.fbliste vollständig neu. Ausführen etwa mit

    python3 fbliste.py fbliste.zip > fbliste.sql
    psql -v ON_ERROR_STOP=1 -d <Datenbank> -f fbliste.sql
"""

import argparse
import csv
import io
import re
import sys
import zipfile
from pathlib import Path

DATEI_LAND = re.compile(r'^fb-(?P<land>.+)-(?P<haupt>\d)(?P<unter>\d)\.csv$', re.IGNORECASE)
DATEI_BUND = re.compile(r'^OKSTRA_FB[-_]1(?P<unter>\d)\.csv$', re.IGNORECASE)


def lies_text(daten):
    """Die Listen sind meist Windows-1252, einzelne UTF-8."""
    try:
        return daten.decode('utf-8')
    except UnicodeDecodeError:
        return daten.decode('cp1252')


def version_aus_name(name):
    """(Bundesland, Hauptversion, Unterversion) aus dem Dateinamen, sonst None."""
    m = DATEI_LAND.match(name)
    if m:
        return m['land'], int(m['haupt']), int(m['unter'])
    m = DATEI_BUND.match(name)
    if m:
        return '', 1, int(m['unter'])
    return None


def sammle_dateien(quellen):
    """Liefert {Version: Text} aus Verzeichnissen und Zip-Archiven."""
    dateien = {}
    for quelle in quellen:
        pfad = Path(quelle)
        if pfad.is_dir():
            eintraege = [(p.name, p.read_bytes) for p in sorted(pfad.rglob('*.csv'))]
        elif zipfile.is_zipfile(pfad):
            archiv = zipfile.ZipFile(pfad)
            eintraege = [(n.rsplit('/', 1)[-1], (lambda n=n: archiv.read(n)))
                         for n in sorted(archiv.namelist()) if n.lower().endswith('.csv')]
        else:
            sys.exit(f'Weder Verzeichnis noch Zip-Archiv: {quelle}')
        for name, lesen in eintraege:
            version = version_aus_name(name)
            if version is None:
                continue
            if version in dateien:
                sys.exit(f'Liste {version} mehrfach angegeben ({name})')
            dateien[version] = lies_text(lesen())
    return dateien


def vereinheitliche_symbolart(s):
    """Vereinheitlicht die Symbolart auf Punkt, Linie, Flaeche und Text."""
    if s == 'Fläche':
        return 'Flaeche'
    if s != 'Flaeche' and s.startswith('Flaeche'):
        return 'Flaeche'
    if s != 'Linie' and 'Linie' in s:
        return 'Linie'
    if s != 'Punkt' and 'Punkt' in s:
        return 'Punkt'
    if s != 'Text' and 'Text' in s:
        return 'Text'
    return s


def zeilen(text):
    """Die Zeilen einer Liste als (datum, fachbedeutung, ras_verm, bezeichnung, symbolart,
    landescode). Gelesen werden die ersten sechs Spalten nach ihrer Position; leere Werte
    werden NULL. Die Spalte Landescode gibt es nur in den Länderlisten."""
    leser = csv.reader(io.StringIO(text, newline=''), delimiter=';', quotechar='"')
    kopf = next(leser, [])
    mit_landescode = len(kopf) > 5 and kopf[5].strip() == 'Landescode'
    for felder in leser:
        f = [x if x != '' else None for x in (felder + [''] * 6)[:6]]
        if not mit_landescode:
            f[5] = None
        # Fachbedeutung, Bezeichnung und Symbolart müssen belegt sein.
        if f[1] is None or f[3] is None or f[4] is None:
            continue
        f[4] = vereinheitliche_symbolart(f[4])
        if f[4].strip() == '' or f[4] == 'Ebene':
            continue
        yield f


def lfdnr(haupt, unter):
    """Laufende Nummer wie in RequestData: 1.3 bis 1.6 -> 2 bis 5, ab 2.0 -> 6 + 10 * (Haupt - 2) + Unter."""
    return unter - 1 if haupt == 1 else 6 + 10 * (haupt - 2) + unter


def sql_text(s):
    return "'" + s.replace("'", "''") + "'"


def copy_wert(s):
    if s is None:
        return r'\N'
    return s.replace('\\', '\\\\').replace('\t', '\\t').replace('\n', '\\n').replace('\r', '\\r')


def schreibe_sql(dateien, aus):
    aus.write('-- Erzeugt von Datenbank/fbliste.py; nicht von Hand ändern.\n')
    aus.write("SET client_encoding = 'UTF8';\nBEGIN;\n\n")
    aus.write('CREATE TEMPORARY TABLE fbliste_neu (bundesland text, hauptversion integer,'
              ' unterversion integer, datum text, fachbedeutung text, ras_verm text,'
              ' bezeichnung text, symbolart text, landescode text) ON COMMIT DROP;\n\n')

    aus.write('-- Fehlende Versionen ergänzen, vorhandene Kennungen bleiben.\n')
    aus.write("INSERT INTO oks_meta.efbversion (id, hauptversion, unterversion, bundesland, lfdnr_vsn)\n"
              "  SELECT -1, 0, 0, 'aus Datei', -1\n"
              "  WHERE NOT EXISTS (SELECT 1 FROM oks_meta.efbversion WHERE id = -1);\n")
    for (land, haupt, unter) in sorted(dateien, key=lambda v: (v[0], v[1], v[2])):
        aus.write('INSERT INTO oks_meta.efbversion (id, hauptversion, unterversion, bundesland, lfdnr_vsn)\n'
                  '  SELECT (SELECT greatest(coalesce(max(id), 0), 0) + 1 FROM oks_meta.efbversion), '
                  f'{haupt}, {unter}, {sql_text(land)}, {lfdnr(haupt, unter)}\n'
                  '  WHERE NOT EXISTS (SELECT 1 FROM oks_meta.efbversion '
                  f'WHERE bundesland = {sql_text(land)} AND hauptversion = {haupt} AND unterversion = {unter});\n')

    aus.write('\nCOPY fbliste_neu FROM stdin;\n')
    anzahl = 0
    for (land, haupt, unter), text in sorted(dateien.items()):
        for z in zeilen(text):
            aus.write('\t'.join([copy_wert(land), str(haupt), str(unter)] + [copy_wert(x) for x in z]) + '\n')
            anzahl += 1
    aus.write('\\.\n\n')

    aus.write('TRUNCATE oks_meta.fbliste;\n'
              'INSERT INTO oks_meta.fbliste (fbversion, datum, fachbedeutung, ras_verm, bezeichnung, symbolart, landescode)\n'
              '  SELECT e.id, n.datum, n.fachbedeutung, n.ras_verm, n.bezeichnung, n.symbolart, n.landescode\n'
              '  FROM fbliste_neu n JOIN oks_meta.efbversion e\n'
              '    ON e.bundesland = n.bundesland AND e.hauptversion = n.hauptversion AND e.unterversion = n.unterversion;\n\n'
              'COMMIT;\n')
    return anzahl


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    parser.add_argument('quelle', nargs='+', help='Verzeichnis oder Zip-Archiv mit den CSV-Dateien')
    parser.add_argument('-o', '--ausgabe', help='SQL-Datei (Vorgabe: Standardausgabe)')
    args = parser.parse_args()

    dateien = sammle_dateien(args.quelle)
    if not dateien:
        sys.exit('Keine Fachbedeutungslisten gefunden.')
    if args.ausgabe:
        with open(args.ausgabe, 'w', encoding='utf-8', newline='\n') as aus:
            anzahl = schreibe_sql(dateien, aus)
    else:
        sys.stdout.reconfigure(encoding='utf-8', newline='\n')
        anzahl = schreibe_sql(dateien, sys.stdout)
    print(f'{len(dateien)} Listen, {anzahl} Zeilen', file=sys.stderr)


if __name__ == '__main__':
    main()
