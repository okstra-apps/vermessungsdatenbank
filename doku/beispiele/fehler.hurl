# SPDX-FileCopyrightText: 2026 interactive instruments GmbH
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Abgewiesene Anfragen und eine abgelehnte Lieferung (doku/Schnittstelle.md, 3, 4 und 7).
# Meldungen kommen in ISO-8859-1 und mit HTML-Entitäten; Vergleiche daher nur bis zum
# ersten Umlaut.

# Unzulässige Zeichen in der SID
POST {{url}}
[Multipart]
nurPruefen: 1
SID: ../beispiel
f1: XML
f7: 3.5
f8: Sachsen-Anhalt
f20: laden
f22: 2398
datei: file,testdaten/beispiel.xml; text/xml
HTTP 200
[Asserts]
jsonpath "$.success" == false
jsonpath "$.message" contains "Session-ID enth&auml;lt unzul&auml;ssige Zeichen"

# Lieferung ohne Datei
POST {{url}}
[Options]
variable: sid=f{{newUuid}}
[Multipart]
nurPruefen: 1
SID: {{sid}}-o
f1: XML
f7: 3.5
f22: 2398
HTTP 200
[Asserts]
jsonpath "$.success" == false
jsonpath "$.message" contains "Es wurden keine Daten hochgeladen."

# Entladen ohne SID
GET {{url}}?request=Read&Auftrag=1
HTTP 200
[Asserts]
jsonpath "$.success" == false
jsonpath "$.message" == "Session-ID ist nicht belegt."

# Entladen in ein nicht unterstütztes Lagesystem
GET {{url}}?request=Read&SID={{sid}}-k&Auftrag=1&f22=4711
HTTP 200
[Asserts]
jsonpath "$.success" == false
jsonpath "$.message" startsWith "Koordinatenreferenzsystem wird nicht unterst"

# Löschen ohne Datenbankauswahl: success true, Ergebnis in der Meldung
GET {{url}}?request=DeleteJob&auftrag=1
HTTP 200
[Asserts]
jsonpath "$.success" == true
jsonpath "$.message" contains "nicht m"

# Lieferung mit unzulässiger Kennung (testdaten/fehler-kennung.xml): angenommen, dann
# abgelehnt, auch als Prüfauftrag
POST {{url}}
[Multipart]
nurPruefen: 1
SID: {{sid}}-p
f1: XML
f2: fehler-kennung.xml
f4: 11.11.2026
f7: 3.5
f8: Sachsen-Anhalt
f20: laden
f22: 2398
f23: 170
datei: file,testdaten/fehler-kennung.xml; text/xml
HTTP 200
[Captures]
auftrag: jsonpath "$.auftragsnummer"
[Asserts]
jsonpath "$.success" == true

GET {{url}}?request=FullQueue
[Options]
retry: 60
retry-interval: 2000
HTTP 200
[Asserts]
xpath "normalize-space(//tr[td[1]='{{auftrag}}']/td[7])" == "abgelehnt"
