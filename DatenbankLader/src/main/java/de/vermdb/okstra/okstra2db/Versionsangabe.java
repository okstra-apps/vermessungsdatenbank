// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.vermdb.okstra.okstra2db;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Version der Vermessungsdatenbank aus dem Manifest des WAR (META-INF/MANIFEST.MF):
 * {@code Implementation-Version} und der Commit, aus dem gebaut wurde
 * ({@code Build-Commit}). Beides trägt der Gradle-Bau ein; fehlt das Manifest oder der
 * Eintrag, etwa beim Start aus einer Entwicklungsumgebung, heißt die Version
 * „unbekannt“.
 */
final class Versionsangabe {

    static final String UNBEKANNT = "unbekannt";

    private final String version;
    private final String commit;

    private Versionsangabe(String version, String commit) {
        this.version = version;
        this.commit = commit;
    }

    /**
     * Liest Version und Commit aus einem Manifest.
     *
     * @param manifest Inhalt von META-INF/MANIFEST.MF, darf {@code null} sein
     * @return die Angabe; ohne Manifest oder Eintrag mit der Version „unbekannt“
     */
    static Versionsangabe lies(InputStream manifest) {
        if (manifest == null) {
            return new Versionsangabe(UNBEKANNT, null);
        }
        try (InputStream ein = manifest) {
            Attributes a = new Manifest(ein).getMainAttributes();
            String version = a.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            if (version == null || version.isBlank()) {
                return new Versionsangabe(UNBEKANNT, null);
            }
            String commit = a.getValue("Build-Commit");
            return new Versionsangabe(version, commit == null || commit.isBlank() ? null : commit);
        } catch (IOException ex) {
            return new Versionsangabe(UNBEKANNT, null);
        }
    }

    /** @return die Version, etwa „2.0.0“, oder „unbekannt“ */
    String version() {
        return version;
    }

    /** @return etwa „2.0.0 (Commit 87d783a)“, ohne Commit nur die Version */
    @Override
    public String toString() {
        return commit == null ? version : version + " (Commit " + commit + ")";
    }
}
