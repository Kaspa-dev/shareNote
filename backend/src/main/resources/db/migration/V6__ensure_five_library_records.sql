-- Top up demo collections without changing existing records or their relationships.
INSERT INTO organizations(name, description)
SELECT name, description FROM (VALUES
    (1, 'Technologijų akademija', 'Praktiniai programavimo kursai ir bendri projektai.'),
    (2, 'Studentų mokslo draugija', 'Tyrimų idėjos ir studijų patirties mainai.'),
    (3, 'Skaitmeninių įgūdžių centras', 'Skaitmeninių įrankių mokymasis.'),
    (4, 'Inovacijų laboratorija', 'Naujų technologijų eksperimentai.'),
    (5, 'Mokymosi bendruomenė', 'Bendradarbiavimas ir dalijimasis žiniomis.')
) AS samples(position, name, description)
ORDER BY position
LIMIT GREATEST(0, 5 - (SELECT count(*) FROM organizations));

INSERT INTO groups(name, description, organization_id)
SELECT name, description, (SELECT min(id) FROM organizations)
FROM (VALUES
    (1, 'Programinės įrangos testavimas', 'Automatiniai testai ir kokybės užtikrinimas.'),
    (2, 'Žiniatinklio kūrimas', 'Svetainių kūrimo praktika.'),
    (3, 'Algoritmų studijos', 'Duomenų struktūros ir uždavinių sprendimas.'),
    (4, 'Projektų dirbtuvės', 'Komandinio darbo praktika.'),
    (5, 'Techninių knygų klubas', 'Knygų aptarimai ir mokymosi užrašai.')
) AS samples(position, name, description)
ORDER BY position
LIMIT GREATEST(0, 5 - (SELECT count(*) FROM groups));

INSERT INTO notes(title, content, category, group_id)
SELECT title, content, category, (SELECT min(id) FROM groups)
FROM (VALUES
    (1, 'Testavimo pagrindai', 'Patikrinti sėkmingus scenarijus, klaidas ir ribines reikšmes.', 'Testavimas'),
    (2, 'Kodo peržiūra', 'Įvertinti kodo aiškumą, teisingumą ir testų aprėptį.', 'Kokybė'),
    (3, 'Darbo planavimas', 'Padalyti užduotis į nedidelius patikrinamus žingsnius.', 'Planavimas'),
    (4, 'Dokumentacijos gairės', 'Aprašyti paleidimą, konfigūraciją ir naudojimo pavyzdžius.', 'Dokumentacija'),
    (5, 'Mokymosi užrašai', 'Užrašyti naujas sąvokas ir išbandyti jas praktiškai.', 'Mokymasis')
) AS samples(position, title, content, category)
ORDER BY position
LIMIT GREATEST(0, 5 - (SELECT count(*) FROM notes));
