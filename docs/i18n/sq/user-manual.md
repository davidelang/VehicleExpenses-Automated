# <a id="vehicle-expenses-automated--user-manual"></a>Shpenzimet e automjeteve të automatizuara — Manuali i përdoruesit

> **Redakto burimin (Markdown).** Shfletuesit dhe lexuesi brenda aplikacionit hapin **HTML-në e dhënë**:
> - Ueb: [`docs/user-manual/index.html`](index.html) (rigjeneroje me `./scripts/render-user-manual.sh`)
> - Aplikacioni: Ndihmë / Rreth → manual i plotë (HTML i bashkuar + pamjet e ekranit)
>
> Mos i drejto përdoruesit fundor drejt URL-ve të papërpunuara `.md` — shfletuesit shfaqin vetëm tekst të thjeshtë.

Gjurmimi i parë i kamerës për mbushjet e karburantit dhe shpenzimet e automjetit, me sinkronizimin dhe rezervimin opsional të shumë pajisjeve nën **llogaritë tuaja** në renë kompjuterike.

Ky është **manuali i plotë** (pamjet e ekranit + çdo hap). Në telefon, **Menyja → Ndihma** është një udhëzues më i shkurtër për fillimin.

**Nuk mbulohet këtu:** Importoni fotografi të vjetra, eksperimentin e shtrirjes dhe eksperimentin e pompës (zhvilluesi / vegla të avancuara).

---

## <a id="table-of-contents"></a>Tabela e përmbajtjes

1. [Ajo që ju nevojitet] (#what-you-need)
2. [Ikonat me një shikim](#icons-at-a-glance)
3. [Hap menunë](#open-the-menu)
4. [Konfigurimi për herë të parë: Menaxho automjetet](#first-time-setup-manage-vehicles)
5. [Rezervimet dhe sinkronizimi me shumë pajisje](#backups-and-multi-device-sync)
6. [Mbush (karburant)](#fill-up-fuel)
7. [Fillimi i udhëtimit] (#start-trip)
8. [Shpenzimet](#expenses)
9. [Raporte](#reports)
10. [Cilësimet (preferencat lokale)] (#settings-local-preferences)
11. [Sinkronizimi](#syncing)
12. [Ndihmë & Rreth](#help--about)
13. [Dokumente të ngjashme](#related-docs)

---

## <a id="what-you-need"></a>Ajo që ju nevojitet

- telefon ose tablet Android.
- Për OCR më të mirë: një pamje e qartë e **odometrit të pultit** dhe **totaleve të pompës** (ose shkruani numrat me dorë).
- Opsionale: llogaritë **ju kontrolloni** për të dhënat e fletëllogaritjes dhe/ose rezervimin e fotografive (shihni [Rezervimet dhe sinkronizimi me shumë pajisje](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Ikonat me një shikim

Këto shfaqen në ekranet kryesore. Njohja e tyre kursen shumë gjueti.

| Ku | Ikona / kontrolli | Çfarë bën |
|-------|----------------|--------------|
| Shiriti i sipërm | **☰ Menu ** (hamburger) | Hap sirtarin e lundrimit |
| Shiriti i sipërm | **ⓘ** (ndihma e faqes) | Ndihmë e shkurtër për faqen **aktuale** (pranë menysë kur është e disponueshme) |
| Shiriti i sipërm | **`?N`** (e verdhë) | Pyetje në pritje të shqyrtimit të importit — hapet Rishikimi i importit |
| Shiriti i sipërm | ***!** (e kuqe) | Një fletëllogaritëse ose destinacion fotografie dështoi së fundi — hapni **Sinkronizimi** për të rregulluar |
| Shiriti i sipërm | **☰ + ←** | Raporti i fëmijëve dhe lista e shpenzimeve shfaq **menynë dhe mbrapa** së bashku; Qendra e raporteve është vetëm menu |
| Cilësimet / redaktimi i karburantit | **←** | Mbrapa (tabela e cilësimeve/fotografia dhe redaktimi i karburantit qëndrojnë të fokusuara) |
| Plotësoni | **Rrethi i bardhë** (qepeni) | Kapni odometrin ose ekranin e pompës për OCR |
| Plotësoni | **Disku / Ruaj ** | Ruani mbushjen (ka nevojë për një automjet dhe të paktën një nga odo / vëllim / kosto) |
| Plotësoni | **↕ shigjeta** (ndërprerës i modalitetit) | Ndrysho modalitetin **odometri ** kundrejt modalitetit **pompë (kosto/vëllim) **. Kufiri i gjelbër thekson grupin e fushave aktive |
| Plotësoni | **↔ shigjeta** (midis kostos dhe vëllimit) | Ndryshoni koston dhe vëllimin nëse OCR i vendos ato në fushat e gabuara |
| Plotësoni | **Zmadhimi 1x / …** | Raportet e zmadhimit të kamerës kur lentet i mbështet |
| Plotësoni (pas kapjes) | **Rifresko** në butonin kryesor | Hiq pamjen paraprake dhe kthehu te kamera e drejtpërdrejtë |
| Plotësoni (gjatë përpunimit) | **X** në butonin kryesor | Anulo regjistrimin në vazhdim/OCR |
| Shpenzime | **Ruaj** | Ruani shpenzimet |
| Shpenzime | **Rrethi i grilave** | Bëj një foto faturë |
| Shpenzime | **Galeria** | Zgjidh një imazh dëftese nga biblioteka |
| Shpenzime | **Rimerre** | Pastro foton e marrjes aktuale dhe shkrepi sërish |
| Shpenzime / Menaxhoni automjete | **+ / −** FABs | Zmadhoni pamjen paraprake të fotografisë |
| Dialogu i pikave të referimit | **Modifiko OCR** ​​| Korrigjoni ose shtoni tekstin pikë referimi që motorët humbën |
| Spreadsheet / Format e fotografive | **🔍 Kërko ** | Shfletoni Google Drive për një fletë ose dosje (pas identifikimit) |

Simbolet e monedhës në fushat e kostos dhe **G/L** në fushat e volumit mund të preken: hapni një meny të vogël për të ndryshuar monedhën ose gallon kundrejt litrave për atë hyrje.

---

## <a id="open-the-menu"></a>Hap menunë

1. Prekni **☰** lart majtas.
2. Zgjidhni një faqe.

![Sirtari i navigimit](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Sirtari kryesor:** Mbushja · Fillimi i udhëtimit · Menaxho automjetet · Shpenzime të reja · **Raporte** · Cilësimet · Sinkronizimi · Ndihma · Rreth.

**Sirtari i eksperimentit** (Cilësimet → Shfaq ekranet e eksperimentit): Eksperimenti i shtrirjes · Eksperimenti i pompës · **Importoni fotografi të vjetra**.

**Përmes qendrës së raporteve (jo sirtar kryesor):** Lista e shpenzimeve · Plotësoni historinë.

---

## <a id="first-time-setup-manage-vehicles"></a>Konfigurimi për herë të parë: Menaxho automjetet

OCR dhe **përputhja automatike e automjetit** funksionojnë më mirë pasi të regjistroni çdo automjet me **fotografinë e pultit të referencës**, të ekzekutoni **Discovery**, të vendosni prerjen e odometrit nga fara e lopës dhe të ruani pikat referuese për atë vizë.

### <a id="open-manage-vehicles"></a>Hap Menaxho Vehicles

Menuja → **Menaxho automjetet**. Zgjidhni një automjet (ose **Shto automjet të ri**).

![Menaxho automjetet me pikë referimi, të korrat dhe zbulimin](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Shto ose modifiko një automjet

1. Hapni menynë rënëse **Vehicle** → zgjidhni një automjet ose **Shto automjet të ri**.
2. Regjistroni ose zgjidhni një **foto të qartë referimi** (grumbull i plotë instrumentesh, i ndriçuar mirë, telefoni afërsisht katror). Përdorni **Take Photo** ose **Galery**.
3. Prekni **Run Discovery** — zbulimi i vozit vendos **farat** të verdha në foto (pikat referuese përdorin të njëjtën `"ML Kit"` JSON si më parë).
4. Prekni **Zgjidhni Odo**, prekni farën në shifra, rregulloni dorezat dhe më pas **Ruaj**.
5. **Edit Odo** rregullon një kuti që është tashmë aty. **Pastro Odo** e heq atë dhe butoni më pas lexon **Zgjidh Odo**.
6. Rishikoni me **Trego pikat e referimit**. Përdorni **Modifiko OCR** ​​për të rregulluar leximet e gabuara ose **shtuar** tekstin e humbur.
7. Plotësoni **Emri i automjetit** (kërkohet), plus markën/modelin/vitin/pllakën sipas dëshirës.
8. Prekni **Krijo automjet** ose **Ruaj ndryshimet** (kërkon emrin + fotografinë e referencës për një automjet të ri).

![Shto fushat e reja të formularit të automjetit](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Pikë referimi: rregulloni atë që zbuloi

Pas **Show Landmarks**, lëvizni listën dhe korrigjoni vlerat. Motorët ndonjëherë humbasin shifra të vogla (për shembull një orë **60** në fund të djathtë të grupit). Përdorni **Modifiko OCR** ​​për t'i shtuar ose rregulluar në mënyrë që identiteti i automjetit të mbetet i besueshëm.

![Dialogu i Landmarks — rishiko dhe modifiko OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Shkrimi pa një foto perfekte

Mund ta përdorni ende aplikacionin duke zgjedhur një automjet dhe **shkruar** odometrin, volumin dhe koston në Plotësim - OCR është opsionale për çdo fushë. Importimi i galerisë funksionon për foton e pikës së referencës kur preferoni të mos shkrepni brenda aplikacionit.

**Këshillë:** Pas sinkronizimit të fletëllogaritjes, përkufizimet e automjeteve (të korrat, pikat referuese) jetojnë në bazën e të dhënave lokale — nuk keni nevojë të rihapni Manage Vehicles for Fill për t'i përdorur ato.

---

## <a id="backups-and-multi-device-sync"></a>Rezervimet dhe sinkronizimi me shumë pajisje

Aplikacioni është ndërtuar në mënyrë që **disa telefona ose tabletë të mund të ndajnë të njëjtat të dhëna të flotës** dhe kështu të mund të mbani një **kopje të të dhënave dhe fotove tuaja jashtë pajisjes**. Kjo bëhet me destinacionet që **ju** i konfiguroni nën **llogaritë tuaja** ose **serverët tuaj** të vetë-strehuar - jo një "Reja e Shpenzimeve të Automjeteve" të drejtuar nga kompania që mund ta shohin njerëzit e tjerë.

### <a id="what-runs-where"></a>Çfarë shkon ku

| Lloj | Çfarë ruan | Përdorimi tipik |
|------|----------------|-------------|
| **Sinkronizimi i fletëllogaritjes/tabelës** | Automjete, mbushje me karburant, shpenzime (rreshta dhe skeda) | Bashkim shumë pajisjesh + kopje rezervë e strukturuar |
| **Rezervimi i fotografisë** | Imazhet binare (pika/pompa/fotot e marrjes/referencat) | Rezervimi i fotografive + rivendosja e skedarëve që mungojnë |

Mund të konfiguroni **shumë destinacione** të çdo lloji (kapak i butë për çdo lloj). Punëtorët manuale **Sinkronizoni tani** dhe **Sfondi** ekzekutojnë ato të aktivizuara.

### <a id="offline-first"></a>Së pari jashtë linje

- **Nuk kërkohet rrjet** për të shtuar një plotësim, shpenzim ose faturë. Gjithçka ruhet **në vend të parë**.
- Kur rrjeti është i disponueshëm, sinkronizimi dhe rezervimi i fotografive ekzekutohen si **detyra në sfond** (në një orar që vendosni dhe kur trokitni lehtë mbi **Sinkronizoni tani**). Dështimet shfaqen si tekst i kuq nën rreshtat e Cilësimeve dhe një **!** në shiritin e titullit të aplikacionit.

### <a id="your-accounts-only"></a>Vetëm llogaritë tuaja

Identifikimi dhe shenjat qëndrojnë në pajisje për ofruesit që zgjidhni (Google, Microsoft, çelësat S3, URL-të e vetë-strehuara etj.). Destinacionet janë nën **kontrollin e plotë të përdoruesit** — llogaria juaj e Google, OneDrive juaj, kova juaj MinIO, hosti juaj EtherCalc, etj. Asgjë nuk ndahet me përdoruesit e tjerë të shpenzimeve të automjeteve përmes një fundi të përbashkët.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Objektivat e mbështetur — të dhëna (tabelare/tabelare)

Konfiguruar te **Menyja → Sinkronizimi → Sinkronizimi i fletëllogaritjes** (e arritshme gjithashtu nga rreshtat e përmbledhjes së Cilësimeve). Opsionet e zgjedhësve të klasit të parë:

| Synimi | Shënime |
|--------|--------|
| **Fletët e Google** | Parazgjedhje e zakonshme; skeda për automjete, shpenzime dhe karburant për automjet |
| **Excel** | Libri i punës i Microsoft përmes lidhjes Graph / OneDrive |
| **EtherCalc** | Dhomat e tabelave bashkëpunuese të organizuara vetë |
| **Të tjera →** mbështetëse të implementuara | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

E shtyrë / jo ende pa kokë (të listuara nën Tjetër por jo plotësisht të zbatuar): OnlyOffice, Collabora. Shihni gjithashtu [indeksi i vetë-pritësit](reference/self-host/INDEX.md).

CSV **eksporti/importi** (ZIP i skedës së njëjtë) ofrohet nga Cilësimet si një rezervë portative, pavarësisht nga sinkronizimi i drejtpërdrejtë.

### <a id="supported-targets--photos-image-backup"></a>Objektivat e mbështetur — foto (rezervë e imazhit)

Konfiguruar te **Menyja → Sinkronizimi → Rezervimi i fotografive** (gjithashtu nga rreshtat përmbledhës të Cilësimeve):

| Synimi | Shënime |
|--------|--------|
| **Google Drive** | Dosja që zgjidhni (shfletoni ose ngjisni URL-në) |
| **OneDrive** | Llogaria Microsoft + prefiksi i rrugës |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO dhe pika të tjera përfundimtare të pajtueshme me S3 |
| **Të tjera ** | ruajtja e mbështetur nga rclone (p.sh. WebDAV, SFTP dhe telekomanda të tjera të kuruara të disponueshme në përzgjedhësin brenda aplikacionit) |

Konfiguro fletët e mashtrimit për objektivat e fotografive të vetë-strehuara dhe tabelore: [indeksi i vetë-pritësit](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Sjellja me shumë pajisje (e shkurtër)

- Rreshtat bashkohen nga **Sinkronizimi ID** me **fitimet e shkrimit të fundit** në vulat kohore **të përditësuara**.
- Fshijet janë të buta; një modifikim më i ri në një pajisje tjetër mund të rivendosë një rresht.
- Duke futur **të njëjtën mbushje dy herë** në dy pajisje krijohen **dy rreshta** — fshini shtesën kur ta vini re.
- Më shumë detaje: [Sinkronizimi i shënimeve të sjelljes](#sync-behavior-notes) dhe [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Shembull: shtoni Google Sheets (të dhëna)

1. **Menyja → Sinkronizimi → Sinkronizimi i fletëllogaritjes** (ose Cilësimet → Sinkronizimi i fletëllogaritjes).

![Lista e sinkronizimit të fletëllogaritjes](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Prekni **Shto destinacionin e fletëllogaritjes**.

![Zgjedhësi i ofruesit](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Zgjidhni **Fletët e Google**.

![Formulari i Fletëve Google](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Identifikohu me Google** një herë — ky identifikim ju lejon gjithashtu të shfletoni. Emri i shfaqur → ngjisni një **URL të fletës** (ende e vlefshme) ose **🔍** shfletoni. Shfletimi hapet në **Të gjitha dhe të ndara** në mënyrë që të mund të zgjidhni një fletëllogaritëse ekzistuese që zotëroni ose që është e ndarë me ju. Më pas planifiko opsionet → aktivizo → ruaj.
5. **Sinkronizoni tani** një herë për të krijuar/përditësuar skedat: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Shembull: shtoni Google Drive (foto)

1. **Menyja → Sinkronizimi → Rezervimi i fotografive** (ose Cilësimet → Rezervimi i fotografisë).

![Lista rezervë e fotografive](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Prekni **Shto destinacionin e fotografisë**.

![Zgjedhësi i ofruesit të fotografive](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Zgjidhni **Google Drive**.

![Formulari i Google Drive](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Identifikohu me Google (Drive)** → URL e dosjes opsionale/shfleto → aktivizo → ruaj → **Sinkronizoje tani**.

Manual **Sinkronizo tani** për fotot është një proces i plotë; kopjimi rezervë në sfond zakonisht përpunon vetëm ngarkimet **në pritje** sipas një orari.

### <a id="sync-behavior-notes"></a>Sinkronizoni shënimet e sjelljes

- Pas azhurnimit të aplikacionit, mund të shihni shkurtimisht **“Përditësimi i bazës së të dhënave pas përmirësimit…”** (mbushja lokale e sinkronizimit me ID).
- Nëse ndërpritet një sinkronizim, sinkronizimi i ardhshëm ** i suksesshëm** ribashkohet dhe riparon skedat në distancë.
- Dështimet: përmbledhje e kuqe në kartat e sinkronizimit + **!** në shiritin e aplikacioneve.

---

## <a id="fill-up-fuel"></a>Mbush (karburant)

Ky është **ekrani bazë** kur hapni aplikacionin.

### <a id="vehicle-selection-usually-automatic"></a>Zgjedhja e automjetit (zakonisht automatike)

Ju **nuk** keni nevojë të zgjidhni më parë automjetin. Kur automjetet kanë **pika referimi** të konfiguruara te "Menaxho automjetet", Fill ** zbulon automatikisht se cilin automjet** nga imazhi i vizës pasi të kapësh odometrin. Mund të hapësh gjithsesi listën rënëse **Vehicle** për ta anuluar nëse është e nevojshme.

### <a id="aim-at-the-odometer"></a>Synoni odometrin

Qëndroni në modalitetin e odometrit dhe kornizojeni grupin. Udhëzim: *Synoni odometrin. Prekni grilat për të kapur.*

![Mbush — kamera e drejtpërdrejtë në odometër (shembull imazhi i vizës në pamjen paraprake)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Pas mbylljes së odometrit

OCR mbush **Odo** dhe përpiqet të përputhet me automjetin nga pikat e referimit (rishiko të dyja nëse është e nevojshme). Butoni kryesor bëhet **Riprovo** për të rixhiruar. Udhëzimi përmbledh leximin.

![Plotëso - rezultati OCR i odometrit](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Modaliteti i pompës (kostoja dhe vëllimi)

1. Prekni **↕** për të kaluar në modalitetin e pompimit: *Synoni shfaqjen e pompës (kosto/vëllim). Prekni grilat.*
2. Kapni totalet e pompës. Plotësoni fushat e kostos dhe vëllimit; përdorni **↔** nëse ato ndërrohen.
3. Prekni monedhën ose **G/L** nëse nevojitet, më pas **Ruaj** (disk). Fushat boshe bëjnë një **mbushje të pjesshme** (lejohet ende).

![Mbush — pamje paraprake e pompës / totalet (shembull imazhi i pompës në pamje paraprake)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Ju qëndroni në Fill për ndalesën tjetër (fushat pastrohen pas ruajtjes). Punoni plotësisht **offline**; sinkronizimi funksionon më vonë në sfond kur konfigurohet.

### <a id="manual-entry-no-camera--bad-ocr"></a>Hyrja manuale (pa kamera / OCR e keqe)

1. Prekni **Odo**, **kosto** ose **volumi** dhe shkruani vlerat (portreti përdor tastierën e sistemit; peizazhi përdor një tastierë në ekran).
2. Zgjidhni ose konfirmoni **Vehicle** nëse zbulimi automatik nuk funksionoi.
3. Ruaj si më sipër.

### <a id="modes-and-borders"></a>Modalitetet dhe kufijtë

- **Kufiri i gjelbër** rreth automjetit+odo → kapja/redaktimi i odometrit.
- **Kufi i gjelbër** rreth kosto+volumi → modaliteti i pompës.
- **Ruaj** qëndron i çaktivizuar derisa të zgjidhet një automjet dhe të paktën njëra nga odo/kosto/vëllimi të ketë të dhëna dhe OCR nuk është ende në punë.

Këshillë në ekran (nën vijën e udhëzimeve): *Gafra = kapja · Disku = ruaj · ↕ = modaliteti i odos/pompës · ↔ = ndërroni koston/vëllimin.*

---

## <a id="expenses"></a>Shpenzime

### <a id="new-expense"></a>Shpenzim i ri

Menu → **Shpenzim i ri**.

![Formulari i ri i shpenzimeve dhe kontrollet e kamerës (shembull imazhi i vizës në brezin e kamerës kur është bosh)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Ruaj** (disk), **kapen ** (foto e marrjes) ose **galeri** (zgjidh imazhin).
2. Plotësoni **Data**, ** Automjeti**, **Shitësi**, **Përshkrimi**, **Shuma** (simboli i monedhës mund të preket), **Kategoria**, **Odometer** opsionale.
3. Faturat me shumë faqe: kapni faqe shtesë nëse ndërfaqja e përdoruesit ofron faqezim (faqja 0 është marrja kryesore).
4. **Ruaj** për të ruajtur (së pari lokale; rezervimi i fotografive dhe sinkronizimi i fletëllogaritjes ndodhin në sfond kur konfigurohen).

### <a id="expense-list"></a>Lista e shpenzimeve

Menuja → **Raportet** → **Lista e shpenzimeve** — shfletoni shpenzimet e kaluara jo të karburantit; hapni një artikull për të modifikuar.

![Lista e shpenzimeve](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Redakto shpenzimet

Hapni një rresht nga lista. Shitësi i saktë, sasia, kategoria, automjeti dhe përshkrimi. Nëse fatura është vetëm në kopje rezervë të fotografive (pa skedar lokal të lexueshëm), përdorni **Merr imazhin nga arkivi** kur shfaqet (funksionon nëpër destinacionet e konfiguruara të fotografive).

![Redakto shpenzimet](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Filloni udhëtimin

Menuja → **Fillimi i udhëtimit** (pasi të mbushni sirtarin). Kapni ose futni odometrin, zgjidhni llojin e udhëtimit, ruajeni me ikonën **disk**. **Stop** është një shkurtore për Personal tani në vendndodhjen e mbajtur GPS. Përdorni **ⓘ** për rikujtuesit e kontrollit.

![Fillimi i udhëtimit](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Fillimet e udhëtimit ruhen si rreshta karburanti me **Lloji i udhëtimit** (jo mbushje normale). Ato shfaqen nën **Raportet → Milje udhëtimi**, jo nën Historia e karburantit.

---

## <a id="reports"></a>Raporton

Menuja → **Raportet** hap qendrën e produktit (përmbledhja e të gjitha kohërave + kartat e katalogut). Kjo është e vetmja sipërfaqe e raporteve të produktit - nuk ka asnjë artikull të veçantë sirtar "Raporte dhe grafiku".

![Qendra e raporteve](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Hapni një kartë për modalitetin e automjetit (**Të gjitha / Secili / Single**), filtrat e periodave, grafikët dhe shpërndajeni (**TEXT / CSV / PDF**). Shiriti i sipërm për fëmijët e raportuar: **☰ + ←** (dhe **ⓘ** kur regjistrohen).

### <a id="time-based-reports"></a>Raporte të bazuara në kohë

Karta kryesore e grafikut. Metrikë opsionale (mpg, vëllimi/distanca si G/mi, çmimi për njësi si p.sh. $/G, kosto/distanca, $ mujore, milje udhëtimi, % e udhëtimit sipas llojit) me **kosha të qetë** dhe **peshore Y të pavarur** (ekonomia majtas; paratë dhe familjet e udhëtimit në të djathtë).

![Raportet e bazuara në kohë](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Raporte të bazuara në kohë — metrikë të lëvizur](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Detajet e matematikës ekonomike: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Historiku i mbushjes kundrejt historisë së karburantit

- **Raporte → Plotëso histori** — mbushje kronologjike për filtrat e raportit (**mbushet vetëm**; asnjë udhëtim nuk fillon).

![Plotëso raportin e historisë](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Historiku i karburantit** (nëse është i pranishëm në navigimin e ndërtesës suaj) — inventari i mbushjes për çdo automjet, gjithashtu mbush vetëm; trokitni lehtë mbi një rresht për ta modifikuar.

### <a id="trip-miles"></a>Udhëtim milje

**Raporte → Milje udhëtimi** — milje sipas llojit, grafikëve dhe një **liste kronologjike e fillimit të udhëtimit/segmenteve**. Prekni një fillim të vërtetë për të hapur **Modifiko mbushjen** për atë rresht.

![Trip milje](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Redakto mbushjen

Nga historiku i mbushjes, Historia e karburantit ose miljet e udhëtimit, hapni një mbushje. Paraqitja: automjeti dhe odometri, **monedha para kostos**, vëllimi, shënimet. Lloji i udhëtimit shfaqet vetëm kur rreshti është fillimi i udhëtimit. Vendndodhja ka një përmbledhje plus **Detajet e vendndodhjes**. Mungon fotografia lokale me identitetin e resë kompjuterike: **Merr imazhin nga arkivi**.

![Redakto mbushjen — Merr nga arkivi kur mungon fotografia lokale](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Kartat e tjera të katalogut përfshijnë shpenzimet sipas kategorisë, përmbledhjen e automjeteve dhe listën e shpenzimeve.

Paraja përdor monedhën e çdo rreshti kur vendoset. Totalet e monedhës së përzier tregojnë **nëntotalet për monedhë** (pa konvertim të heshtur FX).

---

## <a id="syncing"></a>Sinkronizimi

Menuja → **Sinkronizimi** është qendra për destinacionet e fletëllogaritjes dhe fotografive (jo vetëm e varrosur te Cilësimet).

![Sinkronizimi i qendrës](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Kartat për **sinkronizimin e fletëllogaritjes** dhe **Rezervimin e fotografive** me status të shkurtër, **Sinkronizimi** për atë lloj dhe **›** në listën e destinacionit.
- Hapni një destinacion për **Testoni lidhjen** dhe **Sinkronizoni tani (këtë destinacion)** / të gjitha të konfiguruara.
- Dështimi **Detajet** dhe e kuqja **!** në shiritin e titullit bie këtu.
- Konfigurimi hap pas hapi i "Fletëve të Google" dhe "Diskut": [Rezervimet dhe sinkronizimi me shumë pajisje](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Cilësimet (preferencat lokale)

Menuja → **Cilësimet**.

![Cilësimet](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Më shumë cilësime (të lëvizura)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Për destinacionet, preferoni **Meny → Sinkronizimi**. Cilësimet mund të shfaqin ende rreshta përmbledhës që hapin të njëjtat lista.

### <a id="local-preferences-common"></a>Preferencat lokale (të zakonshme)

- **Ruaj fotografitë e faturës së karburantit** / **Ruaj fotografitë e shpenzimeve në vend** — mbani imazhet në pajisje (mund të kërkojë leje për fotografitë).
- **Luaj tingullin e diafragmës**
- **Valuta** / **Njësia e volumit** — parazgjedhjet e aplikacionit (sistemi ose i qartë). Ndryshimi i njësisë së volumit me të dhënat ekzistuese të karburantit mund të ofrojë një dialog konvertimi.
- **Modaliteti i errët **
- **Këshilla për konfigurimin** — rihapni udhëzimet e makinës së parë / sinkronizimit.
- **Plotësimi i korrigjimit** / **Shfaq ekranet e eksperimentit (dev)** — i avancuar; lini për përdorim të përditshëm. Ekranet e eksperimentit nuk janë të dokumentuara këtu.

CSV **eksporti/importi** (ZIP i automjeteve / Shpenzimeve / skedave të karburantit) ofrohet nga Cilësimet kur ofrohet nga ndërtimi aktual.

---

## <a id="help--about"></a>Ndihmë & Rreth

![Ndihmë](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Rreth](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Ndihmë** — nisje e shpejtë në pajisje, udhëzime konfigurimi, lidhje me këtë manual, indeksi i konfigurimit të vetë hostit.
- **Rreth** — versioni, licencat, GitHub, ky manual (i paketuar jashtë linje + HTML në linjë kur publikohet).

---

## <a id="related-docs"></a>Dokumente të ngjashme

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — udhëzues i shkurtër i përditshëm
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — foto e vetë-strehuar/konfigurim tabelor
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — bashkim, rikuperim, dublikatë
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — detaje të matjeve ekonomike
