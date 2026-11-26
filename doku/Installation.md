<!--
SPDX-FileCopyrightText: 2026 interactive instruments GmbH
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Installation

Die Vermessungsdatenbank ist eine Webanwendung für Apache Tomcat 11. Sie braucht eine
PostgreSQL-Datenbank mit PostGIS und die native OKSTRA-Klassenbibliothek (OKLABI) der
Bundesanstalt für Straßenwesen. Es gibt vier Wege:

| Weg | Für wen | Abschnitt |
|---|---|---|
| **Container mit Datenbank** (`Container/compose.yaml`) | neue Installationen ohne vorhandene Datenbank; Standardweg | [1.1](#11-mit-datenbank-im-container) |
| **Container gegen eine vorhandene Datenbank** (`Container/compose.extern.yaml`) | Datenbank wird außerhalb betrieben, etwa auf einem zentralen Datenbankserver | [1.2](#12-gegen-eine-vorhandene-datenbank) |
| Tomcat unter **Linux** | eigener Server ohne Docker | [3](#3-linux) (Details: [2](#2-eigener-tomcat)) |
| Tomcat unter **Windows** als Dienst | eigener Server unter Windows | [4](#4-windows) (Details: [2](#2-eigener-tomcat)) |

Danach: [Inbetriebnahme und Prüfung](#5-inbetriebnahme-und-prüfung), [Update](#6-update).
Die Werte der Konfiguration beschreibt `doku/Konfiguration.md`, die Datenbank
`Datenbank/README.md`.

## 1. Container

Voraussetzung ist Docker mit Compose (ab 2.24) auf einem Rechner mit amd64-Prozessor; auf
arm64 laufen die Images unter Emulation, weil es die native OKLABI nur für amd64 gibt. Beide
Varianten nutzen dasselbe Image; der erste Start baut es (die Webanwendung mit Gradle, die
Dateien des OKLABI-Release von okstra.de).

Die Konfiguration liegt für beide in einem Konfigurationsverzeichnis: `.env` mit den
Einstellungen, `gitter/` mit eigenen Gitterdateien, `fbliste/` mit eigenen
Fachbedeutungslisten. Ohne Angabe ist das `Container/` im Repository. Für den Betrieb
empfiehlt sich ein Verzeichnis außerhalb, etwa `/etc/vermdb`; dessen `.env` nennt es
(`VERMDB_KONFIG=/etc/vermdb`), und jeder Aufruf von `docker compose` bekommt
`--env-file /etc/vermdb/.env` (`Container/README.md`, „Konfigurationsverzeichnis“). Die
Befehle unten zeigen den einfachen Fall ohne eigenes Verzeichnis.

In die `.env` gehören mindestens Behörde und Fachbedeutungsliste:

    VERMDB_BEHOERDE=<Kurzname der Behörde>
    VERMDB_BEHOERDE_NAME=<Name der Behörde>
    VERMDB_FBLISTE=<Bundesland> <Version der Fachbedeutungsliste>

Für die Landesstraßenbaubehörde Sachsen-Anhalt etwa:

    VERMDB_BEHOERDE=LSBB
    VERMDB_BEHOERDE_NAME=Landesstraßenbaubehörde Sachsen-Anhalt
    VERMDB_FBLISTE=Sachsen-Anhalt 3.5
    VERMDB_GITTER_IMAGE=NTv2LSBB_LSA

Das Image bringt die Gitterdateien für Sachsen-Anhalt (`NTv2LSBB_LSA`) und Hessen
(`HeTa2010`) mit; `VERMDB_GITTER_IMAGE` schaltet das des eigenen Landes ein (Abschnitt 2.4,
`Container/README.md`, „Gitterdateien“). Weitere Gitterdateien gehören nach `gitter/`
(Einträge in `gitter/gitter.cfg`), eigene Fachbedeutungslisten, die im OKLABI-Release fehlen,
nach `fbliste/`; der Dienst ergänzt sie beim Start und lädt sie in die Datenbank, ein neues
Image ist dafür nicht nötig. Die Schnittstelle liegt danach unter
`http://127.0.0.1:8099/Okstra2Db/Okstra2DbServlet`. Alle Einstellungen, Volumes, Sicherung
und Abruf der Protokolle beschreibt `Container/README.md`.

### 1.1 Mit Datenbank im Container

Compose startet PostgreSQL mit PostGIS als eigenen Dienst (`datenbank`), dazu die
Vermessungsdatenbank. Im Repository-Ordner:

    docker compose -f Container/compose.yaml up -d

Der erste Start erzeugt die Kennwörter, legt Rolle und Datenbank an, richtet sie mit den
Skripten aus `Datenbank/` ein und lädt die Fachbedeutungslisten des OKLABI-Release. Uploads,
Rückgaben und Protokolle liegen im Datenverzeichnis auf dem Rechner (ohne Angabe `daten/` im
Konfigurationsverzeichnis, für den Betrieb besser etwa `VERMDB_DATEN=/var/lib/vermdb`), die
Datenbank und die Kennwörter in Volumes des Compose-Projekts `vermdb`; ihre Sicherung ist
Teil des Betriebs (`doku/Betrieb.md`). Weiter mit [Abschnitt 5](#5-inbetriebnahme-und-prüfung).

### 1.2 Gegen eine vorhandene Datenbank

Compose startet nur die Vermessungsdatenbank; die Datenbank wird außerhalb betrieben und
gesichert. Sie muss vorher eingerichtet sein:

1. PostgreSQL mit PostGIS, eine leere Datenbank und eine Rolle für die Anwendung; dann die
   Skripte aus `Datenbank/`, wie in `Datenbank/README.md` beschrieben (die Variante 1.1
   erledigt genau das selbst). Die Fachbedeutungslisten lädt der Dienst beim Start.
2. Die Datenbank muss aus dem Container erreichbar sein. Läuft sie auf demselben Rechner,
   ist sie aus dem Container nicht unter `localhost` zu erreichen:
   * Docker Desktop (macOS, Windows): über den Namen `host.docker.internal`;
   * Linux mit Docker Engine: Den Namen gibt es dort nicht. Stattdessen eine Adresse des
     Rechners, auf der PostgreSQL lauscht und Verbindungen aus dem Docker-Netz zulässt
     (`listen_addresses`, `pg_hba.conf`), etwa die Adresse der Docker-Bridge.
3. Verbindung in der `.env`, zusätzlich zu Behörde und Fachbedeutungsliste:

       VERMDB_DB_URL=jdbc:postgresql://<host>:<port>/<datenbank>
       VERMDB_DB_BENUTZER=<rolle>
       VERMDB_DB_PASSWORT=<kennwort>

   `<port>` ist der Port von PostgreSQL, meist `5432`.

   Statt `VERMDB_DB_PASSWORT` geht `VERMDB_DB_PASSWORT_DATEI` mit dem Pfad einer
   eingehängten Datei (`Container/README.md`).
4. Das Datenverzeichnis (`VERMDB_DATEN`, ohne Angabe `daten/` im Konfigurationsverzeichnis)
   muss dem Benutzer `vermdb` (UID 10001) gehören. Unter Linux mit Docker Engine vorher
   anlegen, etwa `sudo install -d -o 10001 -g 10001 /var/lib/vermdb`; unter Docker Desktop
   ist das nicht nötig. Sonst bricht der Start mit einer Meldung ab.

Dann im Repository-Ordner:

    docker compose -f Container/compose.extern.yaml up -d

Wer `VERMDB_PORT` ändert, setzt auch `VERMDB_SERVER_PRAEFIX` passend, sonst zeigen die
Links der Auftragsliste auf Port 8099. Das Compose-Projekt heißt hier `vermdb-extern`;
Befehle wie `logs` oder `ps` gehen mit `-f Container/compose.extern.yaml`. Die
Fachbedeutungslisten (die des Release und eigene aus `fbliste/`) lädt der Dienst beim Start
in die externe Datenbank, wie in 1.1. Weiter mit
[Abschnitt 5](#5-inbetriebnahme-und-prüfung).

## 2. Eigener Tomcat

Für eine Installation ohne Container. Die Schritte stehen vollständig und der Reihe nach in
[Abschnitt 3](#3-linux) (Linux) bzw. [Abschnitt 4](#4-windows) (Windows); für eine erste
Installation genügt einer der beiden. Dieser Abschnitt erklärt, was die Schritte tun, und
beschreibt Abweichungen: andere Pfade, eine Datenbank auf einem anderen Server,
Gitterdateien.

### 2.1 Voraussetzungen

| Bestandteil | Version |
|---|---|
| Java | 25 (64 Bit), etwa OpenJDK aus der Distribution oder Eclipse Temurin; zum Bauen ein JDK |
| Apache Tomcat | 11.0, mindestens 11.0.27 (Sicherheitskorrekturen), geprüft mit 11.0.29 |
| PostgreSQL mit PostGIS | geprüft mit PostgreSQL 14/PostGIS 3.5 und PostgreSQL 18/PostGIS 3.6 |
| Native OKLABI | 1.6.5.0 aus dem Release https://www.okstra.de/oklabi/Release-20260629 |
| Python 3 | für die Einrichtung (OKLABI, Datenbank), nur Standardbibliothek |

Aus dem OKLABI-Release werden gebraucht: die beiden Java-Archive (lädt der Bau, 2.2), die
Ressourcen `Ressourcen.zip` mit Schemata, Fachbedeutungslisten und Gitterdateien sowie die
native Bibliothek der Plattform (lädt `werkzeuge/oklabi.py`, 2.4). Alle landen in
`oklabi-release/` im Repository. **Ist okstra.de nicht erreichbar,** sind sie anderswo zu
beschaffen und unverändert, nicht ausgepackt, dort abzulegen; welche Dateien, unter welchem
Namen und mit welcher Prüfsumme, steht in `oklabi-release/README.md`, „Ohne Zugang zu
okstra.de“. Bau und Skript nehmen sie dann von dort.

### 2.2 Bau

Gebaut wird mit einem JDK 25. Die Umgebungsvariable `JAVA_HOME` muss auf dieses JDK zeigen
(unter Windows etwa `C:\Program Files\Java\jdk-25…`); ist sie nicht gesetzt, nimmt der Bau
`java` aus dem `PATH`, fehlt auch das, bricht er mit „JAVA_HOME is not set“ ab. Die beiden
Java-Archive des Release lädt er beim ersten Mal von okstra.de nach `oklabi-release/`:

    ./gradlew build                 # unter Windows: gradlew.bat build

Gebraucht werden später daraus:

* `DatenbankLader/build/libs/Okstra2Db.war`: die Webanwendung;
* `DatenbankLader/build/tomcat-lib/`: `OKLABI.jar` und der PostgreSQL-Treiber für das
  `lib`-Verzeichnis des Tomcat.

Wohin sie kommen, steht an den entsprechenden Stellen weiter unten.

### 2.3 Datenbank

`Datenbank/einrichten.py` richtet die Datenbank in einem Aufruf ein. Es meldet sich mit `psql`
als Superuser an (unter Windows findet es `psql.exe` im Installationsverzeichnis von
PostgreSQL) und

1. prüft, ob PostGIS installiert ist;
2. legt die Rolle `vermdb` an, mit der sich die Anwendung anmeldet (das Kennwort fragt es ab
   oder erzeugt eines), und die Datenbank `vermdb`;
3. richtet sie mit `Datenbank/einrichten.sql` ein: Erweiterung PostGIS, Schemata `oks_meta`
   und `oks_2022`, Rechte der Rolle;
4. lädt die Fachbedeutungslisten aus `Ressourcen.zip` (Vorgabe: die in `oklabi-release/`)
   und aus weiteren Verzeichnissen oder Zip-Archiven, die als Argumente folgen;
5. nennt am Ende Adresse und Benutzer für die Datenquellen des Tomcat (2.5).

Vorhandenes bleibt: Ein zweiter Aufruf lässt Rolle und eingerichtete Datenbank unverändert und
lädt nur die Fachbedeutungslisten neu, etwa nach einem neuen OKLABI-Release. Optionen zeigt
`einrichten.py --help`: andere Namen (`--datenbank`, `--rolle`), ein anderer Server
(`--host`, `--port`, `--superuser`), das OKSTRA-Schema passend zum Init-Parameter
`okstraVersionDatabase` (`--okstra-schema`), das Lagesystem (`--srid`, Vorgabe 25832) und
`--ohne-beispiele` (keine Beispielwerte für Gebiete und FB-Ausschlüsse). Die einzelnen
Schritte mit `psql` beschreibt `Datenbank/README.md`.

### 2.4 OKLABI

`werkzeuge/oklabi.py <Verzeichnis>` nimmt die native Bibliothek der Plattform und die
Ressourcen aus `oklabi-release/` (fehlen sie, lädt es sie von okstra.de), prüft ihre
Prüfsummen und packt sie aus:

* `<Verzeichnis>/lib`: die native Bibliothek (unter Windows die DLLs aus
  `VS2022-Lib64-1-6-5-0.zip`, unter Linux die Bibliotheken aus `ubuntu-2404-1-6-5-0.tbz`;
  für eine andere Distribution das passende Paket des Release mit `--nativ-archiv <Datei>`);
* `<Verzeichnis>/Ressourcen`: die Ressourcen mit `res/config.cfg`.

Am Ende nennt es die Werte für den Tomcat:

* das Verzeichnis `lib` für die Java-Option `-Djava.library.path` (dort sucht der Loader der
  OKLABI), unter Windows auch für den `PATH` des Dienstes, unter Linux für
  `LD_LIBRARY_PATH` (dort sucht das Betriebssystem die Bibliotheken, von denen die OKLABI
  abhängt);
* `OKLABI_HOME`: das Ressourcenverzeichnis; darüber findet die OKLABI Schemata,
  Fachbedeutungslisten und Gitterdateien;
* `PROJ_LIB`: dessen Unterverzeichnis `gdal_data`; darüber findet die Bibliothek PROJ ihre
  `proj.db`. Fehlt die Variable, scheitert jede Koordinatentransformation; die Daten kommen
  dann unverändert im Lagesystem der Datenbank zurück, nur das Protokoll meldet
  „Cannot find proj.db“.

**`proj.db`.** Die Bibliothek PROJ 9.8.1 der OKLABI 1.6.5.0 (Linux und Windows) braucht eine
`proj.db` im Layout 1.6. Die `Ressourcen.zip` des Release bringt sie seit dem 03.11.2026 mit;
die bis dahin ausgelieferte enthielt eine zu alte (Layout 1.2), mit der jede
Koordinatentransformation scheitert, ohne dass es auffällt. Ressourcen aus dieser Zeit, etwa
einer bestehenden Installation, bringt `werkzeuge/projdb.py <Ressourcenverzeichnis>` auf den
Stand: Es lädt die `proj.db` aus PROJ 9.8.1 (Debian-Paket aus snapshot.debian.org, mit
Prüfsumme) und setzt sie ein; die alte bleibt als `proj.db.orig` erhalten. Eine passende
`proj.db` lässt es unverändert.

**Neues OKLABI-Release.** Das alte Verzeichnis umbenennen und `oklabi.py` erneut aufrufen;
danach Gitterdateien und `res/user.cfg` übernehmen (unten).

**Gitterdateien (optional).** Für Transformationen zwischen amtlichen Lagesystemen mit
regionalen Verzerrungen und zwischen Höhensystemen kann die OKLABI Gitterdateien nutzen:
für die Lage im Format NTv2 (`.gsb`), für Höhen im Format GTX (`.gtx`). Nötig sind sie nicht:
Ohne Gitter rechnet die OKLABI die Lage mit allgemeinen Parametern, um einige Zentimeter
anders als mit Gitter (unten); Höhen zwischen DHHN92 und DHHN2016 bleiben ohne Gitter
unverändert (Unterschied im Millimeter- bis Zentimeterbereich). Ein Gitter lohnt sich nur
für ein Paar von Systemen, zwischen denen die Installation tatsächlich umrechnet, also für
die Lage- und Höhensysteme der Lieferungen und der Datenbank (`okstraLagesystem`,
`okstraHoehensystem`) und für die beim Entladen angeforderten Systeme. Welche das sind, hängt vom Land und von den Clients ab.

Für ihre Nutzung gelten die Bedingungen ihrer Herausgeber. Zwei Gitter unter der Datenlizenz
Deutschland – Namensnennung 2.0 liegen im Repository (`Container/gitter-image/`, mit
Quellvermerken) und im Container-Image; im Container schaltet `VERMDB_GITTER_IMAGE` sie ein
(Abschnitt 1). Andere beschafft, wer sie braucht, selbst (im Container: `gitter/` des
Konfigurationsverzeichnisses, `Container/README.md`). Ohne Container liegen sie im
Ressourcenverzeichnis unter `ntv2/` und werden in eine Benutzerkonfiguration der OKLABI
eingetragen (unten). Das OKLABI-Release 1.6.5.0 enthält noch einige Gitter samt Einträgen in
`res/config.cfg`, künftige Releases ggf. nicht.

Gitter, die für Installationen der Vermessungsdatenbank in Frage kommen:

| Gitter | Wann nötig | Systeme | Bezug und Bedingungen | Eintrag |
|---|---|---|---|---|
| NTv2LSBB_LSA | nur in Sachsen-Anhalt, wenn Daten in 42/83 Gauß-Krüger 4 vorkommen | 42/83, Gauß-Krüger 4 (2398) ↔ ETRS89/UTM 32 (25832) und UTM 33 (25833) | Landesstraßenbaubehörde Sachsen-Anhalt, auf deren Webseite (Vermessung, „Transformation“) und in `Container/gitter-image/`; Datenlizenz Deutschland – Namensnennung 2.0 | `Gitterdatei=2398@25832@NTv2LSBB_LSA.gsb`, `Gitterdatei=2398@25833@NTv2LSBB_LSA.gsb` |
| HeTa2010 | nur in Hessen, wenn Daten in DHDN/Gauß-Krüger 3 vorkommen | DHDN, Gauß-Krüger 3 (31467) ↔ ETRS89/UTM 32 (25832) | Hessische Verwaltung für Bodenmanagement und Geoinformation (HVBG), kostenfrei über „Geodaten online“, und in `Container/gitter-image/`; Datenlizenz Deutschland – Namensnennung 2.0 | `Gitterdatei=31467@25832@HeTa2010.gsb` |
| HOETRA2016 | bundesweit, wenn Lieferungen in DHHN92 vorkommen und die Datenbank in DHHN2016 ist (oder umgekehrt) | DHHN92 ↔ DHHN2016 | Bezirksregierung Köln, Geobasis NRW: Endnutzer-Paket der HOETRA2016.DLL (`geobasis_gfp_trans_hoetra2016_endnutzer.zip`), kostenfrei; Nutzungsbedingungen für Geodätische Fachprogramme (interne Nutzung) | `Gitterdatei=urn:adv:crs:DE_DHHN92_NH@urn:adv:crs:DE_DHHN2016_NH@Hoetra2016.gtx` |

Ein Lagegitter gehört nur in die Installation des Landes, für das es gilt: Punkte außerhalb
lässt die OKLABI unverändert (unten), statt sie ohne Gitter umzurechnen. Das Gitter
NTv2LSBB_LSA reicht von 50,9° bis 53,1° N und 10,5° bis 13,33° O, HeTa2010 von 49,33° bis
51,87° N und 7,5° bis 10,5° O. Weitere Länder geben eigene NTv2-Gitter heraus (etwa
Baden-Württemberg, Sachsen, Thüringen, Saarland); sie werden ebenso eingetragen.

HOETRA2016 kommt als NTv2-Datei `Hoetra2016.gsb`, mit der Höhendifferenz im Feld der
Breitenverschiebung. PROJ nimmt Höhengitter aber nur als `.gtx` an; mit der `.gsb` im
Eintrag bleiben die Höhen unverändert, und das Protokoll meldet nur „vgridshift (File not
found or invalid)“. Umwandeln mit `werkzeuge/gsb2gtx.py` (nur Standardbibliothek; gleiches
Ergebnis wie `gdal_translate -b 1 -of GTX`):

    python3 werkzeuge/gsb2gtx.py geobasis_gfp_trans_hoetra2016_endnutzer.zip <Ressourcen>/ntv2/Hoetra2016.gtx

Ein Eintrag hat die Form `Gitterdatei=<Quellsystem>@<Zielsystem>@<Datei>`, die Systeme als
EPSG-Code oder als URN, die Datei relativ zum Verzeichnis aus `OrdnerGitterdateien`
(Vorgabe `~/ntv2`, `~` steht für das Ressourcenverzeichnis). Er gilt auch in der
Gegenrichtung. Nach einer Änderung den Tomcat neu starten.

Fehlt für ein Paar von Systemen ein Eintrag oder die eingetragene Datei, rechnet die OKLABI
ohne Gitter, ohne Meldung und im Lagesystem um einige Zentimeter anders. Ob eine
Gitterdatei verwendet wurde, steht im Protokoll des Dienstes: „Transformiere
Koordinatenreferenzsystem von […] nach […] Gitterdatei: […]“. Punkte außerhalb des Gitters
bleiben unverändert („Fehler bei Transformation des Punktes … Die Lage bleibt unverändert“).

Die Einträge gehören in eine eigene Datei, etwa `res/user.cfg`, die in `res/config.cfg`
mit der dort schon vorbereiteten Zeile `BenutzerKonfiguration=~/res/user.cfg` eingeschaltet
wird. Ein neues Release der Ressourcen ersetzt `res/config.cfg`; danach ist nur diese Zeile
wieder einzuschalten und `ntv2/` zu übernehmen. Mit dem Release 1.6.5.0 stehen die
mitgelieferten Einträge in `res/config.cfg`; eigene Einträge in `res/user.cfg` kommen
hinzu.

### 2.5 Tomcat

**Bibliotheken.** Die Dateien aus `DatenbankLader/build/tomcat-lib/` (`OKLABI.jar`,
`postgresql-….jar`) in das Verzeichnis `lib` des Tomcat kopieren.

**Datenquellen.** Die Webanwendung verweist über `META-INF/context.xml` auf zwei globale
Datenquellen, die in der `conf/server.xml` des Tomcat stehen (Abschnitt
`GlobalNamingResources`). Beide zeigen auf dieselbe Datenbank; die Namen sind historisch
und müssen so lauten:

    <Resource name="de.ii.datasource.okstra-db" auth="Container" type="javax.sql.DataSource"
              driverClassName="org.postgresql.Driver"
              url="jdbc:postgresql://localhost:5432/vermdb"
              username="vermdb" password="<Kennwort>" maxIdle="1"/>
    <Resource name="de.ii.datasource.svc-vermdb" auth="Container" type="javax.sql.DataSource"
              driverClassName="org.postgresql.Driver"
              url="jdbc:postgresql://localhost:5432/vermdb"
              username="vermdb" password="<Kennwort>" maxIdle="1"/>

Die `server.xml` enthält damit ein Kennwort und sollte nur für das Konto des Tomcat lesbar
sein. Statt fester Werte gehen Platzhalter für Umgebungsvariablen, etwa
`password="${VERMDB_DB_PASSWORT}"` (siehe `doku/Konfiguration.md`, Abschnitt 6).

**Webanwendung.** Das WAR nicht nach `webapps` kopieren, sondern als Verzeichnis
`webapps/Okstra2Db` auspacken: Die `web.xml` der Installation liegt darin unter `WEB-INF/`,
und ein WAR daneben würde der Tomcat neu auspacken und dabei die `web.xml` überschreiben.

**`web.xml`.** Die Vorlage im WAR (`WEB-INF/web.xml`, im Repository
`DatenbankLader/src/main/webapp/WEB-INF/web.xml`) enthält alle Parameter mit
Beispielwerten. Anzupassen sind mindestens:

| Parameter | Inhalt |
|---|---|
| `okstraUploadpath`, `okstraOklabilog`, `okstraErrorexportpath`, `okstraReturnpath` | die vier Datenverzeichnisse; sie müssen vorhanden und für das Konto des Tomcat beschreibbar sein |
| `okstraAuthority`, `okstraAuthName` | Kurzname und Name der Behörde |
| `okstraFbliste` | Bundesland und Version der Fachbedeutungsliste, etwa `Sachsen-Anhalt 3.5` |
| `okstraServerPrefix` | Adresse, unter der Protokolle und Rückgaben abrufbar sind (Abschnitt 2.6) |
| `okstraVersionDatabase` | OKSTRA-Version des Schemas, `2.022` für `oks_2022` |

Die Zeichensätze (`request-character-encoding`, `response-character-encoding`) bleiben auf
ISO-8859-1. Altdaten und Planung sind optional und in der Vorlage auskommentiert. Lokale
Dateien statt hochgeladener (Parameter `uploadfile`) nimmt der Dienst nur an, wenn
`okstraTransferpath` ein Übergabeverzeichnis nennt.

**OkWS.** Nur wenn ein OKSTRA-Webdienst angebunden wird: `okstraWSbackend` auf `1`, und
`okws/beispiel-wfs.xml` aus dem Repository an den Ort aus `okstraWSresponse` kopieren
(Vorlage: `C:/Vermessungsdatenbank/okws/beispiel-wfs.xml`). Der Dienst liefert die Datei
als Antwort auf das Lebenszeichen des OkWS (`okws/README.md`).

**Java-Optionen und Umgebung.** Der Tomcat braucht:

* `-Djava.library.path=<Verzeichnis der nativen OKLABI>` (2.4);
* `--enable-native-access=ALL-UNNAMED`; die Startskripte des Tomcat 11 und `service.bat`
  setzen sie selbst;
* genug Speicher für große Lieferungen, etwa `-Xmx3072m`;
* die Umgebungsvariablen `OKLABI_HOME` und `PROJ_LIB`, dazu das Verzeichnis der nativen
  OKLABI im `PATH` (Windows) bzw. `LD_LIBRARY_PATH` (Linux) (2.4).

Gesetzt werden sie unter Linux in `<Tomcat>/bin/setenv.sh` (Abschnitt 3, Schritt 11), beim
Windows-Dienst mit der Einrichtung des Dienstes (Abschnitt 4, Schritt 9). Ein Tomcat, der
mit `startup.bat` statt als Dienst läuft, liest sie aus `<Tomcat>\bin\setenv.bat`.

### 2.6 Protokolle und Rückgaben

Der Dienst schreibt Protokolle, Datenfehler und die Ergebnisse des Entladens in die
Verzeichnisse aus `okstraOklabilog`, `okstraErrorexportpath` und `okstraReturnpath`. Die
Clients brauchen diese Dateien über HTTP: Die Auftragsliste verlinkt Protokolle und
Datenfehler, und beim asynchronen Entladen holt der Client das Ergebnis ab. Die Adresse dafür
steht in `okstraServerPrefix`, darunter `log/`, `datenfehler/` und `download/`. Fehlt sie,
gehen Prüfen, Laden und synchrones Entladen trotzdem, aber die Links der Auftragsliste führen
ins Leere, und asynchrones Entladen kommt beim Client nicht an.

Am einfachsten liefert der Tomcat die drei Verzeichnisse selbst aus, mit einer zweiten
Webanwendung `/vermdb` ohne eigenen Code. Sie besteht aus zwei Dateien; der Container nutzt
dieselben, daher liegen die Vorlagen unter `Container/conf/`:

* **`vermdb.xml`**, die Kontextdatei, gehört nach `<Tomcat>/conf/Catalina/localhost/`. Sie
  ordnet den Pfaden `log/`, `datenfehler/` und `download/` die drei Verzeichnisse zu (je
  ein Attribut `base`) und nennt in `docBase` ein sonst leeres Verzeichnis der Anwendung,
  etwa `<Tomcat>/vermdb`. Die Vorlage enthält die Pfade des Containers; der Inhalt mit den
  Pfaden der Beispiele steht in Abschnitt 3 bzw. 4.
* **`vermdb-web.xml`** gehört als `WEB-INF/web.xml` in das Verzeichnis aus `docBase`. Sie
  sorgt dafür, dass der Tomcat jede Datei nur ausliefert und nichts davon ausführt, und
  bleibt unverändert.

`okstraServerPrefix` ist dann `http://<Rechner>:<Port>/vermdb/`, mit dem Namen, unter dem
die Clients den Rechner erreichen. Ausgeliefert wird nur lesend und ohne
Verzeichnislisten; das Upload-Verzeichnis bleibt unerreichbar. Ein vorhandener Webserver
kann die drei Verzeichnisse stattdessen ebenso unter einem Präfix mit `log/`,
`datenfehler/` und `download/` ausliefern.

Wer die Schnittstelle erreicht, kann Aufträge einstellen und entladen und erreicht auch
diese Dateien. Ist der Dienst über den Rechner hinaus erreichbar, gehört ein Webserver mit
Anmeldung davor.

## 3. Linux

Beispiel für Ubuntu 24.04 mit den Pfaden `/opt/tomcat`, `/opt/oklabi` und
`/var/lib/vermdb`; die Befehle als root im Verzeichnis des Repositorys. Was die Schritte
tun, erklärt Abschnitt 2.

**1. Pakete.** Java, Python und die Bibliotheken, die die native OKLABI aus der Distribution
erwartet; dazu PostgreSQL mit PostGIS, wenn die Datenbank auf diesem Rechner läuft:

    apt-get install --no-install-recommends openjdk-25-jdk-headless python3 curl \
      libdeflate0 libtiff6 libjpeg-turbo8 libxml2 libicu74 libcurl4t64 libexpat1
    apt-get install postgresql postgresql-16-postgis-3

**2. Bauen** (2.2):

    ./gradlew build

Bricht der Bau ab, weil okstra.de nicht erreichbar ist: `oklabi-release/README.md`, „Ohne
Zugang zu okstra.de“.

**3. OKLABI** einrichten (2.4):

    python3 werkzeuge/oklabi.py /opt/oklabi

Ohne Zugang zu okstra.de vorher `Ressourcen.zip` und `ubuntu-2404-1-6-5-0.tbz` nach
`oklabi-release/` legen (wie beim Bau).

**4. Datenbank** einrichten (2.3), als Superuser `postgres` über den Socket, also ohne dessen
Kennwort; das Repository muss dafür für den Benutzer `postgres` lesbar sein:

    sudo -u postgres python3 Datenbank/einrichten.py oklabi-release/Ressourcen.zip --host /var/run/postgresql

Das Skript fragt ein Kennwort für die Rolle `vermdb` (leer lassen: es erzeugt eines und zeigt
es an); es wird in Schritt 8 gebraucht.

**5. Tomcat** aus dem Archiv von Apache, unter einem eigenen Konto:

    useradd --system --home-dir /opt/tomcat --shell /usr/sbin/nologin tomcat
    curl -fsSLO https://archive.apache.org/dist/tomcat/tomcat-11/v11.0.29/bin/apache-tomcat-11.0.29.tar.gz
    curl -fsSL https://archive.apache.org/dist/tomcat/tomcat-11/v11.0.29/bin/apache-tomcat-11.0.29.tar.gz.sha512 | sha512sum -c -
    mkdir /opt/tomcat
    tar -xzf apache-tomcat-11.0.29.tar.gz -C /opt/tomcat --strip-components=1
    rm -rf /opt/tomcat/webapps/*

**6. Webanwendung, Bibliotheken und Datenverzeichnisse** (2.5):

    python3 -m zipfile -e DatenbankLader/build/libs/Okstra2Db.war /opt/tomcat/webapps/Okstra2Db
    cp DatenbankLader/build/tomcat-lib/*.jar /opt/tomcat/lib/
    mkdir -p /var/lib/vermdb/upload /var/lib/vermdb/download /var/lib/vermdb/log /var/lib/vermdb/datenfehler

**7. `web.xml`** in `/opt/tomcat/webapps/Okstra2Db/WEB-INF/web.xml` anpassen (Bedeutung in
2.5):

| Parameter | Wert |
|---|---|
| `okstraUploadpath` | `/var/lib/vermdb/upload` |
| `okstraOklabilog` | `/var/lib/vermdb/log` |
| `okstraErrorexportpath` | `/var/lib/vermdb/datenfehler` |
| `okstraReturnpath` | `/var/lib/vermdb/download` |
| `okstraAuthority`, `okstraAuthName` | Kurzname und Name der Behörde |
| `okstraFbliste` | Bundesland und Version der Fachbedeutungsliste, etwa `Sachsen-Anhalt 3.5` |
| `okstraServerPrefix` | `http://<Rechner>:8080/vermdb/` |

**8. Datenquellen:** in `/opt/tomcat/conf/server.xml` innerhalb von
`<GlobalNamingResources>` die beiden `<Resource>`-Einträge aus 2.5 einfügen, mit Adresse,
Benutzer und Kennwort aus Schritt 4.

**9. Protokolle und Rückgaben** über den Kontext `/vermdb` (2.6):

    mkdir -p /opt/tomcat/vermdb/WEB-INF /opt/tomcat/conf/Catalina/localhost
    cp Container/conf/vermdb-web.xml /opt/tomcat/vermdb/WEB-INF/web.xml

und `/opt/tomcat/conf/Catalina/localhost/vermdb.xml` mit diesem Inhalt anlegen:

    <Context docBase="/opt/tomcat/vermdb">
      <Resources cachingAllowed="false">
        <PreResources className="org.apache.catalina.webresources.DirResourceSet"
                      base="/var/lib/vermdb/log" webAppMount="/log" readOnly="true"/>
        <PreResources className="org.apache.catalina.webresources.DirResourceSet"
                      base="/var/lib/vermdb/datenfehler" webAppMount="/datenfehler" readOnly="true"/>
        <PreResources className="org.apache.catalina.webresources.DirResourceSet"
                      base="/var/lib/vermdb/download" webAppMount="/download" readOnly="true"/>
      </Resources>
    </Context>

**10. Rechte:**

    chown -R tomcat:tomcat /opt/tomcat /var/lib/vermdb
    chmod 600 /opt/tomcat/conf/server.xml

**11. Java-Optionen und Umgebung** (2.4, 2.5) in `/opt/tomcat/bin/setenv.sh`:

    JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
    export OKLABI_HOME=/opt/oklabi/Ressourcen
    export PROJ_LIB=/opt/oklabi/Ressourcen/gdal_data
    export LD_LIBRARY_PATH=/opt/oklabi/lib
    CATALINA_OPTS="$CATALINA_OPTS -Xmx3072m -Djava.library.path=/opt/oklabi/lib"

**12. Dienst** mit systemd, `/etc/systemd/system/vermdb.service`:

    [Unit]
    Description=Vermessungsdatenbank (Tomcat 11)
    After=network.target postgresql.service

    [Service]
    User=tomcat
    Group=tomcat
    ExecStart=/opt/tomcat/bin/catalina.sh run
    SuccessExitStatus=143

    [Install]
    WantedBy=multi-user.target

Starten mit `systemctl daemon-reload && systemctl enable --now vermdb`. Das Protokoll des
Tomcat zeigt `journalctl -u vermdb`, das der Vermessungsdatenbank liegt in
`/var/lib/vermdb/log`. Weiter mit [Abschnitt 5](#5-inbetriebnahme-und-prüfung).

## 4. Windows

Beispiel mit allem unter `C:\Vermessungsdatenbank`: das Repository in `Quellen`, der Tomcat in
`Tomcat`, die OKLABI in `OKLABI`, die Daten in `Daten`. Die Vorlage der `web.xml` nennt
diese Pfade schon. Was die Schritte tun, erklärt Abschnitt 2.

Alle Befehle in einer **Eingabeaufforderung (`cmd.exe`) als Administrator**, nicht in der
PowerShell, und im Verzeichnis des Repositorys:

    cd /d C:\Vermessungsdatenbank\Quellen

**1. Programme installieren.**

* **JDK 25**, etwa Eclipse Temurin (MSI für Windows x64 von adoptium.net). Im Installer bei
  „Set JAVA_HOME variable“ „Will be installed on local hard drive“ wählen; ohne diese
  Variable finden weder der Bau noch die Einrichtung des Dienstes das JDK. Nachträglich geht
  das mit `setx /M JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-25…-hotspot"`
  (Verzeichnis anpassen). Danach eine neue Eingabeaufforderung öffnen; `echo %JAVA_HOME%`
  muss das Verzeichnis zeigen.
* **Python 3** von python.org; der Installer bringt den Aufruf `py` mit.
* **PostgreSQL** mit dem Installer von EDB (postgresql.org, „Download“, „Windows“). Das
  Kennwort für den Superuser `postgres`, nach dem der Installer fragt, wird in Schritt 4
  gebraucht; den Port 5432 lassen. Am Ende den **Stack Builder** starten, die
  PostgreSQL-Installation auswählen und unter „Spatial Extensions“ PostGIS für diese
  PostgreSQL-Version installieren. Eine Beispiel-Datenbank anzulegen ist nicht nötig.
* **Tomcat 11** von tomcat.apache.org als Archiv „64-bit Windows zip“
  (`apache-tomcat-11.0.<n>-windows-x64.zip`, mindestens 11.0.27), nicht den „Windows
  Service Installer“. Auspacken und den Ordner `apache-tomcat-11.0.<n>` als
  `C:\Vermessungsdatenbank\Tomcat` ablegen. Den Dienst richtet Schritt 9 ein.

**2. Bauen** (2.2):

    gradlew.bat build

Bricht der Bau ab, weil okstra.de nicht erreichbar ist: `oklabi-release\README.md`, „Ohne
Zugang zu okstra.de“; die Dateien gehören nach `C:\Vermessungsdatenbank\Quellen\oklabi-release`.

**3. OKLABI** einrichten (2.4):

    py werkzeuge\oklabi.py C:\Vermessungsdatenbank\OKLABI

Ohne Zugang zu okstra.de vorher `Ressourcen.zip` und `VS2022-Lib64-1-6-5-0.zip` nach
`oklabi-release` legen (wie beim Bau). Danach liegen die DLLs in `C:\Vermessungsdatenbank\OKLABI\lib` und die Ressourcen in
`C:\Vermessungsdatenbank\OKLABI\Ressourcen`.

**4. Datenbank** einrichten (2.3):

    py Datenbank\einrichten.py

Das Skript fragt nach dem Kennwort von `postgres` aus Schritt 1 und nach einem neuen Kennwort
für die Rolle `vermdb` (leer lassen: es erzeugt eines und zeigt es an). Am Ende nennt es
Adresse, Benutzer und Kennwort für Schritt 7. Meldet es, dass PostGIS fehlt oder sich nicht
laden lässt, PostGIS mit dem Stack Builder für genau diese PostgreSQL-Version (neu)
installieren.

**5. Webanwendung, Bibliotheken und Datenverzeichnisse** (2.5). Die Beispielanwendungen des
Tomcat in `webapps` werden nicht gebraucht:

    rmdir /s /q C:\Vermessungsdatenbank\Tomcat\webapps
    mkdir C:\Vermessungsdatenbank\Tomcat\webapps\Okstra2Db
    tar -xf DatenbankLader\build\libs\Okstra2Db.war -C C:\Vermessungsdatenbank\Tomcat\webapps\Okstra2Db
    copy DatenbankLader\build\tomcat-lib\*.jar C:\Vermessungsdatenbank\Tomcat\lib\
    mkdir C:\Vermessungsdatenbank\Daten\upload C:\Vermessungsdatenbank\Daten\download C:\Vermessungsdatenbank\Daten\log C:\Vermessungsdatenbank\Daten\datenfehler

**6. `web.xml`** in `C:\Vermessungsdatenbank\Tomcat\webapps\Okstra2Db\WEB-INF\web.xml` mit
einem Texteditor anpassen (Bedeutung in 2.5). Die Datenverzeichnisse stimmen schon; Pfade in
dieser Datei stehen mit `/`:

| Parameter | Wert |
|---|---|
| `okstraAuthority`, `okstraAuthName` | Kurzname und Name der Behörde |
| `okstraFbliste` | Bundesland und Version der Fachbedeutungsliste, etwa `Sachsen-Anhalt 3.5` |
| `okstraServerPrefix` | `http://<Rechner>:8080/vermdb/`, für einen Test auf demselben Rechner `http://localhost:8080/vermdb/` |

**7. Datenquellen:** in `C:\Vermessungsdatenbank\Tomcat\conf\server.xml` innerhalb von
`<GlobalNamingResources>` die beiden `<Resource>`-Einträge aus 2.5 einfügen, mit Adresse,
Benutzer und Kennwort aus Schritt 4.

**8. Protokolle und Rückgaben** über den Kontext `/vermdb` (2.6):

    mkdir C:\Vermessungsdatenbank\Tomcat\vermdb\WEB-INF C:\Vermessungsdatenbank\Tomcat\conf\Catalina\localhost
    copy Container\conf\vermdb-web.xml C:\Vermessungsdatenbank\Tomcat\vermdb\WEB-INF\web.xml

und `C:\Vermessungsdatenbank\Tomcat\conf\Catalina\localhost\vermdb.xml` mit diesem Inhalt
anlegen:

    <Context docBase="C:/Vermessungsdatenbank/Tomcat/vermdb">
      <Resources cachingAllowed="false">
        <PreResources className="org.apache.catalina.webresources.DirResourceSet"
                      base="C:/Vermessungsdatenbank/Daten/log" webAppMount="/log" readOnly="true"/>
        <PreResources className="org.apache.catalina.webresources.DirResourceSet"
                      base="C:/Vermessungsdatenbank/Daten/datenfehler" webAppMount="/datenfehler" readOnly="true"/>
        <PreResources className="org.apache.catalina.webresources.DirResourceSet"
                      base="C:/Vermessungsdatenbank/Daten/download" webAppMount="/download" readOnly="true"/>
      </Resources>
    </Context>

**9. Dienst** einrichten und starten. `service.bat` legt den Dienst `Tomcat11` an und
übernimmt Speicher, Startart und Java-Optionen (2.5) aus den Variablen davor; die
Umgebung (2.4) setzt danach `tomcat11.exe`:

    cd /d C:\Vermessungsdatenbank\Tomcat\bin
    set JvmMx=3072
    set SERVICE_STARTUP_MODE=auto
    set "JvmArgs=-Djava.library.path=C:\Vermessungsdatenbank\OKLABI\lib"
    service.bat install
    tomcat11.exe //US//Tomcat11 ++Environment OKLABI_HOME=C:\Vermessungsdatenbank\OKLABI\Ressourcen
    tomcat11.exe //US//Tomcat11 ++Environment PROJ_LIB=C:\Vermessungsdatenbank\OKLABI\Ressourcen\gdal_data
    tomcat11.exe //US//Tomcat11 ++Environment "PATH='C:\Vermessungsdatenbank\OKLABI\lib;%SystemRoot%\System32;%SystemRoot%'"
    net start Tomcat11

Meldet `service.bat`, dass `JAVA_HOME` fehlt, siehe Schritt 1. Das Verzeichnis der DLLs steht
an zwei Stellen: im `java.library.path` (dort sucht der Loader der OKLABI) und im `PATH` des
Dienstes (dort sucht Windows die DLLs, von denen diese abhängen, etwa `OklabiTiff…`,
`libcrypto-3-x64`, `libssl-3-x64`). Der `PATH` gilt nur für den Dienst, nicht systemweit,
damit andere Programme die OpenSSL- und GDAL-DLLs der OKLABI nicht sehen. Fehlt das
Verzeichnis im `PATH`, bricht der Dienst beim Start mit einem Hinweis darauf ab. Liegt in
`<Tomcat>\bin` oder in `C:\Windows\System32` eine andere `libcrypto-3-x64.dll`, nimmt Windows
diese vor der im `PATH`.

Die Einstellungen zeigt und ändert `tomcat11w.exe //ES//Tomcat11` (Reiter „Java“ und
„Startup“); die Umgebung steht in der Registrierung unter
`HKLM\SOFTWARE\WOW6432Node\Apache Software Foundation\Procrun 2.0\Tomcat11\Parameters`, Wert
`Environment`. Bei `tomcat11.exe //US//` ergänzt `++` die vorhandenen Werte, `--` ersetzt sie;
den Befehl also nur einmal ausführen oder mit `--` neu setzen.

Der Dienst läuft unter dem Konto „Lokaler Dienst“ (`NT Authority\LocalService`), weil
`service.bat` kein Konto vorgibt. Dieses Konto muss das Verzeichnis der OKLABI (`lib` und
`Ressourcen`) lesen und in die Datenverzeichnisse schreiben können; unterhalb von
`C:\Vermessungsdatenbank` erben die Verzeichnisse dafür die Rechte von „Benutzer“
(Lesen) und „Authentifizierte Benutzer“ (Ändern). Zeigt der Dienst trotz richtigem `PATH`
die Meldung, die nativen Bibliotheken der OKLABI könnten nicht geladen werden, fehlen
meist diese Rechte (`icacls C:\Vermessungsdatenbank\OKLABI\lib` nennt dann nur SYSTEM,
Administratoren und den Besitzer). Abhilfe in einer Eingabeaufforderung als Administrator:

    icacls C:\Vermessungsdatenbank\OKLABI\lib /reset /T /Q
    icacls C:\Vermessungsdatenbank\OKLABI\Ressourcen /reset /T /Q
    net stop Tomcat11 && net start Tomcat11

Das Protokoll des Tomcat liegt in `C:\Vermessungsdatenbank\Tomcat\logs`, das der
Vermessungsdatenbank in `C:\Vermessungsdatenbank\Daten\log`. Weiter mit
[Abschnitt 5](#5-inbetriebnahme-und-prüfung).

## 5. Inbetriebnahme und Prüfung

**Start-Protokoll.** Im Protokoll der Vermessungsdatenbank (`Svc_<Zeit>.log` im Verzeichnis
aus `okstraOklabilog`, im Container `docker compose -f Container/compose.yaml logs vermdb`
bzw. mit `compose.extern.yaml`) stehen beim Start:

* `Vermessungsdatenbank <Version> (Commit …)` mit der installierten Version;
* `OKSTRA-Datenquelle: jdbc/OKSTRA-DB` und `Service-VermDB-Datenquelle: jdbc/SVC-VERMDB`,
  ohne „nicht verfügbar“ (die Meldungen zu Altdaten und Planung sind ohne diese Datenbanken
  normal);
* `OKLABI-Version vom 19. Juni 2026 (1.6.5.0, …)`;
* `Vorgabe für FBListe: …` mit der eingestellten Liste;
* bei OkWS `Backend für OkWS wird eingerichtet` ohne Folgefehler und ohne „Default-Response
  für OkWS ist nicht verfügbar“.

Kann der Dienst die native OKLABI nicht laden oder ist ein Parameter falsch, bricht er den
Start mit einer Meldung ab, die den Grund nennt; sie steht im Protokoll des Tomcat und
erscheint beim ersten Aufruf als Fehlerseite.

**Anfragen.** Mit `<URL>` für `http://<Rechner>:<Port>/Okstra2Db/Okstra2DbServlet`, im
Beispiel des eigenen Tomcat `http://localhost:8080/Okstra2Db/Okstra2DbServlet`. `curl` gehört
auch zu Windows; in der Eingabeaufforderung steht am Ende einer fortgesetzten Zeile `^`
statt `\`, und die Befehle laufen im Verzeichnis des Repositorys:

    curl "<URL>?request=GetThemes"
    curl "<URL>?request=Queue"

Die erste Anfrage liefert die Themen als JSON, die zweite die Auftragsliste (anfangs leer).

**Prüfauftrag** mit `testdaten/beispiel.xml` aus dem Repository (Gauß-Krüger 4 im Lagestatus 150
mit DHHN2016, Fachbedeutungsliste Sachsen-Anhalt 3.5), ohne die Datenbank zu ändern:

    curl -F nurPruefen=1 -F SID=probe1 -F PRJNM=Probe -F username=probe \
      -F f1=XML -F f2=beispiel.xml -F f4=11.11.2026 -F f7=3.5 -F f8=Sachsen-Anhalt \
      -F f20=laden -F f22=2398 -F f23=170 \
      -F "datei=@testdaten/beispiel.xml;type=text/xml" "<URL>"

Antwort `{ "success": true, "auftragsnummer": <Nr> }`. Nach wenigen Sekunden steht der
Auftrag in der Liste der Prüfaufträge (`<URL>?request=QueueCheck`) als „geprüft“, mit Link
auf das Prüfprotokoll. Der Link muss sich öffnen lassen (Abschnitt 2.6). `f22`/`f23` nennen
Lage- und Höhensystem der Datei (2398 = 42/83 Gauß-Krüger 4 im Lagestatus 150, 170 = DHHN2016),
`f7`/`f8` Version und Bundesland der Fachbedeutungsliste; die Liste Sachsen-Anhalt 3.5 muss dafür in
`oks_meta.fbliste` geladen sein (`Datenbank/README.md`).

**Laden und Koordinatentransformation.** Dieselbe Anfrage mit `pruefeDatei=1` statt
`nurPruefen=1` (und `SID=probe2`) lädt die Datei; die Liste der Ladeaufträge
(`<URL>?request=Queue`) zeigt den Auftrag danach als „geladen“. Dann in UTM 33 entladen:

    curl -o probe.xml "<URL>?request=Read&SID=probe3&Auftrag=<Nr>&f22=25833"

In `probe.xml` muss `srsName="urn:adv:crs:ETRS89_UTM33"` stehen, und die Ausdehnung muss
bei `274749.876 5742459.809` beginnen (ohne `f22`, also in UTM 32 der Datenbank, bei
`688550.314 5740957.345`; mit dem Gitter NTv2LSBB_LSA gerechnet, ohne Gitter weichen die
Werte um einige Zentimeter ab). Kommen die Daten unverändert in UTM 32 zurück, fehlt
`PROJ_LIB` oder die `proj.db` ist zu alt (Abschnitt 2.4); das Protokoll des Dienstes
(`Svc_….log`) meldet dann „Cannot find proj.db“ bzw. dass die Transformation nicht
unterstützt wird. Den Probeauftrag danach löschen:

    curl "<URL>?request=DeleteJob&auftrag=<Nr>&datasource=Bestand"

**Mit Hurl.** Dieselben Schritte und weitere Anfragen, mit Prüfung der Antworten, führen die
Beispiele in `doku/beispiele/` aus (`doku/beispiele/README.md`); für den Container (sollte 
die Basis-URL von `<URL>` nicht `http://127.0.0.1:8099` sein, dann ist 
`doku/beispiele/container.env` entsprechend anzupassen):

    hurl --test --jobs 1 --variables-file doku/beispiele/container.env --file-root . doku/beispiele/*.hurl

In der Windows-Eingabeaufforderung erweitert die Shell `*` nicht, das übernimmt dort `--glob`:

    hurl --test --jobs 1 --variables-file doku/beispiele/container.env --file-root . --glob "doku/beispiele/*.hurl"

## 6. Update

Die Datenbank bleibt bei einem Update unverändert, sofern die Hinweise zur Version nichts
anderes sagen.

**Container:**

    git pull
    docker compose -f Container/compose.yaml up -d --build           # 1.1
    docker compose -f Container/compose.extern.yaml up -d --build    # 1.2

**Eigener Tomcat:**

1. Neu bauen (Abschnitt 2.2).
2. Tomcat bzw. Dienst stoppen.
3. `webapps/Okstra2Db/WEB-INF/web.xml` sichern, das Verzeichnis `webapps/Okstra2Db` löschen
   und das neue WAR dorthin auspacken, die gesicherte `web.xml` zurückkopieren.
   Unterschiede zur neuen Vorlage prüfen (neue oder entfallene Parameter nennen die
   Hinweise zur Version).
4. `OKLABI.jar` und den PostgreSQL-Treiber in `lib` durch die neuen aus
   `DatenbankLader/build/tomcat-lib/` ersetzen; einen älteren Treiber entfernen.
5. Starten und prüfen wie in Abschnitt 5.

Für einen Rückfall vorher das Verzeichnis `webapps/Okstra2Db` und die beiden Jars in `lib`
sichern.

**Neues OKLABI-Release:** das Verzeichnis der OKLABI umbenennen und mit
`werkzeuge/oklabi.py` neu einrichten (Adresse und Prüfsummen des Release stehen im Skript
und sind dafür anzupassen); die
Gitterdateien in `ntv2/` und `res/user.cfg` übernehmen und `BenutzerKonfiguration` wieder
einschalten (Abschnitt 2.4), `OKLABI.jar` aus dem neuen Bau. Neue Fachbedeutungslisten lädt
ein erneuter Aufruf von `Datenbank/einrichten.py` (Abschnitt 2.3). Bei einem neuen Lagesystem
in der OKLABI ist `Datenbank/31-ekoordrefsys.sql` neu zu erzeugen.
