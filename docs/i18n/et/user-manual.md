# <a id="vehicle-expenses-automated--user-manual"></a>Automaatsed sõidukikulud – kasutusjuhend

> **Muuda allikat (märgistus).** Brauserid ja rakendusesisene lugeja avavad **renderdatud HTML-i**:
> – Veeb: [`docs/user-manual/index.html`](index.html) (regenereerige `./scripts/render-user-manual.sh`-ga)
> - Rakendus: Abi / Teave → täielik kasutusjuhend (komplektis HTML + ekraanipildid)
>
> Ärge suunake lõppkasutajaid töötlemata `.md` URL-idele – brauserid näitavad ainult lihtteksti.

Kaamerapõhine kütusetankide ja sõidukikulude jälgimine koos valikulise mitme seadme sünkroonimise ja varundamisega **teie** pilvekontode all.

See on **täisjuhend** (ekraanipildid + iga samm). Telefonis on **Menüü → Abi** lühem alustamisjuhend.

**Siin ei käsitleta:** importige vanu pilte, joonduskatset ja pumbakatset (arendaja / täiustatud tööriistad).

---

## <a id="table-of-contents"></a>Sisukord

1. [Mida te vajate](#what-you-need)
2. [Ikoonid lühidalt](#icons-at-a-glance)
3. [Avage menüü](#open-the-menu)
4. [Esmakordne seadistamine: sõidukite haldamine](#first-time-setup-manage-vehicles)
5. [Varundamine ja mitme seadmega sünkroonimine](#backups-and-multi-device-sync)
6. [Täitke (kütus)](#fill-up-fuel)
7. [Alusta reisi](#start-trip)
8. [Kulud](#expenses)
9. [Aruanded](#reports)
10. [Seaded (kohalikud eelistused)](#settings-local-preferences)
11. [Sünkroonimine](#syncing)
12. [Abi ja teave](#help--about)
13. [Seotud dokumendid](#related-docs)

---

## <a id="what-you-need"></a>Mida vajate

– Androidi telefon või tahvelarvuti.
– parima OCR-i jaoks: selge vaade **armatuurlaua läbisõidumõõdikule** ja **pumba kogusummadele** (või sisestage numbrid käsitsi).
– valikuline: kontod **teie juhitavad** arvutustabeliandmete ja/või fotode varundamiseks (vt [Varundamine ja mitme seadmega sünkroonimine](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Ikoonid lühidalt

Need kuvatakse põhiekraanidel. Nende tundmine säästab palju jahti.

| Kus | Ikoon / juhtelement | Mida see teeb |
|-------|-----------------|--------------|
| Ülemine riba | **☰ Menüü** (hamburger) | Avab navigeerimissahtli |
| Ülemine riba | **ⓘ** (lehe abi) | Lühiabi **praeguse** lehe jaoks (menüü kõrval, kui see on saadaval) |
| Ülemine riba | **`?N`** (kollane) | Ootel impordi ülevaatuse küsimused — avab Impordi ülevaatuse |
| Ülemine riba | **!** (punane) | Arvutustabeli või foto sihtkoht ebaõnnestus hiljuti — parandamiseks avage **Sünkroonimine**
| Ülemine riba | **☰ + ←** | Aruande lapsed ja Kulude loend näitavad **menüüd ja tagasi** koos; Reports Hub on ainult menüü |
| Seadistused / kütuse muutmine | **←** | Tagasi (seadete arvutustabel/foto ja kütuse redigeerimine jäävad tahaplaanile) |
| Täida | **Valge ring** (katik) | Jäädvustage läbisõidumõõdiku või pumba kuva OCR jaoks |
| Täida | **Ketta / Salvesta** | Salvestage täitmine (vajab sõidukit ja vähemalt üht odo / maht / maksumus) |
| Täida | **↕ nooled** (režiimilüliti) | Lülitage **odomeetri režiim** vs **pumba (kulu/maht) režiim**. Roheline ääris tõstab esile aktiivse väljarühma |
| Täida | **↔ nooled** (maksumuse ja mahu vahel) | Vahetage kulu ja maht, kui OCR asetab need valedesse väljadesse |
| Täida | **Suum 1x / …** | Kaamera suumisuhted, kui objektiiv neid toetab |
| Täida (pärast püüdmist) | **Värskenda** põhinupul | Loobu eelvaatest ja naaske reaalajas kaamerasse |
| Täida (töötlemise ajal) | **X** põhinupul | Tühista pooleliolev jäädvustamine/OCR |
| Kulu | **Salvesta** | Säästke kulusid |
| Kulu | **Katiku ring** | Tehke kviitungi foto |
| Kulu | **Galerii** | Valige teegist kviitungi pilt |
| Kulu | **Kordusvõte** | Kustutage praegune kviitungi foto ja pildistage uuesti |
| Kulutused / sõidukite haldamine | **+ / −** FAB-d | Suumi foto eelvaadet |
| Maamärkide dialoog | **OCR-i muutmine** | Parandage või lisage orientiiritekst, millest mootorid märkamata jäid |
| Arvutustabel / Fotovormid | **🔍 Otsi** | Sirvige Google Drive'is lehte või kausta (pärast sisselogimist) |

Valuuta sümbolid kuluväljadel ja **G/L** mahuväljadel on puudutatavad: avage väike menüü, et muuta selle kirje valuuta või gallonite ja liitrite vahel.

---

## <a id="open-the-menu"></a>Avage menüü

1. Puudutage vasakus ülanurgas **☰**.
2. Valige leht.

![Navigeerimissahtel](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Peasahtel:** Täida · Alusta reisi · Halda sõidukeid · Uus kulu · **Aruanded** · Seaded · Sünkroonimine · Abi · Teave.

**Katsesahtel** (Seaded → Näita katsekuvasid): joonduskatse · Pumbakatse · **Impordi vanad pildid**.

**Aruandekeskuse kaudu (mitte peasahtel):** Kulude loend · Täida ajalugu.

---

## <a id="first-time-setup-manage-vehicles"></a>Esmakordne seadistamine: sõidukite haldamine

OCR ja **automaatne sõidukite sobitamine** töötavad kõige paremini pärast seda, kui registreerite iga sõiduki **armatuurlaual oleva fotoga**, käivitate **Discovery**, määrate odomeetri saagi mõla seemnest ja salvestate selle armatuurlaua jaoks orientiirid.

### <a id="open-manage-vehicles"></a>Avage Sõidukite haldamine

Menüü → **Halda sõidukeid**. Valige sõiduk (või **Lisa uus sõiduk**).

![Hallake sõidukeid võrdluskriipsu, põllukultuuride ja avastustega](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Lisage või muutke sõidukit

1. Avage rippmenüü **Sõiduk** → valige sõiduk või **Lisa uus sõiduk**.
2. Jäädvustage või valige selge **viitekriipsu foto** (täielik näidikuplokk, hästi valgustatud, telefon ligikaudu ruudukujuline). Kasutage **Tee foto** või **Galerii**.
3. Puudutage valikut **Käivita Discovery** – mõla tuvastamine asetab fotole kollased **seemned** (maamärgid kasutavad sama `"ML Kit"` JSON-i nagu varem).
4. Puudutage valikut **Vali Odo**, puudutage numbrite seemet, reguleerige käepidemeid ja seejärel nuppu **Salvesta**.
5. **Muuda Odot** kohandab juba olemasolevat kasti. **Clear Odo** eemaldab selle ja seejärel on nupul **Vali Odo**.
6. Vaadake üle **Näita maamärke**. Kasutage **Muuda OCR-i** valelugemiste parandamiseks või **lisage** vastamata jäänud tekst.
7. Täitke **Sõiduki nimi** (nõutav) ja mark/mudel/aasta/plaat vastavalt oma soovile.
8. Puudutage **Loo sõiduk** või **Salvesta muudatused** (uue sõiduki jaoks on vajalik nimi + võrdlusfoto).

![Lisa uued sõiduki vormi väljad](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a> Maamärgid: parandage see, mis Discoveryl kahe silma vahele jäi

Pärast **Kuva maamärgid** kerige loendit ja parandage väärtused. Mootorid jätavad mõnikord vahele väikesed numbrid (nt kell **60** klastri allosas paremal). Kasutage nende lisamiseks või parandamiseks nuppu **OCR-i muutmine**, et sõiduki identiteet jääks usaldusväärseks.

![Maamärkide dialoog — OCR-i ülevaatamine ja muutmine](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Tippimine ilma täiusliku fotota

Saate siiski rakendust kasutada, valides sõiduki ja **sisestades** läbisõidumõõdiku, mahu ja maksumuse väljale Täitmine – OCR on igal väljal valikuline. Galerii importimine töötab viitekriipsu foto puhul, kui te ei soovi rakendusesisest pildistada.

**Nõuanne.** Pärast arvutustabeli sünkroonimist asuvad sõidukite määratlused (kultuurid, maamärgid) kohalikus andmebaasis – nende kasutamiseks ei pea te uuesti avama funktsiooni Manage Vehicles for Fill.

---

## <a id="backups-and-multi-device-sync"></a>Varundamine ja mitme seadmega sünkroonimine

Rakendus on loodud nii, et **mitmed telefonid või tahvelarvutid saavad jagada samu seadmepargi andmeid** ja nii saate hoida **oma andmete ja fotode koopiaid seadmest eemal**. Seda tehakse sihtkohtadega, mille **teie** seadistate **oma** kontode all või **teie** isehostitavate serveritega – mitte ettevõtte hallatava sõidukikulude pilvega, mida teised inimesed näevad.

### <a id="what-runs-where"></a>Mis kus jookseb

| Lahke | Mida see salvestab | Tüüpiline kasutus |
|------|-----------------|-------------|
| **Arvutustabeli/tabeli sünkroonimine** | Sõidukid, kütusetäidised, kulud (read ja vahelehed) | Mitme seadme ühendamine + struktureeritud varundamine |
| **Foto varukoopia** | Binaarsed pildid (kriips/pump/kviitung/viitefotod) | Fotode varundamine + puuduvate failide taastamine |

Saate konfigureerida igat tüüpi **mitu sihtkohta** (pehme kork tüübi kohta). Käsitsi **Sünkrooni kohe** ja **taustal** töötavad töötajad käitavad lubatud töötajaid.

### <a id="offline-first"></a>Esmalt võrguühenduseta

– **Täitmise, kulu või kviitungi lisamiseks pole võrku vaja**. Kõik salvestatakse **kõigepealt kohapeal**.
– kui võrk on saadaval, käivitatakse sünkroonimine ja fotode varundamine **taustaülesannetena** (teie määratud ajakava järgi ja kui puudutate valikut **Sünkrooni kohe**). Rikked kuvatakse punase tekstina seadete ridade all ja **!** rakenduse tiitliribal.

### <a id="your-accounts-only"></a>Ainult teie kontod

Sisselogimine ja märgid jäävad teie valitud pakkujate jaoks seadmesse (Google, Microsoft, S3 võtmed, isehostitud URL-id jne). Sihtkohad on **kasutaja täieliku kontrolli** all – teie Google'i konto, teie OneDrive, teie MinIO-salv, teie EtherCalci host jne. Ühise taustaprogrammi kaudu ei jagata midagi teiste sõidukikulude kasutajatega.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Toetatud sihtmärgid – andmed (arvutustabel / tabel)

Seadistatud jaotises **Menüü → Sünkroonimine → Arvutustabeli sünkroonimine** (saadaval ka seadete kokkuvõtte ridadelt). Esimese klassi valija valikud:

| Sihtmärk | Märkmed |
|---------|--------|
| **Google'i arvutustabelid** | Tavaline vaikimisi; Sõidukite, kulude ja sõidukipõhise kütuse vahekaardid |
| **Excel** | Microsofti töövihik Graph / OneDrive-stiilis köitmise kaudu |
| **EtherCalc** | Ise hostitud arvutustabelite koostööruumid |
| **Muud →** juurutatud taustaprogrammid | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Edasilükatud / pole veel peata (loetletud jaotises Muu, kuid pole täielikult rakendatud): OnlyOffice, Collabora. Vaadake ka [self-host index](reference/self-host/INDEX.md).

CSV **eksport/import** (sama vahekaardi paigutusega ZIP) on seadetes saadaval kaasaskantava varukoopiana, sõltumata reaalajas sünkroonimisest.

### <a id="supported-targets--photos-image-backup"></a>Toetatud sihtmärgid – fotod (pildi varukoopia)

Seadistatud jaotises **Menüü → Sünkroonimine → Fotode varundamine** (ka seadete kokkuvõtte ridadelt):

| Sihtmärk | Märkmed |
|---------|--------|
| **Google Drive** | Teie valitud kaust (sirvige või kleepige URL) |
| **OneDrive** | Microsofti konto + tee eesliide |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO ja muud S3-ga ühilduvad lõpp-punktid |
| **Muu** | rclone-toega salvestusruum (nt WebDAV, SFTP ja muud kureeritud kaugjuhtimispuldid, mis on saadaval rakendusesiseses valijas) |

Petulehtede seadistamine isehostitud fotode ja tabelite sihtmärkide jaoks: [self-host index](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Mitme seadme käitumine (lühike)

– read liidetakse **Sünkroonimise ID** abil **Viimati kirjutamise võidud** **Värskendatud** ajatemplitel.
– kustutamised on pehmed; uuem muudatus teises seadmes võib rea taastada.
– **sama täidise kaks korda** sisestamine kahes seadmes loob **kaks rida** – kustutage lisa, kui märkate.
– üksikasjalikum teave: [Märkused sünkroonimise käitumisest](#sync-behavior-notes) ja [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Näide: lisage Google'i arvutustabelid (andmed)

1. **Menüü → Sünkroonimine → Arvutustabeli sünkroonimine** (või Seaded → Arvutustabeli sünkroonimine).

![Arvutustabeli sünkroonimisloend](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Puudutage valikut **Lisa arvutustabeli sihtkoht**.

![Pakkuja valija](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Valige **Google'i arvutustabelid**.

![Google'i arvutustabelite vorm](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Logige Google'iga sisse** üks kord – see sisselogimine võimaldab teil ka sirvida. Kuvatav nimi → kleepige **lehe URL** (veel kehtiv) või **🔍** sirvige. Sirvimine avaneb jaotises **Kõik ja jagatud**, et saaksite valida olemasoleva või teiega jagatud arvutustabeli. Seejärel ajastage valikud → luba → salvesta.
5. **Sünkroonige kohe** üks kord vahekaartide loomiseks/värskendamiseks: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Näide: lisage Google Drive (fotod)

1. **Menüü → Sünkroonimine → Fotode varundamine** (või Seaded → Foto varundamine).

![Photo backup list](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Puudutage **Lisa foto sihtkoht**.

![Fotopakkuja valija](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Valige **Google Drive**.

![Google Drive'i vorm](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Logige sisse Google'iga (Drive)** → valikuline kausta URL/sirvi → luba → salvesta → **Sünkrooni kohe**.

Manuaalne **Sünkrooni kohe** fotode jaoks on täispass; taustal varundamine töötleb tavaliselt **ainult ootel** üleslaadimisi ajakava alusel.

### <a id="sync-behavior-notes"></a>Sünkroonimiskäitumise märkmed

– pärast rakenduse täiendamist võite korraks näha teadet **„Andmebaasi värskendamine pärast täiendamist…”** (kohalik sünkroonimis-id-tagatäide).
– kui sünkroonimine katkeb, liidetakse järgmine **edukas** sünkroonimine uuesti ja parandatakse kaugvahekaardid.
– tõrked: punane kokkuvõte kaartide sünkroonimisel + **!** rakenduse ribal.

---

## <a id="fill-up-fuel"></a> Täitke (kütus)

See on rakenduse avamisel **avakuva**.

### <a id="vehicle-selection-usually-automatic"></a>Sõiduki valik (tavaliselt automaatne)

Te **ei** pea esmalt sõidukit valima. Kui sõidukitel on jaotises Manage Vehicles seadistatud **orientiirid**, tuvastab Täitmine **armatuurlaua pildilt automaatselt, millise sõiduki** pärast läbisõidumõõdiku jäädvustamist. Vajadusel alistamise jaoks saate siiski avada rippmenüü **Sõiduk**.

### <a id="aim-at-the-odometer"></a>Sihtige odomeetrile

Jääge läbisõidumõõdiku režiimile ja raamistage kobar. Juhend: *Sihtige odomeetrile. Jäädvustamiseks puudutage katikut.*

![Täida — reaalajas kaamera läbisõidumõõdikul (näide kriipsupilt eelvaates)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Pärast läbisõidumõõdiku katikut

OCR täidab **Odo** ja proovib sõidukit orientiiride järgi sobitada (vajadusel vaadake mõlemad üle). Peanupp muutub uuesti pildistamiseks **Proovi uuesti**. Juhend võtab lugemise kokku.

![Täida — odomeetri OCR-i tulemus](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Pumba režiim (kulu ja maht)

1. Puudutage **↕** pumbarežiimile lülitumiseks: *Sihtige pumba kuvale (kulu/maht). Puudutage katikut.*
2. Jäädvustage pumba kogusummad. Täidetakse kulu ja mahu väljad; kasutage **↔**, kui need on vahetatud.
3. Vajadusel puudutage valuutat või **G/L** ja seejärel **Salvesta** (ketas). Tühjad väljad täidavad **osalise täitmise** (ikka on lubatud).

![Täida — pumba eelvaade / kogusummad (eelvaates pumba pildi näide)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Jääte järgmise peatuseni täitma (väljad kustutatakse pärast salvestamist). Töötage täielikult **võrguühenduseta**; sünkroonimine töötab hiljem taustal, kui see on seadistatud.

### <a id="manual-entry-no-camera--bad-ocr"></a>Käsitsi sisestamine (kaamera puudub / halb OCR)

1. Puudutage **Odo**, **kulu** või **helitugevus** ja sisestage väärtused (portree kasutab süsteemi klaviatuuri; horisontaalpaigutus kasutab ekraanil kuvatavat klahvistikku).
2. Valige või kinnitage **Sõiduk**, kui automaatne tuvastamine ei käivitunud.
3. Salvestage nagu ülal.

### <a id="modes-and-borders"></a>Režiimid ja äärised

– **Roheline ääris** sõiduki ümber + odo → odomeetri jäädvustamine/muutmine.
– **Roheline ääris** kulu + maht → pumbarežiim.
– **Salvesta** jääb keelatuks, kuni valitakse sõiduk ja vähemalt üks parameetritest odo/cost/volume sisaldab andmeid ning optiline tekstituvastus ei tööta.

Ekraanil kuvatav näpunäide (juhiste rea all): *Katik = jäädvustamine · Ketas = salvesta · ↕ = odo/pumbarežiim · ↔ = vahetuskulu/maht.*

---

## <a id="expenses"></a>Kulud

### <a id="new-expense"></a>Uus kulu

Menüü → **Uus kulu**.

![Uus kuluvorm ja kaamera juhtnupud (näide kriipsu kujutis kaameraribal, kui see on tühi)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Salvesta** (ketas), **katik** (kviitungi foto) või **galerii** (vali pilt).
2. Täitke **Kuupäev**, **Sõiduk**, **Teenusepakkuja**, **Kirjeldus**, **Summa** (valuuta sümbol, koputatav), **Kategooria**, valikuline **Odomeeter**.
3. Mitmeleheküljelised kviitungid: jäädvustage täiendavaid lehti, kui kasutajaliides pakub lehitsemist (lehekülg 0 on esmane kviitung).
4. **Salvesta** salvestamiseks (kõigepealt kohalik; fotode varundamine ja arvutustabeli sünkroonimine toimub seadistamisel taustal).

### <a id="expense-list"></a>Kulude nimekiri

Menüü → **Aruanded** → **Kulude loend** — varasemate kütusega mitteseotud kulude sirvimine; avage redigeerimiseks üksus.

![Kululoend](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Muutke kulusid

Avage loendist rida. Õige müüja, summa, kategooria, sõiduk ja kirjeldus. Kui kviitung on ainult fotovarukoopias (loetav kohalik fail puudub), kasutage kuvamisel käsku **Too pilt arhiivist** (toimib konfigureeritud fotosihtkohtades).

![Muuda kulusid](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Alusta reisi

Menüü → **Alusta reisi** (pärast Sahtli täitmist). Jäädvustage või sisestage läbisõidumõõdik, valige reisi tüüp, salvestage **ketta** ikooniga. **Stopp** on otsetee Isiklikule nüüd peetavas GPS-asukohas. Kasutage **ⓘ** juhtmeeldetuletuste jaoks.

![Alusta reisi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Reisi stardid salvestatakse kütuseridadena **Trip Type**-ga (mitte tavalised täitmised). Need kuvatakse jaotises **Aruanded → Sõidumiilid**, mitte jaotises Kütuse ajalugu.

---

## <a id="reports"></a>Aruanded

Menüü → **Aruanded** avab tootekeskuse (kogu aegade kokkuvõte + kataloogikaardid). See on ainus tootearuannete pind – eraldi sahtli üksust "Aruanded ja diagrammid" pole.

![Aruannete keskus](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Avage kaart sõidukirežiimi (**Kõik / Iga / Üksik**), perioodifiltrite, diagrammide ja jagamise (**TEXT / CSV / PDF**) jaoks. Ülemine riba teatatavatest lastest: **☰ + ←** (ja **ⓘ** registreerimisel).

### <a id="time-based-reports"></a>Ajapõhised aruanded

Peamine kaardikaart. Valikulised mõõdikud (mpg, maht/kaugus, nt G/miil, ühikuhind, nt $/G, kulu/vahemaa, kuu $, reisi miilid, reisi % tüübi järgi) koos **Smooth** prügikastide ja **sõltumatute Y-kaaludega** (säästlikkus vasakul; raha ja reiside perekonnad paremal).

![Ajapõhised aruanded](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Ajapõhised aruanded – keritud mõõdikud](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Majandusmatemaatika üksikasjad: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Täitmise ajalugu vs kütuseajalugu

- **Aruanded → Täiteajalugu** — aruandefiltrite kronoloogilised täidised (**ainult täitmised**; reisi ei alga).

![Täida ajaloo aruanne](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

– **Kütuse ajalugu** (kui see on teie konstruktsiooni navigeerimismenüüs olemas) – sõidukipõhine täitevaru, samuti ainult täidetakse; puudutage redigeerimiseks rida.

### <a id="trip-miles"></a>Reisimiili

**Aruanded → Reisimiilid** – miilid tüübi, graafikute ja kronoloogilise **reisi alguse/lõigu loendi** järgi. Puudutage tõelist algust, et avada selle rea valik **Muuda täitmist**.

![Reisimiili](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Muuda täitmist

Avage täitmine jaotises Täiteajalugu, Kütuseajalugu või Sõidumiilid. Paigutus: sõiduk ja odomeeter, **valuuta enne maksumust**, maht, märkmed. Reisi tüüp kuvatakse ainult siis, kui rida on reisi algus. Asukohal on kokkuvõte pluss **Asukoha üksikasjad**. Pilveidentiteediga kohalik foto puudub: **Tooge pilt arhiivist**.

![Muuda täitmist – tooge arhiivist, kui kohalik foto puudub](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Muud kataloogikaardid sisaldavad kulusid kategooriate kaupa, sõiduki kokkuvõtet ja kulude nimekirja.

Raha kasutab iga rea ​​valuutat, kui see on määratud. Segavaluuta kogusummad näitavad **valuutade vahesummasid** (vaikset valuutavahetust ei toimu).

---

## <a id="syncing"></a>Sünkroonimine

Menüü → **Sünkroonimine** on arvutustabelite ja fotode sihtkohtade keskus (mitte ainult seadete alla maetud).

![Sünkroonimisjaotur](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

– kaardid **arvutustabeli sünkroonimiseks** ja **fotode varundamiseks** lühikese olekuga, **sünkroonimiseks** selle liigi jaoks ja **›** sihtkohtade loendisse.
– avage sihtkoht **Testi ühendust** ja **Sünkrooni kohe (see sihtkoht)** / kõik on konfigureeritud.
– tõrge **Üksikasjad** ja punane **!** tiitliribal maanduvad siia.
– Google'i arvutustabelite ja Drive'i samm-sammuline seadistamine: [Varundamine ja mitme seadmega sünkroonimine](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Seaded (kohalikud eelistused)

Menüü → **Seaded**.

![Seaded](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Rohkem seadeid (keritud)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Sihtkohtade jaoks eelistage **Menüü → Sünkroonimine**. Seaded võivad siiski kuvada kokkuvõtte ridu, mis avavad samu loendeid.

### <a id="local-preferences-common"></a>Kohalikud eelistused (tavalised)

– **Säästa kütusekviitungi fotod** / **Salvesta kulufotosid kohapeal** – hoia pilte seadmes (võib taotleda Photos luba).
– **Esita katiku heli**
– **Valuuta** / **Mahuühik** – rakenduse vaikeseaded (süsteemne või selgesõnaline). Mahuühiku muutmine olemasolevate kütuseandmetega võib avada teisendusdialoogi.
– **Tume režiim**
– **Seadistusnõuanded** – sõiduki esmakordse käitamise / sünkroonimise õpetused uuesti avamiseks.
– **Silumise täitmine** / **Kuva katsekuvad (dev)** – täiustatud; jäta igapäevaseks kasutamiseks maha. Siin ei dokumenteerita katseekraane.

CSV **eksport/import** (sõidukite / kulude / kütuse vahekaardid) on saadaval seadetes, kui seda pakub praegune versioon.

---

## <a id="help--about"></a>Abi ja teave

![Abi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![About](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

– **Abi** – seadmesisene kiirkäivitus, häälestusõpetused, link käesolevale juhendile, isehosti seadistamise register.
– **Teave** – versioon, litsentsid, GitHub, käesolev juhend (võrguühenduseta komplektis + võrgus HTML avaldamisel).

---

## <a id="related-docs"></a>Seotud dokumendid

– [QUICK_GUIDE.md](../QUICK_GUIDE.md) – lühike igapäevane juhend
– [self-host/INDEX.md](../reference/self-host/INDEX.md) – isehostitud fotode/tabelite seadistus
– [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) – liitmine, taastamine, duplikaadid
– [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) – majandusmõõdikute üksikasjad
