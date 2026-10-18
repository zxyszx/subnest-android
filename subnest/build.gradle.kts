plugins {
    id("plaza.android.application")
    id("plaza.android.compose")
}

android {
    namespace = "io.github.zxyszx.subnest"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "io.github.zxyszx.subnest"
        versionCode = 3
        versionName = "0.1.0-preview.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":subnest-data"))
    implementation(project(":designsys"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.core)
    implementation(libs.kotlinx.coroutines.core)
    constraints {
        implementation(libs.androidx.concurrent.futures)
        implementation(libs.androidx.compose.material3)
        implementation(libs.androidx.compose.material.icons.core)
    }
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
