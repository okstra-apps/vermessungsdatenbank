// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Entladen des geladenen Musterlageplans: synchron und asynchron, Projekt und Auftrag,
 * Kompression, Zielversion, Lage- und Höhensystem, Fläche, Thema und Fehlerfälle
 * (doku/Schnittstelle.md, 3.3, 3.4, 4.2).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EntladenIT {

    private final Umgebung u = Umgebung.get();
    private int auftrag;
    private Inhalt original;
    private String utm32;

    @BeforeAll
    void laden() throws IOException {
        auftrag = u.liefere("entladen", "musterlageplan.xml", false);
        original = Inhalt.von(Files.readAllBytes(u.testdatei("musterlageplan.xml")));
        utm32 = lese("request", "Read", "SID", "e0", "Auftrag", String.valueOf(auftrag)).zusammenfassung();
    }

    private Inhalt lese(String... parameter) {
        Antwort a = u.get(parameter);
        assertEquals(200, a.status(), a.text());
        return Inhalt.von(a.entpackt());
    }

    @Test
    void auftrag() {
        Antwort a = u.get("request", "Read", "SID", "e1", "Auftrag", String.valueOf(auftrag));
        assertEquals(200, a.status());
        // Die Datei kommt unverändert, mit dem Zeichensatz ihres Kopfes
        assertEquals("text/xml;charset=utf-8", a.typ().replace(" ", ""));
        Inhalt i = Inhalt.von(a.inhalt());
        assertEquals("http://www.okstra.de/okstra/2.022", i.okstraNamensraum());
        // Das Höhensystem steht nur im srsName, wenn es von dem der Datenbank abweicht
        assertEquals(List.of("urn:adv:crs:ETRS89_UTM32"), i.lagesysteme());
        assertEquals(original.texte(), i.texte());
        Referenz.pruefe("entladen-musterlageplan-utm32.txt", i.zusammenfassung());
    }

    @Test
    void projekt() {
        assertEquals(utm32, lese("request", "Read", "SID", "e2", "Projekt", "Pentladen").zusammenfassung());
    }

    @Test
    void komprimiert() {
        Antwort a = u.get("request", "Read", "SID", "e3", "Auftrag", String.valueOf(auftrag), "gzip", "1");
        assertEquals(200, a.status());
        assertEquals("application/x-gzip", a.medientyp());
        assertEquals(utm32, Inhalt.von(a.entpackt()).zusammenfassung());
    }

    @Test
    void readLock() {
        assertEquals(utm32, lese("request", "ReadLock", "SID", "e4", "Auftrag", String.valueOf(auftrag))
                .zusammenfassung());
        assertEquals(utm32, lese("request", "ReadLock", "SID", "e5", "Auftrag", String.valueOf(auftrag),
                "gzip", "1").zusammenfassung());
    }

    @Test
    void zielversion() {
        Inhalt i = lese("request", "Read", "SID", "e6", "Auftrag", String.valueOf(auftrag), "Version", "2.020");
        assertEquals("http://www.okstra.de/okstra/2.020", i.okstraNamensraum());
        assertEquals(original.texte(), i.texte());
        Referenz.pruefe("entladen-musterlageplan-2020.txt", i.zusammenfassung());
    }

    @Test
    void rueckwegNachGk4() {
        Inhalt i = lese("request", "Read", "SID", "e7", "Auftrag", String.valueOf(auftrag), "f22", "2398");
        assertEquals(List.of("urn:adv:crs:DE_42-83_3GK4"), i.lagesysteme());
        assertEquals(original.texte(), i.texte());
        double[] d = Inhalt.abweichung(original.punkte(), i.punkte());
        assertTrue(d[0] <= 0.0015 && d[1] <= 0.001, "Abweichung Lage " + d[0] + ", Höhe " + d[1]);
    }

    @Test
    void utm33() {
        Inhalt i = lese("request", "Read", "SID", "e8", "Auftrag", String.valueOf(auftrag), "f22", "25833");
        assertEquals(List.of("urn:adv:crs:ETRS89_UTM33"), i.lagesysteme());
        assertEquals(original.anzahl(), i.anzahl());
    }

    @Test
    void hoehensystemWirdNurBenannt() {
        // Bekannt: Entladen nach DHHN92 nennt das Höhensystem, rechnet die Höhen aber nicht
        // um (doku/Schnittstelle.md, 8). Festgehaltenes Verhalten.
        Inhalt i = lese("request", "Read", "SID", "e9", "Auftrag", String.valueOf(auftrag), "f23", "160");
        assertTrue(i.lagesysteme().contains("urn:adv:crs:ETRS89_UTM32*urn:adv:crs:DE_DHHN92_NH"),
                i.lagesysteme().toString());
        List<double[]> p = i.punkte();
        List<double[]> q = Inhalt.von(u.get("request", "Read", "SID", "e10", "Auftrag", String.valueOf(auftrag))
                .inhalt()).punkte();
        assertEquals(q.size(), p.size());
        for (int k = 0; k < p.size(); k++) {
            assertEquals(Inhalt.format(q.get(k)), Inhalt.format(p.get(k)));
        }
    }

    @Test
    void flaeche() {
        String[] e = u.sql("select st_xmin(e) || '|' || st_ymin(e) || '|' || st_xmax(e) || '|' || st_ymax(e) from"
                + " (select st_extent(punktgeometrie) e from oks_2022.allgemeines_punktobjekt where _jobnr_ = "
                + auftrag + ") x").split("\\|");
        double x0 = Double.parseDouble(e[0]) - 1;
        double x1 = Double.parseDouble(e[2]) + 1;
        double ym = (Double.parseDouble(e[1]) + Double.parseDouble(e[3])) / 2;
        double y1 = Double.parseDouble(e[3]) + 1;
        String area = String.format(Locale.ROOT, "SRID=25832;POLYGON((%.3f %.3f,%.3f %.3f,%.3f %.3f,%.3f %.3f,%.3f %.3f))",
                x0, ym, x1, ym, x1, y1, x0, y1, x0, ym);
        // Mit dem Auftrag verknüpft: Die Fläche allein umfasst auch Aufträge anderer Tests
        Inhalt i = lese("request", "Read", "SID", "e11", "Auftrag", String.valueOf(auftrag), "Area", area);
        assertTrue(i.anzahl() > 0 && i.anzahl() < original.anzahl(), "Objekte: " + i.anzahl());
        Referenz.pruefe("entladen-musterlageplan-flaeche.txt", i.zusammenfassung());
    }

    @Test
    void themaLiefertNichts() {
        // Bekannt: Entladen nach Thema 1 bis 9 fragt eine fehlende Spalte ab und liefert
        // eine leere Datei (doku/Schnittstelle.md, 8). Festgehaltenes Verhalten.
        assertEquals(0, lese("request", "Read", "SID", "e12", "Auftrag", String.valueOf(auftrag), "Thema", "5")
                .anzahl());
    }

    @Test
    void ohneBedingungLeer() {
        assertEquals(0, lese("request", "Read", "SID", "e13").anzahl());
        assertEquals(0, lese("request", "Read", "SID", "e14", "Projekt", "unbekannt").anzahl());
    }

    @Test
    void fehlerfaelle() {
        assertEquals(500, u.get("request", "Read", "SID", "e15", "Auftrag", String.valueOf(auftrag),
                "Version", "9.999").status());
        assertEquals(500, u.get("request", "Read", "SID", "e16", "Auftrag", "abc").status());
        Antwort srs = u.get("request", "Read", "SID", "e17", "Auftrag", String.valueOf(auftrag), "f22", "4711");
        assertEquals(200, srs.status());
        assertTrue(srs.text().contains("\"success\": false"), srs.text());
        Antwort sid = u.get("request", "Read", "SID", "../x", "Auftrag", String.valueOf(auftrag));
        assertTrue(sid.text().contains("\"success\": false"), sid.text());
    }

    @Test
    void asynchron() {
        Antwort a = u.formular(Map.of("holeDatei", "read", "SID", "easync", "TS", "2026-11-12-12-00-00",
                "Auftrag", String.valueOf(auftrag), "f4", "12.11.2026", "username", "test"));
        assertEquals(200, a.status());
        assertTrue(a.erfolg(), a.text());
        assertEquals("success", u.warteAufEntladen("easync"));
        Antwort datei = u.abrufen("/vermdb/download/easync/OkstraUnload.xml");
        assertEquals(200, datei.status());
        assertEquals(utm32, Inhalt.von(datei.inhalt()).zusammenfassung());
        assertEquals(200, u.abrufen("/vermdb/download/easync/OkstraUnload.log").status());
    }
}
