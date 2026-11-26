// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.okstra2db;

import java.util.Locale;

/**
 * Format einer Lieferung (OKSTRA-XML oder CTE) und Eingabeprüfung dafür.
 *
 * <p>CTE wird abgewiesen: CTE bildet die Geometrie mit Objekten des Geometrieschemas ab,
 * die die OKLABI nur mit der Einstellung {@code ExpressGeometrie} liest. Ohne sie gingen
 * alle Geometrien verloren; die Einstellung gilt für den ganzen Prozess und verändert dann
 * auch gleichzeitige Arbeit mit OKSTRA-XML.
 */
final class Lieferformat {

    static final String XML = "XML";
    static final String CTE = "CTE";

    /** Meldung bei einer Lieferung als CTE. */
    static final String MELDUNG_CTE = "Lieferungen im Format CTE werden nicht unterstützt;"
            + " bitte als OKSTRA-XML liefern (f1=XML). Die Anfrage wird abgewiesen.";

    private Lieferformat() {
    }

    /**
     * Bestimmt das Format einer Lieferung.
     * @param f1 Angabe des Clients, {@code CTE} (Groß- und Kleinschreibung beliebig) oder
     *     ein anderer Wert für XML; leer oder {@code null}, wenn sie fehlt
     * @param dateiname Name der gelieferten Datei, für den Fall ohne Angabe
     * @return {@link #CTE} oder {@link #XML}; ohne Angabe {@code CTE} bei der Endung
     *     {@code .cte}, sonst {@code XML}
     */
    static String bestimme(String f1, String dateiname) {
        if (f1 != null && !f1.isBlank()) {
            return CTE.equalsIgnoreCase(f1.trim()) ? CTE : XML;
        }
        String name = dateiname == null ? "" : dateiname.toLowerCase(Locale.ROOT);
        return name.endsWith(".cte") ? CTE : XML;
    }

    /**
     * Eingabeprüfung des Formats.
     * @return Meldung, wenn die Lieferung abzuweisen ist, sonst leer
     */
    static String pruefe(String f1, String dateiname) {
        return CTE.equals(bestimme(f1, dateiname)) ? MELDUNG_CTE : "";
    }
}
