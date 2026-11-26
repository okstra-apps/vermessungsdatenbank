<!--
SPDX-FileCopyrightText: 2026 interactive instruments GmbH
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Überblick und Architektur

## 1. Zweck

Die Vermessungsdatenbank verwaltet Vermessungsdaten des Straßenbaus im Format des OKSTRA
(Objektkatalog für das Straßen- und Verkehrswesen): Bestands- und Baubestandsvermessungen
mit Punkten, Linien, Flächen, Symbolen und Beschriftungen, deren Bedeutung die
Fachbedeutungslisten der Länder festlegen, dazu Fachobjekte wie Böschungen oder Bauwerke.

Sie nimmt Lieferungen von Vermessungsstellen entgegen, prüft sie, rechnet sie in die
OKSTRA-Version, das Lage- und das Höhensystem der Datenbank um und lädt sie in eine
PostgreSQL-Datenbank mit PostGIS. Jede Lieferung bildet einen Auftrag; Aufträge gehören zu
Projekten. Aus der Datenbank gibt sie Daten wieder als OKSTRA-Datei ab, ausgewählt nach
Projekt, Auftrag oder Gebiet und umgerechnet in die gewünschte Version und das gewünschte
Lagesystem. Andere Anwendungen, etwa ein Kartendienst, lesen die Datenbank direkt oder
fragen sie über den OKSTRA-Webdienst (OkWS) ab.

Die Anwendung ist ein Servlet für Apache Tomcat. Die fachliche Arbeit am OKSTRA (Lesen,
Prüfen, Migrieren, Transformieren, Schreiben) leistet die OKSTRA-Klassenbibliothek (OKLABI)
der Bundesanstalt für Straßen- und Verkehrswesen, die Übertragung zwischen OKLABI und
Datenbank deren Komponente `OkstraSqlUtil`. Entstanden ist die Vermessungsdatenbank ab 2014
für eine Landesstraßenbauverwaltung.

## 2. Komponenten

```mermaid
flowchart LR
    C["Clients<br/>(Webformular, Themenbrowser,<br/>Fachanwendung)"]
    W["OKSTRA-Webdienst<br/>(OkWS)"]
    subgraph T["Apache Tomcat 11"]
        S["Servlet<br/>Okstra2DbServlet"]
        K["Kontext /vermdb"]
    end
    subgraph O["OKLABI 1.6.5.0 (BASt)"]
        J["OKLABI.jar"]
        N["native OKLABI<br/>(mit PROJ, GDAL, Xerces-C …)"]
        U["OkstraSqlUtil"]
    end
    R[("Ressourcen der OKLABI<br/>Schemata, Fachbedeutungslisten,<br/>Gitterdateien")]
    D[("PostgreSQL/PostGIS<br/>oks_meta, oks_2022")]
    F[("Datenverzeichnisse<br/>Uploads, Protokolle,<br/>Rückgaben, Datenfehler")]

    C -- "HTTP" --> S
    C -- "HTTP (Protokolle, Rückgaben)" --> K
    W -- "Kanal der OKLABI (TCP)" --> N
    S --> J --> N
    N --> R
    S --> U -- "JDBC" --> D
    S -- "JDBC" --> D
    S --> F
    K --> F
```

| Komponente | Aufgabe |
|---|---|
| **Servlet** `Okstra2DbServlet` | nimmt Anfragen über HTTP an, führt die Warteschlange, steuert Prüfen, Laden und Entladen, schreibt Protokolle und Auftragslisten |
| **OKLABI** (`OKLABI.jar` und native Bibliothek) | liest und schreibt OKSTRA-Dateien (XML; CTE weist der Dienst ab), prüft gegen Schema, Schlüsseltabellen und Fachbedeutungslisten, migriert zwischen OKSTRA-Versionen, transformiert Koordinaten (PROJ mit Gitterdateien); stellt den Kanal für den OkWS bereit |
| **`OkstraSqlUtil`** | überträgt Datenbestände der OKLABI in die Datenbank und liest sie zurück; legt die Tabellen des OKSTRA-Schemas beim ersten Laden selbst an |
| **PostgreSQL mit PostGIS** | Schema `oks_meta` für Warteschlange, Aufträge, Projekte, Kopfdaten, Fachbedeutungslisten und weitere Listen; OKSTRA-Schema (etwa `oks_2022`) mit einer Tabelle je Objektart |
| **Datenverzeichnisse** | hochgeladene Lieferungen, Protokolle, Rückgaben je Anfrage, Datenfehler |
| **Kontext `/vermdb`** bzw. Webserver | liefert Protokolle, Datenfehler und Rückgaben an die Clients aus |
| **OkWS** | eigener Webdienst, der die Vermessungsdatenbank über den Kanal der OKLABI abfragt (optional) |

Auf die Datenbank greifen nur das Servlet und `OkstraSqlUtil` zu, beide in Java über JDBC
mit den Datenquellen des Tomcat. Die native OKLABI hat in dieser Version keine eigene
Verbindung zu PostgreSQL; sie liest nur ihre Ressourcen, darunter die Schemadatenbank
(SQLite).

Die Datenbank hat zwei Datenquellen, die auf dieselbe Datenbank zeigen: die OKSTRA-Daten
und die Service-Datenbank mit `oks_meta`. Optional kommen getrennte Datenbanken für
Altdaten und Planungsdaten hinzu, mit derselben Struktur.

### Datenhaltung

| Schema | Inhalt |
|---|---|
| `oks_meta` | `warteschlange` (Aufträge mit Status und Protokollen), `auftrag` und `projekt` (mit Gebietsgeometrie), `_header_` mit `txtpool` (Kopfdaten jedes Fachobjekts: Auftrag, Fachbedeutungsliste, Lage- und Höhensystem der Lieferung), `fbliste` mit `efbversion` (Fachbedeutungslisten), `ekoordrefsys` (Lage- und Höhensysteme), `gebiete`, `fbausschluss`; Funktionen zum Pflegen von Auftrag und Projekt und zum Löschen eines Auftrags |
| OKSTRA-Schema, etwa `oks_2022` | eine Tabelle je Objektart der OKSTRA-Version der Datenbank, Zwischentabelle für Beziehungen, Metadaten von `OkstraSqlUtil`; angelegt beim ersten Laden |

Die Skripte für `oks_meta` liegen in `Datenbank/` (`Datenbank/README.md`).

## 3. Abläufe

### 3.1 Lieferung laden

Lieferungen gehen in eine Warteschlange; ein einzelner Lader arbeitet sie nacheinander ab.

```mermaid
sequenceDiagram
    actor C as Client
    participant S as Servlet
    participant Q as Warteschlange<br/>(oks_meta)
    participant L as Lader
    participant O as OKLABI
    participant U as OkstraSqlUtil
    participant D as Datenbank

    C->>S: POST Lieferung (Datei, SID, Projekt, Systeme, FB-Liste)
    S->>S: speichern, SID und Version prüfen
    S->>Q: Auftrag anlegen
    S-->>C: { success, auftragsnummer }
    S->>L: Lader anstoßen (falls frei)
    L->>Q: nächsten wartenden Auftrag holen
    L->>O: Datei lesen und prüfen
    O-->>L: Prüfprotokoll
    L->>L: Eingabeprüfung (Kennungen)
    alt nur prüfen oder abgelehnt
        L->>Q: Status „geprüft“ bzw. „abgelehnt“
    else laden
        L->>O: migrieren (OKSTRA-Version), transformieren (Lage, Höhe)
        L->>U: übertragen
        U->>D: Fachobjekte und Kopfdaten schreiben
        L->>Q: Status „geladen“, Protokolle, Datenfehler
        Q->>D: Auftrag und Projektgebiet pflegen (Trigger)
    end
    L->>Q: nächster Auftrag
    C->>S: GET Queue
    S-->>C: Auftragsliste mit Status und Links
```

Der Status eines Auftrags (`doku/Schnittstelle.md`, 5):

```mermaid
stateDiagram-v2
    [*] --> wartet
    wartet --> laden_pruefen: Lader beginnt
    laden_pruefen --> geprueft: nur prüfen
    laden_pruefen --> abgelehnt: Systeme oder Kennungen unzulässig
    laden_pruefen --> migrieren
    migrieren --> uebertragen
    uebertragen --> geladen
    uebertragen --> Ladefehler
    geladen --> geloescht: DeleteJob
    laden_pruefen: laden, prüfen
    geprueft: geprüft
    uebertragen: übertragen
    geloescht: gelöscht
```

### 3.2 Daten entladen

Das Servlet wählt die Objekte aus (Bedingungen je Objektart), `OkstraSqlUtil` liest sie per
JDBC und legt sie als Fachobjekte in einem Datenbestand der OKLABI an. Die OKLABI migriert und
transformiert den Bestand und schreibt ihn selbst als OKSTRA-XML in eine temporäre Datei.
Synchron (GET `Read`) reicht das Servlet diese Datei Byte für Byte in die Antwort durch;
asynchron (POST `holeDatei`) antwortet es sofort und kopiert die Datei in das
Rückgabeverzeichnis der Anfrage, wo der Client sie über `okstraServerPrefix` abholt. Die
temporäre Datei löscht es danach.

```mermaid
sequenceDiagram
    actor C as Client
    participant S as Servlet
    participant U as OkstraSqlUtil
    participant D as Datenbank
    participant O as OKLABI
    participant K as /vermdb

    C->>S: POST holeDatei (SID, Projekt/Auftrag/Gebiet, Version, Systeme)
    S-->>C: { success, "Download-Auftrag erhalten." }
    S->>S: erledigt.txt = working
    S->>D: Objektkennungen der Aufträge lesen (oks_meta._header_)
    S->>S: je Objektart Abfrage mit Bedingungen bilden
    S->>U: Abfragen übergeben
    U->>D: Objekte lesen (JDBC)
    U->>O: Fachobjekte im Datenbestand der OKLABI anlegen
    S->>O: migrieren, transformieren
    S->>O: als OKSTRA-XML in temporäre Datei schreiben
    O->>O: Datei schreiben
    S->>S: nach Rückgaben/SID/OkstraUnload.xml kopieren, erledigt.txt = success
    loop bis success oder failed
        C->>K: GET download/SID/erledigt.txt
    end
    C->>K: GET download/SID/OkstraUnload.xml
```

### 3.3 OkWS

Der OkWS fragt einzelne Objektarten mit Bedingungen ab. Er spricht nicht HTTP mit dem
Servlet, sondern den Kommunikationskanal der OKLABI; das Servlet meldet sich dort beim Start
als Backend an (`okstraWSbackend`).

```mermaid
sequenceDiagram
    participant W as OkWS
    participant N as OKLABI (Kanal)
    participant S as Servlet (Backend)
    participant D as Datenbank

    S->>N: beim Start als Backend anmelden (Port okstraWSport)
    W->>N: Anfrage (TYPE, Bedingungen, OKSTRAVERSION, WFSEXPECTED)
    N->>S: Anfrage weiterreichen
    S->>D: Objekte der Objektart nach Bedingungen auswählen und lesen
    S-->>N: OKSTRA- bzw. WFS-Antwort
    N-->>W: Antwort
```

## 4. Repository

| Ordner | Inhalt |
|---|---|
| `DatenbankLader/` | die Webanwendung: Servlet (`src/main/java`), `web.xml`-Vorlage und `context.xml` (`src/main/webapp`), Unit-Tests (`src/test/java`) |
| `Datenbank/` | Skripte für die Datenbank (`oks_meta`), Laden der Fachbedeutungslisten |
| `Container/` | Image und Compose für die Standardinstallation |
| `werkzeuge/` | Hilfsprogramme für die Installation (`proj.db`, Höhengitter) |
| `oklabi-release/` | Java-Archive des OKLABI-Release, vom Bau geladen (nicht versioniert) |
| `doku/` | Dokumentation |
| `LICENSES/`, `NOTICE`, `REUSE.toml` | Lizenzen |

Gebaut wird mit Gradle (`README.md`). Das WAR enthält das Servlet, `OkstraSqlUtil` und die
Bibliotheken aus Maven Central (Apache Commons IO, Log4j, PostgreSQL-JDBC); `OKLABI.jar`
und der PostgreSQL-Treiber gehören in das `lib`-Verzeichnis des Tomcat, die native OKLABI
neben ihn.

## 5. Weiter

* `doku/Installation.md`: Installation im Container und mit eigenem Tomcat
* `doku/Konfiguration.md`: Parameter und Umgebung
* `doku/Schnittstelle.md`: Anfragen, Status, bekannte Einschränkungen
* `doku/Betrieb.md`: Betrieb, Fehlerbilder, Sicherung
