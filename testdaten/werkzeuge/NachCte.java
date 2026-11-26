// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

import de.okstra.oklabi.AusgabeKonverter;
import de.okstra.oklabi.CTEAusgabeKonverter;
import de.okstra.oklabi.DateiAusgabe;
import de.okstra.oklabi.DateiEingabe;
import de.okstra.oklabi.Datenbestand;
import de.okstra.oklabi.OklabiLoader;
import de.okstra.oklabi.Umgebung;
import de.okstra.oklabi.XMLEingabeKonverter;
import de.okstra.oklabi.eEinstellung;

/**
 * Schreibt eine OKSTRA-XML-Datei als CTE, mit der Geometrie als EXPRESS-Objekte
 * ({@code pExpressGeometrie}); ohne diese Einstellung enthält die CTE-Datei keine
 * Geometrie. Läuft im Container der Vermessungsdatenbank, siehe README.md.
 */
public class NachCte {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Aufruf: NachCte <eingabe.xml> <ausgabe.cte>");
            System.exit(2);
        }
        if (!OklabiLoader.load()) {
            throw new IllegalStateException("Die native OKLABI wurde nicht geladen: " + OklabiLoader.MESSAGE);
        }
        Umgebung.SetzeEinstellung(eEinstellung.pExpressGeometrie, true);
        DateiEingabe ein = DateiEingabe.Erzeuge(args[0]);
        XMLEingabeKonverter lesen = XMLEingabeKonverter.Erzeuge(ein);
        Datenbestand bestand = lesen.Lade();
        DateiAusgabe aus = DateiAusgabe.Erzeuge(args[1]);
        AusgabeKonverter schreiben = CTEAusgabeKonverter.Erzeuge(aus);
        schreiben.Entlade(bestand);
        schreiben.Vernichte();
        aus.Vernichte();
        bestand.Vernichte();
        lesen.Vernichte();
        ein.Vernichte();
    }
}
