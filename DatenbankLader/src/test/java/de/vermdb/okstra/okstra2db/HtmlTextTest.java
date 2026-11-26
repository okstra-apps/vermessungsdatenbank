// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.okstra2db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HtmlTextTest {

    @Test
    void umlauteAlsEntitaeten() {
        assertEquals("&Auml;u&szlig;eres &uuml;ber &Ouml;l &Uuml; &auml; &ouml;",
                HtmlText.umlaute("Äußeres über Öl Ü ä ö"));
        // nur die Umlaute, kein Markup
        assertEquals("<b>&amp;", HtmlText.umlaute("<b>&amp;"));
    }

    @Test
    void maskiereMarkupUndAnfuehrungszeichen() {
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", HtmlText.maskiere("<script>alert(1)</script>"));
        assertEquals("Probe O&#39;Neill &lt;b&gt;fett&lt;/b&gt;", HtmlText.maskiere("Probe O'Neill <b>fett</b>"));
        assertEquals("a=&quot;1&quot; &amp;amp;", HtmlText.maskiere("a=\"1\" &amp;"));
        assertEquals("&lt;unbekannt&gt;", HtmlText.maskiere("<unbekannt>"));
    }

    @Test
    void maskiereUmlauteNachDemMarkup() {
        // & wird zuerst maskiert, die Entitäten der Umlaute bleiben dadurch intakt
        assertEquals("Pr&uuml;fdatei &amp; &Auml;", HtmlText.maskiere("Prüfdatei & Ä"));
    }

    @Test
    void maskiereNullWieBisher() {
        assertEquals("null", HtmlText.maskiere(null));
    }
}
