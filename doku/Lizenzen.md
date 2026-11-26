<!--
SPDX-FileCopyrightText: 2026 interactive instruments GmbH
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Lizenzen und Drittkomponenten

Dieser Bericht beschreibt, unter welchen Lizenzen die Vermessungsdatenbank steht, welche
Komponenten Dritter das Repository enthält, ein Bau einbindet oder das Container-Image
mitbringt, und was bei einer Weitergabe zu beachten ist. Stand: Version 2.0.0, November
2026. Die genauen Fassungen der Bibliotheken nennen die Stücklisten (Abschnitt 6).

## 1. Die Vermessungsdatenbank

| | |
|---|---|
| Rechteinhaber | interactive instruments GmbH |
| Lizenz des Codes | GNU Affero General Public License, Version 3 oder später (`AGPL-3.0-or-later`) |
| Zusatzerlaubnis | nach § 7 AGPL für die Verbindung mit der OKSTRA-Klassenbibliothek (OKLABI) und `OkstraSqlUtil` der Bundesanstalt für Straßen- und Verkehrswesen (BASt), Wortlaut in `NOTICE` |
| Daten für die Tests, Gitterdateien | Datenlizenz Deutschland – Namensnennung – Version 2.0 (`DL-DE-BY-2.0`), Namensnennung je Datei (Abschnitte 2 und 5) |
| Lizenzangaben | nach der [REUSE-Spezifikation 3.3](https://reuse.software/spec-3.3/): Kopf in jeder Datei, sonst Eintrag in `REUSE.toml`; Lizenztexte in `LICENSES/` |

**Zusatzerlaubnis.** OKLABI und `OkstraSqlUtil` stehen unter eigenen Nutzungsbestimmungen
der BASt, die mit der AGPL nicht verträglich sind. Die Zusatzerlaubnis erlaubt, die
Vermessungsdatenbank mit ihnen zu verbinden und so weiterzugeben; ihr Quellcode gehört nicht
zum „vollständigen Quellcode“ nach der AGPL, für sie gelten ihre eigenen Bestimmungen.

**Keine Änderungen an OKLABI und `OkstraSqlUtil`.** Die Nutzungsbestimmungen der BASt
erlauben, beide zu ändern; eine veränderte Version darf aber nur als unabtrennbarer
Bestandteil einer Anwendung weitergegeben werden. Das Repository enthält deshalb keine
Korrekturen am Code der BASt, der Bau verwendet die unveränderten Release-Dateien mit
Prüfsumme. Wie mit bekannten Fehlern umzugehen ist, beschreibt `doku/Schnittstelle.md`
Abschnitt 8.

## 2. Inhalt des Repositorys

Das Repository enthält den eigenen Code (Webanwendung, Skripte für die Datenbank, Bau,
Container), die Dokumentation, Daten für die Tests und zwei Gitterdateien für
Koordinatentransformationen. Es enthält **nicht**: OKLABI und `OkstraSqlUtil`, die
Fachbedeutungslisten der Länder und weitere Gitterdateien.

| Komponente | Lizenz | Hinweis |
|---|---|---|
| eigener Code und Dokumentation | `AGPL-3.0-or-later` | |
| Gradle Wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) | `Apache-2.0` | unverändert aus der Gradle-Distribution |
| `testdaten/beispiel.xml` und daraus abgeleitete Dateien, `okws/beispiel-wfs.xml` | `DL-DE-BY-2.0` | Namensnennung: Landesstraßenbaubehörde Sachsen-Anhalt |
| `testdaten/musterlageplan*.xml` | `DL-DE-BY-2.0` | Namensnennung: © GeoBasis-DE / LVermGeo ST |
| Referenzdateien der Integrationstests | `DL-DE-BY-2.0` | Namensnennung wie die Testdaten, aus denen sie stammen (`REUSE.toml`) |
| `Container/gitter-image/NTv2LSBB_LSA.gsb` | `DL-DE-BY-2.0` | Namensnennung: Landesstraßenbaubehörde Sachsen-Anhalt (Abschnitt 5) |
| `Container/gitter-image/HeTa2010.gsb` | `DL-DE-BY-2.0` | Namensnennung: Hessische Verwaltung für Bodenmanagement und Geoinformation (Abschnitt 5) |

Alle Dateien tragen Lizenzangaben; `reuse lint` meldet keine Lücke.

## 3. Was ein Bau einbindet

Der Bau (`./gradlew build`) lädt Bibliotheken aus Maven Central und prüft sie gegen die
Prüfsummen in `gradle/verification-metadata.xml`. OKLABI und `OkstraSqlUtil` kommen aus dem
freigegebenen Release der BASt, das der Bau von okstra.de lädt und gegen Prüfsummen in
`gradle.properties` prüft; wer baut, bezieht sie damit selbst.

### 3.1 Im WAR

| Komponente | Lizenz | Bewertung |
|---|---|---|
| Apache Commons IO | `Apache-2.0` | verträglich mit der AGPL |
| Apache Log4j 2 (API, Core, 1.2-API) | `Apache-2.0` | verträglich |
| PostgreSQL JDBC Driver | `BSD-2-Clause` | verträglich |
| `OkstraSqlUtil` (Release der BASt, unverändert) | Nutzungsbestimmungen der BASt | Zusatzerlaubnis (Abschnitt 1) |
| Nutzungsbestimmungen der BASt (`META-INF/OKLABI-Nutzungsbestimmungen.txt`) | – | unverändert mitgegeben |

Lizenz- und NOTICE-Dateien der Bibliotheken liegen in ihren Jars unter `META-INF/`. Die
Servlet-API und den Verbindungspool stellt der Tomcat bereit (`Apache-2.0`).

### 3.2 Im lib-Verzeichnis des Tomcat

| Komponente | Lizenz | Bewertung |
|---|---|---|
| `OKLABI.jar` (Release der BASt, unverändert) | Nutzungsbestimmungen der BASt | Zusatzerlaubnis |
| PostgreSQL JDBC Driver | `BSD-2-Clause` | verträglich |

### 3.3 Nur für Bau und Tests

Diese Werkzeuge gehen nicht in WAR oder Image ein:

| Komponente | Lizenz |
|---|---|
| Gradle | `Apache-2.0` |
| CycloneDX-Plugin für Gradle (Stückliste des WAR) | `Apache-2.0` |
| JUnit | `EPL-2.0` |
| Testcontainers | `MIT` |
| Hurl (Image, Beispiele der Dokumentation in den Integrationstests) | `Apache-2.0` |
| syft (Image, Stückliste des Container-Image) | `Apache-2.0` |

## 4. Was das Container-Image mitbringt

Das Repository enthält das `Dockerfile`, kein fertiges Image. Wer das Image baut, lädt das
OKLABI-Release selbst von okstra.de und ist dessen Lizenznehmer. Das Image enthält:

| Komponente | Lizenz | Hinweis |
|---|---|---|
| Apache Tomcat 11 (Basisimage) | `Apache-2.0` | |
| Eclipse Temurin JDK 25 (Basisimage) | `GPL-2.0` mit Classpath Exception | Systembibliothek im Sinne der GPL |
| Ubuntu 24.04 mit Paketen (u. a. libtiff, libxml2, libicu, libjpeg-turbo, curl, postgresql-client, tzdata, python3) | jeweils freie Lizenzen | Lizenztexte im Image unter `/usr/share/doc` |
| WAR und Bibliotheken aus Abschnitt 3 | wie dort | |
| native OKLABI und Ressourcen (Release der BASt) | Nutzungsbestimmungen der BASt | Ressourcen mit OKSTRA-Schemata und 65 Fachbedeutungslisten der Länder; ohne die Gitterdateien des Release |
| Gitterdateien `NTv2LSBB_LSA.gsb` und `HeTa2010.gsb` | `DL-DE-BY-2.0` | aus dem Repository, unter `/opt/OKLABI_HOME/ntv2-image/` mit Quellvermerken (Abschnitt 5) |
| Lizenzen der Vermessungsdatenbank, `NOTICE`, Nutzungsbestimmungen der BASt | – | unter `/opt/vermdb/` |

Die native OKLABI bringt Bibliotheken mit eigenen Lizenzen mit; das Release enthält deren
Lizenztexte nicht:

| Bibliothek | Fassung | Lizenz |
|---|---|---|
| GDAL | 3.9.3 | `MIT` |
| PROJ | 9.8.1 | `MIT` |
| GEOS | 3.13.0 | `LGPL-2.1-or-later` |
| SpatiaLite | 5 | `MPL-1.1`, `GPL-2.0-or-later` oder `LGPL-2.1-or-later` (wählbar) |
| FreeXL | 1 | wie SpatiaLite |
| SQLite | 3.50 | gemeinfrei |
| Xerces-C | 3.3 | `Apache-2.0` |
| Boost (stacktrace, url) | 1.90 | `BSL-1.0` |
| zlib | 1.3 | `Zlib` |

**Weitergabe eines gebauten Image.** Wer ein gebautes Image an Dritte weitergibt, übernimmt
die Pflichten dieser Komponenten: Lizenztexte beifügen (für die Bibliotheken der nativen
OKLABI fehlen sie im Image), für GEOS und SpatiaLite/FreeXL den Quellcode der eingesetzten
Fassungen zugänglich machen oder anbieten. Die Nutzungsbestimmungen der BASt erlauben die
Weitergabe der unveränderten OKLABI „zusammen mit anderer vom Lizenznehmer hergestellter
Software“. Ob ein fertiges Image veröffentlicht wird, ist nicht Teil dieses Repositorys.

## 5. Gitterdateien

Repository und Image enthalten zwei Gitterdateien, unverändert und mit Quellvermerk
(`Container/gitter-image/README.md`, `NOTICE`); im Image sind sie erst eingeschaltet, wenn
eine Installation es mit `VERMDB_GITTER_IMAGE` verlangt (`Container/README.md`). Weitere
Gitter bezieht, wer sie braucht, beim Herausgeber und hält dessen Bedingungen ein;
Einrichtung in `doku/Installation.md`, Abschnitt 2.4.

| Gitter | Herausgeber | Bedingungen | Im Repository und Image |
|---|---|---|---|
| NTv2LSBB_LSA (Sachsen-Anhalt, 42/83 Gauß-Krüger 4 ↔ ETRS89/UTM 32 und 33) | Landesstraßenbaubehörde Sachsen-Anhalt | Datenlizenz Deutschland – Namensnennung 2.0 | ja |
| HeTa2010 (Hessen, DHDN Gauß-Krüger 3 ↔ ETRS89/UTM 32) | Hessische Verwaltung für Bodenmanagement und Geoinformation | Datenlizenz Deutschland – Namensnennung 2.0 | ja |
| HOETRA2016 (DHHN92 ↔ DHHN2016) | Bezirksregierung Köln, Geobasis NRW | Nutzungsbedingungen für Geodätische Fachprogramme: kostenfrei zur internen Nutzung, keine Weitergabe an Dritte | nein |

Wer ein gebautes Image weitergibt, gibt die beiden Gitter mit; die Namensnennung steht im
Image in `/opt/OKLABI_HOME/ntv2-image/README.md` und `/opt/vermdb/NOTICE`.

## 6. Stücklisten

* **WAR:** Der Bau erzeugt `DatenbankLader/build/reports/cyclonedx/bom.json` (CycloneDX) mit
  allen Bibliotheken im WAR, ihren Fassungen, Prüfsummen und Lizenzen, einschließlich
  `OkstraSqlUtil`; das Image enthält sie unter `/opt/vermdb/bom.json`.
* **Image:** `Container/sbom.sh` erzeugt eine Stückliste des ganzen Image mit syft. Die
  Bibliotheken der nativen OKLABI erkennt syft nicht; sie stehen in der Tabelle in
  Abschnitt 4.

## 7. Prüfung

* Lizenzangaben aller Dateien des Repositorys mit `reuse lint` (REUSE 3.3), ohne Befund.
* Bibliotheken aus Maven Central gegen Prüfsummen (`gradle/verification-metadata.xml`),
  Archive des OKLABI-Release und Nutzungsbestimmungen der BASt gegen Prüfsummen in
  `gradle.properties`, Basisimages über ihren Digest im `Dockerfile`.
* Verträglichkeit: Alle freien Komponenten in WAR und Image sind mit der AGPL 3.0
  verträglich; für OKLABI und `OkstraSqlUtil` gilt die Zusatzerlaubnis.
