# <a id="vehicle-expenses-automated--user-manual"></a> Automatiserede køretøjsudgifter — Brugervejledning

> **Rediger kilde (Markdown).** Browsere og in-app-læseren åbner den **gengivede HTML**:
> - Web: [`docs/user-manual/index.html`](index.html) (gendan med `./scripts/render-user-manual.sh`)
> - App: Hjælp / Om → fuld manual (bundtet HTML + skærmbilleder)
>
> Ret ikke slutbrugere til rå `.md` URL'er – browsere viser kun almindelig tekst.

Kamera-første sporing til brændstofpåfyldning og køretøjsudgifter med valgfri synkronisering og backup af flere enheder under **dine** skykonti.

Dette er den **fulde manual** (skærmbilleder + hvert trin). På telefonen er **Menu → Hjælp** en kortere vejledning til at komme i gang.

**Ikke dækket her:** Importer gamle billeder, justeringsforsøg og pumpeeksperiment (udvikler/avancerede værktøjer).

---

## <a id="table-of-contents"></a> Indholdsfortegnelse

1. [Hvad du har brug for](#what-you-need)
2. [Ikoner på et øjeblik](#icons-at-a-glance)
3. [Åbn menuen](#open-the-menu)
4. [Førstegangsopsætning: Administrer køretøjer](#first-time-setup-manage-vehicles)
5. [Sikkerhedskopier og synkronisering af flere enheder](#backups-and-multi-device-sync)
6. [Fyld (brændstof)](#fill-up-fuel)
7. [Start tur](#start-trip)
8. [Udgifter](#expenses)
9. [Rapporter](#reports)
10. [Indstillinger (lokale præferencer)](#settings-local-preferences)
11. [Synkronisering](#syncing)
12. [Hjælp og om](#help--about)
13. [Relaterede dokumenter](#related-docs)

---

## <a id="what-you-need"></a>Hvad du har brug for

- Android telefon eller tablet.
- For den bedste OCR: et klart overblik over din **kilometertæller** og **pumpetotaler** (eller indtast tallene i hånden).
- Valgfrit: konti **du kontrollerer** til regnearksdata og/eller fotosikkerhedskopiering (se [Sikkerhedskopier og synkronisering af flere enheder](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a> Ikoner på et øjeblik

Disse vises på hovedskærmene. At kende dem sparer en masse jagt.

| Hvor | Ikon / kontrol | Hvad det gør |
|-------|----------------|--------------|
| Top bar | **☰ Menu** (hamburger) | Åbner navigationsskuffen |
| Top bar | **ⓘ** (sidehjælp) | Kort hjælp til den **aktuelle** side (ved siden af ​​menuen, når den er tilgængelig) |
| Top bar | **`?N`** (gul) | Afventende spørgsmål om importgennemgang — åbner Importgennemgang |
| Top bar | **!** (rød) | Et regneark eller fotodestination mislykkedes for nylig — åbn **Synkroniserer** for at rette |
| Top bar | **☰ + ←** | Rapport børn og Udgifter liste vis **menu og tilbage** sammen; Rapporthub er kun menu |
| Indstillinger / brændstofredigering | **←** | Tilbage (indstillingsregneark/foto og brændstofredigering forbliver tilbagefokuseret) |
| Fyld | **Hvid cirkel** (lukker) | Optag kilometertæller eller pumpedisplay til OCR |
| Fyld | **Disk / Gem** | Gem påfyldningen (kræver et køretøj og mindst én af odo / volumen / omkostninger) |
| Fyld | **↕ pile** (tilstandsskifter) | Skift **kilometertællertilstand** vs. **pumpetilstand (pris/volumen)**. Grøn kant fremhæver den aktive feltgruppe |
| Fyld | **↔ pile** (mellem omkostninger og volumen) | Skift pris og volumen, hvis OCR placerer dem i de forkerte felter |
| Fyld | **Zoom 1x / …** | Kameraets zoomforhold, når objektivet understøtter dem |
| Fyld (efter fangst) | **Opdater** på hovedknap | Kassér forhåndsvisning og vend tilbage til live kamera |
| Fyld (under behandling) | **X** på hovedknappen | Annuller igangværende optagelse/OCR |
| Udgift | **Gem** | Spar udgiften |
| Udgift | **Lukkercirkel** | Tag et kvitteringsbillede |
| Udgift | **Galleri** | Vælg et kvitteringsbillede fra biblioteket |
| Udgift | **Gentag** | Ryd det aktuelle kvitteringsbillede og skyd igen |
| Udgifter / Administrer køretøjer | **+ / −** FAB'er | Zoom billedet forhåndsvisning |
| Dialogboksen Landemærker | **Rediger OCR** ​​| Ret eller tilføj skelsættende tekst, som motorerne gik glip af |
| Regneark / fotoformularer | ** søgning** | Gennemse Google Drev efter et ark eller en mappe (efter login) |

Valutasymboler på prisfelter og **G/L** på volumenfelter kan trykkes: Åbn en lille menu for at ændre valuta eller gallons vs. liter for den post.

---

## <a id="open-the-menu"></a>Åbn menuen

1. Tryk på **☰** øverst til venstre.
2. Vælg en side.

![Navigationsskuffe](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Hovedskuffe:** Fyld · Start tur · Administrer køretøjer · Ny udgift · **Rapporter** · Indstillinger · Synkronisering · Hjælp · Om.

**Eksperimentskuffe** (Indstillinger → Vis eksperimentskærme): Justeringseksperiment · Pumpeeksperiment · **Importér gamle billeder**.

**Via Reports hub (ikke hovedskuffe):** Udgiftsliste · Udfyld historik.

---

## <a id="first-time-setup-manage-vehicles"></a>Førstegangsopsætning: Administrer køretøjer

OCR og **automatisk køretøjsmatchning** fungerer bedst, når du har registreret hvert køretøj med et **referencebillede på instrumentbrættet**, kører **Discovery**, indstiller kilometertællerafgrøden fra et padle-frø og gemmer vartegn for den dash.

### <a id="open-manage-vehicles"></a>Åbn Administrer køretøjer

Menu → **Administrer køretøjer**. Vælg et køretøj (eller **Tilføj nyt køretøj**).

![Administrer køretøjer med referencestreg, afgrøder og opdagelse](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Tilføj eller rediger et køretøj

1. Åbn rullemenuen **Køretøj** → vælg et køretøj eller **Tilføj nyt køretøj**.
2. Tag eller vælg et tydeligt **referencebillede af bindestreg** (fuld instrumentklynge, godt oplyst, telefonen nogenlunde firkantet). Brug **Tag billede** eller **Galleri**.
3. Tryk på **Kør Discovery** — padle-detektion placerer gule **frø** på billedet (landemærker bruger den samme `"ML Kit"` JSON som før).
4. Tryk på **Vælg Odo**, tryk på frøet på cifrene, juster håndtagene og derefter **Gem**.
5. **Rediger Odo** justerer en boks, der allerede er der. **Clear Odo** fjerner den, og knappen viser derefter **Select Odo**.
6. Gennemgå med **Vis landemærker**. Brug **Rediger OCR** ​​til at rette fejllæsninger eller **tilføj** tekst, der blev glemt.
7. Udfyld **Køretøjsnavn** (påkrævet), plus mærke/model/årgang/plade, som du vil.
8. Tryk på **Opret køretøj** eller **Gem ændringer** (kræver navn + referencebillede for et nyt køretøj).

![Tilføj nye køretøjsformularfelter](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Landemærker: ret hvad Discovery gik glip af

Efter **Vis landemærker** skal du rulle listen og rette værdierne. Motorer savner nogle gange små cifre (for eksempel et ur **60** nederst til højre i klyngen). Brug **Rediger OCR** ​​til at tilføje eller rette dem, så køretøjets identitet forbliver pålidelig.

![Landmarks dialog — gennemse og rediger OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Takning uden et perfekt billede

Du kan stadig bruge appen ved at vælge et køretøj og **skrive** kilometertæller, volumen og pris på Fill — OCR er valgfrit for hvert felt. Galleri-import fungerer for reference-dash-billedet, når du foretrækker ikke at optage i appen.

**Tip:** Efter regnearkssynkronisering findes køretøjsdefinitioner (afgrøder, vartegn) i den lokale database - du behøver ikke genåbne Administrer køretøjer til Fill for at bruge dem.

---

## <a id="backups-and-multi-device-sync"></a>Sikkerhedskopier og synkronisering af flere enheder

Appen er bygget, så **flere telefoner eller tablets kan dele de samme flådedata**, og så du kan holde en **kopi af dine data og billeder væk fra enheden**. Det gøres med destinationer **du** konfigurerer under **dine** konti eller **dine** selvhostede servere - ikke en firmadrevet "Køretøjsudgifter-sky", som andre mennesker kan se.

### <a id="what-runs-where"></a>Hvad kører hvor

| Kind | Hvad det gemmer | Typisk brug |
|-------|----------------|-------------|
| **Synkronisering af regneark/tabel** | Køretøjer, brændstofpåfyldninger, udgifter (rækker og faner) | Fletning af flere enheder + struktureret sikkerhedskopiering |
| **Foto backup** | Binære billeder (dash/pumpe/kvittering/referencebilleder) | Foto backup + gendan manglende filer |

Du kan konfigurere **flere destinationer** af hver art (soft cap pr. type). Manuel **Synkroniser nu** og **baggrund** arbejdere kører de aktiverede.

### <a id="offline-first"></a>Offline først

- **Intet netværk er påkrævet** for at tilføje en opfyldning, udgift eller kvittering. Alt gemmes **lokalt først**.
- Når netværket er tilgængeligt, kører synkronisering og sikkerhedskopiering af fotos som **baggrundsopgaver** (i henhold til en tidsplan, du angiver, og når du trykker på **Synkroniser nu**). Fejl vises som rød tekst under indstillingsrækkerne og et **!** i appens titellinje.

### <a id="your-accounts-only"></a> Kun dine konti

Login og tokens forbliver på enheden for de udbydere, du vælger (Google, Microsoft, S3-nøgler, webadresser, der selv hoster, og så videre). Destinationer er under **fuld kontrol af brugeren** — din Google-konto, din OneDrive, din MinIO-bøtte, din EtherCalc-vært osv. Intet deles med andre køretøjsudgifter-brugere via en delt backend.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Understøttede mål — data (regneark/tabel)

Konfigureret under **Menu → Synkronisering → Regnearkssynkronisering** (også tilgængelig fra rækkerne med oversigt over Indstillinger). Førsteklasses plukkermuligheder:

| Mål | Noter |
|--------|--------|
| **Google Sheets** | Almindelig standard; faner for køretøjer, udgifter og brændstof pr. køretøj |
| **Excel** | Microsoft projektmappe via Graph / OneDrive-stil binding |
| **EtherCalc** | Selvværtede kollaborative regnearksrum |
| **Andre →** implementerede backends | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Udskudt / endnu ikke hovedløst (opført under Andet, men ikke fuldt implementeret): OnlyOffice, Collabora. Se også [selvværtsindeks](reference/self-host/INDEX.md).

CSV **eksport/import** (ZIP med samme fanelayout) er tilgængelig fra Indstillinger som en bærbar backup, uafhængig af live-synkronisering.

### <a id="supported-targets--photos-image-backup"></a>Understøttede mål — fotos (billedbackup)

Konfigureret under **Menu → Synkronisering → Billedsikkerhedskopiering** (også fra rækker med oversigt over Indstillinger):

| Mål | Noter |
|--------|--------|
| **Google Drev** | Mappe du vælger (gennemse eller indsæt URL) |
| **OneDrive** | Microsoft-konto + stipræfiks |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO og andre S3-kompatible slutpunkter |
| **Andet** | rclone-understøttet lagring (f.eks. WebDAV, SFTP og andre kurerede fjernbetjeninger tilgængelige i in-app-vælgeren) |

Opsæt snydeark til selvhostede foto- og tabelmål: [selvværtsindeks](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Multi-enhedsadfærd (kort)

- Rækker flettes efter **Synkroniserings-id** med **sidste skrivning-vinder** på **Opdateret** tidsstempler.
- Sletninger er bløde; en nyere redigering på en anden enhed kan gendanne en række.
- Indtastning af **samme fyld to gange** på to enheder skaber **to rækker** — slet det ekstra, når du bemærker det.
- Flere detaljer: [Synkroniseringsadfærdsnotater](#sync-behavior-notes) og [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Eksempel: tilføje Google Sheets (data)

1. **Menu → Synkronisering → Synkronisering af regneark** (eller Indstillinger → Synkronisering af regneark).

![Synkroniseringsliste for regneark](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Tryk på **Tilføj regnearksdestination**.

![Udbydervælger](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Vælg **Google Sheets**.

![Google Sheets-formular](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Log ind med Google** én gang — det login giver dig også mulighed for at browse. Vist navn → indsæt en **Arkwebadresse** (stadig gyldig) eller ****** gennemse. Gennemse åbner på **Alle og delte**, så du kan vælge et eksisterende regneark, du ejer, eller som deles med dig. Derefter planlægge muligheder → aktiver → gem.
5. **Synkroniser nu** én gang for at oprette/opdatere faner: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Eksempel: tilføje Google Drev (billeder)

1. **Menu → Synkronisering → Billedsikkerhedskopiering** (eller Indstillinger → Billedsikkerhedskopiering).

![Foto backup liste](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Tryk på **Tilføj fotodestination**.

![Fotoudbydervælger](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Vælg **Google Drev**.

![Google Drev-formular](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Log ind med Google (Drev)** → valgfri mappe-URL/gennemse → aktiver → gem → **Synkroniser nu**.

Manuel **Synkroniser nu** for billeder er en fuld adgang; Background backup behandler typisk **afventende** uploads efter en tidsplan.

### <a id="sync-behavior-notes"></a>Synkroniser adfærdsnoter

- Efter app-opgradering kan du kort se **"Opdaterer database efter opgradering..."** (lokal sync-id-udfyldning).
- Hvis en synkronisering afbrydes, fusionerer den næste **vellykkede** synkronisering igen og reparerer fjernfaner.
- Fejl: rød oversigt på synkroniseringskort + **!** i app-bjælken.

---

## <a id="fill-up-fuel"></a>Fyld (brændstof)

Dette er **startskærmen**, når du åbner appen.

### <a id="vehicle-selection-usually-automatic"></a>Valg af køretøj (normalt automatisk)

Du behøver **ikke** at vælge køretøjet først. Når køretøjer har **vartegn** sat op i Administrer køretøjer, registrerer Fyld automatisk hvilket køretøj** fra instrumentbrættet billedet, efter du har taget kilometertælleren. Du kan stadig åbne rullemenuen **Køretøj** for at tilsidesætte, hvis det er nødvendigt.

### <a id="aim-at-the-odometer"></a> Sigt mod kilometertælleren

Bliv i kilometertællertilstand, og indram klyngen. Instruktion: * Sigt mod kilometertæller. Tryk på udløseren for at optage.*

![Fyld — live kamera på kilometertæller (eksempel på bindestregbillede i forhåndsvisning)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Efter kilometertællerudløseren

OCR udfylder **Odo** og forsøger at matche køretøjet fra vartegn (gennemgå begge om nødvendigt). Hovedknappen bliver til **Prøv igen** for at optage igen. Instruktionen opsummerer læsningen.

![Fyld — kilometertæller OCR-resultat](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Pumpetilstand (pris og volumen)

1. Tryk på **↕** for at skifte til pumpetilstand: *Sigt mod pumpevisning (pris/volumen). Tryk på udløseren.*
2. Optag pumpetotalerne. Omkostnings- og volumenfelter fylder; brug **↔**, hvis de er byttet om.
3. Tryk på valuta eller **G/L**, hvis det er nødvendigt, og derefter på **Gem** (disk). Tomme felter gør en **delvis udfyldning** (stadig tilladt).

![Fyld — pumpeeksempel/totaler (eksempel pumpebillede i forhåndsvisning)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Du forbliver på Fyld til næste stop (felter ryddes efter lagring). Arbejd fuldt ud **offline**; synkronisering kører senere i baggrunden, når den er konfigureret.

### <a id="manual-entry-no-camera--bad-ocr"></a>Manuel indtastning (intet kamera / dårlig OCR)

1. Tryk på **Odo**, **cost** eller **volume** og typeværdier (stående bruger systemtastaturet; liggende bruger et skærmtastatur).
2. Vælg eller bekræft **Køretøj**, hvis automatisk registrering ikke kørte.
3. Gem som ovenfor.

### <a id="modes-and-borders"></a>Tilstande og grænser

- **Grøn kant** omkring køretøj+kilometerstand → optagelse/redigering af kilometertæller.
- **Grøn kant** omkring pris+volumen → pumpetilstand.
- **Gem** forbliver deaktiveret, indtil et køretøj er valgt, og mindst én af odo/pris/volumen har data, og OCR stadig ikke kører.

Tip på skærmen (under instruktionslinjen): *Shutter = capture · Disk = gem · ↕ = odo/pumpe-tilstand · ↔ = swap cost/volume.*

---

## <a id="expenses"></a>Udgifter

### <a id="new-expense"></a>Ny udgift

Menu → **Ny udgift**.

![Ny udgiftsformular og kamerakontroller (eksempel på bindestregbillede i kamerabåndet, når det er tomt)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Gem** (disk), **lukker** (kvitteringsbillede) eller **galleri** (vælg billede).
2. Udfyld **Dato**, **Køretøj**, **Leverandør**, **Beskrivelse**, **Beløb** (kan trykkes på valutasymbol), **Kategori**, valgfri **Kilometertæller**.
3. Kvitteringer på flere sider: Optag yderligere sider, hvis brugergrænsefladen tilbyder personsøgning (side 0 er den primære kvittering).
4. **Gem** til lagring (lokal først; fotosikkerhedskopiering og regnearkssynkronisering sker i baggrunden, når de er konfigureret).

### <a id="expense-list"></a>Udgiftsliste

Menu → **Rapporter** → **Udgiftsliste** — gennemse tidligere ikke-brændstofudgifter; åbne et element for at redigere.

![Udgiftsliste](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Rediger udgift

Åbn en række fra listen. Korrekt leverandør, beløb, kategori, køretøj og beskrivelse. Hvis kvitteringen kun er i fotobackup (ingen læsbar lokal fil), skal du bruge **Hent billede fra arkiv**, når den vises (virker på tværs af konfigurerede fotodestinationer).

![Rediger udgift](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Start turen

Menu → **Start tur** (efter Fyld skuffen i). Optag eller indtast kilometertæller, vælg turtype, gem med ikonet **disk**. **Stop** er en genvej til Personlig nu på den fastholdte GPS-position. Brug **ⓘ** til kontrolpåmindelser.

![Start turen](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Turstarter gemmes som brændstofrækker med en **Trip Type** (ikke normal påfyldning). De vises under **Rapporter → Rejsemil**, ikke under Brændstofhistorik.

---

## <a id="reports"></a>Rapporter

Menu → **Rapporter** åbner produkthubben (oversigt over alle tider + katalogkort). Dette er den eneste produktrapportoverflade - der er ikke noget separat "Rapporter og diagrammer"-skuffeelement.

![Rapporthub](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Åbn et kort til køretøjstilstand (**Alle / Hver / Enkelt**), periodefiltre, diagrammer og del (**TEKST / CSV / PDF**). Øverste bjælke på indberetning af børn: **☰ + ←** (og **ⓘ** når de er registreret).

### <a id="time-based-reports"></a>Tidsbaserede rapporter

Hovedkortkortet. Valgfri metrikker (mpg, volumen/afstand såsom G/mi, enhedspris såsom $/G, pris/distance, månedlige $, tur miles, tur % efter type) med **Smooth** skraldespande og **uafhængige Y-skalaer** (økonomi til venstre; penge og rejsefamilier til højre).

![Tidsbaserede rapporter](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Tidsbaserede rapporter — rullede metrics](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Detaljer om økonomi matematik: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Fyld historik vs. brændstof historik

- **Rapporter → Fyldhistorik** — kronologiske fyldninger for rapportfiltrene (**kun fyld**; ingen tur starter).

![Fyld historikrapport](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Brændstofhistorik** (hvis den findes i din bygnings navigation) - påfyldningsbeholdning pr. køretøj, fylder også kun; tryk på en række for at redigere.

### <a id="trip-miles"></a>Tur miles

**Rapporter → Rejsemil** — miles efter type, diagrammer og en kronologisk **turstart/segmentliste**. Tryk på en rigtig start for at åbne **Rediger fyld** for den række.

![Tur miles](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Rediger fyld

Fra Fyldningshistorik, Brændstofhistorik eller Tripmile skal du åbne en fyldning. Layout: køretøj og kilometertæller, **valuta før pris**, volumen, noter. Turtype vises kun, når rækken er en turstart. Placeringen har en oversigt plus **Placeringsdetaljer**. Manglende lokalt billede med skyidentitet: **Hent billede fra arkiv**.

![Rediger fyld — Hent fra arkiv, når der mangler et lokalt foto](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Andre katalogkort inkluderer udgifter efter kategori, køretøjsoversigt og udgiftsliste.

Penge bruger hver rækkes valuta, når den er indstillet. Blandede valutatotaler viser **subtotaler pr. valuta** (ingen tavs valutakonvertering).

---

## <a id="syncing"></a>Synkroniser

Menu → **Synkronisering** er omdrejningspunktet for regnearks- og fotodestinationer (ikke kun begravet under Indstillinger).

![Synkroniseringshub](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Kort til **Synkronisering af regneark** og **Fotosikkerhedskopiering** med kort status, **Synkronisering** for den slags og **›** til destinationslisten.
- Åbn en destination for **Testforbindelse** og **Synkroniser nu (denne destination)** / alt konfigureret.
- Fejl **Detaljer** og den røde **!** i titellinjen lander her.
- Trin-for-trin opsætning af Google Sheets og Drev: [Sikkerhedskopier og synkronisering af flere enheder](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Indstillinger (lokale præferencer)

Menu → **Indstillinger**.

![Indstillinger](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Flere indstillinger (rullet)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

For destinationer skal du foretrække **Menu → Synkronisering**. Indstillinger kan stadig vise oversigtsrækker, der åbner de samme lister.

### <a id="local-preferences-common"></a>Lokale præferencer (fælles)

- **Gem brændstofkvitteringsbilleder** / **Gem udgiftsbilleder lokalt** — gem billeder på enheden (kan anmode om fotos-tilladelse).
- **Afspil lukkerlyd**
- **Valuta** / **Volumenhed** — app-standarder (system eller eksplicit). Ændring af volumenenhed med eksisterende brændstofdata kan give en konverteringsdialog.
- **Mørk tilstand**
- **Opsætningstip** — genåbn førstegangs-køretøj/synkroniseringsvejledninger.
- **Debug Fill** / **Vis eksperimentskærme (dev)** — avanceret; lade være til daglig brug. Eksperimentskærme er ikke dokumenteret her.

CSV **eksport/import** (ZIP for køretøjer / Udgifter / Brændstof-faner) er tilgængelig fra Indstillinger, når det tilbydes af den aktuelle build.

---

## <a id="help--about"></a>Hjælp og om

![Hjælp](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Om](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Hjælp** — hurtig start på enheden, opsætningsvejledninger, link til denne manual, selvværtsopsætningsindeks.
- **Om** — version, licenser, GitHub, denne vejledning (bundtet offline + online HTML, når publiceret).

---

## <a id="related-docs"></a>Relaterede dokumenter

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — kort daglig guide
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — selv-hostet foto/tabelopsætning
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — fletning, gendannelse, dubletter
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — detaljer om økonomimålinger
