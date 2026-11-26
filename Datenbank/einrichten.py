#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 interactive instruments GmbH
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Richtet die Datenbank der Vermessungsdatenbank ein (Linux und Windows).

Legt mit psql als Superuser die Rolle der Anwendung und die Datenbank an, führt
einrichten.sql aus und lädt die Fachbedeutungslisten (fbliste.py). Vorher prüft es, ob
PostGIS installiert ist. Bereits Vorhandenes bleibt: eine vorhandene Rolle samt Kennwort,
eine schon eingerichtete Datenbank (dann werden nur die Fachbedeutungslisten neu geladen).
Die Kennwörter fragt das Skript ab, wenn PGPASSWORD bzw. VERMDB_DB_PASSWORT nicht gesetzt
sind; mit einem Socket-Verzeichnis als --host (unter Linux etwa als Benutzer postgres mit
--host /var/run/postgresql) meldet sich der Superuser ohne Kennwort an. Nur Standardbibliothek; psql wird im PATH und unter Windows in
C:\\Program Files\\PostgreSQL\\<Version>\\bin gesucht.

    einrichten.py [Fachbedeutungslisten ...] [--datenbank vermdb] [--rolle vermdb]
                  [--host localhost] [--port 5432] [--superuser postgres] [--psql <Pfad>]
                  [--okstra-schema oks_2022] [--srid 25832] [--ohne-beispiele]

Fachbedeutungslisten: Ressourcen.zip des OKLABI-Release oder Verzeichnisse und Zip-Archive
mit eigenen Listen; Vorgabe ist Ressourcen.zip in oklabi-release/ (werkzeuge/oklabi.py).
"""

import argparse
import getpass
import os
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HIER = Path(__file__).resolve().parent
REPOSITORY = HIER.parent
NAME = re.compile(r"^[a-z_][a-z0-9_]*$")


class Fehler(Exception):
    pass


def finde_psql(angegeben):
    if angegeben:
        return angegeben
    gefunden = shutil.which("psql")
    if gefunden:
        return gefunden
    if os.name == "nt":
        basis = Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "PostgreSQL"
        kandidaten = [p for p in basis.glob("*/bin/psql.exe")]
        kandidaten.sort(key=lambda p: int(p.parent.parent.name) if p.parent.parent.name.isdigit() else 0)
        if kandidaten:
            return str(kandidaten[-1])
    raise Fehler("psql nicht gefunden. Pfad mit --psql angeben, unter Windows etwa "
                 r'--psql "C:\Program Files\PostgreSQL\18\bin\psql.exe".')


class Psql:
    def __init__(self, programm, host, port, benutzer, kennwort):
        self.programm = programm
        self.basis = [programm, "-X", "-q", "-v", "ON_ERROR_STOP=1", "-h", host, "-p", str(port),
                      "-U", benutzer]
        self.env = dict(os.environ, PGPASSWORD=kennwort, PGCLIENTENCODING="UTF8",
                        PGCONNECT_TIMEOUT="10")

    def __call__(self, datenbank, *argumente, eingabe=None):
        r = subprocess.run(self.basis + ["-d", datenbank, *argumente], input=eingabe,
                           capture_output=True, env=self.env, encoding="utf-8", errors="replace")
        if r.returncode != 0:
            raise Fehler(r.stderr.strip() or f"psql endete mit {r.returncode}")
        return r.stdout.strip()

    def wert(self, datenbank, sql):
        return self(datenbank, "-At", "-c", sql)


def literal(text):
    return "'" + text.replace("'", "''") + "'"


def kennwort_der_rolle(rolle):
    kennwort = os.environ.get("VERMDB_DB_PASSWORT")
    if kennwort:
        return kennwort, False
    while True:
        kennwort = getpass.getpass(f"Neues Kennwort für die Rolle {rolle} (leer: erzeugen): ")
        if not kennwort:
            return secrets.token_urlsafe(18), True
        if getpass.getpass("Kennwort wiederholen: ") == kennwort:
            return kennwort, False
        print("Die Eingaben stimmen nicht überein.")


def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("fbliste", nargs="*", type=Path,
                   help="Ressourcen.zip oder Verzeichnisse/Zip-Archive mit Fachbedeutungslisten")
    p.add_argument("--datenbank", default="vermdb")
    p.add_argument("--rolle", default="vermdb", help="Rolle, mit der sich die Anwendung anmeldet")
    p.add_argument("--host", default="localhost", help="Rechner oder Socket-Verzeichnis")
    p.add_argument("--port", default=5432, type=int)
    p.add_argument("--superuser", default="postgres")
    p.add_argument("--psql", help="Pfad zu psql bzw. psql.exe")
    p.add_argument("--okstra-schema", default="oks_2022",
                   help="passend zum Init-Parameter okstraVersionDatabase (2.022 → oks_2022)")
    p.add_argument("--srid", default=25832, type=int, help="Lagesystem der Datenbank")
    p.add_argument("--ohne-beispiele", action="store_true",
                   help="keine Beispielwerte für Gebiete und FB-Ausschlüsse")
    a = p.parse_args()

    for name in (a.datenbank, a.rolle, a.okstra_schema):
        if not NAME.match(name):
            raise Fehler(f"Name {name!r}: nur Kleinbuchstaben, Ziffern und _")
    quellen = a.fbliste or [REPOSITORY / "oklabi-release" / "Ressourcen.zip"]
    for q in quellen:
        if not q.exists():
            raise Fehler(f"{q} fehlt. Ressourcen.zip des OKLABI-Release angeben "
                         "(werkzeuge/oklabi.py legt sie in oklabi-release/ ab).")

    programm = finde_psql(a.psql)
    print(f"psql: {programm}")
    socket = a.host.startswith("/")  # Unix-Socket: Anmeldung ohne Kennwort (peer)
    su_kennwort = os.environ.get("PGPASSWORD") or ("" if socket else getpass.getpass(
        f"Kennwort des Superusers {a.superuser} (bei der Installation von PostgreSQL vergeben): "))
    psql = Psql(programm, a.host, a.port, a.superuser, su_kennwort)

    print(f"PostgreSQL {psql.wert('postgres', 'SHOW server_version')} auf {a.host}:{a.port}")
    postgis = psql.wert("postgres", "SELECT default_version FROM pg_available_extensions "
                                    "WHERE name = 'postgis'")
    if not postgis:
        raise Fehler("PostGIS ist für diese PostgreSQL-Installation nicht installiert. Unter "
                     "Windows mit dem Stack Builder nachinstallieren (Spatial Extensions, PostGIS "
                     "für genau diese PostgreSQL-Version), unter Linux das Paket der Distribution, "
                     "etwa postgresql-<Version>-postgis-3.")
    print(f"PostGIS {postgis} verfügbar")

    # Rolle
    erzeugt = False
    if psql.wert("postgres", f"SELECT 1 FROM pg_roles WHERE rolname = {literal(a.rolle)}"):
        print(f"Rolle {a.rolle} ist vorhanden; Kennwort bleibt unverändert")
        kennwort = None
    else:
        kennwort, erzeugt = kennwort_der_rolle(a.rolle)
        psql("postgres", eingabe=f"CREATE ROLE {a.rolle} LOGIN PASSWORD {literal(kennwort)};\n")
        print(f"Rolle {a.rolle} angelegt")

    # Datenbank
    if psql.wert("postgres", f"SELECT 1 FROM pg_database WHERE datname = {literal(a.datenbank)}"):
        print(f"Datenbank {a.datenbank} ist vorhanden")
    else:
        psql("postgres", "-c", f"CREATE DATABASE {a.datenbank}")
        print(f"Datenbank {a.datenbank} angelegt")

    if psql.wert(a.datenbank, "SELECT 1 FROM pg_namespace WHERE nspname = 'oks_meta'"):
        print("oks_meta ist vorhanden; die Datenbank ist schon eingerichtet")
    else:
        print(f"Einrichten: oks_meta und {a.okstra_schema}", flush=True)
        try:
            psql(a.datenbank, "-v", f"rolle={a.rolle}", "-v", f"okstra_schema={a.okstra_schema}",
                 "-v", f"srid={a.srid}", "-v", f"beispiele={'off' if a.ohne_beispiele else 'on'}",
                 "-f", str(HIER / "einrichten.sql"))
        except Fehler as f:
            if "postgis" in str(f).lower():
                raise Fehler(f"{f}\n\nPostGIS ist installiert, lässt sich aber nicht laden. Meist "
                             "passt das PostGIS-Paket nicht zur PostgreSQL-Version oder eine "
                             "Bibliothek fehlt, von der PostGIS abhängt. PostGIS für genau diese "
                             "PostgreSQL-Version neu installieren; die Meldung oben nennt die "
                             "Datei, die sich nicht laden ließ.") from None
            raise

    # Fachbedeutungslisten
    with tempfile.TemporaryDirectory() as tmp:
        sql = Path(tmp) / "fbliste.sql"
        r = subprocess.run([sys.executable, str(HIER / "fbliste.py"), *map(str, quellen),
                            "-o", str(sql)], capture_output=True, encoding="utf-8", errors="replace")
        if r.returncode != 0:
            raise Fehler(r.stderr.strip())
        print(f"Fachbedeutungslisten: {r.stderr.strip()}", flush=True)
        psql(a.datenbank, "-f", str(sql))
    print("Fachbedeutungslisten geladen")

    print()
    print("Fertig. Für die Datenquellen in der server.xml des Tomcat:")
    print(f"  url:      jdbc:postgresql://{'localhost' if socket else a.host}:{a.port}/{a.datenbank}")
    print(f"  username: {a.rolle}")
    if erzeugt:
        print(f"  password: {kennwort}   (erzeugt; jetzt notieren)")
    elif kennwort:
        print("  password: das angegebene Kennwort")
    else:
        print("  password: das Kennwort der vorhandenen Rolle")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Fehler as f:
        print(f"Fehler: {f}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
