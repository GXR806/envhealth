# Best Practices and Security Alignment Update

## 安全对齐范围与优先级

- **High：清除后写入/缓存复活防护**。MaintenanceGate blocked+generation，DataMaintenance两阶段清除，stop/join在写锁外，Room全表清除+DataStore reset+耳机pending/旧历史读缓存清空，报告链串行化。失败clearing/error持久，重启fail-closed，用户可重复确认恢复。
- **High：通知副作用与重放**。私有HealthActionReceiver，显式immutable动作PendingIntent；UUID/枚举/URI/持久事件身份+24h窗口，先claimAction再降音量，读回降低才有效，永不提高。goAsync8秒包括锁等待，取消不吞；MainActivity不处理或转发外部nested Intent，BOOT受保护系统广播只恢复Work，不启动硬件。
- **High：通知发送节流原子边界**。先DataStore持久claim后notify；未知结果/进程死亡保留节流，可能丢一次通知；权限/频道预检查失败不claim，明确send=false回滚；分母只有系统接受且保存成功事件，不假称跨系统事务/恰好一次。
- **High：私有数据与后台授权恢复**。AutoBackup、cloud-backup、device-transfer排除database（包括WorkManager库）、file/datastore、sharedpref；reset后台false，旧启动generation失效，START_NOT_STICKY，必须从可见页面明确授权后手动开始。允许旧手动采集持续直到明确关闭，关闭await后返UI。
- **Medium：生命周期资源**。共享AppContainer Room，writer/采集cancel+join；CameraX Main解绑后IO等待executor；错误隔离释放，不阻塞Main，不使用GlobalScope，不存原始音视频或上传。系统强杀仍可能损失未落库数据，OEM硬件释放需设备验证。

## 实际修改/新增范围（包括工作树已有本阶段与P6实现，不只基础设施）

以app/src/main/java/com/GXR806/envhealth/为代码路径前缀；此清单记录实际工作树差异，不声称全部均由本轮创建。

- 构建/入口：app/build.gradle.kts、app/src/main/AndroidManifest.xml、EnvHealthApplication.kt、MainActivity.kt。
- 设置/存储：data/local/datastore/SettingsDataStore.kt、SettingsSnapshot.kt；data/repository/SettingsRepository.kt、ReminderStore.kt。
- 保留DAO：data/local/room/dao/HeadphoneUsageDao.kt、SleepSessionDao.kt；实际Room schema v3，本轮无升级。
- 采集停止/校准：data/sensor/audio/AudioNoiseRecorder.kt、NoiseMonitor.kt；data/sensor/light/LightMonitor.kt、LightSensorCamera.kt；data/sensor/volume/HeadphoneGuard.kt、VolumeMonitor.kt。
- 分析：domain/analysis/HeadphoneUsageTracker.kt、RiskEvaluator.kt、ReminderPolicy.kt；P5 SleepAnalyzer/AdviceEngine未改。
- 会话/用例取消：domain/sleep/SleepSessionManager.kt；domain/usecase/GenerateHealthAdviceUseCase.kt、GenerateSleepReportUseCase.kt（取消重抛，无算法修改）。
- 通知：notification/NotificationHelper.kt、HealthActionReceiver.kt。
- 后台边界：service/EnvMonitorService.kt；worker/BootReceiver.kt、DataMaintenance.kt、ReminderCoordinator.kt、ReminderWorker.kt。
- UI：ui/screen/HomeScreen.kt、SettingsScreen.kt、HeadphoneGuardScreen.kt；ui/viewmodel/SettingsViewModel.kt、ReportViewModel.kt、HeadphoneGuardViewModel.kt。
- 备份：app/src/main/res/xml/backup_rules.xml、data_extraction_rules.xml。
- JVM测试：app/src/test/java/com/GXR806/envhealth/domain/analysis/HeadphoneGuardTest.kt、ReminderPolicyTest.kt、MaintenanceTest.kt。
- 仪器：app/src/androidTest/java/com/GXR806/envhealth/IsolatedTestRunner.kt、ReminderIntegrationTest.kt、data/local/room/dao/NoiseSampleDaoTest.kt。
- 三件套与审计：README.md、AGENT.md、TODO.md、docs/testing.md、docs/security-alignment.md。
- 新依赖**只有批准androidTest work-testing2.9.1**；其余手动DI/JUnit/Compose/Room/DataStore/WorkManager沿用现有库，无Hilt或androidTest coroutines-test。

## 精确配置统一diff（由git实际输出；对比仓库HEAD包含此前阶段改动）

```diff
diff --git a/app/build.gradle.kts b/app/build.gradle.kts
--- a/app/build.gradle.kts
+++ b/app/build.gradle.kts
@@ -14,7 +14,7 @@ android {
         targetSdk = 34
         versionCode = 1
         versionName = "1.0"
-        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
+        testInstrumentationRunner = "com.GXR806.envhealth.IsolatedTestRunner"
     }

     buildTypes {
@@ -67,6 +67,7 @@ dependencies {
     testImplementation(libs.junit)
     testImplementation(libs.kotlinx.coroutines.test)
     testImplementation(libs.androidx.room.testing)
+    androidTestImplementation("androidx.work:work-testing:2.9.1")
     androidTestImplementation(platform(libs.androidx.compose.bom))
     androidTestImplementation(libs.androidx.compose.ui.test.junit4)
     androidTestImplementation(libs.androidx.espresso.core)
diff --git a/app/src/main/AndroidManifest.xml b/app/src/main/AndroidManifest.xml
--- a/app/src/main/AndroidManifest.xml
+++ b/app/src/main/AndroidManifest.xml
@@ -37,6 +37,11 @@
             </intent-filter>
         </activity>

+        <receiver android:name=".notification.HealthActionReceiver" android:exported="false" />
+        <receiver android:name=".worker.BootReceiver" android:exported="false">
+            <intent-filter><action android:name="android.intent.action.BOOT_COMPLETED" /></intent-filter>
+        </receiver>
+
         <!-- 环境监测前台服务 -->
         <service
             android:name=".service.EnvMonitorService"
diff --git a/app/src/main/res/xml/backup_rules.xml b/app/src/main/res/xml/backup_rules.xml
--- a/app/src/main/res/xml/backup_rules.xml
+++ b/app/src/main/res/xml/backup_rules.xml
@@ -1,13 +1,6 @@
-<?xml version="1.0" encoding="utf-8"?><!--
-   Sample backup rules file; uncomment and customize as necessary.
-   See https://developer.android.com/guide/topics/data/autobackup
-   for details.
-   Note: This file is ignored for devices older than API 31
-   See https://developer.android.com/about/versions/12/backup-restore
--->
+<?xml version="1.0" encoding="utf-8"?>
 <full-backup-content>
-    <!--
-   <include domain="sharedpref" path="."/>
-   <exclude domain="sharedpref" path="device.xml"/>
--->
-</full-backup-content>
\ No newline at end of file
+    <exclude domain="database" path="." />
+    <exclude domain="file" path="datastore/" />
+    <exclude domain="sharedpref" path="." />
+</full-backup-content>
diff --git a/app/src/main/res/xml/data_extraction_rules.xml b/app/src/main/res/xml/data_extraction_rules.xml
--- a/app/src/main/res/xml/data_extraction_rules.xml
+++ b/app/src/main/res/xml/data_extraction_rules.xml
@@ -1,19 +1,13 @@
-<?xml version="1.0" encoding="utf-8"?><!--
-   Sample data extraction rules file; uncomment and customize as necessary.
-   See https://developer.android.com/about/versions/12/backup-restore#xml-changes
-   for details.
--->
+<?xml version="1.0" encoding="utf-8"?>
 <data-extraction-rules>
     <cloud-backup>
-        <!-- TODO: Use <include> and <exclude> to control what is backed up.
-        <include .../>
-        <exclude .../>
-        -->
+        <exclude domain="database" path="." />
+        <exclude domain="file" path="datastore/" />
+        <exclude domain="sharedpref" path="." />
     </cloud-backup>
-    <!--
     <device-transfer>
-        <include .../>
-        <exclude .../>
+        <exclude domain="database" path="." />
+        <exclude domain="file" path="datastore/" />
+        <exclude domain="sharedpref" path="." />
     </device-transfer>
-    -->
-</data-extraction-rules>
\ No newline at end of file
+</data-extraction-rules>
```

其他源码差异在IDE Changes中可直接审阅。完整tracked精确diff可用`git --no-pager diff -- app README.md AGENT.md TODO.md`；未追踪新增文件是全新源码，必须连同上述范围清单审阅，不能只看git diff默认tracked输出。不做git add/commit，不覆盖用户其他未提交工作。

## 验证与限制

Debug及仪器APK编译成功；专项19通过；全套61通过/4项原P5旧失败；connectedDebugAndroidTest无设备，**未设备运行**。静态检查三个核心新文件无报告问题；XML IDE曾报告exclude属性提示，但aapt编译成功，不将IDE提示当作设备备份恢复验收。设备需验证BOOT、8秒消费者锁等待超时、Action音量读回、停止竞态、清除失败恢复、备份/转移实际排除。测试与工程作用域已隔离，详情docs/testing.md。此报告是本地静态加固交接，不是Google Play合规认证、医疗安全或完整动态渗透测试。
