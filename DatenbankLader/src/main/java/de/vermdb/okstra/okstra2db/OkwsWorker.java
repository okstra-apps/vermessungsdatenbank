/*
 * SPDX-FileCopyrightText: 2024-2026 interactive instruments GmbH
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package de.vermdb.okstra.okstra2db;

import de.okstra.oklabi.AnyType;
import de.okstra.oklabi.Ausnahme;
import de.okstra.oklabi.Eigenschaft;
import de.okstra.oklabi.Enumerator;
import de.okstra.oklabi.Objektart;
import de.okstra.oklabi.Verarbeiter;
import de.okstra.oklabi.eMeldTyp;
import de.okstra.oklabi.eProtokollTyp;
import de.okstra.oklabi.eTyp;
import de.okstra.sql.util.Db2Okstra;
import de.okstra.sql.util.DbMetadaten;
import de.okstra.sql.util.Okstra2Db;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Die Klasse implementiert einen Worker, der Anfragen aus
 * dem Kommunikationskanal empfängt, verarbeitet und das
 * Ergebnis zurückliefert.
 * @author Vogelsang
 * 
 * 2024-05-24 Default Worker für OkWS-Tests
 * 2024-07-09 Parameterauswertung gemäß Konzept
 * 2024-07-10 Worker für OkWS-Anschluss integriert
 * 2024-07-15 Mehrfachnutzung von Parameternamen in Kommunikationskanal
 * 2024-11-22 Neue Parameter OkstraVersion, wfsExpected für Backend im OkWS
 * 
*/
public class OkwsWorker extends Verarbeiter {
    private final static String DELIMITER = "$@$";
    private final static String DELIMITER_SPLIT = "\\$\\@\\$";
    private final Okstra2DbServlet servlet;
    private final String defaultResponse;
    private final de.okstra.oklabi.Version dbVersion;
    private final int srid;
    private final Map<String, String[]> conditionMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private String propName;
    private final boolean bZipResponse = false;
    
    /**
     * Der Worker kennt den Pfad eines Standard-Response
     * @param service
     * @param path Dateiname für Response
     * @param version OKSTRA-Version
     * @param srs
     */
    public OkwsWorker(Okstra2DbServlet service, String path, de.okstra.oklabi.Version version, int srs) {
        servlet = service;
        defaultResponse = path;
        dbVersion = version;
        srid = srs;
    }
    
    /**
     * Jeder Request wird auf einer Kopie des registrierten
     * Verarbeiters ausgeführt. Diese Methode stellt das
     * Wirtsobjekt für die Bearbeitung des Requests bereit.
     * @return Worker-Objekt
     */
    @Override
    public Verarbeiter Kopiere() {
        return new OkwsWorker(this.servlet, this.defaultResponse, this.dbVersion, this.srid);
    } 
    
    /**
     * Zentrale Methode am Verarbeiter, die die Funktionalität
     * implementiert. In der ersten Version liefert dieser
     * Verarbeiter stets dieselbe Datei als Ergebnis ab.
     * @return Rückgabewert für die rufende Stelle
     */
    @Override
    public String FuehreAus()
    {
        try {
            TreeMap<String, String> reqData = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            String res = parseParameters(reqData);
            if (res != null && res.contains("Fehler")) {
                return erzeugeReport(res);
            }
            if (reqData.containsKey("OKWS_TEST")) {
                // Operation SignOfLife, Default-Response liefern
                if (defaultResponse != null) {
                    return readFile(defaultResponse, bZipResponse, true);
                }
            } else {
                PostRequestContent postReq = new PostRequestContent();
                // Wir gehen hier unkonditional auf die Bestandsdatenbank
                postReq.addConnection(servlet.getOkstraConnection(), servlet.getVermdbConnection(),
                        servlet.getDbVersion(), servlet.getOkstraVersion());
                File ausgabe = File.createTempFile("readOut", (bZipResponse ? ".gz" : ".tmp"));
                servlet.readRequest(null, postReq, reqData, ausgabe);
                return readFile(ausgabe.getAbsolutePath(), bZipResponse, false);
            }
        } catch (Ausnahme ex) {
            return erzeugeReport(ex);
        } catch (Exception ex) {
            return erzeugeReport(ex.getMessage());
        } finally {
        }
        return erzeugeReport("Keine Daten gefunden.");
    }

    /**
     * 
     * @param file
     * @return
     * @throws IOException 
     */
    private String readFile(String file, boolean bZipped, boolean bKeep) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        File datei = new File(file);
        if (bZipped) {
            int lng = 32 * 1024;
            byte[] bytes = new byte[lng];
            try (FileInputStream fis = new FileInputStream(datei)) {
                int anz;
                while ((anz = fis.read(bytes, 0, lng)) > 0) {
                    for (int pos = 0; pos < anz; pos++) {
                        stringBuilder.append((char)bytes[pos]);
                    }
                }
            }
        } else {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                String ls = System.getProperty("line.separator");
                while((line = reader.readLine()) != null) {
                    stringBuilder.append(line);
                    stringBuilder.append(ls);
                }
            }
        }
        if (!bKeep) {
            datei.delete();
        }
        return stringBuilder.toString();
    }
    
    private String erzeugeReport(Ausnahme aus) {
        try {
            return erzeugeReport(aus.GibMeldungstext());
        } catch (Ausnahme ex) {
        }
        return erzeugeReport("Unknown Error");
    }
    
    private String erzeugeReport(String msg) {
        try {
            Db2Okstra.protokolliere(-1, "Ausnahme bei Bearbeitung einer Anfrage: " + msg,
                    eMeldTyp.warnung, eProtokollTyp.meldungen);
        } catch (Ausnahme ex) {
        }
        String ls = System.getProperty("line.separator");
        String res = "<?xml version=\"1.0\" ?>" + ls;
        res += "<ExceptionReport version=\"2.0.2\"" + ls;
        res += "   xmlns=\"http://www.opengis.net/ows/1.1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" + ls;
        res += "   xsi:schemaLocation=\"http://www.opengis.net/ows/1.1 http://schemas.opengis.net/ows/1.1.0/owsAll.xsd\">" + ls;
        res += "   <Exception exceptionCode=\"OperationProcessingFailed\" locator=\"GetFeature\">" + ls;
        res += "      <ExceptionText>" + msg + "</ExceptionText>" + ls;
        res += "   </Exception>" + ls;
        res += "</ExceptionReport>";
        return res;
    }

    /**
     * Vorbelegung des Zählers für die erkannten Schlüssel
     * @return Keymap
     */
    private Map<String, Integer> initializeMap() {
        HashMap<String, Integer> paramMap = new HashMap<>();
        paramMap.put("EQ", 0);
        paramMap.put("NE", 0);
        paramMap.put("GT", 0);
        paramMap.put("GE", 0);
        paramMap.put("LT", 0);
        paramMap.put("LE", 0);
        paramMap.put("LIKE", 0);
        paramMap.put("INTERSECT", 0);
        paramMap.put("OPERATION", 0);
        paramMap.put("TYPE", 0);
        paramMap.put("FILTERTYPE", 0);
        paramMap.put("SRS", 0);
        paramMap.put("TEST", 0);
        paramMap.put("ID", 0);
        paramMap.put("MAXFEATURES", 0);
        paramMap.put("OKSTRAVERSION", 0);
        paramMap.put("WFSEXPECTED", 0);
        return paramMap;
    }
    
    /**
     * Untersucht die Parameter der Anfrage.
     * @param reqData Tabelle mit Parameterwerten
     * @return Meldungstext
     * @throws Exception 
     */
    private String parseParameters(Map<String, String> reqData) throws Exception {
        String res = "";
        String req = "";
        String diagCondition = "";
        StarteParameterLesen();
        AnyType any = GibParameternamen();
        Enumerator enumer = any.GibEnumerator();
        String operation = "";
        propName = "";
        String featType = "";
        String filtType = "";
        String srsName = "";
        String idFilt = "";
        String mxFeat = "";
        String readVersion = "";
        String wfsResponse = "";
        boolean bSignOfLife = false;
        int oksVsnValid = 0;
        int wfsFlagValid = 0;
        int countNotID = 0;
        String lastKey = "<unbekannt>";
        final Map<String, Integer> paramMap = initializeMap();
        for (; !enumer.IstBeendet(); enumer.Weiter())
        {
            String k = (String)enumer.GibObjekt();
            String w = GibParameterwert(k);
            // Aufsammeln fürs Protokoll
            req += " Key: \"" + k + "\" Value: \"" + (w.length() > 100 ? w.substring(0, 96) + "..." : w) + "\"";
            
            // Schlüssel analysieren
            String key = k.toUpperCase(Locale.GERMANY);
            String tag;
            if (key.startsWith("EQ_")) {
                ++countNotID;
                paramMap.put("EQ", paramMap.get("EQ")+1);
                diagCondition = decodeParam(k, w, "=", key.substring(3), lastKey, diagCondition);
            } else if (key.startsWith("NE_")) {
                ++countNotID;
                paramMap.put("NE", paramMap.get("NE")+1);
                diagCondition = decodeParam(k, w, "<>", key.substring(3), lastKey, diagCondition);
            } else if (key.startsWith("GT_")) {
                ++countNotID;
                paramMap.put("GT", paramMap.get("GT")+1);
                diagCondition = decodeParam(k, w, ">", key.substring(3), lastKey, diagCondition);
            } else if (key.startsWith("GE_")) {
                ++countNotID;
                paramMap.put("GE", paramMap.get("GE")+1);
                diagCondition = decodeParam(k, w, ">=", key.substring(3), lastKey, diagCondition);
            } else if (key.startsWith("LT_")) {
                ++countNotID;
                paramMap.put("LT", paramMap.get("LT")+1);
                diagCondition = decodeParam(k, w, "<", key.substring(3), lastKey, diagCondition);
            } else if (key.startsWith("LE_")) {
                ++countNotID;
                paramMap.put("LE", paramMap.get("LE")+1);
                diagCondition = decodeParam(k, w, "<=", key.substring(3), lastKey, diagCondition);
            } else if (key.startsWith("LIKE_")) {
                ++countNotID;
                paramMap.put("LIKE", paramMap.get("LIKE")+1);
                diagCondition = decodeParam(k, w, "LIKE", key.substring(5), lastKey, diagCondition);
            } else if (key.startsWith("INTERSECT_")) {
                ++countNotID;
                paramMap.put("INTERSECT", paramMap.get("INTERSECT")+1);
                diagCondition = decodeParam(k, w, "INTERSECT", key.substring("INTERSECT_".length()), lastKey, diagCondition);
            } else {
                switch(key) {
                    case "OPERATION":
                        paramMap.put("OPERATION", paramMap.get("OPERATION")+1);
                        if (!operation.isEmpty()) {
                            res += (!res.isEmpty() ? ", " : "") +
                                    "Warnung: Operation mehrfach angegeben.";
                        }
                        operation = w;
                        break;
                    case "TYPE":
                        paramMap.put("TYPE", paramMap.get("TYPE")+1);
                        if (!featType.isEmpty()) {
                            res += (!res.isEmpty() ? ", " : "") +
                                    "Fehler: Type mehrfach angegeben.";
                        }
                        featType = w;
                        break;
                    case "FILTERTYPE":
                        paramMap.put("FILTERTYPE", paramMap.get("FILTERTYPE")+1);
                        if (!filtType.isEmpty()) {
                            res += (!res.isEmpty() ? ", " : "") +
                                    "Warnung: FilterType mehrfach angegeben.";
                        }
                        filtType = w;
                        break;
                    case "MAXFEATURES":
                        paramMap.put("MAXFEATURES", paramMap.get("MAXFEATURES")+1);
                        if (!mxFeat.isEmpty()) {
                            res += (!res.isEmpty() ? ", " : "") +
                                    "Warnung: maximale Feature-Anzahl mehrfach angegeben.";
                        }
                        mxFeat = w;
                        break;
                    case "SRS":
                        paramMap.put("SRS", paramMap.get("SRS")+1);
                        if (!srsName.isEmpty()) {
                            res += (!res.isEmpty() ? ", " : "") +
                                    "Warnung: Koordinatenrefenzsystem mehrfach angegeben.";
                        }
                        srsName = w;
                        break;
                    case "ID":
                        paramMap.put("ID", paramMap.get("ID")+1);
                        if (!idFilt.isEmpty()) {
                            if (!res.contains("ID-Filter")) {
                                res += (!res.isEmpty() ? ", " : "") +
                                        "Warnung: Parameter für ID-Filter mehrfach angegeben.";
                            }
                            if (!idFilt.endsWith(DELIMITER))
                                idFilt += DELIMITER;
                        }
                        idFilt += w;
                        break;
                    case "TEST":
                        ++countNotID;
                        paramMap.put("TEST", paramMap.get("TEST")+1);
                        if (bSignOfLife) {
                            res += (!res.isEmpty() ? ", " : "") +
                                    "Warnung: Test mehrfach angegeben.";
                        }
                        bSignOfLife = true;
                        break;
                    case "OKSTRAVERSION":
                        paramMap.put("OKSTRAVERSION", paramMap.get("OKSTRAVERSION")+1);
                        if (!readVersion.isEmpty()) {
                            if (!res.contains("OKSTRA-Version")) {
                                res += (!res.isEmpty() ? ", " : "") +
                                        "Warnung: Parameter für OKSTRA-Version mehrfach angegeben.";
                            }
                            oksVsnValid = 2;
                        }
                        de.okstra.oklabi.Version vsn = DbMetadaten.DekodiereVersion(w);
                        if (vsn == null) {
                            res += (!res.isEmpty() ? ", " : "") +
                                "Warnung: Parameter " + w + " für OKSTRA-Version ist falsch angegeben.";
                            oksVsnValid = -1;
                        } else {
                            vsn.Vernichte();
                            oksVsnValid = 1;
                        }
                        readVersion += w;
                        break;
                    case "WFSEXPECTED":
                        paramMap.put("WFSEXPECTED", paramMap.get("WFSEXPECTED")+1);
                        if (!wfsResponse.isEmpty()) {
                            if (!res.contains("WFS-Response-Format")) {
                                res += (!res.isEmpty() ? ", " : "") +
                                        "Warnung: Parameter für WFS-Response-Format mehrfach angegeben.";
                            }
                            wfsFlagValid = 2;
                        }
                        if ("0".equals(w) || "nein".equalsIgnoreCase(w) || "false".equalsIgnoreCase(w)) {
                            wfsResponse += "0";
                            wfsFlagValid = 1;
                        } else if ("1".equals(w) || "ja".equalsIgnoreCase(w) || "true".equalsIgnoreCase(w)) {
                            wfsResponse += "1";
                            wfsFlagValid = 1;
                        } else {
                            res += (!res.isEmpty() ? ", " : "") +
                                "Warnung: Parameter für WFS-Response-Format ist falsch kodiert.";
                            wfsFlagValid = -1;
                        }
                        break;
                    default:
                        res += (!res.isEmpty() ? ", " : "") +
                                "Fehler: Parameter \"" + k + "\" wurde nicht erkannt.";
                        break;
                }
            }
            lastKey = k;
        }

        if (!propName.isEmpty()) {
            // Eigenschaften: immer paarig. Name war angegeben, Wert dazu fehlt
            diagCondition += "Fehler: Parameterreihenfolge falsch angegeben, Eigenschaftswert fehlt. Eigenschaft: " + propName + " ";
        }
        
        if (!idFilt.isEmpty() && !conditionMap.isEmpty()) {
            diagCondition += "Fehler: ID-Filter darf nicht zusammen mit anderen Bedingungen angegeben werden. ";
        }
        
        // Meldungen aus der Parameterauswertung anhängen
        if (diagCondition.contains("Fehler")) {
            res += (res.isEmpty() ? "" : ", ") + diagCondition;
        }
        
        String msg = checkSrs(srsName);
        if (!msg.isEmpty()) {
            res += (res.isEmpty() ? "" : ", ") + msg;
        }
        
        Db2Okstra.protokolliere(-1, "OkWS-Anfrage mit Parametern: " + req, eMeldTyp.info, eProtokollTyp.meldungen);
        if (!res.isEmpty()) {
            Db2Okstra.protokolliere(-1, "Meldung zur Parameterauswertung: " + res, eMeldTyp.warnung, eProtokollTyp.meldungen);
        }
        // Nachbearbeitung
        if (!res.contains("Fehler")) {
            // Feature Type muss angegeben werden außer bei Sign of Life
            if (!featType.isEmpty()) {
                Objektart oa;
                try {
                    oa = Objektart.Gib(featType, dbVersion);
                    if (oa == null || !oa.IstKonfiguriert()) {
                        res = (res.isEmpty() ? "" : ", ") + "Fehler: Type \"" + featType + "\" ist keine gültige OKSTRA-Objektart.";
                        return res;
                    }
                } catch (Ausnahme ex) {
                    res = (res.isEmpty() ? "" : ", ") + "Fehler: Type \"" + featType + "\" ist keine gültige OKSTRA-Objektart";
                    return res;
                }
                if (paramMap.get("ID") > 0 && countNotID > 0) {
                    res = (res.isEmpty() ? "" : ", ") + "Fehler: Abfrage per ID kann nicht mit anderen Bedingungen kombiniert werden";
                    return res;
                }
                reqData.put("OKWS_TYPE", featType);
                res = normalizeCondition(oa, idFilt, res);
                if (res.contains("Fehler"))
                    return res;
            } else {
                // Keine Objektart: geht nur mit Sign of Life
                if (!bSignOfLife) {
                    res = (res.isEmpty() ? "" : ", ") + "Fehler: kein FeatureType angegeben.";
                    return res;
                }
            }
            if (bSignOfLife)
                reqData.put("OKWS_TEST", "1");
            if (!operation.isEmpty())
                reqData.put("OKWS_OPERATION", operation);
            if (!filtType.isEmpty())
                reqData.put("OKWS_FILTERTYPE", filtType);
            if (!mxFeat.isEmpty())
                reqData.put("OKWS_MAXFEATURES", mxFeat);
            if (!srsName.isEmpty())
                reqData.put("OKWS_SRSNAME", srsName);
            if (oksVsnValid == 1)
                reqData.put("OKWS_VERSION", readVersion);
            else
                reqData.put("OKWS_VERSION", servlet.okstraVersion);
            if (wfsFlagValid == 1)
                reqData.put("OKWS_WFSEXPECTED", wfsResponse);
            reqData.put("OKWS_CONDITION", writeCondition());
        }
        return res;
    }
    
    /**
     * Untersucht das Koordinatenreferenzsystem für die Rückgabe
     * @param srsName
     * @return Meldungstext
     */
    private String checkSrs(String srsName) {
        String res = "";
        if (!srsName.isEmpty()) {
            if (srsName.toUpperCase(Locale.GERMANY).startsWith("EPSG:")) {
                String srs = srsName.substring("EPSG:".length());
                switch (srs) {
                    default:
                        res = "Fehler: Koordinatenreferenzsystem " + srsName + " falsch angegeben.";
                        break;
                    case "25832":
                    case "25833":
                    case "31466":
                    case "31467":
                    case "31468":
                    case "2397":
                    case "2398":
                    case "2399":
                    case "4326":
                    case "4979":
                        break;
                }
            } else {
                res = "Fehler: Koordinatenreferenzsystem " + srsName + " falsch angegeben.";
            }
        }
        return res;
    }
    
    /**
     * Dient zum Auseinandernehmen von *_NAME und *_WERT. Parameter müssen paarig auftreten
     * und sind reihenfolgenabhängig. 
     * @param key Schlüssel
     * @param w Wert
     * @param op Operation
     * @param tag Tag (NAME|WERT)
     * @param lastKey letzter Schlüssel
     * @param diag Meldung
     * @return 
     */
    private String decodeParam(String key, String w, String op, String tag, String lastKey, String diag) {
        String res = diag;
        if (tag.equals("NAME")) {
            if (!propName.isEmpty()) {
                // Eigenschaftsname darf nicht belegt sein
                res += (!res.isEmpty() ? ", " : "") +
                        "Fehler: Parameterreihenfolge (Eigenschaftsname) falsch angegeben, Vorgänger: " + lastKey;
            }
            // Eigenschaftsnamen übernehmen
            propName = w;
        } else if (tag.equals("WERT")) {
            if (propName.isEmpty()) {
                // Eigenschaftsname darf nicht fehlen
                res += (!res.isEmpty() ? ", " : "") +
                        "Fehler: Parameterreihenfolge (Eigenschaftswert) falsch angegeben, Vorgänger: " + lastKey;
            } else {
                // Eigenschaftswert übernehmen
                res += enterCondition(propName, op, w);
            }
            propName = "";
        } else {
            // Nicht erkanntes Tag
            res += (!res.isEmpty() ? ", " : "") +
                    "Fehler: Parametername falsch angegeben: " + key;
        }
        return res;
    }
    
    /**
     * Sucht Eigenschaft zur Objektart
     * @param oa Objektart
     * @param name Name
     * @return Eigenschaft
     * @throws Ausnahme 
     */
    private Eigenschaft gibEigenschaft(Objektart oa, String name) throws Ausnahme {
        if (oa != null) {
            for (Object o : oa.GibEigenschaften()) {
                Eigenschaft e = (Eigenschaft)o;
                if (e.GibName().equalsIgnoreCase(name)) {
                    return e;
                }
            }
        }
        return null;
    }
    
    /**
     * Untersucht Bedingung, meldet Fehler bei mehrfachem Auftreten derselben Eigenschaft
     * in Verbindung mit der gleichen Operation
     * @param propName Eigenschaftsname
     * @param op Operation
     * @param val Vergleichswert
     * @return Statusmeldung
     */
    private String enterCondition(String propName, String op, String val) {
        String key = propName + op;
        if (conditionMap.containsKey(key)) {
            return "Fehler: Eigenschaft \"" + propName + "\" wird mehrfach mit der Operation " + op + " benutzt. ";
        } else {
            conditionMap.put(key, new String[] { propName, op, val});
        }
        return "";
    }
    
    /**
     * Normiert in der Tabellen mit den Bedinungen den Eigenschaftsnamen,
     * damit der Datenbankzugriff auf die zugehörige Spalte funktioniert.
     * Es gibt reservierte Namen und Sonderzeichen, wo ein Escape nötig ist (z.B. -)
     * @param oa Objektart
     * @param idFilt ID-Filter
     * @param res aufgesammelte Meldungen
     * @throws Ausnahme 
     */
    private String normalizeCondition(Objektart oa, String idFilt, String res) throws Ausnahme {
        String diag = res;
        for (Entry<String, String[]> entry : conditionMap.entrySet()) {
            String nm = entry.getValue()[0];
            Eigenschaft eig = gibEigenschaft(oa, nm);
            if (eig == null) {
                diag += (diag.isEmpty() ? "" : ", ") + "Fehler: Name \"" + nm +
                        "\" ist kein gültiger Eigenschaftsname für Objektart " + oa.GibName();
            } else {
                String op = entry.getValue()[1];
                String pName = eig.GibName();
                eTyp typ = eig.GibTyp();
                if (typ.equals(eTyp.geoType)) {
                    // Nur geometrische Operation erlaubt
                    if (!"INTERSECT".equals(op)) {
                        diag += (diag.isEmpty() ? "" : ", ") + "Fehler: geoemetrische Eigenschaft \"" + pName +
                                "\" kann nur mit geometrischer Operation benutzt werden.";
                    }
                }
                if (typ.equals(eTyp.binaryType)) {
                    // Binaerdaten sind nicht erlaubt
                    diag += (diag.isEmpty() ? "" : ", ") + "Fehler: Binärdaten-Eigenschaft \"" + pName +
                            "\" kann nicht gefiltert werden.";
                }
                if ("LIKE".equals(op)) {
                    // Nur mit Strings erlaubt
                    if (!typ.equals(eTyp.stringType)) {
                        diag += (diag.isEmpty() ? "" : ", ") + "Fehler: die Eigenschaft \"" + pName +
                                "\" ist kein String und kann nicht auf ein Muster geprüft werden.";
                    }
                }
                String escaped = pName;
                if (entry.getKey().toUpperCase(Locale.GERMANY).startsWith(pName.toUpperCase(Locale.GERMANY))) {
                    escaped = Okstra2Db.EscapeSpaltenname(pName, true);
                    entry.getValue()[0] = escaped;
                }
                
                // Jetzt wird noch der Vergleichswert an die SQL-Syntax angepasst
                // Es können mehrere Werte auftreten, getrennt durch $@$
                String[] values;
                String val = entry.getValue()[2];
                if (!val.contains(DELIMITER)) {
                    values = new String[] { val };
                } else {
                    switch (op) {
                        case ">":
                        case ">=":
                        case "<":
                        case "<=":
                        {
                            String msg = "Fehler: die Vergleichsoperation \"" + op +
                                        "\" kann nicht multiple Werte angewendet werden.";
                            if (!diag.contains(msg))
                                diag += (diag.isEmpty() ? "" : ", ") + msg;
                            break;
                        }
                        default:
                            break;
                    }
                    values = val.split(DELIMITER_SPLIT);
                }
                val = "";
                boolean bOk = true;
                for (String w : values) {
                    if (typ.equals(eTyp.stringType) ||
                        typ.equals(eTyp.dateType)) {
                        if (op.equals("LIKE")) {
                            val += (!val.isEmpty() ? " OR (" : "(") + escaped + " LIKE " + SqlWert.text(w) + ")";
                        } else {
                            val += (!val.isEmpty() ? ", " : "") + SqlWert.text(w);
                        }
                    } else if (typ.equals(eTyp.intType) ||
                               typ.equals(eTyp.doubleType) ||
                               typ.equals(eTyp.floatType) ||
                               typ.equals(eTyp.longIntType)) {
                        // nur Zahlen; anderes scheiterte bisher erst an der Abfrage
                        String zahl;
                        try {
                            zahl = SqlWert.zahl(w);
                        } catch (NumberFormatException ex) {
                            bOk = false;
                            diag += (diag.isEmpty() ? "" : ", ") + "Fehler: Wert \"" + w +
                                    "\" für die Eigenschaft \"" + pName + "\" ist keine Zahl.";
                            continue;
                        }
                        val += (!val.isEmpty() ? ", " : "") + zahl;
                    } else if (typ.equals(eTyp.boolType)) {
                        val += (!val.isEmpty() ? ", " : "") + ("TRUE".equalsIgnoreCase(w) ? "true" : "false");
                    } else if (typ.equals(eTyp.geoType)) {
                        val += (!val.isEmpty() ? " OR " : "") + "ST_INTERSECTS(" + escaped + ", ";
                        String geo = "ST_TRANSFORM(ST_GEOMFROMEWKT(" + SqlWert.text(w) + "), " + String.valueOf(srid) + ")";
                        val += geo + ")";
                    } else if (!typ.equals(eTyp.binaryType)) {
                        bOk = false;
                        diag += (diag.isEmpty() ? "" : ", ") + "Fehler: die Eigenschaft \"" + propName +
                                "\" kann wegen ihres Datentyps nicht gefiltert werden.";
                    }
                }
                if (bOk) {
                    if (!typ.equals(eTyp.geoType)) {
                        switch (op) {
                            case "=":
                            case "<>":
                                entry.getValue()[2] = " " + escaped + " IN (" + val + ")";
                                break;
                            case "LIKE":
                                if (val.contains(" OR ")) {
                                    entry.getValue()[2] = "(" + val + ")";
                                } else {
                                    entry.getValue()[2] = val;
                                }
                                break;
                            case ">":
                            case ">=":
                            case "<":
                            case "<=":
                                entry.getValue()[2] = " " + escaped + op + val;
                                break;
                            default:
                                break;
                        }
                    } else {
                        if (val.contains(" OR ")) {
                            entry.getValue()[2] = "(" + val + ")";
                        } else {
                            entry.getValue()[2] = val;
                        }
                    }
                }
            }
        }
        // ID-Filter getrennt abhandeln
        if (!idFilt.isEmpty()) {
            String[] values;
            String val = idFilt;
            if (!val.contains(DELIMITER)) {
                values = new String[] { val };
            } else {
                values = val.split(DELIMITER_SPLIT);
            }
            val = "";
            for (String w : values) {
                val += (!val.isEmpty() ? ", " : "") + SqlWert.text(w);
            }
            // Bedingung unter Spezialnamen in der Map verankern
            conditionMap.put("*OID*", new String[] { "*OID*", "=", " oid::text IN (" + val + ")"});
        }
        return diag;
    }
    
    /**
     * Fügt alle Bedingungen zu einer SQL-Bedingung zusammen
     * @return WHERE clause
     */
    private String writeCondition() {
        String res = "";
        for (Entry<String, String[]> entry : conditionMap.entrySet()) {
            if (!res.isEmpty()) {
                res += " AND ";
            }
            res += entry.getValue()[2];
        }
        return res;
    }
}
