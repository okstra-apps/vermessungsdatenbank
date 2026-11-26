<!--
SPDX-FileCopyrightText: 2026 interactive instruments GmbH
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Schnittstelle

Die Vermessungsdatenbank nimmt OKSTRA-Lieferungen entgegen, prüft und lädt sie in die
Datenbank und gibt Daten als OKSTRA-Datei wieder ab. Clients (etwa ein Themenbrowser oder
ein Dialog in einem Straßeninformationssystem) sprechen sie über HTTP an; der OKSTRA-Webdienst
(OkWS) über einen eigenen Kommunikationskanal (Abschnitt 6). Die Schnittstelle ist über die Jahre aus einem
Webformular gewachsen; die Feldnamen `f1`, `f2` … stammen daher.

Beispiele verwenden `<URL>` für die Adresse des Servlets, etwa
`http://localhost:8099/Okstra2Db/Okstra2DbServlet` im Container, und die Beispieldaten aus
`testdaten/`. Die Antworten sind die einer frischen Standardinstallation, in der
`beispiel.xml` als Auftrag 1 geprüft und als Auftrag 2 geladen wurde. Dieselben Anfragen mit
Prüfung der Antworten liegen als Hurl-Dateien in `doku/beispiele/`.

## 1. Allgemeines

| | |
|---|---|
| Adresse | `http://<Rechner>:<Port>/Okstra2Db/Okstra2DbServlet` |
| Methoden | GET für Abfragen, Entladen und Löschen eines Auftrags; POST für Lieferungen, asynchrones Entladen und Historisierung |
| Zugriffsschutz | keiner; Anmeldung und Zugriffsbeschränkung sind Sache eines vorgeschalteten Webservers |
| Parameternamen | Groß- und Kleinschreibung beliebig |
| Zeichensatz | Formularfelder und Antworten in ISO-8859-1; entladene Dateien unverändert so, wie die OKLABI sie schreibt, mit dem Zeichensatz ihres Kopfes im `Content-Type` (unter Linux `utf-8`, unter Windows `Windows-1252`) |
| Antwortformat | nach `returnFormat` (`doku/Konfiguration.md`, 1.8): JSON `{ "success": true\|false, "auftragsnummer": …, "message": "…" }` oder Klartext `accept` bzw. `failure` mit der Meldung in der nächsten Zeile. Die Beispiele zeigen JSON |
| HTTP-Status | 200 auch bei `"success": false`; 500 mit der Fehlerseite des Tomcat, wenn die Verarbeitung mit einer Ausnahme abbricht (Abschnitt 8) |

Zu den Antworten:

* GET-Antworten tragen `Content-Type: text/json` (bzw. `text/xml` bei Klartext),
  Auftragslisten `text/html`, `Areas` und `DeleteJob` `text/plain`, entladene Dateien
  `text/xml; charset=<Zeichensatz aus dem Kopf der Datei>` bzw. `application/x-gzip`. POST-Antworten haben keinen `Content-Type`.
* Fehlermeldungen in Antworten auf POST verwenden HTML-Entitäten für Umlaute
  (`unvollst&auml;ndig`); die übrigen Meldungen enthalten die Umlaute unverändert.
* Anführungszeichen in Meldungen werden in JSON als `""` wiedergegeben, Backslashes und
  Steuerzeichen gar nicht maskiert; eine Meldung, die sie enthält, ist kein gültiges JSON.

Jede GET-Anfrage außer `Areas`, `Read` und `ReadLock` stößt außerdem den nächsten wartenden
Auftrag der Warteschlange an. Wird dabei ein Auftrag gestartet, wird bei `GetThemes`,
`GetTypes`, `Init`, `WakeUp`, `DeleteJob` und unbekannten Anfragen an die Meldung ein
„, Ladevorgang angestossen“ angehängt (Abschnitt 8). Die Auftragslisten antworten nach dem
Anstoßen etwa vier Sekunden später.

**Aliase.** Einige Parameter haben zwei Namen (etwa `SID` und `SessionID`). Sind beide mit
verschiedenen Werten angegeben, weist der Dienst die Anfrage ab („… ist mehrdeutig belegt“).

## 2. Ablauf

Lieferungen (POST mit Datei) gehen in eine Warteschlange und werden nacheinander
abgearbeitet. Die Antwort kommt sofort mit der Auftragsnummer; den Fortgang zeigt die
Auftragsliste (`Queue`). Ein Auftrag durchläuft:

1. **Registrieren:** Der Dienst speichert die Datei, prüft SID, Lage- und Höhensystem und die
   OKSTRA-Version (`okstraVersionRead`) und legt den Auftrag an.
2. **Prüfen:** Die OKLABI liest die Datei und prüft sie gegen das OKSTRA-Schema, die
   Schlüsseltabellen und die Fachbedeutungsliste; dazu die Eingabeprüfung (Abschnitt 7).
   Ergebnis ist das Prüfprotokoll. Ein Prüfauftrag (`nurPruefen`) endet hier.
3. **Migrieren und transformieren:** in die OKSTRA-Version, das Lage- und das Höhensystem der
   Datenbank.
4. **Laden:** Übertragung in die Datenbank (Ladeprotokoll); fehlerhafte Objekte stehen
   zusätzlich in der Datei der Datenfehler, geladen werden sie trotzdem. Danach werden
   Auftrag und Projektgebiet in `oks_meta` gepflegt.

Entladen geschieht sofort (GET `Read`) oder im Hintergrund (POST `holeDatei`), nicht über die
Warteschlange.

## 3. GET

Der Parameter `request` wählt die Operation. Fehlt er oder ist er unbekannt:
`{ "success": false, "message": "Request-Typ unbekannt." }`.

### 3.1 Abfragen

| `request` | Antwort |
|---|---|
| `GetThemes` | `{ "success": true, "message": "Hydrographie\|Kataster\|Leitungen\|Planung\|Topographie\|Verkehr\|Verwahltungsgrenzen\|DGM\|DOM" }`; die Stellen 1 bis 9 entsprechen dem Parameter `Thema` (Schreibweise wie ausgeliefert) |
| `GetTypes` | `{ "success": true, "message": "1\|2\|3\|4" }`; ohne erkennbare Verwendung |
| `Areas` | Klartext `\|<Kennung>%<Name>\|<Kennung>%<Name>…` aus `oks_meta.gebiete`, etwa `\|1%Beispiel Deutschland\|2%Beispiel Mitte`; die Kennung ist der Wert für `Gebiet` |
| `Init` | `{ "success": true, "message": "Service initialisiert" }` |
| `WakeUp` | `{ "success": true, "message": "Service benachrichtigt" }` |

`Init` und `WakeUp` stoßen nur die Warteschlange an.

    curl "<URL>?request=GetThemes"

### 3.2 Auftragslisten

HTML-Tabelle der Aufträge, neueste zuerst.

| `request` | Aufträge |
|---|---|
| `Queue` | Ladeaufträge, ohne gelöschte |
| `QueueCheck` | Prüfaufträge, ohne gelöschte |
| `FullQueue` | alle Einträge der Warteschlange |

| Parameter | Werte |
|---|---|
| `datasource` | `Bestand` (Vorgabe, auch bei unbekanntem Wert), `Altdaten`, `Planung`. Ohne eingerichtete Datenbank für Altdaten bzw. Planung: HTTP 500 |

Spalten: Auftrag, Projekt, Session-ID, Benutzername, Zeitstempel, Name (der Datei), Status
(Abschnitt 5), eingestellt, Stichtag, Format, Löschung, Version Datensatz,
Fachbedeutungen, KoordRefSystem, Höhensystem, beendet, Laden, Migration, Übertragung,
Datenfehler. Die letzten vier verlinken die Protokolle bzw. die Datei der Datenfehler unter
`okstraServerPrefix`.

    curl "<URL>?request=Queue"

### 3.3 `Read`: Daten entladen

Die Antwort ist die OKSTRA-Datei (XML), ausgewählt aus dem Bestand. Der Dienst legt dabei
`<okstraReturnpath>/<SID>/` an und schreibt `erledigt.txt`.

| Parameter | Alias | Pflicht | Bedeutung |
|---|---|---|---|
| `SID` | `SessionID` | ja | Kennung der Anfrage (Abschnitt 7) |
| `TS` | `TimeStamp` | nein | Zeitstempel des Clients, nur protokolliert |
| `Projekt` | `PRJNM` | ¹ | Objekte aus den Aufträgen dieses Projekts |
| `Auftrag` | | ¹ | Objekte aus diesen Aufträgen, Auftragsnummern durch Komma getrennt; zusätzlich zu `Projekt` |
| `Gebiet` | `f100` | ¹ | Objekte im Gebiet mit dieser Kennung aus `Areas`. Ein Wert, der keine Kennung ist, wird mit einer Warnung im Protokoll übergangen |
| `Area` | | ¹ | Objekte in dieser Fläche, als EWKT; ohne `SRID=25832;` am Anfang wird sie in das Lagesystem der Datenbank umgerechnet. Vorrang vor `Gebiet`; umschließende Anführungszeichen werden entfernt |
| `Thema` | `f21` | ¹ | `0` (Vorgabe) ohne Einschränkung; `1` bis `9` nach `GetThemes`. Siehe Abschnitt 8 |
| `Version` | `f101` | nein | OKSTRA-Version der Datei, aus `okstraVersionWrite`; Vorgabe die Version der Datenbank |
| `f22` | `SRS` | nein | Lagesystem der Datei als EPSG-Code: `25832` (Vorgabe), `25833`, `2398`, `31466`, `31467`, `31468`, `31469`, `4326`; andere Werte weist der Dienst ab („Koordinatenreferenzsystem wird nicht unterstützt.“) |
| `f23` | `HS` | nein | Höhensystem als AdV-Code: `170` DHHN2016 (Vorgabe), `160` DHHN92 |
| `gzip` | | nein | `1`, `true` oder `gzip`: Datei gzip-komprimiert (`application/x-gzip`) |

¹ Die Bedingungen werden mit UND verknüpft. **Ohne jede Bedingung, oder wenn keine passt
(etwa ein unbekanntes Projekt), enthält die Datei keine Objekte**; einen Abzug des ganzen
Bestands gibt es nicht.

Wirksam sind nur die Namen `f22` und `f23`; mit `SRS` und `HS` prüft der Dienst den Wert,
wendet ihn aber nicht an. Die Transformation nutzt die Gitterdateien der OKLABI
(`doku/Installation.md`, 2.4).

    curl -o auftrag.xml "<URL>?request=Read&SID=abzug1&Auftrag=2"
    curl -o auftrag-utm33.xml "<URL>?request=Read&SID=abzug2&Auftrag=2&f22=25833"
    curl -o projekt.xml.gz "<URL>?request=Read&SID=abzug3&Projekt=Beispiel&gzip=1"
    curl -G -o flaeche.xml "<URL>" --data-urlencode request=Read --data-urlencode SID=abzug4 \
      --data-urlencode "Area=SRID=25832;POLYGON((688500 5740900,688600 5740900,688600 5741000,688500 5741000,688500 5740900))" \
      --data-urlencode Auftrag=2

`auftrag.xml` ist eine `gml:FeatureCollection` mit 77 `gml:featureMember` in UTM 32 (Ausdehnung
ab `688550.314 5740957.345`, mit dem Gitter NTv2LSBB_LSA gerechnet), `auftrag-utm33.xml`
dieselben in UTM 33 (ab `274749.876 5742459.809`, `srsName="urn:adv:crs:ETRS89_UTM33"`). `projekt.xml.gz` ist
gzip-komprimiert (`Content-Type: application/x-gzip`). `flaeche.xml` enthält 10 Objekte:
2 Punkte und 1 Linie in der Fläche und die 7 Böschungen des Auftrags (Abschnitt 8).

Fehler: fehlende oder ungültige SID, ein nicht unterstütztes Lagesystem und mehrdeutige
Parameter beantwortet der Dienst mit `"success": false`; eine nicht schreibbare Version
(„Die Version … wird nicht unterstützt“), ein Thema außerhalb von 0 bis 9 und eine
`Auftrag`-Liste, die nicht aus ganzen Zahlen besteht, mit HTTP 500.

### 3.4 `ReadLock`

Gedacht zum Entladen mit Sperre der Objekte; die Sperre ist nicht umgesetzt. Die Anfrage
verhält sich wie `Read` (3.3), mit denselben Parametern.

### 3.5 `DeleteJob`: Daten eines Auftrags löschen

Entfernt die Objekte eines Ladeauftrags aus der Datenbank und markiert den Auftrag als
gelöscht. Dateien (Upload, Protokolle) bleiben.

| Parameter | Pflicht | Werte |
|---|---|---|
| `auftrag` | ja | Auftragsnummer |
| `datasource` | ja | `Bestand`, `Altdaten`, `Planung` |

Die Antwort ist immer `"success": true`, das Ergebnis steht in `message`:
„Auftragslöschung:  Auftrag 2 entfernt, 12 Datentabellen betroffen.“ (mit zwei Leerzeichen
nach dem Doppelpunkt) bzw. „Auftragslöschung: nicht möglich …“ (etwa ohne `datasource` oder
bei ungültiger Nummer).

    curl "<URL>?request=DeleteJob&auftrag=2&datasource=Bestand"

## 4. POST

Die Art der Anfrage ergibt sich aus den Feldern, in dieser Reihenfolge:

1. `nurPruefen` vorhanden oder `pruefeDatei` belegt: **Lieferung** (4.1);
2. sonst `holeDatei` belegt: **asynchrones Entladen** (4.2);
3. sonst: **Historisierung** (4.3).

Lieferungen kommen als `multipart/form-data` mit der Datei, asynchrones Entladen und
Historisierung als `application/x-www-form-urlencoded`.

### 4.1 Lieferung: prüfen oder laden

| Feld | Pflicht | Bedeutung |
|---|---|---|
| Datei | ja¹ | Dateiteil unter beliebigem Feldnamen; gespeichert als `<okstraUploadpath>/<SID>_<Dateiname>` |
| `pruefeDatei` | ² | beliebiger Wert: prüfen und laden |
| `nurPruefen` | ² | vorhanden (Wert beliebig): nur prüfen |
| `SID` | ja | Kennung der Anfrage (Abschnitt 7) |
| `PRJNM` | nein | Projektname; Grundlage für das Entladen nach `Projekt` |
| `username` | nein | Benutzer; fehlt er, trägt der Dienst das Konto des Tomcat ein |
| `TS` | nein | Zeitstempel des Clients, nur gespeichert |
| `f1` | nein | Format: `XML` (jeder andere Wert außer `CTE` gilt als XML). Fehlt es, entscheidet die Endung: `.cte` gilt als CTE, sonst XML. CTE wird abgewiesen (Abschnitt 7) |
| `f2` | nein | Dateiname beim Client, für Auftragsliste und Protokolle |
| `f4` | nein | Stichtag, `TT.MM.JJJJ` oder `JJJJ-MM-TT`; leer oder `unbelegt`: heute. Wird Beginn der Gültigkeit neuer Objekte. Ungültige Tage rechnet der Dienst weiter (`31.02.2026` → `03.03.2026`) |
| `f7` | ja | Fachbedeutungsliste: Version wie `3.5` (mit `f8`) oder laufende Nummer (`2`–`5` → 1.3–1.6, ab `6` → 2.0 nach `6 + 10·(Haupt−2) + Unter`); `-1`: aus der Datei |
| `f8` | ja³ | Bundesland der Liste: Name (`Sachsen-Anhalt`, `Hessen`, `alle`, `Autobahn` …) oder Nummer: `0` alle, `1` Schleswig-Holstein, `2` Hamburg, `3` Niedersachsen, `4` Bremen, `5` Nordrhein-Westfalen, `6` Hessen, `7` Rheinland-Pfalz, `8` Baden-Württemberg, `9` Bayern, `10` Saarland, `11` Berlin, `12` Brandenburg, `13` Mecklenburg-Vorpommern, `14` Sachsen, `15` Sachsen-Anhalt, `16` Thüringen, `17` ohne, `18` Autobahn. Bei `alle`, `ohne` und `Autobahn` mit einer Liste ab 2.0 gilt das Land aus `okstraFbliste` |
| `f20` | ja³ | `laden`; jeder andere Wert und das Fehlen markieren den Auftrag als Löschauftrag (Abschnitt 8) |
| `f21` | nein | Thema, wird in den Kopfdaten gespeichert |
| `f22` | ja | Lagesystem der Datei, EPSG: `25832`, `25833`, `2398`, `31466`, `31467`, `31468`; `31469` und `4326` gelten als nicht angegeben (dann das der Datei) |
| `f23` | nein | Höhensystem der Datei: `160` DHHN92, `170` DHHN2016; fehlt es, das der Datei |
| `database` | nein | `inventory` Bestand (Vorgabe), `olddata` Altdaten, `planning` Planung |
| `uploadfile` | ¹ | statt der Datei: Pfad einer Datei im Übergabeverzeichnis des Servers (Abschnitt 7) |

¹ Datei oder `uploadfile`. ² eines von beiden. ³ nicht erzwungen, ohne sinnvollen Wert aber
falsches Ergebnis (Abschnitt 8).

Antwort: `{ "success": true, "auftragsnummer": 1 }`. Abgewiesen mit `"success": false` werden
Anfragen ohne oder mit ungültiger SID und ohne Datei. Fehlen `f7` oder `f22`, antwortet der
Dienst mit HTTP 500. Ob die Lieferung inhaltlich in Ordnung ist, zeigen danach Status und
Protokolle in der Auftragsliste.

    curl -F nurPruefen=1 -F SID=lieferung1 -F PRJNM=Beispiel -F username=beispiel \
      -F f1=XML -F f2=beispiel.xml -F f4=11.11.2026 -F f7=3.5 -F f8=Sachsen-Anhalt \
      -F f20=laden -F f22=2398 -F f23=170 \
      -F "datei=@testdaten/beispiel.xml;type=text/xml" "<URL>"

`beispiel.xml` liegt in Gauß-Krüger 4 (42/83, `2398`) mit DHHN2016 (`170`) und nutzt die
Fachbedeutungsliste Sachsen-Anhalt 3.5. Zum Laden `pruefeDatei=1` statt `nurPruefen=1`
(Antwort dann mit Auftragsnummer 2). Die Auftragsliste zeigt danach „geprüft“ bzw.
„geladen“.

### 4.2 Asynchrones Entladen (`holeDatei`)

Als Formular (`application/x-www-form-urlencoded`). Als Multipart nimmt der Dienst die
Anfrage nur mit einem Dateiteil an (die Datei wird im Upload-Verzeichnis gespeichert, sonst
nicht verwendet); ohne Datei weist er sie ab („Es wurden keine Daten hochgeladen“).

| Feld | Bedeutung |
|---|---|
| `holeDatei` | beliebiger Wert |
| `database` | wie bei 4.1; wirkt als Formular nicht: Entladen wird immer aus dem Bestand (Abschnitt 8) |
| übrige | wie bei GET `Read` (3.3): `SID`, `Projekt`, `Auftrag`, `Gebiet`, `Area`, `Thema`, `Version`, `f22`, `f23`, `gzip` |

Antwort sofort: `{ "success": true, "message": "Download-Auftrag erhalten." }`. Das Ergebnis
entsteht im Hintergrund in `<okstraReturnpath>/<SID>/`:

| Datei | Inhalt |
|---|---|
| `erledigt.txt` | `working`, `success` oder `failed`, bei `failed` mit Meldung in der nächsten Zeile |
| `OkstraUnload.xml` (bzw. `.xml.gz`) | die Daten |
| `OkstraUnload.log` | Protokoll |

Abgeholt wird über `okstraServerPrefix` (`<Präfix>download/<SID>/…`, siehe
`doku/Installation.md`, 2.6):

    curl --data-urlencode holeDatei=1 --data-urlencode SID=abzug5 --data-urlencode Auftrag=2 "<URL>"
    curl "http://localhost:8099/vermdb/download/abzug5/erledigt.txt"
    curl -o abzug5.xml "http://localhost:8099/vermdb/download/abzug5/OkstraUnload.xml"

`erledigt.txt` lautet nach wenigen Sekunden `success`; `abzug5.xml` entspricht dann
`auftrag.xml` aus 3.3 bis auf Kennungen, Namen und Zeitstempel im Kopf und die Prüfsumme.

### 4.3 Historisierung

Beendet Objekte und verknüpft sie mit Nachfolgern (Vorgänger-Nachfolger-Beziehung der
OKSTRA-Historisierung). **Derzeit wirkungslos**, siehe Abschnitt 8.

| Feld | Pflicht | Bedeutung |
|---|---|---|
| `op` | ja | `1` Identitätswechsel (1 Quelle → 1 Ziel), `2` Teilung (1 → mindestens 2), `3` Verschmelzung (mindestens 2 → 1), `4` Beendigung (mindestens 1 → keines) |
| `qi` | ja | Kennungen (`foid`) der Quellobjekte, durch `;` getrennt |
| `zi` | ja außer bei `4` | Kennungen der Zielobjekte, durch `;` getrennt |
| `f4` | nein | Stichtag wie bei 4.1, Vorgabe heute |
| `SID` | nein | Kennung der Anfrage |

Wirkung: Ende der Gültigkeit der Quellen und Beginn der Gültigkeit der Ziele auf den
Stichtag, je Paar ein Eintrag der Vorgänger-Nachfolger-Beziehung. Antwort bei Erfolg
`{ "success": true }`, bei Fehlern **Klartext** `failure` mit den Meldungen, auch bei
`returnFormat=JSON` (etwa `failureQuelle in Datenbank nicht gefunden.`).

## 5. Auftragsstatus

Die Auftragsliste zeigt den Status als Text, die Warteschlange (`oks_meta.warteschlange`,
Spalte `status`) speichert ihn als Zahl.

| Anzeige | Bedeutung |
|---|---|
| wartet | eingestellt, noch nicht begonnen |
| laden, prüfen / migrieren / übertragen | in Bearbeitung |
| geladen / geladen, mit Meldungen | Ladeauftrag abgeschlossen |
| geprüft / geprüft, mit Meldungen | Prüfauftrag abgeschlossen |
| abgelehnt | Lieferung nicht verwendbar (Lage- oder Höhensystem, unzulässige Kennungen; Status −5) |
| Ladefehler | Übertragung in die Datenbank gescheitert (Status −8) |
| gelöscht | Daten mit `DeleteJob` entfernt (andere negative Werte) |
| abgebrochen | beendet, ohne den Abschluss zu erreichen |

Die Zahl setzt sich aus Stufen zusammen: +100 Prüfung, +500 Lage- oder Höhensystem
ungültig, +1000 je Beginn und Ende der Migration, +10.000 Transformation, +1.000.000
Abschluss. Die letzte Ziffer unterscheidet: `0` geladen, `1` geladen mit Meldungen, `5`
geprüft, `6` geprüft mit Meldungen; etwa `1010100` geladen, `1010105` geprüft.

## 6. OkWS-Kanal

Ist `okstraWSbackend` eingeschaltet (`doku/Konfiguration.md`, 1.7), beantwortet der Dienst
Anfragen des OKSTRA-Webdienstes über den Kommunikationskanal der OKLABI (TCP auf der
Loopback-Adresse, Port `okstraWSport`), nicht über HTTP. Abgefragt wird der Bestand.

| Parameter | Bedeutung |
|---|---|
| `TEST` | Lebenszeichen; liefert die Datei aus `okstraWSresponse` |
| `TYPE` | Objektart (Pflicht außer bei `TEST`); muss in der OKSTRA-Version der Datenbank vorkommen |
| `MAXFEATURES` | Höchstzahl der Objekte |
| `OKSTRAVERSION` | OKSTRA-Version der Antwort, Vorgabe die der Datenbank |
| `WFSEXPECTED` | `1`/`ja`/`true` (Vorgabe) oder `0`/`nein`/`false`: Antwort im Format eines WFS |
| `ID` | Auswahl über die Objektkennung (`oid`); nicht mit anderen Bedingungen kombinierbar |
| `EQ_`, `NE_`, `GT_`, `GE_`, `LT_`, `LE_`, `LIKE_`, `INTERSECT_` je mit `NAME` und `WERT` | Bedingung als Paar, etwa `EQ_NAME=Strassenname` und `EQ_WERT=…`; mehrere Werte durch `$@$` getrennt; Bedingungen mit UND verknüpft. `INTERSECT_` nur für Geometrien (EWKT), `LIKE_` nur für Texte. Ein nicht numerischer Wert für eine Zahl wird abgewiesen („Fehler: Wert … ist keine Zahl“) |
| `SRS` | wird geprüft (`EPSG:` mit 25832, 25833, 31466 bis 31468, 2397 bis 2399, 4326 oder 4979, sonst Fehler „Koordinatenreferenzsystem … falsch angegeben“), aber nicht angewandt |
| `OPERATION`, `FILTERTYPE` | werden angenommen, aber nicht ausgewertet |

Die Antwort ist immer in ETRS89/UTM 32 (EPSG:25832) und DHHN2016, wie bei GET `Read` ohne
`f22` und `f23`; liegt die Datenbank in einem anderen System, rechnet der Dienst um.

## 7. Eingabeprüfung

* **Session-ID** (`SID`): Sie wird Teil von Datei- und Verzeichnisnamen. Erlaubt sind 1 bis
  128 Zeichen aus `A-Z`, `a-z`, `0-9`, `_`, `-` und `.`, nicht mit einem Punkt am Anfang;
  andere weist der Dienst ab („Session-ID enthält unzulässige Zeichen …“).
* **Lokale Dateien** (`uploadfile`): nur aus dem Übergabeverzeichnis `okstraTransferpath`
  oder darunter; ein relativer Pfad gilt relativ zu diesem Verzeichnis. Ohne den
  Init-Parameter, oder für einen Pfad außerhalb, weist der Dienst die Anfrage ab, auch wenn
  zusätzlich eine Datei hochgeladen wurde.
* **Kennungen** von Schlüsseltabellen- und Aufzählungseinträgen in Lieferungen: nur
  Buchstaben (`A-Z`, `a-z`, `Ä`, `Ö`, `Ü`, `ä`, `ö`, `ü`, `ß`), Ziffern, Leerzeichen und
  `( ) * + - . / ? _`; das deckt alle vordefinierten Kennungen der OKSTRA-Versionen 1.007
  bis 2.023 ab. Eine Lieferung mit anderen Zeichen lehnt der Dienst ab, auch bei einem
  reinen Prüfauftrag (Status „abgelehnt“, Meldung „Unzulässige Kennung …“ im Prüfprotokoll).
* **Entladen:** `Auftrag` muss eine Liste ganzer Zahlen sein, `Gebiet` eine Kennung aus
  `Areas`.
* **OkWS:** Werte für Zahleigenschaften müssen Zahlen sein.
* **Format:** Lieferungen als CTE (`f1=CTE`, ohne `f1` eine Datei mit der Endung `.cte`)
  weist der Dienst ab („Lieferungen im Format CTE werden nicht unterstützt …“). CTE bildet die
  Geometrie mit Objekten des Geometrieschemas ab, die die OKLABI nur mit der Einstellung
  `ExpressGeometrie` liest; ohne sie gingen alle Geometrien verloren, mit ihr ändert sich
  prozessweit auch die Verarbeitung von OKSTRA-XML.

## 8. Bekannte Einschränkungen

Der Dienst behält in Version 2.0 sein bisheriges Verhalten bei, auch wo es fehlerhaft ist; 
Änderungen am Verhalten erfolgen über den normalen Weg. Bekannt sind:

**Schnittstelle**

* **Historisierung** findet keine Objekte und antwortet „Quelle in Datenbank nicht
  gefunden“ (bei `op` 1 bis 3 mit `zi` davor „Ziel in Datenbank nicht gefunden.“): Sie sucht
  die Kopfdaten im OKSTRA-Schema, sie liegen aber in `oks_meta`. Auch die Tabelle
  `_vonnach_`, mit der die Abfrage verknüpft, legt kein Skript in `Datenbank/` an.
* **Entladen nach Thema** (1 bis 9) wirkt nicht: Die Abfrage verlangt eine Spalte
  `kategorie` in `oks_meta.fbliste`, die es nicht gibt (der Fehler steht im Protokoll). Mit
  den Themen 1 bis 7 und 9 enthält die Datei keine Objekte; mit Thema 8 kommen `DGM`,
  `Dreieck` und `Bruchkante` des Auftrags, Projekts oder der Fläche ohne Auswahl nach
  Fachbedeutung mit.
* **Entladen in ein anderes Höhensystem** (`f23`) rechnet die Höhen nicht um: Die Datei
  trägt das gewünschte System, die Werte sind die der Datenbank. Beim Laden wird richtig
  umgerechnet.
* **Umlaute in Formularfeldern:** In Multipart-Lieferungen werden `ß`, `Ä`, `Ö`, `Ü` (etwa im
  Projektnamen) verstümmelt gespeichert, wenn der Client UTF-8 sendet; `ä`, `ö`, `ü` gehen.
  In Formularen (`application/x-www-form-urlencoded`, etwa `holeDatei` und Historisierung)
  werden bei UTF-8 alle Umlaute verstümmelt. Ein Client, der ISO-8859-1 sendet, ist nicht
  betroffen.
* **Asynchrones Entladen** wählt als Formular die Datenbank nicht aus: `database` wird erst
  nach der Auswahl der Datenbank übernommen, entladen wird immer aus dem Bestand (4.2).
* **Bundesland:** Fehlt `f8`, ist es leer oder unbekannt, nimmt der Dienst Sachsen-Anhalt statt
  des konfigurierten Landes; die Warnung im Protokoll nennt dabei das konfigurierte Land.
* **Löschauftrag** (`f20` ≠ `laden`, auch `Laden`): Die Markierung wird nur gespeichert; der
  Dienst lädt die Datei trotzdem, und der Auftrag erscheint nur in `FullQueue`, mit seinem
  Status und als Löschauftrag gekennzeichnet.
* **Fläche und Objekte ohne eigene Geometrie:** `Area` (und `Gebiet`) wirkt nur auf
  Objektarten mit Geometrie. Mit `Auftrag` oder `Projekt` zusammen kommen Objekte ohne eigene
  Geometrie (etwa Böschungen) vollständig mit, mit `Area` allein nur, soweit ein entladenes
  Objekt über eine Relation auf sie verweist.
* **`Queue` und `QueueCheck`** trennen Prüf- und Ladeaufträge über die letzte Stelle des
  Status. In `Queue` statt `QueueCheck` erscheinen daher abgelehnte Prüfaufträge (Status
  „abgelehnt“), Prüfaufträge, die warten oder noch laufen, und Prüfaufträge mit Fehlern aus
  der Warteschlange (siehe unten).
* **HTTP 500 statt einer Meldung** bei fehlendem `f7` oder `f22` und bei einem nicht
  numerischen `f23` in einer Lieferung, bei einem Fehler beim Eintragen in die
  Warteschlange, bei GET `Read`/`ReadLock` mit nicht schreibbarer `Version`, ungültigem
  `Thema` oder `Auftrag` oder einer `Area` mit weniger als 11 Zeichen, und bei Auftragslisten
  für nicht eingerichtete Datenbanken. Beim asynchronen Entladen (`holeDatei`) antwortet der
  Dienst in diesen Fällen mit Erfolg, und `erledigt.txt` lautet `failed`.
* **`SRS` und `HS`** werden beim Entladen geprüft, aber nicht angewandt (3.3). Im OkWS-Kanal
  wird `SRS` geprüft, aber nicht angewandt; die Antwort ist immer in EPSG:25832 und DHHN2016
  (Abschnitt 6).
* **OkWS:** `NE_` wirkt wie `EQ_`, auch mit mehreren Werten. Parameterwerte, die `=`
  enthalten, gehen im Kanal der OKLABI verloren.
* **`erledigt.txt` vor dem Abschluss:** Beim Prüfen und Laden steht dort schon `success`
  bzw. `failed`, während der Auftrag in der Auftragsliste noch „übertragen“ zeigt (etwa
  während die Datenfehler ausgegeben werden); abgeschlossen ist er erst, wenn die Liste den
  Endstatus zeigt.
* **Status nach Ablehnung wegen Lage- oder Höhensystem:** Der Auftrag endet auf −1 statt −5,
  erscheint daher als „gelöscht“ und nur in `FullQueue`, und `erledigt.txt` bleibt auf
  `working`.
* **Meldungen** mit HTML-Entitäten und nicht immer gültiges JSON (Abschnitt 1).
* **Status von Prüfaufträgen aus der Warteschlange:** Ein Prüfauftrag, der warten musste,
  weil gerade ein anderer Auftrag lief, oder der beim Start des Dienstes wieder aufgenommen
  wird, endet mit einem um eins zu hohen Status: fehlerfrei „geprüft, mit Meldungen“ (…06
  statt …05), mit Fehlern „geladen“ (…07 statt …06). Ursache: Die Warteschlange vermerkt einen
  Prüfauftrag mit Status 1, und diese 1 geht in den Endstatus ein; ein sofort gestarteter
  Prüfauftrag beginnt bei 0. Die Protokolle sind richtig.
* **Zusatz „Ladevorgang angestossen“:** Startet eine GET-Anfrage einen wartenden Auftrag,
  endet die Meldung von `GetThemes`, `GetTypes`, `Init`, `WakeUp`, `DeleteJob` und unbekannten
  Anfragen mit „, Ladevorgang angestossen“ (bei einem Fehler dabei „, Ausnahme beim Start
  eines neuen Ladevorgangs“). Ein Client, der die Themen von `GetThemes` am `|` trennt,
  erhält dann als letztes Thema „DOM, Ladevorgang angestossen“.
* **Planung in der Warteschlange:** Sind Altdaten eingerichtet, startet der Dienst wartende
  Aufträge für die Planung nicht von sich aus.
* **`okstraErrorexportpath`** muss gesetzt sein, sonst brechen die Auftragslisten ab, sobald
  sie einen Auftrag enthalten.
* **Stoppen der Webanwendung** beendet etwa zehn Sekunden später den ganzen Tomcat, mit dem
  Rückgabewert 0.

**OKLABI und Datenbankkomponente (`OkstraSqlUtil` 1.6.5.0)**

Diese Fehler liegen im Code der BASt. Die Nutzungsbestimmungen der BASt erlauben jedem
Lizenznehmer, OKLABI und `OkstraSqlUtil` zu ändern; eine veränderte Version darf aber nur
als unabtrennbarer Bestandteil einer Anwendung weitergegeben werden. Dieses Repository
enthält deshalb keine Korrekturen am Code der BASt und baut gegen das unveränderte Release.
Fehler sind daher direkt der Pflegestelle der OKLABI zu melden (https://www.okstra.de/oklabi).

* Mit dem unveränderten Release bricht die Übertragung beim ersten Objekt ab, das sich nicht
  in die Datenbank schreiben lässt (auch bei einem fehlenden Schlüsseltabelleneintrag). Die
  Übertragung arbeitet ohne Transaktion: Das bis dahin Übertragene bleibt in der Datenbank.
* Ein **erneutes Laden** bereits vorhandener Objekte (Wiederimport) scheitert bei Objektarten
  mit einer Eigenschaft mit Fachbedeutung, auch wenn sie leer ist: Die Änderungsanweisung
  ist ungültig. Korrekturen gehen über Löschen (`DeleteJob`) und neues Laden.
* Die Datenbankkomponente verarbeitet bestimmte Werte aus Lieferungen nicht zuverlässig; die
  Eingabeprüfung (Abschnitt 7) weist solche Lieferungen vorher ab.
