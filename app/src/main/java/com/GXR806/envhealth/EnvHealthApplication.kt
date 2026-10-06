package com.GXR806.envhealth

import android.app.Application
import com.GXR806.envhealth.di.AppContainer

/**
 * 应用程序类
 * 
 * 负责初始化应用级别的单例依赖
 */
class EnvHealthApplication : Application() {
    
    lateinit var appContainer: AppContainer
        private set
    
    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
