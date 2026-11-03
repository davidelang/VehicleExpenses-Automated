# <a id="vehicle-expenses-automated--user-manual"></a>Despeses del vehicle automatitzades — Manual d'usuari

> **Edita la font (Markdown).** Els navegadors i el lector de l'aplicació obren l'**HTML renderitzat**:
> - Web: [`docs/user-manual/index.html`](index.html) (regenera amb `./scripts/render-user-manual.sh`)
> - Aplicació: Ajuda / Quant a → manual complet (HTML inclòs + captures de pantalla)
>
> No apunteu els usuaris finals a URL `.md` en brut: els navegadors només mostren text sense format.

Seguiment de la càmera primer per omplir combustible i despeses del vehicle, amb sincronització i còpia de seguretat de diversos dispositius opcionals als **els vostres** comptes al núvol.

Aquest és el **manual complet** (captures de pantalla + cada pas). Al telèfon, **Menú → Ajuda** és una guia inicial més curta.

**No s'inclou aquí:** Importa imatges antigues, experiment d'alineació i experiment de bomba (eines per a desenvolupadors/avançades).

---

## <a id="table-of-contents"></a>Taula de continguts

1. [El que necessites](#what-you-need)
2. [Icones d'un cop d'ull](#icons-at-a-glance)
3. [Obre el menú](#open-the-menu)
4. [Configuració per primera vegada: Gestiona els vehicles](#first-time-setup-manage-vehicles)
5. [Còpies de seguretat i sincronització de diversos dispositius](#backups-and-multi-device-sync)
6. [Omplir (combustible)](#fill-up-fuel)
7. [Comença el viatge](#start-trip)
8. [Despeses](#expenses)
9. [Informes](#reports)
10. [Configuració (preferències locals)](#settings-local-preferences)
11. [Sincronitzant](#syncing)
12. [Ajuda i informació](#help--about)
13. [Documents relacionats](#related-docs)

---

## <a id="what-you-need"></a>El que necessites

- Telèfon o tauleta Android.
- Per obtenir el millor OCR: una visió clara del **odòmetre del tauler** i dels **totals de la bomba** (o escriviu els números a mà).
- Opcional: comptes **que controleu** per a dades del full de càlcul i/o còpies de seguretat de fotos (vegeu [Còpia de seguretat i sincronització de diversos dispositius](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Icones d'un cop d'ull

Apareixen a les pantalles principals. Conèixer-los estalvia molta caça.

| On | Icona / control | Què fa |
|--------|-----------------|--------------|
| Barra superior | **☰ Menú** (hamburguesa) | Obre el calaix de navegació |
| Barra superior | **ⓘ** (ajuda de la pàgina) | Ajuda breu per a la pàgina **actual** (al costat del menú quan estigui disponible) |
| Barra superior | **`?N`** (groc) | Preguntes pendents de revisió d'importació: obre Revisió d'importació |
| Barra superior | **!** (vermell) | Fa poc s'ha produït un error en un full de càlcul o una destinació de fotos: obriu **Sincronització** per solucionar-ho |
| Barra superior | **☰ + ←** | Informe dels nens i la llista de despeses mostra **menú i torna** junts; El centre d'informes només és el menú |
| Configuració / edició de combustible | **←** | Enrere (la configuració del full de càlcul/foto i l'edició de combustible es mantenen enrere) |
| Omplir | **Cercle blanc** (obturador) | Captura l'odòmetre o la pantalla de la bomba per a OCR |
| Omplir | **Disc / Desa** | Guardar el farciment (necessita un vehicle i almenys un d'odo/volum/cost) |
| Omplir | **↕ fletxes** (interruptor de mode) | Commuta entre **mode odòmetre** i **mode bomba (cost/volum)**. La vora verda destaca el grup de camps actiu |
| Omplir | **↔ fletxes** (entre cost i volum) | Canvia el cost i el volum si l'OCR els posa als camps equivocats |
| Omplir | **Amplia 1x / …** | Relacions de zoom de la càmera quan la lent les admet |
| Omplir (després de la captura) | **Actualitzar** al botó principal | Descarta la vista prèvia i torna a la càmera en directe |
| Omplir (mentre es processa) | **X** al botó principal | Cancel·la la captura/OCR en curs |
| Despesa | **Desa** | Estalvieu la despesa |
| Despesa | **Cercle de l'obturador** | Fes una foto del rebut |
| Despesa | **Galeria** | Trieu una imatge de rebut de la biblioteca |
| Despesa | **Reprendre** | Esborra la foto del rebut actual i torna a disparar |
| Despesa / Gestió de Vehicles | **+ / −** FABs | Amplia la vista prèvia de la foto |
| Diàleg de punts de referència | **Edita OCR** ​​| Corregiu o afegiu el text de referència que els motors s'han perdut |
| Full de càlcul / Formularis fotogràfics | **🔍 Cerca** | Navegueu per Google Drive per trobar un full o una carpeta (després d'iniciar la sessió) |

Els símbols de moneda als camps de cost i **G/L** als camps de volum es poden tocar: obriu un petit menú per canviar la moneda o els galons i els litres per a aquesta entrada.

---

## <a id="open-the-menu"></a>Obre el menú

1. Toqueu **☰** a la part superior esquerra.
2. Trieu una pàgina.

![Calaix de navegació](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Caixó principal:** Omplir · Iniciar viatge · Gestionar vehicles · Despesa nova · **Informes** · Configuració · Sincronització · Ajuda · Quant a.

**Caixó d'experiments** (Configuració → Mostra les pantalles d'experiment): Experiment d'alineació · Experiment de bomba · **Importa imatges antigues**.

**A través del centre d'informes (no al calaix principal):** Llista de despeses · Omplir l'historial.

---

## <a id="first-time-setup-manage-vehicles"></a>Configuració per primera vegada: gestiona els vehicles

L'OCR i la **concordança automàtica de vehicles** funcionen millor després de registrar cada vehicle amb una **foto del tauler de comandament**, executar **Discovery**, establir el tall de l'odòmetre a partir d'una llavor de paleta i desar punts de referència per a aquest guió.

### <a id="open-manage-vehicles"></a>Obre Gestiona vehicles

Menú → **Gestiona vehicles**. Trieu un vehicle (o **Afegeix un vehicle nou**).

![Gestiona els vehicles amb guió de referència, cultius i descobriment](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Afegeix o edita un vehicle

1. Obriu el menú desplegable **Vehicle** → trieu un vehicle o **Afegeix un vehicle nou**.
2. Captura o tria una **foto de referència del guió** clara (tauler d'instruments complet, ben il·luminat, telèfon aproximadament encès). Utilitzeu **Fes foto** o **Galeria**.
3. Toqueu **Executar descobriment**: la detecció de pales col·loca **llavors** grogues a la foto (els punts de referència utilitzen el mateix `"ML Kit"` JSON que abans).
4. Toqueu **Seleccioneu Odo**, toqueu la llavor dels dígits, ajusteu les nanses i després **Desa**.
5. **Edita Odo** ajusta un quadre que ja hi és. **Esborra Odo** l'elimina i el botó diu **Selecciona Odo**.
6. Revisa amb **Mostra punts de referència**. Utilitzeu **Edita OCR** ​​per corregir errors de lectura o **afegir** text que s'ha perdut.
7. Ompliu **Nom del vehicle** (obligatori), més marca/model/any/placa com vulgueu.
8. Toqueu **Crea un vehicle** o **Desa els canvis** (requereix nom + foto de referència per a un vehicle nou).

![Afegeix nous camps de formulari de vehicle](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Fonts: solucioneu el que Discovery ha perdut

Després de **Mostrar punts de referència**, desplaceu-vos per la llista i corregiu els valors. De vegades, els motors perden dígits petits (per exemple, un rellotge **60** a la part inferior dreta del clúster). Utilitzeu **Edita OCR** ​​per afegir-los o solucionar-los perquè la identitat del vehicle sigui fiable.

![Diàleg de punts de referència — revisa i edita OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Escrivint sense una foto perfecta

Encara podeu utilitzar l'aplicació seleccionant un vehicle i **escrivint** odòmetre, volum i cost a Omplir: l'OCR és opcional per a cada camp. La importació de la galeria funciona per a la foto del guió de referència quan preferiu no disparar des de l'aplicació.

**Consell:** després de la sincronització del full de càlcul, les definicions de vehicles (cultius, punts de referència) es troben a la base de dades local; no cal que torneu a obrir Gestiona els vehicles per omplir-les per utilitzar-les.

---

## <a id="backups-and-multi-device-sync"></a>Còpies de seguretat i sincronització de diversos dispositius

L'aplicació està dissenyada de manera que **diversos telèfons o tauletes puguin compartir les mateixes dades de la flota**, i així podeu mantenir una **còpia de les vostres dades i fotos fora del dispositiu**. Això es fa amb les destinacions que **que** configureu a **els vostres** comptes o **els vostres** servidors autoallotjats, no un "núvol de despeses de vehicles" dirigit per l'empresa que altres persones puguin veure.

### <a id="what-runs-where"></a>Què corre per on

| amable | Què emmagatzema | Ús típic |
|------|-----------------|--------------|
| **Full de càlcul/sincronització tabular** | Vehicles, farciments de combustible, despeses (files i pestanyes) | Combinació de diversos dispositius + còpia de seguretat estructurada |
| **Còpia de seguretat de fotos** | Imatges binàries (guionet/bomba/rebut/fotos de referència) | Còpia de seguretat de fotos + restauració dels fitxers que falten |

Podeu configurar **múltiples destinacions** de cada tipus (tapa suau per tipus). Els treballadors manuals **Sincronitza ara** i **de fons** executen els activats.

### <a id="offline-first"></a>Primer fora de línia

- **No es requereix cap xarxa** per afegir un farciment, una despesa o un rebut. Tot es desa **primer localment**.
: quan la xarxa estigui disponible, la sincronització i la còpia de seguretat de les fotos s'executen com a **tasques en segon pla** (en una programació que hàgiu establert i quan toqueu **Sincronitza ara**). Els errors es mostren com a text vermell a les files de configuració i un **!** a la barra de títol de l'aplicació.

### <a id="your-accounts-only"></a>Només els vostres comptes

L'inici de sessió i els testimonis romanen al dispositiu per als proveïdors que trieu (claus de Google, Microsoft, S3, URL autoallotjades, etc.). Les destinacions estan sota el **control total de l'usuari**: el vostre compte de Google, el vostre OneDrive, el vostre cub de MiniIO, el vostre amfitrió EtherCalc, etc. No es comparteix res amb altres usuaris de Vehicle Expenses mitjançant un backend compartit.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Objectius admesos — dades (full de càlcul/tabular)

Configurat a **Menú → Sincronització → Sincronització del full de càlcul** (també es pot accedir des de les files de resum de la configuració). Opcions de selecció de primera classe:

| Objectiu | Notes |
|---------|--------|
| **Fulls de càlcul de Google** | Per defecte comú; pestanyes per a vehicles, despeses i combustible per vehicle |
| **Excel** | Llibre de treball de Microsoft mitjançant l'enllaç d'estil Graph / OneDrive |
| **EtherCalc** | Sales de fulls de càlcul col·laboratius autoallotjades |
| **Altres →** backends implementats | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Ajornat / encara no sense cap (amb la llista d'Altres però no s'ha implementat completament): OnlyOffice, Collabora. Vegeu també [índex d'autoamfitrió](reference/self-host/INDEX.md).

CSV **exportació/importació** (ZIP del mateix disseny de pestanyes) està disponible a Configuració com a còpia de seguretat portàtil, independentment de la sincronització en directe.

### <a id="supported-targets--photos-image-backup"></a>Objectius admesos: fotos (còpia de seguretat de la imatge)

Configurat a **Menú → Sincronització → Còpia de seguretat de fotos** (també des de les files de resum de la configuració):

| Objectiu | Notes |
|---------|--------|
| **Google Drive** | Carpeta que trieu (buscar o enganxar l'URL) |
| **OneDrive** | Compte de Microsoft + prefix de camí |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO i altres terminals compatibles amb S3 |
| **Altres** | Emmagatzematge amb còpia de seguretat de rclone (p. ex., WebDAV, SFTP i altres comandaments a distància seleccionats disponibles al selector de l'aplicació) |

Configura fulls de trucs per a objectius tabulars i fotos autoallotjats: [índex d'autoallotjament](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Comportament de diversos dispositius (curt)

: les files es combinen per **ID de sincronització** amb **últimes victòries d'escriptura** a les marques de temps **Actualitzades**.
- Les supressions són suaus; una edició més recent en un altre dispositiu pot restaurar una fila.
: introduir el **mateix farciment dues vegades** en dos dispositius crea **dues files**; suprimiu l'extra quan ho noteu.
- Més detalls: [Notes de comportament de sincronització](#sync-behavior-notes) i [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Exemple: afegir Fulls de càlcul de Google (dades)

1. **Menú → Sincronització → Sincronització del full de càlcul** (o Configuració → Sincronització del full de càlcul).

![Llista de sincronització de fulls de càlcul](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Toqueu **Afegeix una destinació al full de càlcul**.

![Selector de proveïdors](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Trieu **Fulls de càlcul de Google**.

![Formulari de Fulls de Google](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Inicieu la sessió amb Google** una vegada: aquest inici de sessió també us permet navegar. Nom de visualització → enganxeu un **URL del full** (encara vàlid) o **🔍** navega. La navegació s'obre a **Tot i compartit** perquè pugueu triar un full de càlcul existent que teniu o que es comparteixi amb vosaltres. A continuació, programa les opcions → activa → desa.
5. **Sincronitza ara** una vegada per crear/actualitzar pestanyes: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Exemple: afegir Google Drive (fotos)

1. **Menú → Sincronització → Còpia de seguretat de fotos** (o Configuració → Còpia de seguretat de fotos).

![Llista de còpia de seguretat de fotos](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Toqueu **Afegeix una destinació de foto**.

![Selector de proveïdor de fotos](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Trieu **Google Drive**.

![Formulari de Google Drive](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Inicieu la sessió amb Google (Drive)** → URL de carpeta opcional/explora → activa → desa → **Sincronitza ara**.

Manual **Sincronitzar ara** per a fotos és una passada completa; La còpia de seguretat en segon pla normalment processa càrregues **només pendents** segons una programació.

### <a id="sync-behavior-notes"></a>Sincronitza les notes de comportament

- Després de l'actualització de l'aplicació, podeu veure breument **"Actualització de la base de dades després de l'actualització..."** (emplenament d'identificador de sincronització local).
- Si una sincronització s'interromp, la següent sincronització **èxit** es torna a combinar i repara les pestanyes remotes.
- Errors: resum vermell a les targetes de sincronització + **!** a la barra de l'aplicació.

---

## <a id="fill-up-fuel"></a>Omplir (combustible)

Aquesta és la **pantalla d'inici** quan obriu l'aplicació.

### <a id="vehicle-selection-usually-automatic"></a>Selecció de vehicles (normalment automàtica)

**No** cal que trieu el vehicle primer. Quan els vehicles tinguin **punts de referència** configurats a Gestiona vehicles, emplena **detecta automàticament quin vehicle** des de la imatge del guió després de capturar el comptaquilòmetres. Encara podeu obrir el menú desplegable **Vehicle** per anul·lar-lo si cal.

### <a id="aim-at-the-odometer"></a>Apunta al comptaquilòmetres

Mantingueu-vos en mode odòmetre i emmarqueu el clúster. Instrucció: *Apunteu al comptaquilòmetres. Toca l'obturador per capturar.*

![Omplir — càmera en directe al comptaquilòmetres (exemple d'imatge del guió a la vista prèvia)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Després de l'obturador del comptaquilòmetres

OCR omple **Odo** i intenta fer coincidir el vehicle a partir de punts de referència (reviseu tots dos si cal). El botó principal es converteix en **Reintentar** per tornar a disparar. La instrucció resumeix la lectura.

![Omplir — resultat OCR del comptaquilòmetres](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Mode de bomba (cost i volum)

1. Toqueu **↕** per canviar al mode de bomba: *Apunteu a la pantalla de la bomba (cost/volum). Toca l'obturador.*
2. Captureu els totals de la bomba. Els camps de cost i volum omplen; utilitzeu **↔** si s'intercanvien.
3. Toqueu moneda o **G/L** si cal i després **Desa** (disc). Els camps buits fan un **emplenament parcial** (encara es permet).

![Emplenar — vista prèvia de la bomba / totals (exemple d'imatge de la bomba a la vista prèvia)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Et quedes a Emplenar per a la següent parada (camps esborrats després de desar). Treballar completament **fora de línia**; la sincronització s'executa més tard en segon pla quan es configura.

### <a id="manual-entry-no-camera--bad-ocr"></a>Entrada manual (sense càmera/OCR dolent)

1. Toqueu **Odo**, **cost** o **volum** i escriviu els valors (el vertical utilitza el teclat del sistema; l'horizontal utilitza un teclat en pantalla).
2. Trieu o confirmeu el **Vehicle** si la detecció automàtica no s'ha executat.
3. Desa com a dalt.

### <a id="modes-and-borders"></a>Modes i vores

- **Bord verd** al voltant del vehicle+odo → captura/edició del comptaquilòmetres.
- **Bord verd** al voltant de cost+volum → mode bomba.
- **Desa** roman desactivat fins que es selecciona un vehicle i almenys un d'odo/cost/volum té dades i l'OCR encara no s'està executant.

Consell a la pantalla (a sota de la línia d'instruccions): *Obturador = captura · Disc = desar · ↕ = mode odo/bomba · ↔ = cost/volum d'intercanvi.*

---

## <a id="expenses"></a>Despeses

### <a id="new-expense"></a>Nova despesa

Menú → **Nova despesa**.

![Formulari de despeses i controls de càmera nous (exemple d'imatge de guió a la banda de la càmera quan està buida)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Desa** (disc), **obturador** (foto del rebut) o **galeria** (tria la imatge).
2. Ompliu **Data**, **Vehicle**, **Proveïdor**, **Descripció**, **Import** (símbol de moneda activable), **Categoria**, **Odòmetre** opcional.
3. Rebuts de diverses pàgines: captureu pàgines addicionals si la IU ofereix paginació (la pàgina 0 és el rebut principal).
4. **Desa** per emmagatzemar (local primer; la còpia de seguretat de fotos i la sincronització del full de càlcul es produeixen en segon pla quan es configuren).

### <a id="expense-list"></a>Llista de despeses

Menú → **Informes** → **Llista de despeses**: explora les despeses anteriors que no són de combustible; obre un element per editar.

![Llista de despeses](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Edita la despesa

Obre una fila de la llista. Correcte el proveïdor, la quantitat, la categoria, el vehicle i la descripció. Si el rebut només es troba a la còpia de seguretat de la foto (no hi ha fitxer local llegible), utilitzeu **Obtén la imatge de l'arxiu** quan es mostri (funciona amb les destinacions de fotos configurades).

![Edita despesa](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Comença el viatge

Menú → **Comença el viatge** (després d'omplir el calaix). Captura o introdueix el comptequilòmetres, tria el tipus de viatge i desa amb la icona de **disc**. **Stop** és una drecera per a Personal ara a la ubicació GPS mantinguda. Utilitzeu **ⓘ** per als recordatoris de control.

![Comença el viatge](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Els inicis de viatge s'emmagatzemen com a files de combustible amb un **Tipus de viatge** (no d'ompliment normal). Apareixen a **Informes → Milles de viatge**, no a Historial de combustible.

---

## <a id="reports"></a>Informes

Menú → **Informes** obre el centre de productes (resum de tots els temps + targetes de catàleg). Aquesta és l'única superfície d'informes de producte: no hi ha cap element separat del calaix "Informes i gràfics".

![Centre d'informes](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Obre una targeta per al mode vehicle (**Tot/Cada/Individual**), filtres de períodes, gràfics i compartir (**TEXT/CSV/PDF**). Barra superior dels nens denunciats: **☰ + ←** (i **ⓘ** quan estigui registrat).

### <a id="time-based-reports"></a>Informes basats en el temps

La carta principal del gràfic. Mètriques opcionals (mpg, volum/distància com ara G/mi, preu unitari com ara $/G, cost/distància, $ mensuals, milles de viatge, % de viatge per tipus) amb contenidors **Smooth** i **escales Y independents** (economia a l'esquerra; diners i famílies de viatges a la dreta).

![Informes basats en el temps](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Informes basats en el temps — mètriques desplaçades](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Detalls de matemàtiques d'economia: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Historial d'ompliment vs historial de combustible

- **Informes → Historial d'emplenament**: emplenaments cronològics per als filtres d'informes (**només omplert**; no s'inicia cap viatge).

![Emplenar l'informe de l'historial](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Historial de combustible** (si està present a la navegació de la vostra construcció): inventari d'ompliment per vehicle, també s'omple només; toqueu una fila per editar.

### <a id="trip-miles"></a>Viatge milles

**Informes → Milles de viatge** — milles per tipus, gràfics i una **llista d'inici/segments** cronològica. Toqueu un inici real per obrir **Edita l'emplenament** per a aquesta fila.

![Viatge milles](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Edita el farciment

Des de l'historial d'ompliment, l'historial de combustible o les milles de viatge, obriu un ompliment. Distribució: vehicle i comptaquilòmetres, **moneda abans del cost**, volum, notes. El tipus de viatge només apareix quan la fila és un inici de viatge. La ubicació té un resum més **Detalls de la ubicació**. Falta una foto local amb la identitat del núvol: **Obtén la imatge de l'arxiu**.

![Edita l'emplenament — Obtén de l'arxiu quan falta una foto local](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Altres targetes de catàleg inclouen despeses per categoria, resum del vehicle i llista de despeses.

Els diners utilitzen la moneda de cada fila quan s'estableix. Els totals de monedes mixtes mostren **subtotals per moneda** (sense conversió silenciosa de divises).

---

## <a id="syncing"></a>Sincronització

Menú → **Sincronització** és el centre per a les destinacions de fulls de càlcul i fotos (no només enterrat a Configuració).

![Centre de sincronització](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Targetes per a **Sincronització del full de càlcul** i **Còpia de seguretat de fotos** amb estat breu, **Sincronització** per a aquest tipus i **›** a la llista de destinació.
- Obriu una destinació per a **Connexió de prova** i **Sincronitzar ara (aquesta destinació)** / tot configurat.
- Falla **Detalls** i el vermell **!** a la barra de títol aterren aquí.
- Configuració pas a pas de Google Sheets i Drive: [Còpies de seguretat i sincronització de diversos dispositius](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Configuració (preferències locals)

Menú → **Configuració**.

![Configuració](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Més configuracions (desplaçades)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Per a destinacions, preferiu **Menú → Sincronització**. La configuració encara pot mostrar files de resum que obrin les mateixes llistes.

### <a id="local-preferences-common"></a>Preferències locals (comú)

- **Desa les fotos del rebut de combustible** / **Desa les fotos de despeses localment**: manté les imatges al dispositiu (pot demanar permís per a Fotos).
- **Reprodueix el so de l'obturador**
- **Moneda** / **Unitat de volum**: valors per defecte de l'aplicació (sistema o explícit). Canviar la unitat de volum amb les dades de combustible existents pot oferir un diàleg de conversió.
- **Mode fosc**
- **Consells de configuració**: torneu a obrir els tutorials de sincronització i vehicles de primera execució.
- **Emplenat de depuració** / **Mostra les pantalles d'experiment (dev)** — avançat; deixar fora per a l'ús diari. Les pantalles d'experiments no estan documentades aquí.

CSV **exportació/importació** (CIP de les pestanyes de vehicles/despeses/combustible) està disponible a Configuració quan ho ofereix la versió actual.

---

## <a id="help--about"></a>Ajuda i informació

![Ajuda](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Quant a](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Ajuda**: inici ràpid al dispositiu, tutorials de configuració, enllaç a aquest manual, índex de configuració d'auto-amfitrió.
- **Quant a** — versió, llicències, GitHub, aquest manual (agrupat fora de línia + HTML en línia quan es publica).

---

## <a id="related-docs"></a>Documents relacionats

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — guia diària curta
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — configuració de foto/tabular autoallotjada
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — combinació, recuperació, duplicats
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — detall de mètriques d'economia
