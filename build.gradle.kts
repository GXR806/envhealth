// 项目级 Gradle 配置：各模块通过版本目录统一管理依赖。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}