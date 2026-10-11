# AGENT 当前维护交接

## 应用外无消息最新诊断

已核Main/Home/Service/Guard/AppContainer/Coordinator/Store/Policy，无Home退后台取消/依赖UI订阅证据。确证代码缺陷：实时提醒一次普通异常永久结束而传感器仍运行；新增Service-owned RealtimeReminderListener，检查失败退避5s后保留下次事件/tick检查、上游失败重订阅；不重启硬件、不绕过claim，取消/Error不重试。移除takeWhile早结束，授权/后台/维护仍由Service停止及Coordinator发前重读/generation保护。未复现设备，不能断言实际根因。

设置新增真实策略检查（只请求运行Service）、只读刷新、各类型原因/时间、持久事件与claim/quietUntil区别、activeNotifications3000–3003和静默频道。notify返回/recordSent/active均不是heads-up证明；前台/静默已发送仍占24h，不退后台重发或cancel，不改channelID/DND/权限/公式/依赖/schema。clear重置进程诊断，持久详情读取持maintenanceGate/generation。

验证：debug、androidTest、full JVM102/0/0串行成功；新增仪器后androidTest再次成功、release成功。Listener JVM新增4；Reminder仪器新增3共19仅编译（simulated owner/Flow不是系统后台）。major69仍在，coverage未重跑。ui_state No running devices found，无connected/logcat/截图。安全真机步骤见README最新段：可见启动→按Home非force-stop/划掉→分别查抽屉/横幅→返回刷新状态/提供截图日志；低音量降低可调阈值，不清库绕冷却，不要求高音量。


## 本次延迟/显著性修复优先交接

原5s tick实读播放；实际音量/设备变化立即force，无关settings不频繁调用。Guard单Channel消费者tracker更新后先发布风险；completed缓存启动/60s/日期刷新，新insert返回ID加入缓存并移除pending；失败保留known+error，unknown不伪造周0。clear重置所有缓存/pending/poll/generation，stop先volatile撤资格且短同步状态发布锁避免stop后旧running复活（无锁内DB）。外部P7清理最多60s刷新；90d删除通常不影响7d风险。数据库很慢仍积压后续事件，无硬实时保证，不丢权重、不新增并行writer。

Dashboard/耳机页生产共享HIGH Banner，errorContainer/中文原因/polite/48dp，stop撤销、断开只保留合理周累计风险；不额外notify/自动调音量。Settings频道真实诊断ON_RESUME刷新+直达旧健康ID，ActivityNotFound fallback app设置；新渠道首次默认声音AudioAttributes，不修改旧用户控制行为/DND/24h7d/policy。HIGH风险与可调通知阈值口径不同。

最终串行JVM98/0/0（新增PlaybackPollPolicy3）、debug/androidTest/release成功，HTML直接确认98/0/0；UI instrument7（新增Banner1）+Reminder16等仅编译。无runningdevices、无截图/connected/Profiler，major69仍在；未重跑coverage，后文覆盖和95/6等是旧快照不代表本次。真机安全操作以README新增段为准，不清库绕冷却、不长时高音量。无新依赖/schema/SDK/P8或评分风险公式改变。


## HIGH无健康通知修复交接

已授权bug修复：Service监听耳机/设置distinct+conflate，Noise Flow sample30s + 独立30s ticker；音量/连接变化立即检查（OEM音量5s，播放识别现5s（前次30s））。实时Coordinator.run(remindOnly=true)跳过cleanup/prune，运行周累计用既有guard，不新增全库查询；Worker15min兜底不变。stop先volatile资格撤销/cancel并join；notify前重读settings/risk、running callback/generation核验；维护锁可能等待报告，但stop取消锁等待。24h/7d/静默/隐私后台/权限频道、false回滚、unknown atmostonce不变，不清lastSent/不换channel。只提醒监听异常停止，传感器继续；Reminder最小类型原因日志+设置频道独立说明。

本次assembleDebug、assembleDebugAndroidTest、testDebugUnitTest串行成功，95 passed/0 failed/0 skipped；原JaCoCo major69仍存在。Reminder仪器16（原8+实时8）仅编译，设备工具No running devices found。未重跑release/coverage，后文对应修复前证据，whole行覆盖不适用于本次增量。真机按README修复段，不清数据，首次类型或自然冷却后短时>80%实际播放验证，并核验stop/关闭/clear无新提醒。

## 状态与硬约束

代码交付 1.0.0/code1，发布与设备验收待完成。此文取代旧A/B/C/D顶部增量和互相矛盾历史，不沿用旧73/61/4/无覆盖/Room1/自动清理未实现等状态。README → 本文 → TODO；测试细节见docs/testing.md，静态边界见docs/privacy-review.md。

- 包名com.GXR806.envhealth；min26、compile/target34；Room **schema3**，已有destructive fallback，不兼容旧库可能清空，未新增迁移。
- 原始音频/图像仅内存，不落盘/上传。特征7天；已结束会话/报告/建议/耳机区间90天；时间毫秒，区间半开。
- 噪音默认偏移96dB、Lux K125为估算，媒体音量%不是dB。设置噪音偏移finite0..120，后续帧立即，Leq最多1min含旧帧。luxEstimationFactor字段尚未接入相机公式，不宣称UI可实时校准lux。
- 不改P5生产评分公式/权重、P6风险、提醒契约，不新增依赖/DI框架、SDK/schema；未经负责人明确授权不实现P8或其他阶段。
- **手动睡眠已实现，P8自动睡眠未实现**。无设备，不把编译/JVM/simulated UI当生命周期或Play认证。

## 入口与架构

手动DI AppContainer → 单Room/Settings Repository → domain；Compose + Activity VM/StateFlow；四Tab本地rememberSaveable索引/AnimatedContent，无NavHost迁移。

- MainActivity：Theme外同SettingsVM订阅；真实snapshot null加载gate，PrivacyConsentContent/PrivacyPolicyDialog滚动说明、同意持久/拒绝退出、设置只读入口。HomeScreen.active=false撤销退场pending授权启动。
- ui/component：MonitorControls、RealtimeEnvironmentCard、RecentReportCard、DashboardCards、ThemeModeOptions、HistoryChart、Empty/Error/Loading；是真实生产组件，不是仅测试复制UI。
- Home/Dashboard：用户开始/停止、后台引导、实时环境、今日耳机风险、最近报告；保存/任务错误由MonitorState.error首页显示，报告/历史错误可重试。
- SettingsScreen/SettingsViewModel：阈值、校准、原子静默HH:mm、后台、主题/隐私、确认清除。operation+maintenanceGate.write；主题SYSTEM/LIGHT/DARK，默认/非法存储回退SYSTEM，非法写入拒绝，切换不重启Activity/Service、不自动采集。
- ReportScreen/ReportViewModel：Activity范围factory；会话→分析→报告→建议完整写入链同一gate/generation，不嵌套锁。SleepSessionManager/UseCases取消异常重抛。历史只展示持久评分/summary，不把无报告外键的建议关联旧报告。
- HistoryRepository四路只读Flow + Default HistoryAggregator；HistoryVM维护blocked取消/generation重读，离页停止订阅并清replay。日自然小时含DST，周一周/月自然日；范围90日/13周/3月；Noise/Light BETWEEN传end-1ms。噪音算术均值趋势，光照finite非空lux分布计数，耳机已结束重叠区间裁剪毫秒→分钟有线USB/蓝牙堆叠，报告按会话startTime平均totalScore；null缺口不跨线/补零。
- NoiseMonitor/AudioNoiseRecorder + LightMonitor/LightSensorCamera：共享Repository与AppContainer的小批次，不另开Room。CameraX前置，曝光不可用降级；1s分析、30s特征降采样。Aweight内存复用、noise sort复用、light早期1s过滤为B已完成，本轮不重复改公式。
- VolumeMonitor/HeadphoneGuard：STREAM_MUSIC只读百分比，ContentObserver/AudioDeviceCallback，5s音量OEM兜底、5s播放状态；串行Channel/IO tracker，单调计时、暂停不计权重，>120s结束逻辑会话，拔出/停止即结束，已完成区间时间加权。USB持久WIRED；SCO/BLE不支持，多输出不保证真实路由。
- RiskEvaluator：全部严格>；>60min且当前播放音量>60%=HIGH，仅>60min=MEDIUM，>80%或7d>40h=HIGH，短时>60%=LOW，SAFE仅未触发产品阈值。

## 隐私与维护必须保持

privacyConsentVersion当前v1默认未同意。AppContainer进程SupervisorJob+IO scope明确close/onTerminate取消；冷启动先加载maintenance/同意，失败保持关闭。Application/BOOT/Worker/开启后台/Service实际采集均检查授权，清除reset撤回同意且后台false；再次同意只恢复周期清理，绝不开硬件。

START_NOT_STICKY，服务启动请求generation检查；在可见Activity用户动作后创建前台通知，再读设置才开始硬件。停止立即Main注销耳机/解绑CameraX；IO AudioRecord release finally、cancel/join recorder/analysis/save/frame/sampling，等待executor终止；完成后stopForeground/stopSelf。onDestroy不runBlocking，独立短生命周期cleanup owner不受serviceScope cancel影响，完成后cancel。重复stop保留仍需join的任务/executor，不覆盖为null。

DataMaintenance独立clearMutex串行开关/清除：封入口/持久clearing、取消work → **不持gate等待stop/join** → gate内clearAllTables/清所有pending缓存/通知/reset → generation失效旧报告/历史 → 后台false/同意撤回。NonCancellable执行段避免页面离开半清；error持久fail-closed可再清除。清除后未同意不恢复cleanup。

提醒唯一15min Work兼保留/24h清理节流；后台false但已同意仅cleanup，clearing/error或未同意不执行，BOOT仅恢复Work。先durable claim再notify；权限/频道预检查不claim，明确false回滚，未知结果保留claim可能少通知；仅接受且保存成功事件计分母。动作私有显式immutable、UUID/URI/持久事件匹配、防重放，8s超时覆盖锁等待；只用户点击降低STREAM_MUSIC且读回更低才有效。备份/转移排除database/datastore/sharedpref。

## C batch/stability最终实现

BoundedSampleBatch生产类，AppContainer分别拥有Noise/Light批次，Service实例销毁不丢失败pending。每30s选最新特征，最多3条，第三条/最老条90s触发Repository.insertSamples → suspend DAO @Insert(List)自带事务，无额外@Transaction。Mutex串行，IO+NonCancellable保护提交及成功移除；offer游标更新同取消保护。健康运行触发≤90s，调度/DB完成不是硬实时保证。

save失败保留≤3，不再接收/增长，暂停该模块硬件并首页中文错误/metadata-only日志；用户stop仍release/join并显式flush重试，下次明确手动启动先flush旧batch，失败不开硬件。无auto retry/auto start。正常stop join后Noise尾latest无burst也入批，Light同，lastQueuedTimestamp避dup。clear在全部writerjoin后discard，后续不得重试清除前记录。未持久pending在OOM/kill仍可丢，不能承诺durability。

App全局uncaught handler保存原systemHandler并finally委托，不吞致命、不上传、不自动重启、不阻塞等待；非fatal只尽力异步请求既有service cleanup，OOM/Error不创建cleanup。App/Service/cleanup/Headphone关键owner CoroutineExceptionHandler记录最小中文元数据、fail-closed或请求异步停止；Error交系统终止。没有GlobalScope/裸Thread新增；相机单分析Executor明确shutdown/await，不是无owner线程。

## 最终真实自动化与发布证据

最终testDebugUnitTest：**95 passed/0 failed/0 skipped**。SleepAnalyzer22、Advice25、Risk7、Time4、Audio4、Headphone6、History8、Maintenance4、Reminder9、Batch5、Example1；B测试调用生产类/Repository fake，不测试复制评分实现。

createDebugUnitTestCoverageReport重新生成并读取直接类HTML：Sleep107/107、Advice120/123、Risk14/14、Time2/2，四核心 **243/246=98.78%**（用户批准核心目标≥60%已达）；whole **762/5671=13.44%**（missed4909，含UI/生成DAO等，未达到whole60，非当前核心交付门槛）。路径app/build/reports/coverage/test/debug/index.html与各直接核心类html；JVM报告app/build/reports/tests/testDebugUnitTest/index.html。不可照抄前次733/5503或核心替代whole。

最终assembleDebug/assembleDebugAndroidTest/assembleRelease成功。当前Gradle9.6/AGP9.4.1/JDK25 + Java source/target11；JaCoCo0.8.12对JDK25 providers major69警告仍存在，但报告成功，不升级下载、不宣称0warnings。

仪器：16隔离ReminderIntegrationTest（含本次实时8）、6生产Compose simulated UI harness、18DAO及Example只编译，未连接设备执行。Runner普通Application，in-memoryRoom/独立临时DataStore/clock/sink/WorkerFactory不触生产库/通知/音量。无截图、Profiler、lintRelease、standalone R8 analyzer执行。

release minify/resource shrink开启，app-release-unsigned.apk，1.0.0/code1；无生产密钥、未签名安装/发布。可选签名实际读取STORE_FILE/STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD环境变量，all absent unsigned，partial/empty/missing file中文fail，不debug-key回退，不打印秘密；gitignore *.jks/*.keystore/keystore.properties，未实现localproperties读取。Proguard无应用DAO/Entity/全包keep，依赖consumer/AAPT负责库和Manifest，最小实际Worker反射构造保留；已读取release configuration与metadata，release成功不是设备混淆回归。

源码8权限；merged release额外WAKE_LOCK/ACCESS_NETWORK_STATE/signature动态receiver权限；**无INTERNET/WRITE_SETTINGS**，main静态无上传调用。不冒充网络动态审计/Play批准。

## 验收待完成与维护红线

adb devices空：connected/部署/截图/Profiler/签名安装阻塞。按TODO/交付总结操作预期验收隐私拒绝/失败/重启、后台默认false与所有入口gate、授权退场、真实采集/尾flush/DB失败有界暂停/资源、P6 OEM/暂停/路由、通知动作、7/90d清理、clear并发/失败/kill/缓存、theme/200%字体/TalkBack/对比度、release Room/Worker反射回归。

系统kill/OOM未保证最后写入，OEM播放约5s目标而非硬实时、媒体路由估算，缺曝光/低性能/过夜资源性能待Profiler。可选遗留P8/导出/专用图标/whole覆盖提升须另授权，不把它们标已实现或自动进入新阶段。CHANGELOG/DEBUG及docs/security-alignment是历史专项说明，当前验证数字以本文/README/docs/testing为准，不从旧阶段数字推出最终状态。
