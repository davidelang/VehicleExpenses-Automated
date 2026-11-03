# <a id="vehicle-expenses-automated--user-manual"></a>Automaattiset ajoneuvokulut — Käyttöopas

> **Muokkaa lähdettä (Markdown).** Selaimet ja sovelluksen sisäinen lukija avaavat **renderoidun HTML-koodin**:
> - Verkko: [`docs/user-manual/index.html`](index.html) (luo uudelleen komennolla `./scripts/render-user-manual.sh`)
> - Sovellus: Ohje / Tietoja → täydellinen käyttöopas (mukana HTML + kuvakaappaukset)
>
> Älä osoita käyttäjiä raaka-URL-osoitteisiin `.md` – selaimet näyttävät vain pelkkää tekstiä.

Kameran ensimmäinen seuranta polttoaineen täyttöjä ja ajoneuvokuluja varten valinnaisella usean laitteen synkronoinnilla ja varmuuskopioinnilla **omien** pilvitilien alla.

Tämä on **koko käsikirja** (kuvakaappaukset + jokainen vaihe). Puhelimessa **Valikko → Ohje** on lyhyempi aloitusopas.

**Ei käsitellä tässä:** Tuo vanhat kuvat, kohdistuskokeilu ja pumppukoe (kehittäjä / lisätyökalut).

---

## <a id="table-of-contents"></a>Sisällysluettelo

1. [Mitä tarvitset](#what-you-need)
2. [Kuvakkeet yhdellä silmäyksellä](#icons-at-a-glance)
3. [Avaa valikko](#open-the-menu)
4. [Ensimmäinen asennus: Hallitse ajoneuvoja](#first-time-setup-manage-vehicles)
5. [Varmuuskopiot ja usean laitteen synkronointi](#backups-and-multi-device-sync)
6. [Täytä (polttoaine)](#fill-up-fuel)
7. [Aloita matka](#start-trip)
8. [Kulut](#expenses)
9. [Raportit](#reports)
10. [Asetukset (paikalliset asetukset)](#settings-local-preferences)
11. [Synkronointi](#syncing)
12. [Ohje ja Tietoja](#help--about)
13. [Aiheeseen liittyvät asiakirjat](#related-docs)

---

## <a id="what-you-need"></a>Mitä tarvitset

- Android-puhelin tai tabletti.
– Paras tekstintunnistus: selkeä näkymä **kojelaudan matkamittarista** ja **pumpun kokonaisluvuista** (tai kirjoita numerot käsin).
– Valinnainen: tilit **hallinnassasi** laskentataulukkotietojen ja/tai valokuvien varmuuskopiointia varten (katso [Varmuuskopiot ja usean laitteen synkronointi](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Kuvakkeet yhdellä silmäyksellä

Nämä näkyvät päänäytöissä. Niiden tunteminen säästää paljon metsästystä.

| Missä | Kuvake / säädin | Mitä se tekee |
|-------|-----------------|---------------|
| Yläpalkki | **☰ Menu** (hampurilainen) | Avaa navigointilaatikon |
| Yläpalkki | **ⓘ** (sivun ohje) | Lyhyt ohje **nykyiselle**-sivulle (valikon vieressä, kun se on saatavilla) |
| Yläpalkki | **`?N`** (keltainen) | Odottavat tuontitarkistuskysymykset — avaa Tuo tarkistus |
| Yläpalkki | **!** (punainen) | Laskentataulukon tai valokuvan kohde epäonnistui äskettäin — avaa **Synkronointi** korjataksesi |
| Yläpalkki | **☰ + ←** | Raportti lapset ja kululuettelo näyttää **menun ja takaisin** yhdessä; Raporttikeskus on vain valikko |
| Asetukset / polttoaineen muokkaus | **←** | Takaisin (asetukset laskentataulukko/valokuva ja polttoaineen muokkaus pysyvät taaksepäin keskittyneinä) |
| Täytä | **Valkoinen ympyrä** (suljin) | Kaappaa matkamittarin tai pumpun näyttö tekstintunnistusta varten |
| Täytä | **Levy / Tallenna** | Tallenna täyttö (vaatii ajoneuvon ja vähintään yhden odo / tilavuus / hinta) |
| Täytä | **↕ nuolet** (tilakytkin) | Vaihda **matkamittarin tila** vs. **pumppu (hinta/tilavuus) -tila**. Vihreä reuna korostaa aktiivisen kenttäryhmän |
| Täytä | **↔ nuolet** (kustannusten ja tilavuuden välillä) | Vaihda kustannukset ja määrä, jos OCR asetti ne vääriin kenttiin |
| Täytä | **Zoomaa 1x / …** | Kameran zoomaussuhteet, kun objektiivi tukee niitä |
| Täyttö (sieppauksen jälkeen) | **Päivitä** pääpainikkeessa | Hylkää esikatselu ja palaa live-kameraan |
| Täytä (käsittelyn aikana) | **X** pääpainikkeessa | Peruuta käynnissä oleva kaappaus/OCR |
| Kulut | **Tallenna** | Säästä kulut |
| Kulut | **Sulkimen ympyrä** | Ota kuittikuva |
| Kulut | **Galleria** | Valitse kuittikuva kirjastosta |
| Kulut | **Uudelleenotto** | Tyhjennä nykyinen kuittikuva ja kuvaa uudelleen |
| Kustannukset / Ajoneuvojen hallinta | **+ / −** FAB:t | Zoomaa kuvan esikatselu |
| Maamerkit-valintaikkuna | **Muokkaa tekstintunnistusta** | Korjaa tai lisää maamerkkiteksti, josta moottorit eivät jääneet |
| Laskentataulukko / valokuvalomakkeet | **🔍 Haku** | Selaa Google Drivesta taulukkoa tai kansiota (kirjautumisen jälkeen) |

Kustannuskenttien valuuttasymbolit ja tilavuuskenttien **G/L** ovat napsautettavat: avaa pieni valikko vaihtaaksesi valuuttaa tai gallonaa litraa vastaan.

---

## <a id="open-the-menu"></a>Avaa valikko

1. Napauta **☰** vasemmassa yläkulmassa.
2. Valitse sivu.

![Navigointilaatikko](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Päälaatikko:** Täyttö · Aloita matka · Hallinnoi ajoneuvoja · Uusi kulu · **Raportit** · Asetukset · Synkronointi · Ohje · Tietoja.

**Kokeilulaatikko** (Asetukset → Näytä kokeilunäytöt): Kohdistuskoe · Pumppukoe · **Tuo vanhoja kuvia**.

**Raporttikeskuksen kautta (ei päälaatikko):** Kululuettelo · Täytä historia.

---

## <a id="first-time-setup-manage-vehicles"></a>Ensimmäinen asennus: Hallinnoi ajoneuvoja

OCR ja **automaattinen ajoneuvojen täsmäys** toimivat parhaiten, kun olet rekisteröinyt jokaisen ajoneuvon **viitekuvalla kojelautaan**, suoritat **Discoveryn**, määrität matkamittarin sadon melan siemenestä ja tallennat kyseisen kojelaudan maamerkit.

### <a id="open-manage-vehicles"></a>Avaa Hallitse ajoneuvoja

Valikko → **Hallinnoi ajoneuvoja**. Valitse ajoneuvo (tai **Lisää uusi ajoneuvo**).

![Hallinnoi ajoneuvoja viiteviivalla, viljelykasveilla ja löydöillä](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Lisää tai muokkaa ajoneuvo

1. Avaa **Ajoneuvo**-pudotusvalikko → valitse ajoneuvo tai **Lisää uusi ajoneuvo**.
2. Ota tai valitse selkeä **viiteviivakuva** (täysi mittaristo, hyvin valaistu, puhelin suunnilleen neliömäinen). Käytä **Ota valokuva** tai **Galleria**.
3. Napauta **Suorita Discovery** — melan tunnistus sijoittaa keltaisia ​​**siemeniä** valokuvaan (maamerkit käyttävät samaa `"ML Kit"` JSON-koodia kuin aiemmin).
4. Napauta **Valitse Odo**, napauta numeroiden siementä, säädä kahvoja ja sitten **Tallenna**.
5. **Edit Odo** säätää jo olemassa olevaa laatikkoa. **Clear Odo** poistaa sen, ja painikkeessa lukee **Valitse Odo**.
6. Tarkista **Näytä maamerkit** -toiminnolla. Käytä **Muokkaa tekstintunnistusta** korjataksesi virheellisiä lukuja tai **lisääksesi** tekstiä, joka jäi huomaamatta.
7. Täytä **Ajoneuvon nimi** (pakollinen) sekä merkki/malli/vuosi/kilpi haluamallasi tavalla.
8. Napauta **Luo ajoneuvo** tai **Tallenna muutokset** (vaatii nimen + viitekuvan uudelle ajoneuvolle).

![Lisää uusia ajoneuvolomakkeen kenttiä](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Maamerkit: korjaa se, mikä Discoveryltä jäi huomaamatta

Vieritä luetteloa **Näytä maamerkit** -kohdan jälkeen ja korjaa arvot. Moottorista puuttuu joskus pieniä numeroita (esimerkiksi kello **60** klusterin oikeassa alakulmassa). Käytä **Muokkaa OCR:ää** lisätäksesi tai korjataksesi ne, jotta ajoneuvon identiteetti pysyy luotettavana.

![Maamerkit-valintaikkuna — tarkista ja muokkaa tekstintunnistusta](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Kirjoittaminen ilman täydellistä valokuvaa

Voit silti käyttää sovellusta valitsemalla ajoneuvon ja **kirjoittamalla** matkamittarin, äänenvoimakkuuden ja hinnan täyttöön – OCR on valinnainen jokaiselle kenttään. Gallerian tuonti toimii viiteviivakuvassa, kun et halua kuvata sovelluksen sisällä.

**Vinkki:** Laskentataulukon synkronoinnin jälkeen ajoneuvojen määritelmät (viljelmät, maamerkit) säilyvät paikallisessa tietokannassa – sinun ei tarvitse avata uudelleen Täytä ajoneuvojen hallintaa käyttääksesi niitä.

---

## <a id="backups-and-multi-device-sync"></a>Varmuuskopiot ja usean laitteen synkronointi

Sovellus on rakennettu niin, että **usat puhelimet tai tabletit voivat jakaa saman kaluston tiedot**, joten voit pitää **kopion tiedoistasi ja valokuvistasi pois laitteeltasi**. Tämä tehdään kohteilla, jotka **sinä** määrität **tileissäsi** tai **omaisännöidyissä** palvelimissa – ei yrityksen ylläpitämä "Vehicle Expenses -pilvi", jonka muut voivat nähdä.

### <a id="what-runs-where"></a>Mikä ajaa missä

| Kiltti | Mitä se varastoi | Tyypillinen käyttö |
|------|-----------------|-------------|
| **Laskentataulukon/taulukon synkronointi** | Ajoneuvot, polttoaineen täyttömäärät, kulut (rivit ja välilehdet) | Usean laitteen yhdistäminen + strukturoitu varmuuskopiointi |
| **Valokuvien varmuuskopiointi** | Binaarikuvat (kojetaulu/pumppu/kuitti/viitekuvat) | Valokuvien varmuuskopiointi + puuttuvien tiedostojen palauttaminen |

Voit määrittää **useita kohteita** kutakin tyyppiä kohden (pehmeä korkki tyyppiä kohden). Manuaalinen **Synkronoi nyt** ja **tausta** -työntekijät käyttävät käytössä olevia.

### <a id="offline-first"></a> Offline ensin

- **Verkkoa ei tarvita** täytön, kulujen tai kuitin lisäämiseen. Kaikki tallennetaan **paikallisesti ensin**.
– Kun verkko on käytettävissä, synkronointi ja valokuvien varmuuskopiointi suoritetaan **taustatehtävinä** (määritetyssä aikataulussa ja napauttamalla **Synkronoi nyt**). Virheet näkyvät punaisena tekstinä Asetukset-riveillä ja **!**-merkillä sovelluksen otsikkorivillä.

### <a id="your-accounts-only"></a>Vain omat tilit

Kirjautuminen ja tunnukset pysyvät laitteessa valitsemillesi palveluntarjoajille (Google, Microsoft, S3-avaimet, itseisännöidyt URL-osoitteet ja niin edelleen). Kohteet ovat **käyttäjän täydessä hallinnassa** – Google-tilisi, OneDrive, MinIO-säilö, EtherCalc-isäntä jne. Mitään ei jaeta muiden ajoneuvokulujen käyttäjien kanssa jaetun taustajärjestelmän kautta.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Tuetut kohteet — tiedot (laskentataulukko / taulukko)

Määritetty kohdassa **Valikko → Synkronointi → Taulukkosynkronointi** (pääsee myös asetusten yhteenvedoriveiltä). Ensiluokkaiset poimintavaihtoehdot:

| Kohde | Huomautuksia |
|---------|--------|
| **Google Sheets** | Yhteinen oletusarvo; välilehdet ajoneuvoille, kuluille ja ajoneuvokohtaiselle polttoaineelle |
| **Excel** | Microsoft-työkirja Graph / OneDrive-tyylisellä sidontalla |
| **EtherCalc** | Itseisännöidyt yhteiskäyttöiset taulukkolaskentahuoneet |
| **Muut →** toteutetut taustaohjelmat | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Lykätty / ei vielä päättömänä (lueteltu kohdassa Muut, mutta ei täysin toteutettu): OnlyOffice, Collabora. Katso myös [self-host index](reference/self-host/INDEX.md).

CSV **vienti/tuonti** (saman välilehden asettelun ZIP) on saatavana Asetuksista kannettavana varmuuskopiona, riippumatta live-synkronoinnista.

### <a id="supported-targets--photos-image-backup"></a>Tuetut kohteet — valokuvat (kuvan varmuuskopio)

Konfiguroitu kohdassa **Valikko → Synkronointi → Valokuvien varmuuskopiointi** (myös asetusten yhteenvetoriveiltä):

| Kohde | Huomautuksia |
|---------|--------|
| **Google Drive** | Valitsemasi kansio (selaa tai liitä URL-osoite) |
| **OneDrive** | Microsoft-tili + polun etuliite |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO ja muut S3-yhteensopivat päätepisteet |
| **Muu** | rclone-tuettu tallennustila (esim. WebDAV, SFTP ja muut kuratoidut kaukosäätimet, jotka ovat saatavilla sovelluksen sisäisessä poimijassa) |

Luo itseisännöidyt valokuva- ja taulukkokohteet: [self-host index](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Usean laitteen toiminta (lyhyt)

– Rivit yhdistetään **synkronointitunnuksella** **viimeisen kirjoitusvoiton** kanssa **päivitetyissä** aikaleimoissa.
- Poistot ovat pehmeitä; uudempi muokkaus toisella laitteella voi palauttaa rivin.
– **sama täyttö kahdesti** kahdella laitteella luo **kaksi riviä** – poista ylimääräinen, kun huomaat.
– Lisätietoja: [Synkronointikäyttäytymisilmoitukset](#sync-behavior-notes) ja [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Esimerkki: lisää Google Sheets (data)

1. **Valikko → Synkronointi → Taulukkosynkronointi** (tai Asetukset → Taulukkosynkronointi).

![Laskentataulukon synkronointiluettelo](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Napauta **Lisää laskentataulukon kohde**.

![Provider picker](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Valitse **Google Sheets**.

![Google Sheets -lomake](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Kirjaudu sisään Googlella** kerran – kirjautumisen avulla voit myös selata. Näyttönimi → liitä **taulukon URL-osoite** (edelleen) tai **🔍** selaa. Selaus avautuu kohdassa **Kaikki ja jaetut**, jotta voit valita olemassa olevan laskentataulukon, jonka omistat tai joka on jaettu kanssasi. Sitten ajoita asetukset → ota käyttöön → tallenna.
5. **Synkronoi nyt** kerran luodaksesi/päivittääksesi välilehtiä: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Esimerkki: lisää Google Drive (kuvat)

1. **Valikko → Synkronointi → Valokuvan varmuuskopiointi** (tai Asetukset → Valokuvien varmuuskopiointi).

![Photo backup list](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Napauta **Lisää valokuvakohde**.

![Photo provider picker](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Valitse **Google Drive**.

![Google Drive -lomake](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Kirjaudu sisään Googlella (Drive)** → valinnainen kansion URL-osoite/selaa → ota käyttöön → tallenna → **Synkronoi nyt**.

Manuaalinen **Synkronoi nyt** valokuville on täysi passi; taustavarmuuskopiointi käsittelee yleensä **vain odottavat** lataukset aikataulun mukaisesti.

### <a id="sync-behavior-notes"></a>Synkronointikäyttäytymistä koskevat huomautukset

- Sovelluksen päivityksen jälkeen saatat nähdä hetken **"Päivitetään tietokantaa päivityksen jälkeen..."** (paikallinen synkronointitunnuksen jälkitäyttö).
– Jos synkronointi keskeytyy, seuraava **onnistunut** synkronointi sulautuu uudelleen ja korjaa etävälilehdet.
- Virheet: punainen yhteenveto korttien synkronoinnissa + **!** sovelluspalkissa.

---

## <a id="fill-up-fuel"></a>Täytä (polttoaine)

Tämä on **aloitusnäyttö**, kun avaat sovelluksen.

### <a id="vehicle-selection-usually-automatic"></a>Ajoneuvon valinta (yleensä automaattinen)

Sinun **ei** tarvitse valita ajoneuvoa ensin. When vehicles have **landmarks** set up in Manage Vehicles, Fill **auto-detects which vehicle** from the dash image after you capture the odometer. Voit silti avata avattavan **Ajoneuvo**-valikon ohittaaksesi sen tarvittaessa.

### <a id="aim-at-the-odometer"></a> Tähtää matkamittariin

Pysy matkamittaritilassa ja kehystä klusteri. Ohje: * Tähtää matkamittariin. Napauta suljinta ottaaksesi kuvan.*

![Täytä — reaaliaikainen kamera matkamittarissa (esimerkiksi viivakuva esikatselussa)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>matkamittarin sulkimen jälkeen

OCR täyttää **Odon** ja yrittää löytää ajoneuvon maamerkeistä (tarkista molemmat tarvittaessa). Pääpainikkeesta tulee **Uudelleenkuvaus**. Ohje tiivistää lukeman.

![Täyttö — matkamittarin OCR-tulos](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Pumpputila (hinta ja tilavuus)

1. Napauta **↕** vaihtaaksesi pumpputilaan: * Suuntaa pumpun näyttöön (hinta/tilavuus). Napauta suljinta.*
2. Tallenna pumpun kokonaissummat. Kustannus- ja volyymikentät täyttyvät; käytä **↔**, jos ne vaihdetaan.
3. Napauta tarvittaessa valuutta tai **G/L** ja sitten **Tallenna** (levy). Tyhjät kentät täyttävät **osittaisen täytön** (vielä sallittu).

![Täytä — pumpun esikatselu / kokonaissummat (esikatselussa esimerkkipumpun kuva)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Pysyt Täytä seuraavaa pysäkkiä varten (kentät tyhjennetään tallennuksen jälkeen). Työskentele täysin **offline-tilassa**; synkronointi suoritetaan myöhemmin taustalla, kun se on määritetty.

### <a id="manual-entry-no-camera--bad-ocr"></a>Manuaalinen syöttö (ei kameraa / huono OCR)

1. Napauta **Odo**, **kustannus** tai **tilavuus** ja kirjoita arvot (pysty käyttää järjestelmän näppäimistöä, vaaka käyttää näytön näppäimistöä).
2. Valitse tai vahvista **Ajoneuvo**, jos automaattinen tunnistus ei toiminut.
3. Tallenna kuten yllä.

### <a id="modes-and-borders"></a>Moodit ja reunat

- **Vihreä reunus** ajoneuvon ympärillä+odo → matkamittarin tallentaminen/muokkaus.
- **Vihreä reunus** kustannus+tilavuus → pumpputila.
- **Tallenna** pysyy poissa käytöstä, kunnes ajoneuvo on valittu ja ainakin yhdellä odo/cost/volume -tilasta on tietoja, eikä OCR ole vielä käynnissä.

Näytön vinkki (ohjerivin alapuolella): *Suljin = kaappaa · Levy = tallenna · ↕ = odo/pumpputila · ↔ = vaihtohinta / tilavuus.*

---

## <a id="expenses"></a>Kulut

### <a id="new-expense"></a>Uusi kulu

Menu → **Uusi kulu**.

![Uusi kululomake ja kameran säätimet (esimerkiksi viivakuva kamerakaistalla, kun se on tyhjä)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Tallenna** (levy), **suljin** (kuittikuva) tai **galleria** (valitse kuva).
2. Täytä **Päivämäärä**, **Ajoneuvo**, **Myyjä**, **Kuvaus**, **Summa** (valuuttasymboli napsautettava), **Luokka**, valinnainen **matkamittari**.
3. Monisivuiset kuitit: kaappaa lisäsivuja, jos käyttöliittymä tarjoaa sivun (sivu 0 on ensisijainen kuitti).
4. **Tallenna** tallentaaksesi (paikallinen ensin; valokuvien varmuuskopiointi ja laskentataulukon synkronointi tapahtuvat taustalla, kun se on määritetty).

### <a id="expense-list"></a>Kululuettelo

Valikko → **Raportit** → **Kululuettelo** — selaa aiempia muita kuin polttoainekuluja; avaa muokattava kohde.

![Kululuettelo](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Muokkaa kuluja

Avaa rivi luettelosta. Oikea myyjä, määrä, luokka, ajoneuvo ja kuvaus. Jos kuitti on vain valokuvan varmuuskopiossa (ei luettavissa olevaa paikallista tiedostoa), käytä **Hae kuva arkistosta**, kun se näkyy (toimii kaikissa määritetyissä valokuvakohteissa).

![Muokkaa kuluja](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a> Aloita matka

Valikko → **Aloita matka** (Täytä laatikko -kohdan jälkeen). Tallenna tai syötä matkamittari, valitse matkatyyppi, tallenna **levy**-kuvakkeella. **Stop** on pikakuvake henkilökohtaiseen nyt pidetyssä GPS-sijainnissa. Käytä **ⓘ** ohjausmuistutuksiin.

![Aloita matka](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Matkan aloitukset tallennetaan polttoaineriveinä, joissa on **Trip Type** (ei normaalit täytöt). Ne näkyvät kohdassa **Raportit → Matkamailit**, eivät Polttoainehistoria-kohdassa.

---

## <a id="reports"></a>Raportit

Valikko → **Raportit** avaa tuotekeskuksen (kaikkien aikojen yhteenveto + luettelokortit). Tämä on ainoa tuoteraporttien pinta - ei ole erillistä "Raportit ja kaaviot" -laatikkoa.

![Raporttikeskus](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Avaa kortti ajoneuvotilaa varten (**Kaikki / Jokainen / Yksi**), jaksosuodattimet, kaaviot ja jakaminen (**TEKSTI / CSV / PDF**). Ilmoituslapsien yläpalkki: **☰ + ←** (ja **ⓘ** rekisteröityessä).

### <a id="time-based-reports"></a>Aikaperusteiset raportit

Pääkarttakortti. Valinnaiset mittarit (mpg, tilavuus/etäisyys, kuten G/mi, yksikköhinta, kuten $/G, hinta/etäisyys, kuukausittaiset $, matkamailit, matka-% tyypin mukaan) **Smooth**-astioilla ja **riippumattomilla Y-asteikoilla** (säästö vasen; raha- ja matkaperheet oikealla).

![Aikaperusteiset raportit](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Aikaperusteiset raportit – vierityt tiedot](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Talousmatematiikan tiedot: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Täyttöhistoria vs. polttoainehistoria

- **Raportit → Täyttöhistoria** — raporttisuodattimien kronologiset täytöt (**vain täytteet**; matkaa ei aloiteta).

![Täytä historiaraportti](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Fuel History** (jos se on rakennuksesi navigaatiossa) – ajoneuvokohtainen täyttövarasto, myös vain täyttää; napauta riviä muokataksesi.

### <a id="trip-miles"></a>Matka mailia

**Raportit → Matkamailit** — mailit tyypin mukaan, kaaviot ja kronologinen **matkan aloitus-/segmenttiluettelo**. Napauta oikeaa alkua avataksesi kyseisen rivin **Muokkaa täyttöä**.

![Matkamailia](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Muokkaa täyttöä

Avaa täyttö kohdassa Täyttöhistoria, Polttoainehistoria tai Matkamailit. Asettelu: ajoneuvo ja matkamittari, **valuutta ennen kustannuksia**, tilavuus, huomautukset. Matkatyyppi näkyy vain, kun rivi on matkan aloitus. Sijainnissa on yhteenveto sekä **Sijaintitiedot**. Puuttuu paikallinen kuva pilvi-identiteetillä: **Hae kuva arkistosta**.

![Muokkaa täyttöä — Hae arkistosta, kun paikallinen kuva puuttuu](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Muut kuvastokortit sisältävät kulut luokittain, ajoneuvoyhteenvedon ja kululuettelon.

Money käyttää kunkin rivin valuuttaa, kun se on asetettu. Sekavaluuttasummat näyttävät **valuuttakohtaiset välisummat** (ei hiljaista valuuttamuunnosa).

---

## <a id="syncing"></a> Synkronoidaan

-valikko → **Synkronointi** on laskentataulukko- ja valokuvakohteiden keskus (ei vain haudattu asetuksiin).

![Synkronointikeskus](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Kortit **laskentataulukon synkronointiin** ja **valokuvien varmuuskopiointiin**, joissa on lyhyt tila, **synkronointi** tällaisille tyypeille ja **›** kohdeluetteloon.
- Avaa kohde **Testaa yhteys** ja **Synkronoi nyt (tämä kohde)** / kaikki määritetty.
- Virhe **Tiedot** ja punainen **!** otsikkorivillä laskeutuvat tänne.
– Google Sheetsin ja Driven vaiheittaiset asetukset: [Varmuuskopiot ja usean laitteen synkronointi](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Asetukset (paikalliset asetukset)

Valikko → **Asetukset**.

![Asetukset](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Lisää asetuksia (vieritetty)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Valitse määränpäille **Valikko → Synkronointi**. Asetuksissa voi silti näkyä yhteenvetorivejä, jotka avaavat samat luettelot.

### <a id="local-preferences-common"></a>Paikalliset asetukset (yleinen)

- **Tallenna polttoainekuittivalokuvat** / **Tallenna kulukuvia paikallisesti** — säilytä kuvat laitteella (voi pyytää valokuvien lupaa).
- **Toista suljinääni**
- **Valuutta** / **Välimäärä** – sovelluksen oletusasetukset (järjestelmä tai eksplisiittiset). Tilavuusyksikön vaihtaminen olemassa olevilla polttoainetiedoilla voi tarjota muunnosikkunan.
- **Tumma tila**
– **Asennusvinkkejä** – avaa ajoneuvon ensiajo-/synkronointiohjeet uudelleen.
- **Virheenkorjauksen täyttö** / **Näytä kokeilunäytöt (kehittäjä)** – edistynyt; jättää päivittäiseen käyttöön. Kokeilunäytöksiä ei ole dokumentoitu tässä.

CSV **vienti/tuonti** (ajoneuvojen / kulujen / polttoaineen postinumero) on käytettävissä asetuksista, kun nykyinen kokoonpano tarjoaa sen.

---

## <a id="help--about"></a>Ohje ja Tietoja

![Ohje](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Tietoja](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Ohje** — laitteen pika-aloitus, asennusohjeet, linkki tähän oppaaseen, itseisännän asennushakemisto.
- **Tietoja** — versio, lisenssit, GitHub, tämä käsikirja (mukana offline + online-HTML, kun se julkaistaan).

---

## <a id="related-docs"></a>Aiheeseen liittyvät asiakirjat

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) - lyhyt jokapäiväinen opas
- [self-host/INDEX.md](../reference/self-host/INDEX.md) – itseisännöity valokuva-/taulukkoasetus
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) - yhdistäminen, palautus, kaksoiskappaleet
– [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) – taloudellisten mittareiden tiedot
