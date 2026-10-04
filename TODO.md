# TODO

## 阶段进度总览

- [~] P0 工程脚手架（设备验证与完整验收尚未完成）
- [ ] P1 数据层（仅待负责人下达任务）
- [ ] P2 环境监测采集与估算（仅待负责人下达任务）
- [ ] P3 耳机守护与前台服务（仅待负责人下达任务）
- [ ] P4 报告与通知（仅待负责人下达任务）
- [ ] P5 测试、权限三态与验收（仅待负责人下达任务）

## P0 本阶段任务

- [x] 配置 Kotlin、Compose、Material 3、minSdk 26、targetSdk 34。
- [x] 配置版本目录与 Room、DataStore、WorkManager、CameraX、协程、权限库依赖。
- [x] 建立 UI / ViewModel / Domain / Data / Service / Worker / Notification / Util 包结构。
- [x] 建立 MainActivity 与首页、环境监测、耳机守护、报告四个占位 Tab。
- [x] 添加统一 Constants、LogX、TimeUtils。
- [x] Manifest 预声明后续权限，不申请。
- [x] 校正文档中的 AGP、Gradle Wrapper、Java 源码目标与当前实现事实。
- [x] 移除 app 模块对不存在 `proguard-rules.pro` 的无用引用，开启 BuildConfig 生成以支持调试日志门控。

### P0 验收标准

- [~] `./gradlew assembleDebug` 成功且无警告级错误（已有构建记录，但本次未重复构建；IDE 更新提示仍可能存在）。
- [~] App 能启动，四个 Tab 可切换（未进行设备验证）。
- [x] 三份文档存在并真实反映工程现状。

## 遗留问题

- [ ] 四个 Tab 当前没有图标，页面仍为占位。
- [ ] 当前没有 ViewModel、Room 实体、DAO、数据库或 DataStore Key。
- [ ] 数据层、权限三态封装、后台服务、采集/分析/通知与测试均未实现。
- [ ] 后续工作须等待负责人下达明确任务，不在此文档自定义额外阶段目标。
