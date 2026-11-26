<!--
SPDX-FileCopyrightText: 2026 interactive instruments GmbH
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Datenbank

Die Vermessungsdatenbank braucht PostgreSQL mit PostGIS und zwei Schemata:

* `oks_meta`: Warteschlange, Aufträge und Projekte, Kopfdaten der Fachobjekte,
  Fachbedeutungslisten, Gebiete. Es wird mit den Skripten in diesem Ordner angelegt.
* das OKSTRA-Schema, etwa `oks_2022`: die Fachdaten. Es bleibt leer; `OkstraSqlUtil` legt
  beim ersten Laden die Tabellen der Objektarten, die Zwischentabelle, die Sequenz für die
  Objektkennungen und seine Metadaten selbst an.

## Einrichten

Die Standardinstallation im Container (`Container/README.md`) erledigt die folgenden
Schritte beim ersten Start selbst, für einen eigenen Tomcat das Skript `einrichten.py` in
einem Aufruf (`doku/Installation.md`, Abschnitt 2.3; Optionen mit `--help`). Hier die
Schritte einzeln mit `psql`.

Voraussetzungen: PostgreSQL mit PostGIS (geprüft mit PostgreSQL 14 und PostGIS 3.5 sowie
PostgreSQL 18 und PostGIS 3.6), eine leere Datenbank und eine Rolle, mit der sich die
Anwendung anmeldet. Die Skripte legen die
Rolle nicht an, weil sie ein Kennwort braucht:

    CREATE ROLE vermdb LOGIN PASSWORD '…';
    CREATE DATABASE vermdb;

Dann als Superuser:

    psql -v rolle=vermdb -d vermdb -f Datenbank/einrichten.sql
    python3 Datenbank/fbliste.py <Ressourcen.zip oder fbliste-Verzeichnis> -o fbliste.sql
    psql -v ON_ERROR_STOP=1 -U vermdb -d vermdb -f fbliste.sql

`einrichten.sql` läuft in einer Transaktion und nimmt diese psql-Variablen:

| Variable | Vorgabe | Bedeutung |
|---|---|---|
| `rolle` | (Pflicht) | Rolle der Anwendung; ihr gehören danach beide Schemata und alle Objekte |
| `okstra_schema` | `oks_2022` | OKSTRA-Schema; muss zum Init-Parameter `okstraVersionDatabase` passen (`2.022` → `oks_2022`) |
| `srid` | `25832` | Lagesystem der Datenbank (Geometrien von Aufträgen, Projekten, Gebieten) |
| `beispiele` | `on` | Beispielwerte für `gebiete` und `fbausschluss` anlegen |

Außer den Tabellen richtet das Skript ein:

* die Erweiterung PostGIS;
* das Recht `CREATE` auf der Datenbank für die Rolle;
* den `search_path` der Rolle in dieser Datenbank mit `oks_meta`: Die Service-Verbindung
  (`svc_vermdb`) fragt einige Tabellen ohne Schema ab.

Die Altdaten- und Planungsdatenbanken (`okstra_data_alt`, `okstra_data_planung`) werden
genauso eingerichtet.

## Dateien

| Datei | Inhalt |
|---|---|
| `einrichten.py` | richtet in einem Aufruf Rolle, Datenbank und Fachbedeutungslisten ein (mit `psql`) |
| `einrichten.sql` | Einstieg; bindet die übrigen Skripte ein |
| `10-tabellen.sql` | Tabellen und Sichten in `oks_meta` |
| `20-funktionen.sql` | Funktionen und Trigger: Kopfdaten über `txtpool`, Pflege von Auftrag, Projekt und Projektgebiet nach dem Laden, `loesche_auftrag` |
| `30-efbversion.sql` | feste Einträge der Fachbedeutungslisten: bundesweit 1.3 bis 1.6 (nicht als CSV im Release, aber Rückfall der Kopfdaten) und `aus Datei` |
| `31-ekoordrefsys.sql` | Lage- und Höhensysteme mit dem Text der OKLABI; erzeugt mit `werkzeuge/Ekoordrefsys.java` |
| `40-beispiele.sql` | Beispielwerte für `gebiete` und `fbausschluss` |
| `fbliste.py` | lädt die Fachbedeutungslisten (siehe unten) |
| `werkzeuge/Ekoordrefsys.java` | erzeugt `31-ekoordrefsys.sql` aus der OKLABI |

## Init-Parameter der Webanwendung

Die Skripte richten die Datenbank so ein, wie es diese Init-Parameter in der `web.xml`
erwarten:

* `svc_schema` = `oks_meta` (die Funktionen verwenden diesen Namen fest);
* `okstraJobliste` = `auftrag`;
* `okstraTxtpool` = `1` und `headerInMetadata` = `1`: Kopfdaten in `oks_meta._header_`
  über die Sicht `_kopfdaten_`;
* `okstraVersionDatabase` passend zu `okstra_schema`.

## Lage- und Höhensysteme (`31-ekoordrefsys.sql`)

`OkstraSqlUtil` schreibt in die Kopfdaten den Text, den die OKLABI zu einem Lage- oder
Höhensystem liefert; der Trigger auf `_kopfdaten_` übersetzt ihn über `ekoordrefsys` in den
Code. Fehlt ein Text, scheitert das Laden. Die Tabelle muss daher zur OKLABI-Version passen
und ist bei einem neuen Release neu zu erzeugen, etwa im Container der Vermessungsdatenbank:

    docker run --rm -v "$PWD/Datenbank/werkzeuge:/w:ro" --entrypoint java <Image> \
      --enable-native-access=ALL-UNNAMED -Djava.library.path=/opt/lib \
      -cp /usr/local/tomcat/lib/OKLABI.jar /w/Ekoordrefsys.java > Datenbank/31-ekoordrefsys.sql

In einer bestehenden Datenbank fehlende Einträge ergänzen:

    INSERT INTO oks_meta.ekoordrefsys … ON CONFLICT (code) DO UPDATE SET wert = EXCLUDED.wert;

## Fachbedeutungslisten (`fbliste.py`)

Füllt `oks_meta.efbversion` und `oks_meta.fbliste` aus den CSV-Dateien der
Fachbedeutungslisten, die mit der OKLABI ausgeliefert werden (Ordner `fbliste/` im Release
oder in `Ressourcen.zip`). Voraussetzung ist Python 3.

    python3 Datenbank/fbliste.py <fbliste-Verzeichnis oder Zip> [...] -o fbliste.sql
    psql -v ON_ERROR_STOP=1 -d <Datenbank> -f fbliste.sql

* Eingelesen werden `fb-<Bundesland>-<Haupt><Unter>.csv` und für die bundesweiten Listen
  1.3 bis 1.6 `OKSTRA_FB-13.csv` bis `OKSTRA_FB_16.csv` (nicht im Release enthalten).
* Übernommen werden die ersten sechs Spalten (Datum, Fachbedeutung, RAS-Verm, Bezeichnung,
  Symbolart, Landescode); leere Werte werden `NULL`. Zeilen ohne Fachbedeutung,
  Bezeichnung oder Symbolart und Zeilen mit der Symbolart „Ebene“ entfallen; die Symbolart
  wird auf `Punkt`, `Linie`, `Flaeche` und `Text` vereinheitlicht.
* Das Skript läuft in einer Transaktion. Fehlende Versionen werden in `efbversion`
  ergänzt; vorhandene Kennungen bleiben unverändert, weil die Kopfdaten und die
  Datentabellen auf sie verweisen. `fbliste` wird vollständig neu geladen.

Geprüft am Bestand aus Sachsen-Anhalt (Stand Oktober 2026): Alle 66 dort geladenen Listen
entstehen zeilengleich; die Kennungen bleiben, neu hinzu kommen die Listen, die im Bestand
fehlen.
