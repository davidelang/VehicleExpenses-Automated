# <a id="vehicle-expenses-automated--user-manual"></a>Beban Kendaraan Otomatis — Panduan Pengguna

> **Edit sumber (Penurunan harga).** Browser dan pembaca dalam aplikasi membuka **HTML yang dirender**:
> - Web: [`docs/user-manual/index.html`](index.html) (dibuat ulang dengan `./scripts/render-user-manual.sh`)
> - Aplikasi: Bantuan / Tentang → manual lengkap (paket tangkapan layar HTML +)
>
> Jangan arahkan pengguna akhir ke URL mentah `.md` — browser hanya menampilkan teks biasa.

Pelacakan yang mengutamakan kamera untuk pengisian bahan bakar dan pengeluaran kendaraan, dengan sinkronisasi multi-perangkat opsional dan pencadangan di akun cloud **Anda**.

Ini adalah **panduan lengkap** (tangkapan layar + setiap langkah). Di telepon, **Menu → Bantuan** adalah panduan singkat untuk memulai.

**Tidak dibahas di sini:** Impor Gambar Lama, Eksperimen Penyelarasan, dan Eksperimen Pompa (pengembang / alat lanjutan).

---

## <a id="table-of-contents"></a>Daftar isi

1. [Apa yang Anda butuhkan](#what-you-need)
2. [Sekilas tentang ikon](#icons-at-a-glance)
3. [Buka menu](#open-the-menu)
4. [Pengaturan pertama kali: Kelola Kendaraan](#first-time-setup-manage-vehicles)
5. [Pencadangan dan sinkronisasi multi-perangkat](#backups-and-multi-device-sync)
6. [Isi (bahan bakar)](#fill-up-fuel)
7. [Mulai perjalanan](#start-trip)
8. [Beban](#expenses)
9. [Laporan](#reports)
10. [Pengaturan (preferensi lokal)](#settings-local-preferences)
11. [Sinkronisasi](#syncing)
12. [Bantuan & Tentang](#help--about)
13. [Dokumen terkait](#related-docs)

---

## <a id="what-you-need"></a>Apa yang Anda butuhkan

- Ponsel atau tablet Android.
- Untuk OCR terbaik: tampilan jelas **odometer dasbor** dan **total pompa** Anda (atau ketik angkanya dengan tangan).
- Opsional: akun **yang Anda kendalikan** untuk data spreadsheet dan/atau cadangan foto (lihat [Pencadangan dan sinkronisasi multi-perangkat](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Ikon sekilas

Ini muncul di layar utama. Mengetahui mereka menghemat banyak perburuan.

| Dimana | Ikon / kontrol | Apa fungsinya |
|-------|----------------|--------------|
| Bilah atas | **☰ Menu** (hamburger) | Membuka panel samping navigasi |
| Bilah atas | **ⓘ** (bantuan halaman) | Bantuan singkat untuk halaman **saat ini** (di sebelah menu bila tersedia) |
| Bilah atas | **`?N`** (kuning) | Pertanyaan tinjauan impor yang tertunda — membuka Tinjauan impor |
| Bilah atas | **!** (merah) | Tujuan spreadsheet atau foto baru-baru ini gagal — buka **Sinkronisasi** untuk memperbaiki |
| Bilah atas | **☰ + ←** | Laporkan anak-anak dan daftar Pengeluaran menunjukkan **menu dan kembali** secara bersamaan; Hub laporan hanya ada di menu |
| Setting / edit bahan bakar | **←** | Kembali (pengaturan spreadsheet/foto dan edit bahan bakar tetap fokus ke belakang) |
| Isi | **Lingkaran putih** (rana) | Tangkap odometer atau tampilan pompa untuk OCR |
| Isi | **Disk / Simpan** | Hemat pengisian (membutuhkan kendaraan dan minimal satu odo/volume/biaya) |
| Isi | **↕ panah** (pengalih mode) | Alihkan **mode odometer** vs **mode pompa (biaya/volume)**. Batas hijau menyorot grup bidang aktif |
| Isi | **↔ panah** (antara biaya & volume) | Tukar biaya dan volume jika OCR memasukkannya ke kolom yang salah |
| Isi | **Perbesar 1x / …** | Rasio zoom kamera bila lensa mendukungnya |
| Isi (setelah pengambilan) | **Segarkan** pada tombol utama | Buang pratinjau dan kembali ke kamera langsung |
| Isi (saat memproses) | **X** pada tombol utama | Batalkan pengambilan yang sedang berlangsung/OCR |
| Biaya | **Simpan** | Hemat biaya |
| Biaya | **Lingkaran rana** | Ambil foto tanda terima |
| Biaya | **Galeri** | Pilih gambar tanda terima dari perpustakaan |
| Biaya | **Mengambil kembali** | Hapus foto tanda terima saat ini dan potret lagi |
| Pengeluaran / Kelola Kendaraan | **+ / −** FAB | Perbesar pratinjau foto |
| Dialog landmark | **Edit OCR** ​​| Perbaiki atau tambahkan teks landmark yang terlewatkan oleh mesin |
| Formulir Spreadsheet / Foto | **🔍 Telusuri** | Jelajahi Google Drive untuk mencari sheet atau folder (setelah masuk) |

Simbol mata uang pada kolom biaya dan **G/L** pada kolom volume dapat diketuk: buka menu kecil untuk mengubah mata uang atau galon vs liter untuk entri tersebut.

---

## <a id="open-the-menu"></a>Buka menunya

1. Ketuk **☰** di kiri atas.
2. Pilih halaman.

![Laci navigasi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Laci utama:** Isi · Mulai perjalanan · Kelola Kendaraan · Pengeluaran baru · **Laporan** · Pengaturan · Sinkronisasi · Bantuan · Tentang.

**Laci eksperimen** (Pengaturan → Tampilkan layar eksperimen): Eksperimen Penyelarasan · Eksperimen Pompa · **Impor Gambar Lama**.

**Melalui hub Laporan (bukan laci utama):** Daftar pengeluaran · Isi riwayat.

---

## <a id="first-time-setup-manage-vehicles"></a>Pengaturan pertama kali: Kelola Kendaraan

OCR dan **pencocokan kendaraan otomatis** berfungsi paling baik setelah Anda mendaftarkan setiap kendaraan dengan **foto referensi dasbor**, menjalankan **Discovery**, menyetel potongan odometer dari benih dayung, dan menyimpan landmark untuk dasbor tersebut.

### <a id="open-manage-vehicles"></a>Buka Kelola Kendaraan

Menu → **Kelola Kendaraan**. Pilih kendaraan (atau **Tambahkan Kendaraan Baru**).

![Kelola Kendaraan dengan dasbor referensi, tanaman, dan penemuan](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Tambahkan atau edit kendaraan

1. Buka dropdown **Kendaraan** → pilih kendaraan atau **Tambahkan Kendaraan Baru**.
2. Ambil atau pilih **foto dasbor referensi** yang jelas (kluster instrumen lengkap, penerangan cukup, ponsel dalam posisi persegi). Gunakan **Ambil Foto** atau **Galeri**.
3. Ketuk **Jalankan Penemuan** — deteksi dayung menempatkan **biji** berwarna kuning pada foto (tengara menggunakan `"ML Kit"` JSON yang sama seperti sebelumnya).
4. Ketuk **Pilih Odo**, ketuk benih pada angka, sesuaikan pegangannya, lalu **Simpan**.
5. **Edit Odo** menyesuaikan kotak yang sudah ada. **Clear Odo** menghapusnya, dan tombol kemudian bertuliskan **Select Odo**.
6. Tinjau dengan **Tampilkan Tengara**. Gunakan **Edit OCR** ​​untuk memperbaiki kesalahan membaca atau **menambahkan** teks yang terlewat.
7. Isi **Nama Kendaraan** (wajib diisi), ditambah merek/model/tahun/pelat sesuai keinginan.
8. Ketuk **Buat Kendaraan** atau **Simpan Perubahan** (membutuhkan nama + foto referensi untuk kendaraan baru).

![Tambahkan kolom formulir kendaraan baru](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Tanda: perbaiki apa yang terlewatkan oleh Discovery

Setelah **Tampilkan Tengara**, gulir daftar dan perbaiki nilainya. Mesin terkadang melewatkan angka kecil (misalnya jam **60** di kanan bawah cluster). Gunakan **Edit OCR** ​​untuk menambah atau memperbaikinya sehingga identitas kendaraan tetap dapat diandalkan.

![Dialog landmark — meninjau dan mengedit OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Mengetik tanpa foto yang sempurna

Anda masih dapat menggunakan aplikasi dengan memilih kendaraan dan **mengetik** odometer, volume, dan biaya pada Isi — OCR bersifat opsional untuk setiap bidang. Impor galeri berfungsi untuk foto dasbor referensi saat Anda memilih untuk tidak memotret dalam aplikasi.

**Kiat:** Setelah sinkronisasi spreadsheet, definisi kendaraan (tanaman, landmark) ada di database lokal — Anda tidak perlu membuka kembali Kelola Kendaraan untuk Diisi untuk menggunakannya.

---

## <a id="backups-and-multi-device-sync"></a>Cadangan dan sinkronisasi multi-perangkat

Aplikasi ini dibuat agar **beberapa ponsel atau tablet dapat berbagi data armada yang sama**, sehingga Anda dapat menyimpan **salinan data dan foto Anda di luar perangkat**. Hal ini dilakukan dengan tujuan yang **Anda** konfigurasikan di bawah akun **Anda** atau server **Anda** yang dihosting sendiri — bukan “cloud Pengeluaran Kendaraan” yang dikelola perusahaan yang dapat dilihat orang lain.

### <a id="what-runs-where"></a>Apa yang berjalan di mana

| Jenis | Apa yang disimpannya | Penggunaan khas |
|------|----------------|-------------|
| **Sinkronisasi spreadsheet/tabel** | Kendaraan, pengisian bahan bakar, pengeluaran (baris dan tab) | Penggabungan multi-perangkat + pencadangan terstruktur |
| **Cadangan foto** | Gambar biner (dasbor/pompa/kwitansi/foto referensi) | Cadangan foto + pulihkan file yang hilang |

Anda dapat mengonfigurasi **beberapa tujuan** dari setiap jenis (soft cap per jenis). Pekerja manual **Sinkronkan sekarang** dan **latar belakang** menjalankan pekerja yang diaktifkan.

### <a id="offline-first"></a>Offline dulu

- **Tidak diperlukan jaringan** untuk menambahkan pengisian, pengeluaran, atau tanda terima. Semuanya disimpan **secara lokal terlebih dahulu**.
- Saat jaringan tersedia, sinkronisasi dan pencadangan foto dijalankan sebagai **tugas latar belakang** (sesuai jadwal yang Anda atur, dan saat Anda mengetuk **Sinkronkan sekarang**). Kegagalan ditampilkan sebagai teks merah di bawah baris Pengaturan dan **!** di bilah judul aplikasi.

### <a id="your-accounts-only"></a>Akun Anda saja

Proses masuk dan token tetap ada di perangkat untuk penyedia yang Anda pilih (Google, Microsoft, kunci S3, URL yang dihosting sendiri, dan sebagainya). Tujuan berada di bawah **kontrol penuh pengguna** — akun Google Anda, OneDrive Anda, bucket MinIO Anda, host EtherCalc Anda, dll. Tidak ada yang dibagikan dengan pengguna Pengeluaran Kendaraan lainnya melalui backend bersama.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Target yang didukung — data (spreadsheet / tabel)

Dikonfigurasi dalam **Menu → Sinkronisasi → Sinkronisasi spreadsheet** (juga dapat dijangkau dari baris ringkasan Pengaturan). Opsi pemilih kelas satu:

| Sasaran | Catatan |
|--------|--------|
| **Google Spreadsheet** | Standar umum; tab untuk Kendaraan, Pengeluaran, dan bahan bakar per kendaraan |
| **Unggul** | Buku kerja Microsoft melalui pengikatan gaya Grafik / OneDrive |
| **EtherCalc** | Ruang spreadsheet kolaboratif yang dihosting sendiri |
| **Lainnya →** mengimplementasikan backend | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Ditangguhkan / belum tanpa kepala (tercantum di bawah Lainnya tetapi belum diterapkan sepenuhnya): OnlyOffice, Collabora. Lihat juga [indeks host mandiri](reference/self-host/INDEX.md).

CSV **ekspor/impor** (ZIP dengan tata letak tab yang sama) tersedia dari Pengaturan sebagai cadangan portabel, tidak bergantung pada sinkronisasi langsung.

### <a id="supported-targets--photos-image-backup"></a>Target yang didukung — foto (cadangan gambar)

Dikonfigurasi pada **Menu → Sinkronisasi → Pencadangan foto** (juga dari baris ringkasan Pengaturan):

| Sasaran | Catatan |
|--------|--------|
| **Google Drive** | Folder yang Anda pilih (jelajahi atau tempel URL) |
| **OneDrive** | Akun Microsoft + awalan jalur |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO, dan titik akhir lain yang kompatibel dengan S3 |
| **Lainnya** | penyimpanan yang didukung rclone (misalnya WebDAV, SFTP, dan remote pilihan lainnya yang tersedia di pemilih dalam aplikasi) |

Siapkan lembar contekan untuk foto yang dihosting sendiri dan target tabel: [indeks host mandiri](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Perilaku multi-perangkat (pendek)

- Baris digabungkan berdasarkan **Sync ID** dengan **last-write-wins** pada stempel waktu **Diperbarui**.
- Penghapusannya lembut; pengeditan yang lebih baru di perangkat lain dapat memulihkan baris.
- Memasukkan **isian yang sama dua kali** pada dua perangkat akan menghasilkan **dua baris** — hapus baris tambahan saat Anda menyadarinya.
- Lebih detail: [Sinkronkan catatan perilaku](#sync-behavior-notes) dan [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Contoh: tambahkan Google Spreadsheet (data)

1. **Menu → Sinkronisasi → Sinkronisasi spreadsheet** (atau Pengaturan → Sinkronisasi spreadsheet).

![Daftar sinkronisasi spreadsheet](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Ketuk **Tambahkan tujuan spreadsheet**.

![Pemilih penyedia](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Pilih **Google Spreadsheet**.

![Formulir Google Spreadsheet](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Masuk dengan Google** sekali — dengan masuk itu juga Anda dapat menjelajah. Nama tampilan → tempel **URL Sheet** (masih valid) atau **🔍** jelajahi. Telusuri terbuka di **Semua & dibagikan** sehingga Anda dapat memilih spreadsheet yang Anda miliki atau yang dibagikan dengan Anda. Kemudian jadwalkan opsi → aktifkan → simpan.
5. **Sinkronkan sekarang** sekali untuk membuat/memperbarui tab: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Contoh: tambahkan Google Drive (foto)

1. **Menu → Sinkronisasi → Pencadangan foto** (atau Pengaturan → Pencadangan foto).

![Daftar cadangan foto](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Ketuk **Tambahkan tujuan foto**.

![Pemilih penyedia foto](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Pilih **Google Drive**.

![Formulir Google Drive](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Masuk dengan Google (Drive)** → URL/jelajah folder opsional → aktifkan → simpan → **Sinkronkan sekarang**.

Manual **Sinkronisasi sekarang** untuk foto sudah bisa dilakukan; pencadangan latar belakang biasanya memproses unggahan **hanya tertunda** sesuai jadwal.

### <a id="sync-behavior-notes"></a>Sinkronkan catatan perilaku

- Setelah pemutakhiran aplikasi, Anda mungkin melihat sekilas **“Memperbarui basis data setelah pemutakhiran…”** (pengisian ulang id sinkronisasi lokal).
- Jika sinkronisasi terganggu, sinkronisasi **berhasil** berikutnya akan digabungkan kembali dan memperbaiki tab jarak jauh.
- Kegagalan: ringkasan merah pada kartu Sinkronisasi + **!** di bilah aplikasi.

---

## <a id="fill-up-fuel"></a>Isi (bahan bakar)

Ini adalah **layar utama** saat Anda membuka aplikasi.

### <a id="vehicle-selection-usually-automatic"></a>Pemilihan kendaraan (biasanya otomatis)

Anda **tidak** perlu memilih kendaraan terlebih dahulu. Saat kendaraan telah menyiapkan **tengara** di Kelola Kendaraan, Isi **deteksi otomatis kendaraan mana** dari gambar dasbor setelah Anda mengambil odometer. Anda masih dapat membuka dropdown **Kendaraan** untuk menggantinya jika diperlukan.

### <a id="aim-at-the-odometer"></a>Arahkan ke odometer

Tetap dalam mode odometer dan bingkai cluster. Petunjuk: *Arahkan ke odometer. Ketuk rana untuk mengambil gambar.*

![Isi — kamera langsung di odometer (contoh gambar dasbor di pratinjau)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Setelah penutup odometer

OCR mengisi **Odo** dan mencoba mencocokkan kendaraan dari landmark (tinjau keduanya jika diperlukan). Tombol utama menjadi **Coba lagi** untuk memotret ulang. Instruksi merangkum bacaan.

![Isi — hasil OCR odometer](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Mode pompa (biaya dan volume)

1. Ketuk **↕** untuk beralih ke mode pompa: *Arahkan ke tampilan pompa (biaya/volume). Ketuk rana.*
2. Tangkap total pompa. Isi kolom biaya dan volume; gunakan **↔** jika ditukar.
3. Ketuk mata uang atau **G/L** jika diperlukan, lalu **Simpan** (disk). Bidang yang kosong akan diisi **sebagian** (masih diperbolehkan).

![Isi — pratinjau pompa / total (contoh gambar pompa dalam pratinjau)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Anda tetap mengisi untuk perhentian berikutnya (bidang kosong setelah disimpan). Bekerja sepenuhnya **offline**; sinkronisasi berjalan nanti di latar belakang saat dikonfigurasi.

### <a id="manual-entry-no-camera--bad-ocr"></a>Entri manual (tidak ada kamera / OCR buruk)

1. Ketuk **Odo**, **cost**, atau **volume** dan ketik nilainya (potret menggunakan keyboard sistem; lanskap menggunakan keypad di layar).
2. Pilih atau konfirmasi **Kendaraan** jika deteksi otomatis tidak berjalan.
3. Simpan seperti di atas.

### <a id="modes-and-borders"></a>Mode dan batas

- **Perbatasan hijau** di sekitar kendaraan+odo → menangkap/mengedit odometer.
- **Batas hijau** di sekitar biaya+volume → mode pompa.
- **Simpan** tetap dinonaktifkan hingga kendaraan dipilih dan setidaknya salah satu odo/biaya/volume memiliki data, dan OCR tidak lagi berjalan.

Tip di layar (di bawah baris instruksi): *Shutter = capture · Disk = save · ↕ = mode odo/pompa · ↔ = biaya/volume swap.*

---

## <a id="expenses"></a>Beban

### <a id="new-expense"></a>Pengeluaran baru

Menu → **Pengeluaran baru**.

![Formulir pengeluaran baru dan kontrol kamera (contoh gambar dasbor di pita kamera saat kosong)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Simpan** (disk), **rana** (foto tanda terima), atau **galeri** (pilih gambar).
2. Isi **Tanggal**, **Kendaraan**, **Vendor**, **Deskripsi**, **Jumlah** (simbol mata uang dapat diketuk), **Kategori**, opsional **Odometer**.
3. Tanda terima multi-halaman: ambil halaman tambahan jika UI menawarkan paging (halaman 0 adalah tanda terima utama).
4. **Simpan** untuk disimpan (lokal terlebih dahulu; pencadangan foto dan sinkronisasi spreadsheet terjadi di latar belakang saat dikonfigurasi).

### <a id="expense-list"></a>Daftar biaya

Menu → **Laporan** → **Daftar pengeluaran** — menelusuri pengeluaran non-bahan bakar sebelumnya; membuka item untuk diedit.

![Daftar biaya](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Edit pengeluaran

Buka baris dari daftar. Vendor, jumlah, kategori, kendaraan, dan deskripsi yang benar. Jika tanda terima hanya ada dalam cadangan foto (tidak ada file lokal yang dapat dibaca), gunakan **Ambil gambar dari arsip** saat ditampilkan (berfungsi di seluruh tujuan foto yang dikonfigurasi).

![Edit biaya](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Mulai perjalanan

Menu → **Mulai perjalanan** (setelah Isi laci). Ambil atau masukkan odometer, pilih jenis perjalanan, simpan dengan ikon **disk**. **Berhenti** adalah pintasan untuk Pribadi yang sekarang berada di lokasi GPS yang disimpan. Gunakan **ⓘ** untuk pengingat kontrol.

![Mulai perjalanan](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Awal perjalanan disimpan sebagai baris bahan bakar dengan **Jenis Perjalanan** (bukan pengisian normal). Mereka muncul di **Laporan → Jarak perjalanan**, bukan di Riwayat Bahan Bakar.

---

## <a id="reports"></a>Laporan

Menu → **Laporan** membuka hub produk (ringkasan sepanjang masa + kartu katalog). Ini adalah satu-satunya laporan produk yang muncul — tidak ada item laci “Laporan & Bagan” yang terpisah.

![Pusat laporan](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Buka kartu untuk mode kendaraan (**Semua / Masing-masing / Tunggal**), filter periode, bagan, dan bagikan (**TEKS / CSV / PDF**). Bilah atas pada anak laporan: **☰ + ←** (dan **ⓘ** saat terdaftar).

### <a id="time-based-reports"></a>Laporan berdasarkan waktu

Kartu grafik utama. Metrik opsional (mpg, volume/jarak seperti G/mi, harga satuan seperti $/G, biaya/jarak, $ bulanan, mil perjalanan, % perjalanan menurut jenis) dengan wadah **Halus** dan **skala Y independen** (sisi kiri ekonomi; uang dan keluarga perjalanan di sebelah kanan).

![Laporan berdasarkan waktu](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Laporan berbasis waktu — metrik yang digulir](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Detail matematika ekonomi: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Riwayat pengisian vs Riwayat Bahan Bakar

- **Laporan → Isi riwayat** — pengisian kronologis untuk filter laporan (**hanya diisi**; tidak ada perjalanan dimulai).

![Isi laporan riwayat](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Riwayat Bahan Bakar** (jika ada di navigasi bangunan Anda) — inventaris pengisian per kendaraan, juga hanya diisi; ketuk satu baris untuk mengedit.

### <a id="trip-miles"></a>Perjalanan mil

**Laporan → Jarak tempuh perjalanan** — jarak tempuh menurut jenis, bagan, dan kronologis **daftar awal perjalanan / segmen**. Ketuk awal yang sebenarnya untuk membuka **Edit isi** untuk baris tersebut.

![Perjalanan mil](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Edit isi

Dari Riwayat pengisian, Riwayat Bahan Bakar, atau Jarak perjalanan, buka pengisian. Tata letak: kendaraan dan odometer, **mata uang sebelum biaya**, volume, catatan. Jenis perjalanan hanya muncul bila baris tersebut merupakan awal perjalanan. Lokasi memiliki ringkasan ditambah **Detail lokasi**. Foto lokal dengan identitas cloud tidak ada: **Ambil gambar dari arsip**.

![Edit isi — Ambil dari arsip jika foto lokal hilang](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Kartu katalog lainnya mencakup pengeluaran berdasarkan kategori, ringkasan kendaraan, dan daftar pengeluaran.

Uang menggunakan mata uang setiap baris saat disetel. Total mata uang campuran menunjukkan **subtotal per mata uang** (tidak ada konversi FX senyap).

---

## <a id="syncing"></a>Sinkronisasi

Menu → **Sinkronisasi** adalah hub untuk tujuan spreadsheet dan foto (tidak hanya terkubur di bawah Pengaturan).

![Hub sinkronisasi](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Kartu untuk **Sinkronisasi spreadsheet** dan **Cadangan foto** dengan status pendek, **Sinkronisasi** untuk jenis itu, dan **›** ke dalam daftar tujuan.
- Buka tujuan untuk **Uji koneksi** dan **Sinkronkan sekarang (tujuan ini)** / semua dikonfigurasi.
- Kegagalan **Detail** dan tanda merah **!** di bilah judul muncul di sini.
- Penyiapan Google Spreadsheet dan Drive langkah demi langkah: [Pencadangan dan sinkronisasi multi-perangkat](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Pengaturan (preferensi lokal)

Menu → **Pengaturan**.

![Pengaturan](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Pengaturan lainnya (digulir)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Untuk tujuan, pilih **Menu → Sinkronisasi**. Pengaturan mungkin masih menampilkan baris ringkasan yang membuka daftar yang sama.

### <a id="local-preferences-common"></a>Preferensi lokal (umum)

- **Simpan Foto Tanda Terima Bahan Bakar** / **Simpan Foto Pengeluaran Secara Lokal** — menyimpan gambar di perangkat (dapat meminta izin Foto).
- **Putar Suara Rana**
- **Mata Uang** / **Satuan volume** — default aplikasi (sistem atau eksplisit). Mengubah satuan volume dengan data bahan bakar yang ada mungkin menawarkan dialog konversi.
- **Mode gelap**
- **Tips pengaturan** — membuka kembali tutorial sinkronisasi/kendaraan yang dijalankan pertama kali.
- **Isi Debug** / **Tampilkan layar eksperimen (dev)** — lanjutan; tinggalkan untuk penggunaan sehari-hari. Layar eksperimen tidak didokumentasikan di sini.

CSV **ekspor/impor** (ZIP Kendaraan / Pengeluaran / Tab Bahan Bakar) tersedia dari Pengaturan ketika ditawarkan oleh versi saat ini.

---

## <a id="help--about"></a>Bantuan & Tentang

![Bantuan](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Tentang](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Bantuan** — mulai cepat di perangkat, tutorial pengaturan, tautan ke manual ini, indeks pengaturan host mandiri.
- **Tentang** — versi, lisensi, GitHub, manual ini (dibundel offline + HTML online saat dipublikasikan).

---

## <a id="related-docs"></a>Dokumen terkait

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — panduan singkat sehari-hari
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — pengaturan foto/tabel yang dihosting sendiri
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — penggabungan, pemulihan, duplikat
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — detail metrik ekonomi
