# syntax=docker/dockerfile:1
# SPDX-FileCopyrightText: 2026 interactive instruments GmbH
# SPDX-License-Identifier: AGPL-3.0-or-later
# Image der Vermessungsdatenbank: Tomcat 11 mit Java 25, der nativen OKLABI 1.6.5.0 und
# der Webanwendung. Build-Kontext ist das Repository (siehe Container/README.md).
#
# Der Bau braucht nur Docker: Die Webanwendung baut Gradle in der Stufe bau-docker
# (Variante release), die Dateien des OKLABI-Release lädt die Stufe oklabi von okstra.de
# und prüft sie. Liegen sie schon in Container/oklabi/ oder oklabi-release/, werden
# diese genommen. Mit BAU=lokal übernimmt das Image stattdessen die Ergebnisse von
# ./gradlew build aus DatenbankLader/build/ (Testumgebung, Variante intern).
#
# Plattform linux/amd64: OKLABI 1.6.5.0 liefert nur Bibliotheken für amd64. Das native
# Paket ubuntu-2404 passt zur Basis Ubuntu 24.04 (noble) des Tomcat-Image; beide sind
# gemeinsam zu wechseln. Die Bau-Stufen laufen auf der Plattform des Rechners.
ARG TOMCAT_BILD=tomcat:11.0.29-jdk25-temurin-noble@sha256:f74af2669a61dff7c5d2f540b2e1d9d9df0780d53270a631ed3d38c7c95b1a65
ARG JDK_BILD=eclipse-temurin:25-jdk-noble@sha256:4ddc1a8bdaf283421df5487411aff10b797ccf1e06a339041005114c8f489b5c
ARG PYTHON_BILD=python:3.14-slim-trixie@sha256:22eafb370920ad8b429e0aec3fff433e0669968519f5d60469d83729c138b28b
ARG BAU=docker

# --- OKLABI-Release: laden, prüfen, auspacken; Fachbedeutungslisten als SQL ---
# Container/bau/gitter.py entfernt die Gitterdateien des Release samt Einträgen und schaltet
# die Benutzerkonfiguration ~/ntv2/gitter.cfg ein; das Image bringt stattdessen die Gitter
# aus Container/gitter-image/ mit, einzuschalten über VERMDB_GITTER_IMAGE (siehe unten).
FROM --platform=$BUILDPLATFORM ${PYTHON_BILD} AS oklabi
ARG OKLABI_RELEASE=https://www.okstra.de/oklabi/Release-20260629
ARG OKLABI_NATIV=ubuntu-2404-1-6-5-0
COPY Container/bau/ /bau/
COPY Datenbank/fbliste.py /bau/
COPY Container/oklabi/ /lokal/container/
COPY oklabi-release/ /lokal/release/
RUN set -eu; \
    python3 -I /bau/oklabi.py --release "$OKLABI_RELEASE" --liste /bau/oklabi.sha256 \
      --ziel /oklabi /lokal/container /lokal/release; \
    cd /oklabi; \
    python3 -m tarfile --filter tar -e "$OKLABI_NATIV.tbz" .; mv "$OKLABI_NATIV" lib; \
    python3 -m zipfile -e Ressourcen.zip .; mv Ressourcen OKLABI_HOME; \
    python3 -I /bau/gitter.py OKLABI_HOME; \
    python3 -I /bau/fbliste.py Ressourcen.zip -o fbliste.sql

# --- Webanwendung mit Gradle (Variante release) ---
FROM --platform=$BUILDPLATFORM ${JDK_BILD} AS bau-docker
WORKDIR /quelle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY DatenbankLader/build.gradle.kts DatenbankLader/
COPY DatenbankLader/src/ DatenbankLader/src/
COPY --from=oklabi /oklabi/*.jar.zip oklabi-release/
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain -PoklabiVariante=release build

FROM scratch AS ergebnis-docker
COPY --from=bau-docker /quelle/DatenbankLader/build/libs/Okstra2Db.war /
COPY --from=bau-docker /quelle/DatenbankLader/build/tomcat-lib/ /tomcat-lib/
COPY --from=bau-docker /quelle/DatenbankLader/build/reports/cyclonedx/bom.json /
COPY --from=bau-docker /quelle/DatenbankLader/build/lizenzen/ /lizenzen/

FROM scratch AS ergebnis-lokal
COPY DatenbankLader/build/libs/Okstra2Db.war /
COPY DatenbankLader/build/tomcat-lib/ /tomcat-lib/
COPY DatenbankLader/build/reports/cyclonedx/bom.json /
COPY DatenbankLader/build/lizenzen/ /lizenzen/

FROM ergebnis-${BAU} AS ergebnis

# --- Image ---
FROM --platform=linux/amd64 ${TOMCAT_BILD}

# Kein "apt-get upgrade": Sicherheitsstände kommen über ein neueres Basis-Image.
# postgresql-client für die Einrichtung der Datenbank (vermdb.sh einrichten), python3 für
# Datenbank/fbliste.py (Fachbedeutungslisten in die Datenbank, vermdb.sh)
RUN set -eu; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
      libdeflate0 libtiff6 libjpeg-turbo8 libxml2 libicu74 libcurl4t64 libexpat1 \
      curl tzdata postgresql-client python3; \
    ln -sf /usr/share/zoneinfo/Europe/Berlin /etc/localtime; \
    apt-get clean; rm -rf /var/lib/apt/lists/*

# Native Bibliotheken (Oklabi/Bibliothek/Linux/) und Ressourcen (Oklabi/Ressourcen/)
COPY --from=oklabi /oklabi/lib/ /opt/lib/
COPY --from=oklabi /oklabi/OKLABI_HOME/ /opt/OKLABI_HOME/

# Fachbedeutungslisten: die des Release unter fbliste-release/; fbliste/ verweist darauf und
# wird von vermdb.sh beim Start um die eigenen Listen (/opt/vermdb/fbliste, von Compose
# eingehängt) ergänzt
RUN set -eu; \
    mv /opt/OKLABI_HOME/fbliste /opt/OKLABI_HOME/fbliste-release; \
    mkdir /opt/OKLABI_HOME/fbliste /opt/vermdb /opt/vermdb/fbliste; \
    for f in /opt/OKLABI_HOME/fbliste-release/*; do ln -s "$f" /opt/OKLABI_HOME/fbliste/; done

# Gitterdateien: die des Image (Container/gitter-image/README.md) mit ihren Einträgen
# (<Name>.cfg) unter ntv2-image/; ntv2/ samt gitter.cfg stellt vermdb.sh beim Start aus den
# in VERMDB_GITTER_IMAGE genannten und den eigenen Gittern (/opt/vermdb/gitter, von
# Compose eingehängt) zusammen
COPY Container/gitter-image/*.gsb Container/gitter-image/*.cfg Container/gitter-image/README.md /opt/OKLABI_HOME/ntv2-image/
RUN mkdir -p /opt/OKLABI_HOME/ntv2 /opt/vermdb/gitter

# Im lib-Verzeichnis des Tomcat wie bei den Installationen ohne Container: OKLABI.jar
# und der PostgreSQL-Treiber für die Datenquellen der server.xml
COPY --from=ergebnis /tomcat-lib/ /usr/local/tomcat/lib/

# Die server.xml liest die Datenbankverbindung und die web.xml einige Werte aus
# Umgebungsvariablen (EnvironmentPropertySource)
RUN printf '%s\n' \
      'export OKLABI_HOME=/opt/OKLABI_HOME' \
      'export PROJ_LIB=/opt/OKLABI_HOME/gdal_data' \
      'export LD_LIBRARY_PATH=/usr/local/tomcat/native-jni-lib:/opt/lib' \
      'export CATALINA_OPTS="$CATALINA_OPTS -Xms512m -Xmx3072m -Djava.library.path=/usr/local/tomcat/native-jni-lib:/opt/lib -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED -Dorg.apache.tomcat.util.digester.PROPERTY_SOURCE=org.apache.tomcat.util.digester.EnvironmentPropertySource"' \
      > /usr/local/tomcat/bin/setenv.sh; \
    chmod 755 /usr/local/tomcat/bin/setenv.sh

# Die Webanwendung entpackt, mit der web.xml des Containers
COPY --from=ergebnis /Okstra2Db.war /tmp/Okstra2Db.war
RUN set -eu; \
    rm -rf /usr/local/tomcat/webapps/*; \
    mkdir /usr/local/tomcat/webapps/Okstra2Db; \
    cd /usr/local/tomcat/webapps/Okstra2Db && jar -xf /tmp/Okstra2Db.war; \
    rm /tmp/Okstra2Db.war
COPY Container/conf/web.xml /usr/local/tomcat/webapps/Okstra2Db/WEB-INF/web.xml
COPY Container/conf/server.xml /usr/local/tomcat/conf/server.xml

# Kontext /vermdb: liefert Protokolle, Datenfehler und Rückgaben aus (okstraServerPrefix)
COPY Container/conf/vermdb.xml /usr/local/tomcat/conf/Catalina/localhost/vermdb.xml
COPY Container/conf/vermdb-web.xml /usr/local/tomcat/vermdb/WEB-INF/web.xml

# Vorgabeantwort des OkWS-Backends (Init-Parameter okstraWSresponse)
COPY okws/beispiel-wfs.xml /opt/okws/beispiel-wfs.xml

# Einrichtung der Datenbank: Skripte aus Datenbank/ und die Fachbedeutungslisten des
# Release; Stückliste der Webanwendung und Lizenzen
COPY Datenbank/*.sql /opt/vermdb/datenbank/
COPY --from=oklabi /oklabi/fbliste.sql /opt/vermdb/datenbank/
COPY Datenbank/fbliste.py /opt/vermdb/fbliste.py
COPY --from=ergebnis /bom.json /opt/vermdb/bom.json
COPY LICENSES/ /opt/vermdb/LICENSES/
COPY NOTICE /opt/vermdb/NOTICE
COPY --from=ergebnis /lizenzen/ /opt/vermdb/LICENSES/
COPY --chmod=755 Container/vermdb.sh /usr/local/bin/vermdb.sh

# Betrieb ohne root. Daten, Uploads, Rückgaben und Protokolle unter /var/home/Daten
# (Volume); ein leeres benanntes Volume übernimmt Verzeichnisse und Eigentümer.
RUN set -eu; \
    groupadd --system --gid 10001 vermdb; \
    useradd --system --uid 10001 --gid vermdb --create-home --home-dir /home/vermdb \
      --shell /usr/sbin/nologin vermdb; \
    mkdir -p /var/home/Daten/Okstra2DB/upload /var/home/Daten/Okstra2DB/download \
      /var/home/Daten/Okstra2DB/log /var/home/Daten/Okstra2DB/datenfehler; \
    chown -R vermdb:vermdb /var/home/Daten /usr/local/tomcat/logs /usr/local/tomcat/temp \
      /usr/local/tomcat/work /opt/OKLABI_HOME/fbliste /opt/OKLABI_HOME/ntv2
USER vermdb

ENV LANG=C.UTF-8 LC_ALL=C.UTF-8
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -fsS -o /dev/null "http://localhost:8080/Okstra2Db/Okstra2DbServlet?request=GetThemes" || exit 1
CMD ["/usr/local/bin/vermdb.sh", "start"]
