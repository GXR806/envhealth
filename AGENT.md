# AGENT 上下文交接文档

## 1. 当前阶段与进度

P0 工程骨架已建立：包含 Compose 容器、四个占位 Tab、依赖配置与后续分层目录。当前未完成设备启动验证，不能称为已验收；本阶段未实现采集、分析、通知或运行时权限申请。

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
```

Service、Worker、Notification 作为后台任务边界，必须由用户可停止并释放资源。

## 3. 目录结构

- `ui/screen`：页面；当前为占位。
- `ui/component`：复用组件；当前为占位。
- `ui/theme`：Compose 主题。
- `ui/navigation`：导航约定；当前四 Tab 由 MainActivity 使用 `rememberSaveable` 保存索引管理，未使用 NavHost。
- `ui/viewmodel`：状态与事件；当前为占位，无 ViewModel 实现。
- `domain/model`、`usecase`、`analysis`：领域模型、用例、分析；当前为占位。
- `data/local/room`、`datastore`：本地持久化；当前为占位。
- `data/repository`：数据仓库；当前为占位。
- `data/sensor/audio`、`light`、`volume`：传感器接入；当前不采集。
- `service`、`worker`、`notification`：后台服务、任务与通知；当前不启动。
- `util`：Constants、LogX、TimeUtils。

## 4. 已完成模块

- 应用入口：[MainActivity.kt](app/src/main/java/com/GXR806/envhealth/MainActivity.kt)：Compose 容器与四 Tab 占位切换；函数命名使用英文 PascalCase，UI 文案仍为中文。
- 统一工具：[Constants.kt](app/src/main/java/com/GXR806/envhealth/util/Constants.kt)、LogX.kt、TimeUtils.kt。
- 工程依赖：Compose、Navigation Compose、Lifecycle ViewModel Compose、Room、DataStore、WorkManager、CameraX、协程、Accompanist Permissions 已加入版本目录与 app 模块，但当前业务代码未使用这些后续能力。
- 权限声明：仅 Manifest 预声明，不申请。
- 构建配置：AGP 9.4.1、Gradle Wrapper 9.6.0；Java 11 是源码兼容目标，不代表 IDE/JDK 运行版本。

## 5. 数据模型

当前无 Room 实体、DAO、数据库，无 DataStore Key，无枚举定义。后续新增时必须先登记字段与含义。

## 6. 全局约定

- 包名：`com.GXR806.envhealth`。
- Kotlin + Compose + Material 3 + MVVM + 单向数据流。
- 代码注释、UI 文案、文档使用中文；代码函数命名使用符合 Kotlin 习惯的英文。
- 时间跨层与持久化统一使用毫秒时间戳；由 TimeUtils 统一获取。
- 全局常量位于 `util/Constants.kt`，日志通过 LogX；正式版本不输出日志，日志不得包含原始采集内容。
- 禁止主线程 IO、GlobalScope、裸 Thread。
- 所有采集任务必须可停止并释放 AudioRecord、CameraX 等资源。
- 音频、图像只做内存特征计算，不落盘、不上传、不保存原始帧。

## 7. 关键常量与校准参数

- `DEFAULT_DB_CALIBRATION_OFFSET_DB = 0f`：分贝估算校准偏移占位。
- `DEFAULT_LUX_ESTIMATION_FACTOR = 1f`：lux 估算系数占位。
- 当前没有实际采集与估算；以上常量不代表真实测量结论，当前也未实现可配置机制。

## 8. 已知问题 / 技术债

- 四 Tab 当前没有图标，页面仅用于验证切换；尚未进行设备验证。
- 当前没有 ViewModel、Room 实体、DAO、数据库或 DataStore Key。
- 数据层、权限三态封装、后台服务与测试尚未实现。
- IDE 可能存在版本或配置更新提示，不能表述为无警告。

## 9. 绝对不能改动的部分

- 包名 `com.GXR806.envhealth`、minSdk 26、targetSdk 34。
- 隐私约束：原始音频与图像不得落盘或上传。
- 估算指标必须标注“估算值”；校准常数目前仅是占位，后续如实现配置必须可配置。
- 未经 Tech Lead 确认不得新增依赖或实现后续阶段功能。

## 10. 后续工作

仅在负责人下达明确任务后推进；当前不自定义额外阶段目标，不提前实现采集、分析、通知、权限申请或数据层功能。
