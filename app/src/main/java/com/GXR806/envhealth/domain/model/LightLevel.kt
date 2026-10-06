package com.GXR806.envhealth.domain.model

/**
 * 光照等级分类
 */
enum class LightLevel {
    /** 黑暗：< 5 lux（如关灯睡眠环境） */
    DARK,
    
    /** 昏暗：5-50 lux（如夜灯、微弱照明） */
    DIM,
    
    /** 适中：50-300 lux（如室内正常照明） */
    MODERATE,
    
    /** 明亮：> 300 lux（如白天室内、户外阴天） */
    BRIGHT;
    
    companion object {
        /**
         * 根据 lux 值判断光照等级
         */
        fun fromLux(lux: Float): LightLevel {
            return when {
                lux < 5f -> DARK
                lux < 50f -> DIM
                lux < 300f -> MODERATE
                else -> BRIGHT
            }
        }
        
        /**
         * 根据相对亮度指数判断等级（降级模式，0-100）
         */
        fun fromRelativeBrightness(brightness: Float): LightLevel {
            return when {
                brightness < 10f -> DARK
                brightness < 30f -> DIM
                brightness < 60f -> MODERATE
                else -> BRIGHT
            }
        }
    }
}
