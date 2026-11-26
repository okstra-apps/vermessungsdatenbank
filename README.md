<!--
SPDX-FileCopyrightText: 2026 interactive instruments GmbH
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Vermessungsdatenbank

Die Vermessungsdatenbank verwaltet Vermessungsdaten des Straßenbaus im Format des OKSTRA
(Objektkatalog für das Straßen- und Verkehrswesen). Sie nimmt Lieferungen über HTTP
entgegen, prüft sie, rechnet sie in die OKSTRA-Version, das Lage- und das Höhensystem der
Datenbank um und lädt sie in PostgreSQL mit PostGIS; aus der Datenbank gibt sie Daten als
OKSTRA-Datei wieder ab. Die fachliche Arbeit am OKSTRA leistet die OKSTRA-Klassenbibliothek
(OKLABI) der Bundesanstalt für Straßen- und Verkehrswesen. Die Anwendung ist ein Servlet für
Apache Tomcat 11 mit Java 25 (`doku/Ueberblick.md`).

## Schnellstart

Mit Docker Compose, samt Datenbank und ohne JDK auf dem Rechner:

    docker compose -f Container/compose.yaml up -d

Der erste Start baut das Image und richtet die Datenbank ein. Die Schnittstelle liegt dann
unter `http://127.0.0.1:8099/Okstra2Db/Okstra2DbServlet`, etwa
`…?request=Queue` für die Auftragsliste. Einstellungen (Behörde, Fachbedeutungsliste) in
`Container/.env` oder einem eigenen Konfigurationsverzeichnis, siehe `Container/README.md`.

## Dokumentation

* `doku/Ueberblick.md`: Zweck, Komponenten und Abläufe mit Diagrammen
* `doku/Installation.md`: Installation im Container und mit eigenem Tomcat unter Linux und
  Windows, Prüfung, Update
* `doku/Konfiguration.md`: alle Parameter der `web.xml`, Datenquellen, Umgebung des Tomcat,
  Umgebungsvariablen
* `doku/Schnittstelle.md`: alle Anfragen über HTTP und den OkWS-Kanal, Auftragsstatus,
  Eingabeprüfung, bekannte Einschränkungen
* `doku/beispiele/`: Anfragen mit den Beispieldaten als Hurl-Dateien, mit Prüfung der
  Antworten
* `doku/Lizenzen.md`: Lizenzen, Drittkomponenten in Repository, WAR und Image, Gitterdateien,
  Stücklisten
* `doku/Betrieb.md`: Überwachung, Protokolle, Beispiele für Fehlerbilder, Aufräumen,
  Datensicherung, Fachbedeutungslisten
* `Datenbank/README.md`: Einrichten der Datenbank
* `Container/README.md`: Container, Einstellungen, Kennwörter, Gitterdateien

## Bau

Gebaut wird mit Gradle und einem installierten JDK 25. Die beiden Java-Archive des
OKLABI-Release lädt der Bau beim ersten Mal von okstra.de nach `oklabi-release/` (siehe
dort).

    ./gradlew build

Ergebnisse:

* `DatenbankLader/build/libs/Okstra2Db.war`: die Webanwendung für einen vorhandenen
  Tomcat 11, mit `OkstraSqlUtil` und den übrigen Bibliotheken.
* `DatenbankLader/build/tomcat-lib/`: für das `lib`-Verzeichnis des Tomcat `OKLABI.jar`
  (zur nativen OKLABI) und der PostgreSQL-Treiber für die Datenquellen der `server.xml`, in
  derselben Version wie im WAR.
* `DatenbankLader/build/reports/cyclonedx/bom.json`: Stückliste der Komponenten (SBOM) im
  Format CycloneDX.

Die Abhängigkeiten werden aus Maven Central geladen und gegen die Prüfsummen in
`gradle/verification-metadata.xml` geprüft, die Archive des OKLABI-Release gegen die
Prüfsummen in `gradle.properties`. Ohne JDK baut das Image alles selbst (Schnellstart).

## Mitwirken

Hinweise zu Bau, Tests, Regeln für Änderungen und Herkunftsnachweis in `CONTRIBUTING.md`.
Sicherheitslücken bitte vertraulich melden, wie in `SECURITY.md` beschrieben.
Repository und Issue-Tracker: <https://github.com/okstra-apps/vermessungsdatenbank>

## Lizenz

Die Vermessungsdatenbank steht unter der GNU Affero General Public License, Version 3 oder
später (`AGPL-3.0-or-later`, Text in `LICENSES/`). Eine Zusatzerlaubnis nach § 7 erlaubt die
Verbindung mit der OKSTRA-Klassenbibliothek (OKLABI) und `OkstraSqlUtil` der Bundesanstalt
für Straßen- und Verkehrswesen; Wortlaut und Hinweise zu Drittkomponenten in `NOTICE`. Die
Lizenzangaben folgen der [REUSE-Spezifikation](https://reuse.software/spec-3.3/).
Einzelheiten zu Drittkomponenten und ihren Lizenzen: `doku/Lizenzen.md`.

Daten für die Tests (Dateien in `testdaten/` und Referenzdateien der Integrationstests)
stehen unter der [Datenlizenz Deutschland – Namensnennung – Version 2.0](https://www.govdata.de/dl-de/by-2-0)
(`DL-DE-BY-2.0`), Namensnennung je Datei in `REUSE.toml` und `testdaten/README.md`.
