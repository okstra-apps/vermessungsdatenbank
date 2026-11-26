// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.okstra2db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Antwort beim synchronen Entladen ({@code Read}, {@code ReadLock}): Die von der OKLABI
 * geschriebene Datei wird unverändert übertragen, der Content-Type nennt den Zeichensatz
 * aus ihrer XML-Deklaration.
 */
final class Entladeantwort {

    /** Zeichensatz, wenn die Datei keinen angibt (Vorgabe von XML). */
    static final String VORGABE = "UTF-8";

    private static final int ANFANG = 256;

    private static final Pattern DEKLARATION = Pattern.compile(
            "^(?:\\xEF\\xBB\\xBF)?\\s*<\\?xml\\s[^>]*?\\bencoding\\s*=\\s*[\"']([A-Za-z][A-Za-z0-9._-]*)[\"']");

    private Entladeantwort() {
    }

    /**
     * Zeichensatz aus der XML-Deklaration am Anfang einer Datei.
     * @param anfang erste Bytes der Datei
     * @param laenge Anzahl gültiger Bytes
     * @return Name des Zeichensatzes, {@code null} ohne Deklaration oder ohne Angabe
     */
    static String zeichensatz(byte[] anfang, int laenge) {
        // ISO-8859-1 bildet jedes Byte auf ein Zeichen ab; die Deklaration ist ASCII
        String text = new String(anfang, 0, Math.max(0, laenge), StandardCharsets.ISO_8859_1);
        Matcher m = DEKLARATION.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Content-Type für eine entladene XML-Datei.
     * @param datei entladene Datei
     * @return etwa {@code text/xml; charset=utf-8}
     * @throws IOException Datei nicht lesbar
     */
    static String inhaltstyp(Path datei) throws IOException {
        byte[] anfang = new byte[ANFANG];
        int laenge;
        try (InputStream is = Files.newInputStream(datei)) {
            laenge = is.readNBytes(anfang, 0, ANFANG);
        }
        String zs = zeichensatz(anfang, laenge);
        return "text/xml; charset=" + (zs != null ? zs : VORGABE);
    }
}
