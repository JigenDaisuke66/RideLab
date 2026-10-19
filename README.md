<div align="center">

# RideLab

**把手机变成你的室内骑行仪表盘**

连接骑行台与心率传感器 · ERG 训练 · 后台记录 · 本地分析

![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)
![Local first](https://img.shields.io/badge/Data-Local%20first-35D0BA)

[功能概览](#功能概览) · [设备兼容性](#设备兼容性) · [开始使用](#开始使用) · [开发与测试](#开发与测试) · [English](README.en.md)

</div>

RideLab 是一款使用 Kotlin 和 Jetpack Compose 开发的原生 Android 室内骑行应用。连接蓝牙骑行台和心率传感器后，可以查看实时数据、调整目标功率、完成结构化训练，并将骑行保存在手机中。

**无需注册账号，无需云服务。** 骑行记录、训练分析与数据导出均在本地完成。

## 功能概览

| 模块 | 当前功能 |
| --- | --- |
| 设备连接 | BLE 扫描、按角色选择设备，骑行台与心率传感器独立连接、同时工作 |
| 实时骑行 | 功率、心率、踏频、骑行时间，以及最近 5 分钟的实时曲线 |
| ERG 控制 | 直接输入目标功率、±5 W 微调、明确点击应用；按骑行台保存上次应用的目标 |
| 训练与 FTP | 内置结构化课程、FTP Ramp Test，以及不需要预先填写 FTP 的初始测试 |
| 后台记录 | 前台服务维持骑行与约 1 Hz 本地记录，支持切换应用、锁屏及屏幕旋转 |
| 骑行管理 | 开始、暂停、继续、结束；结束记录后保留设备连接，可手动断开 |
| 历史与详情 | 按年/月折叠、单条删除、功率/心率/踏频图表、采样数量与连续性信息 |
| 训练分析 | 平均/最大值、NP、IF、TSS、固定时长最佳功率、功率区间、W/kg 与历史最佳对比 |
| 能量估算 | 基于已记录功率的功与卡路里估算，以及食物能量对照 |
| 导出与恢复 | 单次 CSV、全部 CSV 的 ZIP 导出，以及可在应用内恢复的完整骑行备份 |
| 悬浮窗 | 跨应用透明 HUD，显示计时、功率、心率和踏频；支持折叠曲线、拖动、尺寸/字号/透明度调整 |
| 外观与语言 | 五种主题配色，浅色/深色/跟随系统；独立悬浮窗配色与三种布局；七种界面语言 |

> 训练课程与 FTP 测试的自动 ERG 当前仅对已识别的 ThinkRider X2 MAX 开放。其他设备的手动控制能力见下表。缺失传感器数据不会补成 0；真实的 0 W 会保留。

### 分析数据如何解读

- FTP 与体重可留空，不会为用户编造默认值。开始骑行时保存当时的设置，后续修改不会改写该次骑行的历史快照。
- NP 需要足够连续的有效功率数据；IF/TSS 还需要该次骑行保存的 FTP。
- 最佳功率覆盖 5 秒、15 秒、30 秒、1 分钟、5 分钟、20 分钟和 60 分钟，数据不足时显示暂无数据。
- 卡路里与食物对照是基于功率和固定效率假设的估算，不是个体代谢测量。

## 设备兼容性

**以实际 GATT 服务与特征判断能力，不以设备名称或“支持蓝牙”作为通用兼容保证。** 功率测量和 ERG 控制是两项独立能力。

| 设备 / 协议 | 数据与控制 | 验证范围 |
| --- | --- | --- |
| ThinkRider X2 MAX | Cycling Power 功率/踏频 + FE-C over BLE ERG，目标范围 50–2000 W | 已实机验证的主要开发设备；支持现有自动训练路径 |
| 标准 FTMS 骑行台 | Indoor Bike Data；满足能力、功率范围及控制特征要求时提供手动 ERG | 已实现协议与单元测试，仍需不同型号实机验证；自动训练暂不开放 |
| 其他 FE-C over BLE 骑行台 | 检测到完整 FEC1/FEC2/FEC3 布局时提供保守的 50–200 W 手动 ERG | 未验证型号，不承诺通用兼容；自动训练暂不开放 |
| 仅提供 Cycling Power 的设备 | 标准功率测量，存在曲柄转数数据时计算踏频 | 不会仅凭功率服务启用 ERG；独立外部功率计角色仍待完善 |
| 标准 BLE 心率传感器 | Heart Rate Service，支持 UINT8/UINT16 BPM | 已验证与 X2 MAX 同时连接；具体型号仍需实测 |

X2 MAX 实机使用的是 **Cycling Power + FE-C over BLE**，并未发现 FTMS 服务。同时发现完整 FE-C 与 FTMS 控制能力时，当前优先选择 FE-C；一次目标只通过一个协议发送。

FTMS ERG 需要读取有效的设备功率范围并完成控制授权，收到协议成功响应后才报告接受目标。恢复已保存的目标值或重新连接设备，**都不会自动发送功率命令**。

详细的能力判断、范围限制及新设备测试步骤见 [设备兼容性说明](TRAINER_COMPATIBILITY.md)。

## 开始使用

### 安装

- 手机需要 **Android 12（API 31）或更高版本**，并支持 Bluetooth LE。
- 若 [Releases](https://github.com/JigenDaisuke66/RideLab/releases) 中提供 APK，可下载后安装；没有可用安装包时，请通过 Android Studio 从源码运行。
- AAB 是应用分发包，不能像 APK 一样直接点击安装。

### 第一次骑行

1. 开启蓝牙，允许 RideLab 使用附近设备权限。
2. 在首页分别选择骑行台与心率传感器，确认实时数值正常。
3. 如已知 FTP、体重，可在设置中填写；不清楚时可留空。受支持的骑行台可使用“初始 FTP 测试”估算，再确认是否保存结果。
4. 点击 **开始骑行**。允许通知可方便在后台查看骑行状态。
5. 自由骑行时，编辑 ERG 目标后点击 **应用目标** 才会发送命令；也可在受支持的骑行台上选择训练课程。
6. 需要休息时点击暂停；暂停冻结有效骑行时间、采样及课程进度，保留连接和已有 ERG 阻力。结束后到历史页查看记录。

开启悬浮窗需要额外授予“显示在其他应用上层”权限。可在骑行页或设置页开启及调整；折叠后仍保留计时与功率。

首次使用建议先做一次短骑行和一次锁屏测试，确认本机后台限制不会影响记录。后台能力依赖前台服务及设备系统策略；首次测试方法见 [后台骑行验证](FOREGROUND_SERVICE_VERIFICATION.md)。

## 数据导出与恢复

| 格式 | 用途 | 导出内容 | 可恢复到 RideLab |
| --- | --- | --- | --- |
| 单次 `.csv` | 查看某次骑行，使用 Excel / WPS 分析 | 一次骑行的原始采样 | 否 |
| 全部 CSV `.zip` | 一次导出全部历史供外部查看 | `summary.csv` 汇总表 + 每次骑行的 CSV | 否 |
| `.ridelab` | 备份、换机或恢复已删除的骑行 | 已完成/已中断骑行的完整采样、会话信息与历史 FTP/体重 | 是 |

历史页顶部提供全部 CSV 导出、备份导出与恢复入口；单次 CSV 在骑行详情中导出。

恢复备份会**合并记录**，保留现有数据并跳过完全相同的骑行；不覆盖正在进行的记录。备份不含应用设置和正在进行的骑行。

CSV 中空白表示缺失数据，0 表示真实零值。导出文件可能包含传感器蓝牙地址；`.ridelab` 是带完整性校验的压缩文件，未加密。完整格式、容量限制和验证步骤见 [备份说明](HISTORY_BACKUP.md)。

## 语言

支持简体中文、English、日本語、繁體中文（台灣）、Español、Português（Brasil）和 Français。

默认跟随系统，不支持的系统语言回退到简体中文；设置中手动选择的语言会保存，并支持 Android 系统的应用语言设置。

## 开发与测试

### 环境

- 支持本项目 Android Gradle Plugin 的 Android Studio。
- 推荐使用 Android Studio 内置的 **JDK 21**。
- 按项目配置安装 Android SDK：当前 `compileSdk = 37.1`、`targetSdk = 36`、`minSdk = 31`。
- 使用仓库的 Gradle Wrapper；版本以 [版本目录](gradle/libs.versions.toml) 与 [Wrapper 配置](gradle/wrapper/gradle-wrapper.properties) 为准。

```bash
git clone https://github.com/JigenDaisuke66/RideLab.git
cd RideLab
```

用 Android Studio 打开项目，完成 Gradle 同步，在真实 Android 设备上运行 `app`。蓝牙和后台行为需实机验证。

运行单元测试与编译检查，不生成 APK：

```bash
# macOS / Linux
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

```powershell
# Windows PowerShell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

设备测试代码编译通过不代表设备测试已经执行；协议单元测试也不代表所有骑行台已经兼容。

### 代码结构

```text
app/src/main/java/com/isaac/ridelab/
├── ble/          原生 BLE、测量协议及骑行台控制适配
├── service/      长连接、骑行生命周期、后台记录与悬浮窗入口
├── workout/      课程与 FTP 测试
├── data/         Room 数据、本地设置与 Repository
├── analytics/    可独立测试的训练分析
├── export/       CSV、批量导出及备份格式
├── locale/       应用语言策略
└── ui/           Compose 页面、ViewModel、图表、主题与悬浮窗展示
```

BLE 连接和骑行记录由 `RideRecordingService` 持有，Compose 负责展示与事件分发。开发前请阅读 [项目说明](PROJECT.md)、[UI 架构](UI_ARCHITECTURE.md) 和 [路线图](ROADMAP.md)。

## 反馈与贡献

欢迎通过 [Issues](https://github.com/JigenDaisuke66/RideLab/issues) 反馈问题或提交设备验证结果。请尽量提供：

- RideLab 版本、手机型号及 Android 版本；
- 骑行台 / 传感器型号与固件版本；
- 操作步骤、预期结果及实际表现；
- 设备发现的服务、控制协议与功率范围；
- 相关日志：连接/控制看 `RideLabGatt`，课程看 `RideLabWorkout`，悬浮窗看 `RideLabOverlay`。

分享日志或备份前，可先移除不希望公开的设备地址与个人记录。提交代码时，请保持改动聚焦，补充相关测试，并为新增界面文字提供七语言资源。

### 更多说明

- [骑行分析](TRAINING_ANALYTICS_VERIFICATION.md) · [最佳功率与区间](ANALYTICS_LAYER1_VERIFICATION.md)
- [结构化训练](WORKOUT_PHASE1_VERIFICATION.md) · [初始 FTP 测试](INITIAL_FTP_VERIFICATION.md)
- [悬浮窗 HUD](OVERLAY_HUD_VERIFICATION.md) · [本地数据与备份](HISTORY_BACKUP.md)

当前尚未提供云同步、账号系统、FIT/TCX 导出、PowerMatch 或坡度模拟。实现情况与待验证项目请以源码和对应验证文档为准。
