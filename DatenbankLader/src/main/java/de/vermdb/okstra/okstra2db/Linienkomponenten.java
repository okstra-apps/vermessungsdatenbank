// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.okstra2db;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Setzt eine mehrteilige Linie aus den WKT ihrer Komponenten zusammen.
 *
 * <p>Behelf für die OKLABI (bis einschließlich 1.6.5.0): Beim WKT einer mehrteiligen Linie
 * zerlegt sie Kreisbögen und Splines in Polygonzüge und bringt diese immer auf zwei
 * Dimensionen; in einer dreidimensionalen Linie erhalten die Bogenpunkte dann die Höhe 0.
 * Das WKT einer einzelnen Komponente ist dagegen richtig.
 */
final class Linienkomponenten {

    private static final Pattern LINIE = Pattern.compile("^\\s*LINESTRING(\\s+Z)?\\s*\\((.*)\\)\\s*$");

    private Linienkomponenten() {
    }

    /**
     * Mehrteilige Linie aus den WKT der Komponenten.
     * @param komponenten WKT der Komponenten, je {@code LINESTRING} oder {@code LINESTRING Z}
     * @return {@code MULTILINESTRING} bzw. {@code MULTILINESTRING Z}; {@code null}, wenn eine
     *     Komponente leer oder kein {@code LINESTRING} ist oder die Dimensionen gemischt sind
     */
    static String zusammensetzen(List<String> komponenten) {
        if (komponenten == null || komponenten.isEmpty()) {
            return null;
        }
        StringBuilder teile = new StringBuilder();
        Boolean mitZ = null;
        for (String wkt : komponenten) {
            Matcher m = wkt == null ? null : LINIE.matcher(wkt);
            if (m == null || !m.matches() || m.group(2).isBlank()) {
                return null;
            }
            boolean z = m.group(1) != null;
            if (mitZ == null) {
                mitZ = z;
            } else if (mitZ != z) {
                return null;
            }
            if (teile.length() > 0) {
                teile.append(',');
            }
            teile.append('(').append(m.group(2)).append(')');
        }
        return (mitZ ? "MULTILINESTRING Z (" : "MULTILINESTRING (") + teile + ")";
    }
}
