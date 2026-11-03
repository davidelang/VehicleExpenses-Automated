# <a id="vehicle-expenses-automated--user-manual"></a>Voertuigkosten geautomatiseerd — Gebruikershandleiding

> **Bron bewerken (Markdown).** Browsers en de in-app-lezer openen de **gerenderde HTML**:
> - Web: [`docs/user-manual/index.html`](index.html) (regenereren met `./scripts/render-user-manual.sh`)
> - App: Help / Info → volledige handleiding (gebundelde HTML + screenshots)
>
> Wijs eindgebruikers niet naar onbewerkte `.md` URL's: browsers tonen alleen platte tekst.

Camera-first tracking voor tankbeurten en voertuigkosten, met optionele synchronisatie en back-up van meerdere apparaten onder **uw** cloudaccounts.

Dit is de **volledige handleiding** (screenshots + elke stap). Aan de telefoon is **Menu → Help** een kortere handleiding om aan de slag te gaan.

**Hier niet behandeld:** Oude afbeeldingen, uitlijningsexperiment en pompexperiment importeren (ontwikkelaar / geavanceerde tools).

---

## <a id="table-of-contents"></a>Inhoudsopgave

1. [Wat je nodig hebt](#what-you-need)
2. [Pictogrammen in één oogopslag](#icons-at-a-glance)
3. [Het menu openen](#open-the-menu)
4. [Eerste installatie: Voertuigen beheren](#first-time-setup-manage-vehicles)
5. [Back-ups en synchronisatie met meerdere apparaten](#backups-and-multi-device-sync)
6. [Vullen (brandstof)](#fill-up-fuel)
7. [Start rit](#start-trip)
8. [Uitgaven](#expenses)
9. [Rapporten](#reports)
10. [Instellingen (lokale voorkeuren)](#settings-local-preferences)
11. [Synchroniseren](#syncing)
12. [Hulp en informatie](#help--about)
13. [Gerelateerde documenten](#related-docs)

---

## <a id="what-you-need"></a>Wat je nodig hebt

- Android-telefoon of tablet.
- Voor de beste OCR: een duidelijk zicht op uw **dashboardkilometerteller** en **pomptotalen** (of typ de cijfers met de hand).
- Optioneel: accounts **die u beheert** voor spreadsheetgegevens en/of fotoback-up (zie [Back-ups en synchronisatie van meerdere apparaten](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Pictogrammen in één oogopslag

Deze verschijnen op de hoofdschermen. Als je ze kent, bespaar je een hoop jacht.

| Waar | Icoon / controle | Wat het doet |
|-------|----------------|-------------|
| Bovenbalk | **☰ Menukaart** (hamburger) | Opent de navigatielade |
| Bovenbalk | **ⓘ** (paginahulp) | Korte hulp voor de **huidige** pagina (naast menu indien beschikbaar) |
| Bovenbalk | **`?N`** (geel) | In behandeling zijnde vragen over importcontroles - opent Importcontrole |
| Bovenbalk | **!** (rood) | Een spreadsheet of fotobestemming is onlangs mislukt. Open **Synchroniseren** om het probleem op te lossen |
| Bovenbalk | **☰ + ←** | Rapport kinderen en onkostenlijst tonen **menu en terug** samen; Rapportenhub is alleen menu |
| Instellingen / brandstof bewerken | **←** | Terug (instellingenspreadsheet/foto en brandstofbewerking blijven teruggefocust) |
| Vul | **Witte cirkel** (sluiter) | Leg kilometerteller- of pompweergave vast voor OCR |
| Vul | **Schijf / Opslaan** | Bespaar de tankbeurt (heeft een voertuig nodig en minstens één van odo / volume / kosten) |
| Vul | **↕ pijlen** (modusschakelaar) | Schakel tussen de **kilometertellermodus** en de **pompmodus (kosten/volume)**. Groene rand markeert de actieve veldgroep |
| Vul | **↔ pijlen** (tussen kosten en volume) | Wissel kosten en volume om als OCR ze in de verkeerde velden plaatst |
| Vul | **Zoom 1x / …** | Zoomverhoudingen van de camera wanneer de lens deze ondersteunt |
| Vullen (na vastleggen) | **Vernieuwen** op hoofdknop | Voorbeeld negeren en terugkeren naar livecamera |
| Vullen (tijdens verwerking) | **X** op hoofdknop | Lopende opname/OCR annuleren |
| Kosten | **Opslaan** | Bespaar de kosten |
| Kosten | **Sluitercirkel** | Maak een ontvangstfoto |
| Kosten | **Galerij** | Kies een bonafbeelding uit de bibliotheek |
| Kosten | **Herkansing** | Wis de huidige bonfoto en maak opnieuw een foto |
| Uitgaven / Voertuigen beheren | **+ / −** FAB's | Zoom in op het fotovoorbeeld |
| Dialoogvenster Oriëntatiepunten | **OCR bewerken** | Corrigeer of voeg oriëntatiepunttekst toe die de motoren hebben gemist |
| Spreadsheet / Fotoformulieren | **🔍 Zoeken** | Blader door Google Drive naar een blad of map (na inloggen) |

Valutasymbolen op kostenvelden en **G/L** op volumevelden kunnen worden aangetikt: open een klein menu om de valuta of gallons versus liters voor die invoer te wijzigen.

---

## <a id="open-the-menu"></a>Open het menu

1. Tik linksboven op **☰**.
2. Kies een pagina.

![Navigatielade](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Hoofdlade:** Vullen · Start reis · Voertuigen beheren · Nieuwe uitgaven · **Rapporten** · Instellingen · Synchroniseren · Help · Info.

**Experimentlade** (Instellingen → Experimentschermen tonen): Uitlijningsexperiment · Pompexperiment · **Oude afbeeldingen importeren**.

**Via Rapportenhub (niet hoofdlade):** Onkostenlijst · Vulgeschiedenis.

---

## <a id="first-time-setup-manage-vehicles"></a>Eerste installatie: voertuigen beheren

OCR en **automatische voertuigmatching** werken het beste nadat u elk voertuig hebt geregistreerd met een **referentiedashboardfoto**, **Discovery** hebt uitgevoerd, de kilometerteller hebt ingesteld op basis van een peddelzaadje en oriëntatiepunten voor dat streepje hebt opgeslagen.

### <a id="open-manage-vehicles"></a>Open Voertuigen beheren

Menu → **Voertuigen beheren**. Kies een voertuig (of **Nieuw voertuig toevoegen**).

![Beheer voertuigen met referentiestreepje, bijsnijden en ontdekking](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Een voertuig toevoegen of bewerken

1. Open de vervolgkeuzelijst **Voertuig** → kies een voertuig of **Nieuw voertuig toevoegen**.
2. Maak of kies een duidelijke **referentiedashboardfoto** (volledig instrumentenpaneel, goed verlicht, telefoon ongeveer recht gericht). Gebruik **Maak foto** of **Galerij**.
3. Tik op **Run Discovery** — paddledetectie plaatst gele **zaden** op de foto (oriëntatiepunten gebruiken dezelfde `"ML Kit"` JSON als voorheen).
4. Tik op **Selecteer Odo**, tik met het zaadje op de cijfers, pas de hendels aan en vervolgens op **Opslaan**.
5. **Odo bewerken** past een vak aan dat er al is. **Clear Odo** verwijdert het en op de knop staat dan **Select Odo**.
6. Beoordeel met **Mijlpalen weergeven**. Gebruik **OCR bewerken** om verkeerd gelezen tekst te corrigeren of **toe te voegen** gemiste tekst.
7. Vul **Voertuignaam** in (verplicht), plus merk/model/jaar/kenteken zoals je wilt.
8. Tik op **Voertuig maken** of **Wijzigingen opslaan** (vereist naam + referentiefoto voor een nieuw voertuig).

![Nieuwe voertuigformuliervelden toevoegen](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Oriëntatiepunten: corrigeer wat Discovery heeft gemist

Blader na **Oriëntatiepunten weergeven** door de lijst en corrigeer de waarden. Motoren missen soms kleine cijfers (bijvoorbeeld een klok **60** rechtsonder in het cluster). Gebruik **OCR bewerken** om ze toe te voegen of te corrigeren, zodat de voertuigidentiteit betrouwbaar blijft.

![Dialoogvenster Oriëntatiepunten - OCR bekijken en bewerken](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Typen zonder perfecte foto

Je kunt de app nog steeds gebruiken door een voertuig te selecteren en de kilometerteller, het volume en de kosten **in te typen** op Fill. OCR is optioneel voor elk veld. Galerijimport werkt voor de referentiedashfoto als u liever niet in de app fotografeert.

**Tip:** Na de spreadsheetsynchronisatie staan ​​voertuigdefinities (gewassen, oriëntatiepunten) in de lokale database. U hoeft Voertuigen beheren voor Fill niet opnieuw te openen om ze te gebruiken.

---

## <a id="backups-and-multi-device-sync"></a>Back-ups en synchronisatie van meerdere apparaten

De app is zo gebouwd dat **meerdere telefoons of tablets dezelfde wagenparkgegevens kunnen delen**, en dat u een **kopie van uw gegevens en foto's buiten het apparaat kunt bewaren**. Dat wordt gedaan met bestemmingen die **u** configureert onder **uw** accounts of **uw** zelfgehoste servers – niet een door het bedrijf beheerde “Voertuigkostenwolk” die andere mensen kunnen zien.

### <a id="what-runs-where"></a>Wat waar draait

| Soort | Wat het opslaat | Typisch gebruik |
|------|----------------|------------|
| **Spreadsheet-/tabelsynchronisatie** | Voertuigen, tankbeurten, uitgaven (rijen en tabbladen) | Samenvoegen van meerdere apparaten + gestructureerde back-up |
| **Fotoback-up** | Binaire afbeeldingen (streepje/pomp/bon/referentiefoto's) | Fotoback-up + ontbrekende bestanden herstellen |

U kunt **meerdere bestemmingen** van elk type configureren (soft cap per type). Handmatige **Nu synchroniseren** en **achtergrond**-werknemers voeren de ingeschakelde werkrollen uit.

### <a id="offline-first"></a>Eerst offline

- **Er is geen netwerk vereist** om een tankbeurt, uitgave of ontvangstbewijs toe te voegen. Alles wordt **eerst lokaal** opgeslagen.
- Wanneer het netwerk beschikbaar is, worden synchronisatie en fotoback-up uitgevoerd als **achtergrondtaken** (volgens een schema dat u instelt en wanneer u op **Nu synchroniseren** tikt). Fouten worden weergegeven als rode tekst onder de rijen Instellingen en een **!** in de titelbalk van de app.

### <a id="your-accounts-only"></a>Alleen uw accounts

Inloggen en tokens blijven op het apparaat staan voor de providers die u kiest (Google, Microsoft, S3-sleutels, zelf-gehoste URL's, enzovoort). Bestemmingen staan ​​onder **volledige controle van de gebruiker**: uw Google-account, uw OneDrive, uw MiniIO-bucket, uw EtherCalc-host, enz. Er wordt niets gedeeld met andere Vehicle Expenses-gebruikers via een gedeelde backend.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Ondersteunde doelen — gegevens (spreadsheet/tabel)

Geconfigureerd onder **Menu → Synchroniseren → Spreadsheetsynchronisatie** (ook bereikbaar via de samenvattingsrijen van Instellingen). Eersteklas pickeropties:

| Doel | Opmerkingen |
|--------|--------|
| **Google Spreadsheets** | Gemeenschappelijke standaard; tabbladen voor voertuigen, kosten en brandstof per voertuig |
| **Excel** | Microsoft-werkmap via binding in Graph/OneDrive-stijl |
| **EtherCalc** | Zelf-gehoste samenwerkende spreadsheetruimtes |
| **Andere →** geïmplementeerde backends | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Uitgesteld / nog niet headless (vermeld onder Overige maar nog niet volledig geïmplementeerd): OnlyOffice, Collabora. Zie ook [self-host index](reference/self-host/INDEX.md).

CSV **exporteren/importeren** (ZIP met dezelfde tabbladindeling) is via Instellingen beschikbaar als draagbare back-up, onafhankelijk van livesynchronisatie.

### <a id="supported-targets--photos-image-backup"></a>Ondersteunde doelen — foto's (back-up van afbeeldingen)

Geconfigureerd onder **Menu → Synchroniseren → Fotoback-up** (ook vanuit de overzichtsrijen van Instellingen):

| Doel | Opmerkingen |
|--------|--------|
| **Google Drive** | Map die u kiest (blader of plak URL) |
| **OneDrive** | Microsoft-account + padvoorvoegsel |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO en andere S3-compatibele eindpunten |
| **Anders** | door rclone ondersteunde opslag (bijv. WebDAV, SFTP en andere beheerde afstandsbedieningen beschikbaar in de in-app-kiezer) |

Cheatsheets instellen voor zelfgehoste foto's en tabellarische doelen: [self-host index](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Gedrag op meerdere apparaten (kort)

- Rijen samengevoegd op **Sync-ID** met **last-write-wins** op **Bijgewerkte** tijdstempels.
- Verwijderingen zijn zacht; een nieuwere bewerking op een ander apparaat kan een rij herstellen.
- Als je **twee keer dezelfde vulling** invoert op twee apparaten, worden er **twee rijen** gemaakt. Verwijder de extra rij als je het merkt.
- Meer details: [Opmerkingen over synchronisatiegedrag](#sync-behavior-notes) en [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Voorbeeld: Google Spreadsheets (gegevens) toevoegen

1. **Menu → Synchroniseren → Spreadsheetsynchronisatie** (of Instellingen → Spreadsheetsynchronisatie).

![Spreadsheetsynchronisatielijst](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Tik op **Spreadsheetbestemming toevoegen**.

![Providerkiezer](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Kies **Google Spreadsheets**.

![Google Spreadsheets-formulier](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Meld u één keer aan bij Google**. Met deze aanmelding kunt u ook browsen. Weergavenaam → plak een **Blad-URL** (nog steeds geldig) of **🔍** browse. Bladeren wordt geopend op **Alles en gedeeld**, zodat u een bestaande spreadsheet kunt kiezen waarvan u de eigenaar bent of die met u wordt gedeeld. Plan vervolgens opties → inschakelen → opslaan.
5. **Nu synchroniseren** één keer om tabbladen te maken/bij te werken: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Voorbeeld: Google Drive toevoegen (foto's)

1. **Menu → Synchroniseren → Fotoback-up** (of Instellingen → Fotoback-up).

![Fotoback-uplijst](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Tik op **Fotobestemming toevoegen**.

![Fotoproviderkiezer](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Kies **Google Drive**.

![Google Drive-formulier](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Aanmelden met Google (Drive)** → optionele map-URL/bladeren → inschakelen → opslaan → **Nu synchroniseren**.

Handmatig **Nu synchroniseren** voor foto's is een volledige pass; back-up op de achtergrond verwerkt uploads die alleen in behandeling zijn doorgaans volgens een schema.

### <a id="sync-behavior-notes"></a>Gedragsnotities synchroniseren

- Na de app-upgrade ziet u mogelijk kort **“Database bijwerken na upgrade...”** (lokale synchronisatie-ID-aanvulling).
- Als een synchronisatie wordt onderbroken, wordt de volgende **succesvolle** synchronisatie opnieuw samengevoegd en worden externe tabbladen gerepareerd.
- Mislukkingen: rood overzicht op Kaarten synchroniseren + **!** in de app-balk.

---

## <a id="fill-up-fuel"></a>Vullen (brandstof)

Dit is het **startscherm** wanneer u de app opent.

### <a id="vehicle-selection-usually-automatic"></a>Voertuigselectie (meestal automatisch)

U hoeft **niet** eerst het voertuig te kiezen. Wanneer voor voertuigen **oriëntatiepunten** zijn ingesteld in Voertuigen beheren, detecteert Fill **automatisch welk voertuig** op de dashboardafbeelding nadat u de kilometerteller hebt vastgelegd. U kunt indien nodig nog steeds de vervolgkeuzelijst **Voertuig** openen om deze te overschrijven.

### <a id="aim-at-the-odometer"></a>Richt op de kilometerteller

Blijf in de kilometertellermodus en kadreer het cluster. Instructie: * Richt op de kilometerteller. Tik op de sluiter om vast te leggen.*

![Vul — live camera op kilometerteller (voorbeeld dashboardafbeelding in voorbeeld)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Na de kilometertellersluiter

OCR vult **Odo** en probeert het voertuig te matchen op basis van oriëntatiepunten (bekijk beide indien nodig). De hoofdknop wordt **Opnieuw proberen** om opnieuw te schieten. Instructie vat de lezing samen.

![Vul — OCR-resultaat kilometerteller](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Pompmodus (kosten en volume)

1. Tik op **↕** om naar de pompmodus te schakelen: *Richt op het pompdisplay (kosten/volume). Tik op sluiter.*
2. Leg de pomptotalen vast. Kosten- en volumevelden vullen; gebruik **↔** als ze worden verwisseld.
3. Tik indien nodig op valuta of **G/L** en vervolgens op **Opslaan** (schijf). Lege velden worden **gedeeltelijk gevuld** (nog steeds toegestaan).

![Vullen — pompvoorbeeld / totalen (voorbeeld pompafbeelding in voorbeeld)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Je blijft op Fill voor de volgende stop (velden worden gewist na opslaan). Werk volledig **offline**; synchronisatie wordt later op de achtergrond uitgevoerd indien geconfigureerd.

### <a id="manual-entry-no-camera--bad-ocr"></a>Handmatige invoer (geen camera / slechte OCR)

1. Tik op **Odo**, **kosten** of **volume** en typ waarden (staand gebruikt het systeemtoetsenbord; liggend gebruikt een toetsenbord op het scherm).
2. Kies of bevestig het **Voertuig** als de automatische detectie niet is uitgevoerd.
3. Opslaan zoals hierboven.

### <a id="modes-and-borders"></a>Modi en randen

- **Groene rand** rond voertuig+odo → kilometerteller vastleggen/bewerken.
- **Groene rand** rond kosten+volume → pompmodus.
- **Opslaan** blijft uitgeschakeld totdat een voertuig is geselecteerd en ten minste één van odo/kosten/volume gegevens heeft, en OCR niet nog steeds actief is.

Tip op het scherm (onder de instructieregel): *Sluiter = vastleggen · Schijf = opslaan · ↕ = odo/pompmodus · ↔ = kosten/volume omwisselen.*

---

## <a id="expenses"></a>Uitgaven

### <a id="new-expense"></a>Nieuwe uitgave

Menu → **Nieuwe uitgave**.

![Nieuw onkostenformulier en camerabediening (voorbeeld streepjesafbeelding in cameraband wanneer leeg)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Opslaan** (schijf), **sluiter** (bonfoto) of **galerij** (kies afbeelding).
2. Vul **Datum**, **Voertuig**, **Verkoper**, **Beschrijving**, **Bedrag** (valutasymbool tikbaar), **Categorie**, optioneel **Kilometerteller** in.
3. Ontvangsten van meerdere pagina's: leg extra pagina's vast als de gebruikersinterface paging aanbiedt (pagina 0 is de primaire ontvangst).
4. **Opslaan** om op te slaan (eerst lokaal; fotoback-up en spreadsheetsynchronisatie gebeuren op de achtergrond indien geconfigureerd).

### <a id="expense-list"></a>Onkostenlijst

Menu → **Rapporten** → **Onkostenlijst** — blader door de niet-brandstofkosten; open een item om te bewerken.

![Onkostenlijst](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Onkosten bewerken

Open een rij uit de lijst. Correcte leverancier, bedrag, categorie, voertuig en beschrijving. Als de kassabon alleen in een fotoback-up staat (geen leesbaar lokaal bestand), gebruik dan **Afbeelding ophalen uit archief** wanneer deze wordt weergegeven (werkt op geconfigureerde fotobestemmingen).

![Onkosten bewerken](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Start reis

Menu → **Start reis** (na Vul de lade in). Leg de kilometerteller vast of voer deze in, kies het rittype en sla op met het **schijf**-pictogram. **Stop** is een snelkoppeling voor Persoonlijk nu op de vastgehouden GPS-locatie. Gebruik **ⓘ** voor controleherinneringen.

![Start reis](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Tripstarts worden opgeslagen als brandstofrijen met een **Reistype** (geen normale tankbeurten). Ze verschijnen onder **Rapporten → Ritmijlen**, niet onder Brandstofgeschiedenis.

---

## <a id="reports"></a>Rapporten

Menu → **Rapporten** opent de producthub (overzicht aller tijden + cataloguskaarten). Dit is het enige oppervlak met productrapporten; er is geen afzonderlijk lade-item 'Rapporten en grafieken'.

![Rapportenhub](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Open een kaart voor de voertuigmodus (**Alles / Elk / Enkel**), periodefilters, grafieken en delen (**TEKST / CSV / PDF**). Bovenste balk op rapport kinderen: **☰ + ←** (en **ⓘ** indien geregistreerd).

### <a id="time-based-reports"></a>Tijdgebaseerde rapporten

De hoofdkaartkaart. Optionele statistieken (mpg, volume/afstand zoals G/mi, eenheidsprijs zoals $/G, kosten/afstand, maandelijks $, ritmijlen, rit% per type) met **Vloeiende** bakken en **onafhankelijke Y-schalen** (economie links; geld- en reisfamilies aan de rechterkant).

![Tijdgebaseerde rapporten](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Tijdgebaseerde rapporten — gescrolde statistieken](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Details van economische wiskunde: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Vul geschiedenis versus brandstofgeschiedenis

- **Rapporten → Vulgeschiedenis** — chronologische vullingen voor de rapportfilters (**alleen vullingen**; er wordt geen reis gestart).

![Vul geschiedenisrapport in](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Brandstofgeschiedenis** (indien aanwezig in de navigatie van uw build) — vul de inventaris per voertuig, vult ook alleen; tik op een rij om te bewerken.

### <a id="trip-miles"></a>Reismijlen

**Rapporten → Ritmijlen** — mijlen per type, grafieken en een chronologische **reisstart-/segmentlijst**. Tik op een echt begin om **Opvulling bewerken** voor die rij te openen.

![Reismijlen](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Invulling bewerken

Open een tankbeurt vanuit Vulgeschiedenis, Brandstofgeschiedenis of Ritmijlen. Indeling: voertuig en kilometerteller, **valuta vóór kosten**, volume, aantekeningen. Het reistype wordt alleen weergegeven als de rij een reisbegin is. Locatie heeft een samenvatting plus **Locatiedetails**. Ontbrekende lokale foto met cloudidentiteit: **Afbeelding ophalen uit archief**.

![Opvulling bewerken — Ophalen uit archief wanneer lokale foto ontbreekt](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Andere cataloguskaarten bevatten uitgaven per categorie, voertuigoverzicht en uitgavenlijst.

Geld gebruikt de valuta van elke rij wanneer deze is ingesteld. Totalen voor gemengde valuta tonen **subtotalen per valuta** (geen stille valutaconversie).

---

## <a id="syncing"></a>Synchroniseren

Menu → **Synchroniseren** is de hub voor spreadsheet- en fotobestemmingen (niet alleen verborgen onder Instellingen).

![Synchronisatiehub](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Kaarten voor **Spreadsheetsynchronisatie** en **Fotoback-up** met korte status, **Sync** voor dat soort, en **›** in de bestemmingslijst.
- Open een bestemming voor **Testverbinding** en **Synchroniseer nu (deze bestemming)** / allemaal geconfigureerd.
- Mislukking **Details** en de rode **!** in de titelbalk komen hier terecht.
- Stapsgewijze installatie van Google Spreadsheets en Drive: [Back-ups en synchronisatie van meerdere apparaten] (#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Instellingen (lokale voorkeuren)

Menu → **Instellingen**.

![Instellingen](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Meer instellingen (gescrolld)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Voor bestemmingen geeft u de voorkeur aan **Menu → Synchroniseren**. Instellingen tonen mogelijk nog steeds samenvattingsrijen die dezelfde lijsten openen.

### <a id="local-preferences-common"></a>Lokale voorkeuren (algemeen)

- **Foto's van brandstofbonnen opslaan** / **Onkostenfoto's lokaal opslaan** — bewaar afbeeldingen op het apparaat (kan toestemming voor Foto's vragen).
- **Speel sluitergeluid af**
- **Valuta** / **Volume-eenheid** — standaardinstellingen van de app (systeem of expliciet). Het wijzigen van de volume-eenheid met bestaande brandstofgegevens kan een conversiedialoog bieden.
- **Donkere modus**
- **Installatietips** — heropen zelfstudies over voertuigen/synchronisatie voor de eerste keer.
- **Debug Fill** / **Experimentschermen tonen (dev)** — geavanceerd; laat staan ​​voor dagelijks gebruik. Experimentschermen worden hier niet gedocumenteerd.

CSV **exporteren/importeren** (ZIP van voertuigen / uitgaven / brandstoftabbladen) is beschikbaar via Instellingen wanneer dit wordt aangeboden door de huidige build.

---

## <a id="help--about"></a>Hulp en informatie

![Hulp](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Over](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Help** — snelle start op het apparaat, installatiehandleidingen, link naar deze handleiding, index voor zelfhostinstallatie.
- **Over** — versie, licenties, GitHub, deze handleiding (offline gebundeld + online HTML wanneer gepubliceerd).

---

## <a id="related-docs"></a>Gerelateerde documenten

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — korte dagelijkse gids
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — zelf-gehoste foto/tabelconfiguratie
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — samenvoegen, herstellen, duplicaten
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — detail van economische statistieken
