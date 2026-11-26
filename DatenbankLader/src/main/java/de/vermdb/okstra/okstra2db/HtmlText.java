// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.okstra2db;

/**
 * Text für HTML-Ausgaben des Dienstes (Auftragsliste, Meldungen).
 */
final class HtmlText {

    private HtmlText() {
    }

    /**
     * Ersetzt deutsche Umlaute und ß durch HTML-Entitäten; sonst unverändert.
     * @param str Text
     * @return Text mit Entitäten
     */
    static String umlaute(String str) {
        return str.replace("ß", "&szlig;")
                .replace("ä", "&auml;")
                .replace("ö", "&ouml;")
                .replace("ü", "&uuml;")
                .replace("Ä", "&Auml;")
                .replace("Ö", "&Ouml;")
                .replace("Ü", "&Uuml;");
    }

    /**
     * Maskiert Text für HTML-Inhalt und Attributwerte; Umlaute wie bisher als Entitäten.
     * @param str Text, {@code null} wird wie bisher zu "null"
     * @return maskierter Text
     */
    static String maskiere(String str) {
        return umlaute(String.valueOf(str).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"));
    }
}
