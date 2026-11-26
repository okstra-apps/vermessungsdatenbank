/*
 * SPDX-FileCopyrightText: 2018-2026 interactive instruments GmbH
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package de.vermdb.okstra.okstra2db;

import de.okstra.sql.util.DbVerbindung;
import de.okstra.sql.util.Okstra2Db;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 *
 * @author Vogelsang
 * 
 * 2018-12-12 Transport Höhensystem
 * 2023-03-22 Datenbank für Altdaten ergänzt
 * 2023-11-20 Datenbank für Planungsdaten ergänzt
 * 2024-06-24 Dateipfad-Parameter ergänzt
 * 2024-06-25 Parameter-Map mit CASE_INSENSITIVE_ORDER
 * 2024-07-01 Parameterauswertung vereinheitlicht
 * 2025-06-16 Lage- und Höhensystem beachten
 * 2025-11-18 Nur-Prüfen-Modus
*/
public class PostRequestContent {
    private final Map<String, String> mapItems = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private String exceptionMsg = "";
    private String filePath = "";
    private String stichtag = "";
    DbVerbindung oksVerbindung;
    DbVerbindung svcVerbindung;
    String dbVersionString;
    de.okstra.oklabi.Version dbVersion;
    boolean bBestand = true;
    boolean bAltdaten = false;
    boolean bPlanung = false;
    boolean bUploadExists = false;
    boolean bCheckOnly = false;
    public PostRequestContent() {
        this.oksVerbindung = null;
        this.svcVerbindung = null;
        this.dbVersion = null;
        this.dbVersionString = "";
    }

    /**
     * Setzt die Verbindungen zur Datenbank auf
     * @param oksConn
     * @param svcConn
     * @param dbVers
     * @param dbVersString 
     */
    public void addConnection(DbVerbindung oksConn, DbVerbindung svcConn, de.okstra.oklabi.Version dbVers, String dbVersString) {
        this.oksVerbindung = oksConn;
        this.svcVerbindung = svcConn;
        this.dbVersion = dbVers;
        this.dbVersionString = dbVersString;
    }

    /**
     * Gibt die Verbindung zur OKSTRA-Datenbank
     * @return Verbindung
     */
    public DbVerbindung getOkstraVerbindung() {
        return oksVerbindung;
    }
    
    /**
     * Gibt die Verbindung zur Service-Datenbank
     * @return Verbindung
     */
    public DbVerbindung getServiceVerbindung() {
        return svcVerbindung;
    }
    
    /**
     * Gibt die Version der OKSTRA-Datenbank
     * @return Version
     */
    public de.okstra.oklabi.Version getDbVersion() {
        return dbVersion;
    }
    
    /**
     * Gibt die Version der OKSTRA-Datenbank als String
     * @return Versionsstring
     */
    public String getDbVersionString() {
        return dbVersionString;
    }

    /**
     * Setzt Wert der Nur-Pruefen-Flagge
     * @param val Flagge
     */
    public void setNurPruefen(boolean val) {
        bCheckOnly = val;
    }

    /**
     * Gibt Wert der Nur-Pruefen-Flagge zurück
     * @return Flagge
     */
    public boolean getNurPruefen() {
        return bCheckOnly;
    }

    /**
     * Übernimmt Wert zum Parameter
     * @param key Schlüssel
     * @param value Wert
     */
    public void setItem(String key, String value) {
        mapItems.put(key, value);
        if ("f4".equalsIgnoreCase(key)) {
            if (value == null || value.trim().isEmpty() ||
                "unbelegt".equals(value.toLowerCase(Locale.GERMANY))) {
                stichtag = Okstra2Db.formatDate(new Date(), "yyyy-MM-dd");
            } else {
                // Normalisierung
                try {
                    Date dt = Okstra2Db.wandleDatumString(value);
                    stichtag = Okstra2Db.formatDate(dt, "yyyy-MM-dd");
                } catch (Exception ex) {
                    exceptionMsg = ex.getMessage();
                    stichtag = Okstra2Db.formatDate(new Date(), "yyyy-MM-dd");
                }
            }
        }
        else if ("database".equalsIgnoreCase(key)) {
            bBestand = (value.equalsIgnoreCase("inventory"));
            bAltdaten = (value.equalsIgnoreCase("olddata"));
            bPlanung = (value.equalsIgnoreCase("planning"));
        }
        else if ("uploadfile".equalsIgnoreCase(key)) {
            // Erst das Servlet prüft den Pfad gegen das Übergabeverzeichnis
            // (setzeUploadfile)
            bUploadExists = false;
        }
    }

    /** Zulässige Session-ID: wird Teil von Datei- und Verzeichnisnamen */
    private static final Pattern SESSION_ID = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,127}");

    /**
     * Prüft eine Session-ID.
     * @param sid Session-ID
     * @return Meldung oder Leerstring, wenn sie zulässig ist
     */
    public static String pruefeSessionId(String sid) {
        if (sid != null && SESSION_ID.matcher(sid).matches()) {
            return "";
        }
        return "Session-ID enthält unzulässige Zeichen (erlaubt: 1 bis 128 Zeichen aus A-Z, a-z, 0-9, " +
                "_ - ., nicht mit Punkt beginnend). Die Anfrage wird abgewiesen.";
    }

    /**
     * Übernimmt die vom Servlet geprüfte lokale Datei (Parameter uploadfile).
     * @param pfad absoluter Pfad im Übergabeverzeichnis
     */
    public void setzeUploadfile(String pfad) {
        mapItems.put("uploadfile", pfad);
        bUploadExists = true;
    }
    
    /**
     * Holt Wert aus Parametetabelle
     * @param key Schlüssel
     * @return Wert
     */
    public String getItem(String key) {
        return mapItems.get(key);
    }
    
    /**
     * Prüft die Parametertabelle, ob der Schlüssel vorhanden ist
     * @param key Schlüssel
     * @param check Prüft Wert bei Vorhandensein, ob nicht null und nicht leer
     * @return Flagge
     */
    public boolean hasItem(String key, boolean check) {
        if (mapItems.containsKey(key)) {
            if (check) {
                String val = getItem(key);
                if (val == null || val.trim().isEmpty()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Liest Fehlermeldung
     * @return Fehlermeldung
     */
    public String getExceptionMsg() {
        return exceptionMsg;
    }

    /**
     * Liest Stichtag
     * @return Datumstring
     */
    public String getStichtag() {
        if (stichtag.isEmpty()) {
            stichtag = Okstra2Db.formatDate(new Date(), "yyyy-MM-dd");
        }
        return stichtag;
    }

    /**
     * Gibt Wert der Bestands-Flagge zurück
     * @return Flagge
     */
    public boolean getIstBestand() {
        return bBestand;
    }

    /**
     * Gibt Wert der Altdaten-Flagge zurück
     * @return Flagge
     */
    public boolean getIstAltdaten() {
        return bAltdaten;
    }
    
    /**
     * Gibt den Typ der Datenbankanbindung zurück.
     * @return Anbindungstyp
     */
    public String getDatenbankTyp() {
        if (bBestand)
            return "Bestandsdaten";
        else if (bAltdaten)
            return "Altdaten";
        else if (bPlanung)
            return "Planungsdaten";
        else
            return "unbekannt";
    }

    /**
     * Gibt Wert der Planungsdaten-Flagge zurück
     * @return Flagge
     */
    public boolean getIstPlanung() {
        return bPlanung;
    }

    /**
     * Setzt Fehlermeldung
     * @param excMsg 
     */
    public void setExceptionMsg(String excMsg) {
        this.exceptionMsg = (this.exceptionMsg.isEmpty() ? "" : "\n") + excMsg;
    }

    /**
     * Liest Dateipfad
     * @return Pfad
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Setzt Dateipfad
     * @param fp 
     */
    public void setFilePath(String fp) {
        this.filePath = fp;
    }

    /**
     * Gibt zurück, ob eine Upload-Datei vorhanden ist
     * @return Flagge
     */
    public boolean getUploadExists() {
        return bUploadExists;
    }
    
    /**
     * Übernimmt die Parameter und prüft diese
     * @param parms Parametertabelle
     * @return Meldung
     */
    public String addParameters(Map<String,String> parms) {
        String msg = "";
        if (parms != null) {
            for (Map.Entry<String, String> entry : parms.entrySet())
            {
                String key = entry.getKey();
                String val = entry.getValue();
                this.setItem(key, val);
            }
        } else {
            msg = "Parametertabelle ist leer.";
        }
        return msg;
    }
    
    /**
     * Validiert die Parameterzusammenstellung
     * @param parms Normalisierte Parametertabelle
     * @return Meldung bei Fehlern
     */
    public String checkParameters(Map<String,String> parms) {
        String msg = "";
        String nl = "";
        String sessionId = null;
        if (this.hasItem("SID", true)) {
            sessionId = this.getItem("SID");
            if (this.hasItem("SessionID", true)) {
                if (!sessionId.equalsIgnoreCase(this.getItem("SessionID"))) {
                    msg = "Session-ID ist mehrdeutig belegt.";
                    nl = "\n";
                }
            }
        } else if (this.hasItem("SessionID", true)) {
            sessionId = this.getItem("SessionID");
        }
        if (sessionId != null) {
            String fehler = pruefeSessionId(sessionId);
            if (!fehler.isEmpty()) {
                msg += nl + fehler;
                nl = "\n";
            }
            parms.put("SessionID", sessionId);
            parms.put("SID", sessionId);
        } else {
            msg = "Session-ID ist nicht belegt.";
            nl = "\n";
        }
        if (this.hasItem("TS", true)) {
            String val = this.getItem("TS");
            if (this.hasItem("TimeStamp", true)) {
                if (!this.getItem("TimeStamp").equalsIgnoreCase(val)) {
                    msg += nl + "TimeStamp ist mehrdeutig belegt.";
                    nl = "\n";
                }
            }
            parms.put("TimeStamp", val);
            parms.put("TS", val);
        } else if (this.hasItem("TimeStamp", true)) {
            String val = this.getItem("TimeStamp");
            parms.put("TimeStamp", val);
            parms.put("TS", val);
        }
        if (this.hasItem("f101", true)) {
            String val = this.getItem("f101");
            if (this.hasItem("Version", true)) {
                if (!this.getItem("Version").equalsIgnoreCase(val)) {
                    msg += nl + "Version ist mehrdeutig belegt.";
                    nl = "\n";
                }
            }
            parms.put("Version", val);
            parms.put("f101", val);
        } else if (this.hasItem("Version", true)) {
            String val = this.getItem("Version");
            parms.put("Version", val);
            parms.put("f101", val);
        }
        if (this.hasItem("f100", true)) {
            String val = this.getItem("f100");
            if (this.hasItem("Gebiet", true)) {
                if (!this.getItem("Gebiet").equalsIgnoreCase(val)) {
                    msg += nl + "Gebiet ist mehrdeutig belegt.";
                    nl = "\n";
                }
            }
            parms.put("Gebiet", val);
            parms.put("f100", val);
        } else if (this.hasItem("Gebiet", true)) {
            String val = this.getItem("Gebiet");
            parms.put("Gebiet", val);
            parms.put("f100", val);
        }
        if (this.hasItem("AREA", true)) {
            String val = this.getItem("AREA");
            parms.put("Area", val);
        }
        if (this.hasItem("f21", true)) {
            String val = this.getItem("f21");
            if (this.hasItem("Thema", true)) {
                if (!this.getItem("Thema").equalsIgnoreCase(val)) {
                    msg += nl + "Thema ist mehrdeutig belegt.";
                    nl = "\n";
                }
            }
            parms.put("Thema", val);
            parms.put("f21", val);
        } else if (this.hasItem("Thema", true)) {
            String val = this.getItem("Thema");
            parms.put("Thema", val);
            parms.put("f21", val);
        }
        if (this.hasItem("PRJNM", true)) {
            String val = this.getItem("PRJNM");
            if (this.hasItem("Projekt", true)) {
                if (!this.getItem("Projekt").equalsIgnoreCase(val)) {
                    msg += nl + "Projekt ist mehrdeutig belegt.";
                    nl = "\n";
                }
            }
            parms.put("Projekt", val);
            parms.put("PRJNM", val);
        } else if (this.hasItem("Projekt", true)) {
            String val = this.getItem("Projekt");
            parms.put("Projekt", val);
            parms.put("PRJNM", val);
        }
        if (this.hasItem("Auftrag", true)) {
            String val = this.getItem("Auftrag");
            parms.put("Auftrag", val);
        }
        if (this.hasItem("gzip", true)) {
            String val = this.getItem("gzip");
            parms.put("gzip", val);
        }
        if (this.hasItem("f22", true)) {
            String val = this.getItem("f22");
            if (this.hasItem("SRS", true)) {
                if (!this.getItem("SRS").equalsIgnoreCase(val)) {
                    msg += nl + "Koordinatenreferenzsystem ist mehrdeutig belegt.";
                    nl = "\n";
                }
            }
            parms.put("SRS", val);
            parms.put("f22", val);
            switch (val) {
                case "25832":
                case "25833":
                case "2398":
                case "31466":
                case "31467":
                case "31468":
                case "31469":
                case "4326":
                    break;
                default:
                    msg += nl + "Koordinatenreferenzsystem wird nicht unterstützt.";
                    nl = "\n";
                    break;
            }
        } else if (this.hasItem("SRS", true)) {
            String val = this.getItem("SRS");
            parms.put("SRS", val);
            parms.put("f22", val);
            switch (val) {
                case "25832":
                case "25833":
                case "2398":
                case "31466":
                case "31467":
                case "31468":
                case "31469":
                case "4326":
                    break;
                default:
                    msg += nl + "Koordinatenreferenzsystem wird nicht unterstützt.";
                    nl = "\n";
                    break;
            }
        }
        if (this.hasItem("f23", true)) {
            String val = this.getItem("f23");
            if (this.hasItem("HS", true)) {
                if (!this.getItem("HS").equalsIgnoreCase(val)) {
                    msg += nl + "Höhensystem ist mehrdeutig belegt.";
                    nl = "\n";
                }
            }
            parms.put("HS", val);
            parms.put("f23", val);
            switch (val) {
                case "160":
                case "170":
                    break;
                default:
                    msg += nl + "Höhensystem wird nicht unterstützt.";
                    nl = "\n";
                    break;
            }
        } else if (this.hasItem("HS", true)) {
            String val = this.getItem("HS");
            parms.put("HS", val);
            parms.put("f23", val);
            switch (val) {
                case "160":
                case "170":
                    break;
                default:
                    msg += nl + "Höhensystem wird nicht unterstützt.";
                    nl = "\n";
                    break;
            }
        }
        else if( this.hasItem("holeDatei", true)) {
            String val = this.getItem("holeDatei");
            parms.put("holeDatei", val);
            parms.put("ReadLock", val);
        }
        return msg;
    } 
}
