// SPDX-FileCopyrightText: 2026 interactive instruments GmbH
// SPDX-License-Identifier: AGPL-3.0-or-later

import java.security.MessageDigest

// OKLABI und OkstraSqlUtil aus dem Release der BASt. Fehlen die Archive in
// oklabi-release/, lädt der Bau sie von okstra.de (oklabiRelease). Sie werden vor dem
// Auspacken gegen ihre SHA-256 in gradle.properties geprüft; die Prüfung der
// Abhängigkeiten von Gradle (gradle/verification-metadata.xml) erfasst nur, was aus
// Maven Central kommt. Ebenso die Nutzungsbestimmungen der BASt, die mit WAR, Image
// und Update-Paket weitergegeben werden.
//
// Übersetzt wird immer gegen das OKLABI.jar des Release. Ins WAR kommt OKLABI.jar
// nicht: Zur Laufzeit liegt es im lib-Verzeichnis des Tomcat, zusammen mit der
// nativen OKLABI.

val internVorhanden = file("intern/OkstraSqlUtil").isDirectory
val oklabiVariante = providers.gradleProperty("oklabiVariante")
    .getOrElse(if (internVorhanden) "intern" else "release")
when {
    oklabiVariante !in setOf("intern", "release") ->
        throw GradleException("oklabiVariante muss \"intern\" oder \"release\" sein, nicht \"$oklabiVariante\"")
    oklabiVariante == "intern" && !internVorhanden ->
        throw GradleException("oklabiVariante=intern verlangt den Ordner intern/")
}
extra["oklabiVariante"] = oklabiVariante
logger.lifecycle("OKLABI und OkstraSqlUtil: $oklabiVariante")

// Version der Vermessungsdatenbank und Commit, aus dem gebaut wird (Kurzform, mit
// -dirty bei nicht committeten Änderungen; leer ohne Git, etwa im Image-Bau)
val vermdbVersion = providers.gradleProperty("vermdbVersion").get()
val buildCommit: String = try {
    providers.exec {
        commandLine("git", "describe", "--always", "--dirty", "--abbrev=7", "--exclude=*")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
} catch (e: Exception) {
    // kein git vorhanden (Image-Bau)
    ""
}
extra["vermdbVersion"] = vermdbVersion
extra["buildCommit"] = buildCommit
if (!vermdbVersion.endsWith("-SNAPSHOT") && buildCommit.endsWith("-dirty")) {
    logger.warn("Version $vermdbVersion aus einem Stand mit nicht committeten Änderungen ($buildCommit)")
}
logger.lifecycle("Vermessungsdatenbank $vermdbVersion" + if (buildCommit.isEmpty()) "" else " ($buildCommit)")

fun sha256(datei: File): String =
    MessageDigest.getInstance("SHA-256").digest(datei.readBytes()).joinToString("") { "%02x".format(it) }

val oklabiRelease = providers.gradleProperty("oklabiRelease").get().trimEnd('/')
val offline = gradle.startParameter.isOffline

// Aufgabe, die eine fehlende Datei des Release von url nach oklabi-release/ lädt und
// sie gegen ihre SHA-256 prüft
fun geprueftLaden(aufgabe: String, datei: File, url: String, soll: String): TaskProvider<Task> =
    tasks.register(aufgabe) {
        description = "Lädt ${datei.name}, falls es fehlt, und prüft seine SHA-256"
        inputs.property("pruefsumme", soll)
        doLast {
            if (!datei.isFile) {
                val hinweis = "Datei von Hand nach ${datei.parentFile} legen, siehe oklabi-release/README.md"
                if (offline) {
                    throw GradleException("Datei des OKLABI-Release fehlt: $datei; im Offline-Modus wird sie nicht geladen. $hinweis ($url)")
                }
                logger.lifecycle("Lade $url")
                datei.parentFile.mkdirs()
                val teil = File(datei.parentFile, datei.name + ".part")
                try {
                    java.net.URI(url).toURL().openStream().use { ein -> teil.outputStream().use { ein.copyTo(it) } }
                } catch (e: Exception) {
                    teil.delete()
                    throw GradleException("Datei des OKLABI-Release konnte nicht geladen werden: $url (${e.message}). $hinweis")
                }
                val geladen = sha256(teil)
                if (geladen != soll) {
                    teil.delete()
                    throw GradleException("Prüfsumme der geladenen ${datei.name} stimmt nicht: $geladen statt $soll")
                }
                if (!teil.renameTo(datei)) {
                    throw GradleException("$teil konnte nicht in $datei umbenannt werden")
                }
            }
            val ist = sha256(datei)
            if (ist != soll) {
                throw GradleException("Prüfsumme von ${datei.name} stimmt nicht: $ist statt $soll")
            }
        }
    }

// Lädt ein fehlendes Release-Archiv, prüft es und packt das Jar daraus nach
// build/oklabi/<verzeichnis> aus
fun releaseJar(aufgabe: String, archivEigenschaft: String, pruefsummeEigenschaft: String,
               jar: String, verzeichnis: String): TaskProvider<Sync> {
    val archiv = rootProject.file(providers.gradleProperty(archivEigenschaft).get())
    val soll = providers.gradleProperty(pruefsummeEigenschaft).get()
    val url = "$oklabiRelease/Oklabi/Bibliothek/Java/${archiv.name}"
    val pruefen = geprueftLaden("${aufgabe}Pruefen", archiv, url, soll)
    return tasks.register<Sync>("${aufgabe}Entpacken") {
        description = "Packt $jar aus ${archiv.name} aus"
        dependsOn(pruefen)
        from(zipTree(archiv)) { include(jar) }
        into(layout.buildDirectory.dir("oklabi/$verzeichnis"))
    }
}

// build/oklabi/archiv/OKLABI.jar: unverändert, für das lib-Verzeichnis des Tomcat
val oklabiEntpacken = releaseJar("oklabi", "oklabiArchiv", "oklabiPruefsumme", "OKLABI.jar", "archiv")
// build/oklabi/okstrasqlutil/OkstraSqlUtil.jar: unverändert, für das WAR
releaseJar("okstraSqlUtil", "okstraSqlUtilArchiv", "okstraSqlUtilPruefsumme", "OkstraSqlUtil.jar", "okstrasqlutil")

// build/oklabi/lizenz/OKLABI-Nutzungsbestimmungen.txt: die Nutzungsbestimmungen der BASt
// für OKLABI und OkstraSqlUtil, unverändert (seit 13.11.2026 UTF-8), für WAR, Image und
// Update-Paket
val nutzungsbestimmungen = rootProject.file(providers.gradleProperty("oklabiNutzungsbestimmungenDatei").get())
val nutzungsbestimmungenPruefen = geprueftLaden("oklabiNutzungsbestimmungenPruefen", nutzungsbestimmungen,
    providers.gradleProperty("oklabiNutzungsbestimmungen").get(),
    providers.gradleProperty("oklabiNutzungsbestimmungenPruefsumme").get())
tasks.register<Sync>("oklabiNutzungsbestimmungen") {
    description = "Legt die Nutzungsbestimmungen der BASt nach build/oklabi/lizenz"
    dependsOn(nutzungsbestimmungenPruefen)
    from(nutzungsbestimmungen) { rename { "OKLABI-Nutzungsbestimmungen.txt" } }
    into(layout.buildDirectory.dir("oklabi/lizenz"))
}

// build/oklabi/OKLABI.jar zum Übersetzen: Das Jar des Release enthält neben den
// Klassen die Quellen; sie werden entfernt, damit javac sie nicht mit übersetzt.
tasks.register<Jar>("oklabiJar") {
    description = "OKLABI.jar aus dem Release, ohne die mitgelieferten Quellen"
    dependsOn(oklabiEntpacken)
    from(oklabiEntpacken.map { zipTree(it.destinationDir.resolve("OKLABI.jar")) }) {
        exclude("**/*.java")
    }
    archiveFileName.set("OKLABI.jar")
    destinationDirectory.set(layout.buildDirectory.dir("oklabi"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

subprojects {
    // intern ist nur der Ordner der internen Module
    if (path == ":intern") return@subprojects

    apply(plugin = "java")
    version = vermdbVersion

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        // Übersetzt wird mit einem JDK 25, gleich mit welchem Gradle selbst läuft.
        // Gradle sucht das JDK unter den installierten und lädt keines herunter.
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        // Bytecode und API für Java 25 (LTS); Tomcat 11 verlangt mindestens Java 17
        options.release.set(25)
        options.encoding = "UTF-8"
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    // Das interne Modul OKLABI enthält diese Klassen selbst
    if (name != "OKLABI") {
        dependencies {
            "compileOnly"(files(rootProject.layout.buildDirectory.file("oklabi/OKLABI.jar")).builtBy(":oklabiJar"))
        }
    }
}

// Update-Paket für die bestehenden Installationen, nur mit dem internen Teil
if (internVorhanden) {
    apply(from = "intern/Update/paket.gradle.kts")
}
