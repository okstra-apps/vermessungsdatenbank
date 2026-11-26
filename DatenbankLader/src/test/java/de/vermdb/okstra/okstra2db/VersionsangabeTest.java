// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.okstra2db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class VersionsangabeTest {

    private static InputStream manifest(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void versionMitCommit() {
        Versionsangabe v = Versionsangabe.lies(manifest(
                "Manifest-Version: 1.0\r\nImplementation-Version: 2.0.0-rc.1\r\nBuild-Commit: 87d783a\r\n\r\n"));
        assertEquals("2.0.0-rc.1 (Commit 87d783a)", v.toString());
        assertEquals("2.0.0-rc.1", v.version());
    }

    @Test
    void versionOhneCommit() {
        Versionsangabe v = Versionsangabe.lies(manifest("Manifest-Version: 1.0\nImplementation-Version: 2.0.0\n\n"));
        assertEquals("2.0.0", v.toString());
        assertEquals("2.0.0", v.version());
    }

    @Test
    void ohneEintragOderManifestUnbekannt() {
        assertEquals(Versionsangabe.UNBEKANNT, Versionsangabe.lies(manifest("Manifest-Version: 1.0\n\n")).toString());
        assertEquals(Versionsangabe.UNBEKANNT, Versionsangabe.lies(null).version());
    }
}
