# 环境健康 - Android 应用

## 项目简介

**环境健康**是一款 Android 应用，旨在帮助用户监测并改善睡眠环境质量。通过实时采集噪音、光照等环境数据，应用能够生成睡眠环境报告，并提供个性化的健康建议。

## 核心功能

### 已完成功能（v1.4）

#### 1. 环境监测
- **噪音检测**：实时监测环境噪音（Leq、Max、Peak dB），突发噪音事件检测
- **光照检测**：使用后置摄像头计算真实 Lux 值，支持光照等级判定（黑暗/昏暗/正常/明亮/强光）

#### 2. 睡眠环境分析 ⭐ 新功能
- **睡眠会话管理**：手动开始/结束睡眠监测
- **三维评分算法**：
  - 噪音评分（0-100）：基于平均分贝、峰值分贝、突发事件次数
  - 光照评分（0-100）：基于平均光照、波动、睡前趋势
  - 规律性评分（0-100）：基于近 7 天入睡时间标准差
  - 综合评分：加权和（噪音 45% + 光照 35% + 规律性 20%）
- **智能建议引擎**：基于 12+ 条规则生成个性化建议，24 小时内自动去重

#### 3. 报告页面 ⭐ 新功能
- **圆形评分图表**：直观展示综合评分（0-100）
- **三维评分展示**：噪音/光照/规律性详细评分
- **建议列表**：可展开/收起的健康建议卡片

### 待完成功能

- 耳机守护模块（P6）
- 数据保留策略（P7）
- 自动睡眠判定（P8）
- 历史报告查看
- 设置页面

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
- ✅ 实现智能建议引擎（12+ 条规则，24 小时去重）
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

## 文档

- [TODO.md](TODO.md)：项目开发清单
- [AGENT.md](AGENT.md)：AI Agent 上下文交接文档

## 贡献指南

本项目当前为内部开发阶段，暂不接受外部贡献。如有问题或建议，请联系项目负责人。

## 许可证

版权所有 © 2024 GXR806. 保留所有权利。

---

**最后更新**：2024 年（P5 睡眠环境分析与建议引擎已完成）  
**下一步**：P6 耳机守护模块
