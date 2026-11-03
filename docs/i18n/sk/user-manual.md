# <a id="vehicle-expenses-automated--user-manual"></a>Automatizované náklady na vozidlo — Používateľská príručka

> **Upraviť zdroj (Markdown).** Prehliadače a čítačka v aplikácii otvoria **vykreslený kód HTML**:
> - Web: [`docs/user-manual/index.html`](index.html) (obnoviť pomocou `./scripts/render-user-manual.sh`)
> - Aplikácia: Pomocník / Informácie → celý manuál (pribalený HTML + snímky obrazovky)
>
> Neukazujte koncovým používateľom na nespracované adresy URL `.md` – prehliadače zobrazujú iba obyčajný text.

Sledovanie tankovania paliva a výdavkov vozidla prvou kamerou s voliteľnou synchronizáciou viacerých zariadení a zálohovaním pod **vašimi** cloudovými účtami.

Toto je **úplná príručka** (snímky obrazovky + každý krok). V telefóne je **Menu → Pomocník** kratšia príručka pre začiatočníkov.

**Tu nie je zahrnuté:** Import starých obrázkov, Experiment so zarovnaním a Experiment s pumpou (nástroje pre vývojárov / pokročilé).

---

## <a id="table-of-contents"></a>Obsah

1. [Čo potrebujete](#what-you-need)
2. [Ikony na prvý pohľad](#icons-at-a-glance)
3. [Otvoriť menu](#open-the-menu)
4. [Prvé nastavenie: Správa vozidiel](#first-time-setup-manage-vehicles)
5. [Zálohy a synchronizácia viacerých zariadení](#backups-and-multi-device-sync)
6. [Naplniť (palivo)](#fill-up-fuel)
7. [Začiatok cesty](#start-trip)
8. [Výdavky](#expenses)
9. [Správy](#reports)
10. [Nastavenia (miestne preferencie)](#settings-local-preferences)
11. [Synchronizácia](#syncing)
12. [Pomoc a informácie](#help--about)
13. [Súvisiace dokumenty](#related-docs)

---

## <a id="what-you-need"></a>Čo potrebujete

- Android telefón alebo tablet.
– Pre najlepšie OCR: čistý pohľad na váš **počítadlo kilometrov** na prístrojovej doske a **súčty čerpadiel** (alebo zadajte čísla ručne).
- Voliteľné: Účty **ovládate** pre tabuľkové údaje a/alebo zálohovanie fotografií (pozri [Zálohy a synchronizácia viacerých zariadení](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Ikony na prvý pohľad

Tieto sa zobrazia na hlavnej obrazovke. Ich poznanie ušetrí veľa lovu.

| Kde | Ikona / ovládanie | Čo to robí |
|-------|----------------|--------------|
| Horná lišta | **☰ Menu** (hamburger) | Otvorí navigačnú zásuvku |
| Horná lišta | **ⓘ** (pomoc k stránke) | Krátka pomoc pre **aktuálnu** stránku (vedľa ponuky, ak je k dispozícii) |
| Horná lišta | **`?N`** (žltá) | Čakajúce otázky kontroly importu – otvorí Kontrola importu |
| Horná lišta | **!** (červená) | Cieľová tabuľka alebo fotografia nedávno zlyhala – otvorte **Synchronizácia** a opravte |
| Horná lišta | **☰ + ←** | Hlásiť deti a zoznam výdavkov zobrazuje **menu a späť** spolu; Centrum prehľadov je len ponuka |
| Nastavenia / úprava paliva | **←** | Späť (nastavenia tabuľky/fotky a úpravy paliva zostanú zaostrené späť) |
| Vyplniť | **Biely kruh** (uzávierka) | Zachytenie zobrazenia počítadla kilometrov alebo pumpy pre OCR |
| Vyplniť | **Disk / Uložiť** | Uložte si tankovanie (potrebuje vozidlo a aspoň jeden z pach/objem/cena) |
| Vyplniť | **↕ šípky** (prepínač režimu) | Prepnúť **režim počítadla kilometrov** vs **režim pumpy (cena/objem)**. Zelený okraj zvýrazňuje aktívnu skupinu polí |
| Vyplniť | **↔ šípky** (medzi cenou a objemom) | Vymeňte náklady a objem, ak ich OCR vložíte do nesprávnych polí |
| Vyplniť | **Priblíženie 1x / …** | Pomery priblíženia fotoaparátu, keď ich objektív podporuje |
| Vyplňte (po zachytení) | **Obnoviť** na hlavnom tlačidle | Zahodiť náhľad a vrátiť sa do živého fotoaparátu |
| Vyplňte (počas spracovania) | **X** na hlavnom tlačidle | Zrušiť prebiehajúce snímanie/OCR |
| Výdavok | **Uložiť** | Ušetrite náklady |
| Výdavok | **Kruh uzávierky** | Odfotiť účtenku |
| Výdavok | **Galéria** | Vyberte si obrázok účtenky z knižnice |
| Výdavok | **Opätovný záber** | Vymažte aktuálnu fotografiu účtenky a nasnímajte znova |
| Výdavky / Správa vozidiel | **+ / −** FAB | Zväčšiť náhľad fotografie |
| Dialógové okno orientačné body | **Upraviť OCR** ​​| Opravte alebo pridajte orientačný text, ktorý motory vynechali |
| Tabuľkový / Foto formuláre | **🔍 Hľadať** | Vyhľadajte na Disku Google hárok alebo priečinok (po prihlásení) |

Symboly meny v poliach ceny a **G/L** v poliach objemu sa dajú klepnúť: otvorte malú ponuku na zmenu meny alebo galónov vs litrov pre danú položku.

---

## <a id="open-the-menu"></a>Otvorte menu

1. Klepnite na **☰** vľavo hore.
2. Vyberte stránku.

![Navigačný vysúvací panel](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Hlavná zásuvka:** Naplniť · Štart cesty · Správa vozidiel · Nové výdavky · **Správy** · Nastavenia · Synchronizácia · Pomocník · Informácie.

**Zásuvka experimentu** (Nastavenia → Zobraziť obrazovky experimentu): Experiment so zarovnaním · Experiment s pumpou · **Import starých obrázkov**.

**Cez centrum správ (nie hlavná zásuvka):** Zoznam výdavkov · História vypĺňania.

---

## <a id="first-time-setup-manage-vehicles"></a>Prvé nastavenie: Správa vozidiel

OCR a **automatické priraďovanie vozidiel** funguje najlepšie, keď zaregistrujete každé vozidlo pomocou **referenčnej fotografie z palubnej dosky**, spustíte **Discovery**, nastavíte orezanie počítadla kilometrov zo semena pádla a uložíte orientačné body pre danú pomlčku.

### <a id="open-manage-vehicles"></a>Otvoriť správu vozidiel

Menu → **Správa vozidiel**. Vyberte vozidlo (alebo **Pridať nové vozidlo**).

![Spravujte vozidlá pomocou referenčnej pomlčky, plodín a objavovania](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Pridajte alebo upravte vozidlo

1. Otvorte rozbaľovaciu ponuku **Vozidlo** → vyberte vozidlo alebo **Pridať nové vozidlo**.
2. Nasnímajte alebo vyberte jasnú **referenčnú fotografiu pomlčky** (úplný združený prístroj, dobre osvetlený, telefón je približne štvorcový). Použite **Odfotiť** alebo **Galériu**.
3. Klepnite na **Spustiť zisťovanie** – detekcia pádla umiestni na fotografiu žlté **semená** (orientačné body používajú rovnaký `"ML Kit"` JSON ako predtým).
4. Klepnite na **Vybrať Odo**, klepnite na semienko na číslice, upravte rukoväte a potom na **Uložiť**.
5. **Upraviť Odo** upraví pole, ktoré už existuje. **Clear Odo** ho odstráni a na tlačidle sa zobrazí nápis **Select Odo**.
6. Skontrolujte pomocou **Zobraziť orientačné body**. Pomocou **Upraviť OCR** ​​opravte nesprávne prečítané položky alebo **pridajte** text, ktorý ste vynechali.
7. Vyplňte **Názov vozidla** (povinné) plus značku/model/rok/štítok, ako chcete.
8. Klepnite na **Vytvoriť vozidlo** alebo **Uložiť zmeny** (vyžaduje meno + referenčnú fotografiu pre nové vozidlo).

![Pridať nové polia formulára vozidla](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a> Orientačné body: opravte to, čo Discovery zmeškal

Po **Zobraziť orientačné body** prejdite zoznamom a opravte hodnoty. Motorom niekedy chýbajú malé číslice (napríklad hodiny **60** vpravo dole na klastri). Použite **Upraviť OCR** ​​na ich pridanie alebo opravu, aby identita vozidla zostala spoľahlivá.

![Dialógové okno Orientačné body – kontrola a úprava OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Písanie bez dokonalej fotografie

Aplikáciu môžete naďalej používať výberom vozidla a **zadaním** počítadla kilometrov, objemu a ceny na Fill – OCR je voliteľné pre každé pole. Import galérie funguje pre referenčnú fotografiu, keď nechcete snímať v aplikácii.

**Tip:** Po synchronizácii tabuľky sú definície vozidiel (plodiny, orientačné body) uložené v lokálnej databáze – na ich použitie nemusíte znova otvárať Spravovať vozidlá na vyplnenie.

---

## <a id="backups-and-multi-device-sync"></a>Zálohy a synchronizácia viacerých zariadení

Aplikácia je vytvorená tak, aby **niekoľko telefónov alebo tabletov mohlo zdieľať rovnaké údaje o vozovom parku**, a tak si môžete ponechať **kópiu svojich údajov a fotografií mimo zariadenia**. To sa deje s cieľmi, ktoré **vy** konfigurujete pod **vašimi** účtami alebo **vašimi** servermi s vlastným hosťovaním – nie firemným „cloudom výdavkov na vozidlá“, ktorý môžu vidieť iní ľudia.

### <a id="what-runs-where"></a>Čo kde beží

| Druh | Čo ukladá | Typické použitie |
|------|----------------|-------------|
| **Tabuľková / tabuľková synchronizácia** | Vozidlá, palivové nádrže, výdavky (riadky a záložky) | Zlúčenie viacerých zariadení + štruktúrované zálohovanie |
| **Záloha fotografií** | Binárne obrázky (pomlčka/čerpadlo/účtenka/referenčné fotografie) | Zálohovanie fotografií + obnovenie chýbajúcich súborov |

Môžete nakonfigurovať **viaceré miesta určenia** každého druhu (soft cap na typ). Manuálne **Synchronizovať teraz** a pracovníci **na pozadí** spúšťajú povolené.

### <a id="offline-first"></a>Najskôr offline

- **Nie je potrebná žiadna sieť** na pridanie výdaja, výdaja alebo účtenky. Všetko sa uloží **najprv lokálne**.
- Keď je dostupná sieť, synchronizácia a zálohovanie fotografií sa spustí ako **úlohy na pozadí** (podľa nastaveného plánu a keď klepnete na **Synchronizovať teraz**). Zlyhania sa zobrazia ako červený text pod riadkami Nastavenia a **!** v záhlaví aplikácie.

### <a id="your-accounts-only"></a>Iba vaše účty

Prihlasovacie údaje a tokeny zostanú v zariadení pre poskytovateľov, ktorých si vyberiete (Google, Microsoft, kľúče S3, vlastné adresy URL atď.). Ciele sú pod **úplnou kontrolou používateľa** — váš účet Google, váš OneDrive, váš bucket MinIO, váš hostiteľ EtherCalc atď. Nič sa nezdieľa s ostatnými používateľmi nákladov na vozidlá prostredníctvom zdieľaného backendu.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Podporované ciele — údaje (tabuľkový/tabuľkový)

Konfigurované v **Menu → Synchronizácia → Synchronizácia hárka** (dostupné aj z riadkov súhrnu nastavení). Prvotriedne možnosti výberu:

| Cieľ | Poznámky |
|--------|--------|
| **Tabuľky Google** | Bežné predvolené nastavenie; karty pre vozidlá, výdavky a palivo pre vozidlo |
| **Excel** | Pracovný zošit Microsoft cez väzbu v štýle Graph / OneDrive |
| **EtherCalc** | Samoobslužné miestnosti na spoluprácu s tabuľkami |
| **Iné →** implementované backendy | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Odložené / zatiaľ bez hlavy (uvedené v časti Iné, ale nie úplne implementované): OnlyOffice, Collabora. Pozri tiež [index vlastného hostiteľa](reference/self-host/INDEX.md).

CSV **export/import** (ZIP s rovnakým rozložením kariet) je dostupný v Nastaveniach ako prenosná záloha, nezávislá od živej synchronizácie.

### <a id="supported-targets--photos-image-backup"></a>Podporované ciele — fotografie (záloha obrázkov)

Konfigurované v **Menu → Synchronizácia → Zálohovanie fotografií** (tiež z riadkov súhrnu nastavení):

| Cieľ | Poznámky |
|--------|--------|
| **Disk Google** | Priečinok, ktorý si vyberiete (prehliadať alebo prilepiť URL) |
| **OneDrive** | Účet Microsoft + predpona cesty |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO a ďalšie koncové body kompatibilné s S3 |
| **Iné** | úložisko zálohované rclone (napr. WebDAV, SFTP a ďalšie spravované diaľkové ovládače dostupné vo výbere v aplikácii) |

Nastaviť cheatsheets pre self-hosted fotografie a tabuľkové ciele: [self-host index](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Správanie viacerých zariadení (krátke)

- Riadky sa zlúčia podľa **ID synchronizácie** s **výhrami posledného zápisu** na **Aktualizovaných** časových pečiatkach.
- Vymazania sú mäkké; novšia úprava na inom zariadení môže obnoviť riadok.
– Zadaním **rovnakej výplne dvakrát** na dvoch zariadeniach sa vytvoria **dva riadky** — keď si to všimnete, odstráňte navyše.
- Ďalšie podrobnosti: [Synchronizovať poznámky o správaní](#sync-behavior-notes) a [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Príklad: pridajte Tabuľky Google (údaje)

1. **Menu → Synchronizácia → Synchronizácia hárka** (alebo Nastavenia → Synchronizácia hárka).

![Zoznam synchronizácie tabuľky](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Klepnite na **Pridať cieľ tabuľky**.

![Výber poskytovateľa](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Vyberte **Tabuľky Google**.

![Formulár Tabuliek Google](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Prihláste sa pomocou Google** raz – toto prihlásenie vám tiež umožní prehliadať. Zobrazovaný názov → prilepte **Webovú adresu hárka** (stále platná) alebo **🔍** prehliadajte. Prehliadať sa otvorí na **Všetky a zdieľané**, takže si môžete vybrať existujúcu tabuľku, ktorú vlastníte alebo ktorú s vami zdieľame. Potom možnosti plánovania → povoliť → uložiť.
5. **Synchronizujte teraz** raz na vytvorenie/aktualizáciu kariet: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Príklad: pridať Disk Google (fotografie)

1. **Menu → Synchronizácia → Zálohovanie fotografií** (alebo Nastavenia → Zálohovanie fotografií).

![Zoznam zálohovania fotografií](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Klepnite na **Pridať cieľ fotografie**.

![Výber poskytovateľa fotografií](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Vyberte **Disk Google**.

![Formulár Disku Google](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Prihlásiť sa pomocou Google (Disk)** → voliteľná adresa URL priečinka/prehliadať → povoliť → uložiť → **Synchronizovať teraz**.

Manuálna **Synchronizácia teraz** pre fotografie je úplná; zálohovanie na pozadí zvyčajne spracováva **len čakajúce** nahrávania podľa plánu.

### <a id="sync-behavior-notes"></a>Synchronizovať poznámky k správaniu

– Po inovácii aplikácie sa môže krátko zobraziť **„Aktualizácia databázy po aktualizácii...“** (záloha miestneho synchronizačného ID).
- Ak sa synchronizácia preruší, ďalšia **úspešná** synchronizácia sa znova spojí a opraví vzdialené karty.
- Zlyhania: červený súhrn na kartách synchronizácie + **!** na paneli aplikácií.

---

## <a id="fill-up-fuel"></a>Doplniť (palivo)

Toto je **domovská obrazovka**, keď otvoríte aplikáciu.

### <a id="vehicle-selection-usually-automatic"></a>Výber vozidla (zvyčajne automatický)

**nemusíte** si najprv vybrať vozidlo. Keď majú vozidlá **orientačné body** nastavené v Manage Vehicles, Fill **automaticky rozpozná, ktoré vozidlo** z obrazu prístrojovej dosky po zachytení počítadla kilometrov. Stále môžete otvoriť rozbaľovaciu ponuku **Vozidlo** a v prípade potreby ju prepísať.

### <a id="aim-at-the-odometer"></a>Namierte na počítadlo kilometrov

Zostaňte v režime počítadla kilometrov a orámujte klaster. Pokyn: *Namierte na počítadlo kilometrov. Snímajte klepnutím na spúšť.*

![Vyplniť — kamera v priamom prenose na počítadle kilometrov (v ukážke ukážkový obrázok pomlčky)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Po uzávierke počítadla kilometrov

OCR vyplní **Odo** a pokúsi sa priradiť vozidlo k orientačným bodom (v prípade potreby skontrolujte obe). Hlavné tlačidlo sa zmení na **Opakovať** na opätovné nasnímanie. Inštrukcia sumarizuje čítanie.

![Vyplniť — výsledok OCR počítadla kilometrov](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Režim pumpy (cena a objem)

1. Klepnutím na **↕** prepnete do režimu pumpy: *Zamerajte na displej pumpy (cena/objem). Klepnite na uzávierku.*
2. Zaznamenajte súčty čerpadla. Vyplňte polia nákladov a objemu; použite **↔**, ak sú vymenené.
3. V prípade potreby klepnite na menu alebo **G/L** a potom na **Uložiť** (disk). Prázdne polia tvoria **čiastočnú výplň** (stále povolené).

![Vyplniť — náhľad pumpy / súčty (príklad obrázku pumpy v náhľade)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Zostanete na Vyplniť na ďalšiu zastávku (polia sa po uložení vymažú). Pracujte plne **offline**; synchronizácia sa po nakonfigurovaní spustí neskôr na pozadí.

### <a id="manual-entry-no-camera--bad-ocr"></a>Manuálne zadanie (žiadna kamera / zlé OCR)

1. Klepnite na **Odo**, **cena** alebo **objem** a zadajte hodnoty (na výšku sa používa systémová klávesnica, na šírku sa používa klávesnica na obrazovke).
2. Ak automatická detekcia neprebehla, vyberte alebo potvrďte **Vehicle**.
3. Uložte ako vyššie.

### <a id="modes-and-borders"></a>Režimy a okraje

- **Zelený okraj** okolo vozidla+odo → zachytávanie/úprava počítadla kilometrov.
- **Zelený okraj** okolo cena+objem → režim pumpy.
- **Uložiť** zostane deaktivované, kým nie je vybraté vozidlo a aspoň jeden z parametrov odo/cena/objem nemá údaje a OCR stále nie je spustené.

Tip na obrazovke (pod riadkom s pokynmi): *Uzávierka = snímanie · Disk = uloženie · ↕ = režim odo/pumpa · ↔ = výmenná cena/objem.*

---

## <a id="expenses"></a>Výdavky

### <a id="new-expense"></a>Nový výdavok

Menu → **Nové náklady**.

![Nový výdajový formulár a ovládacie prvky fotoaparátu (príklad obrazu pomlčky v páse fotoaparátu, keď je prázdny)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Uložiť** (disk), **závierka** (fotka účtenky) alebo **galéria** (vybrať obrázok).
2. Vyplňte **Dátum**, **Vozidlo**, **Predajcu**, **Popis**, **Sumu** (symbol meny možno klepnúť), **Kategóriu**, voliteľné **Počítadlo kilometrov**.
3. Viacstranové potvrdenky: zachytávajte ďalšie strany, ak používateľské rozhranie ponúka stránkovanie (strana 0 je primárna potvrdenka).
4. **Uložiť** na uloženie (najskôr lokálne; zálohovanie fotografií a synchronizácia tabuliek prebieha na pozadí, keď je nakonfigurovaná).

### <a id="expense-list"></a>Zoznam výdavkov

Menu → **Správy** → **Zoznam výdavkov** — prehliadať minulé výdavky, ktoré nesúvisia s palivom; otvorte položku na úpravu.

![Zoznam výdavkov](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Upraviť výdavky

Otvorte riadok zo zoznamu. Správny predajca, množstvo, kategória, vozidlo a popis. Ak je účtenka iba v zálohe fotografie (žiadny čitateľný lokálny súbor), pri zobrazení použite možnosť **Načítať obrázok z archívu** (funguje v rámci nakonfigurovaných cieľových umiestnení fotografií).

![Upraviť náklady](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Začať cestu

Menu → **Začiatok cesty** (po Vyplniť zásuvku). Zaznamenajte alebo zadajte počítadlo kilometrov, vyberte typ cesty a uložte pomocou ikony **disk**. **Stop** je skratka pre Osobné teraz v zadržanej polohe GPS. Na pripomenutia ovládania použite **ⓘ**.

![Začiatok cesty](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Začiatky jazdy sú uložené ako riadky paliva s **Typom jazdy** (nie normálne náplne). Zobrazujú sa v časti **Správy → míle cesty**, nie v časti História paliva.

---

## <a id="reports"></a>Správy

Menu → **Správy** otvorí produktové centrum (celkový súhrn + katalógové karty). Toto je jediný povrch prehľadov produktov – neexistuje žiadna samostatná položka zásuvky „Správy a grafy“.

![Centrum prehľadov](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Otvorte kartu pre režim vozidla (**Všetky / Každé / Jedno**), filtre období, grafy a zdieľanie (**TEXT / CSV / PDF**). Horný panel na podriadených údajoch: **☰ + ←** (a **ⓘ** pri registrácii).

### <a id="time-based-reports"></a>Prehľady založené na čase

Hlavná karta mapy. Voliteľné metriky (mpg, objem/vzdialenosť, napr. G/mi, jednotková cena, napr. $/G, cena/vzdialenosť, mesačné $, míle cesty, % cesty podľa typu) s **Smooth** zásobníkmi a **nezávislou stupnicou Y** (ekonomika vľavo; rodiny peňazí a výletov vpravo).

![Časové prehľady](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Prehľady založené na čase – posúvané metriky](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Podrobnosti o ekonomickej matematike: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>História vyplňovania verzus história paliva

- **Správy → História vypĺňania** — chronologické vyplnenie filtrov prehľadov (**len výplne**; žiadna cesta nezačína).

![Vyplňte správu o histórii](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **História paliva** (ak je prítomná v navigácii vašej zostavy) – inventár naplnenia podľa vozidla sa tiež len vyplní; klepnite na riadok, ktorý chcete upraviť.

### <a id="trip-miles"></a>Typ míle

**Správy → Míle cesty** — míle podľa typu, mapy a chronologický **zoznam začiatku cesty / segmentov**. Klepnutím na skutočný začiatok otvoríte **Upraviť výplň** pre daný riadok.

![Trip miles](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Upraviť výplň

Otvorte výplň z Histórie výplní, História paliva alebo Cestovné míle. Rozloženie: vozidlo a počítadlo kilometrov, **mena pred nákladmi**, objem, poznámky. Typ cesty sa zobrazí iba vtedy, keď je riadok začiatok cesty. Miesto má súhrn plus **Podrobnosti o polohe**. Chýba miestna fotografia s cloudovou identitou: **Načítať obrázok z archívu**.

![Upraviť výplň — Načítať z archívu, keď chýba miestna fotografia](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Ďalšie katalógové karty obsahujú výdavky podľa kategórie, súhrn vozidla a zoznam výdavkov.

Money používa menu každého riadka, keď je nastavená. Súčty v zmiešaných menách zobrazujú **medzisúčty za jednotlivé meny** (bez tichého FX konverzie).

---

## <a id="syncing"></a>Synchronizuje sa

Menu → **Syncing** je centrum pre tabuľky a fotografie (nielen v Nastaveniach).

![Rozbočovač synchronizácie](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

– Karty pre **Synchronizáciu tabuľky** a **Zálohovanie fotografií** s krátkym stavom, **Synchronizácia** pre tento druh a **›** do zoznamu cieľov.
- Otvorte cieľ pre **Testovať pripojenie** a **Synchronizovať teraz (tento cieľ)** / všetko je nakonfigurované.
- Zlyhanie **Podrobnosti** a červené **!** v záhlaví tu pristanú.
– Podrobné nastavenie Tabuliek Google a Disku: [Zálohy a synchronizácia viacerých zariadení] (#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Nastavenia (miestne preferencie)

Menu → **Nastavenia**.

![Nastavenia](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Viac nastavení (posunuté)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Pre ciele uprednostnite **Menu → Synchronizácia**. Nastavenia môžu stále zobrazovať súhrnné riadky, ktoré otvárajú rovnaké zoznamy.

### <a id="local-preferences-common"></a>Miestne preferencie (bežné)

- **Uložiť fotografie príjmu paliva** / **Uložiť fotografie výdavkov lokálne** — uchovávajte obrázky v zariadení (môže požiadať o povolenie Fotografie).
- **Prehrať zvuk uzávierky**
- **Mena** / **Jednotka objemu** – predvolené nastavenia aplikácie (systémové alebo explicitné). Zmena jednotky objemu s existujúcimi údajmi o palive môže ponúknuť dialógové okno konverzie.
- **Tmavý režim**
- **Tipy na nastavenie** – znova otvorte návody na prvé spustenie vozidla / synchronizáciu.
- **Výplň ladenia** / **Zobrazenie obrazoviek experimentu (dev)** – pokročilé; nechajte na každodenné použitie. Experimentálne obrazovky tu nie sú zdokumentované.

CSV **export/import** (karty PSČ vozidiel / Výdavky / Palivo) je k dispozícii v Nastaveniach, ak ich ponúka aktuálna zostava.

---

## <a id="help--about"></a>Pomoc a informácie

![Pomoc](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![O](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Pomocník** — rýchly štart na zariadení, návody na nastavenie, odkaz na túto príručku, index nastavenia vlastného hostiteľa.
- **O ** — verzia, licencie, GitHub, táto príručka (pribalená offline + online HTML po zverejnení).

---

## <a id="related-docs"></a>Súvisiace dokumenty

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — krátky každodenný sprievodca
- [vlastný hostiteľ/INDEX.md](../reference/self-host/INDEX.md) — vlastné nastavenie fotografie/tabuľky
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — zlúčenie, obnovenie, duplikáty
– [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — podrobnosti ekonomických metrík
