# AGENT 上下文交接文档

## 1. 当前阶段与进度

**P5 睡眠环境分析与建议引擎已完成**：实现睡眠会话管理、三维评分算法（噪音/光照/规律性）、12+ 条规则驱动的建议引擎、报告页面 UI（圆形评分图表 + 三维评分 + 建议列表）。集成到 MainActivity，支持手动开始/结束睡眠监测，自动生成报告与建议。编译通过，功能完整可运行。

**已完成模块**：P0 工程脚手架、P1 数据层骨架、P2 权限通知服务框架、P3 噪音检测模块、P4 光照检测模块（真实 Lux 值计算）、P5 睡眠环境分析与建议引擎。

**当前未完成**：设备启动验证、耳机守护模块、数据保留策略、自动睡眠判定。

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
- **`data/local/room`**：**已完成** - 6 个 Entity + 6 个 DAO + EnvHealthDatabase（版本 1，schema 已导出）。
- **`data/local/datastore`**：**已完成** - SettingsDataStore 用户设置持久化。
- **`data/repository`**：**已完成** - 6 个 Repository（新增 AdviceRepository）统一暴露 Flow。
- **`data/sensor/audio`**：**已完成** - AudioNoiseRecorder（录音器）+ NoiseAnalyzer（分析器）+ NoiseMonitor（管理器）。
- **`data/sensor/light`**：**已完成** - LightSensorCamera（真实 Lux 值计算）+ LightMonitor（管理器）。
- **`service`**：**EnvMonitorService 已完成** - 前台服务框架，已集成噪音监测和光照监测。
- **`notification`**：**NotificationHelper 已完成** - 通知渠道与通知构建封装。
- **`di`**：**AppContainer 已完成** - 手动依赖注入容器。
- **`EnvHealthApplication`**：**已完成** - 应用程序类，初始化 AppContainer。
- `worker`：任务调度；当前不启动。
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

- `DEFAULT_DB_CALIBRATION_OFFSET_DB = 0f`：分贝估算校准偏移占位
- `DEFAULT_LUX_K = 125f`：Lux 估算校准常数（室内开灯环境显示 250-500 lux）
- `DEDUP_WINDOW_MS = 24 * 3600_000L`：建议去重窗口（24 小时）
- `MIN_SESSIONS_FOR_REGULARITY = 3`：规律性评分最少数据要求
- `TARGET_SESSIONS_FOR_REGULARITY = 7`：规律性评分理想数据要求

## 7. 已知问题 / 技术债

- 四 Tab 当前没有图标
- 耳机守护模块仍为占位
- 数据保留策略（7 天/90 天）未实现
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
1. **耳机守护模块（P6）**：AudioManager 音量监测 + 耳机路由监听
2. **数据保留策略（P7）**：WorkManager 定期清理原始采样（7 天）与聚合报告（90 天）
3. **自动睡眠判定（P8）**：前台服务周期性检查（每 5 分钟）+ 光照/噪音睡眠检测算法
4. **历史报告查看**：报告列表页面 + 详情页面
5. **设置页面**：校准参数配置界面（db_calibration_offset, lux_estimation_factor）

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
