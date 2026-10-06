# 环境健康 - Android 应用

## 本轮授权阶段：设置、健康提醒、数据维护（最新交接，优先于下方 P6 历史记录）

### 已实现
- 首页“设置与数据管理”入口 → Activity 范围 SettingsViewModel → SettingsScreen。噪音阈值40–90估算dB、音量20–80%、连续收听15–90分钟、原子静默起止HH:mm（支持跨午夜，起止相同全天静默）、校准偏移使用可编辑数字/小数 OutlinedTextField 输入并显示当前持久值，保存由 SettingsViewModel 校验 finite 且 0–120dB，默认96dB；空白、非法数字、NaN/Infinity及越界均显示中文错误、不保存、不崩溃。读到新校准设置及清除维护状态变化同步输入，后续帧即时生效。手动改基线版本使旧反馈不覆盖手动意图。
- 后台开关默认false；首页/报告页遵守开关并引导用户明确授权。开启只保存授权/恢复周期任务，绝不自动启动硬件；回首页手动点击并完成录音/相机/通知权限流程。关闭立即调用服务停止并挂起await全部writer完成，不阻塞Main；旧手动监测可持续直到关闭。服务START_NOT_STICKY，不因系统重建自动采集，启动请求带维护generation以拒绝清除前排队请求。
- ReminderPolicy与P5评分/P6风险展示独立：每类型24h节流，30天反馈，至少5个完整24h响应窗口；仅降低媒体音量且读回确实更低计有效响应，响应率≥50%阈值-5，<20%阈值+5，范围限制与严重风险保护。百分比不是dB，7天40h不是安全保证。
- 单一15分钟唯一周期ReminderWorker兼顾提醒与保留；后台false时只清理；BOOT_COMPLETED仅恢复任务，绝不启动Service。每24h至多清理一次，特征采样7天、已结束会话/报告/建议/耳机区间90天，截止线严格小于、保护未结束记录，报告随会话外键级联删除。
- 通知独立静默渠道，显式immutable动作PendingIntent，私有Receiver校验UUID/类型/动作/URI/持久事件/24h窗口。8秒deadline包括维护锁等待，CancellationException不吞；点击降音量才调整STREAM_MUSIC（MODIFY_AUDIO_SETTINGS，无WRITE_SETTINGS），从不提高当前音量。稍后提醒保持24h节流，静音7天。
- **先持久claim再notify**：DataStore保存claim失败不会发送；权限/全局通知/频道预检查关闭不消耗分母。明确send=false安全回滚claim；未知异常/进程死亡保留节流，可能丢失一次通知，这是至多一次的明确取舍。分母仅包括notify接受且成功保存的事件，不保证用户真正看见通知；系统notify与DataStore没有跨系统事务。
- 全量清除可重入：封入口并持久clearing → 取消唯一work → 不持维护写锁停止/await服务及耳机 → 维护锁内清空Room全部表 → 丢弃失败待写耳机区间/历史缓存，清实时快照及通知 → reset全部DataStore（含提醒反馈/静音/节流），后台false → generation失效报告/建议缓存和旧写入 → 恢复仅cleanup工作。失败持久error并fail-closed，再次确认清除可恢复；清除执行段NonCancellable保证离开页面不会半途释放入口。App冷启动先加载维护态；失败/中断清除不自动采集。
- Service共享AppContainer数据库/仓库，不再另外打开Room；噪音采集、分析、定时writer及光照frame/save/sampling任务cancel+join，CameraX先Main解绑再IO等待分析executor结束，耳机监听Main注销后等单消费者最后落库。报告会话→分析→报告→建议整个链持同一maintenance gate，不嵌套获取同锁等待Service；usecase/会话管理取消异常明确重抛，P5评分算法与旧4个失败测试未改。
- Room实际schema版本为**3**（本轮没有升级schema；既有destructive fallback意味着旧不兼容版本可能清空数据）。噪音估算校准默认**96dB**，后续帧即时更新，Leq窗口最多1分钟含旧帧，不改历史。
- 备份与设备转移规则排除所有database、file/datastore、sharedpref，避免记录/提醒反馈/后台授权在恢复时复活。没有原始音频/图像落盘或网络上传。

### 实际验证结果（不得宣称全部验收通过）
- `:app:assembleDebug :app:assembleDebugAndroidTest`：成功；本次仪器测试最小增强后再次运行`:app:assembleDebugAndroidTest`成功，并重跑下述专项19项成功；未运行仪器测试、未重跑全套。
- 专项 `ReminderPolicyTest + HeadphoneGuardTest + MaintenanceTest`：**19 passed / 0 skipped / 0 failed**（9+6+4）。维护4项覆盖停止join顺序、失败重试、关闭await、清除取消完成/旧generation拒写。
- 全套 `:app:testDebugUnitTest`：**61 passed / 0 skipped / 4 failed**，仅原有AdviceEngineTest四项旧阈值/去重断言，未修改它们；不是全绿。
- `:app:connectedDebugAndroidTest`：**No connected devices!**，没有执行设备测试，不能将编译通过写成仪器测试通过。
- ReminderIntegrationTest仍为7项（扩展既有测试，未增加项数），已编译、未设备执行：fake sink捕获事件，按本地ZoneId明确日期/时刻直接断言跨午夜两侧silent=true、白天silent=false；设置测试cancelAndJoin旧DataStore owner后，以新scope/PreferenceDataStoreFactory重新打开同一随机临时文件，完整对比设置快照（阈值/校准/后台/原子静默时间）及ReminderStore节流/静音/有效反馈，校验重开后的动作防重放、同type MUTE七天前1ms阻断/截止恢复和reset；另覆盖TestDriver周期/24h/后台关闭仅清理、权限不claim、Room保留边界/进行中保护/报告cascade、清除chain可重入/旧写入拒绝、false sink回滚/未知结果节流、维护阻断。IsolatedTestRunner不启动生产Application/DI调度，临时DataStore、in-memory Room、WorkerFactory手动注入runtime、假时钟/通知sink，不触达生产通知/数据库；tearDown取消当前（包括重开后）owner。NoiseSampleDaoTest改runBlocking；唯一新依赖为批准的androidTest `androidx.work:work-testing:2.9.1`，不新增androidTest coroutines-test、Hilt或其他库。
- 尚未设备验证：设置UI/权限授权、后台关闭资源释放与最终落库、清除并发/杀进程恢复、BOOT、通知/动作/OEM/音量读回、备份/转移、TestDriver仪器运行。有设备后运行 `:app:connectedDebugAndroidTest` 并逐项验收，不假称成功。
- 测试分析与安全范围报告：见 [docs/testing.md](docs/testing.md)、[docs/security-alignment.md](docs/security-alignment.md)。


## 项目简介

**环境健康**是一款 Android 应用，旨在帮助用户监测并改善睡眠环境质量。通过实时采集噪音、光照等环境数据，应用能够生成睡眠环境报告，并提供个性化的健康建议。

## 核心功能

### 已完成功能（v1.4）

#### 1. 环境监测
- **噪音检测**：实时监测环境噪音（Leq、Max、Peak dB），突发噪音事件检测
- **光照检测**：使用前置摄像头估算 Lux 值，支持光照等级判定（黑暗/昏暗/正常/明亮/强光）

#### 2. 睡眠环境分析 ⭐ 新功能
- **睡眠会话管理**：手动开始/结束睡眠监测
- **三维评分算法**：
  - 噪音评分（0-100）：基于平均分贝、峰值分贝、突发事件次数
  - 光照评分（0-100）：基于平均光照、波动、睡前趋势
  - 规律性评分（0-100）：基于近 7 天入睡时间标准差
  - 综合评分：加权和（噪音 45% + 光照 35% + 规律性 20%）
- **智能建议引擎**：基于 12+ 条规则生成个性化建议，每次报告重新评估（不做 24 小时去重）

#### 3. 报告页面 ⭐ 新功能
- **圆形评分图表**：直观展示综合评分（0-100）
- **三维评分展示**：噪音/光照/规律性详细评分
- **建议列表**：可展开/收起的健康建议卡片

#### 4. 耳机守护（P6，专项自动化通过，真机待验证）
- 首页手动启动环境监测后同时守护耳机；耳机页/首页/通知停止全部监测。
- 有线、USB、蓝牙 A2DP 连接监听，媒体音量档位百分比、播放状态估算。
- 实际播放时间累计、时间加权平均音量、最大音量、今日/近 7 天累计。
- 多原因风险卡，未连接/未启动引导与中文错误状态。

### 待完成功能

- P6 真机验证（代码已实现）
- 数据保留策略设备验收（P7代码已实现）
- 自动睡眠判定（P8）
- 历史报告查看
- 设置页面设备验收（代码已实现）

## 技术栈

- **语言**：Kotlin
- **UI 框架**：Jetpack Compose + Material 3
- **架构**：MVVM + 单向数据流
- **数据库**：Room
- **相机**：CameraX + Camera2 Interop
- **音频**：AudioRecord
- **并发**：Kotlin Coroutines + Flow
- **权限管理**：Accompanist Permissions
- **依赖注入**：手动 DI（AppContainer）

## 项目结构

```text
com.GXR806.envhealth/
├── ui/                          # UI 层
│   ├── screen/                  # 页面
│   │   ├── HomeScreen.kt        # 首页（监测控制）
│   │   ├── MonitorScreen.kt     # 环境监测页面
│   │   └── ReportScreen.kt      # 报告页面 ⭐
│   ├── viewmodel/               # ViewModel
│   │   └── ReportViewModel.kt   # 报告 ViewModel ⭐
│   └── theme/                   # 主题
├── domain/                      # 领域层
│   ├── model/                   # 领域模型
│   ├── sleep/                   # 睡眠管理 ⭐
│   │   └── SleepSessionManager.kt
│   ├── analysis/                # 分析引擎 ⭐
│   │   ├── SleepAnalyzer.kt
│   │   ├── AdviceEngine.kt
│   │   └── NoiseAnalyzer.kt
│   └── usecase/                 # 用例 ⭐
│       ├── GenerateSleepReportUseCase.kt
│       └── GenerateHealthAdviceUseCase.kt
├── data/                        # 数据层
│   ├── local/room/              # 数据库
│   │   ├── entity/              # 6 个 Entity
│   │   ├── dao/                 # 6 个 DAO
│   │   └── EnvHealthDatabase.kt
│   ├── repository/              # 仓库
│   │   ├── NoiseRepository.kt
│   │   ├── LightRepository.kt
│   │   ├── SleepRepository.kt
│   │   └── AdviceRepository.kt  ⭐
│   └── sensor/                  # 传感器
│       ├── audio/               # 音频采集
│       └── light/               # 光照采集
├── service/                     # 服务
│   └── EnvMonitorService.kt     # 前台服务
├── notification/                # 通知
│   └── NotificationHelper.kt
├── di/                          # 依赖注入 ⭐
│   └── AppContainer.kt
├── util/                        # 工具类
│   └── permission/              # 权限管理
├── EnvHealthApplication.kt      # 应用程序类 ⭐
└── MainActivity.kt              # 主 Activity
```

## 开发规范

### 代码风格
- 包名：`com.GXR806.envhealth`
- 代码注释、UI 文案、文档使用中文
- 代码函数命名使用符合 Kotlin 习惯的英文
- 时间统一使用毫秒时间戳（TimeUtils.nowMillis()）
- 日志通过 LogX 统一管理

### 架构约束
- 禁止主线程 IO、GlobalScope、裸 Thread
- 所有采集任务必须可停止并释放资源
- 音频、图像只做内存特征计算，不落盘、不上传、不保存原始帧
- 前台服务必须可被用户停止并释放资源

### 数据保留策略
- 原始采样数据（noise_samples, light_samples）：保留 7 天
- 聚合报告与记录（sleep_sessions, sleep_reports, advice_items）：保留 90 天

## 构建与运行

### 环境要求
- Android Studio Rabbit (2026.2.1) 或更高版本
- JDK 11
- Android SDK 34
- Gradle 9.6.0
- AGP 9.4.1

### 编译
```bash
./gradlew assembleDebug
```

### 运行
1. 部署到真机（推荐，需要麦克风和相机权限）
2. 点击"首页" Tab，授予权限后点击"开始监测"
3. 切换到"环境监测" Tab 查看实时数据
4. 切换到"报告" Tab，开始睡眠监测，结束后查看报告与建议

## 权限说明

应用需要以下权限：
- `RECORD_AUDIO`：录制音频以监测噪音
- `CAMERA`：使用摄像头计算光照强度
- `POST_NOTIFICATIONS`（Android 13+）：发送前台服务通知
- `FOREGROUND_SERVICE`：运行前台服务
- `FOREGROUND_SERVICE_MICROPHONE`：麦克风类型前台服务
- `FOREGROUND_SERVICE_CAMERA`：相机类型前台服务

**隐私承诺**：
- 原始音频与图像仅在内存中处理，不落盘、不上传
- 所有采集数据仅用于本地分析，不会发送到任何服务器

## 版本历史

### v1.4（当前版本）⭐
- ✅ 实现睡眠会话管理（手动开始/结束）
- ✅ 实现三维评分算法（噪音/光照/规律性）
- ✅ 实现智能建议引擎（12+ 条规则，无 24 小时去重限制）
- ✅ 实现报告页面 UI（圆形评分 + 三维评分 + 建议列表）
- ✅ 集成依赖注入（AppContainer + EnvHealthApplication）

### v1.3
- ✅ 实现光照检测模块（真实 Lux 值计算）
- ✅ 使用后置摄像头 + Camera2 Interop 获取曝光参数
- ✅ 调整校准常数 K = 125（室内开灯环境显示 250-500 lux）
- ✅ 集成到 EnvMonitorService

### v1.2
- ✅ 实现噪音检测模块（AudioRecord + RMS/Leq/Max）
- ✅ 实现前台服务框架（EnvMonitorService）
- ✅ 实现监测页面 UI（实时曲线图 + 噪音等级）

### v1.1
- ✅ 实现权限管理框架（三态处理 + 权限引导对话框）
- ✅ 实现通知基础设施（NotificationHelper）

### v1.0
- ✅ 项目脚手架（Kotlin + Jetpack Compose + Material 3）
- ✅ 数据库设计（Room + 6 个 Entity + 6 个 DAO）
- ✅ Repository 层实现

## P6 实施与验证记录

### 架构与数据
`EnvMonitorService → VolumeMonitor → HeadphoneGuard（串行 Channel / IO）→ HeadphoneUsageTracker + RiskEvaluator → HeadphoneRepository → Room`；页面通过 `HeadphoneGuardViewModel.uiState` 订阅 `StateFlow`。AppContainer 手动 DI，四 Tab 不新增导航库。关键文件：`data/sensor/volume/{VolumeMonitor,HeadphoneGuard}.kt`、`domain/analysis/{HeadphoneUsageTracker,RiskEvaluator}.kt`、`ui/screen/HeadphoneGuardScreen.kt`、`ui/viewmodel/HeadphoneGuardViewModel.kt`。

仓库沿用实际名称 `HeadphoneRepository`（不是新建 HeadphoneUsageRepository）。数据库版本 1/schema/实体字段未变：`id/startTime/endTime/avgVolumePercent/maxVolumePercent/deviceType`。每个连续实际播放区间落一条已完成记录，暂停即结束当前持久化区间，但短暂停顿保留逻辑会话；这样 `endTime-startTime` 就是收听时长，不含暂停。USB 在原有枚举中映射 WIRED，UI 显示 USB；A2DP 映射 BLUETOOTH。平均音量按毫秒加权。单调时钟 `elapsedRealtime` 计时；开始是墙钟毫秒，结束是开始墙钟加单调时长的估算时间，避免调时导致负时长。今日按本地午夜、近 7 天按滚动 168 小时半开区间裁剪，包含尚未落库的播放区间；成功入库后从待写列表移除再读取历史，避免双算。冷启动页面仅查询历史，不启动守护。

### 规则与限制
所有边界严格 `>`，多原因同时展示，优先级 HIGH > MEDIUM > LOW > SAFE：本次累计实际播放 >60 分钟且当前播放音量 >60% 为 HIGH；仅 >60 分钟为 MEDIUM；>80% 或近 7 天 >40 小时为 HIGH；短时 >60% 为 LOW；其他 SAFE（只表示未触发产品阈值）。短暂停顿不计入时间或音量权重，超过 2 分钟才结束逻辑会话，拔出/服务停止立即结束。百分比不是 dB，40 小时不是无声级前提的 WHO 安全保证。

公开 SDK 没有 `Settings.System.VOLUME_SETTINGS`；使用 `CONTENT_URI` 子树观察并重读 STREAM_MUSIC。OEM 可能不通知，5 秒刷新音量兜底；`isMusicActive` 每 30 秒轮询（设备回调触发额外立即检查）。暂停/恢复检测可延迟最多约 30 秒，故暂停排除和跨日分配是估算。多个设备同时连接使用首个支持的输出设备，不能证明其他应用媒体实际路由到耳机。蓝牙通话 SCO、BLE/助听设备不在本阶段支持范围。系统强杀进程不能保证 onDestroy，当前未结束区间可能丢失。普通停止在 Main 同步注销耳机监听并解绑 CameraX，协程直接 suspend 调用噪音停止（AudioRecord stop/release 在 IO，release 由 finally 保证），挂起等待最后落库后清状态、移除前台通知并 stopSelf，不阻塞主线程。任一模块失败不会阻止其他模块释放。onDestroy 同步注销/解绑后立即返回，使用独立且完成后自动 cancel 的短生命周期清理 scope；耳机单消费者也独立于 serviceScope，在 IO 完成 tracker 最后区间落库后 Main 串行清理并发布共享停止信号。重复停止不重复清理，停止期间拒绝重启；serviceScope cancel 不取消最终落库。Provider 延迟回调在停止后不再绑定，CameraX 分析 executor 一并 shutdown。没有 GlobalScope、裸 Thread 或停止/销毁 main runBlocking（既有初始化校准读取不在本次修改范围）。数据库错误展示并保留内存待写区间，下次事件/重启守护重试；失败后若进程被杀，未落库区间可能丢失。数据保留目标为聚合记录 90 天，清理仍属未授权 P7。

P6 不新增依赖/权限/服务，不请求 WRITE_SETTINGS，不改变音量、不录音、不保存或上传原始音视频。但守护复用环境前台服务，所以启动仍须通过首页现有录音/相机/通知权限流程。

### 本次真实工具结果
- `assembleDebug`：最终 `Build finished successfully.`（初次发现协程作用域 this 与 Service 类型不符，已修正后重建）。
- `:app:testDebugUnitTest --tests com.GXR806.envhealth.domain.analysis.HeadphoneGuardTest`：`6 passed, 0 skipped, 0 failed`。覆盖四级/严格边界/多原因、2 分钟暂停恢复、超过 2 分钟、拔出/重复停止、加权时长、墙钟跳变、时间区间裁剪。
- `:app:testDebugUnitTest`：`48 passed, 0 skipped, 4 failed`；4 项均为原有 AdviceEngineTest：`edgeCase_noRulesTrigger_shouldReturnEmpty`、`rule_R13_avgLeqModerate_shouldTrigger`、`deduplication_recentAdviceExists_shouldSkip`、`rule_R01_avgLeqNormal_shouldNotTrigger`。其旧阈值/去重断言与当前 P5 规则不一致，本任务未修改 P5 算法或测试；全套测试并非全绿。
- 生命周期红线修正后重新执行 `assembleDebug`：`Build finished successfully.`；重新执行同一 P6 专项命令：`6 passed, 0 skipped, 0 failed`。6 项为纯领域状态机/风险专项，未自动覆盖 Service/Android 回调线程/并发销毁；这些仍需设备验收。
- 未部署或验证真机：有线/USB/A2DP 插拔、音量按键/OEM 通知、第三方媒体暂停恢复、通知停止、后台与资源释放仍待实测。

## 文档

- [TODO.md](TODO.md)：项目开发清单
- [AGENT.md](AGENT.md)：AI Agent 上下文交接文档

## 贡献指南

本项目当前为内部开发阶段，暂不接受外部贡献。如有问题或建议，请联系项目负责人。

## 许可证

版权所有 © 2024 GXR806. 保留所有权利。

---

**最后更新**：设置/提醒/清除/保留与自动化代码已实现；专项19通过，全套61通过/4项P5旧失败，仪器APK编译成功但无设备可运行。
**下一步**：连接设备完成本轮清单与P6验收；P8自动睡眠与其他扩展未经另行授权不推进。
