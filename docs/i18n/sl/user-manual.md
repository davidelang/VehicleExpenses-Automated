# <a id="vehicle-expenses-automated--user-manual"></a>Avtomatizirani stroški vozila — Uporabniški priročnik

> **Urejanje vira (Markdown).** Brskalniki in bralnik v aplikaciji odprejo **upodobljeni HTML**:
> - Splet: [`docs/user-manual/index.html`](index.html) (regeneriraj z `./scripts/render-user-manual.sh`)
> - Aplikacija: Pomoč / O → celoten priročnik (v paketu HTML + posnetki zaslona)
>
> Ne usmerjajte končnih uporabnikov na neobdelane `.md` URL-je — brskalniki prikazujejo samo navadno besedilo.

Sledenje s kamero za točenje goriva in stroške vozila z izbirno sinhronizacijo v več napravah in varnostnim kopiranjem pod **vašimi** računi v oblaku.

To je **celoten priročnik** (posnetki zaslona + vsak korak). V telefonu je **Meni → Pomoč** krajši vodnik za začetek.

**Tukaj ni zajeto:** Uvoz starih slik, poskus poravnave in poskus črpanja (orodja za razvijalce/napredna orodja).

---

## <a id="table-of-contents"></a>Kazalo

1. [Kaj potrebuješ](#what-you-need)
2. [Ikone na prvi pogled](#icons-at-a-glance)
3. [Odpri meni](#open-the-menu)
4. [Prva nastavitev: Upravljanje vozil](#first-time-setup-manage-vehicles)
5. [Varnostne kopije in sinhronizacija več naprav](#backups-and-multi-device-sync)
6. [Nalijte (gorivo)](#fill-up-fuel)
7. [Začetek potovanja](#start-trip)
8. [Stroški](#expenses)
9. [Poročila](#reports)
10. [Nastavitve (lokalne nastavitve)](#settings-local-preferences)
11. [Sinhronizacija](#syncing)
12. [Pomoč in o](#help--about)
13. [Povezani dokumenti](#related-docs)

---

## <a id="what-you-need"></a>Kaj potrebuješ

- telefon ali tablica Android.
– Za najboljši OCR: jasen pogled na vaš **števec kilometrov na armaturni plošči** in **skupne vrednosti črpalke** (ali vnesite številke ročno).
– Izbirno: računi, ki jih **nadzirate** za podatke preglednic in/ali varnostno kopiranje fotografij (glejte [Varnostne kopije in sinhronizacija v več napravah](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Ikone na prvi pogled

Te se prikažejo na glavnih zaslonih. Njihovo poznavanje prihrani veliko lova.

| Kje | Ikona / nadzor | Kaj počne |
|-------|----------------|--------------|
| Zgornja vrstica | **☰ Meni** (hamburger) | Odpre navigacijski predal |
| Zgornja vrstica | **ⓘ** (pomoč za stran) | Kratka pomoč za **trenutno** stran (zraven menija, ko je na voljo) |
| Zgornja vrstica | **`?N`** (rumena) | Čakajoča vprašanja o pregledu uvoza — odpre se pregled uvoza |
| Zgornja vrstica | **!** (rdeča) | Cilj preglednice ali fotografije je bil nedavno neuspešen — odprite **Sinhronizacijo**, da popravite |
| Zgornja vrstica | **☰ + ←** | Poročilo o otrocih in seznam stroškov prikazujeta **meni in nazaj** skupaj; Središče za poročila je samo meni |
| Nastavitve / urejanje goriva | ** ←** | Nazaj (preglednica z nastavitvami/urejanje fotografij in goriva ostane osredotočeno nazaj) |
| Izpolni | **Beli krog** (zaklop) | Zajemite prikaz števca kilometrov ali črpalke za OCR |
| Izpolni | **Disk / Shrani** | Shranite polnjenje (potrebuje vozilo in vsaj eno od odo/volumen/strošek) |
| Izpolni | **↕ puščice** (stikalo načina) | Preklopite med **načinom števca kilometrov** in **načinom črpalke (cena/prostornina)**. Zelena obroba označuje aktivno skupino polj |
| Izpolni | **↔ puščice** (med ceno in količino) | Zamenjaj stroške in količino, če ju OCR postavi v napačna polja |
| Izpolni | **Povečava 1x / …** | Razmerja povečave fotoaparata, ko jih objektiv podpira |
| Izpolnite (po zajemu) | **Osveži** na glavnem gumbu | Zavrzi predogled in se vrni na kamero v živo |
| Izpolnite (med obdelavo) | **X** na glavnem gumbu | Prekliči zajem v teku/OCR |
| Stroški | **Shrani** | Prihranite stroške |
| Stroški | **Zaklopni krog** | Posnemi fotografijo računa |
| Stroški | **Galerija** | Izberite sliko potrdila iz knjižnice |
| Stroški | **Ponovno posnemi** | Počistite trenutno fotografijo računa in ponovno fotografirajte |
| Stroški / Upravljanje vozil | **+ / −** FAB | Povečaj predogled fotografije |
| Pogovorno okno znamenitosti | **Uredi OCR** ​​| Popravite ali dodajte besedilo mejnika, ki so ga motorji spregledali |
| Obrazci za preglednice/fotografije | **🔍 Iskanje** | Prebrskajte v storitvi Google Drive za list ali mapo (po prijavi) |

Valutnih simbolov v poljih s stroški in **G/L** v poljih s količino se je mogoče dotakniti: odprite majhen meni, da spremenite valuto ali galone v primerjavi z litri za ta vnos.

---

## <a id="open-the-menu"></a>Odprite meni

1. Tapnite **☰** zgoraj levo.
2. Izberite stran.

![Navigacijski predal](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Glavni predal:** Izpolnite · Začnite potovanje · Upravljajte vozila · Novi stroški · **Poročila** · Nastavitve · Sinhronizacija · Pomoč · O.

**Predal za poskuse** (Nastavitve → Prikaži zaslone poskusov): Poskus poravnave · Eksperiment črpanja · **Uvozi stare slike**.

**Prek središča za poročila (ne v glavnem predalu):** Seznam stroškov · Izpolni zgodovino.

---

## <a id="first-time-setup-manage-vehicles"></a>Prva nastavitev: Upravljanje vozil

OCR in **samodejno ujemanje vozil** delujeta najbolje, ko vsako vozilo registrirate z **referenčno fotografijo armaturne plošče**, zaženete **Discovery**, nastavite obrezovanje števca kilometrov iz semena za veslanje in shranite orientacijske točke za ta pomišljaj.

### <a id="open-manage-vehicles"></a>Odpri Upravljanje vozil

Meni → **Upravljanje vozil**. Izberite vozilo (ali **Dodaj novo vozilo**).

![Upravljanje vozil z referenčno ploščo, pridelki in odkrivanjem](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Dodajte ali uredite vozilo

1. Odprite spustni meni **Vozilo** → izberite vozilo ali **Dodaj novo vozilo**.
2. Posnemite ali izberite jasno **fotografijo referenčne črtice** (celotna instrumentna plošča, dobro osvetljena, telefon približno pravokoten). Uporabite **Posnemi fotografijo** ali **Galerija**.
3. Tapnite **Zaženi odkrivanje** — zaznavanje vesla postavi rumena **semena** na fotografijo (mejniki uporabljajo isti `"ML Kit"` JSON kot prej).
4. Tapnite **Izberi Odo**, tapnite seme na številkah, prilagodite ročice in nato **Shrani**.
5. **Edit Odo** prilagodi polje, ki je že tam. **Počisti Odo** ga odstrani, gumb pa se glasi **Izberi Odo**.
6. Pregledajte z **Pokaži znamenitosti**. Uporabite **Edit OCR**, da popravite napačno branje ali **dodate** besedilo, ki je bilo zgrešeno.
7. Izpolnite **Ime vozila** (obvezno) in znamko/model/leto/tablico, kot želite.
8. Tapnite **Ustvari vozilo** ali **Shrani spremembe** (za novo vozilo je potrebno ime + referenčna fotografija).

![Dodaj nova polja obrazca za vozila](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Točke: popravite, kar je Discovery zamudil

Po **Pokaži znamenitosti** se pomaknite po seznamu in popravite vrednosti. Motorji včasih zgrešijo majhne številke (na primer ura **60** na spodnji desni strani gruče). Uporabite **Uredi OCR**, da jih dodate ali popravite, da bo identiteta vozila zanesljiva.

![Pogovorno okno Znamenitosti — pregled in urejanje OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Tipkanje brez popolne fotografije

Aplikacijo lahko še vedno uporabljate tako, da izberete vozilo in **vtipkate** števec kilometrov, prostornino in ceno na Fill — OCR ni obvezen za vsako polje. Uvoz galerije deluje za referenčno fotografijo črtice, ko ne želite fotografirati v aplikaciji.

**Namig:** Po sinhronizaciji preglednice so definicije vozil (pridelki, mejniki) v lokalni zbirki podatkov – za njihovo uporabo vam ni treba znova odpreti možnosti Upravljanje vozil za polnjenje.

---

## <a id="backups-and-multi-device-sync"></a>Varnostne kopije in sinhronizacija v več napravah

Aplikacija je zasnovana tako, da **več telefonov ali tabličnih računalnikov lahko deli iste podatke o voznem parku** in tako lahko obdržite **kopijo svojih podatkov in fotografij iz naprave**. To se naredi s cilji, ki jih **vi** konfigurirate pod **vašimi** računi ali **vašimi** strežniki, ki jih sami gostite – ne v »oblaku za stroške vozil«, ki ga vodi podjetje, ki ga lahko vidijo drugi ljudje.

### <a id="what-runs-where"></a>Kaj teče kam

| Prijazen | Kaj shranjuje | Tipična uporaba |
|------|----------------|-------------|
| **Sinhronizacija preglednice/tabele** | Vozila, dolivanje goriva, stroški (vrstice in zavihki) | Spajanje več naprav + strukturirano varnostno kopiranje |
| **Varnostna kopija fotografij** | Binarne slike (pomišljaj/črpalka/račun/referenčne fotografije) | Varnostno kopiranje fotografij + obnovitev manjkajočih datotek |

Konfigurirate lahko **več ciljev** vsake vrste (mehka kapica na vrsto). Ročno **Sinhroniziraj zdaj** in delavci v **ozadju** izvajajo omogočene.

### <a id="offline-first"></a>Najprej brez povezave

- **Za dodajanje polnjenja, stroškov ali potrdila** ni potrebno omrežje. Vse se shrani **najprej lokalno**.
– Ko je omrežje na voljo, se sinhronizacija in varnostno kopiranje fotografij izvajata kot **opravila v ozadju** (po urniku, ki ga nastavite, in ko tapnete **Sinhroniziraj zdaj**). Napake so prikazane kot rdeče besedilo pod vrsticami z nastavitvami in **!** v naslovni vrstici aplikacije.

### <a id="your-accounts-only"></a>Samo vaši računi

Prijava in žetoni ostanejo v napravi za ponudnike, ki jih izberete (Google, Microsoft, ključi S3, URL-ji, ki jih sami gostite itd.). Cilji so pod **popolnim nadzorom uporabnika** – vaš Google račun, vaš OneDrive, vaše vedro MinIO, vaš gostitelj EtherCalc itd. Nič se ne deli z drugimi uporabniki Vehicle Expenses prek skupnega zaledja.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Podprti cilji — podatki (preglednica/tabelarni)

Konfigurirano pod **Meni → Sinhronizacija → Sinhronizacija preglednice** (dostopno tudi iz vrstic s povzetkom nastavitev). Možnosti izbirnika prvega razreda:

| Cilj | Opombe |
|--------|--------|
| **Google Preglednice** | Skupna privzeta vrednost; zavihki za vozila, stroške in gorivo na vozilo |
| **Excel** | Microsoftov delovni zvezek prek vezave v slogu Graph/OneDrive |
| **EtherCalc** | Samostojne sobe za sodelovanje s preglednicami |
| **Drugo →** implementirana zaledja | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Odloženo / še ni brez glave (navedeno pod Drugo, vendar ni v celoti implementirano): OnlyOffice, Collabora. Glej tudi [indeks samogostitelja](reference/self-host/INDEX.md).

CSV **izvoz/uvoz** (ZIP iste postavitve zavihka) je na voljo v nastavitvah kot prenosna varnostna kopija, neodvisno od sinhronizacije v živo.

### <a id="supported-targets--photos-image-backup"></a>Podprti cilji — fotografije (varnostna kopija slike)

Konfigurirano pod **Meni → Sinhronizacija → Varnostno kopiranje fotografij** (tudi v vrsticah s povzetkom nastavitev):

| Cilj | Opombe |
|--------|--------|
| **Google Drive** | Mapa, ki jo izberete (prebrskajte ali prilepite URL) |
| **OneDrive** | Microsoftov račun + predpona poti |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO in druge končne točke, združljive s S3 |
| **Drugo** | shramba, podprta z rclone (npr. WebDAV, SFTP in drugi izbrani daljinci, ki so na voljo v izbirniku v aplikaciji) |

Nastavitev goljufij za fotografije in tabelarne cilje, ki jih sami gostite: [indeks samogostitelja](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Vedenje v več napravah (kratko)

- Vrstice se združijo po **Sync ID** z **last-write-wins** na **Updated** časovnih žigih.
- Izbrisi so mehki; novejše urejanje v drugi napravi lahko obnovi vrstico.
- Če dvakrat vnesete **isto polnjenje** v dveh napravah, ustvarite **dve vrstici** — izbrišite dodatek, ko opazite.
– Več podrobnosti: [Opombe o vedenju sinhronizacije](#sync-behavior-notes) in [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Primer: dodajte Google Preglednice (podatki)

1. **Meni → Sinhronizacija → Sinhronizacija preglednice** (ali Nastavitve → Sinhronizacija preglednice).

![Seznam za sinhronizacijo preglednic](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Tapnite **Dodaj cilj preglednice**.

![Izbira ponudnika](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Izberite **Google Preglednice**.

![obrazec za Google Preglednice](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Enkrat se prijavite z Googlom** — ta prijava vam omogoča tudi brskanje. Prikazno ime → prilepite **URL lista** (še veljaven) ali **🔍** brskajte. Brskanje se odpre na **Vse in v skupni rabi**, tako da lahko izberete obstoječo preglednico, ki je vaša ali je v skupni rabi z vami. Nato možnosti razporeda → omogoči → shrani.
5. **Sinhroniziraj zdaj** enkrat, da ustvariš/posodobiš zavihke: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Primer: dodajte Google Drive (fotografije)

1. **Meni → Sinhronizacija → Varnostno kopiranje fotografij** (ali Nastavitve → Varnostno kopiranje fotografij).

![Seznam varnostnih kopij fotografij](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Tapnite **Dodaj cilj fotografije**.

![Izbirnik ponudnika fotografij](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Izberite **Google Drive**.

![obrazec Google Drive](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Prijava z Google (Drive)** → izbirni URL mape/prebrskaj → omogoči → shrani → **Sinhroniziraj zdaj**.

Ročna **Sync now** za fotografije je popolna; varnostno kopiranje v ozadju običajno obdeluje **samo čakajoče** nalaganje po urniku.

### <a id="sync-behavior-notes"></a>Sinhroniziraj opombe o obnašanju

- Po nadgradnji aplikacije boste morda za kratek čas videli **»Posodabljanje baze podatkov po nadgradnji…«** (zapolnitev lokalnega sinhroniziranega ID-ja).
- Če je sinhronizacija prekinjena, naslednja **uspešna** sinhronizacija ponovno združi in popravi oddaljene zavihke.
- Napake: rdeč povzetek na karticah za sinhronizacijo + **!** v vrstici aplikacij.

---

## <a id="fill-up-fuel"></a>Polivanje (gorivo)

To je **začetni zaslon**, ko odprete aplikacijo.

### <a id="vehicle-selection-usually-automatic"></a>Izbira vozila (običajno samodejno)

**Ni** vam treba najprej izbrati vozila. Ko imajo vozila v upravljanju vozil nastavljene **točke**, Fill **samodejno zazna, katero vozilo** iz slike armaturne plošče, potem ko zajamete števec kilometrov. Še vedno lahko odprete spustni meni **Vozilo** in ga po potrebi preglasite.

### <a id="aim-at-the-odometer"></a>Nameri na števec kilometrov

Ostanite v načinu števca prevožene poti in uokvirite gručo. Navodilo: *Pomerite se na števec kilometrov. Dotaknite se sprožilca za zajem.*

![Izpolni — kamera v živo na števcu kilometrov (primer slike pomišljaja v predogledu)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Za zaklopom števca kilometrov

OCR zapolni **Odo** in poskuša ujemati vozilo z mejniki (preglejte oboje, če je potrebno). Glavni gumb postane **Poskusi znova** za ponovno fotografiranje. Navodilo povzema branje.

![Izpolni — rezultat OCR števca kilometrov](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Način črpalke (cena in količina)

1. Tapnite **↕**, da preklopite v način črpalke: *Ciljajte na prikaz črpalke (cena/prostornina). Tapnite sprožilec.*
2. Zajemite skupne vrednosti črpalke. Izpolnite polja za stroške in količino; uporabite **↔**, če sta zamenjani.
3. Po potrebi tapnite valuto ali **G/L** in nato **Shrani** (disk). Prazna polja so **delna izpolnitev** (še vedno dovoljeno).

![Izpolni — predogled črpalke / skupne vrednosti (primer slike črpalke v predogledu)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Za naslednji postanek ostanete na Izpolni (polja se po shranjevanju počistijo). Delajte popolnoma **brez povezave**; sinhronizacija teče pozneje v ozadju, ko je konfigurirana.

### <a id="manual-entry-no-camera--bad-ocr"></a>Ročni vnos (brez kamere/slab OCR)

1. Tapnite **Odo**, **strošek** ali **glasnost** in vnesite vrednosti (pokončno uporabite sistemsko tipkovnico; ležeče uporabite tipkovnico na zaslonu).
2. Izberite ali potrdite **Vozilo**, če se samodejno zaznavanje ni zagnalo.
3. Shrani kot zgoraj.

### <a id="modes-and-borders"></a>Načini in meje

- **Zelena obroba** okoli vozila+odo → zajem/urejanje števca kilometrov.
- **Zelena obroba** okoli cene+količine → način črpalke.
- **Shrani** ostane onemogočeno, dokler ni izbrano vozilo in vsaj eden od odo/stroški/prostornine vsebuje podatke in OCR še vedno ne teče.

Namig na zaslonu (pod vrstico z navodili): *Zaklop = zajemanje · Disk = shranjevanje · ↕ = način odo/črpanje · ↔ = zamenjava stroškov/prostornine.*

---

## <a id="expenses"></a>Stroški

### <a id="new-expense"></a>Nov strošek

Meni → **Nov strošek**.

![Nov obrazec za stroške in kontrolniki kamere (primer slike pomišljaja v pasu kamere, ko je prazen)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Shrani** (disk), **zaklop** (fotografija potrdila) ali **galerija** (izberi sliko).
2. Izpolnite **Datum**, **Vozilo**, **Dobavitelj**, **Opis**, **Znesek** (simbol valute se lahko dotaknete), **Kategorija**, neobvezno **Števec kilometrov**.
3. Večstranska potrdila: zajemite dodatne strani, če uporabniški vmesnik omogoča ostranjevanje (stran 0 je primarno potrdilo).
4. **Shrani** za shranjevanje (najprej lokalno; varnostno kopiranje fotografij in sinhronizacija preglednice potekata v ozadju, ko je konfigurirano).

### <a id="expense-list"></a>Seznam stroškov

Meni → **Poročila** → **Seznam stroškov** — prebrskajte pretekle stroške, ki niso povezani z gorivom; odprite predmet za urejanje.

![Seznam stroškov](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Urejanje stroškov

Odprite vrstico s seznama. Pravilen prodajalec, znesek, kategorija, vozilo in opis. Če je potrdilo samo v varnostni kopiji fotografije (brez berljive lokalne datoteke), uporabite **Pridobi sliko iz arhiva**, ko je prikazano (deluje med konfiguriranimi cilji fotografij).

![Uredi stroške](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Začni pot

Meni → **Začni potovanje** (po Izpolnite predal). Zajemite ali vnesite števec kilometrov, izberite vrsto potovanja, shranite z ikono **disk**. **Stop** je bližnjica za osebno zdaj na zadržani lokaciji GPS. Uporabite **ⓘ** za nadzorne opomnike.

![Začetek potovanja](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Začetki poti so shranjeni kot vrstice za gorivo z **Vrsto vožnje** (ne običajno polnjenje). Prikažejo se pod **Poročila → Prevožene milje**, ne pod Zgodovina goriva.

---

## <a id="reports"></a>Poročila

Meni → **Poročila** odpre središče izdelkov (stalni povzetek + kataloške kartice). To je edina površina za poročila o izdelkih – v predalu ni ločenega elementa »Poročila in grafikoni«.

![Središče poročil](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Odprite kartico za način vozila (**Vsi / Vsak / Posamezno**), filtre obdobij, grafikone in skupno rabo (**BESEDILO / CSV / PDF**). Zgornja vrstica pri otrocih poročil: **☰ + ←** (in **ⓘ**, ko so registrirani).

### <a id="time-based-reports"></a>Časovna poročila

Glavna karta karte. Izbirne meritve (mpg, prostornina/razdalja, kot je G/mi, cena na enoto, kot je $/G, cena/razdalja, mesečni $, potovalne milje, % potovanja glede na vrsto) z **Gladkimi** zabojniki in **neodvisnimi Y lestvicami** (varčnost levo; denar in družine potovanj na desni).

![Časovna poročila](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Časovna poročila — drsne meritve](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Podrobnosti ekonomske matematike: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Zgodovina polnjenja v primerjavi z zgodovino goriva

- **Poročila → Izpolni zgodovino** — kronološka polnjenja za filtre poročil (**samo polnjenja**; noben začetek potovanja).

![Izpolni poročilo o zgodovini](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Zgodovina goriva** (če je prisotna v navigaciji vaše zgradbe) — popis polnjenja za posamezno vozilo, tudi samo polnjenja; tapnite vrstico za urejanje.

### <a id="trip-miles"></a>Potovane milje

**Poročila → Prevožene milje** — milje po vrsti, grafikoni in kronološki **seznam začetka potovanja/odsekov**. Tapnite pravi začetek, da odprete **Uredi polnilo** za to vrstico.

![Potovane milje](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Urejanje polnila

V možnosti Fill history (Zgodovina polnjenja), Fuel History (Zgodovina goriva) ali Trip miles (Potovane milje) odprite polnjenje. Postavitev: vozilo in števec kilometrov, **valuta pred stroški**, prostornina, opombe. Vrsta potovanja se prikaže le, če je vrstica začetek potovanja. Lokacija ima povzetek in **podrobnosti o lokaciji**. Manjka lokalna fotografija z identiteto v oblaku: **Pridobi sliko iz arhiva**.

![Uredi polnilo — Pridobi iz arhiva, ko manjka lokalna fotografija](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Druge kataloške kartice vključujejo stroške po kategorijah, povzetek vozila in seznam stroškov.

Money uporablja valuto vsake vrstice, ko je nastavljena. Seštevki v mešanih valutah prikazujejo **vmesne vsote za posamezne valute** (brez tihe pretvorbe tečajev).

---

## <a id="syncing"></a>Sinhronizacija

Meni → **Sinhronizacija** je središče za cilje preglednic in fotografij (niso samo zakopani v nastavitvah).

![Sinhronizirajoče središče](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Kartice za **Sinhronizacijo preglednic** in **Varnostno kopiranje fotografij** s kratkim stanjem, **Sinhronizacija** za to vrsto in **›** na ciljni seznam.
- Odprite cilj za **Preskusi povezavo** in **Sinhroniziraj zdaj (ta cilj)** / vse konfigurirano.
- Napaka **Podrobnosti** in rdeči **!** v naslovni vrstici pristaneta tukaj.
– Nastavitev Google Preglednic in Drive po korakih: [Varnostne kopije in sinhronizacija v več napravah](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Nastavitve (lokalne nastavitve)

Meni → **Nastavitve**.

![Nastavitve](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Več nastavitev (pomaknjeno)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Za cilje izberite **Meni → Sinhronizacija**. Nastavitve lahko še vedno prikazujejo vrstice s povzetki, ki odpirajo iste sezname.

### <a id="local-preferences-common"></a>Lokalne nastavitve (skupno)

- **Shrani fotografije računov za gorivo** / **Lokalno shrani fotografije stroškov** — hranite slike v napravi (lahko zahtevate dovoljenje za fotografije).
- **Predvajaj zvok zaklopa**
- **Valuta** / **Enota količine** — privzete nastavitve aplikacije (sistemske ali eksplicitne). Spreminjanje prostorninske enote z obstoječimi podatki o gorivu lahko ponudi pogovorno okno za pretvorbo.
- **Temni način**
- **Nasveti za nastavitev** — znova odprite vadnice za prvo vožnjo vozila/sinhronizacijo.
- **Dopolnitev za odpravljanje napak** / **Prikaži poskusne zaslone (dev)** — napredno; pustite za vsakodnevno uporabo. Eksperimentalni zasloni tukaj niso dokumentirani.

CSV **izvoz/uvoz** (ZIP zavihkov Vozila / Stroški / Gorivo) je na voljo v nastavitvah, če jih ponuja trenutna zgradba.

---

## <a id="help--about"></a>Pomoč & O

![Pomoč](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![O](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Pomoč** — hiter začetek v napravi, vadnice za nastavitev, povezava do tega priročnika, kazalo nastavitev samostojnega gostitelja.
- **O** — različica, licence, GitHub, ta priročnik (v paketu brez povezave + spletni HTML, ko je objavljen).

---

## <a id="related-docs"></a>Povezani dokumenti

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — kratek vsakodnevni vodnik
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — nastavitev fotografij/tabel s samostojnim gostovanjem
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — spajanje, obnovitev, dvojniki
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — podrobnosti meritev ekonomičnosti
