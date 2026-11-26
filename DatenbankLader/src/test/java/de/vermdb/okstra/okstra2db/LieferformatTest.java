// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.okstra2db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LieferformatTest {

    @Test
    void angabeDesClients() {
        assertEquals("CTE", Lieferformat.bestimme("CTE", "a.xml"));
        assertEquals("CTE", Lieferformat.bestimme("cte", "a.xml"));
        assertEquals("CTE", Lieferformat.bestimme(" CTE ", "a.xml"));
        assertEquals("XML", Lieferformat.bestimme("XML", "a.cte"));
        // jeder andere Wert gilt wie bisher als XML
        assertEquals("XML", Lieferformat.bestimme("GML", "a.cte"));
    }

    @Test
    void ohneAngabeNachEndung() {
        assertEquals("CTE", Lieferformat.bestimme(null, "Lieferung.CTE"));
        assertEquals("CTE", Lieferformat.bestimme("", "a.cte"));
        // bisher wurde auch .xml als CTE gelesen
        assertEquals("XML", Lieferformat.bestimme("", "a.xml"));
        assertEquals("XML", Lieferformat.bestimme("", "a.XML"));
        assertEquals("XML", Lieferformat.bestimme(null, "ohne-endung"));
        assertEquals("XML", Lieferformat.bestimme(null, null));
    }

    @Test
    void pruefung() {
        assertEquals(Lieferformat.MELDUNG_CTE, Lieferformat.pruefe("CTE", "a.cte"));
        assertEquals(Lieferformat.MELDUNG_CTE, Lieferformat.pruefe(null, "a.cte"));
        assertEquals("", Lieferformat.pruefe("XML", "a.xml"));
        assertEquals("", Lieferformat.pruefe(null, "a.xml"));
    }
}
