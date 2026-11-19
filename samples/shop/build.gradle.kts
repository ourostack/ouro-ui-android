// A shopping list that declares its actions once (shopping.actions.mts) and gets both doors from them: buttons people
// press and AppFunctions agents call. CI runs its X-ray on an emulator.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "bot.ouro.shop"
    compileSdk = 37
    defaultConfig {
        applicationId = "bot.ouro.shop"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = providers.gradleProperty("ouro.version").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

ksp { arg("appfunctions:aggregateAppFunctions", "true") }

dependencies {
    implementation(project(":ouro-hx"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    ksp(libs.androidx.appfunctions.compiler)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
