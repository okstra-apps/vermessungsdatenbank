/*
 * SPDX-FileCopyrightText: 2014-2026 interactive instruments GmbH
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package de.vermdb.okstra.okstra2db;

import de.okstra.oklabi.Ausnahme;
import de.okstra.oklabi.Protokollant;
import de.okstra.oklabi.eMeldTyp;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;

/**
 * Verteilt die Meldungen anhand der Threads in dem
 * sie ausgegeben werden.
 * @author Vogelsang
 * @version 2014-11-25
 * 2021-03-08 Kanalnummer für Protokollant
 * 2023-01-09 Log4j Brücke 1->2 eingebaut
 */
public class JavaProtokollant extends Protokollant {
    private Logger logger = null;
    
    private BufferedWriter getLogger(long threadId) {
        if (mapLogger.containsKey(threadId)) {
            return mapLogger.get(threadId);
        }
        return null;
    }
    
    private final static Map<Long, BufferedWriter> mapLogger = new HashMap<>();
    private final static Map<Long, Long> mapValidator = new HashMap<>();
    private static JavaProtokollant protokoll = null;
    
    /**
     * Erzeugt Protokollanten
     * @param lo
     * @param file
     * @return Protokollant
     * @throws Exception
     */
    public static Protokollant erzeugeProtokoll(Logger lo, String file) throws Exception {
        synchronized (mapLogger) {
            long threadId = Thread.currentThread().getId();
            if (protokoll == null) {
                protokoll = new JavaProtokollant(lo);
                protokoll.SetzeKanalnummer((int)threadId);
            }
            if (mapLogger.containsKey(threadId)) {
                throw new Exception("Fehler beim Anlegen eines Protokolls.");
            }
            if (file != null && !file.isEmpty()) {
                File fi = new File(file);
                BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(fi)));
                mapLogger.put(threadId, w);
                Okstra2DbServlet.erlaubeZugriff(file, false);
            }
            return protokoll;
        }
    }
    
    /**
     * Entfernt Protokollanten
     * @throws Exception
     */
    public static synchronized void entferneProtokoll() throws Exception {
        long threadId = Thread.currentThread().getId();
        synchronized (mapLogger) {
            if (mapLogger != null && mapLogger.containsKey(threadId)) {
                try (BufferedWriter w = mapLogger.get(threadId)) {
                    w.flush();
                }
                mapLogger.remove(threadId);
            }
            if (mapValidator != null && mapValidator.containsKey(threadId)) {
                mapValidator.remove(threadId);
            }
        }
    }
    
    /**
     * Befragt Protokollanten
     * @param pattern Suchtyp
     * @return Validierungsfehler-Flagge
     * @throws Exception
     */
    public static boolean befrageProtokoll(String pattern) throws Exception {
        synchronized (mapLogger) {
            long threadId = Thread.currentThread().getId();
            if (mapValidator != null && mapValidator.containsKey(threadId)) {
                Long entry = mapValidator.get(threadId);
                if (("V".equals(pattern) && ((entry & 1) != 0))) {
                    mapValidator.put(threadId, (entry-1));
                    return true;
                }
            }
            return false;
        }
    }
    
    private JavaProtokollant(long id) { super(id); }
    private JavaProtokollant(Logger lo) throws Exception {
        logger = lo;
    }
    
    /**
     * Gibt Meldung aus, verteilt an den passenden Logger
     * @param nr
     * @param text
     * @param typ
     * @throws Ausnahme
     */
    @Override
    public void Melde(int nr, String text, eMeldTyp typ) throws Ausnahme {
        long threadId = Thread.currentThread().getId();
        BufferedWriter logg = getLogger(threadId);
        if (logg == null && protokoll != null) {
            logg = getLogger((long)protokoll.GibKanalnummer());
        }
        try {
            if (logg != null) {
                // Ausgabe in Protokoll, dass zum Thread gehört
                logg.write(text);
                logg.newLine();
                logg.flush();
            }
        }
        catch (IOException ex) {
            // Whoops...
            if (logger != null) {
                logger.warn("Fehler bei Ausgabe in Protokoll in Thread " + String.valueOf(threadId));
            }
        }
        if (logger != null) {
            // Ausgabe ins Service-Protokoll, Thread-Id mit ausgeben
            synchronized (mapLogger) {
                if (nr != -1) {
                    long entry = 0;
                    if (text != null) {
                        if (text.startsWith("Validieren:")) {
                            entry |= 1;
                        }
                    }
                    mapValidator.put(threadId, entry);
                }
            }
            String formatted = String.format("Thread %d: %s", threadId, text);
            if (eMeldTyp.abbruch.equals(typ)) {
                logger.error(formatted);
            } else if (eMeldTyp.debug.equals(typ)) {
                logger.debug(formatted);
            } else if (eMeldTyp.fehler.equals(typ)) {
                logger.error(formatted);
            } else if (eMeldTyp.info.equals(typ)) {
                logger.info(formatted);
            } else if (eMeldTyp.logging.equals(typ)) {
                logger.info(formatted);
            } else if (eMeldTyp.statistik.equals(typ)) {
                logger.debug(formatted);
            } else if (eMeldTyp.warnung.equals(typ)) {
                logger.warn(formatted);
            }
        }
    }
    
    /**
     * Schliesst den Protokollanten.
     */
    public void Schliesse() {
    }
    
}
