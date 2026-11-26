<!--
SPDX-FileCopyrightText: 2026 interactive instruments GmbH
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Container

Die Standardinstallation der Vermessungsdatenbank: Docker Compose mit zwei Diensten.

* `vermdb`: Apache Tomcat 11 mit Java 25, die native OKLABI 1.6.5.0 für Linux (amd64) und
  die Webanwendung, eingerichtet wie eine Installation ohne Container (`OKLABI.jar` und
  PostgreSQL-Treiber im `lib`-Verzeichnis des Tomcat).
* `datenbank`: PostgreSQL 18 mit PostGIS 3.6.

## Starten

Voraussetzung ist Docker mit Compose (ab 2.24). Beim Bau braucht Docker Zugang zu Docker
Hub, Maven Central, dem Gradle Plugin Portal und okstra.de. Im
Repository:

    docker compose -f Container/compose.yaml up -d

Beim ersten Start geschieht Folgendes:

1. Das Image wird gebaut (einige Minuten). Gradle baut die Webanwendung, die Dateien des
   OKLABI-Release werden geladen und geprüft.
2. Der Dienst `kennwoerter` erzeugt zufällige Kennwörter für die Datenbank.
3. PostgreSQL legt seine Datenbank an.
4. Der Dienst `einrichtung` legt die Rolle `vermdb` und die Datenbank `vermdb` an und
   richtet sie mit den Skripten aus `Datenbank/` ein.
5. Die Vermessungsdatenbank startet. Vorher stellt sie die Fachbedeutungslisten zusammen
   (die des OKLABI-Release und eigene, „Fachbedeutungslisten“) und lädt sie in die
   Datenbank.

Bei jedem weiteren Start prüft `einrichtung` nur, ob etwas fehlt, und der Dienst lädt die
Fachbedeutungslisten nur, wenn sie sich geändert haben.

## Konfigurationsverzeichnis

Die Konfiguration der Installation liegt in einem Verzeichnis, getrennt vom Image:

| Inhalt | Zweck |
|---|---|
| `.env` | Einstellungen („Einstellungen“) |
| `gitter/` | eigene Gitterdateien mit `gitter.cfg`, falls die des Image nicht reichen („Gitterdateien“) |
| `fbliste/` | eigene Fachbedeutungslisten („Fachbedeutungslisten“) |

Ohne weitere Angabe ist das `Container/` im Repository; die Dateien darin sind von Git
ausgenommen. Für den Betrieb empfiehlt sich ein Verzeichnis außerhalb des Repositorys, etwa
`/etc/vermdb`, damit ein neuer Checkout oder `git pull` die Konfiguration nicht berührt. Die
`.env` dort nennt das Verzeichnis selbst, und Compose bekommt sie mit `--env-file`:

    VERMDB_KONFIG=/etc/vermdb

    docker compose --env-file /etc/vermdb/.env -f Container/compose.yaml up -d

`--env-file` gehört dann zu jedem Aufruf von `docker compose` (auch `ps`, `logs`, `restart`),
etwa über einen Alias. Einzeln abweichende Orte: `VERMDB_GITTER`, `VERMDB_FBLISTEN`.

Die Schnittstelle liegt unter `http://127.0.0.1:8099/Okstra2Db/Okstra2DbServlet`, etwa
`…?request=Queue` für die Auftragsliste. Der Port ist nur vom Rechner selbst erreichbar;
`VERMDB_PORT` ändert Adresse und Port (siehe unten).

Mit `docker compose -f Container/compose.yaml ps` lässt sich der Zustand prüfen, mit
`… logs vermdb` das Protokoll des Tomcat. Beide Dienste haben einen Healthcheck.

Eine Prüfung mit den Beispieldaten (prüfen, laden, entladen, löschen) beschreibt
`doku/Installation.md`, Abschnitt 5; mit [Hurl](https://hurl.dev) im Wurzelverzeichnis des
Repositorys:

    hurl --test --jobs 1 --variables-file doku/beispiele/container.env --file-root . doku/beispiele/*.hurl

## Einstellungen

Einstellungen stehen in der `.env` des Konfigurationsverzeichnisses (ohne Angabe
`Container/.env`). Die Datei ist optional und gehört nicht ins Repository. Behörde und
FB-Liste sollten gesetzt werden:

    VERMDB_BEHOERDE=<Kurzname der Behörde>
    VERMDB_BEHOERDE_NAME=<Name der Behörde>
    VERMDB_FBLISTE=<Bundesland> <Version der Fachbedeutungsliste>

Für die Landesstraßenbaubehörde Sachsen-Anhalt etwa:

    VERMDB_BEHOERDE=LSBB
    VERMDB_BEHOERDE_NAME=Landesstraßenbaubehörde Sachsen-Anhalt
    VERMDB_FBLISTE=Sachsen-Anhalt 3.5
    VERMDB_GITTER_IMAGE=NTv2LSBB_LSA

Alle Variablen aus `.env` gehen in den Container. Die `web.xml` des Containers enthält
für die folgenden Parameter Platzhalter der Form `${VARIABLE:-Vorgabe}`, die der Tomcat
beim Start ersetzt; ohne Variable gilt die Vorgabe. Eine Variable nicht leer setzen, denn
dann gilt der leere Wert und nicht die Vorgabe.

| Variable | Parameter | Vorgabe |
|---|---|---|
| `VERMDB_BEHOERDE` | `okstraAuthority` | `Behörde (anpassen)` |
| `VERMDB_BEHOERDE_NAME` | `okstraAuthName` | `Name der Behörde (anpassen)` |
| `VERMDB_DATENBANK_NAME` | `okstraDbName` | `Vermessungsdatenbank` |
| `VERMDB_FBLISTE` | `okstraFbliste`, etwa `Sachsen-Anhalt 3.5` | `Bundesland 0.0` (keine Vorgabe) |
| `VERMDB_FBLISTE_PRUEFUNG` | `okstraFblisteCheck` | `einfach:ohneSymbolbedeutung:ohneTextbedeutung` |
| `VERMDB_SERVER_PRAEFIX` | `okstraServerPrefix`, siehe „Protokolle und Rückgaben“ | `http://localhost:8099/vermdb/` |
| `VERMDB_OKSTRA_VERSION` | `okstraVersionDatabase`; legt auch das OKSTRA-Schema an (`2.022` → `oks_2022`) | `2.022` |
| `VERMDB_LAGESYSTEM` | `okstraLagesystem` | `UTM32` |
| `VERMDB_HOEHENSYSTEM` | `okstraHoehensystem` | `DHHN2016` |
| `VERMDB_OKWS` | `okstraWSbackend` (`1` an, `0` aus) | `1` |
| `VERMDB_OKWS_PORT` | `okstraWSport` | `24641` |

Weitere Variablen für Compose und die Einrichtung:

| Variable | Bedeutung | Vorgabe |
|---|---|---|
| `VERMDB_PORT` | Adresse und Port des Tomcat auf dem Rechner, etwa `8099` für alle Adressen | `127.0.0.1:8099` |
| `VERMDB_TAG` | Name (Tag) des Image | `latest` |
| `VERMDB_KONFIG` | Konfigurationsverzeichnis (siehe dort); nur zusammen mit `--env-file` | `Container` |
| `VERMDB_GITTER_IMAGE` | Gitter des Image, die eingeschaltet werden (durch Leerzeichen oder Komma getrennt), etwa `NTv2LSBB_LSA` in Sachsen-Anhalt oder `HeTa2010` in Hessen (siehe „Gitterdateien“) | keines |
| `VERMDB_GITTER` | Verzeichnis mit eigenen Gitterdateien und `gitter.cfg` (siehe „Gitterdateien“) | `<Konfigurationsverzeichnis>/gitter` |
| `VERMDB_FBLISTEN` | Verzeichnis mit eigenen Fachbedeutungslisten (siehe „Fachbedeutungslisten“) | `<Konfigurationsverzeichnis>/fbliste` |
| `VERMDB_DATEN` | Datenverzeichnis auf dem Rechner (siehe „Daten und Kennwörter“); `daten` für ein Volume | `<Konfigurationsverzeichnis>/daten` |
| `VERMDB_DB_NAME`, `VERMDB_DB_BENUTZER` | Datenbank und Rolle der Anwendung | `vermdb` |
| `VERMDB_DB_SRID` | Lagesystem der Datenbank beim Einrichten (Variable `srid` von `einrichten.sql`) | `25832` |
| `VERMDB_DB_BEISPIELE` | Beispielwerte für Gebiete und FB-Ausschlüsse anlegen (`on`/`off`) | `off` |

`VERMDB_OKSTRA_VERSION`, `VERMDB_DB_*` und `VERMDB_DB_SRID` wirken beim Einrichten nur auf
eine neue Datenbank. Weitere Parameter der `web.xml` lassen sich auf dieselbe Weise über
eine eigene `conf/web.xml` setzen (Namen nach demselben Schema: `VERMDB_` und ein
deutscher Name). Bleibt ein Platzhalter ohne Variable und ohne Vorgabe stehen, startet der
Dienst nicht und nennt den Parameter.

Nach einer Änderung an `.env` (mit `--env-file`, falls das Konfigurationsverzeichnis
außerhalb liegt):

    docker compose -f Container/compose.yaml up -d

## Daten und Kennwörter

Die Daten liegen an drei Stellen:

| Ort | Inhalt |
|---|---|
| Datenverzeichnis auf dem Rechner, ohne Angabe `daten/` im Konfigurationsverzeichnis (`VERMDB_DATEN`) | Uploads, Rückgaben, Protokolle, Datenfehler; im Container `/var/home/Daten/Okstra2DB` |
| Volume `datenbank` des Projekts `vermdb` | PostgreSQL |
| Volume `kennwoerter` des Projekts `vermdb` | Kennwörter des Superusers `postgres` und der Rolle der Anwendung |

Das Datenverzeichnis ist ein gewöhnliches Verzeichnis: Protokolle und Rückgaben lassen sich
dort direkt lesen und mitsichern. Für den Betrieb empfiehlt sich ein eigener Ort, etwa
`VERMDB_DATEN=/var/lib/vermdb`. Es muss dem Benutzer `vermdb` (UID 10001) gehören; das
erledigt der Dienst `einrichtung` bei jedem Start für die Verzeichnisse, die der Dienst
braucht. `VERMDB_DATEN=daten` nimmt stattdessen ein Volume `daten` des Projekts.

`docker compose down` lässt die Volumes stehen, `docker compose down -v` löscht sie samt
Datenbank; das Datenverzeichnis auf dem Rechner bleibt in beiden Fällen. Eine Sicherung der Datenbank:

    docker compose -f Container/compose.yaml exec datenbank pg_dump -U postgres -Fc vermdb > vermdb.dump

Die Kennwörter stehen nur im Volume `kennwoerter`; die Dienste lesen sie als Datei
(`POSTGRES_PASSWORD_FILE`, `VERMDB_DB_PASSWORT_DATEI`). Sie erscheinen weder in `.env`
noch in `docker inspect` noch im Image. Eigene Kennwörter vor dem ersten Start:

    docker compose -f Container/compose.yaml run --rm --no-deps kennwoerter \
      sh -c 'printf %s "<Kennwort>" > /kennwoerter/anwendung'

Ebenso `/kennwoerter/superuser`. Das Kennwort der Anwendung lässt sich auch später so
ändern: `docker compose -f Container/compose.yaml up -d` überträgt es mit `einrichtung` in
die Datenbank, `… restart vermdb` lässt die Anwendung es neu lesen. Für den Superuser gilt
das nicht, denn PostgreSQL liest sein Kennwort nur beim Anlegen.

Die Vermessungsdatenbank läuft als Benutzer `vermdb` (UID 10001), nicht als root. Ist das
Datenverzeichnis für ihn nicht beschreibbar, bricht der Start mit einer Meldung ab.

## Protokolle und Rückgaben

Die Auftragsliste verlinkt Protokolle und Datenfehler unter `okstraServerPrefix`; beim
asynchronen Entladen (`holeDatei`) holt der Client das Ergebnis dort ab. Diese Dateien
liefert der Tomcat selbst aus, unter dem Kontext `/vermdb`:

| Adresse | Verzeichnis |
|---|---|
| `/vermdb/log/` | Protokolle (`okstraOklabilog`) |
| `/vermdb/datenfehler/` | Datenfehler (`okstraErrorexportpath`) |
| `/vermdb/download/` | Rückgaben, je Session-ID ein Verzeichnis (`okstraReturnpath`) |

Ausgeliefert wird nur lesend und ohne Verzeichnislisten; Uploads sind nicht erreichbar.
`VERMDB_SERVER_PRAEFIX` muss die Adresse sein, unter der die Clients den Container
erreichen, mit `/vermdb/` am Ende. Wer die Schnittstelle erreicht, erreicht auch diese
Dateien, wie bisher bei einem eigenen Webserver. Soll der Dienst über den Rechner hinaus
erreichbar sein, gehört ein Webserver mit Anmeldung davor.

## Gitterdateien

Das Image bringt zwei Gitterdateien für Transformationen der Lage mit
(`Container/gitter-image/README.md`), beide unter der Datenlizenz Deutschland –
Namensnennung 2.0:

| Name | Systeme | Herausgeber |
|---|---|---|
| `NTv2LSBB_LSA` | 42/83 Gauß-Krüger 4 (2398) ↔ ETRS89/UTM 32 und 33 (25832, 25833) | Landesstraßenbaubehörde Sachsen-Anhalt |
| `HeTa2010` | DHDN Gauß-Krüger 3 (31467) ↔ ETRS89/UTM 32 (25832) | Hessische Verwaltung für Bodenmanagement und Geoinformation |

Eingeschaltet ist keines: Ein Gitter gilt nur für sein Land, und Punkte außerhalb lässt die
OKLABI unverändert, statt sie ohne Gitter umzurechnen. Eine Installation schaltet das Gitter
ihres Landes in der `.env` ein, etwa `VERMDB_GITTER_IMAGE=NTv2LSBB_LSA`; ein unbekannter Name
hält den Start mit einer Meldung auf. Das Protokoll des Containers nennt die eingeschalteten
Gitter und ihre Einträge („Gitter des Image: …“, „Gitter: Gitterdatei=…“).

Weitere Gitter (etwa HOETRA2016 für DHHN92 ↔ DHHN2016) liegen in `gitter/` des
Konfigurationsverzeichnisses (anderer Ort: `VERMDB_GITTER`), zusammen mit einer `gitter.cfg`
mit ihren Einträgen; Compose hängt das Verzeichnis nur lesend als `/opt/vermdb/gitter` ein.
Beim Start ergänzen sie die eingeschalteten Gitter des Image: Eine eigene Datei mit dem
Namen einer des Image ersetzt diese, ein eigener Eintrag für dasselbe Paar von Systemen
(gleich in welcher Richtung) den des Image. Bezug, Bedingungen und die Umwandlung von
HOETRA2016 nach `.gtx` stehen in `doku/Installation.md`, Abschnitt 2.4, ein Beispiel in
`gitter/README.md`. Nach einer Änderung genügt `docker compose -f Container/compose.yaml
restart vermdb`.

## Fachbedeutungslisten

Das Image enthält die Fachbedeutungslisten des OKLABI-Release. Eigene Listen (etwa eine
neue Version, die im Release fehlt) liegen als CSV im Format der OKLABI in `fbliste/` des
Konfigurationsverzeichnisses (anderer Ort: `VERMDB_FBLISTEN`) und werden nur lesend als
`/opt/vermdb/fbliste` eingehängt; Name und Format in `fbliste/README.md`. Beim Start stellt
der Dienst die Listen des Release und die eigenen zusammen (eine eigene Datei mit dem Namen
einer Liste des Release ersetzt diese) und gleicht sie mit `oks_meta.fbliste` und
`oks_meta.efbversion` ab: Hat sich etwas geändert, lädt er die Listen neu. Das Protokoll
nennt jede eigene Liste. Nach einer Änderung genügt
`docker compose -f Container/compose.yaml restart vermdb`; ein neues Image ist nicht nötig.
Soll eine eigene Liste die Vorgabe werden, `VERMDB_FBLISTE` setzen.

## Gegen eine vorhandene Datenbank

`compose.extern.yaml` startet nur die Vermessungsdatenbank, gegen eine PostgreSQL-
Datenbank mit PostGIS, die nach `Datenbank/README.md` eingerichtet ist. In der `.env` des
Konfigurationsverzeichnisses:

    VERMDB_DB_URL=jdbc:postgresql://<host>:<port>/<datenbank>
    VERMDB_DB_BENUTZER=<benutzer>
    VERMDB_DB_PASSWORT=<passwort>

`<port>` ist der Port von PostgreSQL, meist `5432`.

Statt `VERMDB_DB_PASSWORT` geht auch `VERMDB_DB_PASSWORT_DATEI` mit dem Pfad einer
eingehängten Datei. Einen Dienst `einrichtung` gibt es hier nicht: Unter Linux mit Docker
Engine das Datenverzeichnis vorher für den Benutzer `vermdb` anlegen, etwa
`sudo install -d -o 10001 -g 10001 /var/lib/vermdb` (unter Docker Desktop nicht nötig). Dann:

    docker compose -f Container/compose.extern.yaml up -d

## Bau

    docker compose -f Container/compose.yaml build

Das `Dockerfile` baut in Stufen:

* `oklabi` lädt die Dateien des OKLABI-Release von okstra.de und prüft sie gegen
  `bau/oklabi.sha256`. Liegen sie schon in `Container/oklabi/` oder `oklabi-release/`,
  nimmt der Bau diese (siehe `oklabi/README.md`). Aus den Fachbedeutungslisten erzeugt
  `Datenbank/fbliste.py` das SQL für die Einrichtung.
* `bau-docker` baut die Webanwendung mit Gradle (Variante `release`).
* Das Image übernimmt beides.

Die Basisimages sind mit Digest festgelegt. Mit dem Argument `BAU=lokal` übernimmt das
Image stattdessen die Ergebnisse von `./gradlew build` aus `DatenbankLader/build/`
(so baut die interne Testumgebung).

Die Stückliste der Webanwendung (CycloneDX) liegt im Image unter `/opt/vermdb/bom.json`,
die Lizenzen unter `/opt/vermdb/` (`NOTICE`, `LICENSES/` mit den Nutzungsbestimmungen der
BASt für OKLABI und `OkstraSqlUtil`). Eine Stückliste des ganzen Image erzeugt
`Container/sbom.sh` mit syft.

## Befehle des Image

`/usr/local/bin/vermdb.sh` mit einem Befehl:

* `start` (Vorgabe): stellt die Fachbedeutungslisten zusammen, lädt sie bei einer Änderung
  in die Datenbank (ein Fehler dabei ergibt nur eine Warnung) und startet Tomcat im
  Vordergrund. Verlangt `VERMDB_DB_URL`, `VERMDB_DB_BENUTZER` und `VERMDB_DB_PASSWORT` oder
  `VERMDB_DB_PASSWORT_DATEI`.
* `kennwoerter`: erzeugt fehlende Kennwörter in `/kennwoerter`, als root.
* `einrichten`: legt Rolle und Datenbank an und richtet die Datenbank ein, soweit das
  fehlt. Verlangt das Kennwort des Superusers
  (`VERMDB_DB_SUPERUSER_PASSWORT` oder `…_DATEI`); Server über `VERMDB_DB_HOST` und
  `VERMDB_DB_PORT` (Vorgabe `datenbank:5432`).
* `fblisten`: lädt die Fachbedeutungslisten (Release und eigene) neu in die Datenbank,
  auch wenn sie aktuell sind. Verbindung wie bei `start` (aus `VERMDB_DB_URL`). Aufruf:
  `docker compose -f Container/compose.yaml run --rm vermdb /usr/local/bin/vermdb.sh fblisten`
  (ebenso mit `compose.extern.yaml`).

## Konfiguration

* `conf/web.xml`: Werte der Anwendung, abgeleitet aus der Vorlage
  `DatenbankLader/src/main/webapp/WEB-INF/web.xml`. Altdaten und Planung sind nicht
  eingerichtet, ebenso kein Übergabeverzeichnis für `uploadfile`.
* `conf/server.xml`: Tomcat mit den beiden Datenquellen; Adresse und Zugang aus der
  Umgebung.
* `conf/vermdb.xml`, `conf/vermdb-web.xml`: Kontext `/vermdb` für Protokolle und Rückgaben.
Die Vorgabeantwort des OkWS-Backends (`okstraWSresponse`) übernimmt das Image aus
`okws/beispiel-wfs.xml` nach `/opt/okws/`.

Das OkWS-Backend lauscht im Container nur auf der Loopback-Adresse; ein OkWS müsste im
selben Container laufen.

## Update

    git pull
    docker compose -f Container/compose.yaml up -d --build

Bringt ein neues Image andere Fachbedeutungslisten mit, lädt der Dienst sie beim Start
selbst. Vor dem Update abwarten, bis kein Auftrag in Bearbeitung ist.
