# ShareNote – galutinė projekto ataskaita

## 1. Sprendžiamo uždavinio aprašymas

### 1.1. Sistemos paskirtis

Komandos ar studijų grupės užrašai dažnai išskaidomi skirtinguose dokumentuose ir susirašinėjimuose. Dėl to sunku rasti reikalingą informaciją ir suprasti, kuriai temai ar grupei ji priklauso. **ShareNote** skirta bendriems užrašams kaupti, skaityti ir tvarkyti vienoje internetinėje sistemoje.

Biblioteką sudaro organizacijos, grupės ir užrašai. Organizacija gali turėti daug grupių; grupė gali priklausyti vienai organizacijai arba veikti savarankiškai. Kiekvienas užrašas privalo priklausyti vienai grupei ir turi antraštę, turinį bei kategoriją.

Sistemoje yra du vaidmenys: **USER (skaitytojas)** peržiūri duomenis, o **ADMIN (administratorius)** juos kuria, keičia ir šalina. Visi prisijungę naudotojai mato visą biblioteką: organizacijos ir grupės naudojamos informacijai susisteminti, individuali narystė ar privatūs užrašai nerealizuoti.

### 1.2. Funkciniai reikalavimai

| ID | Reikalavimas | Realizacija |
| --- | --- | --- |
| FR-01 | Registruoti naudotoją | Registracijos forma ir `POST /api/auth/register`; suteikiamas USER vaidmuo. |
| FR-02 | Prisijungti ir atsijungti | Prisijungus gaunamas JWT; atsijungiant pašalinama naršyklės sesija. |
| FR-03 | Riboti veiksmus pagal vaidmenį | USER ir ADMIN gali skaityti; kurti, atnaujinti ir trinti gali tik ADMIN. Teises tikrina API. |
| FR-04 | Tvarkyti organizacijas | Sąrašas, peržiūra pagal ID, sukūrimas, pakeitimas ir ištrynimas. |
| FR-05 | Tvarkyti grupes | Penkios CRUD operacijos; organizacijos priskyrimas neprivalomas. |
| FR-06 | Tvarkyti užrašus | Penkios CRUD operacijos; privaloma antraštė, turinys, kategorija ir grupė. |
| FR-07 | Ieškoti ir filtruoti | Naršyklėje ieškoma pagal pavadinimą, turinį / aprašymą ir kategoriją; užrašai filtruojami pagal grupę, grupės – pagal organizaciją. API palaiko `groupId` ir `organizationId` filtrus. |
| FR-08 | Tikrinti įvestį | Tikrinami privalomi laukai, jų ilgis, tipai ir teigiami ryšių ID; klaidos rodomos sąsajoje. |
| FR-09 | Išlaikyti ryšių vientisumą | Negalima ištrinti grupės su užrašais arba organizacijos su grupėmis. Grąžinamas 409. |
| FR-10 | Patvirtinti trynimą | Prieš DELETE rodomas patvirtinimo dialogas. |
| FR-11 | Informuoti apie būseną | Rodomas duomenų krovimas, tuščias sąrašas, klaidos ir sėkmės pranešimai. Pasibaigus sesijai grįžtama į prisijungimą. |
| FR-12 | Naudotis skirtinguose ekranuose | Prisitaikantis išdėstymas, mobilusis meniu, klaviatūra valdomi dialogai ir pagalbos langas. |

### 1.3. Duomenų ir prieigos taisyklės

| Objektas | Laukai ir apribojimai |
| --- | --- |
| Organizacija | `id` sugeneruojamas; `name` – 1–120 simbolių, ne vien tarpai; `description` – privaloma eilutė iki 2000 simbolių, gali būti tuščia. |
| Grupė | `id`, `name`, `description` kaip organizacijos; `organizationId` – teigiamas egzistuojančios organizacijos ID arba `null`. Lauką leidžiama praleisti. |
| Užrašas | `id` sugeneruojamas; `title` – 1–200, `content` – 1–20 000, `category` – 1–100 simbolių; reikšmės negali būti vien tarpai; `groupId` – privalomas teigiamas egzistuojančios grupės ID. |
| Paskyra | `username` – 3–80 lotyniškų raidžių, skaičių arba `_.-`; raidžių dydis nesvarbus. `password` – bent 12 simbolių ir ne daugiau kaip 72 UTF-8 baitai. Slaptažodis saugomas BCrypt maišos pavidalu. |

JWT galioja 3600 sekundžių, turi `sub`, `username`, `role`, `iss`, `iat`, `exp` laukus ir pasirašomas RS256. API nenaudoja serverio HTTP sesijų. Naršyklė prieigos ir atnaujinimo žetonus saugo `sessionStorage`. Atnaujinimo žetonas galioja iki 7 dienų nuo prisijungimo ir kiekvieną kartą pakeičiamas nauju. Naršyklė automatiškai atnaujina JWT prieš galiojimo pabaigą arba gavusi 401. API paleidžiant generuojama nauja RSA raktų pora; galiojantis atnaujinimo žetonas leidžia gauti naują JWT. Atsijungimas atšaukia atnaujinimo žetoną, bet jau išduotas JWT galioja iki galiojimo pabaigos. [Žetonų atnaujinimo API ir veikimas](docs/token-renewal.md).

## 2. Sistemos architektūra

Frontend failų struktūra, modulių atsakomybės ir kūrimo komandos aprašytos [frontend/README.md](frontend/README.md).

### 2.1. UML diegimo diagrama

![ShareNote UML diegimo diagrama](docs/report/Model.jpg)

| Komponentas | Technologijos ir atsakomybė |
| --- | --- |
| Naršyklės klientas | React 19, Vite 6, JavaScript ir CSS; formos, navigacija, paieška, rezultatų pateikimas ir HTTP užklausos. |
| `frontend` | Nginx pateikia Vite sukurtus statinius failus ir perduoda `/api/` užklausas į `api:8080`. |
| `api` | Java 21, Spring Boot 3.5.5, Spring Security; autentifikavimas, vaidmenys, validacija, CRUD ir klaidų atsakymai. |
| `db` | PostgreSQL 17; organizacijų, grupių, užrašų ir paskyrų lentelės, išoriniai raktai ir nuolatinis duomenų saugojimas. |
| Migracijos | Flyway paleidimo metu sukuria / atnaujina schemą ir pradinius demonstracinius duomenis. |
| Azure tarpinis serveris | Caddy 2 priima HTTPS, nukreipia į frontend, o Swagger ir OpenAPI užklausas – tiesiai į API. |

API kontroleriai priima HTTP užklausas, `ShareNoteService` vykdo dalykinę logiką ir SQL per `JdbcClient`, o `AuthService` valdo paskyras bei žetonus. `ApiErrors` vienodina klaidas į `application/problem+json`. Ryšiai duomenų bazėje: `organizations 1 — 0..* groups`, `groups 1 — 0..* notes`; iš grupės pusės organizacija neprivaloma, iš užrašo pusės grupė privaloma. `app_users` saugo paskyras ir vaidmenis, bet neturi narystės ryšių su grupėmis.

Vietiniame [compose.yaml](compose.yaml) Caddy nenaudojamas: sąsaja pasiekiama per `localhost:3000`, API – `localhost:8080`, PostgreSQL – `localhost:5433` (konteinerio prievadas 5432). Azure [konfigūracijoje](deploy/compose.azure.yaml) viešai atveriami tik Caddy 80 ir 443 prievadai; kiti servisai bendrauja vidiniame Docker tinkle. PostgreSQL duomenys laikomi `postgres-data` tome.

## 3. Naudotojo sąsajos projektas ir realizacija

Kiekvienas scheminis lango projektas (**wireframe**) susietas su jį atitinkančia realizacijos iškarpa. Schemos parengtos pagal dabartinę realizaciją ir nėra ankstesnio projektavimo proceso įrodymas. Iškarpos užfiksuotos iš veikiančios vietinės React sąsajos su demonstraciniais API atsakymais (`Universitetas`, `Saityno technologijos`, `HTTP metodai`). Jos parodo tikrą sąsajos kodą, tačiau nėra integracijos su veikiančiu serveriu patikra. Fotografavimo scenarijus: [scripts/capture-report.mjs](scripts/capture-report.mjs).

### 3.1. Prisijungimas ir registracija

```text
+------------------------------------------------------------------+
| ShareNote                                      Sistemos šūkis     |
+--------------------------------+---------------------------------+
| Sistemos paskirtis              | Prisijungimo / registracijos      |
| Antraštė ir trumpas aprašymas   | antraštė                          |
|                                | Naudotojo vardas [_____________]  |
| Užrašinės iliustracija          | Slaptažodis      [_____________]  |
|                                | [ ] Rodyti slaptažodį             |
|                                | [Klaidos pranešimas, jei yra]     |
|                                | [Prisijungti / Sukurti paskyrą]   |
|                                | [Registruotis / Prisijungti]      |
+--------------------------------+---------------------------------+
| Sistemos pavadinimas                            [Apie ir pagalba] |
+------------------------------------------------------------------+
```

Prisijungimas ir registracija naudoja tą patį maketą. Registracija suteikia skaitytojo teises. Slaptažodžio rodymą galima įjungti, o vykstant užklausai pateikimo mygtukas išjungiamas.

![Prisijungimo realizacija](docs/report/login.png)

![Registracijos realizacija](docs/report/register.png)

### 3.2. Užrašų, grupių ir organizacijų sąrašai

```text
+------------------------------------------------------------------+
| ShareNote       [Užrašai] [Grupės] [Organizacijos]  Vardas [Išeiti] |
+------------------------------------------------------------------+
| Darbo erdvė > Skiltis                                             |
| Skilties antraštė (kiekis)                   [+ Sukurti įrašą]*    |
+-------------------+----------------------------------------------+
| BIBLIOTEKA        | [Ieškoti........................] [Filtras] [↻] |
| Užrašai           | VISI ĮRAŠAI                           Kiekis  |
| Grupės            | Ikona | Kategorija / priklausomybė             |
| Organizacijos     |       | Pavadinimas                           |
|                   |       | Turinio / aprašymo ištrauka       [>] |
| Pagalba           |----------------------------------------------|
| Prieigos teisės   | Kiti įrašai arba tuščios būsenos paaiškinimas  |
+-------------------+----------------------------------------------+
* Tik administratoriui. Organizacijų sąraše ryšio filtro nėra.
```

Visos trys skiltys naudoja vienodą navigaciją ir sąrašo struktūrą. Užrašų filtras parenka grupę, grupių filtras – organizaciją arba savarankiškas grupes. Paieška ir sąsajos filtrai vykdomi naršyklėje jau gautiems duomenims.

![Užrašų sąrašas](docs/report/notes.png)

![Grupių sąrašas](docs/report/groups.png)

![Organizacijų sąrašas](docs/report/organizations.png)

### 3.3. Sukūrimo ir redagavimo dialogai

```text
+--------------------------------------------------+
| Sukurti / Redaguoti užrašą                    [X] |
| Antraštė [_____________________________________] |
| Kategorija [____________]  Grupė [Pasirinkti    v] |
| Užrašo turinys                                    |
| [______________________________________________] |
| [______________________________________________] |
| Ilgio apribojimas / klaidos pranešimas             |
|                         [Atšaukti] [Išsaugoti]    |
+--------------------------------------------------+

+--------------------------------------------------+
| Sukurti / Redaguoti grupę                     [X] |
| Pavadinimas [__________________________________] |
| Organizacija [Savarankiška grupė / pasirinkti  v] |
| Aprašymas [____________________________________] |
|                         [Atšaukti] [Išsaugoti]    |
+--------------------------------------------------+

+--------------------------------------------------+
| Sukurti / Redaguoti organizaciją              [X] |
| Pavadinimas [__________________________________] |
| Aprašymas [____________________________________] |
|                         [Atšaukti] [Išsaugoti]    |
+--------------------------------------------------+
```

Redaguojant tas pats dialogas užpildomas esamomis reikšmėmis. Sukūrimui siunčiamas POST, pakeitimui – PUT. Užrašo negalima išsaugoti nepasirinkus grupės; jei grupių nėra, pateikiamas paaiškinimas. Organizacijos ryšys grupės formoje neprivalomas.

| Užrašo sukūrimas | Užrašo redagavimas |
| --- | --- |
| ![Naujo užrašo forma](docs/report/note-create.png) | ![Užrašo redagavimas](docs/report/note-edit.png) |

| Grupės forma | Organizacijos forma |
| --- | --- |
| ![Grupės forma](docs/report/group-create.png) | ![Organizacijos forma](docs/report/organization-create.png) |

### 3.4. Peržiūra ir trynimo patvirtinimas

```text
+--------------------------------------------------+
| Įrašo pavadinimas                             [X] |
| Kategorija / ištekliaus tipas                     |
| Visas turinys arba aprašymas                      |
| Grupė / organizacija (kai taikoma)                |
|                         [Ištrinti]* [Redaguoti]*  |
+--------------------------------------------------+
* Tik administratoriui.

+--------------------------------------------------+
| Ištrinti įrašą?                               [X] |
| Įrašo pavadinimas ir negrįžtamo veiksmo aprašymas  |
| Priklausomybių paaiškinimas / klaida, jei taikoma  |
|                      [Atšaukti] [Taip, ištrinti]  |
+--------------------------------------------------+
```

Paspaudus įrašą, jo aktualūs duomenys paimami GET užklausa pagal ID. Trynimas vykdomas tik patvirtinus. Jei serveris grąžina 409, dialoge išlieka ryšių konflikto paaiškinimas.

| Peržiūros dialogas | Trynimo dialogas |
| --- | --- |
| ![Užrašo peržiūra](docs/report/detail.png) | ![Trynimo patvirtinimas](docs/report/delete.png) |

### 3.5. Pagalba ir mobilioji sąsaja

```text
+---------------------------------------+
| Jūsų ShareNote erdvė               [X] |
| Organizacijų, grupių ir užrašų ryšiai   |
| Kaip pradėti?                         |
| Prieigos teisės ir trynimo tvarka      |
| [Supratau]                            |
+---------------------------------------+

+-----------------------------+
| ShareNote               [☰] |
| Darbo erdvė > Užrašai        |
| Užrašai (kiekis)             |
| [+ Sukurti užrašą]*          |
| [Ieškoti..................] |
| [Grupės filtras]        [↻] |
| Įrašo pavadinimas       [>] |
| Turinio ištrauka            |
| [Apie ir pagalba]           |
+-----------------------------+
```

Mobiliajame vaizde šoninė juosta paslepiama, o navigacija atidaroma meniu mygtuku. Dialogai palaiko uždarymą Escape klavišu ir grąžina fokusą į juos atidariusį valdiklį; vykstant išsaugojimui ar trynimui uždarymas blokuojamas.

| Pagalba | Mobilusis vaizdas |
| --- | --- |
| ![Pagalbos langas](docs/report/help.png) | ![Mobilioji sąsaja](docs/report/mobile.png) |

## 4. API specifikacija

Atskiras [api-spec.yaml](api-spec.yaml) failas aprašo **OpenAPI 3.0.1** formatu visas 17 operacijų: parametrus, įvesties ir išvesties schemas, autentifikavimą, atsakymų kodus bei JSON pavyzdžius. Jis parengtas pagal kontrolerius, servisus ir saugos konfigūraciją, patikslinus automatinį [docs/openapi.json](docs/openapi.json) eksportą.

### 4.1. Bendros taisyklės

- Bazinis vietinis URL: `http://localhost:8080`.
- Išteklių metodams siunčiama `Authorization: Bearer <JWT>`. Vietoje `<JWT>` naudojamas prisijungimo atsakymo `accessToken`. POST, PUT ir DELETE išteklių operacijoms būtinas ADMIN žetonas.
- Registracija ir prisijungimas vieši. Registruojantis negalima pasirinkti ADMIN vaidmens. Net viešam metodui pateikus netinkamą Bearer žetoną, saugos filtras gali grąžinti 401.
- Užklausos kūnas ir sėkmingi atsakymai naudoja `application/json`, klaidos – `application/problem+json`. DELETE sėkmės atsakymas 204 neturi kūno.
- Sąrašai surikiuoti pagal ID didėjimo tvarka, gali būti tušti; puslapiavimas nerealizuotas.
- `GET /api/groups?organizationId=1` filtruoja pagal organizaciją; `GET /api/notes?groupId=1` – pagal grupę. Neegzistuojantis filtro tėvinis išteklius sukelia 404. Be filtro pateikiamas visas sąrašas.
- PUT pakeičia visus redaguojamus laukus ir nesukuria naujo ištekliaus. Praleistas grupės `organizationId` reiškia `null`.
- Nežinomi JSON laukai ir netinkami tipai atmetami. PATCH nerealizuotas.
- Pavyzdžių ID ir duomenys iliustraciniai. Skaitymo, keitimo ir trynimo pavyzdžiuose laikoma, kad įrašai jau egzistuoja. Kuriant naudojamas serverio grąžintas ID. `<gautas-JWT>` žymi vietą tikram žetonui ir nėra galiojantis žetonas.

### 4.2. Atsakymų kodai

| Kodas | Reikšmė |
| --- | --- |
| 200 | Sėkmingas skaitymas, pakeitimas arba prisijungimas. |
| 201 | Sukurta paskyra arba išteklius. Išteklių CRUD atveju pateikiama `Location` antraštė. |
| 204 | Išteklius ištrintas; kūnas tuščias. |
| 400 | Netaisyklingas JSON, nežinomas laukas arba netinkamas tipas. |
| 401 | Trūksta galiojančio JWT arba neteisingi prisijungimo duomenys. |
| 403 | Vaidmuo neleidžia operacijos. |
| 404 | Išteklius arba susijęs išteklius nerastas. |
| 406 | Accept prašo nepalaikomo formato. |
| 409 | Užimtas naudotojo vardas arba DB ryšių konfliktas. |
| 415 | Content-Type turi būti application/json. |
| 422 | Laukai neatitinka validacijos reikalavimų. |

Toliau nurodyti kiekvieno metodo sėkmės ir numatomi kliento klaidų kodai. Netikėti serverio ar infrastruktūros sutrikimai gali papildomai sukelti 5xx. Jei užklausa pažeidžia kelias taisykles, pirmiausia ją gali atmesti saugos filtras. 406 klaidos kūnas priklauso ir nuo to, ar `Accept` leidžia klaidos formatą.

### 4.3. Metodų suvestinė

| Metodas | Kelias | Prieiga | Atsakymų kodai |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | Vieša | 201, 400, 401, 406, 409, 415, 422 |
| POST | `/api/auth/login` | Vieša | 200, 400, 401, 406, 415, 422 |
| GET | `/api/organizations` | USER, ADMIN | 200, 400, 401, 403, 406 |
| GET | `/api/organizations/{id}` | USER, ADMIN | 200, 400, 401, 403, 404, 406 |
| POST | `/api/organizations` | ADMIN | 201, 400, 401, 403, 406, 409, 415, 422 |
| PUT | `/api/organizations/{id}` | ADMIN | 200, 400, 401, 403, 404, 406, 409, 415, 422 |
| DELETE | `/api/organizations/{id}` | ADMIN | 204, 400, 401, 403, 404, 409 |
| GET | `/api/groups` | USER, ADMIN | 200, 400, 401, 403, 404, 406 |
| GET | `/api/groups/{id}` | USER, ADMIN | 200, 400, 401, 403, 404, 406 |
| POST | `/api/groups` | ADMIN | 201, 400, 401, 403, 404, 406, 409, 415, 422 |
| PUT | `/api/groups/{id}` | ADMIN | 200, 400, 401, 403, 404, 406, 409, 415, 422 |
| DELETE | `/api/groups/{id}` | ADMIN | 204, 400, 401, 403, 404, 409 |
| GET | `/api/notes` | USER, ADMIN | 200, 400, 401, 403, 404, 406 |
| GET | `/api/notes/{id}` | USER, ADMIN | 200, 400, 401, 403, 404, 406 |
| POST | `/api/notes` | ADMIN | 201, 400, 401, 403, 404, 406, 409, 415, 422 |
| PUT | `/api/notes/{id}` | ADMIN | 200, 400, 401, 403, 404, 406, 409, 415, 422 |
| DELETE | `/api/notes/{id}` | ADMIN | 204, 400, 401, 403, 404 |

### 4.4. Kiekvieno metodo panaudojimo pavyzdžiai

#### 1. `POST /api/auth/register`

Atsakymų kodai: **201, 400, 401, 406, 409, 415, 422**. Vieša operacija. Pateikus netinkamą Bearer žetoną, saugos filtras gali grąžinti 401.

Užklausa:

```http
POST /api/auth/register HTTP/1.1
Host: localhost:8080
Accept: application/json, application/problem+json
Content-Type: application/json

{
  "username": "studentas",
  "password": "Pavyzdinis-123!"
}
```

Atsakymas:

```http
HTTP/1.1 201 Created
Cache-Control: no-store
Content-Type: application/json

{
  "accessToken": "<gautas-JWT>",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

#### 2. `POST /api/auth/login`

Atsakymų kodai: **200, 400, 401, 406, 415, 422**. Vieša operacija. Pateikus netinkamą Bearer žetoną, saugos filtras gali grąžinti 401.

Užklausa:

```http
POST /api/auth/login HTTP/1.1
Host: localhost:8080
Accept: application/json, application/problem+json
Content-Type: application/json

{
  "username": "studentas",
  "password": "Pavyzdinis-123!"
}
```

Atsakymas:

```http
HTTP/1.1 200 OK
Cache-Control: no-store
Content-Type: application/json

{
  "accessToken": "<gautas-JWT>",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

#### 3. `GET /api/organizations`

Atsakymų kodai: **200, 400, 401, 403, 406**. Leidžiama USER ir ADMIN.

Užklausa:

```http
GET /api/organizations HTTP/1.1
Host: localhost:8080
Authorization: Bearer <JWT>
Accept: application/json, application/problem+json
```

Atsakymas:

```http
HTTP/1.1 200 OK
Content-Type: application/json

[
  {
    "id": 1,
    "name": "Universitetas",
    "description": "Bendra studijų erdvė."
  }
]
```

#### 4. `GET /api/organizations/{id}`

Atsakymų kodai: **200, 400, 401, 403, 404, 406**. Leidžiama USER ir ADMIN.

Užklausa:

```http
GET /api/organizations/1 HTTP/1.1
Host: localhost:8080
Authorization: Bearer <JWT>
Accept: application/json, application/problem+json
```

Atsakymas:

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": 1,
  "name": "Universitetas",
  "description": "Bendra studijų erdvė."
}
```

#### 5. `POST /api/organizations`

Atsakymų kodai: **201, 400, 401, 403, 406, 409, 415, 422**. Leidžiama tik ADMIN.

Užklausa:

```http
POST /api/organizations HTTP/1.1
Host: localhost:8080
Authorization: Bearer <JWT>
Accept: application/json, application/problem+json
Content-Type: application/json

{
  "name": "Universitetas",
  "description": "Bendra studijų erdvė."
}
```

Atsakymas:

```http
HTTP/1.1 201 Created
Location: /api/organizations/1
Content-Type: application/json

{
  "id": 1,
  "name": "Universitetas",
  "description": "Bendra studijų erdvė."
}
```

#### 6. `PUT /api/organizations/{id}`

Atsakymų kodai: **200, 400, 401, 403, 404, 406, 409, 415, 422**. Leidžiama tik ADMIN. PUT pakeičia visus redaguojamus laukus; nesamo ištekliaus nesukuria.

Užklausa:

```http
PUT /api/organizations/1 HTTP/1.1
Host: localhost:8080
Authorization: Bearer <JWT>
Accept: application/json, application/problem+json
Content-Type: application/json

{
  "name": "Universitetas (atnaujinta)",
  "description": "Bendra studijų erdvė."
}
```

Atsakymas:

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": 1,
  "name": "Universitetas (atnaujinta)",
  "description": "Bendra studijų erdvė."
}
```

#### 7. `DELETE /api/organizations/{id}`

Atsakymų kodai: **204, 400, 401, 403, 404, 409**. Leidžiama tik ADMIN.

Užklausa:

```http
DELETE /api/organizations/1 HTTP/1.1
Host: localhost:8080
Authorization: Bearer <JWT>
```

Atsakymas:

```http
HTTP/1.1 204 No Content
```

Atsakymo kūnas tuščias. Prieš trynimą pašalinamos organizacijos grupės.

#### 8. `GET /api/groups`

Atsakymų kodai: **200, 400, 401, 403, 404, 406**. Leidžiama USER ir ADMIN.

Užklausa:

```http
GET /api/groups?organizationId=1 HTTP/1.1
Host: localhost:8080
Authorization: Bearer <JWT>
Accept: application/json, application/problem+json
```

Atsakymas:

```http
HTTP/1.1 200 OK
Content-Type: application/json

[
  {
    "id": 1,
    "name": "Saityno technologijos",
    "description": "Paskaitų medžiaga.",
    "organizationId": 1
  }
]
```

#### 9. `GET /api/groups/{id}`

Atsakymų kodai: **200, 400, 401, 403, 404, 406**. Leidžiama USER ir ADMIN.

Užklausa:

```http
GET /api/groups/1 HTTP/1.1
Host: localhost:8080
Authorization: Bearer <JWT>
Accept: application/json, application/problem+json
```

Atsakymas:

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": 1,
  "name": "Saityno technologijos",
  "description": "Paskaitų medžiaga.",
  "organizationId": 1
}
```

#### 10. `POST /api/groups`

Atsakymų kodai: **201, 400, 401, 403, 404, 406, 409, 415, 422**. Leidžiama tik ADMIN.

Užklausa:

```http
POST /api/groups HTTP/1.1
Host: localhost:8080
Authorization: Bearer <JWT>
Accept: application/json, application/problem+json
Content-Type: application/json

{
  "name": "Saityno technologijos",
  "description": "Paskaitų medžiaga.",
  "organizationId": 1
}
```

Atsakymas:

```http
HTTP/1.1 201 Created
Location: /api/groups/1
Content-Type: application/json

{
  "id": 1,
  "name": "Saityno technologijos",
  "description": "Paskaitų medžiaga.",
  "organizationId": 1
}
```

#### 11. `PUT /api/groups/{id}`

Atsakymų kodai: **200, 400, 401, 403, 404, 406, 409, 415, 422**. Leidžiama tik ADMIN. PUT pakeičia visus redaguojamus laukus; nesamo ištekliaus nesukuria.

Užklausa:

```http
PUT /api/groups/1 HTTP/1.1
Host: localhost:8080
Authorization: Bearer <JWT>
Accept: application/json, application/problem+json
Content-Type: application/json

{
  "name": "Saityno technologijos (atnaujinta)",
  "description": "Paskaitų medžiaga.",
  "organizationId": 1
}
```

Atsakymas:

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": 1,
  "name": "Saityno technologijos (atnaujinta)",
  "description": "Paskaitų medžiaga.",
  "organizationId": 1
}
```

#### 12. `DELETE /api/groups/{id}`

Atsakymų kodai: **204, 400, 401, 403, 404, 409**. Leidžiama tik ADMIN.

Užklausa:

```http
DELETE /api/groups/1 HTTP/1.1
Host: localhost:8080
Authorization: Bearer <JWT>
```

Atsakymas:

```http
HTTP/1.1 204 No Content
```

Atsakymo kūnas tuščias. Prieš trynimą pašalinami grupės užrašai.

#### 13. `GET /api/notes`

Atsakymų kodai: **200, 400, 401, 403, 404, 406**. Leidžiama USER ir ADMIN.

Užklausa:

```http
GET /api/notes?groupId=1 HTTP/1.1
Host: localhost:8080
Authorization: Bearer <JWT>
Accept: application/json, application/problem+json
```

Atsakymas:

```http
HTTP/1.1 200 OK
Content-Type: application/json

[
  {
    "id": 1,
    "title": "HTTP metodai",
    "content": "GET skaito, POST sukuria.",
    "category": "REST",
    "groupId": 1
  }
]
```

#### 14. `GET /api/notes/{id}`

Atsakymų kodai: **200, 400, 401, 403, 404, 406**. Leidžiama USER ir ADMIN.

Užklausa:

```http
GET /api/notes/1 HTTP/1.1
Host: localhost:8080
Authorization: Bearer <JWT>
Accept: application/json, application/problem+json
```

Atsakymas:

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": 1,
  "title": "HTTP metodai",
  "content": "GET skaito, POST sukuria.",
  "category": "REST",
  "groupId": 1
}
```

#### 15. `POST /api/notes`

Atsakymų kodai: **201, 400, 401, 403, 404, 406, 409, 415, 422**. Leidžiama tik ADMIN.

Užklausa:

```http
POST /api/notes HTTP/1.1
Host: localhost:8080
Authorization: Bearer <JWT>
Accept: application/json, application/problem+json
Content-Type: application/json

{
  "title": "HTTP metodai",
  "content": "GET skaito, POST sukuria.",
  "category": "REST",
  "groupId": 1
}
```

Atsakymas:

```http
HTTP/1.1 201 Created
Location: /api/notes/1
Content-Type: application/json

{
  "id": 1,
  "title": "HTTP metodai",
  "content": "GET skaito, POST sukuria.",
  "category": "REST",
  "groupId": 1
}
```

#### 16. `PUT /api/notes/{id}`

Atsakymų kodai: **200, 400, 401, 403, 404, 406, 409, 415, 422**. Leidžiama tik ADMIN. PUT pakeičia visus redaguojamus laukus; nesamo ištekliaus nesukuria.

Užklausa:

```http
PUT /api/notes/1 HTTP/1.1
Host: localhost:8080
Authorization: Bearer <JWT>
Accept: application/json, application/problem+json
Content-Type: application/json

{
  "title": "HTTP metodai (atnaujinta)",
  "content": "GET skaito, POST sukuria.",
  "category": "REST",
  "groupId": 1
}
```

Atsakymas:

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": 1,
  "title": "HTTP metodai (atnaujinta)",
  "content": "GET skaito, POST sukuria.",
  "category": "REST",
  "groupId": 1
}
```

#### 17. `DELETE /api/notes/{id}`

Atsakymų kodai: **204, 400, 401, 403, 404**. Leidžiama tik ADMIN.

Užklausa:

```http
DELETE /api/notes/1 HTTP/1.1
Host: localhost:8080
Authorization: Bearer <JWT>
```

Atsakymas:

```http
HTTP/1.1 204 No Content
```

Atsakymo kūnas tuščias.

### 4.5. Klaidos pavyzdys

Administratorius bando sukurti užrašą be privalomos kategorijos:

```http
POST /api/notes HTTP/1.1
Host: localhost:8080
Authorization: Bearer <JWT>
Content-Type: application/json
Accept: application/json, application/problem+json

{"title":"HTTP metodai","content":"GET skaito.","groupId":1}
```

```http
HTTP/1.1 422 Unprocessable Entity
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Payload field values do not meet the validation requirements.",
  "instance": "/api/notes",
  "errors": [{"field": "category", "message": "must not be blank"}]
}
```

Visų metodų klaidų atsakymų struktūros ir iliustraciniai kūnai taip pat pateikti [api-spec.yaml](api-spec.yaml). Validacijos pranešimo tekstas gali priklausyti nuo validatoriaus lokalės.


## 5. Paleidimas ir patikra

### 5.1. Vietinis paleidimas

Reikalingas Docker su Linux konteineriais. Prieš pirmą paleidimą projekto šaknyje sukuriamas vietinis `.env` failas su `ADMIN_USERNAME`, `ADMIN_PASSWORD` ir, pasirinktinai, `DB_PASSWORD`. Slaptažodžiai neturi būti įtraukiami į Git; administratoriaus slaptažodis turi atitikti 1.3 skyriaus taisykles. Numatytasis administratoriaus vardas – `admin`, numatytojo administratoriaus slaptažodžio nėra. Administratoriaus sukūrimas nekeičia jau egzistuojančios paskyros slaptažodžio ir nepakelia USER teisių; jei vardas jau priklauso USER, reikia kito `ADMIN_USERNAME`.

```sh
docker compose up -d --build
```

- Naudotojo sąsaja: [http://localhost:3000](http://localhost:3000).
- API dokumentacija: [Swagger UI](http://localhost:8080/swagger-ui.html).
- Dinaminė specifikacija: [OpenAPI JSON](http://localhost:8080/v3/api-docs).
- Stabdymas: `docker compose down`; duomenų tomas paliekamas.

### 5.2. Patikros ir jų ribos

2026-09-27 patikra sėkminga: `api-spec.yaml` validuotas su `openapi-spec-validator`, visų JSON pavyzdžių atitiktis schemoms patikrinta su `openapi-schema-validator`, patvirtintas 17 operacijų atitikimas kontroleriams, README vietinės nuorodos ir 13 sąsajos iškarpų buvimas. API integraciniai testai rengiant šią ataskaitą iš naujo neleisti. Sąsajos iškarpos sukuriamos automatizuotai su demonstraciniais API atsakymais, realiai vykdant React kodą. Tai nepakeičia API integracinių bandymų su PostgreSQL.

Saugykloje yra šios pakartojamos patikros:

| Patikra | Paleidimas | Kas tikrinama |
| --- | --- | --- |
| API integraciniai testai | `cd backend`, tada `./mvnw.cmd test` (Windows) arba `sh ./mvnw test` | CRUD, validacija, ryšiai, autentifikavimas ir autorizacija su Testcontainers PostgreSQL; reikia Java 21 ir Docker. |
| Sąsajos testai | `cd frontend`, `npm ci`, `npx playwright install chromium`, `npm test` | Administratoriaus CRUD, skaitytojo teisės, paieška, dialogai, sesijos klaidos ir prisitaikantis išdėstymas su imituojamu API. |
| Sąsajos surinkimas | `cd frontend`, `npm run build` | Vite produkcinio paketo sukūrimas. |
| Veikiančio API demonstracija | Iš šaknies `node scripts/demo.mjs` | 15 CRUD operacijų; reikia veikiančio API ir aplinkoje nustatytų administratoriaus duomenų. |
| Ataskaitos iškarpų atkūrimas | Paleisti `npm run dev` iš `frontend/`, tada iš šaknies `node scripts/capture-report.mjs` | 13 React sąsajos iškarpų su nekintamais demonstraciniais duomenimis. |

Postman naudojimui pateikti [kolekcija](docs/ShareNote.postman_collection.json), [aplinka](docs/ShareNote.local.postman_environment.json) ir [instrukcija](docs/postman.md). Ankstesnių patikrų rezultatai išsaugoti [docs/verification.md](docs/verification.md); jų datos ir aprėptis netapatinamos su šios ataskaitos rengimu. Azure diegimo failai laikomi [deploy/](deploy/), papildomas aprašymas – [docs/azure.md](docs/azure.md).

## 6. Projekto išvados

1. Sukurta trijų lygių bendrų užrašų biblioteka: organizacijos, neprivalomai joms priskirtos grupės ir grupių užrašai. Tokia struktūra leidžia sisteminti komandos arba studijų medžiagą.
2. Realizuotos 19 REST operacijų: 15 išteklių CRUD metodų ir keturi autentifikavimo metodai. OpenAPI specifikacija, kiekvieno metodo atsakymų kodai bei užklausų ir atsakymų pavyzdžiai sudaro aiškų kliento ir serverio sąveikos aprašą.
3. React sąsajoje įgyvendintos peržiūros, paieškos, filtravimo ir administravimo funkcijos. Vienodas sąrašų bei dialogų išdėstymas pritaikytas tiek kompiuterio, tiek telefono ekranui.
4. JWT ir serverio pusėje tikrinami vaidmenys atskiria skaitymo bei keitimo teises. Įvesties validacija ir PostgreSQL išoriniai raktai apsaugo duomenų struktūrą nuo neteisingų ryšių ir neleidžiamo trynimo.
5. Docker Compose atskiria sąsają, API ir duomenų bazę, o Flyway automatizuoja schemos parengimą. Saugykloje pateikta vietinio ir Azure diegimo konfigūracija.
6. Dabartinė versija skirta bendrai bibliotekai ir vienam API egzemplioriui. Tolesnės plėtros kryptys: individuali grupių narystė ir privatumas, užrašų pakeitimų istorija, serverinė paieška bei puslapiavimas, ilgalaikis JWT raktų saugojimas ir prieigos JWT atšaukimas iki galiojimo pabaigos. Šios galimybės dabartinėje realizacijoje neįgyvendintos; atnaujinimo žetonai jau atšaukiami atsijungiant.

