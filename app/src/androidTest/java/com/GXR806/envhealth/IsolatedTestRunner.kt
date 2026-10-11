package com.GXR806.envhealth

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** 不运行生产 Application 的 DI/周期调度；所有测试显式构造临时存储和假通知。 */
class IsolatedTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, Application::class.java.name, context)
}
