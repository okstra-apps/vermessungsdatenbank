// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.okstra2db;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prüft die Parameter der web.xml auf Platzhalter für Umgebungsvariablen, die der Tomcat
 * nicht ersetzt hat. Ersetzt werden {@code ${NAME}} und {@code ${NAME:-Vorgabe}} nur,
 * wenn der Tomcat mit
 * {@code -Dorg.apache.tomcat.util.digester.PROPERTY_SOURCE=org.apache.tomcat.util.digester.EnvironmentPropertySource}
 * läuft; eine fehlende Variable ohne Vorgabe bleibt wörtlich stehen.
 */
final class Platzhalter {

    private static final Pattern PLATZHALTER = Pattern.compile("\\$\\{[^}]*\\}");

    private Platzhalter() {
    }

    /**
     * @param parameter Name und Wert der Parameter
     * @return je Parameter mit nicht ersetztem Platzhalter ein Eintrag „Name (Platzhalter)“
     */
    static List<String> offene(Map<String, String> parameter) {
        List<String> offen = new ArrayList<>();
        for (Map.Entry<String, String> e : parameter.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            Matcher m = PLATZHALTER.matcher(e.getValue());
            List<String> gefunden = new ArrayList<>();
            while (m.find()) {
                gefunden.add(m.group());
            }
            if (!gefunden.isEmpty()) {
                offen.add(e.getKey() + " (" + String.join(", ", gefunden) + ")");
            }
        }
        return offen;
    }

    /**
     * @param offen Ergebnis von {@link #offene(Map)}, nicht leer
     * @return Meldung für den Abbruch beim Start
     */
    static String meldung(List<String> offen) {
        return "Die web.xml enthält nicht ersetzte Platzhalter für Umgebungsvariablen: "
                + String.join("; ", offen) + ". Die Umgebungsvariablen sind nicht gesetzt, oder der Tomcat"
                + " läuft ohne -Dorg.apache.tomcat.util.digester.PROPERTY_SOURCE="
                + "org.apache.tomcat.util.digester.EnvironmentPropertySource.";
    }
}
