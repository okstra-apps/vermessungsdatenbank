<!--
SPDX-FileCopyrightText: 2026 interactive instruments GmbH
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# OkWS

`beispiel-wfs.xml` ist die Vorgabeantwort des OkWS-Backends auf das Lebenszeichen des
OKSTRA-Webdienstes (Anfrage mit `TEST`, Operation `SignOfLife`). Der Dienst liefert sie
unverändert aus der Datei, die der Init-Parameter `okstraWSresponse` nennt
(`doku/Konfiguration.md`); alle anderen Anfragen des OkWS gehen an die Datenbank. Nötig ist
sie nur, wenn das OkWS-Backend eingeschaltet ist (`okstraWSbackend`); fehlt sie, meldet der
Dienst das beim Start und beantwortet das Lebenszeichen mit einem `ExceptionReport`.

* Container: Das Image übernimmt die Datei nach `/opt/okws/`.
* Eigener Tomcat: die Datei an den Ort aus `okstraWSresponse` kopieren (Vorlage der
  `web.xml`: `C:/Vermessungsdatenbank/okws/beispiel-wfs.xml`).

Die Datei ist `testdaten/beispiel.xml` im WFS-Format (UTF-8), erzeugt mit
`testdaten/werkzeuge/NachWfs.java` (`testdaten/README.md`). Lizenz wie der
Beispieldatensatz: Datenlizenz Deutschland – Namensnennung – Version 2.0,
Landesstraßenbaubehörde Sachsen-Anhalt.
