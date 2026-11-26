<!--
SPDX-FileCopyrightText: 2026 interactive instruments GmbH
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Sicherheit

## Sicherheitslücken melden

Bitte melden Sie vermutete Sicherheitslücken **vertraulich** und nicht als öffentliches
Issue, damit Betreiber ein Update einspielen können, bevor die Lücke bekannt wird.

Im Repository unter **Security** → **Report a vulnerability**, direkt <https://github.com/okstra-apps/vermessungsdatenbank/security/advisories/new>.
Die Meldung sehen nur Sie und die Pflegenden des Projekts; dafür ist ein Konto bei GitHub nötig.

Hilfreich sind:

* betroffene Version (Commit oder Release) und Installationsart (Container, eigener Tomcat);
* Beschreibung der Lücke und ihrer Auswirkung;
* Schritte oder eine Anfrage, mit der sie sich nachvollziehen lässt;
* gegebenenfalls ein Vorschlag zur Behebung.

Wir bestätigen den Eingang, prüfen die Meldung und stimmen das weitere Vorgehen mit Ihnen
ab: Behebung, Information der bekannten Betreiber und den Zeitpunkt der Veröffentlichung.
Bitte veröffentlichen Sie Einzelheiten erst nach dieser Abstimmung.

## Geltungsbereich

* **Dieses Repository:** das Servlet, die Skripte für Datenbank und Container und die
  Werkzeuge. Sicherheitskorrekturen gibt es für den aktuellen Stand des Hauptzweigs bzw. die
  jüngste Version.
* **OKLABI und `OkstraSqlUtil`** der Bundesanstalt für Straßen- und Verkehrswesen: Lücken
  darin bitte an deren Pflegestelle melden (https://www.okstra.de/oklabi).
* **Apache Tomcat, Java, PostgreSQL, PostGIS** und die Bibliotheken aus Maven Central: an das
  jeweilige Projekt. Ihre Versionen stehen in den Stücklisten (`bom.json` aus dem Bau,
  `Container/sbom.sh` für das Image).

## Hinweise für den Betrieb

* Der Dienst hat keinen eigenen Zugriffsschutz. Wer die Schnittstelle erreicht, kann
  Lieferungen einstellen, Daten entladen, Aufträge löschen und Protokolle abrufen. Ist der
  Dienst über den eigenen Rechner hinaus erreichbar, gehört ein Webserver mit Anmeldung
  davor (`doku/Installation.md`, Abschnitt 2.6).
* Updates zeitnah einspielen; die Eingabeprüfung des Dienstes beschreibt
  `doku/Schnittstelle.md`, Abschnitt 7.
* Zugangsdaten der Datenbank stehen in der `conf/server.xml` des Tomcat bzw. im Container in
  einem eigenen Volume, nicht in der Webanwendung (`doku/Konfiguration.md`, Abschnitt 3).
