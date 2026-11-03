# <a id="vehicle-expenses-automated--user-manual"></a>Automatizált járműköltségek — Felhasználói kézikönyv

> **Forrás szerkesztése (Markdown).** A böngészők és az alkalmazáson belüli olvasó megnyitják a **megjelenített HTML-t**:
> - Web: [`docs/user-manual/index.html`](index.html) (újragenerálás a `./scripts/render-user-manual.sh`-vel)
> - Alkalmazás: Súgó / Névjegy → teljes kézikönyv (csomagolt HTML + képernyőképek)
>
> Ne irányítsa a végfelhasználókat nyers `.md` URL-ekre – a böngészők csak egyszerű szöveget jelenítenek meg.

Első kamerakövetés az üzemanyag-feltöltések és a járműköltségek tekintetében, opcionális többeszköz-szinkronizálással és biztonsági mentéssel a **saját** felhőfiókok alatt.

Ez a **teljes kézikönyv** (képernyőképek + minden lépés). A telefonon a **Menü → Súgó** egy rövidebb útmutató az első lépésekhez.

**Itt nem szerepel:** Régi képek importálása, igazítási kísérlet és szivattyúkísérlet (fejlesztői / speciális eszközök).

---

## <a id="table-of-contents"></a>Tartalomjegyzék

1. [Mire van szüksége](#what-you-need)
2. [Ikonok egy pillantásra](#icons-at-a-glance)
3. [Nyissa meg a menüt](#open-the-menu)
4. [Első beállítás: Járművek kezelése](#first-time-setup-manage-vehicles)
5. [Biztonsági mentések és több eszköz szinkronizálása](#backups-and-multi-device-sync)
6. [Töltés (üzemanyag)](#fill-up-fuel)
7. [Utazás indítása](#start-trip)
8. [Kiadások](#expenses)
9. [Jelentések](#reports)
10. [Beállítások (helyi beállítások)](#settings-local-preferences)
11. [Szinkronizálás](#syncing)
12. [Súgó és névjegy](#help--about)
13. [Kapcsolódó dokumentumok](#related-docs)

---

## <a id="what-you-need"></a>Mire van szüksége

– Android telefon vagy táblagép.
– A legjobb OCR érdekében: tiszta nézet a **műszerfali kilométer-számlálóról** és a **szivattyú összesített értékéről** (vagy írja be kézzel a számokat).
– Nem kötelező: a **Ön által szabályozott fiókok** a táblázatadatokhoz és/vagy a fényképek biztonsági mentéséhez (lásd: [Biztonsági mentések és több eszközzel történő szinkronizálás](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Ikonok egy pillantásra

Ezek a fő képernyőkön jelennek meg. Ezek ismerete sok vadászatot takarít meg.

| Hol | Ikon / vezérlő | Mit csinál |
|-------|-----------------|---------------|
| Felső sáv | **☰ Menü** (hamburger) | Megnyitja a navigációs fiókot |
| Felső sáv | **ⓘ** (oldal súgó) | Rövid súgó az **aktuális** oldalhoz (a menü mellett, ha elérhető) |
| Felső sáv | **`?N`** (sárga) | Függőben lévő import-ellenőrzési kérdések — megnyitja az importálási ellenőrzést |
| Felső sáv | **!** (piros) | A közelmúltban meghiúsult egy táblázat vagy fénykép célhelye – nyissa meg a **Szinkronizálás** lehetőséget a javításhoz |
| Felső sáv | **☰ + ←** | A gyerekek jelentése és a költséglista együtt mutatja a **menüt és vissza**; A Reports hub csak a menüben működik |
| Beállítások / üzemanyag szerkesztés | **←** | Vissza (a beállítások táblázata/fényképe és az üzemanyag szerkesztése hátrafelé fókuszálva marad) |
| Kitöltés | **Fehér kör** (redőny) | A kilométer-számláló vagy a szivattyú kijelzőjének rögzítése az OCR-hez |
| Kitöltés | **Lemez / Mentés** | Mentse el a tankolást (járműre van szüksége, és legalább egy odo / térfogat / költség) |
| Kitöltés | **↕ nyilak** (üzemmód kapcsoló) | Váltás a **kilométerszámláló üzemmód** és a **szivattyú (költség/térfogat) üzemmód** között. A zöld szegély kiemeli az aktív mezőcsoportot |
| Kitöltés | **↔ nyilak** (költség és mennyiség között) | A csereköltség és a mennyiség, ha az OCR rossz mezőkbe helyezte őket |
| Kitöltés | **Zoom 1x / …** | A fényképezőgép zoom-arányai, ha az objektív támogatja azokat |
| Kitöltés (rögzítés után) | **Frissítés** a fő gombon | Az előnézet elvetése, és visszatérés az élő kamerához |
| Töltés (feldolgozás közben) | **X** a főgombon | A folyamatban lévő rögzítés/OCR megszakítása |
| Költség | **Mentés** | Mentse el a kiadást |
| Költség | **Zárókör** | Készítsen nyugtát |
| Költség | **Galéria** | Válasszon egy nyugta képet a könyvtárból |
| Költség | **Újrafelvétel** | Törölje az aktuális nyugta fényképét, és készítse újra |
| Költségek / Járművek kezelése | **+ / −** FAB-ok | A fénykép előnézetének nagyítása |
| Tereptárgyak párbeszédpanel | **OCR szerkesztése** | Javítsa ki vagy adjon hozzá iránypont szöveget, amelyet a motorok kihagytak |
| Táblázat / Fénykép űrlapok | **🔍 Keresés** | Tallózzon a Google Drive-ban egy lapot vagy mappát keresve (bejelentkezés után) |

A költségmezőkön lévő pénznemszimbólumok és a mennyiségmezőkben a **G/L** megérinthetők: nyisson meg egy kis menüt a pénznem vagy gallon/liter közötti módosításhoz az adott bejegyzéshez.

---

## <a id="open-the-menu"></a>Nyissa meg a menüt

1. Érintse meg a **☰** elemet a bal felső sarokban.
2. Válasszon egy oldalt.

![Navigációs fiók](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Fő fiók:** Töltés · Utazás indítása · Járművek kezelése · Új kiadás · **Jelentések** · Beállítások · Szinkronizálás · Súgó · Névjegy.

**Kísérleti fiók** (Beállítások → Kísérleti képernyők megjelenítése): Igazítási kísérlet · Szivattyús kísérlet · **Régi képek importálása**.

**A Reports hubon keresztül (nem a fő fiókon):** Költséglista · Kitöltési előzmények.

---

## <a id="first-time-setup-manage-vehicles"></a>Első beállítás: Járművek kezelése

Az OCR és az **automatikus járműegyeztetés** akkor működik a legjobban, ha minden járművet regisztrál egy **referencia-műszerfali fotóval**, futtatja a **Discovery** programot, beállítja a kilométer-számláló termését egy lapátmagból, és elmenti az adott műszerfal tereptárgyait.

### <a id="open-manage-vehicles"></a>Nyissa meg a Járművek kezelése lehetőséget

Menü → **Járművek kezelése**. Válasszon járművet (vagy **Új jármű hozzáadása**).

![Járművek kezelése referencia kötőjellel, termésekkel és felfedezéssel](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Jármű hozzáadása vagy szerkesztése

1. Nyissa meg a **Jármű** legördülő menüt → válasszon járművet vagy **Új jármű hozzáadása** lehetőséget.
2. Készítsen vagy válasszon egy tiszta **referencia-fényképet** (teljes műszercsoport, jól megvilágított, nagyjából négyzet alakú telefon). Használja a **Fénykép készítése** vagy a **Galéria** lehetőséget.
3. Érintse meg a **Futtassa a Discovery** elemet – a lapátérzékelés sárga **magokat** helyez el a fényképen (a tereptárgyak ugyanazt a `"ML Kit"` JSON-t használják, mint korábban).
4. Koppintson a **Select Odo** elemre, koppintson a számjegyek magjára, állítsa be a fogantyúkat, majd a **Mentés** lehetőséget.
5. Az **Odo szerkesztése** beállít egy már ott lévő mezőt. A **Clear Odo** eltávolítja, és a gombon a **Select Odo** felirat olvasható.
6. Tekintse át a **Termékpontok megjelenítése** segítségével. Használja az **OCR szerkesztése** lehetőséget a félreolvasások kijavításához vagy a kihagyott szöveg **hozzáadásához.
7. Töltse ki a **Jármű neve** mezőt (kötelező), valamint adja meg a márkát/modell/év/számtáblát tetszés szerint.
8. Érintse meg a **Jármű létrehozása** vagy a **Módosítások mentése** lehetőséget (új járműhöz név + referenciafotó szükséges).

![Új járműűrlapmezők hozzáadása](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Termékek: javítsd ki, amit a Discovery kihagyott

A **Termékpontok megjelenítése** után görgessen a listán, és javítsa ki az értékeket. A motorokból néha hiányoznak a kis számjegyek (például egy óra **60** a klaszter jobb alsó sarkában). Az **OCR szerkesztése** segítségével hozzáadhatja vagy javíthatja őket, hogy a jármű azonossága megbízható maradjon.

![Landmarks párbeszédpanel – az OCR áttekintése és szerkesztése](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Gépelés tökéletes fénykép nélkül

Továbbra is használhatja az alkalmazást, ha kiválaszt egy járművet, és **beírja** a kilométer-számlálót, a mennyiséget és a költségeket a kitöltéshez – az OCR minden mezőben nem kötelező. A galéria importálása működik a referencia-kötőjel-fotónál, ha nem szeretne az alkalmazáson belül fényképezni.

**Tipp:** A táblázat szinkronizálása után a járműdefiníciók (termények, tereptárgyak) a helyi adatbázisban jelennek meg – használatukhoz nem kell újra megnyitnia a Járművek kezelése a kitöltéshez.

---

## <a id="backups-and-multi-device-sync"></a>Biztonsági mentések és több eszköz szinkronizálása

Az alkalmazás úgy készült, hogy **több telefon vagy táblagép megoszthassa ugyanazokat a flottaadatokat**, így Ön megtarthatja **adatainak és fényképeinek másolatát az eszközről**. Ez a **Ön** a **saját** fiókokban vagy a **saját** saját üzemeltetésű kiszolgálókkal konfigurálható úti célokkal történik – nem egy vállalati üzemeltetésű „Járműköltségek felhő”, amelyet mások láthatnak.

### <a id="what-runs-where"></a>Mi hol fut

| Kedves | Mit tárol | Tipikus felhasználás |
|------|----------------|-------------|
| **Táblázat/táblázat szinkronizálás** | Járművek, üzemanyag-feltöltések, kiadások (sorok és fülek) | Több eszköz egyesítése + strukturált biztonsági mentés |
| **Fénykép biztonsági mentése** | Bináris képek (műszerfal/szivattyú/nyugta/referenciafotók) | Fotó biztonsági mentése + hiányzó fájlok visszaállítása |

Minden fajtából **több célt** konfigurálhat (típusonként lágy sapka). A kézi **Szinkronizálás most** és a **háttérben** dolgozók futtatják az engedélyezetteket.

### <a id="offline-first"></a> Először offline

– **Nincs szükség hálózatra** a feltöltési, költség- vagy nyugta hozzáadásához. Minden mentésre kerül **előbb helyben**.
– Ha a hálózat elérhető, a szinkronizálás és a fényképek biztonsági mentése **háttérfeladatokként** fut (az Ön által beállított ütemezés szerint, és amikor megérinti a **Szinkronizálás most** lehetőséget). A hibák piros szövegként jelennek meg a Beállítások sorok alatt és **!** az alkalmazás címsorában.

### <a id="your-accounts-only"></a>Csak az Ön fiókjai

A bejelentkezés és a tokenek az eszközön maradnak az Ön által választott szolgáltatóknál (Google, Microsoft, S3 kulcsok, saját hosztolt URL-ek stb.). A célállomások **teljesen a felhasználó irányítása alatt állnak** – az Ön Google-fiókja, OneDrive-ja, MinIO-tárhelye, EtherCalc-gazdagépe stb. Semmi sincs megosztva más Vehicle Expenses-felhasználókkal egy megosztott háttérrendszeren keresztül.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Támogatott célok — adatok (táblázat/táblázat)

A **Menü → Szinkronizálás → Táblázatszinkronizálás** alatt konfigurálható (a Beállítások összesítő soraiból is elérhető). Első osztályú választási lehetőségek:

| Cél | Megjegyzések |
|--------|--------|
| **Google Táblázatok** | Általános alapértelmezett; fülek a járművekhez, a költségekhez és a járművenkénti üzemanyaghoz |
| **Excel** | Microsoft-munkafüzet Graph / OneDrive-stílusú kötésen keresztül |
| **EtherCalc** | Saját üzemeltetésű együttműködési táblázatkezelő szobák |
| **Egyéb →** implementált háttérprogramok | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Elhalasztott / még nem fej nélküli (az Egyéb alatt van felsorolva, de nincs teljesen megvalósítva): OnlyOffice, Collabora. Lásd még: [self-host index](reference/self-host/INDEX.md).

A CSV **exportálás/importálás** (ugyanolyan lapelrendezésű ZIP-fájl) a Beállításokban érhető el hordozható biztonsági másolatként, az élő szinkronizálástól függetlenül.

### <a id="supported-targets--photos-image-backup"></a>Támogatott célpontok — fotók (képmásolat)

A **Menü → Szinkronizálás → Fotó biztonsági mentése** alatt konfigurálva (a Beállítások összefoglaló soraiból is):

| Cél | Megjegyzések |
|--------|--------|
| **Google Drive** | A választott mappa (böngészés vagy URL beillesztése) |
| **OneDrive** | Microsoft-fiók + elérési út előtag |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO és más S3-kompatibilis végpontok |
| **Egyéb** | rclone-alapú tárhely (pl. WebDAV, SFTP és más, az alkalmazáson belüli választóban elérhető kurált távvezérlők) |

Csallólapok beállítása saját tárolt fénykép- és táblázatcélokhoz: [saját gazdagép indexe](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Több eszköz viselkedése (rövid)

– A sorok egyesítése a **szinkronizálási azonosító** és az **utolsó írási nyeremények** által a **frissített** időbélyegeken.
– A törlések lágyak; egy másik eszközön végzett újabb szerkesztés visszaállíthatja a sort.
– Ha két eszközön beírja **ugyanazt a kitöltést**, akkor **két sor** jön létre – ha észreveszi, törölje a pluszt.
– További részletek: [Szinkronizálási viselkedés megjegyzései](#sync-behavior-notes) és [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Példa: Google Táblázatok hozzáadása (adatok)

1. **Menü → Szinkronizálás → Táblázatszinkronizálás** (vagy Beállítások → Táblázatszinkronizálás).

![Táblázatszinkronizálási lista](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Érintse meg a **Táblázat célhelyének hozzáadása** elemet.

![Szolgáltatóválasztó](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Válassza a **Google Táblázatok** lehetőséget.

![Google Táblázatok űrlap](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Jelentkezzen be a Google-lal** egyszer – ez a bejelentkezés lehetővé teszi a böngészést is. Megjelenített név → illesszen be egy **Munkalap URL-t** (még mindig érvényes), vagy **🔍** böngésszen. A Böngészés a **Minden és megosztott** oldalon nyílik meg, így kiválaszthat egy meglévő vagy Önnel megosztott táblázatot. Ezután ütemezési lehetőségek → engedélyezés → mentés.
5. **Szinkronizálás most** egyszer a lapok létrehozásához/frissítéséhez: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Példa: Google Drive hozzáadása (fotók)

1. **Menü → Szinkronizálás → Fotó biztonsági mentése** (vagy Beállítások → Fotó biztonsági mentése).

![Fényképek biztonsági mentési listája](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Érintse meg a **Fotó hozzáadása** lehetőséget.

![Fotószolgáltató választó](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Válassza a **Google Drive** lehetőséget.

![Google Drive űrlap](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Jelentkezzen be a Google-lal (Drive)** → opcionális mappa URL-címe/böngészés → engedélyezése → mentése → **Szinkronizálás most**.

Manuális **Szinkronizálás most** a fényképekhez teljes értékű; A háttérben készült biztonsági mentés általában ütemezetten dolgozza fel a **csak függőben lévő** feltöltéseket.

### <a id="sync-behavior-notes"></a>Szinkronizálási viselkedési megjegyzések

– Az alkalmazásfrissítés után röviden láthatja a **„Adatbázis frissítése frissítés után…”** (helyi szinkronizálási azonosítójú háttérkitöltés).
– Ha a szinkronizálás megszakad, a következő **sikeres** szinkronizálás újra egyesül, és kijavítja a távoli lapokat.
– Hibák: piros összefoglaló a kártyák szinkronizálásánál + **!** az alkalmazássávon.

---

## <a id="fill-up-fuel"></a>Töltés (üzemanyag)

Ez a **kezdőképernyő**, amikor megnyitja az alkalmazást.

### <a id="vehicle-selection-usually-automatic"></a>Járműválasztás (általában automatikus)

**nem** kell először kiválasztania a járművet. Ha a járműveken **tereptárgyak** vannak beállítva a Járművek kezelése részben, a Kitöltés **automatikusan felismeri, hogy melyik jármű** a műszerfalról, miután elkészítette a kilométer-számlálót. Szükség esetén továbbra is megnyithatja a **Jármű** legördülő menüt a felülbíráláshoz.

### <a id="aim-at-the-odometer"></a> Célozzon a kilométer-számlálóra

Maradjon kilométerszámláló módban, és keretezze be a klasztert. Utasítás: * Célozzon a kilométer-számlálóra. Érintse meg az exponálót a rögzítéshez.*

![Kitöltés – élő kamera a kilométer-számlálón (példa kötőjel kép az előnézetben)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>A kilométerszámláló redőny után

Az OCR kitölti az **Odo**-t, és megpróbálja a járműhöz igazítani a tereptárgyak alapján (szükség esetén mindkettőt ellenőrizni kell). A fő gomb a **Újra** lesz az újrafelvételhez. Az utasítás összefoglalja az olvasmányt.

![Kitöltés — kilométer-számláló OCR eredménye](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Szivattyú üzemmód (költség és mennyiség)

1. Érintse meg a **↕** elemet a szivattyú üzemmódra váltáshoz: *Célozzon a szivattyú kijelzőjére (költség/térfogat). Koppintson a redőnyre.*
2. Rögzítse a szivattyú összesített értékét. Költség és mennyiség mezők kitöltése; használja a **↔**-t, ha felcserélik őket.
3. Érintse meg a pénznemet vagy a **G/L** elemet, ha szükséges, majd a **Mentés** (lemez). Az üres mezők **részleges kitöltést eredményeznek** (még mindig megengedett).

![Kitöltés — szivattyú előnézete / összegek (például pumpakép az előnézetben)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

A következő megállóig a Kitöltésen marad (a mezők mentés után törlődnek). Teljesen **offline** munkavégzés; a szinkronizálás később fut a háttérben, ha be van állítva.

### <a id="manual-entry-no-camera--bad-ocr"></a>Manuális bevitel (nincs kamera / rossz OCR)

1. Érintse meg a **Odo**, **költség** vagy **hangerő** elemet, és írja be az értékeket (az álló a rendszer billentyűzetét használja, a fekvő a képernyőn megjelenő billentyűzetet).
2. Válassza vagy erősítse meg a **Jármű** lehetőséget, ha az automatikus felismerés nem futott.
3. Mentse el a fentiek szerint.

### <a id="modes-and-borders"></a>Módok és szegélyek

- **Zöld szegély** a jármű körül+odo → kilométer-számláló rögzítése/szerkesztése.
- **Zöld keret** a költség+térfogat körül → szivattyú üzemmód.
– A **Mentés** letiltva marad mindaddig, amíg ki nem választanak egy járművet, és az odo/cost/volume legalább egy nem rendelkezik adatokkal, és az OCR még nem fut.

Tipp a képernyőn (az utasítássor alatt): *Zár = rögzítés · Lemez = mentés · ↕ = odo/pump mód · ↔ = csereköltség/kötet.*

---

## <a id="expenses"></a>Költségek

### <a id="new-expense"></a>Új kiadás

Menü → **Új költség**.

![Új kiadási űrlap és kameravezérlők (például kötőjel kép a kamerasávban, ha üres)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Mentés** (lemez), **rekesz** (nyugta fotó) vagy **galéria** (kép kiválasztása).
2. Töltse ki a következőt: **Dátum**, **Jármű**, **Szállító**, **Leírás**, **Összeg** (koppintható pénznemszimbólum), **Kategória**, opcionális **Kilométerszámláló**.
3. Többoldalas nyugták: további oldalak rögzítése, ha a felhasználói felület lapozást kínál (a 0. oldal az elsődleges nyugta).
4. **Mentés** a tároláshoz (első a helyi; a fényképek biztonsági mentése és a táblázatok szinkronizálása a háttérben történik, ha konfigurálva van).

### <a id="expense-list"></a>Költséglista

Menü → **Jelentések** → **Költséglista** — böngészhet a múltbeli, nem üzemanyag-kiadások között; nyissa meg a szerkeszteni kívánt elemet.

![Költséglista](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Költség szerkesztése

Nyisson meg egy sort a listából. Helyes szállító, mennyiség, kategória, jármű és leírás. Ha a nyugta csak fényképes biztonsági másolatban van (nincs olvasható helyi fájl), használja a **Kép lekérése az archívumból** lehetőséget, amikor megjelenik (a konfigurált fotócélhelyeken működik).

![Költség szerkesztése](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Utazás indítása

Menü → **Utazás indítása** (a fiók kitöltése után). Rögzítse vagy írja be a kilométer-számlálót, válassza ki az utazás típusát, és mentse el a **lemez** ikonnal. A **Stop** a Személyes most parancsikon a tartott GPS-helyen. Használja a **ⓘ** vezérlő emlékeztetőket.

![Utazás indítása](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Az útindítások üzemanyagsorokként vannak tárolva, **Kifutás típussal** (nem normál feltöltések). A **Jelentések → Utazási mérföldek** alatt jelennek meg, nem az Üzemanyagelőzmények alatt.

---

## <a id="reports"></a>Jelentések

Menü → **Jelentések** megnyitja a termékközpontot (minden idők összefoglalója + katalóguskártyák). Ez az egyetlen termékjelentési felület – nincs külön „Jelentések és diagramok” fiókelem.

![Jelentések központja](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Nyisson meg egy kártyát a jármű módhoz (**Összes / Mindegyik / Egyetlen**), időszakszűrőkhöz, diagramokhoz és megosztáshoz (**TEXT / CSV / PDF**). A jelentési gyermekek felső sávja: **☰ + ←** (és **ⓘ** regisztráció esetén).

### <a id="time-based-reports"></a>Időalapú jelentések

A fő diagramkártya. Opcionális mérőszámok (mpg, térfogat/távolság, például G/mi, egységár, például $/G, költség/távolság, havi $, utazási mérföld, utazás % típus szerint) **Smooth** tárolókkal és **független Y mérleggel** (gazdaságosság balra; pénz és utazás családok a jobb oldalon).

![Időalapú jelentések](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Időalapú jelentések – görgetett mutatók](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

A gazdaságos matematikai adatok: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Kitöltési előzmények és üzemanyag-előzmények

- **Jelentések → Kitöltési előzmények** — a jelentésszűrők időrendi kitöltése (**csak kitöltések**; nem indul út).

![Kitöltési előzmények jelentés](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

– **Üzemanyag-előzmények** (ha megtalálható az építmény navigációjában) – járműnkénti feltöltési készlet, szintén csak kitöltések; koppintson egy sorra a szerkesztéshez.

### <a id="trip-miles"></a>Utazás mérföld

**Jelentések → Útmérföldek** – mérföldek típusonként, diagramok és időrendi **útkezdési/szegmenslista**. Koppintson egy igazi kezdetre az adott sor **Kitöltés szerkesztése** megnyitásához.

![Utazás mérföld](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Kitöltés szerkesztése

A Kitöltési előzmények, Üzemanyagelőzmények vagy Utazási mérföldek területen nyisson meg egy kitöltést. Elrendezés: jármű és kilométerszámláló, **költség előtti pénznem**, mennyiség, megjegyzések. Az utazás típusa csak akkor jelenik meg, ha a sor egy utazás kezdete. A helyhez egy összefoglaló tartozik, plusz **Helyadatok**. Hiányzó helyi fotó felhőazonossággal: **Kép lekérése az archívumból**.

![Kitöltés szerkesztése — Lehívás az archívumból, ha hiányzik a helyi fotó](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Az egyéb katalóguskártyák kategóriánkénti kiadásokat, járműösszefoglalót és költséglistát tartalmaznak.

A pénz az egyes sorok pénznemét használja, amikor be van állítva. A vegyes devizaösszegek **pénznemenkénti részösszegeket** mutatnak (nincs csendes devizakonverzió).

---

## <a id="syncing"></a>Szinkronizálás

Menü → **Szinkronizálás** a táblázatkezelő és fotócélok központja (nem csak a Beállítások alatt található).

![Szinkronizálási központ](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

– Kártyák a **Táblázatszinkronizálás** és a **Fotók biztonsági mentése** funkcióhoz rövid állapottal, **Szinkronizálás** az ilyen típusokhoz, és **›** a céllistához.
- Nyissa meg a **Kapcsolat tesztelése** és **Szinkronizálás most (ez a cél)** célhelyet / minden konfigurálva.
– A hiba **Részletek** és a piros **!** a címsorban itt landol.
– A Google Táblázatok és a Drive lépésről lépésre történő beállítása: [Biztonsági mentések és több eszköz szinkronizálása](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Beállítások (helyi beállítások)

Menü → **Beállítások**.

![Beállítások](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![További beállítások (görgetve)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Célállomások esetén előnyben részesítse a **Menü → Szinkronizálás** lehetőséget. A beállítások továbbra is megjeleníthetnek összefoglaló sorokat, amelyek ugyanazokat a listákat nyitják meg.

### <a id="local-preferences-common"></a>Helyi beállítások (gyakori)

– **Üzemanyag-visszavételi fényképek mentése** / **Költségfotók mentése helyben** – a képek megtartása az eszközön (kérhet Photos engedélyt).
– **Zárhang lejátszása**
– **Pénznem** / **Térfogat mértékegysége** – az alkalmazás alapértelmezett beállításai (rendszer vagy explicit). A térfogat mértékegységének megváltoztatása a meglévő üzemanyagadatokkal konverziós párbeszédablakot eredményezhet.
- **Sötét mód**
– **Beállítási tippek** – az első futási jármű/szinkronizálási oktatóanyagok újbóli megnyitása.
– **Hibakeresési kitöltés** / **Kísérleti képernyők megjelenítése (fejlesztő)** – haladó; hagyja ki a napi használatra. A kísérleti képernyők itt nincsenek dokumentálva.

A CSV **export/import** (Járművek ZIP-kódja / Költségek / Üzemanyag lapok) a Beállításokban érhető el, ha az aktuális build kínálja.

---

## <a id="help--about"></a>Súgó és névjegy

![Súgó](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Névjegy](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Súgó** – gyorsindítás az eszközön, beállítási útmutatók, hivatkozás a kézikönyvre, saját gazdagép beállítási indexe.
– **Névjegy** – verzió, licencek, GitHub, ez a kézikönyv (offline csomagban + online HTML közzétételkor).

---

## <a id="related-docs"></a>Kapcsolódó dokumentumok

– [QUICK_GUIDE.md](../QUICK_GUIDE.md) – rövid mindennapi útmutató
– [self-host/INDEX.md](../reference/self-host/INDEX.md) – saját tárolású fénykép/táblázatos beállítás
– [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) – egyesítés, helyreállítás, ismétlődések
– [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) – a gazdaságossági mutatók részletei
