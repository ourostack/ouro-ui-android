// Ouro Actions for Android: the runtime every door goes through (the person's press and an agent's AppFunction call),
// on the catalog ouro-actions-android generates from core's defineAction.
plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "bot.ouro.ui.actions"
    compileSdk = 37
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }
    publishing { singleVariant("release") { withSourcesJar() } }
}

dependencies {
    api(libs.androidx.appfunctions)
    api(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "bot.ouro.ui"
            artifactId = "ouro-actions"
            version = providers.gradleProperty("ouro.version").get()
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("Ouro Actions for Android")
                description.set("The runtime behind Ouro UI's actions on Android: a person's press and an agent's AppFunction call take one path, person-only actions refuse agents, and a consequential action asks the person first, with a built-in confirmation.")
                url.set("https://github.com/ourostack/ouro-ui-android")
                licenses { license { name.set("MIT"); url.set("https://opensource.org/licenses/MIT") } }
                // Maven Central requires the developers and scm blocks (RELEASING.md).
                developers { developer { id.set("arimendelow"); name.set("Ari Mendelow"); url.set("https://github.com/arimendelow") } }
                scm {
                    url.set("https://github.com/ourostack/ouro-ui-android")
                    connection.set("scm:git:https://github.com/ourostack/ouro-ui-android.git")
                    developerConnection.set("scm:git:ssh://git@github.com/ourostack/ouro-ui-android.git")
                }
            }
        }
    }
    repositories { maven { name = "build"; url = uri(rootProject.layout.buildDirectory.dir("maven")) } }
}
