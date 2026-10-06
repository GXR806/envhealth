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

## P6: 耳机守护模块（待实现）
- [ ] HeadphoneSensor 实现（AudioManager + BroadcastReceiver）
- [ ] HeadphoneAnalyzer 实现（音量分级 + 佩戴时长统计）
- [ ] 数据采集流程（集成到 EnvMonitorService）
- [ ] 耳机守护页面 UI

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
- [ ] **M5**: 完成耳机守护模块（P6）
- [ ] **M6**: 完成数据保留策略（P7）
- [ ] **M7**: 完成所有功能并发布（P8-P9）

---

**当前版本**: v1.4  
**最后更新**: P5 睡眠环境分析与建议引擎已完成  
**下一步**: P6 耳机守护模块
