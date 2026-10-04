# 环境健康（EnvHealth）

> 数智随行，健康随行

## 简介

环境健康是一个 Kotlin + Jetpack Compose + Material 3 的本地优先应用骨架，后续用于展示环境监测、耳机守护与健康报告。本阶段仅提供工程结构与四个占位页面，未实现采集、分析、通知或运行时权限申请。

## 功能特性

- [x] Compose 应用骨架与 Material 3 主题
- [x] 底部四 Tab 占位导航：首页、环境监测、耳机守护、报告
- [x] UI / ViewModel / Domain / Data / Service / Worker / Notification 分层目录
- [x] Room、DataStore、WorkManager、CameraX 等后续模块依赖配置
- [x] 统一常量、日志、时间工具
- [ ] 环境传感器采集（待负责人下达任务）
- [ ] 音频与图像特征分析（待负责人下达任务）
- [ ] 报告与通知（待负责人下达任务）

## 构建与运行

- Android Gradle Plugin：9.4.1（版本目录）
- Gradle Wrapper：9.6.0（`gradle/wrapper/gradle-wrapper.properties`）
- Java 源码与目标兼容性：11（`app/build.gradle.kts` 的 compileOptions；不等同于当前 IDE/JDK 运行环境）
- Gradle daemon 配置：`gradle.properties` 当前仅配置 JVM 参数、编码及配置缓存等项目设置，未声明固定 JDK。
- compileSdk / targetSdk：34
- minSdk：26
- 构建命令：`./gradlew assembleDebug`
- 当前未进行设备验证，因此不能据此声明 App 已启动或验收通过。IDE 可能仍显示版本或配置更新提示。

## 权限说明

Manifest 已预先声明后续功能所需权限，但本阶段不主动申请、不执行采集：

- `RECORD_AUDIO`：后续耳机守护与环境声音估算。
- `CAMERA`：后续图像特征分析。
- `POST_NOTIFICATIONS`：后续健康提醒。
- `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MICROPHONE`、`FOREGROUND_SERVICE_CAMERA`：后续长时任务。
- `MODIFY_AUDIO_SETTINGS`：后续音量相关功能。
- `RECEIVE_BOOT_COMPLETED`：后续按用户设置恢复后台任务。

后续实现必须区分拒绝、拒绝且不再询问、永久拒绝，并提供设置页降级路径。

## 隐私声明

设计目标为隐私优先：音频与图像只在内存中提取特征，不落盘、不上传、不保存原始帧。所有估算指标必须显式标注“估算值”。当前 `Constants` 中的校准值仅为占位常量，不代表真实测量结果，也尚未实现可配置机制。

## 项目结构

`com.GXR806.envhealth` 下按 UI、ViewModel、Domain、Data、Service、Worker、Notification、Util 分层；当前导航使用 `rememberSaveable` 保存 Tab 索引，不是 `NavHost`。当前没有 ViewModel、Room 实体、DAO、数据库或 DataStore Key；具体职责与状态见 [AGENT.md](AGENT.md)。

## 版本记录

- 1.0：建立工程骨架、四个占位 Tab、依赖版本目录与项目文档；设备启动与完整验收尚未确认。
