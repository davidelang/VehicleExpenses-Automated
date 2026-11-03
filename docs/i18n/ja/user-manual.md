# <a id="vehicle-expenses-automated--user-manual"></a>車両経費自動化 — ユーザーマニュアル

> **ソースを編集 (Markdown)。** ブラウザとアプリ内リーダーは **レンダリングされた HTML** を開きます。
> - ウェブ: [`docs/user-manual/index.html`](index.html) (`./scripts/render-user-manual.sh` で再生成)
> - アプリ: ヘルプ / 概要 → 完全なマニュアル (HTML + スクリーンショットが同梱されています)
>
> エンドユーザーに生の `.md` URL を指定しないでください。ブラウザにはプレーンテキストのみが表示されます。

燃料補給と車両費用をカメラファーストで追跡し、**クラウド アカウントでのマルチデバイス同期とバックアップをオプションで利用できます。

これは **完全なマニュアル** (スクリーンショット + 各ステップ) です。 電話では、**メニュー → ヘルプ** という短いスタート ガイドが表示されます。

**ここでは説明しません:** 古い画像、位置合わせ実験、およびポンプ実験をインポートします (開発者/高度なツール)。

---

## <a id="table-of-contents"></a>目次

1. [必要なもの](#what-you-need)
2. [アイコンの概要](#icons-at-a-glance)
3. [メニューを開く](#open-the-menu)
4. [初回セットアップ: 車両の管理](#first-time-setup-manage-vehicles)
5. [バックアップとマルチデバイス同期](#backups-and-multi-device-sync)
6. [給油(燃料)](#fill-up-fuel)
7. [旅行開始](#start-trip)
8. [経費](#expenses)
9. [レポート](#reports)
10. [設定(ローカル設定)](#settings-local-preferences)
11. [同期中](#syncing)
12. [ヘルプと概要](#help--about)
13. [関連ドキュメント](#related-docs)

---

## <a id="what-you-need"></a>必要なもの

- Android スマートフォンまたはタブレット。
- 最適な OCR を実現するには: **ダッシュボードの走行距離計**と**ポンプの合計**を明確に表示します (または数字を手で入力します)。
- オプション: スプレッドシート データや写真のバックアップ用に **あなたが管理する**アカウント ([バックアップとマルチデバイス同期](#backups-and-multi-device-sync) を参照)。

---

## <a id="icons-at-a-glance"></a> アイコンの概要

これらはメイン画面に表示されます。 それらを知っていれば、狩猟の手間が大幅に節約できます。

| どこ | アイコン/コントロール | 何をするのか |
|------|----------------|--------------|
| トップバー | **☰ メニュー** (ハンバーガー) | ナビゲーションドロワーを開きます |
| トップバー | **ⓘ** (ページヘルプ) | **現在の**ページの簡単なヘルプ (利用可能な場合はメニューの隣) |
| トップバー | **`?N`** (黄色) | 保留中のインポート レビューの質問 — インポート レビューが開きます |
| トップバー | **!** (赤) | スプレッドシートまたは写真の保存先が最近失敗しました — **同期中**を開いて修正してください |
| トップバー | **☰ + ←** | お子様のレポートと経費リストには、**メニューと戻る**が一緒に表示されます。 レポートハブはメニューのみです |
| 設定 / 燃料編集 | **←** | 戻る (設定スプレッドシート/写真と燃料の編集は背面に集中したままになります) |
| 塗りつぶし | **白丸** (シャッター) | OCR 用に走行距離計またはポンプ表示をキャプチャ |
| 塗りつぶし | **ディスク / 保存** | 充填を保存します (車両と、走行距離/量/コストの少なくとも 1 つが必要です) |
| 塗りつぶし | **↕ 矢印** (モード切り替え) | **走行距離計モード**と**ポンプ (コスト/音量) モード**を切り替えます。 緑色の境界線はアクティブなフィールド グループを強調表示します。
| 塗りつぶし | **↔ 矢印** (コストとボリュームの間) | OCR が間違ったフィールドにコストとボリュームを入力した場合にコストとボリュームを交換する |
| 塗りつぶし | **1 倍ズーム / …** | レンズがサポートしている場合のカメラのズーム比 |
| フィル (キャプチャ後) | メイン ボタンの **更新** | プレビューを破棄してライブカメラに戻る |
| 塗りつぶし（処理中） | メインボタンの **X** | 進行中のキャプチャ/OCR をキャンセル |
| 経費 | **保存** | 経費を節約する |
| 経費 | **シャッターサークル** | 領収書の写真を撮る |
| 経費 | **ギャラリー** | ライブラリからレシート画像を選択 |
| 経費 | **再取得** | 現在のレシートの写真をクリアして再度撮影 |
| 経費 / 車両の管理 | **+ / −** FAB | 写真のプレビューをズームする |
| ランドマークダイアログ | **OCR を編集** | エンジンが見逃したランドマーク テキストを修正または追加します。
| スプレッドシート / 写真フォーム | **🔍 検索** | Google ドライブでシートまたはフォルダーを参照します (サインイン後) |

コスト フィールドの通貨記号と体積フィールドの **G/L** はタップ可能です。小さなメニューを開いて、そのエントリの通貨またはガロン対リットルを変更します。

---

## <a id="open-the-menu"></a>メニューを開く

1. 左上の**☰**をタップします。
2. ページを選択します。

![ナビゲーションドロワー](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**メインドロワー:** 入力、旅行の開始、車両の管理、新しい経費、**レポート**、設定、同期、ヘルプ、概要。

**実験ドロワー** (設定 → 実験画面の表示): アライメント実験 · ポンプ実験 · **古い画像のインポート**。

**レポートハブ経由 (メインドロワーではない):** 経費リスト · 履歴を入力します。

---

## <a id="first-time-setup-manage-vehicles"></a>初回セットアップ: 車両の管理

OCR と **自動車両マッチング**は、各車両を **参考ダッシュボード写真**で登録し、**ディスカバリー**を実行し、パドル シードから走行距離計のクロップを設定し、そのダッシュボードのランドマークを保存した後に最も効果的に機能します。

### <a id="open-manage-vehicles"></a>車両の管理を開く

メニュー → **車両の管理**。 車両を選択します (または **新しい車両を追加**)。

![リファレンスダッシュ、作物、発見で車両を管理](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>車両を追加または編集する

1. **車両**ドロップダウンを開き、車両を選択するか、**新しい車両を追加**します。
2. 鮮明な**参考ダッシュボード写真**をキャプチャまたは選択します（計器盤全体、明るい照明、電話機がほぼ真正面にあるもの）。 **写真を撮る** または **ギャラリー** を使用します。
3. [**ディスカバリーの実行**] をタップします。パドル検出により、写真上に黄色の **シード**が配置されます (ランドマークは以前と同じ `"ML Kit"` JSON を使用します)。
4. [**Odo を選択**] をタップし、数字のシードをタップしてハンドルを調整し、**保存**します。
5. **Odo の編集** は、すでに存在するボックスを調整します。 **Odo をクリア**すると削除され、ボタンには **Odo を選択**と表示されます。
6. **ランドマークを表示** で確認します。 **OCR の編集**を使用して誤読を修正するか、欠落したテキストを**追加**してください。
7. **車名** (必須) に加えて、必要に応じてメーカー/モデル/年式/プレートを入力します。
8. **車両の作成** または **変更の保存** をタップします (新しい車両には名前と参考写真が必要です)。

![新しい車両フォームフィールドを追加](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>ランドマーク: Discovery が見逃したものを修正します

**ランドマークの表示** の後、リストをスクロールして値を修正します。 エンジンは時々小さな数字を見逃すことがあります (たとえば、クラスターの右下の時計 **60**)。 **OCR の編集** を使用して追加または修正し、車両 ID の信頼性を維持します。

![ランドマークダイアログ — OCRの確認と編集](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>完璧な写真なしで入力しています

車両を選択し、[Fill] に走行距離計、走行量、コストを **入力**することでアプリを使用できます。OCR はすべてのフィールドでオプションです。 ギャラリーのインポートは、アプリ内で撮影したくない場合の参照ダッシュ写真に機能します。

**ヒント:** スプレッドシートの同期後、車両定義 (作物、ランドマーク) はローカル データベースに保存されます。これらを使用するために [塗りつぶし用車両の管理] を再度開く必要はありません。

---

## <a id="backups-and-multi-device-sync"></a>バックアップとマルチデバイス同期

このアプリは、**複数の携帯電話またはタブレットで同じフリート データを共有できる**ように構築されており、**データや写真のコピーをデバイスから外した状態に保つことができます**。 これは、他の人が見ることができる会社運営の「車両経費クラウド」ではなく、**アカウントまたは**セルフホスト型サーバーの下で**あなた**が設定した宛先を使用して行われます。

### <a id="what-runs-where"></a>何がどこで走るのか

| 種類 | 保存するもの | 一般的な使用方法 |
|------|----------------|---------------|
| **スプレッドシート/表形式の同期** | 車両、給油、経費 (行とタブ) | マルチデバイスのマージ + 構造化バックアップ |
| **写真のバックアップ** | バイナリ画像 (ダッシュ/ポンプ/レシート/参考写真) | 写真のバックアップ + 失われたファイルを復元 |

種類ごとに **複数の宛先** を設定できます (タイプごとにソフト キャップ)。 手動 **今すぐ同期** ワーカーと **バックグラウンド** ワーカーは、有効になっているワーカーを実行します。

### <a id="offline-first"></a> まずはオフライン

- **補充、経費、または領収書を追加するためにネットワークは必要ありません**。 すべては **最初にローカル** に保存されます。
- ネットワークが利用可能な場合、同期と写真のバックアップは **バックグラウンド タスク**として実行されます (設定したスケジュールに従って、[**今すぐ同期**] をタップすると)。 失敗すると、設定行の下に赤いテキストが表示され、アプリのタイトル バーに **!** が表示されます。

### <a id="your-accounts-only"></a>あなたのアカウントのみ

選択したプロバイダー (Google、Microsoft、S3 キー、セルフホスト URL など) のサインインとトークンはデバイス上に残ります。 宛先は **ユーザーの完全な制御** (Google アカウント、OneDrive、MinIO バケット、EtherCalc ホストなど) にあります。共有バックエンドを通じて他の Vehicle Expenses ユーザーと共有されるものは何もありません。

### <a id="supported-targets--data-spreadsheet--tabular"></a>サポートされるターゲット — データ (スプレッドシート/表形式)

**メニュー → 同期 → スプレッドシート同期** で設定します (設定の概要行からもアクセスできます)。 ファーストクラスのピッカーのオプション:

| ターゲット | メモ |
|--------|--------|
| **Google スプレッドシート** | 一般的なデフォルト。 車両、経費、車両ごとの燃料のタブ |
| **エクセル** | Graph / OneDrive スタイルのバインドによる Microsoft ワークブック |
| **イーサ計算** | 自己ホスト型の共同スプレッドシート ルーム |
| **その他 →** 実装されたバックエンド | **Baserow**、**NocoDB**、**Airtable**、**PocketBase**、**Supabase**、**Firebase**、**Zoho Sheet** |

延期/まだヘッドレスではない (「その他」にリストされているが完全には実装されていない): OnlyOffice、Collabora。 [セルフホストインデックス](reference/self-host/INDEX.md)も参照してください。

CSV **エクスポート/インポート** (同じタブ レイアウトの ZIP) は、ライブ同期とは独立して、ポータブル バックアップとして [設定] から利用できます。

### <a id="supported-targets--photos-image-backup"></a>サポートされるターゲット — 写真 (イメージバックアップ)

**メニュー → 同期 → 写真バックアップ** で設定します (設定の概要行からも):

| ターゲット | メモ |
|--------|--------|
| **Google ドライブ** | 選択したフォルダー (URL を参照または貼り付け) |
| **OneDrive** | Microsoft アカウント + パスのプレフィックス |
| **S3** | AWS、Wasabi、Cloudflare R2、MinIO、およびその他の S3 互換エンドポイント |
| **その他** | rclone-backed ストレージ (例: WebDAV、SFTP、アプリ内ピッカーで利用可能なその他の厳選されたリモート) |

セルフホストの写真と表形式のターゲットのチートシートをセットアップします: [セルフホスト インデックス](reference/self-host/INDEX.md)。

### <a id="multi-device-behavior-short"></a> マルチデバイスの動作 (短い)

- **Updated** タイムスタンプで **last-write-wins** を使用して **同期 ID** によって行がマージされます。
- 削除はソフトです。 別のデバイスで新しい編集を行うと、行を復元できます。
- 2 つのデバイスで **同じ塗りつぶしを 2 回**入力すると、**2 行**が作成されます。気づいたときに余分な行を削除します。
- 詳細: [同期動作メモ](#sync-behavior-notes) および [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md)。

### <a id="example-add-google-sheets-data"></a>例: Google スプレッドシート (データ) を追加

1. **メニュー → 同期 → スプレッドシート同期** (または設定 → スプレッドシート同期)。

![スプレッドシート同期リスト](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. **スプレッドシートの宛先を追加** をタップします。

![プロバイダーピッカー](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. **Google スプレッドシート**を選択します。

![Google スプレッドシート フォーム](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Google でサインイン** します。このサインインにより、閲覧することもできます。 表示名 → **シート URL** (まだ有効) を貼り付けるか、**🔍** を参照します。 [**すべてと共有**] で [参照] が開くので、所有している、または共有されている既存のスプレッドシートを選択できます。 次に、オプションをスケジュール→有効→保存します。
5. **今すぐ同期**してタブを作成/更新します: `Vehicles`、`Expenses`、`Fuel - {vehicle name}`。

### <a id="example-add-google-drive-photos"></a>例: Google ドライブを追加 (写真)

1. **メニュー → 同期 → 写真バックアップ** (または設定 → 写真バックアップ)。

![写真バックアップリスト](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. **写真の宛先を追加** をタップします。

![写真プロバイダーピッカー](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. **Google ドライブ**を選択します。

![Google ドライブフォーム](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Google でサインイン (ドライブ)** → オプションのフォルダー URL/参照 → 有効化 → 保存 → **今すぐ同期**。

写真の手動 **今すぐ同期** は完全なパスです。 バックグラウンド バックアップは通常、**保留中のみ** のアップロードをスケジュールに従って処理します。

### <a id="sync-behavior-notes"></a>同期動作のメモ

- アプリのアップグレード後、**「アップグレード後にデータベースを更新しています…」** (ローカル同期 ID バックフィル) が一時的に表示される場合があります。
- 同期が中断された場合、次の **成功した** 同期ではリモート タブが再マージされ、修復されます。
- 失敗: カードの同期に赤い概要 + **!** (アプリバー)。

---

## <a id="fill-up-fuel"></a>給油（燃料）

これは、アプリを開いたときの**ホーム画面**です。

### <a id="vehicle-selection-usually-automatic"></a>車両選択 (通常は自動)

最初に車両を選択する必要は**ありません**。 [車両の管理] で車両に **ランドマーク** が設定されている場合、走行距離計をキャプチャした後、Fill はダッシュボードの画像から**どの車両**を自動検出します。 必要に応じて、[**車両**] ドロップダウンを開いて上書きすることができます。

### <a id="aim-at-the-odometer"></a> オドメーターを目指してください

オドメーターモードを維持し、クラスターをフレームに収めます。 指示: *走行距離計を目指してください。 シャッターをタップして撮影します。*

![塗りつぶし — オドメーター上のライブカメラ (プレビューのダッシュ画像の例)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>オドメーターシャッター後

OCR は **Odo** を入力し、ランドマークから車両を照合しようとします (必要に応じて両方を確認します)。 メインボタンが**Retry**となり、再撮影します。 説明書は読んだ内容を要約したものです。

![フィル - オドメーター OCR 結果](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>ポンプモード（コストと容量）

1. **↕** をタップしてポンプモードに切り替えます: *ポンプ表示 (コスト/容量) を目指します。 シャッターをタップします*。
2. ポンプの合計を取得します。 コストとボリュームのフィールドが入力されます。 入れ替える場合は **↔** を使用してください。
3. 必要に応じて通貨または **G/L** をタップし、**保存** (ディスク) をタップします。 空のフィールドは **部分的な塗りつぶし**を作成します (引き続き許可されます)。

![塗りつぶし — ポンプ プレビュー / 合計 (プレビュー内のポンプ画像の例)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

次のストップまでフィルを続けます (フィールドは保存後にクリアされます)。 完全に**オフライン**で作業します。 同期は、構成されている場合はバックグラウンドで後で実行されます。

### <a id="manual-entry-no-camera--bad-ocr"></a>手動入力 (カメラなし / OCR 不良)

1. **Odo**、**cost**、または **volume** をタップし、値を入力します (縦向きの場合はシステム キーボードを使用し、横向きの場合はオンスクリーン キーパッドを使用します)。
2. 自動検出が実行されなかった場合は、**車両**を選択または確認します。
3. 上記のように保存します。

### <a id="modes-and-borders"></a>モードとボーダー

- 車両 + odo の周囲の **緑色の境界線** → 走行距離計をキャプチャ/編集します。
- **コスト+ボリューム→ポンプモードの周りの**緑色の境界線**。
- **保存**は、車両が選択され、odo/コスト/ボリュームの少なくとも 1 つにデータがあり、OCR がまだ実行されていないまで無効のままです。

画面上のヒント (指示行の下): *シャッター = キャプチャ · ディスク = 保存 · ↕ = odo/ポンプ モード · ↔ = スワップ コスト/ボリューム。*

---

## <a id="expenses"></a>経費

### <a id="new-expense"></a>新たな出費

メニュー → **新しい経費**。

![新しい経費フォームとカメラ コントロール (空の場合のカメラ バンド内のダッシュ画像の例)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **保存** (ディスク)、**シャッター** (領収書の写真)、または **ギャラリー** (画像を選択)。
2. **日付**、**車両**、**ベンダー**、**説明**、**金額** (通貨記号をタップ可能)、**カテゴリー**、オプションの**走行距離**を入力します。
3. 複数ページのレシート: UI がページングを提供する場合は、追加のページをキャプチャします (ページ 0 がプライマリ レシートです)。
4. **保存** して保存します (最初にローカル。設定されている場合、写真のバックアップとスプレッドシートの同期はバックグラウンドで行われます)。

### <a id="expense-list"></a>経費リスト

メニュー → **レポート** → **経費リスト** — 過去の燃料以外の経費を参照します。 編集する項目を開きます。

![経費リスト](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>経費を編集

リストから行を開きます。 ベンダー、金額、カテゴリ、車両、説明を修正します。 レシートが写真バックアップのみにある場合 (読み取り可能なローカル ファイルがない場合)、表示されている場合は **アーカイブから画像を取得** を使用します (設定された写真の保存先全体で機能します)。

![経費編集](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>旅行の開始

メニュー → **旅行開始** (引き出しに記入した後)。 オドメーターをキャプチャまたは入力し、走行タイプを選択し、**ディスク** アイコンで保存します。 **Stop** は、保持されている GPS 位置にある Personal Now のショートカットです。 コントロールリマインダーには**ⓘ**を使用してください。

![旅行開始](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

トリップの開始は、**トリップ タイプ** の燃料行として保存されます (通常の充填ではありません)。 これらは、燃料履歴ではなく、**レポート → 走行マイル** に表示されます。

---

## <a id="reports"></a>レポート

メニュー → **レポート** により、製品ハブ (全期間の概要 + カタログ カード) が開きます。 これは唯一の製品レポート画面です。個別の「レポートとチャート」ドロワー項目はありません。

![レポートハブ](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

車両モード (**すべて / 各 / シングル**)、期間フィルター、チャート、および共有 (**TEXT / CSV / PDF**) のカードを開きます。 レポートの子の上部バー: **☰ + ←** (登録時は **ⓘ**)。

### <a id="time-based-reports"></a>時間ベースのレポート

メインチャートカード。 **スムーズ** ビンと **独立した Y スケール**を備えたオプションの指標 (mpg、G/mi などの量/距離、$/G などの単価、コスト/距離、月額 $、旅行マイル、タイプ別の旅行 %) (左側が経済、右側がお金と旅行ファミリー)。

![時間ベースのレポート](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![時間ベースのレポート — スクロールされたメトリクス](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

経済数学の詳細: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md)。

### <a id="fill-history-vs-fuel-history"></a>充填履歴と燃料履歴

- **レポート → 履歴の入力** — レポート フィルターの時系列の入力 (**入力のみ**、旅行は開始されません)。

![記入履歴レポート](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **燃料履歴** (ビルドのナビゲーションに存在する場合) — 車両ごとの充填インベントリ、充填のみも可能。 編集する行をタップします。

### <a id="trip-miles"></a>旅行マイル

**レポート → 旅行マイル** — 種類別のマイル、グラフ、時系列の **旅行開始/区間リスト**。 実際の開始をタップして、その行の **塗りつぶしの編集** を開きます。

![旅行マイル](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>編集塗りつぶし

充填履歴、燃料履歴、または旅行マイルから充填を開きます。 レイアウト: 車両と走行距離計、**価格前の通貨**、音量、メモ。 旅行タイプは、その行が旅行開始の場合にのみ表示されます。 場所には概要と **場所の詳細**が含まれています。 クラウド ID を持つローカル写真がありません: **アーカイブから画像を取得**。

![編集フィル — ローカル写真が見つからない場合はアーカイブから取得](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

その他のカタログ カードには、カテゴリ別の経費、車両概要、経費リストが含まれています。

お金は、設定されている場合、各行の通貨を使用します。 混合通貨の合計には **通貨ごとの小計**が表示されます (サイレント FX 換算はありません)。

---

## <a id="syncing"></a>同期中

メニュー → **同期** は、スプレッドシートと写真の保存先のハブです ([設定] に埋め込まれているだけではありません)。

![同期ハブ](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- 短いステータスの **スプレッドシート同期** および **写真バックアップ**、その種類の **同期**、および宛先リストへの **›** 用のカード。
- **接続のテスト**と**今すぐ同期 (この宛先)** の宛先を開きます / すべて構成されています。
- 失敗 **詳細** とタイトル バーの赤い **!** がここに表示されます。
- Google スプレッドシートとドライブのステップバイステップのセットアップ: [バックアップとマルチデバイス同期](#backups-and-multi-device-sync)。

---

## <a id="settings-local-preferences"></a>設定 (ローカル設定)

メニュー → **設定**。

![設定](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![その他の設定(スクロール)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

宛先については、**メニュー → 同期** を選択してください。 設定では、同じリストを開く概要行が引き続き表示される場合があります。

### <a id="local-preferences-common"></a>ローカル設定 (共通)

- **燃料領収書の写真を保存** / **経費の写真をローカルに保存** — 画像をデバイス上に保存します (写真の許可を要求する場合があります)。
- **シャッター音を再生**
- **通貨** / **ボリューム単位** — アプリのデフォルト (システムまたは明示的)。 既存の燃料データで体積単位を変更すると、変換ダイアログが表示される場合があります。
- **ダークモード**
- **セットアップのヒント** — 最初に実行される車両/同期チュートリアルを再度開きます。
- **デバッグフィル** / **実験画面の表示 (開発)** — 高度な機能。 毎日の使用のために外してください。 実験画面についてはここでは説明しません。

CSV **エクスポート/インポート** (車両/経費/燃料タブの ZIP) は、現在のビルドで提供されている場合、設定から利用できます。

---

## <a id="help--about"></a>ヘルプと概要

![ヘルプ](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![概要](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **ヘルプ** — デバイス上のクイック スタート、セットアップ チュートリアル、このマニュアルへのリンク、セルフホスト セットアップのインデックス。
- **概要** — バージョン、ライセンス、GitHub、このマニュアル (公開時にはオフライン + オンライン HTML がバンドルされます)。

---

## <a id="related-docs"></a>関連ドキュメント

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — 短い日常ガイド
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — 自己ホスト型の写真/表形式のセットアップ
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — マージ、リカバリ、重複
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — 経済指標の詳細
