-- SPDX-FileCopyrightText: 2026 interactive instruments GmbH
-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Beispielwerte für oks_meta.gebiete und oks_meta.fbausschluss. Sie zeigen das Format;
-- für den Betrieb durch eigene Werte ersetzen. Die Gebiete sind in geografischen
-- Koordinaten angegeben und werden in die SRID der Datenbank umgerechnet.

INSERT INTO oks_meta.gebiete (name, the_geom) VALUES
    ('Beispiel Deutschland', ST_Transform(ST_GeomFromText(
        'MULTIPOLYGON(((5.8 47.2, 15.1 47.2, 15.1 55.1, 5.8 55.1, 5.8 47.2)))', 4326), :srid)),
    ('Beispiel Mitte', ST_Transform(ST_GeomFromText(
        'MULTIPOLYGON(((9.5 50.5, 12.5 50.5, 12.5 53.0, 9.5 53.0, 9.5 50.5)))', 4326), :srid));

-- blocked: Ist der Init-Parameter okstraFblisteExclusion gesetzt, gilt beim Prüfen und
-- Laden ein Objekt mit dieser Fachbedeutung (bei dieser Symbolart) als fehlerhaft; für
-- das Projektgebiet zählt es nicht. no_import wertet die Vermessungsdatenbank nicht aus.
INSERT INTO oks_meta.fbausschluss (no_import, fachbedeutung, bezeichnung, symbolart, blocked) VALUES
    (NULL, '0162.F002', 'Referenzstation', 'Punkt', true),
    (NULL, '9291.F005', 'Parkbügel', 'Punkt', false);
