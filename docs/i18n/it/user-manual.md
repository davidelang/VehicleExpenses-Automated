# <a id="vehicle-expenses-automated--user-manual"></a>Spese veicolo automatizzate - Manuale dell'utente

> **Modifica sorgente (Markdown).** I browser e il lettore in-app aprono il **HTML renderizzato**:
> - Web: [`docs/user-manual/index.html`](index.html) (rigenera con `./scripts/render-user-manual.sh`)
> - App: Guida/Informazioni → manuale completo (HTML + screenshot in bundle)
>
> Non indirizzare gli utenti finali a URL `.md` non elaborati: i browser mostrano solo testo normale.

Monitoraggio tramite telecamera per i rifornimenti di carburante e le spese del veicolo, con sincronizzazione multi-dispositivo opzionale e backup nei **tuoi** account cloud.

Questo è il **manuale completo** (screenshot + ogni passaggio). Sul telefono, **Menu → Aiuto** è una guida introduttiva più breve.

**Non trattato qui:** Importa vecchie immagini, Esperimento di allineamento ed Esperimento di pompaggio (strumenti di sviluppo/avanzati).

---

## <a id="table-of-contents"></a>Indice

1. [Cosa ti serve](#what-you-need)
2. [Icone in sintesi](#icons-at-a-glance)
3. [Apri il menu](#open-the-menu)
4. [Prima configurazione: Gestisci veicoli](#first-time-setup-manage-vehicles)
5. [Backup e sincronizzazione multi-dispositivo](#backups-and-multi-device-sync)
6. [Riempire (carburante)](#fill-up-fuel)
7. [Inizia viaggio](#start-trip)
8. [Spese](#expenses)
9. [Rapporti](#reports)
10. [Impostazioni (preferenze locali)](#settings-local-preferences)
11. [Sincronizzazione](#syncing)
12. [Aiuto e informazioni](#help--about)
13. [Documenti correlati](#related-docs)

---

## <a id="what-you-need"></a>Quello che ti serve

- Telefono o tablet Android.
- Per un OCR migliore: una visione chiara del **contachilometri del cruscotto** e dei **totali della pompa** (o digita i numeri a mano).
- Opzionale: account **che controlli** per i dati del foglio di calcolo e/o il backup delle foto (vedi [Backup e sincronizzazione multi-dispositivo](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Icone a colpo d'occhio

Appaiono nelle schermate principali. Conoscerli risparmia molta caccia.

| Dove | Icona/controllo | Cosa fa |
|-------|----------------|--------------|
| Barra superiore | **☰ Menù** (hamburger) | Apre il cassetto di navigazione |
| Barra superiore | **ⓘ** (aiuto della pagina) | Breve aiuto per la pagina **corrente** (accanto al menu quando disponibile) |
| Barra superiore | **`?N`** (giallo) | Pending import review questions — opens Import review |
| Barra superiore | **!** (rosso) | Recentemente si è verificato un errore in un foglio di calcolo o in una destinazione per foto: apri **Sincronizzazione** per correggere |
| Barra superiore | **☰ + ←** | Report children and Expenses list show **menu and back** together; Reports hub is menu only |
| Impostazioni/modifica carburante | **←** | Back (settings spreadsheet/photo and fuel edit stay back-focused) |
| Riempi | **White circle** (shutter) | Capture odometer or pump display for OCR |
| Riempi | **Disco/Salva** | Save the fill-up (needs a vehicle and at least one of odo / volume / cost) |
| Riempi | **↕ arrows** (mode switch) | Toggle **odometer mode** vs **pump (cost/volume) mode**. Green border highlights the active field group |
| Riempi | **↔ arrows** (between cost & volume) | Swap cost and volume if OCR put them in the wrong fields |
| Riempi | **Zoom 1x /...** | Camera zoom ratios when the lens supports them |
| Riempi (dopo l'acquisizione) | **Refresh** on main button | Discard preview and return to live camera |
| Fill (while processing) | **X** sul pulsante principale | Cancel in-progress capture/OCR |
| Spesa | **Salva** | Risparmia sulla spesa |
| Spesa | **Cerchio otturatore** | Scatta una foto della ricevuta |
| Spesa | **Galleria** | Pick a receipt image from the library |
| Spesa | **Riprendi** | Cancella la foto della ricevuta corrente e scatta di nuovo |
| Expense / Manage Vehicles | **+ / −** FAB | Ingrandisci l'anteprima della foto |
| Finestra di dialogo Punti di riferimento | **Modifica OCR** ​​| Correct or add landmark text the engines missed |
| Foglio di calcolo/Moduli fotografici | **🔍 Cerca** | Browse Google Drive for a sheet or folder (after sign-in) |

I simboli di valuta sui campi dei costi e **G/L** sui campi del volume sono selezionabili: apri un piccolo menu per modificare la valuta o i galloni rispetto ai litri per quella voce.

---

## <a id="open-the-menu"></a>Apre il menu

1. Tocca **☰** in alto a sinistra.
2. Scegli una pagina.

![Cassetto di navigazione](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Cassetto principale:** Compila · Inizia viaggio · Gestisci veicoli · Nuova spesa · **Rapporti** · Impostazioni · Sincronizzazione · Aiuto · Informazioni.

**Cassetto degli esperimenti** (Impostazioni → Mostra schermate degli esperimenti): Esperimento di allineamento · Esperimento pompa · **Importa vecchie immagini**.

**Tramite hub Report (non cassetto principale):** Elenco spese · Compila cronologia.

---

## <a id="first-time-setup-manage-vehicles"></a>Prima configurazione: gestisci veicoli

OCR e **abbinamento automatico dei veicoli** funzionano meglio dopo aver registrato ciascun veicolo con una **foto di riferimento del cruscotto**, eseguito **Discovery**, impostato il ritaglio del contachilometri da un seme di pagaia e salvato i punti di riferimento per quel trattino.

### <a id="open-manage-vehicles"></a>Apri Gestisci veicoli

Menu → **Gestisci veicoli**. Scegli un veicolo (o **Aggiungi nuovo veicolo**).

![Gestisci veicoli con trattino di riferimento, ritaglio e scoperta](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Aggiungi o modifica un veicolo

1. Apri il menu a discesa **Veicolo** → scegli un veicolo o **Aggiungi nuovo veicolo**.
2. Scatta o scegli una **foto di riferimento** chiara del cruscotto (quadro strumenti completo, ben illuminato, telefono più o meno quadrato). Utilizza **Scatta foto** o **Galleria**.
3. Tocca **Esegui rilevamento**: il rilevamento tramite paddle inserisce **semi** gialli sulla foto (i punti di riferimento utilizzano lo stesso `"ML Kit"` JSON di prima).
4. Tocca **Seleziona Odo**, tocca il seme sulle cifre, regola le maniglie, quindi **Salva**.
5. **Modifica Odo** modifica una casella già presente. **Cancella Odo** lo rimuove e il pulsante quindi legge **Seleziona Odo**.
6. Rivedi con **Mostra punti di riferimento**. Utilizza **Modifica OCR** ​​per correggere errori di lettura o **aggiungere** testo sfuggito.
7. Inserisci il **Nome del veicolo** (richiesto), più marca/modello/anno/targa come preferisci.
8. Tocca **Crea veicolo** o **Salva modifiche** (richiede nome + foto di riferimento per un nuovo veicolo).

![Aggiungi campi modulo nuovo veicolo](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Punti di riferimento: correggi ciò che manca a Discovery

Dopo **Mostra punti di riferimento**, scorrere l'elenco e correggere i valori. A volte nei motori mancano le cifre piccole (ad esempio l'orologio **60** in basso a destra nel quadro strumenti). Utilizza **Modifica OCR** ​​per aggiungerli o correggerli in modo che l'identità del veicolo rimanga affidabile.

![Finestra di dialogo Punti di riferimento: rivedi e modifica OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Digitazione senza una foto perfetta

Puoi comunque utilizzare l'app selezionando un veicolo e **digitando** contachilometri, volume e costo in Compila: l'OCR è facoltativo per ogni campo. L'importazione della galleria funziona per la foto di riferimento quando preferisci non scattare in-app.

**Suggerimento:** dopo la sincronizzazione del foglio di calcolo, le definizioni dei veicoli (ritagliati, punti di riferimento) risiedono nel database locale: non è necessario riaprire Gestisci veicoli da riempire per utilizzarle.

---

## <a id="backups-and-multi-device-sync"></a>Backup e sincronizzazione multi-dispositivo

L'app è progettata in modo che **più telefoni o tablet possano condividere gli stessi dati della flotta** e che tu possa conservare una **copia dei tuoi dati e delle tue foto fuori dal dispositivo**. Ciò avviene con le destinazioni che **tu** configuri nei **tuoi** account o nei **tuoi** server self-hosted, non in un "cloud spese veicoli" gestito dall'azienda che altre persone possono vedere.

### <a id="what-runs-where"></a>Cosa corre dove

| Gentile | Cosa memorizza | Uso tipico |
|------|----------------|-------------|
| **Foglio di calcolo/Sincronizzazione tabellare** | Veicoli, rifornimenti carburante, spese (righe e schede) | Unione multi-dispositivo + backup strutturato |
| **Backup foto** | Immagini binarie (trattino/pompa/ricevuta/foto di riferimento) | Backup foto + ripristino file mancanti |

Puoi configurare **destinazioni multiple** di ogni tipo (soft cap per tipo). Gli operatori manuali **Sincronizza ora** e **in background** eseguono quelli abilitati.

### <a id="offline-first"></a>Prima offline

- **Non è necessaria alcuna rete** per aggiungere un rifornimento, una spesa o una ricevuta. Tutto viene salvato **prima localmente**.
- Quando la rete è disponibile, la sincronizzazione e il backup delle foto vengono eseguiti come **attività in background** (secondo una pianificazione impostata e quando tocchi **Sincronizza ora**). Gli errori vengono visualizzati come testo rosso sotto le righe Impostazioni e un **!** nella barra del titolo dell'app.

### <a id="your-accounts-only"></a>Solo i tuoi account

L'accesso e i token rimangono sul dispositivo per i provider scelti (Google, Microsoft, chiavi S3, URL self-hosted e così via). Le destinazioni sono sotto il **pieno controllo dell'utente**: il tuo account Google, il tuo OneDrive, il tuo bucket MinIO, il tuo host EtherCalc, ecc. Nulla viene condiviso con altri utenti di Spese veicolo tramite un backend condiviso.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Obiettivi supportati: dati (foglio di calcolo/tabella)

Configurato in **Menu → Sincronizzazione → Sincronizzazione foglio di calcolo** (raggiungibile anche dalle righe di riepilogo delle Impostazioni). Opzioni di selezione di prima classe:

| Obiettivo | Note |
|--------|--------|
| **Fogli Google** | Impostazione predefinita comune; schede per veicoli, spese e carburante per veicolo |
| **Eccelle** | Cartella di lavoro Microsoft tramite rilegatura in stile Graph/OneDrive |
| **EtherCalc** | Stanze di fogli di calcolo collaborativi self-hosted |
| **Altro →** backend implementati | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Differito/non ancora headless (elencato sotto Altro ma non completamente implementato): OnlyOffice, Collabora. Vedi anche [indice self-host](reference/self-host/INDEX.md).

CSV **esporta/importa** (ZIP con lo stesso layout della scheda) è disponibile da Impostazioni come backup portatile, indipendente dalla sincronizzazione live.

### <a id="supported-targets--photos-image-backup"></a>Obiettivi supportati: foto (backup immagine)

Configurato in **Menu → Sincronizzazione → Backup foto** (anche dalle righe di riepilogo Impostazioni):

| Obiettivo | Note |
|--------|--------|
| **Google Drive** | Cartella scelta (sfoglia o incolla l'URL) |
| **OneDrive** | Account Microsoft + prefisso percorso |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO e altri endpoint compatibili con S3 |
| **Altro** | Archiviazione supportata da rclone (ad esempio WebDAV, SFTP e altri telecomandi selezionati disponibili nel selettore in-app) |

Impostazione dei cheatsheet per obiettivi tabulari e foto self-hosted: [indice self-host](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Comportamento multi-dispositivo (breve)

- Le righe si uniscono per **Sincronizza ID** con **ultima scrittura vince** sui timestamp **Aggiornati**.
- Le eliminazioni sono soft; una modifica più recente su un altro dispositivo può ripristinare una riga.
- Inserendo lo **stesso riempimento due volte** su due dispositivi si creano **due righe**: elimina quelle extra quando noti.
- Maggiori dettagli: [Note sul comportamento di sincronizzazione](#sync-behavior-notes) e [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Esempio: aggiungi Fogli Google (dati)

1. **Menu → Sincronizzazione → Sincronizzazione foglio di calcolo** (o Impostazioni → Sincronizzazione foglio di calcolo).

![Elenco sincronizzazione fogli di calcolo](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Tocca **Aggiungi destinazione foglio di calcolo**.

![Selettore fornitore](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Scegli **Fogli Google**.

![Modulo Fogli Google](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Accedi con Google** una volta: l'accesso ti consente anche di navigare. Nome visualizzato → incolla un **URL del foglio** (ancora valido) o **🔍** sfoglia. La navigazione si apre su **Tutti e condivisi** così puoi scegliere un foglio di lavoro esistente di tua proprietà o condiviso con te. Quindi pianifica le opzioni → abilita → salva.
5. **Sincronizza ora** una volta per creare/aggiornare le schede: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Esempio: aggiungi Google Drive (foto)

1. **Menu → Sincronizzazione → Backup foto** (o Impostazioni → Backup foto).

![Elenco backup foto](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Tocca **Aggiungi destinazione foto**.

![Selettore fornitore di foto](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Scegli **Google Drive**.

![Modulo Google Drive](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Accedi con Google (Drive)** → URL/sfoglia cartella opzionale → abilita → salva → **Sincronizza ora**.

La **Sincronizzazione ora** manuale per le foto è un passaggio completo; il backup in background in genere elabora i caricamenti **solo in attesa** in base a una pianificazione.

### <a id="sync-behavior-notes"></a>Note sul comportamento di sincronizzazione

- Dopo l'aggiornamento dell'app potresti visualizzare brevemente **"Aggiornamento del database dopo l'aggiornamento..."** (backfill dell'ID di sincronizzazione locale).
- Se una sincronizzazione viene interrotta, la successiva sincronizzazione **riuscita** riunisce e ripara le schede remote.
- Errori: riepilogo rosso sulle carte Sincronizzazione + **!** nella barra dell'app.

---

## <a id="fill-up-fuel"></a>Riempimento (carburante)

Questa è la **schermata iniziale** quando apri l'app.

### <a id="vehicle-selection-usually-automatic"></a>Selezione del veicolo (solitamente automatica)

**Non** è necessario ritirare prima il veicolo. Quando i veicoli hanno **punti di riferimento** impostati in Gestisci veicoli, Compila **rileva automaticamente quale veicolo** dall'immagine del cruscotto dopo aver acquisito il contachilometri. Puoi comunque aprire il menu a discesa **Veicolo** per eseguire l'override, se necessario.

### <a id="aim-at-the-odometer"></a> Mira al contachilometri

Rimani in modalità contachilometri e inquadra il quadro strumenti. Istruzioni: *Mirare al contachilometri. Tocca l'otturatore per scattare.*

![Riempimento: telecamera live sul contachilometri (esempio di immagine del trattino in anteprima)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Dopo l'otturatore del contachilometri

L'OCR compila **Odo** e tenta di abbinare il veicolo ai punti di riferimento (rivedi entrambi se necessario). Il pulsante principale diventa **Riprova** per ripetere la ripresa. L'istruzione riassume la lettura.

![Riempi - risultato OCR contachilometri](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Modalità pompa (costo e volume)

1. Toccare **↕** per passare alla modalità pompa: *Mirare al display della pompa (costo/volume). Tocca l'otturatore.*
2. Cattura i totali della pompa. I campi costo e volume si riempiono; utilizzare **↔** se vengono scambiati.
3. Tocca valuta o **G/L** se necessario, quindi **Salva** (disco). I campi vuoti comportano un **riempimento parziale** (ancora consentito).

![Riempi - anteprima pompa/totali (esempio immagine pompa in anteprima)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Rimani su Compila per la fermata successiva (i campi si svuotano dopo il salvataggio). Lavora completamente **offline**; la sincronizzazione viene eseguita successivamente in background quando configurata.

### <a id="manual-entry-no-camera--bad-ocr"></a>Inserimento manuale (nessuna fotocamera/OCR difettoso)

1. Tocca **Odo**, **costo** o **volume** e digita i valori (in verticale viene utilizzata la tastiera di sistema; in orizzontale viene utilizzata una tastiera su schermo).
2. Scegli o conferma il **Veicolo** se il rilevamento automatico non è stato eseguito.
3. Salva come sopra.

### <a id="modes-and-borders"></a>Modalità e confini

- **Bordo verde** attorno al veicolo+odo → acquisizione/modifica contachilometri.
- **Bordo verde** attorno a costo+volume → modalità pompa.
- **Salva** rimane disabilitato finché non viene selezionato un veicolo e almeno uno tra odo/costo/volume contiene dati e l'OCR non è ancora in esecuzione.

Suggerimento sullo schermo (sotto la riga di istruzioni): *Otturatore = cattura · Disco = salva · ↕ = modalità odo/pompa · ↔ = costo/volume di scambio.*

---

## <a id="expenses"></a>Spese

### <a id="new-expense"></a>Nuova spesa

Menu → **Nuova spesa**.

![Nuovo modulo spese e controlli della fotocamera (esempio di immagine del trattino nella fascia della fotocamera quando vuota)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Salva** (disco), **otturatore** (foto della ricevuta) o **galleria** (scegli immagine).
2. Compila **Data**, **Veicolo**, **Fornitore**, **Descrizione**, **Importo** (simbolo della valuta selezionabile), **Categoria**, opzionale **Contachilometri**.
3. Ricevute multipagina: acquisisci pagine aggiuntive se l'interfaccia utente offre l'impaginazione (la pagina 0 è la ricevuta principale).
4. **Salva** nell'archivio (prima in locale; il backup delle foto e la sincronizzazione del foglio di calcolo avvengono in background quando configurati).

### <a id="expense-list"></a>Lista spese

Menu → **Rapporti** → **Elenco spese**: sfoglia le spese passate non legate al carburante; aprire un elemento da modificare.

![Lista spese](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Modifica spesa

Apre una riga dall'elenco. Venditore, importo, categoria, veicolo e descrizione corretti. Se la ricevuta è solo nel backup della foto (nessun file locale leggibile), utilizza **Recupera immagine dall'archivio** quando viene visualizzata (funziona con tutte le destinazioni foto configurate).

![Modifica spesa](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Inizia viaggio

Menu → **Inizia viaggio** (dopo Compila il cassetto). Cattura o inserisci il contachilometri, scegli il tipo di viaggio, salva con l'icona **disco**. **Stop** è una scorciatoia per Personale ora nella posizione GPS mantenuta. Utilizza **ⓘ** per i promemoria di controllo.

![Inizia viaggio](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Gli inizi del viaggio vengono memorizzati come righe di carburante con un **Tipo di viaggio** (non riempimenti normali). Appaiono in **Rapporti → Miglia di viaggio**, non in Cronologia carburante.

---

## <a id="reports"></a>Rapporti

Menu → **Rapporti** apre l'hub del prodotto (riepilogo storico + schede catalogo). Questa è l'unica superficie per i resoconti del prodotto: non esiste un elemento separato del cassetto "Rapporti e grafici".

![Hub rapporti](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Apri una scheda per la modalità veicolo (**Tutti / Ciascuno / Singolo**), filtri periodici, grafici e condivisione (**TESTO / CSV / PDF**). Barra superiore sul rapporto figli: **☰ + ←** (e **ⓘ** se registrato).

### <a id="time-based-reports"></a>Rapporti basati sul tempo

La carta cartografica principale. Metriche opzionali (mpg, volume/distanza come G/mi, prezzo unitario come $/G, costo/distanza, $ mensili, miglia di viaggio, % di viaggio per tipo) con contenitori **Smooth** e **scale Y indipendenti** (economia a sinistra; denaro e famiglie di viaggio a destra).

![Rapporti basati sul tempo](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Report basati sul tempo - metriche a scorrimento](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Dettagli della matematica economica: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Riempi la cronologia rispetto alla cronologia del carburante

- **Rapporti → Compila cronologia**: riempimenti cronologici per i filtri dei report (**solo riempimenti**; nessun inizio viaggio).

![Compila rapporto cronologia](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Cronologia carburante** (se presente nella navigazione della tua build): inventario di rifornimento per veicolo, anche solo rifornimento; tocca una riga per modificarla.

### <a id="trip-miles"></a>Miglia di viaggio

**Rapporti → Miglia di viaggio**: miglia per tipo, grafici e un **elenco cronologico di inizio viaggio/segmento**. Tocca un inizio reale per aprire **Modifica riempimento** per quella riga.

![Miglia percorse](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Modifica riempimento

Da Cronologia rifornimenti, Cronologia carburante o Miglia di viaggio, aprire un rifornimento. Layout: veicolo e contachilometri, **valuta prima del costo**, volume, note. Il tipo di viaggio viene visualizzato solo quando la riga indica l'inizio di un viaggio. La posizione ha un riepilogo più **Dettagli sulla posizione**. Foto locale mancante con identità cloud: **Recupera immagine dall'archivio**.

![Modifica riempimento: recupera dall'archivio quando manca la foto locale](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Le altre schede del catalogo includono le spese per categoria, il riepilogo del veicolo e l'elenco delle spese.

Money utilizza la valuta di ogni riga quando impostata. I totali in valute miste mostrano **totali parziali per valuta** (nessuna conversione FX silenziosa).

---

## <a id="syncing"></a>Sincronizzazione

Menu → **Sincronizzazione** è l'hub per fogli di calcolo e destinazioni foto (non solo sepolto in Impostazioni).

![Hub di sincronizzazione](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Schede per **Sincronizzazione fogli di calcolo** e **Backup foto** con stato breve, **Sincronizzazione** per quel tipo e **›** nell'elenco delle destinazioni.
- Apri una destinazione per **Verifica connessione** e **Sincronizza ora (questa destinazione)** / tutto configurato.
- I **Dettagli** del fallimento e il **!** rosso nella barra del titolo finiscono qui.
- Configurazione dettagliata di Fogli Google e Drive: [Backup e sincronizzazione multi-dispositivo](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Impostazioni (preferenze locali)

Menu → **Impostazioni**.

![Impostazioni](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Altre impostazioni (scorrite)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Per le destinazioni, preferisci **Menu → Sincronizzazione**. Le impostazioni potrebbero comunque mostrare righe di riepilogo che aprono gli stessi elenchi.

### <a id="local-preferences-common"></a>Preferenze locali (comune)

- **Salva foto delle ricevute di carburante** / **Salva foto delle spese localmente**: conserva le immagini sul dispositivo (può richiedere l'autorizzazione per le foto).
- **Riproduci suono otturatore**
- **Valuta** / **Unità volume**: impostazioni predefinite dell'app (di sistema o esplicite). La modifica dell'unità di volume con i dati del carburante esistenti può offrire una finestra di dialogo di conversione.
- **Modalità oscura**
- **Suggerimenti per la configurazione**: riapri i tutorial di prima esecuzione del veicolo/sincronizzazione.
- **Compilazione debug** / **Mostra schermate dell'esperimento (sviluppo)** — avanzato; lasciare fuori per l'uso quotidiano. Le schermate dell'esperimento non sono documentate qui.

CSV **esportazione/importazione** (ZIP di veicoli/spese/schede carburante) è disponibile da Impostazioni quando offerto dalla build corrente.

---

## <a id="help--about"></a>Aiuto e informazioni

![Aiuto](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Informazioni](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Guida**: avvio rapido sul dispositivo, tutorial di configurazione, collegamento a questo manuale, indice di configurazione dell'host autonomo.
- **Informazioni**: versione, licenze, GitHub, questo manuale (raggruppato offline + HTML online quando pubblicato).

---

## <a id="related-docs"></a>Documenti correlati

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — breve guida quotidiana
- [self-host/INDEX.md](../reference/self-host/INDEX.md) - configurazione foto/tabella ospitata autonomamente
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — unisci, ripristina, duplica
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — dettaglio delle metriche economiche
