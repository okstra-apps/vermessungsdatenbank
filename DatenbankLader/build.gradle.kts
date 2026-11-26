// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.security.MessageDigest
import org.cyclonedx.model.Component
import org.cyclonedx.model.License
import org.cyclonedx.model.LicenseChoice

plugins {
    war
    // Stückliste der Komponenten (SBOM) im Format CycloneDX
    id("org.cyclonedx.bom") version "3.6.1"
}

// Die Bibliotheken im WAR sind genau die bisher mitgelieferten, ohne die
// Abhängigkeiten, die sie selbst angeben
configurations.implementation {
    isTransitive = false
}
configurations.runtimeClasspath {
    isTransitive = false
}

// OkstraSqlUtil und OKLABI.jar aus dem Release oder aus den internen Modulen
// (oklabiVariante, siehe gradle.properties)
val oklabiVariante = rootProject.extra["oklabiVariante"] as String
val releaseOkstraSqlUtil = rootProject.layout.buildDirectory.file("oklabi/okstrasqlutil/OkstraSqlUtil.jar")
val releaseOklabi = rootProject.layout.buildDirectory.file("oklabi/archiv/OKLABI.jar")

dependencies {
    if (oklabiVariante == "intern") {
        implementation(project(":intern:OkstraSqlUtil"))
    } else {
        implementation(files(releaseOkstraSqlUtil).builtBy(":okstraSqlUtilEntpacken"))
    }

    // FileUtils
    implementation("commons-io:commons-io:2.22.0")
    // Der Quelltext protokolliert über die log4j-1.2-API, ausgeführt von log4j 2
    implementation("org.apache.logging.log4j:log4j-1.2-api:2.26.1")
    implementation("org.apache.logging.log4j:log4j-api:2.26.1")
    implementation("org.apache.logging.log4j:log4j-core:2.26.1")
    // Der Zugriff auf die Datenbank geht über JDBC; Geometrien als Text (EWKT)
    implementation("org.postgresql:postgresql:42.7.15")

    // Stellt der Tomcat bereit: Servlet-API 6.1 (Jakarta EE 11) und den
    // Verbindungspool, auf dessen BasicDataSource das Servlet die Datenquellen castet
    compileOnly("org.apache.tomcat:tomcat-servlet-api:11.0.29")
    compileOnly("org.apache.tomcat:tomcat-dbcp:11.0.29")

    // Unit-Tests für Klassen ohne native OKLABI. Die Klassen von OKLABI.jar (ohne die
    // native Bibliothek) braucht OkstraSqlUtil schon beim Laden.
    testRuntimeOnly(files(rootProject.layout.buildDirectory.file("oklabi/OKLABI.jar")).builtBy(":oklabiJar"))
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// Nutzungsbestimmungen der BASt für OKLABI und OkstraSqlUtil: im WAR unter META-INF/,
// für Image und Update-Paket unter build/lizenzen/
val nutzungsbestimmungen = files(rootProject.layout.buildDirectory.file("oklabi/lizenz/OKLABI-Nutzungsbestimmungen.txt"))
    .builtBy(":oklabiNutzungsbestimmungen")

tasks.war {
    archiveFileName.set("Okstra2Db.war")
    metaInf { from(nutzungsbestimmungen) }
    // Version und Commit; das Servlet nennt sie beim Start im Protokoll
    val commit = rootProject.extra["buildCommit"] as String
    manifest {
        attributes("Implementation-Title" to "Vermessungsdatenbank",
            "Implementation-Version" to project.version.toString())
        if (commit.isNotEmpty()) {
            attributes("Build-Commit" to commit)
        }
    }
}

val lizenzenBereitstellen = tasks.register<Sync>("lizenzen") {
    description = "Legt die Nutzungsbestimmungen der BASt nach build/lizenzen"
    from(nutzungsbestimmungen)
    into(layout.buildDirectory.dir("lizenzen"))
}
tasks.assemble {
    dependsOn(lizenzenBereitstellen)
}

// Für das lib-Verzeichnis des Tomcat: OKLABI.jar (zur nativen OKLABI) und der
// PostgreSQL-Treiber, denn die Datenquellen der server.xml legt der Tomcat selbst an,
// mit dem Treiber aus seinem lib-Verzeichnis. Dieselbe Treiberfassung wie im WAR.
val tomcatLib = configurations.create("tomcatLib") {
    isTransitive = false
}
dependencies {
    tomcatLib("org.postgresql:postgresql:42.7.15")
    if (oklabiVariante == "intern") {
        tomcatLib(project(":intern:OKLABI"))
    } else {
        tomcatLib(files(releaseOklabi).builtBy(":oklabiEntpacken"))
    }
}
val tomcatLibBereitstellen = tasks.register<Sync>("tomcatLib") {
    description = "Legt die Bibliotheken für das lib-Verzeichnis des Tomcat nach build/tomcat-lib"
    from(tomcatLib)
    into(layout.buildDirectory.dir("tomcat-lib"))
}
tasks.assemble {
    dependsOn(tomcatLibBereitstellen)
}

// Stückliste der Komponenten (CycloneDX, JSON) nach build/reports/cyclonedx/bom.json.
// Erfasst wird der runtimeClasspath, also die Bibliotheken in WEB-INF/lib aus Maven
// Central. Nicht enthalten sind die Bestandteile, die der Tomcat bereitstellt
// (Servlet-API, PostgreSQL-Treiber in seinem lib-Verzeichnis). Das Plugin erfasst keine
// Jars aus Dateien; in der Release-Variante werden OkstraSqlUtil (im WAR) und OKLABI.jar
// (im lib-Verzeichnis des Tomcat) deshalb nachträglich mit Version, Prüfsumme und
// Lizenz eingetragen.
// Das Plugin erkennt den Wechsel der Variante nicht von selbst
tasks.cyclonedxDirectBom {
    includeConfigs = listOf("runtimeClasspath")
    inputs.property("oklabiVariante", oklabiVariante)
}
tasks.cyclonedxBom {
    inputs.property("oklabiVariante", oklabiVariante)
    projectType = Component.Type.APPLICATION
    componentName = "Vermessungsdatenbank"
    licenseChoice = LicenseChoice().apply {
        addLicense(License().apply { id = "AGPL-3.0-or-later" })
    }
    xmlOutput.convention(null as RegularFile?)
    if (oklabiVariante == "release") {
        dependsOn(":oklabiEntpacken", ":okstraSqlUtilEntpacken")
        inputs.files(releaseOklabi, releaseOkstraSqlUtil)
        val version = providers.gradleProperty("oklabiVersion").get()
        val release = providers.gradleProperty("oklabiRelease").get()
        val archive = mapOf(
            "OKLABI" to providers.gradleProperty("oklabiArchiv").get(),
            "OkstraSqlUtil" to providers.gradleProperty("okstraSqlUtilArchiv").get())
        val jars = mapOf("OKLABI" to releaseOklabi, "OkstraSqlUtil" to releaseOkstraSqlUtil)
        val bom = jsonOutput
        doLast {
            val datei = bom.get().asFile
            @Suppress("UNCHECKED_CAST")
            val inhalt = JsonSlurper().parse(datei) as MutableMap<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val komponenten = inhalt.getOrPut("components") { mutableListOf<Any?>() } as MutableList<Any?>
            @Suppress("UNCHECKED_CAST")
            val abhaengigkeiten = inhalt.getOrPut("dependencies") { mutableListOf<Any?>() } as MutableList<MutableMap<String, Any?>>
            // Die Jars gehören zum Modul DatenbankLader, nicht zur Wurzel der Stückliste
            @Suppress("UNCHECKED_CAST")
            val wurzel = komponenten.map { it as Map<String, Any?> }.firstOrNull { it["name"] == "DatenbankLader" }
                ?: ((inhalt["metadata"] as Map<String, Any?>)["component"] as Map<String, Any?>)
            val neu = mutableListOf<String>()
            for ((name, jar) in jars) {
                val ref = "bast:$name@$version"
                val pruefsumme = MessageDigest.getInstance("SHA-256").digest(jar.get().asFile.readBytes())
                    .joinToString("") { "%02x".format(it) }
                komponenten.add(mapOf(
                    "type" to "library",
                    "bom-ref" to ref,
                    "supplier" to mapOf("name" to "Bundesanstalt für Straßen- und Verkehrswesen (BASt)"),
                    "name" to name,
                    "version" to version,
                    "description" to if (name == "OKLABI")
                        "OKSTRA-Klassenbibliothek, Java-Anbindung; im lib-Verzeichnis des Tomcat zusammen mit der nativen OKLABI"
                        else "Datenbankanbindung der OKLABI; im WAR",
                    "scope" to "required",
                    "hashes" to listOf(mapOf("alg" to "SHA-256", "content" to pruefsumme)),
                    "licenses" to listOf(mapOf("license" to mapOf(
                        "name" to "Nutzungsbestimmungen der BASt für die OKSTRA-Klassenbibliothek",
                        "url" to "https://www.okstra.de/oklabi/Lizenz/nutzung.txt"))),
                    "externalReferences" to listOf(mapOf(
                        "type" to "distribution",
                        "url" to "$release/Oklabi/Bibliothek/Java/" + File(archive.getValue(name)).name))))
                abhaengigkeiten.add(mutableMapOf("ref" to ref, "dependsOn" to emptyList<String>()))
                neu.add(ref)
            }
            val eintrag = abhaengigkeiten.firstOrNull { it["ref"] == wurzel["bom-ref"] }
            if (eintrag != null) {
                @Suppress("UNCHECKED_CAST")
                eintrag["dependsOn"] = ((eintrag["dependsOn"] as List<String>?) ?: emptyList()) + neu
            } else {
                abhaengigkeiten.add(mutableMapOf("ref" to wurzel["bom-ref"], "dependsOn" to neu))
            }
            datei.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(inhalt)) + "\n")
        }
    }
}
tasks.assemble {
    dependsOn(tasks.cyclonedxBom)
}

// Integrationstests gegen die Container-Installation (Container/compose.yaml) mit den
// Beispieldaten aus testdaten/. Sie brauchen Docker und laufen nicht mit check/build, weil
// der Image-Bau selbst ./gradlew build ausführt; Aufruf: ./gradlew integrationTest.
// Das Image entsteht aus dem WAR dieses Baus (BAU=lokal), also in der gewählten
// oklabiVariante. Optionen:
//   -PintegrationstestBild=<name:tag>  Name des Image (Vorgabe vermdb:integrationstest)
//   -PohneBildbau                      vorhandenes Image nehmen, nicht neu bauen
//   -PreferenzenAktualisieren          Referenzdateien unter src/integrationTest/resources/
//                                      erwartet/ aus den Ergebnissen neu schreiben
val integrationTest: SourceSet = sourceSets.create("integrationTest")
dependencies {
    "integrationTestImplementation"(platform("org.junit:junit-bom:6.1.3"))
    "integrationTestImplementation"("org.junit.jupiter:junit-jupiter")
    "integrationTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    "integrationTestImplementation"("org.testcontainers:testcontainers:2.0.5")
}
val integrationstestBild = providers.gradleProperty("integrationstestBild").getOrElse("vermdb:integrationstest")
val bildBauen = tasks.register<Exec>("integrationstestBild") {
    description = "Baut das Image für die Integrationstests aus den Ergebnissen dieses Baus (BAU=lokal)"
    dependsOn(tasks.war, tomcatLibBereitstellen, lizenzenBereitstellen, tasks.cyclonedxBom)
    onlyIf { !providers.gradleProperty("ohneBildbau").isPresent }
    workingDir = rootDir
    commandLine("docker", "build", "-q", "--platform", "linux/amd64", "-f", "Container/Dockerfile",
        "--build-arg", "BAU=lokal", "-t", integrationstestBild, ".")
}
tasks.register<Test>("integrationTest") {
    description = "Integrationstests gegen die Container-Installation (braucht Docker)"
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    useJUnitPlatform()
    dependsOn(bildBauen)
    shouldRunAfter(tasks.test)
    // Die Tests teilen sich eine Installation und laufen nacheinander
    maxParallelForks = 1
    systemProperty("vermdb.repository", rootDir.absolutePath)
    systemProperty("vermdb.arbeitsverzeichnis", layout.buildDirectory.dir("integrationstest").get().asFile.absolutePath)
    systemProperty("vermdb.bild", integrationstestBild)
    systemProperty("vermdb.oklabiVariante", oklabiVariante)
    if (providers.gradleProperty("referenzenAktualisieren").isPresent) {
        systemProperty("vermdb.referenzenAktualisieren", "true")
    }
    // Ergebnis hängt vom Image ab, das Gradle nicht als Eingabe kennt
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
