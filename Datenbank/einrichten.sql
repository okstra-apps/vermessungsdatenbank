-- SPDX-FileCopyrightText: 2026 interactive instruments GmbH
-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Richtet eine leere Datenbank für die Vermessungsdatenbank ein (siehe README.md).
--
--   psql -v rolle=<Benutzer> [-v okstra_schema=oks_2022] [-v srid=25832] [-v beispiele=off] \
--        -d <Datenbank> -f Datenbank/einrichten.sql
--
-- Aufzurufen als Superuser (Erweiterung PostGIS, Rechte, search_path). Die Objekte
-- gehören danach der Rolle; die Rolle selbst muss vorher angelegt sein.

\set ON_ERROR_STOP on
\if :{?rolle}
\else
    \echo 'Bitte die Rolle der Anwendung angeben: psql -v rolle=<Benutzer> ...'
    \quit
\endif
\if :{?okstra_schema}
\else
    \set okstra_schema oks_2022
\endif
\if :{?srid}
\else
    \set srid 25832
\endif
\if :{?beispiele}
\else
    \set beispiele on
\endif

BEGIN;

CREATE EXTENSION IF NOT EXISTS postgis;

-- OkstraSqlUtil legt im OKSTRA-Schema beim ersten Laden selbst Tabellen an
GRANT CREATE ON DATABASE :"DBNAME" TO :"rolle";
-- Die Service-Verbindung fragt Tabellen in oks_meta ohne Schema ab
ALTER ROLE :"rolle" IN DATABASE :"DBNAME" SET search_path TO "$user", public, oks_meta;

SET ROLE :"rolle";
CREATE SCHEMA :"okstra_schema";
\ir 10-tabellen.sql
\ir 20-funktionen.sql
\ir 30-efbversion.sql
\ir 31-ekoordrefsys.sql
\if :beispiele
\ir 40-beispiele.sql
\endif
RESET ROLE;

COMMIT;
\echo 'Datenbank eingerichtet; Fachbedeutungslisten mit Datenbank/fbliste.py laden (im Container lädt sie der Dienst beim Start).'
