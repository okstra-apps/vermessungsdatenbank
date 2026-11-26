-- SPDX-FileCopyrightText: 2014-2026 interactive instruments GmbH
-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Tabellen und Sichten im Schema oks_meta. Wird von einrichten.sql eingebunden
-- (psql-Variable srid).

CREATE SCHEMA oks_meta;

-- Warteschlange: ein Eintrag je Auftrag (Prüfen, Laden, Löschen), mit Protokollen
CREATE TABLE oks_meta.warteschlange (
    auftragsnummer serial PRIMARY KEY,
    original text,
    lokal text,
    erfasst timestamp without time zone,
    version integer[],
    status integer DEFAULT 0,
    pruefprotokoll text DEFAULT '',
    migrationprotokoll text DEFAULT '',
    ladeprotokoll text DEFAULT '',
    fbversion text[],
    srs integer,
    beendet timestamp without time zone,
    bearbeitung boolean DEFAULT false,
    stichtag date,
    format text NOT NULL,
    datenfehler text DEFAULT '',
    loeschung boolean DEFAULT false,
    sessionid text DEFAULT '',
    "timestamp" text DEFAULT '',
    projekt text DEFAULT '',
    username text DEFAULT ''
);

-- Projekte und geladene Aufträge mit ihrem Gebiet; gepflegt vom Trigger
-- warteschlange_bu (20-funktionen.sql)
CREATE TABLE oks_meta.projekt (
    projekt text PRIMARY KEY,
    geometrie public.geometry(MultiPolygonZ, :srid),
    id serial NOT NULL
);

CREATE TABLE oks_meta.auftrag (
    auftragsnummer integer PRIMARY KEY,
    projekt text REFERENCES oks_meta.projekt (projekt) MATCH FULL ON UPDATE CASCADE ON DELETE SET NULL,
    sessionid text DEFAULT '',
    dateiname text,
    erfasst timestamp without time zone,
    beendet timestamp without time zone,
    version integer[],
    fbversion text,
    fbversionid integer,
    origcrs text,
    origcrsid integer DEFAULT 0,
    orighs text,
    orighsid integer DEFAULT 1,
    stichtag date,
    username text DEFAULT '',
    geometrie public.geometry(MultiPolygonZ, :srid)
);
CREATE INDEX auftrag_projekt_idx ON oks_meta.auftrag (projekt);

-- Versionen der Fachbedeutungslisten und deren Inhalt (fbliste.py)
CREATE TABLE oks_meta.efbversion (
    id integer PRIMARY KEY,
    hauptversion integer NOT NULL,
    unterversion integer NOT NULL,
    bundesland text NOT NULL,
    lfdnr_vsn integer
);

CREATE VIEW oks_meta.v_fbversion AS
 SELECT id, hauptversion, unterversion, bundesland,
    'Fachbedeutungen ' ||
        CASE WHEN length(bundesland) = 0 THEN '' ELSE bundesland || ' ' END ||
        hauptversion::text || '.' || unterversion::text AS langtext
   FROM oks_meta.efbversion;

CREATE TABLE oks_meta.fbliste (
    fbversion integer,
    datum text,
    fachbedeutung text,
    ras_verm text,
    bezeichnung text,
    symbolart text,
    landescode text
);
CREATE INDEX fbliste_idx ON oks_meta.fbliste (fachbedeutung, fbversion, symbolart);

-- Gesperrte Fachbedeutungen (blocked): beim Prüfen und Laden ein Fehler, wenn
-- okstraFblisteExclusion gesetzt ist; zählen nicht für das Projektgebiet
CREATE TABLE oks_meta.fbausschluss (
    no_import text,
    fachbedeutung text,
    bezeichnung text,
    symbolart text,
    blocked boolean
);

-- Lage- und Höhensysteme mit dem Text der OKLABI (31-ekoordrefsys.sql)
CREATE TABLE oks_meta.ekoordrefsys (
    code integer PRIMARY KEY,
    wert text NOT NULL
);

-- Kopfdaten der Fachobjekte. OkstraSqlUtil schreibt sie mit Klartexten in die Sicht
-- _kopfdaten_; deren Trigger legt die Texte in txtpool ab und speichert in _header_
-- nur Kennungen.
CREATE TABLE oks_meta.txtpool (
    id integer NOT NULL UNIQUE,
    wert text PRIMARY KEY
);

CREATE TABLE oks_meta._header_ (
    foid bigint NOT NULL UNIQUE,
    gueltigvon date NOT NULL,
    gueltigbis date,
    guid text NOT NULL,
    klassenname integer NOT NULL REFERENCES oks_meta.txtpool (id),
    fbversion integer NOT NULL REFERENCES oks_meta.efbversion (id),
    origcrs integer NOT NULL REFERENCES oks_meta.ekoordrefsys (code),
    orighs integer NOT NULL REFERENCES oks_meta.ekoordrefsys (code),
    kategorie integer NOT NULL,
    gesperrtdurch text NOT NULL,
    projekt integer REFERENCES oks_meta.txtpool (id),
    auftrag integer,
    oksversion integer,
    CHECK (gueltigbis IS NULL OR gueltigbis >= gueltigvon)
);
CREATE INDEX _header_auftrag_idx ON oks_meta._header_ (auftrag);

CREATE VIEW oks_meta._kopfdaten_ AS
 SELECT h.foid,
    h.gueltigvon,
    h.gueltigbis,
    h.guid,
    t1.wert AS klassenname,
    fb.langtext AS fbversion,
    k1.wert AS origcrs,
    k2.wert AS orighs,
    t2.wert AS kategorie,
    h.gesperrtdurch,
    t3.wert AS projekt,
    h.auftrag,
    h.oksversion
   FROM oks_meta._header_ h
     LEFT JOIN oks_meta.txtpool t1 ON t1.id = h.klassenname
     LEFT JOIN oks_meta.txtpool t2 ON t2.id = h.kategorie
     LEFT JOIN oks_meta.txtpool t3 ON t3.id = h.projekt
     LEFT JOIN oks_meta.v_fbversion fb ON fb.id = h.fbversion
     LEFT JOIN oks_meta.ekoordrefsys k1 ON k1.code = h.origcrs
     LEFT JOIN oks_meta.ekoordrefsys k2 ON k2.code = h.orighs;

-- Gebiete für die Abfrage Areas und das Entladen nach Gebiet
CREATE TABLE oks_meta.gebiete (
    oid serial PRIMARY KEY,
    name text,
    the_geom public.geometry(Geometry, :srid)
);
CREATE INDEX gebiete_name_idx ON oks_meta.gebiete (name);

-- Auftragslisten der Anfragen FullQueue (alle Einträge) und Queue (ohne gelöschte)
CREATE VIEW oks_meta.fullqueue AS
 SELECT w.auftragsnummer AS "Auftrag",
    (SELECT h.projekt FROM oks_meta.auftrag h WHERE h.auftragsnummer = w.auftragsnummer) AS "Projekt",
    w.sessionid AS "Session-ID",
    w.username AS "Benutzername",
    w."timestamp" AS "Zeitstempel",
    w.original AS "Name",
        CASE
            WHEN w.bearbeitung THEN
            CASE
                WHEN w.status = 0 THEN 'laden, prüfen'
                WHEN w.status >= 1000 AND w.status < 10000 THEN 'migrieren'
                WHEN w.status = -5 THEN 'abgelehnt'
                WHEN w.status = -8 THEN 'Ladefehler'
                WHEN w.status < 0 THEN 'gelöscht'
                ELSE 'übertragen'
            END
            ELSE
            CASE
                WHEN w.status = -5 THEN 'abgelehnt'
                WHEN w.status = -8 THEN 'Ladefehler'
                WHEN w.status < 0 THEN 'gelöscht'
                WHEN w.beendet IS NULL THEN 'wartet'
                WHEN w.status >= 1000000 THEN
                CASE
                    WHEN mod(w.status, 10) = 1 THEN 'geladen, mit Meldungen'
                    WHEN mod(w.status, 10) = 5 THEN 'geprüft'
                    WHEN mod(w.status, 10) = 6 THEN 'geprüft, mit Meldungen'
                    ELSE 'geladen'
                END
                ELSE 'abgebrochen'
            END
        END AS "Status",
    w.erfasst AS eingestellt,
    w.stichtag AS "Stichtag",
    w.format AS "Format",
        CASE WHEN w.loeschung THEN 'ja' ELSE 'nein' END AS "Löschung",
        CASE
            WHEN w.version[3] = 0 THEN w.version[1]::text || '.' ||
                CASE WHEN w.version[2] < 10 THEN '00' WHEN w.version[2] < 100 THEN '0' ELSE '' END ||
                w.version[2]::text
            ELSE w.version[1]::text || '.' ||
                CASE WHEN w.version[2] < 10 THEN '00' WHEN w.version[2] < 100 THEN '0' ELSE '' END ||
                w.version[2]::text || '.' || w.version[3]::text
        END AS "Version",
    w.fbversion[1] || '.' || w.fbversion[2] || ' ' || w.fbversion[3] AS "Fachbedeutungen",
    (SELECT ksys.wert FROM oks_meta.ekoordrefsys ksys WHERE ksys.code = w.srs % 1000000) AS "KoordRefSys",
    (SELECT ksys.wert FROM oks_meta.ekoordrefsys ksys WHERE ksys.code = w.srs / 1000000) AS "Höhensystem",
        CASE WHEN w.beendet IS NULL THEN '' ELSE w.beendet::text END AS beendet,
    w.pruefprotokoll AS "Laden",
    w.migrationprotokoll AS "Migration",
    w.ladeprotokoll AS "Übertragung",
    w.datenfehler AS "Datenfehler"
   FROM oks_meta.warteschlange w;

CREATE VIEW oks_meta.queue AS
 SELECT q.*
   FROM oks_meta.fullqueue q
   JOIN oks_meta.warteschlange w ON w.auftragsnummer = q."Auftrag"
  WHERE (w.status >= 0 OR w.status IN (-8, -5)) AND NOT w.loeschung;
