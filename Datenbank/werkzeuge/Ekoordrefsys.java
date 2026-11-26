// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

import de.okstra.oklabi.OklabiLoader;
import de.okstra.oklabi.Umgebung;
import de.okstra.oklabi.eAufzaehlungen;

/**
 * Schreibt den Inhalt von oks_meta.ekoordrefsys als SQL: alle Lage- und Höhensysteme,
 * die die OKLABI kennt, mit ihrem Text. OkstraSqlUtil trägt diese Texte in die
 * Kopfdaten ein, der Trigger auf oks_meta._kopfdaten_ übersetzt sie über die Tabelle
 * zurück in den Code. Die Tabelle muss daher zur OKLABI-Version passen.
 *
 *   java --enable-native-access=ALL-UNNAMED -Djava.library.path=<native OKLABI> \
 *        -cp OKLABI.jar Ekoordrefsys.java > 31-ekoordrefsys.sql
 *
 * Codes ab 65519 sind Bausteine (Datum, Abbildung), keine Bezugssysteme.
 */
public class Ekoordrefsys {
    public static void main(String[] args) throws Exception {
        if (!OklabiLoader.load()) {
            throw new IllegalStateException("Native OKLABI nicht geladen: " + OklabiLoader.MESSAGE);
        }
        String version = Umgebung.GibOKLABIReleaseVersion().split(",")[0];
        StringBuilder sql = new StringBuilder();
        // REUSE-IgnoreStart
        sql.append("-- SPDX-FileCopyrightText: 2026 interactive instruments GmbH\n")
           .append("-- SPDX-License-Identifier: AGPL-3.0-or-later\n")
        // REUSE-IgnoreEnd
           .append("-- Erzeugt mit Datenbank/werkzeuge/Ekoordrefsys.java aus der OKLABI ").append(version)
           .append("; nicht von Hand ändern.\n")
           .append("INSERT INTO oks_meta.ekoordrefsys (code, wert) VALUES\n");
        String trenner = "";
        int anzahl = 0;
        for (int code = 0; code < 65519; code++) {
            String text = Umgebung.GibTextZuWert(eAufzaehlungen.tKoordRefSys, code);
            if (text == null || text.isEmpty() || text.equals("<unbekannt>")) {
                continue;
            }
            sql.append(trenner).append("    (").append(code).append(", '")
               .append(text.replace("'", "''")).append("')");
            trenner = ",\n";
            anzahl++;
        }
        sql.append(";\n");
        System.out.print(sql);
        System.err.println(anzahl + " Lage- und Höhensysteme aus OKLABI " + version);
    }
}
