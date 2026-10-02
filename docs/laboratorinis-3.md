# 3 laboratorinis darbas – React naudotojo sąsaja

## Paleidimas

Iš projekto katalogo: `docker compose up -d --build`. Sąsaja: http://localhost:3000, API: http://localhost:8080.

Lokaliam React kūrimui (Node.js 22+):

```sh
cd frontend
npm ci
npm run dev
```

Vite adresas: http://localhost:5173. API turi veikti 8080 prievade; `/api` užklausas Vite persiunčia į jį. Docker aplinkoje tą daro Nginx. CORS išimčių nereikia.

Registracija suteikia USER (peržiūros) teises. ADMIN paskyros sukūrimas per `ADMIN_USERNAME` ir `ADMIN_PASSWORD` aprašytas pagrindiniame README. Esamos API prieigos taisyklės nekeičiamos.

## Reikalavimų realizacija

| Reikalavimas | Realizacija |
| --- | --- |
| React sąsaja REST API | `frontend/src/App.jsx`, `features/`, `services/api.js`; autentifikacija ir organizacijų, grupių, užrašų GET, POST, PUT, DELETE |
| Responsive išdėstymas | `styles.css`: 768 px lūžio taškas, papildomas 769–1050 px išdėstymas |
| Prisitaikantys paveikslėliai | `img { max-width: 100%; height: auto }`; vietinė SVG užrašinės iliustracija prisijungimo ir tuščio sąrašo languose |
| Skirtingos header/content/footer sritys | Baltas header, šviesi darbo erdvė, žalsvas footer; kiekvienoje keli elementai, atskiri nuorodų ir mygtukų stiliai |
| Įvedimo elementai | Text, password, search, checkbox, select, textarea; susietos etiketės, privalomi laukai, ilgio ribojimai |
| Transitions / animacijos | Sąrašų hover, mygtukų perėjimai, langų atsiradimas, įkėlimo indikatorius; palaikoma reduced-motion nuostata |
| Responsive meniu | Horizontalūs desktop punktai ir mobilus hamburger su aria-expanded |
| Vektorinės ikonos | Lucide React SVG ikonos |
| Papildomas šriftas | Manrope Variable iš @fontsource-variable; šrifto failai pateikiami lokaliai |
| Prasmingas modalas | Įrašo peržiūra, redagavimas, trynimo patvirtinimas ir pagalba; native dialog, Escape, focus grąžinimas |
| Spalvų darna | Tamsiai žalias akcentas, neutralūs šviesūs paviršiai, atskira klaidų spalva |
| Tinklelis | CSS Grid darbo erdvė ir formos, Flexbox navigacija ir įrašai |
| Matomumas ir pasiekiamumas | Focus indikatoriai, skip link, semantinės sritys, etiketės, pranešimų role=status/alert |
| Formų nuoseklumas | Vienodas redaktorius trims resursams, API klaidų rodymas, mygtukų blokavimas siunčiant |
| Vientisas dizainas | Vienoda tipografija, tarpai, formų ir mygtukų stiliai; lietuviška sąsaja |

## Demonstravimas

1. Prisijunkite administratoriumi. Peržiūrėkite užrašą, paiešką ir grupės filtrą.
2. Sukurkite organizaciją, joje grupę, grupėje užrašą. Redaguokite įrašą.
3. Pabandykite trinti grupę su užrašu – bus rodoma API ryšių konflikto klaida.
4. Ištrinkite demonstracijos užrašą, grupę ir organizaciją šia tvarka.
5. Atsijunkite ir registruokite įprastą naudotoją: redagavimo veiksmų nebus.
6. Sumažinkite ekraną iki 390 px: atverkite hamburger meniu ir modalą.

## Patikrinimas

```sh
cd frontend
npm run build
npx playwright install chromium
npm test
```

Playwright testai naudoja Chromium ir imituotą API, todėl duomenų bazės nekeičia. Jie tikrina CRUD darbo eigą, USER prieigą, paiešką, mobilų meniu, focus grąžinimą, konfliktą, 401 ir horizontalų perpildymą.

JWT ir atnaujinimo žetonas laikomi sessionStorage. JWT automatiškai atnaujinamas prieš galiojimo pabaigą; atnaujinimo sesija galioja iki 7 dienų nuo prisijungimo. Atsijungiant atnaujinimo žetonas atšaukiamas. Tikras teises visada tikrina REST API; React teisių tikrinimas valdo tik matomus veiksmus.
