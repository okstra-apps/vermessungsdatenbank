-- SPDX-FileCopyrightText: 2014-2026 interactive instruments GmbH
-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Funktionen und Trigger in oks_meta. Wird von einrichten.sql eingebunden
-- (psql-Variablen okstra_schema und srid). Funktionen, die das OKSTRA-Schema oder die
-- SRID enthalten, entstehen über \gexec mit Platzhaltern, weil psql in
-- Funktionsrümpfen keine Variablen ersetzt.

-- Kopfdaten: Klartexte aus OkstraSqlUtil in Kennungen übersetzen
CREATE FUNCTION oks_meta._header_trigger() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
   insert into oks_meta.txtpool(wert,id) values (new.klassenname,(select count(*) as cnt from oks_meta.txtpool)+1) ON CONFLICT DO NOTHING;
   insert into oks_meta.txtpool(wert,id) values ((case when new.kategorie is null then '' else new.kategorie end),(select count(*) as cnt from oks_meta.txtpool)+1) ON CONFLICT DO NOTHING;
   insert into oks_meta.txtpool(wert,id) values (new.projekt,(select count(*) as cnt from oks_meta.txtpool)+1) ON CONFLICT DO NOTHING;
   insert into oks_meta._header_ values (
     new.foid,
     new.gueltigvon,
     new.gueltigbis,
     new.guid,
     (select id from oks_meta.txtpool t1 where t1.wert = new.klassenname),
     (select id from oks_meta.v_fbversion fv where fv.langtext = (case when new.fbversion is null then 'Fachbedeutungen 1.6' else new.fbversion end)),
     (select code from oks_meta.ekoordrefsys ek where ek.wert = new.origcrs),
     (select code from oks_meta.ekoordrefsys ek where ek.wert = new.orighs),
     (select id from oks_meta.txtpool t2 where t2.wert = (case when new.kategorie is null then '' else new.kategorie end)),
     new.gesperrtdurch,
     (select id from oks_meta.txtpool t3 where t3.wert = new.projekt),
     new.auftrag,
     new.oksversion);
return new;
END
$$;

CREATE TRIGGER oks_meta_header_insert INSTEAD OF INSERT ON oks_meta._kopfdaten_
    FOR EACH ROW EXECUTE FUNCTION oks_meta._header_trigger();

-- Projektgebiet eines Auftrags: konkave Hülle der Geometrien ausgewählter Objektarten,
-- ohne die in fbausschluss gesperrten Fachbedeutungen
SELECT replace(replace($funktion$
CREATE FUNCTION oks_meta.ermittle_projektgeometrie_table(auftragsnr integer, schop text, tab text, col text, typ text, pnt integer, res public.geometry) RETURNS public.geometry
    LANGUAGE plpgsql COST 200
    AS $$
DECLARE
    geom geometry;
    sch text;
    cnt integer;
    points text;
    pointe text;
    stmt text;
BEGIN
    -- Funktion zur Ermittlung von Projektgeometrien
    sch := schop || '.';
    --RAISE NOTICE 'Auftrag %, Tabelle %', auftragsnr, tab;
    EXECUTE 'SELECT count(table_name) FROM information_schema.tables WHERE table_schema=''' ||
          schop || ''' AND table_type=''BASE TABLE'' AND table_name=''' ||
          lower(tab) || '''' into cnt;
    IF cnt > 0 THEN
        IF pnt > 0
        THEN
            points := 'ST_Points(';
            pointe := ')';
        ELSE
            points := '';
            pointe := '';
        END IF;
        stmt := 'SELECT ST_ConcaveHull(ST_Collect(' || points || 'ST_Force2d(t.' || col || ')' || pointe || '), 0.8) ' ||
                'FROM ' || sch || tab || ' t WHERE t._jobnr_ = ' || auftragsnr;
        IF typ IS NOT NULL
        THEN
            stmt := stmt || ' AND t.fachliche_bedeutung NOT IN ' ||
             '(SELECT fachbedeutung FROM oks_meta.fbausschluss a ' ||
             'WHERE a.symbolart = ''' || typ || ''' AND a.blocked)';
        END IF;
        --RAISE NOTICE 'Statement %', stmt;
        EXECUTE stmt into geom;

        IF geom IS NOT NULL
        THEN
            geom := st_buffer(geom, 1.0);
            --RAISE NOTICE 'Daten gefunden für Tabelle %, Anzahl %', tab, st_numgeometries(geom);
            IF res IS NULL
            THEN
                res := geom;
            ELSE
                res := ST_Union(res, geom);
            END IF;
        END IF;

        IF lower(tab) = 'baum'
        THEN
            EXECUTE 'SELECT count(table_name) FROM information_schema.tables WHERE table_schema=''' ||
                  schop || ''' AND table_type=''BASE TABLE'' AND table_name=''' ||
                  'multigeometrie' || '''' into cnt;
            IF cnt > 0 THEN
                stmt := 'SELECT ST_ConcaveHull(ST_Collect(' || points || 'ST_Force2d(t.' || col || ')' || pointe || '), 0.8) ' ||
                        'FROM ' || sch || tab || ' t, ' || sch || 'zwischentab z, ' || sch || 'multigeometrie m ' ||
                        'WHERE t._jobnr_ = ' || auftragsnr ||
                        ' AND z.id = t.oid and z.role = ''Multigeometrie'' and m.oid = z.rid';
                 --RAISE NOTICE 'Statement %', stmt;
                EXECUTE stmt into geom;
        
                IF geom IS NOT NULL
                THEN
                    geom := st_buffer(geom, 1.0);
                    --RAISE NOTICE 'Daten gefunden für Tabelle multigeometrie, Anzahl %', st_numgeometries(geom);
                    IF res IS NULL
                    THEN
                        res := geom;
                    ELSE
                        res := ST_Union(res, geom);
                    END IF;
                END IF;
            END IF;
        END IF;
    ELSE
        RETURN res;
    END IF;

    IF res IS NOT NULL
    THEN
        res := ST_Force3D(ST_SetSRID(ST_Multi(res), @SRID@));
    END IF;

    RETURN res;
END;
$$;
$funktion$, '@OKSTRA_SCHEMA@', :'okstra_schema'), '@SRID@', :'srid') \gexec

SELECT replace(replace($funktion$
CREATE FUNCTION oks_meta.ermittle_projektgeometrie(auftragsnr integer) RETURNS public.geometry
    LANGUAGE plpgsql COST 200
    AS $$
DECLARE
    res geometry;
    schop text;
BEGIN
    -- Funktion zur Ermittlung von Projektgeometrien
    --RAISE NOTICE 'Auftrag %', auftragsnr;
    schop := '@OKSTRA_SCHEMA@';
    res := oks_meta.ermittle_projektgeometrie_table(
        auftragsnr, schop, 'allgemeines_Punktobjekt', 'punktgeometrie', 'Punkt', 0, NULL);
    res := oks_meta.ermittle_projektgeometrie_table(
        auftragsnr, schop, 'Achshauptpunkt', 'punktgeometrie', NULL, 0, res);
    res := oks_meta.ermittle_projektgeometrie_table(
        auftragsnr, schop, 'Baum', 'geometrie_punktobjekt', NULL, 0, res);
    res := oks_meta.ermittle_projektgeometrie_table(
        auftragsnr, schop, 'allgemeines_Linienobjekt', 'liniengeometrie', 'Linie', 0, res);
    res := oks_meta.ermittle_projektgeometrie_table(
        auftragsnr, schop, 'Achse', 'geolinie', NULL, 0, res);
    res := oks_meta.ermittle_projektgeometrie_table(
        auftragsnr, schop, 'Achselement', 'geolinie', NULL, 0, res);
    res := oks_meta.ermittle_projektgeometrie_table(
        auftragsnr, schop, 'allgemeines_Flaechenobjekt', 'flaechengeometrie', 'Flaeche', 1, res);
    RETURN res;
END;
$$;
$funktion$, '@OKSTRA_SCHEMA@', :'okstra_schema'), '@SRID@', :'srid') \gexec

-- Spalten _jobnr_, _fbversion_, _fbtext_ in den Datentabellen eines Auftrags setzen
CREATE FUNCTION oks_meta.pflege_datentabelle(meta text, schema text, projekt text, auftrag integer, fbversion integer, force integer, verbos integer) RETURNS integer
    LANGUAGE plpgsql COST 50
    AS $$
DECLARE
   md text;
   sch text;
   schop text;
   proj text;
   zwtab text;
   anz integer;
   arr text[];
   zwa text[];
   tab text;
   cmd text;
   cnt integer;
   lng integer;
   prop text;
   gtyp text;
   fbcmd text;
   fb16 integer;
BEGIN
   fb16 := coalesce((SELECT id FROM oks_meta.v_fbversion WHERE langtext = 'Fachbedeutungen 1.6'), -1);
   IF meta IS NULL THEN
      md := '';
   ELSE
      md := meta || '.';
   END IF;
   IF schema IS NULL THEN
      sch := '';
      schop := '';
   ELSE
      sch := schema || '.';
      schop := schema;
   END IF;
   IF force > 0 THEN
      proj := '';
   ELSE
      proj := 'projekt = ''' || projekt || ''' AND ';
   END IF;

   -- Tabellennamen besorgen aus den Kopfdaten
   EXECUTE 'with foo as (SELECT distinct(klassenname) as tabelle FROM ' || md ||
      '_kopfdaten_ where ' || proj || 'auftrag = ' || auftrag::text || ') ' ||
      'select array_agg(tabelle) from foo' INTO arr;
   
   -- Mehrfachaufrufe der Funktion oder fehlende Daten durchlassen
   lng := 0;
   IF arr IS NOT NULL THEN
      lng := array_length(arr,1);
   END IF;
   IF lng = 0 THEN
      IF verbos > 0 THEN
         RAISE NOTICE 'Keine Daten gefunden für Projekt %, Auftrag %', projekt, auftrag;
      END IF;
      return lng::integer;
   END IF;

   -- Über alle Tabellen laufen und die Auftragsnummern setzen
   FOREACH tab IN ARRAY arr LOOP
      cnt := 0;
      cmd := '';
      -- Prüfe Datentabelle auf Existenz. Es können Einträge von temporären
      -- Geometrieobjekten auftauchen, dazu gibt es eventuell keine Datentabelle
      EXECUTE 'SELECT count(table_name) FROM information_schema.tables WHERE table_schema=''' ||
          schop || ''' AND table_type=''BASE TABLE'' AND table_name=''' ||
          lower(tab) || '''' into cnt;
      if cnt > 0 then
          -- Datentabelle existiert, betroffene Einträge anpassen
          IF verbos > 0 THEN
             RAISE NOTICE 'Setze Auftragsnummer Datentabelle % für Projekt %, Auftrag %', tab, projekt, auftrag;
          END IF;
          fbcmd := '';
          EXECUTE 'SELECT eigenschaft FROM ' || sch || '_fb_ WHERE objektart = ''' || lower(tab) || '''' into prop;
          IF prop IS NOT NULL THEN
             EXECUTE 'SELECT typ FROM ' || sch || '_fb_ WHERE objektart = ''' || lower(tab) || '''' into gtyp;
             IF gtyp IS NOT NULL then
                -- Fachbedeutungstext suchen
                fbcmd := ', _fbversion_ = ' || fbversion::text || 
                         ', _fbtext_ = (SELECT bezeichnung FROM ' || md || 'fbliste WHERE fbversion = ' || fbversion::text ||
                         ' AND symbolart = ''' || gtyp || ''' AND fachbedeutung = ' || prop || ') ';
                cmd := 'ALTER TABLE ' || sch || tab ||
                       ' ADD COLUMN IF NOT EXISTS _fbversion_ integer NOT NULL DEFAULT ' || fb16::text || ';' ||
                       ' ALTER TABLE ' || sch || tab ||
                       ' ADD COLUMN IF NOT EXISTS _fbtext_ text; ';
             END IF;
          END IF;
          cmd := cmd || 'ALTER TABLE ' || sch || tab ||
                 ' ADD COLUMN IF NOT EXISTS _jobnr_ integer NOT NULL DEFAULT -1;' ||
                 ' CREATE INDEX IF NOT EXISTS ' || tab || '_jobnr_idx'
                 ' ON ' || sch || tab || ' USING btree (_jobnr_);' ||
                 ' update ' || sch || tab || ' set _jobnr_ = ' || auftrag::text ||
                 fbcmd ||
                 ' where oid in ' ||
                 '(select foid::text from ' || md || '_header_ where ' ||
                 'klassenname = (select id from oks_meta.txtpool where wert = ''' || tab || ''') and ' || proj ||
                 'auftrag = ' || auftrag::text || ');';
      elsif verbos > 0 then
          RAISE NOTICE 'Datentabelle % fehlt für Projekt %, Auftrag %', tab, projekt, auftrag;
      end if;
      IF verbos > 1 THEN
         RAISE NOTICE '   Befehl % ', cmd;
      END IF;
      EXECUTE cmd;
   END LOOP;

   RETURN lng::integer;
END;
$$;

-- Nach dem Laden eines Auftrags: Projekt und Auftrag anlegen oder fortschreiben,
-- Datentabellen pflegen, Gebiete berechnen; bei Löschung den Auftrag entfernen
SELECT replace(replace($funktion$
CREATE FUNCTION oks_meta.erzeuge_projektdaten() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    AS $$
DECLARE
    anz integer;
    projektbez text;
    usernm text;
    crs text;
    hs text;
    fbl text;
    crsid integer;
    hsid integer;
    fblid integer;
    gebiet geometry;
BEGIN
    -- Loeschung von Auftraegen beruecksichtigen:
    IF NEW.loeschung = TRUE
    THEN
        DELETE FROM oks_meta.auftrag WHERE auftragsnummer = NEW.auftragsnummer;
        RETURN NEW;
    END IF;
    
    -- Nur aktiv werden, wenn der Auftrag durchgelaufen ist:
    IF NEW.beendet IS NULL
    THEN
        RETURN NEW;
    END IF;
    
    projektbez = k.projekt FROM oks_meta."_kopfdaten_" k WHERE k.auftrag = NEW.auftragsnummer LIMIT 1;
    IF projektbez IS NOT NULL THEN
       -- Kann passieren bei Fehlerabbruch vor dem Laden der Daten
       anz := COUNT(*) FROM oks_meta.projekt WHERE projekt = projektbez;
       IF anz = 0
       THEN
          -- Projektdatensatz neu anlegen
          INSERT INTO oks_meta.projekt (projekt) SELECT projektbez;
       END IF;

       anz := COUNT(*) FROM oks_meta.auftrag WHERE auftragsnummer = NEW.auftragsnummer;
       IF anz = 0
       THEN
          -- Auftragsdatensatz neu anlegen
          INSERT INTO oks_meta.auftrag (auftragsnummer, projekt) SELECT NEW.auftragsnummer, projektbez;
       END IF;

       usernm = w.username FROM oks_meta."warteschlange" w WHERE w.auftragsnummer = NEW.auftragsnummer LIMIT 1;
       crs = k.origcrs FROM oks_meta."_kopfdaten_" k WHERE k.auftrag = NEW.auftragsnummer LIMIT 1;
       hs = k.orighs FROM oks_meta."_kopfdaten_" k WHERE k.auftrag = NEW.auftragsnummer LIMIT 1;
       fbl = k.fbversion FROM oks_meta."_kopfdaten_" k WHERE k.auftrag = NEW.auftragsnummer LIMIT 1;
       EXECUTE 'SELECT e.code FROM oks_meta.ekoordrefsys e WHERE e.wert = ''' || crs::text || ''';' INTO crsid;
       IF crsid IS NULL THEN
          crsid = 0;
       END IF;
       EXECUTE 'SELECT hs.code FROM oks_meta.ekoordrefsys hs WHERE wert = ''' || hs::text || ''';' INTO hsid;
       IF hsid IS NULL THEN
          hsid = 1;
       END IF;
       EXECUTE 'SELECT vfb.id FROM oks_meta.v_fbversion vfb WHERE langtext = ''' || fbl::text || ''';' INTO fblid;
       IF fblid IS NULL THEN
          fblid = (SELECT id FROM oks_meta.v_fbversion WHERE langtext = 'Fachbedeutungen 1.6');
       END IF;
    
       anz := oks_meta.pflege_datentabelle('oks_meta', '@OKSTRA_SCHEMA@', '', NEW.auftragsnummer, fblid, 1, 0);
       UPDATE oks_meta.auftrag SET
            projekt = projektbez,
            username = usernm,
            sessionid = NEW.sessionid,
            dateiname = NEW.original,
            erfasst = new.erfasst,
            beendet = NEW.beendet,
            version = NEW.version,
            fbversion = fbl,
            origcrs = crs,
            orighs = hs,
            fbversionid = fblid,
            origcrsid = crsid,
            orighsid = hsid,
            stichtag = NEW.stichtag,
            geometrie = oks_meta.ermittle_projektgeometrie(NEW.auftragsnummer)
        WHERE auftragsnummer = NEW.auftragsnummer;

        anz := COUNT(auftragsnummer) FROM oks_meta.auftrag WHERE projekt = projektbez;
        IF anz > 1 THEN
            gebiet = st_multi(st_union(a.geometrie)) FROM oks_meta.auftrag a WHERE a.projekt = projektbez AND a.geometrie is not null;
        ELSE
            gebiet = a.geometrie FROM oks_meta.auftrag a where a.projekt = projektbez;
        END IF;
        --gebiet = st_multi(st_collect(a.geometrie)) FROM oks_meta.auftrag a WHERE a.projekt = projektbez;

        -- alt:
        --gebiet = st_multi(st_concavehull(a.geometrie, 0.8)) FROM oks_meta.auftrag a WHERE a.projekt = projektbez LIMIT 1;

        UPDATE oks_meta.projekt SET
            geometrie = gebiet
        WHERE projekt = projektbez;
    END IF;
    
    RETURN NEW;
END;
$$;
$funktion$, '@OKSTRA_SCHEMA@', :'okstra_schema'), '@SRID@', :'srid') \gexec

CREATE TRIGGER warteschlange_bu BEFORE UPDATE ON oks_meta.warteschlange
    FOR EACH ROW EXECUTE FUNCTION oks_meta.erzeuge_projektdaten();

-- Auftrag löschen (Anfrage DeleteJob): Datentabellen, Zwischentabelle, Kopfdaten,
-- Projekt und Auftrag; Status in der Warteschlange
CREATE FUNCTION oks_meta.loesche_auftrag(meta text, schema text, projekt text, auftrag integer, force integer, verbos integer) RETURNS integer
    LANGUAGE plpgsql COST 50
    AS $$

DECLARE
   md text;
   sch text;
   schop text;
   proj text;
   zwtab text;
   anz integer;
   arr text[];
   zwa text[];
   tab text;
   cmd text;
   cnt integer;
   lng integer;
   kopf text;
   projektbez text;
   gebiet geometry;
BEGIN
   IF meta IS NULL THEN
      md := '';
   ELSE
      md := meta || '.';
   END IF;
   IF schema IS NULL THEN
      sch := '';
      schop := '';
   ELSE
      sch := schema || '.';
      schop := schema;
   END IF;
   IF force > 0 THEN
      proj := '';
   ELSE
      proj := 'projekt = ''' || projekt || ''' AND ';
   END IF;

   -- Auftragstabelle bereinigen
   lng := COUNT(*) FROM oks_meta.auftrag a WHERE a.auftragsnummer = auftrag;
   anz := COUNT(*) FROM oks_meta.auftrag a WHERE a.auftragsnummer = auftrag and a.projekt IS NOT NULL and length(trim(a.projekt)) > 0;
   gebiet := null;
   cmd := null;
   IF lng > 0 THEN
      -- Es gibt den Auftrag, wie viele zum selben Projekt?
      cmd := '';
      IF anz > 0 THEN
        projektbez := a.projekt from oks_meta.auftrag a where a.auftragsnummer = auftrag;
        anz := COUNT(*) FROM oks_meta.auftrag a WHERE a.projekt = projektbez;
        IF anz = 1
        THEN
           -- 1 Eintrag: raus damit aus der Projekttabelle
           cmd := 'delete from ' || md || 'projekt p where p.projekt = ''' || projektbez || '''; ';
        END IF;
      END IF;
      -- Raus aus der Auftragstabelle, Projekttabelle danach bearbeiten (wegen FK)
      cmd := 'delete from ' || md || 'auftrag where ' || proj || 'auftragsnummer = ' || auftrag::text || '; ' || cmd;
   END IF;
   IF cmd IS NOT NULL THEN
       IF verbos > 1 THEN
          RAISE NOTICE 'Bereinige Projekttabelle für Projekt %, Auftrag % Anzahl % Befehl: %', projektbez, auftrag, anz, cmd;
       ELSEIF verbos > 0 THEN
          RAISE NOTICE 'Bereinige Projekttabelle für Projekt %, Auftrag %, Anzahl %', projektbez, auftrag, anz;
       END IF;
       EXECUTE cmd;
   END IF;
   IF anz > 1 THEN
      -- Geometrie in Projekttabelle anpassen
      IF anz > 2 THEN
         -- mehr als 1 Eintrag bleibt übrig: Auftragsgeometrien zusammenfassen
         gebiet = st_multi(st_union(a.geometrie)) FROM oks_meta.auftrag a WHERE a.projekt = projektbez;
      ELSE
         -- 1 Eintrag bleibt übrig: Auftragsgeometrie des übriggebliebenen Eintrags übernehmen
         gebiet = a.geometrie FROM oks_meta.auftrag a where a.projekt = projektbez;
      END IF;
      IF verbos > 1 THEN
         RAISE NOTICE 'Aktualisiere Projektgeometrie für Projekt %, Auftrag %', projektbez, auftrag;
      END IF;
      UPDATE oks_meta.projekt p SET geometrie = gebiet WHERE p.projekt = projektbez;
   END IF;
   
   -- Tabellennamen besorgen aus den Kopfdaten
   EXECUTE 'with foo as (SELECT distinct(klassenname) as tabelle FROM ' || md ||
                         '_kopfdaten_ where ' || proj || 'auftrag = ' ||
                         auftrag::text || ') ' ||
            'select array_agg(tabelle) from foo' INTO arr;
   
   -- Mehrfachaufrufe der Funktion oder fehlende Daten durchlassen
   lng := 0;
   IF arr IS NOT NULL THEN
      lng := array_length(arr,1);
   END IF;
   IF lng = 0 THEN
      IF verbos > 0 THEN
         RAISE NOTICE 'Keine Daten gefunden für Projekt %, Auftrag %', projekt, auftrag;
      END IF;
      -- Status in Warteschlange anpassen
      cmd := 'update ' || md || 'warteschlange set status = -1, loeschung = true where auftragsnummer = ' || auftrag::text || ';'; 
      IF verbos > 1 THEN
         RAISE NOTICE '   Befehl % ', cmd;
      END IF;
      EXECUTE cmd;
      return lng::integer;
   END IF;

   -- Zwischentabelle bereinigen von allen betroffenen Einträgen
   cmd := 'SELECT array_agg(table_name) FROM information_schema.tables ' ||
         'WHERE table_schema=''' || schop ||
         ''' AND table_type=''BASE TABLE'' AND table_name LIKE ''zwischentab%'';';
   IF verbos > 1 THEN
      RAISE NOTICE '   Befehl % ', cmd;
   END IF;
   EXECUTE cmd into zwa;
   zwtab := 'zwischentab';
   kopf := '_kopfdaten_';
   IF zwa IS NOT NULL AND array_length(zwa,1) > 0 THEN
      FOREACH tab IN ARRAY zwa LOOP
         IF tab = 'zwischentab_data' THEN
            zwtab := tab;
            kopf := '_header_';
         END IF;
      END LOOP;
   END IF;

   cmd := 'with foo as (SELECT foid::text FROM ' || md || kopf ||
                        ' where ' || proj || 'auftrag = ' ||
                        auftrag::text || ') ' ||
            'delete from ' || sch || zwtab || ' where ' ||
            'id::text in (select foid from foo); ' ||
          'with foo as (SELECT foid::text FROM ' || md || kopf ||
                        ' where ' || proj || 'auftrag = ' ||
                        auftrag::text || ') ' ||
            'delete from ' || sch || zwtab || ' where ' ||
            'rid::text in (select foid from foo);';
   IF verbos > 1 THEN
      RAISE NOTICE 'Bereinige Zwischentabelle für Projekt %, Auftrag % Befehl: %', projekt, auftrag, cmd;
   ELSIF verbos > 0 THEN
      RAISE NOTICE 'Bereinige Zwischentabelle für Projekt %, Auftrag %', projekt, auftrag;
   END IF;
   EXECUTE cmd;
   
-- Über alle Tabellen laufen und die betroffenen Inhalte herauslöschen
   FOREACH tab IN ARRAY arr LOOP
      cnt := 0;
      cmd := '';
      -- Prüfe Datentabelle auf Existenz. Es können Einträge von temporären
      -- Geometrieobjekten auftauchen, dazu gibt es eventuell keine Datentabelle
      EXECUTE 'SELECT count(table_name) FROM information_schema.tables WHERE table_schema=''' ||
          schop || ''' AND table_type=''BASE TABLE'' AND table_name=''' ||
          lower(tab) || '''' into cnt;
      if cnt > 0 then
          -- Datentabelle existiert, betroffene Einträge löschen
          IF verbos > 0 THEN
             RAISE NOTICE 'Bereinige Datentabelle % für Projekt %, Auftrag %', tab, projekt, auftrag;
          END IF;
          cmd := ' delete from ' || sch || tab || ' where oid in ' ||
                      '(select foid::text from ' || md || '_header_ where ' ||
                      'klassenname = (select id from oks_meta.txtpool where wert = ''' || tab || ''') and ' || proj ||
                      'auftrag = ' || auftrag::text || ');';
      elsif verbos > 0 then
          RAISE NOTICE 'Datentabelle % fehlt für Projekt %, Auftrag %', tab, projekt, auftrag;
      end if;
      -- Kopfdaten bereinigen, auch für die temporären Einträge
      cmd := cmd || ' delete from ' || md || '_header_ where ' ||
                      'klassenname = (select id from oks_meta.txtpool where wert = ''' || tab || ''') and ' || proj ||
                      'auftrag = ' || auftrag::text || ';';
      IF verbos > 1 THEN
         RAISE NOTICE '   Befehl % ', cmd;
      END IF;
      EXECUTE cmd;
   END LOOP;
   
   -- Status in Warteschlange anpassen
   cmd := 'update ' || md || 'warteschlange set status = -1, loeschung = true where auftragsnummer = ' || auftrag::text || ';'; 
   IF verbos > 1 THEN
      RAISE NOTICE '   Befehl % ', cmd;
   END IF;
   EXECUTE cmd;
   RETURN lng::integer;
END;
$$;
