# AGENT 上下文交接文档

## 最新授权修复：主题选择（优先于下方视觉阶段）
- 已修 SettingsSnapshot 重复 package、SettingsRepository 遗失 settingsDataStore、SettingsDataStore 遗失 KEY_THEME_MODE。首页设置顶部增加跟随系统/浅色/深色 48dp 单选行；默认 SYSTEM，DataStore持久、非法写入拒绝/非法存储回退 SYSTEM，清除 resetAll 恢复 SYSTEM。
- SettingsVM.theme 沿既有 operation + maintenanceGate.write。同一 Activity SettingsVM 外提到 Theme 外订阅，Main 解析 LIGHT=false / DARK=true / else=系统；全页面及系统栏样式即时同步，仅重组、不重建服务/Activity、不自动采集。无新依赖/SDK/schema/其他业务改动。
- 扩展现有隔离 ReminderIntegrationTest：DARK持久及 owner 重开对比、invalid拒绝/存储回退、reset SYSTEM，编译成功未设备执行。assembleDebug、assembleDebugAndroidTest成功；正确包四专项共27通过/0失败/0跳过（8+9+6+4），首次错误维护包过滤只跑23项后已重跑。未重跑全套，原4项AdviceEngineTest失败仍是历史限制。
- 真机待验收：设置三选即时覆盖全部Tab、系统栏/三键对比度、旋转重启持久、SYSTEM跟随与强制模式不随系统、清除恢复SYSTEM/后台false、服务状态不被切换改变、TalkBack/200%字体/小屏。前次无设备，本轮未部署/截图/connected执行，不得称设备通过。详见README最新修复段。


## 最新授权：统一视觉、Dashboard、历史图表与报告（优先级最高）

- 当前新增 DashboardViewModel/HistoryViewModel、HistoryRepository、纯Kotlin HistoryAggregator、HistoryReportRow 查询投影；AppContainer手动DI。四Tab结构保留，MainActivity AnimatedContent 与图标，Home原授权手动流程保留且退场active=false撤销pending，不开启后台自动采集。未新增依赖、实体、schema迁移；Room仍3，P5公式/P6/P7规则未改，P8仍未实现。
- 固定青绿蓝色板，深浅色MaterialTheme/系统sp字体，Shape8/12/16/24/28dp与Spacing8/16/24dp。Monitor等级色改主题语义色并保持文字等级；统一EmptyState/ErrorState/LoadingSkeleton、数值动画，Canvas不做无限动画。
- 历史只读链：Noise/Light原BETWEEN传end-1ms；SleepReportDao.getHistory联合会话startTime及完整报告，HistoryReportRow不是实体；HeadphoneUsageDao.getOverlapping查询已结束重叠区间。HistoryRepository四路Flow在Default聚合，HistoryVM维护blocked取消、generation重读，WhileSubscribed离页立即停止且清replay。详细规则见README最新段。
- 日/周/月为本地自然范围，周一开始、毫秒半开；日实际小时含DST、周月按自然日。可上一/下一与回当前（90日/13周/3月范围限制，不改90天保留）。噪音leqDb算术均值仅趋势；光照<5/5–50/50–300/≥300lux真实采样计数，nullable/非finite排除；耳机区间按桶裁剪毫秒转分钟、有线USB/蓝牙堆叠，仅已持久结束区间；报告以会话开始时间分桶totalScore均值。缺失null不填零不跨缺口连线，采样只存7天故月范围会缺失。
- 历史列表详情本地id/BackHandler，范围变化/删除/维护失效选中，不缓存旧实体；历史仅展示持久评分摘要，无报告关联的建议不混用。旧最新报告/建议和会话控制保留，ReportViewModel写入gate不动。图表中文逐桶语义描述、heading/单位/缺失说明，新增触控至少48dp；未真机验证。
- 本轮串行结果：assembleDebug成功；HistoryAggregatorTest8通过/0跳过/0失败；全套69通过/0跳过/4失败（原AdviceEngineTest保持不动）；assembleDebugAndroidTest成功。adb devices空列表，未运行仪器/真机；没有现有@Preview故没有before渲染或截图依赖。不能声称全绿/设备验证。
- 待验收：深浅色对比度、200%字体/小屏、TalkBack/触控、Tab/详情/旋转、图表日期边界/错误重试/部分缺失、长期采样性能、动画退场权限回调不启动、清除后Dashboard/图表/详情缓存不复活；上一授权全部P6/P7/提醒/维护设备项仍待验收。
- 本节与README最新段优先于下方“未实现历史报告”、旧61/4、Tab无图标等历史描述；不要照抄旧状态。


## 0. 最新授权阶段交接（优先于下方P6历史描述）

本轮已实施设置VM/Screen/首页入口、后台关闭await、完整可重入清除chain、采集与writer全部join、报告链maintenance gate、后台false重置/仅cleanup恢复、BOOT任务恢复、备份排除、TestDriver隔离测试。具体规则与实现详见README最前“本轮授权阶段”和docs/testing.md、docs/security-alignment.md。未实现P8自动睡眠；历史报告现已由最新视觉阶段实现；不可擅自加依赖/改P5算法。

- 实际Room为**schema v3**，已有destructive fallback；本轮未升级。噪音校准默认**96dB**，实时后续帧更新、Leq最多1min含旧校准。下文v1/0f是旧阶段记录，不得照抄为当前事实。
- SettingsScreen校准使用可编辑OutlinedTextField数字/小数输入，显示当前持久偏移；保存经SettingsVM operation内toFloatOrNull、finite和0..120校验，非法输入中文错误、不崩溃、不落库。新校准设置/清除维护状态变化同步输入；默认96及后续帧即时生效保持不变。
- SettingsScreen通过Activity ViewModel，后台默认false、开启不自动采集，Home/报告先开关授权再手动启动；服务START_NOT_STICKY及启动generation检查。关闭DataStore后立刻停止await所有writer，不能因defaultfalse在刚启动时反复自动停。
- DataMaintenance使用独立clearMutex串行清除/开关，maintenanceGate封新入口；持久clearing，取消work，**不持gate等待stop/join**，然后gate内clearAllTables/清缓存/通知/reset，后台false，generation失效旧报告写入/缓存，恢复唯一cleanup工作。error持久fail-closed并可重复清除恢复。执行段NonCancellable，UI离开不导致删除后还开着入口。所有新的写入调用必须遵守gate/generation，不可新加绕过gate的报告caller。
- NoiseMonitor取消并join分析/save/录音任务；LightMonitor保留停止任务和camera，先Main解绑后join frame/save/sampling、IO等待analysis executor结束；Service使用AppContainer共享Room。HeadphoneGuard停止后清除tracker pending和历史缓存generation，防止清除后失败待写/旧读取复活。系统强杀仍不能保证最后落库。
- 报告整条链由ReportViewModel gate串行，MainActivity以viewModel(factory)获取，不再remember裸VM；UseCase/SessionManager只作必要CancellationException重抛，无P5公式/阈值改动。不在usecase内重复获取同一mutex以免死锁。
- 提醒先DataStore durable节流claim后notify，未知结果保留claim（可能少一次通知），明确false回滚；权限/频道关闭预检查不claim、不计响应分母。真实接受且保存成功事件才算分母。ActionReceiver8s超时覆盖锁等待，私有显式immutable动作、UUID/URI/持久事件匹配、claimAction防重放、读回更低才有效。
- BOOT仅恢复Work，不启动硬件。后台false周期Worker只执行保留，clearing/error阻断。备份/设备转移排除database、datastore文件、sharedpref。
- 新依赖仅批准androidTest work-testing2.9.1；维持manualDI，测试Runner使用普通Application隔离生产调度，独立临时DataStore/in-memory Room、假clock/sink/WorkerFactory；NoiseSampleDaoTest runBlocking，无新增androidTest coroutines-test/Hilt。
- 真实结果：assembleDebug与assembleDebugAndroidTest成功；ReminderPolicy9 + HeadphoneGuard6 + Maintenance4 = **19通过**。全套最新**61通过/4失败/0跳过**，4项原有AdviceEngineTest保持不动。connectedDebugAndroidTest失败 **No connected devices!**；7项新仪器集成测试仅编译，没有执行。不得声称设备/仪器/全套全绿。
- 待验收：首页设置与阈值持久/静默边界、校准实时、关闭监听/CameraX/AudioRecord释放及最终落库、并发清除/重试/进程恢复/旧VM缓存、通知权限/频道/动作/OEM/BOOT/备份恢复；P6全部原设备项仍待测。

下方P6段为历史交接：有冲突时以上最新段及代码为准。


## 1. 当前阶段与进度

**P5 睡眠环境分析与建议引擎已完成**：实现睡眠会话管理、三维评分算法（噪音/光照/规律性）、12+ 条规则驱动的建议引擎、报告页面 UI（圆形评分图表 + 三维评分 + 建议列表）。集成到 MainActivity，支持手动开始/结束睡眠监测，自动生成报告与建议。编译通过，功能完整可运行。

**已完成模块**：P0 工程脚手架、P1 数据层骨架、P2 权限通知服务框架、P3 噪音检测模块、P4 光照检测模块（真实 Lux 值计算）、P5 睡眠环境分析与建议引擎。

**当前未完成**：设备启动验证、P6及本轮真机验收、自动睡眠判定。设置/保留/提醒/清除已实现；最新全套61通过/4项原有AdviceEngineTest失败，仪器测试无设备，勿声称全部验证通过。

## 2. 架构总览

```text
UI（Compose screen/component/navigation/theme）
        ↓ 单向数据流
ViewModel（ui/viewmodel）
        ↓
Domain（model/usecase/analysis/sleep）
        ↓
Data（repository/local/sensor）
        ↓
Room / DataStore / CameraX / 音频与光照传感器
        ↓
Service（EnvMonitorService 前台服务）
```

Service、Worker、Notification 作为后台任务边界，必须由用户可停止并释放资源。

## 3. 目录结构

- **`ui/screen`**：**HomeScreen 已完成** - 首页监测按钮与权限流程；**MonitorScreen 已完成** - 环境监测页面；**ReportScreen 已完成** - 睡眠报告页面。
- `ui/component`：复用组件；当前为占位。
- `ui/theme`：Compose 主题。
- `ui/navigation`：导航约定；当前四 Tab 由 MainActivity 使用 `rememberSaveable` 保存索引管理，未使用 NavHost。
- **`ui/viewmodel`**：**ReportViewModel 已完成** - 报告页面状态与事件管理。
- **`domain/model`**：**已完成** - 6 个枚举 + 6 个数据类（SleepReport 已更新支持 id 和 regularityScore nullable）。
- **`domain/usecase`**：**已完成** - GenerateSleepReportUseCase、GenerateHealthAdviceUseCase。
- **`domain/analysis`**：**已完成** - SleepAnalyzer（三维评分算法）、AdviceEngine（12+ 条规则）。
- **`domain/sleep`**：**已完成** - SleepSessionManager（睡眠会话管理器）。
- **`data/local/room`**：**已完成** - 6 个 Entity + 6 个 DAO + EnvHealthDatabase（实际版本 3，schema 已导出）。
- **`data/local/datastore`**：**已完成** - SettingsDataStore 用户设置持久化。
- **`data/repository`**：**已完成** - 6 个 Repository（新增 AdviceRepository）统一暴露 Flow。
- **`data/sensor/audio`**：**已完成** - AudioNoiseRecorder（录音器）+ NoiseAnalyzer（分析器）+ NoiseMonitor（管理器）。
- **`data/sensor/light`**：**已完成** - LightSensorCamera（真实 Lux 值计算）+ LightMonitor（管理器）。
- **`service`**：**EnvMonitorService 已完成** - 前台服务框架，已集成噪音监测和光照监测。
- **`notification`**：**NotificationHelper 已完成** - 通知渠道与通知构建封装。
- **`di`**：**AppContainer 已完成** - 手动依赖注入容器。
- **`EnvHealthApplication`**：**已完成** - 应用程序类，初始化 AppContainer。
- `worker`：ReminderWorker唯一15分钟周期；后台关闭仅清理，Boot仅恢复调度，DataMaintenance全量维护。
- **`util`**：Constants、LogX、TimeUtils、**MonitorState**（全局状态）、**AppResult**（结果封装）。
- **`util/permission`**：**PermissionManager** + Compose 权限扩展 + **PermissionRationaleDialog**。

## 4. 已完成模块

### 4.1 应用入口
- [MainActivity.kt](app/src/main/java/com/GXR806/envhealth/MainActivity.kt)：Compose 容器与四 Tab 切换，**已集成通知渠道初始化、ReportViewModel 实例化、报告页面路由**。
- [EnvHealthApplication.kt](app/src/main/java/com/GXR806/envhealth/EnvHealthApplication.kt)：应用程序类，初始化 AppContainer 依赖注入容器。

### 4.2 报告页面（ui/screen）
- [`ReportScreen.kt`](app/src/main/java/com/GXR806/envhealth/ui/screen/ReportScreen.kt)：
  - 睡眠会话控制（开始/结束/取消）
  - 综合评分圆形图表（0-100 分，颜色分级）
  - 三维评分卡片（噪音/光照/规律性）
  - 健康建议列表（可展开/收起）
  - 空状态提示
  - UI 状态响应式更新

### 4.3 报告 ViewModel（ui/viewmodel）
- [`ReportViewModel.kt`](app/src/main/java/com/GXR806/envhealth/ui/viewmodel/ReportViewModel.kt)：
  - **状态管理**：
    - `uiState: StateFlow<ReportUiState>`：UI 状态 Flow
    - `currentSession: StateFlow<SleepSession?>`：当前会话 Flow
    - `latestReport: StateFlow<SleepReport?>`：最新报告 Flow
    - `latestAdvice: StateFlow<List<AdviceItem>>`：最新建议 Flow
  - **事件处理**：
    - `startSleepSession()`：开始睡眠会话
    - `endSleepSession()`：结束睡眠会话并生成报告
    - `cancelSleepSession()`：取消睡眠会话
    - `resetUiState()`：重置 UI 状态
  - **UI 状态枚举**：
    - `Idle`：空闲
    - `Loading`：加载中
    - `SessionStarted`：会话已开始
    - `SessionCancelled`：会话已取消
    - `ReportGenerated`：报告已生成
    - `Error`：错误

### 4.4 睡眠会话管理器（domain/sleep）
- [`SleepSessionManager.kt`](app/src/main/java/com/GXR806/envhealth/domain/sleep/SleepSessionManager.kt)：
  - `startManualSession()`：手动开始睡眠会话
  - `endSession(sessionId)`：结束睡眠会话（ONGOING → COMPLETED）
  - `cancelSession(sessionId)`：取消睡眠会话（ONGOING → CANCELLED）
  - `getOngoingSession()`：获取当前进行中的会话（Flow）
  - `getOngoingSessionOnce()`：获取当前进行中的会话（一次性）

### 4.5 睡眠分析器（domain/analysis）
- [`SleepAnalyzer.kt`](app/src/main/java/com/GXR806/envhealth/domain/analysis/SleepAnalyzer.kt)：
  - **噪音评分（0-100）**：
    - 公式：`noiseScore = 100 - (avgLeqPenalty + maxDbPenalty + burstEventPenalty)`
    - avgLeqPenalty = min((avgLeq - 20) * 3.0, 50)      // 基线 20dB，权重 50%（✅ 更敏感）
    - maxDbPenalty = min((maxDb - 35) * 1.5, 30)         // 基线 35dB，权重 30%（✅ 更敏感）
    - burstEventPenalty = min(burstCount * 4.0, 20)      // 权重 20%（✅ 更敏感）
    - **突发噪声检测**：相邻帧差值 > 15dB 且超过阈值，计为一次突发事件
  - **光照评分（0-100）**：
    - 公式：`lightScore = 100 - (avgLuxPenalty + fluctuationPenalty + preSleepTrendPenalty)`
    - avgLuxPenalty = min((avgLux - 2) * 2.5, 50)       // 基线 2 lux，权重 50%（✅ 更敏感）
    - fluctuationPenalty = min(stdDevLux * 3.0, 30)     // 权重 30%（✅ 更敏感）
    - preSleepTrendPenalty = 20 if 睡前1h未下降 else 0 // 权重 20%
    - **Bug修复**：avgLux = 0 时返回默认分 88（避免误判为 70 分）
    - **光照采集**：使用前置摄像头进行监测
  - **规律性评分（0-100）**：
    - 公式：`regularityScore = 100 - sleepTimeStdDevPenalty`
    - sleepTimeStdDevPenalty = min(stdDevHours * 20, 100)
    - 需要至少 3 天数据
  - **综合评分**：
    - 公式：`totalScore = noiseScore * 0.45 + lightScore * 0.35 + regularityScore * 0.20`
    - 规律性数据不足时：`totalScore = noiseScore * 0.60 + lightScore * 0.40`

### 4.6 建议引擎（domain/analysis）
- [`AdviceEngine.kt`](app/src/main/java/com/GXR806/envhealth/domain/analysis/AdviceEngine.kt)：
  - **规则表驱动**：13 条规则（R01-R08, R11-R15）
  - **去重策略**：❌ 已取消 24 小时去重限制，每次生成报告时重新评估所有规则
  - **规则分类**：
    - 噪音相关：R01-R03, R13（4 条）
      - R01: avgLeq > 25 dB（✅ 已调整为 25dB，更灵敏）
      - R02: burstCount > 5（✅ 已调整为 5 次，更灵敏）
      - R03: maxDb > 70 dB
      - R13: avgLeq in 15-25 dB（✅ 已调整为 15-25 dB）
    - 光照相关：R04-R07（4 条）
    - 规律性相关：R08（1 条）
    - 综合评分：R11-R12, R14（3 条）
    - 数据不足：R15（1 条）
  - **优先级**：1-5（5 最高）

### 4.7 UseCase 层（domain/usecase）
- [`GenerateSleepReportUseCase.kt`](app/src/main/java/com/GXR806/envhealth/domain/usecase/GenerateSleepReportUseCase.kt)：
  - 调用 SleepAnalyzer 分析睡眠会话
  - 创建 SleepReport 并存储到数据库
  - 返回报告和分析结果
- [`GenerateHealthAdviceUseCase.kt`](app/src/main/java/com/GXR806/envhealth/domain/usecase/GenerateHealthAdviceUseCase.kt)：
  - 调用 AdviceEngine 生成建议
  - 批量存储建议到数据库
  - 返回建议列表

### 4.8 依赖注入（di）
- [`AppContainer.kt`](app/src/main/java/com/GXR806/envhealth/di/AppContainer.kt)：
  - **数据库**：EnvHealthDatabase（Room）
  - **Repository**：NoiseRepository、LightRepository、SleepRepository、AdviceRepository
  - **Domain**：SleepSessionManager、SleepAnalyzer、AdviceEngine
  - **UseCase**：GenerateSleepReportUseCase、GenerateHealthAdviceUseCase

### 4.9 领域模型更新（domain/model）
- [`SleepReport.kt`](app/src/main/java/com/GXR806/envhealth/domain/model/SleepReport.kt)：
  - 新增 `id: Long` 字段（数据库自动生成）
  - `regularityScore: Float?`（nullable，支持数据不足场景）

### 4.10 数据库更新（data/local/room）
- [`SleepReportEntity.kt`](app/src/main/java/com/GXR806/envhealth/data/local/room/entity/SleepReportEntity.kt)：
  - 新增 `id: Long` 字段（PrimaryKey, autoGenerate）
  - `regularityScore: Float?`（nullable）
  - 索引调整：`indices = [Index("sessionId")]`（非唯一）

### 4.11 Repository 更新（data/repository）
- [`AdviceRepository.kt`](app/src/main/java/com/GXR806/envhealth/data/repository/AdviceRepository.kt)：
  - `insertAdvice(advice: AdviceItem)`：插入建议
  - `insertAllAdvice(adviceList: List<AdviceItem>)`：批量插入建议
  - `getAllAdvice()`：查询所有建议（Flow）
  - `getAdviceByCategory(category: AdviceCategory)`：按类别查询建议
  - `getRecentAdvice(limit: Int)`：查询最近 N 条建议
  - `getAdviceSince(sinceTime: Long)`：查询指定时间后的建议（用于去重）

## 5. P5 睡眠环境分析与建议引擎详细说明

### 5.1 核心流程
1. **开始睡眠会话**：用户点击"开始监测" → SleepSessionManager.startManualSession() → 创建 SleepSession（ONGOING）
2. **结束睡眠会话**：用户点击"结束监测" → SleepSessionManager.endSession() → 更新 SleepSession（COMPLETED）
3. **生成报告**：GenerateSleepReportUseCase → SleepAnalyzer.analyze() → 计算三维评分 → 保存 SleepReport
4. **生成建议**：GenerateHealthAdviceUseCase → AdviceEngine.generateAdvice() → 匹配规则表 → 保存 AdviceItem（已取消去重）
5. **UI 展示**：ReportScreen 订阅 ViewModel StateFlow → 显示评分 + 建议列表

### 5.2 评分算法关键参数
- **噪音基准**：
  - 评分基线：20 dB（✅ 已调整为 20dB，更灵敏）
  - 噪音峰值阈值：35 dB（✅ 已调整为 35dB，更灵敏）
  - 突发噪声检测：1秒内最大分贝变化 > 15 dB（✅ 已移除分贝阈值限制）
- **光照基准**：
  - 黑暗阈值：5 lux
  - 光照波动阈值：20 lux 标准差
  - 睡前趋势：前半段与后半段亮度比较
- **规律性基准**：
  - 最少数据要求：3 天
  - 理想数据要求：7 天
  - 每小时标准差扣 20 分

### 5.3 建议规则触发条件
- **R01**：avgLeq > 45 dB（噪音偏高）
- **R02**：burstCount > 10（突发噪音频繁）
- **R03**：maxDb > 70 dB（噪音峰值过高）
- **R04**：avgLux > 50 lux（光照过强）
- **R05**：stdDevLux > 20 lux（光照波动大）
- **R06**：!preSleepTrendDecreasing（睡前光照未调暗）
- **R08**：stdDevHours > 1.5（入睡时间不规律）
- **R14**：totalScore < 40（睡眠环境较差）
- **R15**：regularityScore == null（规律性数据不足）

### 5.4 UI 交互设计
- **会话控制区域**：
  - 未开始：显示"开始监测"按钮
  - 进行中：显示"结束监测"和"取消"按钮
  - 加载中：显示 CircularProgressIndicator
  - 错误：显示错误提示
- **评分展示**：
  - 圆形评分图表：150dp 圆形，颜色分级（绿/黄/橙/红）
  - 三维评分卡片：噪音/光照/规律性（带颜色点）
  - 规律性数据不足时显示"数据不足"
- **建议列表**：
  - 卡片式展示，可点击展开/收起
  - 显示建议标题、类别、详细内容
  - 空建议时显示"暂无建议"

## 6. 关键常量与校准参数

- `SettingsDataStore.DEFAULT_DB_CALIBRATION_OFFSET = 96f`：当前运行默认分贝估算校准偏移（可配置）；不是实测标准声压校准
- `DEFAULT_LUX_K = 125f`：Lux 估算校准常数（室内开灯环境显示 250-500 lux）
- `DEDUP_WINDOW_MS = 24 * 3600_000L`：建议去重窗口（24 小时）
- `MIN_SESSIONS_FOR_REGULARITY = 3`：规律性评分最少数据要求
- `TARGET_SESSIONS_FOR_REGULARITY = 7`：规律性评分理想数据要求

## 7. 已知问题 / 技术债

- 四 Tab 当前没有图标
- 耳机守护代码已完成，插拔/播放/OEM/停止资源释放等设备验证待进行
- 数据保留策略（7 天/90 天）已实现，设备验收未完成
- 自动睡眠判定未实现
- 设备验证尚未进行
- 校准偏移参数需要用户通过标准设备校准
- 通知图标当前使用系统默认图标，后续需替换为专用图标
- 报告页面当前仅显示最新报告，未实现历史报告查看

## 8. 绝对不能改动的部分

- 包名 `com.GXR806.envhealth`、minSdk 26、targetSdk 34
- 隐私约束：原始音频与图像不得落盘或上传
- 估算指标必须标注"估算值"；校准常数可配置
- 数据保留策略：原始采样 7 天，聚合报告 90 天
- 时间统一使用毫秒时间戳
- 前台服务必须可被用户停止并释放资源
- 未经 Tech Lead 确认不得新增依赖或实现后续阶段功能
- 评分算法公式与权重配置（除非经过实测校准）

## 9. 后续工作入口

**已完成阶段**：
- ✅ P0 工程脚手架
- ✅ P1 数据层骨架
- ✅ P2 权限、通知、前台服务框架
- ✅ P3 噪音检测模块
- ✅ P4 光照检测模块（真实 Lux 值计算）
- ✅ P5 睡眠环境分析与建议引擎

**下一阶段建议**：
1. **P6 设备验收**：按 TODO 中未勾选项验证，不另行扩展功能
2. **数据保留策略（P7）**：WorkManager 定期清理原始采样（7 天）与聚合报告（90 天）
3. **自动睡眠判定（P8）**：前台服务周期性检查（每 5 分钟）+ 光照/噪音睡眠检测算法
4. **历史报告查看**：报告列表页面 + 详情页面
5. **设置页面**：校准参数配置界面（db_calibration_offset, lux_estimation_factor）

## 11. P6 实施交接（本次 Tech Lead 已授权）

### 目录、命名与架构
- `data/sensor/volume/VolumeMonitor.kt`：只读 STREAM_MUSIC 当前/max 百分比；Settings.System.CONTENT_URI descendants + ContentObserver；AudioDeviceCallback 有线/USB/A2DP。公开 SDK 无 VOLUME_SETTINGS 常量；5s 音量刷新作为 OEM 漏通知兜底，30s isMusicActive，设备回调额外立即刷新。
- `domain/analysis/HeadphoneUsageTracker.kt`：纯 Kotlin 串行状态机，elapsedRealtime 单调计时；短暂停顿不计时/不计权重，严格 >120000ms 才结束逻辑会话，拔出/停止立即结束。每个实际播放区间持久化独立记录，短暂停顿恢复仍累加本次。
- `domain/analysis/RiskEvaluator.kt`：SAFE/LOW/MEDIUM/HIGH；HIGH 优先，中文多原因并存，全部严格 >。>60min且当前播放音量>60%=HIGH；仅>60min=MEDIUM；>80%或7天>40h=HIGH；短时>60%=LOW。百分比不是dB，40h不是WHO安全保证。
- `data/sensor/volume/HeadphoneGuard.kt`：服务生命周期、Channel单消费者/IO存取、StateFlow快照、异常展示和内存待写重试。暂停即写完成区间，停止注销回调、排队停止、等待落库后取消任务；重复停止共享完成信号。错误停止不阻止释放耳机监听。冷启动读取历史不启动守护。
- `ui/viewmodel/HeadphoneGuardViewModel.kt` + `ui/screen/HeadphoneGuardScreen.kt`：MVVM，Activity范围ViewModel，通过StateFlow显示连接、音量、加权平均/最大、本次/今日/7天、风险、引导与错误。
- `AppContainer` 新增 headphoneRepository/headphoneGuard；`EnvMonitorService` 在既有手动监测启动后启动守护，停止与销毁时释放；`MainActivity` 耳机Tab替换占位，跳首页复用授权和启动按钮。无导航库、新服务、权限或依赖。

### 数据与兼容
实际既有仓库叫 `HeadphoneRepository`，未另外建立 HeadphoneUsageRepository。DAO新增getCompletedIntervals。保持Room版本1/schema/HeadphoneUsageEntity/领域模型不变：id（自增）、startTime（毫秒墙钟）、endTime（startTime+单调实际时长的估算墙钟）、avgVolumePercent（时间加权）、maxVolumePercent、deviceType（WIRED/BLUETOOTH；USB兼容映射WIRED但UI区别显示）。不持久化暂停，区间end-start即实际播放时长；逻辑会话仅内存。今日按本地午夜、7天为滚动168小时半开区间裁剪，加入进行中区间；成功落库项先移出待写列表，再读历史，避免重复计算。未动P5评分、数据库schema、依赖、SDK配置（min26/target34/compile34）。

### 验证证据与限制
`assembleDebug`最终 Build finished successfully；P6专项 `:app:testDebugUnitTest --tests com.GXR806.envhealth.domain.analysis.HeadphoneGuardTest` 为6 passed/0 skipped/0 failed。首次构建Service的this作用域错误已修复。全套`:app:testDebugUnitTest`为48 passed/4 failed，失败均为AdviceEngineTest旧阈值/去重断言（详见README），未越权修P5代码或测试。没有运行真机验证，TODO设备项仍[ ]。生命周期红线修正后再次assembleDebug成功，P6专项再次6 passed/0 skipped/0 failed；6项仅覆盖纯领域状态机/风险，不是Service线程与销毁竞态测试。本次未重跑全套，48/4为前次记录。

暂停检测/恢复最多约30秒延迟，边界时长与音量权重是估算；多连接输出选择首个支持设备，不保证媒体实际路由；USB存储类型WIRED；未支持SCO/BLE/助听。调墙钟时绝对区间位置是估算但单调时长稳定。普通用户停止在Main立即注销耳机监听、解绑CameraX，直接suspend noiseMonitor.stop（AudioRecord停止/释放在IO，finally保证release），挂起等待最终落库，不阻塞主线程；逐项失败隔离，最终清状态/foreground/stopSelf。onDestroy不再runBlocking：同步注销/解绑后返回，独立短生命周期cleanup scope继续IO释放/落库并自动cancel，不受serviceScope.cancel影响。耳机stop为Main-only即时入口，返回共享Deferred；IO单消费者完成tracker最后落库后在Main统一清理events/consumer/stopSignal，再发布done并cancel自有scope，等待者不参与清理。停止期间不可start，CameraProvider迟到回调不再绑定，分析executor停止。系统强杀进程未必执行销毁，也不保证异步写入完成，当前未完成/未落库区间可能丢失。既有onCreate校准读取runBlocking不属于本次停止/销毁修改范围。数据库错误保留内存待写列表并展示，下次事件/重启守护重试，杀进程会丢未落库部分。查询已完成区间按事件刷新；P7尚未清理，历史量增长需后续授权优化。耳机模块不录音、修改音量或请求WRITE_SETTINGS，但因共用环境服务，仍依赖首页录音/相机/通知权限。聚合耳机记录目标90天，自动清理仍未实现。下一入口是P6设备验收；P7/P8必须另行明确授权。

仅在负责人下达明确任务后推进；当前不自定义额外阶段目标。

## 10. 新 AI Agent 接手指南

1. **阅读顺序**：README.md → AGENT.md → TODO.md
2. **理解架构**：查看第 2 节架构总览与第 3 节目录结构
3. **熟悉 P5 核心**：查看第 5 节 P5 睡眠环境分析与建议引擎详细说明
4. **查看代码**：
   - 报告页面：`ui/screen/ReportScreen.kt`、`ui/viewmodel/ReportViewModel.kt`
   - 睡眠管理：`domain/sleep/SleepSessionManager.kt`
   - 分析引擎：`domain/analysis/SleepAnalyzer.kt`、`domain/analysis/AdviceEngine.kt`
   - UseCase：`domain/usecase/GenerateSleepReportUseCase.kt`、`domain/usecase/GenerateHealthAdviceUseCase.kt`
   - 依赖注入：`di/AppContainer.kt`、`EnvHealthApplication.kt`
5. **运行测试**：
   - 编译：`./gradlew assembleDebug`
   - 设备测试：部署到真机，点击"报告" Tab，开始睡眠监测，结束后查看报告与建议
6. **开始任务**：等待 Tech Lead 下达明确任务后开工
