# 测试策略与最终真实结果

## 应用外无消息最新验证（优先于旧快照）

本次串行assembleDebug、assembleDebugAndroidTest、testDebugUnitTest成功，工具返回102 passed/0 failed/0 skipped；追加仪器后assembleDebugAndroidTest再次成功，assembleRelease成功。RealtimeReminderListenerTest新增4：无UI subscriber模拟服务flow/5s fresh与running、检查异常继续、上游重订阅、cancel不重试；调用生产listener/policy，无Android后台保证。ReminderIntegrationTest新增3共19：无UI owner恢复且共享冷却、runtime旧读数/停止拒绝→模拟5s fresh接受、频道阻断原因与false rollback不造event。仪器只编译，ui_state无设备，未connected/logcat/截图/真实通知。JaCoCo major69仍在，未重跑coverage。不将notify正常返回、持久event或系统active列表当横幅可见证明。README最新段为安全真机验收步骤。


## 本次提醒延迟/显著性最新证据（优先于下方旧快照）

最终串行testDebugUnitTest、assembleDebug、assembleDebugAndroidTest、assembleRelease均成功；直接读取JVM index.html确认 **98 tests/0 failures/0 skipped**。新增PlaybackPollPolicyTest3调用生产5s规则：边界、实际音量变化与start/device force、无关settings不频繁poll。新增生产HeadphoneRiskBanner simulated UI1（ProductionUiHarnessTest共7）：HIGH→SAFE、无播放周风险、stop撤Banner，仅编译未设备执行；Reminder16不变。No running devices found；未执行connected/截图/Profiler/慢Repository instrument，不能称生命周期/实际heads-up/低延迟通过。单消费者DB慢仍可能阻塞后续事件；60s全历史查询与缓存更新正确性需真机验收。

major69 JaCoCo/JDK25 instrumentation日志仍在，未加依赖/升级。此次未重跑coverage，下方覆盖数值只为旧快照，不代表新增生产代码分母或覆盖；下方95与UI6为前次结果。release本次已重建成功，未签名安装/发布。


## HIGH通知修复增量验证

本次串行assembleDebug、assembleDebugAndroidTest、testDebugUnitTest成功：95 passed/0 failed/0 skipped；JaCoCo major69告警仍存在，无新依赖。ReminderIntegrationTest由8增至16项，新增实时>80音量/rapid updates/Worker并发仅一次、24h+7d静音/静默、channelblocked不claim+false rollback、stop/后台false/privacyfalse、锁等待generation撤销、claim后设置撤销、cancel锁等待及时结束、claim后guard撤销。调用生产Coordinator与隔离fake runtime/clock/sink/store，无真实通知。**16项仅编译，无设备执行**；设备工具返回No running devices found，不宣称Service真机生命周期通过。

本次未重跑coverage/assembleRelease；下列coverage与release证据为修复前快照，whole分母不适用于新增代码。真实设备验证步骤见README修复段，保留lastSent/quietUntil，不靠清库绕过节流。

## 范围与隔离

保持manualDI、JUnit4/coroutines-test已有栈；无新依赖/Mockk/Hilt/Robolectric/截图框架。AGP内置JaCoCo0.8.12开启debug unit coverage。生产算法测试使用真实SleepAnalyzer/AdviceEngine/RiskEvaluator/TimeUtils，通过fake DAO/Repository边界输入，不复制评分实现。

IsolatedTestRunner普通Application不启动生产DI/周期调度；ReminderIntegrationTest使用in-memoryRoom、随机临时DataStore、明确owner scope、fake clock/sink/WorkerFactory/TestDriver。不访问生产库、拍摄/录音/改系统音量/发真实通知；tearDown cancelAndJoin owner/取消work/关闭Room。DAO测试runBlocking只存在测试，不在生产Main。

## 最终执行

最终串行Gradle调用：testDebugUnitTest、createDebugUnitTestCoverageReport、assembleDebug、assembleDebugAndroidTest、assembleRelease全部Build finished successfully。**95 passed/0 failed/0 skipped**：

| JVM类 | 数量 |
|---|---:|
| SleepAnalyzerTest | 22 |
| AdviceEngineTest | 25 |
| RiskEvaluatorTest | 7 |
| TimeUtilsTest | 4 |
| AudioOptimizationTest | 4 |
| HeadphoneGuardTest | 6 |
| HistoryAggregatorTest | 8 |
| MaintenanceTest | 4 |
| ReminderPolicyTest | 9 |
| BoundedSampleBatchTest | 5 |
| ExampleUnitTest | 1 |

Batch5覆盖第三条事务、90s及尾flush、失败保留3/拒增长/显式重试、写入中取消不拆commit/成功移除、join后discard不复活；这是生产batch的测试，不代表真实传感器stop尾帧/OEM已验证。

当前JDK25 providers可能产生JaCoCo0.8.12 unsupported major69 instrument日志；**任务和HTML报告仍成功**，未升级下载，不称0warning。

## 重新生成的直接生产核心行覆盖

基于HTML Total的Lines total减missed，不取Instructions百分比，不把嵌套数据类或测试替身计入核心。

- SleepAnalyzer：missed0/total107 → 107/107=100%。
- AdviceEngine：missed3/total123 → 120/123=97.56%。
- RiskEvaluator：missed0/total14 → 14/14=100%。
- TimeUtils：missed0/total2 → 2/2=100%。
- 四直接核心加权 **243/246=98.78%**，用户批准≥60%核心目标已达。
- whole-module：missed4909/total5671 → **762/5671=13.44%**；包含Compose/生成DAO/服务等未执行部分，不达到whole60，不以核心替代。

报告：`app/build/reports/tests/testDebugUnitTest/index.html`；`app/build/reports/coverage/test/debug/index.html`；核心直接类 `com.GXR806.envhealth.domain.analysis/SleepAnalyzer.html`、`AdviceEngine.html`、`RiskEvaluator.html`，`com.GXR806.envhealth.util/TimeUtils.html`（均位于coverage根下）。前次733/5503不再作为最终数字。

## 仪器仅编译，未设备执行

- ReminderIntegrationTest **16项**（原8+上述实时8）：同意持久/reset撤回/Worker gate；周期/24h/后台关闭cleanup；真实临时Settings/Theme/quiet/feedback跨owner重开；保留边界/ongoing/cascade；权限不claim；clear join顺序/reentrant/stale；false/unknown sink；maintenance/action replay。
- ProductionUiHarnessTest **6项**：渲染真实MonitorControls/RealtimeEnvironmentCard/RecentReportCard、隐私同意/拒绝/null加载/只读说明、主题三选；授权/采集/报告状态simulated，不能称硬件端到端。
- NoiseDAO7、LightDAO5、SleepSessionDAO6及Example1：APK编译。编译不代表Room设备执行通过。

`adb devices`空列表，本轮未run connected/deploy/截图/Profiler，未run lintRelease/standaloneR8量化脚本。用户设备接入后 `gradlew.bat :app:connectedDebugAndroidTest`；发布前需签名release安装验证Room/Worker反射及所有关键流程。具体操作/预期见TODO及交付总结，不自动推进P8。
