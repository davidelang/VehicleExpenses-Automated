# <a id="vehicle-expenses-automated--user-manual"></a>Gharama za Gari Zinazojiendesha — Mwongozo wa Mtumiaji

> **Hariri chanzo (Alama chini).** Vivinjari na kisoma ndani ya programu hufungua **HTML iliyotolewa**:
> - Wavuti: [`docs/user-manual/index.html`](index.html) (jitengenezee kwa `./scripts/render-user-manual.sh`)
> - Programu: Usaidizi / Kuhusu → mwongozo kamili (vifurushi vya HTML + picha za skrini)
>
> Usiwaelekeze watumiaji wa mwisho kwenye URL ghafi `.md` — vivinjari huonyesha maandishi wazi pekee.

Ufuatiliaji wa kwanza wa kamera kwa ujazo wa mafuta na gharama za gari, kwa kusawazisha kwa hiari vifaa vingi na kuhifadhi chini ya **akaunti zako** za wingu.

Huu ndio **mwongozo kamili** (picha za skrini + kila hatua). Kwenye simu, **Menyu → Usaidizi** ni mwongozo mfupi zaidi wa kuanza.

**Haijaangaziwa hapa:** Ingiza Picha za Zamani, Majaribio ya Kupanga Mipangilio na Majaribio ya Pampu (wasanidi programu / zana za kina).

---

## <a id="table-of-contents"></a>Jedwali la yaliyomo

1. [Unachohitaji](#what-you-need)
2. [Aikoni kwa muhtasari](#icons-at-a-glance)
3. [Fungua menyu](#open-the-menu)
4. [Usanidi wa mara ya kwanza: Dhibiti Magari](#first-time-setup-manage-vehicles)
5. [Hifadhi na usawazishaji wa vifaa vingi](#backups-and-multi-device-sync)
6. [Jaza (mafuta)](#fill-up-fuel)
7. [Anza safari](#start-trip)
8. [Gharama](#expenses)
9. [Ripoti](#reports)
10. [Mipangilio (mapendeleo ya karibu)](#settings-local-preferences)
11. [Kusawazisha](#syncing)
12. [Msaada na Kuhusu](#help--about)
13. [Hati zinazohusiana](#related-docs)

---

## <a id="what-you-need"></a>Unachohitaji

- Simu au kompyuta kibao ya Android.
- Kwa OCR bora zaidi: mwonekano wazi wa **dashibodi odometer** yako na **jumla za pampu** (au charaza nambari kwa mkono).
- Hiari: akaunti **unayodhibiti** kwa data ya lahajedwali na/au kuhifadhi nakala za picha (angalia [Hifadhi na usawazishaji wa vifaa vingi](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Aikoni kwa muhtasari

Hizi huonekana kwenye skrini kuu. Kuwajua kunaokoa uwindaji mwingi.

| Wapi | Aikoni / udhibiti | Inafanya nini |
|-------|----------------|--------------|
| Upau wa juu | **☰ Menyu** (hamburger) | Hufungua droo ya kusogeza |
| Upau wa juu | **ⓘ** (msaada wa ukurasa) | Usaidizi mfupi wa ukurasa wa **sasa** (karibu na menyu inapopatikana) |
| Upau wa juu | **`?N`** (njano) | Maswali ya ukaguzi wa uagizaji yanayosubiri - hufungua Leta ukaguzi |
| Upau wa juu | **!** (nyekundu) | Lahajedwali au lengwa la picha limeshindwa hivi majuzi — fungua **Kusawazisha** ili kurekebisha |
| Upau wa juu | **☰ + ←** | Ripoti orodha ya watoto na Gharama onyesha **menyu na urejeshe** pamoja; Kitovu cha ripoti ni menyu pekee |
| Mipangilio / hariri ya mafuta | **←** | Nyuma (mipangilio lahajedwali/picha na uhariri wa mafuta ubaki ukiwa umezingatia) |
| Jaza | **Mduara mweupe** (kifunga) | Nasa odometer au onyesho la pampu la OCR |
| Jaza | **Diski / Hifadhi** | Hifadhi kujaza (inahitaji gari na angalau moja ya odo / kiasi / gharama) |
| Jaza | **↕ mishale** (kubadili hali) | Geuza modi ya **odometer** dhidi ya **pampu (gharama/kiasi)**. Mpaka wa kijani huangazia kikundi kinachotumika cha uga |
| Jaza | **↔ mishale** (kati ya gharama na sauti) | Badilisha gharama na sauti ikiwa OCR itaziweka katika sehemu zisizo sahihi |
| Jaza | **Kuza 1x / …** | Uwiano wa kukuza kamera wakati lenzi inawasaidia |
| Jaza (baada ya kukamata) | **Onyesha upya** kwenye kitufe kikuu | Tupa onyesho la kukagua na urudi kwenye kamera ya moja kwa moja |
| Jaza (wakati unachakata) | **X** kwenye kitufe kikuu | Ghairi kunasa inayoendelea/OCR |
| Gharama | **Hifadhi** | Okoa gharama |
| Gharama | **Mduara wa shutter** | Piga picha ya risiti |
| Gharama | **Nyumba ya sanaa** | Chagua picha ya risiti kutoka kwa maktaba |
| Gharama | **Chukua tena** | Futa picha ya sasa ya risiti na upige tena |
| Gharama / Dhibiti Magari | **+ / −** FABs | Kuza onyesho la kukagua picha |
| Kidirisha cha alama kuu | **Hariri OCR** ​​| Sahihisha au ongeza maandishi muhimu ambayo injini zilikosa |
| Lahajedwali / Fomu za Picha | **🔍 Tafuta** | Vinjari Hifadhi ya Google kwa laha au folda (baada ya kuingia) |

Alama za sarafu kwenye sehemu za gharama na **G/L** kwenye sehemu za sauti zinaweza kuguswa: fungua menyu ndogo ili kubadilisha fedha au galoni dhidi ya lita kwa ingizo hilo.

---

## <a id="open-the-menu"></a>Fungua menyu

1. Gusa **☰** juu kushoto.
2. Chagua ukurasa.

![Droo ya kusogeza](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Droo kuu:** Jaza · Safari ya kuanza · Dhibiti Magari · Gharama mpya · **Ripoti** · Mipangilio · Kusawazisha · Msaada · Kuhusu.

**Droo ya majaribio** (Mipangilio → Onyesha skrini za majaribio): Majaribio ya Kupangilia · Jaribio la Pampu · **Leta Picha za Zamani**.

**Kupitia kitovu cha Ripoti (sio droo kuu):** Orodha ya gharama · Jaza historia.

---

## <a id="first-time-setup-manage-vehicles"></a>Usanidi wa mara ya kwanza: Dhibiti Magari

OCR na **ulinganishaji wa otomatiki wa gari** hufanya kazi vizuri zaidi baada ya kusajili kila gari kwa **picha ya dashibodi ya marejeleo**, endesha **Ugunduzi**, weka mazao ya odometer kutoka kwa mbegu ya paddle, na uhifadhi alama muhimu za dashibodi hiyo.

### <a id="open-manage-vehicles"></a>Fungua Dhibiti Magari

Menyu → **Dhibiti Magari**. Chagua gari (au **Ongeza Gari Jipya**).

![Dhibiti Magari kwa kutumia dashi ya marejeleo, mazao na uvumbuzi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Ongeza au uhariri gari

1. Fungua menyu kunjuzi ya **Gari** → chagua gari au **Ongeza Gari Jipya**.
2. Nasa au chagua picha ya*** ya dashi ya marejeleo iliyo wazi (sehemu ya ala kamili, yenye mwanga wa kutosha, simu takribani mraba imewashwa). Tumia **Piga Picha** au **Nyumba ya sanaa**.
3. Gusa **Endesha Ugunduzi** — utambuzi wa kasia huweka manjano **mbegu** kwenye picha (alama hutumia `"ML Kit"` JSON sawa na hapo awali).
4. Gusa **Chagua Odo**, gusa mbegu kwenye tarakimu, rekebisha vishikizo, kisha **Hifadhi**.
5. **Hariri Odo** hurekebisha kisanduku ambacho tayari kipo. **Futa Odo** huiondoa, na kitufe kisha kusoma **Chagua Odo**.
6. Kagua ukitumia **Onyesha Alama**. Tumia **Hariri OCR** ​​ili kurekebisha kusomwa vibaya au **kuongeza** maandishi ambayo yalikosa.
7. Jaza **Jina la Gari** (inahitajika), pamoja na kutengeneza/modeli/mwaka/sahani upendavyo.
8. Gusa **Unda Gari** au **Hifadhi Mabadiliko** (inahitaji jina + picha ya marejeleo kwa gari jipya).

![Ongeza sehemu mpya za fomu za gari](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Alama: rekebisha kile ambacho Discovery ilikosa

Baada ya **Onyesha Alama**, sogeza orodha na uweke thamani sahihi. Injini wakati mwingine hukosa tarakimu ndogo (kwa mfano saa **60** upande wa chini kulia wa nguzo). Tumia **Hariri OCR** ​​kuziongeza au kuzirekebisha ili utambulisho wa gari uendelee kuaminika.

![Kidirisha cha alama alama — kagua na uhariri OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Kuandika bila picha kamili

Bado unaweza kutumia programu kwa kuchagua gari na **kuandika** odometer, sauti na gharama kwenye Jaza — OCR ni ya hiari kwa kila sehemu. Uingizaji wa ghala hufanya kazi kwa picha ya dashi ya marejeleo unapopendelea kutopiga ndani ya programu.

**Kidokezo:** Baada ya kusawazisha lahajedwali, ufafanuzi wa gari (mimea, alama muhimu) huishi katika hifadhidata ya karibu nawe — huhitaji kufungua tena Dhibiti Magari kwa ajili ya Kujaza ili kuzitumia.

---

## <a id="backups-and-multi-device-sync"></a>Hifadhi nakala na usawazishaji wa vifaa vingi

Programu imeundwa ili **simu au kompyuta kibao kadhaa ziweze kushiriki data sawa ya meli**, na ili uweze kuhifadhi **nakala ya data na picha zako nje ya kifaa**. Hilo linafanywa na maeneo yanayolengwa **wewe** kusanidi chini ya **akaunti** zako au **seva zako** zinazopangishwa binafsi - si "wingu la Gharama za Magari" linaloendeshwa na kampuni ambalo watu wengine wanaweza kuona.

### <a id="what-runs-where"></a>Ni nini kinaenda wapi

| Aina | Inachohifadhi | Matumizi ya kawaida |
|------|----------------|-------------|
| **Lahajedwali / usawazishaji wa jedwali** | Magari, kujaza mafuta, gharama (safu na tabo) | Unganisha vifaa vingi + nakala rudufu iliyopangwa |
| **Chelezo ya picha** | Picha za binary (dashi/pampu/risiti/picha za marejeleo) | Hifadhi nakala ya picha + kurejesha faili ambazo hazipo |

Unaweza kusanidi **maeneo mengi** ya kila aina (kifuniko laini kwa kila aina). Mwongozo **Sawazisha sasa** na **chinichini** wafanyikazi huendesha zile zilizowezeshwa.

### <a id="offline-first"></a>Nje ya mtandao kwanza

- **Hakuna mtandao unaohitajika ** ili kuongeza kujaza, gharama, au risiti. Kila kitu kimehifadhiwa **ndani kwanza**.
- Wakati mtandao unapatikana, kusawazisha na kuhifadhi nakala rudufu ya picha endesha kama **kazi za usuli** (kwenye ratiba uliyoweka, na unapogonga **Sawazisha sasa**). Hitilafu huonekana kama maandishi mekundu chini ya safu mlalo za Mipangilio na **!** katika upau wa kichwa cha programu.

### <a id="your-accounts-only"></a>Akaunti zako pekee

Ingia na ishara hubaki kwenye kifaa kwa watoa huduma unaowachagua (Google, Microsoft, funguo za S3, URL zinazopangishwa binafsi na kadhalika). Marudio yako chini ya **udhibiti kamili wa mtumiaji** - akaunti yako ya Google, OneDrive yako, ndoo yako ya MiniIO, mwenyeji wako wa EtherCalc, n.k. Hakuna chochote kinachoshirikiwa na watumiaji wengine wa Gharama za Magari kupitia mazingira ya nyuma yaliyoshirikiwa.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Malengo yanayotumika — data (lahajedwali / jedwali)

Imesanidiwa chini ya **Menyu → Kusawazisha → Usawazishaji wa Lahajedwali** (pia inaweza kufikiwa kutoka kwa safu mlalo za muhtasari wa Mipangilio). Chaguo za kiteua cha daraja la kwanza:

| Lengo | Vidokezo |
|--------|--------|
| **Majedwali ya Google** | Kawaida chaguo-msingi; vichupo vya Magari, Gharama, na mafuta kwa kila gari |
| **Excel** | Kitabu cha kazi cha Microsoft kupitia kuunganisha kwa Grafu / OneDrive |
| **EtherCalc** | Vyumba vya lahajedwali shirikishi vinavyojipangisha wenyewe |
| **Nyingine →** nakala za nyuma zilizotekelezwa | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Imeahirishwa / haina kichwa bado (imeorodheshwa chini ya Nyingine lakini haijatekelezwa kikamilifu): OnlyOffice, Collabora. Tazama pia [index ya mwenyeji binafsi](reference/self-host/INDEX.md).

CSV **hamisha/leta** (ZIP ya mpangilio sawa wa kichupo) inapatikana kutoka kwa Mipangilio kama hifadhi rudufu inayobebeka, isiyotegemea usawazishaji wa moja kwa moja.

### <a id="supported-targets--photos-image-backup"></a>Malengo yanayotumika — picha (hifadhi nakala ya picha)

Imesanidiwa chini ya **Menyu → Kusawazisha → Hifadhi nakala ya picha** (pia kutoka safu mlalo za muhtasari wa Mipangilio):

| Lengo | Vidokezo |
|--------|--------|
| **Hifadhi ya Google** | Folda unayochagua (vinjari au ubandike URL) |
| **OneDrive** | Akaunti ya Microsoft + kiambishi awali cha njia |
| **S3** | AWS, Wasabi, Cloudflare R2, MiniIO, na ncha zingine zinazoendana na S3 |
| **Nyingine** | hifadhi inayoungwa mkono na rclone (k.m. WebDAV, SFTP, na vidhibiti vingine vilivyoratibiwa vinavyopatikana katika kiteua cha ndani ya programu) |

Sanidi vijisehemu vya kudanganya vya picha na malengo ya jedwali ya kujipangisha mwenyewe: [kielezo cha mwenyeji binafsi](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Tabia ya vifaa vingi (fupi)

- Safu mlalo huunganishwa kwa **Kitambulisho cha Usawazishaji** na **mashindi-mwisho-wa mwisho** kwenye mihuri ya muda **Ilisasishwa.
- Vifuta ni laini; hariri mpya kwenye kifaa kingine inaweza kurejesha safu mlalo.
- Kuingiza **kujaza sawa mara mbili** kwenye vifaa viwili huunda **safu mlalo mbili** — futa ziada unapotambua.
- Maelezo zaidi: [Maelezo ya tabia ya kusawazisha](#sync-behavior-notes) na [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Mfano: ongeza Majedwali ya Google (data)

1. **Menyu → Kusawazisha → Usawazishaji lahajedwali** (au Mipangilio → Usawazishaji wa Lahajedwali).

![Orodha ya kusawazisha lahajedwali](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Gusa **Ongeza lengwa la lahajedwali**.

![Kiteua mtoa huduma](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Chagua **Majedwali ya Google**.

![fomu ya Majedwali ya Google](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Ingia ukitumia Google** mara moja - kuingia huko pia hukuruhusu kuvinjari. Jina la kuonyesha → bandika **URL ya Laha** (bado ni halali) au **🔍** vinjari. Kivinjari hufunguliwa kwenye **Zote & Zilizoshirikiwa** ili uweze kuchagua lahajedwali iliyopo unayomiliki au ambayo imeshirikiwa nawe. Kisha panga chaguzi → wezesha → hifadhi.
5. **Sawazisha sasa** mara moja ili kuunda/kusasisha vichupo: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Mfano: ongeza Hifadhi ya Google (picha)

1. **Menyu → Kusawazisha → Hifadhi nakala ya picha** (au Mipangilio → Hifadhi nakala ya picha).

![Orodha ya nakala rudufu ya picha](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Gusa **Ongeza marudio ya picha**.

![Kiteua cha mtoa huduma wa picha](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Chagua **Hifadhi ya Google**.

![Fomu ya Hifadhi ya Google](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Ingia ukitumia Google (Hifadhi)** → folda ya hiari ya URL/vinjari → washa → hifadhi → **Sawazisha sasa**.

Mwongozo **Sawazisha sasa** kwa picha ni pasi kamili; chelezo cha usuli kwa kawaida huchakata upakiaji wa **inasubiri-pekee** kwa ratiba.

### <a id="sync-behavior-notes"></a>Sawazisha vidokezo vya tabia

- Baada ya uboreshaji wa programu unaweza kuona kwa ufupi **“Kusasisha hifadhidata baada ya kusasisha…”** (kujaza tena kitambulisho cha kusawazisha).
- Usawazishaji ukikatizwa, usawazishaji unaofuata **uliofaulu** huunganishwa tena na kurekebisha vichupo vya mbali.
- Imefeli: muhtasari mwekundu kuhusu Kusawazisha kadi + **!** katika upau wa programu.

---

## <a id="fill-up-fuel"></a>Jaza (mafuta)

Hii ni **skrini ya nyumbani** unapofungua programu.

### <a id="vehicle-selection-usually-automatic"></a>Uteuzi wa gari (kwa kawaida kiotomatiki)

Huhitaji **** kuchagua gari kwanza. Wakati magari yana **alama** zilizowekwa katika Dhibiti Magari, Jaza **inatambua kiotomatiki gari gani** kutoka kwenye picha ya dashi baada ya kunasa odometer. Bado unaweza kufungua menyu kunjuzi ya **Gari** ili kubatilisha ikihitajika.

### <a id="aim-at-the-odometer"></a>Lenga odometer

Kaa katika hali ya odometer na uunde nguzo. Maagizo: *Lenga odometer. Gusa shutter ili kunasa.*

![Jaza — kamera ya moja kwa moja kwenye odometer (mfano wa picha ya dashi katika onyesho la kukagua)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Baada ya shutter ya odometer

OCR inajaza **Odo** na inajaribu kulinganisha gari kutoka alama muhimu (kagua zote mbili ikihitajika). Kitufe kikuu kinakuwa **Jaribu Tena** ili kupiga tena. Maelekezo ni muhtasari wa usomaji.

![Jaza — odometer tokeo la OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Modi ya pampu (gharama na sauti)

1. Gusa **↕** ili ubadilishe hadi modi ya pampu: *Lenga onyesho la pampu (gharama/kiasi). Gusa shutter.*
2. Nasa jumla ya pampu. Gharama na mashamba ya kiasi hujaza; tumia **↔** ikiwa zimebadilishwa.
3. Gusa sarafu au **G/L** ikihitajika, kisha **Hifadhi** (diski). Sehemu tupu hufanya kujaza ** kwa sehemu** (bado inaruhusiwa).

![Jaza — onyesho la kukagua pampu / jumla (mfano wa picha ya pampu katika onyesho la kukagua)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Unasalia kwenye Jaza kwa kituo kinachofuata (uga husafishwa baada ya kuhifadhi). Fanya kazi kikamilifu **nje ya mtandao**; usawazishaji huendeshwa baadaye chinichini wakati kusanidiwa.

### <a id="manual-entry-no-camera--bad-ocr"></a>Ingizo mwenyewe (hakuna kamera / OCR mbaya)

1. Gusa **Odo**, **gharama**, au **kiasi** na uandike thamani (wima hutumia kibodi ya mfumo; mlalo hutumia vitufe vya skrini).
2. Chagua au uthibitishe **Gari** ikiwa utambuzi wa kiotomatiki haukuendeshwa.
3. Hifadhi kama ilivyo hapo juu.

### <a id="modes-and-borders"></a>Modi na mipaka

- **Mpaka wa kijani** kuzunguka gari+odo → kunasa/kuhariri odometer.
- **Mpaka wa kijani** karibu na gharama+kiasi → modi ya pampu.
- **Hifadhi** itasalia kuzima hadi gari lichaguliwe na angalau moja ya odo/gharama/kiasi kiwe na data, na OCR bado haifanyi kazi.

Kidokezo cha skrini (chini ya mstari wa maagizo): *Shutter = kamata · Diski = hifadhi · ↕ = hali ya odo/pampu · ↔ = badilisha gharama/kiasi.*

---

## <a id="expenses"></a>Gharama

### <a id="new-expense"></a>Gharama mpya

Menyu → **Gharama mpya**.

![Fomu mpya ya gharama na vidhibiti vya kamera (mfano picha ya dashi kwenye bendi ya kamera ikiwa tupu)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Hifadhi** (diski), ** shutter** (picha ya risiti), au **matunzio** (chagua picha).
2. Jaza **Tarehe**, **Gari**, **Muuzaji**, **Maelezo**, **Kiasi** (alama ya sarafu inayoweza kuguswa), **Kitengo**, hiari **Odometer**.
3. Stakabadhi za kurasa nyingi: nasa kurasa za ziada ikiwa kiolesura hutoa paging (ukurasa wa 0 ndio risiti ya msingi).
4. **Hifadhi** ili kuhifadhi (ndani kwanza; hifadhi rudufu ya picha na usawazishaji lahajedwali hufanyika chinichini inaposanidiwa).

### <a id="expense-list"></a>Orodha ya gharama

Menyu → **Ripoti** → **Orodha ya Gharama** — vinjari gharama za zamani zisizo za mafuta; fungua kipengee ili kuhariri.

![Orodha ya gharama](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Hariri gharama

Fungua safu mlalo kutoka kwenye orodha. Muuzaji sahihi, kiasi, kategoria, gari na maelezo. Ikiwa risiti iko kwenye hifadhi rudufu ya picha pekee (hakuna faili ya ndani inayoweza kusomeka), tumia **Leta picha kutoka kwenye kumbukumbu** inapoonyeshwa (hufanya kazi katika maeneo yalengwa ya picha).

![Hariri gharama](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Anza safari

Menyu → **Anza safari** (baada ya Kujaza droo). Nasa au weka odometer, chagua aina ya safari, hifadhi kwa aikoni ya **diski**. **Stop** ni njia ya mkato ya Binafsi sasa katika eneo la GPS lililoshikiliwa. Tumia **ⓘ** kwa vikumbusho vya udhibiti.

![Anza safari](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Safari zinazoanza huhifadhiwa kama safu mlalo za mafuta kwa **Aina ya Safari** (si kujaza kawaida). Zinaonekana chini ya **Ripoti → Maili ya safari**, sio chini ya Historia ya Mafuta.

---

## <a id="reports"></a>Ripoti

Menyu → **Ripoti** hufungua kitovu cha bidhaa (muhtasari wa kila wakati + kadi za katalogi). Hili ndilo eneo pekee la ripoti za bidhaa - hakuna kipengee tofauti cha droo ya "Ripoti na Chati".

![Kitovu cha ripoti](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Fungua kadi kwa hali ya gari (**Zote / Kila / Moja**), vichujio vya vipindi, chati na ushiriki (**TEXT / CSV / PDF**). Upau wa juu kwenye ripoti ya watoto: **☰ + ←** (na **ⓘ** wakati umesajiliwa).

### <a id="time-based-reports"></a>Ripoti kulingana na wakati

Kadi kuu ya chati. Vipimo vya hiari (mpg, kiasi/umbali kama vile G/mi, bei ya kitengo kama vile $/G, gharama/umbali, $/G, maili ya safari, safari % kulingana na aina) na **mizani laini** na **mizani ya Y inayojitegemea** (uchumi wa kushoto; pesa na familia za safari upande wa kulia).

![Ripoti kulingana na wakati](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Ripoti kulingana na wakati — vipimo vilivyosogezwa](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Maelezo ya hesabu ya uchumi: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Jaza historia dhidi ya Historia ya Mafuta

- **Ripoti → Jaza historia** — ujazo wa mpangilio kwa vichujio vya ripoti (** hujaza pekee**; hakuna safari inayoanza).

![Jaza ripoti ya historia](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Historia ya Mafuta** (ikiwa ipo katika urambazaji wa muundo wako) — orodha ya kujaza kwa kila gari, pia inajaza pekee; gusa safu mlalo ili kuhariri.

### <a id="trip-miles"></a>Safari maili

**Ripoti → Maili za safari** — maili kwa aina, chati, na mpangilio wa matukio **orodha ya kuanza kwa safari/sehemu**. Gusa mwanzo halisi ili ufungue **Hariri kujaza** kwa safu mlalo hiyo.

![Maili ya safari](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Hariri kujaza

Kutoka kwa historia ya Kujaza, Historia ya Mafuta, au Maili ya Safari, fungua kujaza. Mpangilio: gari na odometer, **fedha kabla ya gharama **, kiasi, noti. Aina ya safari inaonekana tu wakati safu mlalo ni mwanzo wa safari. Mahali pana muhtasari pamoja na **Maelezo ya eneo**. Haipo picha ya ndani yenye utambulisho wa wingu: **Leta picha kutoka kwenye kumbukumbu**.

![Hariri kujaza — Leta kutoka kwenye kumbukumbu wakati picha ya ndani haipo](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Kadi nyingine za katalogi ni pamoja na gharama kulingana na kategoria, muhtasari wa gari na orodha ya gharama.

Pesa hutumia sarafu ya kila safu inapowekwa. Jumla ya sarafu-mseto huonyesha **jumla ndogo za kila sarafu** (hakuna ubadilishaji wa FX kimya).

---

## <a id="syncing"></a>Kusawazisha

Menyu → **Kusawazisha** ndicho kitovu cha lahajedwali na lengwa la picha (sio kuzikwa tu chini ya Mipangilio).

![Kitovu cha kusawazisha](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Kadi za **kusawazisha lahajedwali** na **Nakala rudufu ya picha** zenye hali fupi, **Sawazisha** kwa aina hiyo, na **›** kwenye orodha lengwa.
- Fungua lengwa la **Jaribio la muunganisho** na **Sawazisha sasa (mahali hapa)** / zote zimesanidiwa.
- Imeshindwa **Maelezo** na nyekundu **!** kwenye upau wa kichwa hutua hapa.
- Usanidi wa hatua kwa hatua wa Majedwali ya Google na Hifadhi ya Google: [Hifadhi rudufu na usawazishaji wa vifaa vingi](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Mipangilio (mapendeleo ya ndani)

Menyu → **Mipangilio**.

![Mipangilio](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Mipangilio zaidi (iliyovingirishwa)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Kwa unakoenda, pendelea **Menyu → Kusawazisha**. Mipangilio bado inaweza kuonyesha safu mlalo za muhtasari zinazofungua orodha zile zile.

### <a id="local-preferences-common"></a>Mapendeleo ya ndani (ya kawaida)

- **Hifadhi Picha za Risiti ya Mafuta** / **Hifadhi Picha za Gharama Ndani Yako** — weka picha kwenye kifaa (huenda ukaomba ruhusa ya Picha).
- **Cheza Sauti ya Shutter**
- **Fedha** / **Kipimo cha sauti** — chaguomsingi za programu (mfumo au lugha chafu). Kubadilisha kitengo cha sauti na data iliyopo ya mafuta kunaweza kutoa kidirisha cha kubadilisha.
- **Njia nyeusi**
- **Vidokezo vya kuweka mipangilio** — fungua tena mafunzo ya gari la kwanza/sawazisha.
- **Tatua Jaza** / **Onyesha skrini za majaribio (dev)** — imeboreshwa; acha kwa matumizi ya kila siku. Skrini za majaribio hazijarekodiwa hapa.

CSV **kuuza/kuagiza** (ZIP ya Magari / Gharama / Vichupo vya Mafuta) inapatikana kutoka kwa Mipangilio inapotolewa na muundo wa sasa.

---

## <a id="help--about"></a>Msaada na Kuhusu

![Msaada](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Kuhusu](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Msaada** — anza haraka kwenye kifaa, mafunzo ya kuweka mipangilio, kiungo cha mwongozo huu, faharasa ya usanidi wa mwenyeji mwenyewe.
- **Kuhusu** — toleo, leseni, GitHub, mwongozo huu (uliounganishwa nje ya mtandao + HTML ya mtandaoni unapochapishwa).

---

## <a id="related-docs"></a>Hati zinazohusiana

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — mwongozo mfupi wa kila siku
- [mwenyeji mwenyewe/INDEX.md](../reference/self-host/INDEX.md) — usanidi wa picha/jedwali inayojipangisha
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — unganisha, urejeshaji, nakala
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — maelezo ya vipimo vya uchumi
