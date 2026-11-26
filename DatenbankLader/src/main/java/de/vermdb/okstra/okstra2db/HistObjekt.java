/*
 * SPDX-FileCopyrightText: 2015-2026 interactive instruments GmbH
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package de.vermdb.okstra.okstra2db;

import de.okstra.sql.util.DbMetadaten;
import de.okstra.sql.util.DbVerbindung;
import java.sql.Date;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Organisiert die Kette von verschiedenen Zuständen
 * eines Fachobjekt
 * @author Vogelsang
 * @version 2015-04-01
 * 
 * 2018-11-19 Multischemabetrieb
 */
public class HistObjekt {
    private String oid;
    private String objektart;
    private String guid;
    private Date von;
    private Date bis;
    private String[] vor;
    private String[] nach;
    
    public String getOid() {
        return oid;
    }
    
    public String getObjektart() {
        return objektart;
    }
    
    public String getGuid() {
        return guid;
    }
    
    public Date getVon() {
        return von;
    }
    
    public void setVon(Date value) {
        von = value;
    }
    
    public Date getBis() {
        return bis;
    }
    
    public String getBisText() {
        return (bis == null) ? "7510-12-31" : bis.toString();
    }
    
    public void setBis(Date value) {
        bis = value;
    }
    
    public String[] getVor() {
        return vor;
    }
    
    public void setVor(String[] value) {
        vor = null;
        if (value != null) {
            int anz = 0;
            for (String s : value) {
                if (s == null || s.isEmpty() || s.equalsIgnoreCase("NULL")) {
                    continue;
                }
                ++anz;
            }
            if (anz > 0) {
                vor = new String[anz];
                anz = 0;
                for (String s : value) {
                    if (s == null || s.isEmpty() || s.equalsIgnoreCase("NULL")) {
                        continue;
                    }
                    vor[anz++] = s;
                }
            }
        }
    }
    
    public boolean findVor(String id) {
        if (vor != null) {
            for (String s : vor) {
                if (s.equals(id)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public String[] getNach() {
        return nach;
    }
    
    public void setNach(String[] value) {
        nach = null;
        if (value != null) {
            int anz = 0;
            for (String s : value) {
                if (s == null || s.isEmpty() || s.equalsIgnoreCase("NULL")) {
                    continue;
                }
                ++anz;
            }
            if (anz > 0) {
                nach = new String[anz];
                anz = 0;
                for (String s : value) {
                    if (s == null || s.isEmpty() || s.equalsIgnoreCase("NULL")) {
                        continue;
                    }
                    nach[anz++] = s;
                }
            }
        }
    }
    
    public boolean findNach(String id) {
        if (nach != null) {
            for (String s : nach) {
                if (s.equals(id)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public HistObjekt(String id, String oa, String g) {
        oid = id;
        objektart = oa;
        guid = g;
    }
    
    static public List<HistObjekt> gibObjekte(DbVerbindung dbv, String idListe) throws Exception {
        Set<String> oidSet = new HashSet<>();
        ArrayList<HistObjekt> res = new ArrayList<>();
        if (idListe != null && !idListe.isEmpty()) {
            DbVerbindung verb = DbVerbindung.ErzeugePGVerbindung(dbv);
            ResultSet rs = null;
            DbMetadaten meta = verb.getDbMetadaten();
            String header = DbMetadaten.GibTabelle(meta, DbVerbindung.getKopfdaten());
            String fromto = DbMetadaten.GibTabelle(meta, DbVerbindung.getVonNach());
            try {
                String[] oids = idListe.split(";");
                for (String oid : oids) {
                    String toid = oid.trim();
                    if (toid.isEmpty() || oidSet.contains(toid)) {
                        continue;
                    }
                    try {
                        // nur prüfen: Long.parseLong lässt nur Ziffern und ein Vorzeichen zu
                        SqlWert.ganzzahl(toid);
                    } catch (NumberFormatException ex) {
                        // keine Objektkennung; wie bisher, als die Abfrage daran scheiterte:
                        // nicht gefunden
                        continue;
                    }
                    oidSet.add(toid);
                    int anz = 0;
                    String sqlstmt = String.format(
                       "SELECT DISTINCT h.klassenname, h.guid, h.gueltigvon, h.gueltigbis, " +
                       "array_agg(f1.nach::Text) as nach, " +
                       "array_agg(f2.von::Text) as von, h.foid FROM %s as h " +
                       "LEFT JOIN %s f1 ON f1.von = %s " +
                       "LEFT JOIN %s f2 ON f2.nach = %s " +
                       "WHERE foid=%s GROUP BY f2.nach, f1.von, h.klassenname, " +
                       "h.guid, h.gueltigvon, h.gueltigbis, h.foid;",
                       header, fromto, toid, fromto, toid, toid);
                    rs = dbv.PGDatenLesen(sqlstmt, null);
                    if (rs != null) {
                        while (rs.next()) {
                            ++anz;
                            if (anz == 1) {
                                String oa = rs.getString(1);
                                String g  = rs.getString(2);
                                HistObjekt ho = new HistObjekt(toid, oa, g);
                                Date dvon = rs.getDate(3);
                                Date dbis = rs.getDate(4);
                                Object n = rs.getObject(5);
                                String[] na = (String[])(rs.getArray(5).getArray());
                                Object v = rs.getObject(6);
                                String[] va = (String[])(rs.getArray(6).getArray());
                                ho.setVon(dvon);
                                ho.setBis(dbis);
                                ho.setNach(na);
                                ho.setVor(va);
                                res.add(ho);
                            }
                        }
                        rs.close();
                        rs = null;
                    }
                }
            }
            catch (Exception ex) {
                throw new Exception("Fehler beim Lesen von Daten: " + ex.getMessage());
            }
            finally {
                if (rs != null && !rs.isClosed())
                    rs.close();
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
        }
        return res;
    }
}
