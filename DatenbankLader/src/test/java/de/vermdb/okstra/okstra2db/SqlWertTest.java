// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.okstra2db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SqlWertTest {

    @Test
    void textMaskiertHochkommaUndBackslash() {
        assertEquals("E'abc'", SqlWert.text("abc"));
        assertEquals("E'O''Neill'", SqlWert.text("O'Neill"));
        assertEquals("E'a\\\\b'", SqlWert.text("a\\b"));
        assertEquals("E'x'' OR ''a''=''a'", SqlWert.text("x' OR 'a'='a"));
        assertEquals("E'\\\\'''", SqlWert.text("\\'"));
        assertEquals("E'Straße; \"x\"'", SqlWert.text("Straße; \"x\""));
    }

    @Test
    void textWieBisherNullAlsText() {
        assertEquals("E'null'", SqlWert.text(null));
    }

    @Test
    void textOhneNullzeichen() {
        assertThrows(IllegalArgumentException.class, () -> SqlWert.text("a\u0000b"));
    }

    @Test
    void ganzzahl() {
        assertEquals("10", SqlWert.ganzzahl("10"));
        assertEquals("-3", SqlWert.ganzzahl(" -3 "));
        assertEquals("7", SqlWert.ganzzahl("+7"));
        for (String s : new String[] { "", "1.5", "0 OR oid>0", "1;", "0x10", "10)" }) {
            assertThrows(NumberFormatException.class, () -> SqlWert.ganzzahl(s), s);
        }
    }

    @Test
    void zahl() {
        assertEquals("1.5", SqlWert.zahl("1.5"));
        assertEquals("-2", SqlWert.zahl(" -2 "));
        assertEquals("1e3", SqlWert.zahl("1e3"));
        assertEquals(".5", SqlWert.zahl(".5"));
        for (String s : new String[] { "NaN", "Infinity", "-Infinity", "1,5", "0 OR oid>0", "1d", "0x1p3", "" }) {
            assertThrows(NumberFormatException.class, () -> SqlWert.zahl(s), s);
        }
    }

    @Test
    void ganzzahlListe() {
        assertEquals("10, 9", SqlWert.ganzzahlListe("10, 9"));
        assertEquals("1, 2, 3", SqlWert.ganzzahlListe("1,2 ,3"));
        assertEquals("5", SqlWert.ganzzahlListe("5"));
        for (String s : new String[] { "0) OR (a.auftragsnummer=10", "1,,2", "1,", "" }) {
            assertThrows(NumberFormatException.class, () -> SqlWert.ganzzahlListe(s), s);
        }
    }
}
