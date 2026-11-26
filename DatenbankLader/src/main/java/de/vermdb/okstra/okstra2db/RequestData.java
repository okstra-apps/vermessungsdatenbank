/*
 * SPDX-FileCopyrightText: 2014-2026 interactive instruments GmbH
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package de.vermdb.okstra.okstra2db;

import de.okstra.oklabi.Ausnahme;
import de.okstra.oklabi.Datenbestand;
import de.okstra.oklabi.FblVersion;
import de.okstra.oklabi.OklabiHelper;
import de.okstra.oklabi.Umgebung;
import de.okstra.oklabi.eAufzaehlungen;
import de.okstra.oklabi.eBundesland;
import de.okstra.oklabi.eKoordRefSys;
import de.okstra.oklabi.eMeldTyp;
import de.okstra.oklabi.eMetadaten;
import de.okstra.oklabi.eProtokollTyp;
import de.okstra.sql.util.Db2Okstra;
import de.okstra.sql.util.DbVerbindung;
import de.okstra.sql.util.Okstra2Db;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

/**
 * Speichert Angaben zum Upload-Request
 * @author Vogelsang
 * @version 2014-11-25
 * 
 * 2018-12-12 Transport Höhensystem
 * 2021-04-14 Erkennung Fachbedeutungslistenversion fehlerhaft
 * 2021-06-02 Als Standard-Bundesland Sachsen-Anhalt auswählen
 * 2021-07-26 Transport Projektname
 * 2023-02-02 Fehler bei Übernahme Höhensystem überschreibt Koordinatensystem
 * 2023-03-06 Verallgemeinerung FB-Listenversion (2.0 bis 99.9)
 * 2023-03-22 Datenbank für Altdaten ergänzt
 * 2024-03-22 FB-Liste aus Datei verwenden
 * 2024-03-25 Benutzername
 * 2025-02-17 Format für Stichtag angepasst
 * 2025-02-17 Formt Fachbedeutungslistenparameter erweitert
 * 2025-11-18 Nur-Prüfen-Modus
 */
public class RequestData {

    int jobNumber;
    int status;
    String originalPath;
    String filePath;
    String format;
    String date;
    String theme;
    String types;
    String exceptionMsg;
    String sessionId;
    String project;
    String user;
    String timeStamp;
    de.okstra.oklabi.Version okstraVersion;
    boolean deleteMode;
    FblVersion fbversion;
    eBundesland eBuLa;
    eKoordRefSys eSrs;
    eKoordRefSys eHs;
    eKoordRefSys eSrsDomain;
    eKoordRefSys eHsDomain;
    HashMap<eMetadaten,String> metadata;
    Datenbestand bestand;
    DbVerbindung oksVerbindung;
    DbVerbindung svcVerbindung;
    String dbVersionString;
    de.okstra.oklabi.Version dbVersion;
    boolean bSrsError;
    boolean bKennungenAbgelehnt;
    boolean bFbListeAusDatei;
    boolean bCheckOnly;
    boolean bCheckState;

    /**
     * Default-Konstruktor
     */
    public RequestData() {
        this.jobNumber = -1;
        this.status = 0;
        this.originalPath = "";
        this.filePath = "";
        this.format = "";
        this.date = "";
        this.theme = "";
        this.types = "";
        this.exceptionMsg = "";
        this.sessionId = "";
        this.timeStamp = "";
        this.okstraVersion = null;
        this.deleteMode = false;
        this.metadata = null;
        this.bestand = null;
        this.fbversion = null;
        this.eBuLa = eBundesland.bl_ohne;
        this.eSrs = eKoordRefSys.UTM32N;
        this.eHs = eKoordRefSys.Undefiniert;
        this.sessionId = "";
        this.project = "";
        this.user = "";
        this.oksVerbindung = null;
        this.svcVerbindung = null;
        this.dbVersion = null;
        this.dbVersionString = "";
        this.bSrsError = false;
        this.bKennungenAbgelehnt = false;
        this.eSrsDomain = eKoordRefSys.Undefiniert;
        this.eHsDomain = eKoordRefSys.Undefiniert;
        this.bFbListeAusDatei = false;
        this.bCheckOnly = false;
        this.bCheckState = true;
    }
    
    /**
     * Überladener Konstruktor
     * @param original
     * @param file
     * @param fForm
     * @param mode
     * @param d
     * @param t
     * @param y 
     * @param fbv 
     * @param fbula 
     * @param fsrs 
     * @param fhs 
     * @param fhv 
     * @param fuv 
     * @param fBuLa 
     * @param eLand 
     * @throws java.lang.Exception 
     */
    public RequestData(String original, String file, String fForm, boolean mode, String d, String t, String y,
            String fbv, String fbula, String fsrs, String fhs, int fhv, int fuv, String fBuLa, eBundesland eLand) throws Exception {
        this.oksVerbindung = null;
        this.svcVerbindung = null;
        this.dbVersion = null;
        this.dbVersionString = "";
        this.bCheckOnly = false;
        this.bCheckState = true;
        this.jobNumber = -1;
        this.status = 0;
        this.originalPath = original != null ? original : "";
        this.filePath = file != null ? file : "";
        this.format = fForm != null ? fForm : "";
        this.deleteMode = mode;
        this.date = d != null ? d : "";
        if (date.isEmpty() || "unbelegt".equals(date.toLowerCase(Locale.GERMANY))) {
            date = Okstra2Db.formatDate(new Date(), "yyyy-MM-dd");
        } else {
            // Format Stichtag standardisieren
            Date dt = Okstra2Db.wandleDatumString(date);
            date = Okstra2Db.formatDate(dt, "yyyy-MM-dd");
        }
        this.theme = t != null ? t : "";
        this.types = y != null ? y : "";
        this.exceptionMsg = "";
        this.sessionId = "";
        this.timeStamp = "";
        this.okstraVersion = null;
        this.metadata = null;
        this.bestand = null;
        this.fbversion = null;
        this.bFbListeAusDatei = false;
        this.eBuLa = eBundesland.bl_ohne;
        this.eSrs = eKoordRefSys.UTM32N;
        this.eHs = eKoordRefSys.Undefiniert;
        int haupt = 1;
        int unter = 6;
        String bl = (fBuLa != null && !fBuLa.isEmpty()) ? fBuLa : "Sachsen-Anhalt";
        eBundesland eBl;
        try {
            eBl = OklabiHelper.gibBundesland(fbula);
            if (eBl.equals(eBundesland.bl_ohne)) {
                int num = Integer.parseInt(fbula);
                this.eBuLa = eBundesland.getEnum(num);
            } else {
                this.eBuLa = eBl;
            }
        }
        catch (Exception ex) {
            Db2Okstra.protokolliere(-1, "Ungültige Angabe für Bundesland " + fbula + ", benutzt wird " + bl,
                    eMeldTyp.warnung, eProtokollTyp.meldungen);
            this.eBuLa = eBundesland.ST;
        }
        try {
            String bula = Umgebung.GibTextZuWert(eAufzaehlungen.tBundesland, this.eBuLa.GibCode());
            FblVersion fbvers = fbv.contains(".")
                    ? OklabiHelper.gibFblVersion("Fachbedeutungen " + bula + " " + fbv)
                    : null;
            if (fbvers != null) {
                haupt = fbvers.GibHauptversion();
                unter = fbvers.GibUnterversion();
                bula = fbvers.GibBundesland();
            } else {
                // Als Nummer kodiert
                int num = Integer.parseInt(fbv);
                if (num < 6 && num >= 2) {
                    // Sonderfall alte Versionen 1.3 bis 1.6
                    haupt = 1;
                    unter = num + 1;
                }
                else if (num > 5) {
                    // Version 2.0 entspricht laufender Nummer 6
                    // Maximal erlaubte Version ist 99.9 
                    int nTest = 2 + (int)((num-6)/10);
                    if (nTest > 1 && nTest < 100) {
                        haupt = nTest;
                        unter = (num-6) % 10;
                    }
                    else {
                        haupt = 0;
                    }
                }
                else if (num == -1) {
                    // Das bedeutet "Aus Datei"
                    // Wir bleiben hier bei Liste 1.6
                    haupt = 1;
                    unter = 6;
                    this.bFbListeAusDatei = true;
                }
                else {
                    haupt = 0;
                }
            }
            if ((haupt > 1) && ((this.eBuLa == eBundesland.bl_Autobahn) ||
                    (this.eBuLa == eBundesland.bl_alle) ||
                    (this.eBuLa == eBundesland.bl_ohne))) {
                Db2Okstra.protokolliere(-1, "Benutze Bundesland " + bl + " für FB-Listenversion",
                        eMeldTyp.warnung, eProtokollTyp.meldungen);
                this.eBuLa = eLand.equals(eBundesland.bl_ohne) ? eBundesland.ST : eLand;
            } else if ((haupt == 1) && (this.eBuLa.equals(eBundesland.bl_Autobahn))) {
                Db2Okstra.protokolliere(-1, "Benutze alle Bundesländer für FB-Listenversion",
                        eMeldTyp.warnung, eProtokollTyp.meldungen);
                this.eBuLa = eBundesland.bl_alle;
            }
            if (this.bFbListeAusDatei) {
                if (fbvers != null) {
                    fbvers.Vernichte();
                } 
            } else if (haupt != 0 && !this.eBuLa.equals(eBundesland.bl_ohne)) {
                this.fbversion = fbvers != null ? fbvers : FblVersion.Erzeuge(haupt, unter, bula);
            }
        }
        catch (NumberFormatException | Ausnahme ex) {
            Db2Okstra.protokolliere(-1, "Ungültige Angabe für Fachbedeutungslistenversion, benutze 1.6",
                    eMeldTyp.warnung, eProtokollTyp.meldungen);
            this.fbversion = FblVersion.Erzeuge(1, 6);
        }
        try {
            int num = Integer.parseInt(fsrs);
            switch (num) {
                default:
                    this.eSrs = eKoordRefSys.Undefiniert;
                    break;
                case 25832:
                case 25833:
                case 2398:
                case 31466:
                case 31467:
                case 31468:
                    this.eSrs = eKoordRefSys.getEnum(num);
                    break;
            }
        }
        catch (Exception ex) {
            throw new Exception("Ungültige Angabe für Koordinatenreferenzsystem " + fsrs);
        }
        try {
            int num = (fhs != null ? Integer.parseInt(fhs) : 0);
            switch (num) {
                default:
                    this.eHs = eKoordRefSys.Undefiniert;
                    break;
                case 100:
                case 101:
                case 140:
                case 150:
                case 156:
                case 160:
                case 170:
                case 800:
                case 801:
                case 820:
                case 830:
                case 900:
                case 901:
                    this.eHs = eKoordRefSys.getEnum(num);
                    break;
            }
        }
        catch (Exception ex) {
            throw new Exception("Ungültige Angabe für Höhensystem " + fhs);
        }
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
     * Setzt Wert der Pruefstatus-Flagge
     * @param val Flagge
     */
    public void setPruefStatus(boolean val) {
        bCheckState = val;
    }

    /**
     * Gibt Wert der Pruefstatus-Flagge zurück
     * @return Flagge
     */
    public boolean getPruefStatus() {
        return bCheckState;
    }

    /**
     * Liest Auftragsnummer
     * @return Nummer
     */
    public int getAuftrag() {
        return this.jobNumber;
    }

    /**
     * Setzt Auftragsnummer
     * @param job 
     */
    public void setAuftrag(int job) {
        this.jobNumber = job;
    }

    /**
     * Liest Status
     * @return Status
     */
    public int getStatus() {
        return this.status;
    }

    /**
     * Setzt Status
     * @param st 
     */
    public void setStatus(int st) {
        this.status = st;
    }

    /**
     * Liest Session-ID
     * @return ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Liest Projekname
     * @return Projektname
     */
    public String getProject() {
        return project;
    }

    /**
     * Liest Benutzername
     * @return Benutername
     */
    public String getUser() {
        return user;
    }

    /**
     * Setzt Zeitstempel
     * @param ts
     */
    public void setTimeStamp(String ts) {
        this.timeStamp = ts;
    }

    /**
     * Liest Zeitstempel
     * @return ts
     */
    public String getTimeStamp() {
        return timeStamp;
    }

    /**
     * Setzt Session-ID
     * @param id
     */
    public void setSessionId(String id) {
        this.sessionId = id;
    }

    /**
     * Setzt Projektname
     * @param p
     */
    public void setProject(String p) {
        this.project = p;
    }

    /**
     * Setzt Benutzername
     * @param n
     */
    public void setUser(String n) {
        this.user = n;
    }

    /**
     * Liest Dateinamen der Originaldatei
     * @return Pfad
     */
    public String getOriginalPath() {
        return originalPath;
    }

    /**
     * Setzt Dateinamen der Originaldatei
     * @param op 
     */
    public void setOriginalPath(String op) {
        this.originalPath = op;
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
     * Liest Dateiformat
     * @return Format
     */
    public String getFormat() {
        return format;
    }

    /**
     * Setzt Dateiformat
     * @param f 
     */
    public void setFormat(String f) {
        this.format = f;
    }

    /**
     * Liest Löschmodus
     * @return Löschmodus
     */
    public boolean getDeleteMode() {
        return deleteMode;
    }

    /**
     * Setzt Löchsmodus
     * @param dm 
     */
    public void setDeleteMode(boolean dm) {
        this.deleteMode = dm;
    }

    /**
     * Liest Stichtag
     * @return Stichtag
     */
    public String getDate() {
        return date;
    }

    /**
     * Setzt Stichtag
     * @param d 
     */
    public void setDate(String d) {
        this.date = d;
    }

    /**
     * Liest Fachthemen
     * @return Fachthemen
     */
    public String getTheme() {
        return theme;
    }

    /**
     * Setzt Fachthemen
     * @param t 
     */
    public void setTheme(String t) {
        this.theme = t;
    }

    /**
     * Liest Objektarten
     * @return Objektarten
     */
    public String getTypes() {
        return types;
    }

    /**
     * Setzt Objektarten
     * @param t 
     */
    public void setTypes(String t) {
        this.types = t;
    }

    /**
     * Liest Version
     * @return Version
     */
    public de.okstra.oklabi.Version getVersion() {
        return okstraVersion;
    }

    /**
     * Setzt Version
     * @param v 
     */
    public void setVersion(de.okstra.oklabi.Version v) {
        try {
            if (this.okstraVersion != null) {
                this.okstraVersion.Vernichte();
            }
        }
        catch (Ausnahme ex) {
        }
        this.okstraVersion = v;
    }

    /**
     * Liest Metadaten
     * @return Metadaten
     */
    public HashMap<eMetadaten,String> getMetadata() {
        return metadata;
    }

    /**
     * Setzt Metadaten
     * @param md 
     */
    public void setMetadata(HashMap<eMetadaten,String> md) {
        this.metadata = md;
    }

    /**
     * Liest Datenbestand
     * @return Datenbestand
     */
    public Datenbestand getDatenbestand() {
        return bestand;
    }

    /**
     * Setzt Datenbestand
     * @param bs 
     */
    public void setDatenbestand(Datenbestand bs) {
        this.bestand = bs;
    }

    /**
     * Liest FB-Version
     * @return FblVersion
     */
    public FblVersion getFblVersion() {
        return fbversion;
    }

    /**
     * Setzt FblVersion
     * @param v 
     */
    public void setFblVersion(FblVersion v) {
        try {
            if (this.fbversion != null) {
                this.fbversion.Vernichte();
            }
        }
        catch (Ausnahme ex) {
        }
        this.fbversion = v;
    }

    /**
     * Erfragt ob die FB-Liste aus der Datei genommen wird
     * @return flagge 
     */
    public boolean getFbListeAusDatei() {
        return this.bFbListeAusDatei;
    }

    /**
     * Setzt, ob die FB-Liste aus der Datei zu nehmen ist
     * @param val
     */
    public void setFbListeAusDatei(boolean val) {
        this.bFbListeAusDatei = val;
    }

    /**
     * Liest Bundesland
     * @return eBundesland
     */
    public eBundesland getBundesland() {
        return eBuLa;
    }

    /**
     * Liest Koordinatenreferenzsystem
     * @return eKoordRefSys
     */
    public eKoordRefSys getSrs() {
        return eSrs;
    }

    /**
     * Setzt Koordinatenreferenzsystem
     * @param srs
     */
    public void setSrs(eKoordRefSys srs) {
        eSrs = srs;
    }

    /**
     * Liest Koordinatenreferenzsystem des Datenbestands
     * @return eKoordRefSys
     */
    public eKoordRefSys getSrsBestand() {
        return eSrsDomain;
    }

    /**
     * Setzt Koordinatenreferenzsystem zum Datenbestand
     * @param srs
     */
    public void setSrsBestand(eKoordRefSys srs) {
        eSrsDomain = srs;
    }

    /**
     * Liest Höhensystem
     * @return eKoordRefSys
     */
    public eKoordRefSys getHs() {
        return eHs;
    }

    /**
     * Setzt Höhensystem
     * @param hs
     */
    public void setHs(eKoordRefSys hs) {
        eHs = hs;
    }

    /**
     * Liest Höhensystem des Datenbestands
     * @return eKoordRefSys
     */
    public eKoordRefSys getHsBestand() {
        return eHsDomain;
    }

    /**
     * Setzt Höhensystem zum Datenbestand
     * @param hs
     */
    public void setHsBestand(eKoordRefSys hs) {
        eHsDomain = hs;
    }

    /**
     * Liest Fehlerflagge Koordinatenreferenzsystem
     * @return Flagge
     */
    public boolean getSrsError() {
        return bSrsError;
    }

    /**
     * Setzt Fehlerflagge Koordinatenreferenzsystem
     * @param flg
     */
    public void setSrsError(boolean flg) {
        bSrsError = flg;
    }

    /**
     * Liest, ob die Lieferung wegen unzulässiger Kennungen abgelehnt wird
     * @return Flagge
     */
    public boolean getKennungenAbgelehnt() {
        return bKennungenAbgelehnt;
    }

    /**
     * Setzt, ob die Lieferung wegen unzulässiger Kennungen abgelehnt wird
     * @param flg
     */
    public void setKennungenAbgelehnt(boolean flg) {
        bKennungenAbgelehnt = flg;
    }

    /**
     * Liest Fehlermeldung
     * @return Fehlermeldung
     */
    public String getExceptionMsg() {
        return exceptionMsg;
    }

    /**
     * Setzt Fehlermeldung
     * @param excMsg 
     */
    public void setExceptionMsg(String excMsg) {
        this.exceptionMsg = excMsg;
    }

    /**
     * Wandelt in Text
     * @return Text
     */
    @Override
    public String toString() {
        String vsnStr = "<unbelegt>";
        try {
            if (this.okstraVersion != null)
                vsnStr = this.okstraVersion.GibText();
        }
        catch (Ausnahme ex) {
            vsnStr = "<fehlerhaft>";
        }
        return "RequestData{" + 
                "filePath=" + filePath +
                ", deleteMode=" + (deleteMode ? "ja" : "nein") +
                ", date=" + date +
                ", theme=" + theme +
                ", types=" + types +
                ", okstraVersion=" + vsnStr +
                ", exceptionMsg=" + exceptionMsg + "}";
    }
}
