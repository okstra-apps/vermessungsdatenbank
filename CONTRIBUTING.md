<!--
SPDX-FileCopyrightText: 2026 interactive instruments GmbH
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Mitwirken

Beiträge sind willkommen: Fehlermeldungen, Hinweise zur Dokumentation, Korrekturen und
Erweiterungen. Das Repository liegt auf GitHub unter
<https://github.com/okstra-apps/vermessungsdatenbank>. Fehler und Vorschläge bitte als
[Issue](https://github.com/okstra-apps/vermessungsdatenbank/issues) melden, Änderungen als Pull-Request
einreichen. **Sicherheitslücken nicht öffentlich melden**, sondern wie in `SECURITY.md`
beschrieben.

Derzeit pflegt die interactive instruments GmbH die Vermessungsdatenbank: Sie prüft Beiträge
und entscheidet über ihre Aufnahme. Geplant ist, das Repository künftig auch für weitere
Pflegende zu öffnen.

Die Vermessungsdatenbank ist im Betrieb: Clients verlassen sich auf ihre Schnittstelle, auch
auf Eigenheiten. Das bestimmt die Regeln unten.

## Bau und Tests

Gebraucht wird ein JDK 25; die beiden Java-Archive des OKLABI-Release lädt der Bau beim
ersten Mal von okstra.de nach `oklabi-release/` (siehe dort):

    ./gradlew build        # übersetzen, Unit-Tests, WAR, Bibliotheken für den Tomcat, SBOM
    ./gradlew test         # nur die Unit-Tests

Ohne JDK auf dem Rechner baut das Image alles selbst (`Container/README.md`):

    docker compose -f Container/compose.yaml up -d --build

Die Unit-Tests (`DatenbankLader/src/test/java`, JUnit) prüfen die Teile, die ohne native
OKLABI auskommen: Auswertung der Parameter, Werte in SQL-Anweisungen, Maskierung,
Eingabeprüfung.

Die Integrationstests (`DatenbankLader/src/integrationTest/java`, JUnit mit Testcontainers)
prüfen die Schnittstelle gegen die Container-Installation mit leerer Datenbank und den
Dateien aus `testdaten/`: Prüfen und Laden (Abweisen von CTE, Versionen, Lagesysteme, Sonderzeichen,
Kreisbögen, fehlerhafte Lieferungen), Entladen (synchron, asynchron, Zielversion,
Transformation, Fläche), Auftragslisten und Löschen. Sie brauchen Docker; die Images laufen
nur für amd64 (auf anderen Rechnern unter Emulation, dann dauert ein Lauf einige Minuten).
Sie gehören nicht zu `build`, weil der Image-Bau selbst `./gradlew build` ausführt:

    ./gradlew integrationTest                              # Image bauen, Tests ausführen
    ./gradlew integrationTest -PohneBildbau                # vorhandenes Image nehmen
    ./gradlew integrationTest -PreferenzenAktualisieren    # Referenzdateien neu schreiben

Das Image entsteht aus dem WAR des Baus (`BAU=lokal`) und heißt `vermdb:integrationstest`
(`-PintegrationstestBild=vermdb:<tag>`). Längere Ergebnisse vergleichen die Tests mit
Referenzdateien unter `src/integrationTest/resources/erwartet/`, nachdem Zeitangaben,
Auftragsnummern und ähnliches vereinheitlicht sind; bei einer Abweichung liegt das Ergebnis
unter `DatenbankLader/build/integrationstest/ist/`. Neu geschriebene Referenzdateien vor dem
Einchecken durchsehen. Bekannte Fehler (`doku/Schnittstelle.md`, Abschnitt 8) halten die
Tests als bestehendes Verhalten fest; eine Korrektur ändert dann auch den Test.

Die Beispiele in `doku/beispiele/` (Hurl) gehören zur Dokumentation und prüfen deren
Anfragen und Antworten. `integrationTest` führt sie mit aus (`BeispieleIT`, Hurl als
Container im Netz der Installation); Ausgabe und Bericht von Hurl liegen danach unter
`DatenbankLader/build/integrationstest/hurl/`. Ändert sich eine Antwort, sind Beispiele und
Texte in `doku/` mitzuziehen. Von Hand gegen eine laufende Installation wie in
`doku/beispiele/README.md`.

## Regeln für Änderungen

* **Schnittstelle stabil halten.** Anfragen, Parameter, Antworten und Status bleiben, wie sie
  sind (`doku/Schnittstelle.md`). Neue Parameter sind möglich, wenn bestehende Anfragen sich
  nicht anders verhalten.
* **Verhaltensänderungen vorher abstimmen.** Auch fehlerhaftes Verhalten ist bekannt und
  dokumentiert (`doku/Schnittstelle.md`, Abschnitt 8); eine Korrektur, die Antworten oder
  gespeicherte Daten ändert, zuerst in einem Issue vorschlagen.
* **Dokumentation mitziehen.** Änderungen an Parametern, Anfragen, Installation oder Betrieb
  gehören in dieselbe Änderung wie die Dokumentation in `doku/` und die READMEs.
* **Keine Produktivdaten, keine Zugangsdaten** im Repository, auch nicht in Tests, Beispielen
  oder Protokollen. Testdaten nur, wenn ihre Weitergabe geklärt ist.
* **Sprache:** Bezeichner neuer Klassen und Methoden, Kommentare, Meldungen und
  Dokumentation auf Deutsch, wie im übrigen Code.
* **Abhängigkeiten** kommen aus Maven Central und werden gegen
  `gradle/verification-metadata.xml` geprüft. Nach einer Änderung die Prüfsummen mit einem
  leeren Gradle-Cache neu schreiben, damit auch die Metadaten der Plugins erfasst sind, und
  das Ergebnis durchsehen:

      GRADLE_USER_HOME=$(mktemp -d) ./gradlew --write-verification-metadata sha256 build

* **OKLABI und `OkstraSqlUtil`** gehören der Bundesanstalt für Straßen- und Verkehrswesen
  und nicht zu diesem Repository. Fehler darin bitte an die Pflegestelle der OKLABI melden
  (https://www.okstra.de/oklabi); im Servlet nur umgehen, wo es nötig ist, und den Behelf
  kommentieren.

## Lizenz und Herkunft

Beiträge stehen unter der Lizenz des Projekts: GNU Affero General Public License, Version 3
oder später (`AGPL-3.0-or-later`), mit der Zusatzerlaubnis nach § 7 für die Verbindung mit
OKLABI und `OkstraSqlUtil` (`NOTICE`). Daten für die Tests (Testdaten, Referenzdateien)
stehen unter der Datenlizenz Deutschland – Namensnennung – Version 2.0 (`DL-DE-BY-2.0`). Rechte werden nicht übertragen; die Urheberrechte an
einem Beitrag bleiben bei den Beitragenden.

**Herkunft bestätigen.** Jeder Commit trägt eine Zeile `Signed-off-by: Name <E-Mail>`
(`git commit -s`). Damit bestätigt die beitragende Person das
[Developer Certificate of Origin 1.1](https://developercertificate.org/): dass sie den
Beitrag unter der Lizenz des Projekts einreichen darf. Eine Prüfung im Pull-Request sieht
nach, ob alle Commits die Zeile tragen; Commits im Browser auf GitHub bekommen sie
automatisch. Fehlt sie, lässt sie sich nachtragen und der Zweig erneut übertragen:

    git rebase --signoff main
    git push --force-with-lease

**Lizenzangaben.** Jede Datei trägt Lizenzangaben nach der
[REUSE-Spezifikation](https://reuse.software/spec-3.3/), als Kommentar am Anfang:

    SPDX-FileCopyrightText: <Jahr> <Name oder Organisation>
    SPDX-License-Identifier: AGPL-3.0-or-later

Wer eine bestehende Datei wesentlich ändert, ergänzt eine eigene Zeile
`SPDX-FileCopyrightText`. Dateien ohne Kommentarmöglichkeit stehen in `REUSE.toml`, neue
Referenzdateien der Integrationstests dort mit `DL-DE-BY-2.0`. Prüfen
mit dem Werkzeug `reuse`:

    reuse lint
    docker run --rm -v "$PWD:/data" fsfe/reuse lint      # ohne Installation

## Versionen

Die Versionen folgen [Semantic Versioning 2.0.0](https://semver.org/lang/de/):

* **Hauptversion** (`3.0.0`), wenn sich etwas für Clients oder Betreiber unverträglich
  ändert: Schnittstelle, Antworten, Voraussetzungen der Installation (etwa Tomcat oder Java),
  eine nötige Umstellung der Datenbank;
* **Nebenversion** (`2.1.0`) für verträgliche Erweiterungen;
* **Korrektur** (`2.0.1`) für Fehlerbehebungen ohne Änderung der Schnittstelle.

Die Version steht in `gradle.properties` (`vermdbVersion`). Zwischen zwei Versionen trägt
der Stand die nächste mit `-SNAPSHOT`; Release-Kandidaten zur Erprobung heißen
`<Version>-rc.<n>`. Eine Version wird mit `v<Version>` getaggt; die Änderungen gegenüber der
vorigen Version stehen beim zugehörigen
[Release auf GitHub](https://github.com/okstra-apps/vermessungsdatenbank/releases). Der Bau trägt Version und
Commit in das Manifest des WAR und in die Stückliste ein; der Dienst nennt beide beim Start
im Protokoll.

`2.0.0` ist die erste Open-Source-Version. Davor war die Vermessungsdatenbank seit Jahren
ohne Versionsnummern im Einsatz.

## Commits

Eine Änderung je Commit, mit einer kurzen Überschrift auf Deutsch und darunter, was und warum
geändert wurde; bei Verhaltensänderungen auch, wie sie geprüft wurden.
