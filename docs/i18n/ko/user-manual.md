# <a id="vehicle-expenses-automated--user-manual"></a>차량 경비 자동화 — 사용자 매뉴얼

> **원본 편집(마크다운).** 브라우저와 앱 내 리더는 **렌더링된 HTML**을 엽니다:
> - 웹: [`docs/user-manual/index.html`](index.html) (`./scripts/render-user-manual.sh`로 재생성)
> - 앱: 도움말 / 정보 → 전체 매뉴얼 (번들된 HTML + 스크린샷)
>
> 최종 사용자를 원시 `.md` URL로 안내하지 마세요 — 브라우저는 텍스트만 표시합니다.

연료 주입 및 차량 비용을 위한 카메라 우선 트래킹, 선택적으로 여러 기기 동기화 및 **사용자** 클라우드 계정 아래 백업 제공.

이것은 **전체 매뉴얼**입니다 (스크린샷 + 모든 단계 포함). 휴대폰에서는 **메뉴 → 도움말**이 더 짧은 시작 가이드입니다.

**여기서 다루지 않음:** 이전 사진 가져오기, 정렬 실험, 펌프 실험 (개발자 / 고급 도구).

---

## <a id="table-of-contents"></a>목차

1. [당신이 필요한 것](#what-you-need)
2. [한눈에 보는 아이콘](#icons-at-a-glance)
3. [메뉴 열기](#open-the-menu)
4. [최초 설정: 차량 관리](#first-time-setup-manage-vehicles)
5. [백업 및 다중 기기 동기화](#backups-and-multi-device-sync)
6. [연료 채우기](#fill-up-fuel)
7. [여행 시작](#start-trip)
8. [비용](#expenses)
9. [보고서](#reports)
10. [설정 (로컬 환경 설정)](#settings-local-preferences)
11. [동기화](#syncing)
12. [도움말 및 정보](#help--about)
13. [관련 문서](#related-docs)

---

## <a id="what-you-need"></a>당신에게 필요한 것

- 안드로이드 전화기 또는 태블릿.
- 최고의 OCR을 위해: **계기판 주행 거리계**와 **펌프 총계**를 명확하게 보여 주세요 (또는 숫자를 수동으로 입력하세요).
- 선택 사항: 스프레드시트 데이터 및/또는 사진 백업용으로 **사용자가 관리하는** 계정(참조: [백업 및 다중 기기 동기화](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>한눈에 보는 아이콘

이것들은 메인 화면에 나타납니다. 이것들을 알면 많은 수색을 절약할 수 있습니다.

| 위치 | 아이콘 / 컨트롤 | 기능
|-------|----------------|--------------|
| 상단 바 | **☰ 메뉴** (햄버거) | 내비게이션 서랍을 엽니다 |
| 상단 바 | **ⓘ** (페이지 도움말) | **현재** 페이지에 대한 간단한 도움말 (사용 가능할 경우 메뉴 옆) |
| 상단 바 | **`?N`** (노란색) | 가져오기 검토 대기 질문 — 가져오기 검토 열기 |
| 상단 바 | **!** (빨강) | 스프레드시트나 사진 목적지가 최근에 실패했습니다 — **동기화**를 열어 수정하세요 |
| 상단 바 | **☰ + ←** | 자녀 및 지출 목록 보고 시 **메뉴와 뒤로 가기**가 함께 표시됨; 보고서 허브는 메뉴만 표시됨 |
| 설정 / 연료 편집 | **←** | 뒤로 (설정 스프레드시트/사진과 연료 편집은 뒤로 초점 유지) |
| 채우기 | **하얀 원** (셔터) | OCR용으로 주행거리계 또는 펌프 디스플레이 캡처
| 주유 | **디스크 / 저장** | 주유를 저장합니다 (차량과 주행거리/용량/비용 중 적어도 하나 필요) |
| 채우기 | **↕ 화살표** (모드 전환) | **주행거리계 모드**와 **펌프(비용/용량) 모드** 전환. 녹색 테두리가 활성 필드 그룹을 강조 표시
| 채우기 | **↔ 화살표** (비용과 수량 사이) | OCR이 잘못된 필드에 비용과 수량을 넣었으면 서로 교환
| 채우기 | **줌 1배 / …** | 렌즈가 지원할 때 카메라 줌 비율
| 캡처 후 채우기 | 메인 버튼에서 **새로고침** | 미리보기 폐기 후 라이브 카메라로 돌아가기 |
| 채우기(처리 중) | 메인 버튼에서 **X** | 진행 중인 캡처/OCR 취소 |
| 비용 | **저장** | 비용 저장
| 비용 | **셔터 서클** | 영수증 사진 찍기 |
| 비용 | **갤러리** | 라이브러리에서 영수증 이미지를 선택하세요 |
| 비용 | **재촬영** | 현재 영수증 사진을 지우고 다시 촬영
| 비용 / 차량 관리 | **+ / −** FABs | 사진 미리보기 확대 |
| 랜드마크 대화 | **OCR 편집** | 엔진이 놓친 랜드마크 텍스트를 수정하거나 추가
| 스프레드시트 / 사진 양식 | **🔍 검색** | 시트나 폴더를 위해 Google 드라이브를 탐색하기 (로그인 후) |

비용 필드의 통화 기호와 볼륨 필드의 **G/L**은 탭할 수 있습니다: 해당 항목의 통화 또는 갤런과 리터를 변경할 수 있는 작은 메뉴가 열립니다.

---

## <a id="open-the-menu"></a>메뉴를 열기

1. 왼쪽 상단의 **☰**을 탭하세요.
2. 페이지를 선택하세요.

![탐색 서랍](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**메인 서랍:** 채우기 · 여행 시작 · 차량 관리 · 새 비용 · **보고서** · 설정 · 동기화 · 도움말 · 정보.

**실험 서랍** (설정 → 실험 화면 표시): 정렬 실험 · 펌프 실험 · **예전 사진 가져오기**.

**리포트 허브를 통해 (메인 드로어 아님):** 비용 목록 · 작성 내역.

---

## <a id="first-time-setup-manage-vehicles"></a>첫 설정: 차량 관리

OCR 및 **자동 차량 매칭**은 각 차량을 **참조 대시보드 사진**으로 등록하고, **디스커버리(Discovery)**를 실행하며, 패들 시드(paddle seed)에서 주행거리계 범위를 설정하고 해당 대시보드의 랜드마크를 저장한 후에 가장 잘 작동합니다.

### <a id="open-manage-vehicles"></a>차량 관리 열기

메뉴 → **차량 관리**. 차량을 선택하거나 (**새 차량 추가**)를 선택하세요.

![참조 대시, 작물 및 발견으로 차량 관리](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Add or edit a vehicle

1. **차량** 드롭다운을 열고 → 차량을 선택하거나 **새 차량 추가**를 클릭합니다.
2. 명확한 **참조 대시 사진**(계기판 전체, 잘 조명된, 전화기를 대략 정면으로) 찍거나 선택하세요. **사진 찍기** 또는 **갤러리**를 사용하세요.
3. **Run Discovery**를 탭하세요 — 패들 감지가 사진 위에 노란 **씨앗(seeds)**을 배치합니다 (랜드마크는 이전과 동일한 `"ML Kit"` JSON을 사용합니다).
4. **Odo 선택**을 탭하고, 숫자 위의 시드를 탭한 후, 손잡이를 조정한 다음 **저장**을 탭합니다.
5. **Odo 편집**은 이미 있는 상자를 조정합니다. **Odo 지우기**는 상자를 제거하며, 그러면 버튼에는 **Odo 선택**이라고 표시됩니다.
6. **랜드마크 표시**로 검토하세요. 잘못 읽은 부분을 수정하거나 누락된 텍스트를 **편집 OCR**로 **추가**하세요.
7. **차량 이름**(필수)을 작성하고, 원하는 대로 제조사/모델/연식/번호판도 작성하세요.
8. **차량 생성(Create Vehicle)** 또는 **변경 사항 저장(Save Changes)**를 누르세요 (새 차량의 경우 이름과 참조 사진 필요).

![새 차량 양식 필드 추가](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>랜드마크: 디스커버리가 놓친 것을 고치다

**랜드마크 표시** 후, 목록을 스크롤하며 값을 수정하세요. 엔진은 가끔 작은 숫자를 놓치기도 합니다(예: 클러스터 오른쪽 하단의 시계 **60**). **OCR 편집**을 사용하여 이를 추가하거나 수정하여 차량 신원이 신뢰성을 유지하도록 하세요.

![랜드마크 대화 — OCR 검토 및 편집](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Typing without a perfect photo

차량을 선택하고 주행거리계, 용량, 비용을 Fill에 **입력**하면 여전히 앱을 사용할 수 있습니다 — 모든 필드에서 OCR은 선택 사항입니다. 인앱으로 촬영하고 싶지 않을 때는 참조 대시보드 사진을 위해 갤러리 가져오기가 작동합니다.

**팁:** 스프레드시트 동기화 후, 차량 정의(작물, 랜드마크)는 로컬 데이터베이스에 저장됩니다 — Fill에서 사용하려고 Manage Vehicles를 다시 열 필요가 없습니다.

---

## <a id="backups-and-multi-device-sync"></a>백업 및 다중 장치 동기화

이 앱은 **여러 휴대폰이나 태블릿이 동일한 차량 데이터를 공유할 수 있도록** 제작되었으며, **데이터와 사진의 사본을 기기 외부에 보관할 수 있습니다**. 이는 **귀하**의 계정이나 **귀하**가 직접 호스팅하는 서버에서 설정한 목적지에서 이루어지며, 다른 사람이 볼 수 있는 회사 운영의 “차량 비용 클라우드”가 아닙니다.

### <a id="what-runs-where"></a>What runs where

| 종류 | 저장하는 것 | 일반적인 용도 |
|------|----------------|-------------|
| **스프레드시트 / 표 형식 동기화** | 차량, 연료 충전, 비용(행과 탭) | 다중 장치 병합 + 구조화된 백업 |
| **사진 백업** | 바이너리 이미지(대시/펌프/영수증/참고 사진) | 사진 백업 + 누락된 파일 복원 |

각 종류별로 **여러 목적지**를 구성할 수 있습니다 (유형별 소프트 캡). 수동 **지금 동기화** 및 **백그라운드** 작업자가 활성화된 것들을 실행합니다.

### <a id="offline-first"></a>오프라인 우선

- 연료 주입, 지출, 영수증을 추가하는 데 네트워크가 필요하지 않습니다. 모든 것은 먼저 **로컬에 저장**됩니다.
- 네트워크를 사용할 수 있을 때, 동기화 및 사진 백업은 **백그라운드 작업**으로 실행됩니다 (설정한 일정에 따라 또는 **지금 동기화**를 탭할 때). 실패하면 설정 항목 아래에 빨간색 텍스트로 표시되고 앱 제목 표시줄에 **!**가 나타납니다.

### <a id="your-accounts-only"></a>귀하의 계정만

로그인 및 토큰은 사용자가 선택한 공급자(Google, Microsoft, S3 키, 자체 호스팅 URL 등)에 대해 기기에 그대로 남습니다. 목적지는 **사용자가 완전히 제어**합니다 — 당신의 Google 계정, OneDrive, MinIO 버킷, EtherCalc 호스트 등. 다른 Vehicle Expenses 사용자와 공유 백엔드를 통해 아무것도 공유되지 않습니다.

### <a id="supported-targets--data-spreadsheet--tabular"></a>지원되는 대상 — 데이터 (스프레드시트 / 표 형식)

**메뉴 → 동기화 → 스프레드시트 동기화**에서 구성됨 (설정 요약 행에서도 접근 가능). 일류 선택기 옵션:

| 대상 | 참고 |
|--------|--------|
| **구글 시트** | 일반 기본값; 차량, 비용, 차량별 연료 탭
| **엑셀** | Graph / OneDrive 스타일 바인딩을 통한 Microsoft 워크북 |
| **EtherCalc** | 자체 호스팅 협업 스프레드시트 방 |
| **기타 →** 구현된 백엔드 | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

연기됨 / 아직 비헤드리스 아님 (기타에 나열되어 있지만 완전히 구현되지 않음): OnlyOffice, Collabora. 또한 [셀프 호스트 인덱스](reference/self-host/INDEX.md)도 참조하세요.

CSV **내보내기/가져오기**(같은 탭 레이아웃의 ZIP)는 실시간 동기화와 독립적으로 휴대용 백업으로 설정에서 사용할 수 있습니다.

### <a id="supported-targets--photos-image-backup"></a>지원되는 대상 — 사진(이미지 백업)

**메뉴 → 동기화 → 사진 백업**에서 구성됨 (설정 요약 행에서도 가능):

| 대상 | 참고 |
|--------|--------|
| **구글 드라이브** | 선택한 폴더 (탐색하거나 URL 붙여넣기) |
| **원드라이브** | 마이크로소프트 계정 + 경로 접두사 |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO 및 기타 S3 호환 엔드포인트 |
| **기타** | rclone 기반 스토리지 (예: WebDAV, SFTP 및 앱 내 선택기에서 사용할 수 있는 기타 선별된 원격 저장소) |

자체 호스트 사진 및 테이블 대상용 설정 치트시트: [자체 호스트 인덱스](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>다중 장치 동작 (짧게)

- 행은 **Sync ID**로 병합되며, **Updated** 타임스탬프 기준으로 **마지막 기록이 우선**합니다.
- 삭제는 소프트 삭제입니다; 다른 장치에서의 최신 편집이 행을 복원할 수 있습니다.
- 두 장치에서 **같은 항목을 두 번 입력**하면 **두 개의 행**이 생성됩니다 — 발견하면 여분을 삭제하세요.
- 자세한 내용: [동기화 동작 참고](#sync-behavior-notes) 및 [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>예: Google Sheets (데이터) 추가

1. **메뉴 → 동기화 → 스프레드시트 동기화** (또는 설정 → 스프레드시트 동기화).

![스프레드시트 동기화 목록](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. **스프레드시트 대상 추가**를 탭하세요.

![제공자 선택기](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. **구글 시트**를 선택합니다.

![Google 스프레드시트 폼](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Google로 로그인** 한 번 — 그 로그인으로도 탐색할 수 있습니다. 표시 이름 → **시트 URL** 붙여넣기(여전히 유효) 또는 **🔍** 탐색. 탐색은 **전체 및 공유**에서 열리므로 본인이 소유하거나 공유된 기존 스프레드시트를 선택할 수 있습니다. 그런 다음 일정 옵션 → 활성화 → 저장.
5. 탭을 생성/업데이트하려면 한 번 **지금 동기화**: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>예: 구글 드라이브(사진) 추가

1. **메뉴 → 동기화 → 사진 백업** (또는 설정 → 사진 백업).

![사진 백업 목록](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. **사진 목적지 추가**를 탭하세요.

![사진 제공자 선택기](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. **구글 드라이브**를 선택하세요.

![구글 드라이브 양식](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Google(Drive)로 로그인** → 선택적 폴더 URL/찾아보기 → 활성화 → 저장 → **지금 동기화**.

수동 **지금 동기화**는 사진에 대해 전체 실행이며; 백그라운드 백업은 일반적으로 **대기 중인 항목만** 업로드를 일정에 따라 처리합니다.

### <a id="sync-behavior-notes"></a>동기화 동작 노트

- 앱 업그레이드 후 잠시 동안 **“업그레이드 후 데이터베이스 업데이트 중…”**(로컬 동기화 ID 백필)를 볼 수 있습니다.
- 동기화가 중단되면, 다음 **성공적인** 동기화가 원격 탭을 다시 병합하고 복구합니다.
- 실패: 카드 동기화에 대한 빨간 요약 + 앱 바에 **!**.

---

## <a id="fill-up-fuel"></a>채우기 (연료)

앱을 열면 나타나는 **홈 화면**입니다.

### <a id="vehicle-selection-usually-automatic"></a>차량 선택 (보통 자동)

먼저 차량을 선택할 **필요가 없습니다**. 차량에 **랜드마크**가 관리에서 설정되어 있는 경우, 주행계 사진을 찍은 후 Fill이 대시 이미지에서 **어떤 차량인지 자동으로 감지**합니다. 필요하다면 여전히 **차량** 드롭다운을 열어 수동으로 변경할 수 있습니다.

### <a id="aim-at-the-odometer"></a>주행계에 조준하세요

속도계 모드에서 유지하고 클러스터를 프레이밍하세요. 지시: *속도계를 조준하세요. 셔터를 눌러 촬영하세요.*

![필 — 주행 거리계 실시간 카메라 (미리보기의 대시보드 이미지 예시)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>주행거리계 셔터 후

OCR은 **Odo**를 채우고 랜드마크에서 차량을 일치시키려고 시도합니다(필요하면 둘 다 검토). 주요 버튼은 다시 촬영할 수 있도록 **재시도**로 바뀝니다. 지침은 판독 내용을 요약합니다.

![채우기 — 속도계 OCR 결과](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>펌프 모드 (비용 및 용량)

1. **↕**를 눌러 펌프 모드로 전환: *펌프 디스플레이(비용/용량)를 겨냥하세요. 셔터를 누르세요.*
2. 펌프 합계를 기록합니다. 비용 및 용량 필드를 채우고, 서로 바뀌어 있으면 **↔**를 사용합니다.
3. 필요하면 통화나 **G/L**을 누른 다음, **저장**(디스크)합니다. 빈 필드는 **부분 입력**을 만듭니다(여전히 허용됨).

![채우기 — 펌프 미리보기 / 합계 (미리보기의 예시 펌프 이미지)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

다음 정류장까지 Fill에 머무르세요 (저장 후 필드가 비워집니다). 완전히 **오프라인**으로 작업하세요; 설정 시 나중에 백그라운드에서 동기화가 실행됩니다.

### <a id="manual-entry-no-camera--bad-ocr"></a>수동 입력 (카메라 없음 / OCR 불량)

1. **Odo**, **비용**, 또는 **용량**을 탭하고 값을 입력하세요(세로 모드에서는 시스템 키보드 사용; 가로 모드에서는 화면 키패드 사용).
2. 자동 감지가 실행되지 않은 경우 **차량**을 선택하거나 확인하세요.
3. 위와 같이 저장합니다.

### <a id="modes-and-borders"></a>모드와 경계

- 차량+주행거리계 주변의 **녹색 테두리** → 주행거리계 캡처/편집.
- 비용+부피 주변 **녹색 테두리** → 펌프 모드.
- **저장**은 차량이 선택되고 주행거리/비용/연료량 중 최소 하나에 데이터가 있으며 OCR이 아직 실행 중이지 않을 때까지 비활성 상태로 유지됩니다.

화면 팁 (설명 줄 아래): *셔터 = 캡처 · 디스크 = 저장 · ↕ = 오도/펌프 모드 · ↔ = 비용/볼륨 교환.*

---

## <a id="expenses"></a>지출

### <a id="new-expense"></a>새 지출

메뉴 → **새 지출**.

![새로운 비용 양식 및 카메라 컨트롤(카메라 밴드가 비었을 때 대시 이미지 예시)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **저장**(디스크), **셔터**(영수증 사진), 또는 **갤러리**(이미지 선택).
2. **날짜**, **차량**, **업체**, **설명**, **금액**(통화 기호 탭 가능), **카테고리**, 선택 사항 **주행거리계**를 작성하세요.
3. 다중 페이지 영수증: UI가 페이징을 제공하는 경우 추가 페이지를 캡처합니다(페이지 0이 기본 영수증입니다).
4. **저장**하기 (먼저 로컬에; 설정된 경우 백그라운드에서 사진 백업과 스프레드시트 동기화가 진행됨).

### <a id="expense-list"></a>비용 목록

메뉴 → **보고서** → **지출 목록** — 과거 비연료 지출을 찾아보세요; 항목을 열어 편집할 수 있습니다.

![지출 목록](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>비용 편집

목록에서 행을 엽니다. 올바른 공급업체, 금액, 카테고리, 차량 및 설명을 수정합니다. 영수증이 사진 백업에만 있는 경우(읽을 수 있는 로컬 파일이 없는 경우), 표시될 때 **보관소에서 이미지 가져오기**를 사용합니다(구성된 사진 저장소 전반에서 작동).

![비용 편집](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>여행 시작

메뉴 → **여행 시작** (서랍을 작성한 후). 속도계 값을 캡처하거나 입력하고, 여행 유형을 선택한 후, **디스크** 아이콘으로 저장합니다. **중지**는 현재 잡힌 GPS 위치에서 개인용 바로가기입니다. 제어 알림은 **ⓘ**를 사용하세요.

![여행 시작](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

여행 시작은 **여행 유형**(일반 주유 아님)과 함께 연료 행으로 저장됩니다. 이는 **보고서 → 여행 거리**에서 나타나며, 연료 기록에는 나타나지 않습니다.

---

## <a id="reports"></a>보고서

메뉴 → **보고서**를 클릭하면 제품 허브(전체 요약 + 카탈로그 카드)가 열립니다. 이것이 유일한 제품 보고서 화면이며, 별도의 “보고서 및 차트” 드로어 항목은 없습니다.

![보고서 허브](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

차량 모드(**전체 / 각각 / 단일**)용 카드를 열고, 기간 필터, 차트 및 공유(**텍스트 / CSV / PDF**)를 사용하세요. 보고서 하위 항목 상단 바: **☰ + ←** (등록 시 **ⓘ** 포함).

### <a id="time-based-reports"></a>시간 기반 보고서

주요 차트 카드. 선택적 지표(mpg, 볼륨/거리 예: G/mi, 단가 예: $/G, 비용/거리, 월별 $, 여행 마일, 유형별 여행 %)를 **스무스** 구간과 **독립 Y 축**(경제성은 왼쪽; 금액 및 여행 관련 항목은 오른쪽)으로 표시

![시간 기반 보고서](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![시간 기반 보고서 — 스크롤된 메트릭](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

경제 수학 세부 사항: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>주입 기록 vs 연료 기록

- **보고서 → 충전 내역** — 보고서 필터에 대한 연대순 충전 내역 (**충전만 해당**; 운행 시작은 제외).

![히스토리 보고서 작성](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **연료 기록** (빌드 내 내비게이션에 있는 경우) — 차량별 주유 내역, 주유만 포함; 행을 탭하여 편집.

### <a id="trip-miles"></a>주행 거리(마일)

**보고서 → 주행 거리** — 유형별 마일, 차트, 그리고 연대순 **주행 시작 / 구간 목록**. 실제 시작 항목을 눌러 그 행의 **주유 편집**을 엽니다.

![여행 마일](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>편집 채우기

주유 기록, 연료 기록 또는 주행 거리에서 주유 항목을 엽니다. 레이아웃: 차량 및 주행 거리계, **비용 앞에 통화**, 양, 메모. 주행 유형은 행이 주행 시작일 때만 나타납니다. 위치에는 요약과 함께 **위치 세부 정보**가 있습니다. 클라우드 식별이 있는 현지 사진이 없으면: **보관소에서 이미지 가져오기**.

![편집 채우기 — 로컬 사진이 없을 때 아카이브에서 가져오기](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

다른 카탈로그 카드에는 카테고리별 비용, 차량 요약, 비용 목록이 포함됩니다.

돈은 설정될 때 각 행의 통화를 사용합니다. 혼합 통화 합계는 **통화별 소계**를 보여줍니다(숨겨진 FX 변환 없음).

---

## <a id="syncing"></a>동기화 중

메뉴 → **동기화**는 스프레드시트와 사진 목적지의 중심 허브입니다(설정에만 숨겨져 있지 않습니다).

![허브 동기화](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- 짧은 상태와 해당 종류의 **동기화**, 대상 목록으로의 **›**가 포함된 **스프레드시트 동기화** 및 **사진 백업**용 카드.
- **테스트 연결** 및 **지금 동기화(이 대상)** / 모든 구성된 항목에 대한 대상 열기.
- 실패 **세부 사항**과 제목 표시줄의 빨간 **!**가 여기에 나타납니다.
- 단계별 Google 스프레드시트 및 드라이브 설정: [백업 및 다중 기기 동기화](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>설정 (로컬 환경 설정)

메뉴 → **설정**.

![설정](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![더 많은 설정 (스크롤됨)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

목적지의 경우, **메뉴 → 동기화**를 선호하세요. 설정에서는 여전히 동일한 목록을 여는 요약 행이 표시될 수 있습니다.

### <a id="local-preferences-common"></a> 지역 선호도 (일반)

- **연료 영수증 사진 저장** / **경비 사진을 기기에 저장** — 이미지를 기기에 보관 (사진 권한을 요청할 수 있음).
- **셔터 소리 재생**
- **통화** / **용량 단위** — 앱 기본값 (시스템 또는 명시적). 기존 연료 데이터로 용량 단위를 변경하면 변환 대화 상자가 나타날 수 있습니다.
- **다크 모드**
- **설정 팁** — 첫 실행 차량을 다시 열기 / 튜토리얼 동기화.
- **디버그 채우기** / **실험 화면 표시 (개발용)** — 고급 기능; 일상적인 사용에서는 끄는 것이 좋습니다. 실험 화면은 여기에서 문서화되지 않았습니다.

CSV **내보내기/가져오기**(차량/비용/연료 탭의 ZIP)는 현재 빌드에서 제공되는 경우 설정에서 사용할 수 있습니다.

---

## <a id="help--about"></a>도움말 및 정보

![도움](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![정보](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **도움말** — 기기 내 빠른 시작, 설정 튜토리얼, 이 매뉴얼 링크, 자체 호스트 설정 인덱스.
- **정보** — 버전, 라이선스, GitHub, 이 매뉴얼 (게시 시 번들된 오프라인 + 온라인 HTML).

---

## <a id="related-docs"></a>관련 문서

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — 짧은 일상 가이드
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — 자체 호스팅 사진/표 형식 설정
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — 병합, 복구, 중복
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — 경제 지표 세부 사항
