# <a id="vehicle-expenses-automated--user-manual"></a>車輛費用自動化 — 使用手冊

> **編輯原始碼 (Markdown)。 ** 瀏覽器和應用程式內閱讀器開啟 **已渲染的 HTML**：
> - 網頁：[`docs/user-manual/index.html`](index.html)（用`./scripts/render-user-manual.sh`重新生成）
> - 應用程式：幫助/關於 → 完整手冊（捆綁 HTML + 螢幕截圖）
>
> 請勿將最終使用者指向原始 `.md` URL — 瀏覽器僅顯示純文字。

攝影機優先追蹤燃油加註量和車輛費用，並可選擇在**您的**雲帳戶下進行多設備同步和備份。

這是**完整手冊**（截圖+每一步）。 在電話上，**選單→幫助**是較短的入門指南。

**此處未涵蓋：** 匯入舊圖片、對齊實驗和泵浦實驗（開發人員/高級工具）。

---

## <a id="table-of-contents"></a>目錄

1. [你需要什麼](#what-you-need)
2. [圖標一覽](#icons-at-a-glance)
3. [開啟選單](#open-the-menu)
4.【首次設定：管理車輛】(#first-time-setup-manage-vehicles)
5.【備份與多設備同步】(#backups-and-multi-device-sync)
6. [加註（燃料）](#fill-up-fuel)
7. [開始行程](#start-trip)
8. [費用](#expenses)
9. [報告](#reports)
10. [設定（本地偏好）](#settings-local-preferences)
11. [同步中](#syncing)
12. [幫助與關於](#help--about)
13. [相關文件](#related-docs)

---

## <a id="what-you-need"></a>你需要什麼

- Android 手機或平板電腦。
- 為了獲得最佳 OCR：**儀表板里程表** 和 **泵浦總數** 的清晰視圖（或手動輸入數字）。
- 可選：您控制的帳戶**用於電子表格資料和/或照片備份（請參閱[備份和多設備同步](#backups-and-multi-device-sync)）。

---

## <a id="icons-at-a-glance"></a>圖示一覽

這些出現在主畫面。 了解它們可以節省很多狩獵時間。

|哪裡 |圖示/控制|它有什麼作用 |
|-------|----------------|--------------|
|頂欄| **☰ 選單**（漢堡）|打開導航抽屜 |
|頂欄| **ⓘ**（頁面幫助）| **目前**頁面的簡短幫助（如果可用，則位於選單旁邊）|
|上欄| **`?N`**（黃色）|待處理的導入審核問題 — 開啟導入審核 |
|上欄| **！ **（紅色）|電子表格或照片目標最近失敗 - 開啟 **同步** 進行修復 |
|頂欄| **☰ + ←** |報告兒童和費用清單一起顯示**選單和背面**；報告中心僅是選單 |
|設定/燃料編輯| **←** |返回（設定電子表格/照片和燃料編輯保持返回焦點）|
|填寫 | **白色圓圈**（快門）|捕捉里程表或泵浦顯示以進行 OCR |
|填寫 | **磁碟/保存** |節省加油量（需要一輛車以及里程/體積/成本中至少一項）|
|填寫 | **↕ 箭頭**（模式切換）|切換 **里程表模式** 與 **泵浦（成本/體積）模式**。 綠色邊框突出顯示活動字段組 |
|填寫 | **↔ 箭頭**（成本與數量之間）|如果 OCR 將成本和數量放入錯誤的字段，則交換成本和數量 |
|填入 | **變焦 1 倍 / …** |鏡頭支援時的相機變焦比 |
|填充（捕獲後）| **在主按鈕上刷新** |放棄預覽並返回實時攝像頭 |
|填充（處理時）|主按鈕上的 **X** |取消正在進行的捕獲/OCR |
|費用| **保存** |節省開支|
|費用| **快門圈** |拍攝收據照片 |
|費用| **畫廊** |從庫中選擇一張收據圖像 |
|費用| **重考** |清除目前收據照片並重新拍攝 |
|費用/管理車輛| **+ / −** FAB |縮放照片預覽 |
|地標對話框 | **編輯 OCR** |更正或添加引擎錯過的標誌性文字 |
|試算表/相片表格| **🔍 搜尋** |瀏覽 Google 雲端硬碟尋找工作表或資料夾（登入後）|

成本欄位上的貨幣符號以及容量欄位上的 **G/L** 可點擊：點擊後會打開一個小選單，以更改該項目的貨幣或加侖與公升單位。

---

## <a id="open-the-menu"></a>開啟選單

1. 點選左上角的**☰**。
2. 選擇一個頁面。

![導航抽屜](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**主抽屜：** 填寫·開始行程·管理車輛·新費用·**報告**·設定·同步·幫助·關於。

**實驗抽屜**（設定→顯示實驗畫面）：對齊實驗·幫浦實驗·**匯入舊圖片**。

**透過報告中心（不是主抽屜）：** 費用清單 · 填寫歷史記錄。

---

## <a id="first-time-setup-manage-vehicles"></a>首次設定：管理車輛

OCR 和 **自動車輛匹配** 在你為每輛車註冊 **參考儀表板照片**、運行 **Discovery**、從撥片座標設置里程表裁切並保存儀表板地標後效果最佳。

### <a id="open-manage-vehicles"></a>開啟管理車輛

選單 → **管理車輛**。 選擇車輛（或**新增車輛**）。

![透過參考破折號、作物和發現來管理車輛](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>新增或編輯車輛

1. 開啟**車輛**下拉選單 → 選擇車輛或**新增車輛**。
2. 拍攝或挑選一張清晰的**參考儀表板照片**（完整的儀表組、光線充足、手機大致呈方形）。 使用**拍照**或**圖庫**。
3. 點選 **運行發現** — 槳式偵測將黃色 **種子** 放置在照片上（地標使用與之前相同的 `"ML Kit"` JSON）。
4. 點選 **選擇 Odo**，點選數字上的種子，調整手柄，然後 **儲存**。
5. **編輯 Odo** 調整現有的框框。 **清除 Odo** 將其刪除，然後按鈕顯示 **選擇 Odo**。
6. 使用**顯示地標**進行審查。 使用**編輯 OCR** 修復誤讀或**新增**遺漏的文字。
7. 填寫**車輛名稱**（必填），再加上您喜歡的品牌/型號/年份/車牌。
8. 點選**建立車輛**或**儲存變更**（新車輛需要名稱+參考照片）。

![新增車輛表單欄位](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>地標：修正 Discovery 錯過的內容

**顯示地標**之後，捲動清單並更正值。 引擎有時會錯過小數字（例如群集右下角的時鐘 **60**）。 使用**編輯 OCR** 添加或修復它們，以便車輛身份保持可靠。

![地標對話框 — 檢視與編輯 OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>在沒有完美照片的情況下打字

您仍然可以透過選擇車輛並**輸入**里程表、體積和填充成本來使用該應用程式 - OCR 對於每個欄位都是可選的。 當您不想在應用程式內拍攝時，圖庫匯入適用於參考破折號照片。

**提示：** 電子表格同步後，車輛定義（作物、地標）會保存在本地資料庫中 — 您無需重新開啟「管理車輛 for Fill」即可使用它們。

---

## <a id="backups-and-multi-device-sync"></a>備份與多設備同步

該應用程式的構建使得**多部手機或平板電腦可以共享相同的車隊數據**，因此您可以在設備上保留**數據和照片的副本**。 這是透過您在您的帳戶或自託管伺服器下配置的目的地來完成的，而不是其他人可以看到的公司運行的「車輛費用雲」。

### <a id="what-runs-where"></a>什麼在哪裡運行

|親切 |它存放什麼 |典型用途|
|------|----------------|-------------|
| **電子表格/表格同步** |車輛、加油、費用（行和選項卡）|多設備合併+結構化備份|
| **照片備份** |二進位影像（破折號/幫浦/收據/參考照片）|照片備份+恢復遺失檔案|

您可以設定每種類型的**多個目的地**（每種類型的軟上限）。 手動**立即同步**和**後台**工作人員運行已啟用的工作人員。

### <a id="offline-first"></a>先離線

- **無需網路**即可新增填寫、費用或收據。 一切首先保存在本地**。
- 當網路可用時，同步和照片備份將作為 **後台任務** 運行（按照您設定的時間表，當您點擊 **立即同步** 時）。 失敗在「設定」行下顯示為紅色文本，並在應用程式標題列中顯示為 **!**。

### <a id="your-accounts-only"></a>僅限您的帳戶

登入和令牌會保留在您選擇的提供者（Google、Microsoft、S3 金鑰、自託管 URL 等）的裝置上。 目的地由**使用者完全控制** - 您的 Google 帳戶、您的 OneDrive、您的 MinIO 儲存桶、您的 EtherCalc 主機等。不會透過共享後端與其他車輛費用用戶分享任何內容。

### <a id="supported-targets--data-spreadsheet--tabular"></a>支援的目標 — 資料（電子表格/表格）

在**選單→同步→電子表格同步**下配置（也可以從設定摘要行存取）。 一流的選擇器選項：

|目標|筆記|
|--------|--------|
| **Google表格** |常見預設值；車輛、費用和每輛車燃料的標籤 |
| **Excel** |透過 Graph/OneDrive 式綁定的 Microsoft 工作簿 |
| **以太計算** |自架協作電子表格室 |
| **其他 →** 實作的後端 | **Baserow**、**NocoDB**、**Airtable**、**PocketBase**、**Supabase**、**Firebase**、**Zoho Sheet** |

已延遲/尚未無頭（列在「其他」下但未完全實現）：OnlyOffice、Collabora。 另請參閱[自託管索引](reference/self-host/INDEX.md)。

CSV **匯出/匯入**（相同選項卡佈局的 ZIP）可從「設定」中作為便攜式備份使用，獨立於即時同步。

### <a id="supported-targets--photos-image-backup"></a>支援的目標 — 照片（影像備份）

在**選單→同步→照片備份**下配置（也來自設定摘要行）：

|目標|筆記|
|--------|--------|
| **Google雲端硬碟** |您選擇的資料夾（瀏覽或貼上 URL）|
| **OneDrive** | Microsoft 帳號 + 路徑前綴 |
| **S3** | AWS、Wasabi、Cloudflare R2、MinIO 和其他 S3 相容端點 |
| **其他** | rclone 支援的儲存（例如 WebDAV、SFTP 和應用程式內選擇器中提供的其他精選遙控器）|

設定自託管照片和表格目標的備忘單：[自託管索引](reference/self-host/INDEX.md)。

### <a id="multi-device-behavior-short"></a>多設備行為（短）

- 按 **Sync ID** 與 **Updated** 時間戳上的 **last-write-wins** 合併行。
- 軟體刪除；在另一台裝置上進行較新的編輯可以恢復一行。
- 在兩台裝置上輸入**相同的填充兩次**會建立**兩行** - 當您注意到時刪除多餘的內容。
- 更多詳細資訊：[同步行為註解](#sync-behavior-notes) 和 [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md)。

### <a id="example-add-google-sheets-data"></a>範例：新增 Google 試算表（資料）

1. **選單→同步→電子表格同步**（或設定→電子表格同步）。

![電子表格同步列表](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. 點選 **新增電子表格目標**。

![提供者選擇器](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. 選擇 **Google 表格**。

![Google 表格表格](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **使用 Google 登入**一次 - 該登入還可以讓您瀏覽。 顯示名稱 → 貼上 **工作表 URL**（仍然有效）或 **🔍** 瀏覽。 瀏覽將在**全部和共享**上打開，以便您可以選擇您擁有的或與您共享的現有電子表格。 然後安排選項→啟用→儲存。
5. **立即同步**一次以建立/更新選項卡：`Vehicles`、`Expenses`、`Fuel - {vehicle name}`。

### <a id="example-add-google-drive-photos"></a>範例：新增 Google Drive（照片）

1. **選單→同步→照片備份**（或設定→照片備份）。

![照片備份清單](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. 點選**新增照片目的地**。

![照片提供者選擇器](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. 選擇 **Google 雲端硬碟**。

![Google 雲端硬碟表單](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **使用 Google（雲端硬碟）登入** → 可選資料夾 URL/瀏覽 → 啟用 → 儲存 → **立即同步**。

手動**立即同步**照片是完全通行證；後台備份通常按計劃處理**僅待處理**上傳。

### <a id="sync-behavior-notes"></a>同步行為註釋

- 應用程式升級後，您可能會短暫看到 **「升級後正在更新資料庫...」**（本地同步 ID 回填）。
- 如果同步中斷，下一個**成功的**同步將重新合併並修復遠端選項卡。
- 失敗：同步卡上的紅色摘要 + 應用程式欄中的 **!**。

---

## <a id="fill-up-fuel"></a>填充（燃料）

這是您開啟應用程式時的**主畫面**。

### <a id="vehicle-selection-usually-automatic"></a>車輛選擇（通常是自動的）

您**不需要**需要先選擇車輛。 當車輛在管理車輛中設定了**地標**時，填充**會在捕獲里程表後從儀表板影像中自動偵測哪輛車**。 如果需要，您仍然可以打開 **車輛** 下拉清單進行覆蓋。

### <a id="aim-at-the-odometer"></a>瞄準里程表

保持里程表模式並建立叢集。 說明： *瞄準里程表。 點擊快門進行拍攝。 *

![填充 — 里程表上的即時相機（預覽中的破折號影像範例）](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>里程表快門後

OCR 填充 **Odo** 並嘗試從地標匹配車輛（如果需要，請查看兩者）。 主按鈕變為**重試**以重新拍攝。 指導總結了閱讀內容。

![填寫－里程表OCR結果](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>泵浦模式（成本與體積）

1. 點選 **↕** 切換到泵浦模式： *瞄準泵浦顯示（成本/體積）。 點擊快門。 *
2. 捕獲泵總量。 成本和數量欄位填寫；如果它們被交換，則使用 **↔**。
3. 如有需要，點選貨幣或**總帳**，然後**儲存**（磁碟）。 空白字段會**部分填充**（仍然允許）。

![填充 — 泵浦預覽/總計（預覽中的泵浦影像範例）](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

您將停留在「填滿」位置前往下一站（儲存後欄位會被清除）。 完全**離線**；配置後，同步稍後在背景運行。

### <a id="manual-entry-no-camera--bad-ocr"></a>手動輸入（無相機/OCR 不良）

1. 點選 **Odo**、**cost** 或 **volume** 並鍵入數值（縱向使用系統鍵盤；橫向使用螢幕鍵盤）。
2. 如果自動偵測未運行，請選擇或確認**車輛**。
3. 保存如上。

### <a id="modes-and-borders"></a>模式與邊框

- **車輛+里程周圍的綠色邊框**→捕獲/編輯里程表。
- **綠色邊框**圍繞成本+體積 → 泵浦模式。
- **儲存**保持停用狀態，直到選擇車輛並且 odo/cost/volume 中至少一項有數據，並且 OCR 不再運作。

螢幕提示（指令列下方）： *快門 = 捕捉 · 磁碟 = 儲存 · ↕ = odo/pump 模式 · ↔ = 交換成本/體積。 *

---

## <a id="expenses"></a>費用

### <a id="new-expense"></a>新費用

菜單 → **新費用**。

![新的費用表格和相機控制（例如空時相機帶中的破折號影像）](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **儲存**（磁碟）、**快門**（收據照片）或**圖庫**（選擇影像）。
2. 填寫**日期**、**車輛**、**供應商**、**描述**、**金額**（可點擊貨幣符號）、**類別**、可選**里程表**。
3. 多頁收據：如果 UI 提供分頁功能，則捕獲其他頁面（第 0 頁是主收據）。
4. **儲存** 進行儲存（本地優先；配置後，照片備份和電子表格同步在後台進行）。

### <a id="expense-list"></a>費用清單

選單→**報告**→**費用清單**－瀏覽過去的非燃油費用；開啟要編輯的項目。

![費用清單](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>編輯費用

從清單中開啟一行。 正確的供應商、數量、類別、車輛和描述。 如果收據僅存在於照片備份中（沒有可讀的本機檔案），請在顯示時使用 **從檔案中取得影像**（適用於配置的照片目標）。

![編輯費用](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>開始旅程

選單 → **開始旅行**（填滿抽屜後）。 擷取或輸入里程表，選擇行程類型，使用 **磁碟** 圖示儲存。 **停止** 是「個人」現在處於保持的 GPS 位置的快捷方式。 使用 **ⓘ** 進行控制提醒。

![開始旅程](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

行程開始儲存為具有 **行程類型** 的燃油行（不是正常填充）。 它們出現在**報告 → 行程里程**下，而不是在燃油歷史記錄下。

---

## <a id="reports"></a>報告

選單 → **報告** 開啟產品中心（所有時間摘要 + 目錄卡）。 這是唯一的產品報告介面——沒有單獨的「報告和圖表」抽屜項目。

![報告中心](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

開啟車輛模式卡（**全部/每個/單一**）、期間過濾器、圖表和共用（**TEXT / CSV / PDF**）。 報告兒童的頂部欄：**☰ + ←**（註冊時還有 **ⓘ**）。

### <a id="time-based-reports"></a>基於時間的報告

主圖卡。 可選指標（英里/加侖、體積/距離（如 G/mi）、單價（如 $/G）、成本/距離、每月 $、行程里程、按類型劃分的行程百分比），帶有**平滑**箱和**獨立 Y 刻度**（左側為經濟；右側為金錢和旅行系列）。

![基於時間的報告](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![基於時間的報告 — 滾動指標](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

經濟數學詳細資料：[REPORTS_METRICS.md](reference/REPORTS_METRICS.md)。

### <a id="fill-history-vs-fuel-history"></a>填充歷史記錄與燃油歷史記錄

- **報告 → 填充歷史記錄** — 依時間順序填入報告篩選器（**僅填入**；不開始行程）。

![填入歷史報告](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **燃料歷史記錄**（如果存在於您的建造導航中）- 每輛車的填充庫存，也僅填充；點擊一行進行編輯。

### <a id="trip-miles"></a>行程里程

**報告 → 行程里程** — 按類型、圖表和按時間順序排列的 **行程開始/航段清單**。 點擊真正的開始以打開該行的**編輯填充**。

![行程里程](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>編輯填充

從填充歷史記錄、燃油歷史記錄或行程里程中，開啟填充。 版面：車輛和里程表、**貨幣先於成本**、數量、註釋。 只有當該行是行程開始時，行程類型才會出現。 位置有摘要以及**位置詳細資訊**。 缺少具有雲端身分的本地照片：**從檔案中取得影像**。

![編輯填充 — 本地照片遺失時從檔案取得](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

其他目錄卡包括按類別列出的費用、車輛摘要和費用清單。

Money 在設定時使用每行的貨幣。 混合貨幣總計顯示**每種貨幣小計**（無靜默外匯轉換）。

---

## <a id="syncing"></a>同步中

選單 → **同步** 是電子表格和照片目的地的中心（不僅隱藏在「設定」下）。

![同步中心](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- 用於**電子表格同步**和**照片備份**的卡片，具有短期狀態，**同步**用於此類，以及**›**到目標列表。
- 開啟一個目標用於 **測試連接** 和 **立即同步（此目標）** / 全部配置。
- 失敗**詳細資料**和標題欄中的紅色**！ **落在此處。
- 逐步 Google 試算表和雲端硬碟設定：[備份和多裝置同步](#backups-and-multi-device-sync)。

---

## <a id="settings-local-preferences"></a>設定（本地首選項）

選單 → **設定**。

![設定](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![更多設定（滾動）](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

對於目的地，首選**選單 → 同步**。 設定可能仍會顯示開啟相同清單的摘要行。

### <a id="local-preferences-common"></a>當地偏好（常見）

- **儲存燃油收據照片** / **本地保存費用照片** — 將圖像保留在設備上（可能需要照片許可）。
- **播放快門聲音**
- **貨幣** / **體積單位** — 應用程式預設值（系統或明確）。 使用現有燃料資料更改體積單位可能會提供轉換對話框。
- **深色模式**
- **設定提示** — 重新開啟首次運行車輛/同步教學。
- **調試填充** / **顯示實驗螢幕（開發）** — 高級；留作日常使用。 此處未記錄實驗畫面。

CSV **匯出/匯入**（Vehicles / Expenses / Fuel 分頁的 ZIP）可在設定中使用，前提是當前版本提供此功能。

---

## <a id="help--about"></a>幫助和關於

![求助](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![關於](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **幫助** — 設備上快速入門、設定教學、本手冊連結、自架設定索引。
- **關於** — 版本、許可證、GitHub、本手冊（發佈時捆綁離線 + 線上 HTML）。

---

## <a id="related-docs"></a>相關文檔

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — 簡短的日常指南
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — 自託管照片/表格設定
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — 合併、恢復、重複
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — 經濟指標詳細資訊
