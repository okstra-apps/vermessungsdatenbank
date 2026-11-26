# SPDX-FileCopyrightText: 2026 interactive instruments GmbH
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Ablauf mit testdaten/beispiel.xml (Gauß-Krüger 4 mit DHHN2016, FB-Liste Sachsen-Anhalt 3.5):
# prüfen, laden, entladen (synchron, komprimiert, nach Fläche, asynchron), löschen
# (doku/Schnittstelle.md, 3 und 4). Die SID ist je Lauf neu.

# Prüfauftrag: ändert die Datenbank nicht
POST {{url}}
[Options]
variable: sid=b{{newUuid}}
[Multipart]
nurPruefen: 1
SID: {{sid}}-p
PRJNM: Beispiel
username: beispiel
f1: XML
f2: beispiel.xml
f4: 11.11.2026
f7: 3.5
f8: Sachsen-Anhalt
f20: laden
f22: 2398
f23: 170
datei: file,testdaten/beispiel.xml; text/xml
HTTP 200
[Captures]
pruefauftrag: jsonpath "$.auftragsnummer"
[Asserts]
jsonpath "$.success" == true

# Warten, bis der Prüfauftrag in der Liste der Prüfaufträge als geprüft erscheint
GET {{url}}?request=QueueCheck
[Options]
retry: 60
retry-interval: 2000
HTTP 200
[Asserts]
xpath "normalize-space(//tr[td[1]='{{pruefauftrag}}']/td[7])" == "geprüft"

# Ladeauftrag
POST {{url}}
[Multipart]
pruefeDatei: 1
SID: {{sid}}-l
PRJNM: Beispiel
username: beispiel
f1: XML
f2: beispiel.xml
f4: 11.11.2026
f7: 3.5
f8: Sachsen-Anhalt
f20: laden
f22: 2398
f23: 170
datei: file,testdaten/beispiel.xml; text/xml
HTTP 200
[Captures]
auftrag: jsonpath "$.auftragsnummer"
[Asserts]
jsonpath "$.success" == true

GET {{url}}?request=Queue
[Options]
retry: 60
retry-interval: 2000
HTTP 200
[Asserts]
xpath "normalize-space(//tr[td[1]='{{auftrag}}']/td[7])" == "geladen"

# Entladen in UTM 33 (Lagesystem der Datenbank: UTM 32)
GET {{url}}
[Query]
request: Read
SID: {{sid}}-r
Auftrag: {{auftrag}}
f22: 25833
HTTP 200
[Asserts]
# Zeichensatz je nach Plattform: utf-8 (Linux, Container) oder Windows-1252 (Windows)
header "Content-Type" matches /^text\/xml;\s*charset=(utf-8|Windows-1252)$/
xpath "string(//*[local-name()='Envelope']/@srsName)" == "urn:adv:crs:ETRS89_UTM33"
xpath "count(/*/*[local-name()='featureMember'])" == 77
xpath "string(//*[local-name()='lowerCorner'])" startsWith "27474"

# Nach Projekt, gzip-komprimiert
GET {{url}}
[Query]
request: Read
SID: {{sid}}-g
Projekt: Beispiel
gzip: 1
HTTP 200
[Asserts]
header "Content-Type" contains "application/x-gzip"
bytes startsWith hex,1f8b;

# Nach Fläche (EWKT in UTM 32), hier zusätzlich auf den Auftrag beschränkt: 2 Punkte und
# 1 Linie in der Fläche, dazu alle 7 Böschungen des Auftrags (Objektarten ohne eigene
# Geometrie prüft die Fläche nicht, doku/Schnittstelle.md, 8)
GET {{url}}
[Query]
request: Read
SID: {{sid}}-f
Auftrag: {{auftrag}}
Area: SRID=25832;POLYGON((688500 5740900,688600 5740900,688600 5741000,688500 5741000,688500 5740900))
HTTP 200
[Asserts]
xpath "count(/*/*[local-name()='featureMember'])" == 10

# Asynchron: Auftrag erteilen, auf erledigt.txt warten, Datei abholen
POST {{url}}
[Form]
holeDatei: 1
SID: {{sid}}-a
Auftrag: {{auftrag}}
HTTP 200
[Asserts]
jsonpath "$.message" == "Download-Auftrag erhalten."

GET {{vermdb}}/download/{{sid}}-a/erledigt.txt
[Options]
retry: 60
retry-interval: 2000
HTTP 200
[Asserts]
body startsWith "success"

# Die Datei kommt als application/xml ohne charset; sie ist je nach Plattform in UTF-8 oder
# Windows-1252. ISO-8859-1 dekodiert jedes Byte, die geprüften Werte sind ASCII. Der
# xpath-Filter liest den Text als HTML: Namen klein, mit Präfix, Wurzel unter html/body.
GET {{vermdb}}/download/{{sid}}-a/OkstraUnload.xml
HTTP 200
[Asserts]
bytes charsetDecode "iso-8859-1" xpath "string(//*[local-name()='gml:envelope']/@srsname)" == "urn:adv:crs:ETRS89_UTM32"
bytes charsetDecode "iso-8859-1" xpath "count(//body/*[1]/*[local-name()='gml:featuremember'])" == 77

# Daten des Ladeauftrags löschen
GET {{url}}
[Query]
request: DeleteJob
auftrag: {{auftrag}}
datasource: Bestand
HTTP 200
[Asserts]
jsonpath "$.success" == true
jsonpath "$.message" == "Auftragslöschung:  Auftrag {{auftrag}} entfernt, 12 Datentabellen betroffen."

GET {{url}}?request=Queue
HTTP 200
[Asserts]
xpath "count(//tr[td[1]='{{auftrag}}'])" == 0
