# <a id="vehicle-expenses-automated--user-manual"></a>Otomatik Araç Giderleri — Kullanım Kılavuzu

> **Kaynağı düzenle (Markdown).** Tarayıcılar ve uygulama içi okuyucu **oluşturulan HTML'yi** açar:
> - Web: [`docs/user-manual/index.html`](index.html) (`./scripts/render-user-manual.sh` ile yeniden oluştur)
> - Uygulama: Yardım / Hakkında → tam kılavuz (birlikte verilen HTML + ekran görüntüleri)
>
> Son kullanıcıları ham `.md` URL'lere yönlendirmeyin — tarayıcılar yalnızca düz metin gösterir.

**Bulut hesaplarınız** altında isteğe bağlı çoklu cihaz senkronizasyonu ve yedekleme ile yakıt dolumları ve araç masrafları için ilk kamera takibi.

Bu **tam kılavuzdur** (ekran görüntüleri + her adım). Telefondaki **Menü → Yardım** daha kısa bir başlangıç ​​kılavuzudur.

**Burada ele alınmıyor:** Eski Resimleri İçe Aktarma, Hizalama Deneyi ve Pompa Deneyi (geliştirici / gelişmiş araçlar).

---

## <a id="table-of-contents"></a>İçindekiler

1. [İhtiyacınız olan şey](#what-you-need)
2. [Bir bakışta simgeler](#icons-at-a-glance)
3. [Menüyü aç](#open-the-menu)
4. [İlk kurulum: Araçları Yönet](#first-time-setup-manage-vehicles)
5. [Yedeklemeler ve çoklu cihaz senkronizasyonu](#backups-and-multi-device-sync)
6. [Doldur (yakıt)](#fill-up-fuel)
7. [Yolculuğu başlat](#start-trip)
8. [Giderler](#expenses)
9. [Raporlar](#reports)
10. [Ayarlar (yerel tercihler)](#settings-local-preferences)
11. [Senkronizasyon](#syncing)
12. [Yardım ve Hakkında](#help--about)
13. [İlgili dokümanlar](#related-docs)

---

## <a id="what-you-need"></a>İhtiyacınız olan şey

- Android telefon veya tablet.
- En iyi OCR için: **pano kilometre sayacınızın** ve **pompa toplamlarının** net bir görünümü (veya sayıları elle yazın).
- İsteğe bağlı: e-tablo verileri ve/veya fotoğraf yedekleme için **kontrol ettiğiniz** hesaplar (bkz. [Yedeklemeler ve çoklu cihaz senkronizasyonu](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Bir bakışta simgeler

Bunlar ana ekranlarda görünür. Bunları bilmek avlanmanın büyük kısmını kurtarır.

| Nerede | Simge / kontrol | Ne işe yarar |
|----------|----------------|-----------------|
| Üst çubuk | **☰ Menü** (hamburger) | Gezinme çekmecesini açar |
| Üst çubuk | **ⓘ** (sayfa yardımı) | **Geçerli** sayfa için kısa yardım (varsa menünün yanında) |
| Üst çubuk | **`?N`** (sarı) | Bekleyen içe aktarma inceleme soruları — İçe aktarma incelemesini açar |
| Üst çubuk | **!** (kırmızı) | Yakın zamanda bir e-tablo veya fotoğraf hedefi başarısız oldu — düzeltmek için **Senkronizasyon**'u açın |
| Üst çubuk | **☰ + ←** | Çocukları rapor et ve Giderler listesi **menü ve arka kısmı** birlikte gösterir; Rapor merkezi yalnızca menüden oluşur |
| Ayarlar / yakıt düzenleme | **←** | Geri (ayarlar elektronik tablo/fotoğraf ve yakıt düzenleme geriye odaklı kalır) |
| Doldur | **Beyaz daire** (deklanşör) | OCR için kilometre sayacını veya pompa ekranını yakalayın |
| Doldur | **Disk / Kaydet** | Doldurmayı kaydedin (bir araca ve odo / hacim / maliyetten en az birine ihtiyaç duyar) |
| Doldur | **↕ oklar** (mod anahtarı) | **kilometre sayacı modu** ile **pompa (maliyet/hacim) modu** arasında geçiş yapın. Yeşil kenarlık etkin alan grubunu vurgular |
| Doldur | **↔ okları** (maliyet ve hacim arasında) | OCR yanlış alanlara koyarsa maliyet ve hacmi değiştirin |
| Doldur | **1x yakınlaştır /…** | Lens desteklediğinde kamera yakınlaştırma oranları |
| Doldur (yakaladıktan sonra) | **Yenile** ana düğmede | Önizlemeyi atın ve canlı kameraya dönün |
| Doldur (işlenirken) | **X** ana düğmede | Devam eden yakalamayı/OCR'yi iptal et |
| Gider | **Kaydet** | Masraflardan tasarruf edin |
| Gider | **Deklanşör dairesi** | Makbuz fotoğrafı çekin |
| Gider | **Galeri** | Kitaplıktan bir makbuz resmi seçin |
| Gider | **Yeniden çek** | Mevcut makbuz fotoğrafını silin ve tekrar çekim yapın |
| Gider / Araç Yönetimi | **+ / −** FAB'ler | Fotoğraf önizlemesini yakınlaştırın |
| Yer işaretleri iletişim kutusu | **OCR'yi düzenle** | Motorların gözden kaçırdığı önemli metni düzeltin veya ekleyin |
| Elektronik Tablo / Fotoğraf formları | **🔍 Ara** | Bir sayfa veya klasör için Google Drive'a göz atın (oturum açtıktan sonra) |

Maliyet alanlarındaki para birimi simgelerine ve hacim alanlarındaki **G/L**'ye dokunulabilir: söz konusu giriş için para birimini veya galon ve litreyi değiştirmek için küçük bir menü açın.

---

## <a id="open-the-menu"></a>Menüyü aç

1. Sol üstteki **☰** seçeneğine dokunun.
2. Bir sayfa seçin.

![Gezinme çekmecesi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Ana çekmece:** Doldur · Yolculuğu başlat · Araçları Yönet · Yeni gider · **Raporlar** · Ayarlar · Senkronizasyon · Yardım · Hakkında.

**Deneme çekmecesi** (Ayarlar → Deney ekranlarını göster): Hizalama Deneyi · Pompa Deneyi · **Eski Resimleri İçe Aktar**.

**Raporlar merkezi aracılığıyla (ana çekmece değil):** Gider listesi · Geçmişi doldur.

---

## <a id="first-time-setup-manage-vehicles"></a>İlk kurulum: Araçları Yönetme

OCR ve **otomatik araç eşleştirme**, her aracı bir **referans kontrol paneli fotoğrafı** ile kaydettikten, **Keşif**'i çalıştırdıktan, kilometre sayacını bir kürek tohumundan ayarladıktan ve bu çizgi için yer işaretlerini kaydettikten sonra en iyi şekilde çalışır.

### <a id="open-manage-vehicles"></a>Araçları Yönet'i Aç

Menü → **Araçları Yönet**. Bir araç seçin (veya **Yeni Araç Ekle**).

![Referans çizgisi, mahsuller ve keşifle Araçları yönetin](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Araç ekleyin veya düzenleyin

1. **Araç** açılır menüsünü açın → bir araç seçin veya **Yeni Araç Ekle**.
2. Net bir **referans çizgi fotoğrafı** çekin veya seçin (tam gösterge paneli, iyi aydınlatılmış, telefon kabaca kare şeklinde). **Fotoğraf Çek** veya **Galeri**'yi kullanın.
3. **Keşfi Çalıştır**'a dokunun — kürek algılama, fotoğrafa sarı **tohumları** yerleştirir (yer işaretleri daha önce olduğu gibi aynı `"ML Kit"` JSON'u kullanır).
4. **Odo Seç**'e dokunun, rakamlardaki tohuma dokunun, tutamaçları ayarlayın ve ardından **Kaydet**.
5. **Odo'yu Düzenle** zaten orada olan bir kutuyu ayarlar. **Odo'yu Temizle** onu kaldırır ve düğmede **Odo Seç** yazar.
6. **Yer İşaretlerini Göster** ile inceleyin. Yanlış okunanları düzeltmek veya gözden kaçan **eklemek** için **OCR'yi düzenle** seçeneğini kullanın.
7. **Araç Adını** (gerekli) ve marka/model/yıl/plakayı istediğiniz gibi doldurun.
8. **Araç Oluştur** veya **Değişiklikleri Kaydet**'e dokunun (yeni bir araç için ad + referans fotoğrafı gerektirir).

![Yeni araç form alanları ekle](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Yer işaretleri: Discovery'nin gözden kaçırdığı şeyleri düzeltin

**Yer İşaretlerini Göster**'in ardından listeyi kaydırın ve değerleri düzeltin. Motorlar bazen küçük rakamları kaçırır (örneğin, kümenin sağ alt köşesindeki **60** saati). Araç kimliğinin güvenilir kalması için **OCR'yi düzenle**'yi kullanarak bunları ekleyin veya düzeltin.

![Yer işaretleri iletişim kutusu — OCR'yi inceleyin ve düzenleyin](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Mükemmel bir fotoğraf olmadan yazmak

Uygulamayı, bir araç seçip Dolum kısmına kilometre sayacı, hacim ve maliyeti **yazarak** kullanmaya devam edebilirsiniz — OCR her alan için isteğe bağlıdır. Galeriyi içe aktarma, uygulama içinde çekim yapmamayı tercih ettiğinizde referans çizgi fotoğrafı için çalışır.

**İpucu:** Elektronik tablo senkronizasyonundan sonra araç tanımları (ekinler, yer işaretleri) yerel veritabanında yayınlanır; bunları kullanmak için Araçları Doldurma için Yönet'i yeniden açmanıza gerek yoktur.

---

## <a id="backups-and-multi-device-sync"></a>Yedeklemeler ve çoklu cihaz senkronizasyonu

Uygulama, **birden fazla telefon veya tabletin aynı filo verilerini paylaşabileceği** şekilde tasarlanmıştır ve böylece **verilerinizin ve fotoğraflarınızın bir kopyasını cihazdan** saklayabilirsiniz. Bu, **sizin** hesaplarınız veya **kendi başınıza** barındırılan sunucularınız altında yapılandırdığınız **hedefler** ile yapılır; diğer insanların görebileceği, şirket tarafından işletilen bir "Araç Giderleri bulutu" ile değil.

### <a id="what-runs-where"></a>Ne nerede çalışır

| tür | Neleri saklıyor | Tipik kullanım |
|------|----------------|----------------|
| **E-tablo / tablo senkronizasyonu** | Araçlar, yakıt dolumları, giderler (sıralar ve sekmeler) | Çoklu cihaz birleştirme + yapılandırılmış yedekleme |
| **Fotoğraf yedekleme** | İkili resimler (tire/pompa/makbuz/referans fotoğrafları) | Fotoğraf yedekleme + eksik dosyaları geri yükleme |

Her türden **birden fazla hedefi** yapılandırabilirsiniz (tür başına yumuşak sınır). Manuel **Şimdi senkronize et** ve **arka planda** çalışanlar etkin olanları çalıştırır.

### <a id="offline-first"></a>Önce çevrimdışı

- Doldurma, gider veya makbuz eklemek için **ağ gerekmez**. Her şey **öncelikle yerel olarak** kaydedilir.
- Ağ kullanılabilir olduğunda, senkronizasyon ve fotoğraf yedekleme **arka plan görevleri** olarak çalıştırılır (ayarladığınız bir programa göre ve **Şimdi senkronize et**'e dokunduğunuzda). Arızalar, Ayarlar satırlarının altında kırmızı metinle ve uygulama başlık çubuğunda **!** işaretiyle gösterilir.

### <a id="your-accounts-only"></a>Yalnızca hesaplarınız

Oturum açma ve jetonlar, seçtiğiniz sağlayıcılar (Google, Microsoft, S3 anahtarları, kendi kendine barındırılan URL'ler vb.) için cihazda kalır. Hedefler **kullanıcının tam kontrolü** altındadır (Google hesabınız, OneDrive'ınız, MinIO klasörünüz, EtherCalc ana bilgisayarınız vb.). Paylaşılan bir arka uç aracılığıyla diğer Araç Giderleri kullanıcılarıyla hiçbir şey paylaşılmaz.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Desteklenen hedefler — veriler (elektronik tablo / tablo halinde)

**Menü → Senkronizasyon → E-tablo senkronizasyonu** altında yapılandırılmıştır (Ayarlar özeti satırlarından da ulaşılabilir). Birinci sınıf seçici seçenekleri:

| Hedef | Notlar |
|--------|--------|
| **Google E-Tablolar** | Ortak varsayılan; Araçlar, Giderler ve araç başına yakıt sekmeleri |
| **Excel** | Graph / OneDrive tarzı bağlama yoluyla Microsoft çalışma kitabı |
| **EtherCalc** | Kendi kendine barındırılan ortak çalışmaya dayalı elektronik tablo odaları |
| **Diğer →** uygulanan arka uçlar | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Ertelendi / henüz başsız değil (Diğer altında listelenmiştir ancak tam olarak uygulanmamıştır): OnlyOffice, Collabora. Ayrıca bkz. [kendi kendine barındırılan dizin](reference/self-host/INDEX.md).

CSV **dışa aktarma/içe aktarma** (aynı sekme düzeninin ZIP'i), canlı senkronizasyondan bağımsız olarak, taşınabilir bir yedekleme olarak Ayarlar'dan edinilebilir.

### <a id="supported-targets--photos-image-backup"></a>Desteklenen hedefler — fotoğraflar (görüntü yedekleme)

**Menü → Senkronizasyon → Fotoğraf yedekleme** altında yapılandırılmıştır (ayrıca Ayarlar özeti satırlarından):

| Hedef | Notlar |
|--------|--------|
| **Google Drive** | Seçtiğiniz klasör (URL'ye göz atın veya yapıştırın) |
| **OneDrive** | Microsoft hesabı + yol öneki |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO ve diğer S3 uyumlu uç noktalar |
| **Diğer** | rclone destekli depolama (ör. WebDAV, SFTP ve uygulama içi seçicide bulunan diğer seçilmiş uzaktan kumandalar) |

Kendi kendine barındırılan fotoğraf ve tablolu hedefler için hile sayfaları ayarlayın: [kendi kendine barındırılan dizin](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Çoklu cihaz davranışı (kısa)

- **Güncellenen** zaman damgalarında **Son yazma-kazanımları** ile **Senkronizasyon Kimliği** ile satırlar birleştirilir.
- Silme işlemleri yumuşaktır; başka bir cihazdaki daha yeni bir düzenleme, bir satırı geri yükleyebilir.
- İki cihazda **aynı dolguyu iki kez** girmek **iki satır** oluşturur; fark ettiğinizde fazlalıkları silin.
- Daha fazla ayrıntı: [Davranış notlarını senkronize et](#sync-behavior-notes) ve [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Örnek: Google E-Tablolar'ı (veri) ekleyin

1. **Menü → Senkronizasyon → E-tablo senkronizasyonu** (veya Ayarlar → E-tablo senkronizasyonu).

![E-tablo senkronizasyon listesi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. **E-tablo hedefi ekle**'ye dokunun.

![Sağlayıcı seçici](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. **Google E-Tablolar**'ı seçin.

![Google E-Tablolar formu](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Google ile bir kez oturum açın** — bu oturum açma aynı zamanda göz atmanıza da olanak tanır. Görünen ad → bir **Sayfa URL'si** (hala geçerli) yapıştırın veya **🔍** göz atın. Gözat **Tümü ve paylaşılanlar** üzerinde açılır, böylece sahibi olduğunuz veya sizinle paylaşılan mevcut bir e-tabloyu seçebilirsiniz. Ardından seçenekleri planlayın → etkinleştirin → kaydedin.
5. Sekmeleri oluşturmak/güncellemek için **şimdi senkronize edin**: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Örnek: Google Drive'ı ekleyin (fotoğraflar)

1. **Menü → Senkronizasyon → Fotoğraf yedekleme** (veya Ayarlar → Fotoğraf yedekleme).

![Fotoğraf yedekleme listesi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. **Fotoğraf hedefi ekle**'ye dokunun.

![Fotoğraf sağlayıcı seçici](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. **Google Drive**'ı seçin.

![Google Drive formu](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Google ile oturum açın (Drive)** → isteğe bağlı klasör URL'si/göz at → etkinleştir → kaydet → **Şimdi senkronize et**.

Fotoğraflar için Manuel **Şimdi senkronize et** tam bir geçiştir; arka plan yedeklemesi genellikle **yalnızca beklemedeki** yüklemeleri belirli bir programa göre işler.

### <a id="sync-behavior-notes"></a>Senkronizasyon davranış notları

- Uygulama yükseltmesinden sonra kısaca **“Yükseltme sonrasında veritabanı güncelleniyor…”** (yerel senkronizasyon kimliği dolgusu) ifadesini görebilirsiniz.
- Bir senkronizasyon kesilirse, sonraki **başarılı** senkronizasyon uzak sekmeleri yeniden birleştirir ve onarır.
- Arızalar: Uygulama çubuğunda Senkronizasyon kartları + **!** üzerinde kırmızı özet.

---

## <a id="fill-up-fuel"></a>Doldur (yakıt)

Bu, uygulamayı açtığınızda **ana ekrandır**.

### <a id="vehicle-selection-usually-automatic"></a>Araç seçimi (genellikle otomatik)

Önce aracı seçmenize **gerek yok**. Araçları Yönet'te araçlar **yer işaretleri** ayarlandığında, Doldur kilometre sayacını yakaladıktan sonra gösterge tablosu görüntüsünden **hangi aracı** otomatik olarak algılar. Gerekirse geçersiz kılmak için **Araç** açılır menüsünü yine de açabilirsiniz.

### <a id="aim-at-the-odometer"></a>Kilometre sayacına nişan alın

Kilometre sayacı modunda kalın ve kümeyi çerçeveleyin. Talimat: * Kilometre sayacını hedefleyin. Yakalamak için deklanşöre dokunun.*

![Doldur — kilometre sayacında canlı kamera (önizlemedeki örnek çizgi görüntüsü)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Kilometre sayacı deklanşöründen sonra

OCR **Odo**'yu doldurur ve aracı yer işaretlerinden eşleştirmeye çalışır (gerekirse her ikisini de inceleyin). Yeniden çekim yapmak için ana düğme **Yeniden Dene** olur. Talimat okumayı özetler.

![Doldur — kilometre sayacı OCR sonucu](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Pompa modu (maliyet ve hacim)

1. Pompa moduna geçmek için **↕** öğesine dokunun: *Pompa ekranını hedefleyin (maliyet/hacim). Deklanşöre dokunun.*
2. Pompa toplamlarını yakalayın. Maliyet ve hacim alanları doldurulur; eğer yer değiştirmişlerse **↔** kullanın.
3. Para birimine veya gerekiyorsa **G/L**'ye dokunun, ardından **Kaydet**'e (disk) dokunun. Boş alanlar **kısmi doldurma** oluşturur (hala izin veriliyor).

![Doldur — pompa önizlemesi / toplamlar (önizlemedeki örnek pompa görüntüsü)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Bir sonraki durak için Doldur'da kalırsınız (kaydettikten sonra alanlar temizlenir). Tamamen **çevrimdışı** çalışın; senkronizasyon yapılandırıldığında arka planda daha sonra çalışır.

### <a id="manual-entry-no-camera--bad-ocr"></a>Manuel giriş (kamera yok / hatalı OCR)

1. **Odo**, **maliyet** veya **hacim**'e dokunun ve değerleri yazın (dikey sistem klavyesini, yatay ise ekran tuş takımını kullanır).
2. Otomatik algılama çalışmadıysa **Araç**'ı seçin veya onaylayın.
3. Yukarıdaki gibi kaydedin.

### <a id="modes-and-borders"></a>Modlar ve kenarlıklar

- Araç+odo etrafındaki **yeşil kenarlık** → kilometre sayacını yakalama/düzenleme.
- Maliyet+hacim → pompa modu etrafındaki **yeşil kenarlık**.
- **Kaydet**, bir araç seçilene ve odo/maliyet/hacim değerlerinden en az birinde veri bulunana ve OCR hala çalışmayana kadar devre dışı kalır.

Ekran ipucu (talimat satırının altında): *Deklanşör = yakalama · Disk = kaydetme · ↕ = odo/pompa modu · ↔ = maliyet/hacim değiştirme.*

---

## <a id="expenses"></a>Giderler

### <a id="new-expense"></a>Yeni gider

Menü → **Yeni gider**.

![Yeni gider formu ve kamera kontrolleri (boş olduğunda kamera bandındaki örnek çizgi görüntüsü)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Kaydet** (disk), **deklanşör** (makbuz fotoğrafı) veya **galeri** (resim seç).
2. **Tarih**, **Araç**, **Satıcı**, **Açıklama**, **Tutar** (para birimi sembolüne dokunulabilir), **Kategori**, isteğe bağlı **Kilometre Sayacı**'nı doldurun.
3. Çok sayfalı makbuzlar: Kullanıcı arayüzü sayfalama sunuyorsa ek sayfaları yakalayın (sayfa 0 birincil makbuzdur).
4. **Depolamaya kaydet** (öncelikle yerel; fotoğraf yedekleme ve e-tablo senkronizasyonu, yapılandırıldığında arka planda gerçekleşir).

### <a id="expense-list"></a>Gider listesi

Menü → **Raporlar** → **Gider listesi** — geçmiş yakıt dışı harcamalara göz atın; Düzenlenecek bir öğeyi açın.

![Gider listesi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Gideri düzenle

Listeden bir satır açın. Satıcıyı, miktarı, kategoriyi, aracı ve açıklamayı doğru yapın. Makbuz yalnızca fotoğraf yedeklemesindeyse (okunabilir yerel dosya yoksa), gösterildiğinde **Resmi arşivden getir** seçeneğini kullanın (yapılandırılmış fotoğraf hedeflerinde çalışır).

![Gideri düzenle](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Yolculuğa başla

Menü → **Yolculuğu başlat** (Çekmeceyi doldurduktan sonra). Kilometre sayacını yakalayın veya girin, yolculuk türünü seçin, **disk** simgesiyle kaydedin. **Durdur**, tutulan GPS konumundaki Kişisel için bir kısayoldur. Kontrol hatırlatıcıları için **ⓘ** kullanın.

![Yolculuğu başlat](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Yolculuk başlangıçları **Açma Türü** ile (normal dolumlar değil) yakıt sıraları olarak saklanır. Yakıt Geçmişi altında değil, **Raporlar → Yolculuk milleri** altında görünürler.

---

## <a id="reports"></a>Raporlar

Menü → **Raporlar** ürün merkezini açar (tüm zamanların özeti + katalog kartları). Bu, ürün raporlarının tek yüzeyidir; ayrı bir "Raporlar ve Grafikler" çekmecesi öğesi yoktur.

![Rapor merkezi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Araç modu (**Tümü / Her biri / Tekli**), dönem filtreleri, grafikler ve paylaşım (**METİN / CSV / PDF**) için bir kart açın. Rapor edilen çocukların üst çubuğu: **☰ + ←** (ve kayıtlı olduğunda **ⓘ**).

### <a id="time-based-reports"></a>Zamana dayalı raporlar

Ana harita kartı. **Pürüzsüz** kutular ve **bağımsız Y ölçekleri** (ekonomi solda; para ve seyahat aileleri sağda) ile isteğe bağlı ölçümler (mpg, G/mi gibi hacim/mesafe, $/G gibi birim fiyat, maliyet/mesafe, aylık $, yolculuk milleri, türe göre yolculuk yüzdesi).

![Zamana dayalı raporlar](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Zamana dayalı raporlar — kaydırılmış metrikler](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Ekonomi matematiğinin ayrıntıları: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Doldurma geçmişi ve Yakıt Geçmişi karşılaştırması

- **Raporlar → Doldurma geçmişi** — rapor filtreleri için kronolojik doldurmalar (**yalnızca doldurmalar**; yolculuk başlamaz).

![Geçmiş raporunu doldur](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Yakıt Geçmişi** (yapınızın navigasyonunda mevcutsa) — araç başına dolum envanteri, ayrıca yalnızca dolumlar; düzenlemek için bir satıra dokunun.

### <a id="trip-miles"></a>Yolculuk mili

**Raporlar → Yolculuk milleri** — türe göre miller, grafikler ve kronolojik **yolculuk başlangıç / bölüm listesi**. Söz konusu satır için **Dolguyu düzenle**'yi açmak için gerçek bir başlangıca dokunun.

![Yolculuk mili](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Doldurmayı düzenle

Dolum geçmişi, Yakıt Geçmişi veya Yolculuk millerinden bir dolum açın. Düzen: araç ve kilometre sayacı, **maliyet öncesi para birimi**, hacim, notlar. Yolculuk türü yalnızca satır bir yolculuk başlangıcı olduğunda görünür. Konumun bir özeti ve **Konum ayrıntıları** bulunur. Bulut kimliğine sahip yerel fotoğraf eksik: **Arşivden görseli getir**.

![Dolguyu düzenle — Yerel fotoğraf eksik olduğunda arşivden getir](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Diğer katalog kartlarında kategoriye göre harcamalar, araç özeti ve harcama listesi yer alır.

Para, ayarlandığında her satırın para birimini kullanır. Karışık para birimi toplamları **para birimi başına alt toplamları** gösterir (sessiz döviz dönüşümü yoktur).

---

## <a id="syncing"></a>Senkronizasyon

Menü → **Senkronizasyon** e-tablo ve fotoğraf hedefleri için merkezdir (yalnızca Ayarlar altında gömülü değildir).

![Senkronizasyon merkezi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Kısa durumlu **E-tablo senkronizasyonu** ve **Fotoğraf yedekleme** için kartlar, bu tür için **Senkronizasyon** ve hedef listesine **›**.
- **Bağlantıyı test et** ve **Şimdi senkronize et (bu hedef)** için bir hedef aç / tamamı yapılandırılmış.
- Başarısızlık **Ayrıntılar** ve başlık çubuğundaki kırmızı **!** buraya gelir.
- Adım adım Google E-Tablolar ve Drive kurulumu: [Yedeklemeler ve çoklu cihaz senkronizasyonu](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Ayarlar (yerel tercihler)

Menü → **Ayarlar**.

![Ayarlar](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Diğer ayarlar (kaydırılmış)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Hedefler için **Menü → Senkronizasyon** seçeneğini tercih edin. Ayarlar aynı listeleri açan özet satırlarını göstermeye devam edebilir.

### <a id="local-preferences-common"></a>Yerel tercihler (ortak)

- **Yakıt Makbuz Fotoğraflarını Kaydet** / **Harcama Fotoğraflarını Yerel Olarak Kaydet** — görüntüleri cihazda saklayın (Fotoğraf izni isteyebilir).
- **Deklanşör Sesini Çal**
- **Para birimi** / **Birim birimi** — uygulama varsayılanları (sistem veya açık). Hacim biriminin mevcut yakıt verileriyle değiştirilmesi bir dönüştürme iletişim kutusu sunabilir.
- **Karanlık mod**
- **Kurulum ipuçları** — ilk çalıştırmada araç / senkronizasyon eğitimlerini yeniden açın.
- **Hata Ayıklama Doldurma** / **Deneme ekranlarını göster (geliştirme)** — gelişmiş; günlük kullanıma bırakın. Deney ekranları burada belgelenmemiştir.

CSV **dışa aktarma/içe aktarma** (Araçların ZIP'i / Giderler / Yakıt sekmeleri), mevcut yapı tarafından sunulduğunda Ayarlar'dan edinilebilir.

---

## <a id="help--about"></a>Yardım ve Hakkında

![Yardım](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Hakkında](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Yardım** — cihaz üzerinde hızlı başlangıç, kurulum eğitimleri, bu kılavuza bağlantı, kendi kendine ana bilgisayar kurulum dizini.
- **Hakkında** — sürüm, lisanslar, GitHub, bu kılavuz (çevrimdışı paket halinde + yayınlandığında çevrimiçi HTML).

---

## <a id="related-docs"></a>İlgili dokümanlar

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — kısa günlük rehber
- [kendi kendine barındırılan/INDEX.md](../reference/self-host/INDEX.md) — kendi kendine barındırılan fotoğraf/tablo kurulumu
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — birleştirme, kurtarma, kopyalar
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — ekonomi ölçüm ayrıntıları
