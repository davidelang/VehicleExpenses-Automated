# <a id="vehicle-expenses-automated--user-manual"></a>Cheltuieli automate pentru vehicule — Manual de utilizare

> **Editați sursa (Markdown).** Browserele și cititorul din aplicație deschid **HTML redat**:
> - Web: [`docs/user-manual/index.html`](index.html) (regenerează cu `./scripts/render-user-manual.sh`)
> - Aplicație: Ajutor / Despre → manual complet (HTML pachet + capturi de ecran)
>
> Nu indicați utilizatorii finali către adrese URL brute `.md` — browserele afișează doar text simplu.

Urmărirea în primul rând a camerei pentru umplerile cu combustibil și cheltuielile vehiculului, cu sincronizare opțională pe mai multe dispozitive și backup în conturile dvs. cloud ****.

Acesta este **manualul complet** (capturi de ecran + fiecare pas). La telefon, **Meniu → Ajutor** este un ghid introductiv mai scurt.

**Nu este acoperit aici:** Importați imagini vechi, Experiment de aliniere și Experiment cu pompă (instrumente pentru dezvoltatori / avansate).

---

## <a id="table-of-contents"></a>Cuprins

1. [De ce ai nevoie](#what-you-need)
2. [Icoane dintr-o privire](#icons-at-a-glance)
3. [Deschide meniul](#open-the-menu)
4. [Configurare pentru prima dată: Gestionați vehiculele](#first-time-setup-manage-vehicles)
5. [Backup-uri și sincronizare cu mai multe dispozitive](#backups-and-multi-device-sync)
6. [Umplere (combustibil)](#fill-up-fuel)
7. [Începe călătoria](#start-trip)
8. [Cheltuieli](#expenses)
9. [Rapoarte](#reports)
10. [Setări (preferințe locale)](#settings-local-preferences)
11. [Sincronizare](#syncing)
12. [Ajutor și Despre](#help--about)
13. [Documente similare](#related-docs)

---

## <a id="what-you-need"></a>De ce ai nevoie

- telefon sau tabletă Android.
- Pentru cel mai bun OCR: o vedere clară a **odometrului de pe tabloul de bord** și a **totalurilor pompei** (sau introduceți numerele manual).
- Opțional: conturi **pe care le controlați** pentru datele din foile de calcul și/sau copiile de rezervă ale fotografiilor (consultați [Backup-uri și sincronizare cu mai multe dispozitive](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Icoane dintr-o privire

Acestea apar pe ecranele principale. Cunoașterea lor salvează multă vânătoare.

| Unde | Pictogramă / control | Ce face |
|-------|-----------------|---------------|
| Bara de sus | **☰ Meniu** (hamburger) | Deschide sertarul de navigare |
| Bara de sus | **ⓘ** (ajutor în pagină) | Ajutor scurt pentru pagina **actuală** (lângă meniu când este disponibil) |
| Bara de sus | **`?N`** (galben) | Întrebări de examinare a importului în așteptare — deschide Examinare import |
| Bara de sus | **!** (roșu) | O foaie de calcul sau o destinație de fotografie a eșuat recent — deschideți **Sincronizare** pentru a remedia |
| Bara de sus | **☰ + ←** | Raportați copiii și Lista de cheltuieli arată **meniul și înapoi** împreună; Centrul de rapoarte este doar meniu |
| Setări / modificare combustibil | **←** | Înapoi (setările foaia de calcul/fotografie și editarea combustibilului rămân concentrate în spate) |
| Umpleți | **Cerc alb** (oblon) | Capturați afișajul contorului de parcurs sau al pompei pentru OCR |
| Umpleți | **Disc / Salvare** | Salvați umplerea (necesită un vehicul și cel puțin unul de odo/volum/cost) |
| Umpleți | **↕ săgeți** (comutator de mod) | Comutați **modul contorului de parcurs** față de **modul pompă (cost/volum)**. Chenarul verde evidențiază grupul de câmpuri activ |
| Umpleți | **↔ săgeți** (între cost și volum) | Schimbați costul și volumul dacă OCR le pune în câmpurile greșite |
| Umpleți | **Mărire 1x / …** | Ratele de zoom ale camerei când obiectivul le acceptă |
| Umplere (după capturare) | **Reîmprospătați** pe butonul principal | Renunțați la previzualizare și reveniți la camera live |
| Umplere (în timpul procesării) | **X** pe butonul principal | Anulați captura în curs/OCR |
| Cheltuieli | **Salvează** | Economisiți cheltuiala |
| Cheltuieli | **Cercul obturator** | Faceți o fotografie de chitanță |
| Cheltuieli | **Galerie** | Alegeți o imagine de chitanță din bibliotecă |
| Cheltuieli | **Reluare** | Ștergeți fotografia actuală a chitanței și fotografiați din nou |
| Cheltuiala / Gestionarea vehiculelor | **+ / −** FAB-uri | Măriți previzualizarea fotografiei |
| Dialog Repere | **Editare OCR** ​​| Corectați sau adăugați textul de reper pe care motoarele au ratat |
| Foi de calcul / Formulare foto | **🔍 Căutare** | Răsfoiți Google Drive pentru o foaie sau un dosar (după conectare) |

Simbolurile valutare în câmpurile de cost și **G/L** în câmpurile de volum sunt apasabile: deschideți un meniu mic pentru a schimba moneda sau galoane vs litri pentru acea intrare.

---

## <a id="open-the-menu"></a>Deschide meniul

1. Atingeți **☰** în stânga sus.
2. Alegeți o pagină.

![Sertar de navigare](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Stader principal:** Umpleți · Începeți călătoria · Gestionați vehiculele · Cheltuieli noi · **Rapoarte** · Setări · Sincronizare · Ajutor · Despre.

**Sertarul de experimente** (Setări → Afișați ecranele de experiment): Experiment de aliniere · Experiment cu pompă · **Importați imagini vechi**.

**Prin centrul de rapoarte (nu sertarul principal):** Lista de cheltuieli · Istoricul de completare.

---

## <a id="first-time-setup-manage-vehicles"></a>Configurare pentru prima dată: Gestionați vehiculele

OCR și **potrivirea automată a vehiculelor** funcționează cel mai bine după ce înregistrați fiecare vehicul cu o **fotografie de referință din tabloul de bord**, rulați **Discovery**, setați decuparea contorului de parcurs dintr-o sămânță cu palete și salvați reperele pentru acel bord.

### <a id="open-manage-vehicles"></a>Deschideți Gestionați vehicule

Meniu → **Manage Vehicles**. Alegeți un vehicul (sau **Adăugați un vehicul nou**).

![Gestionați vehiculele cu liniuță de referință, decupări și descoperire](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Adăugați sau editați un vehicul

1. Deschideți meniul derulant **Vehicul** → alegeți un vehicul sau **Adăugați un vehicul nou**.
2. Capturați sau alegeți o **fotografie cu bordura de referință** clară (grup de instrumente complet, bine iluminat, telefonul aproximativ pătrat). Folosiți **Fă fotografie** sau **Galerie**.
3. Atingeți **Run Discovery** — detectarea paletei plasează **semințe** galbene pe fotografie (reperele folosesc același `"ML Kit"` JSON ca înainte).
4. Atingeți **Selectați Odo**, atingeți semințele de pe cifre, reglați mânerele, apoi **Salvați**.
5. **Edit Odo** ajustează o casetă care este deja acolo. **Clear Odo** îl elimină, iar butonul apoi citește **Select Odo**.
6. Examinați cu **Afișați repere**. Utilizați **Editați OCR** ​​pentru a remedia citirile greșite sau **adăugați** text care a fost omis.
7. Completați **Numele vehiculului** (obligatoriu), plus marca/modelul/anul/placuța după cum doriți.
8. Atingeți **Creați vehicul** sau **Salvați modificările** (necesită nume + fotografie de referință pentru un vehicul nou).

![Adăugați câmpuri noi pentru formularul vehiculului](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Repere: remediați ce a omis Discovery

După **Afișați reperele**, derulați lista și corectați valorile. Motoarele ratează uneori cifre mici (de exemplu, un ceas **60** în partea dreaptă jos a clusterului). Utilizați **Editați OCR** ​​pentru a le adăuga sau a le remedia, astfel încât identitatea vehiculului să rămână de încredere.

![Dialog Repere — revizuiți și editați OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Tastarea fără o fotografie perfectă

Puteți folosi aplicația în continuare selectând un vehicul și **tastând** contorul de parcurs, volumul și costul pe Umplere — OCR este opțional pentru fiecare câmp. Importul galeriei funcționează pentru fotografia de referință când preferați să nu fotografiați în aplicație.

**Sfat:** După sincronizarea foii de calcul, definițiile vehiculelor (culturi, repere) se află în baza de date locală - nu este nevoie să redeschideți Gestionați vehiculele pentru umplere pentru a le utiliza.

---

## <a id="backups-and-multi-device-sync"></a>Backup-uri și sincronizare cu mai multe dispozitive

Aplicația este construită astfel încât **mai multe telefoane sau tablete să poată partaja aceleași date despre flotă** și astfel încât să puteți păstra o **copie a datelor și fotografiilor dvs. de pe dispozitiv**. Acest lucru se face cu destinațiile pe care le configurați în **conturile dvs.** sau **serverele dvs. auto-găzduite – nu un „nor pentru cheltuielile vehiculelor” administrat de companie pe care îl pot vedea alți oameni.

### <a id="what-runs-where"></a>Ce rulează unde

| amabil | Ce stochează | Utilizare tipică |
|------|-----------------|--------------|
| **Sincronizare foaie de calcul / tabelar** | Vehicule, umpleri cu combustibil, cheltuieli (rânduri și file) | Îmbinare cu mai multe dispozitive + backup structurat |
| **Copia de rezervă foto** | Imagini binare (liniuță/pompă/chitanță/fotografii de referință) | Backup fotografii + restaurare fișiere lipsă |

Puteți configura **mai multe destinații** de fiecare fel (limită moale pe tip). Lucrătorii manuali **Sincronizați acum** și **de fundal** îi execută pe cei activați.

### <a id="offline-first"></a>Offline mai întâi

- **Nu este necesară nicio rețea** pentru a adăuga o completare, o cheltuială sau o chitanță. Totul este salvat **mai întâi local**.
- Când rețeaua este disponibilă, sincronizarea și copierea de rezervă a fotografiilor rulează ca **sarcini de fundal** (în baza unui program pe care l-ați stabilit și când atingeți **Sincronizați acum**). Eșecurile apar ca text roșu sub rândurile Setări și un **!** în bara de titlu a aplicației.

### <a id="your-accounts-only"></a>Numai conturile dvs

Conectarea și simbolurile rămân pe dispozitiv pentru furnizorii pe care îi alegeți (chei Google, Microsoft, S3, adrese URL găzduite de sine și așa mai departe). Destinațiile sunt sub **controlul deplin al utilizatorului** — contul dvs. Google, OneDrive, compartimentul dvs. MinIO, gazda EtherCalc etc. Nimic nu este partajat cu alți utilizatori Vehicle Expenses printr-un backend partajat.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Tinte acceptate — date (foaie de calcul / tabelar)

Configurat în **Meniu → Sincronizare → Sincronizare foi de calcul** (accesibil și din rândurile rezumate Setări). Opțiuni de selecție de primă clasă:

| Țintă | Note |
|--------|--------|
| **Foi de calcul Google** | Implicit comun; file pentru Vehicule, Cheltuieli și combustibil per vehicul |
| **Excel** | Registrul de lucru Microsoft prin legarea în stil Graph / OneDrive |
| **EtherCalc** | Săli de foi de calcul colaborative auto-găzduite |
| **Alte →** backend-uri implementate | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Amânat/nu este încă fără cap (listat la Altele, dar nu a fost implementat complet): OnlyOffice, Collabora. Vezi și [index de auto-gazdă](reference/self-host/INDEX.md).

CSV **export/import** (ZIP al aceluiași aspect al filei) este disponibil din Setări ca backup portabil, independent de sincronizarea live.

### <a id="supported-targets--photos-image-backup"></a>Ținte acceptate — fotografii (backup imagine)

Configurat în **Meniu → Sincronizare → Backup fotografii** (de asemenea, din rândurile rezumate Setări):

| Țintă | Note |
|--------|--------|
| **Google Drive** | Dosarul pe care îl alegeți (răsfoiți sau inserați adresa URL) |
| **OneDrive** | Cont Microsoft + prefix cale |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO și alte puncte finale compatibile cu S3 |
| **Altele** | Stocare susținută de rclone (de exemplu, WebDAV, SFTP și alte telecomenzi selectate disponibile în selectorul în aplicație) |

Configurați cheatsheets pentru fotografii auto-găzduite și ținte tabelare: [index de auto-găzduire](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Comportament pe mai multe dispozitive (scurt)

- Rândurile îmbinate prin **ID de sincronizare** cu **last-write-wins** pe marcajele de timp **Actualizate**.
- Ștergerile sunt moi; o editare mai nouă pe alt dispozitiv poate restabili un rând.
- Introducerea **aceeași umplere de două ori** pe două dispozitive creează **două rânduri** - ștergeți suplimentar când observați.
- Mai multe detalii: [Sync behavior notes](#sync-behavior-notes) și [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Exemplu: adăugați Foi de calcul Google (date)

1. **Meniu → Sincronizare → Sincronizare foi de calcul** (sau Setări → Sincronizare foi de calcul).

![Lista de sincronizare cu foi de calcul](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Atingeți **Adăugați destinația foii de calcul**.

![Selector de furnizor](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Alegeți **Foi de calcul Google**.

![Formular Foi de calcul Google](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Conectează-te cu Google** o singură dată — această conectare vă permite și să navigați. Nume afișat → inserați o **Adresă URL pentru foaie** (încă valabilă) sau **🔍** răsfoiți. Răsfoiți se deschide pe **Toate și partajate**, astfel încât să puteți alege o foaie de calcul existentă pe care o dețineți sau care este partajată cu dvs. Apoi programați opțiuni → activați → salvați.
5. **Sincronizează acum** o dată pentru a crea/actualiza file: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Exemplu: adăugați Google Drive (fotografii)

1. **Meniu → Sincronizare → Backup fotografii** (sau Setări → Backup fotografii).

![Lista de copii de rezervă pentru fotografii](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Atingeți **Adăugați destinația fotografiei**.

![Selector de furnizor de fotografii](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Alegeți **Google Drive**.

![Formular Google Drive](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Conectați-vă cu Google (Drive)** → URL opțional al folderului/răsfoiți → activați → salvați → **Sincronizați acum**.

Manual **Sincronizare acum** pentru fotografii este o trecere completă; backup în fundal procesează în mod obișnuit încărcări **numai în așteptare** la o programare.

### <a id="sync-behavior-notes"></a>Sincronizați notele de comportament

- După actualizarea aplicației, este posibil să vedeți pentru scurt timp **„Actualizarea bazei de date după actualizare…”** (completare locală a codului de sincronizare).
- Dacă o sincronizare este întreruptă, următoarea sincronizare **reușită** se reunește și repară filele de la distanță.
- Eșecuri: rezumat roșu pe cardurile de sincronizare + **!** în bara de aplicații.

---

## <a id="fill-up-fuel"></a>Umplere (combustibil)

Acesta este **ecranul de pornire** când deschideți aplicația.

### <a id="vehicle-selection-usually-automatic"></a>Selectarea vehiculului (de obicei automată)

**Nu** trebuie să alegeți mai întâi vehiculul. Când vehiculele au **repere** configurate în Gestionare vehicule, Completați **detectă automat ce vehicul** din imaginea bordului după ce capturați contorul de parcurs. Puteți deschide în continuare meniul derulant **Vehicul** pentru a înlocui dacă este necesar.

### <a id="aim-at-the-odometer"></a>Țintește contorul de parcurs

Rămâneți în modul odometru și încadrați grupul. Instrucțiuni: *Țintește odometrul. Atingeți declanșatorul pentru a captura.*

![Umplere — cameră live pe contorul de parcurs (exemplu imagine liniuță în previzualizare)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>După obturatorul odometrului

OCR umple **Odo** și încearcă să se potrivească cu vehiculul din repere (revizuiți ambele dacă este necesar). Butonul principal devine **Reîncercați** pentru a reînregistra. Instrucțiunea rezumă lectura.

![Umplere — rezultat OCR al contorului de parcurs](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Mod pompă (cost și volum)

1. Atingeți **↕** pentru a comuta în modul pompă: *Vizați afișarea pompei (cost/volum). Atingeți declanșatorul.*
2. Captați totalurile pompei. Câmpurile de cost și volum sunt completate; utilizați **↔** dacă sunt schimbate.
3. Atingeți valută sau **G/L** dacă este necesar, apoi **Salvare** (disc). Câmpurile goale fac o **completare parțială** (încă este permisă).

![Umplere — previzualizare pompa / totaluri (exemplu imagine pompa în previzualizare)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Rămâneți pe Fill pentru următoarea oprire (câmpurile șterge după salvare). Lucrați complet **offline**; sincronizarea rulează mai târziu în fundal când este configurată.

### <a id="manual-entry-no-camera--bad-ocr"></a>Introducere manuală (fără cameră/OCR prost)

1. Atingeți **Odo**, **cost** sau **volum** și introduceți valori (portret folosește tastatura de sistem; peisaj folosește o tastatură pe ecran).
2. Alegeți sau confirmați **Vehiculul** dacă detectarea automată nu a rulat.
3. Salvați ca mai sus.

### <a id="modes-and-borders"></a>Moduri și margini

- **Chenar verde** în jurul vehiculului+odo → capturare/editare odometru.
- **Chenar verde** în jurul cost+volum → modul pompă.
- **Salvare** rămâne dezactivat până când este selectat un vehicul și cel puțin unul dintre odo/cost/volum are date, iar OCR nu mai rulează.

Sfat de pe ecran (sub linia de instrucțiuni): *Declanșator = capturare · Disc = salvare · ↕ = modul odo/pompă · ↔ = cost/volum de schimb.*

---

## <a id="expenses"></a>Cheltuieli

### <a id="new-expense"></a>Cheltuială nouă

Meniu → **Cheltuială nouă**.

![Formular nou de cheltuieli și comenzi ale camerei (exemplu imagine liniuță în banda camerei când este goală)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Salvează** (disc), **obturator** (fotografie chitanță) sau **galerie** (alege imaginea).
2. Completați **Data**, **Vehicul**, **Vânzător**, **Descriere**, **Sumă** (simbol valutar care se poate atinge), **Categorie**, **Contor de parcurs** opțional.
3. Chitanțe cu mai multe pagini: capturați pagini suplimentare dacă UI oferă paginare (pagina 0 este chitanța principală).
4. **Salvați** în stocare (în primul rând local; copierea de rezervă a fotografiilor și sincronizarea foii de calcul au loc în fundal când sunt configurate).

### <a id="expense-list"></a>Lista de cheltuieli

Meniu → **Rapoarte** → **Lista de cheltuieli** — răsfoiește cheltuielile anterioare care nu sunt legate de combustibil; deschideți un articol de editat.

![Lista de cheltuieli](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Editați cheltuiala

Deschideți un rând din listă. Vânzătorul corect, cantitatea, categoria, vehiculul și descrierea. Dacă chitanța se află doar în copierea de rezervă a fotografiilor (fără fișier local care poate fi citit), utilizați **Preluare imagine din arhivă** când este afișată (funcționează în toate destinațiile configurate pentru fotografii).

![Editați cheltuieli](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Începe călătoria

Meniu → **Începe călătoria** (după umpleți sertarul). Capturați sau introduceți contorul de parcurs, alegeți tipul de călătorie, salvați cu pictograma **disc**. **Stop** este o comandă rapidă pentru Personal acum la locația GPS reținută. Folosiți **ⓘ** pentru mementouri de control.

![Începe călătoria](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Pornirile de călătorie sunt stocate ca rânduri de combustibil cu un **Tip de călătorie** (nu umplerile normale). Acestea apar sub **Rapoarte → Mile de călătorie**, nu sub Istoricul combustibilului.

---

## <a id="reports"></a>Rapoarte

Meniu → **Rapoarte** deschide centrul de produse (rezumat permanent + carduri de catalog). Aceasta este singura suprafață a rapoartelor de produs - nu există un element separat de sertar „Rapoarte și diagrame”.

![Centrul de rapoarte](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Deschideți un card pentru modul vehicul (**Toate / Fiecare / Singur**), filtre de perioadă, diagrame și partajați (**TEXT / CSV / PDF**). Bara de sus a copiilor raportați: **☰ + ←** (și **ⓘ** când sunteți înregistrat).

### <a id="time-based-reports"></a>Rapoarte bazate pe timp

Cartea de carte principală. Valori opționale (mpg, volum/distanță, cum ar fi G/mi, preț unitar, cum ar fi $/G, cost/distanță, $ lunar, mile de călătorie, % călătorie după tip) cu coșuri **Smooth** și **scări Y independente** (economie stânga; bani și familii de călătorii în dreapta).

![Rapoarte bazate pe timp](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Rapoarte bazate pe timp — valori derulate](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Detalii de matematică economică: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Umplerea istoricului vs Istoricul combustibilului

- **Rapoarte → Istoric completare** — completări cronologice pentru filtrele de raport (**numai completări**; nicio călătorie nu începe).

![Completați raportul istoric](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Istoricul combustibilului** (dacă este prezent în navigarea construcției dvs.) - inventarul de umplere per vehicul, de asemenea, numai umpleri; atingeți un rând pentru a edita.

### <a id="trip-miles"></a>Trip mile

**Rapoarte → Mile de călătorie** — mile după tip, diagrame și o **început/listă de segmente** cronologic. Atingeți un început real pentru a deschide **Editați umplerea** pentru acel rând.

![Trip mile](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Editați umplere

Din Istoricul umplerii, Istoricul combustibilului sau Milele călătoriei, deschideți o umplere. Aspect: vehicul și odometru, **valută înainte de cost**, volum, note. Tipul de călătorie apare numai atunci când rândul este un început de călătorie. Locația are un rezumat plus **Detalii despre locație**. Lipsește o fotografie locală cu identitatea cloud: **Preluează imaginea din arhivă**.

![Editați umplere — Preluați din arhivă când lipsește fotografia locală](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Alte carduri de catalog includ cheltuieli pe categorii, rezumatul vehiculului și lista de cheltuieli.

Money folosește moneda fiecărui rând atunci când este setată. Totalurile în valută mixtă arată **subtotaluri pe monedă** (fără conversie valutară silențioasă).

---

## <a id="syncing"></a>Sincronizare

Meniu → **Sincronizare** este centrul destinațiilor pentru foile de calcul și fotografiile (nu doar îngropate în Setări).

![Hub de sincronizare](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Carduri pentru **Sincronizarea foii de calcul** și **Copia de rezervă pentru fotografii** cu stare scurtă, **Sincronizare** pentru acest tip și **›** în lista de destinații.
- Deschideți o destinație pentru **Testați conexiunea** și **Sincronizați acum (această destinație)** / toate configurate.
- Eșecul **Detalii** și roșul **!** din bara de titlu aterizează aici.
- Configurare pas cu pas pentru Foi de calcul Google și Drive: [Backup-uri și sincronizare pe mai multe dispozitive](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Setări (preferințe locale)

Meniu → **Setări**.

![Setări](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Mai multe setări (defilate)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Pentru destinații, preferați **Meniu → Sincronizare**. Setările pot afișa în continuare rânduri rezumate care deschid aceleași liste.

### <a id="local-preferences-common"></a>Preferințe locale (comune)

- **Salvează fotografiile chitanței de combustibil** / **Salvează fotografiile cheltuielilor local** — păstrează imaginile pe dispozitiv (poate solicita permisiunea Fotografii).
- **Reda sunet obturator**
- **Monetă** / **Unitate de volum** — aplicații implicite (sistem sau explicite). Schimbarea unității de volum cu datele de combustibil existente poate oferi un dialog de conversie.
- **Mod întunecat**
- **Sfaturi de configurare** — redeschideți tutorialele de sincronizare a vehiculului pentru prima rulare.
- **Debug Fill** / **Afișează ecrane de experiment (dev)** — avansat; lăsați-l pentru uz zilnic. Ecranele de experiment nu sunt documentate aici.

CSV **export/import** (ZIP de Vehicule / Cheltuieli / Filele Combustibil) este disponibil din Setări atunci când este oferit de versiunea curentă.

---

## <a id="help--about"></a>Ajutor și Despre

![Ajutor](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Despre](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Ajutor** — pornire rapidă pe dispozitiv, tutoriale de configurare, link către acest manual, index de configurare auto-gazdă.
- **Despre** — versiune, licențe, GitHub, acest manual (pachet offline + HTML online când este publicat).

---

## <a id="related-docs"></a>Documente înrudite

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — scurt ghid zilnic
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — fotografie auto-găzduită/configurare tabulară
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — îmbinare, recuperare, duplicate
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — detalii despre valorile economiei
