// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** Auftragslisten und Löschen von Aufträgen (doku/Schnittstelle.md, 3.2, 3.5). */
class AuftraegeIT {

    private final Umgebung u = Umgebung.get();

    @Test
    void auftragLoeschen() {
        int nr = u.liefere("loeschen", "beispiel.xml", false);
        Map<String, Integer> vorher = u.objekte(nr);
        assertFalse(vorher.isEmpty());
        assertTrue(u.get("request", "Queue").text().contains("<td>" + nr + "</td>"));

        Antwort a = u.get("request", "DeleteJob", "auftrag", String.valueOf(nr), "datasource", "Bestand");
        assertEquals(200, a.status());
        assertTrue(a.erfolg(), a.text());
        assertTrue(a.text().contains("Auftrag " + nr + " entfernt"), a.text());

        assertEquals(Map.of(), u.objekte(nr));
        assertTrue(u.status(nr) < 0, "Status " + u.status(nr));
        assertEquals("gelöscht", u.zeile(nr).get("Status"));
        assertFalse(u.get("request", "Queue").text().contains("<td>" + nr + "</td>"));
    }

    @Test
    void loeschenOhneDatenbestand() {
        // Bekannt: ohne datasource meldet der Dienst Erfolg, löscht aber nichts
        int nr = u.liefere("nichtloeschen", "beispiel.xml", false);
        Antwort a = u.get("request", "DeleteJob", "auftrag", String.valueOf(nr));
        assertTrue(a.erfolg(), a.text());
        assertTrue(a.text().contains("nicht möglich"), a.text());
        assertFalse(u.objekte(nr).isEmpty());
    }

    @Test
    void pruefauftraegeInEigenerListe() {
        int nr = u.liefere("liste", "beispiel.xml", true);
        assertTrue(u.get("request", "QueueCheck").text().contains("<td>" + nr + "</td>"));
        assertFalse(u.get("request", "Queue").text().contains("<td>" + nr + "</td>"));
        assertTrue(u.get("request", "FullQueue").text().contains("<td>" + nr + "</td>"));
    }

    @Test
    void nichtEingerichteterDatenbestand() {
        // Altdaten und Planung sind in der Standardinstallation nicht eingerichtet
        assertEquals(500, u.get("request", "Queue", "datasource", "Altdaten").status());
    }

    @Test
    void protokolleUndVerzeichnisse() {
        int nr = u.liefere("links", "beispiel.xml", false);
        Map<String, String> z = u.zeile(nr);
        Antwort log = u.verlinkt(z.get("Laden"));
        assertEquals(200, log.status());
        assertEquals("text/plain", log.medientyp());
        // Upload und Verzeichnislisten werden nicht ausgeliefert
        assertEquals(404, u.abrufen("/vermdb/upload/").status());
        assertEquals(404, u.abrufen("/vermdb/log/").status());
    }
}
