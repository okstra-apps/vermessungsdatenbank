// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Die Installation aus {@code Container/compose.yaml} mit leerer Datenbank, gestartet einmal
 * für alle Tests. Die Compose-Datei wird in ein eigenes Verzeichnis kopiert, das zugleich
 * Konfigurationsverzeichnis ist ({@code VERMDB_KONFIG}), damit eine lokale
 * {@code Container/.env} die Tests nicht beeinflusst. Die {@code .env} dort schaltet das
 * Gitter {@link #GITTER} des Image ein, wie in einer Installation in Sachsen-Anhalt (die
 * Testdaten sind in 42/83 Gauß-Krüger 4); eigene Gitterdateien gibt es nicht. Als eigene
 * Fachbedeutungsliste liegt {@link #EIGENE_LISTE} bereit,
 * eine Kopie der Liste Sachsen-Anhalt 3.5 des Release.
 */
final class Umgebung {

    static final String SERVLET = "/Okstra2Db/Okstra2DbServlet";

    /** Gitter des Image, das die {@code .env} der Tests einschaltet (VERMDB_GITTER_IMAGE) */
    static final String GITTER = "NTv2LSBB_LSA";

    /** Eigene Fachbedeutungsliste im Konfigurationsverzeichnis: Sachsen-Anhalt „3.6“ */
    static final String EIGENE_LISTE = "fb-Sachsen-Anhalt-36.csv";

    private static Umgebung instanz;

    private final Path repository = Path.of(System.getProperty("vermdb.repository"));
    private final Path arbeitsverzeichnis = Path.of(System.getProperty("vermdb.arbeitsverzeichnis"));
    private final String bild = System.getProperty("vermdb.bild", "vermdb:integrationstest");
    private final String variante = System.getProperty("vermdb.oklabiVariante", "release");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private ComposeContainer compose;
    private String basis;

    private Umgebung() {
    }

    /** Die gestartete Installation. */
    static synchronized Umgebung get() {
        if (instanz == null) {
            Umgebung u = new Umgebung();
            u.starte();
            Runtime.getRuntime().addShutdownHook(new Thread(u::stoppe));
            instanz = u;
        }
        return instanz;
    }

    private void starte() {
        if (!bild.startsWith("vermdb:")) {
            throw new IllegalArgumentException("Das Image muss vermdb:<tag> heißen (compose.yaml): " + bild);
        }
        try {
            Path verzeichnis = arbeitsverzeichnis.resolve("compose");
            if (Files.exists(verzeichnis)) {
                try (Stream<Path> s = Files.walk(verzeichnis)) {
                    for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(p);
                    }
                }
            }
            Files.createDirectories(verzeichnis.resolve("gitter"));
            Files.writeString(verzeichnis.resolve(".env"), "VERMDB_GITTER_IMAGE=" + GITTER + "\n");
            Files.createDirectories(verzeichnis.resolve("fbliste"));
            eigeneListeAnlegen(verzeichnis.resolve("fbliste").resolve(EIGENE_LISTE));
            Files.copy(repository.resolve("Container/compose.yaml"), verzeichnis.resolve("compose.yaml"));
            compose = new ComposeContainer("vermdbit", verzeichnis.resolve("compose.yaml").toFile())
                    .withEnv(Map.of(
                            "VERMDB_TAG", bild.substring("vermdb:".length()),
                            "VERMDB_PORT", "127.0.0.1:0",
                            "VERMDB_KONFIG", verzeichnis.toString(),
                            // Datenverzeichnis als Volume, das mit der Installation gelöscht wird
                            "VERMDB_DATEN", "daten"))
                    .withExposedService("vermdb-1", 8080,
                            Wait.forHealthcheck().withStartupTimeout(Duration.ofMinutes(5)))
                    .withRemoveVolumes(true)
                    .withStartupTimeout(Duration.ofMinutes(10));
            compose.start();
            basis = "http://" + compose.getServiceHost("vermdb-1", 8080) + ":"
                    + compose.getServicePort("vermdb-1", 8080);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Kopiert die Liste Sachsen-Anhalt 3.5 des Release aus dem Image nach {@code ziel}. */
    private void eigeneListeAnlegen(Path ziel) {
        try (GenericContainer<?> c = new GenericContainer<>(DockerImageName.parse(bild))
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("true"))
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(2)))) {
            c.start();
            c.copyFileFromContainer("/opt/OKLABI_HOME/fbliste-release/fb-Sachsen-Anhalt-35.csv", ziel.toString());
        }
    }

    /** Protokoll (Standardausgabe) des Dienstes {@code vermdb}. */
    String protokoll() {
        return compose.getContainerByServiceName("vermdb-1")
                .orElseThrow(() -> new IllegalStateException("Dienst vermdb nicht gefunden")).getLogs();
    }

    private void stoppe() {
        if (compose != null) {
            compose.stop();
        }
    }

    /** OKLABI-Variante des getesteten Baus ({@code intern} oder {@code release}). */
    String variante() {
        return variante;
    }

    /** Datei aus {@code testdaten/}. */
    Path testdatei(String name) {
        return repository.resolve("testdaten").resolve(name);
    }

    Path repository() {
        return repository;
    }

    Path arbeitsverzeichnis() {
        return arbeitsverzeichnis;
    }

    /**
     * Name des Docker-Netzes der Installation; darin ist der Tomcat unter dem Namen des
     * Dienstes {@code vermdb} erreichbar (Port 8080).
     */
    String netz() {
        ContainerState vermdb = compose.getContainerByServiceName("vermdb-1")
                .orElseThrow(() -> new IllegalStateException("Dienst vermdb nicht gefunden"));
        return vermdb.getContainerInfo().getNetworkSettings().getNetworks().keySet().iterator().next();
    }

    // ------------------------------------------------------------------ HTTP

    /** GET an das Servlet; Parameter in der Reihenfolge Name, Wert, Name, Wert … */
    Antwort get(String... parameter) {
        return abrufen(SERVLET + "?" + kodiere(paare(parameter)));
    }

    /** GET eines Pfads unterhalb des Tomcat, etwa {@code /vermdb/log/…}. */
    Antwort abrufen(String pfad) {
        return sende(HttpRequest.newBuilder(URI.create(basis + pfad)).GET());
    }

    /** POST als Formular ({@code application/x-www-form-urlencoded}). */
    Antwort formular(Map<String, String> felder) {
        return sende(HttpRequest.newBuilder(URI.create(basis + SERVLET))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(kodiere(felder))));
    }

    /** POST als {@code multipart/form-data}, Felder in ISO-8859-1, mit einer Datei. */
    Antwort multipart(Map<String, String> felder, Path datei) {
        String grenze = "----vermdb" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try {
            for (Map.Entry<String, String> e : felder.entrySet()) {
                body.write(("--" + grenze + "\r\nContent-Disposition: form-data; name=\"" + e.getKey()
                        + "\"\r\n\r\n" + e.getValue() + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            }
            if (datei != null) {
                body.write(("--" + grenze + "\r\nContent-Disposition: form-data; name=\"datei\"; filename=\""
                        + datei.getFileName() + "\"\r\nContent-Type: text/xml\r\n\r\n")
                        .getBytes(StandardCharsets.ISO_8859_1));
                body.write(Files.readAllBytes(datei));
                body.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
            }
            body.write(("--" + grenze + "--\r\n").getBytes(StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sende(HttpRequest.newBuilder(URI.create(basis + SERVLET))
                .header("Content-Type", "multipart/form-data; boundary=" + grenze)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())));
    }

    private Antwort sende(HttpRequest.Builder anfrage) {
        try {
            HttpResponse<byte[]> r = http.send(anfrage.timeout(Duration.ofMinutes(3)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            return new Antwort(r.statusCode(), r.headers().firstValue("Content-Type").orElse(""), r.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> paare(String... parameter) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < parameter.length; i += 2) {
            m.put(parameter[i], parameter[i + 1]);
        }
        return m;
    }

    private static String kodiere(Map<String, String> felder) {
        return felder.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.ISO_8859_1) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.ISO_8859_1))
                .collect(Collectors.joining("&"));
    }

    // ------------------------------------------------------------------ Aufträge

    /**
     * Stellt eine Datei ein (Prüf- oder Ladeauftrag) und wartet auf den Abschluss.
     * @param sid Session-ID, zugleich Projektname {@code P<sid>}
     * @param datei Datei aus {@code testdaten/}
     * @param nurPruefen Prüfauftrag statt Ladeauftrag
     * @param abweichend Felder, die von den Vorgaben abweichen (f1, f22, f23 …)
     * @return Auftragsnummer
     */
    int liefere(String sid, String datei, boolean nurPruefen, String... abweichend) {
        Map<String, String> felder = new LinkedHashMap<>();
        felder.put(nurPruefen ? "nurPruefen" : "pruefeDatei", "1");
        felder.put("SID", sid);
        felder.put("TS", "2026-11-12-12-00-00");
        felder.put("PRJNM", "P" + sid);
        felder.put("f1", datei.endsWith(".cte") ? "CTE" : "XML");
        felder.put("f2", datei);
        felder.put("f4", "11.11.2026");
        felder.put("f7", "3.5");
        felder.put("f8", "Sachsen-Anhalt");
        felder.put("f20", "laden");
        felder.put("f22", "2398");
        felder.put("f23", "170");
        felder.put("username", "test");
        felder.putAll(paare(abweichend));
        Antwort a = multipart(felder, testdatei(datei));
        assertEquals(200, a.status(), a.text());
        assertTrue(a.erfolg(), a.text());
        int nr = a.auftragsnummer();
        warteAufAbschluss(nr, sid);
        return nr;
    }

    /**
     * Wartet, bis ein Auftrag abgeschlossen ist: Status abgeschlossen (≥ 1.000.000) oder
     * negativ, {@code beendet} gesetzt, {@code bearbeitung} zurückgesetzt und
     * {@code erledigt.txt} nicht mehr {@code working}. {@code erledigt.txt} allein genügt
     * nicht: Status und Bearbeitungsflagge werden teils erst danach gesetzt, bis dahin zeigt
     * die Auftragsliste „übertragen“.
     */
    void warteAufAbschluss(int auftrag, String sid) {
        long ende = System.nanoTime() + Duration.ofMinutes(5).toNanos();
        while (System.nanoTime() < ende) {
            String z = sql("select status, beendet is not null and not coalesce(bearbeitung, false)"
                    + " from oks_meta.warteschlange where auftragsnummer = " + auftrag);
            String[] t = z.split("\\|");
            if (t.length == 2) {
                int status = Integer.parseInt(t[0].trim());
                boolean fertig = "t".equals(t[1].trim());
                if ((status < 0 || status >= 1_000_000) && fertig
                        && !erledigt(sid).startsWith("working")) {
                    return;
                }
            }
            pause(1000);
        }
        fail("Auftrag " + auftrag + " ist nicht abgeschlossen: " + zeile(auftrag));
    }

    /** Inhalt von {@code erledigt.txt} der SID, leer, wenn es sie (noch) nicht gibt. */
    String erledigt(String sid) {
        Antwort a = abrufen("/vermdb/download/" + sid + "/erledigt.txt");
        return a.status() == 200 ? a.text().trim() : "";
    }

    /** Wartet auf ein asynchrones Entladen und liefert {@code erledigt.txt}. */
    String warteAufEntladen(String sid) {
        long ende = System.nanoTime() + Duration.ofMinutes(5).toNanos();
        while (System.nanoTime() < ende) {
            String e = erledigt(sid);
            if (!e.isEmpty() && !e.startsWith("working")) {
                return e;
            }
            pause(1000);
        }
        return fail("Entladen für " + sid + " nicht abgeschlossen");
    }

    /** Numerischer Status in der Warteschlange. */
    int status(int auftrag) {
        return Integer.parseInt(sql("select status from oks_meta.warteschlange where auftragsnummer = " + auftrag)
                .trim());
    }

    /**
     * Zeile eines Auftrags aus {@code FullQueue}, Spaltenüberschrift → Zelleninhalt (HTML
     * ohne Tags, Entitäten aufgelöst; Links als {@code href}).
     */
    Map<String, String> zeile(int auftrag) {
        String html = get("request", "FullQueue").text();
        List<String> kopf = zellen(html.substring(html.indexOf("<tr>"), html.indexOf("</tr>")));
        Matcher m = Pattern.compile("<tr>\\s*<td>" + auftrag + "</td>(.*?)</tr>", Pattern.DOTALL).matcher(html);
        if (!m.find()) {
            return Map.of();
        }
        List<String> werte = zellen("<td>" + auftrag + "</td>" + m.group(1));
        Map<String, String> z = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(kopf.size(), werte.size()); i++) {
            z.put(kopf.get(i), werte.get(i));
        }
        return z;
    }

    private static List<String> zellen(String html) {
        List<String> l = new ArrayList<>();
        Matcher m = Pattern.compile("<td>(.*?)</td>", Pattern.DOTALL).matcher(html);
        while (m.find()) {
            String zelle = m.group(1);
            Matcher href = Pattern.compile("href=\"([^\"]*)\"").matcher(zelle);
            l.add(href.find() ? href.group(1) : entitaeten(zelle.replaceAll("<[^>]*>", "")).trim());
        }
        return l;
    }

    static String entitaeten(String s) {
        return s.replace("&nbsp;", " ").replace("&auml;", "ä").replace("&ouml;", "ö").replace("&uuml;", "ü")
                .replace("&Auml;", "Ä").replace("&Ouml;", "Ö").replace("&Uuml;", "Ü").replace("&szlig;", "ß")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&amp;", "&");
    }

    /** Lädt eine Datei, auf die die Auftragsliste verlinkt (Protokoll, Datenfehler). */
    Antwort verlinkt(String link) {
        int i = link.indexOf("/vermdb/");
        assertTrue(i >= 0, "kein Link unter /vermdb/: " + link);
        return abrufen(link.substring(i));
    }

    // ------------------------------------------------------------------ Datenbank

    /** SQL in der Datenbank {@code vermdb} als Superuser; Ergebnis unformatiert ({@code psql -At}). */
    String sql(String abfrage) {
        ContainerState db = compose.getContainerByServiceName("datenbank-1")
                .orElseThrow(() -> new IllegalStateException("Dienst datenbank nicht gefunden"));
        try {
            ExecResult r = db.execInContainer("psql", "-U", "postgres", "-d", "vermdb", "-AtXq",
                    "-v", "ON_ERROR_STOP=1", "-c", abfrage);
            if (r.getExitCode() != 0) {
                fail("SQL fehlgeschlagen: " + abfrage + "\n" + r.getStderr());
            }
            return r.getStdout().strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Anzahl der Objekte je Tabelle des OKSTRA-Schemas für einen Auftrag, nur Tabellen mit Objekten. */
    Map<String, Integer> objekte(int auftrag) {
        String tabellen = sql("select table_name from information_schema.columns where table_schema = 'oks_2022'"
                + " and column_name = '_jobnr_' order by table_name");
        if (tabellen.isEmpty()) {
            return Map.of();
        }
        String abfrage = tabellen.lines()
                .map(t -> "select '" + t + "', count(*) from oks_2022.\"" + t + "\" where _jobnr_ = " + auftrag)
                .collect(Collectors.joining(" union all ")) + " order by 1";
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String z : sql(abfrage).lines().toList()) {
            String[] t = z.split("\\|");
            int n = Integer.parseInt(t[1]);
            if (n > 0) {
                m.put(t[0], n);
            }
        }
        return m;
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
