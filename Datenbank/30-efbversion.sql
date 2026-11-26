-- SPDX-FileCopyrightText: 2026 interactive instruments GmbH
-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Feste Einträge in oks_meta.efbversion. Die bundesweiten Fachbedeutungslisten 1.3
-- bis 1.6 liefert das OKLABI-Release nicht als CSV mit; die Einträge werden trotzdem
-- gebraucht: Gibt eine Datei keine Liste an, ordnen die Kopfdaten ihre Objekte der
-- Liste 1.6 zu. Die Kennung -1 ("aus Datei") legt auch fbliste.py an. Laufende Nummer
-- wie in fbliste.py: 1.3 bis 1.6 -> 2 bis 5.
INSERT INTO oks_meta.efbversion (id, hauptversion, unterversion, bundesland, lfdnr_vsn) VALUES
    (-1, 0, 0, 'aus Datei', -1),
    (1, 1, 3, '', 2),
    (2, 1, 4, '', 3),
    (3, 1, 5, '', 4),
    (4, 1, 6, '', 5);
