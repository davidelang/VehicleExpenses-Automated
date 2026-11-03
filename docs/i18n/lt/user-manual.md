# <a id="vehicle-expenses-automated--user-manual"></a>Automatizuotos transporto priemonės išlaidos – naudotojo vadovas

> **Redaguoti šaltinį (žymėjimas).** Naršyklės ir programoje esantis skaitytuvas atidaro **pateiktą HTML**:
> – Žiniatinklis: [`docs/user-manual/index.html`](index.html) (generuoti naudojant `./scripts/render-user-manual.sh`)
> - Programa: Pagalba / Apie → visas vadovas (sudėtas HTML + ekrano kopijos)
>
> Nenukreipkite galutinių vartotojų į neapdorotus `.md` URL – naršyklės rodo tik paprastą tekstą.

Pirmoji kamera kuro pildymo ir transporto priemonių išlaidų stebėjimas su pasirenkamu kelių įrenginių sinchronizavimu ir atsargine kopija **jūsų** debesies paskyrose.

Tai yra **visas vadovas** (ekrano kopijos + kiekvienas veiksmas). Telefone **Meniu → Žinynas** yra trumpesnis darbo pradžios vadovas.

**Čia neapima:** Importuokite senus paveikslėlius, lygiavimo eksperimentą ir siurblio eksperimentą (kūrėjo / išplėstiniai įrankiai).

---

## <a id="table-of-contents"></a>Turinys

1. [Ko jums reikia](#what-you-need)
2. [Piktogramos trumpai](#icons-at-a-glance)
3. [Atidaryti meniu] (#open-the-menu)
4. [Sąranka pirmą kartą: tvarkykite transporto priemones](#first-time-setup-manage-vehicles)
5. [Atsarginės kopijos ir kelių įrenginių sinchronizavimas](#backups-and-multi-device-sync)
6. [Užpildykite (degalai)](#fill-up-fuel)
7. [Pradėti kelionę](#start-trip)
8. [Išlaidos](#expenses)
9. [Ataskaitos](#reports)
10. [Nustatymai (vietinės nuostatos)](#settings-local-preferences)
11. [Sinchronizavimas](#syncing)
12. [Pagalba ir apie](#help--about)
13. [Susiję dokumentai](#related-docs)

---

## <a id="what-you-need"></a>Ko jums reikia

– Android telefonas arba planšetinis kompiuteris.
– geriausias OCR: aiškus **prietaisų skydelio odometro** ir **siurblio sumų** vaizdas (arba įveskite skaičius ranka).
– neprivaloma: paskyros, kurias **valdote** skaičiuoklės duomenims ir (arba) nuotraukų atsarginėms kopijoms (žr. [Atsarginės kopijos ir kelių įrenginių sinchronizavimas](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a> Piktogramos trumpai

Jie rodomi pagrindiniuose ekranuose. Žinant juos, sutaupoma daug medžioklės.

| Kur | Piktograma / valdiklis | Ką tai daro |
|-------|-----------------|--------------|
| Viršutinė juosta | **☰ Meniu** (mėsainis) | Atidaro naršymo stalčių |
| Viršutinė juosta | **ⓘ** (puslapio žinynas) | Trumpa **dabartinio** puslapio pagalba (šalia meniu, kai yra) |
| Viršutinė juosta | **`?N`** (geltona) | Laukiami importo peržiūros klausimai – atidaroma Importavimo peržiūra |
| Viršutinė juosta | **!** (raudona) | Neseniai nepavyko sukurti skaičiuoklės arba nuotraukos – atidarykite **Sinchronizuojama**, kad ištaisytumėte |
| Viršutinė juosta | **☰ + ←** | Ataskaitoje apie vaikus ir išlaidų sąraše rodomas **meniu ir atgal** kartu; Ataskaitų centras yra tik meniu |
| Nustatymai / kuro redagavimas | **←** | Atgal (nustatymų skaičiuoklė / nuotrauka ir degalų redagavimas išlieka nukreiptas atgal) |
| Užpildykite | **Baltas apskritimas** (užraktas) | Užfiksuokite odometro arba siurblio ekraną OCR |
| Užpildykite | **Diskas / Išsaugoti** | Išsaugokite pildymą (reikia transporto priemonės ir bent vieno iš odo / apimties / kainos) |
| Užpildykite | **↕ rodyklės** (režimo jungiklis) | Perjungti **odometro režimą** ir **siurblio (kaina / tūris) režimą**. Žalia kraštinė paryškina aktyvią laukų grupę |
| Užpildykite | **↔ rodyklės** (tarp kainos ir apimties) | Sukeitimo kaina ir apimtis, jei OCR juos įvedė neteisinguose laukuose |
| Užpildykite | **Mastelio keitimas 1x / …** | Fotoaparato priartinimo koeficientai, kai objektyvas juos palaiko |
| Užpildymas (po fiksavimo) | **Atnaujinti** pagrindiniame mygtuke | Atmesti peržiūrą ir grįžti į tiesioginį fotoaparatą |
| Užpildykite (apdorojant) | **X** pagrindiniame mygtuke | Atšaukti vykstantį fiksavimą / OCR |
| Išlaidos | **Išsaugoti** | Taupykite išlaidas |
| Išlaidos | **Užrakto ratas** | Padarykite kvito nuotrauką |
| Išlaidos | **Galerija** | Pasirinkite kvito vaizdą iš bibliotekos |
| Išlaidos | **Pakartoti** | Išvalykite esamo kvito nuotrauką ir fotografuokite dar kartą |
| Išlaidos / Tvarkyti transporto priemones | **+ / −** FAB | Padidinkite nuotraukos peržiūrą |
| Orientyrų dialogas | **Redaguoti OCR** ​​| Pataisykite arba pridėkite orientyrinį tekstą, kurio varikliai praleido |
| Skaičiuoklė / Nuotraukų formos | **🔍 Ieškoti** | Naršykite „Google“ diske lapą arba aplanką (prisijungę) |

Valiutos simbolius kainos laukuose ir **G/L** apimties laukuose galima paliesti: atidarykite nedidelį meniu, kad pakeistumėte to įrašo valiutą arba galonus, palyginti su litrais.

---

## <a id="open-the-menu"></a>Atidarykite meniu

1. Viršutiniame kairiajame kampe palieskite **☰**.
2. Pasirinkite puslapį.

![Naršymo stalčius](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Pagrindinis stalčius:** Užpildykite · Pradėkite kelionę · Tvarkykite transporto priemones · Naujos išlaidos · **Ataskaitos** · Nustatymai · Sinchronizavimas · Pagalba · Apie.

**Eksperimentų stalčius** (Nustatymai → Rodyti eksperimento ekranus): Lygiavimo eksperimentas · Siurblio eksperimentas · **Importuoti senus paveikslėlius**.

**Per ataskaitų centrą (ne pagrindinį stalčių):** Išlaidų sąrašas · Užpildykite istoriją.

---

## <a id="first-time-setup-manage-vehicles"></a> Pirmą kartą sąranka: tvarkykite transporto priemones

OCR ir **automatinis transporto priemonių derinimas** veikia geriausiai, kai užregistruojate kiekvieną transporto priemonę su **nuoroda prietaisų skydelio nuotrauka**, paleidžiate **Discovery**, nustatote odometro pasėlius iš irklentės sėklos ir išsaugote to brūkšnio orientyrus.

### <a id="open-manage-vehicles"></a> Atidarykite Tvarkyti transporto priemones

Meniu → **Tvarkyti transporto priemones**. Pasirinkite transporto priemonę (arba **Pridėti naują transporto priemonę**).

![Tvarkykite transporto priemones su nuorodomis, pasėliais ir atradimais](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Pridėkite arba redaguokite transporto priemonę

1. Atidarykite išskleidžiamąjį meniu **Transporto priemonė** → pasirinkite transporto priemonę arba **Pridėti naują transporto priemonę**.
2. Užfiksuokite arba pasirinkite aiškią **referencinio brūkšnio nuotrauką** (visa prietaisų grupė, gerai apšviesta, telefonas maždaug kvadratinis). Naudokite **Fotografuoti** arba **Galerija**.
3. Bakstelėkite **Vykdyti atradimą** – irklų aptikimas ant nuotraukos uždeda geltonas **sėklas** (orientyrai naudoja tą patį `"ML Kit"` JSON kaip ir anksčiau).
4. Bakstelėkite **Pasirinkite Odo**, bakstelėkite skaitmenų sėklą, sureguliuokite rankenas, tada **Išsaugoti**.
5. **Redaguoti Odo** pakoreguoja jau esantį langelį. **Clear Odo** pašalina jį, o mygtukas rodo **Pasirinkite Odo**.
6. Peržiūrėkite naudodami **Rodyti orientyrus**. Naudokite **Redaguoti OCR**, kad ištaisytumėte klaidingus skaitymus arba **pridėkite** praleistą tekstą.
7. Užpildykite **Transporto priemonės pavadinimą** (būtina) ir kaip norite, nurodykite markę/modelį/metus/plokštę.
8. Bakstelėkite **Sukurti transporto priemonę** arba **Išsaugoti pakeitimus** (reikalingas naujos transporto priemonės vardas ir nuoroda).

![Pridėti naujus transporto priemonės formos laukus](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a> Orientyrai: pataisykite tai, ko „Discovery“ praleido

Po **Rodyti orientyrus** slinkite sąrašu ir pataisykite reikšmes. Varikliai kartais praleidžia mažus skaitmenis (pvz., laikrodis **60** apatiniame dešiniajame grupės kampe). Naudokite **Redaguoti OCR**, kad juos pridėtumėte arba pataisytumėte, kad transporto priemonės tapatybė išliktų patikima.

![Dialogo langas su orientyrais – peržiūrėkite ir redaguokite OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Rašykite be tobulos nuotraukos

Vis tiek galite naudoti programą pasirinkę transporto priemonę ir užpildydami **įvesdami** odometrą, tūrį ir kainą – OCR pasirenkamas kiekviename lauke. Galerijos importavimas veikia su nuoroda brūkšnelio nuotrauka, kai nenorite fotografuoti programoje.

**Patarimas:** sinchronizavus skaičiuoklę, transporto priemonių apibrėžimai (pasėliai, orientyrai) yra vietinėje duomenų bazėje – norint juos naudoti, nereikia iš naujo atidaryti Tvarkyti transporto priemones.

---

## <a id="backups-and-multi-device-sync"></a>Atsarginės kopijos ir kelių įrenginių sinchronizavimas

Programa sukurta taip, kad **keli telefonai ar planšetiniai kompiuteriai galėtų bendrinti tuos pačius duomenų parko duomenis**, kad galėtumėte **savo duomenų ir nuotraukų kopijas ne įrenginyje**. Tai daroma su paskirties vietomis, kurias **jūs** konfigūruojate **jūsų** paskyrose arba **savo** savarankiškai priglobtus serverius – ne įmonės valdomą „Transporto priemonių išlaidų debesį“, kurį gali matyti kiti žmonės.

### <a id="what-runs-where"></a>Kas kur veikia

| Natūralus | Kas jame saugoma | Įprastas naudojimas |
|------|-----------------|-------------|
| **Skaičiuoklės / lentelės sinchronizavimas** | Transporto priemonės, degalų pildymas, išlaidos (eilutės ir skirtukai) | Kelių įrenginių sujungimas + struktūrinė atsarginė kopija |
| **Nuotraukų atsarginė kopija** | Dvejetainiai vaizdai (prietaisas / pompa / kvitas / nuorodos nuotraukos) | Nuotraukų atsarginė kopija + trūkstamų failų atkūrimas |

Galite konfigūruoti **kelias kiekvienos rūšies paskirties vietas** (kiekvieno tipo minkštas dangtelis). Rankinis **Sinchronizavimas dabar** ir **fono** darbuotojai vykdo įgalintus.

### <a id="offline-first"></a> Pirmiausia neprisijungę

– **Jokio tinklo nereikia** norint pridėti užpildymą, išlaidas ar kvitą. Viskas išsaugoma **pirmiausia vietoje**.
– kai pasiekiamas tinklas, sinchronizavimas ir nuotraukų atsarginė kopija vykdoma kaip **fono užduotys** (pagal jūsų nustatytą tvarkaraštį ir kai paliesite **Sinchronizuoti dabar**). Gedimai rodomi kaip raudonas tekstas nustatymų eilutėse ir **!** programos pavadinimo juostoje.

### <a id="your-accounts-only"></a>Tik jūsų paskyros

Jūsų pasirinktų teikėjų (Google, Microsoft, S3 raktai, savarankiškai priglobti URL ir pan.) prisijungimas ir prieigos raktai lieka įrenginyje. Paskirties vietas **visiškai valdo vartotojas** – jūsų „Google“ paskyra, „OneDrive“, „MinIO bucket“, „EtherCalc“ priegloba ir kt. Niekas nėra bendrinamas su kitais „Vehicle Expenses“ naudotojais per bendrą užpakalinę programą.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Palaikomi tikslai – duomenys (skaičiuoklė / lentelė)

Konfigūruojama **Meniu → Sinchronizavimas → Skaičiuoklės sinchronizavimas** (taip pat pasiekiama nustatymų suvestinės eilutėse). Pirmos klasės rinkiklio parinktys:

| Tikslas | Pastabos |
|---------|--------|
| **„Google“ skaičiuoklės** | Įprastas nutylėjimas; skirtukai transporto priemonėms, išlaidoms ir degalams vienai transporto priemonei |
| **Excel** | „Microsoft“ darbaknygė naudojant „Graph“ / „OneDrive“ stiliaus įrišimą |
| **EtherCalc** | Savarankiškai priglobtos bendradarbiavimo skaičiuoklių patalpos |
| **Kitos →** įdiegtos užpakalinės programos | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Atidėtas / dar ne be galvos (išvardytas skiltyje Kita, bet ne visiškai įgyvendintas): OnlyOffice, Collabora. Taip pat žr. [self-host index](reference/self-host/INDEX.md).

CSV **eksportavimas / importavimas** (to paties skirtuko išdėstymo ZIP kodas) pasiekiamas nustatymuose kaip nešiojama atsarginė kopija, neatsižvelgiant į tiesioginį sinchronizavimą.

### <a id="supported-targets--photos-image-backup"></a>Palaikomi tikslai – nuotraukos (atsarginė vaizdo kopija)

Sukonfigūruota **Meniu → Sinchronizavimas → Nuotraukų atsarginė kopija** (taip pat iš nustatymų suvestinės eilučių):

| Tikslas | Pastabos |
|---------|--------|
| **„Google“ diskas** | Pasirinktas aplankas (naršykite arba įklijuokite URL) |
| **OneDrive** | „Microsoft“ paskyra + kelio priešdėlis |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO ir kiti su S3 suderinami galutiniai taškai |
| **Kita** | rclone palaikoma saugykla (pvz., WebDAV, SFTP ir kiti kuruojami nuotolinio valdymo pultai, pasiekiami programoje esančiame rinkiklyje) |

Nustatyti savarankiškai priglobtų nuotraukų ir lentelių taikinius: [self-host index](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Kelių įrenginių veikimas (trumpai)

– eilutės sujungiamos naudojant **sinchronizavimo ID** su **paskutinės rašymo laimėjimai** **Atnaujintose** laiko žymose.
– ištrynimai yra minkšti; naujesnis redagavimas kitame įrenginyje gali atkurti eilutę.
– įvedus **tą patį užpildymą du kartus** dviejuose įrenginiuose sukuriamos **dvi eilutės** – pastebėję papildomus elementus ištrinkite.
– daugiau informacijos: [Sinchronizavimo pastabos](#sync-behavior-notes) ir [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Pavyzdys: pridėkite „Google“ skaičiuokles (duomenis)

1. **Meniu → Sinchronizavimas → Skaičiuoklės sinchronizavimas** (arba Nustatymai → Skaičiuoklės sinchronizavimas).

![Skaičiuoklės sinchronizavimo sąrašas](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Bakstelėkite **Pridėti skaičiuoklės paskirties vietą**.

![Teikėjo rinkiklis](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Pasirinkite **„Google“ skaičiuoklės**.

![„Google“ skaičiuoklių forma](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Prisijunkite naudodami „Google“** vieną kartą – prisijungę taip pat galite naršyti. Rodomas pavadinimas → įklijuokite **Lakšto URL** (vis dar galioja) arba **🔍** naršykite. Naršymas atidaromas skiltyje **Visi ir bendrinami**, kad galėtumėte pasirinkti esamą skaičiuoklę, kuri jums priklauso arba kuri yra bendrinama su jumis. Tada suplanuokite parinktis → įgalinti → išsaugoti.
5. **Sinchronizuokite dabar** vieną kartą, kad sukurtumėte / atnaujintumėte skirtukus: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Pavyzdys: pridėkite „Google“ diską (nuotraukas)

1. **Meniu → Sinchronizavimas → Nuotraukų atsarginė kopija** (arba Nustatymai → Nuotraukos atsarginė kopija).

![Atsarginių nuotraukų sąrašas](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Bakstelėkite **Pridėti nuotraukos paskirties vietą**.

![Nuotraukų teikėjo rinkiklis](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Pasirinkite **Google Drive**.

![„Google“ disko forma](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Prisijunkite naudodami „Google“ (Drive)** → pasirenkamas aplanko URL / naršyti → įgalinti → išsaugoti → **Sinchronizuoti dabar**.

Rankinis **Sinchronizuoti dabar** nuotraukoms yra pilnas leidimas; fono atsarginė kopija paprastai apdoroja **tik laukiančius** įkėlimus pagal tvarkaraštį.

### <a id="sync-behavior-notes"></a> Sinchronizavimo elgesio pastabos

– atnaujinę programą galite trumpam pamatyti **„Duomenų bazės atnaujinimas po naujovinimo…“** (vietinis sinchronizavimo ID užpildymas).
– jei sinchronizavimas nutrūksta, kitas **sėkmingas** sinchronizavimas iš naujo sujungiamas ir taisomi nuotoliniai skirtukai.
– gedimai: raudona suvestinė sinchronizuojamose kortelėse + **!** programų juostoje.

---

## <a id="fill-up-fuel"></a> Užpildykite (degalai)

Tai yra **pagrindinis ekranas**, kai atidarote programą.

### <a id="vehicle-selection-usually-automatic"></a>Transporto priemonės pasirinkimas (dažniausiai automatinis)

Pirmiausia **ne**reikia pasirinkti transporto priemonės. Kai transporto priemonėse yra nustatyti **orientyrai** skiltyje „Tvarkyti transporto priemones“, užpildymas **automatiškai aptinka, kuri transporto priemonė** iš brūkšninio vaizdo, kai užfiksuojate odometrą. Vis tiek galite atidaryti išskleidžiamąjį meniu **Transporto priemonė**, kad prireikus nepaisytumėte.

### <a id="aim-at-the-odometer"></a> Nukreipkite į odometrą

Likite odometro režimu ir įrėminkite grupę. Instrukcija: * Nukreipkite į odometrą. Norėdami užfiksuoti, palieskite užraktą.*

![Užpildykite – tiesioginė odometro kamera (pavyzdys brūkšnelio vaizdas peržiūroje)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Po odometro sklendės

OCR užpildo **Odo** ir bando suderinti transporto priemonę pagal orientyrus (jei reikia, peržiūrėkite abu). Pagrindinis mygtukas tampa **Retry**, kad fotografuotumėte iš naujo. Instrukcija apibendrina skaitymą.

![Užpildykite – odometro OCR rezultatas](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Siurblio režimas (kaina ir tūris)

1. Bakstelėkite **↕**, kad įjungtumėte siurblio režimą: * Nukreipkite į siurblio ekraną (kaina / tūris). Bakstelėkite užraktą.*
2. Užfiksuokite siurblio sumas. Kainos ir apimties laukai užpildomi; naudokite **↔**, jei jie yra pakeisti.
3. Palieskite valiutą arba **G/L**, jei reikia, tada **Išsaugoti** (diske). Tušti laukai užpildomi **dalinai** (vis dar leidžiama).

![Užpildyti – siurblio peržiūra / sumos (siurblio vaizdo pavyzdys peržiūroje)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Jūs liekate užpildyti iki kitos stotelės (laukai išvalomi po išsaugojimo). Darbas visiškai neprisijungęs**; sinchronizavimas veikia vėliau fone, kai sukonfigūruotas.

### <a id="manual-entry-no-camera--bad-ocr"></a>Rankinis įvedimas (be kameros / blogas OCR)

1. Palieskite **Odo**, **cost** arba **volume** ir įveskite reikšmes (stačias naudoja sistemos klaviatūrą, gulsčias – ekrano klaviatūrą).
2. Pasirinkite arba patvirtinkite **Transporto priemonė**, jei automatinis aptikimas neveikia.
3. Išsaugokite kaip nurodyta aukščiau.

### <a id="modes-and-borders"></a>Režimai ir kraštinės

– **Žalia kraštinė** aplink transporto priemonę+odo → odometro fiksavimas / redagavimas.
– **Žalias kraštelis** aplink sąnaudas+tūrį → siurblio režimas.
– **Išsaugoti** lieka išjungta, kol pasirenkama transporto priemonė ir bent vienas iš odo/cost/volume turi duomenų, o OCR vis dar neveikia.

Patarimas ekrane (po instrukcijų eilute): *Užraktas = užfiksuoti · Diskas = išsaugoti · ↕ = odo / siurblio režimas · ↔ = apsikeitimo kaina / apimtis.*

---

## <a id="expenses"></a>Išlaidos

### <a id="new-expense"></a>Naujos išlaidos

Meniu → **Naujos išlaidos**.

![Nauja išlaidų forma ir fotoaparato valdikliai (pavyzdys brūkšnelio vaizdas kameros juostoje, kai tuščia)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Išsaugoti** (diske), **užraktas** (kvito nuotrauka) arba **galerija** (pasirinkite vaizdą).
2. Užpildykite **Data**, **Transporto priemonė**, **Pardavėjas**, **Aprašymas**, **Suma** (valiutos simbolis, palieskite), **Kategorija**, neprivaloma **Odometras**.
3. Kelių puslapių kvitai: užfiksuokite papildomus puslapius, jei vartotojo sąsaja siūlo puslapius (0 puslapis yra pagrindinis kvitas).
4. **Išsaugoti**, kad būtų galima saugoti (pirmiausia vietoje; nuotraukų atsarginė kopija ir skaičiuoklės sinchronizavimas vyksta fone, kai sukonfigūruota).

### <a id="expense-list"></a> Išlaidų sąrašas

Meniu → **Ataskaitos** → **Išlaidų sąrašas** — naršyti ankstesnes ne kuro išlaidas; atidaryti elementą, kurį norite redaguoti.

![Išlaidų sąrašas](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Redaguoti išlaidas

Atidarykite eilutę iš sąrašo. Teisingas pardavėjas, kiekis, kategorija, transporto priemonė ir aprašymas. Jei kvitas yra tik atsarginėje nuotraukos kopijoje (nėra skaitomo vietinio failo), naudokite **Gauti vaizdą iš archyvo**, kai rodoma (veikia sukonfigūruotose nuotraukų paskirties vietose).

![Redaguoti išlaidas](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Pradėkite kelionę

Meniu → **Pradėti kelionę** (po Užpildykite stalčių). Užfiksuokite arba įveskite odometrą, pasirinkite kelionės tipą, išsaugokite naudodami **disko** piktogramą. **Stop** yra nuoroda į asmeninį dabar sulaikytoje GPS vietoje. Valdymo priminimams naudokite **ⓘ**.

![Pradėti kelionę](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Kelionės startai išsaugomi kaip kuro eilutės su **Kelionės tipas** (ne įprastas užpildymas). Jie rodomi skiltyje **Ataskaitos → Kelionės mylios**, o ne kuro istorija.

---

## <a id="reports"></a>Ataskaitos

Meniu → **Ataskaitos** atidaromas produktų centras (visų laikų suvestinė + katalogo kortelės). Tai vienintelis gaminio ataskaitų paviršius – nėra atskiro „Ataskaitų ir diagramų“ stalčiaus elemento.

![Ataskaitų centras](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Atidarykite kortelę, skirtą transporto priemonės režimui (**Visi / Kiekvienas / vienas**), laikotarpio filtrus, diagramas ir bendrinimą (**TEXT / CSV / PDF**). Viršutinė pranešimų apie vaikus juosta: **☰ + ←** (ir **ⓘ** registruojantis).

### <a id="time-based-reports"></a>Laiku pagrįstos ataskaitos

Pagrindinė diagramos kortelė. Pasirenkama metrika (mpg, tūris / atstumas, pvz., G/my, vieneto kaina, pvz., $ / G, kaina / atstumas, mėnesio $, kelionės mylios, kelionės % pagal tipą) su **Smooth** dėžėmis ir **nepriklausomomis Y svarstyklėmis** (ekonomiška kairėje; pinigų ir kelionių šeimos dešinėje).

![Laiku pagrįstos ataskaitos](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Laiku pagrįstos ataskaitos – slenkama metrika](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Ekonomikos matematikos informacija: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a> Pildymo istorija ir degalų istorija

– **Ataskaitos → Pildymo istorija** – chronologinis ataskaitų filtrų užpildymas (**tik pildymai**; kelionė neprasideda).

![Užpildyti istorijos ataskaitą](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

– **Degalų istorija** (jei yra jūsų versijos navigacijoje) – kiekvienos transporto priemonės užpildymo inventorius, taip pat tik pildomas; bakstelėkite eilutę, kad galėtumėte redaguoti.

### <a id="trip-miles"></a>Kelionės mylios

**Ataskaitos → Kelionės mylios** – mylios pagal tipą, diagramos ir chronologinis **kelionės pradžios / segmentų sąrašas**. Palieskite tikrą pradžią, kad atidarytumėte tos eilutės **Redaguoti užpildymą**.

![Kelionės mylios](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Redaguoti užpildymą

Skiltyje Pildymo istorija, Degalų istorija arba Kelionės mylios atidarykite užpildymą. Išdėstymas: transporto priemonė ir odometras, **valiuta prieš savikainą**, tūris, pastabos. Kelionės tipas rodomas tik tada, kai eilutė yra kelionės pradžia. Vietovėje yra santrauka ir **Vietos informacija**. Trūksta vietinės nuotraukos su debesies tapatybe: **Gauti vaizdą iš archyvo**.

![Redaguoti užpildą – gauti iš archyvo, kai trūksta vietos nuotraukos](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Kitose katalogų kortelėse pateikiamos išlaidos pagal kategorijas, transporto priemonės suvestinė ir išlaidų sąrašas.

Pinigai naudoja kiekvienos eilutės valiutą, kai ji nustatyta. Mišrios valiutos sumos rodo **valiutos tarpines sumas** (be tylaus FX konvertavimo).

---

## <a id="syncing"></a> Sinchronizuojama

Meniu → **Sinchronizavimas** yra skaičiuoklių ir nuotraukų paskirties vietų centras (ne tik paslėptas skiltyje Nustatymai).

![Sinchronizavimo centras](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

– kortelės, skirtos **Skaičiuoklės sinchronizavimui** ir **Nuotraukų atsarginei kopijai** su trumpa būsena, **Sinchronizacija** šiai rūšiai ir **›** į paskirties sąrašą.
– atidarykite paskirties vietą **Tikrinti ryšį** ir **Sinchronizuoti dabar (šią paskirties vietą)** / viskas sukonfigūruota.
– gedimas **Išsami informacija** ir raudona **!** pavadinimo juostoje patenka čia.
– Žingsnis po žingsnio „Google“ skaičiuoklių ir disko sąranka: [Atsarginės kopijos ir kelių įrenginių sinchronizavimas] (#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Nustatymai (vietinės nuostatos)

Meniu → **Nustatymai**.

![Nustatymai](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Daugiau nustatymų (slinkta)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Tikslams rinkitės **Meniu → Sinchronizavimas**. Nustatymuose vis tiek gali būti rodomos suvestinės eilutės, kurios atidaro tuos pačius sąrašus.

### <a id="local-preferences-common"></a>Vietinės nuostatos (dažnos)

– **Taupykite kuro kvito nuotraukas** / ** Išsaugokite išlaidų nuotraukas vietoje** – laikykite vaizdus įrenginyje (galite paprašyti nuotraukų leidimo).
– **Leiskite užrakto garsą**
– **Valiuta** / **Tūrio vienetas** – programos numatytieji nustatymai (sisteminiai arba aiškūs). Pakeitus tūrio vienetą su esamais degalų duomenimis, gali atsirasti konvertavimo dialogo langas.
– **Tamsus režimas**
– **Sąrankos patarimai** – iš naujo atidarykite pirmojo paleidimo transporto priemonės / sinchronizavimo mokymo programas.
– **Debug Fill** / **Rodyti eksperimento ekranus (dev)** – išplėstinis; palikite kasdieniniam naudojimui. Eksperimento ekranai čia nėra dokumentuojami.

CSV **eksportavimas / importas** (transporto priemonių / išlaidų / degalų ZIP) pasiekiamas nustatymuose, kai tai siūlo dabartinė versija.

---

## <a id="help--about"></a>Pagalba ir apie

![Pagalba](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Apie](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

– **Pagalba** – įrenginio greita pradžia, sąrankos vadovėliai, nuoroda į šį vadovą, savarankiškos prieglobos sąrankos rodyklė.
– **Apie** – versija, licencijos, „GitHub“, šis vadovas (sudėtas neprisijungus + internetinis HTML, kai paskelbiamas).

---

## <a id="related-docs"></a>Susiję dokumentai

– [QUICK_GUIDE.md](../QUICK_GUIDE.md) – trumpas kasdienis vadovas
– [self-host/INDEX.md](../reference/self-host/INDEX.md) – savarankiškai priglobtos nuotraukos / lentelės sąranka
– [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) – sujungimas, atkūrimas, dublikatai
– [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) – išsami ekonominės metrikos informacija
