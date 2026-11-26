/*
 * SPDX-FileCopyrightText: 2014-2026 interactive instruments GmbH
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package de.vermdb.okstra.okstra2db;

import de.okstra.oklabi.AnyType;
import de.okstra.oklabi.AusgabeKonverter;
import de.okstra.oklabi.Ausnahme;
import de.okstra.oklabi.CTEAusgabeKonverter;
import de.okstra.oklabi.CTEEingabeKonverter;
import de.okstra.oklabi.DateiAusgabe;
import de.okstra.oklabi.DateiEingabe;
import de.okstra.oklabi.DateiProtokollant;
import de.okstra.oklabi.Datenbestand;
import de.okstra.oklabi.Eigenschaft;
import de.okstra.oklabi.EingabeKonverter;
import de.okstra.oklabi.Enumerator;
import de.okstra.oklabi.Fachbedeutungsliste;
import de.okstra.oklabi.Fachobjekt;
import de.okstra.oklabi.FblVersion;
import de.okstra.oklabi.Geometrie;
import de.okstra.oklabi.Kommunikationskanal;
import de.okstra.oklabi.Objektart;
import de.okstra.oklabi.OklabiLoader;
import de.okstra.oklabi.Protokollant;
import de.okstra.oklabi.TransformiererLoader;
import de.okstra.oklabi.Umgebung;
import de.okstra.oklabi.Verarbeiter;
import de.okstra.oklabi.Version;
import de.okstra.oklabi.XMLAusgabeKonverter;
import de.okstra.oklabi.XMLEingabeKonverter;
import de.okstra.oklabi.eAufzaehlungen;
import de.okstra.oklabi.eBundesland;
import de.okstra.oklabi.eEinheitTyp;
import de.okstra.oklabi.eEinstellung;
import de.okstra.oklabi.eGeoTyp;
import de.okstra.oklabi.eKodierung;
import de.okstra.oklabi.eKoordRefSys;
import de.okstra.oklabi.eMeldTyp;
import de.okstra.oklabi.eMetadaten;
import de.okstra.oklabi.eModelleigenschaft;
import de.okstra.oklabi.eModellierung;
import de.okstra.oklabi.eProtokollTyp;
import de.okstra.oklabi.eTyp;
import de.okstra.sql.util.Db2Okstra;
import de.okstra.sql.util.DbAuswahl;
import de.okstra.sql.util.DbMetadaten;
import de.okstra.sql.util.DbVerbindung;
import de.okstra.sql.util.FoIdentifizierer;
import de.okstra.sql.util.Okstra2Db;
import static de.okstra.sql.util.Okstra2Db.formatDate;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import javax.sql.DataSource;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

/**
 * Servlet implementation class Okstra2DbServlet
 * @author Vogelsang
 * @version 2014-11-25
 * 
 * 2018-12-12 Transport Höhensystem
 * 2019-03-20 Portnummer Uploadclient macht Probleme
 * 2021-04-14 Startmeldung mit Versionsangaben
 * 2021-04-23 Überschreiben-Modus beim Laden verhindern
 * 2021-05-06 Fachbedeutungsliste für alle Bundesländer nicht erkannt
 * 2021-05-10 Ausführlichere  Protokollierung
 * 2021-06-11 Unload-Version bis 2.020 ermöglichen
 * 2021-07-26 Transport Projektname
 * 2021-07-26 Steuerung der FB-Prüfung erweitert
 * 2021-07-26 Falscher Protokollkanal benutzt
 * 2022-02-14 Aufbereitung Warteschlange korrigiert
 * 2022-12-28 Verallgemeinerung für Behörde
 * 2023-01-09 Log4j Brücke 1->2 eingebaut
 * 2023-02-15 Multipart-Größenangaben in Konfiguration erlauben
 * 2023-02-18 Verallgemeinerungen in Konfiguration
 * 2023-02-20 Schema für Metadatentabellen festlegen
 * 2023-03-02 OKSTRA-Version in Kopfdaten unterbringen
 * 2023-03-02 Kopfdaten in Metadatenschema verlagern
 * 2023-03-22 Datenbank für Altdaten ergänzt
 * 2023-04-03 Fehlermeldungen ergänzt
 * 2023-05-03 Verlinkung Protokolle geändert
 * 2023-11-20 Datenbank für Planungsdaten ergänzt
 * 2024-03-15 Korrekturen, Upload-Service deaktiviert
 * 2024-03-22 Projektbezeichnung in Warteschlange
 * 2024-03-25 Benutzername
 * 2024-03-28 Lade-Abbruch in Warteschlange verankern
 * 2024-03-28 Konditionale Transkodierung Windows-1252 nach UTF-8
 * 2024-03-28 Auftragslöschung
 * 2024-05-23 Automatischer Start, Backend für WS
 * 2024-05-24 Default Worker für OkWS-Tests
 * 2024-06-18 Rückgabetyp festlegen (PLAIN/JSON)
 * 2024-06-25 Parameter-Map mit CASE_INSENSITIVE_ORDER
 * 2024-06-25 Neue Parameter für Kompression, Projekt und Auftrag
 * 2024-06-26 Konfiguration umgestellt auf Kontextparameter
 * 2024-07-01 Parameterauswertung vereinheitlicht
 * 2024-07-03 String-Parameterwerte von umgebenden Tüddeln befreien
 * 2024-07-03 Suchgebiet transformieren wenn erforderlich
 * 2024-07-03 Berechnete Geometrie-Eigenschaften berücksichtigen mit Suchgebiet
 * 2024-07-04 Multigeometrie bei Geometriebedingung berücksichtigen
 * 2024-07-10 Worker für OkWS-Anschluss integriert
 * 2024-07-11 Befüllung der Queue harmonisiert mit View "queue"
 * 2024-07-18 OKLABI-Bibliotheksversion prüfen
 * 2024-07-18 OkWS-Response in UTF-8 ausgeben
 * 2024-07-31 Überflüssige Meldung entfernt
 * 2024-08-01 Umgebungseinstellungen über Parameter ansteuern
 * 2024-08-01 Zugriffe auf Protokolle im Webserver
 * 2024-08-02 Höhensystem einstellen
 * 2024-11-22 Neue Parameter OkstraVersion, wfsExpected für Backend im OkWS
 * 2025-02-17 Format für Stichtag angepasst
 * 2025-06-16 OKSTRA-Version in Datenbank ausgeben
 * 2025-06-17 FB-Ausschlüsse prüfen
 * 2025-10-14 Lagesystem falsch zugewiesen bei Download, ausführlichere Meldungen
 * 2025-10-29 Überschrift Version-Spalte in Warteschlangenübersicht angepasst
 * 2025-11-18 Nur-Prüfen-Modus
 * 2026-01-15 Fehlerbehandlung Nur-Prüfen-Modus angepasst
 * 2026-01-22 Nur-Prüfen-Modus in Warteschlange mitführen
 * 2026-02-23 Lade- und Prüf-Queue unterscheiden
 * 2026-06-25 UploadClient stillgelegt
 * 2026-06-26 Berechtigungen setzen
 * 2026-10-09 Jakarta Servlet 6.1 (Tomcat 11), Multipart der Servlet-API statt Commons FileUpload
 */
// Grenzen für Uploads, wenn die web.xml keine <multipart-config> angibt: Schwelle
// 3 MB, Datei 400 MB, Anfrage 500 MB
@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 3,
        maxFileSize = 1024L * 1024 * 400,
        maxRequestSize = 1024L * 1024 * 500)
public class Okstra2DbServlet extends HttpServlet {

    private static final long serialVersionUID = 4712L;
    
    /**
     * Sammelt die Datenbankverbindungen auf (Bitmaske)
     */
    private static final int DB_OKSTRA    =  1;
    private static final int DB_ALTDATEN  =  2;
    private static final int DB_PLANUNG   =  4;
    private static final int SVC_OKSTRA   =  8;
    private static final int SVC_ALTDATEN = 16;
    private static final int SVC_PLANUNG  = 32;
    private static int DB_FLAG = 0;

    /**
     * Plattform
     */
    private static String platformName = "";

    /**
     * EPSG-Nummer des Koordinatenreferenzsystems in der Datenbank
     */
    private static int SRID = 25832;
    
    /**
     * AdV-Nummer des Höhensystems in der Datenbank
     */
    private static int HSYS = 170;

    /**
     * Zeigt an ob der Loader-Thread aktiv ist oder nicht
     */
    private static boolean loaderActive = false;

    /**
     * log4j-Logger.
     */
    private static final Logger logger = Logger.getRootLogger();

    /**
     * Schemaname Service-Datenbank
     */
    protected String metadataSchema = "public";

    /**
     * Return Path
     */
    protected String returnPath;

    /**
     * Upload Path
     */
    protected String uploadPath;

    /**
     * Error Export Path
     */
    protected String errorExportPath;

    /**
     * Übergabeverzeichnis für lokale Dateien (Parameter uploadfile, Init-Parameter
     * okstraTransferpath), aufgelöst; null, wenn nicht freigegeben
     */
    protected Path transferPath;
    
    /**
     * Flagge zum Zurückschreiben fehlerhafter Fachobjekte
     */
    protected boolean writeErrorObjects = false;
    
    /**
     * Typ der Rückgabe des Dienstes
     *   Alternativen:
     *     plain freies Textformat
     *     json  JSON-Format {
     *                         "success": boolean,
     *                         "auftragsnummer": integer, 
     *                         "message": string
     *                       }
     */
    protected String serviceReturnType = "plain";

    /**
     * Web Server Prefix
     */
    protected String webServerPrefix = "/vermdb/";

    /**
     * Angabe der Behörde
     */
    protected String okstraBehoerde;

    /**
     * Angabe des Langnamens zur Behörde
     */
    protected String okstraLangname;

    /**
     * Angabe des Datenbanknamens
     */
    protected String okstraDbName;
    /** Version der Vermessungsdatenbank aus dem Manifest, gelesen in init() */
    protected Versionsangabe versionsangabe;

    /**
     * Angabe der FB-Listenversion für den Datenexport
     */
    protected String okstraFBliste = "";
    protected String okstraFBlisteBuLa = "";
    protected String okstraFBlisteNr = "";
    protected int okstraFBlisteHaupt = 1;
    protected int okstraFBlisteUnter = 6;
    protected eBundesland okstraFBlisteLand = eBundesland.bl_ohne;
    protected final Set<String> fbAusschluss = new HashSet<>();
    protected boolean checkFbExclusion = false;

    /**
     * Angabe der OKSTRA-Version für die Haltung der Daten in der Datenbank
     */
    protected String okstraVersion;
    public String getOkstraVersion() {
        return okstraVersion;
    }

    /**
     * Angabe der OKSTRA-Version für die Haltung der Daten für die Altdaten
     */
    protected String okstraVersionAltdaten;

    /**
     * Angabe der OKSTRA-Version für die Haltung der Daten für die Planungsdaten
     */
    protected String okstraVersionPlanung;

    /**
     * Angabe der OKSTRA-Version für die Haltung der Daten in der Datenbank
     */
    protected de.okstra.oklabi.Version dbVersion = null;
    public de.okstra.oklabi.Version getDbVersion() {
        return dbVersion;
    }

    /**
     * Angabe der OKSTRA-Version für die Haltung der Daten für die Altdaten
     */
    protected de.okstra.oklabi.Version dbVersionAltdaten = null;

    /**
     * Angabe der OKSTRA-Version für die Haltung der Daten für die Planungsdaten
     */
    protected de.okstra.oklabi.Version dbVersionPlanung = null;

    /**
     * Liste von OKSTRA-Versionen, zwischen denen per
     * Migration gewechselt werden können
     */
    protected String okstraVersionRead;

    /**
     * Liste von OKSTRA-Versionen, die exportiert können
     */
    protected String okstraVersionWrite;

    /**
     * Name der Auftragsliste
     */
    protected String okstraJobliste;

    /** Datenbank Pool OKSTRA-Daten
    */
    private DbVerbindung okstraConnection = null;
    public DbVerbindung getOkstraConnection() {
        return okstraConnection;
    }

    /** Datenbank Pool VermdDB
    */
    private DbVerbindung vermdbConnection = null;
    public DbVerbindung getVermdbConnection() {
        return vermdbConnection;
    }

    /** Datenbank Pool OKSTRA-Altdaten
    */
    private DbVerbindung okstraAltConnection = null;

    /** Datenbank Pool VermdDB-Altdaten
    */
    private DbVerbindung vermdbAltConnection = null;

    /** Datenbank Pool OKSTRA-Planungsdaten
    */
    private DbVerbindung okstraPlanungConnection = null;

    /** Datenbank Pool VermdDB-Planungsdaten
    */
    private DbVerbindung vermdbPlanungConnection = null;

    /** Flagge für OKSTRA-Version in Kopfdaten
    */
    private boolean okstraVersionHeader = false;

    /** Flagge für Kopfdaten in Metadatenschema
    */
    private boolean headerInMetadata = false;
    
    /**
     * Flagge für ausführlichere Protokollierung
     */
    protected boolean verboseLogging = false;
    
    private static final String QUEUE = "warteschlange";
    private static final int SRSFACTOR = 1000000;
    
    private final String marker = "%marker%";
    private final String successMessage = "Auftrag erfasst unter Nummer: ";
    
    private static Verarbeiter OKWS_WORKER = null;
    
    /**
     * Initialisierungen. Hier kommt z.B. die
     * Konstruktion des Kommunikationskanals für den OkWS hinein.
     */
    private boolean initializeBackend(String response, int port) {
        synchronized (this) {
            if (OKWS_WORKER == null) {
                try {
                    Umgebung.SetzeEinstellung(eEinstellung.pStandardKanal, port);
                    OKWS_WORKER = new OkwsWorker(this, response, this.dbVersion, SRID);
                    Kommunikationskanal.Registriere(OKWS_WORKER);
                    Kommunikationskanal.Starte();
                } catch (Exception ex) {
                    logger.warn("Zugriff auf Umgebungseigenschaft fehlgeschlagen: " + ex.getMessage());
                    return false;
                }
            }
            return true;
        }
    }
    
    /**
     * Konstruiert Servlet-Objekt
     */
    public Okstra2DbServlet() {
        this.returnPath = "";
        this.uploadPath = "";
        this.okstraVersion = "";
        this.okstraVersionRead = "";
        this.okstraVersionWrite = "";
        this.okstraBehoerde = "";
        this.okstraLangname = "";
        this.okstraDbName = "Vermessungsdatenbank";
        this.okstraJobliste = "projekt";
    }
    
    /**
     * Räumt das Servlet auf.
     */
    @Override
    public void destroy() {
        try {
            if (vermdbConnection != null) {
                vermdbConnection.Schliesse();
                vermdbConnection = null;
            }
        }
        catch (Exception ex) {
        }
        try {
            if (okstraConnection != null) {
                okstraConnection.Schliesse();
                okstraConnection = null;
            }
        }
        catch (Exception ex) {
        }
        try {
            if (vermdbAltConnection != null) {
                vermdbAltConnection.Schliesse();
                vermdbAltConnection = null;
            }
        }
        catch (Exception ex) {
        }
        try {
            if (okstraAltConnection != null) {
                okstraAltConnection.Schliesse();
                okstraAltConnection = null;
            }
        }
        catch (Exception ex) {
        }
        try {
            if (vermdbPlanungConnection != null) {
                vermdbPlanungConnection.Schliesse();
                vermdbPlanungConnection = null;
            }
        }
        catch (Exception ex) {
        }
        try {
            if (okstraPlanungConnection != null) {
                okstraPlanungConnection.Schliesse();
                okstraPlanungConnection = null;
            }
        }
        catch (Exception ex) {
        }
        try {
            if (OKWS_WORKER != null) {
                OKWS_WORKER.Vernichte();
                OKWS_WORKER = null;
            }
        }
        catch (Exception ex) {
        }
        
        // Treiber deregistrieren wenn er aus dem Kontext der WebApp kommt
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() == cl) {
                // This driver was registered by the webapp's ClassLoader, so deregister it:
                try {
                    DriverManager.deregisterDriver(driver);
                } catch (SQLException e) {
                    System.out.println("Fehler bei Deregistrierung des Treibers " + driver.toString() +
                            " " + e.getMessage());
                }
            }
        }
        
        // Enjoy...
        System.gc();
      
        // Es bleibt ein Timer-Thread stehen (ID 'Timer-0'),
        // kommt aus dem PostgreSQL-Umfeld.
        // Deshalb einige Sekunden warten und dann den Prozess abschiessen
        Thread killingThread = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(10 * 1000);
                    System.exit(0);
                } catch(InterruptedException v) {
                }
                finally {
                }
            }  
        };
        killingThread.start();
    }

    /**
     * Initialisiere Servlet.
     * @throws ServletException
     */
    @Override
    public void init() throws ServletException {
        // Platzhalter für Umgebungsvariablen, die der Tomcat nicht ersetzt hat; vor allem
        // anderen, denn schon der Pfad des Protokolls kommt aus der web.xml
        Map<String, String> parameter = new LinkedHashMap<>();
        for (String name : Collections.list(getServletConfig().getInitParameterNames())) {
            parameter.put(name, getServletConfig().getInitParameter(name));
        }
        for (String name : Collections.list(getServletContext().getInitParameterNames())) {
            parameter.putIfAbsent(name, getServletContext().getInitParameter(name));
        }
        List<String> offen = Platzhalter.offene(parameter);
        if (!offen.isEmpty()) {
            throw new ServletException(Platzhalter.meldung(offen));
        }

        String oklabiLogPath = getLogPath();
        
        // Die Konsole richtet log4j2.xml im WAR ein (gleiches Format); hier nur die Datei
        PatternLayout layout = new PatternLayout("%d{ISO8601} %-5p: %m%n");
        try {
            FileAppender fileAppender = new FileAppender(layout, 
                    oklabiLogPath + "Svc_" + getLogFileDateString() + ".log", false);
            logger.addAppender(fileAppender);

            // ALL | DEBUG | INFO | WARN | ERROR | FATAL | OFF:
            logger.setLevel(Level.DEBUG);
        } catch (IOException ex) {
            throw new ServletException("Fehler bei Einrichtung des Protokolls für den Service.");
        }
        
        versionsangabe = Versionsangabe.lies(getServletContext().getResourceAsStream("/META-INF/MANIFEST.MF"));
        logger.info("Vermessungsdatenbank " + versionsangabe);
        String val = System.getProperty("os.name");
        logger.info("Betriebssystemumgebung: " + val);
        String os = val.toLowerCase(java.util.Locale.ENGLISH);
        if (os.contains("win")) {
            platformName = "Windows";
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            platformName = "Linux/Unix";
        } else if (os.contains("mac")) {
            platformName = "MacOS";
        }

        // Die native OKLABI laden, bevor eine ihrer Klassen benutzt wird. Der Loader der
        // OKLABI übergeht einen Fehlschlag stumm; sonst scheitert erst der erste native
        // Aufruf, mit einem UnsatisfiedLinkError ohne Hinweis auf die Ursache.
        if (!OklabiLoader.load()) {
            String msg = "Die nativen Bibliotheken der OKLABI konnten nicht geladen werden (java.library.path: "
                    + System.getProperty("java.library.path") + ").";
            if ("Windows".equals(platformName)) {
                msg += " Unter Windows muss das Verzeichnis mit den DLLs der OKLABI im PATH des Tomcat-Dienstes"
                        + " stehen: Windows sucht die DLLs, von denen die OKLABI abhängt (u. a. OklabiTiff,"
                        + " libcrypto-3, libssl-3), nicht im java.library.path.";
            }
            msg += " Einzelheiten zeigt der Loader mit -Doklabi.loader.verbose=true in der Standardausgabe des Tomcat.";
            logger.error(msg);
            throw new ServletException(msg);
        }

        val = this.getServletConfig().getInitParameter("verboseLogging");
        if (val != null)
            verboseLogging = "1".equals(val) || "t".equals(val) || "true".equals(val);
        val = this.getServletConfig().getInitParameter("returnFormat");
        if (val != null) {
            if (val.equalsIgnoreCase("json")) {
                serviceReturnType = "json";
            } else {
                serviceReturnType = "plain";
            }
        }
        val = this.getServletConfig().getInitParameter("okstraTxtpool");
        if (val == null)
            val = this.getServletContext().getInitParameter("okstraTxtpool");
        if (val != null && ("1".equals(val) || "t".equals(val) || "true".equals(val))) {
            try {
                Umgebung.SetzeEinstellung(eEinstellung.pSqlLokaleIdVerwenden, false);
                Umgebung.SetzeEinstellung(eEinstellung.pSqlTextPoolVerwenden, true);
                logger.info("Speichere komprimierte Texte in Datenbank, benutze Datenbank-Identifizierer");
            } catch (Ausnahme ex) {
                logger.warn("Zugriff auf Umgebungseigenschaft fehlgeschlagen: " + ex.getMessage());
            }
        } else {
            try {
                Umgebung.SetzeEinstellung(eEinstellung.pSqlLokaleIdVerwenden, false);
                if (Umgebung.GibEinstellung(eEinstellung.pSqlTextPoolVerwenden)) {
                    logger.info("Speichere komprimierte Texte in Datenbank, benutze Datenbank-Identifizierer");
                } else {
                    logger.info("Speichere Klartexte in Datenbank, benutze Datenbank-Identifizierer");
                }
            } catch (Ausnahme ex) {
                logger.warn("Zugriff auf Umgebungseigenschaft fehlgeschlagen: " + ex.getMessage());
            }
        }
        val = this.getServletConfig().getInitParameter("headerInMetadata");
        if (val != null)
            headerInMetadata = "1".equals(val) || "t".equals(val) || "true".equals(val);
        val = this.getServletConfig().getInitParameter("okstraVersionHeader");
        if (val != null)
            okstraVersionHeader = "1".equals(val) || "t".equals(val) || "true".equals(val);
        // Wenn die Kopfdaten im Metadatenschema landen sollen,
        // muss die Version gesetzt werden
        if (!okstraVersionHeader && headerInMetadata)
            okstraVersionHeader = true;
        okstraVersion = this.getServletConfig().getInitParameter("okstraVersionDatabase");
        dbVersion = DbMetadaten.DekodiereVersion(okstraVersion);
        okstraVersionAltdaten = this.getServletConfig().getInitParameter("okstraVersionAltdaten");
        if (okstraVersionAltdaten != null && !okstraVersionAltdaten.trim().isEmpty())
            dbVersionAltdaten = DbMetadaten.DekodiereVersion(okstraVersionAltdaten);
        okstraVersionPlanung = this.getServletConfig().getInitParameter("okstraVersionPlanung");
        if (okstraVersionPlanung != null && !okstraVersionPlanung.trim().isEmpty())
            dbVersionPlanung = DbMetadaten.DekodiereVersion(okstraVersionPlanung);
        val = this.getServletConfig().getInitParameter("svc_schema");
        if (val != null && !val.isEmpty()) {
            metadataSchema = val;
            DbVerbindung.setSchemaPrefix(val);
        }
        okstraVersionRead = this.getServletConfig().getInitParameter("okstraVersionRead");
        okstraVersionWrite = this.getServletConfig().getInitParameter("okstraVersionWrite");
        okstraBehoerde = this.getServletConfig().getInitParameter("okstraAuthority");
        if (okstraBehoerde == null) {
            okstraBehoerde = this.getServletConfig().getServletContext().getInitParameter("okstraAuthority");
            if (okstraBehoerde == null) {
                okstraBehoerde = "unbekannt";
            }
        }
        logger.info("Verwende Behörde = " + okstraBehoerde);
        okstraLangname = this.getServletConfig().getInitParameter("okstraAuthName");
        if (okstraLangname == null) {
            okstraLangname = this.getServletContext().getInitParameter("okstraAuthName");
            if (okstraLangname == null) {
                okstraLangname = "unbekannt";
            }
        }
        logger.info("Verwende Langnamen für Behörde = " + okstraLangname);
        okstraDbName = this.getServletConfig().getInitParameter("okstraDbName");
        if (okstraDbName == null) {
            okstraDbName = this.getServletContext().getInitParameter("okstraDbName");
            if (okstraDbName == null) {
                okstraDbName = "Vermessungsdatenbank";
            }
        }
        logger.info("Verwende Namen für Datenbank = " + okstraDbName);
        okstraJobliste = this.getServletConfig().getInitParameter("okstraJobliste");
        if (okstraJobliste == null) {
            okstraJobliste = this.getServletContext().getInitParameter("okstraJobliste");
            if (okstraJobliste == null) {
                okstraJobliste = "projekt";
            }
        }
        logger.info("Verwende Auftragstabelle = " + okstraJobliste);
        val = this.getServletConfig().getInitParameter("okstraFbliste");
        if (val == null) {
            val = this.getServletContext().getInitParameter("okstraFbliste");
        }
        if (val != null && !val.isEmpty()) {
            okstraFBliste = val;
        }
        boolean bFound = false;
        if (!okstraFBliste.isEmpty()) {
            String[] parts = okstraFBliste.split(" ");
            if (parts.length == 2) {
                String[] numbers = parts[1].split("\\.");
                if (numbers.length == 2) {
                    int hv;
                    int uv;
                    try {
                        hv = Integer.parseInt(numbers[0]);
                        uv = Integer.parseInt(numbers[1]);
                        FblVersion fbv = FblVersion.Erzeuge(hv, uv, parts[0]);
                        if (fbv.IstGueltig()) {
                            okstraFBlisteBuLa = parts[0];
                            okstraFBlisteNr = parts[1];
                            okstraFBlisteHaupt = hv;
                            okstraFBlisteUnter = uv;
                            bFound = true;
                        }
                        fbv.Vernichte();
                        hv = 1;
                        while (hv < 17)
                        {
                            String bl = Umgebung.GibTextZuWert(eAufzaehlungen.tBundesland, hv);
                            if (bl.equals(okstraFBlisteBuLa))
                            {
                                okstraFBlisteLand = eBundesland.getEnum(hv);
                                break;
                            }
                            ++hv;
                        }
                    } catch (Exception ex) {
                        // Fehler, keine Angabe vornehmen
                        okstraFBliste = "";
                    }
                }
            }
        }
        if (bFound) {
            logger.info("Vorgabe für FBListe: " + okstraFBliste);
        } else {
            logger.info("Vorgabe für FBListe: " + (okstraFBliste.isEmpty() ? "<nicht belegt>" : "<ungültig belegt> " + okstraFBliste));
        }
        val = this.getServletConfig().getInitParameter("okstraFblisteCheck");
        if (val == null)
            val = this.getServletContext().getInitParameter("okstraFblisteCheck");
        if (val != null && !val.isEmpty()) {
            try {
                Umgebung.SetzeEinstellung(eEinstellung.pFbFehlerbehandlung, val);
                logger.info("Geforderter Prüfmodus für Fachbedeutungen: " + val);
            } catch (Ausnahme ex) {
                logger.warn("Zugriff auf Umgebungseigenschaft fehlgeschlagen: " + ex.getMessage());
            }
        }
        try {
            val = Umgebung.GibEinstellung(eEinstellung.pFbFehlerbehandlung);
            logger.info("Eingestellter Prüfmodus für Fachbedeutungen: " + val);
        } catch (Ausnahme ex) {
            logger.warn("Zugriff auf Umgebungseigenschaft fehlgeschlagen: " + ex.getMessage());
        }
        val = this.getServletConfig().getInitParameter("okstraFblisteExclusion");
        if (val == null)
            val = this.getServletContext().getInitParameter("okstraFblisteExclusion");
        if (val != null && !val.isEmpty()) {
            checkFbExclusion = "1".equals(val) || "t".equals(val) || "true".equals(val);
            logger.info("Prüfung Fachbedeutungs-Ausschlüsse: " + (checkFbExclusion ? "Ja" : "Nein"));
        }
        val = this.getServletConfig().getInitParameter("okstraLagesystem");
        if (val == null)
            val = this.getServletContext().getInitParameter("okstraLagesystem");
        if (val != null && !val.isEmpty())
        {
            // Künftige Erweiterungsmöglichkeit: Lagesystem einstellen
            // Zurzeit ist nur UTM32 vorgesehen
            boolean found = true;
            switch (val) {
                case "UTM32":
                case "25832":
                    SRID = 25832;
                    break;
                default:
                    found = false;
                    break;
            }
            try {
                if (!found)
                    logger.warn("Lagesystem " + val + " wird nicht unterstützt. Es wird " + Umgebung.GibTextZuWert(eAufzaehlungen.tKoordRefSys, SRID) + " benutzt.");
                else
                    logger.info("Lagesystem in der Datenbank: " + Umgebung.GibTextZuWert(eAufzaehlungen.tKoordRefSys, SRID));
            } catch (Ausnahme ex) {
                logger.warn("Zugriff auf Umgebungseigenschaft fehlgeschlagen: " + ex.getMessage());
            }
        }
        val = this.getServletConfig().getInitParameter("okstraHoehensystem");
        if (val == null)
            val = this.getServletContext().getInitParameter("okstraHoehensystem");
        if (val != null && !val.isEmpty())
        {
            // Künftige Erweiterungsmöglichkeit: Höhensystem einstellen
            // Zurzeit ist nur DHHN_2016 vorgesehen
            boolean found = true;
            switch (val) {
                case "DHHN_2016":
                case "DHHN2016":
                case "170":
                    HSYS = 170;
                    break;
                default:
                    found = false;
                    break;
            }
            try {
                if (!found)
                    logger.warn("Höhensystem " + val + " wird nicht unterstützt. Es wird " + Umgebung.GibTextZuWert(eAufzaehlungen.tKoordRefSys, HSYS) + " benutzt.");
                else
                    logger.info("Höhensystem in der Datenbank: " + Umgebung.GibTextZuWert(eAufzaehlungen.tKoordRefSys, HSYS));
            } catch (Ausnahme ex) {
                logger.warn("Zugriff auf Umgebungseigenschaft fehlgeschlagen: " + ex.getMessage());
            }
        }
        // Die Grenzen für Uploads stehen seit der Umstellung auf die Servlet-API in
        // <multipart-config> der web.xml; die früheren Init-Parameter wirken nicht mehr
        for (String name : new String[] { "max_file_size", "max_request_size", "file_size_threshold" }) {
            if (this.getServletConfig().getInitParameter(name) != null) {
                logger.warn("Init-Parameter " + name + " wird nicht mehr ausgewertet, " +
                        "die Grenzen für Uploads stehen in <multipart-config> der web.xml");
            }
        }
        
        // den Pfad zum Return- und Upload-Verzeichnis zusammenbauen
        returnPath = getReturnPath();
        uploadPath = getUploadPath();
        transferPath = getTransferPath();

        // den Pfad zum Export-Verzeichnis für fehlerhafte Fachobjekte zusammenbauen
        errorExportPath = getErrorExportPath();

        // den Präfix für den Webserver bestimmen
        val = getWebServerPrefix();
        if (val != null && !val.isEmpty())
            webServerPrefix = val;
        
        // die Datenbankverbindung(en) aufsammeln
        String dsres = this.getInitParameter("okstra_database");
        DB_FLAG = 0;
        boolean bRes = true;
        try {
            if (dsres != null)
            {
                Context ctx = (Context) new InitialContext().lookup("java:comp/env");
                org.apache.tomcat.dbcp.dbcp2.BasicDataSource dSrc =
                        (org.apache.tomcat.dbcp.dbcp2.BasicDataSource) ctx.lookup(dsres);
                okstraConnection = DbVerbindung.GibPGVerbindung((DataSource)dSrc, dSrc.getUrl(), dSrc.getUsername(), dSrc.getPassword());
                if (okstraConnection.GibPGSqlConnection() == null) {
                    String msg = String.format("OKSTRA-Datenquelle %s ist nicht verfügbar", dsres);
                    logger.error(msg);
                    throw new ServletException(msg);
                }
                okstraConnection.getDbMetadaten().setBrauchtMetadaten(true);
                okstraConnection.getDbMetadaten().setBrauchtQueue(false);
                String schema = "oks_" + okstraVersion.replace(".", "");
                try {
                    okstraConnection.setSchema(schema);
                    okstraConnection.getDbMetadaten().setDbSchema(schema);
                }
                catch (Exception ex) {
                    logger.error(ex.getMessage());
                    throw new ServletException(ex.getMessage());
                }
                logger.info("OKSTRA-Datenquelle: " + dsres);
                DB_FLAG = DB_OKSTRA;
            }
            else {
                // harter Fehler wenn diese Datenbank fehlt
                String msg = String.format("Datenquelle %s ist nicht verfügbar", "für OKSTRA-Daten");
                logger.error(msg);
                throw new ServletException(msg);
            }
            dsres = this.getInitParameter("okstra_data_alt");
            if (dsres != null)
            {
                bRes = true;
                Context ctx = (Context) new InitialContext().lookup("java:comp/env");
                try {
                    org.apache.tomcat.dbcp.dbcp2.BasicDataSource dSrc =
                            (org.apache.tomcat.dbcp.dbcp2.BasicDataSource) ctx.lookup(dsres);
                    okstraAltConnection = DbVerbindung.GibPGVerbindung((DataSource)dSrc, dSrc.getUrl(), dSrc.getUsername(), dSrc.getPassword());
                }
                catch (NamingException | SQLException ex) {
                    okstraAltConnection = null;
                    bRes = false;
                }
                if (!bRes || (okstraAltConnection != null && okstraAltConnection.GibPGSqlConnection() == null)) {
                    String msg = String.format("OKSTRA-Datenquelle für Altdaten %s ist angegeben aber nicht verfügbar", dsres);
                    logger.warn(msg);
                    try {
                        if (okstraAltConnection != null)
                            okstraAltConnection.Schliesse();
                    } catch (Exception ee) {
                    }
                    okstraAltConnection = null;
                }
                else {
                    okstraAltConnection.getDbMetadaten().setBrauchtMetadaten(true);
                    okstraAltConnection.getDbMetadaten().setBrauchtQueue(false);
                    String schema = "oks_" + okstraVersionAltdaten.replace(".", "");
                    try {
                        okstraAltConnection.setSchema(schema);
                        okstraAltConnection.getDbMetadaten().setDbSchema(schema);
                    }
                    catch (Exception ex) {
                        logger.error(ex.getMessage());
                        throw new ServletException(ex.getMessage());
                    }
                    logger.info("OKSTRA-Datenquelle für Altdaten: " + dsres);
                    DB_FLAG |= DB_ALTDATEN;
                }
            }
            else {
                String msg = String.format("Datenquelle %s ist nicht angegeben", "für OKSTRA-Altdaten");
                logger.info(msg);
            }
            dsres = this.getInitParameter("okstra_data_planung");
            if (dsres != null)
            {
                bRes = true;
                Context ctx = (Context) new InitialContext().lookup("java:comp/env");
                try {
                    org.apache.tomcat.dbcp.dbcp2.BasicDataSource dSrc =
                            (org.apache.tomcat.dbcp.dbcp2.BasicDataSource) ctx.lookup(dsres);
                    okstraPlanungConnection = DbVerbindung.GibPGVerbindung((DataSource)dSrc, dSrc.getUrl(), dSrc.getUsername(), dSrc.getPassword());
                }
                catch (NamingException | SQLException ex) {
                    okstraPlanungConnection = null;
                    bRes = false;
                }
                if (!bRes || (okstraPlanungConnection != null && okstraPlanungConnection.GibPGSqlConnection() == null)) {
                    String msg = String.format("OKSTRA-Datenquelle für Planungsdaten %s ist angegeben aber nicht verfügbar", dsres);
                    logger.warn(msg);
                    try {
                        if (okstraPlanungConnection != null)
                            okstraPlanungConnection.Schliesse();
                    } catch (Exception ee) {
                    }
                    okstraPlanungConnection = null;
                } else {
                    okstraPlanungConnection.getDbMetadaten().setBrauchtMetadaten(true);
                    okstraPlanungConnection.getDbMetadaten().setBrauchtQueue(false);
                    String schema = "oks_" + okstraVersionPlanung.replace(".", "");
                    try {
                        okstraPlanungConnection.setSchema(schema);
                        okstraPlanungConnection.getDbMetadaten().setDbSchema(schema);
                    }
                    catch (Exception ex) {
                        logger.error(ex.getMessage());
                        throw new ServletException(ex.getMessage());
                    }
                    logger.info("OKSTRA-Datenquelle für Planungsdaten: " + dsres);
                    DB_FLAG |= DB_PLANUNG;
                }
            }
            else {
                String msg = String.format("Datenquelle %s ist nicht verfügbar", "für OKSTRA-Planungsdaten");
                logger.info(msg);
            }
            dsres = this.getInitParameter("svc_vermdb");
            if (dsres != null)
            {
                Context ctx = (Context) new InitialContext().lookup("java:comp/env");
                org.apache.tomcat.dbcp.dbcp2.BasicDataSource dSrc =
                        (org.apache.tomcat.dbcp.dbcp2.BasicDataSource) ctx.lookup(dsres);
                vermdbConnection = DbVerbindung.GibPGVerbindung((DataSource)dSrc, dSrc.getUrl(), dSrc.getUsername(), dSrc.getPassword());
                if (vermdbConnection.GibPGSqlConnection() == null) {
                    String msg = String.format("Service-VermDB-Datenquelle %s ist nicht verfügbar", dsres);
                    logger.error(msg);
                    throw new ServletException(msg);
                }
                vermdbConnection.getDbMetadaten().setBrauchtMetadaten(false);
                vermdbConnection.getDbMetadaten().setBrauchtQueue(true);
                logger.info("Service-VermDB-Datenquelle: " + dsres);
                DB_FLAG |= SVC_OKSTRA;
            }
            else {
                // harter Fehler wenn diese Datenbank fehlt
                String msg = String.format("Datenquelle %s ist nicht verfügbar", "für Service-VermDB-Daten");
                logger.error(msg);
                throw new ServletException(msg);
            }
            dsres = this.getInitParameter("svc_vermdb_alt");
            if (dsres != null)
            {
                bRes = true;
                Context ctx = (Context) new InitialContext().lookup("java:comp/env");
                try {
                    org.apache.tomcat.dbcp.dbcp2.BasicDataSource dSrc =
                            (org.apache.tomcat.dbcp.dbcp2.BasicDataSource) ctx.lookup(dsres);
                    vermdbAltConnection = DbVerbindung.GibPGVerbindung((DataSource)dSrc, dSrc.getUrl(), dSrc.getUsername(), dSrc.getPassword());
                }
                catch (NamingException | SQLException ex) {
                    vermdbAltConnection = null;
                    bRes = false;
                }
                if (!bRes || (vermdbAltConnection!= null & vermdbAltConnection.GibPGSqlConnection() == null)) {
                    String msg = String.format("Service-VermDB-Altdaten-Datenquelle %s ist angegeben aber nicht verfügbar", dsres);
                    logger.warn(msg);
                    try {
                        if (vermdbAltConnection != null)
                            vermdbAltConnection.Schliesse();
                    } catch (Exception ee) {
                    }
                    vermdbAltConnection = null;
                } else {
                    vermdbAltConnection.getDbMetadaten().setBrauchtMetadaten(false);
                    vermdbAltConnection.getDbMetadaten().setBrauchtQueue(true);
                    logger.info("Service-VermDB-Altdaten-Datenquelle: " + dsres);
                    if ((DB_FLAG & DB_ALTDATEN) == 0)
                    {
                        String msg = String.format("Datenquelle %s ist nicht nutzbar ohne zugehörige OKSTRA-Daten", "für Service-VermDB-Altdaten");
                        try {
                            vermdbAltConnection.Schliesse();
                        } catch (Exception e) {
                        }
                        vermdbAltConnection = null;
                        logger.error(msg);
                        throw new ServletException(msg);
                    }
                    DB_FLAG |= SVC_ALTDATEN;
                }
            }
            else {
                String msg = String.format("Datenquelle %s ist nicht verfügbar", "für Service-VermDB-Altdaten");
                if ((DB_FLAG & DB_ALTDATEN) != 0) {
                    logger.error(msg + ", die zugehörige OKSTRA-Datenquelle ist jedoch angegeben");
                    throw new ServletException(msg);
                }
                logger.info(msg);
            }
            dsres = this.getInitParameter("svc_vermdb_planung");
            if (dsres != null)
            {
                bRes = true;
                Context ctx = (Context) new InitialContext().lookup("java:comp/env");
                try {
                    org.apache.tomcat.dbcp.dbcp2.BasicDataSource dSrc =
                            (org.apache.tomcat.dbcp.dbcp2.BasicDataSource) ctx.lookup(dsres);
                    vermdbPlanungConnection = DbVerbindung.GibPGVerbindung((DataSource)dSrc, dSrc.getUrl(), dSrc.getUsername(), dSrc.getPassword());
                }
                catch (NamingException | SQLException ex) {
                    vermdbPlanungConnection = null;
                    bRes = false;
                }
                if (!bRes || (vermdbPlanungConnection != null && vermdbPlanungConnection.GibPGSqlConnection() == null)) {
                    String msg = String.format("Service-VermDB-Planungsdaten-Datenquelle %s ist angegeben aber nicht verfügbar", dsres);
                    logger.warn(msg);
                    try {
                        if (vermdbPlanungConnection != null)
                            vermdbPlanungConnection.Schliesse();
                    } catch (Exception ee) {
                    }
                    vermdbPlanungConnection = null;
                } else {
                    vermdbPlanungConnection.getDbMetadaten().setBrauchtMetadaten(false);
                    vermdbPlanungConnection.getDbMetadaten().setBrauchtQueue(true);
                    logger.info("Service-VermDB-Planungsdaten-Datenquelle: " + dsres);
                    if ((DB_FLAG & DB_PLANUNG) == 0)
                    {
                        String msg = String.format("Datenquelle %s ist nicht nutzbar ohne zugehörige OKSTRA-Daten", "für Service-VermDB-Planungsdaten");
                        try {
                            vermdbPlanungConnection.Schliesse();
                        } catch (Exception e) {
                        }
                        vermdbPlanungConnection = null;
                        logger.error(msg);
                        throw new ServletException(msg);
                    }
                    DB_FLAG |= SVC_PLANUNG;
                }
            }
            else {
                String msg = String.format("Datenquelle %s ist nicht verfügbar", "für Service-VermDB-Planungsdaten");
                if ((DB_FLAG & DB_PLANUNG) != 0) {
                    logger.error(msg + ", die zugehörige OKSTRA-Datenquelle ist jedoch angegeben");
                    throw new ServletException(msg);
                }
                logger.info(msg);
            }
        }
        catch (NamingException | SQLException ex) {
            String msg = String.format("Datenquelle %s ist nicht verfügbar", dsres);
            logger.error(msg, ex);
            throw new ServletException(msg);
        }
        
        String cooTrans = this.getServletConfig().getInitParameter("coordTrans");
        if (cooTrans != null && !cooTrans.isEmpty()) {
            try {
                // Installiere die Bibliothek für Transformationen
                // Der Init-Parameter enthält den voll qualifizierten Klassennamen
                // Im Fehlerfall wird der Default-Transformierer (proj) benutzt
                Class cclass = Class.forName(cooTrans);
                TransformiererLoader loader = (TransformiererLoader) cclass.getDeclaredConstructor().newInstance();
                loader.load();
                logger.info("Transformationsbibliothek für " + cooTrans + " installiert.");
            }
            catch (RuntimeException ex) {
                logger.error("Transformationsbibliothek für " + cooTrans + " ist nicht verfügbar.", ex);
                // Trotzdem weitermachen
            }
            catch (Exception ex) {
                logger.error("Transformationsbibliothek für " + cooTrans + " ist nicht verfügbar.", ex);
                // Trotzdem weitermachen
            }
        }
        else {
            logger.info("Verwende PROJ-Transformationsbibliothek.");
        }

        // Tabelle mit den FB-Ausschlüssen lesen
        try {
            fbAusschluss.clear();
            if (checkFbExclusion) {
                liesFbAusschluss(okstraConnection);
                logger.info("Fachbedeutungs-Ausschlussliste mit " + String.valueOf(fbAusschluss.size()) + " Einträgen geladen.");
            }
        } catch (Exception ex) {
            String msg = String.format("Fehler bei Initialisierung der Fachbedeutungs-Ausschlussliste: %s",
                    ex.getMessage());
            logger.error("Fehler bei Initialisierung der FB-Ausschlussliste.", ex);
            throw new ServletException(msg);
        }
        
        // Protokoll für Service installieren
        try {
            Protokollant protokoll = JavaProtokollant.erzeugeProtokoll(logger, "");
            Umgebung.Registriere(protokoll, eProtokollTyp.meldungen);
            Db2Okstra.protokolliere(-1, "Protokoll für Service angeschlossen", eMeldTyp.info, eProtokollTyp.meldungen);
            String dbvsn = Umgebung.GibSchemaDBVersion();
            String okdat = Umgebung.GibOKLABIReleaseDatum();
            String okvsn = Umgebung.GibOKLABIReleaseVersion();
            Db2Okstra.protokolliere(-1, "OKLABI-Version vom " + okdat + " (" + okvsn + ") mit OKSTRA-Schemadatenbank " + dbvsn,
                    eMeldTyp.info, eProtokollTyp.meldungen);
            Db2Okstra.protokolliere(-1, "OKLABI-Bibliotheken hergestellt: " + (String)Umgebung.GibEinstellung(eEinstellung.pHerstellungsDatum),
                    eMeldTyp.info, eProtokollTyp.meldungen);
            Db2Okstra.protokolliere(-1, "Ressourcenverzeichnis: " + (String)Umgebung.GibEinstellung(eEinstellung.pRessourcen),
                    eMeldTyp.info, eProtokollTyp.meldungen);
            int threads = (int)Umgebung.GibEinstellung(eEinstellung.pAnzahlThreads);
            Db2Okstra.protokolliere(-1, "Ladestrategie: " + (threads < 0 ? "sequenziell" : "nebenläufig"),
                    eMeldTyp.info, eProtokollTyp.meldungen);
            
            val = this.getServletConfig().getInitParameter("okstraWSbackend");
            if (val != null && ("1".equals(val) || "t".equals(val) || "true".equals(val))) {
                Db2Okstra.protokolliere(-1, "Backend für OkWS wird eingerichtet",
                        eMeldTyp.info, eProtokollTyp.meldungen);
                val = this.getServletConfig().getInitParameter("okstraWSport");
                int port = (int)Umgebung.GibEinstellung(eEinstellung.pStandardKanal);
                if (val != null && !val.isEmpty()) {
                    try {
                        port = Integer.parseInt(val);
                        if (port < 0 || port > 65535) {
                            port = (int)Umgebung.GibEinstellung(eEinstellung.pStandardKanal);
                            Db2Okstra.protokolliere(-1, "Ungültige Kanalnummer für OkWS: " + val +
                                    ". Es wird der Standardkanal benutzt: " + String.valueOf(port),
                                    eMeldTyp.warnung, eProtokollTyp.meldungen);
                        }
                    } catch (Exception ex) {
                        Db2Okstra.protokolliere(-1, "Ungültige Kanalnummer für OkWS: " + val,
                                eMeldTyp.warnung, eProtokollTyp.meldungen);
                    }
                }
                val = this.getServletConfig().getInitParameter("okstraWSresponse");
                if (val != null) {
                    try {
                        if (!val.isEmpty() && !new File(val).isFile()) {
                            Db2Okstra.protokolliere(-1, "Default-Response für OkWS ist nicht verfügbar: " + val,
                                    eMeldTyp.warnung, eProtokollTyp.meldungen);
                            val = "";
                        }
                    } catch (Exception e) {
                        Db2Okstra.protokolliere(-1, "Fehler bei Zugriff auf Default-Response für OkWS ist nicht verfügbar: " +
                                val + ", Meldung: " + e.getMessage(),
                                eMeldTyp.warnung, eProtokollTyp.meldungen);
                        val = "";
                    }
                } else {
                    Db2Okstra.protokolliere(-1, "Default-Response für OkWS ist nicht angegeben",
                            eMeldTyp.warnung, eProtokollTyp.meldungen);
                    val = "";
                }
                if (!initializeBackend(val, port)) {
                    Db2Okstra.protokolliere(-1, "Backend für OkWS konnte nicht eingerichtet werden",
                            eMeldTyp.warnung, eProtokollTyp.meldungen);
                }
            } else {
                Db2Okstra.protokolliere(-1, "Es wird kein Backend für OkWS eingerichtet",
                        eMeldTyp.info, eProtokollTyp.meldungen);
            }
        }
        catch (Exception ex) {
            String msg = String.format("Protokollant ist nicht verfügbar: %s",
                    ex.getMessage());
            logger.error("Protokollant ist nicht verfügbar.", ex);
            throw new ServletException(msg);
        }
        
        // Laden erfolgreich, aber Bibliotheksversion unpassend?
        if (!OklabiLoader.MESSAGE.isEmpty()) {
            logger.error(OklabiLoader.MESSAGE);
            throw new ServletException(OklabiLoader.MESSAGE);
        }
        
        
        
        // Überschreiben beim Laden von Daten aktivieren
        DbAuswahl.setOverwriteDefault(false);
        
        
        // Die Bearbeitungsflagge in der Warteschlange zurücksetzen
        // und nächsten ausstehenden Request mit etwas Verzögerung bearbeiten
        RequestData reqData = new RequestData();
        reqData.addConnection(okstraConnection, vermdbConnection, dbVersion, okstraVersion);
        try {
            int job = manageQueue(-1, true, reqData, null, null);
            if (job > 0) {
                reqData.setAuftrag(job);
                readRequest(job, reqData);
                startLoaderThread(reqData, 20);
            }
            else if (haveAltdaten())
            {
                // bei den Altdaten nachschauen
                reqData.addConnection(okstraAltConnection, vermdbAltConnection, dbVersionAltdaten, okstraVersionAltdaten);
                job = manageQueue(-1, true, reqData, null, null);
                if (job > 0) {
                    reqData.setAuftrag(job);
                    readRequest(job, reqData);
                    startLoaderThread(reqData, 20);
                }
            }
            else if (havePlanung())
            {
                // bei den Planungsdaten nachschauen
                reqData.addConnection(okstraPlanungConnection, vermdbPlanungConnection, dbVersionPlanung, okstraVersionPlanung);
                job = manageQueue(-1, true, reqData, null, null);
                if (job > 0) {
                    reqData.setAuftrag(job);
                    readRequest(job, reqData);
                    startLoaderThread(reqData, 20);
                }
            }
        }
        catch (Exception ex) {
            String msg = String.format("Fehler bei Initialisierung der Auftragsbearbeitung: %s",
                    ex.getMessage());
            logger.error("Fehler bei Initialisierung der Auftragsbearbeitung.", ex);
            throw new ServletException(msg);
        }
    }
    
    private void liesFbAusschluss(DbVerbindung verb) throws Exception {
        ResultSet rs = null;
        try {
            String stmt = String.format("SELECT fachbedeutung, symbolart FROM %s.%s WHERE blocked;", metadataSchema, "fbausschluss");
            rs = verb.PGDatenLesen(stmt, null);
            if (rs != null)
            {
                while (rs.next())
                {
                    String fb = rs.getString(1);
                    String art = rs.getString(2);
                    if (fb != null && art != null) {
                        fbAusschluss.add(fb + "::" + art);
                    }
                }
            }
        } finally {
            if (rs != null)
                rs.close();
        }
    }

    /**
     * Für den Webservice unter Linux muss die Lesbarkeit der Datei gewährleistet werden.
     * @param fileName Dateiname
     * @param bRek Flagge für Ordner
     * @return Problemmeldung oder Leerstring
     */
    public static String erlaubeZugriff(String fileName, boolean bRek) {
        File file = new File(fileName);
        System.out.print("Setze Berechtigungen für " + file.getAbsolutePath() + "\n");
        if (!"Windows".equalsIgnoreCase(platformName)) {
            // Paranoia: platformName kann falsch angegeben sein
            // Wenn es Backslash oder Doppelpunkt im Dateinamen gibt, sind wir unter Windows
            if (!file.getAbsolutePath().contains("\\") && !file.getAbsolutePath().contains(":")) {
                try {
                    String[] parms = bRek 
                            ? new String[] { "chmod", "-R", "755", fileName }
                            : new String[] { "chmod", "644", fileName };
                    Process process = Runtime.getRuntime().exec(parms);
                    process.waitFor();
                } catch (Throwable ex) {
                    return "Fehler beim Setzen der Zugriffsrechte für Datei " + fileName + " : " + ex.getMessage();
                }
            }
        }
        return "";
    }

    private boolean haveAltdaten() {
        return okstraAltConnection != null &&
               vermdbAltConnection != null &&
               dbVersionAltdaten != null &&
               okstraVersionAltdaten != null && !okstraVersionAltdaten.trim().isEmpty();
    }
    
    private boolean havePlanung() {
        return okstraPlanungConnection != null &&
               vermdbPlanungConnection != null &&
               dbVersionPlanung != null &&
               okstraVersionPlanung != null && !okstraVersionPlanung.trim().isEmpty();
    }
    
    private void startUnloadThread(final PostRequestContent reqData, final int waitSeconds) throws Exception {
        Thread unloadThread = new Thread() {
            @Override
            public void run() {
                try {
                    doUnload(reqData, waitSeconds);
                } catch(InterruptedException v) {
                    logger.error("Entladefehler: ", v);
                }
                finally {
                    try {
                        JavaProtokollant.entferneProtokoll();
                    }
                    catch (Exception ex) {
                    }
                }
            }  
        };
        unloadThread.start();
    }
    
    private void startLoaderThread(final RequestData reqData, final int waitSeconds) throws Exception {
        Thread loaderThread = new Thread() {
            @Override
            public void run() {
                try {
                    doLoad(reqData, waitSeconds);
                } catch(InterruptedException v) {
                    logger.error("Ladefehler: ", v);
                }
                finally {
                    try {
                        JavaProtokollant.entferneProtokoll();
                    }
                    catch (Exception ex) {
                    }
                }
            }  
        };
        loaderThread.start();
    }
    
    /**
     * Nimmt Registrierung einer Datei für die spätere Verarbeitung entgegen.Die Datei wird auf das Vorliegen einer gültigen OKSTRA-Version geprüft.
     * @param fSessionId
     * @param fProject
     * @param fTimeStamp
     * @param fNameClient
     * @param fNameLokal
     * @param fFormat
     * @param nArt
     * @param fDatum
     * @param fThema
     * @param fTypes
     * @param fbliste
     * @param fbula
     * @param fsrs
     * @param fhs
     * @return Identifizierer für Upload
     * @throws RemoteException 
     */
    public String registerUpload(String fSessionId, String fProject, String fTimeStamp, String fNameClient,
            String fNameLokal, String fFormat, int nArt,
            String fDatum, String fThema, String fTypes,
            String fbliste, String fbula, String fsrs, String fhs) throws RemoteException {
        return registerUpload(fSessionId, fProject, "", fTimeStamp, fNameClient, fNameLokal, fFormat, nArt,
                fDatum, fThema, fTypes, fbliste, fbula, fsrs, fhs, false, false, false);
    }
    
    /**
     * Nimmt Registrierung einer Datei für die spätere Verarbeitung entgegen.Die Datei wird auf das Vorliegen einer gültigen OKSTRA-Version geprüft.
     * @param fSessionId
     * @param fProject
     * @param fTimeStamp
     * @param fNameClient
     * @param fNameLokal
     * @param fFormat
     * @param nArt
     * @param fDatum
     * @param fThema
     * @param fTypes
     * @param fbliste
     * @param fbula
     * @param fsrs
     * @param fhs
     * @param bAltdaten
     * @param bPlanung
     * @param bCheck
     * @return Identifizierer für Upload
     * @throws RemoteException 
     */
    public String registerUpload(String fSessionId, String fProject, String fUser, String fTimeStamp, String fNameClient,
            String fNameLokal, String fFormat, int nArt,
            String fDatum, String fThema, String fTypes,
            String fbliste, String fbula, String fsrs, String fhs, boolean bAltdaten, boolean bPlanung, boolean bCheck) throws RemoteException {
        if (nArt != 1 && nArt != 2)
            throw new RemoteException("Art der Datei ist unbekannt: " + String.valueOf(nArt));
        try {
            final RequestData reqData = new RequestData(
                    fNameClient, fNameLokal, fFormat,
                    (nArt == 2),
                    fDatum, fThema, fTypes,
                    fbliste, fbula, fsrs, fhs,
                    okstraFBlisteHaupt, okstraFBlisteUnter, okstraFBlisteBuLa, okstraFBlisteLand);
            reqData.setNurPruefen(bCheck);
            if (bAltdaten && bPlanung) {
                String msg = "Die zu verwendende Datenbank ist mehrdeutig angegeben. Die Anfrage wird abgewiesen.";
                Db2Okstra.protokolliere(-1, msg, eMeldTyp.warnung, eProtokollTyp.meldungen);
                throw new RemoteException(msg);
            }
            else if (bAltdaten) {
                if (haveAltdaten()) {
                    reqData.addConnection(okstraAltConnection, vermdbAltConnection, dbVersionAltdaten, okstraVersionAltdaten);
                }
                else {
                    String msg = "Die verlangte Datenbankverbindung zu den Altdaten existiert nicht oder ist unvollständig. " +
                        "Die Anfrage wird abgewiesen";
                    Db2Okstra.protokolliere(-1, msg, eMeldTyp.warnung, eProtokollTyp.meldungen);
                    throw new RemoteException(msg);
                }
            }
            else if (bPlanung) {
                if (havePlanung()) {
                    reqData.addConnection(okstraPlanungConnection, vermdbPlanungConnection, dbVersionPlanung, okstraVersionPlanung);
                }
                else {
                    String msg = "Die verlangte Datenbankverbindung zu den Planungsdaten existiert nicht oder ist unvollständig. " +
                        "Die Anfrage wird abgewiesen";
                    Db2Okstra.protokolliere(-1, msg, eMeldTyp.warnung, eProtokollTyp.meldungen);
                    throw new RemoteException(msg);
                }
            }
            else {
                // Standard-Datenbankverbindung
                reqData.addConnection(okstraConnection, vermdbConnection, dbVersion, okstraVersion);
            }
            reqData.setSessionId(fSessionId);
            reqData.setProject(fProject);
            reqData.setUser(fUser);
            reqData.setTimeStamp(fTimeStamp);
            if (verboseLogging) {
                Db2Okstra.protokolliere(-1, "Analyzing File...", eMeldTyp.info, eProtokollTyp.meldungen);
            }
            if (analyzeFile(fNameLokal, reqData, false, false, false, null, null) != null) {
                // Im Erfolgsfalle wird der Auftrag wird in die Warteschlange
                // gestellt und ein Thread gestartet, der das Laden anstösst bzw.
                // ausführt. Die gerufenene Funktion kehrt ohne Aktion sofort
                // zurück, wenn bereits ein Ladevorgang in Bearbeitung ist.
                int job = manageQueue(-1, false, reqData, null, null);
                reqData.setAuftrag(job);
                if (verboseLogging) {
                    Db2Okstra.protokolliere(-1, "Submitting Loader Thread...", eMeldTyp.info, eProtokollTyp.meldungen);
                }
                startLoaderThread(reqData, 0);
                
                // Die Auftragsnummer abliefern
                if (verboseLogging) {
                    Db2Okstra.protokolliere(-1, "Thread submitted..." + Okstra2Db.now(), eMeldTyp.info, eProtokollTyp.meldungen);
                }
                return successMessage + String.valueOf(job);
            }
            else if (!reqData.getExceptionMsg().isEmpty()) {
                String msg = "Fehler bei Registrierung" + (reqData.getNurPruefen() ? " des Prüfauftrags" : "") + ", Meldung: " + reqData.getExceptionMsg();
                if (verboseLogging) {
                    Db2Okstra.protokolliere(-1, msg, eMeldTyp.warnung, eProtokollTyp.meldungen);
                }
                throw new RemoteException(msg);
            } else {
                if (verboseLogging) {
                    Db2Okstra.protokolliere(-1, "Unrecognized failure checking upload.", eMeldTyp.warnung, eProtokollTyp.meldungen);
                }
            }
        }
        catch (RuntimeException ex) {
            throw new RemoteException("Ausnahme bei Registrierung: " + ex.getMessage());
        }
        catch (Exception ex) {
            throw new RemoteException("Ausnahme bei Registrierung: " + ex.getMessage());
        }
        throw new RemoteException("OKSTRA-Datei konnte nicht übernommen werden.");
    }
    
    private void readRequest(int auftrag, RequestData reqData) throws Exception {
        if (auftrag < 0) {
            throw new Exception("Auftragsnummer ist ungültig.");
        }
        String stmt = String.format("SELECT original,lokal,version,status,fbversion,srs,stichtag,format,loeschung,sessionid,timestamp,projekt,username FROM %s where auftragsnummer=%d;", getQueue(reqData.getServiceVerbindung()), auftrag);
        ResultSet rs = reqData.getServiceVerbindung().PGDatenLesen(stmt, null);
        int status = 0;
        if (rs != null) {
            while (rs.next())
            {
                reqData.setOriginalPath(rs.getString(1));
                reqData.setFilePath(rs.getString(2));
                Integer[] iArray = (Integer[])(rs.getArray(3).getArray());
                if (iArray == null || iArray.length != 3) {
                    throw new Exception("OKSTRA-Version kann nicht bestimmt werden für Auftrag " + String.valueOf(auftrag));
                }
                Version vers = Version.Erzeuge(iArray[0], iArray[1], iArray[2]);
                if (vers.GibText().contains("?")) {
                    throw new Exception("OKSTRA-Version " + vers.GibText() + " ist ungültig für Auftrag " + String.valueOf(auftrag));
                }
                reqData.setVersion(vers);
                int state = rs.getInt(4);
                reqData.setStatus(state);
                if ((state >= 0) && ((state % 10) != 0))
                    reqData.setNurPruefen(true);
                String[] sArray = (String[])(rs.getArray(5).getArray());
                if (sArray == null || sArray.length != 3) {
                    throw new Exception("Fachbedeutungslistenversion kann nicht bestimmt werden für Auftrag " + String.valueOf(auftrag));
                }
                FblVersion fbv = FblVersion.Erzeuge(Integer.parseInt(sArray[0]),
                        Integer.parseInt(sArray[1]), sArray[2]);
                if (fbv.GibText().contains("?")) {
                    throw new Exception("Fachbedeutungslistenversion " + fbv.GibText() + " ist ungültig für Auftrag " + String.valueOf(auftrag));
                }
                reqData.setFblVersion(fbv);
                // Höhensystem und Koordinatenreferenzsystem sind in eine Spalte kodiert
                int nSrs = rs.getInt(6);
                if (nSrs > SRSFACTOR) {
                    reqData.setHs(eKoordRefSys.getEnum(nSrs/SRSFACTOR));
                    nSrs = nSrs % SRSFACTOR;
                }
                reqData.setSrs(eKoordRefSys.getEnum(nSrs));
                reqData.setDate(rs.getString(7));
                reqData.setFormat(rs.getString(8));
                reqData.setDeleteMode(rs.getBoolean(9));
                reqData.setSessionId(rs.getString(10));
                reqData.setTimeStamp(rs.getString(11));
                reqData.setProject(rs.getString(12));
                reqData.setUser(rs.getString(13));
                ++status;
            }
            rs.close();
        }
        if (status != 1) {
            throw new Exception("Multiple Einträge zu Auftragsnummer " + String.valueOf(auftrag));
        }
    }
    
    private int manageQueue(int auftrag, boolean readMe, RequestData reqData, String property, Object val) throws Exception {
        synchronized (this.marker) {
            if (reqData == null) {
                return -2;
            }
            int job = -1;
            boolean bInit = (property != null && "init".equals(property));
            String warteschlange = getQueue(reqData.getServiceVerbindung());
            if (readMe) {
                if (auftrag < 0) {
                    // Die Auftragsnummer des ersten wartenden Auftrages in der Warteschlange abliefern
                    String stmt = String.format("SELECT auftragsnummer, status FROM %s where beendet IS NULL AND NOT bearbeitung ORDER BY auftragsnummer ASC;", warteschlange);
                    ResultSet rs = reqData.getServiceVerbindung().PGDatenLesen(stmt, null);
                    int status = 0;
                    int state = -1;
                    if (rs != null) {
                        while (rs.next()) {
                            if (job < 0) {
                                job = rs.getInt(1);
                                state = rs.getInt(2);
                            }
                            ++status;
                        }
                        rs.close();
                    }
                    if (state >= 0 && ((state % 10) != 0))
                        reqData.setNurPruefen(true);
                    if (!bInit) {
                        return job;
                    }
                    else {
                        reqData.setAuftrag(job);
                    }
                }
                else {
                    job = auftrag;
                    readRequest(auftrag, reqData);
                }
            }
            else {
                job = reqData.getAuftrag();
            }
            if (!bInit && job < 0) {
                // Auftrag in Warteschlange einstellen
                Version vers = reqData.getVersion();
                FblVersion fbv = reqData.getFbListeAusDatei() ? null : reqData.getFblVersion();
                int fhv = 1;
                int fuv = 6;
                String fbl = "";
                if (fbv == null || !fbv.IstGueltig()) {
                    if (reqData != null && reqData.getMetadata() != null &&
                        reqData.getMetadata().containsKey(eMetadaten.schema_identifiers)) {
                        String fbstring = reqData.getMetadata().get(eMetadaten.schema_identifiers);
                        if (fbstring != null && !fbstring.isEmpty()) {
                            String[] parts = fbstring.split(" ");
                            if (parts.length == 3) {
                                String[] numbers = parts[2].split("\\.");
                                if (numbers.length == 2) {
                                    try {
                                        int hv = Integer.parseInt(numbers[0]);
                                        int uv = Integer.parseInt(numbers[1]);
                                        FblVersion tfbv = FblVersion.Erzeuge(hv, uv, parts[1]);
                                        if (tfbv.IstGueltig()) {
                                            fhv = hv;
                                            fuv = uv;
                                            fbl = parts[1];
                                            logger.info("Benutze Fachbedeutungsliste aus der Eingabedatei: " + tfbv.GibText());
                                        }
                                        reqData.setFblVersion(tfbv);
                                    } catch (Exception ex) {
                                        // Fehler, keine Angabe vornehmen
                                        logger.info("Benutze Vorgabe für Fachbedeutungsliste: Version 1.6");
                                    }
                                }
                            }
                        }
                    }
                } else {
                    fhv = fbv.GibHauptversion();
                    fuv = fbv.GibUnterversion();
                    fbl = fbv.GibBundesland();
                }
                
                // Höhensystem und Koordinatenreferenzsystem sind in eine Spalte kodiert
                int srsVal = reqData.getSrs().GibCode();
                if (!reqData.getHs().equals(eKoordRefSys.Undefiniert)) {
                    srsVal += SRSFACTOR * reqData.getHs().GibCode();
                }
                String stmt = String.format("INSERT INTO %s (original,lokal,erfasst,version,fbversion,srs,stichtag,format,loeschung,sessionid,timestamp,projekt,username,status)%n" +
                    "    VALUES(%s,%n%s,%n%s,ARRAY[%d,%d,%d],ARRAY['%d','%d',%s],%d,%s,%s,%s,%s,%s,%s,%s, %d);",
                    warteschlange, SqlWert.text(reqData.getOriginalPath()), SqlWert.text(reqData.getFilePath()),
                    SqlWert.text(Okstra2Db.now()),
                    vers.GibHauptversion(), vers.GibUnterversion(), vers.GibEntwicklungsversion(),
                    fhv, fuv, SqlWert.text(fbl), srsVal, SqlWert.text(reqData.getDate()), SqlWert.text(reqData.getFormat()),
                    (reqData.getDeleteMode() ? "true" : "false"), SqlWert.text(reqData.getSessionId()),
                    SqlWert.text(reqData.getTimeStamp()), SqlWert.text(reqData.getProject()), SqlWert.text(reqData.getUser()),
                    (reqData.getNurPruefen() ? 1 : 0));
                int status = reqData.getServiceVerbindung().PGDatenAendern(stmt);
                if (status < 0) {
                    throw new Exception("Fehler bei Übernahme eines Auftrages in die Warteschlange. Meldung: " +
                            reqData.getServiceVerbindung().getLetztePGFehlermeldung());
                }
                stmt = String.format("SELECT max(auftragsnummer) FROM %s;", warteschlange);
                ResultSet rs = reqData.getServiceVerbindung().PGDatenLesen(stmt, null);
                status = 0;
                if (rs != null) {
                    while (rs.next())
                    {
                        job = rs.getInt(1);
                        ++status;
                    }
                    rs.close();
                }
                if (status != 1) {
                    throw new Exception("Auftragsnummer konnte nicht bestimmt werden.");
                }
            }
            if (property != null) {
                String stmt = null;
                if ("pruefprotokoll".equals(property) ||
                    "migrationprotokoll".equals(property) ||
                    "ladeprotokoll".equals(property) ||
                    "datenfehler".equals(property)) {
                    if (val != null) {
                        String prot = (String)val;
                        stmt = String.format("UPDATE %s SET %s=%s WHERE auftragsnummer=%d;", warteschlange, property, SqlWert.text(prot), job);
                    }
                }
                else if ("status".equals(property)) {
                    if (val != null) {
                        Integer wert = (Integer)val;
                        stmt = String.format("UPDATE %s SET %s=%d WHERE auftragsnummer=%d;", warteschlange, property, wert, job);
                    }
                }
                else if ("bearbeitung".equals(property)) {
                    if (val != null) {
                        Boolean wert = (Boolean)val;
                        stmt = String.format("UPDATE %s SET %s=%s WHERE auftragsnummer=%d;", warteschlange, property,
                                (wert ? "true" : "false"), job);
                    }
                }
                else if ("erfasst".equals(property) ||
                         "beendet".equals(property)) {
                    if (val != null) {
                        String wert = (String)val;
                        stmt = String.format("UPDATE %s SET %s=%s WHERE auftragsnummer=%d;", warteschlange, property, SqlWert.text(wert), job);
                    }
                }
                else if ("init".equals(property)) {
                    stmt = String.format("UPDATE %s SET bearbeitung=false WHERE bearbeitung=true;", warteschlange);
                }
                if (stmt != null) {
                    int status = reqData.getServiceVerbindung().PGDatenAendern(stmt);
                    if (status < 0) {
                        throw new Exception("Fehler bei Änderung des Wertes für " + property +
                                " eines Auftrages in die Warteschlange. + Meldung:" +
                                reqData.getServiceVerbindung().getLetztePGFehlermeldung());
                    }
                }
            }
            return job;
        }
    }
    
    /**
     * Startet Ladevorgang falls noch kein Loader-Thread unterwegs ist.
     * Diese Methode läuft in einem eigenen Thread und lässt stets nur
     * einen einzelnen Ladevorgang zur selben Zeit zu.
     * @param reqData
     * @throws InterruptedException 
     */
    private void doUnload(PostRequestContent reqData, int waitSeconds) throws InterruptedException {
        if (waitSeconds > 0) {
            Thread.sleep(waitSeconds * 1000);
        }
        TreeMap<String,String> parms = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        String msg = reqData.checkParameters(parms);
        if (msg != null && !msg.isEmpty()) {
            throw new InterruptedException(msg);
        }
        // Session-ID ist ab hier definitiv vorhanden
        // Die übrigen Parameter sind geprüft und normiert.
        String sessionId = parms.get("SID");
        
        // Nun gehts los...
        try {
            // Protokoll anhängen, wird nach dem Return aus dieser Methode
            // vom Caller wieder beseitigt
            logger.debug("Beginne Datenabzug...");
            String logDatei = getLogFileName(sessionId, "OkstraUnload.log");
            JavaProtokollant.erzeugeProtokoll(logger, logDatei);
            erlaubeZugriff(logDatei, false);

            // Und los geht's mit dem Auslesen
            readRequest(null, reqData, parms, null);
        } catch (Exception ex) {
            try {
                writeStateFile(sessionId, 2, ex.getMessage());
            } catch( Exception e) {
                logger.warn("Fehler beim Schreiben der Statusdatei: " + e.getMessage());
            }
            throw new InterruptedException("Fehler beim Entladen von Daten: " + ex.getMessage());
        }
    }
    
    /**
     * Setzt den Loader-Thread-Zustand um
     * @param state neuer Status
     * @return vorheriger Status
     */
    private synchronized boolean lockLoader(boolean state) {
        boolean bOld = loaderActive;
        loaderActive = state;
        return bOld;
    }
    
    /**
     * Startet Ladevorgang falls noch kein Loader-Thread unterwegs ist.
     * Diese Methode läuft in einem eigenen Thread und lässt stets nur
     * einen einzelnen Ladevorgang zur selben Zeit zu.
     * @param reqData
     * @throws InterruptedException 
     */
    private void doLoad(RequestData reqData, int waitSeconds) throws InterruptedException {
        // Paralleles Laden verhindern, nur weitermachen
        // wenn kein anderer Lade-Thread unterwegs ist.
        boolean wasActive = lockLoader(true);
        if (wasActive) {
            return;
        }
        if (waitSeconds > 0) {
            Thread.sleep(waitSeconds * 1000);
        }

        boolean logg = false;
        boolean bRequestChained = false;
        boolean bLoaderActive = false;
        int jump = 1000000;
        try {
            if (verboseLogging) {
                Db2Okstra.protokolliere(-1, "Loader Thread starting..." + Okstra2Db.now(), eMeldTyp.info, eProtokollTyp.meldungen);
            }
            // Auftrag sperren
            manageQueue(reqData.getAuftrag(), false, reqData, "bearbeitung", true);

            // Markiere laufenden Ladevorgang
            writeStateFile(reqData.getSessionId(), 0, "");
            
            // Datei laden in Datenbestand und prüfen, Migration durchführen
            HashSet<String> fehlerIds = null;
            if (writeErrorObjects) {
                fehlerIds = new HashSet<>();
            }
            if (verboseLogging) {
                Db2Okstra.protokolliere(-1, "Loading file " + reqData.getFilePath(), eMeldTyp.info, eProtokollTyp.meldungen);
            }
            analyzeFile(reqData.getFilePath(), reqData, true, true, true, fehlerIds, null);
            
            if (reqData.getKennungenAbgelehnt()) {
                // Eingabeprüfung: Lieferung mit unzulässigen Kennungen wird abgelehnt,
                // auch bei einem reinen Prüfauftrag; Meldungen im Prüfprotokoll
                jump = 0;
                manageQueue(reqData.getAuftrag(), false, reqData, "status", -5);
                manageQueue(reqData.getAuftrag(), false, reqData, "beendet", Okstra2Db.now());
                writeStateFile(reqData.getSessionId(), 2,
                        "Lieferung abgelehnt wegen unzulässiger Kennungen, siehe Prüfprotokoll");
                logger.debug("Lieferung wegen unzulässiger Kennungen abgelehnt.");
            }
            // Koordinatensystem und Höhensystem prüfen, nicht übertragen wenn nicht plausibel
            else if (!reqData.getSrsError()) {
                // Datenbestand in die angeschlossene Datenbank hineinpfeifen
                int finalState = 0;
                String rmsg = null;
                ArrayList<Object> resultat = null;
                if (!reqData.getNurPruefen()) {
                    // Wenn kein reiner Prüfauftrag
                    logger.debug("Beginne Datenübertragung...");
                    String logDatei = getLogFileName(reqData.getFilePath(), reqData, "_transfer", ".log");
                    JavaProtokollant.erzeugeProtokoll(logger, logDatei);
                    erlaubeZugriff(logDatei, false);
                    logg = true;

                    FblVersion fbvsn = reqData.getFbListeAusDatei()
                            ? (reqData.getDatenbestand() != null
                               ? reqData.getDatenbestand().GibFblVersion()
                               : FblVersion.Erzeuge(1, 6))
                            : reqData.getFblVersion();
                    Okstra2Db dbLader = new Okstra2Db(reqData.getOkstraVerbindung().getDbMetadaten(), reqData.getProject(),
                            reqData.getAuftrag(), reqData.getDate(),
                            fbvsn, reqData.getSrs(), reqData.getHs(), reqData.getTheme());
                    dbLader.setOkstraVersionHeader(okstraVersionHeader);
                    dbLader.setHeaderInMetadata(headerInMetadata);
                    try {
                        // Ein Fehler kann dazu geführt haben, dass kein Datenbestand vorhanden ist
                        if (reqData.getDatenbestand() != null) {
                            // Eingabeprüfung unmittelbar vor der Übertragung: der Wert, aus dem
                            // OkstraSqlUtil die Objekt-ID neuer Einträge bildet
                            List<String> unzulaessig = pruefeKennungen(reqData.getDatenbestand(), true);
                            if (!unzulaessig.isEmpty()) {
                                for (String m : unzulaessig) {
                                    Db2Okstra.protokolliere(-1, m, eMeldTyp.fehler, eProtokollTyp.meldungen);
                                }
                                throw new Exception("Übertragung abgebrochen wegen unzulässiger Kennungen");
                            }
                            korrigiereBogenhoehen(reqData.getDatenbestand());
                            bLoaderActive = true;
                            resultat = dbLader.FuehreAus(reqData.getDatenbestand(), null, null, null);
                        } else {
                            logger.warn("   ... keine Daten zur Übertragung vorhanden");
                        }
                    }
                    catch (Throwable rex) {
                        rmsg = rex.getMessage();
                        jump = 10000;
                    }
                    manageQueue(reqData.getAuftrag(), false, reqData, "ladeprotokoll", logDatei);
                    if (!dbLader.getExceptionList().isEmpty()) {
                        finalState = -8;
                    } else {
                        finalState = reqData.getStatus() + jump;
                        if (!reqData.getPruefStatus())
                            finalState += 1;
                    }
                    copyLogFile(reqData.getSessionId(), logDatei);
                } else {
                    finalState = reqData.getStatus() + jump + 
                            (reqData.getPruefStatus() ? 5 : 6); // Zur Unterscheidung von Ladeaufträgen
                }
                reqData.setStatus(finalState);
                jump = 0;
                manageQueue(reqData.getAuftrag(), false, reqData, "status", finalState);
                manageQueue(reqData.getAuftrag(), false, reqData, "beendet", Okstra2Db.now());
                boolean bSuccess;
                String diag = "";
                if (reqData.getNurPruefen()) {
                    bSuccess = reqData.getPruefStatus();
                    if (!bSuccess)
                        diag = "Fachliche Prüfung mit Fehlern";
                } else {
                    bSuccess = ((resultat == null) && (rmsg == null));
                }
                writeStateFile(reqData.getSessionId(), bSuccess ? 1 : 2, diag);
                if (bSuccess) {
                    logger.debug(reqData.getNurPruefen() ? "Datenprüfung beendet." : "Datenübertragung beendet.");
                } else {
                    if (rmsg != null) {
                        logger.debug("Übertragungsfehler: " + rmsg);
                        throw new Exception(rmsg);
                    }
                    if (!reqData.getNurPruefen()) {
                        logger.debug("Übertragungsfehler, bitte Protokoll untersuchen");
                        throw new Exception("Übertragungsfehler");
                    }
                }

                if (!reqData.getNurPruefen() && writeErrorObjects) {
                    schreibeFehler(reqData, fehlerIds);
                }
            }
            else {
                manageQueue(reqData.getAuftrag(), false, reqData, "status", -5);
                manageQueue(reqData.getAuftrag(), false, reqData, "beendet", Okstra2Db.now());
            }
            
            // Nachschauen ob in der Warteschlange noch was steht
            bRequestChained = startNextThread(bRequestChained);
            
            // Noch etwas aufräumen
            Datenbestand bestand = reqData.getDatenbestand();
            reqData.setDatenbestand(null);
            reqData.setFblVersion(null);
            reqData.setVersion(null);
            if (bestand != null) {
                bestand.Vernichte();
            }
        }
        catch (Exception ex) {
            throw new InterruptedException("Abbruch des Ladens: " + ex.getMessage());
        }
        finally {
            // Fertig mit dem Laden, Thread kann sich beenden
            try {
                if (jump > 0) {
                    if (!reqData.getSrsError()) {
                        reqData.setStatus(reqData.getStatus() + jump);
                        manageQueue(reqData.getAuftrag(), false, reqData, "status", reqData.getStatus());
                    } else {
                        manageQueue(reqData.getAuftrag(), false, reqData, "status", (bLoaderActive ? -8 : -1));
                    }
                    manageQueue(reqData.getAuftrag(), false, reqData, "beendet", Okstra2Db.now());
                }
                manageQueue(reqData.getAuftrag(), false, reqData, "bearbeitung", false);
                if (logg) {
                    JavaProtokollant.entferneProtokoll();
                }
            }
            catch (Exception e) {
            }
            if (!bRequestChained) {
                // Lock nur aufheben wenn kein Nachfolgeauftrag unterwegs...
                // Wenn man das aber gar nicht macht, kommt auch kein anderer
                // Lade-Thread mehr in die Puschen :-)
                lockLoader(false);
            }
            // Erzwinge Garbage Collection
            System.gc();
        }
    }
    
    private boolean startNextThread(boolean bRequestChained) throws Exception {
        // Warteschlange untersuchen
        RequestData nextRequest = new RequestData();
        nextRequest.addConnection(okstraConnection, vermdbConnection, dbVersion, okstraVersion);
        int job = manageQueue(-1, true, nextRequest, null, null);
        if (job > 0) {
            nextRequest.setAuftrag(job);
            readRequest(job, nextRequest);
            lockLoader(false);
            bRequestChained = true;
            // Neuen Thread mit etwas Verzögerung loslaufen lassen
            // damit der laufende Prozess sich noch aufräumen kann
            if (verboseLogging) {
                Db2Okstra.protokolliere(-1, "Chaining Loader Thread..." + Okstra2Db.now(), eMeldTyp.info, eProtokollTyp.meldungen);
            }
            startLoaderThread(nextRequest, 3);
        }
        else if (haveAltdaten())
        {
            // bei den Altdaten nachschauen
            nextRequest.addConnection(okstraAltConnection, vermdbAltConnection, dbVersionAltdaten, okstraVersionAltdaten);
            job = manageQueue(-1, true, nextRequest, null, null);
            if (job > 0) {
                nextRequest.setAuftrag(job);
                readRequest(job, nextRequest);
                lockLoader(false);
                bRequestChained = true;
                // Neuen Thread mit etwas Verzögerung loslaufen lassen
                // damit der laufende Prozess sich noch aufräumen kann
                if (verboseLogging) {
                    Db2Okstra.protokolliere(-1, "Chaining Loader Thread..." + Okstra2Db.now(), eMeldTyp.info, eProtokollTyp.meldungen);
                }
                startLoaderThread(nextRequest, 3);
            }
        }
        else if (havePlanung())
        {
            // bei den Planungsdaten nachschauen
            nextRequest.addConnection(okstraPlanungConnection, vermdbPlanungConnection, dbVersionPlanung, okstraVersionPlanung);
            job = manageQueue(-1, true, nextRequest, null, null);
            if (job > 0) {
                nextRequest.setAuftrag(job);
                readRequest(job, nextRequest);
                lockLoader(false);
                bRequestChained = true;
                // Neuen Thread mit etwas Verzögerung loslaufen lassen
                // damit der laufende Prozess sich noch aufräumen kann
                if (verboseLogging) {
                    Db2Okstra.protokolliere(-1, "Chaining Loader Thread..." + Okstra2Db.now(), eMeldTyp.info, eProtokollTyp.meldungen);
                }
                startLoaderThread(nextRequest, 3);
            }
        }
        return bRequestChained;
    }
    
    private void schreibeFehler(RequestData reqData, Set<String> fehlerIds) throws Ausnahme {
        if (reqData == null || fehlerIds == null || fehlerIds.size() == 0) {
            return;
        }
        // Die als fehlerhaft erkannten Fachobjekte in einen eigenen Datenbestand
        // verschieben und diesen als Nachweis für den Datenlieferanten exportieren
        Datenbestand daten = reqData.getDatenbestand();
        DateiAusgabe ausgabe = null;
        AusgabeKonverter ocv = null;
        Datenbestand moved = null;
        Fachbedeutungsliste fbliste = null;
        boolean bEmbed = false;
        try {
            boolean bCTE = ("CTE".equalsIgnoreCase(reqData.getFormat()));
            if (bCTE) {
                // Hierfür muss eine Auflösung aller Relationen der betroffenen
                // Objekte erfolgen (->Geometriemodell, Schlüsseltabellen, etc!)
                // Im Zweifelsfall erhält man den Eingabedatenbestand als
                // Fehlerausgabe. Dehaer wird stets im XML-Format ausgegeben.
                bCTE = false;
            }
            if (!bCTE) {
                bEmbed = (boolean)Umgebung.GibEinstellung(eEinstellung.pSchluesseltabelleneinbettung);
                if (bEmbed) {
                    // Damit in XML auch die Schlüsseltabellen ausggeben werden...
                    Umgebung.SetzeEinstellung(eEinstellung.pSchluesseltabelleneinbettung, false);
                }
            }
            moved = Datenbestand.Erzeuge(daten.GibVersion());

            Iterator<String> iter = fehlerIds.iterator();
            while (iter.hasNext()) {
                String id = iter.next();
                Fachobjekt fObj = daten.Suche(id);
                if (fObj != null) {
                    moved.Verschiebe(fObj);
                }
            }
            if (moved.GibAnzahl() > 0) {
                String file = reqData.getOriginalPath();
                if (!bCTE && file.toLowerCase(Locale.GERMANY).endsWith(".cte")) {
                    file = file.substring(0, file.lastIndexOf('.')) + ".xml";
                }
                String datei = getErrorFileName(reqData, file);
                ausgabe = DateiAusgabe.Erzeuge(datei);
                if (bCTE) {
                    ocv = CTEAusgabeKonverter.Erzeuge(ausgabe);
                }
                else {
                    ocv = XMLAusgabeKonverter.Erzeuge(ausgabe);
                }
                ocv.SetzeMetadaten(eMetadaten.originating_system, "Vermessungsdatenbank");
                ocv.SetzeMetadaten(eMetadaten.author, okstraBehoerde);
                ocv.SetzeMetadaten(eMetadaten.description, "Upload: fachlich zu prüfende Objekte");
                fbliste = Fachbedeutungsliste.Erzeuge(reqData.getFblVersion());
                moved.SetzeFbl(fbliste);
                ocv.Entlade(moved);
                manageQueue(reqData.getAuftrag(), false, reqData, "datenfehler", datei);
            }
        }
        catch (Ausnahme aus) {
            try {
                Db2Okstra.protokolliere(-1, "Fehler bei Ausgabe der fehlerhaften Fachobjekte: " +
                        aus.GibMeldungstext(), eMeldTyp.warnung, eProtokollTyp.meldungen);
            }
            catch (Ausnahme ex) {
                logger.error("Fehler bei Ausgabe der fehlerhaften Fachobjekte", ex);
            }
        }
        catch (Exception ex) {
            try {
                Db2Okstra.protokolliere(-1, "Fehler bei Ausgabe der fehlerhaften Fachobjekte: " +
                        ex.getMessage(), eMeldTyp.warnung, eProtokollTyp.meldungen);
            }
            catch (Ausnahme aus) {
                logger.error("Fehler bei Ausgabe der fehlerhaften Fachobjekte", ex);
            }
        }
        finally {
            try {
                // Ressourcen aufräumen
                if (fbliste != null && moved != null) {
                    moved.SetzeFbl(null);
                    fbliste.Vernichte();
                }
                if (ocv != null) {
                    ocv.Vernichte();
                }
                if (ausgabe != null) {
                    ausgabe.Vernichte();
                }
                if (moved != null) {
                    moved.Vernichte();
                }
                if (bEmbed) {
                    Umgebung.SetzeEinstellung(eEinstellung.pSchluesseltabelleneinbettung, true);
                }
            }
            catch (Ausnahme ex) {
                logger.error("Fehler bei Beseitung von Ressourcen", ex);
            }
        }
    }
    
    private String historisiere(PostRequestContent reqData) throws Exception {
        // Für Rückmeldung
        String diag = "";
        
        String stichtag = reqData.getStichtag();

        String op = reqData.getItem("op");
        if (op != null) {
            op = op.trim();
        }
        boolean opOK = false;
        boolean qiOK = false;
        boolean ziOK = false;
        List<HistObjekt> quelle = null;
        List<HistObjekt> ziel = null;
        if (op == null || op.isEmpty()) {
            diag += "Aktion ist nicht belegt. ";
        } else if (!"1".equals(op) && !"2".equals(op) &&
                   !"3".equals(op) && !"4".equals(op)) {
            diag += "Aktion " + op + " ist unbekannt. ";
        } else {
            opOK = true;
        }
        String qi = reqData.getItem("qi");
        if (qi == null || qi.trim().isEmpty()) {
            diag += "Quelle ist nicht belegt. ";
        } else {
            qiOK = true;
        }
        String zi = reqData.getItem("zi");
        if (opOK) {
            if ("4".equals(op)) {
                if (zi != null && !zi.trim().isEmpty()) {
                    diag += "Bei Beendigung sollte das Ziel nicht belegt sein. ";
                    ziOK = true;
                } else {
                    ziOK = true;
                }
            } else if (zi == null || zi.trim().isEmpty()) {
                diag += "Ziel ist nicht belegt. ";
            } else {
                ziel = HistObjekt.gibObjekte(reqData.getOkstraVerbindung(), zi);
                if (ziel.isEmpty()) {
                    diag += "Ziel in Datenbank nicht gefunden. ";
                } else {
                    ziOK = true;
                }
            }
        }
        if (qiOK) {
            quelle = HistObjekt.gibObjekte(reqData.getOkstraVerbindung(), qi);
            if (quelle.isEmpty()) {
                diag += "Quelle in Datenbank nicht gefunden. ";
                qiOK = false;
            }
        }
        if (!opOK || !qiOK || ! ziOK) {
            return "failure" + diag;
        }
        
        // Beteiligte Objektanzahlen prüfen je nach Operation
        if ("1".equals(op)) {
            // Identitätswechsel
            if (quelle.size() != 1 ||
                ziel.size() != 1) {
                diag += "Ungültige Objektanzahl bei Identitätswechsel. ";
                return "failure" + diag;
            }
        } else if ("2".equals(op)) {
            // Teilung
            if (quelle.size() != 1 ||
                ziel.size() < 2) {
                diag += "Ungültige Objektanzahl bei Teilung. ";
                return "failure" + diag;
            }
        } else if ("3".equals(op)) {
            // Verschmelzung
            if (quelle.size() < 2 ||
                ziel.size() != 1) {
                diag += "Ungültige Objektanzahl bei Verschmelzung. ";
                return "failure" + diag;
            }
        } else if ("4".equals(op)) {
            // Beendigung
        } else {
            // unbekannte Operation
            diag += "Aktion " + op + " wird nicht unterstützt. ";
            return "failure" + diag;
        }
        
        // Quellobjekte prüfen
        // - Stichtag muss >= gueltigvon sein
        // - Stichtag muss <= gueltigbis sein
        boolean bFail = false;
        for (HistObjekt ho : quelle) {
            if (ho.getVon().toString().compareTo(stichtag) > 0) {
                diag += "Quellobjekt " + ho.getOid() + " beginnt nach Stichtag. ";
                bFail = true;
            }
            if (ho.getBisText().compareTo(stichtag) < 0) {
                diag += "Quellobjekt " + ho.getOid() + " endet vor Stichtag. ";
                bFail = true;
            }
            String[] nachf = ho.getNach();
            if (nachf != null && !"4".equals(op)) {
                // Auf Identität prüfen, beim Beenden ist das nicht nötig
                boolean ident = ((ziel != null) && (nachf.length == ziel.size()));
                if (ident) {
                    for (HistObjekt to : ziel) {
                        if (!ho.findNach(to.getOid())) {
                            ident = false;
                            break;
                        }
                    }
                }
                if (!ident) {
                    diag += "Quellobjekt " + ho.getOid() + " besitzt Nachfolger. ";
                    bFail = true;
                }
            }
        }
        
        // Zielobjekte prüfen wenn keine Beendigung
        // - Stichtag muss >= gueltigvon sein
        // - Stichtag muss <= gueltigbis sein
        if (!"4".equals(op)) {
            for (HistObjekt ho : ziel) {
                if (ho.getVon().toString().compareTo(stichtag) > 0) {
                    diag += "Zielobjekt " + ho.getOid() + " beginnt nach Stichtag. ";
                    bFail = true;
                }
                if (ho.getBisText().compareTo(stichtag) < 0) {
                    diag += "Zielobjekt " + ho.getOid() + " endet vor Stichtag. ";
                    bFail = true;
                }
                String[] vorg = ho.getVor();
                if (vorg != null) {
                    // Auf Identität prüfen
                    boolean ident = ((quelle != null) && (vorg.length == quelle.size()));
                    if (ident) {
                        for (HistObjekt qo : quelle) {
                            if (!ho.findVor(qo.getOid())) {
                                ident = false;
                                break;
                            }
                        }
                    }
                    if (!ident) {
                        diag += "Zielobjekt " + ho.getOid() + " besitzt Vorgänger. ";
                        bFail = true;
                    }
                }
            }
        }
        // Raus wenn Fehler gefunden
        if (bFail) {
            return "failure" + diag;
        }
        
        // Operation ausführen
        String stmt = "";
        DbVerbindung verb = DbVerbindung.ErzeugePGVerbindung(reqData.getOkstraVerbindung());
        DbMetadaten meta = verb.getDbMetadaten();
        String header = DbMetadaten.GibTabelle(meta, DbVerbindung.getKopfdaten());
        String fromto = DbMetadaten.GibTabelle(meta, DbVerbindung.getVonNach());
        try {
            for (HistObjekt ho : quelle) {
                // Enddatum für die Vorgänger setzen
                stmt += String.format("update %s set gueltigbis='%s' where foid=%s;%n",
                        header, stichtag, ho.getOid());
                if (!"4".equals(op)) {
                    // Vorgänger-Nachfolger Relation pflegen
                    for (HistObjekt t : ziel) {
                        if (!ho.findNach(t.getOid())) {
                            stmt += String.format("insert into %s (von, nach) values (%s, %s);%n",
                                    fromto, ho.getOid(), t.getOid());
                        }
                    }
                }
            }
            // Startdatum für die Nachfolger setzen
            if (!"4".equals(op)) {
                for (HistObjekt ho : ziel) {
                    stmt += String.format("update %s set gueltigvon='%s' where foid=%s;%n",
                            header, stichtag, ho.getOid());
                }
            }
            int status = verb.PGDatenAendern(stmt);
            if (status < 0) {
                diag += "Fehler bei Datenbankoperation: " + verb.getLetztePGFehlermeldung() + " ";
            }
            stmt = "";
        } catch (Exception ex) {
            diag += "Fehler bei Datenbankoperation: " + ex.getMessage() + " ";
            bFail = true;
        } finally {
            if (verb != null) {
                try {
                    verb.SchliessePG();
                    verb = null;
                }
                catch (Exception ex) {
                    verb = null;
                    throw new Exception("Fehler beim Schliessen einer Datenbankverbindung: " + ex.getMessage());
                }
            }
        }
        return generateResultString("accept", diag, -1);
    }
    
    private String generateResultString(String state, String diagnostic, int jobnr) {
        if (serviceReturnType.equals("plain")) {
            if (diagnostic == null || diagnostic.trim().isEmpty()) {
                return state;
            }
            return state + "\n" + diagnostic;
        } else if (serviceReturnType.equals("json")) {
            // JSON Format
            String res = "{ \"success\": ";
            res += (state.equals("accept")) ? "true" : "false";
            if (jobnr > 0) {
                res += ", \"auftragsnummer\": " + String.valueOf(jobnr);
            }
            if (diagnostic != null && !diagnostic.trim().isEmpty()) {
                res += ", \"message\": \"" +
                        diagnostic.replace("\n", " ").replace("\"", "\"\"") + "\"";
            }
            return res + " }";
        }
        return state;
    }
    
    /**
     * 
     * @param request
     * @param response
     * @throws ServletException
     */
    @Override
    protected void doPost(HttpServletRequest request,
            HttpServletResponse response) throws ServletException {
        try {
            // Request auslesen; Datei(en) speichern, Modus auslesen, beides in einem Objekt zurückgeben
            final PostRequestContent reqData = fileUpload(request);
            if (!reqData.getExceptionMsg().isEmpty()) {
                String msg = "Anfrage-Informationen sind unvollständig oder fehlerhaft: " + reqData.getExceptionMsg();
                if (verboseLogging) {
                    Db2Okstra.protokolliere(-1, msg, eMeldTyp.warnung, eProtokollTyp.meldungen);
                }
                response.getWriter().println(generateResultString("failure", escapeHtml(msg), -1));
                return;
            }

            // Fortsetzung je nach Typ der Anfrage
            boolean bCheckOnly = reqData.getItem("nurPruefen") != null;
            String typ = reqData.getItem("pruefeDatei");
            String status = "";
            if (!bCheckOnly && (typ == null || typ.isEmpty())) {
                typ = reqData.getItem("holeDatei");
                if (typ == null || typ.isEmpty()) {
                    typ = historisiere(reqData);
                    if (typ == null || typ.isEmpty()) {
                        typ = "";
                    } else {
                        response.getWriter().println(typ);
                        return;
                    }
                } else {
                    typ = "Download";
                    try {
                        final int wait = 0;
                        startUnloadThread(reqData, wait);
                        status = "Download-Auftrag erhalten.";
                        if (!reqData.getExceptionMsg().isEmpty()) {
                            status += " Meldung: " + reqData.getExceptionMsg();
                        }
                    } catch (Exception ex) {
                        String msg = "Entladevorgang wurde abgebrochen. Meldung: " + ex.getMessage();
                        if (verboseLogging) {
                            Db2Okstra.protokolliere(-1, msg, eMeldTyp.warnung, eProtokollTyp.meldungen);
                        }
                        response.getWriter().println(generateResultString("failure", escapeHtml(msg), -1));
                        return;
                    }
                }
            } else {
                typ = "Upload";
                reqData.setNurPruefen(bCheckOnly);
                // Nun den Upload registrieren und fertig
                String clientFileName = reqData.getItem("f2");
                String localFileName = reqData.getUploadExists() ? reqData.getItem("uploadfile") : reqData.getFilePath();
                String user = reqData.getItem("username");
                if (user == null || user.trim().isEmpty())
                    user = System.getProperty("user.name"); // "<fehlt>";
                if (verboseLogging) {
                    Db2Okstra.protokolliere(-1, "Registriere UpLoad:");
                    Db2Okstra.protokolliere(-1, " Datei nur prüfen:  " + (bCheckOnly ? "Ja" : "Nein"));
                    Db2Okstra.protokolliere(-1, " Session ID:        " + reqData.getItem("SID"));
                    Db2Okstra.protokolliere(-1, " Project Name:      " + reqData.getItem("PRJNM"));
                    Db2Okstra.protokolliere(-1, " User Name:         " + user);
                    Db2Okstra.protokolliere(-1, " Datenbank:         " + reqData.getDatenbankTyp());
                    Db2Okstra.protokolliere(-1, " Time Stamp:        " + reqData.getItem("TS"));
                    Db2Okstra.protokolliere(-1, " Client File Name:  " + clientFileName); //reqData.getItem("f2"));
                    Db2Okstra.protokolliere(-1, " Local File Name:   " + localFileName);
                    Db2Okstra.protokolliere(-1, " Data Format:       " + reqData.getItem("f1"));
                    Db2Okstra.protokolliere(-1, " Load/Delete Flag:  " + reqData.getItem("f20"));
                    Db2Okstra.protokolliere(-1, " Date:              " + reqData.getItem("f4"));
                    Db2Okstra.protokolliere(-1, " Thema Number:      " + reqData.getItem("f21"));
                    Db2Okstra.protokolliere(-1, " FB List Number:    " + reqData.getItem("f7"));
                    Db2Okstra.protokolliere(-1, " Bundesland Number: " + reqData.getItem("f8"));
                    Db2Okstra.protokolliere(-1, " SRS (EPSG) Number: " + reqData.getItem("f22"));
                    Db2Okstra.protokolliere(-1, " Height System:     " + reqData.getItem("f23"));
                }
                status = registerUpload(
                        reqData.getItem("SID"),   // SessionID
                        reqData.getItem("PRJNM"), // Projektname
                        user,                     // Benutzername
                        reqData.getItem("TS"),    // TimeStamp
                        clientFileName, //reqData.getItem("f2"),    // Dateiname (Client)
                        localFileName,  //reqData.getFilePath(),    // Dateiname (lokal)
                        reqData.getItem("f1"),    // Format
                        ("laden".equals(reqData.getItem("f20")) ? 1 : 2),   // Laden oder Löschen
                        reqData.getItem("f4"),    // Datum
                        reqData.getItem("f21"),   // Thema
                        "1,2",                    // Typen
                        reqData.getItem("f7"),    // Fachbedeutungsliste
                        reqData.getItem("f8"),    // Bundesland dazu
                        reqData.getItem("f22"),   // Koordinatenreferenzsystem
                        reqData.getItem("f23"),  // Höhensystem
                        reqData.getIstAltdaten(),// Altdatenflagge
                        reqData.getIstPlanung(), // Planungsflagge
                        reqData.getNurPruefen()  // Nur-Prüfen-Flagge
                                );
            }
            if (!bCheckOnly && typ.isEmpty()) {
                response.getWriter().println(generateResultString("failure", "Anfrage-Informationen sind unvollst&auml;ndig oder fehlerhaft. Anfragetyp konnte nicht ermittelt werden.", -1));
                return;
            }
            int job = -1;
            if (typ.equals("Upload")) {
                if (status.startsWith(successMessage)) {
                    try {
                        job = Integer.parseInt(status.substring(successMessage.length()));
                        status = "";
                    } catch (Exception ex) {
                        job = -1;
                        status = "Auftragsnummer konnte nicht gelesen werden: " + ex.getMessage();
                    }
                }
            }
            response.getWriter().println(generateResultString("accept", status, job));
        } catch (Exception ex) {
            throw new ServletException(ex);
        }
    }
    
    private boolean decodeGzipParameter(Map<String, String> parms)
    {
        if (parms != null && parms.containsKey("gzip")) {
            String gz = parms.get("gzip");
            if ("1".equals(gz) || "true".equalsIgnoreCase(gz) || "gzip".equalsIgnoreCase(gz)) {
                return true;
            }
        }
        return false;
    }
    
    /** Verarbeite Get Request
    * @param req Das Objekt, das den Request darstellt 
    * @param res Das Objekt, das die Response darstellt
     * @throws ServletException
    */
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res) 
        throws ServletException
    {
        String type = !serviceReturnType.equals("plain") ? "json" : "xml";
        try
        {
            PrintWriter pwrt = null;

            //Parameternamen sind gross/klein-unempfindlich!
            TreeMap<String,String> parms = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (Enumeration ps = req.getParameterNames(); ps.hasMoreElements();)
            {
                String p = (String) ps.nextElement();
                parms.put(p, req.getParameter(p));
            }
            boolean bZip = decodeGzipParameter(parms);
            
            int reqtype = parseParmRequest(parms.get("request"));
            // Read/ReadLock übertragen die entladene Datei unverändert als Bytes
            // (readRequest), daher hier kein Writer
            if (reqtype != 7 && reqtype != 11) {
                pwrt = res.getWriter();
                res.setContentType("text/" + type + "; charset=UTF-8");
            }
            int srctype = 0;
            String msg;
            DbVerbindung verb = null;
            boolean bAltdaten = false;
            boolean bPlanung = false;
            switch (reqtype) {
                case -1:
                    pwrt.println(generateResultString("failure", wakeUp("Request-Typ unbekannt."), -1));
                    break;
                case 1:
                    pwrt.println(generateResultString("accept", wakeUp("Hydrographie|Kataster|Leitungen|Planung|Topographie|Verkehr|Verwahltungsgrenzen|DGM|DOM"), -1));
                    break;
                case 2:
                    pwrt.println(generateResultString("accept", wakeUp("1|2|3|4"), -1));
                    break;
                case 3:
                    pwrt.println(generateResultString("accept", wakeUp("Service initialisiert"), -1));
                    break;
                case 4:
                    pwrt.println(generateResultString("accept", wakeUp("Service benachrichtigt"), -1));
                    break;
                case 5:
                case 8:
                case 10:
                    msg = wakeUp("");
                    if (msg.contains("Ladevorgang angestossen")) {
                        // Neuem Thread etwas Zeit geben
                        Thread.sleep(4 * 1000);
                    }
                    res.setContentType("text/html; charset=UTF-8");
                    srctype = parseDatasourceRequest(parms.get("datasource"));
                    verb = null;
                    bAltdaten = false;
                    bPlanung = false;
                    switch (srctype) {
                        case 3:
                            bPlanung = true;
                            if (this.havePlanung()) {
                                verb = this.vermdbPlanungConnection;
                            }
                            break;
                        case 2:
                            bAltdaten = true;
                            if (this.haveAltdaten()) {
                                verb = this.vermdbAltConnection;
                            }
                            break;
                        case 1:
                        default:
                            verb = this.vermdbConnection;
                            break;
                    }
                    writeQueue(verb, pwrt, reqtype, bAltdaten, bPlanung);
                    break;
                case 6:
                    res.setContentType("text/plain; charset=UTF-8");
                    String wert = readAreas();
                    pwrt.println(wert);
                    break;
                case 7:
                case 11:
                    // Download von Daten
                    if (bZip) {
                        res.setContentType("application/x-gzip;");
                    } else {
                        res.setContentType("text/xml; charset=UTF-8");
                    }
                    PostRequestContent postReq = new PostRequestContent();
                    postReq.addConnection(okstraConnection, vermdbConnection, dbVersion, okstraVersion);
                    // Parameter in PostReqeust übernehmen und normalisieren
                    postReq.addParameters(parms);
                    parms.clear();
                    msg = postReq.checkParameters(parms);
                    if (msg != null && !msg.isEmpty()) {
                        // wie bisher und wie die übrigen Antworten in ISO-8859-1
                        res.setContentType("text/xml; charset=ISO-8859-1");
                        res.getWriter().println(generateResultString("failure", msg, -1));
                        break;
                    }
                    readRequest(res, postReq, parms, null);
                    break;
                case 9:
                    res.setContentType("text/plain; charset=UTF-8");
                    srctype = parseDatasourceRequest(parms.get("datasource"));
                    verb = null;
                    DbVerbindung verb2 = null;
                    bAltdaten = false;
                    bPlanung = false;
                    switch (srctype) {
                        case 3:
                            bPlanung = true;
                            if (this.havePlanung()) {
                                verb = this.okstraPlanungConnection;
                                verb2 = this.vermdbPlanungConnection;
                            }
                            break;
                        case 2:
                            bAltdaten = true;
                            if (this.haveAltdaten()) {
                                verb = this.okstraAltConnection;
                                verb2 = this.vermdbAltConnection;
                            }
                            break;
                        case 1:
                            verb = this.okstraConnection;
                            verb2 = this.vermdbConnection;
                            break;
                        default:
                            break;
                    }
                    String job = parms.get("auftrag");
                    int jobnr;
                    try {
                        jobnr = Integer.parseInt(job);
                    } catch (Exception ex) {
                        jobnr = -1;
                    }
                    String sqlstmt;
                    if (verb != null && verb2 != null && jobnr > 0) {
                        String meta = DbVerbindung.getSchemaPrefix();
                        String data = verb.getDbMetadaten().getDbSchemaUnescaped();
                        sqlstmt = String.format("select %s.loesche_auftrag('%s', '%s', '', %d, 1, 0);",
                                meta, meta, data, jobnr);
                        try {
                            ResultSet rs = verb.PGDatenLesen(sqlstmt, null);
                            int status = 0;
                            int anz = 0;
                            if (rs != null) {
                                while (rs.next()) {
                                    anz = rs.getInt(1);
                                    ++status;
                                }
                                rs.close();
                            }
                            sqlstmt = " Auftrag " + String.valueOf(jobnr) + " entfernt, " +
                                    String.valueOf(anz) + " Datentabelle" + (anz == 1 ? "" : "n") + " betroffen.";
                            Db2Okstra.protokolliere(-1, sqlstmt, eMeldTyp.info, eProtokollTyp.meldungen);
                        } catch (Exception ex) {
                            sqlstmt = " Auftrag " + String.valueOf(jobnr) + " konnte nicht entfernt werden, Meldung: " +
                                    ex.getMessage();
                            Db2Okstra.protokolliere(-1, sqlstmt, eMeldTyp.warnung, eProtokollTyp.meldungen);
                        }
                    } else {
                        sqlstmt = "nicht möglich";
                        if (verb == null || verb2 == null)
                            sqlstmt += " Datenbankverbindung kann nicht bestimmt werden.";
                        if (jobnr < 0)
                            sqlstmt += " Auftragsnummer nicht gefunden oder ungültig.";
                    }
                    pwrt.println(generateResultString("accept", wakeUp("Auftragslöschung: " + sqlstmt), -1));
                    break;
                default:
                    break;
            }
        }
        catch (Exception e)
        {
            throw new ServletException(e);
        }
        finally
        {
        }
    }

    /**
     * Started Ladethread wenn keiner läuft. Kontext: GET-Request
     * @param message Meldungstext
     * @return message Angepasster Meldungstext
     */
    private synchronized String wakeUp(String message) {
        String msg = "";
        if (!lockLoader(true)) {
            // Wenn kein Thread läuft, in der Warteschlange nachschauen
            try {
                boolean bRequestChained = startNextThread(false);
                if (bRequestChained) {
                    msg = "Ladevorgang angestossen";
                } else {
                    lockLoader(false);
                }
            } catch (Exception ex) {
                msg = "Ausnahme beim Start eines neuen Ladevorgangs";
                try {
                    Db2Okstra.protokolliere(-1, msg + ": " + ex.getMessage(), eMeldTyp.warnung, eProtokollTyp.meldungen);
                } catch ( Ausnahme ex1 ) {
                    // Ausnahme während einer Meldung ignorieren
                }
                // Sperre wieder wegnehmen bei Ausnahme
                lockLoader(false);
            }
        }
        return message + (msg.isEmpty() ? "" : ", " + msg);
    }
    
    private String strip(String wert, String del) {
        String res = (wert.startsWith(del))
                ? wert.substring(del.length())
                : wert;
        if (res.endsWith(del)) {
            res = res.substring(0, res.length()-del.length());
        }
        return res;
    }
    
    /**
     * Entlädt Daten aus der Datenbank.
     * @param antwort bei GET {@code Read}/{@code ReadLock} die HTTP-Antwort, in die die Datei
     *     unverändert übertragen wird; sonst {@code null}
     * @param reqData Anfrage; mit {@code antwort == null} und ohne {@code outFile} ein
     *     asynchrones Entladen in das Rückgabeverzeichnis der SID
     * @param parms normalisierte Parameter
     * @param outFile Zieldatei (OkWS) oder {@code null}
     * @throws Exception Fehler beim Entladen
     */
    public void readRequest(HttpServletResponse antwort, PostRequestContent reqData, Map<String, String> parms, File outFile) throws Exception {
        if ((antwort == null && reqData == null && outFile == null) || parms == null) {
            throw new Exception("Unvollständige Anfrage, Entladen von Daten nicht möglich.");
        }
        String sid = parms.get("SessionID");
        String message = "";
        File returnDir = null;
        if (reqData != null && outFile == null) {
            if (sid == null || sid.isEmpty()) {
                throw new Exception("Session-ID unbekannt, Entladen von Daten nicht möglich.");
            }
            returnDir = new File(returnPath + sid);
            if (!returnDir.exists()) {
                boolean mkdir = returnDir.mkdir();
                if (!mkdir) {
                    throw new IOException("Verzeichnis konnte nicht angelegt werden: " + returnDir.getAbsolutePath());
                }
            }
            // Markiere laufenden Entladevorgang
            writeStateFile(sid, 0, "");
        }
        if (sid != null && !sid.isEmpty()) {
            message += "Session-ID: \"" + sid + "\"";
        }
        
        // Zielversion für die Rückgabe:
        // Entweder aus den Parametern oder
        // aus der Datenhaltungsversion bestimmen
        String vers = null;
        if (parms.containsKey("OKWS_VERSION")) {
            vers = parms.get("OKWS_VERSION");
        }
        Version outVers = null;
        if (reqData != null || vers != null) {
            if (vers == null) { 
                if (parms.containsKey("Version")) {
                    vers = parms.get("Version");
                    if (vers == null || vers.trim().isEmpty()) {
                        vers = reqData.getDbVersionString();
                    }
                }
                else {
                    vers = reqData.getDbVersionString();
                }
            }
            if (okstraVersionWrite.contains("|" + vers + "|")) {
                try {
                    String[] parts = vers.split("\\.");
                    if (parts.length > 1) {
                        int haupt = -1;
                        int unter = -1;
                        int rel;
                        if (!parts[0].isEmpty())
                            haupt = Integer.parseInt(parts[0]);
                        if (!parts[1].isEmpty())
                            unter = Integer.parseInt(parts[1]);
                        rel = (parts.length > 2 && !parts[2].isEmpty())
                            ? Integer.parseInt(parts[2])
                            : 0;
                        if (haupt > 0 && unter > 0 && rel >= 0) {
                            outVers = Version.Erzeuge(haupt, unter, rel);
                        }
                        if (outVers != null && outVers.GibText().contains("?")) {
                            outVers.Vernichte();
                            outVers = null;
                        }
                    }
                } catch (Exception ex) {
                    outVers = null;
                }
            }
        }
        if (outVers == null) {
            throw new Exception("Die Version " + vers + " wird nicht unterstützt");
        }
        String sSrs = "25832";
        String statt = "";
        if (reqData != null && reqData.hasItem("f22", true)) {
            sSrs = reqData.getItem("f22");
        }
        eKoordRefSys eSrs;
        try {
            int iSrs = Integer.parseInt(sSrs);
            switch (iSrs) {
                case 2398:
                    eSrs = eKoordRefSys.Pulk42_83_34;
                    break;
                case 25833:
                    eSrs = eKoordRefSys.UTM33N;
                    break;
                case 25832:
                    eSrs = eKoordRefSys.UTM32N;
                    break;
                case 31466:
                    eSrs = eKoordRefSys.GK2DHDN;
                    break;
                case 31467:
                    eSrs = eKoordRefSys.GK3DHDN;
                    break;
                case 31468:
                    eSrs = eKoordRefSys.GK4DHDN;
                    break;
                case 31469:
                    eSrs = eKoordRefSys.GK5DHDN;
                    break;
                case 4326:
                    eSrs = eKoordRefSys.WGS84;
                    break;
                default:
                    logger.warn("Koordinatenreferenzsystem " + sSrs + " wird nicht unterstützt. Es wird UTM32 verwendet");
                    eSrs = eKoordRefSys.UTM32N;
                    statt = sSrs;
                    sSrs = "UTM 32";
                    break;
            }
        } catch (Exception ex) {
            throw new Exception("Das Koordinatenreferenzsystem " + sSrs + " wird nicht unterstützt");
        }
        message += " Lagesystem: " + sSrs;
        if (!statt.isEmpty())
            message += " (anstelle von " + statt + ")";
        String sHs = "170";
        statt = "";
        if (reqData != null && reqData.hasItem("f23", true)) {
            sHs = reqData.getItem("f23");
        }
        eKoordRefSys eHs;
        try {
            int iSrs = Integer.parseInt(sHs);
            switch (iSrs) {
                case 160:
                    eHs = eKoordRefSys.HSYS_DE_DHHN_92_NH;
                    break;
                case 170:
                    eHs = eKoordRefSys.HSYS_DE_DHHN_2016_NH;
                    break;
                default:
                    logger.warn("Höhensystem " + sHs + " wird nicht unterstützt. Es wird DHHN_2016 verwendet");
                    eHs = eKoordRefSys.HSYS_DE_DHHN_2016_NH;
                    statt = sHs;
                    sHs = "DHHN_2016";
                    break;
            }
        } catch (Exception ex) {
            throw new Exception("Das Höhensystem " + sHs + " wird nicht unterstützt");
        }
        message += " Höhensystem: " + sHs;
        if (!statt.isEmpty())
            message += " (anstelle von " + statt + ")";
        
        String spCondition = "";
        String fbCondition = "";
        String okwsType = null;
        String okwsCondition = null;
        String okwsLimit = null;
        String okwsSrsName = null;
        String area = "";
        String thema = "0";
        List<String> oaListe = new ArrayList<>();
        String jobs = "";
        if (parms.containsKey("OKWS_TYPE")) {
            okwsType = parms.get("OKWS_TYPE");
            oaListe.add(okwsType);
            message += " OKWS-Anfrage zum Type " + okwsType;
            okwsCondition = parms.get("OKWS_CONDITION");
            if (parms.containsKey("OKWS_MAXFEATURES"))
                okwsLimit = parms.get("OKWS_MAXFEATURES");
            if (parms.containsKey("OKWS_SRSNAME"))
                okwsSrsName = parms.get("OKWS_SRSNAME");
        } else {
            // Gebietseinschränkung
            if (parms.containsKey("Area")) {
                // Gebiet wurde übergeben, das hat Vorrang
                area = parms.get("Area");
                if (area == null || area.trim().isEmpty()) {
                    area = "";
                } else {
                    area = strip(area, "\"");
                    area = strip(area, "'");
                }
                message += " Flächengeometrie angegeben";
            }
            if (area.isEmpty() && parms.containsKey("Gebiet")) {
                // Gebiet wird per Region festgelegt, Geometrie dazu besorgen
                String gebiet = parms.get("Gebiet");
                area = readArea(gebiet);
                message += " Gebiet \"" + gebiet + "\"";
            }
            if (area != null && !area.isEmpty()) {
                String srs = "SRID=" + String.valueOf(SRID) + ";";
                boolean bTransf = !area.substring(0, srs.length()).equalsIgnoreCase(srs);
                area = "st_geomfromewkt(" + SqlWert.text(area) + ")";
                if (bTransf) {
                    area = "st_transform(" + area + ", " + String.valueOf(SRID) + ")";
                }
                spCondition = "st_intersects(" + marker + ", " + area + ")";
            }

            // Fachthema
            if (parms.containsKey("Thema")) {
                thema = parms.get("Thema");
                if (thema == null || thema.trim().isEmpty()) {
                    thema = "0";
                }
                int wert = 0;
                try {
                    wert = Integer.parseInt(thema);
                } catch (NumberFormatException ex) {
                    throw new Exception("Fehlerhafte Angabe der Kennzeichnung eines Themas: " +
                            ex.getMessage());
                }
                if (wert < 0 || wert > 9) {
                    throw new Exception("Das Thema mit der Kennzeichnung \"" + thema +
                            "\" wird nicht unterstützt.");
                }
                message += " Fachthema \"" + thema + "\"";
            }

            // Auftrags- oder Projekteinschränkung
            String wert = parms.get("Projekt");
            if (wert != null && !wert.trim().isEmpty()) {
                wert = strip(wert, "\"");
                wert = strip(wert, "'");
                try {
                    jobs = getIntegers(false, false, reqData.getServiceVerbindung(),
                            "select auftragsnummer from " + metadataSchema + "." + okstraJobliste + " a where a.projekt = " + SqlWert.text(wert) + ";" );
                } catch (Exception ex) {
                    throw new Exception("Fehler bei Bestimmung der Auftragsnummern zu Projekt \"" + wert + "\".");
                }
                message += " Projekt \"" + wert + "\"";
            }
            wert = parms.get("Auftrag");
            if (wert != null && !wert.trim().isEmpty()) {
                try {
                    // nur ganze Zahlen; anderes scheiterte schon bisher an der Abfrage
                    String liste = SqlWert.ganzzahlListe(wert);
                    String auftr = getIntegers(false, false, reqData.getServiceVerbindung(),
                            "select auftragsnummer from " + metadataSchema + "." + okstraJobliste + " a where a.auftragsnummer in (" + liste + ");" );
                    if (!auftr.isEmpty()) {
                        jobs += (jobs.isEmpty() ? "" : ", ") + auftr;
                    }
                } catch (Exception ex) {
                    throw new Exception("Fehler bei Bestimmung von Auftragsnummern aus Liste \"" + wert + "\".");
                }
                message += " Auftrag \"" + wert + "\"";
            }
        }
        logger.info("Download-Auftrag erhalten: " + message);

        DbMetadaten meta = reqData.getOkstraVerbindung().getDbMetadaten();
        List<String> lenc = new ArrayList<>();
        meta.Verbinde(reqData.getOkstraVerbindung(), lenc);
        // DbMetadaten liest die Tabellennamen nur beim ersten Bedarf. Legt OkstraSqlUtil
        // beim Laden neue Tabellen an (in einer neuen Datenbank beim ersten Auftrag),
        // blieben deren Objektarten bis zum Neustart beim Entladen unberücksichtigt.
        meta.LiesTabellennamen(meta.getKodierung());
        String fbKeys = (okwsType != null) ? "" : readThema(meta, thema, oaListe);
        Version bsVersion = meta.getOkstraVersion();
        Datenbestand bestand = Datenbestand.Erzeuge(bsVersion);
        File tmpFile = null;
        DateiAusgabe ausgabe = null;
        AusgabeKonverter konverter = null;
        Fachbedeutungsliste fbliste = null;
        try {
            Db2Okstra dbo = new Db2Okstra();
            int pass = 0;
            while (++pass < 3) {
                for (String oaName : oaListe) {
                    Objektart oa = Objektart.Gib(oaName, bsVersion);
                    // Geometrieschema wird sonderbehandelt
                    if (oa.GibSchemaname().contains("Geometrieschema")) {
                        continue;
                    }
                    Eigenschaft oksId;
                    try {
                        oksId = Eigenschaft.Gib("OKSTRA_ID", oa);
                    } catch (Ausnahme a) {
                        oksId = null;
                    }
                    String jobCondition = "";
                    if (jobs != null && !jobs.isEmpty()) {
                        jobCondition = getIntegers(true, true, reqData.getServiceVerbindung(),
                        "select foid from " + metadataSchema + "._header_ h where h.auftrag in (" + jobs + ") " +
                        "AND h.klassenname = (select id from " + metadataSchema + ".txtpool where wert = " + SqlWert.text(oaName) + ");" );
                        if (!jobCondition.isEmpty()) {
                            jobCondition = "(oid in (" + jobCondition + "))";
                        }
                    }
                    String tabName = DbMetadaten.GibTabelle(meta, oaName.toLowerCase(Locale.GERMANY));
                    if (oksId == null) {
                        // Nur für solche Objektarten weitermachen,
                        // wo es auch eine OKSTRA_ID gibt (das ist
                        // das zentrale Sortierkriterium in der DB)
                        if (pass == 1) {
                            // Objektart trotzdem aufsammeln...
                            String dummy = null;
                            dbo.versammle(oaName.toLowerCase(Locale.GERMANY), dummy);
                        }
                        continue;
                    }
                    Eigenschaft fbProp = null;
                    if (!fbKeys.isEmpty() && oa.HatFachbedeutung()) {
                        for (Eigenschaft e : oa.GibEigenschaften(true)) {
                            if (e.HatFachbedeutung()) {
                                fbProp = e;
                                break;
                            }
                        }
                    }
                    if (fbProp != null) {
                        fbCondition = fbKeys.replace(marker, fbProp.GibName());
                    }

                    // Geometrische Bedingung passend zur Objektart ergänzen
                    // Es kann mehrere Geometrien geben, diese werden per ODER gesucht
                    String geoCond = "";
                    if (!spCondition.isEmpty()) {
                        for (Eigenschaft e : gibGeoSpalten(oa)) {
                            // Multigeometrie kann beteiligt sein!
                            if (e.GibTyp().equals(eTyp.objectType)) {
                                Objektart asso = e.GibAssoziation();
                                String assoTabName = DbMetadaten.GibTabelle(meta, asso.GibName().toLowerCase(Locale.GERMANY));
                                for (Eigenschaft em : gibGeoSpalten(asso)) {
                                    String join = "(SELECT m." + em.GibName() + " as geom FROM " + tabName + " t " +
                                            "LEFT OUTER JOIN " + meta.getDbSchemaUnescaped() + ".zwischentab z " +
                                            "ON z.id = t.oid AND z.role = '" + e.GibName() + "' AND " +
                                            "source = '" + oa.GibName().toLowerCase(Locale.GERMANY) + "' AND " +
                                            "target = '" + asso.GibName().toLowerCase(Locale.GERMANY) + "' " +
                                            "LEFT OUTER JOIN " + assoTabName + " m ON m.oid = z.rid " + 
                                            "WHERE m." + em.GibName() + " IS NOT NULL)";
                                    if (geoCond.isEmpty()) {
                                        geoCond = spCondition.replace(marker, join);
                                    } else {
                                        geoCond += " OR " + spCondition.replace(marker, join);
                                    }
                                }
                            } else {
                                if (geoCond.isEmpty()) {
                                    geoCond = spCondition.replace(marker, e.GibName());
                                } else {
                                    geoCond += " OR " + spCondition.replace(marker, e.GibName());
                                }
                            }
                        }
                    }
                    String cond;
                    String prop;
                    String where;
                    String limit;
                    long anzahl = Long.MAX_VALUE;
                    if (okwsType != null) {
                        prop = "okstra_id";
                        cond = okwsCondition;
                        if (cond == null || cond.isEmpty()) {
                            where = "";
                        } else {
                            where = " WHERE ";
                        }
                        limit = "";
                        if (okwsLimit != null && !okwsLimit.isEmpty()) {
                            try {
                                anzahl = Long.parseLong(okwsLimit);
                            } catch (Exception ex) {
                                limit = "";
                            }
                        } else {
                            limit = "";
                        }
                    } else {
                        prop = "okstra_id";
                        cond = fbCondition;
                        where = " WHERE ";
                        limit = "";
                        if (!geoCond.isEmpty()) {
                            cond = (cond != null && !cond.isEmpty() ? "(" + cond + ") AND " : "") +
                                    "(" + geoCond + ")";
                        }
                        if (!jobCondition.isEmpty()) {
                            cond = (cond != null && !cond.isEmpty() ? "(" + cond + ") AND " : "") +
                                    jobCondition;
                        }
                    }
                    if (!cond.isEmpty() || okwsType != null) {
                        cond = String.format("SELECT %s from %s%s%s%s",
                                prop, tabName, where, cond, limit);
                        if (pass == 2) {
                            dbo.exportiere(reqData.getOkstraVerbindung(), bestand, oaName, cond, anzahl);
                        }
                    } else if (pass == 1) {
                        // Aufsammeln der Objektarten, die sich durch
                        // keine der Bedingungen qualifizieren.
                        String dummy = null;
                        dbo.versammle(oaName.toLowerCase(Locale.GERMANY), dummy);
                    }
                }
            }

            // Wenn migriert werden muss...
            if (!bsVersion.GibText(true).equals(outVers.GibText(true))) {
                Datenbestand migriert = Datenbestand.Erzeuge(outVers);
                Datenbestand.Migriere(bestand, outVers, migriert);
                bestand.Vernichte();
                bestand = migriert;
                bsVersion = outVers;
            }
            
            // Wenn transformiert werden muss...
            if (eSrs.GibCode() != SRID) {
                Db2Okstra.protokolliere(-1, "Transformiere Lagesystem der entladenen Datein nach " +
                        Umgebung.GibTextZuWert(eAufzaehlungen.tKoordRefSys, eSrs.GibCode()), eMeldTyp.info, eProtokollTyp.meldungen);
                bestand.Transformiere(eSrs);
            }
            if (eHs.GibCode() != HSYS) {
                Db2Okstra.protokolliere(-1, "Transformiere Höhensystem der entladenen Datein nach " +
                        Umgebung.GibTextZuWert(eAufzaehlungen.tKoordRefSys, eHs.GibCode()), eMeldTyp.info, eProtokollTyp.meldungen);
                bestand.Transformiere(eHs);
            }

            // Rausschreiben
            String format = "XML";
            if (parms.containsKey("Format") ) {
                format = ((String)parms.get("Format")).toUpperCase();
            }
            if (outFile != null) {
                tmpFile = outFile;
            } else {
                tmpFile = File.createTempFile("readOut", ".tmp");
            }
            ausgabe = DateiAusgabe.Erzeuge(tmpFile.getAbsolutePath());
            if (format.equals("XML")) {
                konverter = XMLAusgabeKonverter.Erzeuge(ausgabe);
                format = ".xml";
                if (okwsType != null) {
                    ausgabe.SetzeKodierung(eKodierung.UTF8);
                    ((XMLAusgabeKonverter)konverter).SetzeZeichensatz("UTF-8");
                    boolean wfsFormat = true;
                    if (parms.containsKey("OKWS_WFSEXPECTED")) {
                        String wfsExp = parms.get("OKWS_WFSEXPECTED");
                        wfsFormat = ("1".equals(wfsExp) || "ja".equalsIgnoreCase(wfsExp) || "true".equalsIgnoreCase(wfsExp));
                    }
                    ((XMLAusgabeKonverter)konverter).EntladeWFSFormat(wfsFormat);
                }
            } else {
                konverter = CTEAusgabeKonverter.Erzeuge(ausgabe);
                format = ".cte";
            }
            boolean bZip = decodeGzipParameter(parms);
            if (bZip) {
                format += ".gz";
                konverter.Komprimiere(true);
            }
            konverter.SetzeMetadaten(eMetadaten.originating_system, "Vermessungsdatenbank");
            konverter.SetzeMetadaten(eMetadaten.author, okstraBehoerde);
            konverter.SetzeMetadaten(eMetadaten.description, "Exportdaten");
            if (!okstraFBliste.isEmpty()) {
                try {
                    fbliste = Fachbedeutungsliste.Erzeuge(FblVersion.Erzeuge(okstraFBlisteHaupt, okstraFBlisteUnter, okstraFBlisteBuLa));
                } catch (Ausnahme ex) {
                    // Fehler, keine Angabe vornehmen
                    String msg = "Die Vorgabe für die Fachbedeutungsliste konnte nicht angelegt werden. Meldung: " + ex.GibMeldungstext();
                    Db2Okstra.protokolliere(-1, msg, eMeldTyp.warnung, eProtokollTyp.meldungen);
                }
            }
            if (fbliste != null)
                bestand.SetzeFbl(fbliste);
            konverter.Entlade(bestand);
            bestand.Vernichte();
            bestand = null;
            if (fbliste != null)
                fbliste.Vernichte();
            fbliste = null;
            konverter.Vernichte();
            konverter = null;
            ausgabe.Vernichte();
            ausgabe = null;
            if (bZip && tmpFile.exists() && !tmpFile.getAbsolutePath().endsWith(".gz")) {
                File gzfile = new File(tmpFile.getAbsolutePath() + ".gz");
                if (gzfile.exists()) {
                    tmpFile.delete();
                    tmpFile = gzfile;
                }
            }

            // In Ausgabe schreiben
            if (antwort != null) {
                // Nur bei Get-Request: Datei unverändert in die Response übertragen, mit
                // dem Zeichensatz ihrer XML-Deklaration. Bis 2026 wurde sie als
                // windows-1252 gelesen und über einen ISO-8859-1-Writer geschrieben; dabei
                // gingen Zeichen verloren (unter Linux ß, Ä, Ö, Ü, €).
                if (tmpFile.exists()) {
                    antwort.setContentType(bZip
                            ? "application/x-gzip;"
                            : Entladeantwort.inhaltstyp(tmpFile.toPath()));
                    try (InputStream is = new FileInputStream(tmpFile)) {
                        is.transferTo(antwort.getOutputStream());
                    }
                }
            } else if (reqData != null && outFile == null) {
                // Post-Request: Datei an die richtige Stelle kopieren
                if (returnDir != null && tmpFile.exists()) {
                    File res = new File(returnDir.getAbsolutePath() +
                            File.separator + "OkstraUnload" + format);
                    if (res.exists()) {
                        res.delete();
                    }
                    FileUtils.copyFile(tmpFile, res);
                    writeStateFile(sid, 1, ""); // alles ist gut
                } else {
                    writeStateFile(sid, 2, "Keine Entladedatei vorhanden"); // au weia
                }
            }
        } finally {
            if (outFile == null && tmpFile != null && tmpFile.exists()) {
                tmpFile.delete();
                tmpFile = null;
            }
            if (bestand != null) {
                bestand.Vernichte();
                bestand = null;
            }
            if (fbliste != null) {
                fbliste.Vernichte();
                fbliste = null;
            }
            if (konverter != null) {
                konverter.Vernichte();
                konverter = null;
            }
            if (ausgabe != null) {
                ausgabe.Vernichte();
                ausgabe = null;
            }
        }
    }
    
    private List<Eigenschaft> gibGeoSpalten(Objektart oa) throws Ausnahme
    {
        List<Eigenschaft> liste = new ArrayList<>();
        int countPnt = 0, countLin = 0, countFla = 0, countVol = 0;
        Eigenschaft kandPoint = null;
        Eigenschaft kandLine = null;
        Eigenschaft kandSurface = null;
        Eigenschaft kandSolid = null;
        de.okstra.oklabi.Version vsn = oa.GibVersion();
        try
        {
            boolean bExpress = vsn.GibModellierung().equals(eModellierung.EXPRESS);
            for (Eigenschaft eg : oa.GibEigenschaften(true))
            {
                if (eg.IstKuenstlich() || eg.IstImplizit())
                {
                    // Wenn es eine künstliche Geometrie-Eigenschaft gibt,
                    // wird diese bei Objektarten zur OKSTRA-Hauptversion 1
                    // abgebildet. Diese Eigenschaften werden nach Geometrietyp
                    // getrennt von Null beginnend gezählt.
                    if (bExpress)
                    {
                        eTyp typ = eg.GibTyp();
                        if (typ.equals(eTyp.gPointType)) {
                            if (oa.HatGeometrie(eGeoTyp.Punkt)) {
                                if (countPnt == 0) {
                                    liste.add(eg);
                                }
                                countPnt++;
                            }
                        }
                        else if (typ.equals(eTyp.gLineType)) {
                            if (oa.HatGeometrie(eGeoTyp.Linie)) {
                                if (countLin == 0) {
                                    liste.add(eg);
                                }
                                countLin++;
                            }
                        }
                        else if (typ.equals(eTyp.gSurfaceType)) {
                            if (oa.HatGeometrie(eGeoTyp.Flaeche)) {
                                if (countFla == 0) {
                                    liste.add(eg);
                                }
                                countFla++;
                            }
                        }
                        else if (typ.equals(eTyp.gSolidType)) {
                            if (oa.HatGeometrie(eGeoTyp.Volumen)) {
                                if (countVol == 0) {
                                    liste.add(eg);
                                }
                                countVol++;
                            }
                        }
                        else if (typ.equals(eTyp.geoType)) {
                            String eigName = eg.GibName();
                            if ((eigName.contains("GeoPunkt")) && oa.HatGeometrie(eGeoTyp.Punkt))
                            {
                                if (countPnt == 0)
                                    liste.add(eg);
                                countPnt++;
                            }
                            if ((eigName.contains("GeoLinie")) && oa.HatGeometrie(eGeoTyp.Linie))
                            {
                                if (countLin == 0)
                                    liste.add(eg);
                                countLin++;
                            }
                            if ((eigName.contains("GeoFlaeche")) && oa.HatGeometrie(eGeoTyp.Flaeche))
                            {
                                if (countFla == 0)
                                    liste.add(eg);
                                countFla++;
                            }
                            if ((eigName.contains("GeoVolumen")) && oa.HatGeometrie(eGeoTyp.Volumen))
                            {
                                if (countVol == 0)
                                    liste.add(eg);
                                countVol++;
                            }
                        }
                        else {
                            // Kein Geometrie-Typ: Eigenschaft wird ignoriert
                        }
                    }
                    else {
                        // UML-Modellierung: Geometrie-Eigenschaft wird
                        // registriert wenn es berechnete Geometrie gibt
                        String eigName = eg.GibName();
                        if (eigName.contains("GeoPunkt"))
                        {
                            if (kandPoint == null)
                                kandPoint = eg;
                            countPnt++;
                        }
                        if (eigName.contains("GeoLinie"))
                        {
                            if (kandLine == null)
                                kandLine = eg;
                            countLin++;
                        }
                        if (eigName.contains("GeoFlaeche"))
                        {
                            if (kandSurface == null)
                                kandSurface = eg;
                            countFla++;
                        }
                        if (eigName.contains("GeoVolumen"))
                        {
                            if (kandSolid == null)
                                kandSolid = eg;
                            countVol++;
                        }
                    }
                } else if (!bExpress) {
                    // Nicht künstlich, UML-Modellierung: Geometrie-Eigenschaft mitnehmen
                    eEinheitTyp typ = eg.GibEinheit();
                    if (typ.equals(eEinheitTyp.uPoint)   || typ.equals(eEinheitTyp.uMultiPoint)   ||
                        typ.equals(eEinheitTyp.uCurve)   || typ.equals(eEinheitTyp.uMultiCurve)   ||
                        typ.equals(eEinheitTyp.uSurface) || typ.equals(eEinheitTyp.uMultiSurface) ||
                        typ.equals(eEinheitTyp.uSolid)   || typ.equals(eEinheitTyp.uMultiSolid)) {
                        liste.add(eg);
                    }
                    // Multigeometrie kann vorkommen, später aufzulösen
                    if (eg.GibTyp().equals(eTyp.objectType)) {
                        Objektart asso = eg.GibAssoziation();
                        if (asso != null && asso.GibName().equals("Multigeometrie")) {
                            liste.add(eg);
                        }
                    }
                }
            }
            // Falls der implizit berechnete Kandidat keine Typentsprechung bei den
            // UML-Geometrieeigenschaften besitzt, diese Eigenschaft mitnehmen
            if (!bExpress) {
                addPropertyToList(liste, kandPoint,   eEinheitTyp.uPoint,   eEinheitTyp.uMultiPoint);
                addPropertyToList(liste, kandLine,    eEinheitTyp.uCurve,   eEinheitTyp.uMultiCurve);
                addPropertyToList(liste, kandSurface, eEinheitTyp.uSurface, eEinheitTyp.uMultiSurface);
                addPropertyToList(liste, kandSolid,   eEinheitTyp.uSolid,   eEinheitTyp.uMultiSolid);
            }
            return liste;
        }
        finally {
        }
    }
    
    /**
     * In der Liste wird nachgesehen, ob es eine Eigenschaft mit einem der genannten
     * Einheit-Typen gibt. Falls nicht, wird der Kandidat in die Liste mit aufgenommen.
     * @param liste Liste mit Geometrie-Eigenschaften
     * @param kandidat Berechnete Geometrie-Eigenschaft
     * @param t1 Typ (single)
     * @param t2 Typ (multi)
     * @throws Ausnahme 
     */
    private void addPropertyToList(List<Eigenschaft> liste, Eigenschaft kandidat, eEinheitTyp t1, eEinheitTyp t2) throws Ausnahme {
        if (kandidat != null) {
            boolean found = false;
            for (Eigenschaft eg : liste) {
                eEinheitTyp eT = eg.GibEinheit();
                if (eT.equals(t1) || eT.equals(t2)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                liste.add(kandidat);
            }
        }
    }
    
    private String readThema(DbMetadaten meta, String thema, List<String> oaListe) throws Exception {
        // Fachbedeutungen passend zum Thema aufsetzen
        String res = findFbKey(thema);
        Map<String, Objektart> oaMap = meta.GibObjektarten(meta.getKodierung(), false);
        for (Map.Entry<String, Objektart> entry : oaMap.entrySet())
        {
            String oaName = entry.getKey();
            Objektart oa = entry.getValue();
            if (oa == null || oa.IstSchluesseltabelle() || oa.IstKonzeptionell()) {
                // Hilfstabellen überspringen, die anderen
                // Kandidaten werden automatisch mitgenommen
                continue;
            }
            if (thema.equals("0")) {
                // Alle Objektarten
                oaListe.add(oaName);
            } else if (thema.equals("1")) {
                // Hydrographie
            } else if (thema.equals("2")) {
                // Kataster
            } else if (thema.equals("3")) {
                // Leitungen
            } else if (thema.equals("4")) {
                // Planung
                if (oaName.equals("Achse") ||
                    oaName.equals("Querprofil") ||
                    oaName.equals("Gradiente")) {
                    oaListe.add(oaName);
                }
            } else if (thema.equals("5")) {
                // Topographie
            } else if (thema.equals("6")) {
                // Verkehr
            } else if (thema.equals("7")) {
                // Verwaltungsgrenzen
            } else if (thema.equals("8")) {
                // DGM
                if (oaName.equals("DGM") ||
                    oaName.equals("Dreieck") ||
                    oaName.equals("Bruchkante")) {
                    oaListe.add(oaName);
                }
            } else if (thema.equals("9")) {
                // DOM
            }
            if (!res.isEmpty() && !thema.equals("0")) {
                if (oaName.equals("allgemeines_Punktobjekt") ||
                    oaName.equals("allgemeines_Linienobjekt") ||
                    oaName.equals("allgemeines_Flaechenobjekt")) {
                    oaListe.add(oaName);
                }
            }
        }
        return res;
    }
    
    private String findFbKey(String thema) throws Exception {
        String res = "";
        if (thema.equals("0")) {
            // Alle Objektarten
        } else if (thema.equals("1")) {
            // Hydrographie
            res = "WHERE kategorie in (" +
                    "'Gewässer'," +
                    "'Hydrographie'" +
                    ")";
        } else if (thema.equals("2")) {
            // Kataster
            res = "WHERE kategorie in (" +
                    "'Kataster'" +
                    ")";
        } else if (thema.equals("3")) {
            // Leitungen
            res = "WHERE kategorie in (" +
                    "'Ver-/Entsorgung'," +
                    "'Versorgung'" +
                    ")";
        } else if (thema.equals("4")) {
            // Planung
            res = "WHERE kategorie in (" +
                    "'Gradiente / Querprofil'" +
                    ")";
        } else if (thema.equals("5")) {
            // Topographie
            res = "WHERE kategorie in (" +
                    "'Befestigung'," +
                    "'Bewuchs'," +
                    "'Böschung'," +
                    "'DGM'," +
                    "'Nutzungsarten'," +
                    "'Topographie'" +
                    ")";
        } else if (thema.equals("6")) {
            // Verkehr
            res = "WHERE kategorie in (" +
                    "'Verkehr'" +
                    ")";
        } else if (thema.equals("7")) {
            // Verwaltungsgrenzen
            res = "WHERE kategorie in (" +
                    "'Verwaltung'" +
                    ")";
        } else if (thema.equals("8")) {
            // DGM
            res = "WHERE kategorie in (" +
                    "'DGM'" +
                    ")";
        } else if (thema.equals("9")) {
            // DOM
            res = "WHERE kategorie in (" +
                    "'DOM'" +
                    ")";
        }
        if (!res.isEmpty()){
            String stmt = String.format("SELECT fachbedeutung FROM %s %s;",
                    "fbliste", res);
            String wert = "";
            ResultSet rs = this.vermdbConnection.PGDatenLesen(stmt, null);
            int status = 0;
            if (rs != null) {
                while (rs.next())
                {
                    if (status > 0) {
                        wert += ",";
                    }
                    String sVal = rs.getString(1);
                    wert += SqlWert.text(sVal);
                    ++status;
                }
                rs.close();
                if (wert.length() > 0) {
                    wert = marker + " IN (" + wert + ")";
                }
            }
            res = wert;
        }
        return res;
    }
    
    private String readArea(String area) throws Exception {
        if (area == null || area.isEmpty()) {
            // Da sollte schon was drin stehen
            throw new Exception("Keine Bezeichnung für die Region angegeben.");
        }
        String oid;
        try {
            oid = SqlWert.ganzzahl(area);
        } catch (NumberFormatException ex) {
            // Wie bisher, als die Abfrage daran scheiterte: kein Gebiet
            logger.warn("Gebiet \"" + area + "\" ist keine Kennung aus oks_meta.gebiete, es gilt keine Gebietseinschränkung.");
            return "";
        }
        String stmt = String.format("SELECT st_asewkt(the_geom) FROM %s WHERE oid=%s;",
                "gebiete", oid);
        String wert = "";
        ResultSet rs = this.vermdbConnection.PGDatenLesen(stmt, null);
        if (rs != null) {
            while (rs.next())
            {
                String sVal = rs.getString(1);
                wert = sVal;
            }
            rs.close();
        }
        return wert;
    }
    
    private String readAreas() throws Exception {
        String stmt = String.format("SELECT oid,name FROM %s ORDER BY oid;", "gebiete");
        String wert = "";
        ResultSet rs = this.vermdbConnection.PGDatenLesen(stmt, null);
        int status = 0;
        if (rs != null) {
            while (rs.next())
            {
                int iVal = rs.getInt(1);
                wert += "|" + String.valueOf(iVal) + "%";
                String sVal = rs.getString(2);
                wert += sVal;
                ++status;
            }
            rs.close();
            if (wert.length() > 0) {
                wert += "|";
            }
        }
        return wert;
    }
    
    private String escapeHtml(String str) {
        return HtmlText.umlaute(str);
    }

    private String html(String str) {
        return HtmlText.maskiere(str);
    }

    private void writeQueue(DbVerbindung verb, PrintWriter pwrt, int reqtype, boolean bAltdaten, boolean bPlanung) throws Exception {
        boolean bFull = (reqtype==8);
        boolean bCheck = (reqtype==10);
        String dbAuswahl = bAltdaten ? "Altdaten" : (bPlanung ? "Planungsdaten" : "Bestandsdaten");
        if (verb == null) {
            String msg = "Zur gewünschten Datenbank mit den " + dbAuswahl + " besteht keine Datenbankverbindung.";
            throw new Exception(msg);
        }
        String dbVers = bAltdaten ? okstraVersionAltdaten : (bPlanung ? okstraVersionPlanung : okstraVersion);
        pwrt.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        pwrt.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\"");
        pwrt.println("\"http://www.w3.org/TR/html4/loose.dtd\">");
        pwrt.println("<html>\n" +
                     "  <head>\n" +
                     "    <title>\n" +
                     "      Auftragsliste " + (bCheck ? "(Prüfung)" : "(Import)") + " VermDB\n" +
                     "      Version " + escapeHtml(versionsangabe.version()) + "\n" +
                     "    </title>\n" +
                     "    <meta http-equiv=\"expires\" content=\"0\">\n" +
                     "  </head>");
        pwrt.println("  <body>");
        String translated = escapeHtml(okstraLangname);
        pwrt.println("   <p align=\"center\" style=\"font-size:172%; font-weight:bold;\">\n" +
                     "   " + translated + "<br>\n" +
                     "   " + escapeHtml(okstraDbName) + "\n" +
                     "   </p>");
        pwrt.println("   <p align=\"center\" style=\"font-size:172%; font-weight:bold;\">\n" +
                     "   Auftragsliste " + (bCheck ? "(Prüfung)" : "(Import)") + " für die " + dbAuswahl + " (OKSTRA-Version: " + dbVers + ")\n" +
                     "   </p>");
        pwrt.println("   <table align=\"center\" cellpadding=\"2px\" border=\"1\" style=\"font-size:75%;\">");
        pwrt.println(" 	  <tr>\n" +
                     "	   <td><b>Auftrag</b></td>\n" +
                     "	   <td><b>Projekt</b></td>\n" +
                     "	   <td><b>Session-ID</b></td>\n" +
                     "	   <td><b>Benutzername</b></td>\n" +
                     "	   <td><b>Zeitstempel</b></td>\n" +
                     "	   <td><b>Name</b></td>\n" +
                     "	   <td><b>Status</b></td>\n" +
                     "	   <td><b>eingestellt</b></td>\n" +
                     "	   <td><b>Stichtag</b></td>\n" +
                     "	   <td><b>Format</b></td>\n" +
                     "	   <td><b>L&ouml;schung</b></td>\n" +
                     "	   <td><b>Version Datensatz</b></td>\n" +
                     "	   <td><b>Fachbedeutungen</b></td>\n" +
                     "	   <td><b>KoordRefSystem</b></td>\n" +
                     "	   <td><b>H&ouml;hensystem</b></td>\n" +
                     "	   <td><b>beendet</b></td>\n" +
                     "	   <td><b>Laden</b></td>\n" +
                     "	   <td><b>Migration</b></td>\n" +
                     "	   <td><b>&Uuml;bertragung</b></td>\n" +
                     "	   <td><b>Datenfehler</b></td>\n" +
                     "	  </tr>");
        fillQueue(verb, pwrt, bFull, bCheck, bAltdaten, bPlanung);
        pwrt.println("   </table>");
        pwrt.println("  </body>");
        pwrt.println("</html>");
        pwrt.println("");
        pwrt.println("");
    }
    
    private void fillQueue(DbVerbindung verb, PrintWriter pwrt, boolean bFull, boolean bCheck, boolean bAltdaten, boolean bPlanung) throws Exception {
        synchronized (this.marker) {
            String indent = "    ";
            String logPath = getLogPath();
            // Die Auftragsnummern in der Warteschlange auslesen
            String check = bCheck ? " AND (status % 10) IN (5,6)": " AND (status % 10) NOT IN (5,6)";
            String cond = bFull ? "" : " WHERE (status >= 0 OR status IN (-8, -5)) AND NOT loeschung" + check;
            String stmt = String.format("SELECT * FROM %s AS q %s ORDER BY auftragsnummer DESC;",
                    getQueue(verb), cond);
            ResultSet rs = verb.PGDatenLesen(stmt, null);
            int status = 0;
            String wert;
            if (rs != null) {
                while (rs.next()) {
                    pwrt.println(indent+"<tr>");
                    ++status;
                    Timestamp beendet = rs.getTimestamp("beendet");
                    // Auftrag
                    int iVal = rs.getInt("auftragsnummer");
                    pwrt.println(indent+indent+"<td>" + String.valueOf(iVal) + "</td>");
                    wert = rs.getString("projekt");
                    pwrt.println(indent+indent+"<td>" + html(wert == null ? "<unbekannt>" : wert) + "</td>");
                    // SessionID
                    wert = rs.getString("sessionid");
                    pwrt.println(indent+indent+"<td>" + html(wert) + "</td>");
                    // Username
                    wert = rs.getString("username");
                    pwrt.println(indent+indent+"<td>" + html(wert) + "</td>");
                    // Zeitstempel
                    wert = rs.getString("timestamp");
                    pwrt.println(indent+indent+"<td>" + html(wert) + "</td>");
                    // Eingabedatei
                    wert = rs.getString("original");
                    if (wert.startsWith("_")) wert = wert.substring(1);
                    pwrt.println(indent+indent+"<td>" + html(wert) + "</td>");
                    // Status
                    wert = "&nbsp;";
                    boolean bearbeitet = rs.getBoolean("bearbeitung");
                    iVal = rs.getInt("status");
                    if (bearbeitet) {
                        if (iVal == 0) {
                            wert = "laden, prüfen";
                        }
                        else if (iVal >= 1000 && iVal < 10000) {
                            wert = "migrieren";
                        }
                        else if (iVal == -5) {
                            wert = "abgelehnt";
                        }
                        else if (iVal == -8) {
                            wert = "Ladefehler";
                        }
                        else if (iVal < 0) {
                            wert = "gelöscht";
                        }
                        else {
                            wert = "übertragen";
                        }
                    }
                    else {
                        if (iVal == -5) {
                            wert = "abgelehnt";
                        }
                        else if (iVal == -8) {
                            wert = "Ladefehler";
                        }
                        else if (iVal < 0) {
                            wert = "gelöscht";
                        }
                        else if (beendet == null) {
                            wert = "wartet";
                        }
                        else if (iVal >= 1000000) {
                            switch ((iVal % 10)) {
                                case 1:
                                    wert = "geladen, mit Meldungen";
                                    break;
                                case 5:
                                    wert = "geprüft";
                                    break;
                                case 6:
                                    wert = "geprüft, mit Meldungen";
                                    break;
                                default:
                                    wert = "geladen";
                                    break;
                            }
                        }
                        else {
                            wert = "abgebrochen";
                        }
                    }
                    pwrt.println(indent+indent+"<td>" + escapeHtml(wert) + "</td>");
                    // Erfassungsdatum
                    Timestamp date = rs.getTimestamp("erfasst");
                    pwrt.println(indent+indent+"<td>" + formatDate(new Date(date.getTime()), "yyyy-MM-dd HH:mm:ss") + "</td>");
                    // Stichtag
                    Date tag = rs.getDate("stichtag");
                    wert = formatDate(tag, "yyyy-MM-dd");
                    pwrt.println(indent+indent+"<td>" + wert + "</td>");
                    // Datenformat
                    wert = rs.getString("format");
                    pwrt.println(indent+indent+"<td>" + html(wert) + "</td>");
                    // Löschauftrag
                    boolean bDelete = rs.getBoolean("loeschung");
                    pwrt.println(indent+indent+"<td>" + (bDelete ? "ja" : "nein") + "</td>");
                    // Version
                    Integer[] iArray = (Integer[])(rs.getArray("version").getArray());
                    wert = iArray[2] != 0
                         ? String.format("%d.%03d.%d", iArray[0], iArray[1], iArray[2])
                         : String.format("%d.%03d", iArray[0], iArray[1]);
                    pwrt.println(indent+indent+"<td>" + wert + "</td>");
                    // FB-Liste
                    String[] sArray = (String[])(rs.getArray("fbversion").getArray());
                    wert = String.format("%s.%s %s", sArray[0], sArray[1], sArray[2]);
                    pwrt.println(indent+indent+"<td>" + html(wert) + "</td>");
                    // Höhensystem und Koordinatenreferenzsystem sind in eine Spalte kodiert
                    iVal = rs.getInt("srs");
                    int hVal = 0;
                    if (iVal > SRSFACTOR) {
                        hVal = iVal / SRSFACTOR;
                        iVal = iVal % SRSFACTOR;
                    }
                    if (iVal == 2398) {
                        wert = "Lagestatus 150";
                    }
                    else {
                        try {
                            wert = Umgebung.GibTextZuWert(eAufzaehlungen.tKoordRefSys, iVal);
                            if (!wert.contains("<")) {
                                wert = html(wert);
                            } else {
                                wert = "<font color=\"red\"><b>" + html(wert) + "</b></font>";
                            }
                        }
                        catch (Ausnahme ex) {
                            wert = "<font color=\"red\"><b>ungültig</b></font>";
                        }
                    }
                    // escapeHtml nur noch für die Umlaute im eigenen Markup ("ungültig")
                    pwrt.println(indent+indent+"<td>" + escapeHtml(wert) + "</td>");
                    try {
                        wert = Umgebung.GibTextZuWert(eAufzaehlungen.tKoordRefSys, hVal);
                        if (!wert.contains("<")) {
                            wert = html(wert);
                        } else {
                            wert = "<font color=\"red\"><b>" + html(wert) + "</b></font>";
                        }
                    }
                    catch (Exception ex) {
                        String t = ex.getMessage();
                        wert = "<font color=\"red\"><b>ungültig</b></font>";
                    }
                    pwrt.println(indent+indent+"<td>" + escapeHtml(wert) + "</td>");
                    // Fertigstellungsdatum
                    wert = beendet != null ? formatDate(new Date(beendet.getTime()), "yyyy-MM-dd HH:mm:ss") : "&nbsp;";
                    pwrt.println(indent+indent+"<td>" + wert + "</td>");
                    // Lade-/Prüfprotokoll
                    wert = rs.getString("pruefprotokoll");
                    if (wert != null && wert.startsWith(logPath)) {
                        File file = new File(wert);
                        if (!file.exists()) {
                            wert = "fehlt";
                            pwrt.println(indent+indent+"<td>" + escapeHtml(wert) + "</td>");
                        }
                        else {
                            wert = wert.substring(logPath.length());
                            pwrt.println(indent+indent+"<td><a href=\"" + webServerPrefix + "log/" + html(wert) + "\" target=\"_blank\">öffnen</a></td>");
                        }
                    }
                    else if (wert == null || wert.trim().isEmpty()) {
                        wert = "fehlt";
                        pwrt.println(indent+indent+"<td>" + wert + "</td>");
                    }
                    else {
                        pwrt.println(indent+indent+"<td><font color=\"red\">" + html(wert) + "</font></td>");
                    }
                    // Migrationsprotokoll
                    wert = rs.getString("migrationprotokoll");
                    if (wert != null && wert.startsWith(logPath)) {
                        File file = new File(wert);
                        if (!file.exists()) {
                            wert = "fehlt";
                            pwrt.println(indent+indent+"<td>" + wert + "</td>");
                        }
                        else {
                            wert = wert.substring(logPath.length());
                            pwrt.println(indent+indent+"<td><a href=\"" + webServerPrefix + "log/" + html(wert) + "\" target=\"_blank\">öffnen</a></td>");
                        }
                    }
                    else if (wert == null || wert.trim().isEmpty()) {
                        wert = "&nbsp;";
                        pwrt.println(indent+indent+"<td>" + wert + "</td>");
                    }
                    else {
                        pwrt.println(indent+indent+"<td><font color=\"red\">" + html(wert) + "</font></td>");
                    }
                    // Transferprotokoll
                    wert = rs.getString("ladeprotokoll");
                    if (wert != null && wert.startsWith(logPath)) {
                        File file = new File(wert);
                        if (!file.exists()) {
                            wert = "fehlt";
                            pwrt.println(indent+indent+"<td>" + wert + "</td>");
                        }
                        else {
                            wert = wert.substring(logPath.length());
                            pwrt.println(indent+indent+"<td><a href=\"" + webServerPrefix + "log/" + html(wert) + "\" target=\"_blank\">öffnen</a></td>");
                        }
                    }
                    else if (wert == null || wert.trim().isEmpty()) {
                        wert = "fehlt";
                        pwrt.println(indent+indent+"<td>" + wert + "</td>");
                    }
                    else {
                        pwrt.println(indent+indent+"<td><font color=\"red\">" + html(wert) + "</font></td>");
                    }
                    // Datenfehler
                    wert = rs.getString("datenfehler");
                    if (wert != null && wert.startsWith(errorExportPath)) {
                        File file = new File(wert);
                        if (!file.exists()) {
                            wert = "fehlt";
                            pwrt.println(indent+indent+"<td>" + wert + "</td>");
                        }
                        else {
                            wert = wert.substring(errorExportPath.length());
                            pwrt.println(indent+indent+"<td><a href=\"" + webServerPrefix + "datenfehler/" + html(wert) + "\" target=\"_blank\">öffnen</a></td>");
                        }
                    }
                    else if (wert == null || wert.trim().isEmpty()) {
                        wert = "&nbsp;";
                        pwrt.println(indent+indent+"<td>" + wert + "</td>");
                    }
                    else {
                        pwrt.println(indent+indent+"<td><font color=\"red\">" + html(wert) + "</font></td>");
                    }
                    // Tabellenzeile beenden
                    pwrt.println(indent+"</tr>");
                }
                rs.close();
                wert = status != 1 ? " Einträge" : " Eintrag";
                pwrt.println(indent+"<tr>");
                pwrt.println(indent+indent+"<td>&nbsp;</td>");
                pwrt.println(indent+indent+"<td><b>" + String.valueOf(status) + wert + "</b></td>");
                pwrt.println(indent+"</tr>");
            }
        }
    }

    /** Verarbeite Request-Parameter: REQUEST
    * @param p Der Parameterwert für request
    */
    private int parseParmRequest(String p)
    {
        int reqtype = 0;
        if (p != null) {
            for (String req : new String[] {"GetThemes","GetTypes","Init","WakeUp","Queue","Areas","Read","FullQueue","DeleteJob","QueueCheck", "ReadLock"} )
            {
                reqtype++; if (p.equalsIgnoreCase(req)) return reqtype;
            }
        }
        return -1;
    }
    

    /** Verarbeite Request-Parameter: DATASOURCE
    * @param p Der Parameterwert für datasource
    */
    private int parseDatasourceRequest(String p) {
        int srctype = 0;
        if (p != null) {
            for (String req : new String[] {"Bestand", "Altdaten", "Planung"} )
            {
                srctype++; if (p.equalsIgnoreCase(req)) return srctype;
            }
        }
        return -1;
    }
    
    private String getQueue(DbVerbindung verb) {
        return DbMetadaten.GibMetadatenTabelle(QUEUE);
    }
    
    private String getLogPath() {
        String logPath = this.getServletConfig().getInitParameter("okstraOklabilog");
        if (logPath == null) {
            logPath = this.getServletContext().getInitParameter("okstraOklabilog");
        }
        if (logPath != null) {
            if (logPath.startsWith("~")) {
                // Angabe relativ zum Deployment-Pfad des Webservice
                logPath = getServletContext().getRealPath("") +
                        File.separator + logPath.replace("~", "");
            }
            erlaubeZugriff(logPath, true);
            return logPath + File.separator;
        }
        logger.error("Protokollverzeichnis ist nicht korrekt angegeben.");
        return "";
    }
    
    private String getReturnPath() {
        String path = this.getServletConfig().getInitParameter("okstraReturnpath");
        if (path == null) {
            path = this.getServletContext().getInitParameter("okstraReturnpath");
        }
        if (path != null) {
            if (path.startsWith("~")) {
                // Angabe relativ zum Deployment-Pfad des Webservice
                path = getServletContext().getRealPath("") +
                        File.separator + path.replace("~", "");
            }
            erlaubeZugriff(path, true);
            return path + File.separator;
        }
        return path;
    }
    
    private String getUploadPath() {
        String path = this.getServletConfig().getInitParameter("okstraUploadpath");
        if (path == null) {
            path = this.getServletContext().getInitParameter("okstraUploadpath");
        }
        if (path != null) {
            if (path.startsWith("~")) {
                // Angabe relativ zum Deployment-Pfad des Webservice
                path = getServletContext().getRealPath("") +
                        File.separator + path.replace("~", "");
            }
            erlaubeZugriff(path, true);
            return path + File.separator;
        }
        return path;
    }
    
    /**
     * Liest das Übergabeverzeichnis für lokale Dateien (okstraTransferpath).
     * @return aufgelöster Pfad oder null, wenn uploadfile nicht freigegeben ist
     */
    private Path getTransferPath() {
        String path = this.getServletConfig().getInitParameter("okstraTransferpath");
        if (path == null) {
            path = this.getServletContext().getInitParameter("okstraTransferpath");
        }
        if (path == null || path.trim().isEmpty()) {
            logger.info("Lokale Dateien (uploadfile) sind nicht freigegeben, Init-Parameter okstraTransferpath fehlt.");
            return null;
        }
        if (path.startsWith("~")) {
            // Angabe relativ zum Deployment-Pfad des Webservice
            path = getServletContext().getRealPath("") + File.separator + path.replace("~", "");
        }
        try {
            Path real = Paths.get(path).toRealPath();
            if (!Files.isDirectory(real)) {
                logger.error("Übergabeverzeichnis (okstraTransferpath) ist kein Verzeichnis: " + path +
                        "; lokale Dateien (uploadfile) werden abgewiesen.");
                return null;
            }
            logger.info("Übergabeverzeichnis für lokale Dateien (uploadfile): " + real);
            return real;
        } catch (IOException | InvalidPathException ex) {
            logger.error("Übergabeverzeichnis (okstraTransferpath) ist nicht verfügbar: " + path +
                    "; lokale Dateien (uploadfile) werden abgewiesen.");
            return null;
        }
    }

    /**
     * Prüft den Parameter uploadfile: Die Datei muss im Übergabeverzeichnis oder
     * darunter liegen; ein relativer Pfad gilt relativ zum Übergabeverzeichnis.
     * Eine zulässige Datei wird mit ihrem aufgelösten Pfad übernommen.
     * @param reqData Anfrage
     * @return Meldung oder Leerstring
     */
    private String pruefeUploadfile(PostRequestContent reqData) {
        String uf = reqData.getItem("uploadfile");
        if (uf == null || uf.trim().isEmpty()) {
            return "";
        }
        if (transferPath == null) {
            return "Lokale Dateien (uploadfile) sind nicht freigegeben (Init-Parameter okstraTransferpath fehlt). " +
                    "Die Anfrage wird abgewiesen.";
        }
        // nicht vorhanden, ungültig oder außerhalb: dieselbe Meldung, damit sich das
        // Vorhandensein von Dateien außerhalb nicht erfragen lässt
        Path real = Uebergabeverzeichnis.aufloesen(transferPath, uf);
        if (real != null) {
            reqData.setzeUploadfile(real.toString());
            return "";
        }
        // ohne Anführungszeichen: die JSON-Antwort maskiert sie nicht richtig
        return "Die Datei " + uf + " liegt nicht im Übergabeverzeichnis oder ist nicht vorhanden. " +
                "Die Anfrage wird abgewiesen.";
    }

    private String getErrorExportPath() {
        String path = this.getServletConfig().getInitParameter("okstraErrorexportpath");
        if (path == null) {
            path = this.getServletContext().getInitParameter("okstraErrorexportpath");
        }
        if (path != null) {
            writeErrorObjects = true;
            if (path.startsWith("~")) {
                // Angabe relativ zum Deployment-Pfad des Webservice
                path = getServletContext().getRealPath("") +
                        File.separator + path.replace("~", "");
            }
            erlaubeZugriff(path, true);
            return path + File.separator;
        }
        writeErrorObjects = false;
        return path;
    }
    
    private String getWebServerPrefix() {
        String prefix = this.getServletConfig().getInitParameter("okstraServerPrefix");
        if (prefix == null) {
            prefix = this.getServletContext().getInitParameter("okstraServerPrefix");
        }
        return prefix;
    }
    
    private String getFileName(String fileName) {
        String fName = "noFile";
        if (fileName != null) {
            File file = new File(fileName);
            if (file.isFile()) {
                fName = file.getName();
                if (fName.contains(".")) {
                    fName = fName.substring(0, fName.lastIndexOf('.'));
                }
            }
        }
        return fName;
    }
    
    private String getLogFileName(String fileName, RequestData reqData, String prefix, String suffix) {
        String fName = null;
        String auftragNummer = "??";
        if (reqData != null && reqData.getAuftrag() > 0) {
            auftragNummer = String.valueOf(reqData.getAuftrag());
            fName = reqData.getOriginalPath();
            if (fName != null && fName.trim().isEmpty()) {
                fName = null;
            }
        }
        if (fName == null) {
            fName = getFileName(fileName);
        }
        if (!fName.startsWith("_")) {
            fName = "_" + fName;
        }
        String oklabiLogPath = getLogPath();
        String logDatei = oklabiLogPath + auftragNummer +
                prefix + fName + "_" + getLogFileDateString() + suffix;
        return logDatei;
    }
    
    private String getErrorFileName(RequestData reqData, String file) {
        String datei = errorExportPath + String.valueOf(reqData.getAuftrag()) +
                "_fehler_" + getLogFileDateString() +
                (file.startsWith("_") ? "" : "_") + file;
        return datei;
    }
    
    /**
     * Eingabeprüfung der Kennungen von Schlüsseltabellen- und Aufzählungseinträgen eines
     * Datenbestands (Zeichenvorrat siehe {@link Kennungen}).
     * @param bestand Datenbestand
     * @param nurObjektId nur die Eigenschaft, aus der OkstraSqlUtil die Objekt-ID neuer
     *        Einträge bildet (erste Kennungs- oder Langtexteigenschaft, die keine Relation
     *        ist); sonst alle Kennungseigenschaften
     * @return Meldungen, je Objektart, Eigenschaft und Wert eine, höchstens 100; leer, wenn
     *         alle Kennungen zulässig sind
     */
    /**
     * Behelf für die OKLABI (bis einschließlich 1.6.5.0): Beim Übertragen in die Datenbank
     * erhalten Kreisbögen und Splines in mehrteiligen Linien die Höhe 0, weil die OKLABI sie
     * im WKT der ganzen Linie immer zweidimensional interpoliert. Solche Linien werden vorher
     * aus dem WKT ihrer Komponenten neu gesetzt; dort stimmen die Höhen. Andere Geometrien
     * bleiben unverändert, ebenso Linien, bei denen beide WKT gleich sind.
     * @param bestand Datenbestand vor der Übertragung
     * @return Anzahl neu gesetzter Linien
     */
    private int korrigiereBogenhoehen(Datenbestand bestand) throws Exception {
        Map<String, List<String>> geoEigenschaften = new HashMap<>();
        int anzahl = 0;
        Enumerator en = bestand.GibEnumerator();
        try {
            for (; !en.IstBeendet(); en.Weiter()) {
                Fachobjekt fObj = (Fachobjekt) en.GibObjekt();
                Objektart oa = fObj.GibObjektart();
                if (!oa.HatGeometrie(eGeoTyp.Linie)) {
                    continue;
                }
                List<String> namen = geoEigenschaften.get(oa.GibName());
                if (namen == null) {
                    namen = new ArrayList<>();
                    for (Eigenschaft eig : oa.GibEigenschaften(true)) {
                        if (eTyp.geoType.equals(eig.GibTyp())) {
                            namen.add(eig.GibName());
                        }
                    }
                    geoEigenschaften.put(oa.GibName(), namen);
                }
                for (String name : namen) {
                    AnyType any = fObj.Gib(name);
                    if (any == null || any.IstUnbelegt() || !eTyp.geoType.equals(any.GibTyp())) {
                        continue;
                    }
                    if (any.GibAnzahl() > 1) {
                        for (int i = 0; i < any.GibAnzahl(); i++) {
                            if (setzeAusKomponenten((Geometrie) any.GibBei(i))) {
                                anzahl++;
                            }
                        }
                    } else if (setzeAusKomponenten((Geometrie) any.Gib())) {
                        anzahl++;
                    }
                }
            }
        } finally {
            en.Vernichte();
        }
        if (anzahl > 0) {
            Db2Okstra.protokolliere(-1, "Höhen in " + anzahl + " Linie(n) mit Kreisbögen oder Splines"
                    + " aus den Komponenten übernommen (Behelf für die OKLABI)",
                    eMeldTyp.info, eProtokollTyp.meldungen);
        }
        return anzahl;
    }

    private static boolean setzeAusKomponenten(Geometrie geo) throws Exception {
        if (geo == null || geo.IstLeer() || !eGeoTyp.Linie.equals(geo.GibGeometrietyp())
                || geo.GibAnzahlKomponenten() < 2) {
            return false;
        }
        List<String> teile = new ArrayList<>();
        for (Geometrie k : geo.GibKomponenten()) {
            teile.add(k.GibWKT());
        }
        String neu = Linienkomponenten.zusammensetzen(teile);
        if (neu == null || neu.equals(geo.GibWKT())) {
            return false;
        }
        geo.SetzeWKT(neu);
        return true;
    }

    private List<String> pruefeKennungen(Datenbestand bestand, boolean nurObjektId) throws Exception {
        List<String> meldungen = new ArrayList<>();
        Set<String> gemeldet = new HashSet<>();
        Map<String, List<String>> eigenschaften = new HashMap<>();
        int anzahl = 0;
        Enumerator en = bestand.GibEnumerator();
        try {
            for (; !en.IstBeendet(); en.Weiter()) {
                Fachobjekt fObj = (Fachobjekt) en.GibObjekt();
                Objektart oa = fObj.GibObjektart();
                if (!oa.IstSchluesseltabelle() && !oa.IstAufzaehlung()) {
                    continue;
                }
                List<String> namen = eigenschaften.get(oa.GibName());
                if (namen == null) {
                    namen = new ArrayList<>();
                    for (Eigenschaft eig : oa.GibEigenschaften(true)) {
                        if (eig.GibTyp().equals(eTyp.objectType) && !eig.IstInvers()) {
                            continue;
                        }
                        if (nurObjektId) {
                            if (eig.IstKennung() || eig.IstLangtext()) {
                                namen.add(eig.GibName());
                                break;
                            }
                        } else if (eig.IstKennung()) {
                            namen.add(eig.GibName());
                        }
                    }
                    eigenschaften.put(oa.GibName(), namen);
                }
                for (String name : namen) {
                    AnyType any = fObj.Gib(name);
                    if (any == null || any.IstUnbelegt() || !eTyp.stringType.equals(any.GibTyp())) {
                        continue;
                    }
                    List<String> werte = new ArrayList<>();
                    if (any.GibAnzahl() > 1) {
                        for (int i = 0; i < any.GibAnzahl(); i++) {
                            werte.add(String.valueOf(any.GibBei(i)));
                        }
                    } else {
                        werte.add((String) any.Gib());
                    }
                    for (String wert : werte) {
                        if (!Kennungen.zulaessig(wert) && gemeldet.add(oa.GibName() + "|" + name + "|" + wert)) {
                            if (++anzahl <= 100) {
                                meldungen.add(Kennungen.meldung(oa.GibName(), name, wert));
                            }
                        }
                    }
                }
            }
        } finally {
            en.Vernichte();
        }
        if (anzahl > 100) {
            meldungen.add("Weitere unzulässige Kennungen: " + (anzahl - 100) + ".");
        }
        return meldungen;
    }

    private HashMap<eMetadaten,String> analyzeFile(String fileName, RequestData reqData, boolean load, boolean check, boolean migrate, Set<String> fehlerIds, HttpServletResponse response) throws ServletException, IOException {

        DateiAusgabe protDatei = null;
        DateiProtokollant protokoll = null;
        Datenbestand bestand = null;
        Datenbestand migriert = null;
        Enumerator enumer = null;
        Fachbedeutungsliste fbliste = null;
        boolean bError = false;
        boolean bMigration = false;
        try {
            String sessid = reqData != null ? reqData.getSessionId() : "";
            String logDatei = getLogFileName(fileName, reqData, (load ? "_laden" : "_pruefen"), ".log");
            if (load || check) {
                JavaProtokollant.erzeugeProtokoll(logger, logDatei);
                erlaubeZugriff(logDatei, false);
            }

            // Datei mit Oklabi lesen
            bestand = liesOkstra(fileName, reqData, load);
            if (bestand == null) {
                if (response != null) {
                    response.getWriter().println("Fehler beim Lesen der OKSTRA-Eingabedaten");
                }
                return null;
            }
            reqData.setDatenbestand(bestand);
            
            boolean bInvalid = false;
            String msg = "";
            String delim = "";
            if (check && reqData.getStatus() < 100) {
                // Koordinatenreferenzsystem und Höhensystem prüfen
                eKoordRefSys ksys = bestand.GibKoordRefSys();
                reqData.setSrsBestand(ksys);
                if (eKoordRefSys.Verschieden.equals(ksys) ||
                    (eKoordRefSys.Undefiniert.equals(ksys) &&
                     eKoordRefSys.Undefiniert.equals(reqData.getSrs()))) {
                    bInvalid = true;
                    msg = "Es ist kein gültiges Koordinatenreferenzsystem im Datenbestand bekannt oder als Parameter angegeben.";
                    delim = " ";
                } else if (!Umgebung.GibTransformierer().IstUnterstuetzt(reqData.getSrs())) {
                    msg = delim + "Das angegebene Koordinatenreferenzsystem " + 
                            Umgebung.GibTextZuWert(eAufzaehlungen.tKoordRefSys, reqData.getSrs().GibCode()) +
                            "  wird nicht unterstützt.";
                    bInvalid = true;
                    delim = " ";
                } else {
                    if (eKoordRefSys.Undefiniert.equals(ksys)) {
                        msg = "Die Initialisierung des Datenbestandes in das Koordinatenreferenzsystem " +
                                Umgebung.GibTextZuWert(eAufzaehlungen.tKoordRefSys, reqData.getSrs().GibCode()) +
                                " ist erforderlich.";
                        delim = " ";
                    }
                }
                eKoordRefSys hsys = bestand.GibHoehensystem();
                reqData.setHsBestand(hsys);
                if (eKoordRefSys.Verschieden.equals(hsys) ||
                    (eKoordRefSys.Undefiniert.equals(hsys) &&
                     eKoordRefSys.Undefiniert.equals(reqData.getHs()))) {
                    msg += delim + "Es ist kein gültiges Höhensystem im Datenbestand bekannt oder als Parameter angegeben.";
                    bInvalid = true;
                } else if (!Umgebung.GibTransformierer().IstUnterstuetzt(reqData.getHs())) {
                    msg += delim + "Das angegebene Höhensystem " + 
                            Umgebung.GibTextZuWert(eAufzaehlungen.tKoordRefSys, reqData.getHs().GibCode()) +
                            "  wird nicht unterstützt.";
                    bInvalid = true;
                } else {
                    if (eKoordRefSys.Verschieden.equals(hsys) ||
                        eKoordRefSys.Undefiniert.equals(hsys)) {
                        msg = "Die Initialisierung des Datenbestandes in das Höhensystem " +
                                Umgebung.GibTextZuWert(eAufzaehlungen.tKoordRefSys, reqData.getHs().GibCode()) +
                                " ist erforderlich.";
                    }
                }
                if (bInvalid) {
                    reqData.setPruefStatus(false);
                    Db2Okstra.protokolliere(-1, msg, eMeldTyp.warnung, eProtokollTyp.meldungen);
                    if (response != null) {
                        response.getWriter().println(msg);
                    }
                    reqData.setStatus(reqData.getStatus() + 500);
                    manageQueue(reqData.getAuftrag(), false, reqData, "status", reqData.getStatus());
                    reqData.setSrsError(true);
                } else if (!msg.isEmpty()) {
                    Db2Okstra.protokolliere(-1, msg, eMeldTyp.warnung, eProtokollTyp.meldungen);
                }
            }
            
            // Datenbestand prüfen wenn verlangt
            if (check && reqData.getStatus() < 100) {
                FblVersion fbv = (reqData.getFbListeAusDatei()) ? bestand.GibFblVersion() : reqData.getFblVersion();
                if (fbv != null)
                    fbliste = Fachbedeutungsliste.Erzeuge(fbv);
                boolean bCheckFb = true;
                if (fbliste == null) {
                    reqData.setPruefStatus(false);
                    Db2Okstra.protokolliere(-1,
                            "Die Gültigkeit von Fachbdedeutungen kann nicht geprüft werden, ungültige Listenangabe.");
                    bCheckFb = false;
                } else {
                    String fbCheck = (String)Umgebung.GibEinstellung(eEinstellung.pFbFehlerbehandlung);
                    if (fbCheck.contains(":ohneSymbolbedeutung")) {
                        Db2Okstra.protokolliere(-1,
                                "Die Gültigkeit von Fachbdedeutungen an Fachobjekten der Objektart Symbol wird nicht geprüft.");
                    }
                    if (fbCheck.contains(":ohneTextbedeutung")) {
                        Db2Okstra.protokolliere(-1,
                                "Die Gültigkeit von Fachbdedeutungen an Fachobjekten der Objektart Beschriftung wird nicht geprüft.");
                    }
                }
                enumer = bestand.GibEnumerator();
                boolean state = true;
                while (!enumer.IstBeendet()) {
                    Fachobjekt fObj = (Fachobjekt)enumer.GibObjekt();
                    boolean bValid = fObj.IstGueltig();
                    if (bValid) {
                        // Vielleicht wurde eine diagnostische Meldung ausgegeben?
                        if (JavaProtokollant.befrageProtokoll("V"))
                            bValid = false;
                    }
                    if (bCheckFb && fObj.GibObjektart().HatFachbedeutung()) {
                        boolean bFbState = fObj.HatGueltigeFachbedeutung(fbliste);
                        if (bFbState) {
                            // Vielleicht wurde eine diagnostische Meldung ausgegeben?
                            if (JavaProtokollant.befrageProtokoll("V"))
                                bFbState = false;
                        }
                        if (!fbAusschluss.isEmpty()) {
                            String typ;
                            String att = "fachliche_Bedeutung";
                            switch (fObj.GibObjektartname()) {
                                case "allgemeines_Punktobjekt":
                                    typ = "Punkt";
                                    break;
                                case "allgemeines_Linienobjekt":
                                    typ = "Linie";
                                    break;
                                case "allgemeines_Flaechenobjekt":
                                    typ = "Flaeche";
                                    break;
                                case "allgemeines_Volumenobjekt":
                                    typ = "Volumen";
                                    break;
                                case "Symbol":
                                    typ = "Punkt";
                                    att = "Symbolbedeutung";
                                    break;
                                case "Beschriftung":
                                    typ = "Text";
                                    att = "Textbedeutung";
                                    break;
                                default:
                                    typ = null;
                                    break;
                            }
                            if (typ != null) {
                                AnyType any = fObj.Gib(att);
                                if (any != null && (any.GibAnzahl() == 1) && any.GibTyp().equals(eTyp.stringType)) {
                                    String key = (String)any.Gib();
                                    if (fbAusschluss.contains(key + "::" + typ)) {
                                        Db2Okstra.protokolliere(-1, "Fachbedeutungskennung " + key +
                                                " ist explizit ausgeschlossen für Objektart " + fObj.GibObjektartname(),
                                                eMeldTyp.warnung, eProtokollTyp.meldungen);
                                        bFbState = false;
                                    }
                                }
                            }
                        }
                        bValid &= bFbState;
                    }
                    if (!bValid) {
                        state = false;
                        if (fehlerIds != null) {
                            try {
                                AnyType any = fObj.Gib(FoIdentifizierer.getInternId());
                                String objId = (String)any.Gib();
                                fehlerIds.add(objId);
                            }
                            catch (Ausnahme ex) {
                                Db2Okstra.protokolliere(-1, "Fehler bei Zugriff auf interne ID eines Fachobjektes: " +
                                        ex.getMessage(), eMeldTyp.warnung, eProtokollTyp.meldungen);
                            }
                        }
                    }
                    enumer.Weiter();
                }
                if (!state) {
                    reqData.setPruefStatus(state);
                    Db2Okstra.protokolliere(-1, "Es gab Fehler bei der Prüfung der Daten.");
                }
                enumer.Vernichte();
                enumer = null;
                if (fbliste != null) {
                    fbliste.Vernichte();
                    fbliste = null;
                }
                // Eingabeprüfung der Kennungen von Schlüsseltabellen und Aufzählungen
                List<String> unzulaessig = pruefeKennungen(bestand, false);
                if (!unzulaessig.isEmpty()) {
                    for (String m : unzulaessig) {
                        Db2Okstra.protokolliere(-1, m, eMeldTyp.fehler, eProtokollTyp.meldungen);
                    }
                    Db2Okstra.protokolliere(-1, "Die Lieferung wird wegen unzulässiger Kennungen abgelehnt.",
                            eMeldTyp.fehler, eProtokollTyp.meldungen);
                    reqData.setPruefStatus(false);
                    reqData.setKennungenAbgelehnt(true);
                }
                manageQueue(reqData.getAuftrag(), false, reqData, "pruefprotokoll", logDatei);
                reqData.setStatus(reqData.getStatus() + 100);
                manageQueue(reqData.getAuftrag(), false, reqData, "status", reqData.getStatus());
                copyLogFile(sessid, logDatei);
            }
            if (!bInvalid && migrate && !reqData.getKennungenAbgelehnt()) {
                if (!reqData.getVersion().IstGleich(reqData.getDbVersion())) {
                    if (load || check) {
                        JavaProtokollant.entferneProtokoll();
                    }
                    reqData.setStatus(reqData.getStatus() + 1000);
                    manageQueue(reqData.getAuftrag(), false, reqData, "status", reqData.getStatus());
                    logDatei = getLogFileName(fileName, reqData, "_migration", ".log");
                    JavaProtokollant.erzeugeProtokoll(logger, logDatei);
                    erlaubeZugriff(logDatei, false);
                    bMigration = true;
                    migriert = Datenbestand.Erzeuge(reqData.getDbVersion());
                    Datenbestand.Migriere(bestand, reqData.getDbVersion(), migriert);
                    reqData.setDatenbestand(migriert);
                    bestand.Vernichte();
                    bestand = migriert;
                    migriert = null;
                    manageQueue(reqData.getAuftrag(), false, reqData, "migrationprotokoll", logDatei);
                    reqData.setStatus(reqData.getStatus() + 1000);
                    manageQueue(reqData.getAuftrag(), false, reqData, "status", reqData.getStatus());
                    copyLogFile(sessid, logDatei);
                }
                // Das übergebene Koordinatenreferenzsystem und Höhensystem im Datenbestand eintragen
                // Es wird initialisiert wo noch keine Festlegung vorliegt, sonst transformiert
                eKoordRefSys eSrs = reqData.getSrs();
                if (reqData.getSrsBestand().equals(eKoordRefSys.Undefiniert)) {
                    reqData.setSrsBestand(bestand.GibKoordRefSys());
                }
                if (!eKoordRefSys.Undefiniert.equals(eSrs)) {
                    if (reqData.getSrsBestand().equals(eKoordRefSys.Undefiniert)) {
                        bestand.InitialisiereKoordRefSys(eSrs);
                    } else {
                        bestand.Transformiere(eSrs);
                    }
                }
                eKoordRefSys eHs = reqData.getHs();
                if (reqData.getHsBestand().equals(eKoordRefSys.Undefiniert)) {
                    reqData.setHsBestand(bestand.GibHoehensystem());
                }
                if (!eKoordRefSys.Undefiniert.equals(eHs)) {
                    if (reqData.getHsBestand().equals(eKoordRefSys.Undefiniert)) {
                        bestand.InitialisiereHoehensystem(eHs);
                    } else {
                        bestand.Transformiere(eHs);
                    }
                }
                if (eHs.GibCode() != HSYS) {
                    // Höhentransformation in das Höhensystem der Datenbank ausführen
                    Db2Okstra.protokolliere(-1, "Die Transformation des Datenbestandes in das Höhensystem " +
                            Umgebung.GibTextZuWert(eAufzaehlungen.tKoordRefSys, HSYS) +
                            " wird ausgeführt.", eMeldTyp.info, eProtokollTyp.meldungen);
                    eKoordRefSys eDbHs = eKoordRefSys.getEnum(HSYS);
                    bestand.Transformiere(eDbHs);
                }
                reqData.setStatus(reqData.getStatus() + 10000);
                manageQueue(reqData.getAuftrag(), false, reqData, "status", reqData.getStatus());
            }
        } catch (Exception ex) {
            bError = true;
            logger.error(null, ex);
            try {
                Db2Okstra.protokolliere(-1, "Fehler beim Transfer von OKSTRA-Daten in die Datenbank, Meldung: " + ex.getMessage(), eMeldTyp.fehler, eProtokollTyp.meldungen);
            }
            catch (Ausnahme aus) {                
            }
            if (response != null) {
                response.getWriter().println("Fehler beim Transfer von OKSTRA-Daten in die Datenbank");
            }
        } finally {
            // Ressourcen aufräumen
            try {
                if (enumer != null) {
                    enumer.Vernichte();
                }
                if (fbliste != null) {
                    fbliste.Vernichte();
                }
                if (bError && bestand != null) {
                    Datenbestand domain = (reqData != null) ? reqData.getDatenbestand() : null;
                    if (domain != null && domain.Vergleiche(bestand)) {
                        // Datenbestand im Request beseitigen, wird beseitigt
                        // Sonst wird später versucht, diesen zu laden
                        reqData.setDatenbestand(null);
                    }
                    bestand.Vernichte();
                }
                if (bError && migriert != null) {
                    migriert.Vernichte();
                }
                if (bError || !load) {
                    if (protokoll != null) {
                       Db2Okstra.protokolliere(-1, "Ende des Protokolls.", eMeldTyp.info, eProtokollTyp.meldungen);
                       Umgebung.Registriere((DateiProtokollant)null, eProtokollTyp.meldungen);
                       protokoll.Vernichte();
                    }
                    if (protDatei != null) {
                       protDatei.Schliesse();
                       protDatei.Vernichte();
                    }
                }
                if (load || check || bMigration) {
                    JavaProtokollant.entferneProtokoll();
                }
            } catch (Exception ex) {
                logger.error(ex.getMessage(), ex);
            }
        }
        return reqData != null ? reqData.getMetadata() : null;
    }
    
    private String getLogFileName(String sessid, String name) throws Exception {
        String fName = "";
        if (sessid != null && name != null &&
            !sessid.isEmpty() && !name.isEmpty()) {
            String path = returnPath + sessid;
            File returnDir = new File(path);
            if (!returnDir.exists()) {
                Files.createDirectory(returnDir.toPath());
            }
            File logf = new File(path + File.separator + name);
            fName = logf.getAbsolutePath();
            erlaubeZugriff(path, true);
        }
        return fName;
    }
    
    private void writeStateFile(String sessid, int status, String diagnose) throws Exception {
        // Werte in status:
        // 1       fertig, alles klar
        // 0       nicht fertig, arbeitet
        // sonst   fertig, Fehlschlag
        if (sessid != null && !sessid.isEmpty()) {
            String path = returnPath + sessid;
            File returnDir = new File(path);
            if (!returnDir.exists()) {
                Files.createDirectory(returnDir.toPath());
            }
            File state = new File(path + "/erledigt.txt");
            BufferedWriter bw = new BufferedWriter(new FileWriter(state));
            bw.write(status == 1 ? "success" : (status == 0 ? "working" : "failed"));
            if (diagnose != null && !diagnose.isEmpty()) {
                bw.newLine();
                bw.write(diagnose);
            }
            bw.flush();
            bw.close();
        }
    }
    
    private void copyLogFile(String sessid, String logDatei) throws Exception
    {
        if (!sessid.isEmpty()) {
            File returnDir = new File(returnPath + sessid);
            if (!returnDir.exists()) {
                Files.createDirectory(returnDir.toPath());
            }
            File logg = new File(logDatei);
            File logFile = new File(returnDir + File.separator + logg.getName());
            if (logg.exists() && returnDir.exists() && returnDir.isDirectory()) {
                Files.copy(logg.toPath(), logFile.toPath());
            }
        }
    }
    
    // Das ist ein Hack! Solange nicht klar ist, welcher Charset benutzt wird.
    // Annahme: sobald eines der Suchmuster auftritt, liegt Windowss-1252 vor,
    // dann wir in UTF-8 transkodiert
    // Muster entspricht diesen Zeichen: äöüÄÖÜß§€²³¦¨´¸¼½¾‰
    final static String[] PATTERNS = new String[] { 
        "Ã¤", "Ã¶", "Ã¼", "Ã„", "Ã–", "Ãœ", "ÃŸ", "Â§", "â‚¬", "Â²", "Â³", "Â¦", "Â¨", "Â´", "Â¸", "Â¼", "Â½", "Â¾", "â€°" };
    final static Charset WIN1252 = Charset.forName("windows-1252");
    private String fixName(String name) {
        for (String t : PATTERNS) {
            if (name.contains(t)) {
                return new String(name.getBytes(WIN1252), StandardCharsets.UTF_8);
            }
        }
        return name;
    }
    
    /**
     * Prüft wie bisher Commons FileUpload, ob eine Anfrage Multipart ist: POST mit
     * einem Inhaltstyp, der mit "multipart/" beginnt.
     */
    private static boolean istMultipart(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ENGLISH).startsWith("multipart/");
    }

    /**
     * Zeichensatz aus dem Parameter charset eines Content-Type wie bei Commons
     * FileUpload: ohne Angabe die Vorgabe, bei unbekanntem Zeichensatz der der JVM.
     */
    private static Charset zeichensatz(String contentType, Charset vorgabe) {
        if (contentType != null) {
            for (String param : contentType.split(";")) {
                String[] nameWert = param.trim().split("=", 2);
                if (nameWert.length == 2 && "charset".equalsIgnoreCase(nameWert[0].trim())) {
                    try {
                        return Charset.forName(nameWert[1].trim().replace("\"", ""));
                    } catch (IllegalArgumentException ex) {
                        return Charset.defaultCharset();
                    }
                }
            }
        }
        return vorgabe;
    }

    /**
     * Liest den Wert eines Formularfelds wie bisher Commons FileUpload: im
     * Zeichensatz aus dem Content-Type des Teils, ohne Angabe als ISO-8859-1.
     * UTF-8-Werte stellt danach fixName richtig.
     */
    private static String liesFormularfeld(Part part) throws IOException {
        Charset charset = zeichensatz(part.getContentType(), StandardCharsets.ISO_8859_1);
        try (InputStream in = part.getInputStream()) {
            return new String(in.readAllBytes(), charset);
        }
    }

    /**
     * Liefert den Dateinamen eines Teils wie bisher Commons FileUpload: Die
     * Kopfzeilen eines Teils liest es im Zeichensatz der Anfrage und, wenn der
     * Client keinen angibt, im Zeichensatz der JVM. Der Servlet-Container liest sie
     * im Request-Zeichensatz der web.xml (ISO-8859-1); das ist verlustfrei und wird
     * hier umgekehrt.
     */
    private static String liesDateiname(HttpServletRequest request, Part part) {
        Charset charset = zeichensatz(request.getContentType(), Charset.defaultCharset());
        return new String(part.getSubmittedFileName().getBytes(StandardCharsets.ISO_8859_1), charset);
    }

    /**
     * Eingabeprüfung des Formats einer Lieferung ({@link Lieferformat}): CTE wird abgewiesen.
     * Nur bei einer Lieferung (hochgeladene Datei oder {@code uploadfile}) und nur, wenn noch
     * kein anderer Fehler vorliegt.
     */
    private void pruefeFormat(PostRequestContent reqData) {
        boolean lieferung = !reqData.getFilePath().isEmpty() || reqData.getUploadExists();
        if (!lieferung || !reqData.getExceptionMsg().isEmpty()) {
            return;
        }
        String name = reqData.getItem("f2");
        if (name == null || name.isEmpty()) {
            name = reqData.getUploadExists() ? reqData.getItem("uploadfile") : reqData.getFilePath();
        }
        String fehler = Lieferformat.pruefe(reqData.getItem("f1"), name);
        if (!fehler.isEmpty()) {
            reqData.setExceptionMsg(fehler);
            logger.error(fehler);
        }
    }

    private PostRequestContent fileUpload(HttpServletRequest request) {
        PostRequestContent reqData = new PostRequestContent();
        // Default-Datenbankverbindungsparameter eintragen
        reqData.addConnection(okstraConnection, vermdbConnection, dbVersion, okstraVersion);
        boolean multipart = istMultipart(request);
        if (!multipart) {
            if (reqData.getIstAltdaten() && reqData.getIstPlanung()) {
                String msg = "Die verlangte Datenbankverbindung ist mehrdeutig angegeben. " +
                        "Die Anfrage wird abgewiesen.";
                reqData.setExceptionMsg(msg);
                logger.error(msg);
            } else if (reqData.getIstAltdaten()) {
                if (haveAltdaten()) {
                    // Altdatenverbindung wird gewünscht
                    reqData.addConnection(okstraAltConnection, vermdbAltConnection, dbVersionAltdaten, okstraVersionAltdaten);
                }
                else {
                    String msg = "Die verlangte Datenbankverbindung zu den Altdaten existiert nicht oder ist unvollständig. " +
                            "Die Anfrage wird abgewiesen.";
                    reqData.setExceptionMsg(msg);
                    logger.error(msg);
                }
            } else if (reqData.getIstPlanung()) {
                if (havePlanung()) {
                    // Planungdatenverbindung wird gewünscht
                    reqData.addConnection(okstraPlanungConnection, vermdbPlanungConnection, dbVersionPlanung, okstraVersionPlanung);
                }
                else {
                    String msg = "Die verlangte Datenbankverbindung zu den Planungsdaten existiert nicht oder ist unvollständig. " +
                            "Die Anfrage wird abgewiesen.";
                    reqData.setExceptionMsg(msg);
                    logger.error(msg);
                }
            }
            Map<String, String[]> tbl = request.getParameterMap();
            for (Entry<String, String[]> entry : tbl.entrySet()) {
                String[] val = entry.getValue();
                reqData.setItem(entry.getKey(), (val != null && val.length > 0 ? val[0] : ""));
            }
            String fehler = pruefeUploadfile(reqData);
            if (!fehler.isEmpty()) {
                reqData.setExceptionMsg(fehler);
                logger.error(fehler);
                return reqData;
            }
            // Parameterzusammenstellung prüfen
            TreeMap<String, String> parms = new TreeMap<>();
            String msg = reqData.checkParameters(parms);
            if (!msg.isEmpty()) {
                String message = reqData.getExceptionMsg();
                if (message != null && message.isEmpty()) {
                    reqData.setExceptionMsg(message + "\n" + msg);
                } else {
                    reqData.setExceptionMsg(msg);
                }
                logger.error(msg);
            }
            pruefeFormat(reqData);
            return reqData;
        }
        
        // Die Grenzen für Datei, Anfrage und Zwischenspeicher auf der Platte setzt der
        // Servlet-Container nach <multipart-config> bzw. @MultipartConfig
        try {
            // wenn es das Verzeichnis noch nicht gibt, dann legen wir es an
            File returnDir = new File(returnPath);
            if (!returnDir.exists()) {
                boolean mkdir = returnDir.mkdir();
                if (!mkdir) {
                    throw new IOException("Verzeichnis konnte nicht angelegt werden: " + returnPath);
                }
            }

            File uploadDir = new File(uploadPath);
            if (!uploadDir.exists()) {
                boolean mkdir = uploadDir.mkdir();
                if (!mkdir) {
                    throw new IOException("Verzeichnis konnte nicht angelegt werden: " + uploadPath);
                }
            }

            // Request parsen und die einzelnen Teile des Multipart-Requests holen
            Collection<Part> formItems = request.getParts();

            // über die Einzelteile laufen und die Dateien abspeichern.
            // Ein Teil ohne Dateinamen ist ein Formularfeld.
            for (Part item : formItems) {
                // Im ersten Durchgang die Form-Felder lesen
                if (item.getSubmittedFileName() == null) {
                    reqData.setItem(item.getName(), fixName(liesFormularfeld(item)));
                }
            }
            // Session-ID und uploadfile prüfen, bevor eine Datei geschrieben wird
            String fehler = reqData.hasItem("SID", true)
                    ? PostRequestContent.pruefeSessionId(reqData.getItem("SID")) : "";
            if (fehler.isEmpty()) {
                fehler = pruefeUploadfile(reqData);
            }
            if (!fehler.isEmpty()) {
                reqData.setExceptionMsg(fehler);
                logger.error(fehler);
                return reqData;
            }
            String prefix = reqData.getItem("SID") + "_";
            for (Part item : formItems) {
                // Im zweiten Duchgang die Dateien lesen
                if (item.getSubmittedFileName() != null) {
                    String fileName = new File(liesDateiname(request, item)).getName();
                    fileName = fixName(fileName);
                    reqData.setItem(item.getName(), fileName);
                    // Prüfen: fileName kann leer sein (leeres Form)
                    if (!fileName.isEmpty()) {
                        String filePath = uploadPath + File.separator + prefix + fileName;
                        reqData.setFilePath(filePath);
                        // Datei auf die Platte schreiben. Absolut, weil der Container
                        // einen relativen Pfad auf sein temporäres Verzeichnis bezieht
                        item.write(new File(filePath).getAbsolutePath());
                    }
                }
            }
            String uf = reqData.getItem("uploadfile");
            boolean uploadExists = reqData.getUploadExists();
            if (reqData.getFilePath().isEmpty()) {
                // Keine Payload mitgeliefert?
                if (!uploadExists) {
                    String msg = "Es wurden keine Daten hochgeladen. " +
                            "Die Anfrage wird abgewiesen.";
                    reqData.setExceptionMsg(msg);
                    logger.error(msg);
                }
            } else {
                // Payload mitgeliefert, Uploadfile wurde angegeben: melden
                if (uploadExists)
                    Db2Okstra.protokolliere(-1, "Bei einem Upload-Request liegt eine mehrdeutige Angabe für die Datenquelle vor. " +
                            " Die lokale vorliegende Upload-Datei wird benutzt, die hochgeladene Datei wird ignoriert.", eMeldTyp.info, eProtokollTyp.meldungen);
            }
            if (reqData.getIstAltdaten() && reqData.getIstPlanung()) {
                String msg = "Die verlangte Datenbankverbindung ist mehrdeutig angegeben. " +
                        "Die Anfrage wird abgewiesen.";
                reqData.setExceptionMsg(msg);
                logger.error(msg);
            } else if (reqData.getIstAltdaten()) {
                if (haveAltdaten()) {
                    // Altdatenverbindung wird gewünscht
                    reqData.addConnection(okstraAltConnection, vermdbAltConnection, dbVersionAltdaten, okstraVersionAltdaten);
                }
                else {
                    String msg = "Die verlangte Datenbankverbindung zu den Altdaten existiert nicht oder ist unvollständig. " +
                            "Die Anfrage wird abgewiesen.";
                    reqData.setExceptionMsg(msg);
                    logger.error(msg);
                }
            } else if (reqData.getIstPlanung()) {
                if (havePlanung()) {
                    // Planungdatenverbindung wird gewünscht
                    reqData.addConnection(okstraPlanungConnection, vermdbPlanungConnection, dbVersionPlanung, okstraVersionPlanung);
                }
                else {
                    String msg = "Die verlangte Datenbankverbindung zu den Planungsdaten existiert nicht oder ist unvollständig. " +
                            "Die Anfrage wird abgewiesen.";
                    reqData.setExceptionMsg(msg);
                    logger.error(msg);
                }
            }
            // Parameterzusammenstellung prüfen
            TreeMap<String, String> parms = new TreeMap<>();
            String msg = reqData.checkParameters(parms);
            if (!msg.isEmpty()) {
                String message = reqData.getExceptionMsg();
                if (message != null && message.isEmpty()) {
                    reqData.setExceptionMsg(message + "\n" + msg);
                } else {
                    reqData.setExceptionMsg(msg);
                }
                logger.error(msg);
            }
            pruefeFormat(reqData);
            logger.info("Anfrage verarbeitet.");
        } catch (IOException | ServletException | IllegalStateException ex) {
            // IllegalStateException: Grenze aus <multipart-config> überschritten
            reqData.setExceptionMsg(ex.getMessage());
            logger.error("Fehler beim Upload.", ex);
        } catch (Exception ex) {
            reqData.setExceptionMsg(ex.getMessage());
            logger.error("Fehler.", ex);
        }
        return reqData;
    }

    /**
     * Liest die Versionsinformation aus der Datei ein, lädt wenn verlangt
     * @param name
     * @param reqData
     * @param doLoad
     * @return Datenbestand
     */
    public Datenbestand liesOkstra(String name, RequestData reqData, boolean doLoad) {
        synchronized (this) {
            DateiEingabe inp = null;
            XMLEingabeKonverter xcv = null;
            EingabeKonverter cv = null;
            Version vers;
            Datenbestand bestand = null;
            try {
                // Existenz der Datei prüfen
                if (reqData != null) {
                    reqData.setExceptionMsg("");
                    reqData.setVersion(null);
                    if (!doLoad) {
                        reqData.setMetadata(null);
                    }
                }
                File file = name != null ? new File(name) : null;
                if (name == null || file == null || !file.exists()) {
                    if (reqData != null) {
                        reqData.setExceptionMsg("Datei " + (name != null ? name : "<fehlt>" ) + " existiert nicht.");
                    }
                    return null;
                }

                // EingabeKonverter auf DateiEingabe erzeugen
                inp = DateiEingabe.Erzeuge(name);
                String format = "";
                if (reqData != null) {
                    format = reqData.getFormat();
                }
                // Ohne Angabe nach der Endung; bisher wurde auch .xml als CTE gelesen und
                // eine andere Endung abgewiesen. CTE weist die Eingabeprüfung beim Einstellen
                // ab (Lieferformat); hier kommt es nur noch aus älteren Aufträgen an.
                format = Lieferformat.bestimme(format, name);
                if (Lieferformat.CTE.equals(format)) {
                    cv = CTEEingabeKonverter.Erzeuge(inp);
                }
                else {
                    xcv = XMLEingabeKonverter.Erzeuge(inp);
                    cv = xcv;
                }

                // Die Version lesen
                vers = cv.GibVersion();
                if (vers != null && reqData != null) {
                    reqData.setVersion(vers);
                }
                if (vers == null || vers.GibFachdatenmodell() == null) {
                    if (reqData != null) {
                        reqData.setExceptionMsg("Die OKSTRA-Version kann nicht bestimmt werden.");
                    }
                    return null;
                }
                else if (!"OKSTRA".equals(vers.GibFachdatenmodell().GibName())) {
                    if (reqData != null) {
                        reqData.setExceptionMsg("Es können nur OKSTRA-Daten verarbeitet werden.");
                    }
                    return null;
                }
                else {
                    String versText = vers.GibText();
                    if (versText.contains("?")) {
                        if (reqData != null) {
                            reqData.setExceptionMsg("In den Daten ist keine gültige OKSTRA-Version enthalten.");
                        }
                        return null;
                    }
                    else if (!okstraVersionRead.contains("|" + versText + "|")) {
                        if (reqData != null) {
                            reqData.setExceptionMsg("Daten zur OKSTRA-Version " + versText +
                                " können nicht gelesen werden.");
                        }
                        return null;
                    }
                }

                if (xcv != null) {
                    // Aus dem Fachdatenmodell die lokale SchemaLocation berechnen.
                    // Es gibt da OKSTRA-Garstigkeiten ...
                    String locSchema = vers.GibFachdatenmodell().GibEigenschaft(
                            eModelleigenschaft.eXmlLocalSchemaLocation, vers);
                    if (new File(locSchema).exists()) {
                        locSchema = vers.GibFachdatenmodell().GibEigenschaft(
                            eModelleigenschaft.eXmlNamespace, vers) + " " + locSchema.replace(" ", "%20");
                        xcv.SetzeSchemaLocation(locSchema);
                    }
                    else {
                        logger.warn("Achtung! Lokales Schema existiert nicht: " + locSchema);
                    }
                }

                // Metadaten auslesen
                if (!doLoad) {
                    HashMap<eMetadaten,String> metadata = new HashMap<>();
                    // Alle Metadaten auslesen und in Map packen
                    String t;
                    Set<Integer> codes = eMetadaten.author.GibAlleCodes();
                    for (Integer code : codes) {
                        try {
                            eMetadaten nxt = eMetadaten.getEnum(code);
                            String txt = Umgebung.GibTextZuWert(eAufzaehlungen.tMetadaten, code);
                            List<String> mdata = cv.GibMetadaten(nxt);
                            // Einträge können multipel sein
                            if (mdata != null && !mdata.isEmpty()) {
                                for (String str : mdata) {
                                    logger.info("Metadaten   " + txt + ": " + str);
                                    if (!nxt.equals(eMetadaten.schema_identifiers)) {
                                        if (metadata.containsKey(nxt)) {
                                            str = metadata.get(nxt) + "\n" + str;
                                        }
                                        metadata.put(nxt, str);
                                    } else if (str.startsWith("Fachbedeutung")) {
                                        // Bei schema_identifiers nur die Fachbedeutungen mitnehmen
                                        // OKSTRA-Version und eine Profil-Widmung können auch auftreten
                                        metadata.put(nxt, str);
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            t = ex.getMessage();
                        }
                    }
                    if (reqData != null) {
                        reqData.setMetadata(metadata);
                    }

                    logger.info("Metadaten ausgelesen...");
                }

                // Datenbestand einlesen und abliefern. Am längsten dauert oft die Validierung der Datei!
                if (doLoad) {
                    logger.debug("Lade Daten...");
                    bestand = cv.Lade();
                    logger.debug("Laden beendet. Anzahl geladener Objekte: " + bestand.GibAnzahl());
                }
                else {
                    bestand = Datenbestand.Erzeuge(vers);
                }
                cv.Vernichte();
                inp.Vernichte();
                return bestand;
            } catch (Ausnahme ex) {
                try {
                    String msg = ex.GibMeldungstext();
                    if (reqData != null) {
                        reqData.setExceptionMsg(msg);
                    }
                    logger.error(msg, ex);
                    if (bestand != null) {
                        bestand.Vernichte();
                    }
                    if (cv != null) {
                        cv.Vernichte();
                    }
                    if (inp != null) {
                        inp.Vernichte();
                    }
                } catch (Ausnahme inner) {
                    logger.error(inner.getMessage(), inner);
                }
                return null;
            }
        }
    }

    /**
     * Erzeugt einen Zeitstempel
     * @return Zeitstempel
     */
    public static String getLogFileDateString() {
        return Okstra2Db.formatDate(new Date(), "yyyyMMdd_HHmmss");
    }

    /**
     * Führt ein SQL-Statement aus, das eine Liste aus Integer erzeugt
     * @param bAsLong Werte als Long interpretieren
     * @param bWrapString Ergebnisse als Text darstellen
     * @param verb Datenbankverbindung
     * @param sql SQL-Statement
     * @return Liste aus Integer im Textformat
     * @throws Exception 
     */
    private String getIntegers(boolean bAsLong, boolean bWrapString, DbVerbindung verb, String sql) throws Exception
    {
        ResultSet rs = verb.PGDatenLesen(sql, null);
        int status = 0;
        long value;
        StringBuilder res = new StringBuilder();
        String delim = "";
        String wrap = bWrapString ? "'" : "";
        if (rs != null) {
            while (rs.next())
            {
                if (bAsLong)
                    value = rs.getLong(1);
                else
                    value = (long)rs.getInt(1);
                res.append(String.format("%s%s%d%s", delim, wrap, value, wrap));
                delim = ", ";
                status++;
            }
            rs.close();
        } else
            throw new Exception("SQL-Befehl \"" + sql + "\": Fehler bei Datenbankzugriff.");
        return res.toString();
    }

}