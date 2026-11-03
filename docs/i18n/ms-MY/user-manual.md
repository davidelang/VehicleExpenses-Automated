# <a id="vehicle-expenses-automated--user-manual"></a>Perbelanjaan Kenderaan Automatik — Manual Pengguna

> **Edit sumber (Markdown).** Penyemak imbas dan pembaca dalam apl membuka **HTML yang diberikan**:
> - Web: [`docs/user-manual/index.html`](index.html) (jana semula dengan `./scripts/render-user-manual.sh`)
> - Apl: Bantuan / Perihal → manual penuh (HTML + tangkapan skrin disertakan)
>
> Jangan tunjuk pengguna akhir ke URL mentah `.md` — pelayar menunjukkan teks biasa sahaja.

Penjejakan kamera pertama untuk mengisi bahan api dan perbelanjaan kenderaan, dengan penyegerakan dan sandaran berbilang peranti pilihan di bawah akaun awan **anda**.

Ini ialah **manual penuh** (tangkapan skrin + setiap langkah). Pada telefon, **Menu → Bantuan** ialah panduan permulaan yang lebih pendek.

**Tidak dilindungi di sini:** Import Gambar Lama, Percubaan Penjajaran dan Percubaan Pam (pembangun / alatan lanjutan).

---

## <a id="table-of-contents"></a>Daftar kandungan

1. [Apa yang anda perlukan](#what-you-need)
2. [Ikon sepintas lalu](#icons-at-a-glance)
3. [Buka menu](#open-the-menu)
4. [Persediaan kali pertama: Urus Kenderaan](#first-time-setup-manage-vehicles)
5. [Sandaran dan penyegerakan berbilang peranti](#backups-and-multi-device-sync)
6. [Isi (bahan api)](#fill-up-fuel)
7. [Mulakan perjalanan](#start-trip)
8. [Perbelanjaan](#expenses)
9. [Laporan](#reports)
10. [Tetapan (pilihan tempatan)](#settings-local-preferences)
11. [Menyegerakkan](#syncing)
12. [Bantuan & Perihal](#help--about)
13. [Dokumen berkaitan](#related-docs)

---

## <a id="what-you-need"></a>Apa yang anda perlukan

- Telefon atau tablet Android.
- Untuk OCR terbaik: pandangan yang jelas tentang **odometer papan pemuka** dan **jumlah pam** anda (atau taip nombor dengan tangan).
- Pilihan: akaun **anda kawal** untuk data hamparan dan/atau sandaran foto (lihat [Sandaran dan penyegerakan berbilang peranti](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Ikon sepintas lalu

Ini muncul pada skrin utama. Mengetahui mereka menjimatkan banyak perburuan.

| Di mana | Ikon / kawalan | Apa yang dilakukannya |
|-------|----------------|--------------|
| Bar atas | **☰ Menu** (hamburger) | Membuka laci navigasi |
| Bar atas | **ⓘ** (bantuan halaman) | Bantuan ringkas untuk halaman **semasa** (di sebelah menu apabila tersedia) |
| Bar atas | **`?N`** (kuning) | Soalan semakan import belum selesai — membuka Semakan import |
| Bar atas | **!** (merah) | Hamparan atau destinasi foto gagal baru-baru ini — buka **Menyegerakkan** untuk membetulkan |
| Bar atas | **☰ + ←** | Laporkan kanak-kanak dan senarai Perbelanjaan menunjukkan **menu dan belakang** bersama; Hub laporan adalah menu sahaja |
| Tetapan / edit bahan api | **←** | Kembali (tetapan hamparan/foto dan suntingan bahan api kekal fokus semula) |
| Isi | **Bulatan putih** (pengatup) | Tangkap odometer atau paparan pam untuk OCR |
| Isi | **Cakera / Simpan** | Simpan isian (memerlukan kenderaan dan sekurang-kurangnya satu odo / volum / kos) |
| Isi | **↕ anak panah** (suis mod) | Togol **mod odometer** lwn **mod pam (kos/ volum)**. Sempadan hijau menyerlahkan kumpulan medan aktif |
| Isi | **↔ anak panah** (antara kos & volum) | Tukar kos dan volum jika OCR meletakkannya dalam medan yang salah |
| Isi | **Zum 1x / …** | Nisbah zum kamera apabila lensa menyokongnya |
| Isi (selepas tangkapan) | **Segar semula** pada butang utama | Buang pratonton dan kembali ke kamera langsung |
| Isi (semasa memproses) | **X** pada butang utama | Batalkan tangkapan sedang berjalan/OCR |
| Perbelanjaan | **Simpan** | Jimat perbelanjaan |
| Perbelanjaan | **Bulatan pengatup** | Ambil gambar resit |
| Perbelanjaan | **Galeri** | Pilih imej resit daripada perpustakaan |
| Perbelanjaan | **Ambil semula** | Kosongkan foto resit semasa dan tangkap semula |
| Perbelanjaan / Urus Kenderaan | **+ / −** FAB | Zum pratonton foto |
| Dialog tanda tempat | **Edit OCR** ​​| Betulkan atau tambah teks tanda tempat enjin terlepas |
| Borang Hamparan / Foto | **🔍 Cari** | Semak imbas Google Drive untuk helaian atau folder (selepas log masuk) |

Simbol mata wang pada medan kos dan **G/L** pada medan volum boleh diketik: buka menu kecil untuk menukar mata wang atau gelen lwn liter untuk entri tersebut.

---

## <a id="open-the-menu"></a>Buka menu

1. Ketik **☰** di bahagian atas sebelah kiri.
2. Pilih halaman.

![Laci navigasi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Laci utama:** Isi · Mulakan perjalanan · Urus Kenderaan · Perbelanjaan baharu · **Laporan** · Tetapan · Penyegerakan · Bantuan · Perihal.

**Laci percubaan** (Tetapan → Tunjukkan skrin percubaan): Eksperimen Penjajaran · Eksperimen Pam · **Import Gambar Lama**.

**Melalui hab Laporan (bukan laci utama):** Senarai perbelanjaan · Isi sejarah.

---

## <a id="first-time-setup-manage-vehicles"></a>Persediaan kali pertama: Urus Kenderaan

OCR dan **padanan kenderaan automatik** berfungsi paling baik selepas anda mendaftarkan setiap kenderaan dengan **foto papan pemuka rujukan**, jalankan **Discovery**, tetapkan pangkas odometer daripada biji dayung dan simpan tanda tempat untuk sengkang itu.

### <a id="open-manage-vehicles"></a>Buka Urus Kenderaan

Menu → **Urus Kenderaan**. Pilih kenderaan (atau **Tambah Kenderaan Baharu**).

![Urus Kenderaan dengan sengkang rujukan, tanaman dan penemuan](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Tambah atau edit kenderaan

1. Buka menu lungsur **Kenderaan** → pilih kenderaan atau **Tambah Kenderaan Baru**.
2. Tangkap atau pilih **foto sempang rujukan** yang jelas (kelompok instrumen penuh, terang dengan baik, telefon dihidupkan secara kasar). Gunakan **Ambil Foto** atau **Galeri**.
3. Ketik **Run Discovery** — pengesanan dayung meletakkan kuning **benih** pada foto (tanda tempat menggunakan `"ML Kit"` JSON yang sama seperti sebelumnya).
4. Ketik **Pilih Odo**, ketik benih pada digit, laraskan pemegang, kemudian **Simpan**.
5. **Edit Odo** melaraskan kotak yang sudah ada. **Kosongkan Odo** mengalih keluarnya, dan butang kemudian membaca **Pilih Odo**.
6. Semak dengan **Tunjukkan Mercu Tanda**. Gunakan **Edit OCR** ​​untuk membetulkan salah baca atau **menambah** teks yang terlepas.
7. Isikan **Nama Kenderaan** (diperlukan), tambah make/model/tahun/plat yang anda suka.
8. Ketik **Buat Kenderaan** atau **Simpan Perubahan** (memerlukan nama + foto rujukan untuk kenderaan baharu).

![Tambah medan borang kenderaan baharu](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Tanda tempat: betulkan apa yang Discovery terlepas

Selepas **Tunjukkan Mercu Tanda**, tatal senarai dan nilai yang betul. Enjin kadangkala terlepas angka kecil (contohnya jam **60** di sebelah kanan bawah gugusan). Gunakan **Edit OCR** ​​untuk menambah atau membetulkannya supaya identiti kenderaan kekal andal.

![Dialog tanda tempat — semak dan edit OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Menaip tanpa foto yang sempurna

Anda masih boleh menggunakan apl dengan memilih kenderaan dan **menaip** odometer, volum dan kos pada Isi — OCR adalah pilihan untuk setiap medan. Import galeri berfungsi untuk foto sempang rujukan apabila anda memilih untuk tidak menangkap dalam apl.

**Petua:** Selepas penyegerakan hamparan, takrif kenderaan (tanaman, tanda tempat) hidup dalam pangkalan data setempat — anda tidak perlu membuka semula Urus Kenderaan untuk Isi untuk menggunakannya.

---

## <a id="backups-and-multi-device-sync"></a>Sandaran dan penyegerakan berbilang peranti

Apl ini dibina supaya **beberapa telefon atau tablet boleh berkongsi data kumpulan yang sama** dan supaya anda boleh menyimpan **salinan data dan foto anda daripada peranti**. Itu dilakukan dengan destinasi **anda** mengkonfigurasi di bawah akaun **anda** atau **anda** yang dihoskan sendiri — bukan "awan Perbelanjaan Kenderaan" kendalian syarikat yang boleh dilihat oleh orang lain.

### <a id="what-runs-where"></a>Apa yang berjalan di mana

| Jenis | Apa yang disimpannya | Penggunaan biasa |
|------|----------------|------------|
| **Penyegerakan hamparan / jadual** | Kenderaan, isi minyak, perbelanjaan (baris dan tab) | Gabungan berbilang peranti + sandaran berstruktur |
| **Sandaran foto** | Imej binari (foto sempang/pam/resit/rujukan) | Sandaran foto + pulihkan fail yang hilang |

Anda boleh mengkonfigurasi **berbilang destinasi** bagi setiap jenis (soft cap setiap jenis). Manual **Segerakkan sekarang** dan **latar belakang** pekerja menjalankan yang didayakan.

### <a id="offline-first"></a>Di luar talian dahulu

- **Tiada rangkaian diperlukan** untuk menambah isian, perbelanjaan atau resit. Semuanya disimpan **secara tempatan dahulu**.
- Apabila rangkaian tersedia, penyegerakan dan sandaran foto dijalankan sebagai **tugas latar belakang** (pada jadual yang anda tetapkan dan apabila anda mengetik **Segerakkan sekarang**). Kegagalan ditunjukkan sebagai teks merah di bawah baris Tetapan dan **!** dalam bar tajuk apl.

### <a id="your-accounts-only"></a>Akaun anda sahaja

Log masuk dan token kekal pada peranti untuk pembekal yang anda pilih (kunci Google, Microsoft, S3, URL yang dihoskan sendiri dan sebagainya). Destinasi berada di bawah **kawalan penuh pengguna** — akaun Google anda, OneDrive anda, baldi MiniIO anda, hos EtherCalc anda, dsb. Tiada apa-apa yang dikongsi dengan pengguna Perbelanjaan Kenderaan lain melalui bahagian belakang yang dikongsi.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Sasaran yang disokong — data (hamparan / jadual)

Dikonfigurasikan di bawah **Menu → Menyegerak → Penyegerakan hamparan** (juga boleh dicapai daripada baris ringkasan Tetapan). Pilihan pemilih kelas pertama:

| Sasaran | Nota |
|--------|--------|
| **Helaian Google** | lalai biasa; tab untuk Kenderaan, Perbelanjaan dan bahan api setiap kenderaan |
| **Excel** | Buku kerja Microsoft melalui pengikatan gaya Graf / OneDrive |
| **EtherCalc** | Bilik hamparan kerjasama yang dihoskan sendiri |
| **Lain-lain →** hujung belakang yang dilaksanakan | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Ditunda / belum tanpa kepala lagi (disenaraikan di bawah Lain-lain tetapi tidak dilaksanakan sepenuhnya): OnlyOffice, Collabora. Lihat juga [indeks hos kendiri](reference/self-host/INDEX.md).

CSV **eksport/import** (ZIP reka letak tab yang sama) tersedia daripada Tetapan sebagai sandaran mudah alih, bebas daripada penyegerakan langsung.

### <a id="supported-targets--photos-image-backup"></a>Sasaran yang disokong — foto (sandaran imej)

Dikonfigurasikan di bawah **Menu → Menyegerak → Sandaran foto** (juga daripada baris ringkasan Tetapan):

| Sasaran | Nota |
|--------|--------|
| **Google Drive** | Folder yang anda pilih (semak imbas atau tampal URL) |
| **OneDrive** | Akaun Microsoft + awalan laluan |
| **S3** | AWS, Wasabi, Cloudflare R2, MiniIO dan titik akhir serasi S3 yang lain |
| **Lain-lain** | storan bersandarkan rclone (cth. WebDAV, SFTP dan alat kawalan jauh terpilih lain yang tersedia dalam pemilih dalam apl) |

Sediakan helaian tipu untuk foto yang dihoskan sendiri dan sasaran jadual: [indeks hos kendiri](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Gelagat berbilang peranti (pendek)

- Baris bergabung dengan **ID Penyegerakan** dengan **menang-tulis-terakhir** pada cap masa **Dikemas kini**.
- Pemadaman adalah lembut; pengeditan yang lebih baharu pada peranti lain boleh memulihkan baris.
- Memasukkan **isi yang sama dua kali** pada dua peranti menghasilkan **dua baris** — padamkan tambahan apabila anda perasan.
- Butiran lanjut: [Segerakkan nota tingkah laku](#sync-behavior-notes) dan [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Contoh: tambahkan Helaian Google (data)

1. **Menu → Penyegerakan → Penyegerakan hamparan** (atau Tetapan → Penyegerakan hamparan).

![Senarai penyegerakan hamparan](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Ketik **Tambah destinasi hamparan**.

![Pemilih pembekal](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Pilih **Helaian Google**.

![borang Helaian Google](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Log masuk dengan Google** sekali — log masuk itu juga membolehkan anda menyemak imbas. Nama paparan → tampal **URL Helaian** (masih sah) atau **🔍** semak imbas. Semak imbas dibuka pada **Semua & dikongsi** supaya anda boleh memilih hamparan sedia ada yang anda miliki atau yang dikongsi dengan anda. Kemudian jadualkan pilihan → dayakan → simpan.
5. **Segerakkan sekarang** sekali untuk membuat/kemas kini tab: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Contoh: tambah Google Drive (foto)

1. **Menu → Menyegerak → Sandaran foto** (atau Tetapan → Sandaran foto).

![Senarai sandaran foto](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Ketik **Tambah destinasi foto**.

![Pemilih penyedia foto](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Pilih **Google Drive**.

![Borang Google Drive](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Log masuk dengan Google (Drive)** → URL folder pilihan/semak imbas → dayakan → simpan → **Segerakkan sekarang**.

Manual **Segerakkan sekarang** untuk foto adalah lulus penuh; sandaran latar belakang biasanya memproses muat naik **tertunda sahaja** mengikut jadual.

### <a id="sync-behavior-notes"></a>Segerakkan nota tingkah laku

- Selepas naik taraf apl anda boleh melihat sebentar **“Mengemas kini pangkalan data selepas naik taraf…”** (isi semula id penyegerakan tempatan).
- Jika penyegerakan terganggu, penyegerakan **berjaya** seterusnya bergabung semula dan membaiki tab jauh.
- Kegagalan: ringkasan merah pada kad Sync + **!** dalam bar apl.

---

## <a id="fill-up-fuel"></a>Isi (bahan api)

Ini ialah **skrin utama** apabila anda membuka apl.

### <a id="vehicle-selection-usually-automatic"></a>Pemilihan kenderaan (biasanya automatik)

Anda **tidak** perlu memilih kenderaan dahulu. Apabila kenderaan mempunyai **tanda tempat** yang disediakan dalam Urus Kenderaan, Isikan **auto-kesan kenderaan mana** daripada imej sempang selepas anda menangkap odometer. Anda masih boleh membuka menu lungsur **Kenderaan** untuk mengatasi jika perlu.

### <a id="aim-at-the-odometer"></a>Tuju pada odometer

Kekal dalam mod odometer dan bingkai gugusan. Arahan: *Tuju pada odometer. Ketik pengatup untuk menangkap.*

![Isi — kamera langsung pada odometer (contoh imej sempang dalam pratonton)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Selepas pengatup odometer

OCR mengisi **Odo** dan cuba memadankan kenderaan dari tanda tempat (semak kedua-duanya jika perlu). Butang utama menjadi **Cuba Semula** untuk menangkap semula. Arahan meringkaskan bacaan.

![Isi — keputusan OCR odometer](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Mod pam (kos dan volum)

1. Ketik **↕** untuk bertukar kepada mod pam: *Tuju pada paparan pam (kos/ volum). Ketik pengatup.*
2. Tangkap jumlah pam. Isi medan kos dan volum; gunakan **↔** jika ia ditukar.
3. Ketik mata wang atau **G/L** jika perlu, kemudian **Simpan** (cakera). Medan kosong membuat **isi separa** (masih dibenarkan).

![Isi — pratonton pam / jumlah (contoh imej pam dalam pratonton)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Anda kekal di Isi untuk perhentian seterusnya (medan kosong selepas simpan). Bekerja sepenuhnya **luar talian**; penyegerakan berjalan kemudian di latar belakang apabila dikonfigurasikan.

### <a id="manual-entry-no-camera--bad-ocr"></a>Entri manual (tiada kamera / OCR teruk)

1. Ketik **Odo**, **kos**, atau **volume** dan taip nilai (potret menggunakan papan kekunci sistem; landskap menggunakan pad kekunci pada skrin).
2. Pilih atau sahkan **Kenderaan** jika pengesanan automatik tidak berjalan.
3. Simpan seperti di atas.

### <a id="modes-and-borders"></a>Mod dan sempadan

- **Sempadan hijau** di sekeliling kenderaan+odo → menangkap/mengedit odometer.
- **Sempadan hijau** sekitar kos+isipadu → mod pam.
- **Save** kekal dilumpuhkan sehingga kenderaan dipilih dan sekurang-kurangnya satu daripada odo/kos/volume mempunyai data dan OCR tidak masih berjalan.

Petua pada skrin (di bawah baris arahan): *Pengatup = tangkap · Cakera = simpan · ↕ = mod odo/pam · ↔ = kos tukar/volum.*

---

## <a id="expenses"></a>Perbelanjaan

### <a id="new-expense"></a>Perbelanjaan baharu

Menu → **Perbelanjaan baharu**.

![Borang perbelanjaan baharu dan kawalan kamera (contoh imej sempang dalam jalur kamera apabila kosong)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Simpan** (cakera), **pengatup** (foto resit), atau **galeri** (pilih imej).
2. Isikan **Tarikh**, **Kenderaan**, **Penjual**, **Penerangan**, **Jumlah** (simbol mata wang boleh diketuk), **Kategori**, pilihan **Odometer**.
3. Resit berbilang halaman: tangkap halaman tambahan jika UI menawarkan halaman (halaman 0 ialah resit utama).
4. **Simpan** untuk disimpan (tempatan dahulu; sandaran foto dan penyegerakan hamparan berlaku di latar belakang apabila dikonfigurasikan).

### <a id="expense-list"></a>Senarai perbelanjaan

Menu → **Laporan** → **Senarai perbelanjaan** — semak imbas perbelanjaan bukan bahan api yang lalu; buka item untuk diedit.

![Senarai perbelanjaan](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Sunting perbelanjaan

Buka baris daripada senarai. Vendor, amaun, kategori, kenderaan dan perihalan yang betul. Jika resit hanya dalam sandaran foto (tiada fail tempatan yang boleh dibaca), gunakan **Ambil imej daripada arkib** apabila ditunjukkan (berfungsi merentas destinasi foto yang dikonfigurasikan).

![Sunting perbelanjaan](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Mulakan perjalanan

Menu → **Mulakan perjalanan** (selepas Isi dalam laci). Tangkap atau masukkan odometer, pilih jenis perjalanan, simpan dengan ikon **cakera**. **Berhenti** ialah jalan pintas untuk Peribadi sekarang di lokasi GPS yang dipegang. Gunakan **ⓘ** untuk peringatan kawalan.

![Mulakan perjalanan](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Permulaan perjalanan disimpan sebagai baris bahan api dengan **Jenis Perjalanan** (bukan isian biasa). Ia muncul di bawah **Laporan → Batu perjalanan**, bukan di bawah Sejarah Bahan Api.

---

## <a id="reports"></a>Laporan

Menu → **Laporan** membuka hab produk (ringkasan sepanjang masa + kad katalog). Ini adalah satu-satunya permukaan laporan produk — tiada item laci "Laporan & Carta" yang berasingan.

![Hab laporan](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Buka kad untuk mod kenderaan (**Semua / Setiap / Tunggal**), penapis tempoh, carta dan kongsi (**TEKS / CSV / PDF**). Bar teratas pada laporan kanak-kanak: **☰ + ←** (dan **ⓘ** apabila didaftarkan).

### <a id="time-based-reports"></a>Laporan berasaskan masa

Kad carta utama. Metrik pilihan (mpg, volum/jarak seperti G/bt, harga unit seperti $/G, kos/jarak, $ bulanan, batu perjalanan, % perjalanan mengikut jenis) dengan tong **Smooth** dan **skala Y bebas** (kiri ekonomi; keluarga wang dan perjalanan di sebelah kanan).

![Laporan berasaskan masa](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Laporan berasaskan masa — metrik tatal](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Butiran matematik ekonomi: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Isi sejarah lwn Sejarah Bahan Api

- **Laporan → Sejarah isian** — pengisian kronologi untuk penapis laporan (**isi sahaja**; tiada perjalanan dimulakan).

![Isi laporan sejarah](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Sejarah Bahan Api** (jika ada dalam navigasi binaan anda) — inventori isian setiap kenderaan, juga diisi sahaja; ketik satu baris untuk mengedit.

### <a id="trip-miles"></a>Perjalanan batu

**Laporan → Batu perjalanan** — batu mengikut jenis, carta dan kronologi **senarai permulaan perjalanan / segmen**. Ketik permulaan sebenar untuk membuka **Edit isi** bagi baris itu.

![Perjalanan batu](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Edit isian

Daripada sejarah Isian, Sejarah Bahan Api atau Batu Perjalanan, buka isian. Susun atur: kenderaan dan odometer, **mata wang sebelum kos**, volum, nota. Jenis perjalanan muncul hanya apabila baris adalah permulaan perjalanan. Lokasi mempunyai ringkasan ditambah **Butiran lokasi**. Tiada foto tempatan dengan identiti awan: **Ambil imej daripada arkib**.

![Edit isian — Ambil daripada arkib apabila foto tempatan tiada](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Kad katalog lain termasuk perbelanjaan mengikut kategori, ringkasan kenderaan dan senarai perbelanjaan.

Wang menggunakan mata wang setiap baris apabila ditetapkan. Jumlah mata wang campuran menunjukkan **jumlah kecil setiap mata wang** (tiada penukaran FX senyap).

---

## <a id="syncing"></a>Menyegerakkan

Menu → **Menyegerakkan** ialah hab untuk hamparan dan destinasi foto (bukan sahaja terkubur di bawah Tetapan).

![Hab penyegerakan](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Kad untuk **Penyegerakan hamparan** dan **Sandaran foto** dengan status pendek, **Penyegerakan** untuk jenis itu dan **›** ke dalam senarai destinasi.
- Buka destinasi untuk **Uji sambungan** dan **Segerakkan sekarang (destinasi ini)** / semua dikonfigurasikan.
- Kegagalan **Butiran** dan **!** merah dalam bar hakmilik tiba di sini.
- Persediaan Helaian Google dan Drive langkah demi langkah: [Sandaran dan penyegerakan berbilang peranti](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Tetapan (pilihan tempatan)

Menu → **Tetapan**.

![Tetapan](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Lagi tetapan (menatal)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Untuk destinasi, pilih **Menu → Menyegerak**. Tetapan mungkin masih menunjukkan baris ringkasan yang membuka senarai yang sama.

### <a id="local-preferences-common"></a>Pilihan tempatan (biasa)

- **Simpan Foto Resit Bahan Api** / **Simpan Foto Perbelanjaan Setempat** — simpan imej pada peranti (boleh meminta kebenaran Foto).
- **Mainkan Bunyi Pengatup**
- **Mata Wang** / **Unit volum** — lalai apl (sistem atau eksplisit). Menukar unit volum dengan data bahan api sedia ada mungkin menawarkan dialog penukaran.
- **Mod gelap**
- **Petua persediaan** — buka semula tutorial kenderaan / penyegerakan pertama.
- **Isi Nyahpepijat** / **Tunjukkan skrin percubaan (dev)** — lanjutan; biarkan untuk kegunaan harian. Skrin percubaan tidak didokumenkan di sini.

CSV **eksport/import** (ZIP tab Kenderaan / Perbelanjaan / Bahan api) tersedia daripada Tetapan apabila ditawarkan oleh binaan semasa.

---

## <a id="help--about"></a>Bantuan & Perihal

![Bantuan](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Mengenai](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Bantuan** — permulaan pantas pada peranti, tutorial persediaan, pautan ke manual ini, indeks persediaan hos kendiri.
- **Mengenai** — versi, lesen, GitHub, manual ini (digabungkan di luar talian + HTML dalam talian apabila diterbitkan).

---

## <a id="related-docs"></a>Dokumen berkaitan

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — panduan ringkas setiap hari
- [hos kendiri/INDEX.md](../reference/self-host/INDEX.md) — persediaan foto/jadual hos sendiri
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — cantum, pemulihan, pendua
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — butiran metrik ekonomi
