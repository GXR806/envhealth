# 环境健康 release：保持最小规则，依赖的 consumer rules 由 AGP/R8 合并。
# Room生成代码及库consumer rules负责数据库反射入口；DAO/Entity非反射，不全包保留。
# Manifest中的Activity/Service/Receiver由AAPT规则保留，无需重复BootReceiver规则。
# WorkManager持久类名/反射构造：只明确保留本应用的实际Worker及SDK构造签名。
-keep,allowoptimization class com.GXR806.envhealth.worker.ReminderWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
# Kotlin metadata/Compose/Room的必要规则使用依赖consumer rules，无全包keep。
