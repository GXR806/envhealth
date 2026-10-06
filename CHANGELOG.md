# 更新日志

本文档记录 GXR806 环境健康应用的重要更新和变更。

---

## 📅 2024-10-06：评分算法优化与敏感度提升

### 🎯 优化目标
提高噪音和光照评分的敏感度，使评分更准确地反映实际睡眠环境质量。

### 🔧 实施的更改

#### 1. 噪音评分算法优化 ✅
**文件**: [`SleepAnalyzer.kt`](file:///home/wzh/wzh/aaaaaa/gxr-envhealth/envhealth-main/envhealth-main/app/src/main/java/com/GXR806/envhealth/domain/analysis/SleepAnalyzer.kt)

**更改前**：
```kotlin
avgLeqPenalty = min((avgLeq - 25) * 2.5, 45)     // 基线 25dB
maxDbPenalty = min((maxDb - 40) * 1.2, 30)       // 基线 40dB
burstEventPenalty = min(burstCount * 2.5, 25)
```

**更改后**：
```kotlin
avgLeqPenalty = min((avgLeq - 20) * 3.0, 50)     // 基线 20dB，权重 50%
maxDbPenalty = min((maxDb - 35) * 1.5, 30)       // 基线 35dB，权重 30%
burstEventPenalty = min(burstCount * 4.0, 20)    // 权重 20%
```

**效果**：
- 平均噪音基线从 25dB → **20dB**（更灵敏，更早检测到噪音）
- 峰值噪音基线从 40dB → **35dB**（更灵敏）
- 突发噪音惩罚系数从 2.5 → **4.0**（更重视突发噪音）

#### 2. 光照评分算法优化 ✅
**文件**: [`SleepAnalyzer.kt`](file:///home/wzh/wzh/aaaaaa/gxr-envhealth/envhealth-main/envhealth-main/app/src/main/java/com/GXR806/envhealth/domain/analysis/SleepAnalyzer.kt)

**更改前**：
```kotlin
avgLuxPenalty = min((avgLux - 5) * 1.5, 40)
fluctuationPenalty = min(stdDevLux * 2, 30)
preSleepTrendPenalty = 30 if 睡前1h未下降 else 0
```

**更改后**：
```kotlin
avgLuxPenalty = min((avgLux - 2) * 2.5, 50)       // 基线 2 lux
fluctuationPenalty = min(stdDevLux * 3.0, 30)     // 系数 3.0
preSleepTrendPenalty = 20 if 睡前1h未下降 else 0  // 权重 20%
```

**效果**：
- 光照基线从 5 lux → **2 lux**（更灵敏，更早检测到光照干扰）
- 波动惩罚系数从 2.0 → **3.0**（更重视光照稳定性）
- 睡前趋势惩罚从 30 分 → **20 分**（权重调整）

#### 3. Bug 修复：光照评分始终显示 70 分 ✅
**问题**：当光照数据不足时（`avgLux = 0`），评分计算仍返回 70 分，容易误判。

**修复**：
```kotlin
if (stats.avgLux == 0f) {
    LogX.w(TAG, "光照数据不足，返回默认分数 88")
    return 88f  // 数据不足默认分（高于 70，避免误判）
}
```

**效果**：数据不足时返回 88 分，而非 70 分，避免误判为"光照环境一般"。

#### 4. 建议规则调整 ✅
**文件**: [`AdviceEngine.kt`](file:///home/wzh/wzh/aaaaaa/gxr-envhealth/envhealth-main/envhealth-main/app/src/main/java/com/GXR806/envhealth/domain/analysis/AdviceEngine.kt)

**更改**：
- **R13 规则**：噪音适中范围从 **35-45 dB** 调整为 **15-25 dB**
  - 更符合睡眠环境的实际标准
  - 提供更精准的改善建议

### 📊 性能影响对比

| 指标 | 优化前 | 优化后 | 变化 |
|------|--------|--------|------|
| 噪音评分基线 | 25dB | **20dB** | ↓ 5dB（更敏感）|
| 噪音峰值基线 | 40dB | **35dB** | ↓ 5dB（更敏感）|
| 突发噪音系数 | 2.5 | **4.0** | ↑ 60%（更敏感）|
| 光照评分基线 | 5 lux | **2 lux** | ↓ 3 lux（更敏感）|
| 光照波动系数 | 2.0 | **3.0** | ↑ 50%（更敏感）|
| R13 范围 | 35-45 dB | **15-25 dB** | 调整 |
| 光照数据不足时评分 | 70 分 | **88 分** | Bug 修复 |

### 🎯 预期效果

1. **更准确的噪音评分**：20dB 基线能更早检测到轻微噪音干扰
2. **更敏感的光照评分**：2 lux 基线能检测到极低亮度的光照
3. **更精准的建议**：R13 规则调整后，15-25 dB 的适中噪音范围更符合实际
4. **Bug 修复**：光照数据不足时不再误判为 70 分

### 📝 评分公式示例

#### 噪音评分示例

**场景 1：极安静环境（优秀）**
- avgLeq = 15dB, maxDb = 20dB, burstCount = 0
- avgLeqPenalty = 0, maxDbPenalty = 0, burstEventPenalty = 0
- **评分 = 100** ✅

**场景 2：轻微噪音（良好）**
- avgLeq = 25dB, maxDb = 30dB, burstCount = 2
- avgLeqPenalty = (25-20)*3.0 = 15
- maxDbPenalty = 0
- burstEventPenalty = 2*4.0 = 8
- **评分 = 100 - 15 - 0 - 8 = 77** ⚠️

**场景 3：嘈杂环境（较差）**
- avgLeq = 35dB, maxDb = 50dB, burstCount = 8
- avgLeqPenalty = (35-20)*3.0 = 45 (达到上限 50)
- maxDbPenalty = (50-35)*1.5 = 22.5
- burstEventPenalty = 8*4.0 = 20 (达到上限 20)
- **评分 = 100 - 45 - 22.5 - 20 = 12.5** ❌

#### 光照评分示例

**场景 1：完全黑暗（优秀）**
- avgLux = 0, stdDevLux = 0, preSleepTrendDecreasing = true
- **返回默认分 88**（数据不足）

**场景 2：极低光照（优秀）**
- avgLux = 1 lux, stdDevLux = 0.5, preSleepTrendDecreasing = true
- avgLuxPenalty = 0
- fluctuationPenalty = 0.5*3.0 = 1.5
- preSleepTrendPenalty = 0
- **评分 = 100 - 0 - 1.5 - 0 = 98.5** ✅

**场景 3：轻微光照（良好）**
- avgLux = 5 lux, stdDevLux = 2, preSleepTrendDecreasing = false
- avgLuxPenalty = (5-2)*2.5 = 7.5
- fluctuationPenalty = 2*3.0 = 6
- preSleepTrendPenalty = 20
- **评分 = 100 - 7.5 - 6 - 20 = 66.5** ⚠️

---

## 📅 2024-10-06：P5 睡眠环境分析与建议引擎优化

### 🎯 优化目标
改进噪音检测和建议生成机制，提升用户体验。

### 🔧 实施的更改

#### 1. 噪音检测模块优化
**文件**: [`NoiseAnalyzer.kt`](file:///home/wzh/wzh/aaaaaa/gxr-envhealth/envhealth-main/envhealth-main/app/src/main/java/com/GXR806/envhealth/domain/analysis/NoiseAnalyzer.kt), [`SleepAnalyzer.kt`](file:///home/wzh/wzh/aaaaaa/gxr-envhealth/envhealth-main/envhealth-main/app/src/main/java/com/GXR806/envhealth/domain/analysis/SleepAnalyzer.kt)

**更改内容**：
- 突发噪声事件检测改进：
  - 检测条件：**相邻帧差值 > 15dB 且超过阈值**
  - 原有逻辑已正确实现此功能
  - 位置：[`NoiseAnalyzer.detectBurst()`](method://com.GXR806.envhealth.domain.analysis.NoiseAnalyzer#detectBurst) 方法
  
- 噪音评分基线调整：
  - **原基线**: 30dB → 25dB → **20dB**（最终）
  - 理由：更符合实际睡眠环境监测需求
  
- **突发噪声次数**已计入噪音环境评分，权重为 20%

#### 2. 建议引擎简化
**文件**: [`AdviceEngine.kt`](file:///home/wzh/wzh/aaaaaa/gxr-envhealth/envhealth-main/envhealth-main/app/src/main/java/com/GXR806/envhealth/domain/analysis/AdviceEngine.kt), [`ReportViewModel.kt`](file:///home/wzh/wzh/aaaaaa/gxr-envhealth/envhealth-main/envhealth-main/app/src/main/java/com/GXR806/envhealth/ui/viewmodel/ReportViewModel.kt), [`ReportScreen.kt`](file:///home/wzh/wzh/aaaaaa/gxr-envhealth/envhealth-main/envhealth-main/app/src/main/java/com/GXR806/envhealth/ui/screen/ReportScreen.kt)

**更改内容**：
- ❌ **取消 24 小时去重规则**
  - 移除了 `DEDUP_WINDOW_MS` 和去重逻辑
  - 每次生成报告时都会重新评估所有规则
  - 用户可以在短时间内多次测试并获得建议
  
- ❌ **移除"清除缓存"按钮**
  - 删除了 [`ReportViewModel.clearAdviceCache()`](method://com.GXR806.envhealth.ui.viewmodel.ReportViewModel#clearAdviceCache) 方法
  - 删除了报告页面的"清除缓存"按钮
  - 简化了用户界面

#### 3. 光照监测优化
**文件**: [`LightSensorCamera.kt`](file:///home/wzh/wzh/aaaaaa/gxr-envhealth/envhealth-main/envhealth-main/app/src/main/java/com/GXR806/envhealth/data/sensor/light/LightSensorCamera.kt)

**更改内容**：
- ✅ 光照监测改用**前置摄像头**
  - 更适合睡眠环境监测
  - 用户将手机放在床头时，前置摄像头朝向用户，更能准确监测睡眠环境光照

---

## 技术说明

### 采样频率
- **噪音采样**：持续录音，实时分析，**每 30 秒**保存一条记录
- **光照采样**：**每 1 秒**拍摄一帧图像，**每 30 秒**保存一条记录

### 建议规则（13 条）
- R01: 噪音平均 > 25 dB
- R02: 噪音突发 > 5 次
- R03: 噪音峰值 > 70 dB
- R04: 光照平均 > 50 lux
- R05: 光照波动 > 20 lux
- R06: 睡前光照未调暗
- R07: 光照略高 (5-50 lux)
- R08: 规律性标准差 > 1.5 小时
- R11: 总分 60-80（环境良好）
- R12: 总分 > 80（环境优秀）
- R13: 噪音适中 (15-25 dB)
- R14: 总分 < 40（环境较差）
- R15: 规律性数据不足
