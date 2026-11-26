// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

// Bau der Vermessungsdatenbank: die Webanwendung DatenbankLader als WAR für einen
// vorhandenen Tomcat. OKLABI und OkstraSqlUtil kommen aus dem Release der BASt (siehe
// build.gradle.kts und oklabi-release/README.md).
//
// Der Ordner intern/ gehört nicht zur Veröffentlichung. Er enthält Module, die nur für
// die bestehenden Installationen gebaut werden. Fehlt der Ordner, wird nur gegen das
// Release gebaut.
rootProject.name = "Vermessungsdatenbank"
include("DatenbankLader")
if (file("intern/OkstraSqlUtil").isDirectory) {
    include("intern:OKLABI", "intern:OkstraSqlUtil", "intern:LsbbTransform")
}
