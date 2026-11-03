# <a id="vehicle-expenses-automated--user-manual"></a>Automatiserte kjøretøyutgifter — brukerhåndbok

> **Rediger kilde (Markdown).** Nettlesere og i-app-leseren åpner **gjengitt HTML**:
> - Nett: [`docs/user-manual/index.html`](index.html) (regenerer med `./scripts/render-user-manual.sh`)
> - App: Hjelp / Om → full manual (medfølger HTML + skjermbilder)
>
> Ikke pek sluttbrukere på ubehandlede `.md`-URLer – nettlesere viser kun ren tekst.

Kamera-første sporing for drivstoffpåfylling og kjøretøyutgifter, med valgfri synkronisering og sikkerhetskopiering av flere enheter under **dine** skykontoer.

Dette er den **fullstendige manualen** (skjermbilder + hvert trinn). På telefonen er **Meny → Hjelp** en kortere startveiledning.

**Ikke dekket her:** Importer gamle bilder, justeringseksperiment og pumpeeksperiment (utvikler/avanserte verktøy).

---

## <a id="table-of-contents"></a> Innholdsfortegnelse

1. [Det du trenger](#what-you-need)
2. [Ikoner på et øyeblikk](#icons-at-a-glance)
3. [Åpne menyen](#open-the-menu)
4. [Førstegangsoppsett: Administrer kjøretøy](#first-time-setup-manage-vehicles)
5. [Sikkerhetskopier og synkronisering av flere enheter](#backups-and-multi-device-sync)
6. [Fyll (drivstoff)](#fill-up-fuel)
7. [Start turen](#start-trip)
8. [Utgifter](#expenses)
9. [Rapporter](#reports)
10. [Innstillinger (lokale preferanser)](#settings-local-preferences)
11. [Synkronisering](#syncing)
12. [Hjelp og om](#help--about)
13. [Relaterte dokumenter](#related-docs)

---

## <a id="what-you-need"></a>Hva du trenger

- Android-telefon eller nettbrett.
- For best OCR: en klar visning av **dashboardets kilometerteller** og **pumpetotaler** (eller skriv inn tallene for hånd).
– Valgfritt: kontoer **du kontrollerer** for regnearkdata og/eller sikkerhetskopiering av bilder (se [Sikkerhetskopiering og synkronisering av flere enheter](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Ikoner på et øyeblikk

Disse vises på hovedskjermene. Å kjenne dem sparer mye jakt.

| Hvor | Ikon / kontroll | Hva den gjør |
|-------|----------------|--------------|
| Topp bar | **☰ Meny** (hamburger) | Åpner navigasjonsskuffen |
| Topp bar | **ⓘ** (sidehjelp) | Kort hjelp for den **nåværende** siden (ved siden av menyen når tilgjengelig) |
| Topp bar | **`?N`** (gul) | Ventende spørsmål om importgjennomgang – åpner Importer gjennomgang |
| Topp bar | **!** (rød) | Et regneark eller bildemål mislyktes nylig — åpne **Synkroniserer** for å fikse |
| Topp bar | **☰ + ←** | Rapporter barn og utgifter liste vis **meny og tilbake** sammen; Rapportsenter er kun meny |
| Innstillinger / drivstoffredigering | **←** | Tilbake (innstillingsregneark/foto og drivstoffredigering forblir tilbakefokusert) |
| Fyll | **Hvit sirkel** (lukker) | Registrer kilometerteller eller pumpevisning for OCR |
| Fyll | **Disk / Lagre** | Lagre påfyllingen (trenger et kjøretøy og minst en av odo / volum / kostnad) |
| Fyll | **↕ piler** (modusbryter) | Veksle mellom **kilometertellermodus** og **pumpemodus (kostnad/volum)**. Grønn kant markerer den aktive feltgruppen |
| Fyll | **↔ piler** (mellom kostnad og volum) | Bytt kostnad og volum hvis OCR legger dem i feil felt |
| Fyll | **Zoom 1x / …** | Kameraets zoomforhold når objektivet støtter dem |
| Fyll (etter fangst) | **Oppdater** på hovedknapp | Forkast forhåndsvisning og gå tilbake til live kamera |
| Fyll (under behandling) | **X** på hovedknappen | Avbryt pågående opptak/OCR |
| Utgifter | **Lagre** | Spar utgiftene |
| Utgifter | **Lukkersirkel** | Ta et kvitteringsbilde |
| Utgifter | **Galleri** | Velg et kvitteringsbilde fra biblioteket |
| Utgifter | **Reta** | Fjern gjeldende kvitteringsbilde og ta bilder på nytt |
| Utgifter / Administrer kjøretøy | **+ / −** FABs | Zoom forhåndsvisningen av bildet |
| Landemerker dialog | **Rediger OCR** ​​| Korriger eller legg til landemerketekst motorene gikk glipp av |
| Regneark / bildeskjemaer | ** søk** | Bla gjennom Google Disk for et ark eller en mappe (etter pålogging) |

Valutasymboler på kostnadsfelt og **G/L** på volumfelt kan trykkes: åpne en liten meny for å endre valuta eller gallons vs liter for den oppføringen.

---

## <a id="open-the-menu"></a>Åpne menyen

1. Trykk på **☰** øverst til venstre.
2. Velg en side.

![Navigasjonsskuff](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Hovedskuff:** Fyll · Start tur · Administrer kjøretøy · Ny utgift · **Rapporter** · Innstillinger · Synkronisering · Hjelp · Om.

**Eksperimentskuff** (Innstillinger → Vis eksperimentskjermer): Justeringseksperiment · Pumpeeksperiment · **Importer gamle bilder**.

**Via Reports hub (ikke hovedskuff):** Utgiftsliste · Fyll historikk.

---

## <a id="first-time-setup-manage-vehicles"></a>Førstegangsoppsett: Administrer kjøretøy

OCR og **automatisk kjøretøytilpasning** fungerer best etter at du har registrert hvert kjøretøy med et **referansebilde på dashbord**, kjører **Discovery**, setter kilometertelleravlingen fra et årefrø og lagrer landemerker for den dashbordet.

### <a id="open-manage-vehicles"></a>Åpne Administrer kjøretøy

Meny → **Administrer kjøretøy**. Velg et kjøretøy (eller **Legg til nytt kjøretøy**).

![Administrer kjøretøy med referansestrek, avlinger og oppdagelse](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Legg til eller rediger et kjøretøy

1. Åpne rullegardinmenyen **Kjøretøy** → velg et kjøretøy eller **Legg til nytt kjøretøy**.
2. Ta eller velg et tydelig **referansestrekbilde** (full instrumentklynge, godt opplyst, telefon omtrent kvadratisk). Bruk **Ta bilde** eller **Galleri**.
3. Trykk på **Kjør Discovery** — padle-deteksjon plasserer gule **frø** på bildet (landemerker bruker samme `"ML Kit"` JSON som før).
4. Trykk på **Velg Odo**, trykk på frøet på sifrene, juster håndtakene og deretter **Lagre**.
5. **Rediger Odo** justerer en boks som allerede er der. **Tøm Odo** fjerner den, og knappen viser deretter **Velg Odo**.
6. Gjennomgå med **Vis landemerker**. Bruk **Rediger OCR** ​​for å fikse feillesing eller **legge til** tekst som ble savnet.
7. Fyll inn **Kjøretøysnavn** (påkrevd), pluss merke/modell/år/skilt som du vil.
8. Trykk på **Opprett kjøretøy** eller **Lagre endringer** (krever navn + referansebilde for et nytt kjøretøy).

![Legg til nye kjøretøyskjemafelt](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Landemerker: fiks det Discovery gikk glipp av

Etter **Vis landemerker**, bla gjennom listen og korriger verdiene. Noen ganger savner motorer små sifre (for eksempel en klokke **60** nederst til høyre i klyngen). Bruk **Rediger OCR** ​​for å legge til eller fikse dem slik at kjøretøyets identitet forblir pålitelig.

![Landemerker-dialog – se gjennom og rediger OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Tatter uten et perfekt bilde

Du kan fortsatt bruke appen ved å velge et kjøretøy og **skrive** kilometerteller, volum og pris på Fyll — OCR er valgfritt for hvert felt. Galleriimport fungerer for referansestrekbildet når du foretrekker å ikke ta bilder i appen.

**Tips:** Etter regnearksynkronisering, finnes kjøretøydefinisjoner (avlinger, landemerker) i den lokale databasen – du trenger ikke å åpne Administrer kjøretøy for fyll på nytt for å bruke dem.

---

## <a id="backups-and-multi-device-sync"></a>Sikkerhetskopier og synkronisering av flere enheter

Appen er bygget slik at **flere telefoner eller nettbrett kan dele de samme flåtedataene**, og slik at du kan holde en **kopi av dataene og bildene dine utenfor enheten**. Dette gjøres med destinasjoner **du** konfigurerer under **dine** kontoer eller **dine** selvvertsbaserte servere – ikke en bedriftsdrevet "Vehicle Expenses-sky" som andre kan se.

### <a id="what-runs-where"></a>Hva går hvor

| Snill | Hva den lagrer | Typisk bruk |
|-------|----------------|-------------------|
| **Regneark / tabellsynkronisering** | Kjøretøy, drivstofffyllinger, utgifter (rader og faner) | Sammenslåing av flere enheter + strukturert sikkerhetskopiering |
| **Sikkerhetskopiering av bilder** | Binære bilder (dash/pumpe/kvittering/referansebilder) | Sikkerhetskopiering av bilder + gjenopprett manglende filer |

Du kan konfigurere **flere destinasjoner** av hver type (soft cap per type). Manuelle **Synkroniser nå** og **bakgrunnsarbeidere kjører de aktiverte.

### <a id="offline-first"></a>Frakoblet først

- **Ingen nettverk er nødvendig** for å legge til en påfylling, utgift eller kvittering. Alt lagres **lokalt først**.
– Når nettverket er tilgjengelig, kjøres synkronisering og sikkerhetskopiering av bilder som **bakgrunnsoppgaver** (i henhold til en tidsplan du angir, og når du trykker på **Synkroniser nå**). Feil vises som rød tekst under Innstillinger-radene og et **!** i appens tittellinje.

### <a id="your-accounts-only"></a> Kun kontoene dine

Pålogging og tokens forblir på enheten for leverandørene du velger (Google, Microsoft, S3-nøkler, webadresser som er vert for selv, og så videre). Destinasjoner er under **full kontroll av brukeren** - Google-kontoen din, OneDrive, MinIO-bøtten din, EtherCalc-verten, osv. Ingenting deles med andre brukere av kjøretøyutgifter via en delt backend.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Støttede mål — data (regneark/tabell)

Konfigurert under **Meny → Synkronisering → Regnearksynkronisering** (også tilgjengelig fra rader med sammendrag av innstillinger). Førsteklasses plukkealternativer:

| Mål | Merknader |
|--------|--------|
| **Google Regneark** | Vanlig standard; faner for kjøretøy, utgifter og drivstoff per kjøretøy |
| **Excel** | Microsoft-arbeidsbok via Graph / OneDrive-stil binding |
| **EtherCalc** | Selvdrevne samarbeidende regnearkrom |
| **Andre →** implementerte backends | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Utsatt / ikke hodeløs ennå (oppført under Annet, men ikke fullt implementert): OnlyOffice, Collabora. Se også [selvvertsindeks](reference/self-host/INDEX.md).

CSV **eksport/import** (ZIP med samme faneoppsett) er tilgjengelig fra Innstillinger som en bærbar sikkerhetskopi, uavhengig av live-synkronisering.

### <a id="supported-targets--photos-image-backup"></a>Støttede mål — bilder (bildesikkerhetskopi)

Konfigurert under **Meny → Synkronisering → Bildesikkerhetskopiering** (også fra innstillingssammendragsrader):

| Mål | Merknader |
|--------|--------|
| **Google Disk** | Mappe du velger (bla gjennom eller lim inn URL) |
| **OneDrive** | Microsoft-konto + baneprefiks |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO og andre S3-kompatible endepunkter |
| **Annet** | rclone-støttet lagring (f.eks. WebDAV, SFTP og andre kurerte fjernkontroller tilgjengelig i app-velgeren) |

Sett opp jukseark for bilde- og tabellmål som er vert for selv: [selvvertsindeks](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Multi-enhetsadferd (kort)

- Rader slås sammen med **Synkroniserings-ID** med **siste-skriv-vinner** på **Oppdaterte** tidsstempler.
- Deletes er myke; en nyere redigering på en annen enhet kan gjenopprette en rad.
- Hvis du skriver inn **samme fyll to ganger** på to enheter, opprettes **to rader** — slett det ekstra når du legger merke til det.
- Flere detaljer: [Synkroniser adferdsnotater](#sync-behavior-notes) og [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Eksempel: legg til Google Sheets (data)

1. **Meny → Synkronisering → Regnearksynkronisering** (eller Innstillinger → Regnearksynkronisering).

![Synkroniseringsliste for regneark](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Trykk på **Legg til regnearkdestinasjon**.

![Leverandørvelger](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Velg **Google Regneark**.

![Google Regneark-skjema](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Logg på med Google** én gang — den påloggingen lar deg også surfe. Visningsnavn → lim inn en **Arknettadresse** (fortsatt gyldig) eller ****** bla gjennom. Bla gjennom åpnes på **Alle og delte** slik at du kan velge et eksisterende regneark du eier eller som deles med deg. Deretter planlegge alternativer → aktiver → lagre.
5. **Synkroniser nå** én gang for å opprette/oppdatere faner: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Eksempel: legg til Google Disk (bilder)

1. **Meny → Synkronisering → Bildesikkerhetskopiering** (eller Innstillinger → Bildesikkerhetskopiering).

![Sikkerhetskopieringsliste for bilder](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Trykk på **Legg til bildedestinasjon**.

![Fotoleverandørvelger](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Velg **Google Disk**.

![Google Disk-skjema](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Logg på med Google (Drive)** → valgfri mappe-URL/Bla gjennom → aktiver → lagre → **Synkroniser nå**.

Manuell **Synkroniser nå** for bilder er en full pass; bakgrunnssikkerhetskopiering behandler vanligvis **bare ventende** opplastinger etter en tidsplan.

### <a id="sync-behavior-notes"></a>Synkroniser atferdsnotater

- Etter appoppgradering kan du kort se **“Oppdaterer database etter oppgradering...”** (lokal synkroniserings-ID-utfylling).
- Hvis en synkronisering blir avbrutt, slår den neste **vellykkede** synkroniseringen sammen og reparerer eksterne faner.
- Feil: rødt sammendrag på synkroniseringskort + **!** i applinjen.

---

## <a id="fill-up-fuel"></a>Fyll (drivstoff)

Dette er **hjemmeskjermen** når du åpner appen.

### <a id="vehicle-selection-usually-automatic"></a>Valg av kjøretøy (vanligvis automatisk)

Du trenger **ikke** å velge kjøretøyet først. Når kjøretøyer har **landemerker** satt opp i Administrer kjøretøy, oppdager Fill **automatisk hvilket kjøretøy** fra dash-bildet etter at du fanger kilometertelleren. Du kan fortsatt åpne rullegardinmenyen **Kjøretøy** for å overstyre om nødvendig.

### <a id="aim-at-the-odometer"></a> Sikt på kilometertelleren

Hold deg i kilometertellermodus og ramme inn klyngen. Instruksjon: *Sikt mot kilometerteller. Trykk på lukkeren for å ta bilder.*

![Fyll — live kamera på kilometerteller (eksempel på strekbilde i forhåndsvisning)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Etter kilometertellerutløseren

OCR fyller **Odo** og prøver å matche kjøretøyet fra landemerker (gjennomgå begge om nødvendig). Hovedknappen blir **Prøv på nytt** for å ta bilder på nytt. Instruksjonen oppsummerer lesingen.

![Fyll — OCR-resultat for kilometerteller](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Pumpemodus (kostnad og volum)

1. Trykk på **↕** for å bytte til pumpemodus: *Sikt mot pumpevisning (kostnad/volum). Trykk på lukkeren.*
2. Registrer pumpetotalene. Kostnads- og volumfelt fylles ut; bruk **↔** hvis de byttes.
3. Trykk på valuta eller **G/L** om nødvendig, deretter **Lagre** (disk). Tomme felt utgjør en **delvis utfylling** (fortsatt tillatt).

![Fyll — pumpe forhåndsvisning / totaler (eksempel pumpebilde i forhåndsvisning)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Du holder deg på Fill til neste stopp (feltene fjernes etter lagring). Arbeid fullt **offline**; synkronisering kjører senere i bakgrunnen når den er konfigurert.

### <a id="manual-entry-no-camera--bad-ocr"></a>Manuell inntasting (ingen kamera / dårlig OCR)

1. Trykk på **Odo**, **kostnad** eller **volum** og typeverdier (stående bruker systemtastaturet; liggende bruker et skjermtastatur).
2. Velg eller bekreft **Kjøretøy** hvis autodeteksjon ikke kjørte.
3. Lagre som ovenfor.

### <a id="modes-and-borders"></a>Modi og grenser

- **Grønn kant** rundt kjøretøy+odo → fange/redigere kilometerteller.
- **Grønn kant** rundt kostnad+volum → pumpemodus.
- **Lagre** forblir deaktivert til et kjøretøy velges og minst én av odo/kostnad/volum har data, og OCR fortsatt ikke kjører.

Tips på skjermen (under instruksjonslinjen): *Lukker = fangst · Disk = lagre · ↕ = odo/pumpe-modus · ↔ = bytte kostnad/volum.*

---

## <a id="expenses"></a>Utgifter

### <a id="new-expense"></a>Ny utgift

Meny → **Ny utgift**.

![Nytt utgiftsskjema og kamerakontroller (eksempel dash-bilde i kamerabåndet når det er tomt)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Lagre** (diskett), **lukker** (kvitteringsbilde) eller **galleri** (velg bilde).
2. Fyll **Dato**, **Kjøretøy**, **Leverandør**, **Beskrivelse**, **Beløp** (kan trykkes på valutasymbol), **Kategori**, valgfri **Kilometerteller**.
3. Kvitteringer på flere sider: ta opp flere sider hvis brukergrensesnittet tilbyr personsøking (side 0 er den primære kvitteringen).
4. **Lagre** for å lagre (lokal først; sikkerhetskopiering av bilder og regnearksynkronisering skjer i bakgrunnen når konfigurert).

### <a id="expense-list"></a>Utgiftsliste

Meny → **Rapporter** → **Utgiftsliste** — bla gjennom tidligere ikke-drivstoffutgifter; åpne et element for å redigere.

![Utgiftsliste](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Rediger utgift

Åpne en rad fra listen. Riktig leverandør, beløp, kategori, kjøretøy og beskrivelse. Hvis kvitteringen kun er i bildesikkerhetskopiering (ingen lesbar lokal fil), bruk **Hent bilde fra arkiv** når den vises (fungerer på tvers av konfigurerte fotodestinasjoner).

![Rediger utgift](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Start turen

Meny → **Start tur** (etter Fyll i skuffen). Registrer eller skriv inn kilometerteller, velg turtype, lagre med **disk**-ikonet. **Stopp** er en snarvei for Personlig nå på den holdte GPS-posisjonen. Bruk **ⓘ** for kontrollpåminnelser.

![Start turen](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Turstarter lagres som drivstoffrader med en **Trip Type** (ikke normal fylling). De vises under **Rapporter → Reisemil**, ikke under Drivstoffhistorikk.

---

## <a id="reports"></a>Rapporter

Meny → **Rapporter** åpner produkthuben (oppsummering av alle tider + katalogkort). Dette er den eneste produktrapportoverflaten - det er ingen separat "Rapporter og diagrammer"-skuffelement.

![Rapportsenter](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Åpne et kort for kjøretøymodus (**Alle / Hver / Enkelt**), periodefiltre, diagrammer og del (**TEKST / CSV / PDF**). Øverste stolpe på rapporteringsbarn: **☰ + ←** (og **ⓘ** når registrert).

### <a id="time-based-reports"></a>Tidsbaserte rapporter

Hovedkartkortet. Valgfrie beregninger (mpg, volum/distanse som G/mi, enhetspris som $/G, kostnad/distanse, månedlige $, reisemil, reise % etter type) med **Glatt** søppelkasser og **uavhengige Y-skalaer** (økonomi til venstre; penger og reisefamilier til høyre).

![Tidsbaserte rapporter](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Tidsbaserte rapporter – rullede beregninger](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Detaljer om økonomi matematikk: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Fyllhistorikk vs drivstoffhistorikk

- **Rapporter → Fyllhistorikk** — kronologiske fyllinger for rapportfiltrene (**kun fyller**; ingen tur starter).

![Fyll historierapport](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Brivstoffhistorikk** (hvis den finnes i navigasjonen til bygningen din) - fyllebeholdning per kjøretøy, fylles også kun; trykk på en rad for å redigere.

### <a id="trip-miles"></a>Tur miles

**Rapporter → Turmil** — miles etter type, diagrammer og en kronologisk **turstart-/segmentliste**. Trykk på en skikkelig start for å åpne **Rediger fyll** for den raden.

![Tur miles](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Rediger fyll

Åpne en fylling fra Fyllhistorikk, Drivstoffhistorikk eller Tripmil. Layout: kjøretøy og kilometerteller, **valuta før kostnad**, volum, notater. Turtype vises bare når raden er en turstart. Plasseringen har et sammendrag pluss **Plasseringsdetaljer**. Mangler lokalt bilde med skyidentitet: **Hent bilde fra arkiv**.

![Rediger fyll — Hent fra arkiv når lokalt bilde mangler](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Andre katalogkort inkluderer utgifter etter kategori, kjøretøysammendrag og utgiftsliste.

Penger bruker hver rads valuta når den er angitt. Totaler for blandet valuta viser **subtotaler per valuta** (ingen stille valutakonvertering).

---

## <a id="syncing"></a>Synkroniserer

Meny → **Synkronisering** er knutepunktet for regneark- og bildemål (ikke bare begravet under Innstillinger).

![Synkroniseringshub](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Kort for **Synkronisering av regneark** og **Fotosikkerhetskopiering** med kort status, **Synkronisering** for den typen, og **›** til destinasjonslisten.
- Åpne en destinasjon for **Testforbindelse** og **Synkroniser nå (denne destinasjonen)** / alt konfigurert.
- Feil **Detaljer** og den røde **!** i tittellinjen lander her.
– Trinn-for-trinn oppsett av Google Regneark og Disk: [Sikkerhetskopier og synkronisering av flere enheter](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Innstillinger (lokale preferanser)

Meny → **Innstillinger**.

![Innstillinger](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Flere innstillinger (rullet)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

For destinasjoner, foretrekk **Meny → Synkronisering**. Innstillinger kan fortsatt vise sammendragsrader som åpner de samme listene.

### <a id="local-preferences-common"></a>Lokale preferanser (vanlig)

- **Lagre bilder for drivstoffkvittering** / **Lagre utgiftsbilder lokalt** — hold bilder på enheten (kan be om tillatelse til bilder).
- **Spill av lukkerlyd**
- **Valuta** / **Volumenhet** — appstandarder (system eller eksplisitt). Endring av volumenhet med eksisterende drivstoffdata kan tilby en konverteringsdialog.
- **Mørk modus**
- **Oppsetttips** — gjenåpne førstegangskjøretøy / synkroniseringsveiledninger.
- **Debug Fill** / **Vis eksperimentskjermer (dev)** — avansert; la være til daglig bruk. Eksperimentskjermer er ikke dokumentert her.

CSV **eksport/import** (ZIP of Vehicles / Expenses / Fuel-fanene) er tilgjengelig fra Innstillinger når det tilbys av gjeldende versjon.

---

## <a id="help--about"></a>Hjelp og om

![Hjelp](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Om](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Hjelp** — hurtigstart på enheten, oppsettveiledninger, lenke til denne håndboken, selvvertsoppsettindeks.
- **Om** — versjon, lisenser, GitHub, denne håndboken (kombinert offline + online HTML når publisert).

---

## <a id="related-docs"></a>Relaterte dokumenter

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — kort daglig guide
- [selvvert/INDEX.md](../reference/self-host/INDEX.md) — selvvertert bilde/tabelloppsett
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — slå sammen, gjenoppretting, duplikater
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — detaljer om økonomiberegninger
