# 隐私与资源静态复核（1.0.0）

本报告检查app/src/main与本轮release merged Manifest/合并R8配置，不是运行时网络抓包、设备安全认证或Google Play审核通过。

## 实际授权边界

privacyConsentVersion v1默认未同意；Main真实snapshot null加载gate，中文滚动说明/同意保存/拒绝退出，设置只读入口。Application/AppContainer冷启动、BOOT、Worker、开启后台、Service实际采集检查同意和maintenance clearing/error；异常failclosed、CancellationException重抛。清除reset撤回同意/后台false，未重新同意不恢复cleanup；同意只调度周期清理，不开启麦克风/相机。权限授权仍须可见页面明确手动开始，首页退场撤销pending。

原始音频/图像仅内存计算；Room只持久特征与区间/报告/建议，7/90天保留。估算值必须标注，媒体%不是dB。备份和设备转移排除database/datastore/sharedpref；OEM设备恢复未实测。

## 权限：源码8项与merged补充

| 来源 | 权限 | 用途 |
|---|---|---|
| main | RECORD_AUDIO | 内存噪音特征 |
| main | CAMERA | 前置相机光照估算 |
| main | POST_NOTIFICATIONS | 前台/健康提醒通知 |
| main | FOREGROUND_SERVICE | 用户手动可停止服务 |
| main | FOREGROUND_SERVICE_MICROPHONE | Android14麦克风类型 |
| main | FOREGROUND_SERVICE_CAMERA | Android14相机类型 |
| main | MODIFY_AUDIO_SETTINGS | 用户提醒动作只降低媒体音量并读回 |
| main | RECEIVE_BOOT_COMPLETED | 仅恢复本地任务 |
| dependency merged | WAKE_LOCK | WorkManager执行期间唤醒协调 |
| dependency merged | ACCESS_NETWORK_STATE | WorkManager网络状态代理，无互联网访问授权 |
| dependency merged | com.GXR806.envhealth.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION | AndroidX动态receiver签名权限 |

源码Manifest及本轮 `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml` 已读取，**无INTERNET/WRITE_SETTINGS**；main静态搜索HTTP/URLConnection/OkHttp/Retrofit/WebView调用未发现上传客户端，仅文档URL/注释，不推出绝对运行时无联网。合并清单包含库自己的初始化/系统组件，不把所有库组件假称私有：SystemJobService由BIND_JOB_SERVICE限制；Diagnostics/ProfileInstall由DUMP限制。

## 提醒/资源/崩溃

通知先持久claim后send，权限/频道blocked不claim，unknown保留节流可能少一次通知；仅接受且保存成功计反馈分母。私有显式immutable动作，UUID/URI/持久事件/有效窗口匹配、8s超时和防重放；不提升音量。

Noise/Light batch每30s降采样、max3/最老90s，suspendRoom List Insert自带事务，IO+NonCancellable提交移除；失败留≤3、暂停对应采集、中文首页error/metadata日志。AppContainer持有pending，Service stop/join后flush；clear全部writerjoin后discard，不复活旧特征。正常尾帧无burst亦flush并按timestamp去重。系统强杀/OOM仍可能丢未提交特征。

Main解绑CameraX/注销耳机，IO release AudioRecord与executor等待，重复stop保留join句柄；clear gate外stop/join，再gate内删库/reset，error关闭入口。全局uncaught handlerfinally委托原默认系统handler，非fatal仅尽力异步请求cleanup，不等待；OOM/Error不创建cleanup，不吞致命、不上传、不自启动。App/Service/cleanup/耳机ownerCEH最小元数据failclosed。源码无新增裸Thread/GlobalScope，相机Executor明确shutdown/await；已B优化Aweight/sort/light早期采样不重复修改。

## Release规则与证据限制

release minify/resource shrink已构建成功，configuration.txt读取确认Room/Work/Compose等consumer rules，DAO/Entity/应用全包keep已去除；实际Worker保留Context/WorkerParameters构造和类名，Manifest入口由AAPT规则。R8产物/unsigned metadata存在不是混淆设备验证；未运行standalone analyzer/Python脚本，不伪造量化评分。

无连接设备：隐私保存失败/拒绝/重启、BOOT/Worker/service阻断、真实故障批次/停止释放、清除并发与kill、通知动作/OEM路由/备份恢复仍待验收。未run lintRelease/网络抓包/Play审核，不称合规或0warnings。
