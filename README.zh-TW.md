<div align="center">

# RideLab

**將 Android 手機變成你的室內騎行儀表板**

藍牙騎行台 · ERG 訓練 · 背景記錄 · 本機分析

![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)
![Local first](https://img.shields.io/badge/Data-Local%20first-35D0BA)

[English](README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md) · [Español](README.es.md) · [Português (Brasil)](README.pt-BR.md) · [Français](README.fr.md)

</div>

RideLab 是以 Kotlin 和 Jetpack Compose 開發的原生 Android 室內騎行應用程式。連接騎行台與心率感測器後，可查看即時數據、調整目標功率、進行結構化訓練並在手機中保存紀錄。無須帳號或雲端服務。

## 功能

- **即時儀表板**：功率、心率、踏頻、騎行時間，以及最近 5 分鐘的曲線。
- **手動 ERG**：直接輸入功率、±5 W 微調、明確點選套用；依騎行台保存目標，還原設定不會自動傳送指令。
- **訓練與 FTP**：內建課程、FTP Ramp Test，以及無須預填 FTP 的初始測試。自動 ERG 目前僅開放給已識別的 ThinkRider X2 MAX。
- **背景記錄**：前景服務維持連線與約 1 Hz Room 記錄，支援切換應用程式、鎖定螢幕和旋轉。
- **騎行控制**：開始、暫停、繼續、結束。暫停凍結有效時間、採樣及課程進度，保留連線與已套用的阻力；結束記錄後可手動中斷連線。
- **歷史與分析**：依年/月收合、單筆刪除、感測器圖表、採樣連續性、平均/最大值、NP/IF/TSS、最佳功率、功率區間、W/kg 和歷史比較。
- **能量估算**：以功率估算消耗與食物能量對照，並非個人代謝量測。
- **匯出與還原**：單次 CSV、全部 CSV 的 ZIP，以及完整騎行備份。
- **透明浮動視窗**：顯示時間、功率、心率、踏頻，可展開曲線、拖曳與收合，並調整尺寸、字體和透明度。
- **外觀**：五種配色、淺色/深色/跟隨系統、三種浮動視窗版型與七種語言。

缺失數據不會補成 0，真實的零值會保留。FTP、體重可留空，開始騎行時保存快照，後續修改不影響歷史設定。NP 需要足夠連續的功率數據；IF/TSS 另需該次保存的 FTP。最佳功率涵蓋 5/15/30 秒及 1/5/20/60 分鐘。

## 裝置相容性

以實際 GATT 服務及特徵判斷能力；有功率量測不代表支援 ERG。

| 裝置 / 協定 | 目前行為 | 驗證狀態 |
| --- | --- | --- |
| ThinkRider X2 MAX | Cycling Power + FE-C over BLE ERG，50–2000 W，支援現有自動課程 | 主要實機驗證裝置 |
| FTMS 騎行台 | Indoor Bike Data；具備必要特徵與有效回報功率範圍時提供手動 ERG | 已做協定/單元測試，待實機驗證；自動課程停用 |
| 其他 FE-C over BLE | 完整 FEC1/FEC2/FEC3 配置下，保守的 50–200 W 手動 ERG | 未驗證型號；自動課程停用 |
| 僅 Cycling Power | 功率；有曲柄轉數資料時提供踏頻 | 不因此啟用 ERG；獨立外接功率計角色仍待完善 |
| BLE 心率感測器 | 標準 Heart Rate Service，UINT8/UINT16 BPM | 已驗證與 X2 MAX 同時連線；其他型號待測 |

X2 MAX 實機未發現 FTMS，採用 Cycling Power + FE-C。兩種控制協定皆存在時優先 FE-C，同一目標不會透過兩種協定重複傳送。FTMS 必須取得控制權並收到協定成功回應，僅 GATT 寫入成功不算接受目標。重新連線不重播目標。詳細限制見 [相容性說明](TRAINER_COMPATIBILITY.md)，不保證所有騎行台通用。

## 開始使用

1. 使用支援 BLE 的 Android 12（API 31）以上手機。若 [Releases](https://github.com/JigenDaisuke66/RideLab/releases) 有 APK 可下載安裝，否則以 Android Studio 從原始碼執行；AAB 無法像 APK 直接安裝。
2. 開啟藍牙並授予附近裝置權限，分別選擇騎行台與心率感測器。
3. 確認即時讀值，視需要填入 FTP、體重；未知時可留空。支援的騎行台可進行初始 FTP 測試，結果須確認後才保存。
4. 開始騎行並允許通知。手動 ERG 須點選套用才會傳送；結束後到歷史頁查看。

浮動視窗另需「顯示在其他應用程式上層」權限，可從騎行或設定頁調整。首次請先測試短程與鎖屏騎行；背景效果也受手機系統策略影響，見 [背景驗證](FOREGROUND_SERVICE_VERIFICATION.md)。

## 匯出與還原

| 檔案 | 用途 | 可還原 |
| --- | --- | --- |
| 單次 `.csv` | 用 Excel/WPS 查看一次騎行的採樣 | 否 |
| 全部 CSV `.zip` | `summary.csv` 摘要及每次騎行的 CSV | 否 |
| `.ridelab` | 已完成/中斷紀錄、完整採樣及歷史 FTP/體重 | 是 |

歷史頁頂部提供批次匯出與還原，單次 CSV 位於詳情頁。還原合併資料，保留現有紀錄並略過完全相同的紀錄。備份不含進行中的騎行或應用程式設定。CSV 空白代表缺失；備份有完整性校驗但未加密，可能含感測器藍牙位址。詳見 [格式及限制](HISTORY_BACKUP.md)。

## 語言

支援簡體中文、英語、日語、繁體中文（台灣）、西班牙語、巴西葡萄牙語與法語。應用程式預設跟隨系統，不支援的語言回退至簡體中文；手動選擇會保存，支援 Android 個別應用程式語言設定。

## 開發與測試

使用相容的 Android Studio，建議內建 JDK 21；安裝 compile SDK 37.1。target SDK 為 36，最低 API 為 31。版本見 [版本目錄](gradle/libs.versions.toml) 與 [Wrapper](gradle/wrapper/gradle-wrapper.properties)。

```bash
git clone https://github.com/JigenDaisuke66/RideLab.git
cd RideLab
# macOS / Linux
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

Windows PowerShell：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

以 Android Studio 開啟並同步後，在實機執行 `app`。上述指令不產生 APK；編譯裝置測試不代表已執行，也不能取代藍牙實測。服務持有 BLE 與記錄，Compose 只負責呈現及事件。貢獻前請讀 [專案說明](PROJECT.md)、[UI 架構](UI_ARCHITECTURE.md)、[路線圖](ROADMAP.md)。

## 回報問題

請在 [Issues](https://github.com/JigenDaisuke66/RideLab/issues) 提供版本、手機/Android、感測器/騎行台與韌體、重現步驟、實際協定/範圍及相關日誌：`RideLabGatt`、`RideLabWorkout`、`RideLabOverlay`。分享前可移除裝置位址與私人紀錄。貢獻請附相關測試及七語系介面資源。

目前不提供雲端同步、帳號、FIT/TCX、PowerMatch 或坡度模擬。實作與實機驗收狀態請參考各驗證文件。
