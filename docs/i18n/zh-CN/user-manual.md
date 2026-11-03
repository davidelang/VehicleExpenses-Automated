# <a id="vehicle-expenses-automated--user-manual"></a>车辆费用自动化 — 用户手册

> **编辑源代码 (Markdown)。** 浏览器和应用内阅读器打开 **渲染的 HTML**：
> - 网页：[`docs/user-manual/index.html`](index.html)（用`./scripts/render-user-manual.sh`重新生成）
> - 应用程序：帮助/关于 → 完整手册（捆绑 HTML + 屏幕截图）
>
> 不要将最终用户指向原始 `.md` URL — 浏览器仅显示纯文本。

摄像头优先跟踪燃油加注量和车辆费用，并可选择在**您的**云帐户下进行多设备同步和备份。

这是**完整手册**（截图+每一步）。 在电话上，**菜单→帮助**是较短的入门指南。

**此处未涵盖：** 导入旧图片、对齐实验和泵实验（开发人员/高级工具）。

---

## <a id="table-of-contents"></a>目录

1. [你需要什么](#what-you-need)
2. [图标一览](#icons-at-a-glance)
3. [打开菜单](#open-the-menu)
4.【首次设置：管理车辆】(#first-time-setup-manage-vehicles)
5.【备份与多设备同步】(#backups-and-multi-device-sync)
6. [加注（燃料）](#fill-up-fuel)
7. [开始行程](#start-trip)
8. [费用](#expenses)
9. [报告](#reports)
10. [设置（本地偏好）](#settings-local-preferences)
11. [同步中](#syncing)
12. [帮助和关于](#help--about)
13. [相关文档](#related-docs)

---

## <a id="what-you-need"></a>你需要什么

- Android 手机或平板电脑。
- 为了获得最佳 OCR：您的**仪表板里程表**和**泵总数**的清晰视图（或手动输入数字）。
- 可选：您控制的帐户**用于电子表格数据和/或照片备份（请参阅[备份和多设备同步](#backups-and-multi-device-sync)）。

---

## <a id="icons-at-a-glance"></a>图标一览

这些出现在主屏幕上。 了解它们可以节省很多狩猎时间。

| 哪里 | 图标/控制| 它有什么作用 |
|-------|----------------|--------------|
| 顶栏| **☰ 菜单**（汉堡）| 打开导航抽屉|
| 顶栏| **ⓘ**（页面帮助）| **当前**页面的简短帮助（如果可用，则位于菜单旁边）|
| 顶栏| **`?N`**（黄色）| 待处理的导入审核问题 — 打开导入审核 |
| 顶栏| **！**（红色）| 电子表格或照片目标最近失败 - 打开 **同步** 进行修复 |
| 顶栏| **☰ + ←** | 报告儿童和费用清单一起显示**菜单和背面**； 报告中心仅是菜单 |
| 设置/燃料编辑| **←** | 返回（设置电子表格/照片和燃料编辑保持返回焦点）|
| 填写 | **白色圆圈**（快门）| 捕获里程表或泵显示以进行 OCR |
| 填写 | **磁盘/保存** | 节省加油量（需要一辆车以及里程/体积/成本中至少一项）|
| 填写 | **↕ 箭头**（模式切换）| 切换 **里程表模式** 与 **泵（成本/体积）模式**。 绿色边框突出显示活动字段组 |
| 填写 | **↔ 箭头**（成本与数量之间）| 如果 OCR 将成本和数量放入错误的字段，则交换成本和数量 |
| 填写 | **变焦 1 倍 / …** | 镜头支持时的相机变焦比 |
| 填充（捕获后）| **在主按钮上刷新** | 放弃预览并返回实时摄像头 |
| 填充（处理时）| 主按钮上的 **X** | 取消正在进行的捕获/OCR |
| 费用| **保存** | 节省开支 |
| 费用| **快门圈** | 拍摄收据照片 |
| 费用| **画廊** | 从库中选择一张收据图像 |
| 费用| **重考** | 清除当前收据照片并重新拍摄 |
| 费用/管理车辆| **+ / −** FAB | 缩放照片预览 |
| 地标对话框 | **编辑 OCR** | 更正或添加引擎错过的标志性文本 |
| 电子表格/照片表格| **🔍 搜索** | 浏览 Google 云端硬盘查找工作表或文件夹（登录后）|

成本字段上的货币符号和体积字段上的 **G/L** 可点击：点击后会打开一个小菜单，可以更改该条目的货币或加仑与升的单位。

---

## <a id="open-the-menu"></a>打开菜单

1. 点击左上角的**☰**。
2. 选择一个页面。

![导航抽屉](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**主抽屉：** 填写·开始行程·管理车辆·新费用·**报告**·设置·同步·帮助·关于。

**实验抽屉**（设置→显示实验屏幕）：对齐实验·泵实验·**导入旧图片**。

**通过报告中心（不是主抽屉）：** 费用列表 · 填写历史记录。

---

## <a id="first-time-setup-manage-vehicles"></a>首次设置：管理车辆

OCR 和 **自动车辆匹配** 在你为每辆车注册一张 **参考仪表盘照片**、运行 **Discovery**、从踏板种子设置里程表裁剪，并为该仪表盘保存标志性位置后效果最佳。

### <a id="open-manage-vehicles"></a>打开管理车辆

菜单 → **管理车辆**。 选择车辆（或**添加新车辆**）。

![通过参考破折号、作物和发现来管理车辆](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>添加或编辑车辆

1. 打开**车辆**下拉菜单 → 选择车辆或**添加新车辆**。
2. 拍摄或挑选一张清晰的**参考仪表板照片**（完整的仪表组、光线充足、手机大致呈方形）。 使用**拍照**或**图库**。
3. 点击 **运行发现** — 桨式检测将黄色 **种子** 放置在照片上（地标使用与之前相同的 `"ML Kit"` JSON）。
4. 点击 **选择 Odo**，点击数字上的种子，调整手柄，然后 **保存**。
5. **编辑 Odo** 调整已有的框。 **清除 Odo** 将其删除，然后按钮显示 **选择 Odo**。
6. 使用**显示地标**进行审查。 使用**编辑 OCR** 修复误读或**添加**遗漏的文本。
7. 填写**车辆名称**（必填），再加上您喜欢的品牌/型号/年份/车牌。
8. 点击**创建车辆**或**保存更改**（新车辆需要名称+参考照片）。

![添加新车辆表单字段](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>地标：修复 Discovery 错过的内容

**显示地标**之后，滚动列表并更正值。 引擎有时会错过小数字（例如集群右下角的时钟 **60**）。 使用**编辑 OCR** 添加或修复它们，以便车辆身份保持可靠。

![地标对话框 — 查看和编辑 OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>在没有完美照片的情况下打字

您仍然可以通过选择车辆并**输入**里程表、体积和填充成本来使用该应用程序 - OCR 对于每个字段都是可选的。 当您不想在应用程序内拍摄时，图库导入适用于参考破折号照片。

**提示：** 电子表格同步后，车辆定义（作物、地标）会保存在本地数据库中 — 您无需重新打开“管理车辆 for Fill”即可使用它们。

---

## <a id="backups-and-multi-device-sync"></a>备份和多设备同步

该应用程序的构建使得**多部手机或平板电脑可以共享相同的车队数据**，因此您可以在设备上保留**数据和照片的副本**。 这是通过您在您的帐户或自托管服务器下配置的目的地来完成的，而不是其他人可以看到的公司运行的“车辆费用云”。

### <a id="what-runs-where"></a>什么在哪里运行

| 亲切 | 它存储什么 | 典型用途|
|------|----------------|-------------|
| **电子表格/表格同步** | 车辆、加油、费用（行和选项卡）| 多设备合并+结构化备份|
| **照片备份** | 二进制图像（破折号/泵/收据/参考照片）| 照片备份+恢复丢失文件|

您可以配置每种类型的**多个目的地**（每种类型的软上限）。 手动**立即同步**和**后台**工作人员运行已启用的工作人员。

### <a id="offline-first"></a>先离线

- **无需网络**即可添加填写、费用或收据。 一切都首先保存在本地**。
- 当网络可用时，同步和照片备份将作为 **后台任务** 运行（按照您设置的时间表，当您点击 **立即同步** 时）。 失败在“设置”行下显示为红色文本，并在应用程序标题栏中显示为 **!**。

### <a id="your-accounts-only"></a>仅限您的帐户

登录和令牌会保留在您选择的提供商（Google、Microsoft、S3 密钥、自托管 URL 等）的设备上。 目的地由**用户完全控制** - 您的 Google 帐户、您的 OneDrive、您的 MinIO 存储桶、您的 EtherCalc 主机等。不会通过共享后端与其他车辆费用用户共享任何内容。

### <a id="supported-targets--data-spreadsheet--tabular"></a>支持的目标 — 数据（电子表格/表格）

在**菜单→同步→电子表格同步**下配置（也可以从设置摘要行访问）。 一流的选择器选项：

| 目标| 笔记|
|--------|--------|
| **谷歌表格** | 常见默认值； 车辆、费用和每辆车燃料的标签 |
| **Excel** | 通过 Graph/OneDrive 式绑定的 Microsoft 工作簿 |
| **以太计算** | 自托管协作电子表格室 |
| **其他 →** 实施的后端 | **Baserow**、**NocoDB**、**Airtable**、**PocketBase**、**Supabase**、**Firebase**、**Zoho Sheet** |

已推迟/尚未无头（列在“其他”下但未完全实现）：OnlyOffice、Collabora。 另请参阅[自托管索引](reference/self-host/INDEX.md)。

CSV **导出/导入**（相同选项卡布局的 ZIP）可从“设置”中作为便携式备份使用，独立于实时同步。

### <a id="supported-targets--photos-image-backup"></a>支持的目标 — 照片（图像备份）

在**菜单→同步→照片备份**下配置（也来自设置摘要行）：

| 目标| 笔记|
|--------|--------|
| **谷歌云端硬盘** | 您选择的文件夹（浏览或粘贴 URL）|
| **OneDrive** | Microsoft 帐户 + 路径前缀 |
| **S3** | AWS、Wasabi、Cloudflare R2、MinIO 和其他 S3 兼容端点 |
| **其他** | rclone 支持的存储（例如 WebDAV、SFTP 和应用内选择器中提供的其他精选遥控器）|

设置自托管照片和表格目标的备忘单：[自托管索引](reference/self-host/INDEX.md)。

### <a id="multi-device-behavior-short"></a>多设备行为（短）

- 按 **Sync ID** 与 **Updated** 时间戳上的 **last-write-wins** 合并行。
- 软删除； 在另一台设备上进行较新的编辑可以恢复一行。
- 在两台设备上输入**相同的填充两次**会创建**两行** - 当您注意到时删除多余的内容。
- 更多详细信息：[同步行为注释](#sync-behavior-notes) 和 [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md)。

### <a id="example-add-google-sheets-data"></a>示例：添加 Google 表格（数据）

1. **菜单→同步→电子表格同步**（或设置→电子表格同步）。

![电子表格同步列表](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. 点击 **添加电子表格目标**。

![提供商选择器](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. 选择 **Google 表格**。

![Google 表格表格](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **使用 Google 登录**一次 - 该登录还可以让您浏览。 显示名称 → 粘贴 **工作表 URL**（仍然有效）或 **🔍** 浏览。 浏览将在**全部和共享**上打开，以便您可以选择您拥有的或与您共享的现有电子表格。 然后安排选项→启用→保存。
5. **立即同步**一次以创建/更新选项卡：`Vehicles`、`Expenses`、`Fuel - {vehicle name}`。

### <a id="example-add-google-drive-photos"></a>示例：添加 Google Drive（照片）

1. **菜单→同步→照片备份**（或设置→照片备份）。

![照片备份列表](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. 点击**添加照片目的地**。

![照片提供者选择器](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. 选择 **Google 云端硬盘**。

![Google 云端硬盘表单](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **使用 Google（云端硬盘）登录** → 可选文件夹 URL/浏览 → 启用 → 保存 → **立即同步**。

手动**立即同步**照片是完全通行证； 后台备份通常按计划处理**仅待处理**上传。

### <a id="sync-behavior-notes"></a>同步行为注释

- 应用程序升级后，您可能会短暂看到 **“升级后正在更新数据库...”**（本地同步 ID 回填）。
- 如果同步中断，下一个**成功的**同步将重新合并并修复远程选项卡。
- 失败：同步卡上的红色摘要 + 应用栏中的 **!**。

---

## <a id="fill-up-fuel"></a>填充（燃料）

这是您打开应用程序时的**主屏幕**。

### <a id="vehicle-selection-usually-automatic"></a>车辆选择（通常是自动的）

您**不需要**需要先选择车辆。 当车辆在管理车辆中设置了**地标**时，填充**会在捕获里程表后从仪表板图像中自动检测哪辆车**。 如果需要，您仍然可以打开 **车辆** 下拉列表进行覆盖。

### <a id="aim-at-the-odometer"></a>瞄准里程表

保持里程表模式并构建集群。 说明： *瞄准里程表。 点击快门进行拍摄。*

![填充 — 里程表上的实时摄像头（预览中的破折号图像示例）](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>里程表快门后

OCR 填充 **Odo** 并尝试从地标匹配车辆（如果需要，请查看两者）。 主按钮变为**重试**以重新拍摄。 指导总结了阅读内容。

![填写——里程表OCR结果](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>泵模式（成本和体积）

1. 点击 **↕** 切换到泵模式： *瞄准泵显示（成本/体积）。 点击快门。*
2. 捕获泵总量。 成本和数量字段填写； 如果它们被交换，则使用 **↔**。
3. 如果需要，点击货币或**总账**，然后**保存**（磁盘）。 空字段会**部分填充**（仍然允许）。

![填充 — 泵预览/总计（预览中的泵图像示例）](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

您将停留在“填充”位置前往下一站（保存后字段会被清除）。 完全**离线**； 配置后，同步稍后在后台运行。

### <a id="manual-entry-no-camera--bad-ocr"></a>手动输入（无摄像头/OCR 不良）

1. 点击 **Odo**、**cost** 或 **volume** 并键入值（纵向使用系统键盘；横向使用屏幕键盘）。
2. 如果自动检测未运行，请选择或确认**车辆**。
3. 保存如上。

### <a id="modes-and-borders"></a>模式和边框

- **车辆+里程周围的绿色边框**→捕获/编辑里程表。
- **绿色边框**围绕成本+体积 → 泵模式。
- **保存**保持禁用状态，直到选择车辆并且 odo/cost/volume 中至少一项有数据，并且 OCR 不再运行。

屏幕提示（指令行下方）： *快门 = 捕获 · 磁盘 = 保存 · ↕ = odo/pump 模式 · ↔ = 交换成本/体积。*

---

## <a id="expenses"></a>费用

### <a id="new-expense"></a>新费用

菜单 → **新费用**。

![新的费用表格和相机控件（例如空时相机带中的破折号图像）](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **保存**（磁盘）、**快门**（收据照片）或**图库**（选择图像）。
2. 填写**日期**、**车辆**、**供应商**、**描述**、**金额**（可点击货币符号）、**类别**、可选**里程表**。
3. 多页收据：如果 UI 提供分页功能，则捕获其他页面（第 0 页是主收据）。
4. **保存** 进行存储（本地优先；配置后，照片备份和电子表格同步在后台进行）。

### <a id="expense-list"></a>费用清单

菜单→**报告**→**费用清单**——浏览过去的非燃油费用； 打开要编辑的项目。

![费用清单](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>编辑费用

从列表中打开一行。 正确的供应商、数量、类别、车辆和描述。 如果收据仅存在于照片备份中（没有可读的本地文件），请在显示时使用 **从存档中获取图像**（适用于配置的照片目标）。

![编辑费用](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>开始旅程

菜单 → **开始旅行**（填满抽屉后）。 捕获或输入里程表，选择行程类型，使用 **磁盘** 图标保存。 **停止** 是“个人”现在处于保持的 GPS 位置的快捷方式。 使用 **ⓘ** 进行控制提醒。

![开始旅程](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

行程开始存储为具有 **行程类型** 的燃油行（不是正常填充）。 它们出现在**报告 → 行程里程**下，而不是在燃油历史记录下。

---

## <a id="reports"></a>报告

菜单 → **报告** 打开产品中心（所有时间摘要 + 目录卡）。 这是唯一的产品报告界面——没有单独的“报告和图表”抽屉项目。

![报告中心](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

打开车辆模式卡片（**全部/每个/单个**）、期间过滤器、图表和共享（**TEXT / CSV / PDF**）。 报告儿童的顶部栏：**☰ + ←**（注册时还有 **ⓘ**）。

### <a id="time-based-reports"></a>基于时间的报告

主图表卡。 可选指标（英里/加仑、体积/距离（如 G/mi）、单价（如 $/G）、成本/距离、每月 $、行程里程、按类型划分的行程百分比），带有**平滑**箱和**独立 Y 刻度**（左侧为经济；右侧为金钱和旅行系列）。

![基于时间的报告](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![基于时间的报告 — 滚动指标](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

经济数学详细信息：[REPORTS_METRICS.md](reference/REPORTS_METRICS.md)。

### <a id="fill-history-vs-fuel-history"></a>填充历史记录与燃油历史记录

- **报告 → 填充历史记录** — 按时间顺序填充报告过滤器（**仅填充**；不开始行程）。

![填写历史报告](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **燃料历史记录**（如果存在于您的构建导航中）- 每辆车的填充库存，也仅填充； 点击一行进行编辑。

### <a id="trip-miles"></a>行程里程

**报告 → 行程里程** — 按类型、图表和按时间顺序排列的 **行程开始/航段列表**。 点击真正的开始以打开该行的**编辑填充**。

![行程里程](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>编辑填充

从填充历史记录、燃油历史记录或行程里程中，打开填充。 布局：车辆和里程表、**货币先于成本**、数量、注释。 仅当该行是行程开始时，行程类型才会出现。 位置有摘要以及**位置详细信息**。 缺少具有云身份的本地照片：**从存档中获取图像**。

![编辑填充 — 本地照片丢失时从存档中获取](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

其他目录卡包括按类别列出的费用、车辆摘要和费用清单。

Money 在设置时使用每行的货币。 混合货币总计显示**每种货币小计**（无静默外汇转换）。

---

## <a id="syncing"></a>同步中

菜单 → **同步** 是电子表格和照片目的地的中心（不仅隐藏在“设置”下）。

![同步中心](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- 用于**电子表格同步**和**照片备份**的卡片，具有短期状态，**同步**用于此类，以及**›**到目标列表。
- 打开一个目标用于 **测试连接** 和 **立即同步（此目标）** / 全部配置。
- 失败**详细信息**和标题栏中的红色**！**落在此处。
- 分步 Google 表格和云端硬盘设置：[备份和多设备同步](#backups-and-multi-device-sync)。

---

## <a id="settings-local-preferences"></a>设置（本地首选项）

菜单 → **设置**。

![设置](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![更多设置（滚动）](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

对于目的地，首选**菜单 → 同步**。 设置可能仍会显示打开相同列表的摘要行。

### <a id="local-preferences-common"></a>当地偏好（常见）

- **保存燃油收据照片** / **本地保存费用照片** — 将图像保留在设备上（可能需要照片许可）。
- **播放快门声音**
- **货币** / **体积单位** — 应用程序默认值（系统或显式）。 使用现有燃料数据更改体积单位可能会提供转换对话框。
- **深色模式**
- **设置提示** — 重新打开首次运行车辆/同步教程。
- **调试填充** / **显示实验屏幕（开发）** — 高级； 留作日常使用。 此处未记录实验屏幕。

CSV **导出/导入**（车辆/费用/燃料标签的 ZIP 文件）可以从设置中获取，前提是当前版本提供此功能。

---

## <a id="help--about"></a>帮助和关于

![求助](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![关于](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **帮助** — 设备上快速入门、设置教程、本手册链接、自托管设置索引。
- **关于** — 版本、许可证、GitHub、本手册（发布时捆绑离线 + 在线 HTML）。

---

## <a id="related-docs"></a>相关文档

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — 简短的日常指南
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — 自托管照片/表格设置
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — 合并、恢复、重复
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — 经济指标详细信息
