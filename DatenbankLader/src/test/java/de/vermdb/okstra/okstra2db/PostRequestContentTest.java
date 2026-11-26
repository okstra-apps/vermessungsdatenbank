// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.okstra2db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

/**
 * Auswertung der Parameter von POST-Anfragen. Hält auch bestehende Eigenheiten fest
 * (mit „bisher“ gekennzeichnet); Änderungen daran ändern die Schnittstelle.
 */
class PostRequestContentTest {

    private static PostRequestContent anfrage(String... schluesselWert) {
        PostRequestContent p = new PostRequestContent();
        for (int i = 0; i < schluesselWert.length; i += 2) {
            p.setItem(schluesselWert[i], schluesselWert[i + 1]);
        }
        return p;
    }

    private static Map<String, String> parameter() {
        return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    @Test
    void sessionIdZulaessig() {
        for (String sid : new String[] { "sess_6787_2026-05-15-08-20-50", "a", "A.b-c_d", "x".repeat(128), "-x", "_" }) {
            assertEquals("", PostRequestContent.pruefeSessionId(sid), sid);
        }
    }

    @Test
    void sessionIdUnzulaessig() {
        for (String sid : new String[] { "../p30", "..\\x", ".versteckt", "a/b", "a b", "x".repeat(129), "",
                "a\u0000", "sä", "a;b", "C:x" }) {
            assertTrue(PostRequestContent.pruefeSessionId(sid).startsWith("Session-ID enthält unzulässige Zeichen"), sid);
        }
        assertFalse(PostRequestContent.pruefeSessionId(null).isEmpty());
    }

    @Test
    void checkParametersUebernimmtSidUndAlias() {
        Map<String, String> parms = parameter();
        assertEquals("", anfrage("SID", "t1", "TS", "2026-01-01-12-00-00").checkParameters(parms));
        assertEquals("t1", parms.get("SID"));
        assertEquals("t1", parms.get("SessionID"));
        assertEquals("2026-01-01-12-00-00", parms.get("TimeStamp"));

        parms = parameter();
        assertEquals("", anfrage("SessionID", "t2").checkParameters(parms));
        assertEquals("t2", parms.get("SID"));
    }

    @Test
    void checkParametersFehlendeOderMehrdeutigeSid() {
        assertEquals("Session-ID ist nicht belegt.", anfrage("TS", "x").checkParameters(parameter()));
        assertEquals("Session-ID ist nicht belegt.", anfrage("SID", "  ").checkParameters(parameter()));
        assertEquals("Session-ID ist mehrdeutig belegt.", anfrage("SID", "a", "SessionID", "b").checkParameters(parameter()));
        // Groß- und Kleinschreibung zählt beim Vergleich nicht
        assertEquals("", anfrage("SID", "abc", "SessionID", "ABC").checkParameters(parameter()));
    }

    @Test
    void checkParametersPrueftSid() {
        String msg = anfrage("SID", "../../p31").checkParameters(parameter());
        assertTrue(msg.startsWith("Session-ID enthält unzulässige Zeichen"), msg);
    }

    @Test
    void checkParametersLageUndHoehensystem() {
        Map<String, String> parms = parameter();
        assertEquals("", anfrage("SID", "t", "f22", "25832", "f23", "170").checkParameters(parms));
        assertEquals("25832", parms.get("SRS"));
        assertEquals("170", parms.get("HS"));
        for (String srs : new String[] { "25832", "25833", "2398", "31466", "31467", "31468", "31469", "4326" }) {
            assertEquals("", anfrage("SID", "t", "SRS", srs).checkParameters(parameter()), srs);
        }
        assertEquals("Koordinatenreferenzsystem wird nicht unterstützt.",
                anfrage("SID", "t", "f22", "3857").checkParameters(parameter()));
        assertEquals("Höhensystem wird nicht unterstützt.",
                anfrage("SID", "t", "HS", "150").checkParameters(parameter()));
        assertEquals("Koordinatenreferenzsystem ist mehrdeutig belegt.",
                anfrage("SID", "t", "f22", "25832", "SRS", "25833").checkParameters(parameter()));
    }

    @Test
    void checkParametersMehrereFehlerZeilenweise() {
        assertEquals("Session-ID ist nicht belegt.\nKoordinatenreferenzsystem wird nicht unterstützt.",
                anfrage("f22", "1").checkParameters(parameter()));
    }

    @Test
    void checkParametersAliasse() {
        Map<String, String> parms = parameter();
        anfrage("SID", "t", "PRJNM", "P", "f21", "3", "f100", "7", "f101", "2.022", "Auftrag", "10", "gzip", "1",
                "AREA", "POINT(0 0)").checkParameters(parms);
        assertEquals("P", parms.get("Projekt"));
        assertEquals("3", parms.get("Thema"));
        assertEquals("7", parms.get("Gebiet"));
        assertEquals("2.022", parms.get("Version"));
        assertEquals("10", parms.get("Auftrag"));
        assertEquals("1", parms.get("gzip"));
        assertEquals("POINT(0 0)", parms.get("Area"));
        assertEquals("Projekt ist mehrdeutig belegt.",
                anfrage("SID", "t", "PRJNM", "a", "Projekt", "b").checkParameters(parameter()));
    }

    @Test
    void checkParametersHoleDateiBisherNurOhneHoehensystem() {
        // bisher: holeDatei wird nur übernommen, wenn weder f23 noch HS angegeben ist
        Map<String, String> parms = parameter();
        anfrage("SID", "t", "holeDatei", "readLock").checkParameters(parms);
        assertEquals("readLock", parms.get("ReadLock"));
        parms = parameter();
        anfrage("SID", "t", "holeDatei", "readLock", "f23", "170").checkParameters(parms);
        assertFalse(parms.containsKey("ReadLock"));
    }

    @Test
    void datenbestand() {
        assertEquals("Bestandsdaten", anfrage().getDatenbankTyp());
        PostRequestContent alt = anfrage("database", "olddata");
        assertTrue(alt.getIstAltdaten());
        assertEquals("Altdaten", alt.getDatenbankTyp());
        PostRequestContent plan = anfrage("database", "PLANNING");
        assertTrue(plan.getIstPlanung());
        assertEquals("Planungsdaten", plan.getDatenbankTyp());
        assertEquals("unbekannt", anfrage("database", "x").getDatenbankTyp());
    }

    @Test
    void uploadfileErstNachPruefungImServlet() {
        PostRequestContent p = anfrage("uploadfile", "/etc/passwd");
        assertFalse(p.getUploadExists());
        p.setzeUploadfile("/daten/uebergabe/probe.xml");
        assertTrue(p.getUploadExists());
        assertEquals("/daten/uebergabe/probe.xml", p.getItem("uploadfile"));
    }

    @Test
    void stichtag() {
        assertEquals("2026-11-08", anfrage("f4", "08.11.2026").getStichtag());
        assertEquals("2026-11-08", anfrage("f4", "2026-11-08").getStichtag());
        // bisher: der Kalender ist nachsichtig, ungültige Tage laufen in den Folgemonat über
        assertEquals("2026-03-03", anfrage("f4", "31.02.2026").getStichtag());
        // bisher: ohne Trennzeichen eine Meldung und das heutige Datum
        PostRequestContent p = anfrage("f4", "20261108");
        assertTrue(p.getExceptionMsg().startsWith("Datum ungültig"), p.getExceptionMsg());
    }

    @Test
    void parameterOhneGrossKleinschreibung() {
        PostRequestContent p = anfrage("sid", "t");
        assertTrue(p.hasItem("SID", true));
        assertEquals("t", p.getItem("Sid"));
    }
}
