INSERT INTO organizations(name, description) VALUES
('KTU Informatikos fakultetas', 'Studijų medžiaga ir studentų bendradarbiavimas.'),
('Programuotojų klubas', 'Bendrų projektų idėjos ir techninės žinios.');
INSERT INTO groups(name, description, organization_id) VALUES
('IFF-3/6 Saityno technologijos', 'REST API, HTTP ir laboratorinių darbų užrašai.', 1),
('Duomenų bazių studijos', 'Reliacinių duomenų bazių projektavimas ir SQL.', 1),
('Atvirojo kodo projektai', 'Klubo projektų planavimas.', 2),
('Asmeninė mokymosi grupė', 'Savarankiška grupė, nepriklausanti organizacijai.', NULL);
INSERT INTO notes(title, content, category, group_id) VALUES
('HTTP atsakymų kodai', '200 - sėkminga užklausa; 201 - sukurtas resursas; 204 - ištrinta; 404 - nerasta.', 'REST', 1),
('Pirmo laboratorinio darbo planas', 'Realizuoti 15 API metodų, parengti OpenAPI ir automatizuotą demonstraciją.', 'Laboratoriniai', 1),
('Išoriniai raktai', 'Išoriniai raktai užtikrina ryšių tarp organizacijų, grupių ir užrašų vientisumą.', 'SQL', 2),
('Projekto susitikimas', 'Susitarti dėl darbų pasidalijimo ir peržiūrėti pirmą API versiją.', 'Planavimas', 3),
('Savaitės mokymosi tikslai', 'Pakartoti HTTP metodus ir parašyti integracinius testus.', 'Mokymasis', 4);
