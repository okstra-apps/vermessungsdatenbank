#!/bin/bash
# SPDX-FileCopyrightText: 2026 interactive instruments GmbH
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Befehle des Image (siehe Container/README.md):
#
#   start        stellt Gitterdateien und Fachbedeutungslisten zusammen, gleicht die Listen
#                mit der Datenbank ab und startet Tomcat mit der Vermessungsdatenbank im
#                Vordergrund (Vorgabe)
#   kennwoerter  erzeugt fehlende Kennwörter für die Datenbank im Verzeichnis
#                VERMDB_KENNWOERTER (Vorgabe /kennwoerter); als root
#   einrichten   bereitet das Datenverzeichnis vor (als root), legt Rolle und Datenbank
#                an und richtet sie mit den Skripten aus Datenbank/ ein, soweit das
#                fehlt; mit dem Superuser der Datenbank
#   fblisten     lädt die Fachbedeutungslisten (Release und eigene) neu in die Datenbank,
#                auch wenn sie schon aktuell sind; als Rolle der Anwendung
#
# Fachbedeutungslisten: die des OKLABI-Release liegen im Image unter fbliste-release/,
# eigene (CSV im Format der OKLABI, fb-<Land>-<Haupt><Unter>.csv) hängt Compose unter
# /opt/vermdb/fbliste ein. Eine eigene Datei mit dem Namen einer Liste des Release ersetzt
# diese. Die Datenbank führt die Listen zusätzlich in oks_meta.fbliste; der Kommentar der
# Tabelle hält die Prüfsumme des geladenen Stands fest.
#
# Gitterdateien: die des Image liegen unter ntv2-image/ mit ihren Einträgen in <Name>.cfg;
# eingeschaltet sind die in VERMDB_GITTER_IMAGE genannten (durch Leerzeichen oder Komma
# getrennt, Vorgabe keines). Eigene (mit eigener gitter.cfg) hängt Compose unter
# /opt/vermdb/gitter ein. Eine eigene Datei mit dem Namen einer des Image ersetzt diese, ein
# eigener Eintrag für dasselbe Paar von Systemen (in beliebiger Richtung) den des Image.
#
# Kennwörter kommen aus VARIABLE oder aus der Datei in VARIABLE_DATEI. Die Verbindung zur
# Datenbank als Rolle der Anwendung kommt aus VERMDB_DB_HOST/_PORT/_NAME oder, wenn
# VERMDB_DB_HOST fehlt, aus VERMDB_DB_URL (jdbc:postgresql://<host>:<port>/<datenbank>).
set -euo pipefail

DATEN=/var/home/Daten/Okstra2DB
SQL=/opt/vermdb/datenbank
FBL=/opt/OKLABI_HOME/fbliste
FBL_RELEASE=/opt/OKLABI_HOME/fbliste-release
FBL_EIGENE=/opt/vermdb/fbliste
GIT=/opt/OKLABI_HOME/ntv2
GIT_IMAGE=/opt/OKLABI_HOME/ntv2-image
GIT_EIGENE=/opt/vermdb/gitter

# Setzt die Variable $1 aus der Datei in ${1}_DATEI, falls angegeben
aus_datei() {
  local datei_var="${1}_DATEI"
  local datei="${!datei_var:-}"
  [ -n "$datei" ] || return 0
  [ -r "$datei" ] || { echo "$datei_var: Datei $datei ist nicht lesbar" >&2; exit 1; }
  printf -v "$1" '%s' "$(cat "$datei")"
  export "${1?}"
}

pflicht() {
  local v
  for v in "$@"; do
    [ -n "${!v:-}" ] || { echo "Umgebungsvariable $v fehlt" >&2; exit 1; }
  done
}

start() {
  aus_datei VERMDB_DB_PASSWORT
  pflicht VERMDB_DB_URL VERMDB_DB_BENUTZER VERMDB_DB_PASSWORT
  # Das Servlet legt nur die letzte Ebene selbst an. Geprüft wird durch Anlegen: test -w
  # meldet ein eingehängtes Verzeichnis unter Docker Desktop auch dann als beschreibbar,
  # wenn es das nicht ist.
  if ! mkdir -p "$DATEN/upload" "$DATEN/download" "$DATEN/log" "$DATEN/datenfehler" 2>/dev/null \
      || ! touch "$DATEN/log/.schreibprobe" 2>/dev/null; then
    echo "Das Datenverzeichnis (/var/home/Daten, auf dem Rechner VERMDB_DATEN) ist für den" \
      "Benutzer $(id -un) (UID $(id -u)) nicht beschreibbar; es muss ihm gehören." >&2
    exit 1
  fi
  rm -f "$DATEN/log/.schreibprobe"
  gitter_zusammenstellen
  listen_zusammenstellen
  # Ein Fehler beim Abgleich hält den Start nicht auf; er steht im Protokoll
  ( listen_abgleichen ) || echo "WARNUNG: Fachbedeutungslisten der Datenbank nicht abgeglichen (vermdb.sh fblisten)" >&2
  exec catalina.sh run
}

# Verbindung als Rolle der Anwendung (PG…-Variablen für psql)
verbindung_anwendung() {
  aus_datei VERMDB_DB_PASSWORT
  pflicht VERMDB_DB_PASSWORT
  if [ -z "${VERMDB_DB_HOST:-}" ] && [[ "${VERMDB_DB_URL:-}" =~ ^jdbc:postgresql://([^/:?]+)(:([0-9]+))?/([^?]+) ]]; then
    export PGHOST="${BASH_REMATCH[1]}" PGPORT="${BASH_REMATCH[3]:-5432}" PGDATABASE="${BASH_REMATCH[4]}"
  else
    export PGHOST="${VERMDB_DB_HOST:-datenbank}" PGPORT="${VERMDB_DB_PORT:-5432}" PGDATABASE="${VERMDB_DB_NAME:-vermdb}"
  fi
  export PGUSER="${VERMDB_DB_BENUTZER:-vermdb}" PGPASSWORD="$VERMDB_DB_PASSWORT" PGCONNECT_TIMEOUT=10
}

# ntv2/ = eingeschaltete Gitter des Image, ergänzt bzw. ersetzt durch die eigenen;
# gitter.cfg = Einträge der eingeschalteten Gitter des Image ohne die Paare, für die es
# eigene Einträge gibt, dann die eigenen
gitter_zusammenstellen() {
  local f name eigene=/dev/null
  local -a cfgs=()
  find "$GIT" -mindepth 1 -maxdepth 1 -delete
  local auswahl="${VERMDB_GITTER_IMAGE:-}"
  for name in ${auswahl//,/ }; do
    if [ ! -f "$GIT_IMAGE/$name.cfg" ]; then
      echo "VERMDB_GITTER_IMAGE: kein Gitter $name im Image (vorhanden:" \
        "$(cd "$GIT_IMAGE" && ls -- *.cfg | sed 's/\.cfg$//' | paste -sd ' ' -))" >&2
      exit 1
    fi
    echo "Gitter des Image: $name"
    cfgs+=("$GIT_IMAGE/$name.cfg")
    for f in "$GIT_IMAGE/$name".*; do
      [ "$f" = "$GIT_IMAGE/$name.cfg" ] || ln -sfn "$f" "$GIT/"
    done
  done
  for f in "$GIT_EIGENE"/*; do
    [ -f "$f" ] || continue
    name="${f##*/}"
    [ "$name" != gitter.cfg ] || { eigene="$f"; continue; }
    case "$name" in README*) continue ;; esac
    if [ -e "$GIT/$name" ]; then
      echo "Eigene Gitterdatei ersetzt die des Image: $name"
    else
      echo "Eigene Gitterdatei: $name"
    fi
    ln -sfn "$f" "$GIT/$name"
  done
  awk -v eigene="$eigene" '
    function paar(z,   t, k) {
      sub(/\r$/, "", z)
      if (z !~ /^[ \t]*Gitterdatei[ \t]*=/) return ""
      sub(/^[^=]*=[ \t]*/, "", z)
      k = split(z, t, "@")
      if (k < 3) return ""
      return (t[1] < t[2]) ? t[1] "@" t[2] : t[2] "@" t[1]
    }
    FILENAME == eigene { sub(/\r$/, ""); p = paar($0); if (p != "") e[p] = 1; zeilen[++n] = $0; next }
    { sub(/\r$/, ""); p = paar($0)
      if (p != "" && (p in e)) { print "Eigener Eintrag ersetzt den des Image: " $0; next }
      print > ziel }
    END { for (i = 1; i <= n; i++) print zeilen[i] > ziel }
  ' ziel="$GIT/gitter.cfg" "$eigene" "${cfgs[@]}"
  touch "$GIT/gitter.cfg"
  grep -h '^[[:space:]]*Gitterdatei' "$GIT/gitter.cfg" | sed 's/^/Gitter: /' || true
}

# fbliste/ = Listen des Release, ergänzt bzw. ersetzt durch die eigenen
listen_zusammenstellen() {
  local f name
  find "$FBL" -mindepth 1 -maxdepth 1 -delete
  for f in "$FBL_RELEASE"/*; do ln -s "$f" "$FBL/"; done
  for f in "$FBL_EIGENE"/*; do
    [ -f "$f" ] || continue
    name="${f##*/}"
    case "${name,,}" in *.csv) ;; *) continue ;; esac
    if [ -e "$FBL/$name" ]; then
      echo "Eigene Fachbedeutungsliste ersetzt die des Release: $name"
    else
      echo "Eigene Fachbedeutungsliste: $name"
    fi
    ln -sfn "$f" "$FBL/$name"
  done
}

# Prüfsumme über Namen und Inhalt der zusammengestellten Listen
listen_pruefsumme() {
  local f
  (cd "$FBL" && find . -mindepth 1 -maxdepth 1 -print0 | LC_ALL=C sort -z |
    while IFS= read -r -d '' f; do printf '%s ' "${f#./}"; sha256sum < "$f"; done) |
    sha256sum | cut -c 1-64
}

# Lädt die zusammengestellten Listen in die Datenbank und hält ihre Prüfsumme fest
listen_laden() {
  local summe="$1" datei="$SQL/fbliste.sql"
  if [ -n "$(find "$FBL_EIGENE" -mindepth 1 -maxdepth 1 -iname '*.csv' -print -quit 2>/dev/null)" ]; then
    datei="$(mktemp)"
    python3 -I /opt/vermdb/fbliste.py "$FBL" -o "$datei" || return 1
  fi
  echo "Fachbedeutungslisten laden"
  # Die Prüfsumme erst nach erfolgreichem Laden festhalten (start ruft dies ohne set -e auf)
  psql -q -v ON_ERROR_STOP=1 -f "$datei" || return 1
  psql -q -v ON_ERROR_STOP=1 -c "COMMENT ON TABLE oks_meta.fbliste IS 'Fachbedeutungslisten $summe'" || return 1
  [ "$datei" = "$SQL/fbliste.sql" ] || rm -f "$datei"
  psql -Atc "SELECT 'Listen: ' || count(DISTINCT fbversion) || ', Einträge: ' || count(*) FROM oks_meta.fbliste"
}

# Lädt die Listen nur, wenn die Datenbank einen anderen Stand hat
listen_abgleichen() {
  verbindung_anwendung
  local summe vorhanden
  summe="$(listen_pruefsumme)" || return 1
  vorhanden="$(psql -Atc "SELECT coalesce(obj_description('oks_meta.fbliste'::regclass, 'pg_class'), '')")" || return 1
  if [ "$vorhanden" = "Fachbedeutungslisten $summe" ]; then
    echo "Fachbedeutungslisten der Datenbank sind aktuell"
  else
    listen_laden "$summe"
  fi
}

kennwoerter() {
  local verz="${VERMDB_KENNWOERTER:-/kennwoerter}"
  umask 077
  # Superuser für das PostgreSQL-Image (Benutzer postgres, UID 999), Anwendung für
  # dieses Image (Benutzer vermdb, UID 10001)
  erzeuge "$verz/superuser" 999
  erzeuge "$verz/anwendung" 10001
}

erzeuge() {
  if [ ! -s "$1" ]; then
    head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | cut -c 1-32 > "$1"
    echo "Kennwort erzeugt: $1"
  fi
  chown "$2:$2" "$1"
  chmod 400 "$1"
}

# Datenverzeichnis: Unterverzeichnisse anlegen und dem Benutzer vermdb übergeben (ein vom
# Docker angelegtes Verzeichnis des Rechners gehört sonst root). Nicht rekursiv, damit es
# auch bei vielen Dateien schnell geht.
datenverzeichnis() {
  [ "$(id -u)" = 0 ] || return 0
  mkdir -p "$DATEN/upload" "$DATEN/download" "$DATEN/log" "$DATEN/datenfehler"
  chown 10001:10001 /var/home/Daten "$DATEN" "$DATEN/upload" "$DATEN/download" "$DATEN/log" "$DATEN/datenfehler"
}

einrichten() {
  datenverzeichnis
  aus_datei VERMDB_DB_PASSWORT
  aus_datei VERMDB_DB_SUPERUSER_PASSWORT
  pflicht VERMDB_DB_PASSWORT VERMDB_DB_SUPERUSER_PASSWORT
  local rolle="${VERMDB_DB_BENUTZER:-vermdb}" db="${VERMDB_DB_NAME:-vermdb}"
  # OKSTRA-Schema passend zu okstraVersionDatabase: 2.022 → oks_2022
  local version="${VERMDB_OKSTRA_VERSION:-2.022}"
  local schema="oks_${version%%.*}${version#*.}"
  export PGHOST="${VERMDB_DB_HOST:-datenbank}" PGPORT="${VERMDB_DB_PORT:-5432}"
  export PGUSER="${VERMDB_DB_SUPERUSER:-postgres}" PGPASSWORD="$VERMDB_DB_SUPERUSER_PASSWORT"
  export PGDATABASE=postgres PGCONNECT_TIMEOUT=10

  echo "Rolle $rolle und Datenbank $db"
  # Das Kennwort der Rolle folgt bei jedem Lauf der Datei
  psql -q -v ON_ERROR_STOP=1 -v rolle="$rolle" -v pw="$VERMDB_DB_PASSWORT" -v db="$db" <<'EOF'
SELECT format('CREATE ROLE %I LOGIN', :'rolle')
  WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'rolle') \gexec
ALTER ROLE :"rolle" PASSWORD :'pw';
SELECT format('CREATE DATABASE %I', :'db')
  WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'db') \gexec
EOF

  if [ "$(psql -d "$db" -Atc "SELECT count(*) FROM pg_namespace WHERE nspname = 'oks_meta'")" = 0 ]; then
    echo "Einrichten: oks_meta und $schema"
    psql -q -d "$db" -v rolle="$rolle" -v okstra_schema="$schema" \
      -v srid="${VERMDB_DB_SRID:-25832}" -v beispiele="${VERMDB_DB_BEISPIELE:-off}" \
      -f "$SQL/einrichten.sql"
  else
    echo "oks_meta ist vorhanden"
  fi

  # Die Fachbedeutungslisten lädt der Dienst vermdb beim Start (listen_abgleichen)
}

fblisten() {
  listen_zusammenstellen
  verbindung_anwendung
  listen_laden "$(listen_pruefsumme)"
}

case "${1:-start}" in
  start) start ;;
  kennwoerter) kennwoerter ;;
  einrichten) einrichten ;;
  fblisten) fblisten ;;
  *) echo "Unbekannter Befehl: $1 (start, kennwoerter, einrichten, fblisten)" >&2; exit 2 ;;
esac
