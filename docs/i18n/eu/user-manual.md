# <a id="vehicle-expenses-automated--user-manual"></a>Ibilgailuen gastuak automatizatuak — Erabiltzailearen eskuliburua

> **Editatu iturria (Markdown).** Arakatzaileek eta aplikazioko irakurgailuak **errendatutako HTML** irekitzen dute:
> - Weba: [`docs/user-manual/index.html`](index.html) (birsortu `./scripts/render-user-manual.sh`-rekin)
> - Aplikazioa: Laguntza / Honi buruz → eskuliburu osoa (HTML bilduta + pantaila-argazkiak)
>
> Ez jarri azken erabiltzaileei `.md` URL gordinak — arakatzaileek testu arrunta soilik erakusten dute.

Kameraren lehen jarraipena erregaia betetzeko eta ibilgailuen gastuetarako, aukerako gailu anitzeko sinkronizazioa eta babeskopia **zure** hodeiko kontuetan.

Hau **eskuliburu osoa** da (pantaila-argazkiak + urrats guztiak). Telefonoan, **Menua → Laguntza** hasteko gida laburragoa da.

**Ez dago hemen estalita:** Inportatu Irudi zaharrak, Lerrokatze esperimentua eta Pump Esperimentua (garatzaile/tresna aurreratuak).

---

## <a id="table-of-contents"></a>Aurkibidea

1. [Behar duzuna](#what-you-need)
2. [Ikonoak begirada batean](#icons-at-a-glance)
3. [Ireki menua](#open-the-menu)
4. [Lehenengo konfigurazioa: Kudeatu Ibilgailuak](#first-time-setup-manage-vehicles)
5. [Bakopia eta gailu anitzeko sinkronizazioa](#backups-and-multi-device-sync)
6. [Bete (erregaia)](#fill-up-fuel)
7. [Hasi bidaia](#start-trip)
8. [Gastuak](#expenses)
9. [Txostenak](#reports)
10. [Ezarpenak (tokiko hobespenak)](#settings-local-preferences)
11. [Sinkronizatzen](#syncing)
12. [Laguntza eta buruz](#help--about)
13. [Erlazionatutako dokumentuak](#related-docs)

---

## <a id="what-you-need"></a>Behar duzuna

- Android telefonoa edo tableta.
- OCR onena lortzeko: zure **arbel-odometroa** eta **ponpa totalak** ikusteko argia (edo idatzi zenbakiak eskuz).
- Aukerakoa: **zuk kontrolatzen dituzun kontuak** kalkulu-orrien datuetarako eta/edo argazkien babeskopietarako (ikusi [Babeskopiak eta gailu anitzeko sinkronizazioa](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Ikonoak begirada batean

Hauek pantaila nagusietan agertzen dira. Horiek ezagutzeak ehiza asko aurrezten du.

| Non | Ikonoa / kontrola | Zer egiten du |
|-------|-----------------|--------------|
| Goiko barra | **☰ Menua** (hanburgesa) | Nabigazio tiradera |
| Goiko barra | **ⓘ** (orriaren laguntza) | Laguntza laburra **uneko** orrialderako (menuaren ondoan dagoenean) |
| Goiko barra | **`?N`** (horia) | Inportazioen berrikuspen galderak zain — Inportatu berrikuspena | irekitzen du
| Goiko barra | **!** (gorria) | Kalkulu-orri batek edo argazki-helmuga batek huts egin du duela gutxi — ireki **Syncing** konpontzeko |
| Goiko barra | **☰ + ←** | Umeen txostena eta Gastuen zerrenda erakutsi **menua eta atzera** elkarrekin; Txostenen gunea menua soilik da |
| Ezarpenak / erregaia editatzea | **←** | Atzera (ezarpenen kalkulu-orria/argazkia eta erregaiaren edizioa atzera begira geratzen dira) |
| Bete | **Zirkulu zuria** (pertsiana) | Harrapatu odometroa edo ponpa pantaila OCR |
| Bete | **Disko / Gorde** | Gorde betetzea (ibilgailu bat behar du eta gutxienez odo / bolumena / kostua) |
| Bete | **↕ geziak** (modu etengailua) | Aldatu **odometro modua** eta **ponpa (kostua/bolumena) modua**. Ertz berdeak eremu aktiboaren taldea nabarmentzen du |
| Bete | **↔ geziak** (kostuaren eta bolumenaren artean) | Trukatu kostua eta bolumena OCR-k okerreko eremuetan jartzen baditu |
| Bete | **Zokatu 1x / …** | Kameraren zoom-erlazioak lenteak onartzen dituenean |
| Bete (harrapatu ondoren) | **Freskatu** botoi nagusian | Baztertu aurrebista eta itzuli zuzeneko kamerara |
| Bete (prozesatzen ari zaren bitartean) | **X** botoi nagusian | Utzi abian dagoen harrapaketa/OCR |
| Gastua | **Gorde** | Gorde gastua |
| Gastua | **Obturadorearen zirkulua** | Atera ordainagiria argazki bat |
| Gastua | **Galeria** | Aukeratu liburutegiko ordainagiria irudi bat |
| Gastua | **Berrekuratu** | Garbitu uneko ordainagiriaren argazkia eta atera berriro |
| Gastua / Kudeatu Ibilgailuak | **+ / −** FABak | Handitu argazkiaren aurrebista |
| Mugarriak elkarrizketa-koadroa | **Editatu OCR** ​​| Zuzendu edo gehitu mugarri testua motorrak galdutako |
| Kalkulu-orria / Argazki-inprimakiak | **🔍 Bilatu** | Arakatu Google Drive orri bat edo karpeta bat (saioa hasi ondoren) |

Moneta-ikurrak kostu-eremuetan eta **G/L** bolumen-eremuetan sakatu daitezke: ireki menu txiki bat sarrera horretarako moneta edo litroak eta litroak aldatzeko.

---

## <a id="open-the-menu"></a>Ireki menua

1. Sakatu **☰** goiko ezkerrean.
2. Aukeratu orrialde bat.

![Nabigazio tiradera](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Tirader nagusia:** Bete · Bidaia hasi · Ibilgailuak kudeatu · Gastu berriak · **Txostenak** · Ezarpenak · Sinkronizazioa · Laguntza · Honi buruz.

**Esperimentuen tiradera** (Ezarpenak → Erakutsi esperimentu-pantailak): Lerrokatzeko esperimentua · Pump esperimentua · **Inportatu Argazki zaharrak**.

**Txostenen zentroaren bidez (ez tiradera nagusia):** Gastuen zerrenda · Bete historia.

---

## <a id="first-time-setup-manage-vehicles"></a>Lehenengo konfigurazioa: Ibilgailuak kudeatu

OCR eta **ibilgailuen bat etortze automatikoa** funtzionatzen dute hobekien ibilgailu bakoitza **erreferentzia-arbeleko argazki batekin** erregistratu ondoren, **Discovery** exekutatu, kilometrajearen uzta pala-hazi batetik ezarri eta marra horretarako mugarriak gorde ondoren.

### <a id="open-manage-vehicles"></a>Ireki Kudeatu Ibilgailuak

Menua → **Kudeatu ibilgailuak**. Aukeratu ibilgailu bat (edo **Gehitu ibilgailu berria**).

![Kudeatu ibilgailuak erreferentziazko marra, laborantza eta aurkikuntzarekin](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Gehitu edo editatu ibilgailu bat

1. Ireki **Ibilgailua** goitibeherako menua → aukeratu ibilgailu bat edo **Gehitu ibilgailu berria**.
2. Hartu edo aukeratu **erreferentziazko marratxoko argazki** argi bat (tresna multzo osoa, ondo argiztatuta, telefonoa gutxi gorabehera koadroa). Erabili **Atera argazkia** edo **Galeria**.
3. Sakatu **Exekutatu Discovery** — paleta detektatzeko **haziak** horiak jartzen ditu argazkian (mugarriek lehengo `"ML Kit"` JSON bera erabiltzen dute).
4. Ukitu **Hautatu Odo**, sakatu zifren hazia, egokitu heldulekuak eta, ondoren, **Gorde**.
5. **Editatu Odo** dagoeneko hor dagoen koadro bat doitzen du. **Garbitu Odo** kentzen du, eta botoiak **Hautatu Odo** irakurtzen du.
6. Berrikusi **Erakutsi mugarriak**-rekin. Erabili **Editatu OCR** ​​gaizki irakurriak konpontzeko edo galdutako testua **gehitu**.
7. Bete **Ibilgailuaren izena** (beharrezkoa), gehi marka/eredua/urtea/plaka nahi duzun moduan.
8. Sakatu **Sortu ibilgailua** edo **Gorde aldaketak** (izena + erreferentziako argazkia behar dira ibilgailu berri baterako).

![Gehitu ibilgailuaren inprimaki-eremu berriak](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Mugarriak: konpondu Discovery-k galdutakoa

**Erakutsi mugarriak** ondoren, joan zerrendan eta zuzendu balioak. Zenbaki txikiak galdu egiten dituzte motorrek batzuetan (adibidez, klusterraren beheko eskuinean dagoen erlojua **60**). Erabili **Editatu OCR** ​​horiek gehitzeko edo konpontzeko, ibilgailuaren identitatea fidagarria izan dadin.

![Mugarrien elkarrizketa — berrikusi eta editatu OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Argazki perfekturik gabe idazten

Oraindik ere erabil dezakezu aplikazioa ibilgailu bat hautatuz eta **idatzi** kilometroa, bolumena eta kostua Betean — OCR aukerakoa da eremu guztietan. Galeria inportatzeak erreferentziako marratxoaren argazkirako funtzionatzen du aplikazioan ez filmatzea nahiago duzunean.

**Aholkua:** Kalkulu-orriaren sinkronizazioaren ondoren, ibilgailuen definizioak (laboreak, mugarriak) tokiko datu-basean bizi dira; ez duzu berriro ireki behar Kudeatu ibilgailuak Beterako horiek erabiltzeko.

---

## <a id="backups-and-multi-device-sync"></a>Babeskopia eta gailu anitzeko sinkronizazioa

Aplikazioa **zenbait telefonok edo tabletak flotaren datu berdinak parteka ditzakete** eraikita dago, eta **zure datuen eta argazkien kopia bat gorde dezakezu gailutik****. Hori **zuk** konfiguratzen dituzun helmugekin egiten da **zure** kontuetan edo **zure** norberak ostatutako zerbitzarietan, ez beste pertsonek ikus dezaketen konpainiak zuzendutako "Ibilgailuen gastuen hodeia".

### <a id="what-runs-where"></a>Zer doa nondik

| Mota | Zer gordetzen duen | Erabilera tipikoa |
|------|-----------------|-------------|
| **Kalkulu-orria / sinkronizazio tabularra** | Ibilgailuak, erregai betetzeak, gastuak (lerroak eta fitxak) | Gailu anitzeko konbinazioa + babeskopia egituratua |
| **Argazkiaren babeskopia** | Irudi bitarrak (marra/ponpa/ordainagiria/erreferentzia argazkiak) | Argazkien babeskopia + falta diren fitxategiak leheneratu |

Mota bakoitzeko **helmuga anitz** konfigura ditzakezu (mota bakoitzeko txano biguna). Eskuzko **Sync now** eta **background** langileek gaituta daudenak exekutatzen dituzte.

### <a id="offline-first"></a>Lineaz kanpo lehenik

- **Ez da sarerik behar** betetzea, gastua edo ordainagiria gehitzeko. Dena gordetzen da **lokalki lehenik**.
- Sarea erabilgarri dagoenean, sinkronizazioa eta argazkien babeskopia **atzeko zereginak** gisa exekutatzen dira (zuk ezarritako ordutegian eta **Sinkronizatu orain** sakatzen duzunean). Hutsegiteak Ezarpenen errenkadetan eta **!** baten azpian testu gorri gisa agertzen dira aplikazioaren izenburu-barran.

### <a id="your-accounts-only"></a>Zure kontuak bakarrik

Saio-hasiera eta tokenak gailuan geratzen dira aukeratzen dituzun hornitzaileentzat (Google, Microsoft, S3 gakoak, auto-ostatatutako URLak, eta abar). Helmugak **erabiltzailearen kontrol osoa** daude: zure Google kontua, zure OneDrive, zure MinIO kuboa, zure EtherCalc ostalaria, etab. Ez da ezer partekatzen Ibilgailuen Gastuetako beste erabiltzaile batzuekin partekatutako backend baten bidez.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Onartutako helburuak — datuak (kalkulu-orria / taula)

**Menua → Sinkronizatzea → Kalkulu-orrien sinkronizazioa** atalean konfiguratuta (Ezarpenen laburpen-lerroetatik ere eskura daiteke). Lehen mailako hautatzaileen aukerak:

| Helburua | Oharrak |
|--------|---------|
| **Google Orriak** | Lehenetsi arrunta; Ibilgailuetarako fitxak, Gastuak eta ibilgailu bakoitzeko erregaia |
| **Excel** | Microsoft lan-koadernoa Graph / OneDrive estiloko lotura bidez |
| **EtherCalc** | Norberak ostatutako kalkulu-orri kolaboratiboko gelak |
| **Beste batzuk →** inplementatutako backendak | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Atzeratua / oraindik ez dago bururik gabe (Beste batzuk atalean ageri da baina ez da guztiz inplementatu): OnlyOffice, Collabora. Ikusi ere [auto-ostalari indizea](reference/self-host/INDEX.md).

CSV **esportatu/inportatu** (fitxa-diseinu bereko ZIPa) Ezarpenetan eskuragarri dago babeskopia eramangarri gisa, zuzeneko sinkronizaziotik independentea.

### <a id="supported-targets--photos-image-backup"></a>Onartutako helburuak — argazkiak (irudien babeskopia)

**Menua → Sinkronizazioa → Argazkien babeskopia** atalean konfiguratuta (Ezarpenen laburpen-lerroetatik ere):

| Helburua | Oharrak |
|--------|---------|
| **Google Drive** | Aukeratzen duzun karpeta (arakatu edo itsatsi URLa) |
| **OneDrive** | Microsoft kontua + bide-aurrizkia |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO eta S3-rekin bateragarriak diren beste puntu batzuk |
| **Besteak** | rclone-k babestutako biltegiratzea (adibidez, WebDAV, SFTP eta beste urruneko kudeatzaile batzuk eskuragarri daude aplikazio barruko hautatzailean) |

Konfiguratu iruzur-orriak norberak ostatutako argazkietarako eta helburu taularetarako: [auto-ostalari-indizea](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Gailu anitzeko portaera (laburra)

- Errenkadak **Sync ID** bidez batu **azken idazketa-irabaziak**-ekin **Eguneratuta** denbora-zigiluetan.
- Ezabaketak bigunak dira; beste gailu batean egindako edizio berri batek errenkada bat leheneratu dezake.
- Bi gailutan **betegarri bera bi aldiz** sartzean **bi errenkada** sortzen dira — ezabatu gehigarria ohartzen zarenean.
- Xehetasun gehiago: [Sync portaera oharrak](#sync-behavior-notes) eta [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Adibidea: gehitu Google Sheets (datuak)

1. **Menua → Sinkronizazioa → Kalkulu-orrien sinkronizazioa** (edo Ezarpenak → Kalkulu-orrien sinkronizazioa).

![Kalkulu-orrien sinkronizazio-zerrenda](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Sakatu **Gehitu kalkulu-orriaren helmuga**.

![Hornitzaile-hautatzailea](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Aukeratu **Google Orriak**.

![Google Sheets inprimakia](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Hasi saioa Google-rekin** behin — saioa haste horrek arakatzeko ere aukera ematen dizu. Bistaratzeko izena → itsatsi **Orriaren URLa** (balioa da oraindik) edo **🔍** arakatu. Arakatu **Guztiak eta partekatuak** aukeran irekitzen da, zure jabea zaren edo zurekin partekatzen den kalkulu-orri bat hauta dezazun. Ondoren, programatu aukerak → gaitu → gorde.
5. **Sinkronizatu orain** behin fitxak sortzeko/eguneratzeko: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Adibidea: gehitu Google Drive (argazkiak)

1. **Menua → Sinkronizazioa → Argazkien babeskopia** (edo Ezarpenak → Argazkien babeskopia).

![Argazkiaren babeskopien zerrenda](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Sakatu **Gehitu argazki-helmuga**.

![Argazki-hornitzailearen hautatzailea](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Aukeratu **Google Drive**.

![Google Drive inprimakia](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Hasi saioa Google-rekin (Drive)** → aukerako karpeta URL/arakatu → gaitu → gorde → **Sinkronizatu orain**.

Eskuliburua **Sinkronizatu orain** argazkietarako pase osoa da; atzeko planoko babeskopiak normalean **zain daudenak** soilik kargatzen ditu programazio batean.

### <a id="sync-behavior-notes"></a>Sinkronizatu portaera-oharrak

- Aplikazioa eguneratu ondoren, labur-labur ikus dezakezu **"Berritu ondoren datu-basea eguneratzen..."** (sinkronizazio-identifikazio lokala betetzea).
- Sinkronizazioa eteten bada, hurrengo sinkronizazio **arrakastatsua** berriro batu eta urruneko fitxak konpontzen ditu.
- Hutsegiteak: laburpen gorria Sinkronizatzeko txarteletan + **!** aplikazioen barran.

---

## <a id="fill-up-fuel"></a>Bete (erregai)

Hau da **hasierako pantaila** aplikazioa irekitzen duzunean.

### <a id="vehicle-selection-usually-automatic"></a>Ibilgailuen hautaketa (normalean automatikoa)

Ez duzu **ez** lehenik ibilgailua hartu behar. Ibilgailuek **mugarriak** ezarrita dituztenean Ibilgailuak Kudeatu atalean, bete **automatikoki detektatzen du zein ibilgailu** marratxoaren iruditik odometroa atera ondoren. Hala ere, **Ibilgailua** goitibeherako aukera ireki dezakezu behar izanez gero gainidazteko.

### <a id="aim-at-the-odometer"></a>Jokatu odometrora

Egon odometro moduan eta markatu klusterra. Argibidea: *Kilometrora jo. Sakatu obturagailua ateratzeko.*

![Bete — zuzeneko kamera odometroan (adibide marra-irudia aurrebistan)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Kilometroaren obturadorearen ondoren

OCR **Odo** betetzen du eta ibilgailua mugarriekin bat egiten saiatzen da (berrikusi biak behar izanez gero). Botoi nagusia **Berriro saiatu** bihurtzen da berriro filmatzeko. Instrukzioak irakurketa laburtzen du.

![Bete — odometroaren OCR emaitza](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Ponpa modua (kostua eta bolumena)

1. Ukitu **↕** ponpa modura aldatzeko: *Pumparen pantailara zuzendu (kostua/bolumena). Sakatu obturagailua.*
2. Harrapatu ponparen totalak. Kostu eta bolumen eremuak bete; erabili **↔** trukatzen badira.
3. Sakatu moneta edo **G/L** behar izanez gero, eta gero **Gorde** (disko). Eremu hutsek **betetze partziala** egiten dute (oraindik onartzen da).

![Bete — ponparen aurrebista / guztirakoak (adibidea ponparen irudia aurrebistan)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Bete aukeran geratzen zara hurrengo geldialdirako (eremuak garbitu ondoren gorde ondoren). Lan egin guztiz **konexiorik gabe**; sinkronizazioa geroago exekutatzen da atzeko planoan konfiguratuta dagoenean.

### <a id="manual-entry-no-camera--bad-ocr"></a>Eskuzko sarrera (kamerarik gabe/OCR txarra)

1. Sakatu **Odo**, **cost** edo **bolumena** eta idatzi balioak (erretratuak sistemako teklatua erabiltzen du; horizontalak pantailako teklatua erabiltzen du).
2. Aukeratu edo berretsi **Ibilgailua** detekzio automatikoa abiarazi ez bada.
3. Gorde goiko moduan.

### <a id="modes-and-borders"></a>Moduak eta ertzak

- **Ertz berdea** ibilgailuaren+odoaren inguruan → kilometroa atera/editatu.
- **Ertz berdea** kostu+bolumenaren inguruan → ponpa modua.
- **Gorde** desgaituta egongo da ibilgailu bat hautatu arte eta gutxienez odo/kostu/bolumenetako batek datuak dituen arte, eta OCR oraindik ez dabil martxan.

Pantailako aholkua (argibide-lerroaren azpian): *Obtiragailua = harrapatzea · Diskoa = gorde · ↕ = odo/ponpa modua · ↔ = trukatzeko kostua/bolumena.*

---

## <a id="expenses"></a>Gastuak

### <a id="new-expense"></a>Gastu berria

Menua → **Gastu berria**.

![Gastu inprimaki berria eta kameraren kontrolak (adibide marra-irudia kameraren bandan hutsik dagoenean)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Gorde** (disko), **obturagailua** (ordainagiriaren argazkia) edo **galeria** (aukeratu irudia).
2. Bete **Data**, **Ibilgailua**, **Saltzailea**, **Deskribapena**, **Kopurua** (dibisaren ikurra sakatu daiteke), **Kategoria**, aukerakoa **Kilometroa**.
3. Orrialde anitzeko ordainagiriak: atera orri gehigarriak UI-ak orria eskaintzen badu (0 orria da ordainagiri nagusia).
4. **Gorde** gordetzeko (lehenengo tokikoa; argazkien babeskopia eta kalkulu-orrien sinkronizazioa atzeko planoan gertatzen da konfiguratuta dagoenean).

### <a id="expense-list"></a>Gastuen zerrenda

Menua → **Txostenak** → **Gastuen zerrenda** — arakatu iraganeko erregaiak ez diren gastuak; ireki elementu bat editatzeko.

![Gastuen zerrenda](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Editatu gastua

Ireki zerrendako errenkada bat. Zuzendu saltzailea, zenbatekoa, kategoria, ibilgailua eta deskribapena. Ordainagiria argazkien babeskopian soilik badago (ez dago irakur daitekeen tokiko fitxategirik), erabili **Lortu irudia artxibotik** erakusten denean (konfiguratutako argazkien helmuga guztietan funtzionatzen du).

![Editatu gastua](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Hasi bidaia

Menua → **Hasi bidaia** (tiradera bete ondoren). Hartu edo sartu kilometro-kontagailua, aukeratu bidaia mota, gorde **disko** ikonoarekin. **Gelditu** Pertsonalerako lasterbidea da orain atxikitako GPS kokapenan. Erabili **ⓘ** kontrol-abisuak egiteko.

![Hasi bidaia](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Bidaien abiapuntuak erregai-lerro gisa gordetzen dira **Bidaia mota** batekin (ez betetze normalak). **Txostenak → Bidaiaren kilometroak** atalean agertzen dira, ez Erregaiaren historia atalean.

---

## <a id="reports"></a>Txostenak

Menua → **Txostenak** produktuen zentroa irekitzen du (betirako laburpena + katalogo-txartelak). Hau da produktuen txostenen azalera bakarra; ez dago aparteko "Txostenak eta diagramak" tiradera-elementurik.

![Txostenen zentroa](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Ireki txartel bat ibilgailuaren modurako (**Guztiak / Bakoitza / Bakarra**), aldi-iragazkiak, grafikoak eta partekatzeko (**TESTU / CSV / PDF**). Erreportajearen haurren goiko barra: **☰ + ←** (eta **ⓘ** erregistratuta dagoenean).

### <a id="time-based-reports"></a>Denboran oinarritutako txostenak

Grafiko-txartel nagusia. Aukerako neurriak (mpg, bolumena/distantzia, adibidez, G/mi, unitateko prezioa, hala nola, $/G, kostua/distantzia, hileroko $, bidaia-mila, bidaiaren % motaren arabera) **Smooth** edukiontziekin eta **Y eskala independenteak** (ekonomia ezkerrean; dirua eta bidaia-familiak eskuinean).

![Denboran oinarritutako txostenak](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Denboran oinarritutako txostenak — korritutako metrika](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Ekonomia matematikaren xehetasunak: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Bete historia vs Erregaiaren historia

- **Txostenak → Bete historia** — txostenen iragazkien betetze kronologikoak (**betetzen soilik**; bidaiarik ez da hasten).

![Bete historia-txostena](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Erregaiaren historia** (zure eraikuntzako nabigazioan badago) - ibilgailu bakoitzeko betetze-inbentarioa, bakarrik betetzen du; sakatu errenkada bat editatzeko.

### <a id="trip-miles"></a>Bidaia kilometroak

**Txostenak → Bidaiaren kilometroak** — kilometroak motaren, diagramen eta **bidaien hasiera/segmentuen zerrenda** kronologikoaren arabera. Sakatu benetako hasiera bat errenkada horretako **Editatu betetzea** irekitzeko.

![Bidaia milia](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Editatu betetzea

Beteren historia, Erregaiaren historia edo Bidaia-mila ataletan, ireki betegarri bat. Diseinua: ibilgailua eta kilometro-kontagailua, **kostu aurretik moneta**, bolumena, oharrak. Bidaia mota errenkada bidaiaren hasiera denean bakarrik agertzen da. Kokapenaren laburpena gehi **Kokapenaren xehetasunak** ditu. Hodeiko identitatea duen tokiko argazkia falta da: **Lortu irudia artxibotik**.

![Editatu betetzea — Lortu artxibotik tokiko argazkia falta denean](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Beste katalogo txartelek kategoriaka, ibilgailuen laburpena eta gastuen zerrenda biltzen dituzte.

Diruak errenkada bakoitzaren moneta erabiltzen du ezartzen denean. Moneta mistoen guztizkoek **dibisa bakoitzeko azpitotalak** erakusten dituzte (ez dago FX bihurketa isilean).

---

## <a id="syncing"></a>Sinkronizatzen

Menua → **Sinkronizazioa** kalkulu-orrien eta argazkien helmugetarako gunea da (ez bakarrik Ezarpenetan lurperatuta).

![Sinkronizatzen ari den zentroa](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- **Kalkulu-orriaren sinkronizazioa** eta **Argazkien babeskopiak** egoera laburrerako txartelak, **Sinkronizatu** mota horretako eta **›** helmuga zerrendan.
- Ireki helmuga bat **Probatu konexioa** eta **Sinkronizatu orain (helmuga hau)** / guztiak konfiguratuta.
- Hutsegitea **Xehetasunak** eta ** gorria!** izenburu-barran hemen lehorreratzen dira.
- Urratsez urrats Google Sheets eta Drive konfigurazioa: [Bakopia eta gailu anitzeko sinkronizazioa](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Ezarpenak (tokiko hobespenak)

Menua → **Ezarpenak**.

![Ezarpenak](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Ezarpen gehiago (korrituta)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Helmugetarako, nahiago **Menua → Sinkronizatzea**. Baliteke ezarpenek oraindik zerrenda berdinak irekitzen dituzten laburpen errenkadak erakustea.

### <a id="local-preferences-common"></a>Tokiko hobespenak (ohikoak)

- **Gorde erregaiaren ordainagiria argazkiak** / **Gorde gastuen argazkiak lokalean** — gorde irudiak gailuan (Argazkiak baimena eska dezake).
- **Erreproduzi obturadorearen soinua**
- **Moneta** / **Bolumen unitatea** — aplikazioaren lehenetsiak (sistema edo esplizitua). Erregaiaren datuekin bolumen-unitatea aldatzeak bihurtzeko elkarrizketa-koadroa eskain dezake.
- **Modu iluna**
- **Konfiguratzeko aholkuak** - ireki berriro ibilgailuak martxan jarritako / sinkronizatzeko tutorialak.
- **Araztu betetzea** / **Erakutsi esperimentu-pantailak (garapena)** — aurreratua; utzi off eguneroko erabilerarako. Esperimentuen pantailak ez daude hemen dokumentatuta.

CSV **esportazioa/inportazioa** (Ibilgailuen ZIP / Gastuak / Erregai fitxak) Ezarpenetan dago eskuragarri uneko eraikuntzak eskaintzen duenean.

---

## <a id="help--about"></a>Laguntza eta buruz

![Laguntza](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Hori buruz](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Laguntza** — gailuko hasiera azkarra, konfigurazio tutorialak, eskuliburu honetarako esteka, auto-ostalariaren konfigurazio indizea.
- **Hori buruz** — bertsioa, lizentziak, GitHub, eskuliburu hau (lineaz kanpo + sareko HTMLa argitaratzen denean).

---

## <a id="related-docs"></a>Erlazionatutako dokumentuak

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — eguneroko gida laburra
- [auto-ostalari/INDEX.md](../reference/self-host/INDEX.md) — norberak ostatutako argazkia/taularen konfigurazioa
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — bateratzea, berreskuratzea, bikoiztuak
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — ekonomia-neurrien xehetasuna
