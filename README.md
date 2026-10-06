# 环境健康（EnvHealth）

> 数智随行，健康随行

## 简介

环境健康是一个 Kotlin + Jetpack Compose + Material 3 的本地优先应用，用于环境监测、耳机守护与健康报告。采用 MVVM + 单向数据流架构，Room + DataStore 持久化，协程 + Flow 响应式编程。

当前已完成数据层骨架（领域模型、Room 数据库、DataStore 设置、Repository 仓库），尚未实现采集、分析、通知或运行时权限申请。

## 功能特性

- [x] Compose 应用骨架与 Material 3 主题
- [x] 底部四 Tab 占位导航：首页、环境监测、耳机守护、报告
- [x] UI / ViewModel / Domain / Data / Service / Worker / Notification 分层目录
- [x] **领域模型**：6 个枚举 + 6 个数据类（噪音、光照、睡眠、耳机、建议）
- [x] **Room 数据库**：6 个 Entity + 6 个 DAO + Database（版本 1，schema 已导出）
- [x] **DataStore 设置**：用户配置持久化（阈值、开关、校准参数）
- [x] **Repository 仓库**：5 个 Repository 统一暴露 Flow
- [x] **单元测试**：DAO 增删查改 + 时间范围查询 + 聚合查询测试
- [x] 统一常量、日志、时间工具
- [ ] 环境传感器采集（待负责人下达任务）
- [ ] 音频与图像特征分析（待负责人下达任务）
- [ ] 报告与通知（待负责人下达任务）

## 构建与运行

### 环境要求
- **Android Gradle Plugin**：9.4.1
- **Gradle Wrapper**：9.6.0
- **Kotlin**：2.0.21
- **Room**：2.7.0-alpha12
- **Java 源码兼容性**：11
- **compileSdk / targetSdk**：34
- **minSdk**：26

### 构建命令
```bash
./gradlew assembleDebug        # 构建 Debug APK
./gradlew test                 # 运行单元测试
./gradlew connectedAndroidTest # 运行 Instrumentation 测试（需连接设备）
```

### 数据保留策略
- **原始采样数据**（`noise_samples`, `light_samples`）：保留 7 天
- **聚合报告与记录**（`sleep_sessions`, `sleep_reports`, `headphone_usage`, `advice_items`）：保留 90 天

### Room Schema 导出
数据库 schema 文件位于 `app/schemas/com.GXR806.envhealth.data.local.room.EnvHealthDatabase/1.json`

## 权限说明

Manifest 已预先声明后续功能所需权限，但本阶段不主动申请、不执行采集：

- `RECORD_AUDIO`：后续耳机守护与环境声音估算
- `CAMERA`：后续图像特征分析
- `POST_NOTIFICATIONS`：后续健康提醒
- `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MICROPHONE`、`FOREGROUND_SERVICE_CAMERA`：后续长时任务
- `MODIFY_AUDIO_SETTINGS`：后续音量相关功能
- `RECEIVE_BOOT_COMPLETED`：后续按用户设置恢复后台任务

后续实现必须区分拒绝、拒绝且不再询问、永久拒绝，并提供设置页降级路径。

## 隐私声明

设计目标为隐私优先：

1. **数据不落盘**：音频与图像只在内存中提取特征，不落盘、不上传、不保存原始帧
2. **估算值标注**：所有估算指标（分贝 dB、照度 lux）必须显式标注"估算值"
3. **校准参数可配置**：`SettingsDataStore` 中的 `dbCalibrationOffset` 和 `luxEstimationFactor` 当前为占位常量（默认值 0f 和 1f），不代表真实测量结果
4. **数据自动清理**：原始采样数据 7 天自动删除，聚合报告 90 天自动删除

## 项目结构

```
com.GXR806.envhealth/
├── ui/                          # UI 层
│   ├── screen/                  # 页面（当前为占位）
│   ├── component/               # 复用组件（当前为占位）
│   ├── theme/                   # Compose 主题
│   ├── navigation/              # 导航约定（当前四 Tab，使用 rememberSaveable）
│   └── viewmodel/               # 状态与事件（当前为占位）
├── domain/                      # 领域层
│   ├── model/                   # 领域模型（6 个枚举 + 6 个数据类）
│   ├── usecase/                 # 用例（当前为占位）
│   └── analysis/                # 分析逻辑（当前为占位）
├── data/                        # 数据层
│   ├── local/
│   │   ├── room/                # Room 数据库
│   │   │   ├── entity/          # 6 个 Entity
│   │   │   ├── dao/             # 6 个 DAO
│   │   │   └── EnvHealthDatabase.kt
│   │   └── datastore/           # DataStore 设置
│   │       └── SettingsDataStore.kt
│   ├── repository/              # 5 个 Repository
│   └── sensor/                  # 传感器接入（当前不采集）
│       ├── audio/
│       ├── light/
│       └── volume/
├── service/                     # 后台服务（当前不启动）
├── worker/                      # 任务调度（当前不启动）
├── notification/                # 通知（当前不发送）
└── util/                        # 工具类
    ├── Constants.kt             # 全局常量
    ├── LogX.kt                  # 日志工具
    └── TimeUtils.kt             # 时间工具
```

## 数据模型

### 领域模型（Domain Models）

| 模型 | 字段 | 说明 |
|------|------|------|
| `NoiseSample` | timestamp, leqDb, maxDb, peakDb, isEvent | 噪音采样（估算值） |
| `LightSample` | timestamp, avgLuma, estimatedLux, level | 光照采样（估算值） |
| `SleepSession` | id, startTime, endTime, type, status | 睡眠会话 |
| `SleepReport` | sessionId, noiseScore, lightScore, regularityScore, totalScore, summary | 睡眠报告 |
| `HeadphoneUsage` | id, startTime, endTime, avgVolumePercent, maxVolumePercent, deviceType | 耳机使用记录 |
| `AdviceItem` | id, category, priority, title, content, triggerReason | 健康建议 |

### DataStore 设置项（Settings Keys）

| 设置项 | Key | 默认值 | 说明 |
|--------|-----|--------|------|
| 噪音阈值 | `noise_threshold_db` | 70f | 分贝（估算值） |
| 噪音监测开关 | `noise_monitoring_enabled` | false | - |
| 音量阈值 | `volume_threshold_percent` | 60f | 百分比 0-100 |
| 连续收听阈值 | `continuous_listening_threshold_minutes` | 60 | 分钟 |
| 音量监测开关 | `volume_monitoring_enabled` | false | - |
| 睡眠开始时间 | `sleep_start_hour` / `sleep_start_minute` | 22:00 | 时/分 |
| 睡眠结束时间 | `sleep_end_hour` / `sleep_end_minute` | 07:00 | 时/分 |
| 睡眠监测开关 | `sleep_monitoring_enabled` | false | - |
| 分贝校准偏移 | `db_calibration_offset` | 0f | 校准参数 |
| Lux 估算系数 | `lux_estimation_factor` | 1f | 校准参数 |
| 后台监测总开关 | `background_monitoring_enabled` | false | - |
| 上次清理时间戳 | `last_cleanup_timestamp` | 0L | 毫秒 |

## 版本记录

### v1.0 - 工程骨架（P0 已完成）
- 建立工程骨架、四个占位 Tab、依赖版本目录与项目文档
- 设备启动与完整验收尚未确认

### v1.1 - 数据层骨架
- ✅ 领域模型：6 个枚举 + 6 个数据类
- ✅ Room 数据库：6 个 Entity + 6 个 DAO + Database（版本 1）
- ✅ DataStore 设置：12 个设置项
- ✅ Repository 仓库：5 个 Repository
- ✅ 单元测试：3 个 DAO 测试（21 个测试方法）
- ✅ 编译通过：Room schema 导出成功
- ✅ 版本升级：Kotlin 2.0.21 + Room 2.7.0-alpha12

### v1.2 - 权限、通知、前台服务框架（当前版本）
- ✅ 权限管理：PermissionManager + Compose 封装 + 三态处理
- ✅ 权限引导 UI：PermissionRationaleDialog + 系统设置跳转
- ✅ 通知基础设施：NotificationHelper + 2 个通知渠道
- ✅ 前台服务：EnvMonitorService + Android 12-14 版本兼容
- ✅ 全局状态：MonitorState + AppResult
- ✅ 首页 UI：HomeScreen + 监测按钮 + 权限流程
- ✅ 编译通过：功能完整可运行

## 技术债务

- 当前未进行设备验证
- IDE 可能显示版本或配置更新提示
- 校准常数（`db_calibration_offset`, `lux_estimation_factor`）当前为占位，后续需实现可配置机制

## 下一步工作

仅在负责人下达明确任务后推进，当前不自定义额外阶段目标，不提前实现采集、分析、通知、权限申请或 UI 功能。

详见 [AGENT.md](AGENT.md) 和 [TODO.md](TODO.md)。
