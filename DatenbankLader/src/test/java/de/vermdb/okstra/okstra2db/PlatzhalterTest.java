// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.okstra2db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PlatzhalterTest {

    @Test
    void ersetzteWerteOhneMeldung() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("okstraAuthority", "LSBB");
        p.put("okstraServerPrefix", "http://localhost/vermdb/");
        p.put("okstraFblisteCheck", "einfach:ohneSymbolbedeutung");
        p.put("leer", "");
        p.put("ohne", null);
        // ein Dollarzeichen allein ist kein Platzhalter
        p.put("preis", "$5 {x}");
        assertTrue(Platzhalter.offene(p).isEmpty());
    }

    @Test
    void nichtErsetztePlatzhalter() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("okstraAuthority", "${VERMDB_BEHOERDE}");
        p.put("okstraAuthName", "LSBB");
        p.put("okstraUploadpath", "${VERMDB_DATEN:-/daten}/upload/${X}");
        List<String> offen = Platzhalter.offene(p);
        assertEquals(List.of("okstraAuthority (${VERMDB_BEHOERDE})", "okstraUploadpath (${VERMDB_DATEN:-/daten}, ${X})"), offen);
        String m = Platzhalter.meldung(offen);
        assertTrue(m.contains("okstraAuthority (${VERMDB_BEHOERDE}); okstraUploadpath"), m);
        assertTrue(m.contains("PROPERTY_SOURCE=org.apache.tomcat.util.digester.EnvironmentPropertySource"), m);
    }
}
