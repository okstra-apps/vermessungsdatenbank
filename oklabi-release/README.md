<!--
SPDX-FileCopyrightText: 2026 interactive instruments GmbH
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# OKLABI-Release

Die Vermessungsdatenbank nutzt die OKSTRA-Klassenbibliothek (OKLABI) und ihre
Datenbankanbindung `OkstraSqlUtil` aus dem Release 1.6.5.0 der Bundesanstalt für Straßen-
und Verkehrswesen. Beide stehen unter den Nutzungsbestimmungen der BASt
(https://www.okstra.de/oklabi/Lizenz/nutzung.txt) und gehören nicht zu diesem Repository.
Gebraucht werden Dateien aus dem Release und die Nutzungsbestimmungen. Der Bau
(`gradlew build`) und `werkzeuge/oklabi.py` (eigener Tomcat, `doku/Installation.md`,
Abschnitt 2.4) nehmen sie aus diesem Ordner; fehlen sie hier, laden sie sie von okstra.de
und legen sie hier ab.

| Datei | gebraucht von | Adresse | SHA-256 |
|---|---|---|---|
| `OKLABI-java11-1-6-5-0.jar.zip` | Bau | https://www.okstra.de/oklabi/Release-20260629/Oklabi/Bibliothek/Java/OKLABI-java11-1-6-5-0.jar.zip | `136328d9a78f080e9c2fe6d773a58dbe4d60854edd411990c3dedb678844d67c` |
| `OkstraSqlUtil-java11-1-6-5-0.jar.zip` | Bau | https://www.okstra.de/oklabi/Release-20260629/Oklabi/Bibliothek/Java/OkstraSqlUtil-java11-1-6-5-0.jar.zip | `965bf2be859299e455f1d7e0f75a78096bd3764beb581cc04844b377bd8b2db9` |
| `nutzung.txt` | Bau | https://www.okstra.de/oklabi/Lizenz/nutzung.txt | `03eb11b3797cbce3972ae28120e9c096e138f2c54c70f68882c59a71041c7f0e` |
| `Ressourcen.zip` | `oklabi.py`, `Datenbank/einrichten.py` | https://www.okstra.de/oklabi/Release-20260629/Oklabi/Ressourcen/Ressourcen.zip | `c2fec8e0dfd61578c6aa2259c5f85b31741f8f02f27f5760c5968fbdd2c0c669` |
| `VS2022-Lib64-1-6-5-0.zip` | `oklabi.py` unter Windows | https://www.okstra.de/oklabi/Release-20260629/Oklabi/Bibliothek/Windows/VS2022-Lib64-1-6-5-0.zip | `84b54f1691da5fb7165155442f44eb327265795c6b442c419b09523692c08c49` |
| `ubuntu-2404-1-6-5-0.tbz` | `oklabi.py` unter Linux | https://www.okstra.de/oklabi/Release-20260629/Oklabi/Bibliothek/Linux/ubuntu-2404-1-6-5-0.tbz | `460458f2619f440862b9a352528bc8bbb58496f5d27fc01e0bc3ee638bd8c15a` |

## Ohne Zugang zu okstra.de

Ist okstra.de nicht erreichbar, brechen Bau und `oklabi.py` mit einer Meldung ab, die diesen
Ordner nennt. Dann die Dateien anderswo beschaffen, etwa von jemandem, der sie schon geladen
hat (die ausgepackten Dateien einer bestehenden Installation genügen nicht), und von Hand
hierher legen:

* gebraucht werden die drei Dateien für den Bau, für einen eigenen Tomcat dazu
  `Ressourcen.zip` und die native Bibliothek der eigenen Plattform (nicht beide);
* abgelegt wird jede Datei **unverändert und nicht ausgepackt**, unter genau dem Namen aus
  der Tabelle, direkt in diesem Ordner, also `oklabi-release/` im Repository (im Beispiel der
  Installationsanleitung `C:\Vermessungsdatenbank\Quellen\oklabi-release\`);
* die Prüfsumme lässt sich vorher selbst prüfen, unter Windows mit
  `certutil -hashfile <Datei> SHA256`, unter Linux mit `sha256sum <Datei>`. Weicht sie ab,
  brechen Bau bzw. Skript mit einer Meldung ab; die Datei ist dann eine andere als die des
  Release.

Danach den Bau bzw. `oklabi.py` erneut aufrufen.

Die BASt hat `Ressourcen.zip` am 03.11.2026 im selben Release ausgetauscht; geändert ist nur
`gdal_data/proj.db`, die jetzt zur Bibliothek PROJ 9.8.1 passt. Eine früher geladene Datei hat
eine andere Prüfsumme und wird abgewiesen; dann die aktuelle laden. Ebenso hat sie die
Nutzungsbestimmungen am 13.11.2026 in `nutzung.txt` umbenannt und auf UTF-8 umgestellt, bei
unverändertem Wortlaut.

## Prüfung und Verwendung

Der Bau prüft die Archive gegen diese Prüfsummen, bevor er sie auspackt; ein geladenes
Archiv mit falscher Prüfsumme verwirft er. Namen und
Prüfsummen stehen in `gradle.properties`; andere Archive lassen sich etwa mit
`-PoklabiArchiv=... -PoklabiPruefsumme=...` angeben.

* `OKLABI.jar`: Übersetzt wird gegen das Jar des Release (ohne die darin enthaltenen
  Quellen). Ins WAR kommt es nicht; das unveränderte Jar landet in
  `DatenbankLader/build/tomcat-lib/` für das `lib`-Verzeichnis des Tomcat, wo es zusammen
  mit der nativen OKLABI liegt.
* `OkstraSqlUtil.jar`: unverändert im WAR.
* `nutzung.txt`: unverändert als `OKLABI-Nutzungsbestimmungen.txt` im WAR
  unter `META-INF/`, in `DatenbankLader/build/lizenzen/` und im Image unter
  `/opt/vermdb/LICENSES/`; ändert die BASt den Text, scheitert die Prüfsumme, und er ist
  durchzusehen.

`OkstraSqlUtil` 1.6.5.0 bricht das Laden eines Auftrags beim ersten Fachobjekt ab, das sich
nicht in die Datenbank schreiben lässt; das bis dahin Übertragene bleibt in der Datenbank.
