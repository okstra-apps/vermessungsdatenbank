// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.okstra2db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KennungenTest {

    @Test
    void vordefinierteKennungenSindZulaessig() {
        // Beispiele aus den Schlüsseltabellen der OKLABI und aus dem Bestand
        for (String k : new String[] { "", "  ", "7", "0815", "B", "PmB 10/25 VL", "LkwA(grund)", "CI/1", "?",
                "Ö", "GÜW", "Grün", "*", "1+2", "2.1-3", "a_b", "äöß" }) {
            assertTrue(Kennungen.zulaessig(k), k);
        }
    }

    @Test
    void sonderzeichenSindUnzulaessig() {
        for (String k : new String[] { "7'x", "a\\b", "x;y", "\"", "a,b", "a=b", "a\nb", "a\tb", "a\u0000b",
                "%", "<b>", "é", "’", "😀", "a`b", "$1" }) {
            assertFalse(Kennungen.zulaessig(k), k);
        }
        assertFalse(Kennungen.zulaessig(null));
    }

    @Test
    void lesbarMaskiertSteuerzeichenUndKuerzt() {
        assertEquals("a\\u000ab\\u0000", Kennungen.lesbar("a\nb\u0000"));
        assertEquals("x".repeat(200) + " …", Kennungen.lesbar("x".repeat(201)));
        assertEquals("7'x", Kennungen.lesbar("7'x"));
    }

    @Test
    void meldungNenntObjektartUndEigenschaft() {
        String m = Kennungen.meldung("Art_Boeschung", "Kennung", "7'x");
        assertTrue(m.startsWith("Unzulässige Kennung \"7'x\" für Art_Boeschung.Kennung"), m);
    }
}
