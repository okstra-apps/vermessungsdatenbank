// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.okstra2db;

import java.util.regex.Pattern;

/**
 * Eingabeprüfung für die Kennungen von Schlüsseltabellen- und Aufzählungseinträgen
 * aus gelieferten Dateien. Zulässig ist der Zeichenvorrat der vordefinierten Kennungen
 * der OKLABI (OKSTRA 1.007 bis 2.023) zuzüglich ä, ö, ß und Unterstrich.
 */
final class Kennungen {

    /** Zulässige Zeichen, für Meldungen */
    static final String ERLAUBT = "Buchstaben A-Z, a-z, Ä, Ö, Ü, ä, ö, ü, ß, "
            + "Ziffern, Leerzeichen und ( ) * + - . / ? _";

    private static final Pattern ZULAESSIG =
            Pattern.compile("[A-Za-zÄÖÜäöüß0-9 ()*+./?_-]*");

    private static final int MAX_ANZEIGE = 200;

    private Kennungen() {
    }

    /**
     * @param kennung Kennung eines Schlüsseltabellen- oder Aufzählungseintrags
     * @return true, wenn die Kennung nur zulässige Zeichen enthält
     */
    static boolean zulaessig(String kennung) {
        return kennung != null && ZULAESSIG.matcher(kennung).matches();
    }

    /**
     * Kennung für Protokollmeldungen: Steuerzeichen als \\uXXXX, gekürzt auf 200 Zeichen.
     * @param kennung Kennung
     * @return lesbare Form
     */
    static String lesbar(String kennung) {
        String s = String.valueOf(kennung);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && i < MAX_ANZEIGE; i++) {
            char c = s.charAt(i);
            if (Character.isISOControl(c) || Character.getType(c) == Character.FORMAT
                    || Character.isSurrogate(c) || c == ' ' || c == ' ') {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        if (s.length() > MAX_ANZEIGE) {
            sb.append(" …");
        }
        return sb.toString();
    }

    /**
     * @param objektart Name der Objektart
     * @param eigenschaft Name der Eigenschaft
     * @param kennung unzulässige Kennung
     * @return Meldung für das Protokoll
     */
    static String meldung(String objektart, String eigenschaft, String kennung) {
        return "Unzulässige Kennung \"" + lesbar(kennung) + "\" für " + objektart + "." + eigenschaft
                + " (erlaubt: " + ERLAUBT + ").";
    }
}
