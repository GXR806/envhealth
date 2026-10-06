# AGENT 上下文交接文档

## 1. 当前阶段与进度

**P4 光照检测模块已完成**：实现实时环境光照检测（支持真实 Lux 值计算），使用后置摄像头采集 YUV 亮度数据并通过 Camera2 Interop 获取曝光参数（SENSOR_EXPOSURE_TIME 和 SENSOR_SENSITIVITY），应用工程近似公式计算真实 Lux 值。降级模式作为回退方案。编译通过，功能完整可运行。

**已完成模块**：P0 工程脚手架、P1 数据层骨架、P2 权限通知服务框架、P3 噪音检测模块、P4 光照检测模块。

**当前未完成**：设备启动验证、耳机守护模块、睡眠报告模块、分析逻辑、ViewModel 完整实现。

## 2. 架构总览

```text
UI（Compose screen/component/navigation/theme）
        ↓ 单向数据流
ViewModel（ui/viewmodel）
        ↓
Domain（model/usecase/analysis）
        ↓
Data（repository/local/sensor）
        ↓
Room / DataStore / CameraX / 音频与光照传感器
        ↓
Service（EnvMonitorService 前台服务）
```

Service、Worker、Notification 作为后台任务边界，必须由用户可停止并释放资源。

## 3. 目录结构

- **`ui/screen`**：**HomeScreen 已完成** - 首页监测按钮与权限流程；**MonitorScreen 已完成** - 环境监测页面占位；其他页面仍为占位。
- `ui/component`：复用组件；当前为占位。
- `ui/theme`：Compose 主题。
- `ui/navigation`：导航约定；当前四 Tab 由 MainActivity 使用 `rememberSaveable` 保存索引管理，未使用 NavHost。
- `ui/viewmodel`：状态与事件；当前为占位，无 ViewModel 实现。
- **`domain/model`**：**已完成** - 6 个枚举 + 6 个数据类。
- `domain/usecase`、`domain/analysis`：用例、分析；当前为占位。
- **`data/local/room`**：**已完成** - 6 个 Entity + 6 个 DAO + Database（版本 1，schema 已导出）。
- **`data/local/datastore`**：**已完成** - SettingsDataStore 用户设置持久化。
- **`data/repository`**：**已完成** - 5 个 Repository 统一暴露 Flow。
- **`data/sensor/audio`**：**已完成** - AudioNoiseRecorder（录音器）+ NoiseAnalyzer（分析器）+ NoiseMonitor（管理器）。
- `data/sensor/light`、`volume`：传感器接入；当前不采集。
- **`service`**：**EnvMonitorService 已完成** - 前台服务框架，已集成噪音监测。
- **`notification`**：**NotificationHelper 已完成** - 通知渠道与通知构建封装。
- `worker`：任务调度；当前不启动。
- **`util`**：Constants、LogX、TimeUtils、**MonitorState**（全局状态）、**AppResult**（结果封装）。
- **`util/permission`**：**PermissionManager** + Compose 权限扩展 + **PermissionRationaleDialog**。

## 4. 已完成模块

### 4.1 应用入口
- [MainActivity.kt](app/src/main/java/com/GXR806/envhealth/MainActivity.kt)：Compose 容器与四 Tab 切换，**已集成通知渠道初始化**，首页使用真实 HomeScreen。

### 4.2 首页屏幕（ui/screen）
- [`HomeScreen.kt`](app/src/main/java/com/GXR806/envhealth/ui/screen/HomeScreen.kt)：
  - 开始/停止监测按钮
  - 监测状态指示（监测中/未监测）
  - 权限请求流程（三态处理：授权/拒绝/永久拒绝）
  - 权限引导对话框集成
  - 权限授予后自动启动监测

### 4.3 权限管理框架（util/permission）

**核心类**：
- [`PermissionManager`](app/src/main/java/com/GXR806/envhealth/util/permission/PermissionManager.kt)：
  - 定义监测所需权限（RECORD_AUDIO、CAMERA、POST_NOTIFICATIONS）
  - 权限检查、权限中文名称、用途说明
  - 打开应用设置页面
  - PermissionResult 封装（AllGranted/PartiallyDenied/PermanentlyDenied）

**Compose 扩展**：
- [`PermissionStateExt.kt`](app/src/main/java/com/GXR806/envhealth/util/permission/PermissionStateExt.kt)：
  - `rememberMonitorPermissionsState()`：记住监测权限状态
  - `isPermanentlyDenied()`：判断是否被永久拒绝
  - `getPermanentlyDeniedPermissions()`：获取永久拒绝权限列表
  - `getDeniedPermissions()`：获取可再次请求的拒绝权限列表

**权限引导 UI**：
- [`PermissionRationaleDialog.kt`](app/src/main/java/com/GXR806/envhealth/util/permission/PermissionRationaleDialog.kt)：
  - 统一的权限引导对话框
  - 展示权限用途说明
  - 永久拒绝时引导跳转系统设置

### 4.4 通知基础设施（notification）

**核心类**：
- [`NotificationHelper`](app/src/main/java/com/GXR806/envhealth/notification/NotificationHelper.kt)：
  - **通知渠道**：
    - `MONITOR`：环境监测渠道（低优先级，不发声）
    - `HEALTH_ALERT`：健康提醒渠道（高优先级，发声提醒）
  - **通知 ID**：
    - `FOREGROUND_SERVICE = 1001`：前台服务通知
    - `NOISE_ALERT = 2001`：噪音异常提醒
    - `LIGHT_ALERT = 2002`：光照异常提醒
    - `SLEEP_ALERT = 2003`：睡眠质量提醒
    - `HEADPHONE_ALERT = 2004`：耳机音量提醒
  - **方法**：
    - `createNotificationChannels()`：创建通知渠道（在 MainActivity.onCreate 中调用）
    - `buildForegroundServiceNotification()`：构建前台服务通知
    - `sendHealthAlertNotification()`：发送健康提醒通知

### 4.5 前台服务框架（service）

**核心类**：
- [`EnvMonitorService`](app/src/main/java/com/GXR806/envhealth/service/EnvMonitorService.kt)：
  - **Action**：
    - `ACTION_START_MONITOR`：启动监测
    - `ACTION_STOP_MONITOR`：停止监测
  - **静态方法**：
    - `startMonitoring(context)`：启动监测服务
    - `stopMonitoring(context)`：停止监测服务
  - **Android 版本兼容**：
    - Android 12 (API 31+)：前台服务启动限制处理
    - Android 13 (API 33+)：POST_NOTIFICATIONS 权限处理
    - Android 14 (API 34+)：foregroundServiceType 声明（microphone|camera）
  - **当前状态**：服务内不采集，仅有 TODO 占位
  - **TODO**：
    - 启动音频采集（AudioRecord）
    - 启动光照采集（CameraX）
    - 启动音量监测（AudioManager）
    - 停止时释放资源

**Manifest 声明**：
```xml
<service
    android:name=".service.EnvMonitorService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="microphone|camera" />
```

### 4.6 全局状态管理（util）

**MonitorState 单例**：
- [`MonitorState.kt`](app/src/main/java/com/GXR806/envhealth/util/MonitorState.kt)：
  - `isMonitoring: StateFlow<Boolean>`：监测状态 Flow
  - `setMonitoring(monitoring: Boolean)`：设置监测状态
  - `isMonitoringNow(): Boolean`：获取当前状态（非响应式）

**AppResult 封装**：
- [`AppResult.kt`](app/src/main/java/com/GXR806/envhealth/util/AppResult.kt)：
  - `Loading`：加载中
  - `Success<T>(data: T)`：成功
  - `Error(exception: Throwable, message: String)`：失败
  - 辅助属性：`isSuccess`、`isError`、`isLoading`
  - 辅助方法：`getOrNull()`、`getErrorOrNull()`

### 4.7 领域模型层（domain/model）

**6 个枚举类**：
- [`NoiseLevel`](app/src/main/java/com/GXR806/envhealth/domain/model/NoiseLevel.kt)：QUIET, NORMAL, MODERATE, LOUD, VERY_LOUD
- [`LightLevel`](app/src/main/java/com/GXR806/envhealth/domain/model/LightLevel.kt)：DARK, DIM, NORMAL_INDOOR, BRIGHT_INDOOR, VERY_BRIGHT
- [`AdviceCategory`](app/src/main/java/com/GXR806/envhealth/domain/model/AdviceCategory.kt)：NOISE, LIGHT, SLEEP, HEADPHONE, GENERAL
- [`DeviceType`](app/src/main/java/com/GXR806/envhealth/domain/model/DeviceType.kt)：WIRED, BLUETOOTH, SPEAKER, UNKNOWN
- [`SleepSessionType`](app/src/main/java/com/GXR806/envhealth/domain/model/SleepSessionType.kt)：MANUAL, AUTO
- [`SleepSessionStatus`](app/src/main/java/com/GXR806/envhealth/domain/model/SleepSessionStatus.kt)：ONGOING, COMPLETED, CANCELLED

**6 个数据类**：
- [`NoiseSample`](app/src/main/java/com/GXR806/envhealth/domain/model/NoiseSample.kt)：噪音采样（timestamp: Long, leqDb: Float, maxDb: Float, peakDb: Float, isEvent: Boolean）
- [`LightSample`](app/src/main/java/com/GXR806/envhealth/domain/model/LightSample.kt)：光照采样（timestamp: Long, avgLuma: Float, estimatedLux: Float, level: LightLevel）
- [`SleepSession`](app/src/main/java/com/GXR806/envhealth/domain/model/SleepSession.kt)：睡眠会话（id: Long, startTime: Long, endTime: Long?, type: SleepSessionType, status: SleepSessionStatus）
- [`SleepReport`](app/src/main/java/com/GXR806/envhealth/domain/model/SleepReport.kt)：睡眠报告（sessionId: Long, noiseScore: Float, lightScore: Float, regularityScore: Float, totalScore: Float, summary: String）
- [`HeadphoneUsage`](app/src/main/java/com/GXR806/envhealth/domain/model/HeadphoneUsage.kt)：耳机使用记录（id: Long, startTime: Long, endTime: Long?, avgVolumePercent: Float, maxVolumePercent: Float, deviceType: DeviceType）
- [`AdviceItem`](app/src/main/java/com/GXR806/envhealth/domain/model/AdviceItem.kt)：健康建议（id: Long, category: AdviceCategory, priority: Int, title: String, content: String, triggerReason: String, createdAt: Long）

### 4.8 Room 数据库层（data/local/room）

**6 个 Entity + 6 个 DAO + Database**（详见 P1 阶段文档）

### 4.9 DataStore 设置层（data/local/datastore）

**SettingsDataStore**（详见 P1 阶段文档）

### 4.10 Repository 仓库层（data/repository）

**5 个 Repository**（详见 P1 阶段文档）

### 4.11 统一工具
- [Constants.kt](app/src/main/java/com/GXR806/envhealth/util/Constants.kt)：全局常量
- [LogX.kt](app/src/main/java/com/GXR806/envhealth/util/LogX.kt)：日志工具
- [TimeUtils.kt](app/src/main/java/com/GXR806/envhealth/util/TimeUtils.kt)：时间工具
- [MonitorState.kt](app/src/main/java/com/GXR806/envhealth/util/MonitorState.kt)：全局监测状态
- [AppResult.kt](app/src/main/java/com/GXR806/envhealth/util/AppResult.kt)：结果封装

### 4.12 工程依赖
- **Kotlin**：2.0.21
- **Room**：2.7.0-alpha12
- **KSP**：2.0.21-1.0.27
- **Compose BOM**：2024.12.01
- **Accompanist Permissions**：0.36.0
- 其他依赖详见 P1 阶段文档

### 4.13 权限声明
Manifest 已预声明所有权限，当前首页会主动请求 RECORD_AUDIO、CAMERA、POST_NOTIFICATIONS。

### 4.14 构建配置
- AGP 9.4.1、Gradle Wrapper 9.6.0
- Java 11 源码兼容目标
- compileSdk / targetSdk：34
- minSdk：26

## 5. 权限三态处理流程

### 5.1 三态定义
1. **已授权**：用户已授予权限，可正常使用功能
2. **已拒绝（可再次请求）**：用户拒绝但未勾选"不再询问"，可再次弹窗请求
3. **永久拒绝**：用户拒绝并勾选"不再询问"，需引导跳转系统设置

### 5.2 权限请求流程
```text
用户点击"开始监测" 
  ↓
检查权限状态
  ↓ 未授权
显示 PermissionRationaleDialog（说明用途）
  ↓ 用户确认
请求权限 / 跳转设置
  ↓
权限授予后自动启动服务
```

### 5.3 关键实现
- **首次请求**：直接显示系统权限对话框
- **拒绝后再次请求**：先显示 PermissionRationaleDialog 说明用途，用户确认后再弹系统对话框
- **永久拒绝**：显示 PermissionRationaleDialog 引导跳转系统设置页面

## 6. 前台服务启动限制处理

### 6.1 Android 12+ 前台服务启动限制
从 Android 12 (API 31) 开始，应用在以下情况才能启动前台服务：
1. 应用在前台（Activity 可见）
2. 应用有 `SYSTEM_ALERT_WINDOW` 权限
3. 应用刚收到通知点击
4. 应用有其他特殊权限

### 6.2 当前实现
- `EnvMonitorService.startMonitoring()` 方法在启动失败时返回 `false`
- 首页 HomeScreen 在启动失败时有 TODO 占位（需显示错误提示）
- **建议**：确保只在用户可见时（Activity 前台）启动服务

### 6.3 Camera 类型前台服务限制
- Android 14 (API 34+) Camera 类型前台服务不能从后台启动
- 当前实现：在 Activity 中点击按钮启动，满足前台条件
- **注意**：后续如需从 Worker 或 Receiver 启动，需降级为仅 microphone 类型

## 7. 全局约定

- 包名：`com.GXR806.envhealth`
- Kotlin + Compose + Material 3 + MVVM + 单向数据流
- 代码注释、UI 文案、文档使用中文；代码函数命名使用符合 Kotlin 习惯的英文
- 时间跨层与持久化统一使用毫秒时间戳；由 TimeUtils 统一获取
- 全局常量位于 `util/Constants.kt`，日志通过 LogX；正式版本不输出日志，日志不得包含原始采集内容
- 禁止主线程 IO、GlobalScope、裸 Thread
- 所有采集任务必须可停止并释放 AudioRecord、CameraX 等资源
- 音频、图像只做内存特征计算，不落盘、不上传、不保存原始帧

## 8. 关键常量与校准参数

- `DEFAULT_DB_CALIBRATION_OFFSET_DB = 0f`：分贝估算校准偏移占位
- `DEFAULT_LUX_ESTIMATION_FACTOR = 1f`：lux 估算系数占位
- 当前没有实际采集与估算；以上常量不代表真实测量结论
- 校准参数已在 SettingsDataStore 中定义为可配置项（`db_calibration_offset`, `lux_estimation_factor`），但当前 UI 未实现配置界面

## 9. 已知问题 / 技术债

- 四 Tab 当前没有图标
- ViewModel 层当前为占位，无实现
- UI 层当前只有首页 HomeScreen 和环境监测页面 MonitorScreen 实现，其他页面仍为占位
- 光照检测使用降级模式（基于相对亮度估算），无法获取真实 Lux 值
- IDE 可能存在版本或配置更新提示
- Room 使用 2.7.0-alpha12（alpha 版本），后续需关注稳定版发布
- EnvMonitorService 启动失败时首页未显示错误提示（TODO 占位）
- 通知图标当前使用系统默认图标，后续需替换为专用图标
- 设备验证尚未进行
- 校准偏移参数需要用户通过标准设备校准

## 10. 关键实现说明

### 10.1 权限三态判断逻辑
```kotlin
// 永久拒绝判断
fun MultiplePermissionsState.isPermanentlyDenied(): Boolean {
    return permissions.any { perm ->
        when (val status = perm.status) {
            is PermissionStatus.Denied -> !status.shouldShowRationale
            else -> false
        }
    }
}
```

### 10.2 前台服务启动流程
```kotlin
// 1. 首页点击"开始监测"
// 2. 检查权限，未授权则请求
// 3. 权限授予后调用 EnvMonitorService.startMonitoring(context)
// 4. 服务内调用 startForeground() 显示通知
// 5. MonitorState.setMonitoring(true) 更新全局状态
// 6. UI 通过 Flow 响应状态变化
```

### 10.3 通知渠道初始化
```kotlin
// MainActivity.onCreate() 中调用
NotificationHelper.createNotificationChannels(this)
```

## 10.4 光照检测模块（P4 已完成）

### 核心实现
- **LightSensorCamera**：CameraX 光照采集（支持真实 Lux 值计算）
  - **摄像头选择**：使用后置摄像头（`CameraSelector.DEFAULT_BACK_CAMERA`）
    - 原因：符合 P4 原始需求，后置摄像头光线传感器更准确
  - 分辨率：640x480
  - 格式：YUV_420_888
  - 采样频率：1 秒 1 帧
  - **曝光参数获取**：通过 Camera2 Interop `SessionCaptureCallback` 监听 `SENSOR_EXPOSURE_TIME`（ns）和 `SENSOR_SENSITIVITY`（ISO）
  - **Lux 估算公式**（工程近似）：
    ```
    lux ≈ (avgLuma / 255) × K / (exposureTimeSec × (iso / 100))
    ```
    其中 K 为校准常数（默认 125，经实测调整）
  - **光照等级判断**：基于真实 Lux 值分级
    - DARK：< 5 lux
    - DIM：5-50 lux
    - MODERATE：50-300 lux
    - BRIGHT：> 300 lux
  - **降级模式**：当曝光参数不可用时，自动回退到相对亮度模式（0-100%）
  - **校准常数调整**：K 值从初始 2500 降至 125，修复室内开灯环境显示 50,000 lux 的问题（现显示 250-500 lux）
- **LightMonitor**：协调器，每 30 秒保存数据
- **LightMonitorState**：全局状态单例
- **MonitorScreen**：UI 集成，显示噪音 + 光照双模块

### Service 集成
- `EnvMonitorService` 改为继承 `LifecycleService`（支持 CameraX 生命周期绑定）
- 光照监测启动/停止逻辑已集成

## 11. 绝对不能改动的部分

- 包名 `com.GXR806.envhealth`、minSdk 26、targetSdk 34
- 隐私约束：原始音频与图像不得落盘或上传
- 估算指标必须标注"估算值"；校准常数可配置
- 数据保留策略：原始采样 7 天，聚合报告 90 天
- 时间统一使用毫秒时间戳
- 前台服务必须可被用户停止并释放资源
- 未经 Tech Lead 确认不得新增依赖或实现后续阶段功能

## 12. 后续工作入口

**已完成阶段**：
- ✅ P0 工程脚手架
- ✅ P1 数据层骨架
- ✅ P2 权限、通知、前台服务框架
- ✅ P3 噪音检测模块
- ✅ P4 光照检测模块（降级模式）

**下一阶段建议**：
1. **耳机守护模块**：AudioManager 音量监测 + 耳机路由监听
2. **睡眠报告模块**：睡眠会话管理 + 质量评分
3. **健康建议模块**：基于阈值的建议生成
4. **ViewModel 层**：实现状态管理与事件处理
5. **UI 层**：实现其他功能页面（耳机守护、报告）
6. **设置页面**：校准参数配置界面

仅在负责人下达明确任务后推进；当前不自定义额外阶段目标。

## 13. 新 AI Agent 接手指南

1. **阅读顺序**：README.md → AGENT.md → TODO.md
2. **理解架构**：查看第 2 节架构总览与第 3 节目录结构
3. **熟悉权限流程**：查看第 5 节权限三态处理流程
4. **理解前台服务**：查看第 6 节前台服务启动限制处理
5. **查看代码**：
   - 首页：`ui/screen/HomeScreen.kt`
   - 权限管理：`util/permission/` 目录
   - 通知：`notification/NotificationHelper.kt`
   - 前台服务：`service/EnvMonitorService.kt`
   - 全局状态：`util/MonitorState.kt`
6. **运行测试**：
   - 编译：`./gradlew assembleDebug`
   - 设备测试：部署到真机，点击首页"开始监测"按钮，验证权限请求与前台服务启动
7. **开始任务**：等待 Tech Lead 下达明确任务后开工
