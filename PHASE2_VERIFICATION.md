# RideLab Phase 2 验证说明

## 范围与预期行为

- 保留 Phase 1 的扫描逻辑，点击设备名称、卡片背景或“连接此设备”都会连接该行的蓝牙地址。不根据名称、列表位置或广播中是否存在 FTMS 选择连接目标。
- 已选设备卡片使用高亮边框和“已选择”标记；列表外固定显示已选名称、地址和当前连接状态。点击“查看连接详情”可跳转到完整服务发现结果。
- 当前一次连接一个选定设备；选择其他设备会关闭旧连接。
- 顺序为：连接 GATT → 发现服务 → 显示所有服务、特征、属性及描述符 → 检查 0x1826、0x1818 和 FTMS 内的 0x2AD2。
- 发现 0x2AD2 且支持 Notify、有 0x2902 时，开启本地通知并写入 CCCD 0100；收到成功回调后显示“通知已启用”。这不保证设备已经开始发数据。
- 数据页显示速度、踏频和骑行台功率。缺失值显示“暂无数据”；分包中的缺失字段保留最近值及其原有更新时间；5 秒未更新标记为过期。畸形包不更新读数。
- 未发现 FTMS 时，明确提示“GATT 服务发现未找到 FTMS”，仍展示完整发现结果。发现 Cycling Power 不会触发功率解析或厂商协议。
- 连接、服务发现和订阅各有 20 秒超时，失败显示本地化原因与可用的 Android/GATT 状态码。蓝牙关闭、设备断开、权限异常也会进入失败状态。
- 旋转屏幕保留 ViewModel 中的连接和扫描状态；离开前台断开连接，返回后需重新连接。断开后清除实时读数，保留服务发现快照。
- 未实现控制点、ERG、阻力控制、心率/Cycling Power 数据解析、厂商协议、前台录制服务或持久化。

## 语言

- `res/values/strings.xml`：默认简体中文。
- `res/values-en/strings.xml`：完整英文翻译。
- `resources.properties`：`unqualifiedResLocale=zh-CN`。
- 全新安装首次启动明确选择中文，不受系统语言影响。应用内可选择中文或英文；Android 13+ 也保留系统的应用语言设置。显式选择会持久保存，包括 Android 13+ 的“系统默认”。未支持的语言资源回退中文。初始化与存储细节见 PROJECT.md 的 Localization 段落。

## 真机步骤

1. 安装本次 `app/build/outputs/apk/debug/app-debug.apk`。记录手机型号、Android 版本、骑行台型号与已知固件版本。
2. 唤醒 ThinkRider X2 MAX，避免其他训练应用占用连接。授予“附近的设备”权限并开启蓝牙。
3. 开始扫描，找到 `Think X211-0013186`，先直接点击名称或卡片空白处，确认马上出现高亮和固定连接状态。再验证“连接此设备”按钮。广播中没有 0x1826 仍应能进入 GATT 连接。
4. 等待服务发现完成。记录 0x1826、0x1818、0x2AD2 的检测结果，截取完整 GATT 列表，特别是特征属性与 0x2902 描述符。
5. 若存在 FTMS Indoor Bike Data，确认“通知已启用”，踩踏 30–60 秒，观察通知计数、速度、踏频和骑行台功率。未提供的字段应为“暂无数据”，有效零值应正常显示。
6. 停止踩踏，观察设备发送的是零值还是停止通知；后者应在 5 秒后显示过期值。检查无效包计数与日志。
7. 旋转屏幕、手动断开并重连、连接其他设备，确认没有旧会话数据混入。关闭手机蓝牙或让骑行台掉电，确认错误状态和日志。
8. 切到后台再返回，应显示已断开、实时值清空，扫描结果和发现快照仍可查看；手动重连。此阶段不验证锁屏录制。
9. 分别验证中文、英文界面；可用未支持的系统语言验证中文回退。

## Logcat

Android Studio Logcat 设置 `tag:RideLabGatt`，级别选 Debug；同时保留 `BleScanner` 的错误日志。也可在终端执行：

```text
adb logcat -v threadtime RideLabGatt:D BleScanner:D *:S
```

从点击连接之前开始记录，到发现完成或失败后结束，保留以下条目：

- `Device tapped address=... name=...` → `ViewModel connect address=...` → `Connect address=... name=... advertised=...`
- `connectGatt invoked address=... autoConnect=false transport=LE`、`connectGatt returned address=... hasGatt=...`；各阶段地址必须与被点击行完全一致。
- `Connection status=... newState=...`（连接成功通常为 status=0、newState=2）
- `discoverServices invoked address=...`、`discoverServices returned ... started=...`、`Services discovered address=... status=... count=...`
- 全部 `Service uuid=...`、`Characteristic service=... uuid=... properties=0x...`、`Descriptor ...`
- `Capabilities FTMS=... CyclingPower=... IndoorBikeData=...`
- `CCCD write started ... value=0100`、`CCCD write completed ... status=0`
- `Indoor Bike Data notifications enabled; waiting for packets`
- `IndoorBikeData packet=... raw=... flags=... speedKph=... cadenceRpm=... trainerPowerWatts=...`
- `Malformed IndoorBikeData ... reason=... offset=...`
- 所有 `GATT failure`、`Operation timeout`、异常堆栈、断开和关闭异常。

属性位：0x01 广播，0x02 读取，0x04 无响应写，0x08 写，0x10 通知，0x20 指示，0x40 签名写，0x80 扩展属性。属性仅展示，不代表本应用会执行写入或控制。

## 若 GATT 发现后仍无 FTMS，请发回

1. 设备名称、地址（可遮盖部分）、型号、固件版本（若已知）、手机型号、Android 版本。
2. 完整日志，特别是 `Services discovered status=0`、服务数量、所有 Service/Characteristic/Descriptor 行及 Capabilities 行。若发现失败或超时，请报告失败本身，不将其当作“确认没有 FTMS”。
3. 已选设备的检测结果及服务列表截图，确认 `0x1818`、`0xFFF0`、`6e40fec1-b5a3-f393-e0a9-e50e24dcca9e` 是否出现在 GATT 数据库，而不仅是广播。
4. 当时是否踩踏、是否有其他应用连接，以及唤醒/重启骑行台后重连的结果。
5. 若已经有其他 BLE 检查工具的服务导出，可一并提供以比较，不需要尝试私有协议或写入厂商特征。

## 自动验证

点击修复验证结果（2026-09-15）：先对修复前 APK 执行名称点击回归测试，确认回调未触发（预期设备，实际 null）。修复后 APK 和测试 APK 构建成功，15 项单元测试和 Android 13 模拟器上的 15 项设备端测试全部通过，lint 为 0 错误、13 警告。新增真实调用链测试从卡片点击进入生产 Activity、ViewModel 和客户端，确认原生 connectGatt 为同一地址返回非空 GATT 实例。该测试不代表骑行台已连接成功；Phase 2 真机验收仍未通过，需重新验证服务发现与通知。

点击调用链：`DevicesScreen` 的卡片/按钮 `onClick` → `onConnect(device)` → `MainActivity.onDeviceSelected` → `DevicesViewModel.connect` → 停止扫描 → `FtmsClient.connect` → 根据该行地址获取 `BluetoothDevice` → `BluetoothDevice.connectGatt(context, false, callbacks, TRANSPORT_LE)`。连接成功回调进入 `DISCOVERING` 并调用 `discoverServices()`；`onServicesDiscovered` 填充完整服务与特征列表，检测 FTMS 和 Cycling Power。缺失权限先显示结构化失败，再请求权限；待授权的选定设备保存在 ViewModel 中，避免 Activity 重建后丢失。

```text
gradlew.bat assembleDebug assembleDebugAndroidTest test lintDebug
gradlew.bat connectedDebugAndroidTest
```

单元测试覆盖 BLE 服务分类、全部 8192 种 FTMS 已定义标志组合、可选字段偏移、负功率、边界与零值、截断/尾随/保留标志、分包指标更新时间，以及基于 GATT 而非广播的能力检测。设备端测试覆盖应用上下文、语言回退、扫描设备选择、无 FTMS 提示和 Activity 重建。模拟器无法替代真实 BLE 设备验证。

运行完整设备端测试时需开启测试设备的蓝牙。`GattConnectionPathTest` 仅在测试界面提供一个不含 FTMS 广播的设备地址，并验证真实 connectGatt 调用；不会宣称连接到不存在的外设。卡片测试还覆盖同名设备的精确地址选择、列表下方的卡片背景点击、固定状态可见性和失败后重试。

## 本次文件变更

无新增或调整依赖，无第三方 BLE 库。未修改构建脚本、清单或 Phase 1 扫描逻辑。

| 文件 | 变更 |
| --- | --- |
| `AGENTS.md`、`PROJECT.md` | 默认语言改为简体中文，要求完整英文翻译 |
| `ROADMAP.md` | 标记 Phase 1 真机通过、记录广播信息，Phase 2 为当前阶段 |
| `PHASE2_VERIFICATION.md` | 本验证说明 |
| `app/src/main/res/resources.properties` | 默认资源语言 zh-CN |
| `app/src/main/res/values/strings.xml` | 默认中文与所有 Phase 2 文案 |
| `app/src/main/res/values-en/strings.xml` | 新增完整英文资源 |
| `app/src/main/res/values-zh-rCN/strings.xml` | 移除，中文已迁移到默认资源 |
| `app/src/main/java/com/isaac/ridelab/MainActivity.kt` | 接入 ViewModel、连接权限入口与前台生命周期 |
| `app/src/main/java/com/isaac/ridelab/ble/ftms/FtmsClient.kt` | 原生 GATT、服务发现、CCCD 订阅、错误状态、日志及清理 |
| `app/src/main/java/com/isaac/ridelab/ble/ftms/FtmsConstants.kt` | Indoor Bike Data 与 CCCD UUID |
| `app/src/main/java/com/isaac/ridelab/ble/ftms/FtmsParser.kt` | FTMS 标志与可选字段解析 |
| `app/src/main/java/com/isaac/ridelab/data/model/ConnectionState.kt` | 结构化连接阶段 |
| `app/src/main/java/com/isaac/ridelab/data/model/GattDeviceState.kt` | 服务信息、错误类型、实时指标及独立更新时间 |
| `app/src/main/java/com/isaac/ridelab/ui/screen/DevicesViewModel.kt` | 持有扫描与连接客户端 |
| `app/src/main/java/com/isaac/ridelab/ui/screen/DevicesScreen.kt` | 扫描结果连接按钮与连接详情入口 |
| `app/src/main/java/com/isaac/ridelab/ui/screen/ConnectionDetails.kt` | 本地化连接状态、服务列表、特征属性及实时值 |
| `app/src/test/java/com/isaac/ridelab/ble/ftms/FtmsParserTest.kt` | 解析器单元测试 |
| `app/src/test/java/com/isaac/ridelab/ble/ftms/GattDeviceStateTest.kt` | 分包指标与服务能力检测测试 |
| `app/src/androidTest/java/com/isaac/ridelab/LocalizationTest.kt` | 中英文与默认语言回退测试 |
| `app/src/androidTest/java/com/isaac/ridelab/DevicesScreenTest.kt` | 设备选择、无 FTMS 提示和 Activity 重建测试 |

工作区另有 `BleScanner.kt` 导入行末空白变化；扫描实现未改动。

## 实现参考

- Bluetooth SIG [GATT Specification Supplement — Indoor Bike Data](https://btprodspecificationrefs.blob.core.windows.net/gatt-specification-supplement/GATT_Specification_Supplement.pdf)：字段顺序、位标志及单位。
- Android [BluetoothGattCallback](https://developer.android.com/reference/android/bluetooth/BluetoothGattCallback)：API 33 的 value 参数回调与旧版本兼容。
- Android [BluetoothGatt](https://developer.android.com/reference/android/bluetooth/BluetoothGatt)：服务发现、通知启用和描述符写入。
