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
        mavenLocal()   // genkit publicado localmente (publishToMavenLocal) cuando no está el hermano
        google()
    }
}

rootProject.name = "kvalid"

// genkit es la base compartida (REPO SEPARADO). Si está clonado como hermano (../genkit) se usa
// por composite build → código fresco en dev. Si no, se resuelve desde Maven
// (io.github.kuroxbyte:genkit-* / kspkit / aptkit): **genkit se publica ANTES que kvalid**.
val genkitDir = rootDir.resolve("../genkit")
if (genkitDir.exists()) {
    includeBuild(genkitDir)
}

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
