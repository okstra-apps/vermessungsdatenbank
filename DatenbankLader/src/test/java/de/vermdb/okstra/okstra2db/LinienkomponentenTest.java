// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.okstra2db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class LinienkomponentenTest {

    @Test
    void dreidimensional() {
        // Strecke und interpolierter Kreisbogen wie im Beispieldatensatz der LSBB
        assertEquals("MULTILINESTRING Z ((1 2 70,3 4 70),(3 4 70,5 6 71,7 8 72))",
                Linienkomponenten.zusammensetzen(List.of(
                        "LINESTRING Z (1 2 70,3 4 70)",
                        "LINESTRING Z (3 4 70,5 6 71,7 8 72)")));
    }

    @Test
    void zweidimensional() {
        assertEquals("MULTILINESTRING ((1 2,3 4),(3 4,5 6))",
                Linienkomponenten.zusammensetzen(List.of("LINESTRING (1 2,3 4)", "LINESTRING (3 4,5 6)")));
    }

    @Test
    void nichtZusammensetzbar() {
        assertNull(Linienkomponenten.zusammensetzen(List.of()));
        assertNull(Linienkomponenten.zusammensetzen(null));
        assertNull(Linienkomponenten.zusammensetzen(List.of("LINESTRING Z (1 2 3,4 5 6)", "LINESTRING (1 2,3 4)")));
        assertNull(Linienkomponenten.zusammensetzen(List.of("LINESTRING Z (1 2 3,4 5 6)", "LINESTRING EMPTY")));
        assertNull(Linienkomponenten.zusammensetzen(List.of("POINT Z (1 2 3)")));
        assertNull(Linienkomponenten.zusammensetzen(Arrays.asList("LINESTRING (1 2,3 4)", null)));
    }
}
