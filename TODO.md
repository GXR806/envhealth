# 环境健康 - 项目开发清单

## 最新授权修复：主题选择
- [x] 修复重复package/仓库字段丢失/主题key缺失；设置顶部48dp三选一（跟随系统/浅色/深色）
- [x] 默认SYSTEM、DataStore持久、非法写入拒绝/非法存储回退SYSTEM；清除reset恢复SYSTEM
- [x] SettingsVM operation + maintenanceGate.write；Activity Theme外同VM订阅，所有页面/系统栏即时统一，不重建服务、不新增依赖
- [x] 既有隔离仪器设置测试扩展DARK持久/owner重开、非法拒绝/回退、resetSYSTEM（仅编译）
- [x] assembleDebug与assembleDebugAndroidTest成功；正确包四专项27通过/0失败/0跳过（首次错误维护包过滤23项后重跑）
- [~] 未重跑全套；原AdviceEngineTest四失败未改；无本轮仪器/真机通过证据
- [ ] 真机：首页设置三选→全部Tab/设置即时变化、状态栏/手势/三键导航对比度、旋转与重启持久
- [ ] 真机：SYSTEM随系统变化、强制LIGHT/DARK不随系统；清除后SYSTEM/后台false，切换不改变手动采集服务状态
- [ ] 真机：TalkBack单选语义/48dp触控/200%字体/小屏；有设备后执行隔离仪器测试


## 最新授权：视觉、Dashboard与历史可视化
- [x] Color/Type/Shape统一固定青绿蓝色、深浅主题、系统字体、Spacing；监测等级语义色
- [x] Dashboard手动DI/StateFlow：环境实时、今日耳机时长风险、最近睡眠评分；保留设置与一键手动监测、后台false
- [x] 四Tab中文图标/AnimatedContent；首页退场active失效授权pending，开启不自动采集
- [x] 只读历史联合查询/重叠耳机查询，Room schema3不变；HistoryRepository/VM维护generation/block及离页清缓存
- [x] 纯Kotlin自然日周月/半开毫秒聚合，Canvas四图，无假数据，无新增依赖
- [x] 历史范围前后浏览/回当前、列表详情/BackHandler；保留旧会话控制和最新报告建议
- [x] Empty/Error/Loading骨架三态、错误重试、数值动画、Canvas中文语义描述、新增48dp触控
- [x] HistoryAggregatorTest专项8通过（自然边界、跨月闰年DST、空/毫秒、跨午夜、堆叠与分布输入）
- [x] assembleDebug / assembleDebugAndroidTest成功
- [~] 全套69通过/4原AdviceEngineTest失败/0跳过；未改评分或旧断言
- [~] adb devices无设备；未执行connectedDebugAndroidTest，未部署/截图/生产数据操作；无现有Preview可渲染before
- [x] README/AGENT/TODO更新架构、字段、聚合、token及真实验证限制
- [ ] 真机深浅色对比度、200%字体/小屏滚动、TalkBack图表逐桶描述/按钮、触控48dp
- [ ] 真机日周月前后浏览、历史详情返回/旋转、Room错误重试、真实采样及评分一致、7天缺失与90天保留
- [ ] 真机授权弹窗中切页/后台false不开硬件、开始停止资源释放、维护清除后旧首页/图表/详情不复活
- [ ] 真机采样期间页面切换与图表性能、加载动画退出停止、全部之前P6/P7/提醒/维护验收
- [ ] P8自动睡眠不在本轮范围，仍未实现


## 最新授权阶段：设置/提醒/维护闭环
- [x] SettingsViewModel/SettingsScreen/Main首页入口，Activity范围VM、阈值/校准/原子静默时段、校验、确认清除
- [x] 后台默认false，明确授权后手动开始；开启不自动采集；关闭立即停止await；服务非sticky/generation阻断旧启动
- [x] DataMaintenance可重入clearMutex/gate、持久clearing/error、stop/join在gate外、全Room表清除/reset后台false/缓存generation/恢复仅cleanup
- [x] 服务共用AppContainer Room，录音/噪音分析/save及光照frame/save/sampling全部join，Main解绑后IO等待executor结束
- [x] 报告会话→报告→建议链maintenance gate与旧generation拒写、缓存清空；必要取消重抛，不改P5算法/旧失败断言
- [x] ReminderPolicy专项9通过、维护专项4通过、P6专项6通过（合计19）
- [x] 通知先持久节流claim后notify；预检查权限/频道、false回滚、未知结果防重复，分母仅已发送并保存事件；BOOT只任务
- [x] backup/data extraction排除database/datastore/sharedpref
- [x] 新增仅批准androidTest work-testing2.9.1；隔离Runner/WorkerFactory/runtime/Room/DataStore/clock/sink，原NoiseSampleDaoTest改runBlocking
- [x] assembleDebug与assembleDebugAndroidTest编译成功
- [~] 全套单测61通过/0跳过/4失败，仅原有AdviceEngineTest旧阈值/去重断言
- [~] ReminderIntegrationTest仍7项，扩展fake sink跨午夜/白天silent断言及cancelAndJoin旧owner后新scope同临时文件重开持久化、反馈/节流/MUTE七天边界/重放；已编译，未执行仪器测试（此前connectedDebugAndroidTest失败No connected devices!）
- [x] README/AGENT/TODO与测试/安全范围artifact交接
- [ ] 设备：首页/设置入口与Back、阈值范围与重启持久、静默跨午夜/全天、后台授权手动开始/关闭await
- [ ] 设备：实时校准、服务停止AudioRecord/CameraX/耳机回调和最终写入、重复停止/启动竞态
- [ ] 设备：并发报告与清除、清除重入/失败重试/杀进程维护恢复、旧VM/耳机pending/历史缓存不复活
- [ ] 设备：TestDriver仪器集成测试、提醒24h/反馈/权限/频道/静默、私有通知动作读回/OEM/8s超时
- [ ] 设备：BOOT仅任务/后台false仅cleanup、Room7/90天边界级联保护、备份/设备转移排除



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
- [x] HeadphoneRepository/DAO 已完成区间查询；“沿用 schema v1 无迁移”为 P6 历史记录，当前实际 schema v3，本阶段未改变数据库版本
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

## P7: 数据保留策略（代码完成，设备验收待进行）
- [x] 唯一ReminderWorker兼顾保留清理（不新增重复Worker）
- [x] 特征采样7天，聚合会话/报告/建议/耳机区间90天，24h清理节流
- [x] DAO严格截止边界，保护未结束记录，报告外键cascade
- [~] 专项纯策略测试通过，Room仪器测试已编译但无设备执行
- [ ] 设备WorkManager实际调度与保留验收

## P8: 自动睡眠判定（可选）
- [ ] 前台服务周期性检查（每 5 分钟）
- [ ] 基于光照 + 噪音的睡眠检测算法
- [ ] 自动开始/结束睡眠会话

## P9: 高级功能与优化（待定）
- [ ] 数据导出功能
- [x] 历史报告查看（本轮只读列表与评分摘要详情；真机验收见顶部）
- [~] 设置页面已实现（阈值/静默/校准/后台/清除），设备验收待进行；采集频率/保留期限不扩展
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
**最后更新**: 本轮设置/提醒/维护闭环代码及测试已编译；专项19通过，全套61通过/4项旧失败；No connected devices，仪器与设备未验收
**下一步**: 连接设备完成本轮及P6清单；P8/其他扩展须另行授权

勾选标准：[x] 已实现且相关自动化已过；[~] 部分完成或存在验证阻塞；[ ] 未实现/未验证。P0-P5沿用原验收记录，不代表本次全套自动化全绿。
