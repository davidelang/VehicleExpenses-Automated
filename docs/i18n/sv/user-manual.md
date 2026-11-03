# <a id="vehicle-expenses-automated--user-manual"></a>Automatiska fordonskostnader — Användarmanual

> **Redigera källa (Markdown).** Webbläsare och läsaren i appen öppnar den **renderade HTML-koden**:
> - Webb: [`docs/user-manual/index.html`](index.html) (återskapa med `./scripts/render-user-manual.sh`)
> - App: Hjälp / Om → fullständig manual (medföljande HTML + skärmdumpar)
>
> Peka inte slutanvändare på obearbetade `.md`-webbadresser – webbläsare visar endast vanlig text.

Kamera-först spårning för bränslepåfyllning och fordonskostnader, med valfri synkronisering och backup för flera enheter under **dina** molnkonton.

Detta är den **fullständiga manualen** (skärmdumpar + varje steg). På telefonen är **Meny → Hjälp** en kortare guide för att komma igång.

**Täcks inte här:** Importera gamla bilder, inriktningsexperiment och pumpexperiment (utvecklare/avancerade verktyg).

---

## <a id="table-of-contents"></a> Innehållsförteckning

1. [Vad du behöver](#what-you-need)
2. [Ikoner i ett ögonkast](#icons-at-a-glance)
3. [Öppna menyn](#open-the-menu)
4. [Förstagångsinstallation: Hantera fordon](#first-time-setup-manage-vehicles)
5. [Säkerhetskopiering och synkronisering av flera enheter](#backups-and-multi-device-sync)
6. [Fylla (bränsle)](#fill-up-fuel)
7. [Starta resan](#start-trip)
8. [Utgifter](#expenses)
9. [Rapporter](#reports)
10. [Inställningar (lokala inställningar)](#settings-local-preferences)
11. [Synkronisering](#syncing)
12. [Hjälp och om](#help--about)
13. [Relaterade dokument](#related-docs)

---

## <a id="what-you-need"></a>Vad du behöver

- Android-telefon eller surfplatta.
- För bästa OCR: en tydlig vy av din **instrumentpanels vägmätare** och **pumpsummor** (eller skriv siffrorna för hand).
- Valfritt: konton **du kontrollerar** för kalkylbladsdata och/eller fotosäkerhetskopiering (se [Säkerhetskopiering och synkronisering av flera enheter](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a> Ikoner i ett ögonkast

Dessa visas på huvudskärmarna. Att känna till dem sparar mycket jakt.

| Var | Ikon / kontroll | Vad det gör |
|-------|----------------|-------------|
| Översta bar | **☰ Meny** (hamburgare) | Öppnar navigeringslådan |
| Översta bar | **ⓘ** (sidahjälp) | Kort hjälp för den **aktuella** sidan (bredvid menyn när tillgänglig) |
| Översta bar | **`?N`** (gul) | Väntande frågor om importgranskning — öppnar Importgranskning |
| Översta bar | **!** (röd) | Ett kalkylark eller fotomål misslyckades nyligen — öppna **Synkronisera** för att fixa |
| Översta bar | **☰ + ←** | Rapportera barn och utgifter lista visa **meny och tillbaka** tillsammans; Rapporthubb är endast meny |
| Inställningar / bränsleredigering | **←** | Tillbaka (inställningar kalkylblad/foto och bränsleredigering förblir bakåtfokuserad) |
| Fyll | **Vit cirkel** (slutare) | Fånga vägmätare eller pumpdisplay för OCR |
| Fyll | **Disk / Spara** | Spara påfyllningen (behöver ett fordon och minst en av odo / volym / kostnad) |
| Fyll | **↕ pilar** (lägesomkopplare) | Växla **vägmätarläge** vs **pumpläge (kostnad/volym)**. Grön kant markerar den aktiva fältgruppen |
| Fyll | **↔ pilar** (mellan kostnad och volym) | Byt kostnad och volym om OCR placerar dem i fel fält |
| Fyll | **Zooma 1x / …** | Kamerazoomförhållanden när objektivet stöder dem |
| Fyll (efter fångst) | **Uppdatera** på huvudknappen | Släng förhandsgranskningen och återgå till livekamera |
| Fyll (under bearbetning) | **X** på huvudknappen | Avbryt pågående inspelning/OCR |
| Kostnad | **Spara** | Spara kostnaden |
| Kostnad | **Slutarcirkel** | Ta ett kvittofoto |
| Kostnad | **Galleri** | Välj en kvittobild från biblioteket |
| Kostnad | **Ta om** | Rensa aktuellt kvittofoto och fotografera igen |
| Kostnad / Hantera fordon | **+ / −** FABs | Zooma bilden förhandsvisning |
| Dialogrutan Landmärken | **Redigera OCR** ​​| Korrigera eller lägg till landmärke som motorerna missade |
| Kalkylblad / Fotoformulär | ** sökning** | Bläddra i Google Drive efter ett ark eller en mapp (efter inloggning) |

Valutasymboler på kostnadsfält och **G/L** på volymfält går att trycka på: öppna en liten meny för att ändra valuta eller gallon kontra liter för den posten.

---

## <a id="open-the-menu"></a>Öppna menyn

1. Tryck på **☰** längst upp till vänster.
2. Välj en sida.

![Navigeringslåda](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Huvudlåda:** Fyll på · Starta resa · Hantera fordon · Ny utgift · **Rapporter** · Inställningar · Synkronisering · Hjälp · Om.

**Experimentlåda** (Inställningar → Visa experimentskärmar): Justeringsexperiment · Pumpexperiment · **Importera gamla bilder**.

**Via Reports hub (inte huvudlådan):** Kostnadslista · Fyll historik.

---

## <a id="first-time-setup-manage-vehicles"></a>Förstagångsinstallation: Hantera fordon

OCR och **automatisk fordonsmatchning** fungerar bäst efter att du registrerat varje fordon med ett **referensfoto på instrumentbrädan**, kört **Discovery**, ställer in vägmätaren från ett paddelfrö och sparar landmärken för det instrumentbrädan.

### <a id="open-manage-vehicles"></a>Öppna Hantera fordon

Meny → **Hantera fordon**. Välj ett fordon (eller **Lägg till nytt fordon**).

![Hantera fordon med referensstreck, grödor och upptäckt](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Lägg till eller redigera ett fordon

1. Öppna rullgardinsmenyn **Fordon** → välj ett fordon eller **Lägg till nytt fordon**.
2. Ta eller välj ett tydligt **referensstreckfoto** (helt instrumentkluster, väl upplyst, telefonen ungefär kvadratisk). Använd **Ta foto** eller **Galleri**.
3. Tryck på **Kör upptäckt** — paddeldetektering placerar gula **frön** på fotot (landmärken använder samma `"ML Kit"` JSON som tidigare).
4. Tryck på **Välj Odo**, tryck på fröet på siffrorna, justera handtagen och sedan på **Spara**.
5. **Redigera Odo** justerar en ruta som redan finns där. **Clear Odo** tar bort det och knappen läser sedan **Select Odo**.
6. Granska med **Visa landmärken**. Använd **Redigera OCR** ​​för att åtgärda felläsningar eller **lägga till** text som missats.
7. Fyll i **Fordonets namn** (obligatoriskt), plus märke/modell/år/skylt som du vill.
8. Tryck på **Skapa fordon** eller **Spara ändringar** (kräver namn + referensfoto för ett nytt fordon).

![Lägg till nya fordonsformulärfält](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Landmärken: fixa vad Discovery missade

Efter **Visa landmärken**, bläddra i listan och korrigera värden. Motorer missar ibland små siffror (till exempel en klocka **60** längst ner till höger i klustret). Använd **Redigera OCR** ​​för att lägga till eller fixa dem så att fordonsidentiteten förblir pålitlig.

![Landmärken dialog — granska och redigera OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a> Skriver utan ett perfekt foto

Du kan fortfarande använda appen genom att välja ett fordon och **skriva** vägmätare, volym och kostnad vid fyllning — OCR är valfritt för varje fält. Galleriimport fungerar för referensstreckbilden när du föredrar att inte fotografera i appen.

**Tips:** Efter synkronisering av kalkylblad finns fordonsdefinitioner (grödor, landmärken) i den lokala databasen – du behöver inte öppna Hantera fordon för fyllning igen för att använda dem.

---

## <a id="backups-and-multi-device-sync"></a>Säkerhetskopiering och synkronisering av flera enheter

Appen är byggd så att **flera telefoner eller surfplattor kan dela samma flottadata**, och så att du kan hålla en **kopia av dina data och foton utanför enheten**. Det görs med destinationer **du** konfigurerar under **dina** konton eller **dina** servrar som är värdar för sig själva – inte ett företagsdrivet "Vehicle Expenses-moln" som andra kan se.

### <a id="what-runs-where"></a>Vad går vart

| Snäll | Vad den lagrar | Typisk användning |
|-------|----------------|-------------|
| **Kalkylblad / tabellsynkronisering** | Fordon, bränslepåfyllningar, utgifter (rader och flikar) | Sammanfoga flera enheter + strukturerad säkerhetskopiering |
| **Fotobackup** | Binära bilder (streck/pump/kvitto/referensbilder) | Fotosäkerhetskopiering + återställ saknade filer |

Du kan konfigurera **flera destinationer** av varje slag (soft cap per typ). Manuell **Synkronisera nu** och **bakgrundsarbetare kör de aktiverade.

### <a id="offline-first"></a>Offline först

- **Inget nätverk krävs** för att lägga till en påfyllning, kostnad eller kvitto. Allt sparas **lokalt först**.
- När nätverket är tillgängligt körs synkronisering och fotosäkerhetskopiering som **bakgrundsuppgifter** (enligt ett schema som du ställer in och när du trycker på **Synkronisera nu**). Fel visas som röd text under Inställningar-raderna och ett **!** i appens namnlist.

### <a id="your-accounts-only"></a> Endast dina konton

Inloggning och tokens stannar på enheten för de leverantörer du väljer (Google, Microsoft, S3-nycklar, webbadresser som är värdar och så vidare). Destinationer är under **full kontroll av användaren** — ditt Google-konto, din OneDrive, din MinIO-hink, din EtherCalc-värd, etc. Ingenting delas med andra fordonskostnader-användare via en delad backend.

### <a id="supported-targets--data-spreadsheet--tabular"></a> Mål som stöds — data (kalkylblad/tabell)

Konfigurerad under **Meny → Synkronisering → Synkronisering av kalkylblad** (kan även nås från inställningsöversiktsrader). Förstklassiga väljaralternativ:

| Mål | Anteckningar |
|--------|--------|
| **Google Kalkylark** | Vanlig standard; flikar för fordon, utgifter och bränsle per fordon |
| **Excel** | Microsoft-arbetsbok via Graph / OneDrive-liknande bindning |
| **EtherCalc** | Egen värd för samarbetande kalkylbladsrum |
| **Andra →** implementerade backends | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Uppskjuten / inte huvudlös ännu (listad under Annat men inte fullt implementerat): OnlyOffice, Collabora. Se även [egenvärdindex](reference/self-host/INDEX.md).

CSV **export/import** (ZIP med samma fliklayout) är tillgängligt från Inställningar som en bärbar säkerhetskopia, oberoende av livesynkronisering.

### <a id="supported-targets--photos-image-backup"></a> Mål som stöds — foton (bildsäkerhetskopiering)

Konfigurerad under **Meny → Synkronisering → Fotosäkerhetskopiering** (även från inställningsöversiktsrader):

| Mål | Anteckningar |
|--------|--------|
| **Google Drive** | Mapp du väljer (bläddra eller klistra in URL) |
| **OneDrive** | Microsoft-konto + sökvägsprefix |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO och andra S3-kompatibla slutpunkter |
| **Övrigt** | rclone-stödd lagring (t.ex. WebDAV, SFTP och andra kurerade fjärrkontroller tillgängliga i appväljaren) |

Ställ in cheatsheets för foto- och tabellmål: [self-host index](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Beteende för flera enheter (kort)

- Rader sammanfogas med **Synkroniserings-ID** med **sista-skriv-vinster** på **Uppdaterade** tidsstämplar.
- Borttagningar är mjuka; en nyare redigering på en annan enhet kan återställa en rad.
- Om du anger **samma fyllning två gånger** på två enheter skapas **två rader** — radera extra när du märker det.
- Mer detaljer: [Synkroniseringsbeteendeanteckningar](#sync-behavior-notes) och [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Exempel: lägg till Google Sheets (data)

1. **Meny → Synkronisering → Synkronisering av kalkylblad** (eller Inställningar → Synkronisering av kalkylblad).

![Synklista för kalkylark](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Tryck på **Lägg till kalkylarksmål**.

![Leverantörsväljare](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Välj **Google Sheets**.

![Google Sheets-formulär](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Logga in med Google** en gång — den inloggningen låter dig också surfa. Visningsnamn → klistra in en **Arkwebbadress** (fortfarande giltig) eller ****** bläddra. Bläddra öppnas på **Alla och delade** så att du kan välja ett befintligt kalkylark som du äger eller som delas med dig. Sedan schemalägg alternativ → aktivera → spara.
5. **Synkronisera nu** en gång för att skapa/uppdatera flikar: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Exempel: lägg till Google Drive (foton)

1. **Meny → Synkronisering → Fotosäkerhetskopiering** (eller Inställningar → Fotosäkerhetskopiering).

![Foto backup lista](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Tryck på **Lägg till fotodestination**.

![Fotoleverantörsväljare](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Välj **Google Drive**.

![Google Drive-formulär](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Logga in med Google (Drive)** → valfri mappadress/bläddra → aktivera → spara → **Synkronisera nu**.

Manuell **Synkronisera nu** för foton är ett helt pass; bakgrundssäkerhetskopiering behandlar vanligtvis **endast väntande** uppladdningar enligt ett schema.

### <a id="sync-behavior-notes"></a>Synkronisera beteendeanteckningar

- Efter appuppgradering kan du kort se **“Uppdaterar databasen efter uppgradering...”** (lokal sync-id-återfyllning).
- Om en synkronisering avbryts, slås nästa **lyckade** synkronisering samman igen och reparerar fjärrflikar.
- Misslyckanden: röd sammanfattning på synkroniseringskort + **!** i appfältet.

---

## <a id="fill-up-fuel"></a>Fyll (bränsle)

Detta är **startskärmen** när du öppnar appen.

### <a id="vehicle-selection-usually-automatic"></a>Val av fordon (vanligtvis automatiskt)

Du behöver **inte** välja fordonet först. När fordon har **landmärken** inställda i Hantera fordon, upptäcker Fill automatiskt vilket fordon** från streckbilden efter att du har tagit vägmätaren. Du kan fortfarande öppna rullgardinsmenyn **Fordon** för att åsidosätta om det behövs.

### <a id="aim-at-the-odometer"></a>Sikta på vägmätaren

Stanna i vägmätarläge och rama in klustret. Instruktion: *Sikta på vägmätaren. Tryck på slutaren för att fånga.*

![Fyll — livekamera på vägmätaren (exempel på streckbild i förhandsvisning)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Efter vägmätarslutaren

OCR fyller **Odo** och försöker matcha fordonet från landmärken (granska båda om det behövs). Huvudknappen blir **Försök igen** för att fotografera igen. Instruktionen sammanfattar läsningen.

![Fyll — OCR-resultat för vägmätaren](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Pumpläge (kostnad och volym)

1. Tryck på **↕** för att växla till pumpläge: *Sikta på pumpens display (kostnad/volym). Tryck på slutaren.*
2. Fånga pumpsumman. Kostnads- och volymfält fyller; använd **↔** om de byts ut.
3. Tryck på valuta eller **G/L** om det behövs och sedan på **Spara** (diskett). Tomma fält gör en **partiell fyllning** (fortfarande tillåtet).

![Fyll — pumpförhandsgranskning/summor (exempel på pumpbild i förhandsgranskning)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Du stannar på Fill för nästa stopp (fälten rensas efter spara). Arbeta helt **offline**; synkronisering körs senare i bakgrunden när den är konfigurerad.

### <a id="manual-entry-no-camera--bad-ocr"></a>Manuell inmatning (ingen kamera / dålig OCR)

1. Tryck på **Odo**, **kostnad** eller **volym** och typvärden (stående använder systemets tangentbord, liggande använder en knappsats på skärmen).
2. Välj eller bekräfta **Fordon** om automatisk avkänning inte kördes.
3. Spara enligt ovan.

### <a id="modes-and-borders"></a>Lägen och kanter

- **Grön kant** runt fordon+odo → fånga/redigera vägmätare.
- **Grön kant** runt kostnad+volym → pumpläge.
- **Spara** förblir inaktiverat tills ett fordon väljs och minst en av odo/kostnad/volym har data och OCR fortfarande inte körs.

Tips på skärmen (under instruktionsraden): *Slutare = fånga · Disk = spara · ↕ = odo/pumpläge · ↔ = byta kostnad/volym.*

---

## <a id="expenses"></a>Utgifter

### <a id="new-expense"></a>Ny utgift

Meny → **Ny utgift**.

![Nytt utgiftsformulär och kamerakontroller (exempel streckbild i kamerabandet när det är tomt)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Spara** (diskett), **slutare** (kvittofoto) eller **galleri** (välj bild).
2. Fyll i **Datum**, **Fordon**, **Leverant**, **Beskrivning**, **Belopp** (kan tryckas på valutasymbol), **Kategori**, valfri **Kilometermätare**.
3. Flersidiga kvitton: fånga ytterligare sidor om användargränssnittet erbjuder personsökning (sida 0 är det primära kvittot).
4. **Spara** för att lagra (lokal först; fotosäkerhetskopiering och kalkylarkssynkronisering sker i bakgrunden när de konfigureras).

### <a id="expense-list"></a>Utgiftslista

Meny → **Rapporter** → **Utgiftslista** — bläddra bland tidigare icke-bränsleutgifter; öppna ett objekt för att redigera.

![Utgiftslista](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Redigera kostnad

Öppna en rad från listan. Rätt leverantör, belopp, kategori, fordon och beskrivning. Om kvittot endast finns i fotobackup (ingen läsbar lokal fil), använd **Hämta bild från arkiv** när det visas (fungerar över konfigurerade fotodestinationer).

![Redigera kostnad](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Starta resan

Meny → **Starta resa** (efter Fyll i lådan). Fånga eller ange vägmätare, välj resetyp, spara med ikonen **disk**. **Stopp** är en genväg för Personal nu på den hållna GPS-platsen. Använd **ⓘ** för kontrollpåminnelser.

![Starta resan](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Tripsstarter lagras som bränslerader med en **Trip Type** (ej normal fyllning). De visas under **Rapporter → Resemil**, inte under Bränslehistorik.

---

## <a id="reports"></a>Rapporter

Meny → **Rapporter** öppnar produkthubben (sammanfattning av alla tider + katalogkort). Detta är den enda produktrapportytan – det finns ingen separat "Rapporter och diagram"-låda.

![Rapporthubb](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Öppna ett kort för fordonsläge (**Alla / Varje / Enstaka**), periodfilter, diagram och dela (**TEXT / CSV / PDF**). Översta fältet för anmälan av barn: **☰ + ←** (och **ⓘ** när de är registrerade).

### <a id="time-based-reports"></a>Tidsbaserade rapporter

Huvudsjökortskortet. Valfria mätvärden (mpg, volym/avstånd som G/mi, enhetspris som $/G, kostnad/sträcka, månatliga $, resa mil, resa i % per typ) med **Släta** papperskorgar och **oberoende Y-skalor** (ekonomi till vänster; pengar och resefamiljer till höger).

![Tidsbaserade rapporter](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Tidsbaserade rapporter — rullade mätvärden](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Detaljer om ekonomi matematik: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Fyllhistorik vs bränslehistorik

- **Rapporter → Fyllhistorik** — kronologiska fyllningar för rapportfiltren (**endast fyllningar**; ingen resa börjar).

![Fyll historikrapport](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Bränslehistorik** (om det finns i din byggnads navigering) - fyllning per fordon, fyller också endast; tryck på en rad för att redigera.

### <a id="trip-miles"></a> Resemil

**Rapporter → Resemil** — miles per typ, diagram och en kronologisk **lista för resestart/segment**. Tryck på en riktig start för att öppna **Redigera fyllning** för den raden.

![Resemil](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Redigera fyllning

Öppna en påfyllning från Fyllningshistorik, Bränslehistorik eller Tripmil. Layout: fordon och vägmätare, **valuta före kostnad**, volym, anteckningar. Resetyp visas endast när raden är en resstart. Platsen har en sammanfattning plus **Platsinformation**. Lokalt foto med molnidentitet saknas: **Hämta bild från arkiv**.

![Redigera fyllning — Hämta från arkiv när lokalt foto saknas](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Andra katalogkort inkluderar utgifter per kategori, fordonssammanfattning och utgiftslista.

Pengar använder varje rads valuta när den är inställd. Summor i blandad valuta visar **subtotal per valuta** (ingen tyst valutaomvandling).

---

## <a id="syncing"></a>Synkronisera

Meny → **Synkronisering** är navet för kalkylarks- och fotodestinationer (inte bara begravd under Inställningar).

![Synkroniseringshubb](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Kort för **Kalkylarkssynkronisering** och **Fotosäkerhetskopiering** med kort status, **Synkronisering** för den typen och **›** till destinationslistan.
- Öppna en destination för **Testa anslutning** och **Synkronisera nu (denna destination)** / allt konfigurerat.
- Misslyckande **Detaljer** och det röda **!** i titelfältet landar här.
- Steg-för-steg-konfiguration av Google Kalkylark och Drive: [Säkerhetskopiering och synkronisering av flera enheter](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Inställningar (lokala inställningar)

Meny → **Inställningar**.

![Inställningar](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Fler inställningar (rullad)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

För destinationer, föredra **Meny → Synkronisering**. Inställningar kan fortfarande visa sammanfattningsrader som öppnar samma listor.

### <a id="local-preferences-common"></a>Lokala inställningar (vanligt)

- **Spara bränslekvittofoton** / **Spara utgiftsfoton lokalt** — spara bilder på enheten (kan begära fototillstånd).
- **Spela slutarljud**
- **Valuta** / **Volymenhet** — appens standardinställningar (system eller explicit). Att ändra volymenhet med befintliga bränsledata kan erbjuda en konverteringsdialog.
- **Mörkt läge**
- **Installationstips** — öppna självstudier för första körning av fordon/synkronisering igen.
- **Debug Fill** / **Visa experimentskärmar (dev)** — avancerat; lämna för dagligt bruk. Experimentskärmar dokumenteras inte här.

CSV **export/import** (ZIP för fordon / Kostnader / Bränsle flikar) är tillgängligt från Inställningar när det erbjuds av den aktuella versionen.

---

## <a id="help--about"></a>Hjälp & Om

![Hjälp](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Om](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Hjälp** — snabbstart på enheten, installationshandledningar, länk till den här handboken, index för självvärdinställning.
- **Om** — version, licenser, GitHub, denna manual (paketerad offline + online HTML när den publiceras).

---

## <a id="related-docs"></a>Relaterade dokument

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — kort vardagsguide
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — self-hosted foto/tabellinställning
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — sammanfogning, återställning, dubbletter
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — ekonomimått detalj
