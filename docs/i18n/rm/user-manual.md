# <a id="vehicle-expenses-automated--user-manual"></a>Custs da vehichels automatisads  Manual da l'utilisader

> **Modifitgar la funtauna (markdown).** Il navigatur ed il lectur da l'in-app avran il **HTML generà**:
> - Web: [`docs/user-manual/index.html`](index.html) (generar da nov cun `./scripts/render-user-manual.sh`)
> - App: Agid / Info → manual cumplet (HTML lià + screenshots)
>
> Na renviar betg ils utilisaders finals a URL crivas `.md` Ils navigaturs mussan mo text simpel.

Persecuziun da tancadis e custs dal vehichel sin basa da camera, cun sincronisaziun opziunala e segirezza da plirs apparats sut **Voss** contos da cloud.

Quai è il **manual cumplet** (screenshots + mintga pass). Sin il telefon è **menu → agid** in'instrucziun curta pli curta.

**Betg tractà qua:** Importar maletgs vegls, experiment d'orientaziun ed experiment da pumpa (tools da sviluppaders/extendids).

---

## <a id="table-of-contents"></a> Inhaltsverzeichnis

1. [Tge che Vus duvrais](#what-you-need)
2. [Symbols on a glance](#icons-at-a-glance)
3. [Open Menu](#open-the-menu)
4. [Emprima installaziun: administrar vehichels](#first-time-setup-manage-vehicles)
5. [backups e sincronisaziun da plirs apparats](#backups-and-multi-device-sync)
6. [emplenir (carburant) ](#fill-up-fuel)
7. [starter il viadi](#start-trip)
8. [expensas](#expensas)
9. [Rapports](#reports)
10. [configuraziuns (preferenzas localas) ](#settings-local-preferences)
11. [Synchronisation](#syncing)
12. [Hilfe und Info](#help--about)
13. [Related documents](#related-docs)

---

## <a id="what-you-need"></a>Tge che Vus duvrais

Telefon u tablet Android.
Per il meglier OCR: ina clera vista sin Voss **quintakilometers a la tavla d'armatura** e **valurs totalas da la pumpa** (u endatai las cifras a maun).
Opziunal: contos **controllads da Vus** per datas da calculaziun da tabellas e/u segirezza da fotografias (vesair [segirezzas e sincronisaziun da plirs apparats](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a> Icons en in'egliada

Quels cumparan sin ils visurs principals. Sch'ins enconuscha els, spargnan ins blera chatscha.

| Nua | Simbol / pilotadi | Tge ch'i fa |
|-------|----------------|--------------|
| trav sura | ** Menu** (Hamburger) | Avrir la trav da navigaziun |
| trav sura | **i** (agid da la pagina) | Agid curt a la pagina **currenta** (sper il menu, sche disponibel) |
| Lingia superiura | **`?N`** (mellen) | Dumondas pendentas da controlla d'import  avra controlla d'import |
| trav sura | **!** (cotschen) | Ina calculaziun da la tabella u ina mira da fotografar ha fatg dacurt naufragi. Avrir **sincronisaziun** per schliar il problem
| trav sura | ** + ←** | Annunziar uffants e mussar la glista da spesas **Menü ed enavos** ensemen; Il hub da rapports è disponibel mo en il menu |
| Configuraziuns / elavurar carburant | **←** | Enavos (configuraziuns, calculaziun da tabellas/fotografia ed elavuraziun da carburant restan en il focus) |
| Emplenir | **Circul alv** (serrada) | Registrar l'indicaziun dals kilometers u da las pumpas per OCR |
| Emplenir | **Purtaders da datas / arcunar** | Spargnar il tancar (basegna in vehichel ed almain in dad odo / volumen / custs) |
| Emplenir | ** frizzas** (interruptur da modus) | Midar tranter il **modus da quintar kilometers** ed il **modus da pumpa (custs/volumen)**. L'ur verd marchescha la gruppa champestra activa |
| Emplenir | ** frizzas** (tranter custs e volumen) | Sbagliar custs e volumen sche OCR als metta en ils fauss champs |
| Emplenir | **Zoom 1x / ...** | Relaziuns da zoom da la camera, sche l'objectiv las sustegna |
| Emplenir (suenter la registraziun) | **Actualisar** sin il buttun principal | Bittar la prevista e returnar a la camera live |
| Emplenir (durant l'elavuraziun) | **X** sin il buttun principal | Interrumper la registraziun currenta/OCR |
| Expensas | **Arcunar** | Spargnar ils custs |
| Expensas | **Circul final** | Faschai ina fotografia da quittanza |
| Expensas | **Galeria** | Tscherni in maletg da cumprova da la biblioteca |
| Expensas | **Repetiziun** | Stizzar e reprender la fotografia da cumprova actuala |
| Custs / administrar vehichels | **+ / −** FABs | Zoomen Sie die Fotovorschau |
| Champ da dialog Objects d'interess | **Modifitgar OCR** | Curreger u agiuntar text da mussavia che las Engines han survesì |
| Calculaziun da tabellas / formulars da fotografias | **|Tschertga** | Tschertgar en Google Drive in fegl u ordinatur (suenter l'annunzia) |

Simbols da valuta sin champs da custs e **G/L** sin champs da volumen pon ins tippar: Avrir in pitschen menu per midar la valuta u gallons vs. liters per questa inscripziun.

---

## <a id="open-the-menu"></a> Avrir il menu

1. Tippai sisum a sanestra sin ****.
2. Tscherni ina vart.

![Bar da navigaziun](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Taschetta principala:** Emplenir · Cumenzar il viadi · Administrar ils vehichels · Novas ediziuns · **Rapports** · Configuraziuns · Sincronisaziun · Agid · Info.

**Taschetta d'experiments** (configuraziuns → mussar visurs d'experiments): experiment d'orientaziun · experiment da pumpa · **Importar maletgs vegls**.

**Sur il Hub da rapports (betg en il truclet principal):** Glista d'emissiuns · emplenir or il decurs.

---

## <a id="first-time-setup-manage-vehicles"></a>Emprima installaziun: administrar vehichels

OCR e **cumparegliaziun automatica dals vehichels** funcziunan il meglier suenter che Vus avais registrà mintga vehichel cun ina **fotografia dal dashboard da referenza**, fatg **discovery**, fixà il tagl dals kilometers a maun d'in seed da remblar ed arcunà puncts d'orientaziun per quest armatur.

### <a id="open-manage-vehicles"></a> Avrir administrar vehichels.

Menu → **Administrar vehichels**. Tscherni in vehichel (u **Agiuntar in nov vehichel**).

![Verwalten Fahrzeuge mit Referenzstrich, Ernten und Entdeckung](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Agiuntar u modifitgar in vehichel

1. Avrir il menu dropdown **Vehichel** → tscherner in vehichel u **Agiuntar in nov vehichel**.
2. Prendai u tscherni ina clera **fotografia da l'aissa d'armatura da referenza** (instrument cumplet cumbinà, bain illuminà, telefon circa en il dretg angul). Duvrai **prender si fotografia** u **gallaria**.
3. Tap on **Execute recognition**  the paddler recognition placed yellow **seed** on the photo (Sites use the same `"ML Kit"` JSON as before).
4. Tap on **Odo selecziunar**, tap on the start value on the numbers, adjust the dots and then click on **Save**.
5. **Edit Odo** adattescha ina boxa ch'è gia avant maun. **Clear Odo** allontanescha ella ed il buttun è lura **Select Odo**.
6. Controllar cun **Mussar attracziuns**. Duvrar **modifitgar OCR** per curreger sbagls da lectura u per agiuntar text survesì.
7. Endatai **num dal vehichel** (necessari) sco er marca/model/annada/numer tenor giavisch.
8. Tap on **Crear il vehichel** u **Arcunar las midadas** (per in vehichel nov dovri il num e la fotografia da referenza).

![Add new vehicle form fields](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a> Monuments: Curreger quai che Discovery ha survesì

Scrolla suenter **Mussar lieus d'attracziun** tras la glista e curregia las valurs. Tar motors mancan mintgatant pitschnas cifras (p.ex. in'ura **60** giudim a dretga en il cluster). Dovra **modifitgar OCR** per agiuntar u curreger ellas, per che l'identitad dal vehichel restia fidada.

![Dialog d'orientaziun  controllar e modifitgar OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a> Tippar senza ina fotografia perfetga

Vus pudais duvrar vinavant l'app cun tscherner in vehichel e cun **dar en il dumbrader da kilometers, il volumen ed ils custs** tar l'emplenir  OCR è opziunal per mintga champ. L'import da la galaria funcziuna per la fotografia da dash da referenza, sche Vus na vulais betg fotografar en l'app.

**Tip:** Suenter la sincronisaziun da la tabella restan las definiziuns dals vehichels (plantas da cultura, puncts d'orientaziun) arcunadas en la banca da datas locala  Vus na stuais betg avrir danovamain administratar vehichels per emplenir per als duvrar.

---

## <a id="backups-and-multi-device-sync"></a> Backups e sincronisaziun da plirs apparats

L'app è concepida uschia che **plirs telefonins u tablets pon parter las medemas datas da la flotta** e che Vus pudais tegnair ina **copia da Vossas datas e da Vossas fotografias davent da l'apparat**. Quai vegn fatg sur destinaziuns che Vus configurais sut **Voss** contos u **Voss** servers ospitads sezs  e betg sur in cloud da custs da viadi gestiunà da l'interpresa che autras persunas pon vesair.

### <a id="what-runs-where"></a>Tge curra nua

| Amiaivel | Tge ch'el memorisescha | Diever tipic |
|------|----------------|-------------|
| **Calculaziun da tabellas/sincronisaziun da tabellas** | Vehichels, emplenidas da tancs, expensas (lingias e cartas da register) | Reunir plirs apparats + backup structurà |
| **Fotosicherung** | Bilder binär (Armaturenbrett/Pumpe/Quittung/Referenzfotos) | Fotosicherung + fehlende Dateien wiederherstellen |

Vus pudais configurar **pliras finamiras** da mintga gener (soft-cap per tip). Manuals **Sincronisar uss** e **lavuraturs da funs** exequeschan ils lavuraturs activads.

### <a id="offline-first"></a>L'emprim offline

**I na dovra nagina rait** per agiuntar in tancament, ina sortida u in quittanza. Tut vegn arcunà **l'emprim localmain**.
Sche la rait è disponibla, vegnan la sincronisaziun e la segirezza da fotografias fatgas sco **incumbensas da funs** (tenor in urari che Vus definis e sche Vus smatgais sin **sincronisar uss**). Sbagls vegnan mussads sco text cotschen sut las lingias da configuraziun e sco **!** en la trav da titel da l'app.

### <a id="your-accounts-only"></a>Mo Voss contos

L'annunzia ed ils tokens restan sin l'apparat per ils purschiders che Vus avais tschernì (Google, Microsoft, clavs S3, URLs ospitadas sez, e.u.v.). Las finamiras èn suttamessas a la **controlla cumpletta da l'utilisader**  Voss conto da Google, Voss OneDrive, Voss MinIO-Bucket, Voss EtherCalc-Host e.u.v. Sur in backend communabel na vegn partì nagut cun auters utilisaders da Vehicle Expenses.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Finamiras sustegnidas  Datas (calculaziun da tabellas/tabella)

Configurescha sut **Menü → sincronisaziun → sincronisaziun da tabella** (cuntanschibel er sur las lingias da resumaziun da las configuraziuns). Opziuns da picker d'emprima classa:

| Finamira | Notizias |
|--------|--------|
| **Google Sheets** | Standard cuminaivel; cartas da register per vehichels, expensas e carburant per vehichel |
| **Excel** | Mappa da lavur da Microsoft via graph/OneDrive binding |
| **EtherCalc** | Spazis da calculaziun da tabellas collavurativs auto-ospitads |
| **Auters →** backends implementads | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Mess enavos/anc betg Headless (menziunà sut Auters, ma betg implementà cumplettamain): OnlyOffice, Collabora. Guardar er [Selbsthost-Index](reference/self-host/INDEX.md).

CSV **Export/Import** (ZIP cun il medem layout da tab) è disponibel en las configuraziuns sco backup purtabel, independentamain da la sincronisaziun live.

### <a id="supported-targets--photos-image-backup"></a>Targets sustegnids  Fotografias (segirezza dal maletg)

Configurescha sut **Menü → sincronisaziun → segirezza da fotografias** (er da las lingias da resumaziun da las configuraziuns):

| Finamira | Notizias |
|--------|--------|
| **Google Drive** | Ordner tschernì da Vus (tschertgar u inserir URL) |
| **OneDrive** | conto da Microsoft + prefix da la senda |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO ed auters endpoints cumpatibels cun S3 |
| **Auter** | arcun sustegnì da rclone (p.ex. WebDAV, SFTP ed auters remotes curads ch'èn disponibels en la selecziun in-app) |

Configurar cheatsheets per destinaziuns da fotografias e tabellas ospitadas sez: [Selbsthost-Index](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a> Cumportament da multi-device (curt)

Lingias vegnan reunidas suenter **Sync-ID** cun **Last-Write-Win** sin **Actualisads** buls da temp.
Elavuraziuns da stizzar èn lomas; in'elavuraziun pli nova sin in auter apparat po restabilir ina lingia.
Sche Vus endatais sin dus apparats la **medema emplenida duas giadas**, resultan **duas lingias**  stizzai las lingias supplementaras, sche Vus remartgais.
Ulteriurs detagls: [Indicaziuns davart il cumportament da sincronisaziun](#sync-behavior-notes) e [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Exempel: agiuntar Google Sheets (datas)

1. **Menu → sincronisaziun → sincronisaziun calculaziun da tabellas** (u Configuraziuns → sincronisaziun calculaziun da tabellas).

![Glista da sincronisaziun tabellas](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Tap on **Add Table Goal**.

![Selecziun dals purschiders](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Tscherni **Google Sheets**.

![Google Sheets-Formular](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **S'annunziar ina giada cun Google**  cun questa annunzia pudais Vus er navigar. Num d'annunzia → Metter en ina **URL da tabella** (anc valaivla) u **|** tschertgar. Tschertgar vegn avert sut **Tuts & partì**, uschia che Vus pudais tscherner ina tabella existenta che tutga a Vus u ch'è vegnida partida cun Vus. Lura Opziuns d'urari → Activar → Memorisar.
5. **Uss sincronisar** ina giada per crear/actualisar tabs: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Exempel: agiuntar Google Drive (Fotos)

1. **Menu → sincronisaziun → segirezza da fotografias** (u preferenzas → segirezza da fotografias).

![Foto-Backup-Liste](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Tap on **Add photo target**.

![Fotoanbieter-Auswahl](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Tscherni **Google Drive**.

![Google Drive-Formular](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **S'annunziar cun Google (Drive)** → URL dal ordinatur opziunala/Tschertgar → Activar → Memorisar → **Sincronisar uss**.

Manual **Sincronisar uss** per fotografias è in passadi cumplet; La segirezza da funs elavura normalmain **mo pendents** uploads tenor in urari.

### <a id="sync-behavior-notes"></a>Sincronisar notizias da cumportament

Suenter l'upgrade da l'app vegn eventualmain mussà curt **Banca da datas vegn actualisada suenter l'upgrade...** (backfill local da la sincronisaziun ID).
Sch'ina sincronisaziun vegn interrutta, vegnan tar la proxima **sincronisaziun reussida** tabs remots puspè reunids e reparads.
sbagl: resumaziun cotschna tar la sincronisaziun da chartas + **!** en la trav da l'app.

---

## <a id="fill-up-fuel"></a> Emplenir (carburant)

Quai è il **monitur da partenza** sche Vus avris l'app.

### <a id="vehicle-selection-usually-automatic"></a> Selecziun dal vehichel (normalmain automaticamain)

Vus na stuais **betg** selecziunar l'emprim il vehichel. Sche per vehichels en administratar vehichels èn endrizzads **puncts d'orientaziun**, enconuscha Fill **automaticamain tge vehichel** ch'è avant maun a maun dal maletg da la tavla d'armatura, suenter che Vus avais registrà il dumbrader da kilometers. Vus pudais vinavant avrir il menu da dropdown **vehichel** per al transferir en cas da basegn.

### <a id="aim-at-the-odometer"></a>Mira sin il quintakilometers

Restar en il modus dal quintakilometers ed enramar il cluster. Instrucziun: *Mirar sin il quintakilometers. Tappa per registrar sin il cumandader.*

![Füllen  Live-Kamera auf Kilometerzähler (Beispiel-Dash-Bild in der Vorschau)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Suenter la serradira dals kilometers

OCR emplenescha **Odo** ed emprova d'egualisar il vehichel cun agid da puncts d'orientaziun (controllar sche necessari omadus). Il buttun principal daventa **repeti** per sajettar danovamain. L'instrucziun resumescha la lectura.

![Füllen  OCR-Ergebnis des Kilometerzählers](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Pumpmodus (custs e volumen)

1. Tippai sin **** per midar en il modus da pumpa: *Verifitgai l'indicaziun da la pumpa (custs/volumen). Tippai sin il cumenzament.*
2. Registrar las summas da pumpa. Emplenir ils champs dals custs e dal volumen; duvrar ****, sch'els èn confundids.
3. Tippai tenor basegn sin valuta u **cudesch da materia** e lura sin **Archivar** (portader da datas). Champs vids mainan ad ina **emplenida parziala** (anc adina admissibla).

![Füllen  Pumpenvorschau/Summen (Beispielpumpenbild in der Vorschau)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Vus restais tar l'emplenir per il proxim stop (champs vegnan stizzads suenter l'arcunar). Lavurar cumplettamain **offline**; la sincronisaziun vegn exequida pli tard en il fund, sche la configuraziun correspundenta.

### <a id="manual-entry-no-camera--bad-ocr"></a> Endataziun manuala (nagina camera / schlet OCR)

1. Tippai sin **Odo**, **custs** u **volumen** ed endatai valurs (format aut dovra la tastatura da sistem, format traversal dovra ina tastatura da monitur).
2. Tscherni u confermai il **vehichel**, sche l'identificaziun automatica n'è betg vegnida fatga.
3. Memorisar sco sura.

### <a id="modes-and-borders"></a>Modi e cunfins

- **Ur verd** enturn vehichel+odo → registrar/modifitgar quintaders da kilometers.
**Ur verd** enturn custs+volumen → modus da pumpa.
**Arcunar** resta deactivà fin ch'in vehichel vegn selecziunà ed ha almain ina da datas da kilometers/custs/volumen e OCR na va anc betg.

Tip sin il monitur (sut la lingia d'instrucziun): *Trigger = Aufnahme · Festplatte = Speichern ·  = Odo-/Pumpmodus ·  = Kosten/Volumen tauschen.*

---

## <a id="expenses"></a> Custs

### <a id="new-expense"></a>Nova ediziun

Menu → **Nova ediziun**.

![Nov formular da spesas e cumond da la camera (exempel: maletg da stritg en il bindel da la camera, sch'el è vid) ](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Archivar** (portader da datas), **trigader** (fotografia da la quittanza) u **gallaria** (eleger il maletg).
2. Endatai **data**, **vehichel**, **purschider**, **descripziun**, **import** (simbol da valuta palpabel), **categoria**, opziunalmain **kilometer**.
3. Cumprovas da pliras paginas: Registrai paginas supplementaras, sche l'interfatscha d'utilisader porscha paging (pagina 0 è la cumprovas primara).
4. **Arcunar** per arcunar (l'emprim local; segirezza da fotografias e sincronisaziun da la tabella succeda tar configuraziun en il fund).

### <a id="expense-list"></a> Glista dals custs

Menu → **Rapports** → **Glista d'ediziuns**  tschertgar ediziuns passadas che n'han betg da far cun carburant; avrir in element per modifitgar.

![glista da custs](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a> Modifitgar l'ediziun

Avrir ina lingia da la glista. Curreger il purschider, la quantitad, la categoria, il vehichel e la descripziun. Sche la quittanza sa chatta mo en la segirezza da fotografias (nagina datoteca locala legibla), duvrar **Clamar giu maletg da l'archiv** sche inditgà (funcziuna per tut las destinaziuns da fotografias configuradas).

![modifitgar l'ediziun](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>starten viadi

Menu → **Cumenzar il viadi** (suenter avair emplenì il truclet). Tschiffar u endatar il dumbrader da kilometers, tscherner il tip da viadi e l'arcunar cun il **simbol da la dischetta**. **Stop** è ina scursanida per Persunalmain uss al lieu da GPS tegnì. Duvrar **i** per regurdientschas da controlla.

![Reise starten](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Partenzas vegnan arcunadas sco lingias da carburant cun in **tip da viadi** (naginas emplenidas normalas). Ellas cumparan sut **Rapports → miglias da viadi**, betg sut Curs dal carburant.

---

## <a id="reports"></a> Rapports

Menu → **Rapports** avra il hub da products (resumaziun adina + cartas da catalog). Quai è la suletta surfatscha da rapports da products  i na dat nagin element da truclet separà Rapports e diagrams.

![Hub da rapports](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Avrir ina charta per il modus dal vehichel (**Tuts / Mintgin / Singul**), filters da temp, diagrams e parter (**TEXT / CSV / PDF**). trav sura tar uffants da rapport: ** + ←** (e **i** tar registraziun).

### <a id="time-based-reports"></a>Rapports che sa basan sin il temp

La carta principala. Metricas opziunalas (mpg, volumen/distanza sco G/mi, pretsch per toc sco $/G, custs/distanza, $ mensil, miglias da viadi, viadi % tenor tip) cun **bins glischs** e **scalas independentas Y** (economia sanestra; famiglias da daners e da viadi dretga).

![Rapports basads sin il temp](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Rapports basads sin il temp  metricas scrolladas](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Detagls davart la matematica economica: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Füllverlauf vs. Treibstoffverlauf

**Rapports → Curs da l'emplenida**  emplenidas cronologicas per ils filters dals rapports (**mo emplenidas**; naginas partenzas da viadi).

![emplenir il rapport d'andament](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

**Istorgia dal carburant** (sche avant maun en la navigaziun da Voss build)  Inventari d'emplenidas per vehichel, er mo emplenidas; tippar sin ina lingia per la modifitgar.

### <a id="trip-miles"></a>Meilas da viadi

**Rapports → miglias da viadi**  miglias tenor tip, diagrams ed ina glista cronologica **da partenza/segment da viadi**. Tippai sin in cumenzament real per avrir **modifitgar l'emplenida** per questa lingia.

![Reisemeilen](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a> Modifitgar l'emplenida

Avrir sut Istorgia d'emplenida, Istorgia da carburant u miglia da viadi in'emplenida. Layout: vehichel e quintadur da kilometers, **Munaida avant custs**, volumen, notizias. Il tip da viadi vegn mussà mo, sch'i sa tracta tar la lingia d'in cumenzament dal viadi. Il lieu dispona d'ina resumaziun sco er da **detagls dal lieu**. Fotografia locala cun identitad da cloud che manca: **Clamar giu maletg da l'archiv**.

![modifitgar l'emplenida  Clamar giu da l'archiv sche la fotografia locala manca](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Autras cartas da catalog cuntegnan ediziuns tenor categoria, ina survista dals vehichels ed ina glista da las ediziuns.

Money dovra la valuta da mintga lingia, sche quella è fixada. Mussar summas totalas en valutas maschadadas **Summas intermediaras per valuta** (nagina conversiun da devisas taciturna).

---

## <a id="syncing"></a> Sincronisaziun

Menü → **Sincronisaziun** è la centrala per finamiras da calculaziun da tabellas e da fotografias (betg mo sut Einstellungen vergraben).

![Synchronisation-Hub](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

chartas per **calculaziun da tabellas** e **segiranza da fotografias** cun status curt, **sincronisaziun** per quest gener e **›** en la glista da destinaziun.
Avrir ina destinaziun per **testar la colliaziun** e **sincronisar uss (questa destinaziun)** / configurar tut.
sbagls **detagls** ed il cotschen **!** en la trav da titel sa tschentan qua.
Configuraziun pass per pass da Google Sheets e Drive: [backups e sincronisaziun da plirs apparats] (#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a> Configuraziuns (preferenzas localas)

Menu → **Configuraziuns**.

![Configuraziuns](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Ulteriuras configuraziuns (scroll) ](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Per finamiras preferis Vus **Menü → sincronisaziun**. En las configuraziuns vegnan forsa anc adina mussadas lingias da resumaziun ch'avran las medemas glistas.

### <a id="local-preferences-common"></a>Preferenzas localas (frequentas)

**Arcunar fotografias da cumprovas da carburant** / **Arcunar fotografias da spesas localmain**  Tegnair maletgs sin l'apparat (eventualmain vegn dumandada ina permissiun da fotografar).
**Laschar ir il tun da lantschada**
**Munaida** / **Unitad da volumen**  Configuraziuns da standard da l'app (sistem u explicit). Cun midar l'unitad da volumen cun datas da carburant avant maun vegn eventualmain mussà in dialog da conversiun.
**Modus stgir**
**Tipps da setup**  Avrir danovamain ils tutorials davart l'emprim vehichel/sincronisaziun.
**Debug Fill** / **Show experiment screens (dev)**  erweitert; Für den täglichen Gebrauch weglassen. Experimenterbildschirme werden hier nicht dokumentiert.

CSV **Export/Import** (Numer postal da las cartas da register Vehichels/Expensas/Carburant) è disponibel en las configuraziuns, sche quai vegn purschì da la construcziun actuala.

---

## <a id="help--about"></a> Agid ed info

![Hilfe](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Über](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

**Agid**  Start svelt sin l'apparat, tutorials da setup, link a quest manual, Self-Host-Setup-Index.
**Info**  versiun, licenzas, GitHub, quest manual (bundlà offline + HTML online tar la publicaziun).

---

## <a id="related-docs"></a> Documents parentads

[QUICK_GUIDE.md](../QUICK_GUIDE.md)  curt mussavia per il mintgadi
[self-host/INDEX.md](../reference/self-host/INDEX.md)  setup da fotos/ tabellas ospità sez
[SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md)  Reuniun, restabiliment, duplicats
[REPORTS_METRICS.md](../reference/REPORTS_METRICS.md)  Detagl dals indizis economics
