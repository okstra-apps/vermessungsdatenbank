<!--
SPDX-FileCopyrightText: 2026 interactive instruments GmbH
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Betrieb

Hinweise für den laufenden Betrieb: Überwachung, Aufträge und Protokolle, Beispiele für
Fehlerbilder, Aufräumen, Datensicherung, Update und die Pflege von Fachbedeutungslisten,
Gebieten und FB-Ausschlüssen. Installation: `doku/Installation.md`; Parameter:
`doku/Konfiguration.md`; Anfragen und Status: `doku/Schnittstelle.md`.

Beispiele für den Container verwenden `dc` für `docker compose -f Container/compose.yaml`
(Installation mit Datenbank im Container); gegen eine vorhandene Datenbank steht `dc` für
`docker compose -f Container/compose.extern.yaml`, und die Befehle mit dem Dienst
`datenbank` oder `einrichtung` gelten dort nicht: Abfragen, Sicherung und
Fachbedeutungslisten gehen direkt gegen die Datenbank wie beim eigenen Tomcat.
SQL-Beispiele laufen in der Datenbank der Vermessungsdatenbank, im Container etwa mit
`dc exec datenbank psql -U postgres -d vermdb`.

## 1. Überwachung

* **Erreichbarkeit:** `<URL>?request=GetThemes` muss `"success": true` liefern. Der Container
  prüft das selbst (Healthcheck, `dc ps` zeigt `healthy`).
* **Start:** Das Protokoll des Dienstes nennt beim Start die Version der
  Vermessungsdatenbank, Datenquellen, OKLABI-Version,
  FB-Liste und OkWS (`doku/Installation.md`, Abschnitt 5).
* **Warteschlange:** Ein Auftrag, der länger als üblich in Bearbeitung steht, deutet auf ein
  Fehlerbild aus Abschnitt 4. Abfrage:

      SELECT auftragsnummer, status, bearbeitung, erfasst
        FROM oks_meta.warteschlange
       WHERE beendet IS NULL ORDER BY auftragsnummer;

## 2. Aufträge

Die Auftragslisten zeigen den Stand: `<URL>?request=Queue` für Ladeaufträge, `QueueCheck` für
Prüfaufträge, `FullQueue` für alle einschließlich gelöschter (`doku/Schnittstelle.md`, 3.2
und 5). Lieferungen werden nacheinander abgearbeitet; jede GET-Anfrage stößt den nächsten
wartenden Auftrag an.

| Status | Was zu tun ist |
|---|---|
| geladen, geprüft | nichts |
| … mit Meldungen | Prüfprotokoll lesen (Link „Laden“); meist Hinweise der OKLABI zu Fachbedeutungen oder Schlüsselwerten |
| abgelehnt | Prüfprotokoll: Lage- oder Höhensystem passt nicht zur Lieferung, oder unzulässige Kennungen (`doku/Schnittstelle.md`, 7). Lieferung korrigieren und neu einstellen |
| Ladefehler | Ladeprotokoll (Link „Übertragung“). Ein Teil der Objekte kann schon in der Datenbank sein: mit `DeleteJob` entfernen, Lieferung korrigieren, neu einstellen |
| abgebrochen | Protokolle des Auftrags und des Dienstes; wie bei Ladefehler |
| bleibt in Bearbeitung | Abschnitt 4 |

Fehlerhafte Objekte eines Ladeauftrags stehen zusätzlich als eigene Datei unter
„Datenfehler“; sie lässt sich dem Lieferanten zurückgeben.

**Korrekturen** gehen über Löschen und neues Laden: `DeleteJob` entfernt die Objekte eines
Auftrags (`doku/Schnittstelle.md`, 3.5), danach die korrigierte Lieferung neu einstellen. Ein
erneutes Laden derselben Objekte ohne vorheriges Löschen (Wiederimport) scheitert bei
Objekten mit Fachbedeutung.

## 3. Protokolle

| Protokoll | Ort | Inhalt |
|---|---|---|
| Dienst | `Svc_<Zeit>.log` im Verzeichnis aus `okstraOklabilog`, je Start eine Datei | Start, Anfragen, Meldungen der OKLABI, Fehler |
| Aufträge | `<Auftrag>_laden_…log` (Prüfung), `<Auftrag>_transfer_…log` (Übertragung) ebenda | verlinkt in der Auftragsliste |
| Entladen asynchron | `<okstraReturnpath>/<SID>/OkstraUnload.log` | |
| Tomcat | `logs/` des Tomcat; im Container `dc logs vermdb` | Start und Abbruch des Servlets, HTTP 500 |

Im Container liegen die Protokolle der Vermessungsdatenbank im Datenverzeichnis auf dem
Rechner (`<VERMDB_DATEN>/Okstra2DB/log`, ohne Angabe `Container/daten/Okstra2DB/log`) und
lassen sich dort direkt lesen; ebenso im Container, etwa:

    dc exec vermdb sh -c "ls -lt /var/home/Daten/Okstra2DB/log | head"
    dc exec vermdb sh -c "ls -t /var/home/Daten/Okstra2DB/log/Svc_*.log | head -1 | xargs tail -n 50"

Unter Windows mit Git Bash `MSYS_NO_PATHCONV=1` voranstellen, wenn ein Argument mit `/`
beginnt (sonst wird daraus `C:/Program Files/Git/…`).

Zur Fehlersuche schreibt der Init-Parameter `verboseLogging` mehr Meldungen beim Einstellen
von Aufträgen; die Java-Option `-Doklabi.loader.verbose=true` zeigt, welche nativen
Bibliotheken die OKLABI lädt.

## 4. Beispiele für mögliche Fehlerbilder

Die Beispiele beruhen auf Störungen in bestehenden Installationen (zuerst), auf Tests und
auf dem Quelltext; die Liste ist nicht vollständig.

### Dienst startet nicht

Jede Anfrage endet mit HTTP 500; die Fehlerseite und das Protokoll des Tomcat nennen den
Grund:

| Meldung | Abhilfe |
|---|---|
| „Die nativen Bibliotheken der OKLABI konnten nicht geladen werden …“ | `java.library.path`, unter Linux `LD_LIBRARY_PATH`, unter Windows `PATH` des Dienstes (`doku/Installation.md`, 3 und 4) |
| „Die web.xml enthält nicht ersetzte Platzhalter …“ | Umgebungsvariable setzen oder Java-Option `PROPERTY_SOURCE` ergänzen (`doku/Konfiguration.md`, 6) |
| „OKSTRA-Datenquelle … ist nicht verfügbar“, „Datenquelle … ist nicht verfügbar“ | Datenbank erreichbar? Ressourcen in der `server.xml`, Treiber im `lib` des Tomcat (`doku/Konfiguration.md`, 3) |
| Fehler bei der FB-Ausschlussliste | Tabelle `oks_meta.fbausschluss` und Init-Parameter `svc_schema` prüfen |

### Lieferung gegen eine andere Fachbedeutungsliste geprüft

Die Auftragsliste zeigt in der Spalte „Fachbedeutungen“ eine andere Liste als angefragt,
etwa „1.6 alle“ statt „3.6 Sachsen-Anhalt“, und der Auftrag endet „mit Meldungen“; das
Prüfprotokoll meldet viele Fachbedeutungen, die „für kein Bundesland in der Liste …
gefunden“ wurden.

**Ursache:** Die Liste aus `f7`/`f8` (bzw. die Vorgabe `okstraFbliste`) gibt es in den
Ressourcen der OKLABI nicht. Der Dienst nimmt dann ohne Fehlermeldung die bundesweite Liste
1.6; im Protokoll des Dienstes steht nur „Ungültige Angabe für Fachbedeutungslistenversion,
benutze 1.6“. Fehlt die Version in `oks_meta.efbversion`, führen auch Auftrag und Projekt
die Liste 1.6. Viele Meldungen trotz passender Liste deuten dagegen auf ein falsches Land
in `f8`.

**Abhilfe:** Angabe im Client bzw. `okstraFbliste` prüfen. Fehlt die Liste, sie in die
Ressourcen der OKLABI legen und in die Datenbank laden (Abschnitt 8; im Container über
`fbliste/`, `Container/fbliste/README.md`). Danach den Auftrag neu einstellen, einen
Ladeauftrag vorher mit `DeleteJob` entfernen.

### Koordinaten werden nicht transformiert

Entladene Daten kommen unverändert im Lagesystem der Datenbank zurück, oder die Lage weicht um
einige Zentimeter von der amtlichen Transformation ab.

* Unverändert: `PROJ_LIB` fehlt oder die `proj.db` ist zu alt; im Protokoll „Cannot find
  proj.db“ bzw. „Transformation … wird nicht unterstützt“ (`doku/Installation.md`, 2.4).
* Zentimeter: Gitterdatei fehlt oder ist nicht eingetragen (im Container: das Gitter des
  Landes nicht mit `VERMDB_GITTER_IMAGE` eingeschaltet); im Protokoll fehlt „Gitterdatei:
  […]“ (ebenda).
* Unverändert, nur einzelne Lieferungen: Die Punkte liegen außerhalb des eingetragenen
  Gitters, etwa Daten eines Nachbarlandes; im Protokoll „Fehler bei Transformation des
  Punktes … Die Lage bleibt unverändert“ (ebenda).
* Höhen beim Entladen in ein anderes Höhensystem: bekannte Einschränkung
  (`doku/Schnittstelle.md`, 8).

### Auftrag oder Projekt ohne Gebiet

Ein geladener Auftrag erscheint in Anwendungen, die das Projektgebiet nutzen (etwa einem
Themenbrowser), nicht oder an falscher Stelle; `oks_meta.auftrag.geometrie` ist leer oder
falsch.

**Ursache:** Das Gebiet eines Auftrags berechnet die Datenbank, wenn der Auftrag
abgeschlossen wird (Trigger auf `oks_meta.warteschlange`, Funktion
`ermittle_projektgeometrie`), das Projektgebiet aus den Gebieten seiner Aufträge.
Scheitert die Berechnung oder fehlen beim Abschluss noch Daten, bleibt das Gebiet leer oder
veraltet.

**Abhilfe:** Die Berechnung erneut anstoßen, indem der Abschluss des Auftrags noch einmal
geschrieben wird (die Hinweise der Datenbank dabei sind normal):

    UPDATE oks_meta.warteschlange SET beendet = beendet WHERE auftragsnummer = <Nr>;

Scheitert das mit einer Fehlermeldung aus `ermittle_projektgeometrie`, liegt es an den
Geometrien des Auftrags; dann den Auftrag mit `DeleteJob` entfernen, die Lieferung prüfen
und neu einstellen.

### Dienst antwortet nicht, ohne Meldung

Anfragen bleiben ohne Antwort oder laufen in eine Zeitüberschreitung; weder das Protokoll
des Tomcat noch das des Dienstes nennt einen Fehler.

**Ursache:** Meist wartet eine Anfrage auf eine Sperre, die ein anderer Thread hält, etwa ein
Lader, der in der OKLABI oder in der Datenbank hängt (siehe unten „Alle Aufträge bleiben
‚wartet‘“), oder auf eine Datenbankverbindung.

**Abhilfe:** Vor dem Neustart festhalten, woran es hängt: einen Thread-Dump des Tomcat
ziehen und die letzten Zeilen des Protokolls des Dienstes sichern.

    jcmd <PID des Tomcat> Thread.print > threads.txt     # eigener Tomcat, als dessen Benutzer
    dc exec vermdb jcmd 1 Thread.print > threads.txt      # Container

Dazu in der Datenbank laufende und wartende Abfragen der Anwendung ansehen
(`SELECT pid, state, wait_event, query FROM pg_stat_activity WHERE usename = '<Rolle>';`).
Dann den Tomcat neu starten; ein hängender Auftrag bleibt in Bearbeitung (unten).

### Auftrag bleibt in Bearbeitung

Die Auftragsliste zeigt dauerhaft „laden, prüfen“, „migrieren“ oder „übertragen“.

**Ursache:** Der Tomcat wurde während des Auftrags beendet (Absturz, Neustart, Stoppen des
Dienstes). Der Dienst setzt solche Aufträge beim Start nicht zurück; sie bleiben als „in
Bearbeitung“ markiert (`bearbeitung = true`, `beendet` leer). Folgende Aufträge laufen
normal weiter.

**Abhilfe:** Zuerst prüfen, ob schon Objekte des Auftrags in der Datenbank sind:

    SELECT count(*) FROM oks_meta._header_ WHERE auftrag = <Nr>;

* Keine Objekte: Markierung zurücksetzen; der Dienst arbeitet den Auftrag mit der nächsten
  Anfrage (etwa `<URL>?request=WakeUp`) noch einmal ab. Die hochgeladene Datei muss noch
  vorhanden sein.

      UPDATE oks_meta.warteschlange SET bearbeitung = false
       WHERE auftragsnummer = <Nr> AND beendet IS NULL;

* Objekte vorhanden: `<URL>?request=DeleteJob&auftrag=<Nr>&datasource=Bestand` entfernt sie
  und markiert den Auftrag als gelöscht; danach die Lieferung neu einstellen.

### Alle Aufträge bleiben „wartet“

**Ursache:** Seit dem Start gab es keine GET-Anfrage, die die Warteschlange anstößt, oder der
Lader hängt (etwa bei einer sehr großen Lieferung) und hält die Sperre, die paralleles Laden
verhindert.

**Abhilfe:** `<URL>?request=WakeUp`. Bleibt es dabei und zeigt das Protokoll des Dienstes
keinen Fortschritt, den Tomcat neu starten; der hängende Auftrag bleibt dann in Bearbeitung
(siehe oben), die wartenden laufen an.

### Weitere

| Fehlerbild | Ursache und Abhilfe |
|---|---|
| Auftragsliste endet mit HTTP 500 | `okstraErrorexportpath` fehlt (bei Aufträgen mit Datenfehlern), oder `datasource=Altdaten`/`Planung` ohne eingerichtete Datenbank |
| Links der Auftragsliste führen ins Leere | `okstraServerPrefix` passt nicht zur Adresse, oder der Kontext `/vermdb` bzw. Webserver fehlt (`doku/Installation.md`, 2.6). „fehlt“ statt eines Links: Protokoll gelöscht |
| Lieferung mit HTTP 500 abgewiesen | `f7` oder `f22` fehlt |
| „Daten zur OKSTRA-Version … können nicht gelesen werden“ | Version nicht in `okstraVersionRead`; die Liste muss mit `\|` enden |
| Entladene Datei ohne Objekte | keine oder keine passende Bedingung (`Projekt`, `Auftrag`, `Gebiet`, `Area`); `Thema` wirkt nicht (`doku/Schnittstelle.md`, 3.3 und 8) |
| Projektname mit verstümmelten Zeichen | `ß`, `Ä`, `Ö`, `Ü` in Formularfeldern (bekannte Einschränkung) |
| Platte läuft voll | Der Dienst löscht keine Dateien (Abschnitt 5) |
| Tomcat beendet sich ganz | Stoppen oder neues Bereitstellen der Webanwendung beendet etwa zehn Sekunden später den ganzen Tomcat; den Tomcat danach neu starten |

## 5. Aufräumen

Der Dienst löscht keine Dateien, auch nicht beim Löschen eines Auftrags. Es sammeln sich an:

| Verzeichnis | Inhalt | Aufheben |
|---|---|---|
| `okstraUploadpath` | hochgeladene Lieferungen | bis der Auftrag beendet ist; danach nur, wenn Lieferungen nachvollziehbar bleiben sollen |
| `okstraReturnpath` | je Anfrage ein Verzeichnis `<SID>/`, beim asynchronen Entladen mit den Daten | bis der Client abgeholt hat, also einige Tage |
| `okstraOklabilog` | Protokolle des Dienstes und der Aufträge | so lange die Auftragsliste darauf verlinken soll; danach zeigt sie „fehlt“ |
| `okstraErrorexportpath` | Datenfehler der Ladeaufträge | wie die Protokolle |

Beispiele, die Rückgaben älter als 7 Tage und Uploads älter als 90 Tage löschen:

Linux:

    find /var/lib/vermdb/download -mindepth 1 -maxdepth 1 -type d -mtime +7 -exec rm -rf {} +
    find /var/lib/vermdb/upload -type f -mtime +90 -delete

Windows (PowerShell):

    Get-ChildItem C:\Vermessungsdatenbank\Daten\download -Directory |
      Where-Object LastWriteTime -lt (Get-Date).AddDays(-7) | Remove-Item -Recurse
    Get-ChildItem C:\Vermessungsdatenbank\Daten\upload -File |
      Where-Object LastWriteTime -lt (Get-Date).AddDays(-90) | Remove-Item

Container:

    dc exec vermdb find /var/home/Daten/Okstra2DB/download -mindepth 1 -maxdepth 1 -type d -mtime +7 -exec rm -rf {} +
    dc exec vermdb find /var/home/Daten/Okstra2DB/upload -type f -mtime +90 -delete

Uploads von Aufträgen, die noch nicht beendet sind, nicht löschen. Unter Linux setzt der
Dienst bei jedem Start die Rechte aller Datenverzeichnisse rekursiv; viele Dateien verlängern
den Start.

## 6. Datensicherung

Zu sichern sind:

* **die Datenbank**, ganz: `oks_meta` (Warteschlange, Aufträge, Kopfdaten, Listen) und das
  OKSTRA-Schema gehören zusammen;
* **die Datenverzeichnisse**, mindestens Protokolle und Datenfehler, wenn die Auftragsliste
  darauf verweisen soll;
* **die Konfiguration:** `web.xml`, `conf/server.xml`, Kontextdateien und `bin/setenv.sh` bzw.
  die Einstellungen des Windows-Dienstes; die Ressourcen der OKLABI (mit passender `proj.db`),
  Gitterdateien und `res/user.cfg`. Im Container: das Konfigurationsverzeichnis (`.env`,
  `gitter/`, `fbliste/`; ohne Angabe in `Container/`), das Datenverzeichnis
  (`VERMDB_DATEN`) und das Volume `kennwoerter`.

Die Datenbank im eigenen Format von PostgreSQL sichern, am besten, wenn kein Auftrag in
Bearbeitung ist:

    pg_dump -U postgres -Fc vermdb > vermdb.dump
    dc exec -T datenbank pg_dump -U postgres -Fc vermdb > vermdb.dump      # Container

Wiederherstellen (Dienst vorher stoppen; `-C` legt die Datenbank samt Einstellungen neu an,
auch den `search_path` der Rolle, die vorhanden sein muss):

    dropdb -U postgres vermdb
    pg_restore -U postgres -C -d postgres vermdb.dump

Im Container ebenso mit `dc stop vermdb`, `dc exec -T datenbank dropdb -U postgres vermdb`,
`dc exec -T datenbank pg_restore -U postgres -C -d postgres < vermdb.dump` und `dc start vermdb`.

## 7. Update

Vor einem Update die Datenbank sichern und abwarten, bis kein Auftrag mehr in Bearbeitung ist
(Abschnitt 1); sonst bleibt er nach dem Neustart hängen. Ablauf für Container und eigenen
Tomcat: `doku/Installation.md`, Abschnitt 6.

## 8. Fachbedeutungslisten, Gebiete, FB-Ausschlüsse

### Fachbedeutungslisten

Die Fachbedeutungslisten kommen mit den Ressourcen der OKLABI. Die OKLABI prüft Lieferungen
gegen ihre eigenen Listen (`fbliste/` im Ressourcenverzeichnis); die Datenbank führt sie
zusätzlich in `oks_meta.fbliste` und `oks_meta.efbversion`, für die Kopfdaten und für
Anwendungen, die die Datenbank lesen. Neue Listen, etwa aus einem neuen OKLABI-Release, daher
an beiden Stellen nachziehen: Ressourcen ersetzen und die Tabellen neu laden.

    python3 Datenbank/fbliste.py <Ressourcen.zip oder fbliste-Verzeichnis> -o fbliste.sql
    psql -U vermdb -d vermdb -v ON_ERROR_STOP=1 -f fbliste.sql

Das Skript lädt `fbliste` vollständig neu und ergänzt fehlende Versionen in `efbversion`;
vorhandene Kennungen bleiben, weil Kopfdaten und Datentabellen auf sie verweisen
(`Datenbank/README.md`).

**Im Container** geschieht beides beim Start des Dienstes: Er stellt die Listen des
OKLABI-Release im Image und die eigenen aus `fbliste/` des Konfigurationsverzeichnisses
zusammen und lädt sie in die Datenbank, wenn sich etwas geändert hat („Fachbedeutungslisten
laden“ bzw. „… sind aktuell“ im Protokoll, `dc logs vermdb`). Eine neue Liste, die im
Release fehlt, als CSV im Format der OKLABI nach `fbliste/` legen
(`Container/fbliste/README.md`) und `dc restart vermdb`. Erzwingen lässt sich das Laden mit
`dc run --rm vermdb /usr/local/bin/vermdb.sh fblisten`.

Soll eine neue Liste die Vorgabe werden, `okstraFbliste` anpassen (im Container
`VERMDB_FBLISTE`) und den Dienst neu starten.

### Gebiete

**Wozu:** Gebiete sind benannte Flächen, nach denen ein Client Daten entladen kann, ohne selbst
ein Polygon zu kennen, etwa Regionalbereiche oder Straßenmeistereien. `<URL>?request=Areas`
liefert die Liste („|1%Regionalbereich Nord|2%…“), ein Client bietet sie zur Auswahl an, und
`request=Read` mit `Gebiet=<Kennung>` entlädt die Objekte in dieser Fläche
(`doku/Schnittstelle.md`, 3.1 und 3.3). Eine neue Installation hat keine Gebiete.

**Pflege:** Eine Oberfläche dafür gibt es nicht; die Gebiete stehen in der Tabelle
`oks_meta.gebiete` (Kennung `oid`, `name`, Fläche `the_geom` im Lagesystem der Datenbank) und
werden dort mit SQL gepflegt, etwa mit psql als Rolle der Anwendung. Änderungen wirken
sofort.

    -- Gebiet anlegen (Fläche als WKT, hier in UTM 32)
    INSERT INTO oks_meta.gebiete (name, the_geom)
    VALUES ('Regionalbereich Nord', ST_GeomFromText('POLYGON((…))', 25832));
    -- umbenennen bzw. Fläche ersetzen
    UPDATE oks_meta.gebiete SET name = 'Regionalbereich Nordost' WHERE oid = <Kennung>;
    UPDATE oks_meta.gebiete SET the_geom = ST_GeomFromText('POLYGON((…))', 25832) WHERE oid = <Kennung>;
    -- entfernen
    DELETE FROM oks_meta.gebiete WHERE oid = <Kennung>;

Clients, die sich die Kennung eines Gebiets merken, finden es nach dem Entfernen nicht mehr.

### FB-Ausschlüsse

**Wozu:** Die Ausschlussliste nennt Fachbedeutungen (je Symbolart), die nicht in den Bestand
gehören, etwa reine Messpunkte. Ist `okstraFblisteExclusion` eingeschaltet (Vorgabe: aus,
`doku/Konfiguration.md`, 1.3; die mitgelieferte `web.xml` und der Container schalten ihn
ein), gilt beim Prüfen und Laden jedes Objekt mit einer gesperrten Fachbedeutung als
fehlerhaft:

* Das Prüfprotokoll meldet „Fachbedeutungskennung … ist explizit ausgeschlossen für
  Objektart …“, der Auftrag endet „geprüft, mit Meldungen“ bzw. „geladen, mit Meldungen“.
* Bei einem Ladeauftrag steht das Objekt in der Datei der Datenfehler. **Geladen wird es
  trotzdem**, wie andere fehlerhafte Objekte auch (`doku/Schnittstelle.md`, 2).
* Für das Gebiet von Auftrag und Projekt zählen Objekte mit gesperrten Fachbedeutungen
  nicht; das gilt auch, wenn `okstraFblisteExclusion` aus ist.

**Pflege:** Wie die Gebiete in der Tabelle `oks_meta.fbausschluss`, mit SQL. Wirksam sind nur
Zeilen mit `blocked = true`; `symbolart` ist `Punkt`, `Linie`, `Flaeche`, `Volumen` oder `Text`
(Punkt- bzw. Linienobjekte, Symbole, Beschriftungen …). Der Dienst liest die Liste beim
Start; nach einer Änderung neu starten (im Protokoll: „Fachbedeutungs-Ausschlussliste mit …
Einträgen geladen“).

    -- Fachbedeutung sperren
    INSERT INTO oks_meta.fbausschluss (fachbedeutung, bezeichnung, symbolart, blocked)
    VALUES ('0001.F001', 'Messpunkt tachymetrisch', 'Punkt', true);
    -- Sperre aufheben (bzw. die Zeile löschen)
    UPDATE oks_meta.fbausschluss SET blocked = false
     WHERE fachbedeutung = '0001.F001' AND symbolart = 'Punkt';

Ein Werkzeug zum Einlesen wie für die Fachbedeutungslisten gibt es nicht. Viele Einträge
lassen sich als CSV-Datei (UTF-8, Kopfzeile `fachbedeutung,bezeichnung,symbolart,blocked`)
mit `psql` übernehmen:

    \copy oks_meta.fbausschluss (fachbedeutung, bezeichnung, symbolart, blocked) FROM 'ausschluss.csv' WITH (FORMAT csv, HEADER)

Im Container liest `psql` die Datei über die Standardeingabe:

    dc exec -T datenbank psql -U postgres -d vermdb -c "\copy oks_meta.fbausschluss (fachbedeutung, bezeichnung, symbolart, blocked) FROM STDIN WITH (FORMAT csv, HEADER)" < ausschluss.csv

Vorhandene Zeilen bleiben dabei stehen; soll die Datei die Liste ersetzen, vorher
`DELETE FROM oks_meta.fbausschluss;`. Danach den Dienst neu starten.
