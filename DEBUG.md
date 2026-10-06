# 调试指南：健康建议不显示问题

## 问题症状
- 睡眠报告显示正常（有评分）
- 健康建议显示"暂无建议"

## 可能原因

### 1. 24 小时去重策略
**问题**：同一条规则在 24 小时内只会触发一次。如果您在测试中多次生成报告，后续的报告不会再生成相同的建议。

**验证方法**：
```bash
# 查看数据库中的建议记录
adb shell "run-as com.GXR806.envhealth sqlite3 /data/data/com.GXR806.envhealth/databases/env_health.db 'SELECT id, triggerReason, title, createdAt FROM advice_items ORDER BY createdAt DESC LIMIT 10;'"
```

**解决方法**：清除建议缓存
```bash
# 清空建议表
adb shell "run-as com.GXR806.envhealth sqlite3 /data/data/com.GXR806.envhealth/databases/env_health.db 'DELETE FROM advice_items;'"
```

### 2. 所有规则条件都不满足
**问题**：如果环境数据过于"完美"，可能没有任何规则被触发。

**当前规则触发条件**：
- R01: 噪音平均 > 25 dB （✅ 已调整，更灵敏）
- R02: 噪音突发 > 5 次 （✅ 已调整，更灵敏）
- R13: 噪音适中 15-25 dB （✅ 已调整）
- R03: 噪音峰值 > 70 dB
- R04: 光照平均 > 50 lux
- R05: 光照波动 > 20 lux
- R06: 睡前光照未调暗
- R07: 光照略高 (5-50 lux)
- R08: 规律性标准差 > 1.5 小时
- R11: 总分 60-80（环境良好）
- R12: 总分 > 80（环境优秀）⭐ **应该触发**
- R13: 噪音适中 (35-45 dB)
- R14: 总分 < 40（环境较差）
- R15: 规律性数据不足

**验证方法**：
```bash
# 查看 logcat 日志
adb logcat -s AdviceEngine:* GenerateHealthAdviceUseCase:*
```

### 3. 数据采集不足
**问题**：如果睡眠时间太短（< 1 分钟），可能数据不足以进行准确分析。

**建议**：
- 最短测试时间：**2-3 分钟**
- 数据保存间隔：30 秒一条记录
- 至少需要：2-3 条有效数据

## 快速测试步骤

### 步骤 1：清除旧数据
```bash
# 清空建议表（解除 24 小时去重）
adb shell "run-as com.GXR806.envhealth sqlite3 /data/data/com.GXR806.envhealth/databases/env_health.db 'DELETE FROM advice_items;'"

# 清空报告表（可选）
adb shell "run-as com.GXR806.envhealth sqlite3 /data/data/com.GXR806.envhealth/databases/env_health.db 'DELETE FROM sleep_reports;'"
```

### 步骤 2：开始新的睡眠监测
1. 打开应用 → "报告"标签页
2. 点击"开始"按钮
3. **等待至少 2-3 分钟**
4. 在等待期间：
   - 制造噪音（说话、播放音乐）
   - 开关灯光
5. 点击"结束监测"

### 步骤 3：查看日志
```bash
# 实时查看建议生成日志
adb logcat -c && adb logcat | grep -E "(AdviceEngine|GenerateHealthAdviceUseCase|ReportViewModel)"
```

## 预期日志输出

如果一切正常，应该看到：
```
AdviceEngine: 生成建议: [R12] 环境优秀
AdviceEngine: 共生成 1 条建议
GenerateHealthAdviceUseCase: 生成健康建议成功: 共 1 条
ReportViewModel: 健康建议已生成: 1 条
```

如果看到：
```
AdviceEngine: 规则 R12 在 24 小时内已触发，跳过
AdviceEngine: 共生成 0 条建议
GenerateHealthAdviceUseCase: 未生成任何建议（规则未匹配或已去重）
```

说明触发了 24 小时去重策略，需要执行步骤 1 清除旧数据。
