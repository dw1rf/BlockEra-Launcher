-- Adopt the BlockEra default once; subsequent language choices are preserved.
UPDATE settings SET locale = 'ru-RU' WHERE locale = 'en-US';
