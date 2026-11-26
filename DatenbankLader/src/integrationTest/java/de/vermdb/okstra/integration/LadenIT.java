// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Prüfen und Laden der Testdateien: Formate, Versionen, Lagesysteme, Sonderzeichen,
 * Kreisbögen, Wiederimport und fehlerhafte Lieferungen (doku/Schnittstelle.md, 4.1, 5).
 * Jeder Test stellt eigene Aufträge ein. Die Datei mit OKSTRA-IDs wird zuerst neu und erst
 * danach erneut geladen (Wiederimport).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LadenIT {

    private final Umgebung u = Umgebung.get();

    @Test
    void pruefauftragAendertDieDatenbankNicht() {
        int nr = u.liefere("pruefen", "beispiel.xml", true);
        assertEquals(1_010_105, u.status(nr));
        Map<String, String> z = u.zeile(nr);
        assertEquals("geprüft", z.get("Status"));
        assertEquals("2.022", z.get("Version Datensatz"));
        assertEquals("3.5 Sachsen-Anhalt", z.get("Fachbedeutungen"));
        assertEquals("Lagestatus 150", z.get("KoordRefSystem"));
        assertEquals("fehlt", z.get("Übertragung"));
        assertEquals(Map.of(), u.objekte(nr));
        Referenz.pruefe("pruefen-beispiel-protokoll.txt", u.verlinkt(z.get("Laden")).text());
    }

    @Test
    void ladenMitTransformationAusGk4() {
        int nr = u.liefere("laden", "beispiel.xml", false);
        assertEquals(1_010_100, u.status(nr));
        Map<String, String> z = u.zeile(nr);
        assertEquals("geladen", z.get("Status"));
        Referenz.pruefe("laden-beispiel-objekte.txt", text(u.objekte(nr)));
        Referenz.pruefe("laden-beispiel-protokoll.txt", u.verlinkt(z.get("Laden")).text());
        String uebertragung = u.verlinkt(z.get("Übertragung")).text();
        assertTrue(uebertragung.contains("Transformiere Koordinatenreferenzsystem von [Pulkowo 42/83 3GK4] nach [UTM 32 Nord (ETRS89)]"),
                uebertragung);
        // Das eingeschaltete Gitter des Image wird verwendet
        assertTrue(uebertragung.contains("Gitterdatei: ") && uebertragung.contains(Umgebung.GITTER + ".gsb"),
                uebertragung);
        Referenz.pruefe("laden-beispiel-uebertragung.txt", uebertragung);
        // Texte mit Sonderzeichen unverändert in der Datenbank
        List<String> texte = u.sql("select text from oks_2022.beschriftung where _jobnr_ = " + nr).lines().toList();
        assertTrue(texte.contains("Wasserturmstraße"), texte.toString());
        assertTrue(texte.contains("100%<br>A&O<br>1500€<br>alpha@example.org<br>90°"), texte.toString());
        // Lage in UTM 32, mit dem Gitter NTv2LSBB_LSA gerechnet; Stichprobe Flächenobjekt.
        // Ohne Gitter (Helmert-Parameter der proj.db) 688614.65 5740983.12; mit PROJ (cct,
        // hgridshift) aus dem darauf zurückgerechneten Punkt etwa 688614.654 5740983.095
        assertEquals("POINT Z (688614.66 5740983.1 0)", u.sql(
                "select st_astext(st_snaptogrid(st_pointn(st_exteriorring(st_geometryn(flaechengeometrie, 1)), 1), 0.01))"
                        + " from oks_2022.allgemeines_flaechenobjekt where _jobnr_ = " + nr));
    }

    @Test
    void gitterDesImageEingeschaltet() {
        // VERMDB_GITTER_IMAGE in der .env: vermdb.sh stellt das Gitter mit seinen Einträgen
        // zusammen; die übrigen Gitter des Image bleiben ausgeschaltet
        String p = u.protokoll();
        assertTrue(p.contains("Gitter des Image: " + Umgebung.GITTER), p);
        assertTrue(p.contains("Gitter: Gitterdatei=2398@25832@" + Umgebung.GITTER + ".gsb"), p);
        assertTrue(p.contains("Gitter: Gitterdatei=2398@25833@" + Umgebung.GITTER + ".gsb"), p);
        assertFalse(p.contains("HeTa2010"), p);
    }

    @Test
    void kreisboegenBehaltenIhreHoehen() {
        // Behelf für die OKLABI: Linie aus Strecke und zwei Bögen, Höhen 70 bis 72 m
        int nr = u.liefere("boegen", "beispiel.xml", false);
        assertEquals("70|72", u.sql("select st_zmin(liniengeometrie), st_zmax(liniengeometrie)"
                + " from oks_2022.allgemeines_linienobjekt where _jobnr_ = " + nr
                + " and fachliche_bedeutung like '5296.F002%'"));
        assertTrue(u.verlinkt(u.zeile(nr).get("Übertragung")).text().contains(
                "Höhen in 1 Linie(n) mit Kreisbögen oder Splines aus den Komponenten übernommen"));
    }

    @Test
    void musterlageplan() {
        int nr = u.liefere("muster", "musterlageplan.xml", false);
        assertEquals(1_010_100, u.status(nr));
        Map<String, String> z = u.zeile(nr);
        Referenz.pruefe("laden-musterlageplan-objekte.txt", text(u.objekte(nr)));
        Referenz.pruefe("laden-musterlageplan-protokoll.txt", u.verlinkt(z.get("Laden")).text());
        String uebertragung = u.verlinkt(z.get("Übertragung")).text();
        assertTrue(uebertragung.contains(
                "Höhen in 8 Linie(n) mit Kreisbögen oder Splines aus den Komponenten übernommen"), uebertragung);
        List<String> texte = u.sql("select text from oks_2022.beschriftung where _jobnr_ = " + nr).lines().toList();
        assertTrue(texte.contains("ä ö ü ß @ ! &quot; § % & / ( ) {} [] = ? ~ m² m³"), texte.toString());
        assertTrue(texte.contains("Hauptstraße"), texte.toString());
        // Keine Linie mit Höhe 0 neben Höhen über 0 (Bogenpunkte ohne Behelf)
        assertEquals("0", u.sql("select count(*) from oks_2022.allgemeines_linienobjekt where _jobnr_ = " + nr
                + " and st_zmin(liniengeometrie) = 0 and st_zmax(liniengeometrie) > 0"));
    }

    @Test
    void cteWirdAbgewiesen() {
        // CTE bildet die Geometrie mit Objekten des Geometrieschemas ab, die die OKLABI nur
        // mit der prozessweiten Einstellung ExpressGeometrie liest; ohne sie gingen alle
        // Geometrien verloren. Lieferungen als CTE weist die Eingabeprüfung daher ab.
        for (String f1 : new String[] {"CTE", "cte", ""}) {
            Map<String, String> f = new LinkedHashMap<>(Map.of("pruefeDatei", "1", "SID", "cte", "f1", f1,
                    "f7", "3.5", "f8", "Sachsen-Anhalt", "f20", "laden", "f22", "2398", "f23", "170"));
            Antwort a = u.multipart(f, u.testdatei("beispiel.cte"));
            assertEquals(200, a.status());
            assertFalse(a.erfolg(), a.text());
            assertTrue(a.text().contains("Lieferungen im Format CTE werden nicht unterst&uuml;tzt"), a.text());
        }
    }

    @Test
    void ohneFormatangabeNachEndung() {
        // Ohne f1 entscheidet die Endung; .xml wird als XML gelesen (bisher als CTE)
        int nr = u.liefere("ohnef1", "beispiel.xml", false, "f1", "");
        assertEquals(1_010_100, u.status(nr));
        assertEquals(text(u.objekte(u.liefere("mitf1", "beispiel.xml", false))), text(u.objekte(nr)));
    }

    @Test
    @Order(1)
    void andereVersionUndLagesystemMitOkstraId() throws IOException {
        // Aus dem Musterlageplan abgeleitet: OKSTRA 2.020, UTM 33, mit OKSTRA-IDs
        int gk4 = u.liefere("utm33basis", "musterlageplan.xml", false);
        int nr = u.liefere("utm33", "musterlageplan-2020-utm33.xml", false, "f22", "25833");
        assertEquals(1_012_100, u.status(nr));
        Map<String, String> z = u.zeile(nr);
        assertEquals("2.020", z.get("Version Datensatz"));
        assertEquals("UTM 33 Nord (ETRS89)", z.get("KoordRefSystem"));
        assertTrue(z.get("Migration").contains("/vermdb/log/"), z.toString());
        assertEquals(u.objekte(gk4), u.objekte(nr));
        // Gleiche Lage wie aus GK 4 geladen, bis auf Rundung
        double[] d = Inhalt.abweichung(punkte(nr), punkte(gk4));
        assertTrue(d[0] < 0.002 && d[1] < 0.002, "Abweichung Lage " + d[0] + ", Höhe " + d[1]);
        // OKSTRA-IDs der Datei bleiben erhalten
        String datei = Files.readString(u.testdatei("musterlageplan-2020-utm33.xml"));
        Matcher m = Pattern.compile("<okstra:OKSTRA_ID>([0-9A-F]{32})</okstra:OKSTRA_ID>").matcher(datei);
        String id = m.find() ? m.group(1) : "";
        assertFalse(id.isEmpty());
        assertEquals("1", u.sql("select count(*) from (" + alleIds(nr) + ") x where okstra_id = '" + id + "'"));
    }

    @Test
    @Order(2)
    void wiederimportScheitertAmBekanntenFehler() {
        // Bekannter Fehler (W1, OkstraSqlUtil): Der Wiederimport vorhandener Objekte
        // scheitert an einem UPDATE ohne Spaltennamen. Festgehaltenes Verhalten.
        u.liefere("wieder1", "musterlageplan-2020-utm33.xml", false, "f22", "25833");
        int nr = u.liefere("wieder2", "musterlageplan-2020-utm33.xml", false, "f22", "25833");
        String uebertragung = u.verlinkt(u.zeile(nr).get("Übertragung")).text();
        assertTrue(uebertragung.contains("syntax error"), uebertragung);
        // Die interne Variante setzt nach dem Fehler fort, die Release-Variante bricht ab.
        // Die Kennungen sind laufende Nummern der OKLABI im Prozess, daher vereinheitlicht.
        String fehler = uebertragung.lines()
                .filter(l -> l.contains("Fehler") || l.contains("ERROR") || l.contains("abgebrochen"))
                .map(l -> l.replaceAll("(Kennung|Fachobjekts) [0-9]+", "$1 <N>"))
                .distinct().collect(Collectors.joining("\n"));
        Referenz.pruefe("laden-wiederimport-" + u.variante() + ".txt",
                "Status " + u.status(nr) + "\n" + text(u.objekte(nr)) + "\n" + fehler + "\n");
    }

    @Test
    void unzulaessigeKennungFuehrtZurAblehnung() {
        int nr = u.liefere("kennung", "fehler-kennung.xml", false);
        assertEquals(-5, u.status(nr));
        Map<String, String> z = u.zeile(nr);
        assertEquals("abgelehnt", z.get("Status"));
        assertEquals("failed\nLieferung abgelehnt wegen unzulässiger Kennungen, siehe Prüfprotokoll",
                u.erledigt("kennung"));
        assertTrue(u.verlinkt(z.get("Laden")).text().contains(
                "Unzulässige Kennung \"04;0\" für Baumgattung.Kennung"));
        assertEquals(Map.of(), u.objekte(nr));
    }

    @Test
    void ungueltigeFachbedeutungAlsDatenfehler() {
        int nr = u.liefere("fachbedeutung", "fehler-fachbedeutung.xml", false);
        assertEquals(1_010_101, u.status(nr));
        Map<String, String> z = u.zeile(nr);
        assertEquals("geladen, mit Meldungen", z.get("Status"));
        assertTrue(u.verlinkt(z.get("Laden")).text().contains(
                "die fachliche Bedeutung [0000.X999] wurde für kein Bundesland in der Liste"
                        + " [Fachbedeutungen Sachsen-Anhalt 3.5] gefunden"));
        Inhalt fehler = Inhalt.von(u.verlinkt(z.get("Datenfehler")).inhalt());
        Referenz.pruefe("laden-fachbedeutung-datenfehler.txt", fehler.zusammenfassung());
        // Das fehlerhafte Objekt wird trotzdem geladen
        Referenz.pruefe("laden-fachbedeutung-objekte.txt", text(u.objekte(nr)));
    }

    @Test
    void schemafehler() {
        int nr = u.liefere("schema", "fehler-schema.xml", false);
        assertEquals(1_010_101, u.status(nr));
        Map<String, String> z = u.zeile(nr);
        assertTrue(u.verlinkt(z.get("Laden")).text().contains("An error has occurred while parsing/validating"));
        Inhalt fehler = Inhalt.von(u.verlinkt(z.get("Datenfehler")).inhalt());
        Referenz.pruefe("laden-schema-datenfehler.txt", fehler.zusammenfassung());
    }

    @Test
    void lieferungOhneDatei() {
        Antwort a = u.multipart(Map.of("pruefeDatei", "1", "SID", "ohnedatei", "f1", "XML", "f7", "3.5",
                "f8", "Sachsen-Anhalt", "f20", "laden", "f22", "2398"), null);
        assertEquals(200, a.status());
        assertFalse(a.erfolg(), a.text());
        Referenz.pruefe("laden-ohne-datei.txt", a.text());
    }

    @Test
    void lieferungMitUngueltigerSid() {
        Map<String, String> f = new LinkedHashMap<>(Map.of("pruefeDatei", "1", "SID", "a/b", "f1", "XML",
                "f7", "3.5", "f8", "Sachsen-Anhalt", "f20", "laden", "f22", "2398"));
        Antwort a = u.multipart(f, u.testdatei("beispiel.xml"));
        assertFalse(a.erfolg(), a.text());
        assertTrue(a.text().contains("Session-ID enth"), a.text());
    }

    @Test
    void lieferungOhneLagesystem() {
        // Bekannt: ohne f22 bricht der Dienst mit HTTP 500 ab (doku/Schnittstelle.md, 8)
        Antwort a = u.multipart(Map.of("pruefeDatei", "1", "SID", "ohnef22", "f1", "XML", "f7", "3.5",
                "f8", "Sachsen-Anhalt", "f20", "laden"), u.testdatei("beispiel.xml"));
        assertEquals(500, a.status(), a.text());
    }

    // ------------------------------------------------------------------ Hilfen

    /** Geometriespalten der Tabellen mit Auftragsnummer (ohne Sichten). */
    static final String GEOMETRIESPALTEN = "select g.f_table_name || '|' || g.f_geometry_column"
            + " from geometry_columns g join information_schema.tables t on t.table_schema = g.f_table_schema"
            + " and t.table_name = g.f_table_name and t.table_type = 'BASE TABLE'"
            + " join information_schema.columns c on c.table_schema = g.f_table_schema"
            + " and c.table_name = g.f_table_name and c.column_name = '_jobnr_'"
            + " where g.f_table_schema = 'oks_2022'";

    static String text(Map<String, Integer> objekte) {
        return objekte.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n", "", "\n"));
    }

    private String alleIds(int auftrag) {
        return u.sql("select table_name from information_schema.columns where table_schema = 'oks_2022'"
                        + " and column_name = 'okstra_id' intersect select table_name from information_schema.columns"
                        + " where table_schema = 'oks_2022' and column_name = '_jobnr_'")
                .lines().map(t -> "select okstra_id from oks_2022.\"" + t + "\" where _jobnr_ = " + auftrag)
                .collect(Collectors.joining(" union all "));
    }

    /** Alle Punkte der Geometrien eines Auftrags. */
    private List<double[]> punkte(int auftrag) {
        String spalten = u.sql(GEOMETRIESPALTEN);
        String abfrage = spalten.lines().map(z -> z.split("\\|"))
                .map(t -> "select (st_dumppoints(\"" + t[1] + "\")).geom as p from oks_2022.\"" + t[0]
                        + "\" where _jobnr_ = " + auftrag)
                .collect(Collectors.joining(" union all "));
        return u.sql("select st_x(p) || ' ' || st_y(p) || ' ' || coalesce(st_z(p)::text, 'NaN') from (" + abfrage + ") x")
                .lines().map(l -> l.split(" "))
                .map(t -> new double[] {Double.parseDouble(t[0]), Double.parseDouble(t[1]), Double.parseDouble(t[2])})
                .toList();
    }
}
