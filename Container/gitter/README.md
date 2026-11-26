<!--
SPDX-FileCopyrightText: 2026 interactive instruments GmbH
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Eigene Gitterdateien für den Container

Das Image bringt die Gitter `NTv2LSBB_LSA` (Sachsen-Anhalt) und `HeTa2010` (Hessen) mit;
eingeschaltet werden sie mit `VERMDB_GITTER_IMAGE` in der `.env` (`Container/README.md`,
„Gitterdateien“). Dieses Verzeichnis (bzw. `gitter/` im Konfigurationsverzeichnis
`VERMDB_KONFIG`; anderer Ort: `VERMDB_GITTER`) nimmt weitere Gitterdateien auf und eine
Datei `gitter.cfg` mit ihren Einträgen. Compose hängt es nur lesend als `/opt/vermdb/gitter`
in den Container; beim Start ergänzen die Dateien und Einträge die des Image (eine Datei
gleichen Namens oder ein Eintrag für dasselbe Paar von Systemen ersetzt den des Image). Die
Dateien gehören nicht ins Repository (ausgenommen in `Container/.gitignore`); für sie
gelten die Bedingungen ihrer Herausgeber. Bezug und Umwandlung: `doku/Installation.md`,
Abschnitt 2.4.

Beispiel `gitter.cfg` für HOETRA2016:

    Gitterdatei=urn:adv:crs:DE_DHHN92_NH@urn:adv:crs:DE_DHHN2016_NH@Hoetra2016.gtx

Nach einer Änderung:

    docker compose -f Container/compose.yaml restart vermdb
