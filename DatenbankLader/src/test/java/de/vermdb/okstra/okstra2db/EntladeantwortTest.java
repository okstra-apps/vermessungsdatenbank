// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.okstra2db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntladeantwortTest {

    private static String zs(String anfang) {
        byte[] b = anfang.getBytes(StandardCharsets.ISO_8859_1);
        return Entladeantwort.zeichensatz(b, b.length);
    }

    @Test
    void zeichensatzAusDeklaration() {
        // so schreibt die OKLABI unter Linux
        assertEquals("utf-8", zs("<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n<!-- generiert"));
        // so schreibt sie unter Windows (Lieferungen der LSBB)
        assertEquals("Windows-1252", zs("<?xml version=\"1.0\" encoding=\"Windows-1252\"?>\r\n"));
        assertEquals("ISO-8859-1", zs("<?xml version='1.0' encoding = 'ISO-8859-1' standalone='yes'?>"));
        assertEquals("UTF-8", zs("ï»¿<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
    }

    @Test
    void ohneAngabe() {
        assertNull(zs("<?xml version=\"1.0\"?><a encoding=\"x\"/>"));
        assertNull(zs("<gml:FeatureCollection/>"));
        assertNull(zs(""));
        // encoding nur außerhalb der Deklaration
        assertNull(zs("<a/><?xml version=\"1.0\" encoding=\"utf-8\"?>"));
    }

    @Test
    void inhaltstyp(@TempDir Path dir) throws IOException {
        Path utf8 = dir.resolve("a.xml");
        Files.write(utf8, "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<a>Straße €</a>\n"
                .getBytes(StandardCharsets.UTF_8));
        assertEquals("text/xml; charset=utf-8", Entladeantwort.inhaltstyp(utf8));

        Path ohne = dir.resolve("b.xml");
        Files.writeString(ohne, "<a/>");
        assertEquals("text/xml; charset=UTF-8", Entladeantwort.inhaltstyp(ohne));

        Path leer = dir.resolve("c.xml");
        Files.write(leer, new byte[0]);
        assertEquals("text/xml; charset=UTF-8", Entladeantwort.inhaltstyp(leer));
    }
}
