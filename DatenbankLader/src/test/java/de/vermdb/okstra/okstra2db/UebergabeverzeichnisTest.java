// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.okstra2db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UebergabeverzeichnisTest {

    @TempDir
    Path tmp;

    Path verzeichnis;
    Path aussen;

    @BeforeEach
    void anlegen() throws IOException {
        Path wurzel = tmp.toRealPath();
        verzeichnis = Files.createDirectory(wurzel.resolve("uebergabe"));
        Files.createDirectory(verzeichnis.resolve("unter"));
        Files.writeString(verzeichnis.resolve("probe.xml"), "x");
        Files.writeString(verzeichnis.resolve("unter").resolve("tief.xml"), "x");
        aussen = Files.writeString(wurzel.resolve("aussen.xml"), "x");
    }

    @Test
    void relativUndAbsolutImVerzeichnis() {
        assertEquals(verzeichnis.resolve("probe.xml"), Uebergabeverzeichnis.aufloesen(verzeichnis, "probe.xml"));
        assertEquals(verzeichnis.resolve("probe.xml"), Uebergabeverzeichnis.aufloesen(verzeichnis, " probe.xml "));
        assertEquals(verzeichnis.resolve("unter/tief.xml"), Uebergabeverzeichnis.aufloesen(verzeichnis, "unter/tief.xml"));
        assertEquals(verzeichnis.resolve("probe.xml"),
                Uebergabeverzeichnis.aufloesen(verzeichnis, verzeichnis.resolve("probe.xml").toString()));
        assertEquals(verzeichnis.resolve("probe.xml"), Uebergabeverzeichnis.aufloesen(verzeichnis, "unter/../probe.xml"));
    }

    @Test
    void ausserhalbAbgewiesen() {
        assertNull(Uebergabeverzeichnis.aufloesen(verzeichnis, "../aussen.xml"));
        assertNull(Uebergabeverzeichnis.aufloesen(verzeichnis, aussen.toString()));
        assertNull(Uebergabeverzeichnis.aufloesen(verzeichnis, "/etc/passwd"));
        assertNull(Uebergabeverzeichnis.aufloesen(verzeichnis, "../../../../etc/passwd"));
    }

    @Test
    void nichtVorhandenVerzeichnisOderLeer() {
        assertNull(Uebergabeverzeichnis.aufloesen(verzeichnis, "fehlt.xml"));
        assertNull(Uebergabeverzeichnis.aufloesen(verzeichnis, "unter"));
        assertNull(Uebergabeverzeichnis.aufloesen(verzeichnis, ""));
        assertNull(Uebergabeverzeichnis.aufloesen(verzeichnis, "   "));
        assertNull(Uebergabeverzeichnis.aufloesen(verzeichnis, null));
        assertNull(Uebergabeverzeichnis.aufloesen(null, "probe.xml"));
    }

    @Test
    void symbolischerLinkNachAussenAbgewiesen() throws IOException {
        Path link;
        try {
            link = Files.createSymbolicLink(verzeichnis.resolve("link.xml"), aussen);
        } catch (UnsupportedOperationException | IOException ex) {
            assumeTrue(false, "symbolische Links nicht möglich");
            return;
        }
        assertNull(Uebergabeverzeichnis.aufloesen(verzeichnis, link.getFileName().toString()));
    }

    @Test
    void verzeichnisMitAehnlichemNamenAbgewiesen() throws IOException {
        // startsWith vergleicht Pfadbestandteile, nicht Zeichenketten
        Path nachbar = Files.createDirectory(verzeichnis.resolveSibling("uebergabe2"));
        Files.writeString(nachbar.resolve("x.xml"), "x");
        assertNull(Uebergabeverzeichnis.aufloesen(verzeichnis, "../uebergabe2/x.xml"));
    }
}
