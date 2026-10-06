package com.GXR806.envhealth.domain.model

/**
 * 音频设备类型枚举
 * 用于分类耳机设备
 */
enum class DeviceType {
    /** 有线耳机 */
    WIRED,
    
    /** 蓝牙耳机 */
    BLUETOOTH,
    
    /** 扬声器 */
    SPEAKER,
    
    /** 未知设备 */
    UNKNOWN
}
