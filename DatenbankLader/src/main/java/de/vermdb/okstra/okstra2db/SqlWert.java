/*
 * SPDX-FileCopyrightText: 2026 interactive instruments GmbH
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package de.vermdb.okstra.okstra2db;

/**
 * Setzt Werte aus Anfragen und Dateien sicher in SQL-Anweisungen ein. Die Anweisungen
 * laufen über {@code DbVerbindung.PGDatenLesen/PGDatenAendern} aus OkstraSqlUtil, die nur
 * fertige Anweisungen annehmen; daher Literale statt Parameter.
 */
final class SqlWert {

    private SqlWert() {
    }

    /**
     * Text als Zeichenkettenkonstante mit Escape-Syntax ({@code E'…'}): Hochkomma und
     * Backslash werden verdoppelt. Das gilt unabhängig von
     * {@code standard_conforming_strings}. {@code null} wird wie bisher bei
     * {@code String.format("'%s'", …)} zum Text "null".
     * @param wert Text
     * @return SQL-Literal
     */
    static String text(String wert) {
        String s = String.valueOf(wert);
        if (s.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Nullzeichen in einem Textwert");
        }
        return "E'" + s.replace("\\", "\\\\").replace("'", "''") + "'";
    }

    /**
     * Ganze Zahl, auch mit Vorzeichen und Leerzeichen am Rand.
     * @param wert Text
     * @return Zahl als SQL-Text
     * @throws NumberFormatException wenn der Text keine ganze Zahl ist
     */
    static String ganzzahl(String wert) {
        return Long.toString(Long.parseLong(wert.trim()));
    }

    /**
     * Zahl (ganz oder mit Dezimalpunkt, auch mit Exponent).
     * @param wert Text
     * @return Zahl als SQL-Text
     * @throws NumberFormatException wenn der Text keine Zahl ist
     */
    static String zahl(String wert) {
        String s = wert.trim();
        double d = Double.parseDouble(s);
        if (Double.isNaN(d) || Double.isInfinite(d) || !s.matches("[+-]?[0-9.eE+-]+")) {
            throw new NumberFormatException("keine Zahl: " + wert);
        }
        return s;
    }

    /**
     * Durch Kommas getrennte Liste ganzer Zahlen, etwa "1, 2,3".
     * @param wert Text
     * @return Liste als SQL-Text, etwa "1, 2, 3"
     * @throws NumberFormatException wenn ein Element keine ganze Zahl ist
     */
    static String ganzzahlListe(String wert) {
        StringBuilder sb = new StringBuilder();
        for (String teil : wert.split(",", -1)) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(ganzzahl(teil));
        }
        return sb.toString();
    }
}
