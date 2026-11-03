# <a id="vehicle-expenses-automated--user-manual"></a>Automatyczne wydatki na pojazd — Podręcznik użytkownika

> **Edytuj źródło (Markdown).** Przeglądarki i czytnik w aplikacji otwierają **renderowany kod HTML**:
> - Sieć: [`docs/user-manual/index.html`](index.html) (regeneruj za pomocą `./scripts/render-user-manual.sh`)
> - Aplikacja: Pomoc / Informacje → pełna instrukcja (w pakiecie HTML + zrzuty ekranu)
>
> Nie kieruj użytkowników końcowych na surowe `.md` adresy URL — przeglądarki wyświetlają tylko zwykły tekst.

Śledzenie za pomocą kamery tankowań paliwa i wydatków na pojazd, z opcjonalną synchronizacją wielu urządzeń i tworzeniem kopii zapasowych w ramach **twoich** kont w chmurze.

To jest **pełna instrukcja** (zrzuty ekranu + każdy krok). W telefonie **Menu → Pomoc** to krótszy przewodnik wprowadzający.

**Nie ujęte tutaj:** Importuj stare zdjęcia, eksperyment z wyrównaniem i eksperyment z pompą (narzędzia programistyczne / zaawansowane).

---

## <a id="table-of-contents"></a>Spis treści

1. [Czego potrzebujesz](#what-you-need)
2. [Przegląd ikon](#icons-at-a-glance)
3. [Otwórz menu](#open-the-menu)
4. [Konfiguracja po raz pierwszy: Zarządzaj pojazdami](#first-time-setup-manage-vehicles)
5. [Kopie zapasowe i synchronizacja wielu urządzeń](#backups-and-multi-device-sync)
6. [Napełnij (paliwo)](#fill-up-fuel)
7. [Rozpocznij podróż](#start-trip)
8. [Wydatki](#expenses)
9. [Raporty](#reports)
10. [Ustawienia (preferencje lokalne)](#settings-local-preferences)
11. [Synchronizacja](#syncing)
12. [Pomoc i informacje o](#help--about)
13. [Powiązane dokumenty](#related-docs)

---

## <a id="what-you-need"></a>Czego potrzebujesz

– telefon lub tablet z Androidem.
— Aby uzyskać najlepszy OCR: wyraźny widok **licznika przebiegu na desce rozdzielczej** i **sumy pomp** (lub wpisz liczby ręcznie).
– Opcjonalnie: konta **kontrolowane** do przechowywania danych w arkuszach kalkulacyjnych i/lub tworzenia kopii zapasowych zdjęć (zobacz [Kopie zapasowe i synchronizacja wielu urządzeń](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Ikony w skrócie

Pojawiają się one na głównych ekranach. Znajomość ich oszczędza wiele polowań.

| Gdzie | Ikona / kontrola | Co to robi |
|-------|----------------|-------------|
| Górny pasek | **☰ Menu** (hamburger) | Otwiera szufladę nawigacji |
| Górny pasek | **ⓘ** (pomoc strony) | Krótka pomoc dla **bieżącej** strony (obok menu, jeśli jest dostępne) |
| Górny pasek | **`?N`** (żółty) | Oczekujące pytania dotyczące przeglądu importu — otwiera Przegląd importu |
| Górny pasek | **!** (czerwony) | Arkusz kalkulacyjny lub miejsce docelowe zdjęć ostatnio nie powiodło się — otwórz **Synchronizacja**, aby naprawić |
| Górny pasek | **☰ + ←** | Raportuj listę dzieci i wydatków, wyświetlając jednocześnie **menu i tył**; Centrum raportów jest dostępne tylko w menu |
| Ustawienia / edycja paliwa | **←** | Wstecz (ustawienia arkusza kalkulacyjnego/edycji zdjęć i paliwa pozostają skupione na tle) |
| Wypełnij | **Białe kółko** (migawka) | Przechwytywanie licznika przebiegu lub wyświetlacza pompy dla OCR |
| Wypełnij | **Dysk / Zapisz** | Zaoszczędź na tankowaniu (potrzebuje pojazdu i co najmniej jednego odo / objętości / kosztu) |
| Wypełnij | **↕ strzałki** (przełącznik trybu) | Przełącz **tryb licznika kilometrów** vs **tryb pompy (koszt/objętość)**. Zielona ramka podświetla aktywną grupę pól |
| Wypełnij | **↔ strzałki** (pomiędzy kosztem a wolumenem) | Zamień koszt i wolumen, jeśli OCR umieści je w niewłaściwych polach |
| Wypełnij | **Powiększenie 1x / …** | Współczynniki zoomu aparatu, gdy obiektyw je obsługuje |
| Wypełnij (po przechwyceniu) | **Odśwież** na głównym przycisku | Odrzuć podgląd i wróć do kamery na żywo |
| Wypełnij (podczas przetwarzania) | **X** na głównym przycisku | Anuluj trwające przechwytywanie/OCR |
| Wydatek | **Zapisz** | Oszczędzaj wydatki |
| Wydatek | **Koło migawki** | Zrób zdjęcie paragonu |
| Wydatek | **Galeria** | Wybierz obraz paragonu z biblioteki |
| Wydatek | **Powtórz** | Usuń bieżące zdjęcie paragonu i zrób zdjęcie ponownie |
| Wydatki / Zarządzaj pojazdami | **+ / −** FAB-y | Powiększ podgląd zdjęcia |
| Okno dialogowe punktów orientacyjnych | **Edytuj OCR** ​​| Popraw lub dodaj charakterystyczny tekst, który przeoczyły silniki |
| Arkusz kalkulacyjny / formularze fotograficzne | **🔍 Szukaj** | Przeglądaj Dysk Google w poszukiwaniu arkusza lub folderu (po zalogowaniu) |

Symbole walut w polach kosztu i **G/L** w polach wolumenu można dotknąć: otwórz małe menu, aby zmienić walutę lub galony na litry dla tego wpisu.

---

## <a id="open-the-menu"></a>Otwórz menu

1. Naciśnij **☰** w lewym górnym rogu.
2. Wybierz stronę.

![Szuflada nawigacji](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Główna szuflada:** Wypełnij · Rozpocznij podróż · Zarządzaj pojazdami · Nowy wydatek · **Raporty** · Ustawienia · Synchronizacja · Pomoc · Informacje.

**Szuflada eksperymentów** (Ustawienia → Pokaż ekrany eksperymentów): Eksperyment z wyrównaniem · Eksperyment z pompą · **Importuj stare zdjęcia**.

**Za pośrednictwem centrum raportów (nie głównej szuflady):** Lista wydatków · Historia wypełniania.

---

## <a id="first-time-setup-manage-vehicles"></a>Konfiguracja po raz pierwszy: Zarządzaj pojazdami

OCR i **automatyczne dopasowywanie pojazdów** działają najlepiej po zarejestrowaniu każdego pojazdu za pomocą **referencyjnego zdjęcia deski rozdzielczej**, uruchomieniu **Discovery**, ustawieniu przycięcia licznika przebiegu na podstawie wiosła i zapisaniu punktów orientacyjnych dla tej kreski.

### <a id="open-manage-vehicles"></a>Otwórz opcję Zarządzaj pojazdami

Menu → **Zarządzaj pojazdami**. Wybierz pojazd (lub **Dodaj nowy pojazd**).

![Zarządzaj pojazdami za pomocą myślnika referencyjnego, upraw i odkryć](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Dodaj lub edytuj pojazd

1. Otwórz menu rozwijane **Pojazd** → wybierz pojazd lub **Dodaj nowy pojazd**.
2. Zrób lub wybierz wyraźne **referencyjne zdjęcie deski rozdzielczej** (pełny zestaw wskaźników, dobrze oświetlony, telefon mniej więcej ustawiony prosto). Użyj **Zrób zdjęcie** lub **Galeria**.
3. Naciśnij **Uruchom Discovery** — wykrywanie łopatek umieszcza żółte **nasiona** na zdjęciu (punkty orientacyjne używają tego samego `"ML Kit"` JSON co poprzednio).
4. Kliknij **Wybierz Odo**, dotknij nasion na cyfrach, dopasuj uchwyty, a następnie **Zapisz**.
5. **Edytuj Odo** dostosowuje pole, które już tam jest. **Wyczyść Odo** usuwa go, a na przycisku pojawia się napis **Wybierz Odo**.
6. Recenzja z opcją **Pokaż punkty orientacyjne**. Użyj opcji **Edytuj OCR**, aby naprawić błędy odczytane lub **dodać** pominięty tekst.
7. Wypełnij **Nazwa pojazdu** (wymagane) oraz markę/model/rok/tablicę rejestracyjną, według własnego uznania.
8. Naciśnij **Utwórz pojazd** lub **Zapisz zmiany** (wymagana nazwa i zdjęcie referencyjne dla nowego pojazdu).

![Dodaj nowe pola formularza pojazdu](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Zabytki: napraw to, co przeoczyło Discovery

Po **Pokaż punkty orientacyjne** przewiń listę i popraw wartości. Silnikom czasami brakuje małych cyfr (na przykład zegara **60** w prawym dolnym rogu zestawu wskaźników). Użyj **Edytuj OCR**, aby je dodać lub naprawić, aby tożsamość pojazdu pozostała niezawodna.

![Okno punktów orientacyjnych — przeglądaj i edytuj OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Pisanie bez idealnego zdjęcia

Nadal możesz korzystać z aplikacji, wybierając pojazd i **wpisując** licznik przebiegu, objętość i koszt w Fill — OCR jest opcjonalny w każdym polu. Import galerii działa w przypadku zdjęcia referencyjnego, jeśli nie chcesz robić zdjęć w aplikacji.

**Wskazówka:** po zsynchronizowaniu arkusza kalkulacyjnego definicje pojazdów (uprawy, punkty orientacyjne) znajdują się w lokalnej bazie danych — nie musisz ponownie otwierać opcji Zarządzaj pojazdami w celu wypełnienia, aby z nich skorzystać.

---

## <a id="backups-and-multi-device-sync"></a>Kopie zapasowe i synchronizacja wielu urządzeń

Aplikacja została stworzona w taki sposób, aby **kilka telefonów lub tabletów mogło współużytkować te same dane floty** i abyś mógł zachować **kopię swoich danych i zdjęć poza urządzeniem**. Odbywa się to za pomocą miejsc docelowych **które** konfigurujesz w ramach **swoich** kont lub **swoich** serwerów hostowanych samodzielnie — a nie prowadzonej przez firmę „chmurze wydatków na pojazdy”, którą widzą inne osoby.

### <a id="what-runs-where"></a>Co i gdzie biegnie

| Miły | Co przechowuje | Typowe zastosowanie |
|------|----------------|------------|
| **Synchronizacja arkusza kalkulacyjnego/tabelarycznego** | Pojazdy, tankowania, wydatki (wiersze i zakładki) | Scalanie wielu urządzeń + uporządkowana kopia zapasowa |
| **Kopia zapasowa zdjęć** | Obrazy binarne (kreska/pompa/paragon/zdjęcia referencyjne) | Kopia zapasowa zdjęć + przywracanie brakujących plików |

Możesz skonfigurować **wiele miejsc docelowych** każdego rodzaju (miękki limit na typ). Ręczni pracownicy **Synchronizuj teraz** i **w tle** uruchamiają włączone.

### <a id="offline-first"></a>Najpierw offline

- **Nie jest wymagana żadna sieć**, aby dodać uzupełnienie, wydatek lub paragon. Wszystko jest zapisywane **najpierw lokalnie**.
– Gdy sieć jest dostępna, synchronizacja i tworzenie kopii zapasowych zdjęć działają jako **zadania w tle** (zgodnie z ustawionym harmonogramem i po dotknięciu **Synchronizuj teraz**). Błędy są wyświetlane jako czerwony tekst w wierszach Ustawienia i **!** na pasku tytułu aplikacji.

### <a id="your-accounts-only"></a>Tylko Twoje konta

Logowanie i tokeny pozostają na urządzeniu dla wybranych dostawców (Google, Microsoft, klucze S3, własne adresy URL itp.). Miejsca docelowe są pod **pełną kontrolą użytkownika** — Twoje konto Google, Twoja OneDrive, Twój segment MinIO, Twój host EtherCalc itp. Nic nie jest udostępniane innym użytkownikom wydatków na pojazdy poprzez współdzielony backend.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Obsługiwane elementy docelowe — dane (arkusz kalkulacyjny / tabela)

Konfigurowane w **Menu → Synchronizacja → Synchronizacja arkusza kalkulacyjnego** (dostępne również z wierszy podsumowania Ustawień). Opcje pickera najwyższej klasy:

| Cel | Notatki |
|------------|------------|
| **Arkusze Google** | Wspólne ustawienie domyślne; zakładki Pojazdy, Wydatki i paliwo na pojazd |
| **Excel** | Skoroszyt Microsoft za pośrednictwem powiązania w stylu Graph/OneDrive |
| **EtherCalc** | Własne pokoje do współpracy z arkuszami kalkulacyjnymi |
| **Inne →** zaimplementowane backendy | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Arkusz Zoho** |

Odroczone / jeszcze nie całkowicie wdrożone (wymienione w sekcji Inne, ale nie w pełni wdrożone): OnlyOffice, Collabora. Zobacz także [indeks własnego hosta] (reference/self-host/INDEX.md).

CSV **eksport/import** (ZIP o tym samym układzie zakładek) jest dostępny w Ustawieniach jako przenośna kopia zapasowa, niezależna od synchronizacji na żywo.

### <a id="supported-targets--photos-image-backup"></a>Obsługiwane cele — zdjęcia (kopia zapasowa obrazu)

Konfiguracja w **Menu → Synchronizacja → Kopia zapasowa zdjęć** (również z wierszy podsumowania Ustawień):

| Cel | Notatki |
|------------|------------|
| **Dysk Google** | Wybrany folder (przejrzyj lub wklej adres URL) |
| **OneDrive** | Konto Microsoft + prefiks ścieżki |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO i inne punkty końcowe kompatybilne z S3 |
| **Inne** | pamięć masowa wspierana przez rclone (np. WebDAV, SFTP i inne wybrane piloty dostępne w selektorze w aplikacji) |

Ściągawki konfiguracyjne dla hostowanych zdjęć i celów tabelarycznych: [indeks samodzielnego hostowania](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Działanie na wielu urządzeniach (krótkie)

– Wiersze łączą się według **ID synchronizacji** z **wygranymi ostatniego zapisu** na **zaktualizowanych** znacznikach czasu.
– Usunięcia są miękkie; nowsza edycja na innym urządzeniu może przywrócić wiersz.
– Dwukrotne wprowadzenie **tego samego wypełnienia** na dwóch urządzeniach powoduje utworzenie **dwóch wierszy** — jeśli zauważysz, usuń dodatek.
- Więcej szczegółów: [Notatki dotyczące zachowania synchronizacji](#sync-behavior-notes) i [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Przykład: dodaj Arkusze Google (dane)

1. **Menu → Synchronizacja → Synchronizacja arkusza kalkulacyjnego** (lub Ustawienia → Synchronizacja arkusza kalkulacyjnego).

![Lista synchronizacji arkuszy kalkulacyjnych](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Stuknij **Dodaj miejsce docelowe arkusza kalkulacyjnego**.

![Wybór dostawcy](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Wybierz **Arkusze Google**.

![Formularz Arkuszy Google](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Zaloguj się raz w Google** — to logowanie umożliwia także przeglądanie. Wyświetlana nazwa → wklej **URL arkusza** (nadal ważny) lub **🔍** przeglądaj. Przeglądanie otwiera się na **Wszystkie i udostępnione**, dzięki czemu możesz wybrać istniejący arkusz kalkulacyjny, którego jesteś właścicielem lub który Ci udostępniono. Następnie opcje harmonogramu → włącz → zapisz.
5. **Synchronizuj teraz** raz, aby utworzyć/zaktualizować karty: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Przykład: dodaj Dysk Google (zdjęcia)

1. **Menu → Synchronizacja → Kopia zapasowa zdjęć** (lub Ustawienia → Kopia zapasowa zdjęć).

![Lista kopii zapasowych zdjęć](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Stuknij **Dodaj miejsce docelowe zdjęcia**.

![Wybór dostawców zdjęć](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Wybierz **Dysk Google**.

![Formularz Dysku Google](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Zaloguj się za pomocą Google (Dysk)** → opcjonalny adres URL folderu/przeglądaj → włącz → zapisz → **Synchronizuj teraz**.

Ręczny **Synchronizacja teraz** w przypadku zdjęć jest pełna; kopia zapasowa w tle zazwyczaj przetwarza **tylko oczekujące** przesyłanie zgodnie z harmonogramem.

### <a id="sync-behavior-notes"></a>Zsynchronizuj notatki dotyczące zachowania

– Po aktualizacji aplikacji możesz przez chwilę zobaczyć **„Aktualizacja bazy danych po aktualizacji…”** (uzupełnianie lokalnego identyfikatora synchronizacji).
— Jeśli synchronizacja zostanie przerwana, następna **udana** synchronizacja ponownie połączy i naprawia zdalne karty.
- Awarie: czerwone podsumowanie na kartach Synchronizacji + **!** na pasku aplikacji.

---

## <a id="fill-up-fuel"></a>Napełnij (paliwo)

To jest **ekran główny** po otwarciu aplikacji.

### <a id="vehicle-selection-usually-automatic"></a>Wybór pojazdu (zwykle automatyczny)

**Nie** musisz najpierw wybierać pojazd. Gdy pojazdy mają skonfigurowane **punkty orientacyjne** w Zarządzaj pojazdami, funkcja Fill **automatycznie wykrywa, który pojazd** na podstawie obrazu tablicy rozdzielczej po zarejestrowaniu licznika przebiegu. W razie potrzeby możesz nadal otworzyć menu rozwijane **Pojazd**, aby je zastąpić.

### <a id="aim-at-the-odometer"></a>Wyceluj w licznik kilometrów

Pozostań w trybie licznika kilometrów i wykadruj klaster. Instrukcja: * Celuj w licznik kilometrów. Kliknij migawkę, aby uchwycić.*

![Fill — kamera na żywo na liczniku kilometrów (przykładowy obraz kreski w podglądzie)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Za zasłoną licznika kilometrów

OCR wypełnia **Odo** i próbuje dopasować pojazd na podstawie punktów orientacyjnych (w razie potrzeby przejrzyj oba). Główny przycisk zmieni się na **Ponów próbę**, aby ponownie wykonać zdjęcie. Instrukcja podsumowuje lekturę.

![Wypełnienie — wynik OCR licznika kilometrów](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Tryb pompy (koszt i objętość)

1. Naciśnij **↕**, aby przejść do trybu pompy: *Wyceluj w wyświetlacz pompy (koszt/objętość). Kliknij migawkę.*
2. Zbierz sumy pomp. Wypełnienie pól kosztu i wolumenu; użyj **↔**, jeśli są zamienione.
3. W razie potrzeby dotknij waluty lub **G/L**, a następnie **Zapisz** (dysk). Puste pola powodują **częściowe wypełnienie** (nadal dozwolone).

![Wypełnienie — podgląd pompy / sumy (przykładowy obraz pompy w podglądzie)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Pozostaniesz na Wypełnieniu do następnego przystanku (pola zostaną usunięte po zapisaniu). Pracuj w pełni **offline**; synchronizacja będzie działać później w tle, jeśli zostanie skonfigurowana.

### <a id="manual-entry-no-camera--bad-ocr"></a>Wpis ręczny (brak kamery / zły OCR)

1. Stuknij **Odo**, **koszt** lub **objętość** i wpisz wartości (w trybie pionowym używana jest klawiatura systemowa; pozioma korzysta z klawiatury ekranowej).
2. Wybierz lub potwierdź **Pojazd**, jeśli automatyczne wykrywanie nie zostało uruchomione.
3. Zapisz jak wyżej.

### <a id="modes-and-borders"></a>Tryby i granice

- **Zielona ramka** wokół pojazdu+odno → przechwytywanie/edycja licznika przebiegu.
- **Zielona ramka** wokół kosztu+objętości → trybu pompy.
- **Zapisywanie** pozostaje wyłączone do czasu wybrania pojazdu i co najmniej jednego z danych/kosztu/objętości zawiera dane, a OCR nie jest nadal uruchomiony.

Wskazówka ekranowa (poniżej linii instrukcji): *Migawka = przechwytywanie · Dysk = zapisywanie · ↕ = tryb odo/pompy · ↔ = koszt wymiany/objętość.*

---

## <a id="expenses"></a>Wydatki

### <a id="new-expense"></a>Nowy wydatek

Menu → **Nowy wydatek**.

![Nowy formularz wydatków i sterowanie kamerą (przykładowy obraz kreski w paśmie kamery, gdy jest pusty)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Zapisz** (dysk), **migawka** (zdjęcie paragonu) lub **galeria** (wybierz obraz).
2. Wypełnij **Data**, **Pojazd**, **Sprzedawca**, **Opis**, **Kwota** (możliwość dotknięcia symbolu waluty), **Kategoria**, opcjonalnie **Przebieg**.
3. Paragony wielostronicowe: przechwytuj dodatkowe strony, jeśli interfejs użytkownika oferuje stronicowanie (strona 0 jest paragonem głównym).
4. **Zapisz** w sklepie (najpierw lokalnie; kopie zapasowe zdjęć i synchronizacja arkuszy kalkulacyjnych odbywają się w tle, jeśli są skonfigurowane).

### <a id="expense-list"></a>Lista wydatków

Menu → **Raporty** → **Lista wydatków** — przeglądaj dotychczasowe wydatki pozapaliwowe; otwórz element do edycji.

![Lista wydatków](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Edytuj wydatek

Otwórz wiersz z listy. Popraw dostawcę, kwotę, kategorię, pojazd i opis. Jeśli paragon znajduje się tylko w kopii zapasowej zdjęcia (nie ma czytelnego pliku lokalnego), po wyświetleniu użyj opcji **Pobierz obraz z archiwum** (działa we wszystkich skonfigurowanych lokalizacjach docelowych zdjęć).

![Edytuj wydatek](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Rozpocznij podróż

Menu → **Rozpocznij podróż** (po wypełnieniu szuflady). Przechwytuj lub wprowadź licznik przebiegu, wybierz typ podróży, zapisz za pomocą ikony **dysku**. **Stop** to skrót do Osobistego znajdujący się teraz w zatrzymanej lokalizacji GPS. Użyj **ⓘ**, aby wyświetlić przypomnienia o kontroli.

![Rozpocznij podróż](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Rozpoczęcie podróży jest zapisywane jako wiersze paliwa z **Typem podróży** (nie normalne wypełnienia). Pojawiają się one w obszarze **Raporty → Mile podróży**, a nie w Historii paliwa.

---

## <a id="reports"></a>Raporty

Menu → **Raporty** otwiera centrum produktów (podsumowanie wszechczasów + karty katalogowe). Jest to jedyna powierzchnia raportów o produktach — nie ma osobnej pozycji w szufladzie „Raporty i wykresy”.

![Centrum raportów](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Otwórz kartę trybu pojazdu (**Wszystkie / Każdy / Pojedynczy**), filtrów okresowych, wykresów i udostępniania (**TEKST / CSV / PDF**). Górny pasek na raportach podrzędnych: **☰ + ←** (oraz **ⓘ** po zarejestrowaniu).

### <a id="time-based-reports"></a>Raporty oparte na czasie

Główna karta wykresu. Opcjonalne wskaźniki (mpg, objętość/odległość, np. G/mi, cena jednostkowa, np. $/G, koszt/odległość, miesięczne $, mile podróży, % podróży według rodzaju) z **gładkimi** pojemnikami i **niezależnymi skalami Y** (po lewej stronie ekonomicznej; po prawej rodziny pieniędzy i podróży).

![Raporty oparte na czasie](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Raporty oparte na czasie — przewijane metryki](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Szczegóły matematyki ekonomicznej: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Historia napełniania a historia paliwa

- **Raporty → Historia wypełnień** — chronologiczne wypełnienia filtrów raportów (**tylko wypełnienia**; brak startów podróży).

![Wypełnij raport historii](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Historia paliwa** (jeśli jest dostępna w nawigacji Twojej kompilacji) — inwentaryzacja napełnienia według pojazdu, również tylko napełnienia; dotknij wiersza, który chcesz edytować.

### <a id="trip-miles"></a>Mile podróży

**Raporty → Mile podróży** — mile według typu, wykresy i chronologiczna **lista początków/segmentów podróży**. Kliknij prawdziwy początek, aby otworzyć **Edytuj wypełnienie** dla tego wiersza.

![Mile podróży](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Edytuj wypełnienie

W Historii wypełnień, Historii paliwa lub Przebytych milach otwórz tankowanie. Układ: pojazd i licznik kilometrów, **waluta przed kosztami**, objętość, notatki. Typ podróży pojawia się tylko wtedy, gdy wiersz jest początkiem podróży. Lokalizacja zawiera podsumowanie oraz **Szczegóły lokalizacji**. Brakuje lokalnego zdjęcia z tożsamością w chmurze: **Pobierz obraz z archiwum**.

![Edytuj wypełnienie — Pobierz z archiwum, gdy brakuje lokalnego zdjęcia](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Inne karty katalogowe obejmują wydatki według kategorii, podsumowanie pojazdu i listę wydatków.

Money używa waluty każdego wiersza, jeśli jest ustawiona. Sumy w walutach mieszanych pokazują **sumy częściowe dla poszczególnych walut** (bez cichego przeliczania walut).

---

## <a id="syncing"></a>Synchronizacja

Menu → **Synchronizacja** to centrum miejsc docelowych dla arkuszy kalkulacyjnych i zdjęć (nie tylko ukryte w Ustawieniach).

![Koncentrator synchronizacji](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Karty do **Synchronizacji arkusza kalkulacyjnego** i **Kopia zapasowa zdjęć** ze statusem krótkim, **Synchronizacja** dla tego rodzaju i **›** do listy miejsc docelowych.
– Otwórz miejsce docelowe dla **Połączenia testowego** i **Synchronizuj teraz (to miejsce docelowe)** / wszystko skonfigurowane.
- Awaria **Szczegóły** i czerwone **!** na pasku tytułu lądują tutaj.
– Szczegółowa konfiguracja Arkuszy Google i Dysku Google: [Kopie zapasowe i synchronizacja wielu urządzeń] (#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Ustawienia (preferencje lokalne)

Menu → **Ustawienia**.

![Ustawienia](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Więcej ustawień (przewijane)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

W przypadku miejsc docelowych wybierz **Menu → Synchronizacja**. Ustawienia mogą nadal wyświetlać wiersze podsumowań otwierające te same listy.

### <a id="local-preferences-common"></a>Lokalne preferencje (wspólne)

- **Zapisz zdjęcia rachunków za paliwo** / **Zapisz zdjęcia wydatków lokalnie** — przechowuj zdjęcia na urządzeniu (może poprosić o pozwolenie na wykonanie zdjęć).
— **Odtwórz dźwięk migawki**
- **Waluta** / **Jednostka objętości** — domyślne ustawienia aplikacji (systemowe lub jawne). Zmiana jednostki objętości przy użyciu istniejących danych dotyczących paliwa może spowodować wyświetlenie okna dialogowego konwersji.
– **Tryb ciemny**
- **Wskazówki dotyczące konfiguracji** — ponownie otwórz samouczki dotyczące pierwszego uruchomienia pojazdu/synchronizacji.
- **Wypełnienie debugowania** / **Pokaż ekrany eksperymentów (dev)** — zaawansowane; pozostawić do codziennego użytku. Ekrany eksperymentów nie są tutaj udokumentowane.

CSV **eksport/import** (kod pocztowy pojazdów / wydatków / zakładek paliwa) jest dostępny w Ustawieniach, jeśli jest dostępny w bieżącej wersji.

---

## <a id="help--about"></a>Pomoc i informacje

![Pomoc](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![O](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Pomoc** — szybki start na urządzeniu, samouczki dotyczące konfiguracji, łącze do tej instrukcji, indeks konfiguracji samodzielnego hosta.
- **O nas** — wersja, licencje, GitHub, ten podręcznik (w pakiecie offline + HTML online po opublikowaniu).

---

## <a id="related-docs"></a>Powiązane dokumenty

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — krótki przewodnik po życiu codziennym
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — konfiguracja zdjęć/tabeli na własnym serwerze
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — scalanie, odzyskiwanie, duplikaty
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — szczegóły wskaźników ekonomicznych
