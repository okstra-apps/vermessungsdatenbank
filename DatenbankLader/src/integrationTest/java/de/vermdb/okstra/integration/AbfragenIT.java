// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Informationsabfragen ohne Daten (doku/Schnittstelle.md, Abschnitt 3.1). */
class AbfragenIT {

    private final Umgebung u = Umgebung.get();

    @Test
    void getThemes() {
        Antwort a = u.get("request", "GetThemes");
        assertEquals(200, a.status());
        assertEquals("text/json", a.medientyp());
        assertTrue(a.erfolg());
        assertEquals("Hydrographie|Kataster|Leitungen|Planung|Topographie|Verkehr|Verwahltungsgrenzen|DGM|DOM",
                a.meldung());
    }

    @Test
    void getTypes() {
        Antwort a = u.get("request", "GetTypes");
        assertTrue(a.erfolg());
        assertEquals("1|2|3|4", a.meldung());
    }

    @Test
    void init() {
        Antwort a = u.get("request", "Init");
        assertTrue(a.erfolg());
        assertEquals("Service initialisiert", a.meldung());
    }

    @Test
    void parameternamenOhneGrossKlein() {
        Antwort a = u.get("REQUEST", "gettypes");
        assertTrue(a.erfolg());
        assertEquals("1|2|3|4", a.meldung());
    }

    @Test
    void unbekannteAnfrage() {
        for (Antwort a : new Antwort[] {u.get("request", "Unbekannt"), u.get("x", "1")}) {
            assertEquals(200, a.status());
            assertFalse(a.erfolg());
            assertEquals("Request-Typ unbekannt.", a.meldung());
        }
    }

    @Test
    void areasOhneGebiete() {
        // Die Standardinstallation legt keine Gebiete an
        Antwort a = u.get("request", "Areas");
        assertEquals(200, a.status());
        assertEquals("text/plain", a.medientyp());
        Referenz.pruefe("abfragen-areas.txt", a.text());
    }

    @Test
    void auftragslistenAlsHtml() {
        for (String r : new String[] {"Queue", "FullQueue", "QueueCheck"}) {
            Antwort a = u.get("request", r);
            assertEquals(200, a.status(), r);
            assertEquals("text/html", a.medientyp(), r);
            assertTrue(a.text().contains("<table"), r);
        }
    }
}
