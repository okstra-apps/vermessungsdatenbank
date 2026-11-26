<!--
SPDX-FileCopyrightText: 2026 interactive instruments GmbH
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Eigene Fachbedeutungslisten für den Container

Das Image bringt die Fachbedeutungslisten des OKLABI-Release mit. Listen, die dort fehlen
(oder eine geänderte Fassung einer Liste des Release), gehören in dieses Verzeichnis bzw. in
`fbliste/` des Konfigurationsverzeichnisses (`VERMDB_KONFIG`; anderer Ort: `VERMDB_FBLISTEN`).
Compose hängt es nur lesend als `/opt/vermdb/fbliste` ein. Die Dateien gehören nicht ins
Repository (ausgenommen in `Container/.gitignore`).

Format wie die Listen des Release: CSV-Dateien im Format der OKLABI (Windows-1252, `;`),
Name `fb-<Land>-<Haupt><Unter>.csv`, etwa `fb-Sachsen-Anhalt-36.csv` für die Liste 3.6.
Eine Datei mit dem Namen einer Liste des Release ersetzt diese.

Beim Start (`vermdb.sh start`) geschieht Folgendes:

1. Die Listen des Release und die eigenen werden zu den Listen zusammengestellt, gegen die
   die OKLABI Lieferungen prüft; das Protokoll des Containers nennt jede eigene Liste
   („Eigene Fachbedeutungsliste: …“ bzw. „… ersetzt die des Release: …“).
2. Weicht der Stand in der Datenbank (`oks_meta.fbliste`, `oks_meta.efbversion`) davon ab,
   lädt der Dienst die Listen neu („Fachbedeutungslisten laden“); sonst „Fachbedeutungslisten
   der Datenbank sind aktuell“.

Nach dem Ablegen, Ändern oder Entfernen einer Liste genügt also ein Neustart:

    docker compose -f Container/compose.yaml restart vermdb

Eine entfernte Liste verschwindet aus `oks_meta.fbliste`; ihre Version bleibt in
`oks_meta.efbversion` stehen, weil Kopfdaten geladener Aufträge auf sie verweisen können.

Soll eine eigene Liste die Vorgabe werden, `VERMDB_FBLISTE` in der `.env` setzen.
