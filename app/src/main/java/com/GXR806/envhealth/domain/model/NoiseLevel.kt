package com.GXR806.envhealth.domain.model

/**
 * 噪音等级枚举
 * 用于分类噪音强度
 */
enum class NoiseLevel {
    /** 安静：< 40dB */
    QUIET,
    
    /** 正常：40-60dB */
    NORMAL,
    
    /** 轻度嘈杂：60-70dB */
    MODERATE,
    
    /** 嘈杂：70-85dB */
    LOUD,
    
    /** 非常嘈杂：> 85dB */
    VERY_LOUD
}
