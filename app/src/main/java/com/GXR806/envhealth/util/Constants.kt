package com.GXR806.envhealth.util

/** 全局业务常量。 */
object Constants {
    const val APP_NAME = "环境健康"
    const val TAG_PREFIX = "EnvHealth"
    const val ESTIMATED_VALUE_SUFFIX = "（估算值）"
    
    /** 音频采样常量 */
    object Audio {
        /** 采样率：44100 Hz */
        const val SAMPLE_RATE = 44100
        
        /** 单帧时长：100ms */
        const val FRAME_DURATION_MS = 100
        
        /** 每帧采样点数：44100 * 0.1 = 4410 */
        const val SAMPLES_PER_FRAME = (SAMPLE_RATE * FRAME_DURATION_MS / 1000)
        
        /** 聚合间隔：1 秒 = 10 帧 */
        const val AGGREGATION_FRAMES = 10
        
        /** 默认校准偏移：96.0 dB（dBFS -> dB SPL）
         * 
         * 说明：
         * - dBFS 范围：-96 dB 到 0 dB（数字信号满量程）
         * - dB SPL 范围：约 30-90 dB（声压级，真实环境噪音）
         * - 校准偏移 = 96 dB，使得：
         *   - 安静环境（-66 dBFS）→ 30 dB SPL
         *   - 正常说话（-36 dBFS）→ 60 dB SPL
         *   - 嘈杂环境（-16 dBFS）→ 80 dB SPL
         * 
         * 注意：此值为估算值，实际需要通过标准声级计校准
         */
        const val DEFAULT_CALIBRATION_OFFSET = 96.0f
        
        /** 突发噪声检测阈值：相邻帧差值 > 15dB */
        const val BURST_NOISE_THRESHOLD = 15.0f
    }
    
    /** 光照估算常量 */
    object Light {
        /** 
         * Lux 校准常数 K（工程近似）
         * 
         * 用于 lux 估算公式：lux ≈ (avgLuma / 255) × K / (exposureTimeSec × (iso / 100))
         * 
         * 调整说明：
         * - 初始值 2500 导致室内开灯环境显示 ~50,000 lux（偏高 100 倍）
         * - 调整为 125：室内开灯环境 → 250-500 lux（符合实际）
         * - 典型参考值：室内开灯 100-500 lux，办公室 300-500 lux，阴天室外 1000-2000 lux
         * 
         * 注意：此值为经验值，实际需要通过标准照度计校准
         */
        const val DEFAULT_LUX_K = 125f
        
        const val DEFAULT_LUX_ESTIMATION_FACTOR = 1.0f
    }
    
    /** 噪音等级分界（dB SPL） */
    object NoiseThreshold {
        const val QUIET_MAX = 35.0f
        const val NORMAL_MAX = 50.0f
        const val MODERATE_MAX = 65.0f
        // > 65.0f = LOUD / VERY_LOUD
    }
}
