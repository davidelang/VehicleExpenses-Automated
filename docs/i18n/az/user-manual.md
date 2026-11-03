# <a id="vehicle-expenses-automated--user-manual"></a>Avtomatlaşdırılmış Avtomobil Xərcləri — İstifadəçi Təlimatı

> **Mənbəni redaktə edin (Markdown).** Brauzerlər və tətbiqdaxili oxucu **göstərilən HTML**-i açır:
> - Veb: [`docs/user-manual/index.html`](index.html) (`./scripts/render-user-manual.sh` ilə bərpa edin)
> - Tətbiq: Yardım / Haqqında → tam təlimat (birləşdirilmiş HTML + ekran görüntüləri)
>
> Son istifadəçiləri xam `.md` URL-lərə yönəltməyin — brauzerlər yalnız düz mətn göstərir.

**sizin** bulud hesabları altında isteğe bağlı çoxlu cihaz sinxronizasiyası və ehtiyat nüsxəsi ilə yanacaq doldurulması və avtomobil xərcləri üçün kamera ilə ilk izləmə.

Bu **tam təlimatdır** (ekran görüntüləri + hər addım). Telefonda **Menyu → Kömək** başlanğıc üçün daha qısa bələdçidir.

**Burada əhatə olunmur:** Köhnə şəkilləri, Hizalama Təcrübəsini və Nasos Təcrübəsini (inkişafçı / təkmil alətlər) idxal edin.

---

## <a id="table-of-contents"></a>Mündəricat

1. [Nə lazımdır](#what-you-need)
2. [Bir baxışda nişanlar](#icons-at-a-glance)
3. [Menyunu açın](#open-the-menu)
4. [İlk dəfə quraşdırma: Nəqliyyat vasitələrini idarə edin](#first-time-setup-manage-vehicles)
5. [Yedəkləmələr və çoxlu cihaz sinxronizasiyası](#backups-and-multi-device-sync)
6. [Doldurun (yanacaq)](#fill-up-fuel)
7. [Səyahətə başlayın](#start-trip)
8. [Xərclər](#expenses)
9. [Hesabatlar](#reports)
10. [Parametrlər (yerli seçimlər)](#settings-local-preferences)
11. [Sinxronizasiya](#syncing)
12. [Kömək və Haqqında](#help--about)
13. [Əlaqədar sənədlər](#related-docs)

---

## <a id="what-you-need"></a>Sizə lazım olan

- Android telefon və ya planşet.
- Ən yaxşı OCR üçün: **kontrol panelinin odometrinin** və **nasos cəmlərinin** aydın görünüşü (və ya rəqəmləri əl ilə yazın).
- Könüllü: cədvəl datası və/və ya foto yedəkləmə üçün **nəzarət etdiyiniz** hesablar (bax [Yedəkləmələr və çoxlu cihaz sinxronizasiyası](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>İkonlar bir baxışda

Bunlar əsas ekranlarda görünür. Onları bilmək çoxlu ovdan xilas edir.

| Harada | Nişan / nəzarət | Nə edir |
|-------|----------------|--------------|
| Üst bar | **☰ Menyu** (hamburger) | Naviqasiya qutusunu açır |
| Üst bar | **ⓘ** (səhifə yardımı) | **cari** səhifəsi üçün qısa yardım (mövcud olduqda menyunun yanında) |
| Üst bar | **`?N`** (sarı) | Gözlənilən idxalın nəzərdən keçirilməsi sualları — İdxalın nəzərdən keçirilməsi | açılır
| Üst bar | **!** (qırmızı) | Cədvəl və ya foto təyinatı bu yaxınlarda uğursuz oldu — düzəltmək üçün **Sinxronizasiya**-nı açın
| Üst bar | **☰ + ←** | Uşaqları və Xərclər siyahısını **menyu və geri** birlikdə göstərin; Hesabatlar mərkəzi yalnız menyudur |
| Parametrlər / yanacaq redaktəsi | **←** | Geri (parametrlər cədvəli/şəkil və yanacaq redaktəsi geriyə fokuslanır) |
| Doldur | **Ağ dairə** (kapanma) | OCR | üçün odometr və ya nasos ekranını çəkin
| Doldur | **Disk / Saxla** | Doldurmağı saxla (bir avtomobil və ən azı bir odo / həcm / xərc tələb olunur) |
| Doldur | **↕ oxlar** (rejim açarı) | **odometr rejimi** və **nasos (qiymət/həcm) rejimi** ilə dəyişin. Yaşıl sərhəd aktiv sahə qrupunu vurğulayır |
| Doldur | **↔ oxlar** (qiymət və həcm arasında) | OCR onları yanlış sahələrə qoyursa, dəyəri və həcmi dəyişdirin |
| Doldur | **1x böyüt / …** | Obyektiv onları dəstəklədikdə kameranın böyütmə nisbətləri |
| Doldurun (tutduqdan sonra) | **Yenilə** əsas düymədə | Önizləməni ləğv edin və canlı kameraya qayıdın |
| Doldurun (emal zamanı) | **X** əsas düymədə | Davam edən çəkilişi/OCR | ləğv edin
| Xərc | **Saxla** | Xərclərə qənaət |
| Xərc | **Kapalı dairəsi** | Qəbz şəklini çəkin |
| Xərc | **Qalereya** | Kitabxanadan qəbz şəklini seçin |
| Xərc | **Yenidən alın** | Cari qəbz şəklini silin və yenidən çəkin |
| Xərc / Nəqliyyat vasitələrini idarə et | **+ / −** FABs | Fotoya baxışı böyüt |
| İşarələr dialoqu | **OCR-i redaktə et** | Mühərriklərin buraxdığı əlamətdar mətni düzəldin və ya əlavə edin |
| Elektron cədvəl / Şəkil formaları | **🔍 Axtar** | Vərəq və ya qovluq üçün Google Diskə baxın (girişdən sonra) |

Xərc sahələrində valyuta simvolları və həcm sahələrində **G/L** toxuna bilər: həmin giriş üçün valyuta və ya qallon və litri dəyişmək üçün kiçik menyu açın.

---

## <a id="open-the-menu"></a>Menyunu açın

1. Sol yuxarıda **☰** üzərinə klikləyin.
2. Səhifə seçin.

![Naviqasiya çekmecesi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Əsas siyirtmə:** Doldurun · Səfərə başlayın · Nəqliyyat vasitələrini idarə edin · Yeni xərc · **Hesabatlar** · Parametrlər · Sinxronizasiya · Yardım · Haqqında.

**Təcrübə çekmecesi** (Parametrlər → Təcrübə ekranlarını göstər): Hizalama Eksperimenti · Nasos Təcrübəsi · **Köhnə Şəkilləri İdxal Et**.

**Hesabatlar mərkəzi vasitəsilə (əsas siyirtmə deyil):** Xərclər siyahısı · Tarixçəni doldurun.

---

## <a id="first-time-setup-manage-vehicles"></a>İlk dəfə quraşdırma: Nəqliyyat vasitələrini idarə edin

OCR və **avtomatik avtomobil uyğunluğu**, hər bir avtomobili **istinad tablosunun fotoşəkili** ilə qeydiyyatdan keçirdikdən, **Discovery**-ni işə saldıqdan, avar toxumundan odometr məhsulunu təyin etdikdən və həmin tire üçün işarələri saxladıqdan sonra ən yaxşı şəkildə işləyir.

### <a id="open-manage-vehicles"></a>Açıq Avtomobilləri İdarə et

Menyu → **Nəqliyyat vasitələrini idarə et**. Nəqliyyat vasitəsini seçin (və ya **Yeni Avtomobil əlavə edin**).

![İstinad tire, məhsullar və kəşf ilə Nəqliyyat vasitələrini idarə edin](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Avtomobil əlavə edin və ya redaktə edin

1. **Vehicle** açılan menyunu açın → nəqliyyat vasitəsi seçin və ya **Yeni Avtomobil əlavə edin**.
2. Aydın **istinad tire şəkli** çəkin və ya seçin (tam alətlər qrupu, yaxşı işıqlandırılmış, telefon təxminən kvadrat). **Şəkil çək** və ya **Qalereya**-dan istifadə edin.
3. **Run Discovery** seçiminə toxunun — avar aşkarlanması fotoda sarı **toxumlar** yerləşdirir (işarələr əvvəlki kimi eyni `"ML Kit"` JSON-dan istifadə edir).
4. **Select Odo** klikləyin, rəqəmlər üzərində toxuma toxunun, tutacaqları tənzimləyin, sonra **Saxla**.
5. **Edit Odo** artıq mövcud olan qutunu tənzimləyir. **Clear Odo** onu silir və düymə sonra **Select Odo** yazır.
6. **Show Landmarks** ilə nəzərdən keçirin. Səhv oxunuşları düzəltmək və ya buraxılmış mətni **əlavə etmək üçün **OCR-ni redaktə et**-dən istifadə edin.
7. **Maşının Adı** (tələb olunur), üstəgəl marka/model/il/növbəni istədiyiniz kimi doldurun.
8. **Maşın Yarat** və ya **Dəyişiklikləri Saxla** (yeni avtomobil üçün ad + istinad şəkli tələb olunur) üzərinə klikləyin.

![Yeni avtomobil forması sahələri əlavə edin](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>İstiqamət nişanları: Discovery-nin əldən verdiyini düzəldin

**Show Landmarks**-dan sonra siyahını sürüşdürün və dəyərləri düzəldin. Mühərriklər bəzən kiçik rəqəmləri əldən verirlər (məsələn, klasterin aşağı sağ hissəsindəki saat **60**). Avtomobilin şəxsiyyətinin etibarlı qalması üçün onları əlavə etmək və ya düzəltmək üçün **Redaktə OCR**-dan istifadə edin.

![Maraqlar dialoqu — OCR-ni nəzərdən keçirin və redaktə edin](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Mükəmməl şəkil olmadan yazmaq

Siz hələ də nəqliyyat vasitəsini seçərək və Doldurmada odometr, həcm və dəyəri **yazaraq** tətbiqdən istifadə edə bilərsiniz — OCR hər sahə üçün isteğe bağlıdır. Tətbiqdaxili çəkməməyi seçdiyiniz zaman qalereya idxalı istinad tire şəkli üçün işləyir.

**İpucu:** Cədvəl sinxronizasiyasından sonra nəqliyyat vasitələrinin tərifləri (əkinlər, nişanlar) yerli verilənlər bazasında yaşayır — onlardan istifadə etmək üçün Avtomobilləri Doldurmaq üçün İdarə et bölməsini yenidən açmağa ehtiyac yoxdur.

---

## <a id="backups-and-multi-device-sync"></a>Yedəkləmələr və çoxlu cihaz sinxronizasiyası

Tətbiq elə qurulub ki, **bir neçə telefon və ya planşet eyni donanma məlumatını paylaşa bilsin** və beləliklə, siz **məlumat və şəkillərinizin surətini cihazdan kənarda saxlaya biləsiniz**. Bu, digər insanların görə biləcəyi şirkət tərəfindən idarə olunan “Maşın Xərcləri buludu” deyil, **hesablarınız və ya **özünüzə məxsus serverlər altında **siz** konfiqurasiya etdiyiniz təyinatlarla həyata keçirilir.

### <a id="what-runs-where"></a>Nə hara qaçır

| Xeyir | Nə saxlayır | Tipik istifadə |
|------|----------------|-------------|
| **Cədvəl / cədvəl sinxronizasiyası** | Nəqliyyat vasitələri, yanacaq doldurmaları, xərclər (sətirlər və nişanlar) | Çox cihaz birləşməsi + strukturlaşdırılmış ehtiyat |
| **Foto ehtiyat nüsxəsi** | İkili şəkillər (tire/nasos/qəbz/istinad şəkilləri) | Fotonun ehtiyat nüsxəsi + itkin faylları bərpa edin |

Siz hər növdə **birdən çox təyinat yeri** konfiqurasiya edə bilərsiniz (hər növ üçün yumşaq qapaq). Manual **İndi sinxronlaşdırın** və **fonda** işçilər aktiv olanları işə salır.

### <a id="offline-first"></a>Əvvəlcə oflayn

- Doldurma, xərc və ya qəbz əlavə etmək üçün **Şəbəkə tələb olunmur**. Hər şey **öncə yerli olaraq yadda saxlanılır**.
- Şəbəkə mövcud olduqda, sinxronizasiya və fotonun ehtiyat nüsxəsi **fon tapşırıqları** kimi icra olunur (təyin etdiyiniz cədvəl üzrə və **İndi sinxronlaşdırın** seçiminə toxunduğunuz zaman). Uğursuzluqlar Parametrlər sətirlərinin altında qırmızı mətn və tətbiqin başlıq panelində **!** kimi göstərilir.

### <a id="your-accounts-only"></a>Yalnız sizin hesablarınız

Daxil olma və işarələr seçdiyiniz provayderlər üçün cihazda qalır (Google, Microsoft, S3 açarları, özünə məxsus URL-lər və s.). Təyinatlar **istifadəçinin tam nəzarəti altındadır** — Google hesabınız, OneDrive, MiniIO qutunuz, EtherCalc hostunuz və s. Heç bir şey paylaşılan backend vasitəsilə digər Nəqliyyat Xərcləri istifadəçiləri ilə paylaşılmır.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Dəstəklənən hədəflər — data (elektron cədvəl / cədvəl)

**Menyu → Sinxronizasiya → Cədvəl sinxronizasiyası** altında konfiqurasiya edilmişdir (həmçinin Parametrlərin xülasə sətirlərindən əldə etmək olar). Birinci dərəcəli seçici seçimləri:

| Hədəf | Qeydlər |
|--------|--------|
| **Google Cədvəl** | Ümumi defolt; Nəqliyyat vasitələri, xərclər və hər bir avtomobil üçün yanacaq üçün nişanlar |
| **Excel** | Qrafik / OneDrive üslublu bağlama vasitəsilə Microsoft iş kitabı |
| **EtherCalc** | Öz-özünə yerləşdirilən əməkdaşlıq elektron cədvəl otaqları |
| **Digər →** həyata keçirilən arxa uçlar | **Baserow**, **NocoDB**, **Airable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Təxirə salınıb / hələ başsız deyil (Digər altında sadalanıb, lakin tam tətbiq olunmayıb): YalnızOffice, Collabora. Həmçinin baxın [öz-özünə ev sahibi indeksi](reference/self-host/INDEX.md).

CSV **ixrac/idxal** (eyni tab tərtibatının ZIP-i) canlı sinxronizasiyadan asılı olmayaraq portativ ehtiyat nüsxə kimi Parametrlərdən əldə edilə bilər.

### <a id="supported-targets--photos-image-backup"></a>Dəstəklənən hədəflər — fotoşəkillər (şəklin ehtiyat nüsxəsi)

**Menyu → Sinxronizasiya → Fotonun ehtiyat nüsxəsi** altında konfiqurasiya edilib (həmçinin Parametrlərin xülasə sətirlərindən):

| Hədəf | Qeydlər |
|--------|--------|
| **Google Disk** | Seçdiyiniz qovluq (URL-u gözdən keçirin və ya yapışdırın) |
| **OneDrive** | Microsoft hesabı + yol prefiksi |
| **S3** | AWS, Wasabi, Cloudflare R2, MiniIO və digər S3 uyğun son nöqtələr |
| **Digər** | rclone tərəfindən dəstəklənən yaddaş (məsələn, WebDAV, SFTP və tətbiqdaxili seçicidə mövcud olan digər seçilmiş pultlar) |

Öz-özünə yerləşdirilən foto və cədvəl hədəfləri üçün fırıldaq cədvəllərini quraşdırın: [öz-özünə sahiblik indeksi](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Çox cihaz davranışı (qısa)

- Sıralar **Sinxronizasiya ID** ilə **Yenilənmiş** vaxt ştamplarında **son yazma-qalibiyyət** ilə birləşir.
- Silinmələr yumşaqdır; başqa cihazda daha yeni redaktə cərgəni bərpa edə bilər.
- İki cihazda **eyni doldurmanı iki dəfə** daxil etmək **iki sıra** yaradır — fərq etdikdə əlavəni silin.
- Daha ətraflı: [Sinxronizasiya davranış qeydləri](#sync-behavior-notes) və [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Nümunə: Google Cədvəl əlavə edin (data)

1. **Menyu → Sinxronizasiya → Cədvəl sinxronizasiyası** (və ya Parametrlər → Cədvəl sinxronizasiyası).

![Cədvəl sinxronizasiya siyahısı](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. **Cədvəl təyinatı əlavə et** üzərinə klikləyin.

![Provayder seçicisi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. **Google Cədvəl** seçin.

![Google Cədvəl forması](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Google ilə bir dəfə daxil olun** — bu giriş həm də gözdən keçirməyə imkan verir. Göstərilən ad → **Cədvəl URL-i** (hələ də etibarlı) yapışdırın və ya **🔍** gözdən keçirin. Gözdən keçirmə **Hamı və paylaşılan**-da açılır ki, siz sahib olduğunuz və ya sizinlə paylaşılan mövcud cədvəli seçə biləsiniz. Sonra seçimləri planlaşdırın → aktiv edin → yadda saxlayın.
5. **İndi sinxronlaşdırın** tabları yaratmaq/güncəlləmək üçün bir dəfə: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Nümunə: Google Disk əlavə edin (şəkillər)

1. **Menyu → Sinxronizasiya → Fotonun ehtiyat nüsxəsi** (və ya Parametrlər → Fotonun ehtiyat nüsxəsi).

![Foto ehtiyat nüsxəsi siyahısı](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. **Foto təyinatı əlavə et** üzərinə klikləyin.

![Foto provayder seçicisi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. **Google Disk** seçin.

![Google Disk forması](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Google (Drive) ilə daxil olun** → əlavə qovluq URL-i/gözdən keçirin → aktiv edin → yadda saxla → **İndi sinxronlaşdırın**.

Manual **İndi sinxronizasiya** fotoşəkillər üçün tam keçiddir; fon ehtiyat nüsxəsi adətən cədvəl üzrə **yalnız gözlənilən** yükləmələri emal edir.

### <a id="sync-behavior-notes"></a>Sinxronizasiya davranış qeydləri

- Tətbiqi təkmilləşdirdikdən sonra siz qısa müddət ərzində **“Təkmilləşdirmədən sonra verilənlər bazası yenilənir…”** (yerli sinxronizasiya identifikatorunun doldurulması) ilə tanış ola bilərsiniz.
- Sinxronizasiya kəsilirsə, növbəti **uğurlu** sinxronizasiya uzaq tabları yenidən birləşdirir və təmir edir.
- Uğursuzluqlar: proqram panelində sinxronizasiya kartları + **!** üzrə qırmızı xülasə.

---

## <a id="fill-up-fuel"></a>Doldurma (yanacaq)

Bu, proqramı açdığınız zaman **əsas ekrandır**.

### <a id="vehicle-selection-usually-automatic"></a>Avtomobil seçimi (adətən avtomatik)

Əvvəlcə avtomobili seçməyinizə ehtiyac yoxdur. Nəqliyyat vasitələrinin "Nəqliyyat vasitələrini idarə et" bölməsində **işarələri** quraşdırıldıqda, odometri çəkdikdən sonra tire şəklindən **hansı avtomobili** avtomatik aşkarlayanı doldurun. Lazım gələrsə, ləğv etmək üçün hələ də **Vehicle** açılan menyunu aça bilərsiniz.

### <a id="aim-at-the-odometer"></a>Odometerə nişan alın

Odometr rejimində qalın və klasteri çərçivəyə salın. Təlimat: *Odometrə yönəldin. Çəkmək üçün çekimə toxunun.*

![Doldurun — odometrdə canlı kamera (previewdəki tire şəklinə nümunə)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Odometer çekimindən sonra

OCR **Odo**-nu doldurur və avtomobili əlamətdar yerlərdən tutmağa çalışır (lazım olduqda hər ikisini nəzərdən keçirin). Əsas düymə yenidən çəkmək üçün **Yenidən cəhd edin** olur. Təlimat oxunuşu yekunlaşdırır.

![Doldurun — odometr OCR nəticəsi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Nasos rejimi (qiymət və həcm)

1. Nasos rejiminə keçmək üçün **↕** klikləyin: *Nasos ekranına (qiymət/həcm) yönəlin. Çekim düyməsinə toxunun.*
2. Pompanın ümumi sayını çəkin. Xərc və həcm sahələri doldurulur; dəyişdirildikdə **↔** istifadə edin.
3. Valyuta və ya lazım gələrsə **G/L**, sonra **Saxla** (disk) üzərinə toxunun. Boş sahələr **qismən doldurma** edir (hələ də icazə verilir).

![Doldurun — nasosun önizləməsi / cəmi (nasos şəklinin ilkin baxışda nümunəsi)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Siz növbəti dayanacaq üçün Doldurmada qalırsınız (saxladıqdan sonra sahələr təmizlənir). Tam **oflayn** işləyin; konfiqurasiya edildikdə sinxronizasiya daha sonra fonda işləyir.

### <a id="manual-entry-no-camera--bad-ocr"></a>Manual giriş (kamera yoxdur / pis OCR)

1. **Odo**, **qiymət** və ya **həc** və dəyərləri yazın (portret sistem klaviaturasından istifadə edir; landşaft ekrandakı klaviaturadan istifadə edir) üzərinə klikləyin.
2. Avtomatik aşkarlama işləməyibsə, **Avtomobili** seçin və ya təsdiqləyin.
3. Yuxarıdakı kimi yadda saxlayın.

### <a id="modes-and-borders"></a>Rejimlər və sərhədlər

- Avtomobilin ətrafında **Yaşıl haşiyə**+odo → odometrin çəkilməsi/redaktə edilməsi.
- xərc+həcm → nasos rejimi ətrafında **Yaşıl haşiyə**.
- **Saxla** nəqliyyat vasitəsi seçilənə və odo/qiymət/həcmdən ən azı birində data olana və OCR hələ də işləməyənə qədər qeyri-aktiv qalır.

Ekrandakı ipucu (təlimat xəttinin altında): * Çekim = çəkmə · Disk = qənaət · ↕ = odo/nasos rejimi · ↔ = dəyişdirmə dəyəri/həcmi.*

---

## <a id="expenses"></a>Xərclər

### <a id="new-expense"></a>Yeni xərc

Menyu → **Yeni xərc**.

![Yeni xərc forması və kamera nəzarətləri (boş olduqda kamera lentindəki tire şəklinə nümunə)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Saxla** (disk), **kapula** (qəbz şəkli) və ya **qalereya** (şəkli seçin).
2. **Tarix**, **Maşın**, **Satıcı**, **Təsvir**, **Məbləğ** (valyuta simvolu tapıla bilər), **Kateqoriya**, isteğe bağlı **Odometr** doldurun.
3. Çox səhifəli qəbzlər: UI səhifələmə təklif edirsə, əlavə səhifələr çəkin (səhifə 0 əsas qəbzdir).
4. Saxlamaq üçün **Saxla** (əvvəlcə yerli; konfiqurasiya edildikdə foto ehtiyat nüsxəsi və cədvəl sinxronizasiyası fonda baş verir).

### <a id="expense-list"></a>Xərc siyahısı

Menyu → **Hesabatlar** → **Xərclər siyahısı** — yanacaqla bağlı olmayan keçmiş xərclərə göz atın; redaktə etmək üçün elementi açın.

![Xərc siyahısı](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Xərcləri redaktə edin

Siyahıdan bir sıra açın. Düzgün satıcı, məbləğ, kateqoriya, nəqliyyat vasitəsi və təsvir. Qəbz yalnız fotonun ehtiyat nüsxəsindədirsə (oxunan yerli fayl yoxdur), göstərildikdə **Arxivdən şəkli götürün** funksiyasından istifadə edin (konfiqurasiya edilmiş foto təyinatları üzrə işləyir).

![Xərcləri redaktə edin](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Səyahətə başlayın

Menyu → **Səyahətə başla** (Siyamanı doldurduqdan sonra). Odometri çəkin və ya daxil edin, səyahət növünü seçin, **disk** işarəsi ilə yadda saxlayın. **Stop** indi GPS yerində şəxsi üçün qısa yoldur. Nəzarət xatırlatmaları üçün **ⓘ** istifadə edin.

![Səyahətə başlayın](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Səyahət başlanğıcları **Səyahət Tipi** (normal doldurma deyil) ilə yanacaq sıraları kimi saxlanılır. Onlar Yanacaq Tarixçəsi altında deyil, **Hesabatlar → Səfər milləri** altında görünür.

---

## <a id="reports"></a>Hesabatlar

Menyu → **Hesabatlar** məhsul mərkəzini açır (hər zaman xülasə + kataloq kartları). Bu, məhsulun yeganə hesabat səthidir - ayrıca "Hesabatlar və Diaqramlar" qutusu elementi yoxdur.

![Hesabat mərkəzi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Avtomobil rejimi (**Hamı / Hər / Tək**), dövr filtrləri, qrafiklər və paylaşma (**TEXT / CSV / PDF**) üçün kart açın. Hesabatlı uşaqlar üçün üst sətir: **☰ + ←** (və qeydiyyatdan keçdikdə **ⓘ**).

### <a id="time-based-reports"></a>Zamana əsaslanan hesabatlar

Əsas qrafik kartı. **Smooth** qutuları və **müstəqil Y tərəzi** (sağda iqtisadiyyat; pul və səyahət ailələri) ilə əlavə metriklər (mpg, G/mi kimi həcm/məsafə, $/G kimi vahid qiymət, xərc/məsafə, aylıq $, səfər milləri, növə görə səfər %)

![Vaxt əsaslı hesabatlar](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Vaxt əsaslı hesabatlar — sürüşdürülmüş göstəricilər](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

İqtisadiyyat riyaziyyatının təfərrüatları: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Doldurma tarixçəsi və Yanacaq Tarixçəsi

- **Hesabatlar → Tarixçəni doldurun** — hesabat filtrləri üçün xronoloji doldurmalar (**yalnız doldurur**; səfər başlamaz).

![Tarix hesabatını doldurun](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Yanacaq Tarixçəsi** (quruluşunuzun naviqasiyasında varsa) — hər bir avtomobil üçün inventar doldurma, həmçinin yalnız doldurma; redaktə etmək üçün sıraya toxunun.

### <a id="trip-miles"></a>Səyahət milləri

**Hesabatlar → Səyahət milləri** — növə görə millər, qrafiklər və xronoloji **səyahət başlanğıcı / seqment siyahısı**. Həmin cərgə üçün **Redaktə doldurmağı** açmaq üçün real başlanğıca toxunun.

![Səyahət milləri](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Doldurmağı redaktə edin

Doldurma tarixçəsi, Yanacaq Tarixçəsi və ya Səfər millərindən doldurma açın. Layout: avtomobil və odometr, **qiymətdən əvvəlki valyuta**, həcm, qeydlər. Səfər növü yalnız sıra səfər başlanğıcı olduqda görünür. Məkanın xülasəsi və **Məkan təfərrüatları** var. Bulud identifikasiyası ilə yerli fotoşəkil çatışmır: **Arxivdən şəkli əldə edin**.

![Doldurmağı redaktə et — Yerli şəkil olmadıqda arxivdən götür](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Digər kataloq kartlarına kateqoriya üzrə xərclər, nəqliyyat vasitələrinin xülasəsi və xərclər siyahısı daxildir.

Money təyin edildikdə hər bir cərgənin valyutasından istifadə edir. Qarışıq valyuta cəmləri **valyuta üzrə subcəmləri** göstərir (səssiz FX konvertasiyası yoxdur).

---

## <a id="syncing"></a>Sinxronizasiya

Menyu → **Sinxronizasiya** cədvəl və foto təyinatları üçün mərkəzdir (yalnız Parametrlər altında gizlənmir).

![Sinxronizasiya mərkəzi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Qısa statuslu **Cədvəl sinxronizasiyası** və **Foto ehtiyat nüsxəsi**, bu növ üçün **Sinxronizasiya** və təyinat siyahısına **›** üçün kartlar.
- **Sınaq bağlantısı** üçün təyinat yeri açın və **İndi sinxronlaşdırın (bu təyinat yeri)** / hamısı konfiqurasiya olunub.
- Uğursuzluq **Təfsilatlar** və başlıq çubuğundakı qırmızı **!** bura düşür.
- Addım-addım Google Cədvəl və Disk quraşdırması: [Yedəkləmələr və çoxlu cihaz sinxronizasiyası](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Parametrlər (yerli seçimlər)

Menyu → **Parametrlər**.

![Parametrlər](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Daha çox parametrlər (sürüşdürülmüş)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Təyinatlar üçün **Menyu → Sinxronizasiya** seçiminə üstünlük verin. Parametrlər hələ də eyni siyahıları açan xülasə sətirləri göstərə bilər.

### <a id="local-preferences-common"></a>Yerli tərcihlər (ümumi)

- **Yanacaq Qəbzi Fotolarına qənaət edin** / **Xərc Fotolarını Yerli Saxlayın** — şəkilləri cihazda saxlayın (Şəkillər icazəsi tələb oluna bilər).
- **Kapalı Səsi çalın**
- **Valyuta** / **Həcm vahidi** — proqram defoltları (sistem və ya açıq). Mövcud yanacaq məlumatları ilə həcm vahidinin dəyişdirilməsi konvertasiya dialoqunu təklif edə bilər.
- **Qaranlıq rejim**
- **Quraşdırma məsləhətləri** — ilk istifadə edilən avtomobili / sinxronizasiya dərsliklərini yenidən açın.
- **Debug Fill** / **Təcrübə ekranlarını göstər (dev)** — qabaqcıl; gündəlik istifadə üçün buraxın. Təcrübə ekranları burada sənədləşdirilməyib.

CSV **ixrac/idxal** (Avtomobillər / Xərclər / Yanacaq nişanlarının Poçt qutusu) cari quruluş tərəfindən təklif edildikdə Parametrlərdən əldə edilə bilər.

---

## <a id="help--about"></a>Kömək və Haqqında

![Kömək](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Haqqında](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Kömək** — cihazda sürətli başlanğıc, quraşdırma dərsləri, bu təlimata keçid, öz-özünə ev sahibi quraşdırma indeksi.
- **Haqqında** — versiya, lisenziyalar, GitHub, bu təlimat (çaplaşdıqda oflayn + onlayn HTML).

---

## <a id="related-docs"></a>Əlaqədar sənədlər

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — qısa gündəlik bələdçi
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — öz-özünə yerləşdirilən foto/cədvəl quraşdırma
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — birləşmə, bərpa, dublikatlar
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — iqtisadiyyat göstəriciləri təfərrüatı
