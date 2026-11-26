// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.okstra2db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Lokale Dateien auf dem Server statt einer hochgeladenen (Parameter uploadfile): nur aus
 * dem Übergabeverzeichnis (Init-Parameter okstraTransferpath) oder darunter.
 */
final class Uebergabeverzeichnis {

    private Uebergabeverzeichnis() {
    }

    /**
     * Löst die Angabe aus uploadfile gegen das Übergabeverzeichnis auf. Ein relativer
     * Pfad gilt relativ zum Verzeichnis; {@code ..} und symbolische Links werden
     * aufgelöst.
     * @param verzeichnis Übergabeverzeichnis als realer Pfad ({@link Path#toRealPath})
     * @param angabe Wert von uploadfile
     * @return realer Pfad einer regulären Datei im Verzeichnis oder darunter, sonst
     *         {@code null} (auch, wenn die Datei nicht vorhanden ist)
     */
    static Path aufloesen(Path verzeichnis, String angabe) {
        if (verzeichnis == null || angabe == null || angabe.trim().isEmpty()) {
            return null;
        }
        try {
            Path real = verzeichnis.resolve(angabe.trim()).toRealPath();
            if (real.startsWith(verzeichnis) && Files.isRegularFile(real)) {
                return real;
            }
        } catch (IOException | InvalidPathException ex) {
            // nicht vorhanden oder ungültig
        }
        return null;
    }
}
