// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Eigene Fachbedeutungslisten aus dem Konfigurationsverzeichnis ({@code fbliste/}):
 * {@link Umgebung#EIGENE_LISTE} ist beim Start zu den Listen des Release ergänzt und in die
 * Datenbank geladen, und Lieferungen lassen sich gegen sie prüfen.
 */
class FachbedeutungslistenIT {

    private final Umgebung u = Umgebung.get();

    @Test
    void eigeneListeImProtokoll() {
        String p = u.protokoll();
        assertTrue(p.contains("Eigene Fachbedeutungsliste: " + Umgebung.EIGENE_LISTE), p);
        assertTrue(p.contains("Fachbedeutungslisten laden")
                || p.contains("Fachbedeutungslisten der Datenbank sind aktuell"), p);
    }

    @Test
    void eigeneListeInDerDatenbank() {
        assertEquals("1", u.sql("select count(*) from oks_meta.efbversion"
                + " where bundesland = 'Sachsen-Anhalt' and hauptversion = 3 and unterversion = 6"));
        assertTrue(u.sql("select obj_description('oks_meta.fbliste'::regclass, 'pg_class')")
                .startsWith("Fachbedeutungslisten "));
        // Dieselben Einträge wie 3.5, von der die Liste eine Kopie ist
        assertEquals(u.sql("select count(*) from oks_meta.fbliste f join oks_meta.efbversion v on v.id = f.fbversion"
                        + " where v.bundesland = 'Sachsen-Anhalt' and v.hauptversion = 3 and v.unterversion = 5"),
                u.sql("select count(*) from oks_meta.fbliste f join oks_meta.efbversion v on v.id = f.fbversion"
                        + " where v.bundesland = 'Sachsen-Anhalt' and v.hauptversion = 3 and v.unterversion = 6"));
    }

    @Test
    void pruefauftragGegenEigeneListe() {
        int nr = u.liefere("fbl36", "beispiel.xml", true, "f7", "3.6");
        assertEquals("geprüft", u.zeile(nr).get("Status"));
        assertEquals("3.6 Sachsen-Anhalt", u.zeile(nr).get("Fachbedeutungen"));
    }
}
