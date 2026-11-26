// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.integration;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Vergleich mit Referenzdateien unter {@code src/integrationTest/resources/erwartet/}.
 * Verglichen wird nach der {@link #normalisiere(String) Normalisierung}. Mit
 * {@code -PreferenzenAktualisieren} schreibt der Test das Ergebnis als neue Referenz;
 * die Änderung ist dann im Repository zu prüfen. Bei einer Abweichung liegt das Ergebnis
 * unter {@code build/integrationstest/ist/}.
 */
final class Referenz {

    private Referenz() {
    }

    /**
     * Prüft einen Text gegen die Referenzdatei.
     * @param name Dateiname unter {@code erwartet/}
     * @param ist Ergebnis, noch nicht normalisiert
     */
    static void pruefe(String name, String ist) {
        Umgebung u = Umgebung.get();
        String normal = normalisiere(ist);
        Path datei = u.repository().resolve("DatenbankLader/src/integrationTest/resources/erwartet").resolve(name);
        try {
            if (Boolean.getBoolean("vermdb.referenzenAktualisieren")) {
                Files.createDirectories(datei.getParent());
                Files.writeString(datei, normal, StandardCharsets.UTF_8);
                return;
            }
            if (!Files.exists(datei)) {
                fail("Referenzdatei fehlt: " + datei + " (anlegen mit -PreferenzenAktualisieren)");
            }
            String soll = Files.readString(datei, StandardCharsets.UTF_8);
            if (!soll.equals(normal)) {
                Path istDatei = u.arbeitsverzeichnis().resolve("ist").resolve(name);
                Files.createDirectories(istDatei.getParent());
                Files.writeString(istDatei, normal, StandardCharsets.UTF_8);
                fail("Abweichung von " + name + " ab Zeile " + ersteAbweichung(soll, normal)
                        + "; Ergebnis in " + istDatei);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String ersteAbweichung(String soll, String ist) {
        List<String> a = soll.lines().toList();
        List<String> b = ist.lines().toList();
        for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
            String x = i < a.size() ? a.get(i) : "<Ende>";
            String y = i < b.size() ? b.get(i) : "<Ende>";
            if (!x.equals(y)) {
                return (i + 1) + ":\n  erwartet: " + x + "\n  erhalten: " + y;
            }
        }
        return "?";
    }

    /**
     * Vereinheitlicht, was sich von Lauf zu Lauf ändert: Zeitangaben, Dauer, Dateinamen mit
     * Zeitstempel, Auftragsnummern in Dateinamen, temporäre Namen, Prüfsummen, interne
     * Kennungen, Pfade und Rechnerangaben; Zeilenenden als LF.
     */
    static String normalisiere(String s) {
        return s.replace("\r\n", "\n").replace("\r", "\n")
                .replaceAll("[0-9]{4}-[0-9]{2}-[0-9]{2}[T ][0-9]{2}:[0-9]{2}:[0-9]{2}([.,][0-9]+)?([+-][0-9]{2}:?[0-9]{2}|Z)?",
                        "<ZEIT>")
                .replaceAll("[0-9]{2}\\.[0-9]{2}\\.[0-9]{4},? [0-9]{2}:[0-9]{2}(:[0-9]{2})?", "<ZEIT>")
                .replaceAll("[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{2}-[0-9]{2}-[0-9]{2}", "<ZEIT>")
                .replaceAll("\\b[0-9]+([.,][0-9]+)? ?(ms|Sekunden|sec)\\b", "<DAUER>")
                .replaceAll("readOut[0-9]+\\.tmp", "readOut<N>.tmp")
                .replaceAll("\\b[0-9]+_(laden|transfer|migration|fehler)_", "<NR>_$1_")
                .replaceAll("_[0-9]{8}_[0-9]{6}([._])", "_<ZEIT>$1")
                .replaceAll("Hash-Summe: [0-9a-f]+", "Hash-Summe: <…>")
                .replaceAll("gml:id=\"FC_[0-9]+\"", "gml:id=\"FC_<N>\"")
                .replaceAll("benutze [0-9]+ Threads aus Pool\\.\\.\\. \\([0-9]+ Kerne im System\\)",
                        "benutze <N> Threads aus Pool... (<N> Kerne im System)");
    }
}
