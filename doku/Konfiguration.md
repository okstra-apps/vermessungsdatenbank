<!--
SPDX-FileCopyrightText: 2026 interactive instruments GmbH
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Konfiguration

Die Vermessungsdatenbank wird an fünf Stellen eingestellt:

| Ort | Inhalt | Abschnitt |
|---|---|---|
| `WEB-INF/web.xml` der Webanwendung | Parameter des Dienstes, Zeichensätze, Grenzen für Uploads | [1](#1-webxml), [2](#2-weitere-einstellungen-der-webxml) |
| `conf/server.xml` des Tomcat, `META-INF/context.xml` | Datenquellen | [3](#3-datenquellen) |
| Umgebung und Java-Optionen des Tomcat | native OKLABI, Platzhalter | [4](#4-umgebung-des-tomcat) |
| Ressourcen der OKLABI (`res/config.cfg`, Benutzerkonfiguration) | Gitterdateien und Voreinstellungen der OKLABI | [5](#5-ressourcen-der-oklabi) |
| Umgebungsvariablen | Werte der `web.xml` und der `server.xml` über Platzhalter | [6](#6-umgebungsvariablen) |

Die Installation beschreibt `doku/Installation.md`, den Container `Container/README.md`.
Abschnitt [7](#7-dateien-des-dienstes) nennt die Dateien, die der Dienst schreibt.

## 1. `web.xml`

Vorlage mit allen Parametern und Beispielwerten:
`DatenbankLader/src/main/webapp/WEB-INF/web.xml` (im WAR `WEB-INF/web.xml`), für den
Container `Container/conf/web.xml`.

**Servlet- und Kontextparameter.** Ein Teil der Parameter wird nur als Parameter des Servlets
gelesen (`<init-param>` im Element `<servlet>`), die übrigen zuerst dort und dann als
Parameter des Kontexts (`<context-param>`). Die Spalte „Ort“ der Tabellen nennt das:
**S** nur Servlet, **S/K** Servlet oder Kontext. Die Vorlage folgt dieser Aufteilung.

**Werte.** Schalter sind an mit `1`, `t` oder `true`, sonst aus. Verzeichnisse dürfen mit `~`
beginnen und gelten dann relativ zum Verzeichnis der entpackten Webanwendung. Ein Wert kann
Platzhalter für Umgebungsvariablen enthalten (Abschnitt 6). Die Vorgabe gilt, wenn der
Parameter fehlt.

### 1.1 Verzeichnisse

Die Verzeichnisse müssen vorhanden und für das Konto des Tomcat beschreibbar sein; der
Dienst legt sie nicht an, nur Unterverzeichnisse darin. Unter Linux setzt er beim Start die
Rechte jedes dieser Verzeichnisse rekursiv auf 755 (lesbar für alle, etwa für einen
Webserver), ebenso die von Protokollen, die er anlegt. Bei sehr vielen Dateien verlängert das
den Start.

| Parameter | Ort | Vorgabe | Bedeutung |
|---|---|---|---|
| `okstraUploadpath` | S/K | – (Pflicht) | Hochgeladene Dateien |
| `okstraOklabilog` | S/K | – (Pflicht) | Protokolle: das Protokoll des Dienstes und die Protokolle der Aufträge (Abschnitt 7) |
| `okstraErrorexportpath` | S/K | – | Fehlerhafte Fachobjekte eines Ladeauftrags als eigene Datei. Ohne den Parameter entfällt der Export, und die Auftragsliste bricht ab, sobald ein Auftrag einen Eintrag zu Datenfehlern hat; daher immer setzen |
| `okstraReturnpath` | S/K | – (Pflicht) | Rückgaben, je Session-ID (`SID`) ein Verzeichnis, darin `erledigt.txt` und beim asynchronen Entladen die Daten |
| `okstraTransferpath` | S/K | – | Übergabeverzeichnis für lokale Dateien: Mit dem Anfrageparameter `uploadfile` lädt der Dienst statt einer hochgeladenen Datei eine, die in diesem Verzeichnis oder darunter liegt; ein relativer Pfad gilt relativ zu ihm. Ohne den Parameter weist der Dienst `uploadfile` ab |

### 1.2 Behörde und Anzeige

| Parameter | Ort | Vorgabe | Bedeutung |
|---|---|---|---|
| `okstraAuthority` | S/K | `unbekannt` | Kurzname der Behörde; steht als Autor in den Metadaten entladener Dateien |
| `okstraAuthName` | S/K | `unbekannt` | Name der Behörde, Überschrift der Auftragsliste |
| `okstraDbName` | S/K | `Vermessungsdatenbank` | Name der Datenbank in der Auftragsliste |
| `okstraServerPrefix` | S/K | `/vermdb/` | Präfix der Links auf Protokolle (`<Präfix>log/…`) und Datenfehler (`<Präfix>datenfehler/…`) in der Auftragsliste; der Client holt dort auch die Ergebnisse des asynchronen Entladens ab (`<Präfix>download/<SID>/…`, siehe `doku/Installation.md`, Abschnitt 2.6). Mit `/` am Ende |

### 1.3 Fachbedeutungen

| Parameter | Ort | Vorgabe | Bedeutung |
|---|---|---|---|
| `okstraFbliste` | S/K | keine | Fachbedeutungsliste, die gilt, wenn eine Lieferung keine nennt: `<Bundesland> <Hauptversion>.<Unterversion>`, etwa `Sachsen-Anhalt 3.5`. Das Bundesland wie in der OKLABI geschrieben; die Liste muss in `oks_meta.fbliste` geladen sein. Ein ungültiger Wert wird beim Start gemeldet („Vorgabe für FBListe: <ungültig belegt> …“) und nicht verwendet |
| `okstraFblisteCheck` | S/K | Einstellung der OKLABI (`FbFehlerbehandlung` in `res/config.cfg`, im Release `einfach`) | Prüfung der Fachbedeutungen durch die OKLABI: `einfach` übergeht Objektarten, für die es keine Fachbedeutungen gibt (etwa Deckenbuch, Querneigung), `komplett` meldet sie; Zusätze `:ohneSymbolbedeutung` und `:ohneTextbedeutung` schalten die Prüfung für die Objektarten Symbol bzw. Beschriftung ab. Vorlage: `einfach:ohneSymbolbedeutung:ohneTextbedeutung` |
| `okstraFblisteExclusion` | S/K | aus | FB-Ausschlüsse aus `oks_meta.fbausschluss`: Ein Objekt mit einer Fachbedeutung und Symbolart, die dort mit `blocked` steht, gilt beim Prüfen und Laden als fehlerhaft (Meldung, Datenfehler; geladen wird es trotzdem) und zählt nicht für das Projektgebiet; Pflege: `doku/Betrieb.md`, 8 |

### 1.4 Datenbank

Die Datenquellen nennt die `web.xml` mit ihrem Namen im JNDI-Kontext der Webanwendung
(`java:comp/env`); dahinter stehen die `ResourceLink`-Einträge der `context.xml`
(Abschnitt 3). Altdaten und Planung sind optional, aber nur paarweise: Ist die
OKSTRA-Datenquelle angegeben, muss es auch die zugehörige Service-Datenquelle sein und
umgekehrt, sonst startet der Dienst nicht.

| Parameter | Ort | Vorgabe | Bedeutung |
|---|---|---|---|
| `okstra_database` | S | – (Pflicht) | OKSTRA-Daten (Bestand), Vorlage `jdbc/OKSTRA-DB` |
| `svc_vermdb` | S | – (Pflicht) | Service-Datenbank mit Warteschlange, Aufträgen und Kopfdaten (Bestand), Vorlage `jdbc/SVC-VERMDB` |
| `okstra_data_alt`, `svc_vermdb_alt` | S | – | Datenbank für Altdaten (`jdbc/OKSTRA-ALT`, `jdbc/SVC-ALT`); Anfragen wählen sie mit `database=olddata` bzw. `datasource=Altdaten` |
| `okstra_data_planung`, `svc_vermdb_planung` | S | – | Datenbank für Planungsdaten (`jdbc/OKSTRA-PLANUNG`, `jdbc/SVC-PLANUNG`); `database=planning` bzw. `datasource=Planung` |
| `svc_schema` | S | `public` | Schema der Service-Tabellen. Muss `oks_meta` sein: Die Funktionen aus `Datenbank/` verwenden diesen Namen fest |
| `okstraJobliste` | S/K | `projekt` | Tabelle mit den Aufträgen (Spalten `auftragsnummer`, `projekt`) im Schema aus `svc_schema`, für das Entladen nach Projekt oder Auftrag. Muss `auftrag` sein; die Vorgabe passt nicht zum Schema |
| `okstraTxtpool` | S/K | Einstellung der OKLABI | Kopfdaten und Texte komprimiert in der Datenbank speichern (Textpool). Muss für das Schema aus `Datenbank/` an sein |
| `headerInMetadata` | S | aus | Kopfdaten der Fachobjekte in `oks_meta._header_` (über die Sicht `_kopfdaten_`) statt im OKSTRA-Schema. Muss an sein; schaltet `okstraVersionHeader` mit ein |
| `okstraVersionHeader` | S | aus | OKSTRA-Version in den Kopfdaten speichern |

### 1.5 OKSTRA-Versionen

| Parameter | Ort | Vorgabe | Bedeutung |
|---|---|---|---|
| `okstraVersionDatabase` | S | – (Pflicht) | OKSTRA-Version der Datenbank, etwa `2.022`. Daraus ergibt sich das Schema der Fachdaten: `oks_` und die Version ohne Punkt (`oks_2022`) |
| `okstraVersionAltdaten`, `okstraVersionPlanung` | S | – | ebenso für Altdaten und Planung, Pflicht mit der jeweiligen Datenquelle |
| `okstraVersionRead` | S | leer (Pflicht) | OKSTRA-Versionen, die der Dienst annimmt, als Liste mit `\|` vor, zwischen **und nach** den Versionen: `\|1.007\|1.008\|…\|2.023\|`. Ohne das letzte `\|` gilt die letzte Version nicht. Andere Lieferungen weist der Dienst ab („Daten zur OKSTRA-Version … können nicht gelesen werden“) |
| `okstraVersionWrite` | S | leer (Pflicht) | Versionen, in die der Dienst entladen kann (Anfrageparameter `Version`), gleiche Form. Andere führen zur Fehlermeldung „Die Version … wird nicht unterstützt“. Ohne `Version` entlädt der Dienst in der Version der Datenbank; auch sie muss daher in der Liste stehen |

Die Vorlage nennt lesbar 1.007 bis 2.022, schreibbar 2.016 bis 2.022, der Container
zusätzlich 2.023 (OKLABI 1.6.5.0 kennt 1.007 bis 2.023). Beim Laden migriert die OKLABI in
die Version der Datenbank, beim Entladen in die gewünschte.

### 1.6 Lage- und Höhensystem

| Parameter | Ort | Vorgabe | Bedeutung |
|---|---|---|---|
| `okstraLagesystem` | S/K | `25832` | Lagesystem der Datenbank. Unterstützt ist nur ETRS89/UTM 32: `UTM32` oder `25832`; andere Werte meldet der Dienst beim Start und verwendet 25832 |
| `okstraHoehensystem` | S/K | `170` | Höhensystem der Datenbank. Unterstützt ist nur DHHN2016: `DHHN2016`, `DHHN_2016` oder `170` (AdV-Code) |
| `coordTrans` | S | – | Voll qualifizierter Klassenname einer Erweiterung, die eine andere Transformationsbibliothek als PROJ in die OKLABI einbindet (Schnittstelle `de.okstra.oklabi.TransformiererLoader`). Ohne den Parameter, oder wenn die Klasse fehlt, transformiert die OKLABI mit PROJ |

### 1.7 OkWS

Der OKSTRA-Webdienst (OkWS) fragt die Vermessungsdatenbank über einen
Kommunikationskanal der OKLABI ab (TCP auf der Loopback-Adresse).

| Parameter | Ort | Vorgabe | Bedeutung |
|---|---|---|---|
| `okstraWSbackend` | S | aus | Backend für den OkWS einrichten |
| `okstraWSport` | S | `24641` (Standardkanal der OKLABI) | Port des Kanals |
| `okstraWSresponse` | S | – | Datei mit der Antwort auf die Anfrage `SignOfLife` des OkWS (Vorlage: `okws/beispiel-wfs.xml`); fehlt sie, meldet der Dienst das beim Start |

### 1.8 Antworten und Protokoll

| Parameter | Ort | Vorgabe | Bedeutung |
|---|---|---|---|
| `returnFormat` | S | `plain` | Form der Antworten auf POST-Anfragen und der Meldungen: `JSON` (`{ "success": true, "auftragsnummer": … }`) oder `PLAIN` (`accept` bzw. `failure` und Meldung in der nächsten Zeile). Die bekannten Clients erwarten JSON |
| `verboseLogging` | S | aus | Zusätzliche Meldungen beim Einstellen von Aufträgen im Protokoll des Dienstes |

## 2. Weitere Einstellungen der `web.xml`

**Zeichensätze.** `request-character-encoding` und `response-character-encoding` stehen auf
ISO-8859-1 und müssen so bleiben. Tomcat 11 setzt in seiner globalen `conf/web.xml` UTF-8;
damit würden entladene OKSTRA-Dateien, die der Dienst Byte für Byte durchreicht, doppelt
kodiert, und Formularfelder würden anders gelesen, als die Clients sie schicken.

**Grenzen für Uploads** in `<multipart-config>` im Element `<servlet>`: `max-file-size`,
`max-request-size` (Bytes, `-1` ohne Grenze) und `file-size-threshold` (ab dieser Größe
schreibt der Tomcat den Upload auf die Platte). Ohne das Element gelten 400 MB je Datei,
500 MB je Anfrage und 3 MB. Tomcat 11 nimmt außerdem höchstens 50 Teile je
Multipart-Anfrage an (`maxPartCount` am Connector der `server.xml`); ein Upload hat weniger.

**Start.** `<load-on-startup>` lässt das Servlet beim Start des Tomcat initialisieren. Bricht
die Initialisierung ab (native OKLABI nicht ladbar, Datenquelle fehlt, Platzhalter nicht
ersetzt), steht die Meldung im Protokoll des Tomcat, und Anfragen beantwortet der Tomcat mit
einer Fehlerseite, die sie nennt.

**Kopf.** Die `web.xml` folgt Jakarta EE 11 (`<web-app version="6.1" …>`, wie in der Vorlage).
Eine `web.xml` aus einer Installation unter Tomcat 9 ist durch die Vorlage zu ersetzen und
deren Werte zu übernehmen: Mit der alten läuft das Servlet zwar an, aber mit den
Vorgabegrenzen für Uploads und, ohne die beiden Einträge oben, mit UTF-8 statt ISO-8859-1.

**Nicht mehr ausgewertet:** die Init-Parameter `max_file_size`, `max_request_size` und
`file_size_threshold` (ersetzt durch `<multipart-config>`; stehen sie noch in der `web.xml`,
warnt der Dienst beim Start) und `serverPolicy`.

## 3. Datenquellen

Die Webanwendung baut keine Datenbankverbindungen selbst auf. Adresse und Zugang stehen als
globale Ressourcen in der `conf/server.xml` des Tomcat (`GlobalNamingResources`); die
`META-INF/context.xml` im WAR verknüpft sie mit den Namen, die die `web.xml` verwendet:

| Name in der `web.xml` | `ResourceLink` in der `context.xml` | globale Ressource |
|---|---|---|
| `jdbc/OKSTRA-DB` | vorhanden | `de.ii.datasource.okstra-db` |
| `jdbc/SVC-VERMDB` | vorhanden | `de.ii.datasource.svc-vermdb` |
| `jdbc/OKSTRA-ALT`, `jdbc/SVC-ALT` | zu ergänzen | frei wählbar |
| `jdbc/OKSTRA-PLANUNG`, `jdbc/SVC-PLANUNG` | zu ergänzen | frei wählbar |

Die Namen der globalen Ressourcen sind historisch und müssen so lauten, solange die
mitgelieferte `context.xml` gilt. Eine eigene Kontextdatei
`conf/Catalina/localhost/<Name der Webanwendung>.xml` ersetzt die `context.xml` des WAR;
so lassen sich andere Namen oder die Datenquellen für Altdaten und Planung verknüpfen, und
eine zweite Instanz (etwa eine Testinstanz) kann eigene Datenquellen haben.

Eine Ressource in der `server.xml`:

    <Resource name="de.ii.datasource.okstra-db" auth="Container" type="javax.sql.DataSource"
              driverClassName="org.postgresql.Driver"
              url="jdbc:postgresql://<host>:<port>/<Datenbank>"
              username="<rolle>" password="<kennwort>" maxIdle="1"/>

`<port>` ist der Port von PostgreSQL, meist `5432`. Beide Datenquellen eines Bestands zeigen auf dieselbe Datenbank. Der Dienst erwartet den
Verbindungspool des Tomcat (Vorgabe für `type="javax.sql.DataSource"`) und den
PostgreSQL-Treiber im `lib`-Verzeichnis des Tomcat.

**Rolle.** Die Rolle braucht `oks_meta` im `search_path`, weil die Service-Verbindung einige
Tabellen ohne Schema abfragt, und das Recht `CREATE` auf der Datenbank, weil
`OkstraSqlUtil` im OKSTRA-Schema beim ersten Laden Tabellen anlegt. `Datenbank/einrichten.sql`
richtet beides ein (`Datenbank/README.md`).

## 4. Umgebung des Tomcat

| Einstellung | Art | Bedeutung |
|---|---|---|
| `-Djava.library.path=<Verzeichnis>` | Java-Option | Verzeichnis der nativen OKLABI |
| `--enable-native-access=ALL-UNNAMED` | Java-Option | sonst warnt Java 25 beim Laden der nativen OKLABI; die Startskripte des Tomcat 11 setzen sie |
| `-Xmx…` | Java-Option | Speicher; große Lieferungen brauchen mehrere GB (Container: 3 GB) |
| `-Dorg.apache.tomcat.util.digester.PROPERTY_SOURCE=org.apache.tomcat.util.digester.EnvironmentPropertySource` | Java-Option | Platzhalter in `web.xml` und `server.xml` aus Umgebungsvariablen (Abschnitt 6) |
| `-Doklabi.loader.verbose=true` | Java-Option | der Loader der OKLABI schreibt jede geladene Bibliothek in die Standardausgabe des Tomcat (Fehlersuche) |
| `OKLABI_HOME` | Umgebungsvariable | Ressourcenverzeichnis der OKLABI (mit `res/config.cfg`) |
| `PROJ_LIB` | Umgebungsvariable | `<OKLABI_HOME>/gdal_data`; ohne sie scheitern Koordinatentransformationen still |
| `LD_LIBRARY_PATH` | Umgebungsvariable, Linux | Verzeichnis der nativen OKLABI |
| `PATH` | Umgebungsvariable, Windows | Verzeichnis der DLLs der OKLABI, nur für den Dienst; sonst bricht der Start mit einem Hinweis ab |

Einzelheiten und Befehle für Linux und Windows: `doku/Installation.md`, Abschnitte 2.4 bis 4.

## 5. Ressourcen der OKLABI

Die OKLABI liest ihre Voreinstellungen aus `res/config.cfg` im Ressourcenverzeichnis und aus
einer Benutzerkonfiguration, die dort mit `BenutzerKonfiguration=<Datei>` eingeschaltet
wird (`~` steht für das Ressourcenverzeichnis). Für den Dienst wichtig:

* `Gitterdatei=<Quellsystem>@<Zielsystem>@<Datei>` und `OrdnerGitterdateien`: Gitterdateien
  für Transformationen (Bezug und Einträge: `doku/Installation.md`, Abschnitt 2.4). Im
  Container stellt `vermdb.sh` die Einträge beim Start aus den mit `VERMDB_GITTER_IMAGE`
  eingeschalteten Gittern des Image und `gitter/gitter.cfg` des Konfigurationsverzeichnisses
  zusammen (`Container/README.md`, „Gitterdateien“).
* `FbFehlerbehandlung`: Vorgabe für `okstraFblisteCheck`.

Der Dienst setzt beim Start selbst: Textpool und Datenbank-Identifizierer (`okstraTxtpool`),
die FB-Prüfung (`okstraFblisteCheck`) und den Kanal des OkWS (`okstraWSport`).

## 6. Umgebungsvariablen

Werte der `web.xml` und der `server.xml` können aus Umgebungsvariablen kommen. Der Tomcat
ersetzt Platzhalter `${VARIABLE}` und `${VARIABLE:-Vorgabe}` beim Start, wenn er mit der
Java-Option `PROPERTY_SOURCE` aus Abschnitt 4 läuft, etwa:

    <param-value>${VERMDB_BEHOERDE:-LSBB}</param-value>
    <Resource … password="${VERMDB_DB_PASSWORT}" …/>

Ohne Variable gilt die Vorgabe nach `:-`; eine leer gesetzte Variable ergibt den leeren Wert,
nicht die Vorgabe. Bleibt ein Platzhalter in der `web.xml` ohne Variable und ohne Vorgabe
stehen (oder fehlt die Java-Option), startet der Dienst nicht und nennt den Parameter
(„Die web.xml enthält nicht ersetzte Platzhalter …“). Eine `web.xml` mit festen Werten
braucht nichts davon.

Namen nach dem Schema `VERMDB_` und ein deutscher Name. Die `web.xml` und `server.xml` des
Containers verwenden:

| Variable | Parameter |
|---|---|
| `VERMDB_DB_URL`, `VERMDB_DB_BENUTZER`, `VERMDB_DB_PASSWORT` | Datenquellen (`url`, `username`, `password`) |
| `VERMDB_BEHOERDE` | `okstraAuthority` |
| `VERMDB_BEHOERDE_NAME` | `okstraAuthName` |
| `VERMDB_DATENBANK_NAME` | `okstraDbName` |
| `VERMDB_FBLISTE` | `okstraFbliste` |
| `VERMDB_FBLISTE_PRUEFUNG` | `okstraFblisteCheck` |
| `VERMDB_SERVER_PRAEFIX` | `okstraServerPrefix` |
| `VERMDB_OKSTRA_VERSION` | `okstraVersionDatabase` |
| `VERMDB_LAGESYSTEM` | `okstraLagesystem` |
| `VERMDB_HOEHENSYSTEM` | `okstraHoehensystem` |
| `VERMDB_OKWS` | `okstraWSbackend` |
| `VERMDB_OKWS_PORT` | `okstraWSport` |

Vorgaben im Container und die Variablen für Compose und die Einrichtung (Port, Kennwörter,
Gitterverzeichnis): `Container/README.md`.

Linux: Option und Variablen in `bin/setenv.sh` des Tomcat. Windows-Dienst:
`tomcat11.exe //US//<Dienst> ++JvmOptions=-Dorg.apache.tomcat.util.digester.PROPERTY_SOURCE=… ++Environment VERMDB_BEHOERDE=…`.

## 7. Dateien des Dienstes

| Inhalt | Dateiname | Ort | Hinweis |
|---|---|---|---|
| Protokoll des Dienstes | `Svc_<Zeit>.log` | `okstraOklabilog` | je Start eine Datei; dieselben Meldungen in der Standardausgabe des Tomcat |
| Prüfprotokoll eines Auftrags | `<Auftrag>_laden_<Datei>_<Zeit>.log` | `okstraOklabilog` | verlinkt in der Auftragsliste |
| Migrationsprotokoll | `<Auftrag>_migration_<Datei>_<Zeit>.log` | `okstraOklabilog` | nur wenn die OKSTRA-Version der Lieferung von der der Datenbank abweicht; verlinkt in der Auftragsliste |
| Ladeprotokoll | `<Auftrag>_transfer_<Datei>_<Zeit>.log` | `okstraOklabilog` | Übertragung in die Datenbank |
| Datenfehler | `<Auftrag>_fehler_<Zeit>_<Datei>` | `okstraErrorexportpath` | fehlerhafte Fachobjekte eines Ladeauftrags als OKSTRA-Datei (nur wenn es welche gibt), verlinkt in der Auftragsliste |
| Stand einer Anfrage | `<SID>/erledigt.txt` | `okstraReturnpath` | `working`, `success` oder `failed` und Meldung |
| Ergebnis und Protokoll des asynchronen Entladens | `<SID>/OkstraUnload.xml` (bzw. `.xml.gz`), `<SID>/OkstraUnload.log` | `okstraReturnpath` | |
| hochgeladene Lieferung | `<SID>_<Datei>` | `okstraUploadpath` | dieselbe SID mit demselben Dateinamen überschreibt eine frühere Datei |

Der Dienst löscht keine dieser Dateien, auch nicht beim Löschen eines Auftrags; aufzuräumen
ist im Betrieb.
