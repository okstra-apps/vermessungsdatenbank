# SPDX-FileCopyrightText: 2026 interactive instruments GmbH
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Abfragen und Auftragslisten (doku/Schnittstelle.md, 3.1 und 3.2)
#
# GetThemes, GetTypes, Init, WakeUp und unbekannte Anfragen stoßen die Warteschlange an;
# wartet gerade ein Auftrag (etwa aus ablauf.hurl, das parallel läuft), endet die Meldung
# mit ", Ladevorgang angestossen". Geprüft wird deshalb der Anfang der Meldung.

GET {{url}}?request=GetThemes
HTTP 200
[Asserts]
header "Content-Type" contains "text/json"
jsonpath "$.success" == true
jsonpath "$.message" startsWith "Hydrographie|Kataster|Leitungen|Planung|Topographie|Verkehr|Verwahltungsgrenzen|DGM|DOM"

GET {{url}}?request=GetTypes
HTTP 200
[Asserts]
jsonpath "$.message" startsWith "1|2|3|4"

GET {{url}}?request=Init
HTTP 200
[Asserts]
jsonpath "$.message" startsWith "Service initialisiert"

GET {{url}}?request=WakeUp
HTTP 200
[Asserts]
jsonpath "$.message" startsWith "Service benachrichtigt"

# Gebiete aus oks_meta.gebiete; die Standardinstallation legt keine an
GET {{url}}?request=Areas
HTTP 200
[Asserts]
header "Content-Type" contains "text/plain"

GET {{url}}?request=Queue
HTTP 200
[Asserts]
header "Content-Type" contains "text/html"
xpath "normalize-space(//title)" startsWith "Auftragsliste (Import) VermDB Version"

GET {{url}}?request=Unbekannt
HTTP 200
[Asserts]
jsonpath "$.success" == false
jsonpath "$.message" startsWith "Request-Typ unbekannt."
