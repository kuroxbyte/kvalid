plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    explicitApi()
    jvmToolchain(17)

    jvm()
    js(IR) { nodejs(); browser() }
    linuxX64()
    mingwX64()
    macosX64()
    macosArm64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":kvalid-runtime"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
