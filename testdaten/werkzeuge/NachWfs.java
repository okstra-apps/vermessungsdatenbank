// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

import de.okstra.oklabi.DateiAusgabe;
import de.okstra.oklabi.DateiEingabe;
import de.okstra.oklabi.Datenbestand;
import de.okstra.oklabi.OklabiLoader;
import de.okstra.oklabi.XMLAusgabeKonverter;
import de.okstra.oklabi.XMLEingabeKonverter;
import de.okstra.oklabi.eKodierung;
import de.okstra.oklabi.eKoordRefSys;

/**
 * Schreibt eine OKSTRA-XML-Datei im WFS-Format (wfs:FeatureCollection), UTF-8, wie das
 * OkWS-Backend der Vermessungsdatenbank antwortet. Damit entsteht die Vorgabeantwort auf
 * die Anfrage SignOfLife des OkWS (okws/beispiel-wfs.xml) aus
 * testdaten/beispiel.xml. Die Datei nennt ihr Koordinatensystem nicht; wie beim Laden mit
 * f22=2398 und f23=170 gilt Gauß-Krüger 4 (42/83) mit DHHN2016, damit die Geometrien
 * srsName tragen wie in den Antworten aus der Datenbank. Läuft im Container der
 * Vermessungsdatenbank, siehe README.md.
 */
public class NachWfs {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Aufruf: NachWfs <eingabe.xml> <ausgabe.xml>");
            System.exit(2);
        }
        if (!OklabiLoader.load()) {
            throw new IllegalStateException("Die native OKLABI wurde nicht geladen: " + OklabiLoader.MESSAGE);
        }
        DateiEingabe ein = DateiEingabe.Erzeuge(args[0]);
        XMLEingabeKonverter lesen = XMLEingabeKonverter.Erzeuge(ein);
        Datenbestand bestand = lesen.Lade();
        bestand.InitialisiereKoordRefSys(eKoordRefSys.Pulk42_83_34);
        bestand.InitialisiereHoehensystem(eKoordRefSys.HSYS_DE_DHHN_2016_NH);
        DateiAusgabe aus = DateiAusgabe.Erzeuge(args[1]);
        aus.SetzeKodierung(eKodierung.UTF8);
        XMLAusgabeKonverter schreiben = XMLAusgabeKonverter.Erzeuge(aus);
        schreiben.SetzeZeichensatz("UTF-8");
        schreiben.EntladeWFSFormat(true);
        schreiben.Entlade(bestand);
        schreiben.Vernichte();
        aus.Vernichte();
        bestand.Vernichte();
        lesen.Vernichte();
        ein.Vernichte();
    }
}
