# <a id="vehicle-expenses-automated--user-manual"></a>Automatizované náklady na vozidlo — Uživatelská příručka

> **Upravit zdroj (Markdown).** Prohlížeče a čtečka v aplikaci otevřou **vykreslený HTML**:
> - Web: [`docs/user-manual/index.html`](index.html) (regenerovat pomocí `./scripts/render-user-manual.sh`)
> - Aplikace: Nápověda / Informace → úplný manuál (přiložený HTML + snímky obrazovky)
>
> Neukazujte koncovým uživatelům na nezpracované adresy URL `.md` – prohlížeče zobrazují pouze prostý text.

Sledování tankování paliva a výdajů vozidla na první kameru, s volitelnou synchronizací více zařízení a zálohováním pod **vašimi** cloudovými účty.

Toto je **úplný manuál** (snímky obrazovky + každý krok). V telefonu je **Menu → Nápověda** kratším průvodcem pro začátečníky.

**Tady není zahrnuto:** Import starých obrázků, Experiment se zarovnáním a Experiment Pump (nástroje pro vývojáře / pokročilé nástroje).

---

## <a id="table-of-contents"></a>Obsah

1. [Co potřebujete](#what-you-need)
2. [Ikony na první pohled](#icons-at-a-glance)
3. [Otevřít nabídku](#open-the-menu)
4. [První nastavení: Správa vozidel](#first-time-setup-manage-vehicles)
5. [Zálohy a synchronizace více zařízení](#backups-and-multi-device-sync)
6. [Naplnit (palivo)](#fill-up-fuel)
7. [Začátek cesty](#start-trip)
8. [Výdaje](#expenses)
9. [Zprávy](#reports)
10. [Nastavení (místní preference)](#settings-local-preferences)
11. [Synchronizace](#syncing)
12. [Nápověda a informace](#help--about)
13. [Související dokumenty](#related-docs)

---

## <a id="what-you-need"></a>Co potřebujete

- Android telefon nebo tablet.
– Pro nejlepší OCR: jasný pohled na váš **počítadlo kilometrů na palubní desce** a **součty pump** (nebo zadejte čísla ručně).
– Volitelné: účty **spravujete** pro zálohování tabulkových dat a/nebo fotografií (viz [Zálohy a synchronizace více zařízení](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Ikony na první pohled

Objeví se na hlavních obrazovkách. Jejich znalost ušetří spoustu lovu.

| Kde | Ikona / ovládání | Co to dělá |
|-------|----------------|--------------|
| Horní lišta | **☰ Menu** (hamburger) | Otevře navigační zásuvku |
| Horní lišta | **ⓘ** (nápověda ke stránce) | Krátká nápověda pro **aktuální** stránku (vedle nabídky, pokud je k dispozici) |
| Horní lišta | **`?N`** (žlutá) | Čekající otázky kontroly importu — otevře Kontrola importu |
| Horní lišta | **!** (červená) | Cílová tabulka nebo fotografie se nedávno nezdařila – otevřete **Synchronizace** a opravte |
| Horní lišta | **☰ + ←** | Hlásit děti a seznam výdajů zobrazují **menu a zpět** dohromady; Centrum přehledů je pouze v nabídce |
| Nastavení / úprava paliva | **←** | Zpět (nastavení tabulky/fotky a úpravy paliva zůstávají zaměřeny zpět) |
| Vyplňte | **Bílý kruh** (závěrka) | Zachyťte počítadlo kilometrů nebo displej pumpy pro OCR |
| Vyplňte | **Disk / Uložit** | Uložte tankování (potřebuje vozidlo a alespoň jeden z pach/objem/cena) |
| Vyplňte | **↕ šipky** (přepínač režimu) | Přepnout **režim počítadla kilometrů** vs **režim pumpy (cena/objem)**. Zelený rámeček zvýrazní aktivní skupinu polí |
| Vyplňte | **↔ šipky** (mezi cenou a objemem) | Vyměňte náklady a objem, pokud je OCR vloží do nesprávných polí |
| Vyplňte | **Přiblížení 1x / …** | Poměry zoomu fotoaparátu, pokud je objektiv podporuje |
| Vyplňte (po zachycení) | **Obnovit** na hlavním tlačítku | Zahodit náhled a vrátit se do živé kamery |
| Vyplňte (během zpracování) | **X** na hlavním tlačítku | Zrušit probíhající snímání/OCR |
| Výdaj | **Uložit** | Ušetřete náklady |
| Výdaj | **Kruh závěrky** | Vyfotit účtenku |
| Výdaj | **Galerie** | Vyberte si obrázek účtenky z knihovny |
| Výdaj | **Opakování** | Vymažte aktuální účtenku a foťte znovu |
| Náklady / Správa vozidel | **+ / −** FAB | Zvětšit náhled fotografie |
| Dialog Orientační body | **Upravit OCR** ​​| Opravte nebo přidejte orientační text, který motory minuly |
| Tabulkový / foto formulář | **🔍 Hledat** | Vyhledejte na Disku Google list nebo složku (po přihlášení) |

Symboly měn v polích ceny a **G/L** v polích objemu lze klepnout: otevřete malou nabídku a změňte měnu nebo galony vs litry pro daný záznam.

---

## <a id="open-the-menu"></a>Otevřete nabídku

1. Klepněte na **☰** vlevo nahoře.
2. Vyberte stránku.

![Navigační šuplík](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Hlavní zásuvka:** Naplnit · Zahájit cestu · Správa vozidel · Nové výdaje · **Přehledy** · Nastavení · Synchronizace · Nápověda · O aplikaci.

**Zásuvka experimentu** (Nastavení → Zobrazit obrazovky experimentu): Experiment se zarovnáním · Experiment s pumpou · **Import starých obrázků**.

**Přes centrum zpráv (ne hlavní zásuvka):** Seznam výdajů · Historie plnění.

---

## <a id="first-time-setup-manage-vehicles"></a>První nastavení: Správa vozidel

OCR a **automatické přiřazování vozidel** fungují nejlépe poté, co zaregistrujete každé vozidlo pomocí **referenční fotografie z palubní desky**, spustíte **Discovery**, nastavíte ořez počítadla ujetých kilometrů ze semene pádla a uložíte orientační body pro tuto pomlčku.

### <a id="open-manage-vehicles"></a>Otevřete možnost Správa vozidel

Menu → **Správa vozidel**. Vyberte vozidlo (nebo **Přidat nové vozidlo**).

![Spravujte vozidla pomocí referenčních pomlček, plodin a objevování](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Přidat nebo upravit vozidlo

1. Otevřete rozevírací nabídku **Vehicle** → vyberte vozidlo nebo **Přidat nové vozidlo**.
2. Zachyťte nebo vyberte jasnou **referenční fotografii čárky** (úplný sdružený přístroj, dobře osvětlený, telefon zhruba čtvercový). Použijte **Vyfotit** nebo **Galerie**.
3. Klepněte na **Spustit zjišťování** – detekce pádla umístí na fotografii žlutá **semena** (orientační body používají stejný `"ML Kit"` JSON jako předtím).
4. Klepněte na **Vybrat Odo**, klepněte na semeno na číslicích, upravte úchyty a poté na **Uložit**.
5. **Upravit Odo** upraví pole, které již existuje. **Clear Odo** jej odstraní a na tlačítku se zobrazí **Select Odo**.
6. Zkontrolujte pomocí **Zobrazit orientační body**. Pomocí **Upravit OCR** ​​opravte chybně přečtená data nebo **přidejte** text, který chyběl.
7. Vyplňte **Název vozidla** (vyžadováno), plus značka/model/rok/štítek, jak chcete.
8. Klepněte na **Vytvořit vozidlo** nebo **Uložit změny** (vyžaduje jméno + referenční fotografii pro nové vozidlo).

![Přidat pole formuláře nového vozidla](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a> Orientační body: opravte, co Discovery zmeškal

Po **Zobrazit orientační body** procházejte seznam a opravte hodnoty. Motorům někdy chybí malé číslice (například hodiny **60** vpravo dole na sdruženém přístroji). Pomocí **Upravit OCR** ​​je přidejte nebo opravte, aby identita vozidla zůstala spolehlivá.

![Dialog Orientační body – kontrola a úprava OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Psaní bez dokonalé fotografie

Aplikaci můžete stále používat výběrem vozidla a **zadáním** počítadla kilometrů, objemu a ceny na Fill — OCR je pro každé pole volitelné. Import galerie funguje pro referenční fotografii, když nechcete fotit v aplikaci.

**Tip:** Po synchronizaci tabulky jsou definice vozidel (plodiny, orientační body) uloženy v místní databázi – nemusíte znovu otevírat Spravovat vozidla pro vyplnění, abyste je mohli používat.

---

## <a id="backups-and-multi-device-sync"></a>Zálohování a synchronizace více zařízení

Aplikace je vytvořena tak, aby **několik telefonů nebo tabletů mohlo sdílet stejná data vozového parku**, a vy si tak můžete ponechat **kopii svých dat a fotografií mimo zařízení**. To se děje s cíli, **které** konfigurujete pod **vašimi** účty nebo **vašimi** servery s vlastním hostitelem – nikoli firemním „cloudem výdajů na vozidla“, který mohou vidět ostatní lidé.

### <a id="what-runs-where"></a>Co kde běží

| Laskavý | Co ukládá | Typické použití |
|------|----------------|-------------|
| **Tabulková / tabulková synchronizace** | Vozidla, palivové nádrže, výdaje (řádky a záložky) | Sloučení více zařízení + strukturované zálohování |
| **Záloha fotografií** | Binární obrázky (pomlčka/čerpadlo/účtenka/referenční fotografie) | Záloha fotografií + obnovení chybějících souborů |

Můžete nakonfigurovat **více cílů** každého druhu (soft cap na typ). Ruční **Synchronizovat nyní** a pracovníci **na pozadí** spouštějí ty povolené.

### <a id="offline-first"></a>Nejdříve offline

- **Není vyžadována žádná síť** pro přidání doplňku, výlohy nebo účtenky. Vše se uloží **nejprve lokálně**.
– Když je k dispozici síť, synchronizace a zálohování fotografií se spustí jako **úlohy na pozadí** (podle nastaveného plánu a po klepnutí na **Synchronizovat nyní**). Selhání se zobrazí jako červený text pod řádky Nastavení a **!** v záhlaví aplikace.

### <a id="your-accounts-only"></a>Pouze vaše účty

Přihlašovací údaje a tokeny zůstávají v zařízení pro poskytovatele, které si vyberete (Google, Microsoft, klíče S3, adresy URL s vlastním hostitelem atd.). Cíle jsou pod **úplnou kontrolou uživatele** – váš účet Google, váš OneDrive, váš MinIO bucket, váš EtherCalc hostitel atd. Nic není sdíleno s ostatními uživateli Vehicle Expenses prostřednictvím sdíleného backendu.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Podporované cíle — data (tabulkový/tabulkový)

Nakonfigurováno pod **Menu → Synchronizace → Synchronizace tabulky** (dostupné také z řádků souhrnu Nastavení). Prvotřídní možnosti výběru:

| Cíl | Poznámky |
|--------|--------|
| **Tabulky Google** | Běžné výchozí nastavení; karty pro vozidla, výdaje a palivo pro vozidlo |
| **Excel** | Sešit Microsoftu prostřednictvím vazby ve stylu Graph / OneDrive |
| **EtherCalc** | Samoobslužné tabulkové místnosti pro spolupráci |
| **Ostatní →** implementované backendy | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Odloženo / zatím bez hlavy (uvedeno v části Jiné, ale není plně implementováno): OnlyOffice, Collabora. Viz také [index vlastního hostitele](reference/self-host/INDEX.md).

CSV **export/import** (ZIP se stejným rozložením karet) je k dispozici v Nastavení jako přenosná záloha, nezávislá na živé synchronizaci.

### <a id="supported-targets--photos-image-backup"></a>Podporované cíle — fotografie (záloha obrázku)

Nakonfigurováno pod **Nabídka → Synchronizace → Záloha fotografií** (také z řádků souhrnu Nastavení):

| Cíl | Poznámky |
|--------|--------|
| **Disk Google** | Složka, kterou si vyberete (procházet nebo vložit URL) |
| **OneDrive** | Účet Microsoft + předpona cesty |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO a další koncové body kompatibilní s S3 |
| **Ostatní** | úložiště zálohované rclone (např. WebDAV, SFTP a další spravovaná vzdálená zařízení dostupná ve výběru v aplikaci) |

Nastavte cheatsheety pro vlastní hostované fotografie a tabulkové cíle: [index vlastního hostitele](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Chování více zařízení (krátké)

- Řádky se sloučí podle **ID synchronizace** s **výhrami posledního zápisu** na **Aktualizovaných** časových razítkách.
- Smazání je měkké; novější úprava na jiném zařízení může řádek obnovit.
– Zadáním **stejné výplně dvakrát** na dvou zařízeních vytvoříte **dva řádky** — když si toho všimnete, smažte navíc.
- Další podrobnosti: [Poznámky k synchronizaci](#sync-behavior-notes) a [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Příklad: přidat Tabulky Google (data)

1. **Menu → Synchronizace → Synchronizace tabulky** (nebo Nastavení → Synchronizace tabulky).

![Seznam synchronizace tabulky](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Klepněte na **Přidat cíl tabulky**.

![Výběr poskytovatele](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Vyberte **Tabulky Google**.

![Formulář Tabulek Google](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Přihlaste se pomocí Google** jednou – toto přihlášení vám také umožní procházet. Zobrazovaný název → vložte **adresu URL listu** (stále platná) nebo **🔍** procházejte. Procházet se otevře na **Vše a sdíleno**, takže si můžete vybrat existující tabulku, kterou vlastníte nebo kterou s vámi někdo sdílí. Poté možnosti plánování → povolit → uložit.
5. **Synchronizujte nyní** jednou pro vytvoření/aktualizaci karet: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Příklad: přidat Disk Google (fotografie)

1. **Menu → Synchronizace → Záloha fotografií** (nebo Nastavení → Záloha fotografií).

![Seznam zálohování fotografií](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Klepněte na **Přidat cíl fotografie**.

![Výběr poskytovatele fotografií](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Vyberte **Disk Google**.

![Formulář Disku Google](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Přihlaste se pomocí Google (Disk)** → volitelné URL/procházení složky → povolit → uložit → **Synchronizovat nyní**.

Manuální **Synchronizace nyní** pro fotografie je úplná; zálohování na pozadí obvykle zpracovává **pouze čekající** nahrávání podle plánu.

### <a id="sync-behavior-notes"></a>Synchronizovat poznámky k chování

– Po upgradu aplikace se může krátce zobrazit **„Aktualizace databáze po upgradu...“** (záloha místního synchronizačního ID).
– Pokud je synchronizace přerušena, další **úspěšná** synchronizace se znovu spojí a opraví vzdálené karty.
- Selhání: červený souhrn na kartách synchronizace + **!** na liště aplikace.

---

## <a id="fill-up-fuel"></a>Doplňte (palivo)

Toto je **domovská obrazovka**, když otevřete aplikaci.

### <a id="vehicle-selection-usually-automatic"></a>Výběr vozidla (obvykle automatický)

**nemusíte** nejprve vybrat vozidlo. Když mají vozidla **orientační body** nastaveny v Manage Vehicles, Fill **automaticky detekuje, které vozidlo** ze snímku palubní desky poté, co pořídíte počítadlo kilometrů. Stále můžete otevřít rozbalovací nabídku **Vehicle** a v případě potřeby ji přepsat.

### <a id="aim-at-the-odometer"></a>Zamiřte na počítadlo kilometrů

Zůstaňte v režimu počítadla kilometrů a zarámujte sdružený přístroj. Pokyn: *Zamiřte na počítadlo kilometrů. Klepnutím na spoušť zachyťte.*

![Vyplnit — živá kamera na počítadle kilometrů (ukázkový obrázek pomlčky v náhledu)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Po závěrce počítadla kilometrů

OCR vyplní **Odo** a pokusí se přiřadit vozidlo k orientačním bodům (v případě potřeby zkontrolujte oba). Hlavní tlačítko se změní na **Opakovat** pro opětovné fotografování. Instrukce shrnuje četbu.

![Vyplnit — výsledek OCR počítadla kilometrů](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Režim pumpy (cena a objem)

1. Klepnutím na **↕** přepnete do režimu pumpy: *Zamiřte na displej pumpy (cena/objem). Klepněte na spoušť.*
2. Zachyťte celkové hodnoty čerpadla. Vyplňte pole nákladů a objemu; použijte **↔**, pokud jsou prohozeny.
3. V případě potřeby klepněte na měnu nebo **G/L** a poté na **Uložit** (disk). Prázdná pole tvoří **částečné vyplnění** (stále povoleno).

![Výplň — náhled pumpy / součty (příklad obrázku pumpy v náhledu)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Zůstanete na Vyplnit pro další zastávku (pole se po uložení vymažou). Pracujte plně **offline**; synchronizace se po konfiguraci spustí později na pozadí.

### <a id="manual-entry-no-camera--bad-ocr"></a>Manuální zadání (žádná kamera / špatné OCR)

1. Klepněte na **Odo**, **cena** nebo **objem** a zadejte hodnoty (na výšku používá systémovou klávesnici; na šířku používá klávesnici na obrazovce).
2. Pokud autodetekce neproběhla, vyberte nebo potvrďte **Vehicle**.
3. Uložte jako výše.

### <a id="modes-and-borders"></a>Režimy a okraje

- **Zelený okraj** kolem vozidla+odo → zachycení/úprava počítadla kilometrů.
- **Zelený okraj** kolem ceny+objemu → režim čerpadla.
- **Uložit** zůstane deaktivováno, dokud není vybráno vozidlo a alespoň jedno z pach/cen/objem má data a OCR stále neběží.

Tip na obrazovce (pod řádkem s pokyny): *Závěrka = zachycení · Disk = uložení · ↕ = režim ode/pumpa · ↔ = swap cena/objem.*

---

## <a id="expenses"></a>Výdaje

### <a id="new-expense"></a>Nový výdaj

Menu → **Nové výdaje**.

![Nový výdajový formulář a ovládání fotoaparátu (příklad obrázku čárky v pásku fotoaparátu, když je prázdný)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Uložit** (disk), **závěrka** (fotografie účtenky) nebo **galerie** (vybrat obrázek).
2. Vyplňte **Datum**, **Vozidlo**, **Dodavatel**, **Popis**, **Částku** (symbol měny lze klepnout), **Kategorii**, volitelně **Počítadlo kilometrů**.
3. Vícestránkové účtenky: zachyťte další stránky, pokud uživatelské rozhraní nabízí stránkování (stránka 0 je primární účtenka).
4. **Uložit** k uložení (nejprve místní; zálohování fotografií a synchronizace tabulek probíhá na pozadí, pokud je nakonfigurován).

### <a id="expense-list"></a>Seznam výdajů

Menu → **Zprávy** → **Seznam výdajů** — procházení minulých výdajů, které se netýkají paliva; otevřete položku, kterou chcete upravit.

![Seznam výdajů](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Upravit náklady

Otevřete řádek ze seznamu. Opravte dodavatele, množství, kategorii, vozidlo a popis. Pokud je účtenka pouze v záloze fotografie (žádný čitelný místní soubor), použijte při zobrazení **Načíst obrázek z archivu** (funguje napříč nakonfigurovanými cíli fotografií).

![Upravit náklady](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Zahájení cesty

Menu → **Start trip** (po Vyplnění zásuvky). Zachyťte nebo zadejte počítadlo kilometrů, vyberte typ cesty a uložte pomocí ikony **disk**. **Stop** je zkratka pro Osobní nyní v držené poloze GPS. Pro připomenutí ovládání použijte **ⓘ**.

![Start trip](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Začátky jízdy jsou uloženy jako řádky paliva s **Typem jízdy** (ne normální náplně). Zobrazují se v **Hlášení → Trasové míle**, nikoli v Historii paliva.

---

## <a id="reports"></a>Hlášení

Menu → **Reports** otevře produktové centrum (celkový souhrn + katalogové karty). Toto je jediný povrch zpráv o produktech – neexistuje žádná samostatná položka zásuvky „Zprávy a grafy“.

![Centrum přehledů](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Otevřete kartu pro režim vozidla (**Vše / Každé / Jedno**), filtry období, grafy a sdílení (**TEXT / CSV / PDF**). Horní lišta u dětí přehledu: **☰ + ←** (a **ⓘ** při registraci).

### <a id="time-based-reports"></a>Přehledy založené na čase

Hlavní karta mapy. Volitelné metriky (mpg, objem/vzdálenost jako G/mi, jednotková cena jako $/G, cena/vzdálenost, měsíční $, ujeté míle, % cesty podle typu) s **Smooth** přihrádkami a **nezávislými stupnicemi Y** (ekonomika vlevo; rodiny peněz a výletů vpravo).

![Časově založené zprávy](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Přehledy založené na čase – rolované metriky](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Podrobnosti ekonomické matematiky: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Historie plnění versus historie paliva

- **Zprávy → Historie vyplnění** — chronologické vyplnění filtrů zpráv (**pouze vyplnění**; žádná cesta nezačne).

![Hlášení o vyplnění historie](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Historie paliva** (pokud se nachází v navigaci vašeho sestavení) – inventář naplnění pro každé vozidlo, také se pouze naplní; klepněte na řádek, který chcete upravit.

### <a id="trip-miles"></a>Ujeté míle

**Přehledy → Míle cesty** — míle podle typu, grafy a chronologický **seznam začátku cesty / segmentu**. Klepnutím na skutečný začátek otevřete **Upravit výplň** pro daný řádek.

![Trip miles](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Upravit výplň

Z historie plnění, historie paliva nebo ujeté míle otevřete výplň. Uspořádání: vozidlo a počítadlo kilometrů, **měna před cenou**, objem, poznámky. Typ cesty se zobrazí pouze v případě, že řádek představuje začátek cesty. Místo má souhrn plus **Podrobnosti o poloze**. Chybí místní fotka s cloudovou identitou: **Načíst obrázek z archivu**.

![Upravit výplň — Načíst z archivu, když chybí místní fotografie](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Další katalogové karty obsahují výdaje podle kategorie, souhrn vozidel a seznam výdajů.

Money používá měnu každého řádku, když je nastavena. Součty ve smíšených měnách zobrazují **mezisoučty za měnu** (bez tichého převodu FX).

---

## <a id="syncing"></a>Synchronizace

Menu → **Syncing** je centrum pro umístění tabulek a fotografií (nejen v Nastavení).

![Synchronizační centrum](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

– Karty pro **synchronizaci tabulek** a **zálohování fotek** s krátkým stavem, **Synchronizace** pro tento druh a **›** do seznamu cílů.
- Otevřete cíl pro **Test připojení** a **Synchronizovat nyní (tento cíl)** / vše je nakonfigurováno.
- Selhání **Podrobnosti** a červené **!** v záhlaví zde přistávají.
– Krok za krokem nastavení Tabulek Google a Disku: [Zálohy a synchronizace více zařízení] (#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Nastavení (místní preference)

Menu → **Nastavení**.

![Nastavení](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Další nastavení (posunutí)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Pro cíle upřednostňujte **Menu → Synchronizace**. Nastavení mohou stále zobrazovat souhrnné řádky, které otevírají stejné seznamy.

### <a id="local-preferences-common"></a>Místní preference (společné)

- **Save Fuel Receipt Photos** / **Save Expense Photos Locally** — Uchovávejte obrázky v zařízení (může vyžadovat povolení pro Fotky).
- **Přehrát zvuk závěrky**
- **Měna** / **Jednotka objemu** — výchozí nastavení aplikace (systémové nebo explicitní). Změna jednotky objemu pomocí stávajících údajů o palivu může nabídnout dialog pro převod.
- **Tmavý režim**
- **Tipy pro nastavení** — znovu otevřete výukové programy pro první spuštění vozidla / synchronizace.
- **Debug Fill** / **Zobrazit obrazovky experimentu (dev)** — pokročilé; nechte pro každodenní použití. Experimentální obrazovky zde nejsou zdokumentovány.

CSV **export/import** (záložky ZIP vozidel / Výdaje / Palivo) je k dispozici v Nastavení, pokud je nabízí aktuální sestavení.

---

## <a id="help--about"></a>Nápověda a informace

![Nápověda](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![O](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Nápověda** — rychlý start na zařízení, návody k nastavení, odkaz na tuto příručku, index nastavení vlastního hostitele.
- **About** — verze, licence, GitHub, tato příručka (při publikování offline + online HTML).

---

## <a id="related-docs"></a>Související dokumenty

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — krátký každodenní průvodce
– [vlastní hostitel/INDEX.md](../reference/self-host/INDEX.md) – nastavení vlastní fotografie/tabulky
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — sloučení, obnovení, duplikáty
– [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — podrobnosti ekonomických metrik
