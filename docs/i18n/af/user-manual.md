# <a id="vehicle-expenses-automated--user-manual"></a>Voertuiguitgawes outomaties — Gebruikershandleiding

> **Redigeer bron (Markdown).** Blaaiers en die inprogram-leser maak die **weergegee HTML** oop:
> - Web: [`docs/user-manual/index.html`](index.html) (hergenereer met `./scripts/render-user-manual.sh`)
> - Toepassing: Hulp / Meer oor → volledige handleiding (gebundelde HTML + skermkiekies)
>
> Moenie eindgebruikers na rou `.md` URL's wys nie — blaaiers wys slegs gewone teks.

Kamera-eerste dop vir brandstofopvulling en voertuiguitgawes, met opsionele sinchronisasie en rugsteun van verskeie toestelle onder **jou** wolkrekeninge.

Dit is die **volledige handleiding** (skermkiekies + elke stap). Op die telefoon is **Kieslys → Hulp** 'n korter begingids.

**Nie hier gedek nie:** Voer ou prente in, belyningseksperiment en pompeksperiment (ontwikkelaar / gevorderde gereedskap).

---

## <a id="table-of-contents"></a> Inhoudsopgawe

1. [Wat jy nodig het](#what-you-need)
2. [Ikone in 'n oogopslag](#icons-at-a-glance)
3. [Maak die spyskaart oop](#open-the-menu)
4. [Eerste keer opstelling: Bestuur voertuie](#first-time-setup-manage-vehicles)
5. [Rugsteune en multi-toestelsinkronisering] (#backups-and-multi-device-sync)
6. [Vul (brandstof)](#fill-up-fuel)
7. [Begin reis](#start-trip)
8. [Onkoste](#expenses)
9. [Verslae](#reports)
10. [Instellings (plaaslike voorkeure)](#settings-local-preferences)
11. [Sinkronisering](#syncing)
12. [Hulp en meer oor](#help--about)
13. [Verwante dokumente](#related-docs)

---

## <a id="what-you-need"></a>Wat jy nodig het

- Android-foon of -tablet.
- Vir die beste OCR: 'n duidelike aansig van jou **dashboard-odometer** en **pomptotale** (of tik die nommers met die hand).
- Opsioneel: rekeninge **jy beheer** vir sigbladdata en/of fotorugsteun (sien [Rugsteune en multi-toestelsinkronisering](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Ikone in 'n oogopslag

Dit verskyn op die hoofskerms. Om hulle te ken, spaar baie jag.

| Waar | Ikoon / beheer | Wat dit doen |
|-------|----------------|----------------|
| Top bar | **☰ Spyskaart** (hamburger) | Maak die navigasielaai oop |
| Top bar | **ⓘ** (bladsyhulp) | Kort hulp vir die **huidige** bladsy (langs spyskaart wanneer beskikbaar) |
| Top bar | **`?N`** (geel) | Hangende invoerhersieningvrae — maak Invoerhersiening oop |
| Top bar | **!** (rooi) | 'n Sigblad of fotobestemming het onlangs misluk — maak **Sinkroniseer** oop om reg te maak |
| Top bar | **☰ + ←** | Rapporteer kinders en Uitgawes lys wys **spyskaart en terug** saam; Verslae-hub is slegs spyskaart |
| Instellings / brandstof wysig | **←** | Terug (instellings sigblad/foto en brandstofwysiging bly teruggefokus) |
| Vul | **Wit sirkel** (sluiter) | Vang kilometerteller of pompskerm vas vir OCR |
| Vul | **Skyf / Stoor** | Stoor die opvul (benodig 'n voertuig en ten minste een van odo / volume / koste) |
| Vul | **↕ pyle** (modusskakelaar) | Wissel **kilometermodus** vs **pompmodus (koste/volume)**. Groen rand beklemtoon die aktiewe veldgroep |
| Vul | **↔ pyle** (tussen koste en volume) | Ruil koste en volume om as OCR dit in die verkeerde velde plaas |
| Vul | **Zoom 1x / …** | Kamerazoemverhoudings wanneer die lens dit ondersteun |
| Vul (na vang) | **Verfris** op hoofknoppie | Gooi voorskou weg en keer terug na regstreekse kamera |
| Vul (terwyl verwerking) | **X** op hoofknoppie | Kanselleer aan die gang vasvang/OCR |
| Uitgawe | **Stoor** | Bespaar die uitgawe |
| Uitgawe | **Sluitersirkel** | Neem 'n kwitansiefoto |
| Uitgawe | **Galery** | Kies 'n kwitansieprent uit die biblioteek |
| Uitgawe | **Oorneem** | Vee huidige kwitansiefoto uit en skiet weer |
| Uitgawes / Bestuur voertuie | **+ / −** FAB'e | Zoem die fotovoorskou |
| Landmerke dialoog | **Redigeer OCR** ​​| Korrigeer of voeg landmerkteks by wat die enjins gemis het |
| Sigblad / Fotovorms | ** ** Soek** | Blaai deur Google Drive vir 'n blad of vouer (na aanmelding) |

Geldeenheidsimbole op kostevelde en **G/L** op volumevelde is tikbaar: maak 'n klein spyskaart oop om geldeenheid of liter teenoor liter vir daardie inskrywing te verander.

---

## <a id="open-the-menu"></a>Maak die spyskaart oop

1. Tik **☰** links bo.
2. Kies 'n bladsy.

![Navigasielaai](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Hooflaai:** Vul in · Begin rit · Bestuur voertuie · Nuwe uitgawe · **Verslae** · Instellings · Sinkronisering · Hulp · Oor.

**Eksperimentlaai** (Instellings → Wys eksperimentskerms): Belyningseksperiment · Pompeksperiment · **Voer ou prente in**.

**Via Verslae-hub (nie hooflaai nie):** Uitgaweslys · Vul geskiedenis in.

---

## <a id="first-time-setup-manage-vehicles"></a>Eerste keer opstelling: Bestuur voertuie

OCR en **outomatiese voertuigpassing** werk die beste nadat jy elke voertuig met 'n **verwysingspaneelbordfoto** geregistreer het, **Discovery** hardloop, die kilometerteller-oes vanaf 'n paddle-saad stel en landmerke vir daardie dash stoor.

### <a id="open-manage-vehicles"></a> Maak Bestuur voertuie oop

Kieslys → **Bestuur voertuie**. Kies 'n voertuig (of **Voeg nuwe voertuig by**).

![Bestuur voertuie met verwysingsstreep, oeste en ontdekking](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Voeg of wysig 'n voertuig

1. Maak die **Voertuig** aftreklys oop → kies 'n voertuig of **Voeg nuwe voertuig by**.
2. Vang of kies 'n duidelike **verwysingsstreepfoto** (volledige instrumentgroep, goed belig, telefoon min of meer vierkantig). Gebruik **Neem foto** of **Galery**.
3. Tik **Run Discovery** — paddle-detectie plaas geel **sade** op die foto (landmerke gebruik dieselfde `"ML Kit"` JSON as voorheen).
4. Tik **Kies Odo**, tik die saad op die syfers, pas die handvatsels aan, dan **Stoor**.
5. **Redigeer Odo** verstel 'n blokkie wat reeds daar is. **Clear Odo** verwyder dit, en die knoppie lees dan **Select Odo**.
6. Hersien met **Wys landmerke**. Gebruik **Redigeer OCR** ​​om verkeerd geleesde tekste reg te stel of **voeg teks by wat gemis is nie.
7. Vul **Voertuignaam** (vereis), plus fabrikaat/model/jaar/plaat in soos jy wil.
8. Tik op **Skep voertuig** of **Stoor veranderinge** (vereis naam + verwysingsfoto vir 'n nuwe voertuig).

![Voeg nuwe voertuigvormvelde by](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Landmerke: maak reg wat Discovery gemis het

Na **Wys landmerke**, blaai deur die lys en korrigeer waardes. Enjins mis soms klein syfers (byvoorbeeld 'n horlosie **60** regs onder in die groepie). Gebruik **Redigeer OCR** ​​om dit by te voeg of reg te stel sodat voertuigidentiteit betroubaar bly.

![Landmerke-dialoog - hersien en wysig OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Tik sonder 'n perfekte foto

Jy kan steeds die toepassing gebruik deur 'n voertuig te kies en **tik** kilometerteller, volume en koste op Vul - OCR is opsioneel vir elke veld. Galery-invoer werk vir die verwysingsstreepfoto wanneer jy verkies om nie inprogram te skiet nie.

**Wenk:** Na sigbladsinkronisering, is voertuigdefinisies (gewasse, landmerke) in die plaaslike databasis beskikbaar - jy hoef nie Bestuur voertuie vir vul weer oop te maak om dit te gebruik nie.

---

## <a id="backups-and-multi-device-sync"></a>Rugsteune en multi-toestel-sinkronisering

Die toepassing is so gebou dat **verskeie fone of tablette dieselfde vlootdata kan deel**, en dus kan jy 'n **kopie van jou data en foto's van die toestel af hou**. Dit word gedoen met bestemmings **jy** konfigureer onder **jou** rekeninge of **jou** bedieners wat self aangebied word – nie 'n maatskappybestuurde "Voertuiguitgawes-wolk" wat ander mense kan sien nie.

### <a id="what-runs-where"></a>Wat loop waarheen

| Soort | Wat dit stoor | Tipiese gebruik |
|------|----------------|----------------|
| **Sigblad / tabelvormsinkronisering** | Voertuie, brandstofvulsels, uitgawes (rye en oortjies) | Multi-toestel samesmelting + gestruktureerde rugsteun |
| **Foto rugsteun** | Binêre beelde (streep / pomp / kwitansie / verwysing foto's) | Foto rugsteun + herstel ontbrekende lêers |

Jy kan **veelvuldige bestemmings** van elke soort instel (sagte pet per tipe). Handmatige **Sinkroniseer nou** en **agtergrond**-werkers loop die geaktiveerdes.

### <a id="offline-first"></a> Eers vanlyn

- **Geen netwerk word benodig** om 'n opvulling, uitgawe of kwitansie by te voeg nie. Alles word **eerste plaaslik** gestoor.
- Wanneer die netwerk beskikbaar is, loop sinkronisering en foto-rugsteun as **agtergrondtake** (op 'n skedule wat jy stel, en wanneer jy **Sinkroniseer nou** tik). Mislukkings wys as rooi teks onder die instellingsrye en 'n **!** in die programtitelbalk.

### <a id="your-accounts-only"></a>Slegs jou rekeninge

Aanmelding en tekens bly op die toestel vir die verskaffers wat jy kies (Google, Microsoft, S3-sleutels, URL's wat self aangebied word, ensovoorts). Bestemmings is onder **volle beheer van die gebruiker** — jou Google-rekening, jou OneDrive, jou MinIO-emmer, jou EtherCalc-gasheer, ens. Niks word met ander Voertuiguitgawes-gebruikers gedeel deur 'n gedeelde agterkant nie.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Ondersteunde teikens — data (sigblad / tabel)

Gekonfigureer onder **Kieslys → Sinkronisering → Sigbladsinkronisering** (ook bereikbaar vanaf Instellingsopsommingsrye). Eersteklas kieser opsies:

| Teiken | Notas |
|--------|--------|
| **Google Blaaie** | Algemene verstek; oortjies vir voertuie, uitgawes en brandstof per voertuig |
| **Excel** | Microsoft werkboek via Graph / OneDrive-styl binding |
| **EtherCalc** | Self-gasheer samewerkende sigbladkamers |
| **Ander →** geïmplementeerde backends | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Uitgestel / nog nie koploos nie (gelys onder Ander maar nie ten volle geïmplementeer nie): OnlyOffice, Collabora. Sien ook [selfgasheerindeks](reference/self-host/INDEX.md).

CSV **uitvoer/invoer** (zip van dieselfde oortjie-uitleg) is beskikbaar vanaf Instellings as 'n draagbare rugsteun, onafhanklik van lewendige sinkronisering.

### <a id="supported-targets--photos-image-backup"></a>Ondersteunde teikens — foto's (prentrugsteun)

Gekonfigureer onder **Kieslys → Sinkronisering → Foto-rugsteun** (ook vanaf Instellings-opsommingsrye):

| Teiken | Notas |
|--------|--------|
| **Google Drive** | Voer wat jy kies (blaai of plak URL) |
| **OneDrive** | Microsoft-rekening + pad voorvoegsel |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO en ander S3-versoenbare eindpunte |
| **Ander** | rkloon-gesteunde berging (bv. WebDAV, SFTP en ander saamgestelde afstandbeheerders beskikbaar in die inprogram-kieser) |

Stel cheatsheets op vir foto- en tabelteikens wat self aangebied word: [self-gasheer-indeks](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Multi-toestel gedrag (kort)

- Rye smelt saam deur **Sinkroniseer-ID** met **laasskryf-wen** op **Opgedateerde** tydstempels.
- Delete is sag; 'n nuwer wysiging op 'n ander toestel kan 'n ry herstel.
- Deur die **dieselfde vul twee keer** op twee toestelle in te voer, word **twee rye** geskep - vee die ekstra uit wanneer jy agterkom.
- Meer besonderhede: [Sinkroniseer gedragnotas](#sync-behavior-notes) en [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Voorbeeld: voeg Google Sheets (data) by

1. **Kieslys → Sinkronisering → Sigbladsinkronisering** (of Instellings → Sigbladsinkronisering).

![Sigbladsinkroniseringlys](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Tik **Voeg sigbladbestemming by**.

![Verskafferkieser](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Kies **Google Sheets**.

![Google Blaaie-vorm](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Meld een keer aan met Google** — daardie aanmelding laat jou ook blaai. Vertoonnaam → plak 'n **Blad-URL** (nog geldig) of ****** blaai. Blaai maak oop op **Alles en gedeel** sodat jy 'n bestaande sigblad wat jy besit of wat met jou gedeel word, kan kies. Skeduleer dan opsies → aktiveer → stoor.
5. **Sinkroniseer nou** een keer om oortjies te skep/opdateer: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Voorbeeld: voeg Google Drive (foto's) by

1. **Kieslys → Sinkronisering → Foto-rugsteun** (of Instellings → Foto-rugsteun).

![Foto rugsteun lys](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Tik op **Voeg fotobestemming by**.

![Fotoverskafferkieser](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Kies **Google Drive**.

![Google Drive-vorm](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Meld aan met Google (Drive)** → opsionele vouer-URL/blaai → aktiveer → stoor → **Sinkroniseer nou**.

Handleiding **Sinkroniseer nou** vir foto's is 'n volpas; agtergrondrugsteun verwerk tipies **slegs hangende** oplaaie volgens 'n skedule.

### <a id="sync-behavior-notes"></a>Sinkroniseer gedragsnotas

- Na die opgradering van die toepassing kan u dalk kortliks **“Dateer databasis opdateer na opgradering …”** (plaaslike sinchronisasie-ID-aanvulling) sien.
- As 'n sinkronisering onderbreek word, smelt die volgende **suksesvolle** sinkronisering weer saam en herstel afgeleë oortjies.
- Mislukkings: rooi opsomming oor sinkroniseringskaarte + **!** in die toepassingbalk.

---

## <a id="fill-up-fuel"></a>Vul (brandstof)

Dit is die **tuisskerm** wanneer jy die toepassing oopmaak.

### <a id="vehicle-selection-usually-automatic"></a>Voertuigkeuse (gewoonlik outomaties)

Jy hoef **nie** eers die voertuig te kies nie. Wanneer voertuie **landmerke** opgestel het in Bestuur voertuie, bespeur Fill **outo-bespeur watter voertuig** vanaf die dash-prent nadat jy die kilometerteller vasgelê het. Jy kan steeds die **Voertuig**-aftreklys oopmaak om te ignoreer indien nodig.

### <a id="aim-at-the-odometer"></a>Mik na die kilometerteller

Bly in die kilometertellermodus en raam die groepering. Instruksie: *Mik op kilometerteller. Tik die sluiter om vas te vang.*

![Vul — lewendige kamera op kilometerteller (voorbeeld streepprent in voorskou)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Na die kilometerteller-sluiter

OCR vul **Odo** en probeer om die voertuig vanaf landmerke te pas (hersien albei indien nodig). Die hoofknoppie word **Herprobeer** om weer te skiet. Instruksie som die leeswerk op.

![Vul — kilometerteller OCR-resultaat](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a> Pompmodus (koste en volume)

1. Tik **↕** om na pompmodus oor te skakel: *Mik na pompvertoning (koste/volume). Tik die sluiter.*
2. Vang die pomptotale vas. Koste en volume velde vul; gebruik **↔** as hulle omgeruil word.
3. Tik geldeenheid of **G/L** indien nodig, dan **Stoor** (skyf). Leë velde maak 'n **gedeeltelike vulling** (steeds toegelaat).

![Vul — pompvoorskou / totale (voorbeeld pompprent in voorskou)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Jy bly op Vul vir die volgende stop (velde word skoongemaak na stoor). Werk ten volle **vanlyn**; sinchronisasie loop later op die agtergrond wanneer dit opgestel is.

### <a id="manual-entry-no-camera--bad-ocr"></a>Handmatige invoer (geen kamera / slegte OCR)

1. Tik **Odo**, **koste** of **volume** en tipe waardes (portret gebruik die stelselsleutelbord; landskap gebruik 'n skermsleutelbord).
2. Kies of bevestig die **Voertuig** as outo-opsporing nie geloop het nie.
3. Stoor soos hierbo.

### <a id="modes-and-borders"></a>Modusse en grense

- **Groen rand** om voertuig+odo → vasvang/redigeer kilometerteller.
- **Groen rand** rondom koste+volume → pompmodus.
- **Stoor** bly gedeaktiveer totdat 'n voertuig gekies word en ten minste een van odo/koste/volume het data, en OCR werk nie steeds nie.

Wenk op die skerm (onder die instruksielyn): *Sluiter = vasvang · Skyf = stoor · ↕ = odo/pompmodus · ↔ = ruil koste/volume.*

---

## <a id="expenses"></a> Uitgawes

### <a id="new-expense"></a>Nuwe uitgawe

Kieslys → **Nuwe uitgawe**.

![Nuwe uitgawevorm en kamerakontroles (voorbeeld streepprent in kameraband wanneer leeg)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Stoor** (skyf), **sluiter** (kwitansiefoto), of **galery** (kies prent).
2. Vul **Datum**, **Voertuig**, **Verskaffer**, **Beskrywing**, **Bedrag** (geldeenheidsimbool tikbaar), **Kategorie**, opsioneel **Kilometerteller**.
3. Multi-bladsy kwitansies: neem bykomende bladsye vas as die UI blaaier bied (bladsy 0 is die primêre kwitansie).
4. **Stoor** om te stoor (plaaslik eerste; foto-rugsteun en sigbladsinkronisering vind op die agtergrond plaas wanneer dit opgestel is).

### <a id="expense-list"></a>Onkostelys

Kieslys → **Verslae** → **Uitgaweslys** — blaai deur verlede nie-brandstofuitgawes; maak 'n item oop om te wysig.

![Onkostelys](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Redigeer uitgawe

Maak 'n ry uit die lys oop. Korrekte verkoper, bedrag, kategorie, voertuig en beskrywing. As die kwitansie slegs in foto-rugsteun is (geen leesbare plaaslike lêer nie), gebruik **Haal prent uit argief** wanneer dit gewys word (werk oor gekonfigureerde fotobestemmings).

![Redigeer uitgawe](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Begin reis

Kieslys → **Begin reis** (na Vul die laai in). Vang of voer kilometerteller in, kies rittipe, stoor met die **skyf**-ikoon. **Stop** is 'n kortpad vir Persoonlik nou by die gehoue ​​GPS-ligging. Gebruik **ⓘ** vir beheeraanmanings.

![Begin reis](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Ritbegin word as brandstofrye met 'n **Trip Tipe** gestoor (nie normale vullings nie). Hulle verskyn onder **Verslae → Reismyle**, nie onder Brandstofgeskiedenis nie.

---

## <a id="reports"></a>Verslae

Kieslys → **Verslae** maak die produksentrum oop (opsomming van alle tye + kataloguskaarte). Dit is die enigste produkverslae-oppervlak - daar is geen aparte "Verslae en grafieke"-laaiitem nie.

![Verslae-hub](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Maak 'n kaart oop vir voertuigmodus (**Alles / Elk / Enkel**), periodefilters, kaarte en deel (**TEKS / CSV / PDF**). Topbalk op rapporteer kinders: **☰ + ←** (en **ⓘ** wanneer geregistreer).

### <a id="time-based-reports"></a>Tydgebaseerde verslae

Die hoofkaartkaart. Opsionele maatstawwe (mpg, volume/afstand soos G/mi, eenheidsprys soos $/G, koste/afstand, maandelikse $, ritmyl, rit% volgens tipe) met **Gladde**-bakke en **onafhanklike Y-skale** (ekonomie links; geld- en reisgesinne aan die regterkant).

![Tydgebaseerde verslae](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Tydgebaseerde verslae — gerolde statistieke](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Besonderhede van ekonomiese wiskunde: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Vul geskiedenis vs brandstofgeskiedenis

- **Verslae → Vul geskiedenis in** — chronologiese invul vir die verslagfilters (**vul slegs**; geen reis begin nie).

![Vul geskiedenisverslag in](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Brandstofgeskiedenis** (indien teenwoordig in jou gebou se navigasie) - vulvoorraad per voertuig, vul ook net; tik 'n ry om te wysig.

### <a id="trip-miles"></a> Reis myl

**Verslae → Reismyl** — myl volgens tipe, kaarte en 'n chronologiese **reisbegin-/segmentlys**. Tik 'n regte begin om **Redigeer vul** vir daardie ry oop te maak.

![Trip myl](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Redigeer vul

Van Vulgeskiedenis, Brandstofgeskiedenis of Reismyl, maak 'n vul oop. Uitleg: voertuig en kilometerteller, **geldeenheid voor koste**, volume, notas. Rittipe verskyn slegs wanneer die ry 'n ritbegin is. Ligging het 'n opsomming plus **Liggingbesonderhede**. Ontbrekende plaaslike foto met wolkidentiteit: **Haal prent uit argief**.

![Redigeer vul — Haal uit argief wanneer plaaslike foto ontbreek](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Ander kataloguskaarte sluit uitgawes volgens kategorie, voertuigopsomming en uitgawelys in.

Geld gebruik elke ry se geldeenheid wanneer dit gestel is. Gemengde-valuta-totale wys **per-geldeenheid-subtotale** (geen stille FX-omskakeling nie).

---

## <a id="syncing"></a>Sinkronisering

Kieslys → **Sinkronisering** is die middelpunt vir sigblad- en fotobestemmings (nie net onder Instellings begrawe nie).

![Sinkroniseringspilpunt](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Kaarte vir **Sigbladsinkronisering** en **Fotorugsteun** met kort status, **Sinkronisering** vir daardie soort, en **›** in die bestemmingslys.
- Maak 'n bestemming oop vir **Toetsverbinding** en **Sinkroniseer nou (hierdie bestemming)** / alles gekonfigureer.
- Mislukking **Besonderhede** en die rooi **!** in die titelbalk land hier.
- Stap-vir-stap Google Sheets en Drive-opstelling: [Rugsteune en multi-toestel-sinkronisering] (#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Instellings (plaaslike voorkeure)

Kieslys → **Instellings**.

![Instellings](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Meer instellings (geblaai)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Vir bestemmings, verkies **Menu → Sinkronisering**. Instellings kan steeds opsommingsrye wys wat dieselfde lyste oopmaak.

### <a id="local-preferences-common"></a>Plaaslike voorkeure (algemeen)

- **Stoor brandstofkwitansiefoto's** / **Stoor uitgawefoto's plaaslik** — hou prente op die toestel (kan foto's toestemming versoek).
- **Speel sluiterklank**
- **Geldeenheid** / **Volume-eenheid** — toepassingsverstellings (stelsel of eksplisiet). Die verandering van volume-eenheid met bestaande brandstofdata kan 'n omskakeldialoog bied.
- **Donker modus**
- **Opstellingswenke** — heropen eerste-run voertuig / sinchroniseer tutoriale.
- **Debug Fill** / **Wys eksperimentskerms (dev)** — gevorderd; los vir daaglikse gebruik. Eksperimentskerms word nie hier gedokumenteer nie.

CSV **uitvoer/invoer** (zip van voertuie / uitgawes / brandstof-oortjies) is beskikbaar vanaf Instellings wanneer dit deur die huidige gebou aangebied word.

---

## <a id="help--about"></a>Hulp en meer oor

![Hulp](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Oor](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Help** — vinnige begin op die toestel, opstel tutoriale, skakel na hierdie handleiding, self-gasheer opstel indeks.
- **About** — weergawe, lisensies, GitHub, hierdie handleiding (vanlyn saamgebondel + aanlyn HTML wanneer gepubliseer).

---

## <a id="related-docs"></a>Verwante dokumente

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — kort daaglikse gids
- [self-gasheer/INDEX.md](../reference/self-host/INDEX.md) — self-gasheer foto/tabelopstelling
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) - saamsmelt, herstel, duplikate
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — ekonomiese statistieke detail
