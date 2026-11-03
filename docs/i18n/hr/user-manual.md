# <a id="vehicle-expenses-automated--user-manual"></a>Automatizirani troškovi vozila — korisnički priručnik

> **Uredi izvor (Markdown).** Preglednici i čitač unutar aplikacije otvaraju **renderirani HTML**:
> - Web: [`docs/user-manual/index.html`](index.html) (regeneriraj s `./scripts/render-user-manual.sh`)
> - Aplikacija: Pomoć / O → cijeli priručnik (u paketu HTML + snimke zaslona)
>
> Nemojte usmjeravati krajnje korisnike na neobrađene `.md` URL-ove — preglednici prikazuju samo običan tekst.

Praćenje najprije kamerom za točenje goriva i troškove vozila, s dodatnom sinkronizacijom na više uređaja i sigurnosnom kopijom pod **vašim** računima u oblaku.

Ovo je **potpuni priručnik** (snimke zaslona + svaki korak). Na telefonu, **Izbornik → Pomoć** je kraći vodič za početak.

**Ovdje nije obuhvaćeno:** Uvoz starih slika, eksperiment poravnanja i eksperiment pumpanja (razvojni/napredni alati).

---

## <a id="table-of-contents"></a>Sadržaj

1. [Što trebate](#what-you-need)
2. [Ikone na prvi pogled](#icons-at-a-glance)
3. [Otvori izbornik](#open-the-menu)
4. [Prvo postavljanje: Upravljanje vozilima](#first-time-setup-manage-vehicles)
5. [Sigurnosne kopije i sinkronizacija s više uređaja](#backups-and-multi-device-sync)
6. [Napunite (gorivo)](#fill-up-fuel)
7. [Početak putovanja](#start-trip)
8. [Troškovi](#expenses)
9. [Izvješća](#reports)
10. [Postavke (lokalne postavke)](#settings-local-preferences)
11. [Sinkronizacija](#syncing)
12. [Pomoć i o](#help--about)
13. [Povezani dokumenti](#related-docs)

---

## <a id="what-you-need"></a>Što vam treba

- Android telefon ili tablet.
- Za najbolji OCR: jasan prikaz vašeg **odometra na upravljačkoj ploči** i **ukupnih vrijednosti pumpe** (ili upišite brojeve rukom).
- Izborno: računi koje **vi kontrolirate** za podatke proračunske tablice i/ili sigurnosne kopije fotografija (pogledajte [Sigurnosne kopije i sinkronizacija s više uređaja](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Ikone na prvi pogled

Oni se pojavljuju na glavnim zaslonima. Poznavanje njih štedi mnogo lova.

| Gdje | Ikona / kontrola | Što radi |
|-------|----------------|--------------|
| Gornja traka | **☰ Jelovnik** (hamburger) | Otvara navigacijsku ladicu |
| Gornja traka | **ⓘ** (pomoć za stranicu) | Kratka pomoć za **trenutačnu** stranicu (pored izbornika ako je dostupan) |
| Gornja traka | **`?N`** (žuto) | Pitanja za pregled uvoza na čekanju — otvara pregled uvoza |
| Gornja traka | **!** (crveno) | Odredište proračunske tablice ili fotografije nedavno nije uspjelo — otvorite **Sinkronizaciju** da popravite |
| Gornja traka | **☰ + ←** | Izvješće o djeci i Popis troškova prikazuju **izbornik i natrag** zajedno; Središte izvješća je samo izbornik |
| Postavke / uređivanje goriva | ** ←** | Natrag (uređivanje proračunske tablice s postavkama/fotografija i goriva ostaje fokusirano) |
| Ispuni | **Bijeli krug** (zatvarač) | Snimite prikaz brojača kilometara ili pumpe za OCR |
| Ispuni | **Disk / Spremi** | Sačuvajte punjenje (potrebno je vozilo i barem jedno odo / volumen / cijena) |
| Ispuni | **↕ strelice** (prekidač načina) | Prebacite **način brojača kilometara** na **način rada pumpe (cijena/volumen)**. Zeleni obrub ističe aktivnu grupu polja |
| Ispuni | **↔ strelice** (između cijene i količine) | Zamijenite cijenu i količinu ako ih OCR stavi u pogrešna polja |
| Ispuni | **Zumiranje 1x / …** | Omjeri zumiranja fotoaparata kada ih leća podržava |
| Ispunite (nakon snimanja) | **Osvježi** na glavnom gumbu | Odbaci pregled i vrati se na kameru uživo |
| Fill (tijekom obrade) | **X** na glavnom gumbu | Otkaži snimanje/OCR u tijeku |
| Trošak | **Spremi** | Uštedite troškove |
| Trošak | **Krug zatvarača** | Snimite fotografiju računa |
| Trošak | **Galerija** | Odaberite sliku računa iz knjižnice |
| Trošak | **Ponovo snimi** | Izbrišite trenutnu fotografiju računa i ponovno snimite |
| Troškovi / Upravljanje vozilima | **+ / −** FAB | Zumirajte pregled fotografije |
| Dijaloški okvir Orijentiri | **Uredi OCR** ​​| Ispravite ili dodajte tekst orijentira koji su motori propustili |
| Proračunska tablica / Obrasci za fotografije | **🔍 Traži** | Pregledajte Google Drive za list ili mapu (nakon prijave) |

Simboli valute u poljima cijene i **G/L** u poljima volumena mogu se dodirnuti: otvorite mali izbornik za promjenu valute ili galona u odnosu na litre za taj unos.

---

## <a id="open-the-menu"></a>Otvori izbornik

1. Dodirnite **☰** gore lijevo.
2. Odaberite stranicu.

![Navigacijska ladica](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Glavna ladica:** Ispuni · Započni putovanje · Upravljaj vozilima · Novi trošak · **Izvješća** · Postavke · Sinkronizacija · Pomoć · O.

**Eksperimentalna ladica** (Postavke → Prikaži ekrane eksperimenta): Eksperiment poravnanja · Eksperiment pumpe · **Uvoz starih slika**.

**Putem čvorišta Izvješća (ne glavne ladice):** Popis troškova · Ispuni povijest.

---

## <a id="first-time-setup-manage-vehicles"></a>Prvo postavljanje: Upravljanje vozilima

OCR i **automatsko uparivanje vozila** najbolje funkcioniraju nakon što registrirate svako vozilo s **referentnom fotografijom nadzorne ploče**, pokrenete **Discovery**, postavite usjev na brojaču kilometara i spremite orijentire za tu crticu.

### <a id="open-manage-vehicles"></a>Otvorite Upravljanje vozilima

Izbornik → **Upravljanje vozilima**. Odaberite vozilo (ili **Dodaj novo vozilo**).

![Upravljajte vozilima pomoću referentne crtice, usjeva i otkrića](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Dodajte ili uredite vozilo

1. Otvorite padajući izbornik **Vozilo** → odaberite vozilo ili **Dodaj novo vozilo**.
2. Snimite ili odaberite jasnu **fotografiju referentne crtice** (puna ploča s instrumentima, dobro osvijetljena, telefon otprilike u obliku kvadrata). Koristite **Snimi fotografiju** ili **Galeriju**.
3. Dodirnite **Pokreni otkrivanje** — detekcija lopatice postavlja žuta **sjemena** na fotografiju (orijentiri koriste isti `"ML Kit"` JSON kao prije).
4. Dodirnite **Odaberi Odo**, dodirnite početnu vrijednost na znamenkama, podesite ručke, zatim **Spremi**.
5. **Edit Odo** prilagođava okvir koji je već tamo. **Clear Odo** ga uklanja, a gumb zatim glasi **Select Odo**.
6. Pregledajte pomoću **Prikaži orijentire**. Upotrijebite **Uredi OCR** ​​da popravite pogrešno čitanje ili **dodate** tekst koji je propušten.
7. Ispunite **Naziv vozila** (obavezno), plus marku/model/godinu/pločicu po želji.
8. Dodirnite **Kreiraj vozilo** ili **Spremi promjene** (potrebno je ime + referentna fotografija za novo vozilo).

![Dodaj nova polja obrasca vozila](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Orijentiri: popravi ono što je Discovery propustio

Nakon **Prikaži orijentire**, pomaknite se popisom i ispravite vrijednosti. Motorima ponekad nedostaju male znamenke (na primjer sat **60** u donjem desnom kutu klastera). Upotrijebite **Edit OCR** ​​da ih dodate ili popravite kako bi identitet vozila ostao pouzdan.

![Dijalog Orijentiri — pregledajte i uredite OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Pisanje bez savršene fotografije

I dalje možete koristiti aplikaciju odabirom vozila i **upisivanjem** brojača kilometara, volumena i cijene na Ispuni — OCR nije obavezan za svako polje. Uvoz galerije radi za referentnu crtičastu fotografiju kada radije ne snimate u aplikaciji.

**Savjet:** Nakon sinkronizacije proračunske tablice, definicije vozila (usjevi, znamenitosti) žive u lokalnoj bazi podataka — ne morate ponovno otvarati Upravljanje vozilima za Ispunu da biste ih koristili.

---

## <a id="backups-and-multi-device-sync"></a>Sigurnosne kopije i sinkronizacija na više uređaja

Aplikacija je napravljena tako da **nekoliko telefona ili tableta može dijeliti iste podatke o voznom parku** i tako da možete zadržati **kopiju svojih podataka i fotografija s uređaja**. To se radi s odredištima koja **vi** konfigurirate pod **vašim** računima ili **vašim** poslužiteljima koji sami hostiraju — a ne "oblakom troškova vozila" kojim upravlja tvrtka i koji drugi ljudi mogu vidjeti.

### <a id="what-runs-where"></a>Što kamo trči

| Ljubazni | Što pohranjuje | Tipična upotreba |
|------|----------------|-------------|
| **Proračunska tablica/tabelarna sinkronizacija** | Vozila, goriva, troškovi (redovi i kartice) | Spajanje više uređaja + strukturirana sigurnosna kopija |
| **Sigurnosna kopija fotografija** | Binarne slike (fotografije ploče/pumpe/potvrde/referentne slike) | Sigurnosna kopija fotografija + vraćanje datoteka koje nedostaju |

Možete konfigurirati **više odredišta** svake vrste (soft cap po vrsti). Ručni **Sinkroniziraj sada** i **pozadinski** radnici pokreću one omogućene.

### <a id="offline-first"></a>Prvo izvan mreže

- **Nije potrebna mreža** za dodavanje dopune, troška ili računa. Sve se sprema **prvo lokalno**.
- Kada je mreža dostupna, sinkronizacija i sigurnosno kopiranje fotografija pokreću se kao **zadaci u pozadini** (po rasporedu koji postavite i kada dodirnete **Sinkroniziraj sada**). Pogreške se prikazuju kao crveni tekst ispod redaka postavki i **!** u naslovnoj traci aplikacije.

### <a id="your-accounts-only"></a>Samo vaši računi

Prijava i tokeni ostaju na uređaju za pružatelje usluga koje odaberete (Google, Microsoft, S3 ključevi, URL-ovi s vlastitim hostingom itd.). Odredišta su pod **potpunom kontrolom korisnika** — vaš Google račun, vaš OneDrive, vaš MinIO spremnik, vaš EtherCalc host, itd. Ništa se ne dijeli s drugim korisnicima Troškova vozila putem dijeljene pozadine.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Podržani ciljevi — podaci (proračunska tablica/tabelarni)

Konfigurirano pod **Izbornik → Sinkronizacija → Sinkronizacija proračunske tablice** (dostupno i iz redaka sažetka postavki). Mogućnosti berača prve klase:

| Cilj | Bilješke |
|--------|--------|
| **Google tablice** | Uobičajena zadana vrijednost; kartice za Vozila, Troškove i gorivo po vozilu |
| **Excel** | Microsoftova radna knjiga putem povezivanja u stilu Grapha/OneDrive |
| **EtherCalc** | Samostalne sobe za suradnju s proračunskim tablicama |
| **Ostalo →** implementirane pozadine | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Odgođeno / još nije bez glave (navedeno pod Ostalo, ali nije u potpunosti implementirano): OnlyOffice, Collabora. Vidi također [indeks samodomaćina](reference/self-host/INDEX.md).

CSV **izvoz/uvoz** (ZIP istog izgleda kartice) dostupan je u postavkama kao prijenosna sigurnosna kopija, neovisno o sinkronizaciji uživo.

### <a id="supported-targets--photos-image-backup"></a>Podržani ciljevi — fotografije (sigurnosna kopija slike)

Konfigurirano pod **Izbornik → Sinkronizacija → Sigurnosna kopija fotografija** (također iz redaka sažetka postavki):

| Cilj | Bilješke |
|--------|--------|
| **Google Drive** | Mapa koju odaberete (pregledajte ili zalijepite URL) |
| **OneDrive** | Microsoftov račun + prefiks puta |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO i druge krajnje točke kompatibilne sa S3 |
| **Ostalo** | pohrana podržana rclone (npr. WebDAV, SFTP i drugi odabrani daljinski upravljači dostupni u biraču unutar aplikacije) |

Postavite varalice za vlastite fotografije i tablične ciljeve: [indeks samodomaćina](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Ponašanje na više uređaja (kratko)

- Redovi se spajaju prema **ID-u sinkronizacije** s **pobjedama zadnjeg pisanja** na vremenskim oznakama **Ažurirano**.
- Brisanja su mekana; novije uređivanje na drugom uređaju može vratiti redak.
- Unos **istog punjenja dvaput** na dva uređaja stvara **dva retka** — izbrišite višak kada primijetite.
- Više detalja: [Napomene o sinkroniziranju ponašanja](#sync-behavior-notes) i [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Primjer: dodajte Google tablice (podaci)

1. **Izbornik → Sinkronizacija → Sinkronizacija proračunske tablice** (ili Postavke → Sinkronizacija proračunske tablice).

![Popis za sinkronizaciju proračunske tablice](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Dodirnite **Dodaj odredište proračunske tablice**.

![Odabir davatelja](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Odaberite **Google tablice**.

![obrazac Google tablica](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Prijavite se s Googleom** jednom — ta vam prijava također omogućuje pregledavanje. Ime za prikaz → zalijepite **URL lista** (još uvijek vrijedi) ili **🔍** pregledajte. Pregledavanje se otvara na **Sve i zajedničko** tako da možete odabrati postojeću proračunsku tablicu koju posjedujete ili koja je podijeljena s vama. Zatim opcije rasporeda → omogući → spremi.
5. **Sinkronizirajte sada** jednom za izradu/ažuriranje kartica: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Primjer: dodajte Google Drive (fotografije)

1. **Izbornik → Sinkronizacija → Sigurnosna kopija fotografije** (ili Postavke → Sigurnosna kopija fotografije).

![Popis sigurnosnih kopija fotografija](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Dodirnite **Dodaj odredište fotografije**.

![Odabir davatelja fotografija](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Odaberite **Google Drive**.

![Google Drive obrazac](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Prijavite se s Google (Drive)** → izborna mapa URL/pretraži → omogući → spremi → **Sinkroniziraj sada**.

Ručno **Sinkroniziraj sada** za fotografije je pun prolaz; sigurnosna kopija u pozadini obično obrađuje prijenose **samo na čekanju** prema rasporedu.

### <a id="sync-behavior-notes"></a>Sinkroniziraj bilješke o ponašanju

- Nakon nadogradnje aplikacije možete nakratko vidjeti **"Ažuriranje baze podataka nakon nadogradnje..."** (lokalno popunjavanje id-a sinkronizacije).
- Ako se sinkronizacija prekine, sljedeća **uspješna** sinkronizacija ponovno spaja i popravlja udaljene kartice.
- Neuspjesi: crveni sažetak na karticama za sinkronizaciju + **!** na traci aplikacije.

---

## <a id="fill-up-fuel"></a>Napunite (gorivo)

Ovo je **početni zaslon** kada otvorite aplikaciju.

### <a id="vehicle-selection-usually-automatic"></a>Odabir vozila (obično automatski)

**Ne** trebate prvo odabrati vozilo. Kada vozila imaju **orijentire** postavljene u Upravljanju vozilima, Fill **automatski otkriva koje vozilo** sa slike na ploči nakon što snimite brojač kilometara. I dalje možete otvoriti padajući izbornik **Vozilo** da biste ga nadjačali ako je potrebno.

### <a id="aim-at-the-odometer"></a>Naciljaj na brojač kilometara

Ostanite u načinu rada brojača kilometara i uokvirite grupu. Uputa: *Naciljajte na brojač kilometara. Dodirnite okidač za snimanje.*

![Ispuni — kamera uživo na brojaču kilometara (primjer slike crtice u pregledu)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Nakon zatvarača brojača kilometara

OCR ispunjava **Odo** i pokušava usporediti vozilo s orijentirima (pregledajte oba ako je potrebno). Glavni gumb postaje **Pokušaj ponovno** za ponovno snimanje. Uputa sažima pročitano.

![Popunite — OCR rezultat brojača kilometara](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Način pumpanja (cijena i količina)

1. Dodirnite **↕** za prebacivanje u način rada pumpe: *Ciljajte na prikaz pumpe (cijena/volumen). Okidač na dodir.*
2. Zabilježite ukupne vrijednosti pumpe. Ispunite polja za cijenu i količinu; koristite **↔** ako su zamijenjeni.
3. Dodirnite valutu ili **G/L** ako je potrebno, zatim **Spremi** (disk). Prazna polja čine **djelomično popunjavanje** (još uvijek dopušteno).

![Punjenje — pregled pumpe / ukupni iznosi (primjer slike pumpe u pregledu)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Ostajete na Ispuni za sljedeću stanicu (polja se brišu nakon spremanja). Radite potpuno **offline**; sinkronizacija se izvodi kasnije u pozadini kada se konfigurira.

### <a id="manual-entry-no-camera--bad-ocr"></a>Ručni unos (nema kamere / loš OCR)

1. Dodirnite **Odo**, **cijena** ili **volumen** i upišite vrijednosti (portret koristi tipkovnicu sustava; pejzaž koristi tipkovnicu na zaslonu).
2. Odaberite ili potvrdite **Vozilo** ako se automatsko otkrivanje nije pokrenulo.
3. Spremite kao gore.

### <a id="modes-and-borders"></a>Modovi i granice

- **Zelena granica** oko vozila+odo → snimanje/uređivanje brojača kilometara.
- **Zeleni obrub** oko cijene+volumena → način rada pumpe.
- **Spremi** ostaje onemogućeno sve dok se ne odabere vozilo i barem jedno odo/cijena/volumen ima podatke, a OCR još uvijek nije pokrenut.

Savjet na zaslonu (ispod retka s uputama): *Zatvaranje = snimanje · Disk = spremanje · ↕ = odo/pump mod · ↔ = zamjena troškova/volumena.*

---

## <a id="expenses"></a>Troškovi

### <a id="new-expense"></a>Novi trošak

Izbornik → **Novi trošak**.

![Novi obrazac za troškove i kontrole kamere (primjer slike crtice na traci kamere kada je prazna)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Spremi** (disk), **zatvor** (fotografija računa) ili **galerija** (odaberi sliku).
2. Ispunite **Datum**, **Vozilo**, **Dobavljač**, **Opis**, **Iznos** (može se dodirnuti simbol valute), **Kategorija**, opcijski **Odometar**.
3. Potvrde s više stranica: uhvatite dodatne stranice ako korisničko sučelje nudi straničenje (stranica 0 je primarna potvrda).
4. **Spremi** za pohranu (prvo lokalno; sigurnosna kopija fotografija i sinkronizacija proračunske tablice odvijaju se u pozadini kada se konfigurira).

### <a id="expense-list"></a>Popis troškova

Izbornik → **Izvješća** → **Popis troškova** — pregledajte prošle troškove koji nisu za gorivo; otvoriti stavku za uređivanje.

![Popis troškova](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Uredi trošak

Otvaranje retka s popisa. Točan dobavljač, iznos, kategorija, vozilo i opis. Ako je račun samo u sigurnosnoj kopiji fotografije (nema čitljive lokalne datoteke), upotrijebite **Dohvati sliku iz arhive** kada se prikaže (radi na svim konfiguriranim odredištima fotografija).

![Uredi trošak](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Počni putovanje

Izbornik → **Pokreni putovanje** (nakon Ispuni ladicu). Snimite ili unesite brojač kilometara, odaberite vrstu putovanja, spremite s ikonom **disk**. **Zaustavi** je prečac za Osobno sada na zadržanoj GPS lokaciji. Koristite **ⓘ** za podsjetnike za kontrolu.

![Početak putovanja](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Početci putovanja pohranjuju se kao redovi goriva s **Tipom putovanja** (ne normalno punjenje). Pojavljuju se pod **Izvješća → Putne milje**, a ne pod Povijest goriva.

---

## <a id="reports"></a>Izvješća

Izbornik → **Izvješća** otvara središte proizvoda (sažetak svih vremena + kataloške kartice). Ovo je jedina površina za izvješća o proizvodu — nema zasebne stavke u ladici "Izvješća i grafikoni".

![Središte izvješća](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Otvorite karticu za način vozila (**Svi / Svaki / Pojedinačno**), filtre razdoblja, grafikone i dijeljenje (**TEKST / CSV / PDF**). Gornja traka na djeci izvješća: **☰ + ←** (i **ⓘ** kada su registrirani).

### <a id="time-based-reports"></a>Izvješća temeljena na vremenu

Glavna karta karte. Dodatna metrika (mpg, obujam/udaljenost kao što je G/mi, jedinična cijena kao što je $/G, cijena/udaljenost, mjesečni $, milje putovanja, % putovanja po vrsti) s **Glatkim** spremnicima i **nezavisnim Y skalama** (ekonomija lijevo; novac i obitelji putovanja desno).

![Vremenska izvješća](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Izvješća temeljena na vremenu — klizajuće metrike](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Pojedinosti ekonomske matematike: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Povijest punjenja u odnosu na povijest goriva

- **Izvješća → Popuni povijest** — kronološka popuna za filtre izvješća (**samo popuna**; bez pokretanja putovanja).

![Ispunite izvješće o povijesti](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Povijest goriva** (ako postoji u navigaciji vaše konstrukcije) — popis punjenja po vozilu, također samo punjenja; dodirnite redak za uređivanje.

### <a id="trip-miles"></a>Milje putovanja

**Izvješća → Prijeđene milje** — milje prema vrsti, karte i kronološki **popis početaka/segmenta putovanja**. Dodirnite pravi početak da biste otvorili **Uređivanje ispune** za taj redak.

![putne milje](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Uredi ispunu

U Povijest punjenja, Povijest goriva ili Putne milje, otvorite punjenje. Izgled: vozilo i brojač kilometara, **valuta prije troška**, volumen, bilješke. Vrsta putovanja pojavljuje se samo kada je red početak putovanja. Lokacija ima sažetak plus **Pojedinosti o lokaciji**. Nedostaje lokalna fotografija s identitetom u oblaku: **Dohvati sliku iz arhive**.

![Uredi ispunu — dohvati iz arhive ako nedostaje lokalna fotografija](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Ostale kataloške kartice uključuju troškove po kategorijama, sažetak vozila i popis troškova.

Novac koristi valutu svakog retka kada je postavljen. Ukupni iznosi u mješovitim valutama prikazuju **podzbrojeve po valuti** (bez tihe konverzije valuta).

---

## <a id="syncing"></a>Sinkronizacija

Izbornik → **Sinkronizacija** je središte za proračunske tablice i odredišta za fotografije (ne samo u Postavkama).

![Sinkronizirajuće središte](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Kartice za **Sinkronizaciju proračunske tablice** i **Sigurnosno kopiranje fotografija** s kratkim statusom, **Sinkronizacija** za tu vrstu i **›** u popis odredišta.
- Otvorite odredište za **Test veze** i **Sinkroniziraj sada (ovo odredište)** / sve je konfigurirano.
- Greška **Detalji** i crveno **!** u naslovnoj traci padaju ovdje.
- Korak po korak postavljanje Google tablica i Diska: [Sigurnosne kopije i sinkronizacija na više uređaja](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Postavke (lokalne postavke)

Izbornik → **Postavke**.

![Postavke](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Više postavki (pomicano)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Za odredišta preferirajte **Izbornik → Sinkronizacija**. Postavke mogu i dalje prikazivati ​​retke sa sažetkom koji otvaraju iste popise.

### <a id="local-preferences-common"></a>Lokalne postavke (uobičajeno)

- **Spremi fotografije računa za gorivo** / **Spremi fotografije troškova lokalno** — čuvajte slike na uređaju (može zahtijevati dopuštenje za fotografije).
- **Reproduciraj zvuk okidača**
- **Valuta** / **Jedinica volumena** — zadane postavke aplikacije (sustavne ili eksplicitne). Promjena jedinice volumena s postojećim podacima o gorivu može ponuditi dijaloški okvir za pretvorbu.
- **Tamni način rada**
- **Savjeti za postavljanje** — ponovno otvorite vodiče za prvo pokretanje vozila / sinkronizaciju.
- **Debug Fill** / **Prikaži eksperimentalne zaslone (dev)** — napredno; ostaviti za svakodnevnu upotrebu. Eksperimentalni zasloni nisu ovdje dokumentirani.

CSV **izvoz/uvoz** (ZIP kartice Vozila / Troškovi / Gorivo) dostupan je u Postavkama kada to nudi trenutna verzija.

---

## <a id="help--about"></a>Pomoć i o

![Pomoć](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![O](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Pomoć** — brzi početak na uređaju, vodiči za postavljanje, poveznica na ovaj priručnik, indeks postavljanja na samom hostu.
- **O** — verzija, licence, GitHub, ovaj priručnik (u paketu offline + online HTML kada se objavi).

---

## <a id="related-docs"></a>Povezani dokumenti

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — kratki svakodnevni vodič
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — fotografije/tabelarne postavke koje sami hostirate
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — spajanje, oporavak, duplikati
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — pojedinosti o ekonomskoj metrici
