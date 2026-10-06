# 环境健康 - 项目开发清单

## P0: 项目脚手架与基础配置 ✅
- [x] 初始化 Android Studio 项目（Kotlin + Jetpack Compose）
- [x] 配置 Material 3 主题
- [x] 配置 Room 数据库
- [x] 权限声明（录音、相机、通知、前台服务）

## P1: 数据层 ✅
- [x] 定义 Room Entity
  - [x] NoiseSampleEntity
  - [x] LightSampleEntity
  - [x] SleepSessionEntity
  - [x] SleepReportEntity
  - [x] HeadphoneUsageEntity
  - [x] AdviceEntity
- [x] 定义 DAO 接口
- [x] 定义 Domain Model
- [x] 实现 Repository 层
  - [x] NoiseRepository
  - [x] LightRepository
  - [x] SleepRepository
  - [x] AdviceRepository

## P2: 权限管理 ✅
- [x] 封装 PermissionManager
- [x] 三态权限管理（授权/拒绝/永久拒绝）
- [x] 权限引导对话框
- [x] 跳转设置页面

## P3: 噪音监测模块 ✅
- [x] NoiseSensor 实现（AudioRecord + RMS/Leq/Max）
- [x] NoiseAnalyzer 实现（分贝分级 + 突发事件检测）
- [x] 前台服务框架（EnvMonitorService）
- [x] 数据采集流程（5s 周期 + 数据库存储）
- [x] 监测页面 UI（实时曲线图 + 分贝数值 + 噪音等级）

## P4: 光照监测模块 ✅
- [x] LightSensorCamera 实现（CameraX + 后置摄像头 + 手动曝光控制）
- [x] 真实 Lux 值计算（基于 Exposure Value + 标定常数 K=125）
- [x] 光照等级判定（黑暗/昏暗/正常/明亮/强光）
- [x] 集成到 EnvMonitorService
- [x] 监测页面 UI（实时 Lux 显示 + 光照等级）

## P5: 睡眠环境分析与建议引擎 ✅
- [x] Domain 层
  - [x] SleepSessionManager（睡眠会话管理器）
  - [x] SleepAnalyzer（睡眠分析器 - 三维评分算法）
  - [x] AdviceEngine（建议引擎 - 12+ 条规则）
- [x] UseCase 层
  - [x] GenerateSleepReportUseCase
  - [x] GenerateHealthAdviceUseCase
- [x] UI 层
  - [x] ReportViewModel
  - [x] ReportScreen（报告页面 - 圆形评分 + 三维评分 + 建议列表）
- [x] 集成到 MainActivity
- [x] 依赖注入（AppContainer + EnvHealthApplication）

## P6: 耳机守护模块（代码及专项单测完成，设备待验证）
- [x] VolumeMonitor（AudioManager、ContentObserver、AudioDeviceCallback、5s音量/30s播放轮询）
- [x] HeadphoneUsageTracker（单调计时、暂停排除、2min超时、加权音量、区间持久化）
- [x] RiskEvaluator（四级、多原因、严格大于阈值）
- [x] HeadphoneRepository/DAO 已完成区间查询，沿用 schema v1 无迁移
- [x] AppContainer 与 EnvMonitorService 生命周期接入、用户手动启动/停止
- [x] P6 生命周期红线修正：停止/销毁无 main runBlocking；Main 立即注销/CameraX解绑，IO释放AudioRecord及最后落库，独立短生命周期scope完成后cancel；共享停止信号/Main串行清理/停止期间禁止重启
- [x] 修正后重新 assembleDebug 成功、P6专项 6 passed/0 skipped/0 failed（不代表生命周期真机验收）
- [x] HeadphoneGuardViewModel + HeadphoneGuardScreen 接入四 Tab
- [x] assembleDebug 编译通过
- [x] P6 专项 JUnit 6 项通过（无新增依赖/测试基础设施）
- [~] 全套 :app:testDebugUnitTest：48通过/4失败（原有 AdviceEngineTest 旧阈值/去重断言；P5代码未改）
- [ ] 真机有线/USB/A2DP 连接及拔出立即结束
- [ ] 真机音量键、OEM ContentObserver 不通知时刷新兜底
- [ ] 第三方播放暂停/恢复、2min边界及跨午夜累计
- [ ] 首页/耳机页/通知停止后落库、注销监听、后台资源释放
- [ ] 无耳机/未启动/数据库错误状态设备验证

## P7: 数据保留策略（待实现）
- [ ] 实现 DataRetentionWorker（WorkManager）
- [ ] 原始采样数据：保留 7 天
- [ ] 聚合报告与记录：保留 90 天
- [ ] 定期清理策略

## P8: 自动睡眠判定（可选）
- [ ] 前台服务周期性检查（每 5 分钟）
- [ ] 基于光照 + 噪音的睡眠检测算法
- [ ] 自动开始/结束睡眠会话

## P9: 高级功能与优化（待定）
- [ ] 数据导出功能
- [ ] 历史报告查看
- [ ] 设置页面（采集频率、保留期限等）
- [ ] 应用图标与启动页
- [ ] 性能优化与内存管理

## 阶段性里程碑
- [x] **M1**: 完成数据层 + 权限管理（P0-P2）
- [x] **M2**: 完成噪音监测模块（P3）
- [x] **M3**: 完成光照监测模块（P4）
- [x] **M4**: 完成睡眠环境分析与建议引擎（P5）
- [~] **M5**: 耳机守护代码及专项单测完成，真机验收待进行
- [ ] **M6**: 完成数据保留策略（P7）
- [ ] **M7**: 完成所有功能并发布（P8-P9）

---

**当前版本**: v1.4  
**最后更新**: P6代码及专项测试完成；全套测试4项旧断言失败，设备未验证
**下一步**: P6设备验收；P7/P8须另行授权

勾选标准：[x] 已实现且相关自动化已过；[~] 部分完成或存在验证阻塞；[ ] 未实现/未验证。P0-P5沿用原验收记录，不代表本次全套自动化全绿。
