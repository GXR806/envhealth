# 环境健康 1.0.0

Android 本地环境健康应用。**代码交付完成，实体机及发布验收待完成**，不是医疗诊断或安全声级认证。versionCode 1；包名 `com.GXR806.envhealth`；minSdk 26、compile/targetSdk 34；Room schema 3。

## 实际功能

- 四 Tab Compose + Material 3：首页 Dashboard、环境监测、耳机守护、报告（含历史）。设置从首页进入。
- 首次中文可滚动隐私说明，真实设置加载 gate；同意政策 v1 持久化，拒绝退出。设置提供只读隐私说明。清除全部数据撤回同意。
- 麦克风内存计算噪音特征；前置 CameraX 内存估算 lux，缺少曝光参数时显示相对亮度。**dB/lux 均为估算值**，不保存原始音频/图像，不上传。
- 手动睡眠会话开始/结束/取消、三维评分及规则建议；规律性数据不足显示缺失，不伪造评分。**P8 自动睡眠判定未实现**。
- 日/周/月真实历史图表、报告列表及持久评分摘要详情。历史建议没有报告外键，不冒充关联建议。
- 有线/USB/A2DP 耳机监听、媒体音量百分比、播放区间时长/加权音量及多原因风险。百分比不是 dB；7天40小时不是 WHO 安全保证。
- 设置阈值、原子静默时间、噪音校准、后台授权、三种主题、可重入清除；本地提醒/反馈节流与 7/90 天保留。
- 有界批量特征写入与用户可停止的前台服务，维护先 stop/join，再清库/失效缓存，不自动重启。

## 最新状态与 launcher 图标（本次仅图标授权）

用户已反馈通知问题验证成功、连接设备检测已完成；这是用户提供的验证范围，不代表全部生命周期、19项Reminder仪器、Release安装或Profiler均已通过。后文“无设备”是对应历史轮次的工具结果，不应当作当前连接状态。

- 原稿 `artwork/app_icon.png` 原样保留（1245×1250），不覆盖、不重绘。Gradle 原生 Copy 任务 `generateLauncherArtwork` 复制到 `app/build/generated/launcherArtwork/res/drawable-nodpi/ic_app_artwork.png`；该文件是可重建产物，不提交 build 目录。缺少原稿会中文报错，输入变更由Gradle追踪。
- AGP Variant Sources API 注册各variant生成资源并携带依赖，`preBuild`也依赖复制任务；无需额外依赖、图像转换工具或配置期图片解析。
- 普通/圆形自适应图标均用完整原稿：108dp层中心约66dp留白布局，横向微调保持1245:1250比例，蓝紫渐变填充外围。移除旧机器人foreground及其monochrome引用，不把彩色原稿当单色主题图标；不同launcher遮罩/动画仍需设备外观验收。minSdk26使用anydpi adaptive XML；旧密度webp保留但不作为当前adaptive入口。
- Manifest既有icon/roundIcon引用不变；**未修改通知small icon、通知策略、Service、权限、评分、SDK或schema**。
- 本次串行 `:app:assembleDebug`、`:app:assembleRelease` 成功；本次未执行JVM/connected/Profiler/截图，release构建不是签名安装验证。
- 设备更新同签名debug APK后回桌面及应用抽屉查看；若仍缓存旧图标，先移除桌面快捷方式再重新添加，必要时重启桌面/设备。**不要清应用数据或卸载来刷新图标**；不要清除桌面数据导致布局丢失。

## 本次应用外无消息诊断与修复（历史轮次）

已读 Main/Home/Service/Guard/Runtime/Coordinator/Store/Policy：未发现按Home或退出页面会停止已运行提醒；Service/Guard自有owner且直接订阅实时Flow，不依赖UI subscriber。可确证缺陷是实时提醒的一次普通异常会永久结束监听，传感器/Banner仍可运行；改为Service-owned listener，单次检查失败保留claim并在后续事件/tick重查（失败退避5s），上游失败退避重订阅；移除takeWhile终止，授权/维护/stop仍由Service与Coordinator最终gate封闭。未复现用户设备，不能断言异常就是该机根因。5s播放轮询未改，模拟5s更新不是后台硬实时证明。

设置新增“系统提醒状态/最近发出与阻断原因”：各类型本进程最近检查原因、持久notify事件、节流占位/7天静音截止、普通与静默渠道、当前系统active健康通知3000–3003。“检查健康提醒”只请求已运行Service执行原策略，不启动硬件、不绕过24h/7d/静默；“刷新系统与持久记录”只读。notify正常返回+recordSent成功不等于系统展示；active列表也不等于横幅，缺失可能被用户打开/清除或系统移除。未知结果只有claim无event不会自动重发。前台或静默已发送占同一冷却，按Home不再次通知。不删除/换渠道，不改DND/权限/公式，不在退后台cancel通知。

本次串行assembleDebug、assembleDebugAndroidTest、testDebugUnitTest **102 passed/0 failed/0 skipped**成功；追加仪器后assembleDebugAndroidTest再次成功，assembleRelease成功。新增Listener JVM4、Reminder仪器3（Reminder共19）仅编译；simulated owner/Flow不是Android后台/系统通知测试。JaCoCo/JDK25 major69仍在，未重跑coverage。ui_state返回No running devices found，未部署/connected/logcat/截图。

真机步骤：保留数据/既有通知/权限；同意并开启后台后在可见首页手动开始，低音量实际播放支持耳机，可暂时将可调通知阈值降到低于当前百分比（测试后恢复，不必制造HIGH/高音量）。先记录设置页状态，按系统Home退后台（不是force-stop、划掉任务或系统“停止应用”），等待约5s播放识别/检查，分别检查通知抽屉和悬浮横幅。返回设置点击刷新；冷却中不应重发，静默发到无声渠道；停止/关闭后台/clear后不新增。若仍无消息，提供设备/Android版本、状态页完整截图和Reminder/EnvMonitorService最小日志（不含音频），核对fresh/running、权限/频道、静默、24h/7d及未知claim。不能仅凭Banner HIGH或前台1001通知认定健康通知已发。

## 本次提醒延迟与显著性修复

播放识别使用原5秒tick，无新任务；实际媒体音量/设备变化立即实读播放。Guard先发布当前状态/风险再保存与读取，已完成历史缓存每60秒或日期边界刷新，成功写入以生成ID更新缓存避免重复；未知历史提示错误，保留已知累计，不把失败当周零。停止立即撤资格/撤Banner，清除重置缓存与generation。单消费者不丢权重事件；历史/保存很慢仍可能阻塞后续事件，不保证硬实时，未做Profiler或慢库仪器验证。

首页及耳机页共享errorContainer HIGH Banner（中文原因、polite语义、48dp通知设置入口）；通知静默/24h不会隐藏应用内风险。固定风险规则与可调/适应通知策略是不同口径，HIGH不表示每次发通知。首次健康渠道显式默认声音和通知AudioAttributes；旧ID/用户重要性/声音不改。设置显示权限、应用开关、频道重要性/声音/DND，ON_RESUME重读，直达当前健康渠道并兼容应用通知设置fallback。HIGH且有声音也不保证OEM悬浮横幅。

本次最终串行testDebugUnitTest **98 passed/0 failed/0 skipped**、assembleDebug、assembleDebugAndroidTest、assembleRelease成功。新增生产轮询规则JVM3项；生产Banner UI新增1项（UI共7），Reminder16不变，仪器仅编译。设备工具No running devices found；JaCoCo/JDK25 major69仍在。未重跑coverage，后述覆盖数字是本次之前快照，不可当新增代码覆盖。

验收不清数据、不戴耳机短时播放后>80%立即降低，不连续高音量：核验播放通常约5秒识别、HIGH Banner、通知状态及系统设置返回刷新；首次VOLUME或自然24h/7d到期才检查通知。低音量+临时降低可调提醒阈值可安全测试系统通知路径（不用于制造固定HIGH）。停止/断开应撤当前Banner，后台关闭/clear不得新提醒；保留DND、静默、冷却与隐私后台门控。不删除/换渠道，不清lastSent。

## 前次HIGH无健康提醒修复（历史证据）

原实现只有15分钟周期 Worker，没有服务实时提醒。现监测服务持续监听耳机/设置变化，音量与连接/播放变化立即检查（OEM音量兜底约5秒，播放识别现约5秒；前次为约30秒）；噪音Flow每30秒采样，独立30秒ticker检查连续时长/周累计，不等待下一次15分钟Work。音频帧不逐帧获取维护锁或写DataStore。实时使用现有HeadphoneGuard运行周累计，不额外扫90天库、不做清理/prune；Worker仍负责保留与兜底。

服务stop先撤销实时资格/cancel，cleanup加入join；发送前重读设置、风险并核验running/维护generation。报告链占维护锁时检查可能等待锁，但stop可取消等待。共用原Coordinator/store/sink，保留同类型24h节流、7天静音、适应阈值（音量上限80）、严格>80严重音量覆盖、静默渠道与系统DND；不重置历史节流、不改渠道ID。提醒监听错误只停止监听并记录最小日志，不停止传感器数据；未知发送结果仍保留claim。

设置增加说明：前台‘环境监测’渠道与‘健康提醒’/‘静默健康提醒’独立；有前台通知并不证明健康频道开放。日志Reminder仅记录类型/原因：未风险、24小时节流、7天静音、权限/应用通知/健康频道关闭、静默/健康渠道接受、停止/设置变化、明确发送失败回滚。系统接受不等于用户看到或听到。

本次串行assembleDebug、assembleDebugAndroidTest、testDebugUnitTest成功，JVM **95/0/0**，JaCoCo/JDK25 major69告警仍存在。ReminderIntegrationTest现16项（原8+新增实时8）**只编译未执行**；设备工具返回No running devices found。本次未重新运行coverage/release，下面覆盖与release为修复前证据，whole覆盖不代表本次代码。

真机验收：不清全部数据；选择从未发送过的VOLUME类型或等待自然24h/7d到期，打开后台授权、同意、手动监测、连接支持耳机并实际播放，短时将音量调至>80%（随后立即降低，不长时高音量），检查健康通知/日志。rapid updates只应出现1次；stop/后台关闭/清除后不得新增实时提醒。若无通知先按日志检查系统健康渠道、静默窗口和既有节流；静默/DND期间可能只出现在通知栏。仍需设备验证生命周期、真实调度和OEM行为。

## 首次使用与停止

1. 打开应用，等待设置加载；阅读并同意隐私说明。拒绝退出；同意**仅恢复本地周期清理，不开硬件**。
2. 首页 → 设置与数据管理 → 明确打开后台监测授权。默认关闭；打开仍不自动采集。
3. 回到首页，手动点击开始，按用途授予麦克风、相机及 Android 13+ 通知权限。拒绝可重试，永久拒绝进入系统设置；授权中离开首页会撤销待启动意图。
4. 环境页查看估算数据，耳机页查看连接/播放/音量与时长，Dashboard 可打开最近报告。
5. 报告页手动开始睡眠会话，再手动结束生成报告/建议，或取消；睡眠会话不等同于硬件采集开关，使用前确认监测正在运行。
6. 首页/耳机页/前台通知停止，或关闭设置后台授权：立即注销监听/解绑相机，挂起等待 AudioRecord 释放与最终写入，不阻塞主线程。普通停止不撤回隐私同意。
7. 清除全部数据需确认：停止并 join writer、清 Room/缓存/通知、重置设置，后台关闭、主题 SYSTEM、隐私同意撤回；重新同意后也不会自动采集。维护失败保持关闭，可重复清除恢复。

## 采样、批次与保留

- 实时特征分析保持既有频率（光照图像分析最多1秒1帧）。Noise/Light 每30秒选取最新特征，最多3条批次，第三条或最老 pending 达90秒提交 `Repository.insertSamples` → suspend Room `@Insert(List)`。List Insert 自身生成事务，不额外叠加 `@Transaction`。
- 批次 Mutex 串行，提交/成功移除在 IO + NonCancellable 完成；stop 可取消计时任务但 join 等待已进入的写入，再 flush 残留。Noise 尾帧即使无 burst 也保存；Noise/Light 按最新时间戳避免重复加入。
- 保存失败保留每模块最多3条特征，暂停该模块采集并显示首页中文错误/最小日志；无无限 pending、无后台重试或静默丢弃。普通停止及下次明确手动启动可重试旧批次，重试失败不开硬件。失败批次由进程 AppContainer 拥有，服务销毁后仍保留；清除在全部 writer join 后 discard，避免复活。
- **90秒是健康运行时的提交触发上限，不是系统调度或数据库完成的硬实时保证**；失败暂停期间 pending 可能超过90秒。进程强杀/崩溃会丢未提交内存特征，不能保证最终落库。
- 持久特征采样保留7天，已结束会话/报告/建议/耳机区间90天。唯一15分钟 ReminderWorker 每24小时至多清理一次，保护未结束记录。后台关闭但已同意时仅清理；未同意或 maintenance clearing/error 时不执行；BOOT 仅恢复任务。
- 历史范围本地自然日、周一自然周、自然月，毫秒 `[start,end)`，包含 DST；噪音为特征算术均值趋势而非重新计算声学 Leq，光照柱为采样数量而非时长，耳机仅已保存结束区间按桶裁剪，报告按会话开始时间分桶平均总分。缺失 null 不填零、不跨缺口连线；月图可能因7天保留而缺失。

## 校准与可访问性

默认噪音偏移 **96dB**，是产品估算默认值，不是标准声压实测标定。设置接受有限数值0–120（非法/空/NaN/Infinity显示错误不保存），后续帧即时应用；Leq窗口最多1分钟含旧校准帧，历史不改写。Lux 使用工程近似常数 K=125；DataStore 虽有 lux factor 字段，当前相机公式未使用该字段，不能宣称设置可实时校准 lux；需照度计验收，后续接入须另行授权。不改评分公式及权重。

主题跟随系统/浅色/深色，DataStore 持久，切换不重建服务、不启动采集；清除恢复跟随系统。使用系统 sp 字体、48dp以上新增点击目标、文字等级和中文图表语义、空/加载/错误状态。**200%字体、小屏、TalkBack、系统栏对比度尚未实体机验证**。

## 权限与静态隐私边界

源码显式声明8项：

| 权限 | 用途 |
|---|---|
| RECORD_AUDIO | 内存计算噪音特征，原始音频不落盘 |
| CAMERA | 前置相机光照估算，图像不落盘 |
| POST_NOTIFICATIONS | Android13+ 前台/健康提醒通知 |
| FOREGROUND_SERVICE | 用户手动启动的可停止前台服务 |
| FOREGROUND_SERVICE_MICROPHONE | Android14麦克风前台类型 |
| FOREGROUND_SERVICE_CAMERA | Android14相机前台类型 |
| MODIFY_AUDIO_SETTINGS | 用户点击提醒降音量动作，只降低 STREAM_MUSIC 并读回验证 |
| RECEIVE_BOOT_COMPLETED | 仅恢复已同意且无维护错误的 WorkManager任务，不开硬件 |

release merged Manifest 另有依赖引入的 WAKE_LOCK、ACCESS_NETWORK_STATE，以及签名级 DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION；网络状态检测不是互联网访问授权。源码及最终 merged release Manifest **无 INTERNET、无 WRITE_SETTINGS**；`app/src/main` 静态搜索未发现 HTTP/URLConnection/OkHttp/Retrofit/WebView 上传调用。不是运行时抓包、第三方系统安全保证或 Play 审核通过。数据库/DataStore/sharedpref 已排除备份和设备转移，OEM恢复仍需实测。详见 [隐私静态复核](docs/privacy-review.md)。

## 架构与关键入口

`Compose screen/component/theme → Activity ViewModel + StateFlow → domain → Repository → Room/DataStore`；手动 DI [AppContainer](app/src/main/java/com/GXR806/envhealth/di/AppContainer.kt)，无新DI框架。

- [MainActivity](app/src/main/java/com/GXR806/envhealth/MainActivity.kt)：真实隐私加载 gate、四 Tab/主题、Activity范围VM。
- `ui/component`：PrivacyConsentContent/PrivacyPolicyDialog、MonitorControls、DashboardCards、ThemeModeOptions、HistoryChart、统一状态组件。
- `ui/viewmodel`：Settings/Dashboard/History/Report/HeadphoneGuard；报告完整写入链遵守 maintenance gate/generation。
- `domain/analysis`：SleepAnalyzer、AdviceEngine、RiskEvaluator、HistoryAggregator、ReminderPolicy；P5权重不变（规律性足够45/35/20，不足60/40）。
- `data/sensor`：[BoundedSampleBatch](app/src/main/java/com/GXR806/envhealth/data/sensor/BoundedSampleBatch.kt)、NoiseMonitor/AudioNoiseRecorder、LightMonitor/LightSensorCamera、VolumeMonitor/HeadphoneGuard。
- `worker`：DataMaintenance、MaintenanceGate、ReminderWorker/Coordinator、BootReceiver；通知动作私有显式immutable、UUID/URI/持久事件匹配、8秒超时、防重放。
- [EnvMonitorService](app/src/main/java/com/GXR806/envhealth/service/EnvMonitorService.kt)：START_NOT_STICKY，实际硬件启动前重读授权、维护/generation；共享Room；独立短生命周期cleanup scope完成后cancel。App/Service/耳机owner有CoroutineExceptionHandler，fatal Error委托系统，不吞致命异常、不自动重启。全局handler不等待release，非fatal只尽力异步请求停止；OOM不创建cleanup，最终写入不保证。

## 构建、测试和证据

当前工具环境：Android Studio 2026.2.1、Gradle 9.6.0、AGP 9.4.1、Gradle运行JDK25；Java source/target **11仅是源码字节码级别，不代表AGP可用JDK11运行**。JaCoCo0.8.12对部分JDK25 provider class major69报告instrumentation告警，本轮不升级、不下载新库，任务和生产核心HTML报告仍成功生成。CI需验证所用Gradle/AGP/JDK工具兼容性，不把这些告警写成0警告。

Windows 使用 `gradlew.bat`，其他环境使用 `./gradlew`：

```text
gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:createDebugUnitTestCoverageReport
gradlew.bat :app:assembleDebug
gradlew.bat :app:assembleDebugAndroidTest
gradlew.bat :app:assembleRelease
```

最终本轮以上任务均成功。JVM **95 passed / 0 failed / 0 skipped**（原90 + 新批次5）。详情 [测试策略及真实结果](docs/testing.md)。仪器8项隔离集成+6项生产组件simulated UI harness及DAO测试仅编译，无设备执行；simulated不是硬件E2E。

最新重新生成报告行覆盖（不使用旧交接数字）：

| 口径 | 覆盖/总行 | 比例 |
|---|---:|---:|
| 生产 SleepAnalyzer 直接类 | 107/107 | 100% |
| 生产 AdviceEngine 直接类 | 120/123 | 97.56% |
| 生产 RiskEvaluator 直接类 | 14/14 | 100% |
| 生产 TimeUtils 直接类 | 2/2 | 100% |
| 四核心直接类行数加权 | **243/246** | **98.78%** |
| whole-module，含UI/生成DAO等 | **762/5671**（missed4909） | **13.44%** |

用户批准的核心≥60%已达到；不把核心口径替代whole、不称whole达到60%，whole提升为可选后续目标。HTML `app/build/reports/coverage/test/debug/index.html`，直接核心见其 `com.GXR806.envhealth.domain.analysis/{SleepAnalyzer,AdviceEngine,RiskEvaluator}.html` 和 `com.GXR806.envhealth.util/TimeUtils.html`。JVM结果 `app/build/reports/tests/testDebugUnitTest/index.html`。

`adb devices`空列表。本轮未run connected、deploy、截图、Profiler、lintRelease或 standalone R8 analyzer，不称仪器通过/性能改善百分比/0 lint warnings。无真机截图，**此处不放伪图/生成图**；待设备拍摄隐私、Dashboard、浅深主题、历史、耳机和报告。

## Release与可选签名

release minify/resource shrink开启；成功产物为 `app/build/outputs/apk/release/app-release-unsigned.apk`，metadata确认1.0.0/code1。R8输出 `app/build/outputs/mapping/release/{mapping.txt,configuration.txt}`；合并Manifest `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`。未签名APK不能直接安装；没有用户生产密钥，**未签名、未安装、未发布**。

[Gradle](app/build.gradle.kts) 已实际实现可选环境签名：同时设置 `STORE_FILE`（本地keystore绝对路径或项目根相对路径）、`STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。全部缺省构建unsigned；部分/空值或文件不存在时明确报错；不打印秘密，不回退debug key。通过私有终端/CI secret store注入后运行assembleRelease，由负责人验证证书/安装；本轮未设置真实秘密/未实测签名安装。`*.jks`/`*.keystore`/`keystore.properties`已gitignore；只实现环境变量读取，**不宣称读取local properties**。不要在README/仓库/日志保存密码。

[ProGuard规则](app/proguard-rules.pro) 无应用全包/DAO/Entity广泛keep；依赖consumer rules处理Room/Compose/协程，Manifest入口由AAPT处理，明确最小Worker类名+Context/WorkerParameters构造keep（consumer中已有基础构造规则）。已读取release合并configuration确认consumer存在；release成功含R8输出，不等同于混淆后设备回归。r8-analyzer skill仅作静态研究参考，未运行量化脚本/安装Python/制造评分报告。

## FAQ

- **同意或打开后台为什么没采集？** 这是有意的隐私约束；必须回首页明确手动开始并授予权限。BOOT/Worker永不开硬件。
- **没有数据/历史有缺口？** 请确认已手动运行、权限/相机可用；持久图表最多存在批次延迟，超过7天采样自然缺失。缺口不是0。
- **数值不像专业仪表？** dB/lux为工程估算，麦克风/曝光/OEM差异会影响，需标准设备校准；媒体音量百分比不是声压。
- **保存错误怎么办？** 该模块暂停，首页显示错误，最多3条保留内存；停止/明确手动重启重试或确认清除。勿把进程重启当恢复未持久数据。
- **停止或崩溃最后数据一定有吗？** 普通停止会join/flush；系统强杀、OOM、硬件/数据库故障不能保证最后写入。
- **通知没出现？** 检查通知权限、系统/频道、静默、24h节流、7天静音；未知发送结果保留claim可能少一次通知，不盲目重复。
- **耳机路由/暂停不准？** isMusicActive轮询约5秒、音量5秒OEM兜底；多连接选首个支持输出，不保证实际媒体路由；SCO/BLE/助听不支持。

## 验收入口

[TODO](TODO.md)保留每项未验收及原因；[AGENT](AGENT.md)为当前唯一维护交接；[交付总结](C:/Users/ZzhFt/AppData/Local/Google/AndroidStudio2026.2.1/projects/envhealth.5c0fd961/.artifacts/4c5deffb-8fed-4605-8f47-7f9e2f94d11f/project_delivery_summary.artifact.md)含六项交付、主要文件清单、设备操作与预期。下一步是连接实体机验收，不擅自启动P8/导出/新依赖阶段。
