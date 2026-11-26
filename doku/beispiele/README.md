<!--
SPDX-FileCopyrightText: 2026 interactive instruments GmbH
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Beispiele für die Schnittstelle

Anfragen an die Schnittstelle (`doku/Schnittstelle.md`) als [Hurl](https://hurl.dev)-Dateien,
mit den tatsächlichen Antworten als Prüfungen. Sie arbeiten mit den Beispieldaten aus
`testdaten/` und lassen sich gegen jede Installation ausführen; die Adressen stehen in
einer Variablendatei, für die Standardinstallation (`Container/compose.yaml`) in
`container.env`:

    hurl --test --jobs 1 --variables-file doku/beispiele/container.env --file-root . doku/beispiele/*.hurl

(im Wurzelverzeichnis des Repositorys; Hurl ab 6.0). `--jobs 1` führt die Dateien
nacheinander aus: Gleichzeitige Prüfaufträge enden sonst teils mit einem falschen Status
(`doku/Schnittstelle.md`, Abschnitt 8). Schlägt eine Prüfung fehl, zeigt
`--error-format long` die vollständige Antwort; `--report-html <Verzeichnis>` hält alle
Anfragen und Antworten eines Laufs fest. Für eine andere Installation `url`
(Adresse des Servlets) und `vermdb` (`okstraServerPrefix` ohne abschließenden Schrägstrich)
mit `--variable` angeben.

| Datei | Inhalt |
|---|---|
| `abfragen.hurl` | `GetThemes`, `GetTypes`, `Init`, `WakeUp`, `Areas`, Auftragsliste, unbekannte Anfrage |
| `ablauf.hurl` | `beispiel.xml` prüfen und laden, entladen in UTM 33, gzip-komprimiert, nach Fläche und asynchron, den Auftrag löschen |
| `fehler.hurl` | abgewiesene Anfragen (SID, fehlende Datei, Lagesystem) und eine abgelehnte Lieferung (`fehler-kennung.xml`) |

`ablauf.hurl` und `fehler.hurl` legen Aufträge an. Der Ladeauftrag wird am Ende wieder
gelöscht; Prüfaufträge und abgelehnte Lieferungen bleiben in der Warteschlange stehen, wie
bei jedem Client. Eine Produktivinstallation ist daher kein guter Ort für die Beispiele.
Jeder Lauf nimmt eine neue SID.

Die Integrationstests (`./gradlew integrationTest`, `CONTRIBUTING.md`) führen die Beispiele
mit aus, mit Hurl als Container; dafür muss Hurl nicht installiert sein.

Die Beispiele zeigen das Verhalten der Version, mit der sie geprüft wurden, auch dort, wo
es fehlerhaft ist (`doku/Schnittstelle.md`, Abschnitt 8).
