// Ouro HX for Android: core's components in Jetpack Compose, drawn from core's tokens (vendored by hash).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.roborazzi)
    `maven-publish`
}

android {
    namespace = "bot.ouro.ui.hx"
    compileSdk = 37
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // The gallery is drawn on a 411 by 6000 dp screen at xxhdpi, so each snapshot is a bitmap of about 90 MB and every_theme ran out of the default 512 MB heap.
            all { it.maxHeapSize = "3g" }
        }
    }
    publishing { singleVariant("release") { withSourcesJar() } }
}

dependencies {
    api(project(":ouro-actions"))
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Snapshots and the claims report go where CI uploads them from.
roborazzi { outputDir.set(layout.projectDirectory.dir("src/test/snapshots")) }

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "bot.ouro.ui"
            artifactId = "ouro-hx"
            version = providers.gradleProperty("ouro.version").get()
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("Ouro HX for Android")
                description.set("Ouro UI's components in Jetpack Compose: Button, Badge, Card, Item, Checkbox, TextField, Separator, DescriptionList, Dialog, Sheet and ConfirmDialog, from ouro-ui-core's specs and tokens.")
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
