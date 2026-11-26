// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Führt die Beispiele der Dokumentation ({@code doku/beispiele/*.hurl}) gegen die Installation
 * aus, mit Hurl als Container im Netz der Installation. Ausgabe ({@code --error-format long})
 * und JUnit-Bericht von Hurl liegen danach unter {@code build/integrationstest/hurl/}.
 */
class BeispieleIT {

    /** Hurl 8.1.0 (offizielles Image), mit Digest festgelegt */
    static final String HURL = "ghcr.io/orange-opensource/hurl:8.1.0"
            + "@sha256:f8aed33658ba793ed53c5a5b2e57cadd9ec9322c8b56c550652c1f4cbaa241c5";

    private final Umgebung u = Umgebung.get();

    @Test
    void beispieleDerDokumentation() throws IOException {
        Path beispiele = u.repository().resolve("doku/beispiele");
        List<String> dateien;
        try (Stream<Path> s = Files.list(beispiele)) {
            dateien = s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".hurl")).sorted().toList();
        }
        String aufruf = "hurl --test --jobs 1 --error-format long --report-junit /tmp/junit.xml"
                + " --variable url=http://vermdb:8080/Okstra2Db/Okstra2DbServlet"
                + " --variable vermdb=http://vermdb:8080/vermdb"
                + " --file-root /w"
                + dateien.stream().map(d -> " doku/beispiele/" + d).reduce("", String::concat)
                + " > /tmp/hurl.log 2>&1; echo $? > /tmp/rc";
        Path ziel = u.arbeitsverzeichnis().resolve("hurl");
        Files.createDirectories(ziel);
        try (GenericContainer<?> hurl = new GenericContainer<>(DockerImageName.parse(HURL))
                .withNetworkMode(u.netz())
                .withCopyFileToContainer(MountableFile.forHostPath(beispiele), "/w/doku/beispiele")
                .withCopyFileToContainer(MountableFile.forHostPath(u.repository().resolve("testdaten")), "/w/testdaten")
                .withWorkingDirectory("/w")
                .withCreateContainerCmdModifier(c -> c.withEntrypoint("sh"))
                .withCommand("-c", aufruf)
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(10)))) {
            hurl.start();
            for (String datei : new String[] {"rc", "hurl.log", "junit.xml"}) {
                hurl.copyFileFromContainer("/tmp/" + datei, ziel.resolve(datei).toString());
            }
        }
        String log = Files.readString(ziel.resolve("hurl.log"), StandardCharsets.UTF_8);
        String rc = Files.readString(ziel.resolve("rc"), StandardCharsets.UTF_8).trim();
        assertEquals("0", rc, "Hurl meldet Fehler (Ausgabe in " + ziel + "):\n" + log);
    }
}
