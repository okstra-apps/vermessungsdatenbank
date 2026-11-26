// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.integration;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Inhalt einer OKSTRA-XML-Datei, unabhängig von Reihenfolge und internen Kennungen: je
 * Fachobjekt eine Zeile mit Objektart, Eigenschaften, Verweisen (als Objektart des Ziels)
 * und Koordinaten (auf Millimeter gerundet), sortiert. OKSTRA-IDs bleiben außen vor.
 */
final class Inhalt {

    static final String GML = "http://www.opengis.net/gml/3.2";
    static final String XLINK = "http://www.w3.org/1999/xlink";

    private final Document dokument;
    private final Map<String, String> artNachId = new HashMap<>();
    private final List<Element> objekte = new ArrayList<>();

    private Inhalt(byte[] xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setExpandEntityReferences(false);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dokument = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new AssertionError("keine wohlgeformte XML-Datei: " + e.getMessage(), e);
        }
        for (Node n = dokument.getDocumentElement().getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && "featureMember".equals(e.getLocalName())) {
                for (Node c = e.getFirstChild(); c != null; c = c.getNextSibling()) {
                    if (c instanceof Element o) {
                        objekte.add(o);
                        artNachId.put(o.getAttributeNS(GML, "id"), o.getLocalName());
                    }
                }
            }
        }
    }

    /** Liest eine Datei; scheitert, wenn sie kein wohlgeformtes XML ist. */
    static Inhalt von(byte[] xml) {
        return new Inhalt(xml);
    }

    /** Namensraum des Wurzelelements, etwa {@code http://www.okstra.de/okstra/2.022}. */
    String okstraNamensraum() {
        String ns = dokument.getDocumentElement().getAttribute("xmlns:okstra");
        return ns;
    }

    /** Anzahl der Fachobjekte je Objektart. */
    Map<String, Integer> objektarten() {
        Map<String, Integer> m = new TreeMap<>();
        for (Element o : objekte) {
            m.merge(o.getLocalName(), 1, Integer::sum);
        }
        return m;
    }

    int anzahl() {
        return objekte.size();
    }

    /** Werte aller {@code okstra:Text}, sortiert. */
    List<String> texte() {
        List<String> l = new ArrayList<>();
        for (Element o : objekte) {
            for (Element t : elemente(o, "Text")) {
                l.add(t.getTextContent());
            }
        }
        l.sort(null);
        return l;
    }

    /** Alle Koordinaten (x, y, z; z = NaN ohne Höhe). */
    List<double[]> punkte() {
        List<double[]> l = new ArrayList<>();
        for (Element o : objekte) {
            sammlePunkte(o, l);
        }
        return l;
    }

    /** Werte des Attributs {@code srsName} in der Datei. */
    List<String> lagesysteme() {
        List<String> l = new ArrayList<>();
        var alle = dokument.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < alle.getLength(); i++) {
            String s = ((Element) alle.item(i)).getAttribute("srsName");
            if (!s.isEmpty() && !l.contains(s)) {
                l.add(s);
            }
        }
        return l;
    }

    /** Zusammenfassung: Anzahl je Objektart, dann je Fachobjekt eine Zeile, sortiert. */
    String zusammenfassung() {
        StringBuilder sb = new StringBuilder();
        sb.append("Namensraum: ").append(okstraNamensraum()).append('\n');
        sb.append("Lagesysteme: ").append(String.join(", ", lagesysteme())).append('\n');
        objektarten().forEach((k, v) -> sb.append(k).append(": ").append(v).append('\n'));
        sb.append('\n');
        List<String> zeilen = new ArrayList<>();
        for (Element o : objekte) {
            StringBuilder z = new StringBuilder(o.getLocalName());
            beschreibe(o, "", z);
            zeilen.add(z.toString());
        }
        zeilen.sort(null);
        zeilen.forEach(z -> sb.append(z).append('\n'));
        return sb.toString();
    }

    private void beschreibe(Element e, String pfad, StringBuilder z) {
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element c)) {
                continue;
            }
            String name = c.getLocalName();
            if ("OKSTRA_ID".equals(name)) {
                continue;
            }
            String p = pfad.isEmpty() ? name : pfad + "/" + name;
            String href = c.getAttributeNS(XLINK, "href");
            if (!href.isEmpty()) {
                String ziel = artNachId.getOrDefault(href.startsWith("#") ? href.substring(1) : href, "?");
                z.append(" | ").append(p).append("→").append(ziel);
            } else if (GML.equals(firstElementNamespace(c))) {
                List<double[]> l = new ArrayList<>();
                sammlePunkte(c, l);
                z.append(" | ").append(p).append("=[");
                for (int i = 0; i < l.size(); i++) {
                    z.append(i == 0 ? "" : ", ").append(format(l.get(i)));
                }
                z.append(']');
            } else if (hatKindelemente(c)) {
                beschreibe(c, p, z);
            } else {
                String uom = c.getAttribute("uom");
                z.append(" | ").append(p).append('=').append(c.getTextContent().strip())
                        .append(uom.isEmpty() ? "" : " " + uom);
            }
        }
    }

    private static String firstElementNamespace(Element e) {
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element c) {
                return c.getNamespaceURI();
            }
        }
        return null;
    }

    private static boolean hatKindelemente(Element e) {
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) {
                return true;
            }
        }
        return false;
    }

    private static void sammlePunkte(Element e, List<double[]> l) {
        String name = e.getLocalName();
        if (GML.equals(e.getNamespaceURI()) && ("pos".equals(name) || "posList".equals(name))) {
            String dim = e.getAttribute("srsDimension");
            int d = dim.isEmpty() ? 2 : Integer.parseInt(dim);
            String[] t = e.getTextContent().trim().split("\\s+");
            for (int i = 0; i + d - 1 < t.length; i += d) {
                l.add(new double[] {Double.parseDouble(t[i]), Double.parseDouble(t[i + 1]),
                        d > 2 ? Double.parseDouble(t[i + 2]) : Double.NaN});
            }
            return;
        }
        if (GML.equals(e.getNamespaceURI()) && "coordinates".equals(name)) {
            for (String tupel : e.getTextContent().trim().split("\\s+")) {
                String[] t = tupel.split(",");
                l.add(new double[] {Double.parseDouble(t[0]), Double.parseDouble(t[1]),
                        t.length > 2 ? Double.parseDouble(t[2]) : Double.NaN});
            }
            return;
        }
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element c) {
                sammlePunkte(c, l);
            }
        }
    }

    private static List<Element> elemente(Element e, String name) {
        List<Element> l = new ArrayList<>();
        var nl = e.getElementsByTagNameNS("*", name);
        for (int i = 0; i < nl.getLength(); i++) {
            l.add((Element) nl.item(i));
        }
        return l;
    }

    static String format(double[] p) {
        return Double.isNaN(p[2])
                ? String.format(Locale.ROOT, "%.3f %.3f", p[0], p[1])
                : String.format(Locale.ROOT, "%.3f %.3f %.3f", p[0], p[1], p[2]);
    }

    /**
     * Größte Abweichung (Lage, Höhe) von Punkten zum jeweils nächsten Punkt einer anderen
     * Menge.
     */
    static double[] abweichung(List<double[]> von, List<double[]> zu) {
        double lage = 0;
        double hoehe = 0;
        for (double[] p : von) {
            double best = Double.MAX_VALUE;
            double[] q = null;
            for (double[] c : zu) {
                double dz = Double.isNaN(p[2]) || Double.isNaN(c[2]) ? 0 : p[2] - c[2];
                double d = (c[0] - p[0]) * (c[0] - p[0]) + (c[1] - p[1]) * (c[1] - p[1]) + dz * dz;
                if (d < best) {
                    best = d;
                    q = c;
                }
            }
            if (q != null) {
                lage = Math.max(lage, Math.hypot(q[0] - p[0], q[1] - p[1]));
                if (!Double.isNaN(p[2]) && !Double.isNaN(q[2])) {
                    hoehe = Math.max(hoehe, Math.abs(q[2] - p[2]));
                }
            }
        }
        return new double[] {lage, hoehe};
    }
}
