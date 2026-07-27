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
}
// Constraints + @Constraint/@Validated. KMP, cero dependencias. @Retention(SOURCE).
