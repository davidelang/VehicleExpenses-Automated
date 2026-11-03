# <a id="vehicle-expenses-automated--user-manual"></a>Dépenses de véhicule automatisées — Manuel d'utilisation

> **Modifier la source (Markdown).** Les navigateurs et le lecteur intégré à l'application ouvrent le **HTML rendu** :
> - Web : [`docs/user-manual/index.html`](index.html) (régénérer avec `./scripts/render-user-manual.sh`)
> - Application : Aide / À propos → manuel complet (HTML + captures d'écran fournies)
>
> Ne dirigez pas les utilisateurs finaux vers des URL brutes `.md` — les navigateurs affichent uniquement du texte brut.

Suivi par caméra pour les pleins de carburant et les dépenses du véhicule, avec synchronisation et sauvegarde multi-appareils en option sous **vos** comptes cloud.

Ceci est le **manuel complet** (captures d'écran + chaque étape). Sur le téléphone, **Menu → Aide** est un guide de démarrage plus court.

**Non couvert ici :** Importez d'anciennes images, une expérience d'alignement et une expérience de pompe (développeur/outils avancés).

---

## <a id="table-of-contents"></a>Table des matières

1. [Ce dont vous avez besoin](#what-you-need)
2. [Icônes en un coup d'œil](#icons-at-a-glance)
3. [Ouvrir le menu](#open-the-menu)
4. [Première configuration : gérer les véhicules](#first-time-setup-manage-vehicles)
5. [Sauvegardes et synchronisation multi-appareils](#backups-and-multi-device-sync)
6. [Remplir (carburant)](#fill-up-fuel)
7. [Démarrer le voyage](#start-trip)
8. [Dépenses](#expenses)
9. [Rapports](#reports)
10. [Paramètres (préférences locales)](#settings-local-preferences)
11. [Synchronisation](#syncing)
12. [Aide et à propos](#help--about)
13. [Documents associés](#related-docs)

---

## <a id="what-you-need"></a>Ce dont vous avez besoin

- Téléphone ou tablette Android.
- Pour un meilleur OCR : une vue claire de votre **compteur kilométrique de tableau de bord** et de vos **totaux de pompe** (ou tapez les chiffres à la main).
- Facultatif : comptes **que vous contrôlez** pour les données de feuille de calcul et/ou la sauvegarde de photos (voir [Sauvegardes et synchronisation multi-appareils](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Icônes en un coup d'œil

Ceux-ci apparaissent sur les écrans principaux. Les connaître évite beaucoup de chasse.

| Où | Icône / contrôle | Ce qu'il fait |
|-------|----------------|--------------|
| Barre supérieure | **☰ Menu** (hamburger) | Ouvre le tiroir de navigation |
| Barre supérieure | **ⓘ** (page d'aide) | Aide courte pour la page **actuelle** (à côté du menu lorsqu'il est disponible) |
| Barre supérieure | **`?N`** (jaune) | Questions de révision d'importation en attente — ouvre la révision d'importation |
| Barre supérieure | **!** (rouge) | Une feuille de calcul ou une destination de photo a récemment échoué : ouvrez **Synchronisation** pour corriger |
| Barre supérieure | **☰ + ←** | Le rapport sur les enfants et la liste des dépenses affichent ** le menu et l'arrière ** ensemble ; Le hub de rapports est un menu uniquement |
| Paramètres / carburant modifier | **←** | Retour (la feuille de calcul des paramètres/les modifications des photos et du carburant restent centrées sur l'arrière) |
| Remplir | **Cercle blanc** (obturateur) | Capturer l'affichage du compteur kilométrique ou de la pompe pour OCR |
| Remplir | **Disque / Enregistrer** | Économisez le plein (nécessite un véhicule et au moins un parmi odo / volume / coût) |
| Remplir | **↕ flèches** (commutateur de mode) | Basculez entre le **mode compteur kilométrique** et le **mode pompe (coût/volume)**. La bordure verte met en évidence le groupe de champs actif |
| Remplir | **↔ flèches** (entre coût et volume) | Échangez le coût et le volume si l'OCR les place dans les mauvais champs |
| Remplir | **Zoom 1x / …** | Rapports de zoom de l'appareil photo lorsque l'objectif les prend en charge |
| Remplir (après capture) | **Actualiser** sur le bouton principal | Supprimer l'aperçu et revenir à la caméra en direct |
| Remplir (pendant le traitement) | **X** sur le bouton principal | Annuler la capture/OCR en cours |
| Dépense | **Enregistrer** | Économisez les dépenses |
| Dépense | **Cercle d'obturation** | Prendre une photo du reçu |
| Dépense | **Galerie** | Choisissez une image de reçu dans la bibliothèque |
| Dépense | **Reprendre** | Effacer la photo du reçu actuel et prendre à nouveau |
| Dépenses / Gérer les véhicules | **+ / −** FAB | Zoomer l'aperçu de la photo |
| Boîte de dialogue Points de repère | **Modifier l'OCR** ​​| Corriger ou ajouter un texte de repère que les moteurs ont manqué |
| Feuille de calcul / Formulaires photo | **🔍 Recherche** | Parcourez Google Drive pour une feuille ou un dossier (après la connexion) |

Les symboles monétaires sur les champs de coût et **G/L** sur les champs de volume sont accessibles : ouvrez un petit menu pour changer la devise ou les gallons par rapport aux litres pour cette entrée.

---

## <a id="open-the-menu"></a>Ouvrez le menu

1. Appuyez sur **☰** en haut à gauche.
2. Choisissez une page.

![Tiroir de navigation](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Tiroir principal :** Remplir · Commencer le voyage · Gérer les véhicules · Nouvelles dépenses · **Rapports** · Paramètres · Synchronisation · Aide · À propos.

**Tiroir d'expériences** (Paramètres → Afficher les écrans d'expérience) : Expérience d'alignement · Expérience de pompe · **Importer d'anciennes images**.

**Via le hub Rapports (pas le tiroir principal) :** Liste des dépenses · Remplir l'historique.

---

## <a id="first-time-setup-manage-vehicles"></a>Première configuration : gérer les véhicules

L'OCR et la **correspondance automatique des véhicules** fonctionnent mieux après avoir enregistré chaque véhicule avec une **photo de tableau de bord de référence**, exécuté **Discovery**, défini le recadrage du compteur kilométrique à partir d'une graine de pagaie et enregistré les points de repère pour ce tableau de bord.

### <a id="open-manage-vehicles"></a>Ouvrir Gérer les véhicules

Menu → **Gérer les véhicules**. Choisissez un véhicule (ou **Ajouter un nouveau véhicule**).

![Gérer les véhicules avec tableau de bord de référence, cultures et découverte](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Ajouter ou modifier un véhicule

1. Ouvrez le menu déroulant **Véhicule** → choisissez un véhicule ou **Ajouter un nouveau véhicule**.
2. Capturez ou choisissez une **photo de tableau de bord de référence** claire (groupe d'instruments complet, bien éclairé, téléphone à peu près carré). Utilisez **Prendre une photo** ou **Galerie**.
3. Appuyez sur **Exécuter Discovery** — la détection de pagaie place des **graines** jaunes sur la photo (les points de repère utilisent le même `"ML Kit"` JSON qu'avant).
4. Appuyez sur **Sélectionner Odo**, appuyez sur la graine sur les chiffres, ajustez les poignées, puis **Enregistrer**.
5. **Modifier Odo** ajuste une boîte qui est déjà là. **Effacer Odo** le supprime, et le bouton indique alors **Sélectionner Odo**.
6. Vérifiez avec **Afficher les points de repère**. Utilisez **Modifier OCR** ​​pour corriger les erreurs de lecture ou **ajouter** du texte manqué.
7. Remplissez **Nom du véhicule** (obligatoire), plus marque/modèle/année/plaque comme vous le souhaitez.
8. Appuyez sur **Créer un véhicule** ou **Enregistrer les modifications** (nécessite un nom + une photo de référence pour un nouveau véhicule).

![Ajouter de nouveaux champs de formulaire de véhicule](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Points de repère : corrigez ce que Discovery a manqué

Après **Afficher les points de repère**, faites défiler la liste et corrigez les valeurs. Les moteurs manquent parfois de petits chiffres (par exemple une horloge **60** en bas à droite du cluster). Utilisez **Modifier OCR** ​​pour les ajouter ou les corriger afin que l'identité du véhicule reste fiable.

![Boîte de dialogue Points de repère — consulter et modifier l'OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Tap sans photo parfaite

Vous pouvez toujours utiliser l'application en sélectionnant un véhicule et en **saisissant** le compteur kilométrique, le volume et le coût sur Remplissage - l'OCR est facultatif pour chaque champ. L'importation de galerie fonctionne pour la photo du tableau de bord de référence lorsque vous préférez ne pas prendre de photo dans l'application.

**Conseil :** Après la synchronisation de la feuille de calcul, les définitions de véhicules (cultures, points de repère) résident dans la base de données locale — vous n'avez pas besoin de rouvrir la gestion des véhicules à remplir pour les utiliser.

---

## <a id="backups-and-multi-device-sync"></a>Sauvegardes et synchronisation multi-appareils

L'application est conçue pour que **plusieurs téléphones ou tablettes puissent partager les mêmes données de flotte**, et ainsi vous pouvez conserver une **copie de vos données et photos hors de l'appareil**. Cela se fait avec les destinations **que** configurez sous **vos** comptes ou **vos** serveurs auto-hébergés – et non avec un « cloud des dépenses de véhicule » géré par l'entreprise que d'autres personnes peuvent voir.

### <a id="what-runs-where"></a>Qu'est-ce qui court où

| Genre | Ce qu'il stocke | Utilisation typique |
|------|------|-------------|
| **Feuille de calcul/synchronisation tabulaire** | Véhicules, pleins de carburant, dépenses (lignes et onglets) | Fusion multi-appareils + sauvegarde structurée |
| **Sauvegarde de photos** | Images binaires (tableau de bord/pompe/reçu/photos de référence) | Sauvegarde de photos + restauration des fichiers manquants |

Vous pouvez configurer **plusieurs destinations** de chaque type (plafond souple par type). Les travailleurs manuels **Synchroniser maintenant** et **en arrière-plan** exécutent ceux activés.

### <a id="offline-first"></a>Hors ligne d'abord

- **Aucun réseau n'est requis** pour ajouter un remplissage, une dépense ou un reçu. Tout est enregistré **localement d'abord**.
- Lorsque le réseau est disponible, la synchronisation et la sauvegarde des photos s'exécutent en tant que **tâches en arrière-plan** (selon un calendrier que vous définissez et lorsque vous appuyez sur **Synchroniser maintenant**). Les échecs s'affichent sous forme de texte rouge sous les lignes Paramètres et d'un **!** dans la barre de titre de l'application.

### <a id="your-accounts-only"></a>Vos comptes uniquement

La connexion et les jetons restent sur l'appareil pour les fournisseurs que vous choisissez (Google, Microsoft, clés S3, URL auto-hébergées, etc.). Les destinations sont sous **contrôle total de l'utilisateur** : votre compte Google, votre OneDrive, votre compartiment MinIO, votre hôte EtherCalc, etc. Rien n'est partagé avec d'autres utilisateurs de Vehicle Expenses via un backend partagé.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Cibles prises en charge — données (feuille de calcul/tableau)

Configuré sous **Menu → Synchronisation → Synchronisation de la feuille de calcul** (également accessible à partir des lignes récapitulatives des paramètres). Options de sélection de première classe :

| Cible | Remarques |
|--------|--------|
| **Google Feuilles** | Valeur par défaut commune ; onglets pour les véhicules, les dépenses et le carburant par véhicule |
| **Excel** | Classeur Microsoft via reliure de style Graph/OneDrive |
| **ÉtherCalc** | Salles de calcul collaboratives auto-hébergées |
| **Autre →** backends implémentés | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Différé / pas encore sans tête (répertorié sous Autre mais pas entièrement implémenté) : OnlyOffice, Collabora. Voir aussi [index auto-hôte](reference/self-host/INDEX.md).

CSV **export/import** (ZIP de la même disposition d'onglets) est disponible dans Paramètres en tant que sauvegarde portable, indépendante de la synchronisation en direct.

### <a id="supported-targets--photos-image-backup"></a>Cibles prises en charge — photos (sauvegarde d'image)

Configuré sous **Menu → Synchronisation → Sauvegarde de photos** (également à partir des lignes récapitulatives des paramètres) :

| Cible | Remarques |
|--------|--------|
| **Google Drive** | Dossier que vous choisissez (parcourir ou coller l'URL) |
| **OneDrive** | Compte Microsoft + préfixe de chemin |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO et autres points de terminaison compatibles S3 |
| **Autre** | Stockage sauvegardé par rclone (par exemple WebDAV, SFTP et autres télécommandes organisées disponibles dans le sélecteur intégré à l'application) |

Configurer des aide-mémoire pour les cibles photo et tabulaires auto-hébergées : [index auto-hébergé] (reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Comportement multi-appareils (court)

- Les lignes sont fusionnées par **Sync ID** avec **last-write-wins** sur les horodatages **mis à jour**.
- Les suppressions sont douces ; une modification plus récente sur un autre appareil peut restaurer une ligne.
- Saisir le **même remplissage deux fois** sur deux appareils crée **deux lignes** — supprimez l'excédent lorsque vous le remarquez.
- Plus de détails : [Notes de comportement de synchronisation](#sync-behavior-notes) et [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Exemple : ajouter Google Sheets (données)

1. **Menu → Synchronisation → Synchronisation de la feuille de calcul** (ou Paramètres → Synchronisation de la feuille de calcul).

![Liste de synchronisation des feuilles de calcul](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Appuyez sur **Ajouter une destination de feuille de calcul**.

![Sélecteur de fournisseur](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Choisissez **Google Sheets**.

![Formulaire Google Sheets](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Connectez-vous une fois avec Google** : cette connexion vous permet également de naviguer. Nom d'affichage → collez une **URL de feuille** (toujours valide) ou un **🔍** parcours. Parcourir s'ouvre sur **Tous et partagé** afin que vous puissiez choisir une feuille de calcul existante que vous possédez ou qui est partagée avec vous. Puis planifier les options → activer → enregistrer.
5. **Synchronisez maintenant** une fois pour créer/mettre à jour les onglets : `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Exemple : ajouter Google Drive (photos)

1. **Menu → Synchronisation → Sauvegarde de photos** (ou Paramètres → Sauvegarde de photos).

![Liste de sauvegarde des photos](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Appuyez sur **Ajouter une destination photo**.

![Sélecteur de fournisseur de photos](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Choisissez **Google Drive**.

![Formulaire Google Drive](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Connectez-vous avec Google (Drive)** → URL/navigation du dossier facultatif → activer → enregistrer → **Synchroniser maintenant**.

Manuel **Synchroniser maintenant** pour les photos est un passage complet ; la sauvegarde en arrière-plan traite généralement les téléchargements **en attente uniquement** selon une planification.

### <a id="sync-behavior-notes"></a>Notes de comportement de synchronisation

- Après la mise à niveau de l'application, vous pouvez voir brièvement ** « Mise à jour de la base de données après la mise à niveau… »** (remplissage de l'ID de synchronisation local).
- Si une synchronisation est interrompue, la prochaine synchronisation **réussie** refusionne et répare les onglets distants.
- Échecs : résumé rouge sur les cartes de synchronisation + **!** dans la barre d'application.

---

## <a id="fill-up-fuel"></a>Remplissage (carburant)

Ceci est l'**écran d'accueil** lorsque vous ouvrez l'application.

### <a id="vehicle-selection-usually-automatic"></a>Sélection du véhicule (généralement automatique)

Vous n'avez **pas** besoin de choisir le véhicule en premier. Lorsque les véhicules ont des **repères** configurés dans Gérer les véhicules, Fill **détecte automatiquement quel véhicule** à partir de l'image du tableau de bord après avoir capturé le compteur kilométrique. Vous pouvez toujours ouvrir la liste déroulante **Véhicule** pour remplacer si nécessaire.

### <a id="aim-at-the-odometer"></a>Visez le compteur kilométrique

Restez en mode compteur kilométrique et cadrez le cluster. Instruction : * Visez le compteur kilométrique. Appuyez sur l'obturateur pour capturer.*

![Remplir — caméra en direct sur le compteur kilométrique (exemple d'image de tableau de bord en aperçu)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Après l'obturateur du compteur kilométrique

OCR remplit **Odo** et essaie de faire correspondre le véhicule à partir des points de repère (vérifiez les deux si nécessaire). Le bouton principal devient **Réessayer** pour reprendre la photo. Les instructions résument la lecture.

![Remplir — résultat OCR du compteur kilométrique](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Mode pompe (coût et volume)

1. Appuyez sur **↕** pour passer en mode pompe : *Visez l'affichage de la pompe (coût/volume). Appuyez sur le déclencheur.*
2. Capturez les totaux de la pompe. Les champs de coût et de volume sont remplis ; utilisez **↔** s'ils sont échangés.
3. Appuyez sur devise ou **G/L** si nécessaire, puis **Enregistrer** (disque). Les champs vides effectuent un **remplissage partiel** (toujours autorisé).

![Remplir — aperçu de la pompe / totaux (exemple d'image de pompe en aperçu)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Vous restez sur Remplir pour le prochain arrêt (les champs s'effacent après la sauvegarde). Travaillez entièrement **hors ligne** ; la synchronisation s'exécute plus tard en arrière-plan une fois configurée.

### <a id="manual-entry-no-camera--bad-ocr"></a>Saisie manuelle (pas de caméra / OCR incorrect)

1. Appuyez sur **Odo**, **cost** ou **volume** et saisissez les valeurs (le portrait utilise le clavier système ; le paysage utilise un clavier à l'écran).
2. Choisissez ou confirmez le **Véhicule** si la détection automatique ne s'est pas exécutée.
3. Enregistrez comme ci-dessus.

### <a id="modes-and-borders"></a>Modes et bordures

- **Bordure verte** autour du véhicule+odo → capture/édition du compteur kilométrique.
- **Bordure verte** autour du coût+volume → mode pompe.
- **Enregistrer** reste désactivé jusqu'à ce qu'un véhicule soit sélectionné et qu'au moins un des éléments odo/coût/volume contienne des données et que l'OCR ne soit toujours pas en cours d'exécution.

Astuce à l'écran (sous la ligne d'instructions) : *Obturateur = capture · Disque = sauvegarde · ↕ = mode odo/pompe · ↔ = coût d'échange/volume.*

---

## <a id="expenses"></a>Dépenses

### <a id="new-expense"></a>Nouvelle dépense

Menu → **Nouvelle dépense**.

![Nouveau formulaire de dépenses et commandes de caméra (exemple d'image de tableau de bord dans la bande de la caméra lorsqu'elle est vide)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Enregistrer** (disque), **obturateur** (photo du reçu) ou **galerie** (choisir une image).
2. Remplissez **Date**, **Véhicule**, **Vendeur**, **Description**, **Montant** (symbole monétaire tappable), **Catégorie**, **Odomètre** en option.
3. Reçus multipages : capturez des pages supplémentaires si l'interface utilisateur propose la pagination (la page 0 est le reçu principal).
4. **Enregistrer** dans le magasin (local d'abord ; la sauvegarde des photos et la synchronisation des feuilles de calcul s'effectuent en arrière-plan une fois configurées).

### <a id="expense-list"></a>Liste des dépenses

Menu → **Rapports** → **Liste des dépenses** — parcourez les dépenses passées hors carburant ; ouvrez un élément à modifier.

![Liste des dépenses](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Modifier les dépenses

Ouvrez une ligne de la liste. Corrigez le fournisseur, le montant, la catégorie, le véhicule et la description. Si le reçu est uniquement dans une sauvegarde de photos (pas de fichier local lisible), utilisez **Récupérer l'image des archives** lorsqu'il est affiché (fonctionne sur les destinations de photos configurées).

![Modifier la dépense](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Début du voyage

Menu → **Démarrer le voyage** (après remplir le tiroir). Capturez ou saisissez le compteur kilométrique, choisissez le type de trajet, enregistrez avec l'icône **disque**. **Stop** est un raccourci pour Personnel désormais à l'emplacement GPS retenu. Utilisez **ⓘ** pour les rappels de contrôle.

![Début du voyage](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Les démarrages de voyage sont stockés sous forme de lignes de carburant avec un **Type de voyage** (pas de remplissages normaux). Ils apparaissent sous **Rapports → Miles parcourus**, et non sous Historique de carburant.

---

## <a id="reports"></a>Rapports

Menu → **Rapports** ouvre le hub produit (résumé de tous les temps + fiches de catalogue). Il s’agit de la seule surface de rapports sur les produits : il n’y a pas d’élément de tiroir « Rapports et graphiques » distincts.

![Hub des rapports](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Ouvrez une carte pour le mode véhicule (**Tous / Chaque / Unique**), les filtres de période, les graphiques et le partage (**TEXTE / CSV / PDF**). Barre supérieure sur les enfants signalés : **☰ + ←** (et **ⓘ** une fois inscrits).

### <a id="time-based-reports"></a>Rapports basés sur le temps

La carte graphique principale. Mesures facultatives (mpg, volume/distance tel que G/mi, prix unitaire tel que $/G, coût/distance, $ mensuel, miles parcourus, % de trajet par type) avec bacs **Smooth** et **échelles Y indépendantes** (économie à gauche ; argent et familles de voyage à droite).

![Rapports basés sur le temps](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Rapports basés sur le temps — métriques défilées](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Détails des mathématiques économiques : [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Historique de remplissage vs historique de carburant

- **Rapports → Historique des remplissages** — remplissages chronologiques pour les filtres de rapport (**remplissages uniquement** ; aucun voyage ne démarre).

![Remplir le rapport d'historique](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Historique du carburant** (si présent dans la navigation de votre build) — inventaire de remplissage par véhicule, remplissage également uniquement ; appuyez sur une ligne à modifier.

### <a id="trip-miles"></a>Miles de voyage

**Rapports → Miles de voyage** — miles par type, graphiques et **liste chronologique de débuts/segments de voyage**. Appuyez sur un vrai début pour ouvrir **Modifier le remplissage** pour cette ligne.

![Miles de voyage](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Modifier le remplissage

Depuis l'Historique de remplissage, l'Historique de carburant ou les miles parcourus, ouvrez un remplissage. Mise en page : véhicule et compteur kilométrique, **devise avant coût**, volume, notes. Le type de voyage apparaît uniquement lorsque la ligne correspond à un début de voyage. L'emplacement comporte un résumé ainsi que des **Détails de l'emplacement**. Photo locale manquante avec identité cloud : **Récupérer l'image des archives**.

![Modifier le remplissage — Récupérer depuis les archives lorsqu'une photo locale est manquante](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Les autres fiches du catalogue incluent les dépenses par catégorie, le résumé du véhicule et la liste des dépenses.

Money utilise la devise de chaque ligne lorsqu'elle est définie. Les totaux en devises mixtes affichent des **sous-totaux par devise** (pas de conversion FX silencieuse).

---

## <a id="syncing"></a>Synchronisation

Menu → **Synchronisation** est la plaque tournante des feuilles de calcul et des destinations photo (pas seulement enfouies sous Paramètres).

![Hub de synchronisation](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Cartes pour la **synchronisation de feuille de calcul** et la **sauvegarde de photos** avec un statut court, **Sync** pour ce type et **›** dans la liste de destinations.
- Ouvrez une destination pour **Test de connexion** et **Synchroniser maintenant (cette destination)** / tous configurés.
- Échec **Détails** et le **!** rouge dans la barre de titre arrivent ici.
- Configuration étape par étape de Google Sheets et Drive : [Sauvegardes et synchronisation multi-appareils](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Paramètres (préférences locales)

Menu → **Paramètres**.

![Paramètres](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Plus de paramètres (fait défiler)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Pour les destinations, préférez **Menu → Synchronisation**. Les paramètres peuvent toujours afficher des lignes récapitulatives qui ouvrent les mêmes listes.

### <a id="local-preferences-common"></a>Préférences locales (communes)

- **Enregistrer les photos des reçus de carburant** / **Enregistrer les photos des dépenses localement** — conservez les images sur l'appareil (vous pouvez demander l'autorisation des photos).
- **Jouer le son du déclencheur**
- **Devise** / **Unité de volume** — valeurs par défaut de l'application (système ou explicite). La modification de l'unité de volume avec les données de carburant existantes peut offrir une boîte de dialogue de conversion.
- **Mode sombre**
- **Conseils de configuration** — rouvrez les didacticiels de première utilisation sur les véhicules/synchronisation.
- **Debug Fill** / **Afficher les écrans d'expérimentation (dev)** — avancé ; laisser pour un usage quotidien. Les écrans d’expérimentation ne sont pas documentés ici.

CSV **export/import** (ZIP des onglets Véhicules / Dépenses / Carburant) est disponible depuis les Paramètres lorsqu'il est proposé par la version actuelle.

---

## <a id="help--about"></a>Aide et à propos

![Aide](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![À propos](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Aide** — démarrage rapide sur l'appareil, didacticiels de configuration, lien vers ce manuel, index de configuration auto-hébergé.
- **À propos** — version, licences, GitHub, ce manuel (regroupé hors ligne + HTML en ligne une fois publié).

---

## <a id="related-docs"></a>Documents associés

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — petit guide quotidien
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — configuration photo/tabulaire auto-hébergée
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — fusion, récupération, doublons
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — détail des mesures économiques
