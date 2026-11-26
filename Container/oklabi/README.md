<!--
SPDX-FileCopyrightText: 2026 interactive instruments GmbH
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# OKLABI für das Image

Der Image-Bau lädt die Dateien des OKLABI-Release 1.6.5.0
(https://www.okstra.de/oklabi/Release-20260629) selbst und prüft sie gegen
`Container/bau/oklabi.sha256`. Ohne Zugang zu okstra.de (oder um den Download zu sparen)
können sie hier abgelegt werden; sie sind von der Versionsverwaltung ausgenommen. Dasselbe
gilt für die beiden Java-Archive in `oklabi-release/`.

| Datei | Ort im Release |
|---|---|
| `ubuntu-2404-1-6-5-0.tbz` | `Oklabi/Bibliothek/Linux/` |
| `Ressourcen.zip` | `Oklabi/Ressourcen/` |
| `OKLABI-java11-1-6-5-0.jar.zip` | `Oklabi/Bibliothek/Java/` (oder in `oklabi-release/`) |
| `OkstraSqlUtil-java11-1-6-5-0.jar.zip` | `Oklabi/Bibliothek/Java/` (oder in `oklabi-release/`) |
