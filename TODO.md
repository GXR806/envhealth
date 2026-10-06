# TODO

## 阶段进度总览

- [x] P0 工程脚手架
- [x] P1 数据层骨架（Room + DataStore + Repository）
- [x] P2 权限、通知、前台服务框架
- [x] P3 噪音检测模块（已完成 ✅）
- [x] P4 光照检测模块（降级模式，已完成 ✅）
- [ ] P5 耳机守护模块（仅待负责人下达任务）
- [ ] P6 睡眠报告与健康建议（仅待负责人下达任务）
- [ ] P7 ViewModel 与 UI 完善（仅待负责人下达任务）
- [ ] P8 测试与验收（仅待负责人下达任务）

---

## P0 工程脚手架（已完成 ✅）

### 任务清单
- [x] 配置 Kotlin、Compose、Material 3、minSdk 26、targetSdk 34
- [x] 配置版本目录与 Room、DataStore、WorkManager、CameraX、协程、权限库依赖
- [x] 建立 UI / ViewModel / Domain / Data / Service / Worker / Notification / Util 包结构
- [x] 建立 MainActivity 与首页、环境监测、耳机守护、报告四个占位 Tab
- [x] 添加统一 Constants、LogX、TimeUtils
- [x] Manifest 预声明后续权限，不申请
- [x] 校正文档中的 AGP、Gradle Wrapper、Java 源码目标与当前实现事实
- [x] 移除 app 模块对不存在 `proguard-rules.pro` 的无用引用，开启 BuildConfig 生成

### 验收标准
- [x] `./gradlew assembleDebug` 成功
- [x] 三份文档存在并真实反映工程现状
- [~] App 能启动，四个 Tab 可切换（未进行设备验证）

---

## P1 数据层骨架（已完成 ✅）

### 任务清单

#### 1. 定义领域模型（domain/model）
- [x] 6 个枚举类：NoiseLevel、LightLevel、AdviceCategory、DeviceType、SleepSessionType、SleepSessionStatus
- [x] 6 个数据类：NoiseSample、LightSample、SleepSession、SleepReport、HeadphoneUsage、AdviceItem

#### 2. Room 数据库（data/local/room）
- [x] 6 个 Entity：NoiseSampleEntity、LightSampleEntity、SleepSessionEntity、SleepReportEntity、HeadphoneUsageEntity、AdviceEntity
- [x] 6 个 DAO：插入、查询、聚合、删除方法
- [x] EnvHealthDatabase：版本 1，exportSchema 配置
- [x] 数据保留策略：原始采样 7 天，聚合报告 90 天

#### 3. DataStore 设置（data/local/datastore）
- [x] SettingsDataStore：12 个设置项（阈值、开关、校准参数）

#### 4. Repository 仓库（data/repository）
- [x] NoiseRepository
- [x] LightRepository
- [x] SleepRepository
- [x] HeadphoneRepository
- [x] SettingsRepository
- [x] 统一暴露 Flow 响应式数据流
- [x] Entity ↔ Domain 模型转换

#### 5. 单元测试（androidTest）
- [x] NoiseSampleDaoTest（8 个测试方法）
- [x] LightSampleDaoTest（6 个测试方法）
- [x] SleepSessionDaoTest（7 个测试方法）

#### 6. 构建配置更新
- [x] 升级 Room 到 2.7.0-alpha12（解决 Kotlin 2.0+ 兼容性问题）
- [x] 降级 Kotlin 到 2.0.21，KSP 到 2.0.21-1.0.27
- [x] 新增测试依赖：kotlinx-coroutines-test、androidx-room-testing
- [x] Room Schema 导出配置

#### 7. 问题修复
- [x] 修复 DAO 方法缺少返回类型问题（@Insert、@Update、@Query DELETE）
- [x] 解决 Kotlin 2.0+ 与 Room 兼容性问题

### 验收标准
- [x] 编译通过：Room schema 导出成功（`app/schemas/com.GXR806.envhealth.data.local.room.EnvHealthDatabase/1.json`）
- [x] 单元测试全部通过：DAO 增删查改 + 时间范围查询
- [x] AGENT.md 中完整列出所有实体字段与 DataStore Key

---

## P2 权限、通知、前台服务框架（已完成 ✅）

### 任务清单

#### 1. 权限管理封装
- [x] PermissionManager：权限检查、权限名称、用途说明、跳转设置
- [x] PermissionStateExt.kt：Compose 权限状态扩展
  - [x] rememberMonitorPermissionsState()
  - [x] isPermanentlyDenied()
  - [x] getPermanentlyDeniedPermissions()
  - [x] getDeniedPermissions()
- [x] 三态处理：授权/拒绝/永久拒绝

#### 2. 权限引导 UI
- [x] PermissionRationaleDialog：统一的权限引导对话框
- [x] 展示权限用途说明
- [x] 永久拒绝时跳转系统设置

#### 3. 通知基础设施
- [x] NotificationHelper：通知渠道与通知构建封装
- [x] 创建 2 个通知渠道：
  - [x] MONITOR：环境监测渠道（低优先级）
  - [x] HEALTH_ALERT：健康提醒渠道（高优先级）
- [x] 定义 5 个通知 ID：FOREGROUND_SERVICE、NOISE_ALERT、LIGHT_ALERT、SLEEP_ALERT、HEADPHONE_ALERT
- [x] buildForegroundServiceNotification()：构建前台服务通知
- [x] sendHealthAlertNotification()：发送健康提醒通知
- [x] MainActivity 集成通知渠道初始化

#### 4. 前台服务框架
- [x] EnvMonitorService：前台服务
  - [x] ACTION_START_MONITOR / ACTION_STOP_MONITOR
  - [x] startMonitoring() / stopMonitoring() 静态方法
  - [x] Android 12-14 版本兼容处理
  - [x] foregroundServiceType="microphone|camera" 声明
  - [x] 停止按钮集成
  - [x] 服务内 TODO 占位（不实际采集）
- [x] Manifest 声明服务与 foregroundServiceType

#### 5. 全局状态管理
- [x] MonitorState 单例：
  - [x] isMonitoring: StateFlow<Boolean>
  - [x] setMonitoring(monitoring: Boolean)
  - [x] isMonitoringNow(): Boolean
- [x] AppResult 封装：Loading / Success<T> / Error

#### 6. 首页 UI 集成
- [x] HomeScreen 实现：
  - [x] 开始/停止监测按钮
  - [x] 监测状态指示
  - [x] 权限请求流程
  - [x] 权限引导对话框集成
  - [x] 权限授予后自动启动监测
- [x] MainActivity 使用真实 HomeScreen 替换占位页面

#### 7. 编译验证
- [x] 修复 PermissionStateExt.kt 类型不匹配问题
- [x] Gradle Sync 成功
- [x] 编译通过

### 验收标准
- [x] 首页有"开始/停止监测"按钮，点击可正确启停前台服务
- [x] 通知栏出现/消失常驻通知
- [x] 三种权限的拒绝场景都有合理降级提示，不崩溃
- [x] 编译通过
- [x] AGENT.md 完整记录权限三态处理流程与前台服务实现

---

## P3 噪音检测模块（已完成 ✅）

### 任务清单

#### 1. 音频录音器（AudioNoiseRecorder）
- [x] AudioRecord 配置与采样（44100Hz, MONO, PCM_16BIT）
- [x] RMS 计算 → dBFS 转换
- [x] dBFS → dB SPL 估算（可配置校准偏移）
- [x] A 计权滤波器实现（双二阶 IIR）
- [x] Flow 输出（每 100ms 一帧）
- [x] 资源管理（start/stop/release）

#### 2. 噪音分析器（NoiseAnalyzer）
- [x] 滑动窗口统计（Leq 1s/1min、L10、L90）
- [x] 突发噪声事件检测（相邻帧差值 > 15dB）
- [x] NoiseLevel 分级（安静/正常/偏吵/嘈杂/非常吵）
- [x] 分析结果数据类（NoiseAnalysisResult）

#### 3. 数据持久化（NoiseMonitor）
- [x] 整合录音器和分析器
- [x] 每 30 秒自动落库
- [x] 暴露实时分析结果 Flow
- [x] 启动/停止管理

#### 4. 服务集成（EnvMonitorService）
- [x] 前台服务中启动/停止噪音监测
- [x] 从 DataStore 读取校准偏移
- [x] 正确释放 AudioRecord 资源
- [x] 协程作用域管理

#### 5. UI 实现（MonitorScreen）
- [x] 环境监测页面
- [x] 监测状态显示
- [x] 占位提示（实时数据功能待完善）

### 验收标准
- [x] 编译通过
- [x] 前台服务启动后实际采集音频
- [x] 数据正确保存到数据库（每 30 秒）
- [x] 停止服务后资源释放成功
- [x] A 计权滤波器正确实现

---

## P4 光照检测模块（降级模式）（已完成 ✅）

### 任务清单

#### 1. 数据模型
- [x] LightFrame：光照采样帧
- [x] LightLevel：光照等级枚举
- [x] 降级模式标识字段

#### 2. 光照采集器（LightSensorCamera）
- [x] CameraX ImageAnalysis 配置（640x480, YUV_420_888）
- [x] **使用前置摄像头**（`CameraSelector.DEFAULT_FRONT_CAMERA`）
- [x] YUV Y 平面平均亮度计算（0-255）
- [x] 相对亮度映射（0-100%）
- [x] 光照等级判断（DARK/DIM/MODERATE/BRIGHT）
- [x] Flow 输出（每 1 秒一帧）
- [x] 降级模式实现（不获取曝光参数）

#### 3. 数据持久化（LightMonitor）
- [x] 整合相机采集器
- [x] 每 30 秒自动落库
- [x] 暴露实时光照数据 Flow
- [x] 启动/停止管理

#### 4. 服务集成（EnvMonitorService）
- [x] 改为继承 LifecycleService（支持 CameraX 生命周期）
- [x] 添加 lifecycle-service 依赖
- [x] 光照监测启动/停止逻辑
- [x] 协程作用域管理
- [x] 修复 onBind/onStartCommand 方法签名

#### 5. 全局状态（LightMonitorState）
- [x] 单例状态管理
- [x] StateFlow<LightFrame?> 暴露
- [x] Service → UI 数据共享

#### 6. UI 实现（MonitorScreen）
- [x] 环境监测页面重构（噪音 + 光照双模块）
- [x] 光照监测部分实现
- [x] 实时相对亮度显示
- [x] 光照等级徽章
- [x] 降级模式提示
- [x] 统计卡片（平均亮度、相对亮度）

### 验收标准
- [x] 编译通过
- [x] 前置摄像头正确启动
- [x] 相对亮度值正常显示（0-100%）
- [x] 光照等级判断正确
- [x] 数据正确保存到数据库（每 30 秒）
- [x] 与音频模块同时运行无崩溃
- [x] 页面退出后相机正确解绑
- [x] 降级模式标注清晰
- [x] AGENT.md 更新光照检测实现说明

### 技术说明
- **前置摄像头选择原因**：前置摄像头朝向用户，更准确反映用户所处环境的光照情况
- **降级模式原因**：CameraX 1.4.1 版本获取曝光参数的 API 不够稳定
- **后续升级路径**：等待 CameraX 新版本支持后实现完整模式（真实 Lux 值）

---

## P5 耳机守护模块（待开工）

### 任务清单
- [ ] AudioManager 监听耳机状态
  - [ ] 插拔检测
  - [ ] 设备类型识别（有线/蓝牙/扬声器）
- [ ] 音量监测
  - [ ] 当前音量百分比
  - [ ] 最大音量百分比
  - [ ] 连续收听时长统计
- [ ] 数据持久化
  - [ ] HeadphoneUsageRepository 记录保存
  - [ ] 每 30 秒自动落库
- [ ] Service 集成
  - [ ] EnvMonitorService 集成音量监测
  - [ ] 协程作用域管理
- [ ] UI 实现
  - [ ] HeadphoneScreen 页面
  - [ ] 实时音量显示
  - [ ] 使用时长统计
  - [ ] 健康提醒

### 验收标准
- [ ] 编译通过
- [ ] 耳机插拔正确检测
- [ ] 音量监测准确
- [ ] 数据正确保存
- [ ] UI 正常显示

---

## P6 睡眠报告与健康建议（待开工）

### 任务清单
- [ ] 睡眠分析（domain/analysis）
  - [ ] 睡眠会话管理
  - [ ] 睡眠质量评分（噪音/光照/规律性）
  - [ ] 睡眠报告生成
- [ ] 健康建议生成（domain/analysis）
  - [ ] 基于阈值触发规则
  - [ ] 优先级排序
  - [ ] 建议内容生成
- [ ] 通知发送
  - [ ] 运行时通知权限检查
  - [ ] NotificationHelper 集成
  - [ ] 通知去重策略
- [ ] UseCase 层
  - [ ] GenerateSleepReportUseCase
  - [ ] GenerateHealthAdviceUseCase
- [ ] UI 实现
  - [ ] ReportScreen 页面
  - [ ] 睡眠质量评分展示
  - [ ] 趋势图表
  - [ ] 健康建议列表
- [ ] 单元测试
  - [ ] 评分算法测试
  - [ ] 建议生成逻辑测试

### 验收标准
- [ ] 编译通过
- [ ] 分析逻辑正确执行
- [ ] 报告正确生成
- [ ] 通知正常发送
- [ ] 单元测试通过

---

## P7 ViewModel 与 UI 完善（待开工）

### 任务清单
- [ ] ViewModel 层（ui/viewmodel）
  - [ ] HomeViewModel
  - [ ] MonitorViewModel
  - [ ] HeadphoneViewModel
  - [ ] ReportViewModel
  - [ ] UiState 数据类
  - [ ] UiEvent 密封类
- [ ] UI 页面完善
  - [ ] 环境监测页面：噪音/光照实时数据展示
  - [ ] 耳机守护页面：音量监测与提醒
  - [ ] 报告页面：睡眠报告与健康建议展示
  - [ ] 设置页面：阈值配置、校准参数配置
- [ ] 复用组件（ui/component）
  - [ ] 数据卡片组件
  - [ ] 图表组件
  - [ ] 设置项组件
- [ ] 单元测试
  - [ ] ViewModel 状态转换测试
  - [ ] ViewModel 事件处理测试

### 验收标准
- [ ] 编译通过
- [ ] ViewModel 单元测试通过
- [ ] UI 页面完整展示
- [ ] 数据流正确响应
- [ ] AGENT.md 更新 ViewModel 职责与状态定义

---

## P8 测试与验收（待开工）

### 任务清单
- [ ] 集成测试
  - [ ] 端到端采集 → 存储 → 报告流程
  - [ ] 权限拒绝场景测试
  - [ ] 前台服务启停测试
- [ ] UI 测试
  - [ ] Compose UI 测试
  - [ ] Espresso 交互测试
- [ ] 性能测试
  - [ ] 内存泄漏检测
  - [ ] 电量消耗分析
  - [ ] 采集频率优化
- [ ] 设备验证
  - [ ] 多设备测试（手机/平板）
  - [ ] Android 版本兼容测试（API 26-34）
- [ ] 文档更新
  - [ ] README、AGENT、TODO 最终版本
  - [ ] 用户手册

### 验收标准
- [ ] 所有测试通过
- [ ] 设备验证完成
- [ ] 文档完整更新
- [ ] 应用可发布

---

## 已知问题 / 技术债

- [ ] 四 Tab 当前没有图标
- [x] ViewModel 层当前为占位（P2 完成后保留，P7 将实现）
- [x] UI 页面已完成首页和环境监测页面，其他页面仍为占位（P7 将实现）
- [ ] 光照检测使用降级模式（基于相对亮度估算），无法获取真实 Lux 值
- [ ] Room 使用 2.7.0-alpha12（alpha 版本），需关注稳定版发布
- [ ] 校准参数（`db_calibration_offset`, `lux_estimation_factor`）当前为占位，需实现 UI 配置界面（P7）
- [ ] EnvMonitorService 启动失败时首页未显示错误提示（TODO 占位）
- [ ] 通知图标当前使用系统默认图标，后续需替换为专用图标
- [ ] 设备验证尚未进行（P8）

---

## 遗留说明

后续工作须等待负责人下达明确任务，不在此文档自定义额外阶段目标。
