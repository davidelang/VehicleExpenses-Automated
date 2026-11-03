# <a id="vehicle-expenses-automated--user-manual"></a>Mga Gastos sa Sasakyan Automated — User Manual

> **I-edit ang pinagmulan (Markdown).** Binubuksan ng mga browser at in-app reader ang **na-render na HTML**:
> - Web: [`docs/user-manual/index.html`](index.html) (regenerate gamit ang `./scripts/render-user-manual.sh`)
> - App: Tulong / Tungkol → buong manual (naka-bundle na HTML + mga screenshot)
>
> Huwag ituro ang mga end user sa mga hilaw na `.md` URL — ang mga browser ay nagpapakita lamang ng plain text.

Camera-first tracking para sa mga fuel fill-up at gastos sa sasakyan, na may opsyonal na multi-device na pag-sync at backup sa ilalim ng **iyong** cloud account.

Ito ang **buong manual** (mga screenshot + bawat hakbang). Sa telepono, ang **Menu → Help** ay isang mas maikling gabay sa pagsisimula.

**Hindi saklaw dito:** Mag-import ng Mga Lumang Larawan, Eksperimento sa Pag-align, at Eksperimento ng Pump (developer / advanced na tool).

---

## <a id="table-of-contents"></a>Talaan ng nilalaman

1. [Ano ang kailangan mo](#what-you-need)
2. [Mga icon sa isang sulyap](#icons-at-a-glance)
3. [Buksan ang menu](#open-the-menu)
4. [Unang beses na pag-setup: Pamahalaan ang Mga Sasakyan](#first-time-setup-manage-vehicles)
5. [Mga backup at multi-device na pag-sync](#backups-and-multi-device-sync)
6. [Punan (gatong)](#fill-up-fuel)
7. [Simulan ang biyahe](#start-trip)
8. [Mga Gastos](#expenses)
9. [Mga Ulat](#reports)
10. [Mga Setting (mga lokal na kagustuhan)](#settings-local-preferences)
11. [Nagsi-sync](#syncing)
12. [Tulong at Tungkol Sa](#help--about)
13. [Mga kaugnay na doc](#related-docs)

---

## <a id="what-you-need"></a>Ano ang kailangan mo

- Android phone o tablet.
- Para sa pinakamahusay na OCR: isang malinaw na view ng iyong **dashboard odometer** at **mga kabuuan ng pump** (o i-type ang mga numero sa pamamagitan ng kamay).
- Opsyonal: mga account **kinokontrol mo** para sa data ng spreadsheet at/o backup ng larawan (tingnan ang [Mga pag-backup at multi-device na pag-sync](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Mga icon sa isang sulyap

Lumilitaw ang mga ito sa mga pangunahing screen. Ang pag-alam sa kanila ay nakakatipid ng maraming pangangaso.

| Saan | Icon / kontrol | Ano ang ginagawa nito |
|-------|----------------|--------------|
| Nangungunang bar | **☰ Menu** (hamburger) | Binubuksan ang navigation drawer |
| Nangungunang bar | **ⓘ** (tulong sa pahina) | Maikling tulong para sa **kasalukuyang** page (sa tabi ng menu kapag available) |
| Nangungunang bar | **`?N`** (dilaw) | Nakabinbing mga tanong sa pagsusuri sa pag-import — nagbubukas ng pagsusuri sa pag-import |
| Nangungunang bar | **!** (pula) | Nabigo kamakailan ang isang spreadsheet o destinasyon ng larawan — buksan ang **Pag-sync** upang ayusin |
| Nangungunang bar | **☰ + ←** | Iulat ang mga bata at ang listahan ng mga gastos ay nagpapakita ng **menu at pabalik** nang magkasama; Ang hub ng mga ulat ay menu lamang |
| Mga setting / pag-edit ng gasolina | **←** | Bumalik (mga setting ng spreadsheet/larawan at pag-edit ng gasolina ay mananatiling nakatutok) |
| Punan | **Puting bilog** (shutter) | Kunin ang odometer o pump display para sa OCR |
| Punan | **Disk / I-save** | I-save ang fill-up (kailangan ng sasakyan at kahit isa sa odo / volume / gastos) |
| Punan | **↕ arrow** (mode switch) | I-toggle ang **odometer mode** vs **pump (cost/volume) mode**. Binibigyang-diin ng berdeng hangganan ang aktibong pangkat ng field |
| Punan | **↔ mga arrow** (sa pagitan ng gastos at dami) | Magpalit ng gastos at volume kung inilagay sila ng OCR sa mga maling field |
| Punan | **Mag-zoom 1x / …** | Mga ratio ng zoom ng camera kapag sinusuportahan sila ng lens |
| Punan (pagkatapos makuha) | **I-refresh** sa pangunahing button | Itapon ang preview at bumalik sa live na camera |
| Punan (habang pinoproseso) | **X** sa pangunahing button | Kanselahin ang kasalukuyang pagkuha/OCR |
| Gastos | **I-save** | I-save ang gastos |
| Gastos | **Shutter circle** | Kumuha ng larawan ng resibo |
| Gastos | **Gallery** | Pumili ng larawan ng resibo mula sa library |
| Gastos | **Kunin muli** | I-clear ang kasalukuyang larawan ng resibo at kunan muli |
| Gastos / Pamahalaan ang Mga Sasakyan | **+ / −** FABs | I-zoom ang preview ng larawan |
| Dialog ng mga Landmark | **I-edit ang OCR** ​​| Itama o magdagdag ng landmark text na hindi nakuha ng mga makina |
| Spreadsheet / Mga form ng larawan | **🔍 Maghanap** | Mag-browse sa Google Drive para sa isang sheet o folder (pagkatapos mag-sign-in) |

Maaaring i-tap ang mga simbolo ng currency sa mga field ng gastos at **G/L** sa mga field ng volume: magbukas ng maliit na menu upang baguhin ang currency o gallons vs liters para sa entry na iyon.

---

## <a id="open-the-menu"></a>Buksan ang menu

1. I-tap ang **☰** sa kaliwang bahagi sa itaas.
2. Pumili ng pahina.

![Navigation drawer](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Pangunahing drawer:** Punan · Simulan ang biyahe · Pamahalaan ang Mga Sasakyan · Bagong gastos · **Mga Ulat** · Mga Setting · Pag-sync · Tulong · Tungkol sa.

**Experiment drawer** (Mga Setting → Ipakita ang mga screen ng eksperimento): Alignment Experiment · Pump Experiment · **Import Old Pictures**.

**Sa pamamagitan ng Reports hub (hindi pangunahing drawer):** Listahan ng mga gastos · Punan ang kasaysayan.

---

## <a id="first-time-setup-manage-vehicles"></a>Unang beses na pag-setup: Pamahalaan ang Mga Sasakyan

Mas maayos gumana ang OCR at **awtomatikong pagtutugma ng sasakyan** pagkatapos mong irehistro ang bawat sasakyan gamit ang **larawan ng dashboard bilang sanggunian**, patakbuhin ang **Discovery**, itakda ang odometer crop mula sa paddle seed, at i-save ang mga landmark para sa dashboard na iyon.

### <a id="open-manage-vehicles"></a>Buksan Pamahalaan ang Mga Sasakyan

Menu → **Pamahalaan ang Mga Sasakyan**. Pumili ng sasakyan (o **Magdagdag ng Bagong Sasakyan**).

![Pamahalaan ang Mga Sasakyan gamit ang reference na gitling, mga pananim, at pagtuklas](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Magdagdag o mag-edit ng sasakyan

1. Buksan ang dropdown na **Vehicle** → pumili ng sasakyan o **Add New Vehicle**.
2. Kumuha o pumili ng malinaw na **reference dash photo** (full instrument cluster, well lit, phone roughly square-on). Gamitin ang **Kumuha ng Larawan** o **Gallery**.
3. I-tap ang **Run Discovery** — ang paddle detection ay naglalagay ng dilaw na **seeds** sa larawan (ang mga landmark ay gumagamit ng parehong `"ML Kit"` JSON gaya ng dati).
4. I-tap ang **Select Odo**, i-tap ang seed sa mga digit, ayusin ang mga handle, pagkatapos ay **I-save**.
5. Ang **Edit Odo** ay inaayos ang isang kahon na naroon na. Inaalis ito ng **Clear Odo**, at pagkatapos ay babasahin ng button ang **Select Odo**.
6. Suriin gamit ang **Show Landmarks**. Gamitin ang **Edit OCR** ​​para ayusin ang mga maling pagbasa o **magdagdag** ng text na hindi nakuha.
7. Punan ang **Pangalan ng Sasakyan** (kinakailangan), kasama ang make/modelo/taon/plate ayon sa gusto mo.
8. I-tap ang **Gumawa ng Sasakyan** o **I-save ang Mga Pagbabago** (nangangailangan ng pangalan + reference na larawan para sa isang bagong sasakyan).

![Magdagdag ng mga bagong field ng form ng sasakyan](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Mga Landmark: ayusin ang hindi nakuha ng Discovery

Pagkatapos **Show Landmarks**, i-scroll ang listahan at iwasto ang mga value. Minsan nakakaligtaan ng mga makina ang maliliit na digit (halimbawa, isang orasan **60** sa kanang ibaba ng cluster). Gamitin ang **Edit OCR** ​​upang idagdag o ayusin ang mga ito para manatiling maaasahan ang pagkakakilanlan ng sasakyan.

![Dialog ng mga Landmark — suriin at i-edit ang OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Pagta-type nang walang perpektong larawan

Magagamit mo pa rin ang app sa pamamagitan ng pagpili ng sasakyan at **pagta-type** odometer, volume, at gastos sa Fill — Opsyonal ang OCR para sa bawat field. Gumagana ang pag-import ng gallery para sa reference na dash na larawan kapag mas gusto mong huwag mag-shoot ng in-app.

**Tip:** Pagkatapos ng pag-sync ng spreadsheet, ang mga kahulugan ng sasakyan (mga pananim, landmark) ay nakatira sa lokal na database — hindi mo na kailangang muling buksan ang Manage Vehicles for Fill para magamit ang mga ito.

---

## <a id="backups-and-multi-device-sync"></a>Mga backup at multi-device na pag-sync

Binuo ang app para **maaring magbahagi ng parehong fleet data ang ilang mga telepono o tablet**, at para makapagtago ka ng **kopya ng iyong data at mga larawan mula sa device**. Ginagawa iyon sa mga patutunguhan **iyong** iko-configure sa ilalim ng **iyong** mga account o **iyong** mga server na naka-host sa sarili — hindi isang "cloud ng Mga Gastos sa Sasakyan" na pinapatakbo ng kumpanya na makikita ng ibang tao.

### <a id="what-runs-where"></a>Ano ang tumatakbo kung saan

| Mabait | Ano ang iniimbak nito | Karaniwang paggamit |
|-------|----------------|------------|
| **Spreadsheet / tabular sync** | Mga sasakyan, pagpuno ng gasolina, gastos (mga hilera at tab) | Multi-device merge + structured backup |
| **Pag-backup ng larawan** | Binary na mga larawan (dash/pump/resibo/reference na larawan) | Backup ng larawan + ibalik ang mga nawawalang file |

Maaari mong i-configure ang **maraming destinasyon** ng bawat uri (soft cap bawat uri). Manual **Sync now** at **background** na mga manggagawa ang nagpapatakbo ng mga naka-enable.

### <a id="offline-first"></a>Offline muna

- **Walang network ang kinakailangan** upang magdagdag ng fill-up, gastos, o resibo. Lahat ay nai-save **lokal muna**.
- Kapag available na ang network, tumakbo ang pag-sync at pag-backup ng larawan bilang **mga gawain sa background** (sa iskedyul na itinakda mo, at kapag na-tap mo ang **I-sync ngayon**). Lumalabas ang mga pagkabigo bilang pulang text sa ilalim ng mga row ng Mga Setting at isang **!** sa title bar ng app.

### <a id="your-accounts-only"></a>Ang iyong mga account lang

Ang pag-sign in at mga token ay mananatili sa device para sa mga provider na pipiliin mo (Google, Microsoft, S3 keys, self-hosted URL, at iba pa). Ang mga destinasyon ay nasa ilalim ng **ganap na kontrol ng user** — ang iyong Google account, ang iyong OneDrive, ang iyong MiniIO bucket, ang iyong EtherCalc host, atbp. Walang ibabahagi sa iba pang mga user ng Vehicle Expenses sa pamamagitan ng shared backend.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Mga sinusuportahang target — data (spreadsheet / tabular)

Na-configure sa ilalim ng **Menu → Pag-sync → Pag-sync ng spreadsheet** (maaabot din mula sa mga row ng buod ng Mga Setting). Mga opsyon sa first-class picker:

| Target | Mga Tala |
|--------|--------|
| **Google Sheets** | Karaniwang default; mga tab para sa Mga Sasakyan, Gastos, at bawat-sasakyan na gasolina |
| **Excel** | Microsoft workbook sa pamamagitan ng Graph / OneDrive-style binding |
| **EtherCalc** | Self-host na collaborative na mga spreadsheet na kwarto |
| **Iba pa →** ipinatupad na mga backend | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Ipinagpaliban / hindi pa walang ulo (nakalista sa ilalim ng Iba ngunit hindi ganap na ipinatupad): OnlyOffice, Collabora. Tingnan din ang [self-host index](reference/self-host/INDEX.md).

Ang CSV **export/import** (ZIP ng parehong layout ng tab) ay available mula sa Settings bilang portable na backup, na independyente sa live sync.

### <a id="supported-targets--photos-image-backup"></a>Mga sinusuportahang target — mga larawan (backup ng imahe)

Na-configure sa ilalim ng **Menu → Pag-sync → Backup ng larawan** (mula rin sa mga row ng buod ng Mga Setting):

| Target | Mga Tala |
|--------|--------|
| **Google Drive** | Folder na pipiliin mo (mag-browse o mag-paste ng URL) |
| **OneDrive** | Microsoft account + path prefix |
| **S3** | AWS, Wasabi, Cloudflare R2, MiniIO, at iba pang mga endpoint na katugma sa S3 |
| **Iba pa** | rclone-backed storage (hal. WebDAV, SFTP, at iba pang na-curate na remote na available sa in-app picker) |

I-setup ang mga cheatsheet para sa self-host na larawan at mga target na tabular: [self-host index](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Multi-device na gawi (maikli)

- Ang mga row ay pinagsama sa pamamagitan ng **Sync ID** na may **last-write-wins** sa **Updated** timestamp.
- Malambot ang pagtanggal; ang isang mas bagong pag-edit sa isa pang device ay maaaring mag-restore ng isang row.
- Ang pagpasok ng **parehong fill nang dalawang beses** sa dalawang device ay lilikha ng **dalawang row** — tanggalin ang dagdag kapag napansin mo.
- Higit pang detalye: [Mga tala ng gawi sa pag-sync](#sync-behavior-notes) at [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Halimbawa: magdagdag ng Google Sheets (data)

1. **Menu → Pag-sync → Pag-sync ng spreadsheet** (o Mga Setting → Pag-sync ng Spreadsheet).

![Listahan ng pag-sync ng spreadsheet](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. I-tap ang **Magdagdag ng patutunguhan ng spreadsheet**.

![Tagapili ng provider](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Piliin ang **Google Sheets**.

![Google Sheets form](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Mag-sign in gamit ang Google** nang isang beses — hinahayaan ka rin ng pag-sign-in na iyon na mag-browse. Display name → mag-paste ng **Sheet URL** (valid pa rin) o **🔍** browse. Magbubukas ang pag-browse sa **Lahat at ibinahagi** para makapili ka ng umiiral nang spreadsheet na pagmamay-ari mo o ibinahagi sa iyo. Pagkatapos ay mag-iskedyul ng mga opsyon → paganahin → i-save.
5. **I-sync ngayon** nang isang beses para gumawa/mag-update ng mga tab: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Halimbawa: magdagdag ng Google Drive (mga larawan)

1. **Menu → Pag-sync → Backup ng larawan** (o Mga Setting → Backup ng larawan).

![Listahan ng backup ng larawan](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. I-tap ang **Magdagdag ng destinasyon ng larawan**.

![Photo provider picker](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Piliin ang **Google Drive**.

![Google Drive form](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Mag-sign in gamit ang Google (Drive)** → opsyonal na URL ng folder/browse → paganahin → i-save → **I-sync ngayon**.

Manual **Sync now** para sa mga larawan ay isang full pass; ang background backup ay karaniwang nagpoproseso ng **nakabinbin-lamang** na mga pag-upload sa isang iskedyul.

### <a id="sync-behavior-notes"></a>I-sync ang mga tala ng gawi

- Pagkatapos ng pag-upgrade ng app maaari mong panandaliang makita ang **"Pag-update ng database pagkatapos ng pag-upgrade..."** (local sync-id backfill).
- Kung ang isang pag-sync ay naantala, ang susunod na **matagumpay** na pag-sync ay muling magsasama at aayusin ang mga malalayong tab.
- Mga Pagkabigo: pulang buod sa Pag-sync ng mga card + **!** sa app bar.

---

## <a id="fill-up-fuel"></a>Punan (gasolina)

Ito ang **home screen** kapag binuksan mo ang app.

### <a id="vehicle-selection-usually-automatic"></a>Pagpili ng sasakyan (karaniwan ay awtomatiko)

Hindi mo ** kailangang pumili muna ng sasakyan. Kapag may naka-set up na **landmark** ang mga sasakyan sa Manage Vehicles, Punan ang **auto-detect kung aling sasakyan** mula sa dash image pagkatapos mong makuha ang odometer. Maaari mo pa ring buksan ang dropdown na **Vehicle** upang i-override kung kinakailangan.

### <a id="aim-at-the-odometer"></a>Layunin ang odometer

Manatili sa odometer mode at i-frame ang cluster. Panuto: *Layunin ang odometer. I-tap ang shutter para makuha.*

![Fill — live na camera sa odometer (halimbawang dash image sa preview)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Pagkatapos ng shutter ng odometer

Pinupuno ng OCR ang **Odo** at sinusubukang itugma ang sasakyan mula sa mga palatandaan (suriin ang parehong kung kinakailangan). Ang pangunahing button ay nagiging **Retry** para muling kunan. Ibinubuod ng instruksyon ang pagbabasa.

![Punan — resulta ng OCR ng odometer](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Pump mode (gastos at volume)

1. I-tap ang **↕** para lumipat sa pump mode: *Layunin ang pump display (gastos/volume). I-tap ang shutter.*
2. Kunin ang kabuuang pump. Punan ang mga patlang ng gastos at dami; gamitin ang **↔** kung sila ay ipinagpalit.
3. I-tap ang currency o **G/L** kung kinakailangan, pagkatapos ay **I-save** (disk). Ang mga walang laman na field ay gumagawa ng **partial fill** (pinapayagan pa rin).

![Fill — pump preview / mga kabuuan (halimbawa ng pump image sa preview)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Mananatili ka sa Fill para sa susunod na hintuan (malinaw ang mga field pagkatapos i-save). Magtrabaho nang buo **offline**; ang pag-sync ay tumatakbo sa ibang pagkakataon sa background kapag na-configure.

### <a id="manual-entry-no-camera--bad-ocr"></a>Manual na pagpasok (walang camera / masamang OCR)

1. I-tap ang **Odo**, **cost**, o **volume** at mga value ng uri (ginagamit ng portrait ang keyboard ng system; gumagamit ang landscape ng on-screen na keypad).
2. Piliin o kumpirmahin ang **Vehicle** kung hindi tumakbo ang auto-detect.
3. I-save tulad ng nasa itaas.

### <a id="modes-and-borders"></a>Mga mode at hangganan

- **Berdeng hangganan** sa paligid ng sasakyan+odo → pagkuha/pag-edit ng odometer.
- **Green border** sa paligid ng cost+volume → pump mode.
- Mananatiling naka-disable ang **Save** hanggang sa mapili ang isang sasakyan at kahit isa sa odo/cost/volume ay may data, at hindi pa rin tumatakbo ang OCR.

On-screen tip (sa ibaba ng linya ng pagtuturo): *Shutter = capture · Disk = save · ↕ = odo/pump mode · ↔ = swap cost/volume.*

---

## <a id="expenses"></a>Mga gastos

### <a id="new-expense"></a>Bagong gastos

Menu → **Bagong gastos**.

![Bagong form ng gastos at mga kontrol sa camera (halimbawang dash image sa camera band kapag walang laman)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **I-save** (disk), **shutter** (larawan ng resibo), o **gallery** (pumili ng larawan).
2. Punan ang **Petsa**, **Sasakyan**, **Vendor**, **Paglalarawan**, **Halaga** (maaaring i-tap ang simbolo ng pera), **Kategorya**, opsyonal **Odometer**.
3. Mga multi-page na resibo: kumuha ng mga karagdagang pahina kung nag-aalok ang UI ng paging (pahina 0 ang pangunahing resibo).
4. **I-save** upang mag-imbak (lokal muna; ang backup ng larawan at pag-sync ng spreadsheet ay nangyayari sa background kapag na-configure).

### <a id="expense-list"></a>Listahan ng gastos

Menu → **Mga Ulat** → **Listahan ng mga gastos** — i-browse ang mga nakaraang gastos na hindi panggatong; buksan ang isang item upang i-edit.

![Listahan ng gastos](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>I-edit ang gastos

Magbukas ng row mula sa listahan. Tamang vendor, halaga, kategorya, sasakyan, at paglalarawan. Kung ang resibo ay nasa backup lang ng larawan (walang nababasang lokal na file), gamitin ang **Kunin ang larawan mula sa archive** kapag ipinakita (gumagana sa mga naka-configure na destinasyon ng larawan).

![I-edit ang gastos](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Simulan ang biyahe

Menu → **Simulan ang biyahe** (pagkatapos Punan ang drawer). Kunin o ilagay ang odometer, piliin ang uri ng biyahe, i-save gamit ang **disk** icon. Ang **Stop** ay isang shortcut para sa Personal ngayon sa hawak na lokasyon ng GPS. Gamitin ang **ⓘ** para sa mga paalala sa pagkontrol.

![Simulan ang biyahe](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Ang mga pagsisimula ng biyahe ay iniimbak bilang mga hilera ng gasolina na may **Uri ng Biyahe** (hindi normal na pagpuno). Lumalabas ang mga ito sa ilalim ng **Mga Ulat → Mga milya ng biyahe**, hindi sa ilalim ng Kasaysayan ng Fuel.

---

## <a id="reports"></a>Mga Ulat

Menu → **Reports** binubuksan ang product hub (buong-panahon na buod + mga card ng katalogo). Ito lang ang lugar ng mga ulat ng produkto — walang hiwalay na item sa drawer na “Reports & Charts”.

![Reports hub](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Magbukas ng card para sa vehicle mode (**All / Each / Single**), period filters, chart, at share (**TEXT / CSV / PDF**). Nangungunang bar sa ulat ng mga bata: **☰ + ←** (at **ⓘ** kapag nakarehistro).

### <a id="time-based-reports"></a>Mga ulat batay sa oras

Ang pangunahing chart card. Mga opsyonal na sukatan (mpg, volume/distansya gaya ng G/mi, presyo ng unit gaya ng $/G, gastos/distansya, buwanang $, trip miles, trip % ayon sa uri) na may **Smooth** bins at **independent Y scales** (ekonomiya sa kaliwa; pera at mga pamilya ng biyahe sa kanan).

![Mga ulat batay sa oras](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Mga ulat batay sa oras — mga naka-scroll na sukatan](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Mga detalye ng economic math: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Fill history vs Fuel History

- **Mga Ulat → Kasaysayan ng punan** — mga kronolohikal na pagpuno para sa mga filter ng ulat (**mga punan lamang**; walang magsisimulang biyahe).

![Punan ang ulat sa kasaysayan](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Kasaysayan ng Panggatong** (kung naroroon sa nabigasyon ng iyong build) — imbentaryo ng bawat punan ng sasakyan, napupuno lang din; i-tap ang isang row para i-edit.

### <a id="trip-miles"></a>Mga milya ng biyahe

**Mga Ulat → Mga milya ng biyahe** — milya ayon sa uri, mga chart, at isang kronolohikal na **pagsisimula ng paglalakbay / listahan ng segment**. I-tap ang totoong simula para buksan ang **Edit fill** para sa row na iyon.

![Trip miles](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>I-edit ang punan

Mula sa Fill history, Fuel History, o Trip miles, magbukas ng fill. Layout: sasakyan at odometer, **currency bago ang gastos**, volume, mga tala. Lalabas lang ang uri ng biyahe kapag ang row ay simula ng biyahe. Ang lokasyon ay may buod at **Mga detalye ng lokasyon**. Nawawalang lokal na larawan na may cloud identity: **Kunin ang larawan mula sa archive**.

![I-edit ang fill — Kunin mula sa archive kapag nawawala ang lokal na larawan](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Kasama sa iba pang mga card ng katalogo ang mga gastos ayon sa kategorya, buod ng sasakyan, at listahan ng mga gastos.

Ginagamit ng pera ang currency ng bawat row kapag nakatakda. Ang mga kabuuan ng pinaghalong currency ay nagpapakita ng **mga subtotal sa bawat currency** (walang tahimik na conversion ng FX).

---

## <a id="syncing"></a>Si-sync

Menu → **Pag-sync** ay ang hub para sa mga patutunguhan ng spreadsheet at larawan (hindi lamang nakabaon sa ilalim ng Mga Setting).

![Pag-sync ng hub](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Mga card para sa **Spreadsheet sync** at **Photo backup** na may maikling status, **Sync** para sa ganoong uri, at **›** sa listahan ng patutunguhan.
- Magbukas ng patutunguhan para sa **Subukan ang koneksyon** at **I-sync ngayon (ang destinasyong ito)** / lahat ay na-configure.
- Pagkabigo **Mga Detalye** at ang pulang **!** sa title bar ay dumapo dito.
- Hakbang-hakbang na pag-setup ng Google Sheets at Drive: [Mga pag-backup at pag-sync ng maraming device](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Mga Setting (mga lokal na kagustuhan)

Menu → **Mga Setting**.

![Mga Setting](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Higit pang mga setting (naka-scroll)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Para sa mga destinasyon, mas gusto ang **Menu → Pag-sync**. Maaaring magpakita pa rin ang mga setting ng mga row ng buod na nagbubukas ng parehong mga listahan.

### <a id="local-preferences-common"></a>Mga lokal na kagustuhan (karaniwan)

- **I-save ang Mga Larawan ng Resibo ng Gasolina** / **I-save ang Mga Larawan ng Gastos nang Lokal** — panatilihin ang mga larawan sa device (maaaring humiling ng pahintulot sa Mga Larawan).
- **I-play ang Shutter Sound**
- **Currency** / **Volume unit** — mga default ng app (system o tahasan). Ang pagpapalit ng unit ng volume gamit ang kasalukuyang data ng gasolina ay maaaring mag-alok ng convert dialog.
- **Dark mode**
- **Mga tip sa pag-setup** — muling buksan ang mga tutorial sa unang pagtakbo ng sasakyan / pag-sync.
- **Debug Fill** / **Ipakita ang mga screen ng eksperimento (dev)** — advanced; iwanan para sa pang-araw-araw na paggamit. Hindi nakadokumento dito ang mga screen ng eksperimento.

CSV **export/import** (ZIP of Vehicles / Expenses / Fuel tabs) ay available mula sa Settings kapag inaalok ng kasalukuyang build.

---

## <a id="help--about"></a>Tulong at Tungkol sa

![Tulong](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Tungkol sa](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Tulong** — mabilis na pagsisimula sa device, mga tutorial sa pag-setup, link sa manual na ito, index ng pag-setup ng self-host.
- **Tungkol sa** — bersyon, mga lisensya, GitHub, ang manwal na ito (naka-bundle offline + online HTML kapag na-publish).

---

## <a id="related-docs"></a>Mga kaugnay na doc

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — maikling gabay sa araw-araw
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — self-host na larawan/tabular setup
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — pagsamahin, pagbawi, mga duplicate
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — detalye ng sukatan ng ekonomiya
