# Patikros

Patikrinta 2026-09-22, Windows aplinkoje, Java 21 ir tikras PostgreSQL 17 Docker konteineryje.

| Patikra | Rezultatas |
| --- | --- |
| Integraciniai testai (`./mvnw.cmd test`) | 5 scenarijai, 0 klaidų, 0 praleistų |
| HTTP demonstracija (`node scripts/demo.mjs`) | Visi 15 metodų, 28 HTTP patikros, 2,01 s |
| Postman kolekcija per Newman | 23 užklausos, 72 teiginiai, 0 klaidų, 2,2 s |
| OpenAPI | 15 operacijų, įvesties ir išvesties schemos, HTTP atsakymų kodai |
| DB po demonstracijų | Išliko 2 organizacijos, 4 grupės ir 5 pradiniai užrašai |
| Visa Docker Compose aplinka | API ir DB veikia; 28 HTTP patikros per 1,17 s |

Trukmės matuotos jau paleistame serveryje; priklausomybių atsiuntimas ir programos paleidimas į jas neįtraukiami. Konkretaus kompiuterio apkrova gali pakeisti laiką.

Integraciniai testai sukuria atskirą PostgreSQL konteinerį. Tikrinami CRUD metodai, atnaujinimų išsaugojimas, `Location` ir `Content-Type`, filtravimas, savarankiškos grupės, 400/404/409/415/422 atsakymai ir OpenAPI struktūra. Demonstracija papildomai tikrina 204 atsakymų tuščią kūną ir resurso nebuvimą po ištrynimo.

Postman kolekcijos patikrinimą galima pakartoti be Postman GUI:

```powershell
npm exec --yes --package=newman -- newman run docs/ShareNote.postman_collection.json
```
