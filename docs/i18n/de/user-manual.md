# <a id="vehicle-expenses-automated--user-manual"></a>Fahrzeugkosten automatisiert – Benutzerhandbuch

> **Quelle bearbeiten (Markdown).** Browser und der In-App-Reader öffnen den **gerenderten HTML**:
> - Web: [`docs/user-manual/index.html`](index.html) (neu generieren mit `./scripts/render-user-manual.sh`)
> - App: Hilfe / Info → vollständiges Handbuch (gebündeltes HTML + Screenshots)
>
> Weisen Sie Endbenutzer nicht auf rohe `.md` URLs hin – Browser zeigen nur einfachen Text an.

Kamerabasierte Verfolgung von Tankfüllungen und Fahrzeugkosten, mit optionaler Synchronisierung und Sicherung mehrerer Geräte unter **Ihren** Cloud-Konten.

Dies ist das **vollständige Handbuch** (Screenshots + jeder Schritt). Auf dem Telefon ist **Menü → Hilfe** eine kürzere Kurzanleitung.

**Hier nicht behandelt:** Alte Bilder importieren, Ausrichtungsexperiment und Pumpexperiment (Entwickler-/erweiterte Tools).

---

## <a id="table-of-contents"></a>Inhaltsverzeichnis

1. [Was Sie brauchen](#what-you-need)
2. [Symbole auf einen Blick](#icons-at-a-glance)
3. [Menü öffnen](#open-the-menu)
4. [Ersteinrichtung: Fahrzeuge verwalten](#first-time-setup-manage-vehicles)
5. [Backups und Synchronisierung mehrerer Geräte](#backups-and-multi-device-sync)
6. [Füllen (Kraftstoff)](#fill-up-fuel)
7. [Fahrt starten](#start-trip)
8. [Ausgaben](#expenses)
9. [Berichte](#reports)
10. [Einstellungen (lokale Präferenzen)](#settings-local-preferences)
11. [Synchronisierung](#syncing)
12. [Hilfe und Info](#help--about)
13. [Verwandte Dokumente](#related-docs)

---

## <a id="what-you-need"></a>Was Sie brauchen

– Android-Telefon oder -Tablet.
– Für beste OCR: eine klare Sicht auf Ihren **Armaturenbrett-Kilometerzähler** und **Pumpengesamtwerte** (oder geben Sie die Zahlen von Hand ein).
– Optional: Konten **von Ihnen kontrolliert** für Tabellenkalkulationsdaten und/oder Fotosicherung (siehe [Sicherungen und Synchronisierung mehrerer Geräte](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Icons auf einen Blick

Diese erscheinen auf den Hauptbildschirmen. Wenn man sie kennt, erspart man sich viel Jagd.

| Wo | Symbol / Steuerung | Was es tut |
|-------|----------------|--------------|
| Obere Leiste | **☰ Menü** (Hamburger) | Öffnet die Navigationsleiste |
| Obere Leiste | **ⓘ** (Seitenhilfe) | Kurzhilfe zur **aktuellen** Seite (neben dem Menü, sofern verfügbar) |
| Obere Leiste | **`?N`** (gelb) | Ausstehende Importüberprüfungsfragen – öffnet Importüberprüfung |
| Obere Leiste | **!** (rot) | Eine Tabellenkalkulation oder ein Fotoziel ist kürzlich fehlgeschlagen. Öffnen Sie **Synchronisierung**, um das Problem zu beheben
| Obere Leiste | **☰ + ←** | Kinder melden und Spesenliste zeigen **Menü und zurück** zusammen an; Der Berichts-Hub ist nur im Menü verfügbar |
| Einstellungen / Kraftstoff bearbeiten | **←** | Zurück (Einstellungen, Tabellenkalkulation/Foto und Kraftstoffbearbeitung bleiben im Fokus) |
| Füllen | **Weißer Kreis** (Verschluss) | Erfassen Sie die Kilometerzähler- oder Pumpenanzeige für OCR |
| Füllen | **Datenträger / Speichern** | Sparen Sie das Tanken (benötigt ein Fahrzeug und mindestens eines von odo / Volumen / Kosten) |
| Füllen | **↕ Pfeile** (Modusschalter) | Schalten Sie zwischen **Kilometerzählermodus** und **Pumpenmodus (Kosten/Volumen)** um. Der grüne Rand markiert die aktive Feldgruppe |
| Füllen | **↔ Pfeile** (zwischen Kosten und Volumen) | Kosten und Volumen vertauschen, wenn OCR sie in die falschen Felder einfügt |
| Füllen | **Zoom 1x / …** | Zoomverhältnisse der Kamera, wenn das Objektiv sie unterstützt |
| Füllen (nach der Erfassung) | **Aktualisieren** auf der Hauptschaltfläche | Vorschau verwerfen und zur Live-Kamera zurückkehren |
| Füllen (während der Verarbeitung) | **X** auf der Hauptschaltfläche | Laufende Erfassung/OCR abbrechen |
| Aufwand | **Speichern** | Sparen Sie die Kosten |
| Aufwand | **Verschlusskreis** | Machen Sie ein Quittungsfoto |
| Aufwand | **Galerie** | Wählen Sie ein Belegbild aus der Bibliothek aus |
| Aufwand | **Wiederholung** | Aktuelles Belegfoto löschen und erneut aufnehmen |
| Kosten / Fahrzeuge verwalten | **+ / −** FABs | Zoomen Sie die Fotovorschau |
| Dialogfeld „Sehenswürdigkeiten“ | **OCR bearbeiten** | Korrigieren oder fügen Sie wegweisenden Text hinzu, den die Engines übersehen haben |
| Tabellenkalkulation / Fotoformulare | **🔍Suche** | Durchsuchen Sie Google Drive nach einem Blatt oder Ordner (nach der Anmeldung) |

Währungssymbole auf Kostenfeldern und **G/L** auf Volumenfeldern können angetippt werden: Öffnen Sie ein kleines Menü, um die Währung oder Gallonen vs. Liter für diesen Eintrag zu ändern.

---

## <a id="open-the-menu"></a>Öffnen Sie das Menü

1. Tippen Sie oben links auf **☰**.
2. Wählen Sie eine Seite.

![Navigationsleiste](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Hauptschublade:** Ausfüllen · Fahrt starten · Fahrzeuge verwalten · Neue Ausgaben · **Berichte** · Einstellungen · Synchronisierung · Hilfe · Info.

**Experimentschublade** (Einstellungen → Experimentbildschirme anzeigen): Ausrichtungsexperiment · Pumpenexperiment · **Alte Bilder importieren**.

**Über den Berichts-Hub (nicht in der Hauptschublade):** Ausgabenliste · Verlauf ausfüllen.

---

## <a id="first-time-setup-manage-vehicles"></a>Ersteinrichtung: Fahrzeuge verwalten

OCR und **automatischer Fahrzeugabgleich** funktionieren am besten, nachdem Sie jedes Fahrzeug mit einem **Referenz-Dashboard-Foto** registriert haben, **Discovery** ausgeführt haben, den Kilometerzähler-Ausschnitt anhand eines Paddel-Seeds festgelegt und Orientierungspunkte für diesen Armaturenbrett gespeichert haben.

### <a id="open-manage-vehicles"></a>Öffnen Sie „Fahrzeuge verwalten“.

Menü → **Fahrzeuge verwalten**. Wählen Sie ein Fahrzeug (oder **Neues Fahrzeug hinzufügen**).

![Fahrzeuge mit Referenzstrich, Ernten und Entdeckung verwalten](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Ein Fahrzeug hinzufügen oder bearbeiten

1. Öffnen Sie das Dropdown-Menü **Fahrzeug** → wählen Sie ein Fahrzeug aus oder **Neues Fahrzeug hinzufügen**.
2. Nehmen Sie ein klares **Referenz-Armaturenbrettfoto** auf oder wählen Sie es aus (komplettes Kombiinstrument, gut beleuchtet, Telefon ungefähr im rechten Winkel). Verwenden Sie **Foto aufnehmen** oder **Galerie**.
3. Tippen Sie auf **Erkennung ausführen** – die Paddelerkennung platziert gelbe **Samen** auf dem Foto (Sehenswürdigkeiten verwenden dasselbe `"ML Kit"` JSON wie zuvor).
4. Tippen Sie auf **Odo auswählen**, tippen Sie auf den Startwert auf den Ziffern, passen Sie die Ziehpunkte an und klicken Sie dann auf **Speichern**.
5. **Edit Odo** passt eine Box an, die bereits vorhanden ist. **Clear Odo** entfernt es und die Schaltfläche lautet dann **Select Odo**.
6. Überprüfen Sie mit **Sehenswürdigkeiten anzeigen**. Verwenden Sie **OCR bearbeiten**, um Lesefehler zu korrigieren oder übersehenen Text hinzuzufügen.
7. Geben Sie **Fahrzeugname** (erforderlich) sowie Marke/Modell/Jahr/Kennzeichen nach Wunsch ein.
8. Tippen Sie auf **Fahrzeug erstellen** oder **Änderungen speichern** (für ein neues Fahrzeug sind Name und Referenzfoto erforderlich).

![Neue Fahrzeugformularfelder hinzufügen](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Sehenswürdigkeiten: Korrigieren Sie, was Discovery übersehen hat

Scrollen Sie nach **Sehenswürdigkeiten anzeigen** durch die Liste und korrigieren Sie die Werte. Bei Motoren fehlen manchmal kleine Ziffern (z. B. eine Uhr **60** unten rechts im Cluster). Verwenden Sie **OCR bearbeiten**, um sie hinzuzufügen oder zu korrigieren, damit die Fahrzeugidentität zuverlässig bleibt.

![Orientierungsdialog – OCR überprüfen und bearbeiten](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Tippen ohne ein perfektes Foto

Sie können die App weiterhin verwenden, indem Sie ein Fahrzeug auswählen und beim Füllen Kilometerzähler, Volumen und Kosten **eingeben** – OCR ist für jedes Feld optional. Der Galerieimport funktioniert für das Referenz-Dash-Foto, wenn Sie nicht in der App fotografieren möchten.

**Tipp:** Nach der Tabellensynchronisierung bleiben die Fahrzeugdefinitionen (Kulturpflanzen, Orientierungspunkte) in der lokalen Datenbank gespeichert – Sie müssen „Fahrzeuge für Befüllung verwalten“ nicht erneut öffnen, um sie zu verwenden.

---

## <a id="backups-and-multi-device-sync"></a>Backups und Synchronisierung mehrerer Geräte

Die App ist so konzipiert, dass **mehrere Telefone oder Tablets dieselben Flottendaten teilen können** und dass Sie eine **Kopie Ihrer Daten und Fotos vom Gerät fernhalten können**. Dies erfolgt über Ziele, die Sie unter **Ihren** Konten oder **Ihren** selbst gehosteten Servern konfigurieren – und nicht über eine vom Unternehmen betriebene „Fahrzeugkosten-Cloud“, die andere Personen sehen können.

### <a id="what-runs-where"></a>Was läuft wo

| Freundlich | Was es speichert | Typische Verwendung |
|------|----------------|-------------|
| **Tabellenkalkulation/Tabellensynchronisierung** | Fahrzeuge, Tankfüllungen, Ausgaben (Zeilen und Registerkarten) | Zusammenführung mehrerer Geräte + strukturiertes Backup |
| **Fotosicherung** | Binärbilder (Armaturenbrett/Pumpe/Quittung/Referenzfotos) | Fotosicherung + fehlende Dateien wiederherstellen |

Sie können **mehrere Ziele** jeder Art konfigurieren (Soft-Cap pro Typ). Manuelle **Jetzt synchronisieren**- und **Hintergrund**-Worker führen die aktivierten Worker aus.

### <a id="offline-first"></a>Zuerst offline

– **Es ist kein Netzwerk erforderlich**, um eine Tankfüllung, eine Ausgabe oder einen Beleg hinzuzufügen. Alles wird **zuerst lokal** gespeichert.
– Wenn das Netzwerk verfügbar ist, werden Synchronisierung und Fotosicherung als **Hintergrundaufgaben** ausgeführt (nach einem von Ihnen festgelegten Zeitplan und wenn Sie auf **Jetzt synchronisieren** tippen). Fehler werden als roter Text unter den Einstellungszeilen und als **!** in der Titelleiste der App angezeigt.

### <a id="your-accounts-only"></a>Nur Ihre Konten

Anmeldung und Token bleiben für die von Ihnen ausgewählten Anbieter (Google, Microsoft, S3-Schlüssel, selbst gehostete URLs usw.) auf dem Gerät. Die Ziele unterliegen der **vollständigen Kontrolle des Benutzers** – Ihrem Google-Konto, Ihrem OneDrive, Ihrem MinIO-Bucket, Ihrem EtherCalc-Host usw. Über ein gemeinsames Backend wird nichts mit anderen Vehicle Expenses-Benutzern geteilt.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Unterstützte Ziele – Daten (Tabellenkalkulation/Tabelle)

Konfiguriert unter **Menü → Synchronisierung → Tabellensynchronisierung** (auch über die Zusammenfassungszeilen der Einstellungen erreichbar). Erstklassige Picker-Optionen:

| Ziel | Notizen |
|--------|--------|
| **Google Sheets** | Gemeinsamer Standard; Registerkarten für Fahrzeuge, Ausgaben und Kraftstoff pro Fahrzeug |
| **Excel** | Microsoft-Arbeitsmappe über Graph-/OneDrive-Bindung |
| **EtherCalc** | Selbstgehostete kollaborative Tabellenkalkulationsräume |
| **Andere →** implementierte Backends | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Zurückgestellt/noch nicht Headless (unter Andere aufgeführt, aber nicht vollständig implementiert): OnlyOffice, Collabora. Siehe auch [Selbsthost-Index](reference/self-host/INDEX.md).

CSV **Export/Import** (ZIP mit demselben Tab-Layout) ist in den Einstellungen als tragbares Backup verfügbar, unabhängig von der Live-Synchronisierung.

### <a id="supported-targets--photos-image-backup"></a>Unterstützte Ziele – Fotos (Bildsicherung)

Konfiguriert unter **Menü → Synchronisierung → Fotosicherung** (auch aus den Zusammenfassungszeilen der Einstellungen):

| Ziel | Notizen |
|--------|--------|
| **Google Drive** | Von Ihnen ausgewählter Ordner (URL durchsuchen oder einfügen) |
| **OneDrive** | Microsoft-Konto + Pfadpräfix |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO und andere S3-kompatible Endpunkte |
| **Andere** | rclone-gestützter Speicher (z. B. WebDAV, SFTP und andere kuratierte Remotes, die in der In-App-Auswahl verfügbar sind) |

Richten Sie Cheatsheets für selbst gehostete Foto- und Tabellenziele ein: [Selbsthost-Index](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Multi-Device-Verhalten (kurz)

– Zeilen werden nach **Sync-ID** mit **Last-Write-Wins** auf **Aktualisierten** Zeitstempeln zusammengeführt.
– Löschvorgänge sind weich; Eine neuere Bearbeitung auf einem anderen Gerät kann eine Zeile wiederherstellen.
– Wenn Sie auf zwei Geräten die **gleiche Füllung zweimal** eingeben, entstehen **zwei Zeilen** – löschen Sie die zusätzlichen Zeilen, wenn Sie es bemerken.
– Weitere Details: [Hinweise zum Synchronisierungsverhalten](#sync-behavior-notes) und [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Beispiel: Google Sheets (Daten) hinzufügen

1. **Menü → Synchronisierung → Tabellenkalkulationssynchronisierung** (oder Einstellungen → Tabellenkalkulationssynchronisierung).

![Tabellen-Synchronisierungsliste](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Tippen Sie auf **Tabellenziel hinzufügen**.

![Anbieterauswahl](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Wählen Sie **Google Sheets**.

![Google Sheets-Formular](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Einmal mit Google anmelden** – mit dieser Anmeldung können Sie auch surfen. Anzeigename → Fügen Sie eine **Tabellen-URL** ein (noch gültig) oder **🔍** durchsuchen. Durchsuchen wird unter **Alle & geteilt** geöffnet, sodass Sie eine vorhandene Tabelle auswählen können, die Ihnen gehört oder die mit Ihnen geteilt wurde. Dann Zeitplanoptionen → Aktivieren → Speichern.
5. **Jetzt synchronisieren** einmal, um Tabs zu erstellen/aktualisieren: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Beispiel: Google Drive hinzufügen (Fotos)

1. **Menü → Synchronisierung → Fotosicherung** (oder Einstellungen → Fotosicherung).

![Foto-Backup-Liste](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Tippen Sie auf **Fotoziel hinzufügen**.

![Fotoanbieter-Auswahl](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Wählen Sie **Google Drive**.

![Google Drive-Formular](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Mit Google (Drive) anmelden** → optionale Ordner-URL/Durchsuchen → Aktivieren → Speichern → **Jetzt synchronisieren**.

Manuell **Jetzt synchronisieren** für Fotos ist ein vollständiger Durchgang; Die Hintergrundsicherung verarbeitet normalerweise **nur ausstehende** Uploads nach einem Zeitplan.

### <a id="sync-behavior-notes"></a>Verhaltensnotizen synchronisieren

– Nach dem App-Upgrade wird möglicherweise kurz **„Datenbank wird nach Upgrade aktualisiert…“** (lokales Synchronisierungs-ID-Backfill) angezeigt.
– Wenn eine Synchronisierung unterbrochen wird, werden bei der nächsten **erfolgreichen** Synchronisierung Remote-Tabs erneut zusammengeführt und repariert.
– Fehler: rote Zusammenfassung beim Synchronisieren von Karten + **!** in der App-Leiste.

---

## <a id="fill-up-fuel"></a>Füllen (Kraftstoff)

Dies ist der **Startbildschirm**, wenn Sie die App öffnen.

### <a id="vehicle-selection-usually-automatic"></a>Fahrzeugauswahl (normalerweise automatisch)

Sie müssen **nicht** zuerst das Fahrzeug auswählen. Wenn für Fahrzeuge in „Fahrzeuge verwalten“ **Orientierungspunkte** eingerichtet sind, erkennt Fill **automatisch, welches Fahrzeug** anhand des Armaturenbrettbilds vorliegt, nachdem Sie den Kilometerzähler erfasst haben. Sie können weiterhin das Dropdown-Menü **Fahrzeug** öffnen, um es bei Bedarf zu überschreiben.

### <a id="aim-at-the-odometer"></a>Zielen Sie auf den Kilometerzähler

Bleiben Sie im Kilometerzählermodus und rahmen Sie den Cluster ein. Anleitung: *Zielen Sie auf den Kilometerzähler. Tippen Sie zum Aufnehmen auf den Auslöser.*

![Füllen – Live-Kamera auf Kilometerzähler (Beispiel-Dash-Bild in der Vorschau)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Nach dem Kilometerzählerverschluss

OCR füllt **Odo** aus und versucht, das Fahrzeug anhand von Orientierungspunkten abzugleichen (überprüfen Sie bei Bedarf beide). Die Hauptschaltfläche wird zu **Wiederholen**, um erneut zu schießen. Die Anleitung fasst die Lektüre zusammen.

![Füllen – OCR-Ergebnis des Kilometerzählers](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Pumpmodus (Kosten und Volumen)

1. Tippen Sie auf **↕**, um in den Pumpenmodus zu wechseln: *Zielen Sie auf die Pumpenanzeige (Kosten/Volumen). Tippen Sie auf den Auslöser.*
2. Erfassen Sie die Pumpensummen. Kosten- und Volumenfelder ausfüllen; Verwenden Sie **↔**, wenn sie vertauscht sind.
3. Tippen Sie bei Bedarf auf Währung oder **Sachbuch** und dann auf **Speichern** (Datenträger). Leere Felder führen zu einer **teilweisen Füllung** (immer noch zulässig).

![Füllen – Pumpenvorschau/Summen (Beispielpumpenbild in der Vorschau)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Sie bleiben beim Füllen für den nächsten Stopp (Felder werden nach dem Speichern gelöscht). Vollständig **offline** arbeiten; Die Synchronisierung wird bei entsprechender Konfiguration später im Hintergrund ausgeführt.

### <a id="manual-entry-no-camera--bad-ocr"></a>Manuelle Eingabe (keine Kamera / schlechte OCR)

1. Tippen Sie auf **Odo**, **Kosten** oder **Volume** und geben Sie Werte ein (Hochformat verwendet die Systemtastatur, Querformat verwendet eine Bildschirmtastatur).
2. Wählen oder bestätigen Sie das **Fahrzeug**, wenn die automatische Erkennung nicht ausgeführt wurde.
3. Speichern Sie wie oben.

### <a id="modes-and-borders"></a>Modi und Grenzen

- **Grüner Rand** um Fahrzeug+odo → Kilometerzähler erfassen/bearbeiten.
– **Grüner Rand** um Kosten+Volumen → Pumpmodus.
– **Speichern** bleibt deaktiviert, bis ein Fahrzeug ausgewählt wird und mindestens eines von Kilometer/Kosten/Volumen Daten hat und OCR nicht noch läuft.

Tipp auf dem Bildschirm (unter der Anweisungszeile): *Auslöser = Aufnahme · Festplatte = Speichern · ↕ = Odo-/Pumpmodus · ↔ = Kosten/Volumen tauschen.*

---

## <a id="expenses"></a>Kosten

### <a id="new-expense"></a>Neue Ausgabe

Menü → **Neue Ausgabe**.

![Neues Spesenformular und Kamerasteuerung (Beispiel: Strichbild im Kameraband, wenn es leer ist)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Speichern** (Datenträger), **Auslöser** (Quittungsfoto) oder **Galerie** (Bild auswählen).
2. Geben Sie **Datum**, **Fahrzeug**, **Anbieter**, **Beschreibung**, **Betrag** (Währungssymbol antippbar), **Kategorie**, optional **Kilometerzähler** ein.
3. Mehrseitige Belege: Erfassen Sie zusätzliche Seiten, wenn die Benutzeroberfläche Paging anbietet (Seite 0 ist der primäre Beleg).
4. **Speichern** zum Speichern (zuerst lokal; Fotosicherung und Tabellensynchronisierung erfolgen bei Konfiguration im Hintergrund).

### <a id="expense-list"></a>Kostenliste

Menü → **Berichte** → **Ausgabenliste** – durchsuchen Sie vergangene Ausgaben, die nicht mit Treibstoff zusammenhängen; Öffnen Sie ein Element zum Bearbeiten.

![Kostenliste](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Ausgabe bearbeiten

Öffne eine Zeile aus der Liste. Korrigieren Sie Anbieter, Menge, Kategorie, Fahrzeug und Beschreibung. Wenn sich die Quittung nur in der Fotosicherung befindet (keine lesbare lokale Datei), verwenden Sie **Bild aus Archiv abrufen**, wenn angezeigt (funktioniert für alle konfigurierten Fotoziele).

![Ausgabe bearbeiten](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Reise starten

Menü → **Fahrt starten** (nachdem Sie die Schublade ausfüllen). Erfassen oder geben Sie den Kilometerzähler ein, wählen Sie die Fahrtart und speichern Sie sie mit dem **Diskettensymbol**. **Stop** ist eine Abkürzung für „Persönlich jetzt“ am gehaltenen GPS-Standort. Verwenden Sie **ⓘ** für Kontrollerinnerungen.

![Reise starten](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Fahrtstarts werden als Kraftstoffzeilen mit einem **Fahrttyp** gespeichert (keine normalen Füllungen). Sie erscheinen unter **Berichte → Fahrtmeilen**, nicht unter Kraftstoffverlauf.

---

## <a id="reports"></a>Berichte

Menü → **Berichte** öffnet den Produkt-Hub (allzeitige Zusammenfassung + Katalogkarten). Dies ist die einzige Produktberichtsoberfläche – es gibt kein separates Schubladenelement „Berichte und Diagramme“.

![Berichts-Hub](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Öffnen Sie eine Karte für den Fahrzeugmodus (**Alle / Jeder / Einzeln**), Zeitraumfilter, Diagramme und Teilen (**TEXT / CSV / PDF**). Obere Leiste bei Berichtskindern: **☰ + ←** (und **ⓘ** bei Registrierung).

### <a id="time-based-reports"></a>Zeitbasierte Berichte

Die Hauptkartenkarte. Optionale Metriken (mpg, Volumen/Entfernung wie G/mi, Stückpreis wie $/G, Kosten/Entfernung, monatliche $, Reisemeilen, Reise % nach Typ) mit **glatten** Bins und **unabhängigen Y-Skalen** (Economy links; Geld- und Reisefamilien rechts).

![Zeitbasierte Berichte](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Zeitbasierte Berichte – gescrollte Metriken](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Details zur Wirtschaftsmathematik: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Füllverlauf vs. Kraftstoffverlauf

– **Berichte → Ausfüllverlauf** – chronologische Ausfüllungen für die Berichtsfilter (**nur Ausfüllungen**; keine Reisestarts).

![Verlaufsbericht ausfüllen](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

– **Kraftstoffhistorie** (falls in der Navigation Ihres Builds vorhanden) – Füllungsinventar pro Fahrzeug, auch nur Füllungen; Tippen Sie auf eine Zeile, um sie zu bearbeiten.

### <a id="trip-miles"></a>Reisemeilen

**Berichte → Reisemeilen** – Meilen nach Typ, Diagramme und eine chronologische **Reisestart-/Segmentliste**. Tippen Sie auf einen echten Anfang, um **Füllung bearbeiten** für diese Zeile zu öffnen.

![Reisemeilen](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Füllung bearbeiten

Öffnen Sie unter Füllhistorie, Kraftstoffhistorie oder Fahrtmeilen eine Füllung. Layout: Fahrzeug und Kilometerzähler, **Währung vor Kosten**, Volumen, Notizen. Der Reisetyp wird nur angezeigt, wenn es sich bei der Zeile um einen Reisebeginn handelt. Der Standort verfügt über eine Zusammenfassung sowie **Standortdetails**. Fehlendes lokales Foto mit Cloud-Identität: **Bild aus Archiv abrufen**.

![Füllung bearbeiten – Aus Archiv abrufen, wenn lokales Foto fehlt](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Andere Katalogkarten enthalten Ausgaben nach Kategorie, eine Fahrzeugübersicht und eine Ausgabenliste.

Money verwendet die Währung jeder Zeile, wenn diese festgelegt ist. Gesamtsummen in gemischten Währungen zeigen **Zwischensummen pro Währung** (keine stille Devisenumrechnung).

---

## <a id="syncing"></a>Synchronisierung

Menü → **Synchronisierung** ist die Zentrale für Tabellenkalkulations- und Fotoziele (nicht nur unter „Einstellungen“ vergraben).

![Synchronisierungs-Hub](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

– Karten für **Tabellenkalkulation** und **Fotosicherung** mit Kurzstatus, **Synchronisierung** für diese Art und **›** in die Zielliste.
– Öffnen Sie ein Ziel für **Verbindung testen** und **Jetzt synchronisieren (dieses Ziel)** / alles konfiguriert.
– Fehler **Details** und das rote **!** in der Titelleiste landen hier.
– Schritt-für-Schritt-Einrichtung von Google Sheets und Drive: [Backups und Synchronisierung mehrerer Geräte] (#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Einstellungen (lokale Präferenzen)

Menü → **Einstellungen**.

![Einstellungen](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Weitere Einstellungen (gescrollt)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Für Ziele bevorzugen Sie **Menü → Synchronisierung**. In den Einstellungen werden möglicherweise weiterhin Zusammenfassungszeilen angezeigt, die dieselben Listen öffnen.

### <a id="local-preferences-common"></a>Lokale Vorlieben (häufig)

– **Fotos von Kraftstoffbelegen speichern** / **Fotos von Spesen lokal speichern** – Bilder auf dem Gerät behalten (möglicherweise wird eine Fotoerlaubnis angefordert).
– **Auslöserton abspielen**
– **Währung** / **Volumeneinheit** – App-Standardeinstellungen (System oder explizit). Beim Ändern der Volumeneinheit mit vorhandenen Kraftstoffdaten wird möglicherweise ein Konvertierungsdialog angezeigt.
– **Dunkler Modus**
– **Setup-Tipps** – Öffnen Sie die Tutorials zum ersten Fahrzeug/Synchronisierung erneut.
– **Debug Fill** / **Show experiment screens (dev)** – erweitert; Für den täglichen Gebrauch weglassen. Experimentierbildschirme werden hier nicht dokumentiert.

CSV **Export/Import** (Postleitzahl der Registerkarten „Fahrzeuge/Ausgaben/Kraftstoff“) ist in den Einstellungen verfügbar, sofern dies vom aktuellen Build angeboten wird.

---

## <a id="help--about"></a>Hilfe und Info

![Hilfe](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Über](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

– **Hilfe** – Schnellstart auf dem Gerät, Setup-Tutorials, Link zu diesem Handbuch, Self-Host-Setup-Index.
– **Info** – Version, Lizenzen, GitHub, dieses Handbuch (offline gebündelt + Online-HTML bei Veröffentlichung).

---

## <a id="related-docs"></a>Verwandte Dokumente

– [QUICK_GUIDE.md](../QUICK_GUIDE.md) – kurzer Leitfaden für den Alltag
– [self-host/INDEX.md](../reference/self-host/INDEX.md) – selbstgehostetes Foto-/Tabellen-Setup
– [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) – Zusammenführung, Wiederherstellung, Duplikate
– [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) – Detail der Wirtschaftskennzahlen
