plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.jmh)
}

kotlin { jvmToolchain(17) }

// Benchmarks JMH: kvalid (codegen, cero reflexión) vs Hibernate Validator (reflexión).
// No se publica; no participa del release. Los modelos van en main; jmh los ve.
dependencies {
    implementation(project(":kvalid-annotations"))
    implementation(project(":kvalid-runtime"))
    ksp(project(":kvalid-processor"))

    implementation(libs.hibernate.validator)
    runtimeOnly(libs.expressly)
}

jmh {
    warmupIterations.set(2)
    iterations.set(3)
    fork.set(1)
    warmup.set("500ms")
    timeOnIteration.set("500ms")
    benchmarkMode.set(listOf("avgt"))
    timeUnit.set("ns")
}
