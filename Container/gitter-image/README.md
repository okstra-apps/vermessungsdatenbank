<!--
SPDX-FileCopyrightText: 2026 interactive instruments GmbH
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Gitterdateien des Image

Das Image bringt diese Gitterdateien mit. Eingeschaltet wird ein Gitter mit seinem Namen in
`VERMDB_GITTER_IMAGE` (`Container/README.md`, „Gitterdateien“); ohne Angabe rechnet die
OKLABI ohne sie. Die Einträge für die OKLABI stehen je Gitter in `<Name>.cfg`.

| Name | Systeme | Ausdehnung | Herausgeber und Bedingungen |
|---|---|---|---|
| `NTv2LSBB_LSA` | 42/83 Gauß-Krüger 4 (EPSG 2398) ↔ ETRS89/UTM 32 (25832) und UTM 33 (25833), Sachsen-Anhalt | 50,9–53,1° N, 10,5–13,33° O | Landesstraßenbaubehörde Sachsen-Anhalt, dl-de/by-2-0 |
| `HeTa2010` | DHDN Gauß-Krüger 3 (31467) ↔ ETRS89/UTM 32 (25832), Hessen | 49,33–51,87° N, 7,5–10,5° O | Hessische Verwaltung für Bodenmanagement und Geoinformation (HVBG), dl-de/by-2-0 |

Beide stehen unter der Datenlizenz Deutschland – Namensnennung – Version 2.0
(https://www.govdata.de/dl-de/by-2-0). Quellvermerke:

* `NTv2LSBB_LSA.gsb`: Landesstraßenbaubehörde Sachsen-Anhalt, dl-de/by-2-0
  (https://www.govdata.de/dl-de/by-2-0). Unverändert die `.gsb` aus
  `141013_NTv2_LSBB_LSA.zip` von https://lsbb.sachsen-anhalt.de/service/vermessung/
  (Abschnitt „Transformation“; dort auch die `.gsa` und eine Karte des Gültigkeitsbereichs),
  SHA-256 `966bdf736a3d9feca370e978212677e7beb9f082d7a068f0062d014752f056d4`.
* `HeTa2010.gsb`: Hessische Verwaltung für Bodenmanagement und Geoinformation (HVBG),
  dl-de/by-2-0 (https://www.govdata.de/dl-de/by-2-0). Die Datei aus den Ressourcen des
  OKLABI-Release 1.6.5.0, SHA-256
  `3ba3d8d54ec8b8532e77e03604c4b88a00b6ff129eea487ea7665b88e98bcd98`; beim Herausgeber
  kostenfrei über „Geodaten online“, als GeoTIFF auch über das CDN von PROJ.

Ein Gitter gilt nur in seiner Ausdehnung: Punkte außerhalb lässt die OKLABI unverändert
(`doku/Installation.md`, Abschnitt 2.4). Deshalb ist keines fest eingeschaltet; eine
Installation schaltet nur das Gitter ihres Landes ein.

Im Image liegen die Dateien unter `/opt/OKLABI_HOME/ntv2-image/`. Beim Start stellt
`vermdb.sh` daraus und aus den eigenen Gittern (`Container/gitter/README.md`) das Verzeichnis
`ntv2/` mit seiner `gitter.cfg` zusammen.
