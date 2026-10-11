# 环境健康1.0.0交付与验收清单

[x]表示实现/所列自动化已完成，不表示设备验收；[ ]保留未实现或未验收及原因。代码交付完成，发布待验收，不把所有项目勾完。

## 最新用户反馈与 launcher 图标

- [x] 用户反馈通知问题验证成功、连接设备检测已完成（用户证据，仅此范围；下方无设备为历史轮次状态，不代表当前连接）。
- [x] 仅应用launcher授权：artwork/app_icon.png原稿保留，Gradle原生Copy生成drawable-nodpi资源/AGP Variant Sources任务依赖/preBuild依赖/缺图中文fail；前景按原稿比例中心留白+蓝紫背景，普通/round一致，移除机器人monochrome。串行assembleDebug、assembleRelease成功；无依赖/通知small icon/Service/策略/权限/SDK/schema变更。
- [ ] launcher设备外观：同签名更新APK，桌面/抽屉及圆形/圆角遮罩核验白色主体完整与留白；旧图缓存可重新添加快捷方式/重启桌面，不清应用数据、不卸载。本次未截图/部署。
- [ ] 全部生命周期、19项Reminder仪器执行、Release签名安装及Profiler仍按下方独立验收，不因上述用户反馈自动勾选。

## 最新应用外无消息专项（历史自动化轮次）

- [x] 确证并修复普通异常永久终止实时提醒监听；Service-owned恢复检查/上游订阅，取消与generation不变，不重发未知claim。无Home取消/UI订阅依赖证据，设备实际根因未确证。
- [x] 设置系统提醒诊断（近期原因/持久event与claim/静默频道/active列表），“检查健康提醒”调用运行Service原策略，不绕节流或自动开硬件；只读刷新。JVM新增4现102/0/0，Reminder仪器新增3现19仅编译；debug/androidTest/fullunit串行成功，追加后androidTest与release成功，major69仍在，coverage未重跑。
- [ ] 真机：低音量+可调阈值安全测试，按Home非force-stop/划掉，分别查抽屉/横幅和状态页；确认前台/静默已发冷却不重发、异常恢复、stop/clear不新增。ui_state无设备；需状态截图及Reminder/Service日志，不将simulated 5s更新当系统调度保证。

## 已完成代码与自动化

- [x] 本次延迟/显著性：原5s播放tick与音量实变force；Guard先实时发布、60s/日期completed缓存、不丢tracker权重、stop撤资格/clear缓存generation；首页耳机页生产HIGH Banner与频道真实诊断/ON_RESUME/系统直达。JVM新增3现98/0/0，UI新增Banner1现7仅编译；debug/androidTest/release串行成功。无新依赖schemaSDK/评分或风险规则，旧频道/DND/24h7d不动。coverage未重跑。
- [ ] 本次真机追加：不戴耳机短时>80%后立即降低（不持续高音量），5s播放目标/OEM音量变化、首页及耳机HIGH/SAFE/stop/断开/周风险、静默24h仍应用内Banner、频道状态返回刷新与fallback、系统横幅控制；慢库后续事件积压/60s缓存外部清理/日期跨越/clear/stop竞态。无runningdevices，未执行；首次通知类型或自然冷却，不清库绕过。

- [x] HIGH无健康提醒bug：服务实时耳机/设置变化 + Noise sample30s/ticker30s，复用持久提醒门控；stop取消/join、发送前重读/generation；不改评分/风险/保留/渠道ID。新增实时仪器8项仅编译，JVM本次95/0/0；debug及androidTest构建成功，无runningdevices。
- [x] P0–P5：工程、手动DI、Room schema3/Repository、权限、噪音/光照估算、手动睡眠/三维评分/建议/最新报告；公式权重不改。
- [x] P6耳机守护：有线/USB/A2DP、媒体百分比、单调播放区间/加权音量、多原因风险与可停止生命周期。
- [x] P7本地保留：特征7天、已结束报告/会话/建议/耳机90天；唯一Work/24h清理节流/ongoing保护。
- [x] 设置阈值、噪音校准finite0..120/default96、静默原子时间、后台默认false、明确授权后手动开始。
- [x] 主题SYSTEM/LIGHT/DARK持久/即时全页面/系统栏同步、不重启服务；统一视觉、Dashboard、历史自然日周月真实图表/报告详情、空错误加载/中文语义。
- [x] A隐私v1持久/null加载gate/滚动说明/拒绝退出/只读入口；Application/BOOT/Worker/后台/Service检查，清除撤回。
- [x] maintenance可重入clearMutex、持久clearing/error、gate外stop/join、gate内清库/pending/缓存/reset、generation拒旧写、不恢复未同意工作；备份排除。
- [x] 提醒持久claim、通知预检查/false回滚/未知结果至多一次、防重放私有动作、降低音量读回/8sdeadline。
- [x] B音频Aweight/noise排序复用、Light图像1s早期过滤，不重复优化或更改公式。
- [x] C有界Noise/Light每30s特征batch3/最老90s，生产Repository.insertSamples/suspendRoom自动事务；失败≤3保留/暂停/中文error，普通stop尾flush无burst/no dup，NonCancellable提交移除+writerjoin，clear后discard防复活。
- [x] C全局handler finally委托原systemHandler、OOM不等release/不建cleanup，关键owner CEH failclosed、异步资源释放/重复stop保留join句柄，无裸Thread新增。
- [x] D生产组件6项simulated UI harness、隔离集成16项及DAO仪器APK编译（不勾设备执行）。
- [x] 最终JVM95通过/0失败/0跳过（原90+Batch5）；覆盖任务重新生成，直接生产核心243/246=98.78%达用户批准≥60%目标。
- [x] 最终debug/androidTest/release构建成功，release R8/resource shrink、最小Worker规则、consumer复核、Manifest无INTERNET；可选4环境变量生产签名实际配置，无debugkey回退。
- [x] README/AGENT/TODO整体去冲突、privacy/testing更新、六项项目交付总结artifact更新，非阶段日志堆叠。

## 发布前验收阻塞（无连接设备/无生产密钥）

- [ ] connectedDebugAndroidTest：16集成/6simulated UI/DAO等真实设备执行；当前仅编译，adb空列表。
- [ ] debug安装与权限：隐私拒绝/同意保存失败/重启持久/清除再同意，BOOT/Worker/service全部gate；授权弹窗中切Tab/后台false不采集。
- [ ] 真实Noise/Light：实时→30s特征→batch≤3/90s→stop尾flush无burst/无dup；数据库故障暂停/有界保留/明确重试；AudioRecord/CameraX/Executor释放、重复stop、销毁/启动竞态。
- [ ] clear并发报告/stop/重试、进程维护error恢复、所有pending和旧VM/Dashboard/History缓存不复活。
- [ ] P6设备有线/USB/A2DP插拔、第三方暂停恢复/2min/跨午夜、媒体音量键/OEM5s兜底/5s播放目标、实际路由、全部停止入口。
- [ ] HIGH修复真机：首次VOLUME或自然24h/7d到期（不清数据），实际播放短时>80%，约5s音量兜底/5s播放识别目标；rapid update不重发，stop/后台false/privacy reset/clear不得新增实时提醒，日志辨别健康频道独立禁用/DND/静默/节流。设备工具无runningdevices，仅编译16项仪器。
- [ ] 提醒通知权限/频道/24h/静默/7d静音/反馈、动作只降低并读回、防重放/8s锁等待，BOOT只任务。
- [ ] Room7/90天边界/cascade/ongoing保护、Work实际周期/后台false仅cleanup、备份/转移排除。
- [ ] 主题三选/系统栏对比/旋转重启/强制模式、TalkBack/200%字体/小屏48dp/图表语义、真实日周月缺失/详情返回。
- [ ] Profiler CPU/SystemTrace/Memory：30min/2h/过夜、低性能设备/UI采样卡顿/GC/停止后资源，未伪造性能数字。
- [ ] 实体机截图：隐私、Dashboard、Monitor、Headphone、Report/History、深浅主题，无设备不得伪图。
- [ ] unsigned release不可直接安装：负责人提供4签名env/生产key、证书校验后安装，回归Room/Worker反射/全部关键流程；本轮无key/未安装。
- [ ] lintRelease及Play声明/审核：本轮未run lint，不称0warnings；静态隐私不等于Play合规。

操作及每项预期详见交付总结artifact；当前下一维护动作是设备验收，不新增阶段。

## 可选遗留（未授权，非本次已授权实现欠项）

- [ ] P8自动睡眠判定：未实现，不自动推进。
- [ ] 数据导出/专用通知图标/启动页：保持现有资源，另行授权；应用launcher已在本次独立授权完成。
- [ ] whole-module覆盖目标≥60%：**762/5671=13.44%**，含UI/生成DAO等；用户此次批准核心≥60%，已达98.78%，whole提升为另行范围，不偷换口径。
- [ ] Lux factor实时接入/标准仪表标定：当前K125工程估算，字段未用于生产相机公式，需另行授权。
