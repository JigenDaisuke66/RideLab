<div align="center">

# RideLab

**Android スマートフォンを室内サイクリングのダッシュボードに**

BLE トレーナー · ERG トレーニング · バックグラウンド記録 · ローカル分析

![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)
![Local first](https://img.shields.io/badge/Data-Local%20first-35D0BA)

[English](README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md) · [Español](README.es.md) · [Português (Brasil)](README.pt-BR.md) · [Français](README.fr.md)

</div>

RideLab は Kotlin と Jetpack Compose で開発した Android ネイティブアプリです。トレーナーと心拍センサーを接続し、目標パワーの設定、ワークアウト、走行記録の確認を端末内で行えます。アカウントやクラウドサービスは不要です。

## 主な機能

- **リアルタイム表示**：パワー、心拍数、ケイデンス、走行時間、直近5分のグラフ。
- **手動 ERG**：数値入力、±5 W 調整、明示的な適用、トレーナー別の目標保存。設定の復元だけでは指令を送りません。
- **トレーニング**：内蔵コース、FTP ランプテスト、FTP が不明でも始められる初期テスト。自動 ERG は現在、認識済み ThinkRider X2 MAX に限定しています。
- **バックグラウンド記録**：フォアグラウンドサービスが接続と約1 Hz の Room 記録を維持。アプリ切替、画面ロック、回転に対応。
- **走行操作**：開始、一時停止、再開、終了。一時停止は有効時間、記録、コース進行を止め、接続と適用済み負荷を維持します。終了後も接続を保持し、手動で切断できます。
- **履歴・分析**：年/月で折りたたみ、個別削除、センサーグラフ、記録の連続性、平均/最大値、NP/IF/TSS、ベストパワー、パワーゾーン、W/kg、過去記録との比較。
- **消費量の推定**：記録パワーからカロリーと食品のエネルギー換算を表示。個人の代謝を測定するものではありません。
- **データ出力**：走行別 CSV、全 CSV の ZIP、アプリ内で復元できるバックアップ。
- **透明オーバーレイ**：時間、パワー、心拍、ケイデンスを他アプリ上に表示。グラフの開閉、移動、折りたたみ、サイズ・文字・透明度の調整。
- **外観**：5色のパレット、ライト/ダーク/システム設定、3種類のオーバーレイ、7言語。

欠損値を 0 に置き換えず、実際の 0 W は保持します。FTP と体重は未設定にでき、走行開始時に保存するため、後日の設定変更で過去の値は変わりません。NP には十分に連続したパワー、IF/TSS には走行時の FTP が必要です。ベストパワーは5/15/30秒、1/5/20/60分を対象にします。

## デバイス互換性

実際の GATT サービスと特性から機能を判断します。パワー計測に対応していても、ERG 対応とは限りません。

| デバイス / プロトコル | 動作 | 検証状況 |
| --- | --- | --- |
| ThinkRider X2 MAX | Cycling Power + FE-C over BLE ERG、50–2000 W、既存の自動ワークアウト | 主な実機検証済みトレーナー |
| FTMS トレーナー | Indoor Bike Data。必要な特性と有効なパワー範囲があれば手動 ERG | プロトコル/単体テスト済み、実機検証待ち。自動ワークアウト無効 |
| その他の FE-C over BLE | 完全な FEC1/FEC2/FEC3 構成で、保守的な50–200 W の手動 ERG | 未検証モデル。自動ワークアウト無効 |
| Cycling Power のみ | パワーと、クランク回転情報がある場合のケイデンス | ERG を有効にしません。独立した外部パワーメーターの役割は未完成 |
| BLE 心拍センサー | 標準 Heart Rate Service、UINT8/UINT16 BPM | X2 MAX との同時接続を検証済み。他モデルは要検証 |

検証した X2 MAX に FTMS はなく、Cycling Power と FE-C を使います。両制御プロトコルがある場合は FE-C を優先し、同じ目標を二重送信しません。FTMS は制御権と成功応答が必要で、GATT 書込み成功だけでは受理と扱いません。再接続で目標を自動再送しません。全トレーナーの互換性は保証しません。[詳細](TRAINER_COMPATIBILITY.md)。

## 使い始める

1. BLE 対応の Android 12（API 31）以上を使用します。[Releases](https://github.com/JigenDaisuke66/RideLab/releases) に APK があればインストールし、なければ Android Studio から実行してください。AAB は APK のように直接インストールできません。
2. Bluetooth と付近のデバイス権限を有効にし、トレーナーと心拍センサーを個別に選びます。
3. 値を確認し、任意で FTP と体重を設定します。不明なら空欄にできます。対応機器の初期 FTP テスト結果は、確認してから保存します。
4. 走行を開始し、通知を許可します。手動 ERG は「適用」で送信します。終了後は履歴を開きます。

オーバーレイには「他のアプリの上に表示」の許可が必要です。走行画面か設定から調整できます。最初に短い走行と画面ロックを試してください。バックグラウンド動作は端末側の制限にも依存します。[確認手順](FOREGROUND_SERVICE_VERIFICATION.md)。

## エクスポートと復元

| ファイル | 用途 | 復元 |
| --- | --- | --- |
| 個別 `.csv` | Excel/WPS で1走行のサンプルを確認 | 不可 |
| 全 CSV `.zip` | `summary.csv` と走行別 CSV | 不可 |
| `.ridelab` | 完了/中断した走行、全サンプル、走行時の FTP/体重 | 可 |

一括出力と復元は履歴の上部、個別 CSV は詳細画面にあります。復元は既存記録を保持して統合し、完全一致はスキップします。進行中の走行とアプリ設定はバックアップ対象外です。CSV の空欄は欠損です。バックアップは整合性確認付き圧縮形式で、暗号化されていません。センサーのアドレスを含む場合があります。[形式と上限](HISTORY_BACKUP.md)。

## 言語

簡体字中国語、英語、日本語、繁体字中国語（台湾）、スペイン語、ブラジルポルトガル語、フランス語に対応。アプリは初期設定でシステム言語に従い、未対応時は簡体字中国語を使います。手動選択を保存し、Android のアプリ別言語にも対応します。

## 開発とテスト

プロジェクトの AGP に対応した Android Studio と内蔵 JDK 21 を推奨します。compile SDK 37.1、target SDK 36、最小 API 31。[依存バージョン](gradle/libs.versions.toml) / [Wrapper](gradle/wrapper/gradle-wrapper.properties)。

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

Android Studio で同期し、実機で `app` を実行してください。上のコマンドは APK を生成しません。デバイステストのコンパイルは実行を意味せず、単体テストも実機検証の代わりになりません。BLE と記録はサービスが所有し、Compose は表示とイベントを担当します。[プロジェクト](PROJECT.md)、[UI 構成](UI_ARCHITECTURE.md)、[ロードマップ](ROADMAP.md)を参照してください。

## フィードバック

[Issues](https://github.com/JigenDaisuke66/RideLab/issues) にアプリ/Android/端末の情報、トレーナー・センサーのモデルとファームウェア、再現手順、検出プロトコル・範囲、ログを添えてください。`RideLabGatt` は BLE/制御、`RideLabWorkout` はコース、`RideLabOverlay` はオーバーレイです。公開前に必要に応じてアドレスや個人記録を削除してください。コード変更には関連テストと7言語の UI リソースを追加します。

クラウド同期、アカウント、FIT/TCX、PowerMatch、勾配シミュレーションは現在提供していません。実装と実機受入状況は各検証文書に記載しています。
