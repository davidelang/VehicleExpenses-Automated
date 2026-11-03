# <a id="vehicle-expenses-automated--user-manual"></a>Automatizēti transportlīdzekļu izdevumi — lietotāja rokasgrāmata

> **Rediģēt avotu (Markdown).** Pārlūkprogrammas un lietotnē ietvertais lasītājs atver **renderēto HTML**:
> - Tīmeklis: [`docs/user-manual/index.html`](index.html) (atjaunot ar `./scripts/render-user-manual.sh`)
> - Lietotne: Palīdzība / Par → pilna rokasgrāmata (komplektā HTML + ekrānuzņēmumi)
>
> Nenorādiet galalietotājiem uz neapstrādātiem `.md` URL — pārlūkprogrammas rāda tikai vienkāršu tekstu.

Kameras pirmā izsekošana degvielas uzpildīšanai un transportlīdzekļa izdevumiem ar izvēles vairāku ierīču sinhronizāciju un dublēšanu **jūsu** mākoņa kontos.

Šī ir **pilna rokasgrāmata** (ekrānuzņēmumi + katrs solis). Tālrunī **Izvēlne → Palīdzība** ir īsāks darba sākšanas ceļvedis.

**Šeit nav ietverts:** veco attēlu importēšana, izlīdzināšanas eksperiments un sūkņa eksperiments (izstrādātājs/uzlabotie rīki).

---

## <a id="table-of-contents"></a>Satura rādītājs

1. [Kas jums nepieciešams] (#what-you-need)
2. [Ikonas īsumā] (#icons-at-a-glance)
3. [Atveriet izvēlni] (#open-the-menu)
4. [Pirmā iestatīšana: Pārvaldīt transportlīdzekļus](#first-time-setup-manage-vehicles)
5. [Dublēšana un vairāku ierīču sinhronizācija](#backups-and-multi-device-sync)
6. [Uzpilde (degviela)](#fill-up-fuel)
7. [Sākt ceļojumu](#start-trip)
8. [Izdevumi] (#expenses)
9. [Pārskati](#reports)
10. [Iestatījumi (vietējās preferences)] (#settings-local-preferences)
11. [Sinhronizācija](#syncing)
12. [Palīdzība un informācija](#help--about)
13. [Saistītie dokumenti](#related-docs)

---

## <a id="what-you-need"></a>Kas jums nepieciešams

— Android tālrunis vai planšetdators.
— lai iegūtu vislabāko OCR: skaidru skatu uz **paneļa odometru** un **sūkņa kopējos rādītājus** (vai ierakstiet skaitļus ar roku).
— neobligāti: konti, kurus kontrolējat** izklājlapu datiem un/vai fotoattēlu dublēšanai (skatiet [Dublēšana un vairāku ierīču sinhronizācija](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Ikonas īsumā

Tie parādās galvenajos ekrānos. Zinot tos, tiek ietaupīts daudz medību.

| Kur | Ikona / vadīkla | Ko tas dara |
|-------|-----------------|--------------|
| Augšējā josla | **☰ Ēdienkarte** (hamburgers) | Atver navigācijas atvilktni |
| Augšējā josla | **ⓘ** (lapas palīdzība) | Īsa palīdzība **pašreizējai** lapai (blakus izvēlnei, ja pieejama) |
| Augšējā josla | **`?N`** (dzeltens) | Neapstiprinātie importēšanas pārskatīšanas jautājumi — tiek atvērts Importēšanas apskats |
| Augšējā josla | **!** (sarkans) | Nesen neizdevās izveidot izklājlapu vai fotoattēlu — atveriet sadaļu **Sinhronizācija**, lai labotu |
| Augšējā josla | **☰ + ←** | Ziņojums par bērniem un Izdevumu saraksts parāda **izvēlni un atpakaļ** kopā; Atskaišu centrs ir tikai izvēlne |
| Iestatījumi / degvielas rediģēšana | **←** | Atpakaļ (iestatījumu izklājlapa/foto un degvielas rediģēšana ir vērsta uz aizmuguri) |
| Aizpildiet | **Baltais aplis** (aizvars) | Uzņemiet odometra vai sūkņa displeju OCR |
| Aizpildiet | **Disks / Saglabāt** | Saglabājiet uzpildīšanu (nepieciešams transportlīdzeklis un vismaz viens no odo / tilpuma / izmaksām) |
| Aizpildiet | **↕ bultiņas** (režīmu slēdzis) | Pārslēgt **odometra režīmu** pret **sūkņa (izmaksas/apjoms) režīmu**. Zaļā apmale izceļ aktīvo lauku grupu |
| Aizpildiet | **↔ bultiņas** (starp izmaksām un apjomu) | Apmaiņas izmaksas un apjoms, ja OCR ievieto tos nepareizajos laukos |
| Aizpildiet | **Tālummaiņa 1x / …** | Kameras tālummaiņas koeficienti, ja objektīvs tos atbalsta |
| Aizpildīt (pēc uztveršanas) | **Atsvaidzināt** uz galvenās pogas | Atmest priekšskatījumu un atgriezties tiešraides kamerā |
| Pildīt (apstrādes laikā) | **X** uz galvenās pogas | Atcelt notiekošo uzņemšanu/OCR |
| Izdevumi | **Saglabāt** | Ietaupiet izdevumus |
| Izdevumi | **Slēģu aplis** | Uzņemiet kvīts fotoattēlu |
| Izdevumi | **Galerija** | Izvēlieties kvīts attēlu no bibliotēkas |
| Izdevumi | **Atkārtoti uzņemt** | Notīriet pašreizējās kvīts fotoattēlu un uzņemiet vēlreiz |
| Izdevumi / Pārvaldīt transportlīdzekļus | **+ / −** FAB | Tālummainiet fotoattēla priekšskatījumu |
| Orientieru dialoglodziņš | **Rediģēt OCR** ​​| Labojiet vai pievienojiet orientiera tekstu, ko dzinēji palaida garām |
| Izklājlapa / Foto veidlapas | **🔍 Meklēt** | Pārlūkojiet Google disku, lai atrastu lapu vai mapi (pēc pierakstīšanās) |

Valūtas simbolus izmaksu laukos un **G/L** apjoma laukos var pieskarties: atveriet nelielu izvēlni, lai mainītu šī ieraksta valūtu vai galonus pret litriem.

---

## <a id="open-the-menu"></a>Atveriet izvēlni

1. Augšējā kreisajā stūrī pieskarieties **☰**.
2. Izvēlieties lapu.

![Navigācijas atvilktne](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Galvenā atvilktne:** Aizpildiet · Sāciet ceļojumu · Pārvaldiet transportlīdzekļus · Jauni izdevumi · **Pārskati** · Iestatījumi · Sinhronizācija · Palīdzība · Par.

**Eksperimentu atvilktne** (Iestatījumi → Rādīt eksperimenta ekrānus): Izlīdzināšanas eksperiments · Sūknēšanas eksperiments · **Importēt vecos attēlus**.

**Izmantojot pārskatu centru (nevis galveno atvilktni):** Izdevumu saraksts · Aizpildiet vēsturi.

---

## <a id="first-time-setup-manage-vehicles"></a>Pirmā iestatīšana: Pārvaldiet transportlīdzekļus

OCR un **automātiskā transportlīdzekļa saskaņošana** vislabāk darbojas pēc tam, kad esat reģistrējis katru transportlīdzekli ar **atsauces informācijas paneļa fotoattēlu**, palaižat **Discovery**, iestatāt odometra ražu no lāpstiņas sēklas un saglabājat šīs domuzīmes orientierus.

### <a id="open-manage-vehicles"></a>Atveriet Pārvaldīt transportlīdzekļus

izvēlne → **Pārvaldīt transportlīdzekļus**. Izvēlieties transportlīdzekli (vai **Pievienot jaunu transportlīdzekli**).

![Pārvaldiet transportlīdzekļus, izmantojot atsauces domuzīmi, kultūraugus un atklājumus](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Pievienojiet vai rediģējiet transportlīdzekli

1. Atveriet nolaižamo izvēlni **Transportlīdzeklis** → izvēlieties transportlīdzekli vai **Pievienot jaunu transportlīdzekli**.
2. Uzņemiet vai izvēlieties skaidru **atsauces domuzīmes fotoattēlu** (pilns instrumentu bloks, labi apgaismots, tālrunis aptuveni kvadrātveida formā). Izmantojiet **Uzņemt fotoattēlu** vai **Galeriju**.
3. Pieskarieties vienumam **Palaist atklāšanu** — lāpstiņas noteikšana fotoattēlā ievieto dzeltenas **sēklas** (orientieros tiek izmantots tas pats `"ML Kit"` JSON kā iepriekš).
4. Pieskarieties **Atlasīt Odo**, pieskarieties cipariem, pielāgojiet rokturus un pēc tam **Saglabāt**.
5. **Edit Odo** pielāgo jau esošo lodziņu. **Clear Odo** tas tiek noņemts, un pēc tam poga parāda **Atlasīt Odo**.
6. Pārskatiet, izmantojot **Rādīt orientierus**. Izmantojiet **Rediģēt OCR**, lai labotu kļūdas vai **pievienotu** tekstu, kas tika palaists garām.
7. Aizpildiet **Transportlīdzekļa nosaukums** (obligāti), kā arī marku/modeli/gadu/plāksni, kā vēlaties.
8. Pieskarieties **Izveidot transportlīdzekli** vai **Saglabāt izmaiņas** (jaunam transportlīdzeklim ir nepieciešams vārds un atsauces fotoattēls).

![Pievienot jaunus transportlīdzekļa veidlapas laukus](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a> Orientieri: labojiet to, ko Discovery palaida garām

Pēc **Rādīt orientierus** ritiniet sarakstu un labojiet vērtības. Dzinēji dažreiz izlaiž mazus ciparus (piemēram, pulkstenis **60** kopas apakšējā labajā stūrī). Izmantojiet **Rediģēt OCR**, lai tos pievienotu vai labotu, lai transportlīdzekļa identitāte būtu uzticama.

![Orientieru dialoglodziņš — pārskatiet un rediģējiet OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Rakstīšana bez perfekta fotoattēla

Joprojām varat izmantot lietotni, atlasot transportlīdzekli un **ievadot** odometru, apjomu un izmaksas sadaļā Aizpildīšana — OCR nav obligāta katram laukam. Galerijas importēšana darbojas atsauces domuzīmes fotoattēlam, ja nevēlaties fotografēt lietotnē.

**Padoms.** Pēc izklājlapas sinhronizēšanas transportlīdzekļu definīcijas (augi, orientieri) atrodas vietējā datu bāzē — lai tās izmantotu, nav atkārtoti jāatver aizpildīšanai paredzēto transportlīdzekļu pārvaldība.

---

## <a id="backups-and-multi-device-sync"></a>Dublēšana un vairāku ierīču sinhronizācija

Lietojumprogramma ir izstrādāta tā, lai **vairāki tālruņi vai planšetdatori varētu koplietot vienus un tos pašus flotes datus**, lai jūs varētu saglabāt **savu datu un fotoattēlu kopijas ārpus ierīces**. Tas tiek darīts ar galamērķiem, kurus **jūs** konfigurējat **jūsu** kontos vai **jūsu** pašmitinātos serveros — nevis uzņēmuma pārvaldītu “Transportlīdzekļu izdevumu mākoni”, ko var redzēt citi cilvēki.

### <a id="what-runs-where"></a>Kas kur darbojas

| Veids | Ko tas uzglabā | Tipisks lietojums |
|------|----------------|-------------|
| **Izklājlapu/tabulu sinhronizācija** | Transportlīdzekļi, degvielas uzpildes, izdevumi (rindas un cilnes) | Vairāku ierīču sapludināšana + strukturēta dublēšana |
| **Fotoattēlu dublēšana** | Binārie attēli (domuzīme/sūknis/kvīts/atsauces fotoattēli) | Fotoattēlu dublēšana + trūkstošo failu atjaunošana |

Varat konfigurēt **vairākus katra veida galamērķus** (mīkstais vāciņš katram veidam). Manuāli **Sinhronizēt tūlīt** un **fona** darbinieki palaiž iespējotos.

### <a id="offline-first"></a>Vispirms bezsaistē

— **Tīkls nav nepieciešams**, lai pievienotu uzpildi, izdevumus vai kvīti. Viss tiek saglabāts **vispirms lokāli**.
— kad tīkls ir pieejams, sinhronizācija un fotoattēlu dublēšana tiek izpildīta kā **fona uzdevumi** (pēc jūsu iestatītā grafika un pieskaroties **Sinhronizēt tūlīt**). Kļūmes tiek rādītas kā sarkans teksts zem iestatījumu rindām un **!** lietotnes virsrakstjoslā.

### <a id="your-accounts-only"></a>Tikai jūsu konti

Pierakstīšanās un marķieri paliek ierīcē jūsu izvēlētajiem pakalpojumu sniedzējiem (Google, Microsoft, S3 atslēgas, pašmitinātie URL utt.). Galamērķus **pilnībā kontrolē lietotājs** — jūsu Google konts, jūsu OneDrive, jūsu MinIO segments, jūsu EtherCalc resursdators utt. Nekas netiek koplietots ar citiem transportlīdzekļa izdevumu lietotājiem, izmantojot koplietotu aizmugursistēmu.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Atbalstītie mērķi — dati (izklājlapa/tabula)

Konfigurēts sadaļā **Izvēlne → Sinhronizācija → Izklājlapu sinhronizācija** (sasniedzama arī iestatījumu kopsavilkuma rindās). Pirmās klases atlasītāja iespējas:

| Mērķis | Piezīmes |
|--------|--------|
| **Google izklājlapas** | Kopējā noklusējuma; cilnes transportlīdzekļiem, izdevumiem un katrai automašīnai paredzētajai degvielai |
| **Excel** | Microsoft darbgrāmata, izmantojot Graph/OneDrive stila iesiešanu |
| **EtherCalc** | Pašmitinātas sadarbības izklājlapu telpas |
| **Citas →** ieviestās aizmugursistēmas | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Atlikts / vēl nav bez galvas (norādīts sadaļā Cits, bet nav pilnībā ieviests): OnlyOffice, Collabora. Skatiet arī [self-host index](reference/self-host/INDEX.md).

CSV **eksportēšana/importēšana** (tā paša cilnes izkārtojuma ZIP fails) ir pieejams sadaļā Iestatījumi kā pārnēsājams dublējums neatkarīgi no tiešās sinhronizācijas.

### <a id="supported-targets--photos-image-backup"></a>Atbalstītie mērķi — fotoattēli (attēla dublējums)

Konfigurēts sadaļā **Izvēlne → Sinhronizācija → Fotoattēlu dublēšana** (arī no iestatījumu kopsavilkuma rindām):

| Mērķis | Piezīmes |
|--------|--------|
| **Google disks** | Jūsu izvēlētā mape (pārlūkojiet vai ielīmējiet URL) |
| **OneDrive** | Microsoft konts + ceļa prefikss |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO un citi ar S3 saderīgi galapunkti |
| **Cits** | rclone nodrošināta krātuve (piemēram, WebDAV, SFTP un citas atlasītas tālvadības pultis, kas pieejamas lietotņu atlasītājā) |

Iestatiet cheatsheets pašmitinātiem fotoattēlu un tabulas mērķiem: [pašuzņēmēja rādītājs](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Daudzu ierīču darbība (īsa)

— rindas tiek apvienotas, izmantojot **sinhronizācijas ID** ar **pēdējās rakstīšanas uzvaras** uz **Atjauninātajiem** laikspiedoliem.
— svītrojumi ir mīksti; jaunāks labojums citā ierīcē var atjaunot rindu.
— ievadot **vienu un to pašu aizpildījumu divreiz** divās ierīcēs, tiek izveidotas **divas rindas** — izdzēsiet papildu, kad to pamanāt.
— sīkāka informācija: [Sinhronizācijas piezīmes] (#sync-behavior-notes) un [SYNC_BEHAVIOR.md] (reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Piemērs: pievienojiet Google izklājlapas (dati)

1. **Izvēlne → Sinhronizācija → Izklājlapu sinhronizācija** (vai Iestatījumi → Izklājlapu sinhronizācija).

![Izklājlapu sinhronizācijas saraksts](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Pieskarieties **Pievienot izklājlapas galamērķi**.

![Pakalpojumu sniedzēja atlasītājs](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Izvēlieties **Google izklājlapas**.

![Google izklājlapu veidlapa](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Pierakstieties ar Google** vienreiz — šī pierakstīšanās ļauj arī pārlūkot. Parādāmais nosaukums → ielīmējiet **lapas URL** (joprojām derīgs) vai **🔍** pārlūkojiet. Pārlūkošana tiek atvērta sadaļā **Viss un kopīgots**, lai jūs varētu izvēlēties esošu izklājlapu, kas jums pieder vai tiek kopīgota ar jums. Pēc tam plānojiet opcijas → iespējot → saglabāt.
5. **Sinhronizējiet tūlīt** vienreiz, lai izveidotu/atjauninātu cilnes: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Piemērs: pievienojiet Google disku (fotoattēli)

1. **Izvēlne → Sinhronizācija → Fotoattēlu dublēšana** (vai Iestatījumi → Fotoattēlu dublēšana).

![Fotoattēlu dublējumkopiju saraksts](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Pieskarieties **Pievienot fotoattēla galamērķi**.

![Fotoattēlu nodrošinātāja atlasītājs](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Izvēlieties **Google disks**.

![Google diska veidlapa](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Pierakstieties, izmantojot Google (disku)** → izvēles mapes URL/pārlūkot → iespējot → saglabāt → **Sinhronizēt tūlīt**.

Manuāla **Sinhronizācija tūlīt** fotoattēliem ir pilna caurlaide; fona dublēšana parasti apstrādā **tikai gaidošās** augšupielādes saskaņā ar grafiku.

### <a id="sync-behavior-notes"></a> Sinhronizācijas darbības piezīmes

— pēc lietotnes jaunināšanas īslaicīgi var tikt parādīts ziņojums **“Datu bāzes atjaunināšana pēc jaunināšanas…”** (vietējā sinhronizācijas ID aizpildīšana).
— ja sinhronizācija tiek pārtraukta, nākamā **veiksmīgā** sinhronizācija tiek atkārtoti sapludināta un labotas attālās cilnes.
— kļūmes: sarkans kopsavilkums uz kartīšu sinhronizēšanas + **!** lietotņu joslā.

---

## <a id="fill-up-fuel"></a> Uzpildiet (degviela)

Šis ir **sākuma ekrāns**, kad atverat lietotni.

### <a id="vehicle-selection-usually-automatic"></a>Transportlīdzekļa izvēle (parasti automātiski)

Jums **nav** vispirms jāizvēlas transportlīdzeklis. Ja transportlīdzekļiem ir iestatīti **orientieri** sadaļā Transportlīdzekļu pārvaldība, aizpildīšana **automātiski nosaka, kurš transportlīdzeklis** no paneļa attēla pēc odometra uzņemšanas. Joprojām varat atvērt nolaižamo izvēlni **Transportlīdzeklis**, lai vajadzības gadījumā to ignorētu.

### <a id="aim-at-the-odometer"></a> Mērķējiet uz odometru

Palieciet odometra režīmā un ierāmējiet kopu. Instrukcija: * Mērķējiet uz odometru. Lai uzņemtu, pieskarieties aizvaram.*

![Aizpildīt — tiešraides kamera odometrā (piemērs domuzīmes attēla priekšskatījumā)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Pēc odometra aizvara

OCR aizpilda **Odo** un mēģina saskaņot transportlīdzekli no orientieriem (ja nepieciešams, pārskatiet abus). Galvenā poga kļūst par **Atkārtoti**, lai fotografētu atkārtoti. Instrukcija apkopo lasījumu.

![Aizpildīt — odometra OCR rezultāts](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Sūkņa režīms (izmaksas un apjoms)

1. Pieskarieties **↕**, lai pārslēgtos uz sūkņa režīmu: *Mērķējiet uz sūkņa displeju (izmaksas/tilpums). Pieskarieties aizvaram.*
2. Uzņemiet sūkņa kopējos rādītājus. Izmaksu un apjoma lauki aizpilda; izmantojiet **↔**, ja tie ir apmainīti.
3. Pieskarieties valūtai vai **G/L**, ja nepieciešams, pēc tam **Saglabāt** (diskā). Tukšie lauki veido **daļēju aizpildījumu** (joprojām ir atļauts).

![Aizpildīt — sūkņa priekšskatījums / kopsummas (sūkņa attēla piemērs priekšskatījumā)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Jūs paliekat uz Aizpildīt līdz nākamajai pieturai (lauki notīrīti pēc saglabāšanas). Strādājiet pilnībā **bezsaistē**; sinhronizācija darbojas vēlāk fonā, kad tā ir konfigurēta.

### <a id="manual-entry-no-camera--bad-ocr"></a>Manuāla ievade (nav kameras / slikta OCR)

1. Pieskarieties vienumam **Odo**, **maksa** vai **volume** un ierakstiet vērtības (portrets izmanto sistēmas tastatūru; ainava izmanto ekrāna tastatūru).
2. Izvēlieties vai apstipriniet **Vehicle**, ja automātiskā noteikšana nedarbojās.
3. Saglabājiet kā norādīts iepriekš.

### <a id="modes-and-borders"></a>Režīmi un apmales

— **Zaļa apmale** ap transportlīdzekli+odo → odometra tveršana/rediģēšana.
— **Zaļā apmale** ap izmaksu+apjoms → sūkņa režīms.
— **Saglabāt** paliek atspējota, līdz tiek atlasīts transportlīdzeklis un vismaz vienā no odo/cost/volume ir dati, un OCR joprojām nedarbojas.

Padoms ekrānā (zem instrukciju rindas): *Aizvars = tveršana · Disks = saglabāšana · ↕ = odo/pumpa režīms · ↔ = mijmaiņas izmaksas/apjoms.*

---

## <a id="expenses"></a>Izdevumi

### <a id="new-expense"></a>Jauni izdevumi

Izvēlne → **Jauni izdevumi**.

![Jauna izdevumu veidlapa un kameras vadīklas (piemērs domuzīmes attēls kameras joslā, kad tas ir tukšs)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Saglabāt** (disks), **aizvars** (kvīts fotoattēls) vai **galerija** (izvēlēties attēlu).
2. Aizpildiet **Datums**, **Transportlīdzeklis**, **Pārdevējs**, **Apraksts**, **Summa** (valūtas simbols, pieskaroties), **Kategorija**, pēc izvēles **Odometrs**.
3. Vairāku lappušu kvītis: tveriet papildu lapas, ja lietotāja saskarne piedāvā peidžeru (0. lapa ir galvenā kvīts).
4. **Saglabāt**, lai saglabātu (vispirms lokāli; fotoattēlu dublēšana un izklājlapu sinhronizācija notiek fonā, kad tā ir konfigurēta).

### <a id="expense-list"></a>Izdevumu saraksts

Izvēlne → **Pārskati** → **Izdevumu saraksts** — pārlūkojiet iepriekšējos izdevumus, kas nav saistīti ar degvielu; atveriet rediģējamo vienumu.

![Izdevumu saraksts](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Rediģēt izdevumus

Atveriet rindu no saraksta. Pareizs pārdevējs, summa, kategorija, transportlīdzeklis un apraksts. Ja kvīts ir tikai fotoattēla dublējumkopijā (nav lasāma lokālā faila), izmantojiet opciju **Ienesiet attēlu no arhīva**, kad tas tiek parādīts (darbojas visos konfigurētos fotoattēlu galamērķos).

![Rediģēt izdevumus](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Sāciet ceļojumu

Izvēlne → **Sākt ceļojumu** (pēc Aizpildīt atvilktni). Uzņemiet vai ievadiet odometru, izvēlieties brauciena veidu, saglabājiet ar **diska** ikonu. **Apturēt** ir saīsne Personal now aizturētajā GPS vietā. Izmantojiet **ⓘ** kontroles atgādinājumiem.

![Sākt ceļojumu](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Brauciena starti tiek saglabāti kā degvielas rindas ar **Trip Type** (nevis parastās uzpildes). Tie tiek parādīti sadaļā **Pārskati → Brauciena jūdzes**, nevis sadaļā Degvielas vēsture.

---

## <a id="reports"></a>Pārskati

Izvēlne → **Pārskati** atver produktu centru (visu laiku kopsavilkums + kataloga kartītes). Šī ir vienīgā produkta pārskatu virsma — nav atsevišķa atvilktnes vienuma “Pārskati un diagrammas”.

![Pārskatu centrs](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Atveriet karti transportlīdzekļa režīmam (**Visi / Katrs / Viens**), periodu filtriem, diagrammām un kopīgošanai (**TEKSTS / CSV / PDF**). Augšējā josla ziņojuma bērniem: **☰ + ←** (un **ⓘ**, kad reģistrēts).

### <a id="time-based-reports"></a>Laika pārskati

Galvenā diagrammas karte. Izvēles metrika (mpg, apjoms/attālums, piemēram, G/mi, vienības cena, piemēram, USD/G, maksa/attālums, mēnesī USD, brauciena jūdzes, brauciena % pēc veida) ar **Smooth** tvertnēm un **neatkarīgām Y svariem** (ekonomisks pa kreisi; nauda un ceļojumu ģimenes labajā pusē).

![Laika pārskati](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Laika pārskati — ritināta metrika](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Ekonomiskās matemātikas informācija: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Aizpildīšanas vēsture pret degvielas vēsturi

- **Pārskati → Aizpildīšanas vēsture** — pārskatu filtru hronoloģiski aizpildījumi (**tikai aizpildījumi**; ceļojums nesākas).

![Aizpildīt vēstures pārskatu](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

— **Degvielas vēsture** (ja tāda ir jūsu būvējuma navigācijā) — katra transportlīdzekļa aizpildīšanas inventārs, arī aizpilda tikai; pieskarieties rindai, lai rediģētu.

### <a id="trip-miles"></a> Brauciena jūdzes

**Pārskati → Brauciena jūdzes** — jūdzes pēc veida, diagrammas un hronoloģisks **brauciena sākuma/segmentu saraksts**. Pieskarieties īstajam sākumam, lai šai rindai atvērtu **Rediģēt aizpildījumu**.

![Ceļojuma jūdzes](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Rediģēt aizpildījumu

Sadaļās Uzpildes vēsture, Degvielas vēsture vai Brauciena jūdzes atveriet aizpildījumu. Izkārtojums: transportlīdzeklis un odometrs, **valūta pirms pašizmaksas**, apjoms, piezīmes. Ceļojuma veids tiek rādīts tikai tad, ja rinda ir brauciena sākums. Atrašanās vietai ir kopsavilkums un **informācija par atrašanās vietu**. Trūkst vietējā fotoattēla ar mākoņa identitāti: **Ienesiet attēlu no arhīva**.

![Rediģēt aizpildījumu — izgūt no arhīva, ja trūkst vietējā fotoattēla](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Citās katalogu kartēs ir iekļauti izdevumi pa kategorijām, transportlīdzekļa kopsavilkums un izdevumu saraksts.

Money izmanto katras rindas valūtu, kad tā ir iestatīta. Jaukto valūtu kopsummas parāda **par valūtu starpsummas** (bez klusās valūtas konvertēšanas).

---

## <a id="syncing"></a>Sinhronizācija

izvēlne → **Sinhronizācija** ir izklājlapu un fotoattēlu galamērķu centrs (ne tikai apglabāts sadaļā Iestatījumi).

![Sinhronizācijas centrs](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

— kartītes **Izklājlapu sinhronizācijai** un **Fotoattēlu dublēšanai** ar īsu statusu, **Sinhronizācijai** šim veidam un **›** galamērķu sarakstā.
— atveriet galamērķi **Pārbaudīt savienojumu** un **Sinhronizēt tūlīt (šis galamērķis)** / viss ir konfigurēts.
— kļūme **Detaļas** un sarkanais **!** virsraksta joslā nonāk šeit.
— Google izklājlapu un diska soli pa solim iestatīšana: [Dublēšana un vairāku ierīču sinhronizācija] (#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Iestatījumi (vietējās preferences)

Izvēlne → **Iestatījumi**.

![Iestatījumi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Citi iestatījumi (ritināti)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Galamērķiem dodiet priekšroku **Izvēlne → Sinhronizācija**. Iestatījumos joprojām var tikt rādītas kopsavilkuma rindas, kas atver tos pašus sarakstus.

### <a id="local-preferences-common"></a>Vietējās preferences (parasti)

— **Saglabāt degvielas čeku fotoattēlus** / **Saglabāt izdevumu fotoattēlus lokāli** — saglabājiet attēlus ierīcē (var pieprasīt fotoattēlu atļauju).
— **Atskaņot aizvara skaņu**
— **Valūta** / **Skaļuma vienība** — lietotņu noklusējuma iestatījumi (sistēmiski vai skaidri). Mainot tilpuma vienību ar esošajiem degvielas datiem, var tikt parādīts konvertēšanas dialoglodziņš.
— **Tumšais režīms**
— **Iestatīšanas padomi** — atkārtoti atveriet pirmās palaišanas transportlīdzekļa/sinhronizācijas apmācības.
— **Atkļūdošanas aizpildīšana** / **Rādīt eksperimentu ekrānus (izstrādātājs)** — papildu; atstājiet ikdienas lietošanai. Eksperimentu ekrāni šeit nav dokumentēti.

CSV **eksports/imports** (transportlīdzekļu/izdevumu/degvielas pasta indekss) ir pieejams sadaļā Iestatījumi, ja to piedāvā pašreizējā versija.

---

## <a id="help--about"></a>Palīdzība un informācija

![Palīdzība](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Par](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

— **Palīdzība** — ierīces ātrā palaišana, iestatīšanas pamācības, saite uz šo rokasgrāmatu, pašrežīma iestatīšanas rādītājs.
— **Par** — versija, licences, GitHub, šī rokasgrāmata (komplektā bezsaistē + tiešsaistes HTML, kad tā ir publicēta).

---

## <a id="related-docs"></a>Saistītie dokumenti

— [QUICK_GUIDE.md](../QUICK_GUIDE.md) — īss ikdienas ceļvedis
— [self-host/INDEX.md](../reference/self-host/INDEX.md) — pašmitināta fotoattēlu/tabulu iestatīšana
— [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — sapludināšana, atkopšana, dublikāti
— [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — detalizēta informācija par ekonomikas metriku
