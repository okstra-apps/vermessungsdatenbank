<!--
SPDX-FileCopyrightText: 2026 interactive instruments GmbH
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Testdaten

OKSTRA-Dateien für die Integrationstests (`DatenbankLader/src/integrationTest`) und für
Beispiele in der Dokumentation. Alle Dateien gehen auf zwei Beispieldatensätze der
Landesstraßenbaubehörde Sachsen-Anhalt (LSBB) zurück, erstellt mit VESTRA INFRAVISION.

| Datei | Inhalt | Herkunft |
|---|---|---|
| `musterlageplan.xml` | „Musterlageplan mit OKSTRA-Objekten zur Prüfung der Im- und Exportschnittstellen“, OKSTRA 2.022, Windows-1252, 42/83 GK 4 (EPSG 2398) mit DHHN2016, FB-Liste Sachsen-Anhalt 3.5; 189 Fachobjekte | LSBB, 12.11.2026 |
| `beispiel.xml` | Testprojekt, OKSTRA 2.022, Windows-1252, 42/83 GK 4 mit DHHN2016, FB-Liste Sachsen-Anhalt 3.5; 125 Fachobjekte | LSBB, 11.11.2026 |
| `beispiel.cte` | `beispiel.xml` als CTE | abgeleitet |
| `musterlageplan-2020-utm33.xml` | `musterlageplan.xml` als OKSTRA 2.020 in ETRS89/UTM 33, UTF-8, mit OKSTRA-IDs | abgeleitet |
| `fehler-kennung.xml` | `beispiel.xml` mit der unzulässigen Kennung `04;0` einer Baumgattung (Lieferung wird abgelehnt) | abgeleitet |
| `fehler-fachbedeutung.xml` | `beispiel.xml` mit der ungültigen Fachbedeutung `0000.X999` an einem Linienobjekt | abgeleitet |
| `fehler-schema.xml` | `beispiel.xml` mit dem nicht im Schema vorgesehenen Element `okstra:Bezeichnungg` an der Trasse | abgeleitet |

## Was die Datensätze abdecken

**Musterlageplan** (Erläuterungen im Plan als Beschriftungen in der Datei): Linie mit
Signaturausrichtung, Punkte mit Drehwinkel und mit identischer Lage, Symbol, Beschriftungen
mit Drehwinkel, Textzeiger, verschiedenen Textbasispunkten und Sonderzeichen
(`ä ö ü ß @ ! " § % & / ( ) {} [] = ? ~ m² m³`), Linien mit Kreisbögen am Anfang, in der Mitte
und am Ende, Linie mit deckungsgleicher Lage und unterschiedlicher Höhe, Fläche mit Bogen,
Bäume, Böschungen mit und ohne Kehle und mit Bogen.

**Beispiel:** Punkt-, Linien- und Flächenobjekte, Böschungen mit Böschungskantenabschnitten,
Bäume, Beschriftungen mit mehrzeiligem Text (`<br>`) und Sonderzeichen (`€`, `°`, `&`, `ß`),
eine Linie aus einer Strecke und zwei Kreisbögen mit Höhen von 70 bis 72 m.

Beide enthalten keine OKSTRA-IDs, keine fehlerhaften Objekte und keine Altdaten oder
Planungen.

## Änderungen gegenüber den gelieferten Dateien

* `okstra-basis:author`: Kürzel des Bearbeiters durch `Beispiel` ersetzt.
* `okstra-basis:name`: Dateiname der Lieferung durch den Namen hier ersetzt.
* `beispiel.xml`: E-Mail-Adresse in einer Beschriftung durch `alpha@example.org` ersetzt.

Die Prüfsumme im Kommentar am Ende der Dateien passt deshalb nicht mehr; die OKLABI wertet
sie beim Lesen nicht aus. Die Protokolle der LSBB zu Prüfen und Laden sind nicht
übernommen (sie enthalten Pfade der Behörde); ihre Statistik der Objektarten stimmt mit der
der Vermessungsdatenbank überein.

## Abgeleitete Dateien

* `beispiel.cte`: mit `werkzeuge/NachCte.java` im Container der Vermessungsdatenbank
  (Anleitung unten). Ohne die Einstellung `pExpressGeometrie` schreibt die OKLABI CTE ohne
  Geometrie. Die Reihenfolge der Objekte kann von Lauf zu Lauf wechseln. Die
  Vermessungsdatenbank weist CTE ab (`doku/Schnittstelle.md`, Abschnitt 7); der
  Integrationstest prüft das mit dieser Datei.
* `musterlageplan-2020-utm33.xml`: `musterlageplan.xml` geladen (`f22=2398`) und entladen mit
  `request=Read&Auftrag=<Nr>&Version=2.020&f22=25833`; `okstra-basis:name` danach auf den
  Dateinamen gesetzt. Die Transformation lief mit dem Gitter NTv2LSBB_LSA
  (`VERMDB_GITTER_IMAGE=NTv2LSBB_LSA`, wie in den Integrationstests); neu erzeugt am
  17.11.2026, vorher ohne Gitter.
* `fehler-*.xml`: je eine Stelle in `beispiel.xml` geändert, wie in der Tabelle angegeben.
* `okws/beispiel-wfs.xml` (Vorgabeantwort des OkWS-Backends auf `SignOfLife`):
  `beispiel.xml` im WFS-Format (`wfs:FeatureCollection`, UTF-8) mit
  `werkzeuge/NachWfs.java`, aufgerufen wie `NachCte` (unten) mit
  `NachWfs beispiel.xml beispiel-wfs.xml`. Das Werkzeug setzt Gauß-Krüger 4 mit DHHN2016,
  damit die Geometrien `srsName` tragen wie in den Antworten aus der Datenbank.

`NachCte` im laufenden Container ausführen (Übersetzen gegen das `OKLABI.jar` des Release,
Ausführen mit der nativen OKLABI des Image):

    javac --release 21 -implicit:none -sourcepath leer -cp build/oklabi/archiv/OKLABI.jar \
      -d /tmp/nachcte testdaten/werkzeuge/NachCte.java
    docker cp /tmp/nachcte/NachCte.class vermdb-vermdb-1:/tmp/
    docker cp testdaten/beispiel.xml vermdb-vermdb-1:/tmp/
    docker exec -w /tmp vermdb-vermdb-1 sh -c 'export OKLABI_HOME=/opt/OKLABI_HOME \
        PROJ_LIB=/opt/OKLABI_HOME/gdal_data LD_LIBRARY_PATH=/usr/local/tomcat/native-jni-lib:/opt/lib &&
      java --enable-native-access=ALL-UNNAMED -Djava.library.path=$LD_LIBRARY_PATH \
        -cp /tmp:/usr/local/tomcat/lib/OKLABI.jar NachCte beispiel.xml beispiel.cte'

Mit `pExpressGeometrie` kann die OKLABI gelegentlich im nativen Code hängen bleiben; dann den
Prozess beenden und das Werkzeug erneut starten.
    docker cp vermdb-vermdb-1:/tmp/beispiel.cte testdaten/

## Lizenz

Beide Datensätze der LSBB und die daraus abgeleiteten Dateien stehen unter der
[Datenlizenz Deutschland – Namensnennung – Version 2.0](https://www.govdata.de/dl-de/by-2-0)
(`DL-DE-BY-2.0`). Namensnennung:

* `beispiel.xml`, `beispiel.cte`, `fehler-*.xml` und `okws/beispiel-wfs.xml`:
  Landesstraßenbaubehörde Sachsen-Anhalt, dl-de/by-2-0
* `musterlageplan.xml`, `musterlageplan-2020-utm33.xml`: © GeoBasis-DE / LVermGeo ST,
  dl-de/by-2-0

Das gilt auch für die Referenzdateien der Integrationstests, die Auszüge daraus enthalten.
Angaben je Datei in `REUSE.toml`.

Die Dateien dienen als Beispiele für die Schnittstelle.
