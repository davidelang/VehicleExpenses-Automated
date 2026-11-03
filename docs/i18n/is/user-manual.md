# <a id="vehicle-expenses-automated--user-manual"></a>Bifreiðakostnaður sjálfvirkur — notendahandbók

> **Breyta uppruna (Markdown).** Vafrar og lesandi í forriti opna **útgefna HTML**:
> - Vefur: [`docs/user-manual/index.html`](index.html) (endurnýjaðu með `./scripts/render-user-manual.sh`)
> - App: Hjálp / Um → full handbók (búnt HTML + skjámyndir)
>
> Ekki benda notendum á óunnar `.md` vefslóðir — vafrar sýna aðeins venjulegan texta.

Myndavéla-fyrsta mælingar fyrir eldsneytisáfyllingu og ökutækiskostnað, með valfrjálsu samstillingu og öryggisafriti með mörgum tækjum undir **þínum** skýjareikningum.

Þetta er **heildarhandbókin** (skjáskot + hvert skref). Í símanum er **Valmynd → Hjálp** styttri leiðbeiningar um að byrja.

**Ekki fjallað um hér:** Flytja inn gamlar myndir, jöfnunartilraun og dælutilraun (hönnuði / háþróuð verkfæri).

---

## <a id="table-of-contents"></a> Efnisyfirlit

1. [Það sem þú þarft](#what-you-need)
2. [Tákn í fljótu bragði](#icons-at-a-glance)
3. [Opna valmyndina](#open-the-menu)
4. [Fyrstu uppsetning: Stjórna ökutækjum](#first-time-setup-manage-vehicles)
5. [Öryggisafrit og samstilling margra tækja](#backups-and-multi-device-sync)
6. [Fylla (eldsneyti)](#fill-up-fuel)
7. [Start ferð](#start-trip)
8. [Útgjöld](#expenses)
9. [Skýrslur](#reports)
10. [Stillingar (staðbundnar óskir)](#settings-local-preferences)
11. [Samstilling](#syncing)
12. [Hjálp og um](#help--about)
13. [Tengd skjöl](#related-docs)

---

## <a id="what-you-need"></a> Það sem þú þarft

- Android sími eða spjaldtölva.
- Fyrir besta OCR: skýr sýn á **kílómetramælir í mælaborði** og **dælutölur** (eða sláðu inn tölurnar í höndunum).
- Valfrjálst: reikningar **þú stjórnar** fyrir töflureiknisgögn og/eða öryggisafrit af myndum (sjá [Öryggisafrit og samstilling margra tækja](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Tákn í fljótu bragði

Þetta birtast á aðalskjánum. Að þekkja þá sparar mikla veiði.

| Hvar | Tákn / stjórn | Hvað það gerir |
|-------|----------------|----------------|
| Efsti bar | **☰ Matseðill** (hamborgari) | Opnar leiðsöguskúffuna |
| Efsti bar | **ⓘ** (síðuhjálp) | Stutt hjálp fyrir **núverandi** síðu (við hliðina á valmyndinni þegar hún er tiltæk) |
| Efsti bar | **`?N`** (gult) | Spurningar um innflutningsskoðun í bið — opnar Innflutningsskoðun |
| Efsti bar | **!** (rautt) | Áfangastaður fyrir töflureikni eða mynd mistókst nýlega — opnaðu **Samstilling** til að laga |
| Efsti bar | **☰ + ←** | Tilkynna börn og kostnaðarlisti sýna **valmynd og aftur** saman; Skýrslumiðstöð er aðeins valmynd |
| Stillingar / eldsneytisbreyting | **←** | Til baka (stillingartöflureikni/mynd og eldsneytisbreytingar halda afturfókus) |
| Fylltu | **Hvítur hringur** (lokari) | Taktu kílómetramæli eða dæluskjá fyrir OCR |
| Fylltu | **Diskur / Vista** | Vistaðu áfyllinguna (þarf ökutæki og að minnsta kosti einn af odo / rúmmál / kostnaði) |
| Fylltu | **↕ örvar** (hamskipta) | Skiptu um **kílómetramælastillingu** á móti **dælustillingu (kostnaður/rúmmál)**. Grænn rammi undirstrikar virka svæðishópinn |
| Fylltu | **↔ örvar** (milli kostnaðar og rúmmáls) | Skiptu um kostnað og magn ef OCR setur þau í ranga reiti |
| Fylltu | **Aðdráttur 1x / …** | Aðdráttarhlutföll myndavélar þegar linsan styður þau |
| Fylla (eftir töku) | **Refresh** á aðalhnappi | Fleygðu forskoðun og farðu aftur í myndavél í beinni |
| Fylla (meðan í vinnslu) | **X** á aðalhnappi | Hætta við töku/OCR í vinnslu |
| Kostnaður | **Vista** | Sparaðu kostnaðinn |
| Kostnaður | **Lokahringur** | Taktu kvittunarmynd |
| Kostnaður | **Gallerí** | Veldu kvittunarmynd úr bókasafninu |
| Kostnaður | **Endurtaka** | Hreinsaðu núverandi kvittunarmynd og taktu aftur |
| Kostnaður / Stjórna ökutækjum | **+ / −** FABs | Stækkaðu forskoðun myndarinnar |
| Kennileiti gluggi | **Breyta OCR** ​​| Leiðrétta eða bæta við merkistexta sem vélarnar misstu af |
| Töflureiknir / myndaeyðublöð | **ו Leit** | Skoðaðu Google Drive að blaði eða möppu (eftir innskráningu) |

Gjaldmiðlatákn á kostnaðarreitum og **G/L** á rúmmálsreitum er hægt að snerta: opnaðu litla valmynd til að breyta gjaldmiðli eða lítrum á móti lítrum fyrir þá færslu.

---

## <a id="open-the-menu"></a>Opnaðu valmyndina

1. Pikkaðu á **☰** efst til vinstri.
2. Veldu síðu.

![Leiðsöguskúffa](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Aðalskúffa:** Fylla · Hefja ferð · Stjórna ökutækjum · Nýr kostnaður · **Skýrslur** · Stillingar · Samstilling · Hjálp · Um.

**Tilraunaskúffa** (Stillingar → Sýna tilraunaskjái): Jöfnunartilraun · Dælutilraun · **Flytja inn gamlar myndir**.

**Í gegnum skýrslumiðstöð (ekki aðalskúffu):** Útgjaldalisti · Fyllingarferill.

---

## <a id="first-time-setup-manage-vehicles"></a> Uppsetning í fyrsta skipti: Stjórna ökutækjum

OCR og **sjálfvirk samsvörun ökutækja** virka best eftir að þú skráir hvert ökutæki með **viðmiðunarmynd í mælaborði**, keyrir **Discovery**, stillir kílómetramælauppskeruna úr róðrarspaði og vistar kennileiti fyrir það mælaborð.

### <a id="open-manage-vehicles"></a>Opna Manage Vehicles

Valmynd → **Stjórna ökutækjum**. Veldu ökutæki (eða **Bæta við nýju ökutæki**).

![Hafa umsjón með ökutækjum með viðmiðunarstrik, uppskeru og uppgötvun](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Bæta við eða breyta ökutæki

1. Opnaðu fellilistann **Ökutæki** → veldu ökutæki eða **Bæta við nýju ökutæki**.
2. Taktu eða veldu skýra **tilvísunarstrikmynd** (fullur hljóðfærakassi, vel upplýstur, sími nokkurn veginn ferhyrndur). Notaðu **Taka mynd** eða **Gallerí**.
3. Pikkaðu á **Run Discovery** — spaðaskynjun setur gul **fræ** á myndina (kennileiti nota sama `"ML Kit"` JSON og áður).
4. Pikkaðu á **Veldu Odo**, pikkaðu á fræið á tölustafina, stilltu handföngin og svo **Vista**.
5. **Edit Odo** stillir kassa sem er þegar til staðar. **Clear Odo** fjarlægir það og á hnappnum stendur **Veldu Odo**.
6. Upprifjun með **Sýna kennileiti**. Notaðu **Breyta OCR** ​​til að laga mislestur eða **bæta við** texta sem gleymdist.
7. Fylltu út **Nafn ökutækis** (áskilið), ásamt tegund/gerð/árgerð/plötu eins og þú vilt.
8. Pikkaðu á **Búa til farartæki** eða **Vista breytingar** (þarfnast nafn + tilvísunarmynd fyrir nýtt ökutæki).

![Bæta við nýjum eyðublöðum fyrir ökutæki](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Leiðamerki: laga það sem Discovery missti af

Eftir **Sýna kennileiti**, skrunaðu listann og leiðréttu gildin. Vélar missa stundum af litlum tölustöfum (til dæmis klukku **60** neðst til hægri á þyrpingunni). Notaðu **Breyta OCR** ​​til að bæta við eða laga þau svo auðkenni ökutækis haldist áreiðanlegt.

![Leiðamerki valmynd — skoðaðu og breyttu OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Vinnsláttur án fullkominnar myndar

Þú getur samt notað appið með því að velja ökutæki og **slá** inn kílómetramæli, rúmmál og kostnað á fyllingu — OCR er valfrjálst fyrir hvern reit. Galleríinnflutningur virkar fyrir viðmiðunarstrikmyndina þegar þú vilt ekki taka myndir í forritinu.

**Ábending:** Eftir samstillingu töflureikna birtast skilgreiningar ökutækja (uppskera, kennileiti) í staðbundnum gagnagrunni - þú þarft ekki að opna Manage Vehicles for Fill aftur til að nota þær.

---

## <a id="backups-and-multi-device-sync"></a>Öryggisafrit og samstilling margra tækja

Forritið er byggt þannig að **nokkrir símar eða spjaldtölvur geta deilt sömu flotagögnum**, og svo þú getir haldið **afriti af gögnum þínum og myndum utan tækisins**. Það er gert með áfangastaði **þú** stillir undir **þínum** reikningum þínum eða **þínum** netþjónum sem hýst eru sjálfir - ekki fyrirtækisreknu „ökutækjakostnaðarskýi“ sem aðrir geta séð.

### <a id="what-runs-where"></a>Hvað rennur hvar

| Góður | Það sem það geymir | Dæmigert notkun |
|-------|----------------|----------------|
| **Samstilling töflureikni/töflu** | Ökutæki, eldsneytisáfyllingar, útgjöld (raðir og flipar) | Sameining margra tækja + skipulögð öryggisafrit |
| **Afritun mynda** | Tvöfaldur myndir (dæla/dæla/kvittun/viðmiðunarmyndir) | Afrit af myndum + endurheimta vantar skrár |

Þú getur stillt **marga áfangastaði** af hverri tegund (mjúk lok á hverja tegund). Handvirkt **Samstilla núna** og **bakgrunns** starfsmenn keyra þá sem eru virkir.

### <a id="offline-first"></a>Ontengdur fyrst

- **Ekki þarf netkerfi** til að bæta við áfyllingu, kostnaði eða kvittun. Allt er vistað **staðbundið fyrst**.
- Þegar netið er tiltækt keyrir samstilling og öryggisafrit af myndum sem **bakgrunnsverkefni** (samkvæmt áætlun sem þú setur og þegar þú pikkar á **Samstilla núna**). Bilanir birtast sem rauður texti undir stillingarlínunum og **!** í titilstiku forritsins.

### <a id="your-accounts-only"></a> Aðeins reikningarnir þínir

Innskráning og tákn eru áfram á tækinu fyrir þá þjónustuveitendur sem þú velur (Google, Microsoft, S3 lyklar, vefslóðir sem hýst eru sjálfar og svo framvegis). Áfangastaðir eru undir **fullri stjórn notandans** — Google reikningurinn þinn, OneDrive þinn, MinIO fötuna þín, EtherCalc gestgjafinn þinn, osfrv. Engu er deilt með öðrum notendum ökutækjakostnaðar í gegnum sameiginlegan bakenda.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Stydd markmið — gögn (töflureikni/töflu)

Stillt undir **Valmynd → Samstilling → Samstilling töflureikna** (einnig hægt að nálgast í yfirlitslínum stillinga). Fyrsta flokks valkostur:

| Markmið | Skýringar |
|--------|--------|
| **Google Sheets** | Algengt sjálfgefið; flipa fyrir ökutæki, kostnað og eldsneyti á ökutæki |
| **Excel** | Microsoft vinnubók í gegnum graf / OneDrive-stíl bindingu |
| **EtherCalc** | Sjálfstýrð sameiginleg töflureiknaherbergi |
| **Annað →** útfært bakenda | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Frestað / ekki hauslaust ennþá (skráð undir Annað en ekki að fullu útfært): OnlyOffice, Collabora. Sjá einnig [sjálf-hýsingarvísitala](reference/self-host/INDEX.md).

CSV **útflutningur/innflutningur** (ZIP af sama flipaskipulagi) er fáanlegt í stillingum sem flytjanlegt öryggisafrit, óháð samstillingu í beinni.

### <a id="supported-targets--photos-image-backup"></a>Stydd skotmörk — myndir (afrit af mynd)

Stillt undir **Valmynd → Samstilling → Afrit af mynd** (einnig úr yfirlitslínum stillinga):

| Markmið | Skýringar |
|--------|--------|
| **Google Drive** | Mappa sem þú velur (skoða eða líma slóð) |
| **OneDrive** | Microsoft reikningur + slóð forskeytið |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO og aðrir S3-samhæfðir endapunktar |
| **Annað** | rclone-studd geymsla (t.d. WebDAV, SFTP og aðrar fjarstýringar sem eru tiltækar í vali í forriti) |

Settu upp svindlblöð fyrir sjálf-hýst mynd og töflumörk: [sjálf-hýsingarvísitala](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Fjöltækjahegðun (stutt)

- Raðir sameinast eftir **Sync ID** með **last-write-wins** á **Updated** tímasporsgögnum.
- Eyðingar eru mjúkar; nýrri breyting á öðru tæki getur endurheimt röð.
- Ef þú slærð inn **sama fyllinguna tvisvar** á tveimur tækjum myndast **tvær línur** — eyða aukaatriðinu þegar þú tekur eftir því.
- Nánari upplýsingar: [Samstillingarhegðun](#sync-behavior-notes) og [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Dæmi: bæta við Google Sheets (gögnum)

1. **Valmynd → Samstilling → Samstilling töflureikna** (eða Stillingar → Samstilling töflureikna).

![Samstillingarlisti töflureikna](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Pikkaðu á **Bæta við áfangastað fyrir töflureikni**.

![Velji veitenda](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Veldu **Google Sheets**.

![Google Sheets form](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Skráðu þig inn með Google** einu sinni — þessi innskráning gerir þér einnig kleift að vafra. Birtingarnafn → límdu **Slóð blaðs** (enn í gildi) eða ****** flettu. Vafra opnast á **Allt og deilt** svo þú getur valið núverandi töflureikni sem þú átt eða sem er deilt með þér. Síðan tímaáætlun valkostir → virkja → vista.
5. **Samstilla núna** einu sinni til að búa til/uppfæra flipa: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Dæmi: bæta við Google Drive (myndum)

1. **Valmynd → Samstilling → Afritun mynda** (eða Stillingar → Afrit af myndum).

![Myndaafritunarlisti](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Pikkaðu á **Bæta við áfangastað fyrir mynd**.

![Velji myndaveitu](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Veldu **Google Drive**.

![Google Drive eyðublað](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Skráðu þig inn með Google (Drive)** → valfrjáls möppuvefslóð/vafra → virkja → vista → **Samstilla núna**.

Handbók **Samstilling núna** fyrir myndir er fullur aðgangur; öryggisafrit í bakgrunni vinnur venjulega **aðeins í bið** upphleðslu samkvæmt áætlun.

### <a id="sync-behavior-notes"></a>Samstilltu athugasemdir um hegðun

- Eftir uppfærslu forritsins gætirðu séð stuttlega **“Uppfærir gagnagrunn eftir uppfærslu...“** (uppfylling staðbundinnar samstillingar auðkennis).
- Ef samstilling er trufluð, sameinast næsta **velheppnaðar** samstilling aftur og gera við ytri flipa.
- Bilanir: rauð samantekt á samstillingarkortum + **!** í appstikunni.

---

## <a id="fill-up-fuel"></a>Fylla (eldsneyti)

Þetta er **heimaskjárinn** þegar þú opnar forritið.

### <a id="vehicle-selection-usually-automatic"></a>Val á ökutæki (venjulega sjálfvirkt)

Þú þarft **ekki** að velja farartækið fyrst. Þegar ökutæki eru með **kennileita** sett upp í Stjórna ökutækjum, greinir Fill hvaða ökutæki** sjálfkrafa hvaða ökutæki** er á mælaborðsmyndinni eftir að þú tekur kílómetramælirinn. Þú getur samt opnað fellilistann **Ökutæki** til að hnekkja ef þörf krefur.

### <a id="aim-at-the-odometer"></a> Miðaðu að kílómetramælinum

Vertu í kílómetramælisstillingu og rammaðu inn þyrpinguna. Kennsla: *Stefnum á kílómetramæli. Ýttu á lokara til að mynda.*

![Fylla — myndavél í beinni á kílómetramæli (dæmi strikamynd í forskoðun)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Eftir lokara kílómetramælis

OCR fyllir **Odo** og reynir að passa við ökutækið frá kennileitum (skoða bæði ef þörf krefur). Aðalhnappurinn verður að **Rereyna aftur** til að mynda aftur. Kennsla dregur saman lesturinn.

![Fylla — niðurstaða kílómetramælis OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Dælustilling (kostnaður og magn)

1. Pikkaðu á **↕** til að skipta yfir í dælustillingu: *Stefnum á dæluskjáinn (kostnaður/rúmmál). Bankaðu á lokara.*
2. Taktu heildartölur dælunnar. Kostnaðar- og rúmmálsreitir fylla; notaðu **↔** ef þeim er skipt út.
3. Pikkaðu á gjaldmiðil eða **G/L** ef þörf krefur, síðan á **Vista** (diskur). Tómir reitir gera **fylling að hluta** (enn leyfð).

![Fylla — dæluforskoðun / heildartölur (dæmi dælumynd í forskoðun)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Þú heldur áfram á Fill fyrir næsta stopp (reitir hreinsaðir eftir vistun). Vinna að fullu **ótengdur**; sync keyrir síðar í bakgrunni þegar það er stillt.

### <a id="manual-entry-no-camera--bad-ocr"></a>Handvirk færsla (engin myndavél / slæmur OCR)

1. Pikkaðu á **Odo**, **cost** eða **volume** og tegundargildi (andlitsmynd notar kerfislyklaborðið; landslag notar skjátakkaborð).
2. Veldu eða staðfestu **Ökutæki** ef sjálfvirk skynjun virkaði ekki.
3. Vistaðu eins og að ofan.

### <a id="modes-and-borders"></a>Háttur og landamæri

- **Grænir rammar** í kringum ökutæki+kílómetra → taka/breyta kílómetramæli.
- **Græn mörk** í kringum kostnað+rúmmál → dælustilling.
- **Vista** er óvirkt þar til ökutæki er valið og að minnsta kosti eitt af odo/cost/volume hefur gögn og OCR er ekki enn í gangi.

Ábending á skjánum (fyrir neðan leiðbeiningarlínuna): *Shutter = handtaka · Diskur = vista · ↕ = odo/dælustilling · ↔ = skiptakostnaður/magn.*

---

## <a id="expenses"></a> Útgjöld

### <a id="new-expense"></a>Nýr kostnaður

Valmynd → **Nýr kostnaður**.

![Nýtt kostnaðareyðublað og myndavélastýringar (dæmi strikamynd á myndavélarbandi þegar það er tómt)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Vista** (diskur), **lokari** (mynd af kvittun) eða **gallerí** (velja mynd).
2. Fylltu út **Dagsetning**, **Ökutæki**, **Seljandi**, **Lýsing**, **Upphæð** (gengistákn gjaldmiðils), **Flokkur**, valfrjálst **Kilometermælir**.
3. Kvittanir á mörgum síðum: fanga viðbótarsíður ef notendaviðmótið býður upp á síðuskipti (síðu 0 er aðalkvittun).
4. **Vista** til að geyma (staðbundið fyrst; öryggisafrit af myndum og samstillingu töflureikna eiga sér stað í bakgrunni þegar það er stillt).

### <a id="expense-list"></a> Útgjaldalisti

Valmynd → **Skýrslur** → **Útgjaldalisti** — skoðaðu fyrri útgjöld sem ekki eru eldsneyti; opna atriði til að breyta.

![Útgjaldalisti](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Breyta kostnaði

Opnaðu röð af listanum. Rétt seljanda, upphæð, flokk, farartæki og lýsingu. Ef kvittunin er aðeins í öryggisafriti af myndum (engin læsileg staðbundin skrá), notaðu **Sækja mynd úr skjalasafni** þegar hún er sýnd (virkar á milli stilltra myndaáfangastaða).

![Breyta kostnaði](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Start ferð

Valmynd → **Start ferð** (eftir Fylltu í skúffuna). Taktu eða sláðu inn kílómetramæli, veldu tegund ferðar, vistaðu með **diski** tákninu. **Stöðva** er flýtileið fyrir persónulega núna á GPS-staðnum sem haldið er. Notaðu **ⓘ** fyrir stjórnunaráminningar.

![Byrja ferð](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Byrjun ferða eru geymdar sem eldsneytisraðir með **Terð ferð** (ekki venjuleg áfylling). Þær birtast undir **Skýrslur → Ferðamílur**, ekki undir Eldsneytissaga.

---

## <a id="reports"></a> Skýrslur

Valmynd → **Skýrslur** opnar vörumiðstöðina (yfirlit allra tíma + vörulistaspjöld). Þetta er eina vöruskýrslur yfirborðið - það er enginn sérstakur „Reports & Charts“ skúffuhlutur.

![Skýrslumiðstöð](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Opnaðu kort fyrir ökutækisstillingu (**Allir / Hver / Einn**), tímabilssíur, töflur og deilingu (**TEXT / CSV / PDF**). Efsta stika á tilkynningabörnum: **☰ + ←** (og **ⓘ** þegar skráð er).

### <a id="time-based-reports"></a>Tímabundnar skýrslur

Aðalkortakortið. Valfrjáls mælikvarði (mpg, rúmmál/vegalengd eins og G/míl., einingarverð eins og $/G, kostnaður/vegalengd, mánaðarlega $, ferðamílur, ferð % eftir tegund) með **Sléttum** hólfum og **sjálfstæðum Y kvarða** (hagkerfi til vinstri; peningar og ferðafjölskyldur hægra megin).

![Tímabundnar skýrslur](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Tímabundnar skýrslur — fletta mæligildi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Upplýsingar um hagfræðistærðfræði: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Fyllasögu á móti eldsneytissögu

- **Skýrslur → Fyllingarsaga** — tímaröð fyllingar fyrir skýrslusíurnar (**fyllir aðeins**; engin ferð hefst).

![Fylla söguskýrslu](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Eldsneytissaga** (ef það er til staðar í leiðsögn byggingarinnar þinnar) - áfyllingarbirgðir fyrir hvert ökutæki, fyllir einnig aðeins; bankaðu á línu til að breyta.

### <a id="trip-miles"></a> Ferðamílur

**Skýrslur → Ferðamílur** — mílur eftir tegund, töflum og tímaröð **upphafs-/hlutalisti ferðar**. Pikkaðu á alvöru byrjun til að opna **Breyta fyllingu** fyrir þá línu.

![Ferðamílur](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Breyta fyllingu

Frá Fyllingarsögu, Eldsneytissögu eða Ferðamílur, opnaðu áfyllingu. Skipulag: farartæki og kílómetramælir, **gjaldmiðill fyrir kostnað**, rúmmál, athugasemdir. Tegund ferðar birtist aðeins þegar röðin er upphaf ferðar. Staðsetning hefur yfirlit auk **upplýsingar um staðsetningu**. Vantar staðbundna mynd með auðkenni skýja: **Sækja mynd úr skjalasafni**.

![Breyta fyllingu — Sæktu úr skjalasafni þegar staðbundna mynd vantar](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Önnur vörulistakort innihalda útgjöld eftir flokkum, yfirlit yfir ökutæki og kostnaðarlista.

Peningar nota gjaldmiðil hverrar línu þegar þeir eru stilltir. Samtölur í blönduðum gjaldmiðli sýna **undirtölur á hverja gjaldmiðil** (engin hljóðlaus gjaldeyrisbreyting).

---

## <a id="syncing"></a>Samstilling

Valmynd → **Samstilling** er miðstöð töflureikna og myndaáfangastaða (ekki aðeins grafin undir Stillingar).

![Samstillingarmiðstöð](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Kort fyrir **Samstilling töflureikna** og **Afritun mynda** með stuttri stöðu, **Samstilling** fyrir slíka tegund og **›** inn á áfangastaðalistann.
- Opnaðu áfangastað fyrir **Prófu tengingu** og **Samstilltu núna (þessi áfangastaður)** / allt stillt.
- Bilun **Upplýsingar** og rauða **!** í titilstikunni lenda hér.
- Skref-fyrir-skref uppsetning Google Sheets og Drive: [Öryggisafrit og samstilling margra tækja](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a> Stillingar (staðbundnar óskir)

Valmynd → **Stillingar**.

![Stillingar](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Fleiri stillingar (fletta)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Fyrir áfangastaði skaltu velja **Valmynd → Samstilling**. Stillingar gætu samt sýnt yfirlitslínur sem opna sömu lista.

### <a id="local-preferences-common"></a>Staðbundnar óskir (algengar)

- **Vista myndir af bensínkvittun** / **Vista kostnaðarmyndir á staðnum** — haltu myndum á tækinu (gæti beðið um leyfi fyrir myndum).
- **Spilaðu lokarahljóð**
- **Gjaldmiðill** / **Rúmmálseining** — sjálfgefið forrit (kerfi eða skýrt). Að breyta rúmmálseiningu með fyrirliggjandi eldsneytisgögnum gæti boðið upp á umbreytingarglugga.
- **Dökk stilling**
- **Ábendingar um uppsetningu** — opnaðu aftur fyrstu ökutæki / samstillingarkennsluefni.
- **Kembifylling** / **Sýna tilraunaskjái (dev)** — háþróaður; slepptu til daglegrar notkunar. Tilraunaskjáir eru ekki skjalfestir hér.

CSV **útflutningur/innflutningur** (ZIP of Vehicles / Expenses / Eldsneytisflipar) er fáanlegt í stillingum þegar núverandi smíði býður upp á það.

---

## <a id="help--about"></a>Hjálp og um

![Hjálp](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Um](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Hjálp** — flýtiræsing í tækinu, uppsetningarkennsla, hlekkur á þessa handbók, uppsetningarskrá fyrir sjálf-gestgjafa.
- **Um** — útgáfa, leyfi, GitHub, þessi handbók (fylgt án nettengingar + HTML á netinu þegar hún er birt).

---

## <a id="related-docs"></a>Tengd skjöl

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — stutt hversdagsleiðbeiningar
- [sjálfgestgjafi/INDEX.md](../reference/self-host/INDEX.md) — sjálfstýrð mynd/töfluuppsetning
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — sameina, endurheimta, afrit
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — smáatriði hagkerfismælinga
