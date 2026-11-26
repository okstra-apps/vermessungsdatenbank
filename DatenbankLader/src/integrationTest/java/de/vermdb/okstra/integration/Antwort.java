// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.integration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * HTTP-Antwort des Dienstes.
 * @param status HTTP-Status
 * @param typ Content-Type
 * @param inhalt Rumpf
 */
record Antwort(int status, String typ, byte[] inhalt) {

    private static final Pattern ZEICHENSATZ = Pattern.compile("charset=([^;\\s]+)", Pattern.CASE_INSENSITIVE);

    /** Rumpf als Text im Zeichensatz des Content-Type (ohne Angabe ISO-8859-1, wie Tomcat). */
    String text() {
        Matcher m = ZEICHENSATZ.matcher(typ);
        Charset cs = m.find() ? Charset.forName(m.group(1)) : StandardCharsets.ISO_8859_1;
        return new String(inhalt, cs);
    }

    /** Rumpf entpackt, wenn er gzip-komprimiert ist. */
    byte[] entpackt() {
        if (inhalt.length < 2 || (inhalt[0] & 0xff) != 0x1f || (inhalt[1] & 0xff) != 0x8b) {
            return inhalt;
        }
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(inhalt))) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code "success": true} in der JSON-Antwort. */
    boolean erfolg() {
        return text().replace(" ", "").contains("\"success\":true");
    }

    /** {@code auftragsnummer} aus der JSON-Antwort. */
    int auftragsnummer() {
        Matcher m = Pattern.compile("\"auftragsnummer\"\\s*:\\s*(-?\\d+)").matcher(text());
        if (!m.find()) {
            throw new AssertionError("keine Auftragsnummer: " + text());
        }
        return Integer.parseInt(m.group(1));
    }

    /** {@code message} aus der JSON-Antwort, ohne Auflösung von Entitäten. */
    String meldung() {
        Matcher m = Pattern.compile("\"message\"\\s*:\\s*\"(.*)\"\\s*}", Pattern.DOTALL).matcher(text());
        return m.find() ? m.group(1) : "";
    }

    /** Medientyp ohne Parameter, klein geschrieben. */
    String medientyp() {
        int i = typ.indexOf(';');
        return (i < 0 ? typ : typ.substring(0, i)).trim().toLowerCase(Locale.ROOT);
    }
}
