pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "kvalid"

includeBuild("../genkit")

include(
    ":kvalid-annotations",
    ":kvalid-runtime",
    ":kvalid-core",
    ":kvalid-processor",
    ":kvalid-i18n",
    ":kvalid-ktor",
    ":kvalid-spring",
    ":kvalid-benchmarks",
    ":kvalid-apt",
    ":kvalid-samples",
    ":kvalid-integration-tests",
    ":kvalid-incremental-tests",
)
