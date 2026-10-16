plugins {
    id("plaza.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "io.github.zxyszx.subnest.data" }

dependencies {
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
